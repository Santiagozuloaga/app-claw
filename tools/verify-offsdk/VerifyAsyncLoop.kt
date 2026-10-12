package verify

import android.content.Context
import com.example.clawcode.core.storage.InMemoryOllamaConfigStore
import com.example.clawcode.data.model.TaskStatus
import com.example.clawcode.engine.ClawEngine
import com.example.clawcode.engine.MobileActionEngine
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * El bucle asíncrono del agente publicaba "[LOCAL_NPU_AGENT] ... (Sin Ollama / 100% On-Device)" y
 * hacía avanzar el progreso con dos delay() que no correspondían a ningún trabajo. Aquí se comprueba
 * que el modelo local participa de verdad, que cada salto de progreso sigue a un paso real y que, si
 * el runtime no responde, la acción del sistema operativo NO se pierde.
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private const val WEBHOOK = "https://api.openclaw.io/v1/whatsapp/webhook"
private const val MODEL = "modelodepega:1"

private class AgentOllama(val sleepMs: Long) {
    val prompts = Collections.synchronizedList(mutableListOf<String>())
    val firstChatAt = AtomicLong(0L)

    val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/api/chat") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            if (firstChatAt.get() == 0L) firstChatAt.set(System.nanoTime())
            prompts.add(body)
            Thread.sleep(sleepMs)
            respond(exchange, 200, CHAT_REPLY)
        }
        createContext("/api/tags") { exchange -> respond(exchange, 200, TAGS_REPLY) }
        createContext("/api/ps") { exchange -> respond(exchange, 200, """{"models":[]}""") }
        start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"
    fun stop() = server.stop(0)

    private fun respond(exchange: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val CHAT_REPLY = """{"model":"modelodepega:1","message":{"role":"assistant","content":"Se abrio la linterna. Nada pendiente."},"done":true,"done_reason":"stop","prompt_eval_count":44,"prompt_eval_duration":100000000,"eval_count":9,"eval_duration":450000000}"""
        const val TAGS_REPLY = """{"models":[{"name":"modelodepega:1","size":1200000000,"details":{"quantization_level":"Q4_K_M"},"capabilities":["completion"]}]}"""
    }
}

private fun engineFor(host: String): ClawEngine {
    val engine = ClawEngine(configStore = InMemoryOllamaConfigStore())
    engine.updateOllamaConfig(host, MODEL, WEBHOOK, false)
    return engine
}

