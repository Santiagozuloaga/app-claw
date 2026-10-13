package verify

import com.example.clawcode.core.storage.InMemoryOllamaConfigStore
import com.example.clawcode.engine.ClawEngine
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import kotlinx.coroutines.runBlocking

/**
 * La interfaz todavía rotulaba capacidades que esta build no tiene: un feed de consola que
 * anunciaba "Ruta NPU verificada" con 48 t/s inventados, unos botones Lite/Standard/Pro que
 * escribían "Qwen 3.5 4-bit LiteRT" o "Gemma 7B" en el modfile, y tarjetas que decían que el
 * motor operaba en "LiteRT Fallback". Importa más de lo que parece: `runtimeSnapshot()` mete
 * `baseModel` en el prompt que se envía al modelo, así que se le declaraba un runtime falso y
 * el modelo lo repetía. Aquí se comprueba el texto de las pantallas y, sobre todo, lo que
 * realmente sale hacia el runtime tras seleccionar cada preset.
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private const val WEBHOOK = "https://api.openclaw.io/v1/whatsapp/webhook"
private const val MODEL = "modelodepega:1"

private val RUNTIME_FALSO = listOf("LiteRT", "Qwen 3.5", "Gemma 7B", "4-bit")

/** Fuente real de la app, sin las líneas de comentario (los literales no se tocan). */
private fun source(rel: String): String {
    val candidates = listOf(
        "../../app/src/main/java/com/example/clawcode/$rel",
        "../app-claw-main/app/src/main/java/com/example/clawcode/$rel",
        "app-claw-main/app/src/main/java/com/example/clawcode/$rel"
    )
    val file = candidates.map { java.io.File(it) }.firstOrNull { it.isFile }
        ?: error("no se encuentra la fuente real $rel")
    return file.readText().lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
}

private class UiRecordingOllama {
    val prompts = Collections.synchronizedList(mutableListOf<String>())

    private fun reply(exchange: HttpExchange, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/api/chat") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            prompts += body
            // El flag stream cambia la forma de responder: hay que respetarlo o el cliente
            // interpretaría mal la contestación y la prueba fallaría por el arnés, no por la app.
            val wantsStream = Regex(""""stream"\s*:\s*true""").containsMatchIn(body)
            if (wantsStream) {
                val chunks = listOf(
                    """{"model":"modelodepega:1","message":{"role":"assistant","content":"OK-"},"done":false}""",
                    """{"model":"modelodepega:1","message":{"role":"assistant","content":"DEL-MODELO"},"done":false}""",
                    """{"model":"modelodepega:1","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","prompt_eval_count":40,"prompt_eval_duration":100000000,"eval_count":3,"eval_duration":100000000}"""
                ).joinToString("\n")
                reply(exchange, chunks)
            } else {
                reply(exchange, """{"model":"modelodepega:1","message":{"role":"assistant","content":"OK-DEL-MODELO"},"done":true,"done_reason":"stop","prompt_eval_count":40,"prompt_eval_duration":100000000,"eval_count":3,"eval_duration":100000000}""")
            }
        }
        createContext("/api/tags") { exchange ->
            reply(exchange, """{"models":[{"name":"modelodepega:1","size":1200000000,"details":{"quantization_level":"Q4_K_M"},"capabilities":["completion"]}]}""")
        }
        createContext("/api/ps") { exchange -> reply(exchange, """{"models":[]}""") }
        start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"
    fun stop() = server.stop(0)
}

/** Lee los rótulos que cada botón Lite/Standard/Pro escribe de verdad en el modfile. */
private fun presetLabels(): List<Pair<String, String>> {
    val lines = source("ui/screens/ConfigModfileScreen.kt").lines()
    val found = mutableListOf<Pair<String, String>>()
    for (i in lines.indices) {
        val button = Regex("ModelOption\\(\"([^\"]+)\"").find(lines[i])?.groupValues?.get(1) ?: continue
        val next = lines.getOrNull(i + 1).orEmpty()
        // Sólo las filas que escriben un rótulo NUEVO en baseModel; las de voz/temperatura
        // reenvían jarvisModfile.baseModel y no son un preset.
        val marker = "updateJarvisModfile(jarvisModfile.agentName, \""
        val start = next.indexOf(marker)
        if (start < 0) continue
        val from = start + marker.length
        val close = next.indexOf('"', from)
        if (close > from) found += button to next.substring(from, close)
    }
    return found
}

/** Reproduce el clasificador real de la pantalla: qué botón queda seleccionado según el rótulo. */
private fun classify(label: String): String = when {
    label.contains("Lite") -> "Lite"
    label.contains("Pro") -> "Pro"
    else -> "Standard"
}

