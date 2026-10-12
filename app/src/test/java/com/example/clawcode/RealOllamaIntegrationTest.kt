package com.example.clawcode

import com.example.clawcode.engine.ClawEngine
import com.example.clawcode.engine.OllamaClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Prueba de integración contra el runtime local de verdad, no contra un servidor falso. Cubre el
 * salto que las otras dos baterías no pueden dar: que un modelo real conteste y de que el motor
 * reporte métricas medidas. Se salta (no falla) donde no haya un Ollama escuchando en loopback,
 * porque la disponibilidad del runtime es del equipo, no del repo.
 *
 * No se afirma NADA sobre la redacción del modelo: un 0.5 B responde distinto en cada
 * ejecución (ya se observó devolviendo "Kubernetes.io/resource.yaml" donde se le pidió una palabra).
 * Se afirma sobre hechos medibles: que hubo respuesta, que no es el saludo de semilla, que no es un
 * fallback de error, y que las métricas de inferencia vinieron del runtime.
 */
class RealOllamaIntegrationTest {

    private val host = "http://127.0.0.1:11434"

    @Test
    fun elChatConversaConElModeloLocalReal() = runBlocking {
        val modelo = primeroLocalCapaz()
        assumeTrue("no hay runtime Ollama en loopback con un modelo 'completion': prueba saltada", modelo != null)
        val nombre = modelo!!

        val engine = ClawEngine()
        engine.updateOllamaConfig(host, nombre, "", false)
        val antes = engine.chatMessages.value.map { it.id }.toSet()

        engine.sendChatMessage("Repite únicamente esta palabra, sin nada más: MANGO", null, null)

        val burbujas = engine.chatMessages.value.filter { it.id !in antes && it.sender == "JARVIS" }
        assertEquals("el turno real debe publicar exactamente una respuesta", 1, burbujas.size)
        val texto = burbujas.single().text
        assertTrue("el runtime debe devolver algo", texto.isNotBlank())
        assertFalse("no puede ser el saludo de semilla", texto.startsWith("Hola, soy JARVIS"))
        assertFalse("no puede ser un fallo disfrazado de respuesta",
            texto.contains("[Runtime local sin respuesta]") || texto.contains("[MODEL_ERROR]"))
        assertFalse("ninguna ruta real del chat marca simulación", texto.contains("[SIMULATION]"))

        val telemetria = engine.hardwareTelemetry.value
        assertNotNull(telemetria)
        assertTrue(
            "los tokens por segundo tienen que venir del chunk done=true del runtime, no de una constante",
            telemetria.tokensPerSecond > 0.0
        )
        val console = engine.consoleLogs.value.joinToString(" ") { it.message }
        assertTrue("la consola debe registrar que respondió el modelo configurado",
            console.contains(nombre))
    }

    private suspend fun primeroLocalCapaz(): String? =
        OllamaClient(connectTimeoutSeconds = 2, readTimeoutSeconds = 6)
            .listCompletionModels(host)
            .getOrNull()
            ?.firstOrNull { !it.isCloudStub }
            ?.name
            ?.takeIf { it.isNotBlank() }
}
