package verify

import android.content.Context
import com.example.clawcode.engine.ClawEngine
import com.example.clawcode.engine.MobileActionEngine
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

private val json = Json { ignoreUnknownKeys = true }

/** Servidor Ollama de pega: graba cada /api/chat recibido para poder auditar el payload real. */
private class FakeOllama {
    val captured = CopyOnWriteArrayList<String>()

    val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/api/chat") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            captured += body
            respond(exchange, CHAT_REPLY)
        }
        createContext("/api/tags") { exchange -> respond(exchange, TAGS_REPLY) }
        createContext("/api/ps") { exchange -> respond(exchange, PS_REPLY) }
        start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    fun stop() = server.stop(0)

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val CHAT_REPLY = """{"model":"qwen2.5:0.5b","message":{"role":"assistant","content":"RECIBIDO"},"done":true,"done_reason":"stop","prompt_eval_count":12,"prompt_eval_duration":100000000,"eval_count":5,"eval_duration":250000000}"""
        const val TAGS_REPLY = """{"models":[{"name":"qwen2.5:0.5b","size":397458432,"details":{"quantization_level":"Q4_K_M","context_length":32768}}]}"""
        const val PS_REPLY = """{"models":[]}"""
    }
}

private fun messagesOf(payload: String): List<Pair<String, String>> =
    (json.parseToJsonElement(payload).jsonObject["messages"]?.jsonArray ?: return emptyList())
        .map { it.jsonObject }
        .map { obj: JsonObject ->
            obj["role"]?.jsonPrimitive?.content.orEmpty() to obj["content"]?.jsonPrimitive?.content.orEmpty()
        }

private fun send(engine: ClawEngine, text: String) =
    runBlocking { engine.sendChatMessage(text, null, null, MobileActionEngine(Context())) }

fun main() {
    val fake = FakeOllama()
    println("=== PAYLOAD-1: auditoría del JSON que el chat envía al runtime ===")
    println("    servidor de pega en ${fake.baseUrl}")

    val engine = ClawEngine()
    engine.updateOllamaConfig(fake.baseUrl, "qwen2.5:0.5b", "https://api.openclaw.io/v1/whatsapp/webhook", false)

    send(engine, "Primer mensaje: memoriza KIWI-7788.")
    send(engine, "Segundo mensaje: cual era el codigo?")

    check("el servidor recibió las dos peticiones", fake.captured.size == 2, "capturados=${fake.captured.size}")

    val second = messagesOf(fake.captured[1])
    println("    ---- mensajes del 2º request ----")
    second.forEach { (role, content) -> println("      [$role] ${content.take(70)}") }

    val userTurns = second.filter { it.first == "user" }
    val currentPromptCount = second.count { it.first == "user" && it.second == "Segundo mensaje: cual era el codigo?" }

    check("el turno actual NO se envía duplicado", currentPromptCount == 1,
        "veces=$currentPromptCount (el bug lo enviaba 2: historial + userText)")
    check("no hay dos turnos 'user' consecutivos",
        second.map { it.first }.joinToString(",").let { seq ->
            !Regex("user,user").containsMatchIn(seq.replace("system,", ""))
        }, second.map { it.first }.joinToString(","))
    check("el turno anterior va como historial 'user'",
        userTurns.any { it.second == "Primer mensaje: memoriza KIWI-7788." },
        "userTurns=${userTurns.size}")
    check("la respuesta del asistente anterior va en el historial",
        second.any { it.first == "assistant" && it.second == "RECIBIDO" }, "assistant presente")
    check("el system prompt del Modfile abre la conversación",
        second.firstOrNull()?.first == "system" && second.first().second.isNotBlank(),
        "len=${second.firstOrNull()?.second?.length}")
    check("el prompt del sistema no se repite", second.count { it.first == "system" } == 1,
        "system=${second.count { it.first == "system" }}")

    println()
    println("=== PAYLOAD-2: flags de la petición ===")
    val raw = json.parseToJsonElement(fake.captured[1]).jsonObject
    check("stream=true en el chat (los tokens se muestran mientras se generan)",
        raw["stream"]?.jsonPrimitive?.content == "true", "${raw["stream"]?.jsonPrimitive?.content}")
    check("think=false (evita el contenido vacío de los modelos de razonamiento)",
        raw["think"]?.jsonPrimitive?.content == "false", "${raw["think"]?.jsonPrimitive?.content}")
    check("el modelo pedido es el configurado",
        raw["model"]?.jsonPrimitive?.content == "qwen2.5:0.5b", "${raw["model"]?.jsonPrimitive?.content}")
    val options = raw["options"]?.jsonObject
    check("num_predict limita la generación", (options?.get("num_predict")?.jsonPrimitive?.content ?: "0").toInt() > 0,
        "${options?.get("num_predict")?.jsonPrimitive?.content}")

    println()
    println("=== PAYLOAD-3: la respuesta del servidor llega intacta al chat ===")
    val lastJarvis = engine.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text.orEmpty()
    check("el chat muestra lo que generó el modelo", lastJarvis.contains("RECIBIDO"), lastJarvis.take(80))
    val tps = engine.hardwareTelemetry.value.tokensPerSecond
    check("los t/s salen de eval_count/eval_duration del servidor", tps == 20.0f, "tps=$tps (5 tok / 0.25 s)")

    println()
    println("=== PAYLOAD-4: el system prompt no manda fingir un motor inexistente ===")
    val sys = messagesOf(fake.captured.last()).firstOrNull { it.first == "system" }?.second.orEmpty()
    println("    ---- system ----\n    ${sys.replace("\n", "\n    ")}")
    check("sigue habiendo un turno system que el modelo obeyece", sys.isNotBlank(), "len=${sys.length}")
    check("ya no se describe como agente que corre localmente con un motor CLAW",
        !sys.contains("running locally on Android using the unified CLAW Standard engine"), sys.take(90))
    check("declara que la app no trae pesos embebidos",
        sys.contains("embeds no model weights"), "honestidad en el prompt")
    check("prohíbe inventarse una respuesta on-device si el runtime no responde",
        sys.contains("inventing an on-device answer"), "instrucción de honestidad")
    check("conserva la orden de ejecutar acciones del SO",
        sys.contains("Mobile Action") && sys.contains("open apps"), "capacidad real intacta")
    check("el modfile ya no anuncia '4-bit LiteRT'",
        !sys.contains("4-bit LiteRT") && !engine.jarvisModfile.value.baseModel.contains("LiteRT"),
        engine.jarvisModfile.value.baseModel)

    fake.stop()

    println()
    if (failures == 0) println("RESULTADO PAYLOAD: TODAS LAS PRUEBAS PASARON")
    else println("RESULTADO PAYLOAD: $failures PRUEBA(S) FALLARON")
    if (failures != 0) kotlin.system.exitProcess(1)
}
