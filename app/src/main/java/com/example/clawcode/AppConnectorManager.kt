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
    private val AI_EDGE_KEY = "TU_API_KEY_OPCIONAL"

    /**
     * PANEL DE VERIFICACIÓN "APP POR APP"
     * Devuelve el estado actual de cada integración en Android
     */
    fun verificarEstadosApps(): Map<String, String> {
        val estados = mutableMapOf<String, String>()

        // 1. WhatsApp Normal
        estados["WhatsApp"] = if (esAppInstalada("com.whatsapp")) "Verificada (Instalada)" else "No instalada"

        // 2. Google Workspace: se comprueba si hay servicios de Google en el dispositivo; el resto
        //    depende de la cuenta activa y no se puede verificar desde aquí.
        estados["Google Workspace"] = if (esAppInstalada("com.google.android.gms"))
            "Servicios de Google presentes (la cuenta activa no se comprueba desde la app)"
        else
            "Sin Google Play Services en este dispositivo"

        // 3. Notion. Antes: `startsWith("secret_")` -> "Configurada". Como el valor de fábrica ES
        //    "secret_tu_token_aqui", el panel decía "Configurada" precisamente cuando no hay token.
        //    Ningún valor se muestra: sólo si está reemplazado.
        estados["Notion"] = if (esCredencialReal(NOTION_TOKEN, "secret_tu_token")) "Token propio configurado" else "Falta Token (hay un placeholder sin reemplazar)"

        // 4. GitHub, con la misma corrección.
        estados["GitHub"] = if (esCredencialReal(GITHUB_TOKEN, "ghp_tu_token")) "Token propio configurado" else "Falta Token (hay un placeholder sin reemplazar)"

        // 5. Google AI Edge. Se sondea el paquete de AICore en vez de afirmarlo: si está, sigue sin
        //    significar que esta build ejecute algo en él (ninguna ruta carga pesos en el proceso).
        estados["AI Edge (Gemini Nano)"] = if (esAppInstalada("com.google.android.aicore"))
            "AICore presente en el dispositivo (esta build no ejecuta inferencia en él)"
        else
            "No disponible: no se detecta el paquete de AICore"

        return estados
    }

    /**
     * Una credencial cuenta como configurada sólo si salió del placeholder. `prefijo` es el arranque
     * del valor de fábrica; nunca se devuelve ni se registra el contenido del token.
     */
    private fun esCredencialReal(valor: String, prefijoPlaceholder: String): Boolean =
        valor.isNotBlank() && !valor.startsWith(prefijoPlaceholder)

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

    // --- CLIENTE Gemini API (RED), NO INFERENCIA EN EL DISPOSITIVO ---
    // El comentario original decía "Se conecta directamente a AICore en el procesador del teléfono".
    // No: `com.google.ai.client.generativeai` es el cliente de la API de Gemini por HTTPS, y
    // "gemini-nano" no es un modelo que ese cliente admita. Además la clave era un placeholder, así
    // que la llamada sólo podía fallar... después de haber sacado una petición a la red.
    suspend fun aiEdgeProcesarLocal(promptCorto: String): String {
        if (!esCredencialReal(AI_EDGE_KEY, "TU_API_KEY")) {
            return "[AI EDGE NO USADO] No hay credencial de Gemini API configurada y, aunque la " +
                    "hubiera, este camino es una llamada de red: no ejecuta ningún modelo dentro del " +
                    "teléfono. La inferencia local de CLAW pasa por el runtime HTTP configurado."
        }
        return try {
            val model = GenerativeModel(
                modelName = "gemini-pro",
                apiKey = AI_EDGE_KEY
            )
            val response = model.generateContent(promptCorto)
            response.text ?: "La API de Gemini no devolvió texto (respuesta remota, no local)."
        } catch (e: Exception) {
            "Error en la llamada remota de Gemini: ${e.localizedMessage}"
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
