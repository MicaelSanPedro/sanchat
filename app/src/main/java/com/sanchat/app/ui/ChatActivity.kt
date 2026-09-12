package com.sanchat.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sanchat.app.R
import com.sanchat.app.model.Message
import com.sanchat.app.model.Models
import com.sanchat.app.net.NimClient
import com.sanchat.app.store.Store
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme

class ChatActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var conv: com.sanchat.app.model.Conversation
    private lateinit var adapter: MsgAdapter
    private lateinit var markwon: Markwon
    private lateinit var recycler: RecyclerView
    private lateinit var tvSetupWarning: TextView
    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnScrollDown: ImageButton
    private lateinit var tvEmpty: View
    private lateinit var typingIndicator: View
    private lateinit var dots: List<View>

    private val handler = Handler(Looper.getMainLooper())
    private var client: NimClient? = null
    private var streaming = false

    // buffers do stream atual
    private var rawContent = ""   // somente deltas de "content"
    private var thinkBuffer = ""  // somente deltas de "reasoning_content"
    private var pendingUi = false

    // raciocinios expandidos manualmente (sobrevive a rebind)
    private val expandedThink = mutableSetOf<Message>()

    // animacao dos pontinhos
    private var dotPhase = 0
    private val dotRunnable = object : Runnable {
        override fun run() {
            if (!::typingIndicator.isInitialized || typingIndicator.visibility != View.VISIBLE) return
            dotPhase = (dotPhase + 1) % dots.size
            dots.forEachIndexed { i, d ->
                d.animate().alpha(if (i == dotPhase) 1f else 0.25f).setDuration(200).start()
            }
            handler.postDelayed(this, 280)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_chat)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)

        store = Store(this)
        markwon = Markwon.builder(this)
            .usePlugin(CorePlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeTextColor(Color.parseColor("#9DDB3F"))
                        .codeBackgroundColor(Color.parseColor("#10130A"))
                        .codeBlockBackgroundColor(Color.parseColor("#0D0F07"))
                        .linkColor(Color.parseColor("#9DDB3F"))
                        .blockQuoteColor(Color.parseColor("#76B900"))
                        .blockQuoteWidth(3)
                }
            })
            .build()

        val id = intent.getStringExtra("id") ?: run { finish(); return }
        conv = store.getConversation(id) ?: run { finish(); return }

        tvSetupWarning = findViewById(R.id.tvSetupWarning)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        btnScrollDown = findViewById(R.id.btnScrollDown)
        tvEmpty = findViewById(R.id.tvEmpty)
        typingIndicator = findViewById(R.id.typingIndicator)
        dots = listOf(findViewById(R.id.dot1), findViewById(R.id.dot2), findViewById(R.id.dot3))
        recycler = findViewById(R.id.recyclerMessages)

        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        recycler.layoutManager = lm
        adapter = MsgAdapter()
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = updateScrollChip()
        })

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<LinearLayout>(R.id.chipModel).setOnClickListener { pickModel() }

        btnSend.setOnClickListener {
            if (streaming) client?.cancel() else send()
        }
        btnScrollDown.setOnClickListener {
            scrollToBottom()
            btnScrollDown.visibility = View.GONE
        }

        applyInsets()

        if (store.backendUrl().isBlank()) {
            tvSetupWarning.visibility = View.VISIBLE
        }
        refreshHeader()
        adapter.notifyDataSetChanged()
        updateEmpty()
    }

    /** Edge-to-edge: status bar em cima, teclado/nav bar embaixo. */
    private fun applyInsets() {
        val root = findViewById<View>(R.id.root)
        val topBar = findViewById<View>(R.id.topBar)
        val inputBar = findViewById<View>(R.id.inputBar)
        val baseTop = topBar.paddingTop
        val baseBottom = inputBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            topBar.setPadding(
                topBar.paddingLeft, baseTop + bars.top, topBar.paddingRight, topBar.paddingBottom
            )
            inputBar.setPadding(
                inputBar.paddingLeft, inputBar.paddingTop, inputBar.paddingRight,
                baseBottom + maxOf(bars.bottom, ime.bottom)
            )
            insets
        }
    }

    private fun refreshHeader() {
        findViewById<TextView>(R.id.tvTitle).text =
            if (conv.title.isBlank()) getString(R.string.new_conversation) else conv.title
        findViewById<TextView>(R.id.tvModel).text = Models.labelOf(conv.model)
    }

    private fun updateEmpty() {
        tvEmpty.visibility = if (conv.messages.isEmpty()) View.VISIBLE else View.GONE
        if (conv.messages.isEmpty()) btnScrollDown.visibility = View.GONE
    }

    // ---------- envio / stream ----------

    private fun send() {
        val text = etInput.text.toString().trim()
        if (text.isEmpty() || streaming) return
        val backend = store.backendUrl()
        if (backend.isBlank()) {
            tvSetupWarning.visibility = View.VISIBLE
            return
        }
        tvSetupWarning.visibility = View.GONE

        etInput.setText("")
        conv.messages.add(Message("user", text))
        if (conv.title.isBlank()) {
            conv.title = if (text.length > 40) text.substring(0, 40) + "…" else text
        }
        val ai = Message("assistant", "")
        conv.messages.add(ai)
        conv.updatedAt = System.currentTimeMillis()
        store.saveConversation(conv)

        adapter.notifyDataSetChanged()
        updateEmpty()
        scrollToBottom()

        rawContent = ""
        thinkBuffer = ""
        ai.thinking = ""
        ai.content = ""
        streaming = true
        setStreamUi(true)
        setTyping(true)

        val c = NimClient(backend, store.accessToken())
        client = c
        c.streamChat(
            model = conv.model,
            messages = buildHistory(),
            onDelta = { contentDelta, thinkDelta ->
                handler.post {
                    if (thinkDelta != null) thinkBuffer += thinkDelta
                    if (contentDelta != null) rawContent += contentDelta
                    recompute(ai)
                    scheduleUi()
                }
            },
            onDone = { handler.post { finishStream(ai, null) } },
            onError = { msg -> handler.post { finishStream(ai, msg) } }
        )
    }

    /** Liga/desliga visual de "streamando" (botao enviar vira parar). */
    private fun setStreamUi(on: Boolean) {
        btnSend.setImageResource(if (on) R.drawable.ic_stop else R.drawable.ic_send)
        btnSend.contentDescription = getString(if (on) R.string.stop else R.string.send)
    }

    /** Indicador de digitacao (tres pontinhos pulsando). */
    private fun setTyping(on: Boolean) {
        val vis = if (on) View.VISIBLE else View.GONE
        if (typingIndicator.visibility == vis) return
        typingIndicator.visibility = vis
        if (on) handler.post(dotRunnable) else handler.removeCallbacks(dotRunnable)
    }

    /**
     * Recalcula content/thinking da mensagem a partir dos buffers.
     * Cobre os dois formatos: campo reasoning_content (delta) e tags
     * <think>...</think> embutidas no content (estilo DeepSeek-R1).
     */
    private fun recompute(ai: Message) {
        val open = rawContent.indexOf("<think>")
        if (open < 0) {
            ai.content = rawContent
            ai.thinking = thinkBuffer
            return
        }
        val close = rawContent.indexOf("</think>")
        if (close < 0) {
            ai.content = rawContent.substring(0, open)
            ai.thinking = thinkBuffer + rawContent.substring(open + 7)
        } else {
            ai.content = rawContent.substring(0, open) + rawContent.substring(close + 8)
            ai.thinking = thinkBuffer + rawContent.substring(open + 7, close)
        }
    }

    private fun scheduleUi() {
        if (pendingUi) return
        pendingUi = true
        handler.postDelayed({
            pendingUi = false
            flushPending()
        }, 90)
    }

    private fun flushPending() {
        if (!streaming) return
        val pos = conv.messages.size - 1
        if (pos >= 0) {
            adapter.notifyItemChanged(pos)
            val lm = recycler.layoutManager as LinearLayoutManager
            val last = lm.findLastVisibleItemPosition()
            if (last >= pos - 1) scrollToBottom()
            val ai = conv.messages.getOrNull(pos)
            setTyping(ai != null && ai.content.isBlank() && ai.thinking.isBlank())
        }
    }

    private fun finishStream(ai: Message, error: String?) {
        streaming = false
        recompute(ai)
        if (error != null) {
            val prefix = getString(R.string.error_prefix, error)
            ai.content = if (ai.content.isBlank()) prefix else ai.content + "\n\n" + prefix
        }
        conv.updatedAt = System.currentTimeMillis()
        store.saveConversation(conv)
        setTyping(false)
        setStreamUi(false)
        adapter.notifyDataSetChanged()
        refreshHeader()
        updateScrollChip()
    }

    private fun scrollToBottom() {
        recycler.post { recycler.smoothScrollToPosition(adapter.itemCount - 1) }
    }

    private fun updateScrollChip() {
        if (conv.messages.isEmpty() || streaming) {
            btnScrollDown.visibility = View.GONE
            return
        }
        val lm = recycler.layoutManager as? LinearLayoutManager ?: return
        val last = lm.findLastVisibleItemPosition()
        btnScrollDown.visibility =
            if (last != RecyclerView.NO_POSITION && last < adapter.itemCount - 1) View.VISIBLE
            else View.GONE
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("SanChat", text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    /** Historico pro modelo: system + ultimas mensagens visiveis (sem thinking). */
    private fun buildHistory(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        out.add(
            "system" to "You are SanChat, a helpful AI assistant. Reply in the same language the user writes in. Use markdown when helpful."
        )
        for (m in conv.messages.takeLast(25)) {
            if (m.content.isBlank()) continue
            out.add(m.role to m.content)
        }
        return out
    }

    // ---------- seletor de modelo (bottom sheet) ----------

    private fun pickModel() {
        val sheet = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.sheet_models, null)
        val ll = v.findViewById<LinearLayout>(R.id.llModels)
        Models.ALL.forEach { entry ->
            val row = layoutInflater.inflate(R.layout.item_model, ll, false)
            val parts = entry.label.split(" — ")
            val name = row.findViewById<TextView>(R.id.tvModelName)
            name.text = parts.getOrElse(0) { entry.label }
            row.findViewById<TextView>(R.id.tvModelBrand).text = parts.getOrElse(1) { "" }
            val selected = entry.id == conv.model
            name.setTextColor(
                if (selected) Color.parseColor("#9DDB3F") else Color.parseColor("#F0F2E8")
            )
            row.findViewById<ImageView>(R.id.ivSelected).visibility =
                if (selected) View.VISIBLE else View.GONE
            row.setOnClickListener {
                conv.model = entry.id
                store.saveConversation(conv)
                refreshHeader()
                sheet.dismiss()
            }
            ll.addView(row)
        }
        sheet.setContentView(v)
        sheet.show()
    }

    // ---------- adapter ----------

    inner class MsgAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        inner class UserVH(v: View) : RecyclerView.ViewHolder(v) {
            val text: TextView = v.findViewById(R.id.tvText)
        }

        inner class AiVH(v: View) : RecyclerView.ViewHolder(v) {
            val thinkBox: LinearLayout = v.findViewById(R.id.thinkBox)
            val thinkHeaderRow: View = v.findViewById(R.id.thinkHeaderRow)
            val thinkHeader: TextView = v.findViewById(R.id.tvThinkHeader)
            val think: TextView = v.findViewById(R.id.tvThink)
            val chevron: ImageView = v.findViewById(R.id.ivChevron)
            val md: TextView = v.findViewById(R.id.tvMarkdown)
        }

        override fun getItemViewType(position: Int): Int =
            if (conv.messages[position].role == "user") 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                UserVH(inf.inflate(R.layout.item_message_user, parent, false))
            } else {
                AiVH(inf.inflate(R.layout.item_message_ai, parent, false))
            }
        }

        override fun getItemCount() = conv.messages.size

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
            val m = conv.messages[pos]
            if (h is UserVH) {
                h.text.text = m.content
                h.text.setOnLongClickListener {
                    copyToClipboard(m.content)
                    true
                }
            } else if (h is AiVH) {
                val hasThink = m.thinking.isNotBlank()
                h.thinkBox.visibility = if (hasThink) View.VISIBLE else View.GONE
                if (hasThink) {
                    val streamingThis =
                        streaming && pos == conv.messages.size - 1 && m.content.isBlank()
                    h.thinkHeader.text =
                        if (streamingThis) getString(R.string.thinking) else getString(R.string.reasoning)
                    val open = streamingThis || expandedThink.contains(m)
                    h.think.visibility = if (open) View.VISIBLE else View.GONE
                    h.chevron.rotation = if (open) 180f else 0f
                    h.think.text = m.thinking
                    h.thinkHeaderRow.setOnClickListener {
                        if (expandedThink.contains(m)) {
                            expandedThink.remove(m)
                            h.think.visibility = View.GONE
                            h.chevron.rotation = 0f
                        } else {
                            expandedThink.add(m)
                            h.think.visibility = View.VISIBLE
                            h.chevron.rotation = 180f
                        }
                    }
                }
                markwon.setMarkdown(h.md, m.content)
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
