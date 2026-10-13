package verify

import android.app.Application
import android.content.FakePrefsDisk
import androidx.lifecycle.StubUncaughtExceptions
import androidx.lifecycle.viewModelScope
import com.example.clawcode.engine.HardwareTelemetryUtils
import com.example.clawcode.viewmodel.ClawViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private const val PREFS = "claw_ollama_config"

/** Textos hardcodeados que la app puede mostrar sin haber hablado con ningún modelo. */
private val SIMULATED_TEMPLATES = listOf(
    "Hola, soy JARVIS, tu Agente IA Autónomo 100% On-Device",
    "Comprendido. He procesado tu solicitud",
    "Todos los subsistemas de inferencia operan"
)

/**
 * Los flows del ViewModel son stateIn(WhileSubscribed(5000)): sin coleccionista el espejo queda
 * congelado en el valor inicial. Una pantalla Compose los colecciona con collectAsState(), así
 * que el arnés hace exactamente lo mismo para probar el comportamiento real de la app.
 */
private fun subscribeLikeCompose(vm: ClawViewModel): List<Job> = listOf(
    vm.viewModelScope.launch { vm.ollamaConfig.collect {} },
    vm.viewModelScope.launch { vm.chatMessages.collect {} },
    vm.viewModelScope.launch { vm.consoleLogs.collect {} },
    vm.viewModelScope.launch { vm.hardwareTelemetry.collect {} }
)

private suspend fun awaitUntil(timeoutMs: Long = 60_000, predicate: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (predicate()) return true
        delay(100)
    }
    return predicate()
}

/**
 * _chatMessages arranca con un saludo semilla de JARVIS (ClawEngine.kt:1090), así que esperar
 * "cualquier mensaje de JARVIS" devuelve la semilla. Hay que esperar uno con id nuevo.
 */
private suspend fun awaitNewJarvisReply(vm: ClawViewModel, knownIds: Set<String>): String? {
    // Con el chat en stream la burbuja existe desde el primer token: esperar solo su aparición
    // devolvía texto a medio escribir y las aserciones de telemetría competían con el turno.
    // Se aguarda a que el propio estado de carga del runtime se apague (fin de turno real).
    awaitUntil(120_000) {
        vm.chatMessages.value.any { it.sender == "JARVIS" && it.id !in knownIds } &&
                vm.chatAwaitingRuntime.value == null
    }
    // Un margen breve para que el flow espejo propague también logs y telemetría del mismo turno.
    delay(500)
    return vm.chatMessages.value.lastOrNull { it.sender == "JARVIS" && it.id !in knownIds }?.text
}

