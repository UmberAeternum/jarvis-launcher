package com.umberaeternum.jarvis.launcher

import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Client for the desktop Bridge (`jarvis serve`) - the full 19-tool desktop
 * agent over the LAN, token-gated. Blocking calls; invoke from
 * Dispatchers.IO. Errors are returned as readable strings, never thrown.
 */
object DesktopClient {
    data class DesktopConfig(val url: String, val token: String)

    private val json = "application/json".toMediaType()
    private val http = OkHttpClient.Builder()
        .callTimeout(40, TimeUnit.SECONDS)   // long-poll /api/events waits ~25 s
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    data class Event(val id: Int, val type: String, val fields: JSONObject)

    private fun request(
        config: DesktopConfig,
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
    ): Result<JSONObject> {
        val builder = Request.Builder()
            .url(config.url.trimEnd('/') + path)
            .header("X-Jarvis-Token", config.token)
        if (body != null) {
            builder.post(body.toString().toRequestBody(json))
        }
        return try {
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Result.failure(IllegalStateException("HTTP ${response.code}: ${text.take(200)}"))
                } else {
                    Result.success(JSONObject(text))
                }
            }
        } catch (exc: Exception) {
            Result.failure(exc)
        }
    }

    /** Start a turn on the desktop. Returns null on transport failure. */
    fun command(config: DesktopConfig, text: String): JSONObject? =
        request(
            config, "/api/command", "POST",
            JSONObject().put("text", text),
        ).getOrNull()

    /** Long-poll events newer than `since`. Returns null on transport failure. */
    fun eventsSince(config: DesktopConfig, since: Int): JSONArray? =
        request(config, "/api/events?since=$since").getOrNull()
            ?.optJSONArray("events")

    /** Answer a pending confirmation. Returns true when accepted. */
    fun confirm(config: DesktopConfig, confirmId: String, approved: Boolean): Boolean =
        request(
            config, "/api/confirm", "POST",
            JSONObject().put("confirm_id", confirmId).put("approved", approved),
        ).isSuccess
}
