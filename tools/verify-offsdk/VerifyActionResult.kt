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
 * Cuando una acción del SO tenía éxito, `sendChatMessage()` respondía con una plantilla fija
 * ("Acción ejecutada en el dispositivo: ...") y el turno NUNCA llegaba al modelo: era el último
 * tramo del chat que seguía siendo simulación. Se comprueba que ahora el hecho medido se conserva
 * como cabecera, que el turno se manda al runtime, y que si el runtime cae la acción no se pierde.
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private const val WEBHOOK = "https://api.openclaw.io/v1/whatsapp/webhook"
private const val MODEL = "modelodepega:1"

private class RecordingOllama {
    val prompts = Collections.synchronizedList(mutableListOf<String>())

    val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/api/chat") { exchange ->
            prompts += exchange.requestBody.readBytes().decodeToString()
            respond(exchange, """{"model":"modelodepega:1","message":{"role":"assistant","content":"LISTO-DESDE-EL-MODELO"},"done":true,"done_reason":"stop","prompt_eval_count":60,"prompt_eval_duration":100000000,"eval_count":4,"eval_duration":200000000}""")
        }
        createContext("/api/tags") { exchange ->
            respond(exchange, """{"models":[{"name":"modelodepega:1","size":1200000000,"details":{"quantization_level":"Q4_K_M"},"capabilities":["completion"]}]}""")
        }
        createContext("/api/ps") { exchange -> respond(exchange, """{"models":[]}""") }
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

private fun engineFor(host: String): ClawEngine {
    val engine = ClawEngine(configStore = InMemoryOllamaConfigStore())
    engine.updateOllamaConfig(host, MODEL, WEBHOOK, false)
    return engine
}

private fun jarvisTurn(engine: ClawEngine) =
    engine.chatMessages.value.lastOrNull { it.sender == "JARVIS" }

