package com.example.clawcode.core.repository

import android.content.Context
import com.example.clawcode.core.config.ModelRuntimeConfig
import com.example.clawcode.core.download.ModelDownloadManager
import com.example.clawcode.core.manifest.ManifestParser
import com.example.clawcode.core.model.DownloadStatus
import com.example.clawcode.core.model.ModelDescriptor
import com.example.clawcode.core.model.ModelDownloadProgress
import com.example.clawcode.core.storage.ModelStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ModelRepository(
    private val context: Context,
    private val storage: ModelStorage = ModelStorage(context),
    val downloadManager: ModelDownloadManager = ModelDownloadManager(storage)
) {

    private val _availableModels = MutableStateFlow<List<ModelDescriptor>>(emptyList())
    val availableModels: StateFlow<List<ModelDescriptor>> = _availableModels.asStateFlow()

    private val _runtimeConfig = MutableStateFlow(ModelRuntimeConfig())
    val runtimeConfig: StateFlow<ModelRuntimeConfig> = _runtimeConfig.asStateFlow()

    init {
        loadCatalog()
    }

    private fun loadCatalog() {
        // Pre-configured Hugging Face catalog of GGUF models suitable for On-Device Android execution
        val sampleManifest1 = """
            {
              "id": "qwen-3.5-3b-gguf",
              "name": "Qwen 3.5 3B Instruct (GGUF Q4_K_M)",
              "version": "1.0.0",
              "model_type": "gguf",
              "default_context": 8192,
              "recommended_ram_mb": 3072,
              "author": "Qwen/Qwen3.5-3B-GGUF",
              "files": [
                {
                  "name": "qwen3.5-3b-q4_k_m.gguf",
                  "size": 1950000000,
                  "sha256": "3a8d90f23b118b62817812bc85ef109"
                }
              ]
            }
        """.trimIndent()

        val sampleManifest2 = """
            {
              "id": "qwen-3.5-0.5b-gguf",
              "name": "Qwen 3.5 0.5B Lite (GGUF Q8_0)",
              "version": "1.1.0",
              "model_type": "gguf",
              "default_context": 16384,
              "recommended_ram_mb": 1024,
              "author": "Qwen/Qwen3.5-0.5B-GGUF",
              "files": [
                {
                  "name": "qwen3.5-0.5b-q8_0.gguf",
                  "size": 620000000,
                  "sha256": "1f8e9198a2309e419810f22"
                }
              ]
            }
        """.trimIndent()

        val sampleManifest3 = """
            {
              "id": "deepseek-r1-1.5b-gguf",
              "name": "DeepSeek R1 Distill 1.5B (GGUF Q4_K_M)",
              "version": "1.0.0",
              "model_type": "gguf",
              "default_context": 8192,
              "recommended_ram_mb": 2048,
              "author": "unsloth/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
              "files": [
                {
                  "name": "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                  "size": 1120000000,
                  "sha256": "f2910a284e918231028"
                }
              ]
            }
        """.trimIndent()

        val manifest1 = ManifestParser.parseManifest(sampleManifest1)
        val manifest2 = ManifestParser.parseManifest(sampleManifest2)
        val manifest3 = ManifestParser.parseManifest(sampleManifest3)

        _availableModels.value = listOf(
            ManifestParser.toDescriptor(manifest1).copy(
                description = "El modelo oficial recomendado para CLAW. Excelente equilibrio entre razonamiento, ejecución de herramientas e intenciones Android."
            ),
            ManifestParser.toDescriptor(manifest2).copy(
                description = "Ultraligero. Diseñado para dispositivos con poca memoria RAM o modo ahorro de batería sin latencia."
            ),
            ManifestParser.toDescriptor(manifest3).copy(
                description = "Especializado en razonamiento paso a paso, diagnóstico de código y tareas complejas de desarrollo."
            )
        )
    }

    fun isModelInstalled(modelId: String): Boolean {
        return storage.isModelFilePresent(modelId)
    }

    fun getDownloadStatus(modelId: String): DownloadStatus {
        if (_runtimeConfig.value.activeModelId == modelId) return DownloadStatus.ACTIVE
        if (isModelInstalled(modelId)) return DownloadStatus.INSTALLED
        val progressMap = downloadManager.downloadProgresses.value
        return progressMap[modelId]?.status ?: DownloadStatus.NOT_DOWNLOADED
    }

    fun startDownload(model: ModelDescriptor, scope: CoroutineScope) {
        downloadManager.startOrResumeDownload(model, scope) { success ->
            if (success) {
                // Auto-activate if first installed model
                if (_runtimeConfig.value.activeModelId.isBlank()) {
                    setActiveModel(model)
                }
            }
        }
    }

    fun pauseDownload(modelId: String) {
        downloadManager.pauseDownload(modelId)
    }

    fun deleteModel(modelId: String) {
        downloadManager.cancelAndDeleteDownload(modelId)
        if (_runtimeConfig.value.activeModelId == modelId) {
            _runtimeConfig.update { it.copy(activeModelId = "", activeModelName = "Sin modelo activo") }
        }
    }

    fun setActiveModel(model: ModelDescriptor) {
        _runtimeConfig.update {
            it.copy(
                activeModelId = model.id,
                activeModelName = model.name,
                contextLength = model.contextLength
            )
        }
    }

    fun updateRuntimeConfig(
        systemPrompt: String = _runtimeConfig.value.systemPrompt,
        temperature: Float = _runtimeConfig.value.temperature,
        topP: Float = _runtimeConfig.value.topP,
        repeatPenalty: Float = _runtimeConfig.value.repeatPenalty,
        contextLength: Int = _runtimeConfig.value.contextLength,
        powerSavingMode: Boolean = _runtimeConfig.value.powerSavingMode
    ) {
        _runtimeConfig.update {
            it.copy(
                systemPrompt = systemPrompt,
                temperature = temperature,
                topP = topP,
                repeatPenalty = repeatPenalty,
                contextLength = contextLength,
                powerSavingMode = powerSavingMode
            )
        }
    }
}
