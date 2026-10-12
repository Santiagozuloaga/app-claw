package com.example.clawcode.core.model

import android.content.Context
import com.example.clawcode.core.storage.ModelStorage
import com.example.clawcode.data.model.OllamaConfig
import com.example.clawcode.engine.OllamaChatRequest
import com.example.clawcode.engine.OllamaClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed class QwenModelBridgeState {
    object Unloaded : QwenModelBridgeState()
    data class Loading(val progressPercent: Int, val stepMessage: String) : QwenModelBridgeState()
    data class Ready(
        val modelName: String,
        val quantType: String,
        val contextLength: Int,
        val loadedSizeMb: Float
    ) : QwenModelBridgeState()
    data class Inferring(val currentTokens: Int, val tokensPerSecond: Float) : QwenModelBridgeState()
    data class Error(val message: String) : QwenModelBridgeState()
}

data class QwenTestInferenceResult(
    val outputText: String,
    val latencyMs: Long,
    val tokensPerSecond: Float,
    val ramUsedMb: Float,
    val modelVersion: String
)

class QwenModelBridgeService(
    private val context: Context,
    private val ollamaClient: OllamaClient = OllamaClient(),
    private val configProvider: () -> OllamaConfig = { OllamaConfig() }
) {

    private val modelStorage = ModelStorage(context)

    private val _bridgeState = MutableStateFlow<QwenModelBridgeState>(QwenModelBridgeState.Unloaded)
    val bridgeState: StateFlow<QwenModelBridgeState> = _bridgeState.asStateFlow()

    private val _lastTestResult = MutableStateFlow<QwenTestInferenceResult?>(null)
    val lastTestResult: StateFlow<QwenTestInferenceResult?> = _lastTestResult.asStateFlow()

    var activeModelName: String = "sin verificar"
        private set

    private var activeQuantType: String = ""
    private var activeContextLength: Int = 0

    /**
     * Carga REAL: la app no mapea tensores GGUF en el proceso, delega en el runtime local.
     * Por eso "cargar" significa verificar contra el servidor qué modelo sirve de verdad y
     * reportar su tamaño, cuantización y contexto medidos, en lugar de cifras inventadas.
     */
    suspend fun loadQwenModelAsync(modelDescriptor: ModelDescriptor): Boolean = withContext(Dispatchers.IO) {
        val config = configProvider()
        try {
            _bridgeState.value = QwenModelBridgeState.Loading(20, "Consultando el runtime local en ${config.hostUrl}...")

            val catalogue = ollamaClient.listCompletionModels(config.hostUrl).getOrElse { error ->
                _bridgeState.value = QwenModelBridgeState.Error(
                    "No se pudo verificar '${modelDescriptor.name}' en el runtime local: " +
                            (error.message ?: error.javaClass.simpleName)
                )
                return@withContext false
            }
            val localModels = catalogue.filter { !it.isCloudStub }

            _bridgeState.value = QwenModelBridgeState.Loading(
                55,
                "Resolviendo '${modelDescriptor.name}' contra ${localModels.size} modelo(s) local(es) del servidor..."
            )

            val resolved = localModels.firstOrNull { it.name.equals(modelDescriptor.name, ignoreCase = true) }
                ?: localModels.firstOrNull { it.name.equals(config.modelName, ignoreCase = true) }
                ?: localModels.firstOrNull()

            if (resolved == null) {
                _bridgeState.value = QwenModelBridgeState.Error(
                    "El runtime ${config.hostUrl} no expone ningún modelo local descargado " +
                            "(${catalogue.size} entrada(s), todas stub en la nube)."
                )
                return@withContext false
            }

            _bridgeState.value = QwenModelBridgeState.Loading(85, "Leyendo peso y cuantización reales de '${resolved.name}'...")

            activeModelName = resolved.name
            activeQuantType = resolved.quantType.ifBlank { modelDescriptor.quantType }
            activeContextLength = resolved.contextLength

            _bridgeState.value = QwenModelBridgeState.Ready(
                modelName = resolved.name,
                quantType = activeQuantType,
                contextLength = activeContextLength,
                loadedSizeMb = resolved.sizeBytes / (1024f * 1024f)
            )
            true
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            _bridgeState.value = QwenModelBridgeState.Error(
                "Error al verificar el modelo en el runtime local: ${e.message}"
            )
            false
        }
    }

    /**
     * Prueba de inferencia REAL contra el runtime local configurado (Ollama).
     * Sustituye a la simulación anterior de tokens/latencia: todas las métricas
     * devueltas provienen del servidor de inferencia, no de valores inventados.
     */
    suspend fun runTestInference(prompt: String): QwenTestInferenceResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        _bridgeState.value = QwenModelBridgeState.Inferring(currentTokens = 0, tokensPerSecond = 0f)

        val config = configProvider()
        val reply = ollamaClient.chat(
            OllamaChatRequest(
                hostUrl = config.hostUrl,
                model = config.modelName,
                systemPrompt = TEST_SYSTEM_PROMPT,
                history = emptyList(),
                userText = prompt,
                temperature = 0.7f,
                topP = 0.9f,
                stopTokens = emptyList(),
                maxTokens = TEST_MAX_TOKENS
            )
        )

        reply.fold(
            onSuccess = { inference ->
                activeModelName = inference.model
                val footprint = ollamaClient.loadedModelFootprint(config.hostUrl, inference.model).getOrNull()
                if (footprint != null) {
                    activeQuantType = footprint.quantType
                    activeContextLength = footprint.contextLength
                }

                val result = QwenTestInferenceResult(
                    outputText = inference.text,
                    latencyMs = inference.latencyMs,
                    tokensPerSecond = inference.tokensPerSecond,
                    ramUsedMb = footprint?.ramMb ?: processMemoryMb(),
                    modelVersion = "${inference.model} @ ${config.hostUrl}"
                )

                _lastTestResult.value = result
                _bridgeState.value = QwenModelBridgeState.Ready(
                    modelName = inference.model,
                    quantType = activeQuantType,
                    contextLength = activeContextLength,
                    loadedSizeMb = footprint?.ramMb ?: 0f
                )
                result
            },
            onFailure = { error ->
                val reason = error.message ?: error.javaClass.simpleName
                _bridgeState.value = QwenModelBridgeState.Error("Inferencia real fallida: $reason")

                val result = QwenTestInferenceResult(
                    outputText = "Sin respuesta del modelo: $reason",
                    latencyMs = System.currentTimeMillis() - startTime,
                    tokensPerSecond = 0f,
                    ramUsedMb = 0f,
                    modelVersion = "${config.modelName} @ ${config.hostUrl}"
                )
                _lastTestResult.value = result
                result
            }
        )
    }

    private fun processMemoryMb(): Float {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
    }

    suspend fun unloadModelAsync() = withContext(Dispatchers.IO) {
        _bridgeState.value = QwenModelBridgeState.Unloaded
        _lastTestResult.value = null
    }

    private companion object {
        const val TEST_MAX_TOKENS = 256
        const val TEST_SYSTEM_PROMPT =
            "Eres JARVIS, el agente de pruebas del runtime CLAW. Responde de forma breve y directa."
    }
}
