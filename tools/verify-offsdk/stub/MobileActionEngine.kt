package com.example.clawcode.engine

/**
 * STUB SOLO PARA VERIFICACIÓN DE COMPILACIÓN FUERA DE ANDROID. NO forma parte de la app.
 *
 * Reproduce las firmas exactas del MobileActionEngine real (MobileActionEngine.kt:19, :72, :119),
 * que es el colaborador de ClawEngine y ClawViewModel que depende de android.content.Context.
 * Se omite a propósito `: TextToSpeech.OnInitListener` porque exigiría otro stub y no aporta
 * nada a la verificación; el constructor sí recibe Context, igual que el real.
 */

import android.content.Context
import com.example.clawcode.data.model.InstalledApp

class MobileActionEngine(private val context: Context) {

    // Gancho del arnés: un prompt que empieza por "EXECUTA:" simula que el motor REAL detecta
    // una intención y que el intent del SO tiene éxito. Sin ese prefijo el comportamiento sigue
    // siendo el del stub original (sin intención), así que no altera las pruebas ya escritas.
    fun parseNaturalLanguageToFunctionCall(prompt: String): Pair<String, Map<String, String>> =
        if (prompt.startsWith("EXECUTA:")) {
            "abrir_app" to mapOf("app" to prompt.removePrefix("EXECUTA:").trim())
        } else {
            "" to emptyMap()
        }

    fun executeActionIntent(
        actionName: String,
        params: Map<String, String>
    ): Pair<Boolean, String> =
        if (actionName.isBlank()) {
            false to "stub"
        } else {
            true to "Intent resuelta: $actionName (${params["app"] ?: "-"})"
        }

    fun speak(
        text: String,
        voiceStyle: String = "Voz 1",
        pitchMultiplier: Float = 1.0f,
        speechRateMultiplier: Float = 1.0f
    ) = Unit

    fun getInstalledApps(): List<InstalledApp> = emptyList()

    fun launchAppByName(appNameQuery: String): Pair<Boolean, String> = false to "stub"
}
