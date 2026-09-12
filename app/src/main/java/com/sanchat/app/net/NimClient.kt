package com.sanchat.app.net

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Cliente do proxy (Vercel) com streaming SSE no padrao OpenAI. */
class NimClient(private val baseUrl: String, private val token: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // stream aberto: sem timeout de leitura
        .build()

    private var call: Call? = null

    fun cancel() {
        call?.cancel()
    }

    /**
     * onDelta(contentDelta, thinkDelta) — um dos dois pode ser null por chamada.
     * Bloqueia na thread do OkHttp; agende os callbacks na UI a partir deles.
     */
    fun streamChat(
        model: String,
        messages: List<Pair<String, String>>,
        maxTokens: Int = 1024,
        onDelta: (String?, String?) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val arr = JSONArray()
        for ((role, content) in messages) {
            arr.put(JSONObject().put("role", role).put("content", content))
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", arr)
            .put("stream", true)
            .put("max_tokens", maxTokens)
            .put("temperature", 0.7)
            .put("top_p", 0.95)

        val url = baseUrl.trimEnd('/') + "/api/chat"
        val rb = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (token.isNotEmpty()) rb.header("x-sanchat-token", token)

        val c = client.newCall(rb.build())
        call = c
        c.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) onDone() else onError(e.message ?: "network error")
            }

            override fun onResponse(call: Call, resp: Response) {
                resp.use { r ->
                    if (!r.isSuccessful) {
                        val txt = try {
                            r.body?.string() ?: ""
                        } catch (e: Exception) {
                            ""
                        }
                        onError("HTTP ${r.code} ${txt.take(300)}")
                        return
                    }
                    val src = r.body?.source()
                    if (src == null) {
                        onError("empty body")
                        return
                    }
                    try {
                        while (true) {
                            val line = src.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.substring(5).trim()
                            if (data == "[DONE]") break
                            try {
                                val json = JSONObject(data)
                                val choice = json.getJSONArray("choices").getJSONObject(0)
                                val delta = choice.optJSONObject("delta") ?: continue
                                val rc = delta.optString("reasoning_content", "")
                                val ct = delta.optString("content", "")
                                if (rc.isNotEmpty()) onDelta(null, rc)
                                if (ct.isNotEmpty()) onDelta(ct, null)
                            } catch (e: Exception) {
                                // chunk parcial/keepalive: ignora
                            }
                        }
                        onDone()
                    } catch (e: Exception) {
                        if (call.isCanceled()) onDone() else onError(e.message ?: "stream error")
                    }
                }
            }
        })
    }
}
