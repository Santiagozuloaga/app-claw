package com.example.clawcode.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class OllamaChatMessage(val role: String, val content: String)

data class OllamaChatReply(
    val text: String,
    val model: String,
    val thinking: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val tokensPerSecond: Float,
    val latencyMs: Long,
    val stopReason: String
)

data class OllamaModelInfo(
    val name: String,
    val sizeBytes: Long,
    val isCloudStub: Boolean,
    val quantType: String = "",
    val contextLength: Int = 0
)

data class OllamaModelFootprint(
    val model: String,
    val ramMb: Float,
    val vramMb: Float,
    val contextLength: Int,
    val quantType: String
)

class OllamaInferenceException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class OllamaChatRequest(
    val hostUrl: String,
    val model: String,
    val systemPrompt: String,
    val history: List<OllamaChatMessage>,
    val userText: String,
    val temperature: Float,
    val topP: Float,
    val stopTokens: List<String>,
    val maxTokens: Int
)

/**
 * Cliente HTTP real contra un servidor Ollama (local o en la LAN del dispositivo).
 *
 * Contrato verificado contra Ollama 0.34.4, endpoint POST /api/chat:
 * los modelos con capacidad "thinking" devuelven message.content vacío y rellenan
 * message.thinking cuando el presupuesto de tokens se agota en el razonamiento,
 * por eso se envía think=false de forma explícita.
 */