fun main() = runBlocking {
    println("=== VM-1: ClawViewModel real compila y construye con PrefsOllamaConfigStore ===")
    val vm = ClawViewModel(Application())
    check("ClawViewModel(Application()) se construye", true, "sin error de compilación ni de runtime")
    check("expone el engine real", vm.engine.ollamaConfig.value.hostUrl.isNotBlank(),
        vm.engine.ollamaConfig.value.hostUrl)
    check("stateIn inicializa los flows con valor", vm.chatMessages.value.isEmpty() &&
            vm.ollamaConfig.value.hostUrl.isNotBlank(), "chatMessages=[] ollamaConfig listo")

    val collectors = subscribeLikeCompose(vm)
    delay(300)

    println()
    println("=== VM-2: la config que se edita desde el ViewModel LLEGA AL DISCO ===")
    check("antes de configurar no hay fichero de preferencias",
        FakePrefsDisk.snapshot(PREFS).isEmpty(), "snapshot vacío")
    vm.updateOllamaConfig(
        hostUrl = "http://127.0.0.1:11434",
        modelName = "qwen2.5:0.5b",
        webhookUrl = "https://api.openclaw.io/v1/whatsapp/webhook",
        useFallback = true
    )
    val propagated = awaitUntil { vm.ollamaConfig.value.modelName == "qwen2.5:0.5b" }
    val saved = FakePrefsDisk.snapshot(PREFS)
    println("    prefs en disco: $saved")
    check("PrefsOllamaConfigStore está cableado de verdad (no el store en memoria)",
        saved["host_url"] == "http://127.0.0.1:11434", "${saved["host_url"]}")
    check("el modelo también se persiste", saved["model_name"] == "qwen2.5:0.5b",
        "${saved["model_name"]}")
    check("un consumidor suscrito ve el cambio (patrón collectAsState de la UI)",
        propagated, "${vm.ollamaConfig.value.hostUrl} | ${vm.ollamaConfig.value.modelName}")

    println()
    println("=== VM-3: el chat enviado a través del ViewModel hace inferencia REAL ===")
    val seedIds = vm.chatMessages.value.filter { it.sender == "JARVIS" }.map { it.id }.toSet()
    check("el saludo semilla de la app está presente", seedIds.isNotEmpty(), "ids semilla=$seedIds")
    vm.sendChatMessage("Responde únicamente con la palabra PUENTE.")
    val reply = awaitNewJarvisReply(vm, seedIds)
    println("    respuesta: ${reply?.take(200)}")
    check("el ViewModel devuelve una respuesta del modelo", !reply.isNullOrBlank(),
        "len=${reply?.length}")
    check("no es ninguna respuesta simulada hardcodeada",
        reply != null && !SIMULATED_TEMPLATES.any { reply.contains(it) }, "sin plantilla")
    // El turno termina cuando se apaga chatAwaitingRuntime, pero el log de consola y la telemetría
    // se escriben justo después: leídos de una sola disparan FAIL intermitentes bajo carga.
    awaitUntil { vm.consoleLogs.value.any { it.message.contains("Inferencia REAL vía Ollama") } }
    val realLog = vm.consoleLogs.value.filter { it.message.contains("Inferencia REAL vía Ollama") }
    check("queda registrada la inferencia real", realLog.isNotEmpty(),
        realLog.lastOrNull()?.message?.take(160) ?: "ninguno")
    awaitUntil { vm.ollamaConfig.value.isConnected }
    check("el runtime figura como conectado", vm.ollamaConfig.value.isConnected,
        "isConnected=${vm.ollamaConfig.value.isConnected}")
    awaitUntil { vm.hardwareTelemetry.value.tokensPerSecond > 0f }
    val tps = vm.hardwareTelemetry.value.tokensPerSecond
    check("la telemetría de t/s sale de la medición real", tps > 0f, "tps=$tps")

    println()
    println("=== VM-4: un segundo ClawViewModel arranca con la config guardada ===")
    val vm2 = ClawViewModel(Application())
    check("sobrevive a un reinicio de la app",
        vm2.ollamaConfig.value.hostUrl == "http://127.0.0.1:11434" &&
                vm2.ollamaConfig.value.modelName == "qwen2.5:0.5b",
        "${vm2.ollamaConfig.value.hostUrl} | ${vm2.ollamaConfig.value.modelName}")
    check("no se restaura una conectividad inventada",
        !vm2.ollamaConfig.value.isConnected, "isConnected=false hasta medir de nuevo")

    println()
    println("=== VM-5: lo que ve configProvider tras cambiar el runtime en marcha ===")
    // Escenario real: el chat descubre o el usuario edita el host y luego se abre ModelManagerScreen.
    // Un provider que lea .value sin suscripción se quedaría con la foto del arranque.
    vm.updateOllamaConfig("http://127.0.0.1:11434", "modelo-cambiado-en-marcha",
        "https://api.openclaw.io/v1/whatsapp/webhook", true)
    val seenBySubscriber = awaitUntil {
        vm.ollamaConfig.value.modelName == "modelo-cambiado-en-marcha"
    }
    check("el proveedor suscrito recibe el runtime vigente, no el del arranque",
        seenBySubscriber, "modelName=${vm.ollamaConfig.value.modelName}")

    println()
    println("=== VM-6: un fallo del sondeo de telemetría NO derriba la app ni apaga el bucle ===")
    // SystemMonitor.captureMetrics() llama a registerReceiver() y getMemoryInfo(): APIs de
    // plataforma que pueden lanzar (SecurityException, restricciones en segundo plano). El
    // viewModelScope real lleva SupervisorJob, así que una excepción sin capturar dentro del init{}
    // llega al handler de la plataforma y mata el proceso entero. El stub instala ese handler y aquí
    // se comprueba que el fallo queda capturado, que el bucle sigue vivo y que el chat no se entera.
    //
    // Primero hay que APAGAR los bucles de vm y vm2: HardwareTelemetryUtils es un object compartido,
    // así que si no, los sondeos de otros ViewModels se mezclan con los de vm3, los fallos se
    // registran en OTRO ClawEngine y la comprobación de consola queda falseada (eso fue exactamente
    // lo que dio FAIL en la primera versión de esta prueba).
    vm.viewModelScope.cancel()
    vm2.viewModelScope.cancel()
    delay(6_500)
    HardwareTelemetryUtils.reset()
    StubUncaughtExceptions.clear()
    delay(6_500)
    check("los bucles de los ViewModels anteriores están realmente apagados",
        HardwareTelemetryUtils.calls.get() == 0,
        "sondeos residuales=${HardwareTelemetryUtils.calls.get()} en 6,5 s")

    val vm3 = ClawViewModel(Application())
    val vm3Collectors = subscribeLikeCompose(vm3)

    val firstPoll = awaitUntil(30_000) { HardwareTelemetryUtils.calls.get() >= 1 }
    check("el bucle de telemetría arranca con el ViewModel", firstPoll,
        "sondeos=${HardwareTelemetryUtils.calls}")

    HardwareTelemetryUtils.failNext = 2
    val twoFailures = awaitUntil(40_000) { HardwareTelemetryUtils.thrown.size >= 2 }
    check("el sondeo falla dos veces (escenario forzado)", twoFailures,
        "fallidos=${HardwareTelemetryUtils.thrown}")

    check("ninguna excepción llega al handler del scope (la app no se cae)",
        StubUncaughtExceptions.errors.isEmpty(),
        StubUncaughtExceptions.errors.joinToString().ifBlank { "cero excepciones sin capturar" })

    // Se lee el flow autoritativo del motor, no el espejo stateIn(WhileSubscribed), y se espera:
    // el stub apunta «ha fallado» ANTES de que el catch registre el log, así que comprobarlo de
    // inmediato era una carrera de la propia prueba, no un defecto de la app.
    val failLogged = awaitUntil(20_000) {
        vm3.engine.consoleLogs.value.count { it.message.contains("[TELEMETRY_POLL_FAILED]") } >= 2
    }
    val failLogs = vm3.engine.consoleLogs.value.filter { it.message.contains("[TELEMETRY_POLL_FAILED]") }
    check("el fallo se registra en consola en vez de quedar mudo", failLogged,
        "logs=${failLogs.size} | ${failLogs.lastOrNull()?.message?.take(150) ?: "ninguno"}")

    val loopSurvived = awaitUntil(40_000) { HardwareTelemetryUtils.calls.get() >= 3 }
    check("el bucle sigue sondeando después de fallar", loopSurvived,
        "sondeos totales=${HardwareTelemetryUtils.calls}")

    vm3.updateOllamaConfig("http://127.0.0.1:11434", "qwen2.5:0.5b",
        "https://api.openclaw.io/v1/whatsapp/webhook", false)
    val seeds3 = vm3.chatMessages.value.filter { it.sender == "JARVIS" }.map { it.id }.toSet()
    vm3.sendChatMessage("Responde únicamente con la palabra TELEMETRIA.")
    val reply3 = awaitNewJarvisReply(vm3, seeds3)
    check("con la telemetría fallando, el chat sigue respondiendo con un modelo real",
        !reply3.isNullOrBlank() && !SIMULATED_TEMPLATES.any { reply3.contains(it) },
        "${reply3?.take(80) ?: "sin respuesta"}")

    // El motor es la fuente autoritativa de la medición. El flow espejo del ViewModel llega con
    // retardo (stateIn), así que capturar ahí devolvía un valor viejo —0 o del turno anterior—
    // que después "no se pisaba" y la aserción quedaba en el aire.
    // La medición la escribe el motor al cerrar el turno; se aguarda a que exista antes de
    // capturarla, si no el compare posterior parte de un 0.0 que sí cambia después.
    awaitUntil(15_000) { vm3.engine.hardwareTelemetry.value.tokensPerSecond > 0f }
    val tpsMeasured = vm3.engine.hardwareTelemetry.value.tokensPerSecond
    check("la inferencia real deja una medición de t/s no nula", tpsMeasured > 0f, "tps=$tpsMeasured")
    awaitUntil(30_000) { HardwareTelemetryUtils.calls.get() >= 5 }
    awaitUntil(10_000) { vm3.hardwareTelemetry.value.tokensPerSecond == tpsMeasured }
    check(
        "un sondeo posterior no pisa los t/s medidos por la inferencia",
        vm3.engine.hardwareTelemetry.value.tokensPerSecond == tpsMeasured &&
                vm3.hardwareTelemetry.value.tokensPerSecond == tpsMeasured,
        "motor=${vm3.engine.hardwareTelemetry.value.tokensPerSecond} " +
                "espejo=${vm3.hardwareTelemetry.value.tokensPerSecond} tras ${HardwareTelemetryUtils.calls} sondeos"
    )

    vm3Collectors.forEach { it.cancel() }
    vm3.viewModelScope.cancel()

    collectors.forEach { it.cancel() }
    // El init{} del ViewModel lanza un bucle de telemetría infinito: sin cancelarlo la JVM no termina.
    vm.viewModelScope.cancel()
    vm2.viewModelScope.cancel()

    println()
    if (failures == 0) println("RESULTADO VIEWMODEL: TODAS LAS PRUEBAS PASARON")
    else println("RESULTADO VIEWMODEL: $failures PRUEBA(S) FALLARON")
    if (failures != 0) kotlin.system.exitProcess(1)
}
