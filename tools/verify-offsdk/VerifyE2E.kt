package verify

import android.content.Context
import com.example.clawcode.engine.ClawEngine
import com.example.clawcode.engine.MobileActionEngine
import kotlinx.coroutines.runBlocking

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private const val HOST = "http://127.0.0.1:11434"
private const val WEBHOOK = "https://api.openclaw.io/v1/whatsapp/webhook"

private fun lastJarvis(engine: ClawEngine) =
    engine.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text.orEmpty()

private fun logsContaining(engine: ClawEngine, needle: String) =
    engine.consoleLogs.value.filter { it.message.contains(needle) }

private fun send(engine: ClawEngine, text: String) =
    kotlinx.coroutines.runBlocking { engine.sendChatMessage(text, null, null, MobileActionEngine(Context())) }

fun main() {
    println("=== E2E-1: auto-resolución cuando el modelo configurado NO existe ===")
    runBlocking {
        val engine = ClawEngine()
        check("modelo por defecto de la app no existe en el servidor",
            engine.ollamaConfig.value.modelName == "qwen3.5:custom-claw",
            engine.ollamaConfig.value.modelName)
        // El hostUrl por defecto de la app es 10.0.2.2 (alias del emulador Android),
        // inalcanzable desde una JVM de escritorio: se apunta al mismo servidor en localhost.
        engine.updateOllamaConfig(HOST, "qwen3.5:custom-claw", WEBHOOK, false)

        val tpsBefore = engine.hardwareTelemetry.value.tokensPerSecond
        send(engine, "Cuantos planetas tiene el sistema solar? Responde en una sola frase.")

        val reply = lastJarvis(engine)
        println("    ---- RESPUESTA DEL CHAT ----")
        println("    $reply")
        println("    ----------------------------")

        check("el chat produjo respuesta JARVIS", reply.isNotBlank(), "len=${reply.length}")
        check("NO es la plantilla simulada antigua",
            !reply.contains("Comprendido. He procesado tu solicitud"), "sin plantilla")
        check("auto-resolución de modelo activada",
            logsContaining(engine, "[MODEL_AUTORESOLVED]").isNotEmpty(),
            logsContaining(engine, "[MODEL_AUTORESOLVED]").firstOrNull()?.message?.take(160) ?: "ninguno")
        check("inferencia REAL registrada en consola",
            logsContaining(engine, "Inferencia REAL vía Ollama").isNotEmpty(),
            logsContaining(engine, "Inferencia REAL vía Ollama").firstOrNull()?.message?.take(200) ?: "ninguno")
        check("ollamaConfig.isConnected = true (real, no hardcodeado)",
            engine.ollamaConfig.value.isConnected, "isConnected=${engine.ollamaConfig.value.isConnected}")
        val tpsAfter = engine.hardwareTelemetry.value.tokensPerSecond
        check("telemetría t/s sustituida por la medición real (no el 42.8 de fábrica)",
            tpsAfter != tpsBefore && tpsAfter > 0f, "antes=$tpsBefore despues=$tpsAfter")
        check("latencia real registrada (no el '140ms' inventado)",
            logsContaining(engine, "Inferencia REAL vía Ollama")
                .firstOrNull()?.message?.contains("latencia=") == true,
            logsContaining(engine, "Inferencia REAL vía Ollama").firstOrNull()?.message.orEmpty())
    }

    println()
    println("=== E2E-1b: host de fábrica (10.0.2.2) inalcanzable -> descubrimiento del runtime local ===")
    runBlocking {
        // Configuración tal y como viene la app: host del emulador + modelo inexistente.
        val store = com.example.clawcode.core.storage.InMemoryOllamaConfigStore()
        val engine = ClawEngine(configStore = store)
        check("config de fábrica intacta antes de enviar",
            engine.ollamaConfig.value.hostUrl == "http://10.0.2.2:11434" &&
                    engine.ollamaConfig.value.modelName == "qwen3.5:custom-claw",
            "${engine.ollamaConfig.value.hostUrl} | ${engine.ollamaConfig.value.modelName}")

        send(engine, "Di la palabra DESCUBIERTO y nada mas.")
        val reply = lastJarvis(engine)
        println("    ---- RESPUESTA SIN CONFIGURAR NADA ----")
        println("    $reply")

        check("descubrimiento de host activado",
            logsContaining(engine, "[HOST_AUTORESOLVED]").isNotEmpty(),
            logsContaining(engine, "[HOST_AUTORESOLVED]").firstOrNull()?.message?.take(180) ?: "ninguno")
        check("el chat responde con inferencia REAL usando la config de fábrica",
            reply.isNotBlank() && !reply.contains("Comprendido. He procesado tu solicitud"), "len=${reply.length}")
        check("inferencia REAL registrada",
            logsContaining(engine, "Inferencia REAL vía Ollama").isNotEmpty(),
            logsContaining(engine, "Inferencia REAL vía Ollama").firstOrNull()?.message?.take(190) ?: "ninguno")
        check("host descubierto adoptado en la config",
            engine.ollamaConfig.value.hostUrl == "http://127.0.0.1:11434",
            engine.ollamaConfig.value.hostUrl)
        check("modelo real adoptado en la config",
            engine.ollamaConfig.value.modelName == "qwen2.5:0.5b",
            engine.ollamaConfig.value.modelName)
        check("descubrimiento persistido (no se repite en cada mensaje)",
            store.load()?.hostUrl == "http://127.0.0.1:11434", "${store.load()?.hostUrl}")

        send(engine, "Di la palabra SEGUNDA y nada mas.")
        check("el segundo mensaje ya no vuelve a descubrir",
            logsContaining(engine, "[HOST_AUTORESOLVED]").size == 1,
            "veces=${logsContaining(engine, "[HOST_AUTORESOLVED]").size}")
    }

    println()
    println("=== E2E-1c: host elegido por el usuario nunca se sobrescribe ===")
    runBlocking {
        val engine = ClawEngine()
        engine.updateOllamaConfig("http://127.0.0.1:11499", "qwen2.5:0.5b", WEBHOOK, false)
        send(engine, "Hola")
        val reply = lastJarvis(engine)
        check("no se descubre otro host cuando el usuario fijó uno",
            logsContaining(engine, "[HOST_AUTORESOLVED]").isEmpty(), "ninguno")
        check("se conserva el error del host del usuario",
            reply.contains("11499") && reply.contains("[Runtime local sin respuesta]"),
            reply.lineSequence().lastOrNull { it.contains("Runtime local") }?.take(140).orEmpty())
        check("la config del usuario queda intacta",
            engine.ollamaConfig.value.hostUrl == "http://127.0.0.1:11499",
            engine.ollamaConfig.value.hostUrl)
    }

    println()
    println("=== E2E-2: sendChatMessage() con modelo local explícito ===")
    runBlocking {
        val engine = ClawEngine()
        engine.updateOllamaConfig(HOST, "qwen2.5:0.5b", WEBHOOK, false)

        send(engine, "Di la palabra MARMOTA y nada mas.")
        val reply = lastJarvis(engine)
        println("    ---- RESPUESTA DEL CHAT ----")
        println("    $reply")
        check("respuesta real del modelo", reply.isNotBlank(), "len=${reply.length}")
        check("sin plantilla simulada", !reply.contains("Comprendido. He procesado tu solicitud"), "limpio")
        check("sin auto-resolución (modelo válido)", logsContaining(engine, "[MODEL_AUTORESOLVED]").isEmpty(), "ninguno")

        println("    ---- LOG MODEL_INFERENCE ----")
        logsContaining(engine, "[MODEL_RESPONSE_GENERATED]").forEach { println("    ${it.message}") }
    }

    println()
    println("=== E2E-3: el historial multi-turno llega al runtime real ===")
    runBlocking {
        val engine = ClawEngine()
        engine.updateOllamaConfig(HOST, "qwen2.5:0.5b", WEBHOOK, false)

        send(engine, "Memoriza este codigo: KIWI-7788. Confirma en una palabra.")
        send(engine, "Cual era el codigo que te dije? Responde solo con el codigo.")
        val second = lastJarvis(engine)
        println("    ---- SEGUNDA RESPUESTA ----")
        println("    $second")

        // No se afirma que qwen2.5:0.5b recuerde el código: un modelo de 0.5B alucina a menudo y
        // eso mide la calidad del modelo, no el puente. Lo que el código de la app controla, y que
        // se puede comprobar sin azar, es que ambos turnos hicieron inferencia real y que el segundo
        // request llevaba el historial dentro — Ollama lo refleja en prompt_eval_count.
        val generated = logsContaining(engine, "[MODEL_RESPONSE_GENERATED]")
        check("los dos turnos hicieron inferencia REAL", generated.size == 2, "generados=${generated.size}")

        val promptTokens = generated.map {
            Regex("tokens=(\\d+) entrada").find(it.message)?.groupValues?.get(1)?.toInt() ?: -1
        }
        check("el segundo request llevó el historial (más tokens de entrada que el primero)",
            promptTokens.size == 2 && promptTokens[1] > promptTokens[0] && promptTokens.none { it < 0 },
            "entrada turno1=${promptTokens.getOrElse(0) { "?" }} turno2=${promptTokens.getOrElse(1) { "?" }}")

        check("mensajes de usuario preservados en el estado",
            engine.chatMessages.value.count { it.sender == "USER" } == 2,
            "user msgs=${engine.chatMessages.value.count { it.sender == "USER" }}")

        println("    (informativo) recuerdo del modelo: " +
                if (second.contains("KIWI", ignoreCase = true)) "ACIERTA"
                else "no acierta — calidad del modelo de 0.5B, no del puente")
    }

    println()
    println("=== E2E-4: runtime caído -> reserva estática (no se pierde funcionalidad) ===")
    runBlocking {
        val engine = ClawEngine()
        engine.updateOllamaConfig("http://127.0.0.1:11499", "qwen2.5:0.5b", WEBHOOK, false)

        send(engine, "Hola Jarvis, todo bien?")
        val reply = lastJarvis(engine)
        println("    ---- RESPUESTA DEL CHAT ----")
        println("    ${reply.take(400)}")
        check("no hay crash", true, "sendChatMessage retornó")
        check("conserva una reserva estática cuando el runtime cae",
            reply.contains("Comprendido.") && reply.contains("Hola Jarvis"), "fallback presente")
        check("la reserva ya no finge haber procesado el texto en NPU/LiteRT",
            !reply.contains("He procesado") && reply.contains("NO se procesó con ningún modelo"),
            reply.take(160))
        check("avisa del fallo real del runtime",
            reply.contains("[Runtime local sin respuesta]"), "aviso presente")
        check("isConnected = false (veraz)", !engine.ollamaConfig.value.isConnected,
            "isConnected=${engine.ollamaConfig.value.isConnected}")
        check("log de fallback en consola",
            logsContaining(engine, "[MODEL_INFERENCE_FALLBACK]").isNotEmpty(), "presente")
    }

    println()
    println("=== E2E-5: intents de acción del SO siguen teniendo prioridad (regresión) ===")
    runBlocking {
        val engine = ClawEngine()
        engine.updateOllamaConfig(HOST, "qwen2.5:0.5b", WEBHOOK, false)
        val before = engine.chatMessages.value.size
        send(engine, "Configura la herramienta Google_AI_Edge_Gallery_Bridge para que use el modelo DeepSeek-R1-Distill-Qwen-1.5B")
        check("baseModel reconfigurado", engine.jarvisModfile.value.baseModel == "DeepSeek-R1-Distill-Qwen-1.5B",
            engine.jarvisModfile.value.baseModel)
        check("no se llamó al modelo en la ruta de acción",
            logsContaining(engine, "Inferencia REAL vía Ollama").isEmpty(), "sin inferencia")
        check("mensajes añadidos", engine.chatMessages.value.size == before + 2,
            "delta=${engine.chatMessages.value.size - before}")
    }

    println()
    if (failures == 0) println("RESULTADO E2E: TODAS LAS PRUEBAS PASARON")
    else println("RESULTADO E2E: $failures PRUEBA(S) FALLARON")
    if (failures != 0) kotlin.system.exitProcess(1)
}
