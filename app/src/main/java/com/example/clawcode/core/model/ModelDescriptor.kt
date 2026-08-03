package com.example.clawcode.core.model

data class ModelDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val type: String = "gguf",
    val sizeBytes: Long,
    val sha256: String,
    val downloadUrl: String,
    val contextLength: Int = 8192,
    val recommendedRamMb: Int = 3072,
    val quantType: String = "Q4_K_M",
    val authorHuggingFace: String = "Qwen/Qwen3.5-3B-GGUF",
    val description: String = "Modelo Qwen 3.5 optimizado para inferencia GGUF On-Device en Android."
)

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    INSTALLED,
    ACTIVE,
    ERROR
}

data class ModelDownloadProgress(
    val modelId: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progressPercent: Float = 0f,
    val downloadSpeedKbps: Long = 0L,
    val status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val errorMessage: String? = null
)
