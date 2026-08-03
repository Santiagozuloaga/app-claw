package com.example.clawcode.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GitHubRepositoryModule {

    suspend fun fetchRepoSummary(ownerRepo: String): String = withContext(Dispatchers.IO) {
        try {
            val cleanPath = ownerRepo.replace("https://github.com/", "").trim('/')
            val apiUrl = "https://api.github.com/repos/$cleanPath"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "ClawCode-AndroidApp")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                "[GitHub Repo Metadata Fetched]\n$response"
            } else {
                "[GitHub Error] HTTP Code: ${connection.responseCode} para $cleanPath"
            }
        } catch (e: Exception) {
            "[GitHub Exception] Fallo al consultar repositorio: ${e.localizedMessage}"
        }
    }

    suspend fun fetchRawFile(ownerRepo: String, filePath: String, branch: String = "main"): String = withContext(Dispatchers.IO) {
        try {
            val cleanPath = ownerRepo.replace("https://github.com/", "").trim('/')
            val rawUrl = "https://raw.githubusercontent.com/$cleanPath/$branch/$filePath"
            val url = URL(rawUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "ClawCode-AndroidApp")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val content = reader.readText()
                reader.close()
                "[GitHub File: $filePath ($branch)]\n$content"
            } else {
                "[GitHub Error] No se encontró el archivo $filePath en $branch (HTTP ${connection.responseCode})"
            }
        } catch (e: Exception) {
            "[GitHub Exception] Fallo al obtener el archivo: ${e.localizedMessage}"
        }
    }
}
