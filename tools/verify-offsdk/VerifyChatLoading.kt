package verify

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.clawcode.core.storage.InMemoryOllamaConfigStore
import com.example.clawcode.engine.ClawEngine
import com.example.clawcode.engine.MobileActionEngine
import com.example.clawcode.viewmodel.ClawViewModel
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Estado de carga del chat. Con la inferencia simulada el chat "progresaba" solo; ahora un turno
 * real tarda lo que tarde el runtime (medido: 0.5 B local entre 15 y 34 tokens/s, 587-3360 ms).
 * Este arnés usa un Ollama de pega que duerme de forma controlada, para que la ventana de espera
 * sea observable y determinista en vez de depender de la suerte de muestreo.
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private const val WEBHOOK = "https://api.openclaw.io/v1/whatsapp/webhook"
private const val CONFIGURED_MODEL = "modelofalso:1"
private const val REPLIES = "RESPUESTA_DEL_MODELO_LOCAL"
private const val FALLBACK_MARK = "[Runtime local sin respuesta]"

/** Registra cada valor que pasa por el StateFlow, incluido el primero. */
private class Recorder {
    val values: MutableList<String?> = Collections.synchronizedList(mutableListOf())
    val pendingWindows: List<String> get() = synchronized(values) { values.filterNotNull() }
    fun attach(scope: CoroutineScope, flow: StateFlow<String?>) {
        scope.launch { flow.collect { values.add(it) } }
    }
    fun reset() = values.clear()
}

private class SlowOllama(
    val sleepMs: Long,
    /** ok = responde siempre bien; http500 = duerme y revienta; notfound-then-ok = 404 'not found' y luego OK;
     *  notfound-legacy-tags = ídem pero /api/tags sin el campo 'capabilities', como en Ollama antiguos. */
    val mode: String = "ok",
    val modernTags: Boolean = true
) {
    val chatCalls = Collections.synchronizedList(mutableListOf<String>())
    val tagsHits = java.util.concurrent.atomic.AtomicInteger(0)
    private val enVuelo = java.util.concurrent.atomic.AtomicInteger(0)
    val maxEnVuelo = java.util.concurrent.atomic.AtomicInteger(0)

    val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/api/chat") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            chatCalls.add(body)
            val activos = enVuelo.incrementAndGet()
            maxEnVuelo.accumulateAndGet(activos) { actual, nuevo -> maxOf(actual, nuevo) }
            Thread.sleep(sleepMs)
            enVuelo.decrementAndGet()
            val isFirst = chatCalls.size == 1
            when {
                mode == "http500" -> respond(exchange, 500, """{"error":"el modelo de pega se cayó"}""")
                (mode == "notfound-then-ok" || mode == "notfound-legacy-tags") && isFirst ->
                    respond(exchange, 404, """{"error":"model '$CONFIGURED_MODEL' not found"}""")
                else -> respond(exchange, 200, CHAT_REPLY)
            }
        }
        createContext("/api/tags") { exchange ->
            tagsHits.incrementAndGet()
            Thread.sleep(sleepMs)
            respond(exchange, 200, if (modernTags) TAGS_REPLY else LEGACY_TAGS_REPLY)
        }
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
        const val CHAT_REPLY = """{"model":"$REPLIES","message":{"role":"assistant","content":"$REPLIES"},"done":true,"done_reason":"stop","prompt_eval_count":20,"prompt_eval_duration":100000000,"eval_count":6,"eval_duration":300000000}"""
        // Forma medida en Ollama 0.34.4: name, model, size, details y capabilities=["completion","tools"].
        const val TAGS_REPLY = """{"models":[{"name":"qwen2.5:0.5b","model":"qwen2.5:0.5b","size":397821319,"details":{"family":"qwen2","quantization_level":"Q4_K_M","context_length":32768},"capabilities":["completion","tools"]},{"name":"otrolocal:2b","model":"otrolocal:2b","size":1600000000,"details":{"quantization_level":"Q4_K_M"},"capabilities":["completion"]},{"name":"${CONFIGURED_MODEL}","model":"${CONFIGURED_MODEL}","size":1200000000,"details":{"quantization_level":"Q4_K_M"},"capabilities":["completion"]}]}"""
        // Un Ollama anterior a 0.33 no manda 'capabilities'; no debe quedarse el chat sin candidatos.
        const val LEGACY_TAGS_REPLY = """{"models":[{"name":"qwen2.5:0.5b","model":"qwen2.5:0.5b","size":397821319,"details":{"quantization_level":"Q4_K_M"}},{"name":"${CONFIGURED_MODEL}","model":"${CONFIGURED_MODEL}","size":1200000000,"details":{"quantization_level":"Q4_K_M"}}]}"""
    }
}

