package com.google.ai.client.generativeai

import java.util.concurrent.atomic.AtomicInteger

/**
 * STUB SOLO PARA VERIFICACIÓN FUERA DE ANDROID. NO forma parte de la app.
 *
 * Sustituye el cliente de la Gemini API (`com.google.ai.client.generativeai:generativeai:0.9.0`) para
 * poder compilar y ejecutar `AppConnectorManager` sin SDK ni red. Su razón de ser es medir: cada
 * construcción del modelo y cada `generateContent` se cuenta, así que la prueba puede afirmar que con
 * la credencial de fábrica NO se intenta ninguna llamada remota, en lugar de confiar en leer el
 * orden del código.
 */
class GenerativeModel(val modelName: String, val apiKey: String) {

    init {
        FakeGenerative.construidos.incrementAndGet()
        FakeGenerative.ultimoModelo = modelName
        FakeGenerative.ultimaClave = apiKey
    }

    suspend fun generateContent(prompt: String): GenerateContentResponse {
        FakeGenerative.llamadas.incrementAndGet()
        return GenerateContentResponse()
    }
}

class GenerateContentResponse {
    val text: String? get() = "texto remoto simulado por el arnés"
}

object FakeGenerative {
    val construidos = AtomicInteger(0)
    val llamadas = AtomicInteger(0)
    var ultimoModelo: String = ""
    var ultimaClave: String = ""

    fun reset() {
        construidos.set(0)
        llamadas.set(0)
        ultimoModelo = ""
        ultimaClave = ""
    }
}
