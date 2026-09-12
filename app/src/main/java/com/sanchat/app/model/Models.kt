package com.sanchat.app.model

data class Message(
    var role: String,          // "user" | "assistant"
    var content: String,
    var thinking: String = ""  // raciocinio do modelo (DeepSeek-R1), se houver
)

data class Conversation(
    var id: String,
    var title: String,
    var createdAt: Long,
    var updatedAt: Long,
    var model: String = Models.DEFAULT,
    val messages: MutableList<Message> = mutableListOf()
) {
    fun lastUserSnippet(): String {
        for (i in messages.indices.reversed()) {
            val m = messages[i]
            if (m.role == "user") {
                val c = m.content.replace("\n", " ").trim()
                return if (c.length > 120) c.substring(0, 120) + "…" else c
            }
        }
        return ""
    }
}

object Models {
    const val DEFAULT = "meta/llama-3.3-70b-instruct"

    data class Entry(val id: String, val label: String)

    val ALL = listOf(
        Entry("meta/llama-3.3-70b-instruct", "Llama 3.3 70B — geral"),
        Entry("nvidia/llama-3.1-nemotron-70b-instruct", "Nemotron 70B — NVIDIA"),
        Entry("deepseek-ai/deepseek-r1", "DeepSeek R1 — raciocinio"),
        Entry("google/gemma-2-27b-it", "Gemma 2 27B — Google"),
        Entry("qwen/qwen2.5-coder-32b-instruct", "Qwen 2.5 Coder 32B — codigo"),
        Entry("mistralai/mistral-large-2-instruct", "Mistral Large 2")
    )

    fun labelOf(id: String): String = ALL.firstOrNull { it.id == id }?.label ?: id
}