private fun newEngine(fake: SlowOllama): ClawEngine {
    val engine = ClawEngine(configStore = InMemoryOllamaConfigStore())
    engine.updateOllamaConfig(fake.baseUrl, CONFIGURED_MODEL, WEBHOOK, false)
    return engine
}

private fun sample(text: String) = text.replace("\n", " | ").take(200)

private suspend fun awaitUntil(timeoutMs: Long = 8_000, predicate: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (predicate()) return true
        delay(50)
    }
    return predicate()
}

private fun lastJarvis(engine: ClawEngine) =
    engine.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text.orEmpty()

fun main() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val recorder = Recorder()

    println("=== CL-1: turno real contra un runtime que tarda 900 ms ===")
    val ok = SlowOllama(900)
    val engine = newEngine(ok)
    recorder.reset()
    recorder.attach(scope, engine.chatAwaitingRuntime)
    delay(200)
    check("el chat arranca en reposo (sin estado de carga falso)",
        engine.chatAwaitingRuntime.value == null, "inicial=${engine.chatAwaitingRuntime.value}")

    val seenDuring = Collections.synchronizedList(mutableListOf<String?>())
    val watcher = scope.launch { while (isActive) { seenDuring.add(engine.chatAwaitingRuntime.value); delay(60) } }
    runBlocking { engine.sendChatMessage("saluda", null, null, MobileActionEngine(Context())) }
    watcher.cancel()

    check("mientras el runtime trabaja hay estado de carga", recorder.pendingWindows.isNotEmpty(),
        "ventanas=${recorder.pendingWindows.size}")
    check("el estado de carga nombra el modelo configurado",
        recorder.pendingWindows.any { it.contains(CONFIGURED_MODEL) },
        recorder.pendingWindows.firstOrNull() ?: "ninguna")
    check("el estado de carga nombra el host real",
        recorder.pendingWindows.any { it.contains(ok.baseUrl) }, ok.baseUrl)
    check("el estado de carga se apaga al terminar el turno",
        engine.chatAwaitingRuntime.value == null, "final=${engine.chatAwaitingRuntime.value}")
    check("una sola ventana de espera por turno (sin parpadeos intermedios)",
        recorder.pendingWindows.size == 1, "ventanas=${recorder.pendingWindows.size}")
    check("la respuesta visible es la del modelo, no una reserva",
        lastJarvis(engine).contains(REPLIES) && !lastJarvis(engine).contains(FALLBACK_MARK),
        sample(lastJarvis(engine)))
    check("el usuario ve realmente ~900 ms de espera (no se resuelve al instante)",
        seenDuring.count { it != null } >= 3, "muestras en espera=${seenDuring.count { it != null }}")

    println()
    println("=== CL-2: dos turnos seguidos generan exactamente dos ventanas ===")
    recorder.reset()
    runBlocking {
        engine.sendChatMessage("primero", null, null, MobileActionEngine(Context()))
        if (engine.chatAwaitingRuntime.value != null) {
            failures++; println("  FAIL  entre turnos el chat descansa -> sigue en espera")
        }
        engine.sendChatMessage("segundo", null, null, MobileActionEngine(Context()))
    }
    check("cada turno publica su propio estado de carga",
        recorder.pendingWindows.size == 2, "ventanas=${recorder.pendingWindows.size}")
    check("y el chat descansa entre turnos", engine.chatAwaitingRuntime.value == null, "final=null")

    println()
    println("=== CL-3: el estado de carga cubre la auto-resolución del modelo (2 peticiones) ===")
    val resolver = SlowOllama(300, mode = "notfound-then-ok")
    val engine2 = newEngine(resolver)
    recorder.reset()
    recorder.attach(scope, engine2.chatAwaitingRuntime)
    delay(150)
    runBlocking { engine2.sendChatMessage("resuelve", null, null, MobileActionEngine(Context())) }
    check("hubo estado de carga durante la resolución",
        recorder.pendingWindows.isNotEmpty(), "ventanas=${recorder.pendingWindows.size}")
    check("la ventana única abarca el reintento, sin parpadear a 'listo'",
        recorder.pendingWindows.size == 1, "ventanas=${recorder.pendingWindows.size}")
    check("el reintento terminó con respuesta real del servidor",
        lastJarvis(engine2).contains(REPLIES), sample(lastJarvis(engine2)))
    check("el reintento usó un modelo que el servidor sí tiene",
        resolver.chatCalls.size == 2, "peticiones=${resolver.chatCalls.size}")
    check("la auto-resolución queda registrada en consola",
        engine2.consoleLogs.value.any { it.message.contains("MODEL_AUTORESOLVED") },
        engine2.consoleLogs.value.lastOrNull { it.message.contains("AUTORESOLVED") }?.message?.take(120) ?: "sin log")
    check("el estado de carga se apagó", engine2.chatAwaitingRuntime.value == null, "null")

    println()
    println("=== CL-3b: /api/tags sin 'capabilities' (Ollama antiguo) también resuelve ===")
    val legacy = SlowOllama(250, mode = "notfound-legacy-tags", modernTags = false)
    val engineL = newEngine(legacy)
    runBlocking { engineL.sendChatMessage("legado", null, null, MobileActionEngine(Context())) }
    check("un servidor viejo sin capabilities no deja el chat sin candidato",
        lastJarvis(engineL).contains(REPLIES), sample(lastJarvis(engineL)))
    check("y realmente se hizo el reintento", legacy.chatCalls.size == 2, "peticiones=${legacy.chatCalls.size}")
    check("el modelo elegido se persiste en la config",
        engineL.ollamaConfig.value.modelName == "qwen2.5:0.5b",
        "modelo=${engineL.ollamaConfig.value.modelName}")
    legacy.stop()

    println()
    println("=== CL-4: runtime que falla -> la espera se apaga aunque el turno no salga bien ===")
    val broken = SlowOllama(400, mode = "http500")
    val engine3 = newEngine(broken)
    recorder.reset()
    recorder.attach(scope, engine3.chatAwaitingRuntime)
    delay(150)
    runBlocking { engine3.sendChatMessage("falla", null, null, MobileActionEngine(Context())) }
    check("también se avisa de la espera cuando el runtime va a fallar",
        recorder.pendingWindows.isNotEmpty(), "ventanas=${recorder.pendingWindows.size}")
    check("el estado de carga NO se queda colgado tras el fallo",
        engine3.chatAwaitingRuntime.value == null, "final=${engine3.chatAwaitingRuntime.value}")
    val fallbackText = lastJarvis(engine3)
    check("la reserva admite que NO hubo inferencia",
        fallbackText.contains("NO se procesó con ningún modelo"), sample(fallbackText))
    check("la reserva ya no jura un procesamiento en NPU/LiteRT",
        !fallbackText.contains("He procesado") && !fallbackText.contains("(NPU/LiteRT)"),
        sample(fallbackText))
    check("y el error real del servidor se conserva",
        fallbackText.contains(FALLBACK_MARK) && fallbackText.contains("500"), sample(fallbackText))

    println()
    println("=== CL-5: cancelar el turno no deja un indicador de carga zombie ===")
    val slow = SlowOllama(1500)
    val engine4 = newEngine(slow)
    recorder.reset()
    recorder.attach(scope, engine4.chatAwaitingRuntime)
    delay(150)
    val jarvisBeforeCancel = engine4.chatMessages.value.count { it.sender == "JARVIS" }
    val sendJob = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        engine4.sendChatMessage("cancelame", null, null, MobileActionEngine(Context()))
    }
    delay(350)
    check("el turno cancelado estaba efectivamente en espera",
        engine4.chatAwaitingRuntime.value != null, engine4.chatAwaitingRuntime.value ?: "nunca esperó")
    sendJob.cancel()
    val cleared = awaitUntil { engine4.chatAwaitingRuntime.value == null }
    check("al cancelar, el estado de carga se apaga igual (finally)",
        cleared, "esperando=<${engine4.chatAwaitingRuntime.value}>")
    delay(400)
    check("cancelar no publica una respuesta fantasma del agente",
        engine4.chatMessages.value.count { it.sender == "JARVIS" } == jarvisBeforeCancel,
        "JARVIS $jarvisBeforeCancel -> ${engine4.chatMessages.value.count { it.sender == "JARVIS" }}")
    check("cancelar tampoco publica la reserva de fallo",
        !engine4.chatMessages.value.any { it.sender == "JARVIS" && it.text.contains(FALLBACK_MARK) },
        sample(engine4.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text ?: "sin turno"))

    println()
    println("=== CL-6: las acciones rápidas usan el mismo costurón de espera ===")
    val qa = SlowOllama(600)
    val engine5 = newEngine(qa)
    recorder.reset()
    recorder.attach(scope, engine5.chatAwaitingRuntime)
    delay(150)
    runBlocking { engine5.executeQuickActionCheckMemory() }
    check("la acción rápida también muestra estado de carga",
        recorder.pendingWindows.isNotEmpty(), "ventanas=${recorder.pendingWindows.size}")
    check("y también lo limpia al terminar",
        engine5.chatAwaitingRuntime.value == null, "final=${engine5.chatAwaitingRuntime.value}")

    println()
    println("=== CL-7: el ViewModel expone el estado de carga a la interfaz ===")
    val vmFake = SlowOllama(800)
    val vm = ClawViewModel(Application())
    val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vmRecorder = Recorder()
    vm.viewModelScope.launch { vm.chatMessages.collect {} }
    vm.viewModelScope.launch { vm.ollamaConfig.collect {} }
    vm.viewModelScope.launch { vm.consoleLogs.collect {} }
    vmScope.launch { vm.chatMessages.collect {} }
    vmScope.launch { vm.chatAwaitingRuntime.collect {} }
    vmRecorder.attach(vmScope, vm.chatAwaitingRuntime)
    vm.updateOllamaConfig(vmFake.baseUrl, CONFIGURED_MODEL, WEBHOOK, false)
    // El espejo es stateIn(WhileSubscribed): con un delay() fijo la suscripción podía no estar viva
    // todavía y la medición salía en cero. Se espera a que una emisión forzada se refleje de verdad.
    val mirrorLive = awaitUntil(15_000) { vm.ollamaConfig.value.hostUrl == vmFake.baseUrl }

    // Calienta hasta que el medidor vea una ventana de espera. Esto absorbe sólo la latencia de alta
    // del colector: si el estado de carga no se publicara, los tres intentos se agotarían y la
    // aserción fallaría, así que no convierte el defecto en un pase vacío.
    var intentos = 0
    while (vmRecorder.pendingWindows.isEmpty() && intentos < 3) {
        intentos++
        val beforeWarm = vm.chatMessages.value.count { it.sender == "JARVIS" }
        vm.sendChatMessage("calentamiento $intentos")
        awaitUntil(15_000) {
            vm.chatMessages.value.count { it.sender == "JARVIS" } > beforeWarm &&
                    vm.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text?.contains(REPLIES) == true
        }
    }
    check("el medidor del espejo está suscrito antes de medir",
        mirrorLive && vmRecorder.pendingWindows.isNotEmpty(),
        "espejo=$mirrorLive intentos=$intentos ventanas=${vmRecorder.pendingWindows.size}")

    vmRecorder.reset()
    val vmJarvisBefore = vm.chatMessages.value.count { it.sender == "JARVIS" }
    vm.sendChatMessage("hola vm")
    val replied = awaitUntil(15_000) {
        vm.chatMessages.value.count { it.sender == "JARVIS" } > vmJarvisBefore &&
                vm.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text?.contains(REPLIES) == true
    }
    check("el ViewModel publica el turno real del modelo", replied,
        sample(vm.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text.orEmpty()))
    val sawWindow = awaitUntil(15_000) { vmRecorder.pendingWindows.isNotEmpty() }
    check("el ViewModel expone el estado de carga que leería la UI", sawWindow,
        "ventanas=${vmRecorder.pendingWindows.size}")
    check("el espejo del ViewModel vuelve a reposo al acabar",
        vm.chatAwaitingRuntime.value == null, "final=${vm.chatAwaitingRuntime.value}")
    vmScope.cancel()
    vm.viewModelScope.cancel()

    ok.stop(); resolver.stop(); broken.stop(); slow.stop(); qa.stop(); vmFake.stop()
    scope.cancel()

    println()
    println("=== CL-8: un mensaje vacío no entra en el chat ni llega al runtime ===")
    val vacio = SlowOllama(20)
    val engineVacio = newEngine(vacio)
    val mensajesAntes = engineVacio.chatMessages.value.size
    engineVacio.sendChatMessage("   ", null, null, MobileActionEngine(Context()))
    engineVacio.sendChatMessage("\n\t ", null, null, MobileActionEngine(Context()))
    delay(120)
    check("ningún mensaje vacío se publica en el chat",
        engineVacio.chatMessages.value.size == mensajesAntes,
        "antes=$mensajesAntes ahora=${engineVacio.chatMessages.value.size}")
    check("ningún mensaje vacío se envía al runtime", vacio.chatCalls.isEmpty(),
        "peticiones=${vacio.chatCalls.size}")
    check("el rechazo queda registrado en consola con el destino que se salvó",
        engineVacio.consoleLogs.value.any {
            it.message.contains("[INPUT_REJECTED]") && it.message.contains(CONFIGURED_MODEL)
        }, engineVacio.consoleLogs.value.last().message.take(160))
    check("el estado de espera no se enciende para nada",
        engineVacio.chatAwaitingRuntime.value == null, "${engineVacio.chatAwaitingRuntime.value}")

    val conArchivo = SlowOllama(20)
    val engineArchivo = newEngine(conArchivo)
    engineArchivo.sendChatMessage("  ", attachedFileName = "captura.png", mobileActionEngine = MobileActionEngine(Context()))
    delay(150)
    check("un envío con archivo adjunto pero texto en blanco sí llega al modelo",
        conArchivo.chatCalls.size == 1 && lastJarvis(engineArchivo).contains(REPLIES),
        "peticiones=${conArchivo.chatCalls.size} respuesta=${lastJarvis(engineArchivo).take(40)}")
    vacio.stop(); conArchivo.stop()

    println()
    println("=== CL-9: dos envíos casi simultáneos se serializan ===")
    val par = SlowOllama(320)
    val enginePar = newEngine(par)
    val antesPar = enginePar.chatMessages.value.size
    kotlinx.coroutines.coroutineScope {
        launch { enginePar.sendChatMessage("primero", null, null, MobileActionEngine(Context())) }
        delay(40)
        launch { enginePar.sendChatMessage("segundo", null, null, MobileActionEngine(Context())) }
    }
    delay(120)
    check("nunca hay dos turnos en vuelo contra el runtime", par.maxEnVuelo.get() == 1,
        "máximo simultáneo=${par.maxEnVuelo.get()}")
    check("los dos turnos llegaron, en orden y uno por turno",
        par.chatCalls.size == 2 && par.chatCalls[0].contains("primero") && par.chatCalls[1].contains("segundo"),
        "peticiones=${par.chatCalls.size}")
    check("el segundo turno vio la respuesta completa del primero en su historial",
        par.chatCalls[1].contains(REPLIES), "longitud=${par.chatCalls[1].length}")
    val cola = enginePar.chatMessages.value.drop(antesPar).map { it.sender }
    check("el chat queda como USER, JARVIS, USER, JARVIS sin mezclar burbujas",
        cola == listOf("USER", "JARVIS", "USER", "JARVIS"), cola.joinToString(","))
    check("ambos turnos terminados: el estado de vuelta reposa",
        enginePar.chatAwaitingRuntime.value == null, "${enginePar.chatAwaitingRuntime.value}")
    par.stop()

    println()
    println("=== CL-10: invariantes de estado compartido en el motor (sobre la fuente real) ===")
    val fuenteMotor = java.io.File(
        "../app-claw-main/app/src/main/java/com/example/clawcode/engine/ClawEngine.kt"
    ).readText()
    check("el formateo de hora no comparte un SimpleDateFormat entre hilos",
        Regex("""= SimpleDateFormat\(""").findAll(fuenteMotor).count() == 1 &&
                fuenteMotor.contains("ThreadLocal<SimpleDateFormat>") &&
                !fuenteMotor.contains("private val dateFormat ="),
        "instancias=${Regex("""= SimpleDateFormat\(""").findAll(fuenteMotor).count()}")
    check("los turnos de chat están protegidos por un Mutex",
        fuenteMotor.contains("chatTurnMutex = Mutex()") && fuenteMotor.contains("chatTurnMutex.withLock"),
        "mutex declarado y usado")
    check("el punto público de envío sigue siendo sendChatMessage (la UI no cambió de contrato)",
        fuenteMotor.contains("suspend fun sendChatMessage(") &&
                fuenteMotor.contains("private suspend fun sendChatMessageSerialized("),
        "contrato público intacto")

    println()
    println("RESULTADO CARGA: $failures FAIL")
    if (failures > 0) throw AssertionError("$failures fallos en estado de carga del chat")
}
