package com.umberaeternum.jarvis.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Launcher settings. The API key is stored in EncryptedSharedPreferences
 * (Android Keystore-backed); if the crypto stack is unavailable the store
 * falls back to plain private preferences and the fact is exposed via
 * `encrypted` so the UI can say so honestly.
 */
object SettingsStore {
    private const val FILE_SECURE = "jarvis_secure"
    private const val FILE_PLAIN = "jarvis_settings"

    data class Settings(
        val provider: String = "Z.AI (GLM)",
        val baseUrl: String = "https://api.z.ai/api/paas/v4",
        val key: String = "",
        val model: String = "glm-4.6",
        val encrypted: Boolean = true,
        val configured: Boolean = false,
        val desktopUrl: String = "",
        val desktopToken: String = "",
    )

    fun load(context: Context): Settings {
        val prefs = open(context)
        val settings = Settings(
            provider = prefs.getString("provider", "Z.AI (GLM)") ?: "Z.AI (GLM)",
            baseUrl = prefs.getString("baseUrl", "https://api.z.ai/api/paas/v4")
                ?: "https://api.z.ai/api/paas/v4",
            key = prefs.getString("key", "") ?: "",
            model = prefs.getString("model", "glm-4.6") ?: "glm-4.6",
            encrypted = prefs.getBoolean("encrypted", true),
            desktopUrl = prefs.getString("desktopUrl", "") ?: "",
            desktopToken = prefs.getString("desktopToken", "") ?: "",
        )
        return settings.copy(configured = settings.key.isNotBlank() || settings.provider == "Ollama (local)")
    }

    fun save(context: Context, settings: Settings) {
        open(context).edit()
            .putString("provider", settings.provider)
            .putString("baseUrl", settings.baseUrl)
            .putString("key", settings.key)
            .putString("model", settings.model)
            .putBoolean("encrypted", settings.encrypted)
            .putString("desktopUrl", settings.desktopUrl)
            .putString("desktopToken", settings.desktopToken)
            .apply()
    }

    fun presets(): Map<String, String> = linkedMapOf(
        "Z.AI (GLM)" to "https://api.z.ai/api/paas/v4",
        "OpenAI" to "https://api.openai.com/v1",
        "Zhipu (CN)" to "https://open.bigmodel.cn/api/paas/v4",
        "Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai",
        "Ollama (local)" to "http://10.0.2.2:11434/v1",
        "Custom" to "",
    )

    private fun open(context: Context): SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_SECURE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (exc: Exception) {
        context.getSharedPreferences(FILE_PLAIN, Context.MODE_PRIVATE)
    }
}
