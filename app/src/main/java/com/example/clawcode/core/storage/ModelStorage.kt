package com.example.clawcode.core.storage

import android.content.Context
import java.io.File

class ModelStorage(private val context: Context) {

    private val modelsDirectory: File by lazy {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val modelsFolder = File(baseDir, "models")
        if (!modelsFolder.exists()) {
            modelsFolder.mkdirs()
        }
        modelsFolder
    }

    private val downloadsDirectory: File by lazy {
        val downloadsFolder = File(modelsDirectory, "downloads")
        if (!downloadsFolder.exists()) {
            downloadsFolder.mkdirs()
        }
        downloadsFolder
    }

    fun getModelFolder(modelId: String): File {
        val folder = File(modelsDirectory, modelId)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }

    fun getModelFile(modelId: String, fileName: String = "model.gguf"): File {
        return File(getModelFolder(modelId), fileName)
    }

    fun getDownloadChunkFile(modelId: String): File {
        return File(downloadsDirectory, "$modelId.part")
    }

    fun isModelFilePresent(modelId: String, fileName: String = "model.gguf"): Boolean {
        val file = getModelFile(modelId, fileName)
        return file.exists() && file.length() > 0
    }

    fun deleteModel(modelId: String): Boolean {
        val folder = File(modelsDirectory, modelId)
        val chunk = getDownloadChunkFile(modelId)
        if (chunk.exists()) chunk.delete()
        return if (folder.exists()) {
            folder.deleteRecursively()
        } else false
    }

    // getAvailableStorageBytes() eliminado: no lo llamaba nadie y su reserva era 5.000.000.000
    // bytes "por si acaso", una cifra que DiskSpaceManager.getStorageOverview() ya no necesita
    // porque lee StatFs de verdad y reporta -1 (sin medir) cuando falla.

    fun getInstalledModelFiles(): List<File> {
        return modelsDirectory.listFiles { file -> file.isDirectory && file.name != "downloads" }?.toList() ?: emptyList()
    }
}
