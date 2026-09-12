package com.sanchat.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.sanchat.app.R
import com.sanchat.app.model.Conversation
import com.sanchat.app.store.Store
import java.util.Locale

class ConversationsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var adapter: ConvAdapter
    private lateinit var tvEmpty: View
    private lateinit var recycler: RecyclerView
    private lateinit var fab: FloatingActionButton
    private var items: List<Conversation> = listOf()
    private var firstLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_conversations)
        store = Store(this)

        tvEmpty = findViewById(R.id.tvEmpty)
        recycler = findViewById(R.id.recyclerConversations)
        fab = findViewById(R.id.fabNew)
        val topBar = findViewById<View>(R.id.topBar)
        val root = findViewById<View>(R.id.root)

        recycler.layoutManager = LinearLayoutManager(this)
        adapter = ConvAdapter()
        recycler.adapter = adapter

        applyInsets(root, topBar)

        fab.setOnClickListener {
            val conv = store.newConversation(com.sanchat.app.model.Models.DEFAULT)
            store.saveConversation(conv)
            openChat(conv.id)
        }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // Arrastar pra cima dos lados = apagar (com desfazer)
        val ith = ItemTouchHelper(object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.adapterPosition
                if (pos < 0 || pos >= items.size) {
                    adapter.notifyItemChanged(pos)
                    return
                }
                val c = items[pos]
                store.deleteConversation(c.id)
                reload()
                Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.conversation_deleted,
                    Snackbar.LENGTH_LONG
                )
                    .setAction(R.string.undo) {
                        store.saveConversation(c)
                        reload()
                    }
                    .show()
            }
        })
        ith.attachToRecyclerView(recycler)
    }

    private fun applyInsets(root: View, topBar: View) {
        val baseTop = topBar.paddingTop
        val basePadBottom = recycler.paddingBottom
        val baseFab = (fab.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = maxOf(bars.bottom, ime.bottom)
            topBar.setPadding(
                topBar.paddingLeft, baseTop + bars.top, topBar.paddingRight, topBar.paddingBottom
            )
            recycler.setPadding(recycler.paddingLeft, recycler.paddingTop, recycler.paddingRight,
                basePadBottom + bottom)
            val lp = fab.layoutParams as ViewGroup.MarginLayoutParams
            lp.bottomMargin = baseFab + bottom
            fab.requestLayout()
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        items = store.listConversations()
        // animacao de entrada so na primeira vez; depois nao repisca
        if (!firstLoad) recycler.layoutAnimation = null
        adapter.notifyDataSetChanged()
        firstLoad = false
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openChat(id: String) {
        startActivity(Intent(this, ChatActivity::class.java).putExtra("id", id))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun relativeTime(ms: Long): String = DateUtils.getRelativeTimeSpanString(
        ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()

    inner class ConvAdapter : RecyclerView.Adapter<ConvAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val avatar: TextView = v.findViewById(R.id.tvAvatar)
            val title: TextView = v.findViewById(R.id.tvTitle)
            val snippet: TextView = v.findViewById(R.id.tvSnippet)
            val date: TextView = v.findViewById(R.id.tvDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_conversation, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val c = items[pos]
            val shownTitle =
                if (c.title.isBlank()) getString(R.string.new_conversation) else c.title
            h.title.text = shownTitle
            h.snippet.text = c.lastUserSnippet()
            h.date.text = relativeTime(c.updatedAt)
            h.avatar.text = shownTitle.trim().firstOrNull()?.uppercase(Locale.ROOT) ?: "?"
            h.itemView.setOnClickListener { openChat(c.id) }
            h.itemView.setOnLongClickListener {
                val options = arrayOf(getString(R.string.rename), getString(R.string.delete))
                AlertDialog.Builder(this@ConversationsActivity)
                    .setTitle(h.title.text)
                    .setItems(options) { _, which ->
                        if (which == 0) renameDialog(c) else deleteDialog(c)
                    }
                    .show()
                true
            }
        }
    }

    private fun renameDialog(c: Conversation) {
        val input = EditText(this)
        input.setText(c.title)
        input.setSelection(input.text.length)
        input.setBackgroundResource(R.drawable.bg_input)
        input.setPadding(dp(18), dp(14), dp(18), dp(14))
        input.setTextColor(0xFFF0F2E8.toInt())
        input.hint = getString(R.string.new_conversation)
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                c.title = input.text.toString().trim()
                store.saveConversation(c)
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteDialog(c: Conversation) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_conversation_title)
            .setMessage(c.title)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.deleteConversation(c.id)
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
