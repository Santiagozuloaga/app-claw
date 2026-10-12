package com.example.clawcode

import com.example.clawcode.engine.ClawEngine
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Collections

/**
 * Contrato del chat con el runtime real, escrito como prueba unitaria del proyecto (JUnit 4, JVM
 * puro, sin Robolectric ni MockK). Para poder invocar `sendChatMessage()` desde aquí el motor de
 * acciones del SO pasó a ser opcional (`mobileActionEngine: MobileActionEngine? = null`): antes
 * era obligatorio y depende de un `Context`, así que la entrada real del chat no era ejecutable en
 * una prueba de JVM. Con null no se interpreta ninguna intención y el turno sigue yendo al runtime.
 *
 * Lo que fija: que un turno sale de verdad por HTTP contra el runtime configurado, con streaming
 * activado y el texto del usuario; que lo publicado es el texto generado y no una plantilla; que
 * hay una sola burbuja por remitente por turno; que un mensaje vacío no sale a la red; y que sin
 * runtime, o sin motor de acciones, nada se finge ejecutado.
 */
class ChatRealInferenceTest {

    private val requests = Collections.synchronizedList(mutableListOf<String>())
    private var server: HttpServer? = null
    private lateinit var engine: ClawEngine
    private var seedIds = emptySet<String>()

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/x-ndjson")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Before
    fun setUp() {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/chat") { exchange ->
                requests += exchange.requestBody.readBytes().decodeToString()
                respond(
                    exchange,
                    """{"model":"tiny:test","message":{"role":"assistant","content":"HOLA-"},"done":false}
{"model":"tiny:test","message":{"role":"assistant","content":"DEL-MODELO"},"done":false}
{"model":"tiny:test","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","prompt_eval_count":30,"prompt_eval_duration":100000000,"eval_count":5,"eval_duration":250000000}"""
                )
            }
            createContext("/api/tags") { exchange ->
                respond(exchange, """{"models":[{"name":"tiny:test","size":1200000000,"capabilities":["completion"]}]}""")
            }
            createContext("/api/ps") { exchange -> respond(exchange, """{"models":[]}""") }
            start()
        }
        server = http

        engine = ClawEngine()
        engine.updateOllamaConfig("http://127.0.0.1:${http.address.port}", "tiny:test", "", false)
        // El motor arranca con un saludo JARVIS de semilla: sin este snapshot, "la última burbuja"
        // sería el saludo y no lo generado por el modelo.
        seedIds = engine.chatMessages.value.map { it.id }.toSet()
    }

    @After
    fun tearDown() {
        server?.stop(0)
    }

    private fun newBubbles() = engine.chatMessages.value.filter { it.id !in seedIds }

    @Test
    fun elTurnoSalePorHttpAlRuntimeConfigurado() = runBlocking {
        engine.executeQuickActionSummarizeLogs()

        assertEquals("debe salir exactamente una petición de inferencia", 1, requests.size)
        val payload = requests.first()
        assertTrue("el pedido pide streaming real", payload.contains("\"stream\":true"))
        assertTrue("el pedido evita el pensamiento en texto visible", payload.contains("\"think\":false"))
        assertTrue("el modelo del pedido es el configurado, no uno inventado", payload.contains("tiny:test"))
    }

    @Test
    fun loPublicadoEsElTextoGeneradoYNoUnaPlantilla() = runBlocking {
        engine.executeQuickActionSummarizeLogs()

        val bubbles = newBubbles()
        assertEquals("un turno publica una sola burbuja", 1, bubbles.size)
        val text = bubbles.single().text
        assertTrue("la burbuja lleva lo devuelto por el runtime", text.contains("HOLA-DEL-MODELO"))
        assertFalse("no se publica el fallback de 'no se generó resumen' cuando sí respondió",
            text.contains("No se generó resumen"))
        assertFalse("no se etiqueta nada como simulación", text.contains("[SIMULATION]"))
    }

    @Test
    fun sinRuntimeElHuecoSeDeclaraConDatosReales() = runBlocking {
        val dead = ClawEngine()
        dead.updateOllamaConfig("http://127.0.0.1:1", "tiny:test", "", false)
        val before = dead.chatMessages.value.map { it.id }.toSet()

        dead.executeQuickActionSummarizeLogs()

        val text = dead.chatMessages.value.filter { it.id !in before }.last().text
        assertTrue("el turno debe reconocerse como resumen no generado",
            text.contains("[Resumen de Registros]") && text.contains("No se generó resumen"))
        assertTrue("debe quedar explícito que el runtime no respondió",
            text.contains("runtime local no respondió") || text.contains("No se generó resumen"))
        assertFalse("sin runtime no puede aparecer texto de un modelo", text.contains("HOLA-DEL-MODELO"))
    }

    @Test
    fun laEntradaRealDelChatInvocaAlModeloConElTextoDelUsuario() = runBlocking {
        engine.sendChatMessage("dime hola", null, null)

        assertEquals("debe salir exactamente una petición de inferencia", 1, requests.size)
        assertTrue("el texto del usuario viaja en el payload", requests.first().contains("dime hola"))

        val added = newBubbles()
        assertEquals("el turno publica una sola burbuja de usuario", 1, added.count { it.sender == "USER" })
        assertEquals("el turno publica una sola burbuja de respuesta", 1, added.count { it.sender == "JARVIS" })
        assertTrue("la burbuja es lo que devolvió el runtime",
            added.single { it.sender == "JARVIS" }.text.contains("HOLA-DEL-MODELO"))
    }

    @Test
    fun unMensajeVacioNoSaleALaRedNiSePublica() = runBlocking {
        engine.sendChatMessage("   ", null, null)

        assertEquals("un dictado vacío no debe llegar al runtime", 0, requests.size)
        assertTrue("tampoco publica turno", newBubbles().isEmpty())
        assertTrue(
            "el rechazo queda registrado",
            engine.consoleLogs.value.any { it.message.contains("[INPUT_REJECTED]") }
        )
    }

    @Test
    fun sinMotorDeAccionesNoSeFingeUnaEjecuciónEnElDispositivo() = runBlocking {
        engine.sendChatMessage("abre el calendario", null, null)

        assertEquals("el turno se sigue atendiendo en el runtime", 1, requests.size)
        val text = newBubbles().single { it.sender == "JARVIS" }.text
        assertFalse("no puede declararse una acción del SO que no se ejecutó",
            text.contains("Acción ejecutada en el dispositivo"))
        assertTrue("y no se inventa otra cosa: es texto del modelo", text.contains("HOLA-DEL-MODELO"))
    }
}
