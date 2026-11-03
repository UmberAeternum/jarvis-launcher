package com.umberaeternum.jarvis.launcher

import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal OpenAI-compatible chat client - the same surface the desktop
 * JARVIS speaks (Z.AI, OpenAI, Zhipu, Gemini-compat, Ollama). Blocking calls;
   invoke from Dispatchers.IO. Errors are returned as readable strings, never
   thrown, so the UI can report failures honestly.
 */
object BrainClient {
    private val json = "application/json".toMediaType()
    private val http = OkHttpClient.Builder()
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    /** history: (role, content) pairs; returns the reply or "ERROR: ..." */
    fun chat(config: SettingsStore.Settings, history: List<Pair<String, String>>): String {
        if (config.baseUrl.isBlank() || config.model.isBlank()) {
            return "ERROR: brain not configured - open Settings."
        }
        val payload = JSONObject().apply {
            put("model", config.model)
            put("messages", JSONArray().apply {
                for ((role, content) in history) {
                    put(JSONObject().put("role", role).put("content", content))
                }
            })
        }
        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer " + config.key)
            .post(payload.toString().toRequestBody(json))
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching {
                        JSONObject(body).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    "ERROR ${response.code}: ${detail ?: body.take(280)}"
                } else {
                    runCatching {
                        JSONObject(body)
                            .getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                    }.getOrElse { "ERROR: unexpected response shape" }
                }
            }
        } catch (exc: Exception) {
            "ERROR: ${exc.javaClass.simpleName}: ${exc.message}"
        }
    }
}
