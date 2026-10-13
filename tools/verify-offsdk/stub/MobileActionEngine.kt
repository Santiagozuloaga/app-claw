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

    // Mismo contrato que la clase real: speak() informa si encoló, y se puede consultar qué voz
    // está activa. El arnés deja constancia de las llamadas para poder afirmar que un turno habló.
    val llamadasVoz = java.util.Collections.synchronizedList(mutableListOf<String>())

    fun speak(
        text: String,
        voiceStyle: String = "Voz 1",
        pitchMultiplier: Float = 1.0f,
        speechRateMultiplier: Float = 1.0f
    ): Boolean {
        llamadasVoz += "$voiceStyle|$text"
        return true
    }

    fun estadoVoz(): String = "arnés · es-CO · voz: simulada"

    fun vocesEspanolDisponibles(): List<String> = listOf("voz-de-arnes (calidad 50)")

    fun getInstalledApps(): List<InstalledApp> = emptyList()

    // La consola (/intent open, /intent flash, /intent) llama ahora a estas tres. El stub
    // devuelve un catalogo pequeno y fijo para que el contador que imprime la consola sea
    // comprobable: si el engine mostrara un numero escrito a mano, este assert lo cazaría.
    fun getActionCatalog(): List<com.example.clawcode.data.model.MobileActionDef> =
        listOf(
            com.example.clawcode.data.model.MobileActionDef(
                "a1", "abrir_app", com.example.clawcode.data.model.MobileActionCategory.APP_LAUNCHER,
                "stub", "stub", "{}", "android.intent.action.MAIN"
            ),
            com.example.clawcode.data.model.MobileActionDef(
                "a2", "linterna", com.example.clawcode.data.model.MobileActionCategory.SYSTEM,
                "stub", "stub", "{}", "android.hardware.camera2"
            ),
            com.example.clawcode.data.model.MobileActionDef(
                "a3", "mapa", com.example.clawcode.data.model.MobileActionCategory.NAVIGATION,
                "stub", "stub", "{}", "android.intent.action.VIEW"
            )
        )

    // Ahora la consola lo llama de verdad, asi que el stub tiene que poder producir los dos
    // resultados: con objetivo se resuelve, sin objetivo no.
    fun launchAppByName(appNameQuery: String): Pair<Boolean, String> =
        if (appNameQuery.isBlank()) false to "sin objetivo" else true to "stub lanzada: $appNameQuery"
}
