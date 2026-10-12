package verify

import android.content.Context
import com.example.clawcode.core.model.ModelDescriptor
import com.example.clawcode.core.model.QwenModelBridgeService
import com.example.clawcode.core.model.QwenModelBridgeState
import com.example.clawcode.data.model.OllamaConfig
import kotlinx.coroutines.runBlocking

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private const val HOST = "http://127.0.0.1:11434"

fun main() {
    println("=== QWEN-1: runTestInference() ejecuta inferencia REAL ===")
    runBlocking {
        val bridge = QwenModelBridgeService(
            context = Context(),
            configProvider = { OllamaConfig(hostUrl = HOST, modelName = "qwen2.5:0.5b") }
        )
        val result = bridge.runTestInference("Di la palabra VERDE y nada mas.")
        println("    outputText    : ${result.outputText}")
        println("    modelVersion  : ${result.modelVersion}")
        println("    latencyMs     : ${result.latencyMs}")
        println("    tokensPerSecond: ${result.tokensPerSecond}")
        println("    ramUsedMb     : ${result.ramUsedMb}")
        println("    bridgeState   : ${bridge.bridgeState.value}")

        check("respuesta real del modelo", result.outputText.isNotBlank() && !result.outputText.startsWith("Sin respuesta"),
            result.outputText)
        check("no es el texto simulado antiguo",
            !result.outputText.contains("Inferencia completada exitosamente mediante el puente Qwen"), "sin plantilla")
        check("no es la respuesta 'hola/jarvis' simulada",
            !result.outputText.contains("Todos los subsistemas de inferencia operan"), "sin plantilla")
        check("t/s real medido (antes inventado con delay(25))", result.tokensPerSecond > 0f, "${result.tokensPerSecond}")
        check("RAM real vía /api/ps (antes hardcodeado 2450.0)",
            result.ramUsedMb > 0f && result.ramUsedMb != 2450.0f, "${result.ramUsedMb} MB")
        check("modelVersion informa el endpoint real", result.modelVersion.contains(HOST), result.modelVersion)
        val state = bridge.bridgeState.value
        check("estado Ready con nombre de modelo real", state is QwenModelBridgeState.Ready &&
                (state as QwenModelBridgeState.Ready).modelName == "qwen2.5:0.5b", "$state")
        check("quantType real del servidor", state is QwenModelBridgeState.Ready &&
                state.quantType == "Q4_K_M", "${(state as? QwenModelBridgeState.Ready)?.quantType}")
        check("contextLength real del servidor", state is QwenModelBridgeState.Ready &&
                state.contextLength == 4096, "${(state as? QwenModelBridgeState.Ready)?.contextLength}")
        check("activeModelName actualizado al modelo real", bridge.activeModelName == "qwen2.5:0.5b", bridge.activeModelName)
    }

    println()
    println("=== QWEN-2: runTestInference() con runtime caído -> estado Error veraz ===")
    runBlocking {
        val bridge = QwenModelBridgeService(
            context = Context(),
            configProvider = { OllamaConfig(hostUrl = "http://127.0.0.1:11499", modelName = "qwen2.5:0.5b") }
        )
        val result = bridge.runTestInference("hola")
        println("    outputText : ${result.outputText.take(150)}")
        println("    bridgeState: ${bridge.bridgeState.value}")
        check("estado Error (antes siempre fingía Ready)",
            bridge.bridgeState.value is QwenModelBridgeState.Error, "${bridge.bridgeState.value}")
        check("no finge tokens por segundo", result.tokensPerSecond == 0f, "${result.tokensPerSecond}")
        check("explica el fallo real", result.outputText.contains("Sin respuesta del modelo"), result.outputText.take(120))
    }

    println()
    println("=== QWEN-3: loadQwenModelAsync() verifica contra el servidor en vez de simular tensores ===")
    runBlocking {
        val bridge = QwenModelBridgeService(
            context = Context(),
            configProvider = { OllamaConfig(hostUrl = HOST, modelName = "qwen2.5:0.5b") }
        )
        // Descriptor del catálogo decorativo de la app: ese modelo NO existe en el servidor.
        val descriptor = ModelDescriptor(
            id = "qwen-3.5-3b-gguf",
            name = "Qwen 3.5 3B GGUF (Q4_K_M)",
            version = "3.5",
            sizeBytes = 1_900_000_000L,
            sha256 = "0".repeat(64),
            downloadUrl = "https://example.invalid/qwen-3.5-3b.gguf"
        )

        val started = System.currentTimeMillis()
        val ok = bridge.loadQwenModelAsync(descriptor)
        val elapsed = System.currentTimeMillis() - started
        val state = bridge.bridgeState.value as? QwenModelBridgeState.Ready
        println("    bridgeState : $state")
        println("    activo      : ${bridge.activeModelName}")
        println("    duracion    : ${elapsed}ms (antes: 800ms de delay(200)+delay(350)+delay(250))")

        check("devuelve true con el servidor respondiendo", ok, "ok=$ok")
        check("NO declara Ready con el nombre ficticio del descriptor",
            state != null && state.modelName != descriptor.name, "${state?.modelName}")
        check("resuelve al modelo que el servidor sirve de verdad",
            state?.modelName == "qwen2.5:0.5b", "${state?.modelName}")
        check("tamaño real del peso, no el suelo inventado de 1850 MB",
            state != null && state.loadedSizeMb in 300f..500f, "${state?.loadedSizeMb} MB")
        check("cuantización real leída de /api/tags",
            state?.quantType == "Q4_K_M", "${state?.quantType}")
        check("contexto real del modelo, no el 8192 decorativo del descriptor",
            state != null && state.contextLength > 0 && state.contextLength != descriptor.contextLength,
            "${state?.contextLength}")
        check("sin delays falsos de 'mapeo de tensores GGUF'", elapsed < 5000, "${elapsed}ms")
        check("activeModelName coherente con lo verificado",
            bridge.activeModelName == "qwen2.5:0.5b", bridge.activeModelName)
    }

    println()
    println("=== QWEN-4: loadQwenModelAsync() con runtime caído -> Error, no Ready fingido ===")
    runBlocking {
        val bridge = QwenModelBridgeService(
            context = Context(),
            configProvider = { OllamaConfig(hostUrl = "http://127.0.0.1:11499", modelName = "qwen2.5:0.5b") }
        )
        val ok = bridge.loadQwenModelAsync(
            ModelDescriptor(
                id = "qwen-3.5-3b-gguf",
                name = "Qwen 3.5 3B GGUF (Q4_K_M)",
                version = "3.5",
                sizeBytes = 1_900_000_000L,
                sha256 = "0".repeat(64),
                downloadUrl = "https://example.invalid/qwen-3.5-3b.gguf"
            )
        )
        println("    bridgeState: ${bridge.bridgeState.value}")
        check("devuelve false", !ok, "ok=$ok")
        check("estado Error (antes devolvía Ready con 1850 MB inventados)",
            bridge.bridgeState.value is QwenModelBridgeState.Error, "${bridge.bridgeState.value}")
        check("el error explica que no se pudo verificar",
            (bridge.bridgeState.value as? QwenModelBridgeState.Error)?.message?.contains("verificar") == true,
            (bridge.bridgeState.value as? QwenModelBridgeState.Error)?.message?.take(140).orEmpty())
        check("activeModelName no afirma un modelo que no se verificó",
            bridge.activeModelName == "sin verificar", bridge.activeModelName)
    }

    println()
    if (failures == 0) println("RESULTADO QWEN BRIDGE: TODAS LAS PRUEBAS PASARON")
    else println("RESULTADO QWEN BRIDGE: $failures PRUEBA(S) FALLARON")
    if (failures != 0) kotlin.system.exitProcess(1)
}
