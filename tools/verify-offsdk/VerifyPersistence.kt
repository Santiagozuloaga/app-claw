package verify

import android.content.Context
import android.content.FakePrefsDisk
import com.example.clawcode.core.storage.InMemoryOllamaConfigStore
import com.example.clawcode.core.storage.PrefsOllamaConfigStore
import com.example.clawcode.data.model.OllamaConfig
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
private const val PREFS_NAME = "claw_ollama_config"

private fun lastJarvis(engine: ClawEngine) =
    engine.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text.orEmpty()

private fun logsContaining(engine: ClawEngine, needle: String) =
    engine.consoleLogs.value.filter { it.message.contains(needle) }

private fun send(engine: ClawEngine, text: String) =
    runBlocking { engine.sendChatMessage(text, null, null, MobileActionEngine(Context())) }

/** Equivale a matar y relanzar la app: nuevo Context, nuevo store, mismo "disco". */
private fun engineAfterRestart(): ClawEngine =
    ClawEngine(configStore = PrefsOllamaConfigStore(Context()))

fun main() {
    println("=== PERS-1: sin datos guardados -> valores de fábrica, load() no inventa nada ===")
    FakePrefsDisk.reset()
    run {
        val store = PrefsOllamaConfigStore(Context())
        val loaded = store.load()
        check("load() devuelve null en una instalación limpia", loaded == null, "load()=$loaded")

        val engine = ClawEngine(configStore = store)
        check("el motor arranca con los valores por defecto",
            engine.ollamaConfig.value.hostUrl == "http://10.0.2.2:11434" &&
                    engine.ollamaConfig.value.modelName == "qwen3.5:custom-claw",
            "${engine.ollamaConfig.value.hostUrl} | ${engine.ollamaConfig.value.modelName}")
    }

    println()
    println("=== PERS-2: updateOllamaConfig() escribe en disco y se recupera íntegro ===")
    run {
        val engine = ClawEngine(configStore = PrefsOllamaConfigStore(Context()))
        engine.updateOllamaConfig(HOST, "qwen2.5:0.5b", WEBHOOK, false)

        val onDisk = FakePrefsDisk.snapshot(PREFS_NAME)
        println("    ---- CONTENIDO PERSISTIDO ----")
        onDisk.forEach { (k, v) -> println("    $k = $v") }
        check("host guardado", onDisk["host_url"] == HOST, "${onDisk["host_url"]}")
        check("modelo guardado", onDisk["model_name"] == "qwen2.5:0.5b", "${onDisk["model_name"]}")
        check("webhook guardado", onDisk["openclaw_webhook_url"] == WEBHOOK, "${onDisk["openclaw_webhook_url"]}")
        check("flag local guardado", onDisk["use_on_device_litert_fallback"] == false,
            "${onDisk["use_on_device_litert_fallback"]}")
        check("isConnected NO se persiste (se mide en runtime)",
            !onDisk.containsKey("isConnected") && !onDisk.containsKey("is_connected"),
            "claves=${onDisk.keys}")

        val reread = PrefsOllamaConfigStore(Context()).load()
        check("relectura idéntica a lo guardado",
            reread == OllamaConfig(HOST, "qwen2.5:0.5b", WEBHOOK, false, isConnected = false),
            "$reread")
        check("isConnected restaurado como false, no como el 'true' de fábrica",
            reread?.isConnected == false, "isConnected=${reread?.isConnected}")
    }

    println()
    println("=== PERS-3: reinicio simulado -> el chat hace inferencia REAL sin reconfigurar nada ===")
    run {
        val engine = engineAfterRestart()
        check("el motor reiniciado NO vuelve a 10.0.2.2",
            engine.ollamaConfig.value.hostUrl == HOST, engine.ollamaConfig.value.hostUrl)
        check("el motor reiniciado NO vuelve al modelo inexistente",
            engine.ollamaConfig.value.modelName == "qwen2.5:0.5b", engine.ollamaConfig.value.modelName)

        send(engine, "Di la palabra PERSISTE y nada mas.")
        val reply = lastJarvis(engine)
        println("    ---- RESPUESTA DEL CHAT TRAS REINICIO ----")
        println("    $reply")
        check("respuesta real del modelo (sin reserva estática)",
            reply.isNotBlank() && !reply.contains("Comprendido. He procesado tu solicitud"), "len=${reply.length}")
        check("inferencia REAL registrada",
            logsContaining(engine, "Inferencia REAL vía Ollama").isNotEmpty(),
            logsContaining(engine, "Inferencia REAL vía Ollama").firstOrNull()?.message?.take(190) ?: "ninguno")
        check("no hizo falta auto-resolver el modelo",
            logsContaining(engine, "[MODEL_AUTORESOLVED]").isEmpty(), "ninguno")
    }

    println()
    println("=== PERS-4: la auto-resolución de modelo queda fijada para el siguiente arranque ===")
    run {
        val engine = ClawEngine(configStore = PrefsOllamaConfigStore(Context()))
        engine.updateOllamaConfig(HOST, "qwen3.5:custom-claw", WEBHOOK, false)
        send(engine, "Di la palabra AUTO y nada mas.")

        check("auto-resolución ocurrida",
            logsContaining(engine, "[MODEL_AUTORESOLVED]").isNotEmpty(),
            logsContaining(engine, "[MODEL_AUTORESOLVED]").firstOrNull()?.message?.take(160) ?: "ninguno")
        val persistedModel = FakePrefsDisk.snapshot(PREFS_NAME)["model_name"]
        println("    ---- MODELO PERSISTIDO TRAS AUTO-RESOLUCIÓN ----")
        println("    model_name = $persistedModel")
        check("el modelo que sí existe queda persistido",
            persistedModel == "qwen2.5:0.5b", "$persistedModel")
        check("config en memoria coherente con el disco",
            engine.ollamaConfig.value.modelName == persistedModel, engine.ollamaConfig.value.modelName)

        val restarted = engineAfterRestart()
        send(restarted, "Di la palabra SEGUNDO y nada mas.")
        check("tras reiniciar ya no vuelve a auto-resolver",
            logsContaining(restarted, "[MODEL_AUTORESOLVED]").isEmpty(), "ninguno")
        check("tras reiniciar sigue habiendo inferencia REAL",
            logsContaining(restarted, "Inferencia REAL vía Ollama").isNotEmpty(),
            logsContaining(restarted, "Inferencia REAL vía Ollama").firstOrNull()?.message?.take(190) ?: "ninguno")
    }

    println()
    println("=== PERS-5: regresión -> ClawEngine() sin store sigue compilando y comportándose igual ===")
    run {
        val engine = ClawEngine()
        check("motor por defecto con valores de fábrica",
            engine.ollamaConfig.value.hostUrl == "http://10.0.2.2:11434",
            engine.ollamaConfig.value.hostUrl)
        val memStore = InMemoryOllamaConfigStore()
        memStore.save(OllamaConfig(HOST, "qwen2.5:0.5b", WEBHOOK, false))
        check("store en memoria no toca el disco",
            FakePrefsDisk.snapshot(PREFS_NAME)["host_url"] == HOST && memStore.load()?.hostUrl == HOST,
            "mem=${memStore.load()?.hostUrl}")
        check("store en memoria independiente por instancia",
            InMemoryOllamaConfigStore().load() == null, "load()=${InMemoryOllamaConfigStore().load()}")
    }

    println()
    if (failures == 0) println("RESULTADO PERSISTENCIA: TODAS LAS PRUEBAS PASARON")
    else println("RESULTADO PERSISTENCIA: $failures PRUEBA(S) FALLARON")
    if (failures != 0) kotlin.system.exitProcess(1)
}