class OllamaClient(
    private val connectTimeoutSeconds: Long = 10,
    private val readTimeoutSeconds: Long = 240
) {

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

    suspend fun chat(
        request: OllamaChatRequest,
        onToken: ((String) -> Unit)? = null
    ): Result<OllamaChatReply> = withContext(Dispatchers.IO) {
        try {
            Result.success(executeChat(request, onToken))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: OllamaInferenceException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(
                OllamaInferenceException(
                    "No se pudo alcanzar el runtime local en ${request.hostUrl.trimEnd('/')}: ${e.javaClass.simpleName}: ${e.message}",
                    e
                )
            )
        }
    }

    /** Modelos realmente descargados en el servidor, ordenados del más ligero al más pesado. */
    suspend fun listCompletionModels(hostUrl: String): Result<List<OllamaModelInfo>> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(executeListModels(hostUrl))
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Result.failure(
                    OllamaInferenceException(
                        "No se pudo listar modelos en ${hostUrl.trimEnd('/')}: ${e.message}",
                        e
                    )
                )
            }
        }

    private fun executeChat(
        request: OllamaChatRequest,
        onToken: ((String) -> Unit)?
    ): OllamaChatReply {
        val url = "${request.hostUrl.trim().trimEnd('/')}/api/chat"
        if (request.model.isBlank()) {
            throw OllamaInferenceException("El nombre de modelo Ollama está vacío")
        }

        val streaming = onToken != null

        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", streaming)
            put("think", false)
            put(
                "messages",
                buildJsonArray {
                    if (request.systemPrompt.isNotBlank()) {
                        add(
                            buildJsonObject {
                                put("role", "system")
                                put("content", request.systemPrompt)
                            }
                        )
                    }
                    request.history.forEach { message ->
                        add(
                            buildJsonObject {
                                put("role", message.role)
                                put("content", message.content)
                            }
                        )
                    }
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", request.userText)
                        }
                    )
                }
            )
            put(
                "options",
                buildJsonObject {
                    put("temperature", request.temperature.toDouble())
                    put("top_p", request.topP.toDouble())
                    put("num_predict", request.maxTokens)
                    if (request.stopTokens.isNotEmpty()) {
                        put("stop", buildJsonArray { request.stopTokens.forEach { add(it) } })
                    }
                }
            )
        }

        val httpCall = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val startedAt = System.currentTimeMillis()
        httpClient.newCall(httpCall).execute().use { response ->
            if (!response.isSuccessful) {
                val rawBody = response.body?.string().orEmpty()
                val serverError = runCatching {
                    json.parseToJsonElement(rawBody).jsonObject["error"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                throw OllamaInferenceException(
                    if (!serverError.isNullOrBlank()) "Ollama HTTP ${response.code}: $serverError"
                    else "Ollama HTTP ${response.code} en $url"
                )
            }

            val tokenCallback = onToken
            return if (tokenCallback != null) {
                readStreamedReply(response, request, startedAt, tokenCallback)
            } else {
                val rawBody = response.body?.string().orEmpty()
                parseCompleteReply(rawBody, request, System.currentTimeMillis() - startedAt)
            }
        }
    }

    private fun parseCompleteReply(
        rawBody: String,
        request: OllamaChatRequest,
        latencyMs: Long
    ): OllamaChatReply {
        val root = runCatching { json.parseToJsonElement(rawBody).jsonObject }.getOrElse {
            throw OllamaInferenceException("La respuesta de Ollama no es JSON válido", it)
        }

        val message = root["message"]?.jsonObject
            ?: throw OllamaInferenceException("La respuesta de Ollama no incluye el campo 'message'")

        val content = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val thinking = message["thinking"]?.jsonPrimitive?.contentOrNull
            ?: message["reasoning"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val answeredModel = root["model"]?.jsonPrimitive?.contentOrNull ?: request.model
        val stopReason = root["done_reason"]?.jsonPrimitive?.contentOrNull ?: "unknown"

        if (content.isBlank()) {
            throw OllamaInferenceException(emptyContentMessage(answeredModel, stopReason, thinking))
        }

        return buildReply(
            text = content.trim(),
            model = answeredModel,
            thinking = thinking,
            metrics = root,
            completionTokens = root["eval_count"]?.jsonPrimitive?.intOrNull ?: 0,
            latencyMs = latencyMs,
            stopReason = stopReason
        )
    }

    /**
     * Ollama responde el modo stream como NDJSON: una línea JSON por token generado.
     * Cada delta se entrega a [onToken] en el momento en que llega por el socket y el
     * turno se reconstruye al final, por lo que la métrica (eval_count/eval_duration)
     * se lee del chunk terminal, que es el único que la trae.
     */
    private fun readStreamedReply(
        response: Response,
        request: OllamaChatRequest,
        startedAt: Long,
        onToken: (String) -> Unit
    ): OllamaChatReply {
        val bodyStream = response.body?.byteStream()
            ?: throw OllamaInferenceException("Ollama devolvió un cuerpo vacío en modo stream")

        val content = StringBuilder()
        val thinking = StringBuilder()
        var lastChunk: JsonObject? = null
        var chunksReceived = 0
        var sawTerminalChunk = false
        var completeJsonFallback: OllamaChatReply? = null

        BufferedReader(InputStreamReader(bodyStream, Charsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val chunk = runCatching { json.parseToJsonElement(line).jsonObject }.getOrElse {
                    // Un servidor que ignora stream:true responde un único JSON completo (les
                    // pasa a ciertos proxies). Mientras no se haya interpretado ningún chunk,
                    // el cuerpo entero sigue siendo una respuesta válida: se lee de una pieza.
                    if (chunksReceived > 0) {
                        throw OllamaInferenceException(
                            "El stream de Ollama envió una línea que no es JSON válida: ${line.take(120)}", it
                        )
                    }
                    val whole = (listOf(line) + generateSequence { reader.readLine() }.toList())
                        .joinToString("\n")
                    completeJsonFallback = parseCompleteReply(whole, request, System.currentTimeMillis() - startedAt)
                        .also { onToken(it.text) }
                    return@use
                }
                chunksReceived++
                lastChunk = chunk

                val serverError = chunk["error"]?.jsonPrimitive?.contentOrNull
                if (!serverError.isNullOrBlank()) {
                    throw OllamaInferenceException("Ollama interrumpió el stream: $serverError")
                }

                val message = chunk["message"]?.jsonObject
                val delta = message?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (delta.isNotEmpty()) {
                    content.append(delta)
                    onToken(delta)
                }
                val reasoningDelta = message?.get("thinking")?.jsonPrimitive?.contentOrNull
                    ?: message?.get("reasoning")?.jsonPrimitive?.contentOrNull
                if (!reasoningDelta.isNullOrEmpty()) thinking.append(reasoningDelta)

                if (chunk["done"]?.jsonPrimitive?.booleanOrNull == true) {
                    sawTerminalChunk = true
                    break
                }
            }
        }

        completeJsonFallback?.let { return it }

        val metrics = lastChunk
            ?: throw OllamaInferenceException(
                "El stream de ${request.hostUrl.trimEnd('/')} se cerró sin ningún chunk (0 líneas NDJSON)"
            )
        val answeredModel = metrics["model"]?.jsonPrimitive?.contentOrNull ?: request.model
        val stopReason = metrics["done_reason"]?.jsonPrimitive?.contentOrNull ?: "stream_end"

        // Un stream que se cierra sin done=true es una generación truncada (el servidor se
        // cayó, se acabó el tiempo de lectura, etc.). Mostrarse como respuesta completa haría
        // pasar un texto incompleto por válido, así que se propaga el fallo y la burbuja
        // provisional del chat se reemplaza por la reserva estática.
        if (!sawTerminalChunk) {
            throw OllamaInferenceException(
                "El stream de '$answeredModel' se cerró tras ${content.length} caracteres sin el " +
                        "chunk final (done=true): la generación quedó incompleta."
            )
        }

        if (content.isBlank()) {
            throw OllamaInferenceException(emptyContentMessage(answeredModel, stopReason, thinking.toString()))
        }

        return buildReply(
            text = content.toString().trim(),
            model = answeredModel,
            thinking = thinking.toString(),
            metrics = metrics,
            // En modo stream el chunk terminal repite eval_count; si faltara, el número de
            // deltas recibidos es la medida real de tokens generados en este turno.
            completionTokens = metrics["eval_count"]?.jsonPrimitive?.intOrNull
                ?.takeIf { it > 0 } ?: chunksReceived,
            latencyMs = System.currentTimeMillis() - startedAt,
            stopReason = stopReason
        )
    }

    private fun emptyContentMessage(model: String, stopReason: String, thinking: String): String {
        val hint = if (thinking.isNotBlank()) {
            " El presupuesto de tokens se consumió en razonamiento (done_reason=$stopReason); aumenta maxTokens."
        } else ""
        return "El modelo '$model' devolvió contenido vacío (done_reason=$stopReason).$hint"
    }

    private fun buildReply(
        text: String,
        model: String,
        thinking: String,
        metrics: JsonObject,
        completionTokens: Int,
        latencyMs: Long,
        stopReason: String
    ): OllamaChatReply {
        val evalDurationNs = metrics["eval_duration"]?.jsonPrimitive?.longOrNull ?: 0L
        val tokensPerSecond = if (evalDurationNs > 0L) {
            (completionTokens.toDouble() * 1_000_000_000.0 / evalDurationNs.toDouble()).toFloat()
        } else {
            0f
        }
        return OllamaChatReply(
            text = text,
            model = model,
            thinking = thinking,
            promptTokens = metrics["prompt_eval_count"]?.jsonPrimitive?.intOrNull ?: 0,
            completionTokens = completionTokens,
            tokensPerSecond = tokensPerSecond,
            latencyMs = latencyMs,
            stopReason = stopReason
        )
    }

    /** Memoria real reservada por el modelo cargado, obtenida de GET /api/ps. */
    suspend fun loadedModelFootprint(hostUrl: String, model: String): Result<OllamaModelFootprint> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(executeFootprint(hostUrl, model))
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Result.failure(OllamaInferenceException("No se pudo consultar /api/ps: ${e.message}", e))
            }
        }

    private fun executeFootprint(hostUrl: String, model: String): OllamaModelFootprint {
        val url = "${hostUrl.trim().trimEnd('/')}/api/ps"
        val httpCall = Request.Builder().url(url).header("Accept", "application/json").get().build()

        httpClient.newCall(httpCall).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw OllamaInferenceException("Ollama HTTP ${response.code} en $url")
            }
            val models = runCatching { json.parseToJsonElement(rawBody).jsonObject["models"]?.jsonArray }
                .getOrNull()
                ?: throw OllamaInferenceException("La respuesta de /api/ps no incluye 'models'")

            val entry = models.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                .firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == model }
                ?: throw OllamaInferenceException("El modelo '$model' no está cargado en el servidor")

            val sizeBytes = entry["size"]?.jsonPrimitive?.longOrNull ?: 0L
            val vramBytes = entry["size_vram"]?.jsonPrimitive?.longOrNull ?: 0L
            return OllamaModelFootprint(
                model = model,
                ramMb = sizeBytes / (1024f * 1024f),
                vramMb = vramBytes / (1024f * 1024f),
                contextLength = entry["context_length"]?.jsonPrimitive?.intOrNull ?: 0,
                quantType = entry["details"]?.jsonObject
                    ?.get("quantization_level")?.jsonPrimitive?.contentOrNull ?: "desconocida"
            )
        }
    }

    private fun executeListModels(hostUrl: String): List<OllamaModelInfo> {
        val url = "${hostUrl.trim().trimEnd('/')}/api/tags"
        val httpCall = Request.Builder().url(url).header("Accept", "application/json").get().build()

        httpClient.newCall(httpCall).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw OllamaInferenceException("Ollama HTTP ${response.code} en $url")
            }
            val models = runCatching { json.parseToJsonElement(rawBody).jsonObject["models"]?.jsonArray }
                .getOrNull()
                ?: throw OllamaInferenceException("La respuesta de /api/tags no incluye 'models'")

            return models.mapNotNull { element ->
                val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L
                // Versiones recientes de Ollama declaran capabilities en /api/tags
                // (medido en 0.34.4: ["completion","tools"]). Las antiguas no lo envían, así que
                // sólo se descarta un modelo cuando el servidor afirma explícitamente que no
                // puede generar texto; si el campo falta, seguiría siendo un candidato válido.
                val capabilities = obj["capabilities"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    .orEmpty()
                if (capabilities.isNotEmpty() && !capabilities.contains("completion")) return@mapNotNull null
                // Los modelos ":cloud" son punteros remotos de pocos bytes, no pesos locales.
                val isCloudStub = name.contains("cloud", ignoreCase = true) || size < MIN_LOCAL_MODEL_BYTES
                val details = runCatching { obj["details"]?.jsonObject }.getOrNull()
                OllamaModelInfo(
                    name = name,
                    sizeBytes = size,
                    isCloudStub = isCloudStub,
                    quantType = details?.get("quantization_level")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    contextLength = details?.get("context_length")?.jsonPrimitive?.intOrNull ?: 0
                )
            }.sortedBy { it.sizeBytes }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MIN_LOCAL_MODEL_BYTES = 10L * 1024L * 1024L
    }
}
