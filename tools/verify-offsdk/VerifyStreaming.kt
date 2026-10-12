package verify

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.clawcode.core.storage.InMemoryOllamaConfigStore
import com.example.clawcode.engine.ClawEngine
import com.example.clawcode.engine.MobileActionEngine
import com.example.clawcode.engine.OllamaChatMessage
import com.example.clawcode.engine.OllamaChatRequest
import com.example.clawcode.engine.OllamaClient
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * El chat esperaba el texto completo: con un runtime real a ~36 tokens/s el usuario se quedaba
 * varios segundos mirando una pantalla sin nada. Aquí se comprueba el modo stream de Ollama
 * (NDJSON, un chunk por token): que los deltas llegan DE VERDAD de forma incremental, que la
 * burbuja del chat crece con ellos, que el texto final es idéntico al de la respuesta completa,
 * que solo hay una burbuja por turno, y que un stream cortado a la mitad NO se muestra como si
 * fuera una respuesta terminada.
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private const val WEBHOOK = "https://api.openclaw.io/v1/whatsapp/webhook"
private const val MODEL = "qwen-stream-test:1"
private val DELTAS = listOf("Hola", ", ", "esto", " llega", " troceado")
private const val FULL_TEXT = "Hola, esto llega troceado"

/** Servidor Ollama falso que emite el turno como NDJSON, con una pausa real entre chunks. */
private class StreamOllama(
    private val deltas: List<String>,
    private val gapMs: Long,
    private val truncateBeforeTerminal: Boolean = false
) {
    val chatBodies = Collections.synchronizedList(mutableListOf<String>())

    val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/api/chat") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            chatBodies.add(body)
            val wantsStream = body.contains("\"stream\":true")
            if (!wantsStream) {
                // Ollama con stream=false responde un único JSON completo: el falso servidor
                // tiene que comportarse igual que el real o la prueba no valdría nada.
                val complete = """{"model":"$MODEL","message":{"role":"assistant","content":"$FULL_TEXT"},""" +
                        """"done":true,"done_reason":"stop","prompt_eval_count":33,"eval_count":${deltas.size},""" +
                        """"eval_duration":${deltas.size * 1_000_000L}}"""
                val bytes = complete.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                return@createContext
            }
            exchange.responseHeaders.add("Content-Type", "application/x-ndjson")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
                deltas.forEach { delta ->
                    val chunk = """{"model":"$MODEL","message":{"role":"assistant","content":"$delta"},"done":false}"""
                    out.write((chunk + "\n").toByteArray())
                    out.flush()
                    Thread.sleep(gapMs)
                }
                if (!truncateBeforeTerminal) {
                    // El chunk terminal es el único que trae las métricas reales del turno.
                    val terminal = """{"model":"$MODEL","message":{"role":"assistant","content":""},""" +
                            """"done":true,"done_reason":"stop","prompt_eval_count":33,""" +
                            """"eval_count":${deltas.size},"eval_duration":${deltas.size * 1_000_000L}}"""
                    out.write((terminal + "\n").toByteArray())
                    out.flush()
                }
            }
        }
        createContext("/api/tags") { exchange ->
            respond(exchange, """{"models":[{"name":"$MODEL","size":1200000000,"details":{"quantization_level":"Q4_K_M"},"capabilities":["completion"]}]}""")
        }
        createContext("/api/ps") { exchange -> respond(exchange, """{"models":[]}""") }
        // Hijos daemon: si una aserción revienta antes de stop(), la JVM debe poder terminar.
        executor = java.util.concurrent.Executors.newFixedThreadPool(4) { runnable ->
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

private fun requestFor(host: String) = OllamaChatRequest(
    hostUrl = host,
    model = MODEL,
    systemPrompt = "Eres JARVIS.",
    history = listOf(OllamaChatMessage(role = "user", content = "turno previo")),
    userText = "repite el texto",
    temperature = 0.7f,
    topP = 0.9f,
    stopTokens = listOf("[END]"),
    maxTokens = 64
)

private fun engineAt(host: String): ClawEngine {
    val engine = ClawEngine(configStore = InMemoryOllamaConfigStore())
    engine.updateOllamaConfig(host, MODEL, WEBHOOK, false)
    return engine
}

private fun lastJarvis(engine: ClawEngine) =
    engine.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text.orEmpty()

fun main() = runBlocking {
    // El HttpServer del JDK no activa TCP_NODELAY: con chunks de ~90 bytes Nagle los agrupa y el
    // arnés vería los deltas todos juntos, tapando justo lo que se quiere medir.
    System.setProperty("sun.net.httpserver.nodelay", "true")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    println("=== ST-1: el cliente consume NDJSON y entrega cada delta al llegar ===")
    val fake = StreamOllama(DELTAS, gapMs = 120)
    val received = Collections.synchronizedList(mutableListOf<String>())
    val arrivalMs = Collections.synchronizedList(mutableListOf<Long>())
    val callStartedAt = System.currentTimeMillis()
    val reply = OllamaClient().chat(requestFor(fake.baseUrl)) { delta ->
        received.add(delta)
        arrivalMs.add(System.currentTimeMillis() - callStartedAt)
    }.getOrThrow()
    val totalLatency = reply.latencyMs
    delay(50)

    check("el payload pide stream=true cuando hay callback de deltas",
        fake.chatBodies.firstOrNull().orEmpty().contains("\"stream\":true"),
        fake.chatBodies.firstOrNull()?.take(90).orEmpty())
    check("se mantiene think=false (trampa verificada de los modelos con razonamiento)",
        fake.chatBodies.firstOrNull().orEmpty().contains("\"think\":false"),
        "chars=${fake.chatBodies.firstOrNull().orEmpty().length}")
    check("llegan los 5 deltas, en orden y sin doblar", received.toList() == DELTAS, received.joinToString("|"))
    check("el cuerpo llega de forma incremental, no volcado al cierre",
        arrivalMs.last() - arrivalMs.first() >= 3 * 120 && arrivalMs.size == DELTAS.size,
        "primer=${arrivalMs.first()} ultimo=${arrivalMs.last()} deltas=${arrivalMs.size}")
    check("el texto final es la concatenación exacta de los deltas",
        reply.text == FULL_TEXT, "texto='${reply.text}' esperado='$FULL_TEXT'")
    check("las métricas salen del chunk terminal",
        reply.completionTokens == DELTAS.size && reply.promptTokens == 33 && reply.stopReason == "stop",
        "salida=${reply.completionTokens} entrada=${reply.promptTokens} stop=${reply.stopReason}")
    check("tokens/s medidos del stream", reply.tokensPerSecond > 0f, "${reply.tokensPerSecond} t/s")
    fake.stop()

    println("=== ST-2: sin callback se sigue pidiendo la respuesta completa (stream=false) ===")
    val fake2 = StreamOllama(DELTAS, gapMs = 5)
    val plain = OllamaClient().chat(requestFor(fake2.baseUrl)).getOrThrow()
    check("stream=false cuando nadie escucha los deltas",
        fake2.chatBodies.firstOrNull().orEmpty().contains("\"stream\":false"),
        fake2.chatBodies.firstOrNull()?.take(90).orEmpty())
    check("el turno sin stream sigue respondiendo igual", plain.text == FULL_TEXT, plain.text)
    fake2.stop()

    println("=== ST-3: un stream cortado a la mitad NO se vende como respuesta completa ===")
    val cut = StreamOllama(listOf("PARCIAL-TRUNCADO"), gapMs = 5, truncateBeforeTerminal = true)
    val partial = mutableListOf<String>()
    val cutResult = OllamaClient().chat(requestFor(cut.baseUrl)) { partial.add(it) }
    val cutFailure = cutResult.exceptionOrNull()?.message.orEmpty()
    check("el cliente recibió el delta antes del corte", partial == listOf("PARCIAL-TRUNCADO"), partial.joinToString())
    check("el corte se propaga como fallo", cutResult.isFailure, cutFailure.take(160))
    check("el fallo explica que la generación quedó incompleta",
        cutFailure.contains("incompleta") && cutFailure.contains("done=true"), cutFailure.take(200))
    cut.stop()

    println("=== ST-4: la burbuja del chat crece con el stream y termina siendo una sola ===")
    val fake4 = StreamOllama(DELTAS, gapMs = 160)
    val engine = engineAt(fake4.baseUrl)
    val jarvisBefore = engine.chatMessages.value.count { it.sender == "JARVIS" }
    val lengths = Collections.synchronizedList(mutableListOf<Int>())
    val watching = AtomicBoolean(true)
    val watcher: Job = scope.launch {
        engine.chatMessages.collect { list ->
            if (watching.get()) {
                lengths.add(list.lastOrNull { it.sender == "JARVIS" }?.text?.length ?: -1)
            }
        }
    }
    delay(60)
    engine.sendChatMessage("cuentame algo corto", null, null, MobileActionEngine(Context()))
    watching.set(false)
    watcher.cancel()
    delay(60)

    val finalText = lastJarvis(engine)
    val partialSnapshots = lengths.filter { it in 1 until finalText.length }
    check("durante el turno existió una burbuja con texto parcial",
        partialSnapshots.isNotEmpty(), "instantes=${lengths.joinToString(",")}")
    check("el texto parcial es prefijo del final (crece, no se reescribe a capricho)",
        partialSnapshots.all { engine.chatMessages.value.last().text.startsWith(finalText.take(it)) },
        "parciales=${partialSnapshots.joinToString(",")} final='$finalText'")
    check("el turno sigue una sola burbuja JARVIS",
        engine.chatMessages.value.count { it.sender == "JARVIS" } == jarvisBefore + 1,
        "antes=$jarvisBefore ahora=${engine.chatMessages.value.count { it.sender == "JARVIS" }}")
    check("la burbuja queda con el texto completo del modelo",
        finalText == FULL_TEXT, "'$finalText'")
    check("se limpiza el estado de espera al terminar el stream",
        engine.chatAwaitingRuntime.value == null, "${engine.chatAwaitingRuntime.value}")
    check("el turno en stream se registra como inferencia real del modelo que responde",
        engine.consoleLogs.value.any {
            it.message.contains("[MODEL_RESPONSE_GENERATED]") && it.message.contains(MODEL)
        },
        engine.consoleLogs.value.last().message.take(150))
    check("la telemetría refleja tokens/s medidos del stream",
        engine.hardwareTelemetry.value.tokensPerSecond > 0f,
        "${engine.hardwareTelemetry.value.tokensPerSecond} t/s")
    fake4.stop()

    println("=== ST-5: si el stream se corta, la burbuja se reemplaza por la reserva y no queda duplicada ===")
    val cut2 = StreamOllama(listOf("PARCIAL-TRUNCADO"), gapMs = 5, truncateBeforeTerminal = true)
    val engine2 = engineAt(cut2.baseUrl)
    val jarvisBefore2 = engine2.chatMessages.value.count { it.sender == "JARVIS" }
    engine2.sendChatMessage("cuentame algo", null, null, MobileActionEngine(Context()))
    delay(50)
    val fallbackText = lastJarvis(engine2)
    check("no queda el texto truncado como respuesta",
        !fallbackText.contains("PARCIAL-TRUNCADO"), fallbackText.take(180))
    check("se emite la reserva estática con el fallo real",
        fallbackText.contains("[Runtime local sin respuesta]") && fallbackText.contains("incompleta"),
        fallbackText.take(180))
    check("exactamente una burbuja nueva pese al delta ya publicado",
        engine2.chatMessages.value.count { it.sender == "JARVIS" } == jarvisBefore2 + 1,
        "antes=$jarvisBefore2 ahora=${engine2.chatMessages.value.count { it.sender == "JARVIS" }}")
    check("el estado de espera se limpia también en fallo",
        engine2.chatAwaitingRuntime.value == null, "${engine2.chatAwaitingRuntime.value}")
    check("el runtime se marca como caído", !engine2.ollamaConfig.value.isConnected,
        engine2.ollamaConfig.value.isConnected.toString())
    cut2.stop()

    println("=== ST-6: contra el Ollama real el texto llega token a token ===")
    val realHost = "http://127.0.0.1:11434"
    val realModel = runCatching { OllamaClient(2, 5).listCompletionModels(realHost).getOrThrow() }
        .getOrNull()?.firstOrNull { !it.isCloudStub }
    if (realModel == null) {
        check("hay un Ollama real contra el que medir el stream", false, "sin runtime en $realHost")
    } else {
        val realDeltas = Collections.synchronizedList(mutableListOf<String>())
        val realStamps = Collections.synchronizedList(mutableListOf<Long>())
        val realStartedAt = System.currentTimeMillis()
        val realReply = OllamaClient().chat(
            requestFor(realHost).copy(
                model = realModel.name,
                userText = "Enumera en una frase larga diez colores y describe brevemente cada uno."
            )
        ) { delta ->
            realDeltas.add(delta)
            realStamps.add(System.currentTimeMillis() - realStartedAt)
        }.getOrThrow()

        check("el modelo real emite el turno en muchos deltas", realDeltas.size >= 8,
            "deltas=${realDeltas.size} tokens=${realReply.completionTokens}")
        check("los deltas reales se reparten en el tiempo de generación",
            realStamps.last() - realStamps.first() > 300,
            "primero=${realStamps.first()}ms ultimo=${realStamps.last()}ms total=${realReply.latencyMs}ms")
        check("el texto final coincide con lo que fue saliendo por el socket",
            realReply.text == realDeltas.joinToString("").trim(),
            "chars=${realReply.text.length} '${realReply.text.take(70)}'")
        check("la respuesta es del modelo, no la reserva estática",
            realReply.text.length > 40 && !realReply.text.contains("texto estático de la app"),
            realReply.text.take(80))
        check("tokens/s medidos del stream real", realReply.tokensPerSecond > 0f,
            "${realReply.tokensPerSecond} t/s con ${realDeltas.size} deltas")
    }

    println("=== ST-7: un turno con acción del SO también se escribe por el stream ===")
    val fake7 = StreamOllama(DELTAS, gapMs = 150)
    val engine7 = engineAt(fake7.baseUrl)
    val jarvisBefore7 = engine7.chatMessages.value.count { it.sender == "JARVIS" }
    val snapshots7 = Collections.synchronizedList(mutableListOf<String>())
    val watching7 = AtomicBoolean(true)
    val watcher7 = scope.launch {
        engine7.chatMessages.collect { list ->
            if (watching7.get()) {
                snapshots7.add(list.lastOrNull { it.sender == "JARVIS" }?.text.orEmpty())
            }
        }
    }
    delay(60)
    engine7.sendChatMessage("EXECUTA: linterna", null, null, MobileActionEngine(Context()))
    watching7.set(false)
    watcher7.cancel()
    delay(50)

    val final7 = lastJarvis(engine7)
    val headerOnly = snapshots7.firstOrNull {
        it.startsWith("Acción ejecutada en el dispositivo") && !it.contains(FULL_TEXT)
    }
    check("la cabecera medida se publica antes de que el modelo termine",
        headerOnly != null, snapshots7.joinToString(" | ") { "'${it.take(45)}'" }.take(220))
    check("el turno de acción pide stream=true",
        fake7.chatBodies.lastOrNull().orEmpty().contains("\"stream\":true"),
        fake7.chatBodies.lastOrNull()?.take(80).orEmpty())
    check("el prompt de la acción llega al modelo real",
        fake7.chatBodies.lastOrNull().orEmpty().contains("Acción del sistema ya ejecutada"),
        "chars=${fake7.chatBodies.lastOrNull().orEmpty().length}")
    check("el turno acaba en cabecera + texto del modelo",
        final7.startsWith("Acción ejecutada en el dispositivo") && final7.endsWith(FULL_TEXT) &&
                final7.contains("\n\n$FULL_TEXT"),
        "'${final7.take(60)}' … '${final7.takeLast(24)}'")
    check("una sola burbuja JARVIS para el turno de acción",
        engine7.chatMessages.value.count { it.sender == "JARVIS" } == jarvisBefore7 + 1,
        "antes=$jarvisBefore7 ahora=${engine7.chatMessages.value.count { it.sender == "JARVIS" }}")
    fake7.stop()

    println("=== ST-8: una acción rápida también se escribe por el stream ===")
    val fake8 = StreamOllama(DELTAS, gapMs = 150)
    val engine8 = engineAt(fake8.baseUrl)
    val jarvisBefore8 = engine8.chatMessages.value.count { it.sender == "JARVIS" }
    val snaps8 = Collections.synchronizedList(mutableListOf<Int>())
    val watching8 = AtomicBoolean(true)
    val watcher8 = scope.launch {
        engine8.chatMessages.collect { list ->
            if (watching8.get()) snaps8.add(list.lastOrNull { it.sender == "JARVIS" }?.text?.length ?: -1)
        }
    }
    delay(60)
    engine8.executeQuickActionSummarizeLogs()
    watching8.set(false)
    watcher8.cancel()
    delay(50)

    val final8 = lastJarvis(engine8)
    check("la acción rápida pide stream=true al runtime",
        fake8.chatBodies.lastOrNull().orEmpty().contains("\"stream\":true"),
        fake8.chatBodies.lastOrNull()?.take(80).orEmpty())
    check("el informe crece en el chat mientras el modelo lo genera",
        snaps8.any { it in 1 until final8.length }, "instantes=${snaps8.joinToString(",")}")
    check("la acción rápida deja una sola burbuja JARVIS",
        engine8.chatMessages.value.count { it.sender == "JARVIS" } == jarvisBefore8 + 1,
        "antes=$jarvisBefore8 ahora=${engine8.chatMessages.value.count { it.sender == "JARVIS" }}")
    check("el texto publicado es el completo del modelo", final8 == FULL_TEXT, "'$final8'")
    check("la tarea de la acción rápida se registra con su resultado",
        engine8.tasks.value.firstOrNull()?.logs?.any { it.contains("Resumen generado por '$MODEL'") } == true,
        engine8.tasks.value.firstOrNull()?.logs?.last().orEmpty().take(120))
    fake8.stop()

    println("=== ST-9: la burbuja crece a través del ViewModel con el Ollama real ===")
    if (realModel == null) {
        check("hay runtime real para medir el camino completo", false, "sin Ollama en 127.0.0.1:11434")
    } else {
        val vm = com.example.clawcode.viewmodel.ClawViewModel(android.app.Application())
        vm.engine.updateOllamaConfig(realHost, realModel.name, WEBHOOK, false)
        val growth = Collections.synchronizedList(mutableListOf<Int>())
        val baselineIds = Collections.synchronizedSet(mutableSetOf<String>())
        val watchingVm = AtomicBoolean(true)
        var baselined = false
        val collector = scope.launch {
            vm.chatMessages.collect { list ->
                // El espejo (stateIn) arranca vacío y luego suelta las burbujas semilla: la primera
                // emisión no vacía fija la línea base y no cuenta como texto del turno.
                if (!baselined) {
                    if (list.isEmpty()) return@collect
                    baselineIds.addAll(list.filter { it.sender == "JARVIS" }.map { it.id })
                    baselined = true
                    return@collect
                }
                if (watchingVm.get()) {
                    list.lastOrNull { it.sender == "JARVIS" && it.id !in baselineIds }
                        ?.let { growth.add(it.text.length) }
                }
            }
        }
        vm.sendChatMessage("Explica en diez frases cortas qué hace un sistema operativo.")
        // No basta con que aparezca una burbuja: con el stream existe desde el primer token. Se
        // espera a que el texto se estabilice y el motor (fuente autoritativa, no el espejo congelado
        // por stateIn sin suscriptor) diga que ya no está consultando.
        var waitedMs = 0
        var previousLen = -1
        while (waitedMs < 120_000) {
            delay(250)
            waitedMs += 250
            val len = vm.chatMessages.value.lastOrNull { it.sender == "JARVIS" && it.id !in baselineIds }
                ?.text?.length ?: 0
            if (waitedMs >= 1_000 && len > 0 && len == previousLen &&
                    vm.engine.chatAwaitingRuntime.value == null) break
            previousLen = len
        }
        watchingVm.set(false)
        collector.cancel()
        delay(80)

        val distinctGrowth = growth.distinct()
        val vmFinal = vm.chatMessages.value.lastOrNull { it.sender == "JARVIS" && it.id !in baselineIds }?.text.orEmpty()
        check("el turno del ViewModel terminó (no quedó esperando ni mudo)",
            vmFinal.isNotBlank() && vm.engine.chatAwaitingRuntime.value == null,
            "espera=${waitedMs}ms chars=${vmFinal.length}")
        check("el texto va llegando en tamaños crecientes (no aparece de golpe)",
            distinctGrowth.size >= 3 && distinctGrowth.first() < distinctGrowth.last(),
            "larguras=${distinctGrowth.joinToString(",")}")
        check("los tamaños son monótonos: el turno sólo se añade, no se reescribe a capricho",
            growth.zipWithNext().all { (a, b) -> b >= a }, "instantes=${growth.size}")
        check("lo publicado en el espejo del ViewModel es el texto final del modelo",
            vmFinal.length == growth.maxOrNull() && vmFinal.length > 80, "chars=${vmFinal.length}")
        check("el estado de espera se apaga al terminar el turno real",
            vm.engine.chatAwaitingRuntime.value == null, "${vm.engine.chatAwaitingRuntime.value}")
        check("el turno real del ViewModel deja métricas medidas",
            vm.engine.ollamaConfig.value.isConnected &&
                    vm.engine.hardwareTelemetry.value.tokensPerSecond > 0f,
            "t/s=${vm.engine.hardwareTelemetry.value.tokensPerSecond}")
        vm.viewModelScope.cancel()
    }

    scope.cancel()
    println()
    if (failures == 0) println("TODAS LAS PRUEBAS PASARON") else println("$failures PRUEBAS FALLARON")
    if (failures != 0) kotlin.system.exitProcess(1)
}
