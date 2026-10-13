package com.example.clawcode.core.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

data class ModelStorageItem(
    val id: String,
    val name: String,
    val sizeMb: Float,
    val path: String,
    val fileCount: Int,
    val lastModified: String
)

data class StorageOverview(
    val modelsStorageMb: Float,
    val cacheStorageMb: Float,
    val internalDataStorageMb: Float,
    val freeDeviceStorageGb: Float,
    val totalDeviceStorageGb: Float,
    val modelItems: List<ModelStorageItem>
)

class DiskSpaceManager(private val context: Context) {

    private val modelStorage = ModelStorage(context)

    fun getStorageOverview(): StorageOverview {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val modelsFolder = File(baseDir, "models")
        val cacheFolder = context.cacheDir

        val modelsMb = calculateFolderSize(modelsFolder) / (1024f * 1024f)
        val cacheMb = calculateFolderSize(cacheFolder) / (1024f * 1024f)
        val internalMb = calculateFolderSize(context.filesDir) / (1024f * 1024f)

        // StatFs for device storage. El fallback anterior era 16,4 / 128,0 GB "típicos de un
        // Vivo": si StatFs lanzaba una excepción, la pantalla mostraba esas dos cifras como si
        // hubieran sido medidas. -1 significa "sin medir" y las pantallas lo traducen así.
        var freeGb = -1f
        var totalGb = -1f
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val availableBlocks = stat.availableBlocksLong
            val totalBlocks = stat.blockCountLong

            freeGb = (availableBlocks * blockSize) / (1024f * 1024f * 1024f)
            totalGb = (totalBlocks * blockSize) / (1024f * 1024f * 1024f)
        } catch (e: Exception) {
            // Se conserva -1: sin lectura de StatFs no hay cifra que mostrar.
        }

        // List models
        val modelItems = mutableListOf<ModelStorageItem>()
        if (modelsFolder.exists() && modelsFolder.isDirectory) {
            modelsFolder.listFiles()?.forEach { file ->
                if (file.name != "downloads") {
                    val size = if (file.isDirectory) calculateFolderSize(file) else file.length()
                    val filesCount = if (file.isDirectory) file.listFiles()?.size ?: 1 else 1
                    val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
                    
                    modelItems.add(
                        ModelStorageItem(
                            id = file.name,
                            name = formatModelDisplayName(file.name),
                            sizeMb = size / (1024f * 1024f),
                            path = file.absolutePath,
                            fileCount = filesCount,
                            lastModified = dateStr
                        )
                    )
                }
            }
        }

        return StorageOverview(
            modelsStorageMb = modelsMb,
            cacheStorageMb = cacheMb,
            internalDataStorageMb = internalMb,
            freeDeviceStorageGb = freeGb,
            totalDeviceStorageGb = totalGb,
            modelItems = modelItems
        )
    }

    fun clearCache(): Boolean {
        return try {
            var freed = false
            context.cacheDir?.deleteRecursively()
            context.cacheDir?.mkdirs()
            
            context.externalCacheDir?.deleteRecursively()
            context.externalCacheDir?.mkdirs()

            context.codeCacheDir?.deleteRecursively()
            context.codeCacheDir?.mkdirs()
            
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteModel(modelId: String): Boolean {
        return modelStorage.deleteModel(modelId)
    }

    private fun calculateFolderSize(folder: File?): Long {
        if (folder == null || !folder.exists()) return 0L
        var size = 0L
        if (folder.isDirectory) {
            folder.listFiles()?.forEach { child ->
                size += if (child.isDirectory) calculateFolderSize(child) else child.length()
            }
        } else {
            size = folder.length()
        }
        return size
    }

    private fun formatModelDisplayName(fileName: String): String {
        return when {
            fileName.contains("qwen", ignoreCase = true) -> "Qwen 3.5 / 2.5 GGUF Local"
            fileName.contains("llama", ignoreCase = true) -> "Llama 3 Instruct 4-bit"
            fileName.contains("phi", ignoreCase = true) -> "Phi-3 Mini GGUF"
            fileName.contains("deepseek", ignoreCase = true) -> "DeepSeek Coder R1"
            else -> fileName.replace("_", " ").replace("-", " ").capitalize()
        }
    }
}
