package com.example.clawcode.engine.voice

import java.util.Locale

/**
 * Filtro de palabra clave sobre la transcripción que produce el motor de dictado del sistema.
 *
 * Hay que llamarlo por su nombre: esto NO es un detector acústico offline de wake word. No hay
 * modelo de keyword spotting en la app. Lo que existe es una escucha continua de
 * [android.speech.SpeechRecognizer] y este filtro, que decide si la frase transcrita iba dirigida
 * al agente. La diferencia importa: mientras la escucha está activa el micrófono está abierto y
 * todo se transcribe en el dispositivo; sólo se actúa si la frase empieza por la palabra clave.
 *
 * Vive separado del oyente para poder ejecutarse y probarse fuera de Android.
 */
object GatePalabraClave {

    /** Caracteres que valen como separador entre la palabra clave y el comando. */
    private const val SEPARADORES = ",.:;!?¡¿-"

    /**
     * Devuelve el comando a ejecutar, o null si la transcripción no invoca al agente.
     *
     * "Jarvis, abre gmail" -> "abre gmail". "jarvismodelo" -> null: sin separador no cuenta.
     * "Jarvis" a secas -> null: invocar sin comando no debe lanzar nada.
     */
    fun comandoDe(transcripcion: String, wakeWord: String): String? {
        val frase = transcripcion.trim()
        val clave = wakeWord.trim()
        if (frase.isEmpty() || clave.isEmpty()) return null

        val fraseMinus = frase.lowercase(Locale.ROOT)
        val claveMinus = clave.lowercase(Locale.ROOT)
        if (!fraseMinus.startsWith(claveMinus)) return null

        val resto = frase.substring(clave.length)
        val primera = resto.firstOrNull()
        if (primera != null && !primera.isWhitespace() && primera !in SEPARADORES) return null

        return resto.trim { it.isWhitespace() || it in SEPARADORES }.ifEmpty { null }
    }

    /** Si la frase menciona la palabra clave en cualquier posición: sirve para el modo sin gate. */
    fun menciona(transcripcion: String, wakeWord: String): Boolean {
        val clave = wakeWord.trim().lowercase(Locale.ROOT)
        if (clave.isEmpty()) return false
        return transcripcion.lowercase(Locale.ROOT).contains(clave)
    }
}
