package com.sanchat.app.store

import android.content.Context
import com.sanchat.app.model.Conversation
import com.sanchat.app.model.Message
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class Store(context: Context) {

    private val prefs = context.getSharedPreferences("sanchat", Context.MODE_PRIVATE)

    // ---------- Conversas ----------

    fun listConversations(): MutableList<Conversation> {
        val raw = prefs.getString("convs", null) ?: return mutableListOf()
        return try {
            parseConvs(JSONArray(raw))
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveConversation(c: Conversation) {
        val list = listConversations()
        val idx = list.indexOfFirst { it.id == c.id }
        if (idx >= 0) list[idx] = c else list.add(0, c)
        writeConvs(list)
    }

    fun getConversation(id: String): Conversation? =
        listConversations().firstOrNull { it.id == id }

    fun deleteConversation(id: String) {
        writeConvs(listConversations().filter { it.id != id }.toMutableList())
    }

    fun deleteAllConversations() = writeConvs(mutableListOf())

    fun newConversation(model: String): Conversation {
        val now = System.currentTimeMillis()
        return Conversation(
            id = UUID.randomUUID().toString(),
            title = "",
            createdAt = now,
            updatedAt = now,
            model = model
        )
    }

    private fun writeConvs(list: List<Conversation>) {
        val arr = JSONArray()
        for (c in list) {
            val o = JSONObject()
            o.put("id", c.id)
            o.put("title", c.title)
            o.put("createdAt", c.createdAt)
            o.put("updatedAt", c.updatedAt)
            o.put("model", c.model)
            val msgs = JSONArray()
            for (m in c.messages) {
                val mo = JSONObject()
                mo.put("role", m.role)
                mo.put("content", m.content)
                if (m.thinking.isNotEmpty()) mo.put("thinking", m.thinking)
                msgs.put(mo)
            }
            o.put("messages", msgs)
            arr.put(o)
        }
        prefs.edit().putString("convs", arr.toString()).apply()
    }

    private fun parseConvs(arr: JSONArray): MutableList<Conversation> {
        val out = mutableListOf<Conversation>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val msgs = mutableListOf<Message>()
            val ma = o.optJSONArray("messages") ?: JSONArray()
            for (j in 0 until ma.length()) {
                val mo = ma.optJSONObject(j) ?: continue
                msgs.add(
                    Message(
                        role = mo.optString("role", "user"),
                        content = mo.optString("content", ""),
                        thinking = mo.optString("thinking", "")
                    )
                )
            }
            out.add(
                Conversation(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    title = o.optString("title", ""),
                    createdAt = o.optLong("createdAt", 0L),
                    updatedAt = o.optLong("updatedAt", 0L),
                    model = o.optString("model", "meta/llama-3.3-70b-instruct"),
                    messages = msgs
                )
            )
        }
        out.sortByDescending { it.updatedAt }
        return out
    }

    // ---------- Configuracoes ----------

    fun backendUrl(): String =
        prefs.getString("backend", null) ?: BuildConfigProxy.defaultUrl

    fun setBackendUrl(v: String) = prefs.edit().putString("backend", v.trim().trimEnd('/')).apply()

    fun accessToken(): String =
        prefs.getString("token", null) ?: BuildConfigProxy.defaultToken

    fun setAccessToken(v: String) = prefs.edit().putString("token", v.trim()).apply()

    fun languageChosen(): Boolean = prefs.getBoolean("lang_chosen", false)

    fun setLanguageChosen() = prefs.edit().putBoolean("lang_chosen", true).apply()

    /** Ponte lazy para BuildConfig (evita import ciclico desnecessario no App). */
    object BuildConfigProxy {
        val defaultUrl: String get() = com.sanchat.app.BuildConfig.BACKEND_URL
        val defaultToken: String get() = com.sanchat.app.BuildConfig.APP_TOKEN
    }
}
