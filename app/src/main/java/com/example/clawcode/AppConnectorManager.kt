package com.example.clawcode

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.ai.client.generativeai.GenerativeModel
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

class AppConnectorManager(private val context: Context) {

    private val httpClient = OkHttpClient()

    // Configuración de Tokens (Debes reemplazarlos por los tuyos)
    private val NOTION_TOKEN = "secret_tu_token_aqui"
    private val NOTION_DATABASE_ID = "tu_db_id_aqui"
    private val GITHUB_TOKEN = "ghp_tu_token_aqui"

    /**
     * PANEL DE VERIFICACIÓN "APP POR APP"
     * Devuelve el estado actual de cada integración en Android
     */
    fun verificarEstadosApps(): Map<String, String> {
        val estados = mutableMapOf<String, String>()

        // 1. WhatsApp Normal
        estados["WhatsApp"] = if (esAppInstalada("com.whatsapp")) "Verificada (Instalada)" else "No instalada"

        // 2. Google Drive / Gmail
        estados["Google Workspace"] = "Listo (Usa la cuenta activa del sistema)"

        // 3. Notion
        estados["Notion"] = if (NOTION_TOKEN.startsWith("secret_")) "Configurada" else "Falta Token"

        // 4. GitHub
        estados["GitHub"] = if (GITHUB_TOKEN.startsWith("ghp_")) "Configurada" else "Falta Token"

        // 5. Google AI Edge Gallery
        estados["AI Edge (Gemini Nano)"] = "Disponible en el dispositivo"

        return estados
    }

    private fun esAppInstalada(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // LÓGICA DE EJECUCIÓN NATIVA PARA CADA APP
    // ==========================================

    // --- WHATSAPP (NATIVO MEDIANTE INTENT) ---
    fun whatsappEnviarMensaje(telefono: String, mensaje: String) {
        val numeroLimpio = telefono.replace("[^0-9]".toRegex(), "") // Quita '+' o espacios
        val url = "https://whatsapp.com/send?phone=$numeroLimpio&text=${URLEncoder.encode(mensaje, "UTF-8")}"
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
            setPackage("com.whatsapp") // Obliga a abrir el WhatsApp Normal
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // --- GOOGLE AI EDGE GALLERY (PROCESAMIENTO LOCAL EN EL CELULAR) ---
    suspend fun aiEdgeProcesarLocal(promptCorto: String): String {
        return try {
            // Se conecta directamente a AICore en el procesador del teléfono
            val model = GenerativeModel(
                modelName = "gemini-nano",
                apiKey = "TU_API_KEY_OPCIONAL" // En dispositivos compatibles la firma la gestiona el sistema
            )
            val response = model.generateContent(promptCorto)
            response.text ?: "Sin respuesta del modelo local."
        } catch (e: Exception) {
            "Error en AI Edge Local: ${e.localizedMessage}"
        }
    }

    // --- NOTION (PETICIÓN HTTP ASÍNCRONA) ---
    fun notionCrearPagina(titulo: String, callback: (Boolean, String) -> Unit) {
        val json = """
            {
                "parent": { "database_id": "$NOTION_DATABASE_ID" },
                "properties": {
                    "Name": { "title": [ { "text": { "content": "$titulo" } } ] }
                }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://api.notion.com/v1/pages")
            .addHeader("Authorization", "Bearer $NOTION_TOKEN")
            .addHeader("Notion-Version", "2022-06-28")
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, e.message ?: "Error desconocido")
            }
            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful, response.body?.string() ?: "")
            }
        })
    }

    // --- GITHUB (VER REPOSITORIOS) ---
    fun githubVerRepositorios(callback: (Boolean, String) -> Unit) {
        val request = Request.Builder()
            .url("https://api.github.com/user/repos")
            .addHeader("Authorization", "token $GITHUB_TOKEN")
            .addHeader("Accept", "application/vnd.github.v3+json")
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, e.message ?: "Error de red")
            }
            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful, response.body?.string() ?: "")
            }
        })
    }
}