fun main() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    println("=== AL-1: el modelo local participa realmente en el bucle asíncrono ===")
    val fake = AgentOllama(700)
    val engine = engineFor(fake.baseUrl)
    var halfProgressAt = 0L
    scope.launch {
        engine.tasks.collect { tasks ->
            val t = tasks.firstOrNull()
            if (t != null && t.progress == 0.5f && halfProgressAt == 0L) halfProgressAt = System.nanoTime()
        }
    }
    delay(200)
    val startedAt = System.currentTimeMillis()
    engine.runAsyncAgentTaskLoop("enciende la linterna", MobileActionEngine(Context()))
    val elapsed = System.currentTimeMillis() - startedAt
    delay(200)

    check("el bucle preguntó al modelo (1 petición real)", fake.prompts.size == 1,
        "peticiones=${fake.prompts.size}")
    val sentPrompt = fake.prompts.firstOrNull().orEmpty()
    check("el prompt lleva la intención real detectada por el motor de acciones",
        sentPrompt.contains("flashlight") || sentPrompt.contains("linterna"),
        sentPrompt.take(180))
    check("el prompt lleva el resultado medido del sistema, no un texto fijo",
        sentPrompt.contains("Resultado devuelto por el sistema"), sentPrompt.take(120))
    check("la tarea guarda la respuesta del modelo",
        engine.tasks.value.first().logs.any { it.contains("Se abrio la linterna") },
        engine.tasks.value.first().logs.last().take(120))
    check("el progreso de 0.5 ocurre ANTES de consultar el modelo",
        halfProgressAt != 0L && fake.firstChatAt.get() != 0L && halfProgressAt < fake.firstChatAt.get(),
        "mitad=<${halfProgressAt}> chat=<${fake.firstChatAt.get()}>")
    // Relativo a lo que el propio runtime tardó: si la máquina está cargada, las dos cifras crecen
    // juntas y la aserción no se vuelve intermitente. El límite alto sigue teniendo dientes porque
    // los delay() coreografiados que se eliminaron sumaban ~5000 ms extra.
    check("el turno espera al runtime sin duraciones de relleno",
        elapsed >= fake.sleepMs && elapsed < fake.sleepMs + 3_000L,
        "duración=${elapsed}ms, runtime=${fake.sleepMs}ms")
    check("la tarea termina COMPLETED con progreso 1.0",
        engine.tasks.value.first().status == TaskStatus.COMPLETED && engine.tasks.value.first().progress == 1.0f,
        "estado=${engine.tasks.value.first().status} progreso=${engine.tasks.value.first().progress}")

    println()
    println("=== AL-2: desaparece la mentira de '100% On-Device / NPU' ===")
    val allText = (engine.tasks.value.first().description + engine.tasks.value.first().logs.joinToString(" ") +
            engine.consoleLogs.value.joinToString(" ") { it.message })
    check("la descripción de la tarea nombra el runtime real",
        engine.tasks.value.first().description.contains(fake.baseUrl) &&
                engine.tasks.value.first().description.contains(MODEL),
        engine.tasks.value.first().description)
    check("ningún registro jura 'Sin Ollama / 100% On-Device'",
        !allText.contains("100% On-Device"), allText.take(90))
    check("ningún registro jura un NPU/LiteRT que la app no usa",
        !allText.contains("LOCAL_NPU_AGENT") && !allText.contains("LiteRT-LM"),
        allText.take(90))
    check("el consola registra la inferencia real con métricas",
        engine.consoleLogs.value.any { it.message.contains("ASYNC_AGENT_TURN") && it.message.contains(fake.baseUrl) },
        engine.consoleLogs.value.last { it.message.contains("ASYNC") }.message.take(150))
    check("el turno del bucle se etiqueta como lo que es, no como acción rápida",
        engine.consoleLogs.value.any { it.message.contains("[MODEL_RESPONSE_GENERATED] Bucle asíncrono") } &&
                engine.consoleLogs.value.none { it.message.contains("Acción rápida 'Bucle") },
        engine.consoleLogs.value.last { it.message.contains("MODEL_RESPONSE_GENERATED") }.message.take(120))
    check("se conserva el efecto colateral útil (memoria escrita)",
        engine.memories.value.any { it.key == "jarvis_async_last_run" },
        "memorias=${engine.memories.value.size}")

    println()
    println("=== AL-3: runtime caído -> la acción del SO NO se pierde y no se inventa el análisis ===")
    val dead = engineFor("http://127.0.0.1:11499")
    runBlocking { dead.runAsyncAgentTaskLoop("enciende la linterna", MobileActionEngine(Context())) }
    val deadTask = dead.tasks.value.first()
    check("la tarea sigue completándose", deadTask.status == TaskStatus.COMPLETED, "estado=${deadTask.status}")
    check("la acción del dispositivo se ejecutó igual",
        deadTask.logs.any { it.contains("MOBILE_ACTION_EXECUTED") },
        deadTask.logs.joinToString(" ").take(150))
    check("queda explícito que el modelo no aportó nada",
        deadTask.logs.any { it.contains("ASYNC_NO_MODEL") }, deadTask.logs.last().take(150))
    check("no aparece la frase inventada 'Memoria sintetizada'",
        !deadTask.logs.joinToString(" ").contains("Memoria sintetizada"),
        deadTask.logs.joinToString(" ").take(150))
    check("la reserva describe la acción real en lugar de un veredicto de IA",
        deadTask.logs.any { it.contains("Ningún modelo revisó la tarea") },
        deadTask.logs.last().take(170))
    check("el fallo se registra en consola como tal",
        dead.consoleLogs.value.any { it.message.contains("ASYNC_AGENT_ACTION_ONLY") },
        dead.consoleLogs.value.last { it.message.contains("ASYNC") }.message.take(160))

    println()
    println("=== AL-4: el bucle usa el mismo costurón de espera que el chat ===")
    val fake2 = AgentOllama(900)
    val engine2 = engineFor(fake2.baseUrl)
    val sawWaiting = Collections.synchronizedList(mutableListOf<String?>())
    val sampling = java.util.concurrent.atomic.AtomicBoolean(true)
    val cleared = java.util.concurrent.atomic.AtomicBoolean(false)
    val sampler = scope.launch {
        val until = System.currentTimeMillis() + 15_000L
        while (sampling.get() && System.currentTimeMillis() < until) {
            val state = engine2.chatAwaitingRuntime.value
            sawWaiting.add(state)
            if (state == null && sawWaiting.any { it != null }) cleared.set(true)
            delay(40)
        }
    }
    engine2.runAsyncAgentTaskLoop("sube el brillo", MobileActionEngine(Context()))
    // Se observa hasta que el estado se apague, con tope, en vez de dormir una cifra fijada a ojo.
    val waitCap = System.currentTimeMillis() + 5_000L
    while (!cleared.get() && System.currentTimeMillis() < waitCap) delay(50)
    sampling.set(false)
    sampler.cancel()
    check("mientras espera al modelo el bucle publica estado de carga",
        sawWaiting.count { it != null } >= 2, "muestras=${sawWaiting.count { it != null }}")
    check("y lo apaga al terminar", engine2.chatAwaitingRuntime.value == null,
        "final=${engine2.chatAwaitingRuntime.value}")

    fake.stop(); fake2.stop(); scope.cancel()

    println()
    println("RESULTADO ASYNC: $failures FAIL")
    if (failures > 0) throw AssertionError("$failures fallos en el bucle asíncrono del agente")
}
