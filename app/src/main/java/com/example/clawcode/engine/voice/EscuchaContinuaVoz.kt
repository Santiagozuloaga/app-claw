package com.example.clawcode.engine.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Escucha continua real sobre [SpeechRecognizer] del sistema.
 *
 * El proyecto prometió en el transcript de AI Studio un "JARVIS Live Voice" en segundo plano que
 * nunca abrió el micrófono: el servicio sólo publicaba una notificación. Esta clase es lo que
 * faltaba para que esa promesa sea cierta. Funciona enhebrando sesiones: el motor de dictado de
 * Android corta solo tras cada frase, así que al terminar se relanza. Mientras está activo el
 * micro está abierto y la transcripción se procesa en el dispositivo; [GatePalabraClave] decide
 * si la frase iba dirigida al agente.
 *
 * Debe crearse y usarse desde el hilo principal, como exige SpeechRecognizer.
 */
class EscuchaContinuaVoz(
    private val context: Context,
    @Volatile private var palabraClave: String,
    private val onEstado: (String) -> Unit,
    private val onComando: (String) -> Unit
) : RecognitionListener {

    private var motor: SpeechRecognizer? = null
    private val hilo = Handler(Looper.getMainLooper())
    private var erroresSeguidos = 0

    @Volatile
    var activa = false
        private set

    /** Último texto transcrito, para poder mostrarlo sin fingir que es un comando ejecutado. */
    @Volatile
    var ultimaTranscripcion: String = ""
        private set

    fun disponible(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Devuelve false y dice por qué si el dispositivo no puede escuchar. */
    fun iniciar(): Boolean {
        if (activa) return true
        if (!disponible()) {
            onEstado("sin motor de dictado en el dispositivo")
            return false
        }
        activa = true
        erroresSeguidos = 0
        motor = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(this) }
        arrancar()
        onEstado("escuchando")
        return true
    }

    fun detener(motivo: String = "detenida por el usuario") {
        activa = false
        hilo.removeCallbacksAndMessages(null)
        runCatching { motor?.cancel() }
        runCatching { motor?.destroy() }
        motor = null
        onEstado(motivo)
    }

    private fun arrancar() {
        val m = motor ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { m.startListening(intent) }
            .onFailure {
                onEstado("el sistema rechazó la escucha: ${it.message}")
                detener("escucha detenida: el sistema la rechazó")
            }
    }

    /** Relanza tras cada frase, con un margen corto para no martillear el motor. */
    private fun relanzar() {
        if (!activa) return
        hilo.postDelayed({ if (activa) arrancar() }, RELANZAR_MS)
    }

    private fun entregar(texto: String) {
        ultimaTranscripcion = texto
        val comando = GatePalabraClave.comandoDe(texto, palabraClave)
        if (comando != null) {
            erroresSeguidos = 0
            onComando(comando)
        }
    }

    fun setPalabraClave(nueva: String) {
        palabraClave = nueva
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit

    // onEvent es abstracta en RecognitionListener aunque esta obsoleta: hay que implementarla.
    override fun onEvent(event: Int, extras: Bundle?) = Unit

    override fun onPartialResults(partialResults: Bundle?) {
        ultimaTranscripcion = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?: ultimaTranscripcion
    }

    override fun onResults(results: Bundle?) {
        val texto = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (texto.isNotBlank()) entregar(texto)
        relanzar()
    }

    override fun onError(error: Int) {
        // ERROR_NO_MATCH y ERROR_SPEECH_TIMEOUT son normales en una escucha en reposo: no son
        // fallos, son "no se dijo nada". Relanzar sin subir el contador.
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            relanzar()
            return
        }
        erroresSeguidos++
        onEstado("error del motor de dictado (código $error)")
        if (erroresSeguidos >= MAX_ERRORES) {
            // Seguir reintentando en bucle contra un micro denegado consume batería y no arregla
            // nada: se rinde y se dice por qué.
            detener("escucha detenida tras $erroresSeguidos errores seguidos (código $error)")
        } else {
            relanzar()
        }
    }

    private companion object {
        const val RELANZAR_MS = 400L
        const val MAX_ERRORES = 5
    }
}
