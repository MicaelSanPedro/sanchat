package com.sanchat.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sanchat.app.R
import com.sanchat.app.model.Conversation
import com.sanchat.app.store.Store
import java.text.DateFormat
import java.util.Date

class ConversationsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var adapter: ConvAdapter
    private lateinit var tvEmpty: TextView
    private var items: List<Conversation> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversations)
        store = Store(this)

        tvEmpty = findViewById(R.id.tvEmpty)
        val recycler = findViewById<RecyclerView>(R.id.recyclerConversations)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = ConvAdapter()
        recycler.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabNew).setOnClickListener {
            val conv = store.newConversation(com.sanchat.app.model.Models.DEFAULT)
            store.saveConversation(conv)
            openChat(conv.id)
        }
        findViewById<android.widget.ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        items = store.listConversations()
        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun openChat(id: String) {
        startActivity(Intent(this, ChatActivity::class.java).putExtra("id", id))
    }

    inner class ConvAdapter : RecyclerView.Adapter<ConvAdapter.VH>() {
        inner class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
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
            h.title.text = if (c.title.isBlank()) getString(R.string.new_conversation) else c.title
            h.snippet.text = c.lastUserSnippet()
            h.date.text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(c.updatedAt))
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
        val input = android.widget.EditText(this)
        input.setText(c.title)
        input.setSelection(input.text.length)
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
