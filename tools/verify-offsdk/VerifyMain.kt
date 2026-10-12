package verify

import com.example.clawcode.engine.OllamaChatMessage
import com.example.clawcode.engine.OllamaChatRequest
import com.example.clawcode.engine.OllamaClient
import kotlinx.coroutines.runBlocking

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) {
        println("  PASS  $name -> $detail")
    } else {
        failures++
        println("  FAIL  $name -> $detail")
    }
}

// Valores idénticos a los que usa la app por defecto (Models.kt: OllamaConfig / JarvisModfile)
private const val HOST = "http://127.0.0.1:11434"
private const val DEFAULT_MODEL = "qwen3.5:custom-claw"
private const val REAL_LOCAL_MODEL = "qwen2.5:0.5b"
private const val THINKING_MODEL = "qwen3.5:0.8B"
private val SYSTEM_PROMPT = """
    You are JARVIS, an autonomous AI agent running locally on Android using the unified CLAW Standard engine.
    Responde siempre en espanol y en una sola frase corta.
""".trimIndent()
private val STOP_TOKENS = listOf("<|im_end|>", "</s>", "[END]")

private fun request(model: String, userText: String, history: List<OllamaChatMessage> = emptyList()) =
    OllamaChatRequest(
        hostUrl = HOST,
        model = model,
        systemPrompt = SYSTEM_PROMPT,
        history = history,
        userText = userText,
        temperature = 0.7f,
        topP = 0.9f,
        stopTokens = STOP_TOKENS,
        maxTokens = 256
    )

fun main() = runBlocking {
    val client = OllamaClient()

    println("=== TEST 1: inferencia real contra modelo local ===")
    val r1 = client.chat(request(REAL_LOCAL_MODEL, "Presentate en una frase."))
    val ok1 = r1.isSuccess
    val reply1 = r1.getOrNull()
    check("chat() exitoso", ok1, if (ok1) "HTTP OK" else "error=${r1.exceptionOrNull()?.message}")
    if (reply1 != null) {
        println("        texto   : ${reply1.text}")
        println("        modelo  : ${reply1.model}")
        println("        t/s     : ${reply1.tokensPerSecond}")
        println("        latencia: ${reply1.latencyMs} ms")
        println("        tokens  : ${reply1.promptTokens} in / ${reply1.completionTokens} out (stop=${reply1.stopReason})")
        check("texto no vacío y no es plantilla", reply1.text.isNotBlank(), "len=${reply1.text.length}")
        check("tokens de salida reales > 0", reply1.completionTokens > 0, "eval_count=${reply1.completionTokens}")
        check("t/s medido real > 0", reply1.tokensPerSecond > 0f, "tps=${reply1.tokensPerSecond}")
        check("modelo reportado por el servidor", reply1.model == REAL_LOCAL_MODEL, reply1.model)
        check("no contiene la plantilla simulada", !reply1.text.contains("Comprendido. He procesado tu solicitud"), "texto limpio")
    }

    println()
    println("=== TEST 2: multi-turno (system + historial) ===")
    val history = listOf(
        OllamaChatMessage("user", "Recuerda: mi clave de prueba es ZEBRA-42."),
        OllamaChatMessage("assistant", "Entendido, guardare ZEBRA-42.")
    )
    val r2 = client.chat(request(REAL_LOCAL_MODEL, "Cual era mi clave de prueba? Responde solo con la clave.", history))
    val reply2 = r2.getOrNull()
    check("historial enviado y procesado", r2.isSuccess && reply2 != null, reply2?.text ?: "error=${r2.exceptionOrNull()?.message}")
    if (reply2 != null) {
        println("        texto: ${reply2.text}")
        check("el modelo recuerda el contexto", reply2.text.contains("ZEBRA", ignoreCase = true), reply2.text)
    }

    println()
    println("=== TEST 3: modelo thinking -> think:false evita content vacío ===")
    val r3 = client.chat(request(THINKING_MODEL, "Di hola en exactamente 5 palabras."))
    val reply3 = r3.getOrNull()
    check("modelo thinking responde con contenido", r3.isSuccess && !reply3?.text.isNullOrBlank(),
        reply3?.text ?: "error=${r3.exceptionOrNull()?.message}")
    if (reply3 != null) println("        texto: ${reply3.text} (t/s=${reply3.tokensPerSecond})")

    println()
    println("=== TEST 4: modelo por defecto de la app NO existe -> fallo limpio ===")
    val r4 = client.chat(request(DEFAULT_MODEL, "hola"))
    val msg4 = r4.exceptionOrNull()?.message.orEmpty()
    check("chat() falla sin excepción no controlada", r4.isFailure, "isFailure=${r4.isFailure}")
    check("el error menciona 'not found'", msg4.contains("not found", ignoreCase = true), msg4)

    println()
    println("=== TEST 5: listCompletionModels + auto-resolución ===")
    val r5 = client.listCompletionModels(HOST)
    val models = r5.getOrNull().orEmpty()
    check("/api/tags parseado", r5.isSuccess && models.isNotEmpty(), "count=${models.size}")
    val localModels = models.filter { !it.isCloudStub }
    check("stubs :cloud marcados correctamente", localModels.size < models.size,
        "locales=${localModels.size} de ${models.size}")
    check("ordenados por tamaño ascendente", models.map { it.sizeBytes } == models.map { it.sizeBytes }.sorted(),
        "primero=${models.firstOrNull()?.name}")
    val resolved = localModels.firstOrNull()?.name
    println("        auto-resuelto -> $resolved")
    check("auto-resolución elige un modelo local real", resolved != null, "$resolved")

    println()
    println("=== TEST 6: reintento con el modelo auto-resuelto ===")
    if (resolved != null) {
        val r6 = client.chat(request(resolved, "Confirma que eres un modelo real en una frase."))
        val reply6 = r6.getOrNull()
        check("inferencia real con modelo auto-resuelto", r6.isSuccess && !reply6?.text.isNullOrBlank(),
            reply6?.text ?: "error=${r6.exceptionOrNull()?.message}")
        if (reply6 != null) println("        modelo=${reply6.model} t/s=${reply6.tokensPerSecond} lat=${reply6.latencyMs}ms")
    }

    println()
    println("=== TEST 7: servidor inalcanzable -> fallo limpio, no crash ===")
    val badClient = OllamaClient(connectTimeoutSeconds = 3, readTimeoutSeconds = 5)
    val r7 = badClient.chat(
        request(REAL_LOCAL_MODEL, "hola").copy(hostUrl = "http://127.0.0.1:11499")
    )
    val msg7 = r7.exceptionOrNull()?.message.orEmpty()
    check("no lanza excepción al llamador", r7.isFailure, "isFailure=${r7.isFailure}")
    check("mensaje de error descriptivo", msg7.isNotBlank(), msg7.take(140))
    val r8 = badClient.listCompletionModels("http://127.0.0.1:11499")
    check("listCompletionModels también falla limpio", r8.isFailure, "isFailure=${r8.isFailure}")

    println()
    println("=== TEST 8: hostUrl con barra final y espacios ===")
    val r9 = client.chat(request(REAL_LOCAL_MODEL, "Di OK.").copy(hostUrl = "  $HOST/  "))
    check("normaliza la URL del host", r9.isSuccess, r9.getOrNull()?.text ?: r9.exceptionOrNull()?.message.orEmpty())

    println()
    if (failures == 0) {
        println("RESULTADO: TODAS LAS PRUEBAS PASARON")
    } else {
        println("RESULTADO: $failures PRUEBA(S) FALLARON")
    }
    if (failures != 0) kotlin.system.exitProcess(1)
}
