package verify

import android.content.Context
import com.example.clawcode.core.storage.InMemoryOllamaConfigStore
import com.example.clawcode.engine.ClawEngine
import com.example.clawcode.engine.MobileActionEngine
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import kotlinx.coroutines.runBlocking

/**
 * "Google Search Grounding" no buscaba nada: devolvía títulos, URLs y resúmenes escritos por
 * nosotros, marcados como activos, y ese texto se inyectaba como contexto en las respuestas del
 * chat. Aquí se comprueba que el motor declara la ausencia de backend, que no queda ninguna fuente
 * inventada y que ningún turno del chat lleva contexto fabricado.
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private const val WEBHOOK = "https://api.openclaw.io/v1/whatsapp/webhook"
private const val MODEL = "modelogrounding:1"
private const val MODEL_TEXT = "Respuesta textual del modelo local."

private class GroundingOllama {
    val chatRequests = Collections.synchronizedList(mutableListOf<String>())

    val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/api/chat") { exchange ->
            chatRequests.add(exchange.requestBody.readBytes().decodeToString())
            val body = """{"model":"$MODEL","message":{"role":"assistant","content":"$MODEL_TEXT"},""" +
                    """"done":true,"done_reason":"stop","prompt_eval_count":20,"eval_count":8,"eval_duration":16000000}"""
            respond(exchange, body)
        }
        createContext("/api/tags") { exchange ->
            respond(exchange, """{"models":[{"name":"$MODEL","size":1200000000,"capabilities":["completion"]}]}""")
        }
        executor = java.util.concurrent.Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable).apply { isDaemon = true }
        }
        start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"
    fun stop() = server.stop(0)

    private fun respond(exchange: HttpExchange, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}

private fun engineAt(host: String): ClawEngine {
    val engine = ClawEngine(configStore = InMemoryOllamaConfigStore())
    engine.updateOllamaConfig(host, MODEL, WEBHOOK, false)
    return engine
}

fun main() = runBlocking {
    println("=== GR-1: arranque sin datos de grounding inventados ===")
    val fresh = engineAt("http://127.0.0.1:11555")
    check("no hay grounding activo al arrancar", fresh.activeGrounding.value == null,
        "${fresh.activeGrounding.value}")
    check("el historial de grounding empieza vacío (sin 'grounding-init')",
        fresh.groundingHistory.value.isEmpty(), "entradas=${fresh.groundingHistory.value.size}")
    check("la serie de rendimiento ya no nace con porcentajes inventados",
        fresh.taskPerformanceMetrics.value.isEmpty(), "puntos=${fresh.taskPerformanceMetrics.value.size}")

    println()
    println("=== GR-2: pedir grounding sin backend declara que no existe, no lo fingea ===")
    val fake = GroundingOllama()
    val engine = engineAt(fake.baseUrl)
    val result = engine.performSearchGrounding("qué es MCP")
    check("isGroundingActive es falso", !result.isGroundingActive, "${result.isGroundingActive}")
    check("cero fuentes: ninguna URL escrita por nosotros", result.sources.isEmpty(),
        "fuentes=${result.sources.size}")
    check("el resumen dice que no se envió ninguna petición",
        result.summaryText.contains("NO disponible") && result.summaryText.contains("no se ha enviado ninguna petición"),
        result.summaryText.take(160))
    check("cita el runtime que sí responde",
        result.summaryText.contains(MODEL) && result.summaryText.contains(fake.baseUrl),
        result.summaryText.take(200))
    check("la consola registra GROUNDING_UNAVAILABLE y no un falso éxito",
        engine.consoleLogs.value.any { it.message.contains("[GROUNDING_UNAVAILABLE]") } &&
                engine.consoleLogs.value.none { it.message.contains("[GROUNDING SUCCESS]") },
        engine.consoleLogs.value.last().message.take(150))
    check("pedir grounding no dispara tampoco una inferencia", fake.chatRequests.isEmpty(),
        "peticiones=${fake.chatRequests.size}")
    check("el historial guarda el intento honesto", engine.groundingHistory.value.size == 1,
        "entradas=${engine.groundingHistory.value.size}")

    println()
    println("=== GR-3: activar el grounding no inyecta contexto inventado en el chat ===")
    engine.setGroundingEnabled(true)
    val before = fake.chatRequests.size
    engine.sendChatMessage("hola", null, null, MobileActionEngine(Context()))
    val reply = engine.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text.orEmpty()
    check("el turno llega al modelo real", fake.chatRequests.size == before + 1, "peticiones=${fake.chatRequests.size}")
    check("la respuesta es sólo del modelo", reply == MODEL_TEXT, "'$reply'")
    check("no se añade el 'Fuente Grounded' inventado", !reply.contains("Fuente Grounded"), reply.take(120))
    check("ningún texto del motor conserva las URLs fabricadas",
        engine.consoleLogs.value.joinToString(" ") { it.message }
            .let { !it.contains("ai.google.dev") && !it.contains("news.google.com") } &&
                engine.groundingHistory.value.joinToString(" ") { it.summaryText + it.sources.toString() }
                    .let { !it.contains("developer.android.com") && !it.contains("deepmind.google") },
        "urls inventadas en logs/historial")

    fake.stop()
    println()
    if (failures == 0) println("TODAS LAS PRUEBAS PASARON") else println("$failures PRUEBAS FALLARON")
    if (failures != 0) kotlin.system.exitProcess(1)
}
