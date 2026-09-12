package com.sanchat.app.model

data class Message(
    var role: String,          // "user" | "assistant"
    var content: String,
    var thinking: String = ""  // raciocinio do modelo (reasoning_content), se houver
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
    const val DEFAULT = "nvidia/nemotron-3-ultra-550b-a55b"

    data class Entry(val id: String, val label: String)

    // Catalogo 2026 da NIM — testados com chamada real em 13/09/2026.
    // (A NVIDIA aposenta modelos: os originais v1.0.0 viraram 410/404.)
    val ALL = listOf(
        Entry("nvidia/nemotron-3-ultra-550b-a55b", "Nemotron 3 Ultra 550B — NVIDIA"),
        Entry("deepseek-ai/deepseek-v4-flash-0731", "DeepSeek V4 Flash — raciocinio"),
        Entry("nvidia/nemotron-3.5-lightning-30b-a3b", "Nemotron 3.5 Lightning — rapido"),
        Entry("z-ai/glm-5.3-flash", "GLM 5.3 Flash — Z.ai"),
        Entry("openai/gpt-oss-20b", "GPT-OSS 20B — OpenAI"),
        Entry("google/gemma-4-31b-it", "Gemma 4 31B — Google")
    )

    fun labelOf(id: String): String = ALL.firstOrNull { it.id == id }?.label ?: id
}
