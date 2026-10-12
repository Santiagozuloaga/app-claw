package com.example.clawcode.core.storage

import android.content.Context
import com.example.clawcode.data.model.OllamaConfig

/**
 * Sin persistencia la app arranca siempre con los valores de fábrica de [OllamaConfig]
 * (host del emulador + modelo inexistente), de modo que en un teléfono real el chat
 * cae sistemáticamente en la respuesta estática de reserva.
 */
interface OllamaConfigStore {
    fun load(): OllamaConfig?
    fun save(config: OllamaConfig)
}

class InMemoryOllamaConfigStore : OllamaConfigStore {
    private var current: OllamaConfig? = null

    override fun load(): OllamaConfig? = current

    override fun save(config: OllamaConfig) {
        current = config
    }
}

class PrefsOllamaConfigStore(context: Context) : OllamaConfigStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): OllamaConfig? {
        if (!prefs.contains(KEY_HOST_URL)) return null
        val defaults = OllamaConfig()
        return OllamaConfig(
            hostUrl = prefs.getString(KEY_HOST_URL, defaults.hostUrl) ?: defaults.hostUrl,
            modelName = prefs.getString(KEY_MODEL_NAME, defaults.modelName) ?: defaults.modelName,
            openClawWebhookUrl = prefs.getString(KEY_WEBHOOK_URL, defaults.openClawWebhookUrl)
                ?: defaults.openClawWebhookUrl,
            useOnDeviceLiteRTFallback = prefs.getBoolean(
                KEY_USE_ON_DEVICE_FALLBACK,
                defaults.useOnDeviceLiteRTFallback
            ),
            // La conectividad se mide contra el runtime en cada arranque; restaurarla
            // como verdadera sería volver a mentir en la UI.
            isConnected = false
        )
    }

    override fun save(config: OllamaConfig) {
        prefs.edit()
            .putString(KEY_HOST_URL, config.hostUrl)
            .putString(KEY_MODEL_NAME, config.modelName)
            .putString(KEY_WEBHOOK_URL, config.openClawWebhookUrl)
            .putBoolean(KEY_USE_ON_DEVICE_FALLBACK, config.useOnDeviceLiteRTFallback)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "claw_ollama_config"
        const val KEY_HOST_URL = "host_url"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_WEBHOOK_URL = "openclaw_webhook_url"
        const val KEY_USE_ON_DEVICE_FALLBACK = "use_on_device_litert_fallback"
    }
}