fun main() {
    println("=== AR-1: acción del SO exitosa -> el modelo SÍ ve el turno ===")
    val fake = RecordingOllama()
    val engine = engineFor(fake.baseUrl)
    runBlocking {
        engine.sendChatMessage("EXECUTA: calendario", null, null, MobileActionEngine(Context()))
    }

    check("el turno con acción llega al runtime (1 petición real)", fake.prompts.size == 1,
        "peticiones=${fake.prompts.size}")
    val prompt = fake.prompts.firstOrNull().orEmpty()
    check("el prompt declara la acción ya ejecutada", prompt.contains("Acción del sistema ya ejecutada: abrir_app"),
        prompt.take(120))
    check("el prompt lleva el resultado MEDIDO del sistema",
        prompt.contains("Intent resuelta: abrir_app") && prompt.contains("Resultado devuelto por el sistema"),
        prompt.take(120))

    val reply = jarvisTurn(engine)?.text.orEmpty()
    check("la cabecera con el hecho medido se conserva (no se pierde funcionalidad)",
        reply.startsWith("Acción ejecutada en el dispositivo: 'abrir_app'"), reply.take(120))
    check("debajo de la cabecera va la respuesta real del modelo",
        reply.contains("LISTO-DESDE-EL-MODELO"), reply.take(200))
    val meta = jarvisTurn(engine)
    check("el mensaje sigue marcado como acción ejecutada",
        meta?.isActionExecuted == true && meta.actionExecutedName == "abrir_app",
        "accion=${meta?.actionExecutedName}")
    val console = engine.consoleLogs.value.joinToString(" ") { it.message }
    check("la consola etiqueta el turno como acción, no como texto inventado",
        console.contains("Turno con acción 'abrir_app'") &&
                console.contains("[MODEL_RESPONSE_GENERATED]"), "chars=${console.length}")

    fake.stop()

    println()
    println("=== AR-2: runtime caído -> la acción del SO no se pierde y no se finge análisis ===")
    val dead = engineFor("http://127.0.0.1:11499")
    runBlocking {
        dead.sendChatMessage("EXECUTA: linterna", null, null, MobileActionEngine(Context()))
    }
    val deadReply = jarvisTurn(dead)?.text.orEmpty()
    check("el resultado de la acción sigue siendo la primera línea",
        deadReply.startsWith("Acción ejecutada en el dispositivo: 'abrir_app'"), deadReply.take(120))
    check("queda explícito que el runtime no respondió",
        deadReply.contains("[Runtime local sin respuesta]"), deadReply.take(200))
    check("no se inventa una conclusión de IA", !deadReply.contains("LISTO-DESDE-EL-MODELO"),
        "sin texto del modelo")
    check("el fallo se registra en consola",
        dead.consoleLogs.value.any { it.message.contains("[MODEL_INFERENCE_FALLBACK]") }, "fallback loggado")

    println()
    println("=== AR-3: un turno sin acción del SO sigue yendo directo al modelo ===")
    val plain = RecordingOllama()
    val plainEngine = engineFor(plain.baseUrl)
    runBlocking {
        plainEngine.sendChatMessage("explícame qué eres", null, null, MobileActionEngine(Context()))
    }
    val plainReply = jarvisTurn(plainEngine)?.text.orEmpty()
    check("no aparece la plantilla de acción", !plainReply.contains("Acción ejecutada en el dispositivo"),
        plainReply.take(120))
    check("la respuesta es la del modelo", plainReply.contains("LISTO-DESDE-EL-MODELO"), plainReply.take(80))
    check("el turno sin acción hace exactamente una petición", plain.prompts.size == 1,
        "peticiones=${plain.prompts.size}")
    plain.stop()

    println()
    println("=== AR-4: el chat ya no afirma que un puente MCP reconfigurado existe ===")
    val bridgeFake = RecordingOllama()
    val bridgeEngine = engineFor(bridgeFake.baseUrl)
    runBlocking {
        bridgeEngine.sendChatMessage(
            "Configura la herramienta Google_AI_Edge_Gallery_Bridge para que use el modelo DeepSeek-R1-Distill-Qwen-1.5B",
            null, null, MobileActionEngine(Context())
        )
    }
    val bridgeReply = jarvisTurn(bridgeEngine)?.text.orEmpty()
    check("la orden sigue produciendo su efecto real (baseModel cambió)",
        bridgeEngine.jarvisModfile.value.baseModel == "DeepSeek-R1-Distill-Qwen-1.5B",
        bridgeEngine.jarvisModfile.value.baseModel)
    check("el turno ALSO llega al modelo", bridgeFake.prompts.size == 1 &&
            bridgeReply.contains("LISTO-DESDE-EL-MODELO"), "peticiones=${bridgeFake.prompts.size}")
    check("ya no grita '¡Configuración actualizada!' como si el puente existiera",
        !bridgeReply.contains("¡Configuración actualizada!"), bridgeReply.take(90))
    check("aclara que no hay puente MCP y que sólo cambió una etiqueta",
        bridgeReply.contains("no abre ningún puente MCP") && bridgeReply.contains("sólo una etiqueta"),
        bridgeReply.take(160))
    bridgeFake.stop()

    val mcpDead = engineFor("http://127.0.0.1:11499")
    runBlocking { mcpDead.sendChatMessage("¿qué es MCP?", null, null, MobileActionEngine(Context())) }
    val mcpFallback = jarvisTurn(mcpDead)?.text.orEmpty()
    check("la definición de reserva se declara texto estático de la app",
        mcpFallback.contains("texto estático de la app"), mcpFallback.take(150))
    check("ya no presume 'ahora estoy utilizando' un puente configurado",
        !mcpFallback.contains("A través del puente configurado, ahora estoy utilizando"),
        mcpFallback.take(150))
    check("nombra el runtime real que falló",
        mcpFallback.contains(MODEL) && mcpFallback.contains("[Runtime local sin respuesta]"),
        mcpFallback.take(150))

    val mcpLiveFake = RecordingOllama()
    val mcpLive = engineFor(mcpLiveFake.baseUrl)
    runBlocking { mcpLive.sendChatMessage("¿qué es MCP?", null, null, MobileActionEngine(Context())) }
    val mcpLiveReply = jarvisTurn(mcpLive)?.text.orEmpty()
    check("con el runtime vivo responde el modelo, no la definición escrita a mano",
        mcpLiveReply.contains("LISTO-DESDE-EL-MODELO") &&
                !mcpLiveReply.contains("Esta definición la está devolviendo texto estático"),
        mcpLiveReply.take(120))
    mcpLiveFake.stop()

    println()
    println("=== AR-5: MobileActionEngine ya no informa éxito simulado (invariante sobre la fuente) ===")
    val candidatePaths = listOf(
        "../app-claw-main/app/src/main/java/com/example/clawcode/engine/MobileActionEngine.kt",
        "app-claw-main/app/src/main/java/com/example/clawcode/engine/MobileActionEngine.kt"
    )
    val engineFile = candidatePaths.map { java.io.File(it) }.firstOrNull { it.isFile }
    check("se puede leer el fichero real de la app", engineFile != null, candidatePaths.joinToString(" "))
    if (engineFile != null) {
        val src = engineFile.readText()
        // Se comprueba sólo el código: los comentarios explican qué se quitó y por eso citan el
        // texto inventado que ya no aparece en ninguna rama ejecutable.
        val codeOnly = src.lines().joinToString("\n") { it.substringBefore("//") }
        val elseBranch = run {
            val from = codeOnly.indexOf("else -> {")
            if (from < 0) "" else codeOnly.substring(from, minOf(from + 700, codeOnly.length))
        }
        check("el fallback sin Intent ya no finge una ejecución",
            !codeOnly.contains("Simulated execution") && !elseBranch.contains("Pair(true,"),
            "else-> ${elseBranch.take(60)}")
        check("el fallback devuelve false y lo dice en el mensaje",
            elseBranch.contains("Pair(") && elseBranch.contains("false,") &&
                codeOnly.contains("no se ejecutó nada en el dispositivo"), elseBranch.take(220))
        check("OCR no afirma un motor activo sin imagen procesada",
            !codeOnly.contains("OCR Vision text engine active"), "sin resultados inventados")
    }

    println()
    println("=== AR-6: la demo de WhatsApp no anuncia envíos ni modelos inexistentes ===")
    val waFake = RecordingOllama()
    val waEngine = engineFor(waFake.baseUrl)
    check("la bandeja empieza vacía (sin conversaciones de prueba)",
        waEngine.whatsAppMessages.value.isEmpty(), "mensajes=${waEngine.whatsAppMessages.value.size}")

    runBlocking { waEngine.simulateWhatsAppIncoming("Ana", "EXECUTA: linterna", MobileActionEngine(Context())) }
    val waMsg = waEngine.whatsAppMessages.value.firstOrNull()
    val waResponse = waMsg?.responseSent.orEmpty()
    check("el mensaje entrante consultó al runtime real", waFake.prompts.size == 1,
        "peticiones=${waFake.prompts.size}")
    check("la demo no afirma que algo salió por WhatsApp",
        waResponse.startsWith("NO ENVIADA") && !waResponse.contains("Respuesta enviada"),
        waResponse.take(170))
    check("no invoca un modelo que esta build no tiene",
        !waResponse.contains("Qwen 3.5") && !waResponse.contains("LiteRT"), waResponse.take(170))
    check("lo que se muestra es la respuesta del modelo configurado",
        waResponse.contains("LISTO-DESDE-EL-MODELO") && waResponse.contains("modelodepega:1"),
        waResponse.take(200))
    check("la acción del SO sólo figura si se ejecutó",
        waMsg?.actionTriggered == "abrir_app" &&
                waEngine.consoleLogs.value.any {
                    it.message.contains("[WHATSAPP_DEMO_LOCAL]") && it.message.contains("Ningún payload salió")
                },
        waEngine.consoleLogs.value.last().message.take(180))
    waFake.stop()

    val waDead = engineFor("http://127.0.0.1:11499")
    runBlocking { waDead.simulateWhatsAppIncoming("Luis", "hola", MobileActionEngine(Context())) }
    val waDeadResponse = waDead.whatsAppMessages.value.firstOrNull()?.responseSent.orEmpty()
    check("sin runtime la demo lo dice en lugar de fingir el envío",
        waDeadResponse.startsWith("NO ENVIADA: sin runtime") &&
                !waDeadResponse.contains("LISTO-DESDE-EL-MODELO"), waDeadResponse.take(170))
    check("ninguna bandeja muestra teléfonos reales inventados",
        (waEngine.whatsAppMessages.value + waDead.whatsAppMessages.value).none { it.senderPhone.contains("+57") },
        (waEngine.whatsAppMessages.value + waDead.whatsAppMessages.value).joinToString { it.senderPhone })

    println()
    println(if (failures == 0) "RESULTADO ACCION-TURNO: 0 FAIL" else "RESULTADO ACCION-TURNO: $failures FAIL")
}
