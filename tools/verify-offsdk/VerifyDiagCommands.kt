package verify

import android.content.Context
import com.example.clawcode.core.storage.InMemoryOllamaConfigStore
import com.example.clawcode.engine.ClawEngine
import com.example.clawcode.engine.MobileActionEngine
import kotlinx.coroutines.runBlocking

/**
 * La consola de diagnóstico respondía con cuatro líneas "PASSED" escritas a mano, incluida
 * "NPU LiteRT Acceleration -> PASSED (0.42s)", y `/sys status` afirmaba "Qwen 3.5 4-bit LiteRT
 * (100% On-Device NPU)" mientras el único runtime real es Ollama por HTTP. Un diagnóstico que
 * miente es peor que no tener diagnóstico: tapa el bloqueo que el usuario viene a buscar.
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private const val WEBHOOK = "https://api.openclaw.io/v1/whatsapp/webhook"

/** Mantiene el texto de consola que emitió el último comando de la familia pedida. */
private fun lastReply(engine: ClawEngine, prefix: String) =
    engine.consoleLogs.value.lastOrNull { it.message.startsWith(prefix) }?.message.orEmpty()

private fun prompt(engine: ClawEngine, text: String) =
    runBlocking { engine.sendConsolePrompt(text) }

/** Con motor de acciones: es la ruta que la UI usa desde esta ronda. */
private fun promptCon(engine: ClawEngine, text: String) =
    runBlocking { engine.sendConsolePrompt(text, MobileActionEngine(Context())) }

private fun engineAt(host: String): ClawEngine {
    val e = ClawEngine(configStore = InMemoryOllamaConfigStore())
    e.updateOllamaConfig(host, "qwen2.5:0.5b", WEBHOOK, false)
    return e
}

