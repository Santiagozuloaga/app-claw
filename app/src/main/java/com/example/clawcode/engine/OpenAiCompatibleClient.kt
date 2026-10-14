package com.example.clawcode.engine

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val OPENAI_JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/** Error del proveedor compatible con OpenAI. Se propaga a la consola con la causa real. */
class OpenAiInferenceException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Cliente contra cualquier endpoint compatible con OpenAI (`/v1/chat/completions`, `/v1/models`).
 *
 * Existe por un motivo concreto: es la pieza que convierte en verdad el "independiente de Ollama"
 * que el proyecto llevaba escrito desde su primera especificación (línea 7215 del transcript de
 * AI Studio). Cubre, sin cambiar una línea de código, un `llama.cpp` corriendo en el propio
 * teléfono, un servidor en el PC, Groq, OpenRouter o AnythingLLM.
 *
 * Reglas de honestidad, las mismas que `OllamaClient`:
 * - El servidor no responde o devuelve error -> `Result.failure` con la causa. Nunca texto de relleno.
 * - Lo que este protocolo no expone (peso del modelo, cuantización, RAM cargada) se marca como
 *   desconocido con -1 o con `failure`. Un 0 se leería como "0 MB medidos".
 * - `thinking` queda vacío: OpenAI-compatible no tiene ese canal y rellenarlo sería inventarlo.
 */
