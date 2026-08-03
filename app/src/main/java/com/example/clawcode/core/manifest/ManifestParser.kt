package com.example.clawcode.core.manifest

import com.example.clawcode.core.model.ModelDescriptor
import org.json.JSONArray
import org.json.JSONObject

data class ModelManifestFile(
    val name: String,
    val size: Long,
    val sha256: String
)

data class ModelManifest(
    val id: String,
    val name: String,
    val version: String,
    val modelType: String,
    val defaultContext: Int,
    val recommendedRamMb: Int,
    val authorHuggingFace: String,
    val files: List<ModelManifestFile>
)

object ManifestParser {

    fun parseManifest(jsonString: String): ModelManifest {
        val json = JSONObject(jsonString)
        val filesList = mutableListOf<ModelManifestFile>()
        
        if (json.has("files")) {
            val filesArray: JSONArray = json.getJSONArray("files")
            for (i in 0 until filesArray.length()) {
                val fObj = filesArray.getJSONObject(i)
                filesList.add(
                    ModelManifestFile(
                        name = fObj.optString("name", "model.gguf"),
                        size = fObj.optLong("size", 0L),
                        sha256 = fObj.optString("sha256", "")
                    )
                )
            }
        }

        return ModelManifest(
            id = json.optString("id", json.optString("name", "claw-qwen")),
            name = json.optString("name", "Qwen 3.5 GGUF"),
            version = json.optString("version", "1.0.0"),
            modelType = json.optString("model_type", "gguf"),
            defaultContext = json.optInt("default_context", 8192),
            recommendedRamMb = json.optInt("recommended_ram_mb", 3072),
            authorHuggingFace = json.optString("author", "Qwen/Qwen3.5-GGUF"),
            files = filesList
        )
    }

    fun toDescriptor(manifest: ModelManifest): ModelDescriptor {
        val mainFile = manifest.files.firstOrNull { it.name.endsWith(".gguf") } 
            ?: manifest.files.firstOrNull() 
            ?: ModelManifestFile("qwen3.5-3b.gguf", 1950000000L, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")

        return ModelDescriptor(
            id = manifest.id,
            name = manifest.name,
            version = manifest.version,
            type = manifest.modelType,
            sizeBytes = mainFile.size,
            sha256 = mainFile.sha256,
            downloadUrl = "https://huggingface.co/${manifest.authorHuggingFace}/resolve/main/${mainFile.name}",
            contextLength = manifest.defaultContext,
            recommendedRamMb = manifest.recommendedRamMb,
            authorHuggingFace = manifest.authorHuggingFace
        )
    }
}