fun main() {
    println("=== DC-1: /diag con el runtime vivo -> medidas reales, sin 'PASSED' inventado ===")
    val live = engineAt("http://127.0.0.1:11434")
    prompt(live, "/diag run")
    val diag = lastReply(live, "[DIAGNOSTIC SUITE RUNNER]")
    println("    ---- informe ----")
    println(diag.replace("\n", "\n    "))
    check("el comando sigue existiendo y responde", diag.isNotBlank(), "len=${diag.length}")
    check("sondea de verdad el host configurado",
        diag.contains("Runtime local: OK en http://127.0.0.1:11434"), "línea de runtime")
    check("reporta latencia MEDIDA en ms",
        Regex("\\(\\d+ ms medidos\\)").containsMatchIn(diag),
        Regex("\\(\\d+ ms medidos\\)").find(diag)?.value ?: "sin ms")
    check("lista los modelos que el servidor realmente sirve",
        diag.contains("qwen2.5:0.5b"), "modelo real presente")
    check("confirma el modelo configurado como disponible",
        diag.contains("Modelo configurado 'qwen2.5:0.5b': disponible"), "resolución real")
    check("ya no finge un acelerador NPU",
        !diag.contains("PASSED (0.42s)") && !diag.contains("NPU LiteRT Acceleration"), "sin 0.42s")
    check("ya no jura '0 Fatal Crashes' sin haber medido nada",
        !diag.contains("0 Fatal Crashes"), "sin veredicto inventado")
    check("el veredicto sale de la medición",
        diag.contains("puede invocar inferencia REAL"), "overall medido")

    println()
    println("=== DC-2: /diag con el runtime caído -> lo dice, no aprueba en falso ===")
    val dead = engineAt("http://127.0.0.1:11499")
    prompt(dead, "/diag run")
    val diagDead = lastReply(dead, "[DIAGNOSTIC SUITE RUNNER]")
    println("    ${diagDead.replace("\n", " | ").take(300)}")
    check("advierte que no hay runtime", diagDead.contains("SIN RESPUESTA"), "sin respuesta")
    check("conserva la causa del fallo",
        diagDead.contains("ConnectException") || diagDead.contains("Failed to connect"), "causa")
    check("explica la consecuencia para el chat",
        diagDead.contains("reserva estática"), "consecuencia")
    check("el overall no dice que todo va bien", !diagDead.contains("puede invocar inferencia REAL"),
        "veredicto honesto")

    println()
    println("=== DC-3: /sys status reporta el motor REAL, no LiteRT ===")
    val liveStatus = engineAt("http://127.0.0.1:11434")
    runBlocking {
        liveStatus.sendChatMessage("Di la palabra DIAG3 y nada mas.", null, null, MobileActionEngine(Context()))
    }
    check("un turno real deja el runtime como conectado",
        liveStatus.ollamaConfig.value.isConnected, "isConnected tras inferencia real")
    prompt(liveStatus, "/sys status")
    val status = lastReply(liveStatus, "[DIAGNOSTIC REPORT]")
    println("    ${status.replace("\n", " | ").take(320)}")
    check("/sys status llega a la rama de estado (antes caía en telemetría)",
        status.isNotBlank(), "ruta arreglada")
    check("nombra el modelo y host que de verdad se usan",
        status.contains("qwen2.5:0.5b") && status.contains("127.0.0.1:11434"), "motor real")
    check("no afirma que LiteRT/NPU está en el dispositivo",
        !status.contains("100% On-Device") && !status.contains("LiteRT (100%"), "sin mentira de motor")
    // '%.2f' usa el locale por defecto (coma decimal en este JVM): se compara con el mismo
    // formateador que usa la app, no con un literal '23.45'.
    val expectedTps = "%.2f".format(liveStatus.hardwareTelemetry.value.tokensPerSecond)
    check("el throughput listado es el medido por la inferencia",
        status.contains("Throughput medido: $expectedTps t/s") &&
                liveStatus.hardwareTelemetry.value.tokensPerSecond > 0f,
        "tps=$expectedTps")

    println()
    println("=== DC-4: la funcionalidad de la consola se conserva ===")
    val keep = engineAt("http://127.0.0.1:11434")
    prompt(keep, "/diag error")
    val sintetico = lastReply(keep, "[REPORTE DE ERROR SINTETICO")
    check("/diag error se declara texto sintetico y no finge una excepcion",
        sintetico.contains("no es un fallo real") && !sintetico.contains("Recovery Protocol"),
        "longitud=${sintetico.length}")
    promptCon(keep, "/intent open gmail")
    val abierto = lastReply(keep, "[MOBILE INTENT")
    check("/intent open lanza de verdad cuando hay motor de acciones",
        abierto.startsWith("[MOBILE INTENT EJECUTADO]") && abierto.contains("stub lanzada: gmail"),
        abierto.lineSequence().last().trim())
    prompt(keep, "/intent open gmail")
    check("/intent open sin motor declara que no lanzo nada",
        lastReply(keep, "[MOBILE INTENT NO EJECUTADO]").isNotBlank(), "rama honesta")
    promptCon(keep, "/intent flash on")
    check("/intent flash usa Camera2 a traves del motor, no un texto fijo",
        lastReply(keep, "[MOBILE INTENT").let {
            it.startsWith("[MOBILE INTENT EJECUTADO]") || it.startsWith("[MOBILE INTENT FALLO]")
        }, "resultado del intent, no del literal")
    promptCon(keep, "/intent")
    val listado = lastReply(keep, "[MOBILE INTENTS]")
    check("el conteo de acciones sale del catalogo (3 en el stub), no de un literal",
        listado.contains("Acciones declaradas en el catálogo: 3") && !listado.contains("Categorías dispuestas: 10"),
        "contado")
    prompt(keep, "/sys memory")
    check("/sys memory sigue respondiendo",
        lastReply(keep, "[SYSTEM MEMORY REPORT]").isNotBlank(), "rama intacta")
    prompt(keep, "help")
    val ayuda = lastReply(keep, "COMANDOS DISPONIBLES")
    // La propia ayuda explica que /intent list y /tasks no existen; lo que no puede hacer es
    // anunciarlos como comandos disponibles en la lista de viñetas.
    val vinetas = ayuda.lines().filter { it.trimStart().startsWith("•") }.joinToString(" | ")
    check("la ayuda solo anuncia en sus viñetas comandos que tienen rama",
        vinetas.contains("/intent open") && !vinetas.contains("/intent list") && !vinetas.contains("/tasks"),
        "sin comandos fantasma en la lista")
    prompt(keep, "/bridge status")
    check("/bridge status conserva su propio informe (no se lo roba /sys status)",
        lastReply(keep, "[PUENTE OPENCLAW WHATSAPP]").isNotBlank(), "rama del puente intacta")
    prompt(keep, "/sys telemetry")
    check("/sys sigue devolviendo telemetría de hardware",
        lastReply(keep, "[SYSTEM TELEMETRY]").isNotBlank(), "telemetría intacta")
    prompt(keep, "estado del agente")
    check("el texto suelto 'estado' sigue entrando al informe de motor",
        lastReply(keep, "[DIAGNOSTIC REPORT]").isNotBlank(), "alias intacto")

    println()
    println("=== DC-5: un comando desconocido ya no se autodeclara 'exitosamente' procesado ===")
    prompt(keep, "/vuela hacia el horizonte")
    val unknown = keep.consoleLogs.value.last().message
    check("dice que no ejecutó nada", unknown.contains("no se ejecutó ninguna acción"),
        unknown.take(160))
    check("no afirma éxito falso", !unknown.contains("exitosamente"), "sin 'exitosamente'")

    println()
    println("=== DC-6: la superficie de arranque describe el motor REAL, no LiteRT ===")
    val store = InMemoryOllamaConfigStore()
    val first = ClawEngine(configStore = store)
    first.updateOllamaConfig("http://127.0.0.1:11434", "qwen2.5:0.5b", WEBHOOK, false)
    // Segundo motor sobre el mismo 'disco': es lo que ocurre al reiniciar la app.
    val rebooted = ClawEngine(configStore = store)
    val seed = rebooted.chatMessages.value.first { it.id == "c-1" }
    println("    ${seed.text.take(260)}")
    check("el saludo sigue existiendo (no se borra funcionalidad)", seed.sender == "JARVIS", "c-1 presente")
    check("dice con qué runtime responde de verdad",
        seed.text.contains("127.0.0.1:11434") && seed.text.contains("qwen2.5:0.5b"), "motor real")
    check("no promete un agente 100% On-Device que no existe",
        !seed.text.contains("100% On-Device") && !seed.text.contains("LiteRT"), "sin mentira")
    check("el saludo sale de la config guardada, no de una constante",
        !first.chatMessages.value.first { it.id == "c-1" }.text.contains("qwen2.5:0.5b") &&
                seed.text.contains("qwen2.5:0.5b"),
        "arranque limpio vs arranque con config")
    check("avisa de que sin runtime no se simula la respuesta",
        seed.text.contains("en lugar de devolver una respuesta simulada"), "promesa honesta")
    val bootLogs = rebooted.consoleLogs.value
    check("el arranque no incluye el error inventado de la semilla antigua",
        bootLogs.size == 4 && bootLogs.none { it.message.contains("Resolved via Safe Fallback") },
        "logs=${bootLogs.size}")
    val boot = bootLogs.joinToString(" ") { it.message }
    check("el STATUS de arranque nombra el host configurado",
        boot.contains("http://127.0.0.1:11434"), "runtime declarado")
    check("ya no jura un socket Webhook 'verified' que nadie abrió",
        !boot.contains("[SUCCESS] Webhook socket verified"), "sin socket inventado")
    check("reconoce que esta build no trae NPU/LiteRT",
        boot.contains("No hay NPU/LiteRT embebido"), "limitación declarada")

    println()
    println("=== DC-7: /agent y la ruta de inferencia ya no juran un modelo GGUF/LiteRT ===")
    val agent = engineAt("http://127.0.0.1:11434")
    prompt(agent, "/agent")
    val agentReport = lastReply(agent, "[AGENT ENGINE ACTIVE]")
    check("/agent sigue respondiendo", agentReport.contains("Comandos: /agent model"), agentReport.take(60))
    check("/agent nombra el runtime que atiende",
        agentReport.contains("qwen2.5:0.5b") && agentReport.contains("http://127.0.0.1:11434"),
        agentReport.replace("\n", " ").take(140))
    check("/agent ya no inventa 'Qwen 3.5 LiteRT 4-bit'",
        !agentReport.contains("LiteRT 4-bit"), agentReport.replace("\n", " ").take(140))

    prompt(agent, "/agent model")
    val modelReport = lastReply(agent, "[AGENT MODEL CONFIG]")
    check("/agent model conserva los parámetros del modfile",
        modelReport.contains("Temperature:") && modelReport.contains("TopP:") &&
                modelReport.contains("Estilo de Voz TTS:"), modelReport.replace("\n", " ").take(150))
    check("/agent model declara el runtime HTTP real",
        modelReport.contains("Runtime que atiende los turnos") &&
                modelReport.contains("http://127.0.0.1:11434"), modelReport.replace("\n", " ").take(150))
    check("/agent model aclara que no carga LiteRT/GGUF en el proceso",
        modelReport.contains("esta build no carga LiteRT/GGUF"), modelReport.replace("\n", " ").take(150))

    // La línea de enrutado se emite en el turno de chat, no en la consola de comandos.
    val routed = engineAt("http://127.0.0.1:11434")
    runBlocking { routed.sendChatMessage("explícame qué eres", null, null, MobileActionEngine(Context())) }
    val routerLine = routed.consoleLogs.value
        .lastOrNull { it.message.startsWith("[MODEL_INFERENCE_ROUTING]") }?.message.orEmpty()
    check("la ruta del turno existe y apunta al host real",
        routerLine.contains("http://127.0.0.1:11434"), routerLine.take(160))
    check("el enrutado ya no manda el turno a un 'Modelo GGUF' que no se puede cargar",
        !routerLine.contains("Modelo GGUF") && !routerLine.contains("LiteRT\""), routerLine.take(160))

    val withAction = engineAt("http://127.0.0.1:11434")
    runBlocking { withAction.sendChatMessage("abre la linterna", null, null, MobileActionEngine(Context())) }
    val resolverLines = withAction.consoleLogs.value.count { it.sender == "INTENT_RESOLVER" }
    check("el turno sigue dejando UNA línea de resolución de intención (no se perdió el trazo)",
        resolverLines == 1, "líneas=$resolverLines")
    check("el enrutado no interpola la etiqueta inventada del modfile",
        !routerLine.contains("CLAW Standard") && !routerLine.contains("Qwen 3.5 4-bit"),
        routerLine.take(160))

    println()
    println("=== DC-8: el chat produce texto GENERADO por el modelo real, no una plantilla ===")
    val real = engineAt("http://127.0.0.1:11434")
    val echoToken = "ZEBRA-4242"
    runBlocking {
        real.sendChatMessage("Repite exactamente esta palabra y nada más: $echoToken", null, null, MobileActionEngine(Context()))
    }
    val echoReply = real.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text.orEmpty()
    check("el turno real no cae en la reserva estática",
        echoReply.isNotBlank() && !echoReply.contains("NO se procesó con ningún modelo") &&
                !echoReply.contains("[Runtime local sin respuesta]"), echoReply.take(120))
    check("el modelo obedeció una instrucción nueva (no es texto escrito por nosotros)",
        echoReply.uppercase().contains(echoToken), "esperado=$echoToken -> ${echoReply.take(100)}")
    check("la respuesta no es la plantilla del turno con acción del SO",
        !echoReply.startsWith("Acción ejecutada en el dispositivo") &&
                !echoReply.startsWith("Ajuste del modfile aplicado"), echoReply.take(80))
    check("las métricas del turno salen del servidor, no de un delay()",
        real.hardwareTelemetry.value.tokensPerSecond > 0f,
        "t/s=${real.hardwareTelemetry.value.tokensPerSecond}")
    check("el turno marca el runtime como conectado", real.ollamaConfig.value.isConnected, "isConnected")

    val other = engineAt("http://127.0.0.1:11434")
    runBlocking { other.sendChatMessage("¿Cuánto es 21 por 2?", null, null, MobileActionEngine(Context())) }
    val otherReply = other.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text.orEmpty()
    check("dos preguntas distintas no producen la misma respuesta",
        otherReply.isNotBlank() && otherReply != echoReply, otherReply.take(100))

    println()
    println("=== DC-9: /bridge deja de fingir un socket y un 200 OK que no existen ===")
    val liveEng = engineAt("http://127.0.0.1:11434")
    prompt(liveEng, "/bridge")
    val bridgeLive = lastReply(liveEng, "[PUENTE OPENCLAW WHATSAPP]")
    println("    ---- estado del puente ----")
    println(bridgeLive.replace("\n", "\n    "))
    check("/bridge sigue respondiendo", bridgeLive.isNotBlank(), "len=${bridgeLive.length}")
    check("/bridge ya no afirma que el socket está conectado",
        !bridgeLive.contains("Socket State") && !bridgeLive.contains("CONNECTED"),
        bridgeLive.take(160))
    check("declara el estado real: ninguna conexión abierta",
        bridgeLive.contains("NUNCA ABIERTO"), bridgeLive.take(200))
    check("nombra el webhook sólo como configuración",
        bridgeLive.contains(WEBHOOK), "webhook en el informe")
    check("dice qué runtime atiende de verdad los turnos",
        bridgeLive.contains("qwen2.5:0.5b") && bridgeLive.contains("http://127.0.0.1:11434"),
        bridgeLive.take(220))
    check("señala el bloque real en lugar de ocultarlo",
        bridgeLive.contains("Falta en el repo"), bridgeLive.take(240))
    check("sin haber ejecutado ningún turno no afirma que el runtime respondió",
        bridgeLive.contains("sin medir todavía"), bridgeLive.take(260))

    runBlocking {
        liveEng.sendChatMessage("Di sólo la palabra PUENTEBRIDGE.", null, null, MobileActionEngine(Context()))
    }
    prompt(liveEng, "/bridge")
    val bridgeAfterTurn = lastReply(liveEng, "[PUENTE OPENCLAW WHATSAPP]")
    check("tras un turno real, el estado cita la medición salidas del runtime",
        bridgeAfterTurn.contains("respondió en el último turno medido") && bridgeAfterTurn.contains("t/s"),
        bridgeAfterTurn.take(260))

    prompt(liveEng, "/bridge test")
    val bridgeTest = lastReply(liveEng, "[PRUEBA DEL PUENTE OPENCLAW]")
    println("    ---- prueba del puente ----")
    println(bridgeTest.replace("\n", "\n    "))
    check("/bridge test ya no inventa un payload recibido",
        !bridgeTest.contains("INCOMING_WHATSAPP_PAYLOAD") && !bridgeTest.contains("HTTP 200 OK") &&
                !bridgeTest.contains("Forwarded to Jarvis Agent"), bridgeTest.take(160))
    check("aclara que no se envió ningún payload de WhatsApp",
        bridgeTest.contains("Payload de WhatsApp enviado: NINGUNO"), bridgeTest.take(200))
    check("ejecuta un turno real contra el runtime",
        bridgeTest.contains("Turno real ejecutado contra 'qwen2.5:0.5b'") && bridgeTest.contains("t/s"),
        bridgeTest.take(220))
    val modelLine = bridgeTest.substringAfter("Lo que respondió el modelo:", "")
    check("muestra lo que generó el modelo (y no texto nuestro)",
        modelLine.isNotBlank() && !modelLine.contains("Mensaje simulado") &&
                !modelLine.contains("Forwarded to Jarvis Agent"),
        modelLine.take(120))

    val deadEng = engineAt("http://127.0.0.1:11499")
    prompt(deadEng, "/bridge test")
    val deadTest = lastReply(deadEng, "[PRUEBA DEL PUENTE OPENCLAW]")
    check("con el runtime caído la prueba lo dice en vez de simular éxito",
        deadTest.contains("no respondió") && !deadTest.contains("Turno real ejecutado") &&
                !deadTest.contains("HTTP 200 OK"), deadTest.take(220))
    prompt(deadEng, "/bridge")
    val deadBridge = lastReply(deadEng, "[PUENTE OPENCLAW WHATSAPP]")
    check("el estado del puente también es honesto sin runtime",
        deadBridge.contains("NUNCA ABIERTO") && deadBridge.contains("sin medir todavía"),
        deadBridge.take(260))

    println()
    println("=== DC-10: /sys deja de imprimir cifras y aceleradores inventados ===")
    val sysEng = engineAt("http://127.0.0.1:11434")
    prompt(sysEng, "/sys")
    val sysFresh = lastReply(sysEng, "[SYSTEM TELEMETRY]")
    println("    ---- telemetría en frío ----")
    println(sysFresh.replace("\n", "\n    "))
    check("no anuncia un acelerador LiteRT/Vulkan/NPU que la app no tiene",
        !sysFresh.contains("LiteRT") && !sysFresh.contains("Vulkan") && !sysFresh.contains("Adreno"),
        sysFresh.take(220))
    check("declara que no hay acelerador de inferencia",
        sysFresh.contains("Ninguno"), sysFresh.take(200))
    check("sin sondeo previo no suelta una carga de CPU al azar",
        sysFresh.contains("carga sin medir"), sysFresh.take(260))
    check("RAM y batería sin sondeo se marcan como sin medir",
        sysFresh.contains("sin medir todavía"), sysFresh.take(260))
    check("la frecuencia escrita a mano (2.8GHz) ya no aparece",
        !sysFresh.contains("2.8GHz"), sysFresh.take(160))
    check("el número de núcleos viene de la JVM, no de un literal",
        sysFresh.contains("${Runtime.getRuntime().availableProcessors()} núcleos"), sysFresh.take(200))

    prompt(sysEng, "/sys memory")
    val memReport = lastReply(sysEng, "[SYSTEM MEMORY REPORT]")
    check("/sys memory ya no imprime el 68.4% inventado",
        !memReport.contains("68.4") && !memReport.contains("Token Buffer Healthy"), memReport.take(240))
    check("el ratio se calcula sobre las entradas reales",
        Regex("""Compaction Ratio: \d+% de las entradas guardadas""").containsMatchIn(memReport),
        memReport.take(240))

    sysEng.updateHardwareTelemetry(
        com.example.clawcode.data.model.HardwareTelemetry(
            cpuUsagePercent = 33, cpuCores = 8, ramUsedGb = 2.1f, ramTotalGb = 7.4f,
            batteryLevelPercent = 77, batteryTemperatureC = 31f,
            cpuTotalJiffies = 9000L, cpuIdleJiffies = 6000L
        )
    )
    prompt(sysEng, "/sys")
    val sysPolled = lastReply(sysEng, "[SYSTEM TELEMETRY]")
    check("con dos lecturas de /proc/stat presenta la carga como medida",
        sysPolled.contains("33% (delta de /proc/stat") &&
                sysPolled.contains("sondeo real de ActivityManager") &&
                sysPolled.contains("sondeo real de BatteryManager"), sysPolled.take(280))
    check("tras el sondeo desaparece el 'sin medir'",
        !sysPolled.contains("carga sin medir") && !sysPolled.contains("sin medir todavía"),
        sysPolled.take(260))

    println()
    println(if (failures == 0) "RESULTADO DIAG: 0 FAIL" else "RESULTADO DIAG: $failures FAIL")
}