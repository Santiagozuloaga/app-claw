package com.example.clawcode.core.download

import com.example.clawcode.core.integrity.Sha256Verifier
import com.example.clawcode.core.model.DownloadStatus
import com.example.clawcode.core.model.ModelDescriptor
import com.example.clawcode.core.model.ModelDownloadProgress
import com.example.clawcode.core.storage.ModelStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.FileOutputStream

class ModelDownloadManager(private val storage: ModelStorage) {

    private val _downloadProgresses = MutableStateFlow<Map<String, ModelDownloadProgress>>(emptyMap())
    val downloadProgresses: StateFlow<Map<String, ModelDownloadProgress>> = _downloadProgresses.asStateFlow()

    private val activeDownloadJobs = mutableMapOf<String, Job>()
    private val pausedStateMap = mutableMapOf<String, Boolean>()

    fun startOrResumeDownload(
        model: ModelDescriptor,
        scope: CoroutineScope,
        onComplete: (Boolean) -> Unit
    ) {
        if (activeDownloadJobs[model.id]?.isActive == true) return
        pausedStateMap[model.id] = false

        val job = scope.launch(Dispatchers.IO) {
            val chunkFile = storage.getDownloadChunkFile(model.id)
            val existingBytes = if (chunkFile.exists()) chunkFile.length() else 0L
            val targetBytes = model.sizeBytes

            _downloadProgresses.update { current ->
                current + (model.id to ModelDownloadProgress(
                    modelId = model.id,
                    bytesDownloaded = existingBytes,
                    totalBytes = targetBytes,
                    progressPercent = if (targetBytes > 0) (existingBytes.toFloat() / targetBytes * 100f) else 0f,
                    downloadSpeedKbps = 0L,
                    status = DownloadStatus.DOWNLOADING
                ))
            }

            var currentBytes = existingBytes
            val outputStream = FileOutputStream(chunkFile, true)

            try {
                // Determine whether to use real HTTP or simulate
                // To support Hugging Face, we use real network logic if the URL is valid.
                val useRealNetwork = model.downloadUrl.startsWith("http")
                
                if (useRealNetwork) {
                    var conexionHttp: java.net.HttpURLConnection? = null
                    try {
                        val url = java.net.URL(model.downloadUrl)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        conexionHttp = connection
                        connection.requestMethod = "GET"
                        if (existingBytes > 0) {
                            connection.setRequestProperty("Range", "bytes=$existingBytes-")
                        }
                        connection.connect()

                        val downloadStartedAt = System.currentTimeMillis()
                        val stream = connection.inputStream
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var lastUpdateTime = System.currentTimeMillis()
                        var lastUpdateBytes = currentBytes

                        while (stream.read(buffer).also { bytesRead = it } != -1 && isActive && pausedStateMap[model.id] != true) {
                            outputStream.write(buffer, 0, bytesRead)
                            currentBytes += bytesRead

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateTime > 500) {
                                val pct = (currentBytes.toFloat() / targetBytes.toFloat() * 100f).coerceAtMost(100f)
                                // Velocidad realmente observada entre dos refrescores, no un número
                                // escrito a mano: 18500 kbps aparecían antes de leer un solo byte.
                                val elapsedMs = (currentTime - lastUpdateTime).coerceAtLeast(1L)
                                val speedKbps = (currentBytes - lastUpdateBytes) * 1000L / 1024L / elapsedMs
                                _downloadProgresses.update { map ->
                                    map + (model.id to map[model.id]!!.copy(
                                        bytesDownloaded = currentBytes,
                                        progressPercent = pct,
                                        downloadSpeedKbps = speedKbps,
                                        status = DownloadStatus.DOWNLOADING
                                    ))
                                }
                                lastUpdateBytes = currentBytes
                                lastUpdateTime = currentTime
                            }
                        }
                        stream.close()

                        // Cierre de la medición: el corte de 500 ms deja fuera las descargas rápidas,
                        // y la pantalla mostraba "0 bytes" con el modelo ya instalado. Se publica el
                        // total y la velocidad media realmente observada.
                        val elapsedTotalMs = (System.currentTimeMillis() - downloadStartedAt).coerceAtLeast(1L)
                        val averageKbps =
                            (currentBytes - existingBytes) * 1000L / 1024L / elapsedTotalMs
                        _downloadProgresses.update { map ->
                            val current = map[model.id] ?: return@update map
                            map + (model.id to current.copy(
                                bytesDownloaded = currentBytes,
                                progressPercent = (currentBytes.toFloat() / targetBytes.toFloat() * 100f)
                                    .coerceAtMost(100f),
                                downloadSpeedKbps = averageKbps,
                                status = DownloadStatus.DOWNLOADING
                            ))
                        }
                    } catch (e: Exception) {
                        // Sin red no se fabrican pesos: antes caía en runSimulation() y escribía un
                        // .gguf de relleno que la app mostraba como "modelo descargado".
                        failDownload(model.id, "La descarga real falló (${e.javaClass.simpleName}: " +
                                "${e.message ?: "sin mensaje"}). No se simulan pesos locales.")
                        onComplete(false)
                        return@launch
                    } finally {
                        // Fase 7: sin disconnect() el socket de la descarga quedaba retenido hasta
                        // el colector, también al cancelar o al fallar a mitad.
                        conexionHttp?.disconnect()
                    }
                } else {
                    failDownload(model.id, "El descriptor '${model.name}' no trae URL de descarga " +
                            "http(s) real ('${model.downloadUrl}'). Sin URL no hay pesos que instalar; " +
                            "esta build no simula descargas.")
                    onComplete(false)
                    return@launch
                }

                outputStream.close()

                if (pausedStateMap[model.id] == true) {
                    _downloadProgresses.update { map ->
                        map + (model.id to map[model.id]!!.copy(status = DownloadStatus.PAUSED))
                    }
                    return@launch
                }

                if (currentBytes >= targetBytes) {
                    // Verification state
                    _downloadProgresses.update { map ->
                        map + (model.id to map[model.id]!!.copy(status = DownloadStatus.VERIFYING))
                    }
                    delay(500)

                    // Move file from downloads chunk to models folder
                    val finalModelFile = storage.getModelFile(model.id, "model.gguf")
                    chunkFile.copyTo(finalModelFile, overwrite = true)
                    chunkFile.delete()

                    // verifyFileHash devuelve true para manifiestos de desarrollo (sha256 vacío,
                    // 'dummy_sha256' o truncado), así que añadirlo no rompe esa ruta: con un hash
                    // real ya no se instala un archivo corrupto o sustituido, que antes pasaba por
                    // bueno porque sólo se comparaba el tamaño.
                    val isValid = Sha256Verifier.verifySize(finalModelFile, targetBytes) &&
                            Sha256Verifier.verifyFileHash(finalModelFile, model.sha256)
                    if (isValid) {
                        _downloadProgresses.update { map ->
                            map + (model.id to map[model.id]!!.copy(
                                status = DownloadStatus.INSTALLED,
                                progressPercent = 100f
                            ))
                        }
                        onComplete(true)
                    } else {
                        _downloadProgresses.update { map ->
                            map + (model.id to map[model.id]!!.copy(
                                status = DownloadStatus.ERROR,
                                errorMessage = "Error de integridad SHA256 / Tamaño. Archivo descartado."
                            ))
                        }
                        // El mensaje decía "descartado" y el fichero se quedaba en la carpeta de
                        // modelos, listo para aparecer como instalado en un próximo arranque.
                        runCatching { storage.deleteModel(model.id) }
                        onComplete(false)
                    }
                }
            } catch (e: Exception) {
                try { outputStream.close() } catch (_: Exception) {}
                _downloadProgresses.update { map ->
                    map + (model.id to map[model.id]!!.copy(
                        status = DownloadStatus.ERROR,
                        errorMessage = e.localizedMessage ?: "Error de descarga en red"
                    ))
                }
                onComplete(false)
            }
        }
        activeDownloadJobs[model.id] = job
    }
    
    private fun failDownload(modelId: String, reason: String) {
        _downloadProgresses.update { map ->
            val current = map[modelId]
            if (current == null) map else map + (modelId to current.copy(
                status = DownloadStatus.ERROR,
                errorMessage = reason
            ))
        }
    }

    fun pauseDownload(modelId: String) {
        pausedStateMap[modelId] = true
        activeDownloadJobs[modelId]?.cancel()
        _downloadProgresses.update { map ->
            val current = map[modelId]
            if (current != null) {
                map + (modelId to current.copy(status = DownloadStatus.PAUSED))
            } else map
        }
    }

    fun cancelAndDeleteDownload(modelId: String) {
        pausedStateMap[modelId] = true
        activeDownloadJobs[modelId]?.cancel()
        storage.deleteModel(modelId)
        _downloadProgresses.update { map ->
            map - modelId
        }
    }
}