/** Sólo los literales de string de una fuente: es lo que ve el usuario, no los identificadores. */
private fun literalsOf(src: String): String =
    Regex("\"([^\"]*)\"").findAll(src).joinToString("\n") { it.groupValues[1] }

/**
 * Regla del barrido, extraída para poder auto-probarla: un literal presume un acelerador si nombra
 * NPU/LiteRT/GGUF/on-device Y vende ejecución, salvo que lleve negación. `\bNPU\b` porque "INPUT"
 * contiene la secuencia.
 */
private val NEGACIONES = listOf("no ejecuta", "no se ejecut", "no hay runtime", "no trae pesos", "no disponibles", "Ninguno", "ninguna ruta", "aún sin runtime", "sin acelerador", "sin runtime", "sin modelo local", "NO MEDIDO", "tampoco", "todavía falta", "no carga", "no hay modelo", "no NPU", "no dentro", "esta build no")

private fun presumeAcelerador(literal: String): Boolean {
    val acelerador = Regex("\\bNPU\\b|LiteRT|LITERART|GGUF|On-Device|on-device", RegexOption.IGNORE_CASE).containsMatchIn(literal)
    // "ejecuc" además de "ejecut": ejecución lleva c, no t. Perder esa alternativa fue el punto ciego.
    val vendeEjecucion = Regex("ejecut|ejecuc|\\bruns\\b|on-device|aceler|throttle|t[ée]rmic|eficienc|instante|sin internet|offline|passed|100 ?%", RegexOption.IGNORE_CASE).containsMatchIn(literal)
    return acelerador && vendeEjecucion && NEGACIONES.none { literal.contains(it) }
}