class OpenAiCompatibleClient(
    private val apiKey: String = "",
    private val connectTimeoutSeconds: Long = 10,
    private val readTimeoutSeconds: Long = 240
) : ProveedorInferencia {

    override val nombre: String = "openai-compatible"

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun cabeceraAuth(builder: Request.Builder): Request.Builder =
        if (apiKey.isBlank()) builder else builder.header("Authorization", "Bearer $apiKey")

    override suspend fun chat(
        request: OllamaChatRequest,
        onToken: ((String) -> Unit)?
    ): Result<OllamaChatReply> = withContext(Dispatchers.IO) {
        try {
            Result.success(executeChat(request, onToken))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: OpenAiInferenceException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(
                OpenAiInferenceException(
                    "No se pudo alcanzar el proveedor ${request.hostUrl.trimEnd('/')}: " +
                        "${e.javaClass.simpleName}: ${e.message}",
                    e
                )
            )
        }
    }

    private fun executeChat(
        request: OllamaChatRequest,
        onToken: ((String) -> Unit)?
    ): OllamaChatReply {
        if (request.model.isBlank()) {
            throw OpenAiInferenceException("El nombre del modelo está vacío")
        }

        // El payload se construye con el serializador, no con concatenación: un userText con
        // comillas o saltos de línea habría roto el JSON y abierto una inyección de campos.
        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", onToken != null)
            put(
                "messages",
                buildJsonArray {
                    if (request.systemPrompt.isNotBlank()) add(rol("system", request.systemPrompt))
                    request.history.forEach { add(rol(it.role.lowercase(), it.content)) }
                    add(rol("user", request.userText))
                }
            )
            put("temperature", request.temperature.toDouble())
            put("top_p", request.topP.toDouble())
            if (request.maxTokens > 0) put("max_tokens", request.maxTokens)
            if (request.stopTokens.isNotEmpty()) {
                put("stop", buildJsonArray { request.stopTokens.forEach { add(it) } })
            }
            // Sin include_usage este proveedor no devuelve conteo de tokens, y entonces los
            // tokens/segundo no se pueden medir: mejor reportar 0 que estimarlos a ojo.
            if (onToken != null) {
                put("stream_options", buildJsonObject { put("include_usage", true) })
            }
        }

        val url = request.hostUrl.trim().trimEnd('/') + "/v1/chat/completions"
        val httpRequest = cabeceraAuth(
            Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream, application/json")
                .post(payload.toString().toRequestBody(OPENAI_JSON_MEDIA))
        ).build()

        val startedAt = System.currentTimeMillis()
        val acumulado = StringBuilder()
        var modelo = request.model
        var fin = ""
        var promptTokens = 0
        var completionTokens = 0

        httpClient.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val raw = response.body?.string().orEmpty()
                val detalle = runCatching {
                    json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject
                        ?.get("message")?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: raw.take(300)
                throw OpenAiInferenceException(
                    "El proveedor devolvió HTTP ${response.code} en $url: $detalle"
                )
            }

            val lector = BufferedReader(InputStreamReader(response.body?.byteStream(), "UTF-8"))
            while (true) {
                val linea = lector.readLine() ?: break
                if (linea.isBlank()) continue
                val candidato = if (linea.startsWith("data:")) {
                    val dato = linea.removePrefix("data:").trim()
                    if (dato == "[DONE]") break
                    if (!dato.startsWith("{")) continue
                    dato
                } else if (linea.trimStart().startsWith("{")) {
                    // Servidores que ignoran "stream" y devuelven un único JSON completo.
                    linea
                } else continue

                val objeto = runCatching {
                    json.parseToJsonElement(candidato).jsonObject
                }.getOrNull() ?: continue

                (objeto["model"] as? JsonPrimitive)?.contentOrNull?.let { modelo = it }
                objeto["choices"]?.jsonArray?.firstChoice()?.let { choice ->
                    val trozo = (choice["delta"] as? JsonObject)?.text()
                        ?: (choice["message"] as? JsonObject)?.text()
                    if (!trozo.isNullOrEmpty()) {
                        acumulado.append(trozo)
                        onToken?.invoke(trozo)
                    }
                    (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
                        ?.takeIf { it.isNotBlank() }?.let { fin = it }
                }
                (objeto["usage"] as? JsonObject)?.let { uso ->
                    (uso["prompt_tokens"] as? JsonPrimitive)?.intOrNull?.let { promptTokens = it }
                    (uso["completion_tokens"] as? JsonPrimitive)?.intOrNull
                        ?.let { completionTokens = it }
                }
            }
        }

        val latencyMs = System.currentTimeMillis() - startedAt
        if (acumulado.isEmpty()) {
            throw OpenAiInferenceException(
                "El proveedor respondió sin contenido. Revisa que el modelo '$modelo' exista y " +
                    "que la clave de API tenga acceso a él."
            )
        }
        val tokensPorSegundo =
            if (completionTokens > 0 && latencyMs > 0) completionTokens * 1000f / latencyMs else 0f

        return OllamaChatReply(
            text = acumulado.toString(),
            model = modelo,
            thinking = "",
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            tokensPerSecond = tokensPorSegundo,
            latencyMs = latencyMs,
            stopReason = fin.ifBlank { "stop" }
        )
    }

    override suspend fun listCompletionModels(hostUrl: String): Result<List<OllamaModelInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val url = hostUrl.trim().trimEnd('/') + "/v1/models"
                val httpRequest = cabeceraAuth(
                    Request.Builder().url(url).header("Accept", "application/json")
                ).build()

                httpClient.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw OpenAiInferenceException("HTTP ${response.code} al listar modelos en $url")
                    }
                    val cuerpo = response.body?.string().orEmpty()
                    val raiz = json.parseToJsonElement(cuerpo).jsonObject
                    val datos = (raiz["data"] ?: raiz["models"]) as? JsonArray
                        ?: throw OpenAiInferenceException(
                            "La respuesta de /v1/models no trae una lista de modelos reconocible."
                        )

                    val modelos = datos.mapNotNull { elemento ->
                        val obj = elemento as? JsonObject ?: return@mapNotNull null
                        val id = (obj["id"] ?: obj["name"]) as? JsonPrimitive
                        val nombre = id?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        val contexto =
                            (obj["context_length"] as? JsonPrimitive)?.intOrNull ?: 0
                        OllamaModelInfo(
                            name = nombre,
                            // -1 = desconocido: este protocolo no comunica el tamaño del peso.
                            sizeBytes = -1L,
                            isCloudStub = false,
                            quantType = "",
                            contextLength = contexto
                        )
                    }
                    if (modelos.isEmpty()) {
                        throw OpenAiInferenceException(
                            "El proveedor respondió a /v1/models con una lista vacía."
                        )
                    }
                    Result.success(modelos)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Result.failure(
                    OpenAiInferenceException(
                        "No se pudo listar modelos en ${hostUrl.trimEnd('/')}: ${e.message}", e
                    )
                )
            }
        }

    /**
     * OpenAI-compatible no tiene equivalente de `/api/ps` de Ollama: no hay forma de saber cuánta
     * memoria tiene cargada el servidor. Se reporta el fallo para que la tarjeta "Tokens" siga
     * diciendo "sin medir" en vez de un número.
     */
    override suspend fun loadedModelFootprint(
        hostUrl: String,
        model: String
    ): Result<OllamaModelFootprint> = Result.failure(
        OpenAiInferenceException(
            "El proveedor $nombre no expone la huella de memoria del modelo cargado: " +
                "no hay dato que mostrar para '$model'."
        )
    )

    private fun rol(nombreRol: String, contenido: String): JsonObject = buildJsonObject {
        put("role", nombreRol)
        put("content", contenido)
    }

    private fun JsonArray.firstChoice(): JsonObject? =
        (firstOrNull { it is JsonObject } as? JsonObject)

    private fun JsonObject.text(): String? =
        (this["content"] as? JsonPrimitive)?.contentOrNull
}
