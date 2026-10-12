package verify

import android.content.Context
import com.example.clawcode.data.model.TaskStatus
import com.example.clawcode.engine.ClawEngine
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

/** Frases que las quick actions fabricaban antes y que ya no pueden aparecer. */
private val FABRICATED = listOf(
    "Todos los subsistemas",
    "operan de forma óptima",
    "0 advertencias críticas",
    "Almacén de Vectores Local activo",
    "Analizando AST",
    "Arquitectura MVVM con Coroutines y StateFlow verificada",
    "Memoria sincronizada correctamente",
    "Resumen completado con éxito"
)

private val json = Json { ignoreUnknownKeys = true }

private class PromptCapturingOllama(private val reply: String = "RESUMEN-REAL-DEL-MODELO") {
    val prompts = CopyOnWriteArrayList<String>()

    val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/api/chat") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            val messages = json.parseToJsonElement(body).jsonObject["messages"]?.jsonArray.orEmpty()
            prompts += messages.lastOrNull()?.jsonObject
                ?.get("content")?.jsonPrimitive?.content.orEmpty()
            respond(exchange, """{"model":"qwen2.5:0.5b","message":{"role":"assistant","content":"$reply"},"done":true,"done_reason":"stop","prompt_eval_count":40,"eval_count":6,"eval_duration":300000000}""")
        }
        createContext("/api/tags") { exchange ->
            respond(exchange, """{"models":[{"name":"qwen2.5:0.5b","size":397458432,"details":{"quantization_level":"Q4_K_M","context_length":32768}}]}""")
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

private fun lastJarvis(engine: ClawEngine) =
    engine.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text.orEmpty()

private fun logsMatching(engine: ClawEngine, needle: String) =
    engine.consoleLogs.value.count { it.message.contains(needle) }

fun main() {
    println("=== QA-1: las quick actions publican lo que dice el modelo, no una plantilla ===")
    val fake = PromptCapturingOllama()
    val engine = ClawEngine()
    engine.updateOllamaConfig(fake.baseUrl, "qwen2.5:0.5b", "https://api.openclaw.io/v1/whatsapp/webhook", false)

    // Datos reales que el prompt debe arrastrar.
    engine.injectSimulatedLog("PRUEBA_MARCADOR", "linea-unica-ZQX-4417 para auditar el prompt")
    engine.addMemory("clave-ZQX", "Session", "valor-secreto-ZQX-9931")
    val realTokens = engine.memories.value.sumOf { it.tokenCount }

    runBlocking {
        engine.executeQuickActionSummarizeLogs()
        engine.executeQuickActionCheckMemory()
        engine.executeQuickActionAnalyzeCode()
    }

    check("las tres acciones preguntaron al modelo", fake.prompts.size == 3, "prompts=${fake.prompts.size}")

    val logsPrompt = fake.prompts.getOrElse(0) { "" }
    val memPrompt = fake.prompts.getOrElse(1) { "" }
    val codePrompt = fake.prompts.getOrElse(2) { "" }

    check("el resumen recibe los registros REALES de la consola",
        logsPrompt.contains("linea-unica-ZQX-4417"), "prompt len=${logsPrompt.length}")
    check("la memoria recibe las entradas REALES",
        memPrompt.contains("clave-ZQX") && memPrompt.contains("valor-secreto-ZQX-9931"), "incluida")
    check("la memoria usa el recuento real de tokens, no el 14250 de fábrica",
        memPrompt.contains("Tokens reales (suma de tokenCount): $realTokens") &&
                !memPrompt.contains("14250"), "tokens=$realTokens")
    check("el análisis recibe el runtime real y prohíbe inventar una auditoría",
        codePrompt.contains(fake.baseUrl) && codePrompt.contains("no se ha ejecutado ningún análisis estático"),
        "host+advertencia presentes")

    val chatTexts = engine.chatMessages.value.filter { it.sender == "JARVIS" }.map { it.text }
    check("el chat muestra la respuesta del modelo", chatTexts.count { it == "RESUMEN-REAL-DEL-MODELO" } == 3,
        "coincidencias=${chatTexts.count { it == "RESUMEN-REAL-DEL-MODELO" }}")
    check("ningún texto fabricado vuelve al chat",
        chatTexts.none { text -> FABRICATED.any { text.contains(it) } }, "limpio")
    check("queda registrada la inferencia real de cada acción",
        logsMatching(engine, "Acción rápida") == 3 &&
                logsMatching(engine, "[MODEL_RESPONSE_GENERATED] Acción rápida") == 3,
        "logs=${logsMatching(engine, "[MODEL_RESPONSE_GENERATED] Acción rápida")}")

    val tasks = engine.tasks.value.filter { it.id.startsWith("task-quick-") }
    check("las tres tareas se crean y quedan completadas", tasks.size == 3 &&
            tasks.all { it.status == TaskStatus.COMPLETED && it.progress == 1.0f },
        "tasks=${tasks.size} status=${tasks.map { it.status }.distinct()}")
    check("los logs de tarea ya no dicen 'completado con éxito' sin prueba",
        tasks.none { task -> FABRICATED.any { task.logs.joinToString().contains(it) } }, "limpio")
    check("la telemetría refleja la medición del servidor",
        engine.hardwareTelemetry.value.tokensPerSecond == 20.0f,
        "tps=${engine.hardwareTelemetry.value.tokensPerSecond} (6 tok / 0.3 s)")

    fake.stop()

    println()
    println("=== QA-2: runtime caído -> datos medidos, sin diagnóstico inventado ===")
    val dead = ClawEngine()
    dead.updateOllamaConfig("http://127.0.0.1:11499", "qwen2.5:0.5b", "https://api.openclaw.io/v1/whatsapp/webhook", false)
    dead.injectSimulatedLog("PRUEBA_MARCADOR", "registro-caido-ZQX-1")
    runBlocking {
        dead.executeQuickActionSummarizeLogs()
        dead.executeQuickActionCheckMemory()
    }
    val deadTexts = dead.chatMessages.value.filter { it.sender == "JARVIS" }.map { it.text }
    println("    ---- muestra ----")
    println("    ${deadTexts.lastOrNull()?.take(180)?.replace("\n", " | ")}")
    check("avisa de que el runtime no respondió",
        deadTexts.count { it.contains("[Runtime local sin respuesta]") } == 2, "avisos=${deadTexts.count { it.contains("[Runtime local sin respuesta]") }}")
    check("sigue publicando el recuento verificable",
        deadTexts.any { it.contains("Registros reales en consola:") } &&
                deadTexts.any { it.contains("Memorias almacenadas:") }, "datos medidos presentes")
    check("no fabrica ningún diagnóstico",
        deadTexts.none { text -> FABRICATED.any { text.contains(it) } }, "limpio")
    check("no añade la pista del emulador cuando el host no es 10.0.2.2",
        deadTexts.none { it.contains("sólo es alcanzable desde el emulador") },
        "host=127.0.0.1:11499, pista ausente")
    val deadTasks = dead.tasks.value.filter { it.id.startsWith("task-quick-") }
    check("las tareas quedan en ERROR y sin progreso",
        deadTasks.all { it.status == TaskStatus.ERROR && it.progress == 0.0f },
        "status=${deadTasks.map { it.status }.distinct()} progress=${deadTasks.map { it.progress }.distinct()}")
    check("el runtime pasa a no conectado", !dead.ollamaConfig.value.isConnected,
        "isConnected=${dead.ollamaConfig.value.isConnected}")
    check("se registra la caída en consola",
        logsMatching(dead, "[MODEL_INFERENCE_FALLBACK] Acción rápida") == 2,
        "logs=${logsMatching(dead, "[MODEL_INFERENCE_FALLBACK] Acción rápida")}")

    println()
    println("=== QA-3: extremo a extremo contra Ollama real ===")
    runBlocking {
        val live = ClawEngine()
        live.updateOllamaConfig("http://127.0.0.1:11434", "qwen2.5:0.5b",
            "https://api.openclaw.io/v1/whatsapp/webhook", false)
        live.injectSimulatedLog("VERIFY", "entrada de prueba ZQX-LIVE-77")
        live.executeQuickActionSummarizeLogs()
        val reply = lastJarvis(live)
        println("    respuesta del modelo: ${reply.take(220)}")
        check("el modelo real genera el resumen", reply.isNotBlank() &&
                reply != "RESUMEN-REAL-DEL-MODELO", "len=${reply.length}")
        check("no es una de las plantillas fabricadas",
            FABRICATED.none { reply.contains(it) }, "limpio")
        val tps = live.hardwareTelemetry.value.tokensPerSecond
        check("t/s medidos en el modelo real", tps > 0f, "tps=$tps")
        check("inferencia real registrada",
            logsMatching(live, "[MODEL_RESPONSE_GENERATED] Acción rápida 'Resumir registros'") == 1, "ok")
    }

    println()
    if (failures == 0) println("RESULTADO QUICK ACTIONS: TODAS LAS PRUEBAS PASARON")
    else println("RESULTADO QUICK ACTIONS: $failures PRUEBA(S) FALLARON")
    if (failures != 0) kotlin.system.exitProcess(1)
}