fun main() {
    println("=== UI-1: el feed sintético de la consola deja de fingir mediciones ===")
    val console = source("ui/components/DiagnosticConsole.kt")
    val mentiras = listOf(
        "Ruta NPU verificada",
        "(48 t/s)",
        "completada en 115ms",
        "120MB de RAM liberados",
        "CPU 24%",
        "Batería 36°C",
        "Webhook OpenClaw activo",
        "Texto de documento de prueba analizado correctamente"
    ).filter { console.contains(it) }
    check("ningún evento sintético afirma una medición ni una ruta de inferencia",
        mentiras.isEmpty(), mentiras.joinToString(", ").ifEmpty { "cero cifras inventadas" })
    val eventos = Regex("Pair\\(\"SIMULATED_").findAll(console).count()
    val honestos = Regex("\\[SIMULATION\\]\\[NO MEDIDO\\]").findAll(console).count()
    check("el chip de simulación sigue existiendo (no se elimina funcionalidad)", eventos > 0,
        "eventos=$eventos")
    check("todos los eventos se declaran NO MEDIDOS", eventos == honestos,
        "eventos=$eventos con aviso=$honestos")

    println()
    println("=== UI-2: los presets del modfile ya no se atribuyen un runtime ===")
    val modfile = source("ui/screens/ConfigModfileScreen.kt")
    val nombresFalsos = RUNTIME_FALSO.filter { literalsOf(modfile).contains(it) }
    check("la pantalla de modfile no nombra modelos que la app no ejecuta",
        nombresFalsos.isEmpty(), nombresFalsos.joinToString(", ").ifEmpty { "sin nombres de runtime" })
    val etiquetas = presetLabels()
    check("se leen los tres rótulos que la pantalla escribe realmente", etiquetas.size == 3,
        etiquetas.joinToString { it.first })
    check("cada preset conserva su propia tecla seleccionada",
        etiquetas.isNotEmpty() && etiquetas.all { classify(it.second) == it.first },
        etiquetas.joinToString { "${it.first}->${classify(it.second)}" })
    val defecto = Regex("val baseModel: String = \"([^\"]+)\"")
        .find(source("data/model/Models.kt"))?.groupValues?.get(1).orEmpty()
    check("el rótulo de fábrica cae en Standard y no finge runtime",
        classify(defecto) == "Standard" && RUNTIME_FALSO.none { defecto.contains(it) }, defecto)

    println()
    println("=== UI-3: lo que de verdad llega al runtime al elegir cada preset ===")
    val fake = UiRecordingOllama()
    val engine = ClawEngine(configStore = InMemoryOllamaConfigStore())
    engine.updateOllamaConfig(fake.baseUrl, MODEL, WEBHOOK, false)
    for ((button, label) in etiquetas) {
        engine.updateJarvisModfile("CLAW", label, "Eres CLAW.", 0.7f, 0.9f, "claw", false)
        runBlocking { engine.executeQuickActionAnalyzeCode() }
        check("el preset '$button' viaja al runtime en el snapshot",
            fake.prompts.size == etiquetas.indexOf(button to label) + 1 &&
                fake.prompts.last().contains(label),
            "peticiones=${fake.prompts.size}")
    }
    val juntos = fake.prompts.joinToString(" ")
    fake.stop()
    check("los tres presets produjeron tres turnos reales en el runtime",
        fake.prompts.size == 3, "peticiones=${fake.prompts.size}")
    check("el snapshot declara el runtime que sí existe",
        juntos.contains("Runtime Ollama") && juntos.contains(MODEL), "host + modelo presentes")
    val contaminacion = RUNTIME_FALSO.filter { juntos.contains(it) }
    check("ningún turno le declara al modelo un runtime GGUF/LiteRT inexistente",
        contaminacion.isEmpty(), contaminacion.joinToString(", ").ifEmpty { "prompt limpio" })

    println()
    println("=== UI-4: tarjetas que describían el modo de operación del motor ===")
    val disk = source("ui/screens/DiskSpaceManagerScreen.kt")
    check("ya no se afirma que el motor opera en 'LiteRT Fallback'",
        !disk.contains("modo ultraligero LiteRT Fallback") && disk.contains("runtime HTTP"),
        if (disk.contains("runtime HTTP")) "ahora dice runtime HTTP" else "falta la aclaración")
    val designer = source("ui/screens/JarvisAgentDesignerScreen.kt")
    check("el diseñador no promete provisionar ~4.2 GB de un modelo único",
        !designer.contains("4.2 GB") && !designer.contains("Qwen 3.5 LiteRT"), "sin provisión inventada")
    check("el diseñador declara que el APK no trae pesos y el runtime es configurable",
        designer.contains("no trae pesos") && designer.contains("Ollama"), "copy honesto")
    check("el botón ya no finge una descarga de 4.2 GB con ejecución NPU",
        !literalsOf(designer).contains("Descargar CLAW Standard") &&
            !literalsOf(designer).contains("ejecución local NPU") &&
            !designer.contains("4200000"), "sin descarga ni pesos inventados")
    check("el interruptor local se presenta como preferencia sin runtime, no como modo activo",
        !modfile.contains("ConfigSwitchRow(\"Procesamiento Local\"") &&
            modfile.contains("aún sin runtime local"), "etiqueta actualizada")

    println()
    println("=== UI-5: barrido global de rótulos que atribuyen ejecución a un acelerador inexistente ===")
    // Heurística declarada: se mira sólo el texto de los literales de las pantallas (no
    // identificadores), y un literal que nombre NPU/LiteRT/GGUF/on-device está bien SI carries una
    // negación ("no ejecuta", "ninguna ruta", "sin runtime", "[NO MEDIDO]"). Es invariante de fuente:
    // Compose no compila fuera del SDK, así que no hay comprobación visual.
    val directorio = listOf(
        java.io.File("../app-claw-main/app/src/main/java/com/example/clawcode"),
        java.io.File("app-claw-main/app/src/main/java/com/example/clawcode")
    ).firstOrNull { it.isDirectory } ?: error("no se encuentra el árbol de la app")
    val negaciones = listOf("no ", "No ", "Ninguno", "ninguna", "sin ", "aún sin", "NO MEDIDO", "tampoco", "todavía falta")
    val ofensores = mutableListOf<String>()
    directorio.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
        val sinComentarios = f.readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        Regex("\"([^\"]{6,400})\"").findAll(sinComentarios).forEach { m ->
            val literal = m.groupValues[1]
            // \bNPU\b: "CHAT_INPUT" y "[INPUT_REJECTED]" contienen la secuencia NPU y no son una
            // afirmación de acelerador. Y solo cuenta como presunción si además vende ejecución.
            val acelerador = Regex("\\bNPU\\b|LiteRT|LITERART|GGUF|On-Device|on-device").containsMatchIn(literal)
            val vendeEjecucion = Regex("ejecut|ejecuci|Ejecutan|\\bruns\\b|on-device|aceler|throttle|t[ée]rmic|eficienc|instante|sin internet|offline|PASSED|100 ?%").containsMatchIn(literal)
            if (presumeAcelerador(literal)) {
                ofensores += "${f.name}:${literal.take(90)}"
            }
        }
    }
    println()
    println("=== UI-6: la preferencia de procesamiento local no se presenta como un modo activo ===")
    val activity = source("MainActivity.kt")
    val panel = source("ui/screens/DashboardScreen.kt")
    check("el cabecero ya no vende 'SAFE MODE (Local)' sin runtime detrás",
        !activity.contains("\"SAFE MODE (Local)\"") && activity.contains("PREFERENCIA LOCAL SIN RUNTIME"),
        "dice lo que hay")
    check("la tarjeta del panel no anuncia 'LOCAL'/'HOST' sino el transporte real",
        !Regex("\"LOCAL\"|\"HOST\"").containsMatchIn(panel) && panel.contains("SIN RUNTIME"),
        "SIN RUNTIME / HTTP")
    check("el estilo de aviso se conserva (no se eliminó la señalización del estado)",
        activity.contains("isSafeMode"), "isSafeMode sigue gobernando el color")

    // Auto-prueba de la regla con frases que DE VERDAD estuvieron en el código: si el barrido no las
    // señala, su "0 sospechosos" no vale nada. Y no debe marcar las que ya se niegan, para que
    // corregir la mentira no consista en cegar la detección.
    val mentirasHistoricas = listOf(
        "Runs 270M FunctionGemma on-device when offline.",
        "Integración con Google AI Edge Gallery y ejecución de Gemini Nano/Gemma en LiteRT.",
        "100% NPU (Gratis)",
        "DESGLOSE DE CONSUMO & EJECUCIÓN (LITERART NPU)",
        "Eficiencia Térmica NPU:",
        "NPU LiteRT Acceleration -> PASSED (0.42s)",
        "Modelos Locales On-Device (LiteRT-LM / FunctionGemma 270M): Ejecutan acciones móviles al instante sin internet (<100ms)."
    )
    val noVistas = mentirasHistoricas.filterNot { presumeAcelerador(it) }
    check("la regla señala las siete mentiras que hubo en el código", noVistas.isEmpty(),
        if (noVistas.isEmpty()) "7 de 7 detectadas" else "no ve: ${noVistas.joinToString(" | ") { it.take(55) }}")
    val textosHonestos = listOf(
        "AICore presente en el dispositivo (esta build no ejecuta inferencia en él)",
        "[SIMULATION][NO MEDIDO] Turno sintético de prueba: no se ejecutó ninguna inferencia ni se verificó ninguna ruta NPU",
        "Para inferencia dentro del teléfono todavía falta un runtime local empaquetado: ninguna ruta de esta build carga GGUF ni LiteRT en el proceso.",
        "Preferencia: procesar en el dispositivo (aún sin runtime local)",
        "CHAT_INPUT"
    )
    val marcadosPorError = textosHonestos.filter { presumeAcelerador(it) }
    check("y no marca los textos que se niegan a sí mismos", marcadosPorError.isEmpty(),
        if (marcadosPorError.isEmpty()) "0 falsos positivos" else "marca: ${marcadosPorError.joinToString(" | ") { it.take(55) }}")

    ofensores.distinct().sorted().take(12).forEach { println("        - $it") }
    check("ningún rótulo de pantalla afirma ejecución en NPU/LiteRT/GGUF sin negarlo",
        ofensores.isEmpty(), "${ofensores.distinct().size} literales sospechosos")

    println()
    println("=== UI-7: los tres engaños que sólo se veían en pantalla ===")
    val dashboard = source("ui/screens/DashboardScreen.kt")
    check("la tarjeta GPU/NPU no fija 'ACTIVO' como valor",
        !Regex("title = \"GPU/NPU\",\\s*value = \"ACTIVO\"").containsMatchIn(dashboard) &&
            dashboard.contains("npuGpuAccelerator.startsWith(\"Ninguno\")"),
        "el valor sale del estado medido")
    check("la tarjeta de Tokens no arranca con 16384 de fábrica",
        !dashboard.contains("value = \"${'$'}{hardware.totalContextTokens}\"") && dashboard.contains("sin medir"),
        "muestra 'sin medir' hasta que el runtime lo declara")
    check("no queda la conversación de relleno del panel",
        !dashboard.contains("Revisé los logs y encontré 2 advertencias menores") &&
            !dashboard.contains("Análisis de código reciente") &&
            dashboard.contains("mensajes.lastOrNull"),
        "enseña el último intercambio real")
    val telemetria = source("data/model/Models.kt")
    check("el centinela de contexto arranca en 0, no en un número inventado",
        !Regex("totalContextTokens: Int = (?!0)").containsMatchIn(telemetria),
        Regex("totalContextTokens: Int = \\d+").find(telemetria)?.value ?: "no encontrado")
    val motor = source("engine/ClawEngine.kt")
    check("la voz se registra con su resultado, no anunciada por adelantado",
        motor.contains("[VOICE_TTS_QUEUED]") && motor.contains("[VOICE_TTS_FALLA]") &&
            !motor.contains("[VOICE_TTS_TRIGGERED]"),
        "cuando / cuando falla / sin motor")

    println()
    println(if (failures == 0) "RESULTADO UI-CAPACIDADES: 0 FAIL" else "RESULTADO UI-CAPACIDADES: $failures FAIL")
}
