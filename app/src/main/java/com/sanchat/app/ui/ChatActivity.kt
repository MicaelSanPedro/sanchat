package com.sanchat.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sanchat.app.R
import com.sanchat.app.model.Message
import com.sanchat.app.model.Models
import com.sanchat.app.net.NimClient
import com.sanchat.app.store.Store
import io.noties.markwon.Markwon

class ChatActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var conv: com.sanchat.app.model.Conversation
    private lateinit var adapter: MsgAdapter
    private lateinit var markwon: Markwon
    private lateinit var tvModel: TextView
    private lateinit var tvSetupWarning: TextView
    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var tvEmpty: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var client: NimClient? = null
    private var streaming = false

    // buffers do stream atual
    private var rawContent = ""   // somente deltas de "content"
    private var thinkBuffer = ""  // somente deltas de "reasoning_content"
    private var pendingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        store = Store(this)
        markwon = Markwon.create(this)

        val id = intent.getStringExtra("id") ?: run { finish(); return }
        conv = store.getConversation(id) ?: run { finish(); return }

        tvModel = findViewById(R.id.tvModel)
        tvSetupWarning = findViewById(R.id.tvSetupWarning)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        tvEmpty = findViewById(R.id.tvEmpty)

        val recycler = findViewById<RecyclerView>(R.id.recyclerMessages)
        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        recycler.layoutManager = lm
        adapter = MsgAdapter()
        recycler.adapter = adapter

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<LinearLayout>(R.id.topBar).setOnClickListener { pickModel() }

        btnSend.setOnClickListener {
            if (streaming) client?.cancel() else send()
        }

        if (store.backendUrl().isBlank()) {
            tvSetupWarning.visibility = View.VISIBLE
        }
        refreshHeader()
        adapter.notifyDataSetChanged()
        updateEmpty()
    }

    private fun refreshHeader() {
        findViewById<TextView>(R.id.tvTitle).text =
            if (conv.title.isBlank()) getString(R.string.new_conversation) else conv.title
        tvModel.text = Models.labelOf(conv.model)
    }

    private fun updateEmpty() {
        tvEmpty.visibility = if (conv.messages.isEmpty()) View.VISIBLE else View.GONE
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
            val lm = findViewById<RecyclerView>(R.id.recyclerMessages).layoutManager as LinearLayoutManager
            val last = lm.findLastVisibleItemPosition()
            if (last >= pos - 1) scrollToBottom()
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
        adapter.notifyDataSetChanged()
        refreshHeader()
    }

    private fun scrollToBottom() {
        val r = findViewById<RecyclerView>(R.id.recyclerMessages)
        r.post { r.smoothScrollToPosition(adapter.itemCount - 1) }
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

    // ---------- seletor de modelo ----------

    private fun pickModel() {
        val labels = Models.ALL.map { it.label }.toTypedArray()
        val checked = Models.ALL.indexOfFirst { it.id == conv.model }
        AlertDialog.Builder(this)
            .setTitle(R.string.select_model)
            .setSingleChoiceItems(labels, if (checked >= 0) checked else 0) { d, which ->
                conv.model = Models.ALL[which].id
                store.saveConversation(conv)
                refreshHeader()
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- adapter ----------

    inner class MsgAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        inner class UserVH(v: View) : RecyclerView.ViewHolder(v) {
            val text: TextView = v.findViewById(R.id.tvText)
        }

        inner class AiVH(v: View) : RecyclerView.ViewHolder(v) {
            val thinkBox: LinearLayout = v.findViewById(R.id.thinkBox)
            val thinkHeader: TextView = v.findViewById(R.id.tvThinkHeader)
            val think: TextView = v.findViewById(R.id.tvThink)
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
            } else if (h is AiVH) {
                val hasThink = m.thinking.isNotBlank()
                h.thinkBox.visibility = if (hasThink) View.VISIBLE else View.GONE
                if (hasThink) {
                    val streamingThis = streaming && pos == conv.messages.size - 1 && m.content.isBlank()
                    h.thinkHeader.text =
                        if (streamingThis) getString(R.string.thinking) else getString(R.string.reasoning)
                    if (streamingThis && h.think.visibility != View.VISIBLE) {
                        h.think.visibility = View.VISIBLE
                    }
                    h.think.text = m.thinking
                    h.thinkHeader.setOnClickListener {
                        h.think.visibility =
                            if (h.think.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                }
                markwon.setMarkdown(h.md, m.content)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
