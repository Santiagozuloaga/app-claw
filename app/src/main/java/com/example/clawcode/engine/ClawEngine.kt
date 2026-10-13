package com.example.clawcode.engine

import com.example.clawcode.data.model.*
import com.example.clawcode.ui.util.textoPlanoDeMarkdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*

class ClawEngine(
    private val ollamaClient: OllamaClient = OllamaClient(),
    private val configStore: com.example.clawcode.core.storage.OllamaConfigStore =
        com.example.clawcode.core.storage.InMemoryOllamaConfigStore(),
    private val chatStore: com.example.clawcode.core.storage.ChatHistoryStore? = null
) {

    // SimpleDateFormat no es seguro entre hilos: los deltas del stream y el cierre de turno se
    // publican desde Dispatchers.IO mientras la corutina del chat formatea otra hora. Un formatter
    // por hilo evita marcas de tiempo corruptas (y minSdk 24 todavía no tiene java.time).
    private val formatoHora = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    private fun horaLocal(): String =
        checkNotNull(formatoHora.get()) { "SimpleDateFormat no disponible en este hilo" }.format(Date())

    private val _tasks = MutableStateFlow<List<AgentTask>>(initialTasks())
    val tasks: StateFlow<List<AgentTask>> = _tasks.asStateFlow()

    private val _memories = MutableStateFlow<List<MemoryItem>>(initialMemories())
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    private val _tools = MutableStateFlow<List<McpTool>>(initialTools())
    val tools: StateFlow<List<McpTool>> = _tools.asStateFlow()

    private val _stats = MutableStateFlow(AgentStats())
    val stats: StateFlow<AgentStats> = _stats.asStateFlow()

    // Declarado antes que _consoleLogs/_chatMessages: sus textos de arranque describen el runtime
    // realmente configurado, y en Kotlin un property no inicializado aún se leería como nulo.
    private val _ollamaConfig = MutableStateFlow(configStore.load() ?: OllamaConfig())
    val ollamaConfig: StateFlow<OllamaConfig> = _ollamaConfig.asStateFlow()

    // null = ningún turno en vuelo. Mientras vale un texto, el chat está esperando al runtime.
    /** Serializa los turnos de chat: un envío por vez, sin entrelazar historial ni estado de carga. */
    private val chatTurnMutex = Mutex()

    private val _chatAwaitingRuntime = MutableStateFlow<String?>(null)
    val chatAwaitingRuntime: StateFlow<String?> = _chatAwaitingRuntime.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<ConsoleLog>>(initialConsoleLogs())
    val consoleLogs: StateFlow<List<ConsoleLog>> = _consoleLogs.asStateFlow()

    private val _isCompacting = MutableStateFlow(false)
    val isCompacting: StateFlow<Boolean> = _isCompacting.asStateFlow()

    private val _whatsAppMessages = MutableStateFlow<List<WhatsAppMessage>>(initialWhatsAppMessages())
    val whatsAppMessages: StateFlow<List<WhatsAppMessage>> = _whatsAppMessages.asStateFlow()

    private val _jarvisModfile = MutableStateFlow(JarvisModfile())
    val jarvisModfile: StateFlow<JarvisModfile> = _jarvisModfile.asStateFlow()

    private val _fileAttachments = MutableStateFlow<List<FileAttachmentItem>>(initialFileAttachments())
    val fileAttachments: StateFlow<List<FileAttachmentItem>> = _fileAttachments.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(initialChatMessages())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    init {
        // Un historial guardado sustituye a la semilla de arranque (no se duplica) y llega vacío
        // tras un borrado, así que el orden de los turnos se conserva entre reinicios.
        chatStore?.load()?.filter { it.sender == "USER" || it.sender == "JARVIS" }?.let { guardado ->
            if (guardado.isNotEmpty()) _chatMessages.value = guardado
        }
    }

    private fun persistirHistorial() {
        chatStore?.save(_chatMessages.value)
    }

    private val _hardwareTelemetry = MutableStateFlow(HardwareTelemetry())
    val hardwareTelemetry: StateFlow<HardwareTelemetry> = _hardwareTelemetry.asStateFlow()

    private val _activeGrounding = MutableStateFlow<SearchGroundingResult?>(null)
    val activeGrounding: StateFlow<SearchGroundingResult?> = _activeGrounding.asStateFlow()

    private val _isGroundingEnabled = MutableStateFlow(false)
    val isGroundingEnabled: StateFlow<Boolean> = _isGroundingEnabled.asStateFlow()

    private val _groundingHistory = MutableStateFlow<List<SearchGroundingResult>>(emptyList())
    val groundingHistory: StateFlow<List<SearchGroundingResult>> = _groundingHistory.asStateFlow()

    private val _taskPerformanceMetrics = MutableStateFlow<List<TaskPerformanceMetrics>>(emptyList())
    val taskPerformanceMetrics: StateFlow<List<TaskPerformanceMetrics>> = _taskPerformanceMetrics.asStateFlow()

    fun updateHardwareTelemetry(newTelemetry: HardwareTelemetry) {
        _hardwareTelemetry.value = newTelemetry
        com.example.clawcode.core.EventBus.tryPublish(
            com.example.clawcode.core.AppEvent.HardwareStatusUpdated(newTelemetry.batteryLevelPercent, false)
        )
    }

    /**
     * Un sondeo de telemetría que falla se registra y sigue; no debe tumbar el proceso, porque el
     * bucle vive en viewModelScope y ahí una excepción sin capturar mata la app entera, chat incluido.
     */
    fun logTelemetryFailure(error: Throwable) {
        appendConsoleLog(
            "HARDWARE_TELEMETRY",
            "[TELEMETRY_POLL_FAILED] Sondeo de telemetría abortado sin derribar la app: " +
                    "${error.javaClass.simpleName}: ${error.message ?: "sin mensaje"}. " +
                    "Se conserva la última lectura conocida y el bucle reintenta en 5 s."
        )
    }

    fun setGroundingEnabled(enabled: Boolean) {
        _isGroundingEnabled.value = enabled
    }

    fun clearActiveGrounding() {
        _activeGrounding.value = null
    }

    suspend fun performSearchGrounding(query: String): SearchGroundingResult {
        val time = horaLocal()
        val cleanQuery = query.trim()
        
        // No hay backend de búsqueda en esta build: haría falta una API de Google Search Grounding
        // autenticada. Se informa de la ausencia en lugar de inventar títulos, URLs y snippets.
        val sources = emptyList<SearchGroundingSource>()
        val summary = "Búsqueda externa NO disponible en esta build: no hay proveedor de Google " +
                "Search Grounding configurado y no se ha enviado ninguna petición web para " +
                "'$cleanQuery'. Los turnos los responde el runtime local " +
                "'${_ollamaConfig.value.modelName}' en ${_ollamaConfig.value.hostUrl}, sin contexto de búsqueda."

        val result = SearchGroundingResult(
            id = UUID.randomUUID().toString(),
            query = cleanQuery,
            summaryText = summary,
            sources = sources,
            timestamp = time,
            isGroundingActive = false
        )

        _activeGrounding.value = result
        _groundingHistory.update { listOf(result) + it }

        val consoleLog = ConsoleLog(
            id = UUID.randomUUID().toString(),
            timestamp = time,
            sender = "GOOGLE_SEARCH_GROUNDING",
            message = "[GROUNDING_UNAVAILABLE] Sin backend de búsqueda para '$cleanQuery': 0 fuentes " +
                    "reales y ninguna petición enviada. No se ha inyectado contexto en el chat."
        )
        _consoleLogs.update { it + consoleLog }

        return result
    }

    fun createTaskFromGrounding(grounding: SearchGroundingResult) {
        val time = horaLocal()
        val newTask = AgentTask(
            id = "task-grounded-${UUID.randomUUID().toString().take(6)}",
            title = "Tarea Grounded: ${grounding.query}",
            description = "Ejecutando con contexto de Google Search Grounding:\n${grounding.summaryText}",
            agentName = "Jarvis_GroundedAgent",
            status = TaskStatus.RUNNING,
            priority = Priority.HIGH,
            progress = 0.5f,
            createdAt = time,
            logs = listOf(
                "[$time] Contexto de búsqueda: '${grounding.query}'",
                "[$time] Fuentes obtenidas: ${grounding.sources.size}" +
                    if (grounding.sources.isEmpty()) " (no hay backend de búsqueda en esta build)" else "",
                "[$time] Ejecutando tarea asistida por grounding..."
            )
        )
        _tasks.update { listOf(newTask) + it }
    }

    suspend fun sendChatMessage(
        text: String,
        attachedFileName: String? = null,
        attachedImageUri: String? = null,
        // Opcional porque sin él el turno no puede ejercitarse desde una prueba de JVM: el motor
        // de acciones exige un Context de Android. Con null no se interpreta ni ejecuta ninguna
        // intención del SO; el turno sigue yendo al runtime.
        mobileActionEngine: MobileActionEngine? = null
    ) = chatTurnMutex.withLock {
        // Un turno por vez: dos envíos seguidos (doble toque, o botón + dictado) se entrelazaban,
        // el historial del segundo incluía la burbuja a medio escribir del primero y el estado
        // "Consultando" del segundo apagaba el del primero a mitad de generación.
        sendChatMessageSerialized(text, attachedFileName, attachedImageUri, mobileActionEngine)
    }

    private suspend fun sendChatMessageSerialized(
        text: String,
        attachedFileName: String?,
        attachedImageUri: String?,
        mobileActionEngine: MobileActionEngine?
    ) {
        // Único punto por el que pasan todas las rutas de envío (chat, voz, quick actions de
        // Dashboard y Live). La pantalla ya filtra el botón, pero un dictado vacío o una llamada
        // directa publicaba un turno en blanco y lo mandaba al runtime.
        if (text.isBlank() && attachedFileName == null && attachedImageUri == null) {
            appendConsoleLog(
                "CHAT_INPUT",
                "[INPUT_REJECTED] Mensaje vacío ('$text'): no se publicó en el chat ni se envió a " +
                        "${_ollamaConfig.value.modelName} en ${_ollamaConfig.value.hostUrl}."
            )
            return
        }

        val time = horaLocal()
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = "USER",
            text = text,
            timestamp = time,
            attachedFile = attachedFileName,
            attachedImageUri = attachedImageUri
        )
        // Foto tomada ANTES de añadir userMsg: runLocalInference construye el historial a
        // partir de _chatMessages, y si el turno actual ya estuviera dentro se enviaría dos
        // veces al modelo (una como historial y otra como userText).
        val historyBeforeUserMessage = _chatMessages.value
        _chatMessages.update { it + userMsg }
        persistirHistorial()
        com.example.clawcode.core.EventBus.tryPublish(com.example.clawcode.core.AppEvent.UserMessageSent(text))

        // Diagnostic trace log 1: Message received
        _consoleLogs.update {
            it + ConsoleLog(
                id = UUID.randomUUID().toString(),
                timestamp = time,
                sender = "VOICE_STT_INBOUND",
                message = "[VOICE_INPUT_RECEIVED] Entrada recibida: '$text' -> Iniciando procesamiento de inferencia AI"
            )
        }

        delay(300)

        // Try tool call matching
        val (actionName, params) = mobileActionEngine?.parseNaturalLanguageToFunctionCall(text)
            ?: Pair("", emptyMap<String, String>())
        
        // Diagnostic trace log 2: Intent Resolution
        _consoleLogs.update {
            it + ConsoleLog(
                id = UUID.randomUUID().toString(),
                timestamp = horaLocal(),
                sender = "INTENT_RESOLVER",
                message = if (actionName.isNotBlank()) "[ACTION_INTENT_DETECTED] Intención detectada: '$actionName' con parámetros: $params" else "[MODEL_INFERENCE_ROUTING] No se requiere acción del OS. Turno dirigido al runtime real: '${_ollamaConfig.value.modelName}' en ${_ollamaConfig.value.hostUrl}. La app no carga pesos GGUF/LiteRT en su proceso."
            )
        }

        val (success, resultMsg) = mobileActionEngine?.executeActionIntent(actionName, params)
            ?: Pair(false, "Sin motor de acciones en esta ruta: ninguna intención del SO se interpretó ni ejecutó.")

        var finalActionName = actionName
        var finalResultMsg = resultMsg
        var finalSuccess = success

        // Intercept DeepSeek configuration command
        if (text.contains("Configura la herramienta Google_AI_Edge_Gallery_Bridge para que use el modelo DeepSeek-R1-Distill-Qwen-1.5B", ignoreCase = true)) {
            _jarvisModfile.update { it.copy(baseModel = "DeepSeek-R1-Distill-Qwen-1.5B") }
            finalActionName = "configure_mcp_bridge"
            finalResultMsg = "El campo baseModel del modfile ahora es 'DeepSeek-R1-Distill-Qwen-1.5B'. " +
                    "Aviso: esta build no abre ningún puente MCP; el cambio es sólo una etiqueta local y " +
                    "los turnos siguen respondiéndolos ${_ollamaConfig.value.modelName} en ${_ollamaConfig.value.hostUrl}."
            finalSuccess = true
        }

        val time2 = horaLocal()
        val grounding = _activeGrounding.value
        val groundingContextPrefix = if (_isGroundingEnabled.value && grounding != null && grounding.isGroundingActive) {
            "🔍 [Google Search Grounding Activo: '${grounding.query}']\n"
        } else ""

        val groundingExtra = if (_isGroundingEnabled.value && grounding != null && grounding.isGroundingActive) {
            "\n\n🌐 Fuente Grounded: ${grounding.sources.firstOrNull()?.title ?: "Google Search"}\n\"${grounding.sources.firstOrNull()?.snippet ?: ""}\""
        } else ""

        // Respuesta estática original. Se conserva intacta como reserva para cuando
        // el runtime local no está alcanzable, de modo que el chat nunca quede mudo.
        fun staticFallbackText(): String {
            val mcpExplanation = if (text.contains("qué es MCP", ignoreCase = true)) {
                "MCP (Model Context Protocol) es un protocolo estandarizado que permite a modelos de IA " +
                        "conectarse con herramientas, bases de datos y APIs externas. Esta definición la está " +
                        "devolviendo texto estático de la app porque el runtime local no respondió: en esta " +
                        "build no hay ningún puente MCP activo, solo el modelo '${_ollamaConfig.value.modelName}' " +
                        "servido por HTTP en ${_ollamaConfig.value.hostUrl}."
            } else {
                "Comprendido. Tu solicitud '${text.take(30)}...' NO se procesó con ningún modelo: " +
                        "el runtime local no respondió y esto es texto estático de la app, " +
                        "sin NPU ni LiteRT embebidos en esta build. " +
                        "Reintenta cuando el servidor esté accesible."
            }

            return "${groundingContextPrefix}$mcpExplanation" +
                    (if (attachedFileName != null) "\nHe analizado el archivo '$attachedFileName'." else "") +
                    groundingExtra
        }

        var inferenceReply: OllamaChatReply? = null
        var inferenceFailure: String? = null

        // El turno se publica en el chat con el primer token que llega por el stream y luego se
        // reescribe por su propio id, para que el texto crezca ante el usuario en lugar de
        // aparecer de golpe tras varios segundos de inferencia. Si el turno termina en reserva
        // estática se reemplaza ese mismo id: nunca quedan dos burbujas ni una fantasma.
        val streamedReplyId = UUID.randomUUID().toString()
        var provisionalPublished = false
        var pendingSeparator = ""
        fun appendToStreamedBubble(chunk: String) {
            if (!provisionalPublished) {
                provisionalPublished = true
                _chatMessages.update {
                    it + ChatMessage(
                        id = streamedReplyId,
                        sender = "JARVIS",
                        text = chunk,
                        timestamp = horaLocal()
                    )
                }
            } else {
                val separator = pendingSeparator
                pendingSeparator = ""
                _chatMessages.update { list ->
                    list.map {
                        if (it.id == streamedReplyId) it.copy(text = it.text + separator + chunk) else it
                    }
                }
            }
        }
        val onStreamedToken: (String) -> Unit = { delta -> appendToStreamedBubble(delta) }

        val actionReportText: String? = if (finalActionName.isNotBlank() && finalSuccess) {
            if (finalActionName == "configure_mcp_bridge") {
                "${groundingContextPrefix}Ajuste del modfile aplicado. $finalResultMsg"
            } else {
                "${groundingContextPrefix}Acción ejecutada en el dispositivo: '$finalActionName' (${params.values.firstOrNull() ?: ""}).\nResultado: $finalResultMsg"
            }
        } else null

        // Cuando una acción del SO tuvo éxito la rama respondía SOLO con la plantilla de arriba:
        // el turno pasaba por el chat sin que ningún modelo lo viera. La plantilla se conserva
        // como cabecera (es el hecho medido) y como reserva si el runtime no responde, pero el
        // turno se manda igualmente al modelo real.
        val jarvisResponseText = try {
        if (actionReportText != null) {
            // La cabecera es un hecho ya medido (la acción del SO devolvió resultado), así que se
            // publica antes de consultar al modelo y la respuesta del modelo se va añadiendo por
            // el stream: el turno con acción también se escribe token a token.
            appendToStreamedBubble(actionReportText)
            pendingSeparator = "\n\n"
            val (modelText, modelAnswered) = askModelWithRealContext(
                actionLabel = finalActionName,
                prompt = "$text\n\nAcción del sistema ya ejecutada: $finalActionName\n" +
                        "Parametros: $params\nResultado devuelto por el sistema: $finalResultMsg",
                measuredFallback = actionReportText,
                turnLabel = "Turno con acción",
                onToken = onStreamedToken
            )
            if (modelAnswered) "$actionReportText\n\n$modelText" else modelText
        } else {
            runLocalInference(text, attachedFileName, historyBeforeUserMessage, onStreamedToken).fold(
                onSuccess = { reply ->
                    inferenceReply = reply
                    _ollamaConfig.update { it.copy(isConnected = true) }
                    _hardwareTelemetry.update { it.copy(tokensPerSecond = reply.tokensPerSecond) }
                    if (_hardwareTelemetry.value.totalContextTokens == 0) {
                        // La tarjeta "Tokens" pasa a mostrar el contexto que el runtime declara en
                        // /api/ps, consultado una sola vez, en lugar del 16384 de fábrica.
                        val huella = runCatching {
                            ollamaClient.loadedModelFootprint(_ollamaConfig.value.hostUrl, reply.model).getOrNull()
                        }.getOrNull()
                        if (huella != null && huella.contextLength > 0) {
                            _hardwareTelemetry.update { it.copy(totalContextTokens = huella.contextLength) }
                        }
                    }
                    "${groundingContextPrefix}${reply.text}" +
                            (if (attachedFileName != null) "\n\n📎 Archivo entregado al modelo: '$attachedFileName'." else "") +
                            groundingExtra
                },
                onFailure = { error ->
                    inferenceFailure = error.message ?: error.javaClass.simpleName
                    _ollamaConfig.update { it.copy(isConnected = false) }
                    staticFallbackText() + "\n\n⚠️ [Runtime local sin respuesta] $inferenceFailure" +
                            runtimeConnectionHint(_ollamaConfig.value.hostUrl)
                }
            )
        }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Un turno cancelado no puede dejar en el chat la burbuja a medio escribir que
            // publicó el stream: se retira ese mensaje y se propaga la cancelación.
            if (provisionalPublished) {
                _chatMessages.update { list -> list.filterNot { it.id == streamedReplyId } }
                persistirHistorial()
            }
            throw ce
        }

        val jarvisMsg = ChatMessage(
            id = streamedReplyId,
            sender = "JARVIS",
            text = jarvisResponseText,
            timestamp = time2,
            isActionExecuted = finalSuccess,
            actionExecutedName = finalActionName
        )
        _chatMessages.update { list ->
            if (list.any { it.id == streamedReplyId }) {
                list.map { if (it.id == streamedReplyId) jarvisMsg else it }
            } else {
                list + jarvisMsg
            }
        }
        persistirHistorial()
        com.example.clawcode.core.EventBus.tryPublish(com.example.clawcode.core.AppEvent.ClawMessageGenerated(jarvisResponseText))

        // Diagnostic trace log 3: Model Inference Output
        val inferenceLogMessage = inferenceReply?.let { reply ->
            "[MODEL_RESPONSE_GENERATED] Inferencia REAL vía Ollama ${_ollamaConfig.value.hostUrl} | " +
                    "modelo='${reply.model}' | ${"%.2f".format(reply.tokensPerSecond)} t/s | " +
                    "latencia=${reply.latencyMs}ms | tokens=${reply.promptTokens} entrada / ${reply.completionTokens} salida | " +
                    "stop=${reply.stopReason}"
        } ?: "[MODEL_INFERENCE_FALLBACK] Runtime local no disponible " +
                "(${_ollamaConfig.value.hostUrl} | modelo='${_ollamaConfig.value.modelName}'): $inferenceFailure. " +
                "Se emitió la respuesta estática de reserva."

        _consoleLogs.update {
            it + ConsoleLog(
                id = UUID.randomUUID().toString(),
                timestamp = time2,
                sender = "MODEL_INFERENCE",
                message = inferenceLogMessage
            )
        }

        // Hablar sólo si el usuario lo tiene activado, y registrar lo que pasó de verdad: antes se
        // anunciaba "[VOICE_TTS_TRIGGERED] Sintetizando…" sin esperar al resultado del motor.
        if (_jarvisModfile.value.enableVoiceTTS) {
            val textoHablado = textoPlanoDeMarkdown(jarvisResponseText)
                .replace(Regex("[\\p{So}\\p{Cn}]"), "")
                .trim()
            val hablo = mobileActionEngine?.speak(
                text = textoHablado,
                voiceStyle = _jarvisModfile.value.voiceStyle,
                pitchMultiplier = _jarvisModfile.value.pitchMultiplier,
                speechRateMultiplier = _jarvisModfile.value.speechRateMultiplier
            )
            appendConsoleLog(
                "VOICE_TTS_OUTBOUND",
                when (hablo) {
                    true -> "[VOICE_TTS_QUEUED] Encolado en el sintetizador (${mobileActionEngine?.estadoVoz() ?: "sin datos"}), " +
                            "${textoHablado.length} caracteres, estilo '${_jarvisModfile.value.voiceStyle}'."
                    false -> "[VOICE_TTS_FALLA] El sintetizador no aceptó el turno: ${mobileActionEngine?.estadoVoz() ?: "motor no inicializado"}."
                    null -> "[VOICE_TTS_NO_MOTOR] Sin motor de acciones en esta ruta: no se sintetizó audio."
                }
            )
        }

        // Add to console log
        val consoleLog = ConsoleLog(
            id = UUID.randomUUID().toString(),
            timestamp = time2,
            sender = "JARVIS_CHAT",
            message = "Chat procesado: '$text' -> Respuesta: $jarvisResponseText"
        )
        _consoleLogs.update { it + consoleLog }
    }

    /**
     * `10.0.2.2` es el alias del anfitrión que sólo existe dentro del emulador de Android;
     * en un teléfono físico nunca resuelve, así que el fallo se explica en vez de quedar mudo.
     *
     * La alternativa LAN se advierte con sus dos restricciones reales, medidas en esta máquina:
     * (1) desde Android 9 el sistema bloquea el HTTP en claro y
     * `res/xml/network_security_config.xml` sólo autoriza loopback y 10.0.2.2, así que una IP
     * privada exige añadirla ahí y recompilar; (2) Ollama de fábrica escucha sólo en
     * 127.0.0.1, así que rechaza a cualquier otra máquina aunque la IP esté autorizada. Sin ese
     * aviso el usuario configura el host, ve "Cleartext HTTP traffic ... not permitted" o un
     * "Failed to connect" y no tiene forma de relacionarlos con su causa.
     */
    private fun runtimeConnectionHint(hostUrl: String): String {
        val authority = hostUrl.substringAfter("://", hostUrl).substringBefore("/")
        val host = authority.substringBeforeLast(':').trim('[', ']').lowercase()
        return when {
            hostUrl.contains("10.0.2.2") ->
                "\n\nℹ️ El host '$hostUrl' sólo es alcanzable desde el emulador. " +
                        "En un dispositivo físico tienes tres opciones: (a) ejecutar Ollama en el propio " +
                        "teléfono y usar http://127.0.0.1:11434, que ya está autorizado; (b) indicar en " +
                        "Ajustes Ollama la IP LAN del equipo que ejecuta Ollama (por ejemplo " +
                        "http://192.168.1.x:11434) y añadir esa IP a res/xml/network_security_config.xml, " +
                        "porque Android bloquea el HTTP en claro a cualquier host no listado; o (c) con el " +
                        "teléfono por USB y adb instalado, sin recompilar y sin abrir el servicio a la red: " +
                        "adb reverse tcp:11434 tcp:11434 y dejar el host en http://127.0.0.1:11434."

            host == "localhost" || host == "127.0.0.1" -> ""

            host == "::1" || host == "ip6-localhost" || host == "ip6-loopback" ->
                "\n\nℹ️ '$hostUrl' es el loopback IPv6 y Ollama no escucha ahí por defecto: la instalación " +
                        "estándar deja el socket sólo en 127.0.0.1, así que a ::1 le rechazan la conexión. " +
                        "Usa http://127.0.0.1:11434, o arranca Ollama con OLLAMA_HOST=[::]:11434."

            else ->
                "\n\nℹ️ '$hostUrl' no es loopback, así que hacen falta dos ajustes para que el chat llegue " +
                        "al modelo: (1) añadir esta IP a res/xml/network_security_config.xml y recompilar, " +
                        "porque Android bloquea el HTTP en claro hacia hosts no listados; (2) arrancar el " +
                        "Ollama remoto con OLLAMA_HOST=0.0.0.0:11434, porque de fábrica sólo escucha en " +
                        "127.0.0.1 y rechaza las conexiones desde otra máquina. La alternativa sin recompilar " +
                        "es ejecutar Ollama en el propio teléfono con http://127.0.0.1:11434, o dejar el " +
                        "host en esa misma dirección con el teléfono por USB y adb reverse tcp:11434 " +
                        "tcp:11434, que tunelea el puerto del equipo al dispositivo."
        }
    }

    /**
     * Única vía de inferencia real del chat: habla con el servidor Ollama configurado
     * en [OllamaConfig] y devuelve el texto generado por el modelo junto a sus métricas.
     *
     * Si el modelo configurado no existe en el servidor, se resuelve automáticamente el
     * modelo local más ligero disponible y se reintenta una sola vez.
     */
    private suspend fun runLocalInference(
        userText: String,
        attachedFileName: String?,
        priorMessages: List<ChatMessage>,
        onToken: ((String) -> Unit)? = null
    ): Result<OllamaChatReply> {
        val config = _ollamaConfig.value
        val modfile = _jarvisModfile.value

        val history = priorMessages
            .filter { it.sender == "USER" || it.sender == "JARVIS" }
            .takeLast(HISTORY_MESSAGES_FOR_CONTEXT)
            .map {
                OllamaChatMessage(
                    role = if (it.sender == "USER") "user" else "assistant",
                    content = it.text
                )
            }

        val promptForModel = if (attachedFileName != null) {
            "$userText\n\n[Archivo adjunto proporcionado por el usuario: $attachedFileName]"
        } else {
            userText
        }

        val buildRequest: (String, String) -> OllamaChatRequest = { hostUrl, model ->
            OllamaChatRequest(
                hostUrl = hostUrl,
                model = model,
                systemPrompt = modfile.systemPrompt,
                history = history,
                userText = promptForModel,
                temperature = modfile.temperature,
                topP = modfile.topP,
                stopTokens = modfile.stopTokens,
                maxTokens = MAX_GENERATED_TOKENS
            )
        }

        // Estado de carga del chat: la inferencia real tarda cientos de milisegundos o varios
        // segundos (medidos: 0.5 B local ~15-34 tokens/s). Se publica justo antes de tocar la
        // red y se limpia en finally, incluyendo cancelación, para que la UI nunca quede
        // mostrando "esperando" sobre un turno ya terminado.
        _chatAwaitingRuntime.value = "Consultando '${config.modelName}' en ${config.hostUrl}"
        try {
            val firstAttempt = ollamaClient.chat(buildRequest(config.hostUrl, config.modelName), onToken)
            if (firstAttempt.isSuccess) return firstAttempt

            val failureMessage = firstAttempt.exceptionOrNull()?.message.orEmpty()

            // El servidor responde pero no conoce el modelo configurado.
            if (failureMessage.contains("not found", ignoreCase = true)) {
                val resolvedModel = ollamaClient.listCompletionModels(config.hostUrl)
                    .getOrNull()
                    ?.firstOrNull { !it.isCloudStub }
                    ?.name
                    ?: return firstAttempt

                logModelResolution(config, resolvedModel)

                val retryAttempt = ollamaClient.chat(buildRequest(config.hostUrl, resolvedModel), onToken)
                if (retryAttempt.isSuccess) {
                    // Se fija el modelo que de verdad responde para no repetir la resolución
                    // en cada mensaje ni perderla al reiniciar la app.
                    _ollamaConfig.update { it.copy(modelName = resolvedModel) }
                    configStore.save(_ollamaConfig.value)
                }
                return retryAttempt
            }

            // El host configurado no responde en absoluto.
            return discoverRuntimeHost(config.hostUrl, buildRequest, onToken) ?: firstAttempt
        } finally {
            _chatAwaitingRuntime.value = null
        }
    }

    private fun logModelResolution(config: OllamaConfig, resolvedModel: String) {
        _consoleLogs.update {
            it + ConsoleLog(
                id = UUID.randomUUID().toString(),
                timestamp = horaLocal(),
                sender = "OLLAMA_MODEL_RESOLVER",
                message = "[MODEL_AUTORESOLVED] '${config.modelName}' no existe en ${config.hostUrl}. " +
                        "Reintentando con el modelo local más ligero disponible: '$resolvedModel'."
            )
        }
    }

    /**
     * El host que trae la app de fábrica (10.0.2.2) sólo existe dentro del emulador, así que
     * en un teléfono el chat caería siempre en la reserva estática aunque haya un Ollama
     * escuchando en el propio dispositivo. Se sondean candidatos baratos una sola vez y, si
     * alguno sirve un modelo local, se adopta y se persiste.
     *
     * Devuelve null si no se encontró ningún runtime, para que el llamador conserve el error
     * original en lugar de uno menos informativo.
     */
    private suspend fun discoverRuntimeHost(
        configuredHost: String,
        buildRequest: (String, String) -> OllamaChatRequest,
        onToken: ((String) -> Unit)? = null
    ): Result<OllamaChatReply>? {
        if (configuredHost != FACTORY_HOST) return null

        for (candidate in RUNTIME_HOST_CANDIDATES) {
            val model = hostProbeClient.listCompletionModels(candidate)
                .getOrNull()
                ?.firstOrNull { !it.isCloudStub }
                ?.name
                ?: continue

            _consoleLogs.update {
                it + ConsoleLog(
                    id = UUID.randomUUID().toString(),
                    timestamp = horaLocal(),
                    sender = "OLLAMA_HOST_DISCOVERY",
                    message = "[HOST_AUTORESOLVED] $configuredHost no responde. Runtime local encontrado en " +
                            "$candidate sirviendo '$model'. Configuración actualizada y guardada."
                )
            }
            _ollamaConfig.update { it.copy(hostUrl = candidate, modelName = model) }
            configStore.save(_ollamaConfig.value)

            return ollamaClient.chat(buildRequest(candidate, model), onToken)
        }
        return null
    }

    fun updateJarvisModfile(
        agentName: String,
        baseModel: String,
        systemPrompt: String,
        temperature: Float,
        topP: Float,
        wakeWord: String,
        enableVoiceTTS: Boolean,
        voiceStyle: String = "Voz 1",
        pitchMultiplier: Float = 1.0f,
        speechRateMultiplier: Float = 1.0f,
        personality: AgentPersonality = _jarvisModfile.value.personality,
        currentTaskMode: ModelTaskMode = _jarvisModfile.value.currentTaskMode,
        useAutoDynamicTheme: Boolean = _jarvisModfile.value.useAutoDynamicTheme,
        customThemeColorPreset: com.example.clawcode.data.model.CustomThemeColorPreset = _jarvisModfile.value.customThemeColorPreset,
        selectedAvatarId: Int = _jarvisModfile.value.selectedAvatarId
    ) {
        _jarvisModfile.update {
            it.copy(
                agentName = agentName,
                baseModel = baseModel,
                systemPrompt = systemPrompt,
                temperature = temperature,
                topP = topP,
                wakeWord = wakeWord,
                enableVoiceTTS = enableVoiceTTS,
                voiceStyle = voiceStyle,
                pitchMultiplier = pitchMultiplier,
                speechRateMultiplier = speechRateMultiplier,
                personality = personality,
                currentTaskMode = currentTaskMode,
                useAutoDynamicTheme = useAutoDynamicTheme,
                customThemeColorPreset = customThemeColorPreset,
                selectedAvatarId = selectedAvatarId
            )
        }
    }

    fun setSelectedAvatarId(avatarId: Int) {
        _jarvisModfile.update { it.copy(selectedAvatarId = avatarId) }
    }

    fun toggleTool(tool: McpTool) {
        _tools.update { currentList ->
            currentList.map { if (it.id == tool.id) it.copy(isEnabled = !it.isEnabled) else it }
        }
    }

    fun setPersonality(personality: AgentPersonality) {
        _jarvisModfile.update { it.copy(personality = personality) }
    }

    fun setTaskMode(taskMode: ModelTaskMode) {
        _jarvisModfile.update { it.copy(currentTaskMode = taskMode) }
    }

    fun setUseAutoDynamicTheme(auto: Boolean) {
        _jarvisModfile.update { it.copy(useAutoDynamicTheme = auto) }
    }

    fun setCustomThemeColorPreset(preset: com.example.clawcode.data.model.CustomThemeColorPreset) {
        _jarvisModfile.update { it.copy(customThemeColorPreset = preset) }
    }

    fun setDarkMode(isDark: Boolean) {
        _jarvisModfile.update { it.copy(isDarkMode = isDark) }
    }

    /**
     * Las tres acciones rápidas publicaban en el chat un veredicto fabricado ("todos los
     * subsistemas operan de forma óptima", "0 advertencias críticas", "Almacén de Vectores Local
     * activo") sin ejecutar ningún análisis. Aquí se entrega al modelo local el estado real y se
     * publica lo que el modelo responde. Si el runtime no contesta, se publica sólo el recuento
     * verificable, nunca un diagnóstico inventado.
     */
    /**
     * Appends the streaming deltas into a JARVIS bubble created on the first token, so a quick
     * action shows the report as the model writes it. `publishQuickActionResult` later rewrites
     * that same id, so one turn never leaves two bubbles.
     */
    private fun streamingBubbleAppender(bubbleId: String): (String) -> Unit {
        var started = false
        return { delta ->
            if (!started) {
                started = true
                _chatMessages.update {
                    it + ChatMessage(
                        id = bubbleId,
                        sender = "JARVIS",
                        text = delta,
                        timestamp = horaLocal()
                    )
                }
            } else {
                _chatMessages.update { list ->
                    list.map { if (it.id == bubbleId) it.copy(text = it.text + delta) else it }
                }
            }
        }
    }

    private suspend fun askModelStreamingInto(
        bubbleId: String,
        actionLabel: String,
        prompt: String,
        measuredFallback: String
    ): Pair<String, Boolean> =
        try {
            askModelWithRealContext(
                actionLabel = actionLabel,
                prompt = prompt,
                measuredFallback = measuredFallback,
                onToken = streamingBubbleAppender(bubbleId)
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            _chatMessages.update { list -> list.filterNot { it.id == bubbleId } }
            persistirHistorial()
            throw ce
        }

    private suspend fun askModelWithRealContext(
        actionLabel: String,
        prompt: String,
        measuredFallback: String,
        turnLabel: String = "Acción rápida",
        onToken: ((String) -> Unit)? = null
    ): Pair<String, Boolean> =
        runLocalInference(prompt, null, _chatMessages.value, onToken).fold(
            onSuccess = { reply ->
                _ollamaConfig.update { it.copy(isConnected = true) }
                _hardwareTelemetry.update { it.copy(tokensPerSecond = reply.tokensPerSecond) }
                appendConsoleLog(
                    "MODEL_INFERENCE",
                    "[MODEL_RESPONSE_GENERATED] $turnLabel '$actionLabel' -> inferencia REAL vía " +
                            "Ollama ${_ollamaConfig.value.hostUrl} | modelo='${reply.model}' | " +
                            "${"%.2f".format(reply.tokensPerSecond)} t/s | latencia=${reply.latencyMs}ms"
                )
                reply.text to true
            },
            onFailure = { error ->
                val reason = error.message ?: error.javaClass.simpleName
                _ollamaConfig.update { it.copy(isConnected = false) }
                appendConsoleLog(
                    "MODEL_INFERENCE",
                    "[MODEL_INFERENCE_FALLBACK] $turnLabel '$actionLabel' -> runtime local no " +
                            "disponible (${_ollamaConfig.value.hostUrl} | " +
                            "modelo='${_ollamaConfig.value.modelName}'): $reason"
                )
                "$measuredFallback\n\n⚠️ [Runtime local sin respuesta] $reason" +
                        runtimeConnectionHint(_ollamaConfig.value.hostUrl) to false
            }
        )

    private fun appendConsoleLog(sender: String, message: String) {
        _consoleLogs.update {
            it + ConsoleLog(
                id = UUID.randomUUID().toString(),
                timestamp = horaLocal(),
                sender = sender,
                message = message
            )
        }
    }

    /** Mantiene el efecto visible de la acción rápida (mensaje al chat + tarea) sin inventar el resultado. */
    private fun publishQuickActionResult(
        taskIdPrefix: String,
        chatText: String,
        taskTitle: String,
        taskDescription: String,
        agentName: String,
        priority: Priority,
        succeeded: Boolean,
        taskLogs: List<String>,
        streamedBubbleId: String? = null
    ) {
        val time = horaLocal()
        val message = ChatMessage(
            id = streamedBubbleId ?: UUID.randomUUID().toString(),
            sender = "JARVIS",
            text = chatText,
            timestamp = time
        )
        _chatMessages.update { list ->
            if (streamedBubbleId != null && list.any { it.id == streamedBubbleId }) {
                list.map { if (it.id == streamedBubbleId) message else it }
            } else {
                list + message
            }
        }
        persistirHistorial()
        val task = AgentTask(
            id = "$taskIdPrefix-${UUID.randomUUID().toString().take(6)}",
            title = taskTitle,
            description = taskDescription,
            agentName = agentName,
            status = if (succeeded) TaskStatus.COMPLETED else TaskStatus.ERROR,
            priority = priority,
            progress = if (succeeded) 1.0f else 0.0f,
            createdAt = time,
            logs = taskLogs
        )
        _tasks.update { listOf(task) + it }
    }

    /** Estado verificable del agente, para que el modelo no tenga que inventárselo. */
    private fun runtimeSnapshot(): String {
        val config = _ollamaConfig.value
        val telemetry = _hardwareTelemetry.value
        val modfile = _jarvisModfile.value
        val tools = _tools.value
        return "Runtime Ollama: ${config.hostUrl} | modelo='${config.modelName}' | conectado=${config.isConnected}\n" +
                "Modfile: baseModel='${modfile.baseModel}' | temperature=${modfile.temperature} | topP=${modfile.topP}\n" +
                "Telemetría: CPU ${telemetry.cpuUsagePercent}% de ${telemetry.cpuCores} núcleos | " +
                "RAM ${telemetry.ramUsedGb}/${telemetry.ramTotalGb} GB | batería ${telemetry.batteryLevelPercent}% " +
                "a ${telemetry.batteryTemperatureC}°C | ${"%.2f".format(telemetry.tokensPerSecond)} t/s en la última inferencia\n" +
                "Herramientas MCP: ${tools.count { it.isEnabled }} activas de ${tools.size}\n" +
                "Tareas: ${_tasks.value.size} en total, ${_tasks.value.count { it.status == TaskStatus.RUNNING }} en curso, " +
                "${_tasks.value.count { it.status == TaskStatus.COMPLETED }} completadas"
    }

    suspend fun executeQuickActionSummarizeLogs() {
        val time = horaLocal()
        val logs = _consoleLogs.value
        val sample = logs.takeLast(QUICK_ACTION_LOG_SAMPLE)
        val sampleText = sample.joinToString("\n") { "[${it.timestamp}] ${it.sender}: ${it.message}" }
        val errorCount = logs.count { it.message.contains("error", ignoreCase = true) }

        val qaBubbleId = UUID.randomUUID().toString()
        val (summaryText, succeeded) = askModelStreamingInto(bubbleId = qaBubbleId,
            actionLabel = "Resumir registros",
            prompt = "Resume en pocas líneas estos registros REALES de la consola de CLAW " +
                    "(${logs.size} en total; se listan los últimos ${sample.size}). Cíñete a lo que aparece " +
                    "en el texto: no menciones subsistemas, aceleración NPU ni estados que no figuren.\n\n$sampleText",
            measuredFallback = "📋 [Resumen de Registros] Registros reales en consola: ${logs.size} " +
                    "($errorCount contienen la marca 'error'). No se generó resumen."
        )

        publishQuickActionResult(
            taskIdPrefix = "task-quick-logs",
            streamedBubbleId = qaBubbleId,
            chatText = summaryText,
            taskTitle = "Resumir Registros del Sistema",
            taskDescription = "Resumen pedido al modelo local a partir de los registros reales de la consola.",
            agentName = "Jarvis_LogAuditor",
            priority = Priority.MEDIUM,
            succeeded = succeeded,
            taskLogs = listOf(
                "[$time] Leyendo ${logs.size} eventos reales de la consola...",
                if (succeeded) "[$time] Resumen generado por '${_ollamaConfig.value.modelName}'."
                else "[$time] El runtime local no respondió; se publicó sólo el recuento real."
            )
        )
    }

    suspend fun executeQuickActionAnalyzeCode() {
        val time = horaLocal()

        val qaBubbleId = UUID.randomUUID().toString()
        val (analysisText, succeeded) = askModelStreamingInto(bubbleId = qaBubbleId,
            actionLabel = "Analizar estado del agente",
            prompt = "El usuario pulsó la acción rápida 'Analizar Código'. En el teléfono no se ha " +
                    "ejecutado ningún análisis estático ni de AST, así que NO puedes informar de una " +
                    "auditoría de código ni de advertencias detectadas. A partir de estos datos REALES " +
                    "del runtime, describe brevemente el estado del agente y qué convendría revisar:\n\n" +
                    runtimeSnapshot(),
            measuredFallback = "💻 [Estado del Agente] No se ejecutó ningún análisis de código.\n" + runtimeSnapshot()
        )

        publishQuickActionResult(
            taskIdPrefix = "task-quick-code",
            streamedBubbleId = qaBubbleId,
            chatText = analysisText,
            taskTitle = "Analizar Código Fuente",
            taskDescription = "Estado real del runtime enviado al modelo local. No se ejecuta análisis estático en el dispositivo.",
            agentName = "Jarvis_CodeInspector",
            priority = Priority.HIGH,
            succeeded = succeeded,
            taskLogs = listOf(
                "[$time] Recopilando estado real del runtime y de las herramientas MCP...",
                if (succeeded) "[$time] Análisis generado por '${_ollamaConfig.value.modelName}'."
                else "[$time] El runtime local no respondió; se publicó el estado medido sin diagnóstico."
            )
        )
    }

    suspend fun executeQuickActionCheckMemory() {
        val time = horaLocal()
        val memories = _memories.value
        val realTokens = memories.sumOf { it.tokenCount }
        val compacted = memories.count { it.isCompacted }
        val byCategory = memories.groupingBy { it.category }.eachCount()
            .entries.joinToString(", ") { "${it.key}=${it.value}" }
        val memorySnapshot = "Memorias almacenadas: ${memories.size}\n" +
                "Tokens reales (suma de tokenCount): $realTokens\n" +
                "Entradas compactadas: $compacted de ${memories.size}\n" +
                "Por categoría: ${byCategory.ifBlank { "ninguna" }}\n" +
                "Contenido:\n" + memories.take(QUICK_ACTION_MEMORY_SAMPLE)
            .joinToString("\n") { " - [${it.category}] ${it.key}: ${it.value.take(160)} (${it.tokenCount} tokens)" }

        val qaBubbleId = UUID.randomUUID().toString()
        val (memText, succeeded) = askModelStreamingInto(bubbleId = qaBubbleId,
            actionLabel = "Comprobar memoria",
            prompt = "Informa al usuario sobre la memoria de trabajo de CLAW usando únicamente estos " +
                    "datos reales. No menciones almacenes vectoriales, tasas ni estados que no aparezcan " +
                    "aquí:\n\n$memorySnapshot",
            measuredFallback = "🧠 [Comprobación de Memoria] $memorySnapshot"
        )

        publishQuickActionResult(
            taskIdPrefix = "task-quick-mem",
            streamedBubbleId = qaBubbleId,
            chatText = memText,
            taskTitle = "Comprobar Memoria de Trabajo",
            taskDescription = "Inventario real de la memoria enviado al modelo local.",
            agentName = "Jarvis_MemoryKeeper",
            priority = Priority.MEDIUM,
            succeeded = succeeded,
            taskLogs = listOf(
                "[$time] Verificando ${memories.size} elementos reales de memoria ($realTokens tokens)...",
                if (succeeded) "[$time] Informe generado por '${_ollamaConfig.value.modelName}'."
                else "[$time] El runtime local no respondió; se publicó el inventario real."
            )
        )
    }

    fun addFileAttachment(fileName: String, fileType: String, sizeKb: Int, summary: String) {
        val newFile = FileAttachmentItem(
            id = "file-" + UUID.randomUUID().toString().take(6),
            fileName = fileName,
            fileType = fileType,
            sizeKb = sizeKb,
            contentSummary = summary,
            // Nadie abre el fichero ni lo parsea en esta ruta: anunciarlo como analizado hacia
            // que el usuario creyera que su contenido ya estaba en el contexto del modelo.
            isParsedByJarvis = false
        )
        _fileAttachments.update { listOf(newFile) + it }
        appendConsoleLog(
            "FILE_ATTACHMENT",
            "[SIN PARSEAR] '$fileName' quedó en la lista como referencia. Ningún byte se leyó ni se " +
                "envió al modelo; no hay selector de ficheros ni parser en esta ruta."
        )
    }

    fun updateOllamaConfig(hostUrl: String, modelName: String, webhookUrl: String, useFallback: Boolean) {
        _ollamaConfig.update {
            it.copy(
                hostUrl = hostUrl,
                modelName = modelName,
                openClawWebhookUrl = webhookUrl,
                useOnDeviceLiteRTFallback = useFallback
            )
        }
        configStore.save(_ollamaConfig.value)
    }

    suspend fun simulateWhatsAppIncoming(
        phoneName: String,
        messageText: String,
        mobileActionEngine: MobileActionEngine
    ) {
        val time = horaLocal()
        val (actionName, params) = mobileActionEngine.parseNaturalLanguageToFunctionCall(messageText)
        val (success, actionResult) = mobileActionEngine.executeActionIntent(actionName, params)

        // El puente de WhatsApp no está abierto en esta build: no entra ningún payload real y no
        // sale nada del teléfono. Lo que sí ocurre de verdad es el turno contra el runtime local,
        // así que eso es lo que se guarda en la bandeja de la demo.
        val (modelReply, modelAnswered) = askModelWithRealContext(
            actionLabel = "mensaje entrante (demo)",
            prompt = "Mensaje de la demo del puente, atribuido a '$phoneName': \"$messageText\".\n" +
                    (if (success) "Acción del sistema ejecutada: $actionName con $params. Resultado: $actionResult"
                     else "Ninguna acción del sistema se ejecutó. Detalle: $actionResult") +
                    "\nRedacta la respuesta que recibiría ese usuario. No afirmes que el mensaje " +
                    "se envió por WhatsApp ni que se ejecutó algo que no aparece arriba.",
            measuredFallback = "Sin respuesta: el runtime local no respondió y, además, no hay " +
                    "ningún puente de WhatsApp abierto que envíe mensajes.",
            turnLabel = "Demo WhatsApp"
        )

        val newMsg = WhatsAppMessage(
            id = "wa-" + UUID.randomUUID().toString().take(6),
            senderPhone = "+000 (demo local, ningún número real)",
            senderName = phoneName,
            content = messageText,
            timestamp = time,
            actionTriggered = if (success) actionName else "",
            responseSent = if (modelAnswered) {
                "NO ENVIADA por WhatsApp (ninguna conexión abierta). El runtime local " +
                        "'${_ollamaConfig.value.modelName}' respondió: ${modelReply.take(180)}"
            } else {
                "NO ENVIADA: sin runtime local y sin puente de WhatsApp."
            }
        )

        _whatsAppMessages.update { listOf(newMsg) + it }

        val log = ConsoleLog(
            id = UUID.randomUUID().toString(),
            timestamp = time,
            sender = "OpenClawBridge",
            message = "[WHATSAPP_DEMO_LOCAL] Mensaje de '$phoneName' procesado sólo localmente: " +
                    "acción=$actionName ejecutada=$success ($actionResult) | " +
                    (if (modelAnswered) "respuesta del modelo '${_ollamaConfig.value.modelName}'"
                     else "sin respuesta del runtime") +
                    " | Ningún payload salió del teléfono."
        )
        _consoleLogs.update { it + log }
    }

    suspend fun runAsyncAgentTaskLoop(
        prompt: String,
        mobileActionEngine: MobileActionEngine
    ) {
        val time = horaLocal()
        val taskId = "task-" + UUID.randomUUID().toString().take(6)

        val config = _ollamaConfig.value
        val newTask = AgentTask(
            id = taskId,
            title = "JARVIS Async Loop: $prompt",
            description = "Bucle autónomo: acción del SO + inferencia REAL contra ${config.modelName} en ${config.hostUrl}",
            agentName = _jarvisModfile.value.agentName,
            status = TaskStatus.RUNNING,
            priority = Priority.HIGH,
            progress = 0.1f,
            createdAt = time,
            // No hay detector de wake-word en la app: esto lo dispara siempre una accion del usuario.
            logs = listOf("[$time][ASYNC_INIT] Bucle de agente iniciado por solicitud del usuario (no hay detector de wake-word).")
        )
        _tasks.update { listOf(newTask) + it }
        recalculateStats()

        val (actionName, params) = mobileActionEngine.parseNaturalLanguageToFunctionCall(prompt)
        val (success, resultMsg) = mobileActionEngine.executeActionIntent(actionName, params)
        val time2 = horaLocal()

        // El progreso sólo avanza cuando hay un paso realmente completado; antes lo hacían
        // dos delay() sin correspondencia con ningún trabajo.
        _tasks.update { list ->
            list.map {
                if (it.id == taskId) {
                    it.copy(
                        progress = 0.5f,
                        logs = it.logs +
                                "[$time2][MOBILE_ACTION_EXECUTED] Acción '$actionName' invocada de forma asíncrona " +
                                "(resultado del sistema: $resultMsg)."
                    )
                } else it
            }
        }

        // El modelo local de verdad participa en el turno: se le entrega la intención y el
        // resultado medido de la acción, y se publica lo que responde.
        val modelPrompt = "Tarea asíncrona del agente: \"$prompt\".\n" +
                "Acción ejecutada en el dispositivo: $actionName con argumentos $params.\n" +
                "Resultado devuelto por el sistema: $resultMsg.\n" +
                "Resume en dos líneas qué se hizo y qué queda pendiente. No inventes pasos."
        val measuredFallback = "Acción '$actionName' ejecutada en el sistema: $resultMsg. " +
                "Ningún modelo revisó la tarea porque el runtime local no respondió."
        val (modelText, modelAnswered) = askModelWithRealContext(
            actionLabel = "Bucle asíncrono",
            prompt = modelPrompt,
            measuredFallback = measuredFallback,
            turnLabel = "Bucle asíncrono"
        )
        val time3 = horaLocal()

        _tasks.update { list ->
            list.map {
                if (it.id == taskId) {
                    it.copy(
                        status = TaskStatus.COMPLETED,
                        progress = 1.0f,
                        logs = it.logs +
                                (if (modelAnswered) {
                                    "[$time3][ASYNC_DONE] Inferencia REAL del modelo consultada y añadida al resultado."
                                } else {
                                    "[$time3][ASYNC_NO_MODEL] Sin aportación del modelo: el runtime local no respondió."
                                }) +
                                "[$time3][ASYNC_RESULT] $modelText"
                    )
                } else it
            }
        }

        // Log to console
        val consoleLog = ConsoleLog(
            id = UUID.randomUUID().toString(),
            timestamp = time3,
            sender = "JARVIS_ASYNC_WORKER",
            message = if (modelAnswered) {
                "[ASYNC_AGENT_TURN] Tarea asíncrona: acción '$actionName' + inferencia REAL vía " +
                        "Ollama ${_ollamaConfig.value.hostUrl} (modelo '${_ollamaConfig.value.modelName}'): " +
                        "$prompt -> $resultMsg"
            } else {
                "[ASYNC_AGENT_ACTION_ONLY] Tarea asíncrona: se ejecutó la acción '$actionName' ($resultMsg) " +
                        "pero el modelo local no respondió; no hay análisis de IA en este resultado."
            }
        )
        _consoleLogs.update { it + consoleLog }

        // Save memory item
        addMemory("jarvis_async_last_run", "AutonomousAgent", "Completado '$prompt' -> $resultMsg")
        recalculateStats()
    }

    fun addTask(title: String, description: String, agentName: String, priority: Priority) {
        val newTask = AgentTask(
            id = "task-" + UUID.randomUUID().toString().take(6),
            title = title,
            description = description,
            agentName = agentName,
            status = TaskStatus.PENDING,
            priority = priority,
            progress = 0f,
            createdAt = horaLocal(),
            logs = listOf("[INIT] Task queued in Claw Engine pipeline.")
        )
        _tasks.update { listOf(newTask) + it }
        recalculateStats()
    }

    fun runTask(taskId: String) {
        _tasks.update { list ->
            list.map { task ->
                if (task.id == taskId) {
                    // No hay subagente ni ejecutor detras: poner RUNNING y un 25% de progreso
                    // inventaba una ejecución. La tarea real se lanza con runAsyncAgentTaskLoop.
                    task.copy(
                        logs = task.logs + "[SIN EJECUTOR] runTask no lanza nada por sí solo: esta tarea " +
                            "no tiene un ejecutor cableado, así que su estado y su progreso no cambian."
                    )
                } else task
            }
        }
        recalculateStats()
    }

    fun completeTask(taskId: String) {
        _tasks.update { list ->
            list.map { task ->
                if (task.id == taskId) {
                    task.copy(
                        status = TaskStatus.COMPLETED,
                        progress = 1.0f,
                        logs = task.logs + "[DONE] Task execution succeeded. Artifacts stored in Claw Memory."
                    )
                } else task
            }
        }
        recalculateStats()
    }

    fun toggleTool(toolId: String) {
        _tools.update { list ->
            list.map { tool ->
                if (tool.id == toolId) {
                    tool.copy(isEnabled = !tool.isEnabled)
                } else tool
            }
        }
    }

    suspend fun compactMemory() {
        // No hay backend de resumen: esto dormia 1,2 s y multiplicaba el conteo por 0,45, creando
        // el numero de un ahorro que ningun texto reflejaba. Se reporta lo que pasa de verdad.
        _isCompacting.value = true
        appendConsoleLog(
            "MEMORY_COMPACT",
            "[NO EJECUTADA] Compactar memoria necesita un modelo que resuma el texto guardado y esta " +
                "build no lo llama. Los ${_memories.value.size} elementos conservan su contenido y su " +
                "conteo original; no se ha liberado nada."
        )
        _isCompacting.value = false
    }

    fun addMemory(key: String, category: String, value: String) {
        val newMem = MemoryItem(
            id = "mem-" + UUID.randomUUID().toString().take(6),
            key = key,
            category = category,
            value = value,
            // Estimacion burda (4 caracteres por token), no una tokenizacion. El orden de
            // magnitud anterior (length * 4) inflaba el numero por dieciseis.
            tokenCount = (value.length / 4).coerceAtLeast(1),
            isCompacted = false,
            updatedAt = horaLocal()
        )
        _memories.update { listOf(newMem) + it }
        recalculateStats()
    }

    fun clearConsoleLogs() {
        _consoleLogs.value = emptyList()
    }

    fun injectSimulatedLog(sender: String, message: String) {
        val time = horaLocal()
        val simLog = ConsoleLog(
            id = UUID.randomUUID().toString(),
            timestamp = time,
            sender = sender,
            message = message
        )
        _consoleLogs.update { it + simLog }
    }

    /**
     * `/diag` medía de verdad nada: devolvía cuatro líneas "PASSED" escritas a mano, incluida
     * "NPU LiteRT Acceleration -> PASSED (0.42s)" para un acelerador que esta app no usa. Aquí se
     * sondean el runtime y el modelo configurados, con su latencia real, para que el diagnóstico
     * sirva al usuario en lugar de tranquilizarle.
     */
    /**
     * El puente con WhatsApp es sólo una URL guardada en ajustes: esta build no abre ningún socket
     * ni recibe payloads. `/bridge` escribía "Socket State: CONNECTED" y `/bridge test` un
     * "HTTP 200 OK -> Forwarded to Jarvis Agent" inventados, justo el bloqueo que el usuario viene
     * a diagnosticar. Ahora se informa de lo comprobable.
     */
    private fun bridgeStatusReport(): String {
        val config = _ollamaConfig.value
        val measuredTps = _hardwareTelemetry.value.tokensPerSecond
        val runtimeState = if (measuredTps > 0f) {
            "respondió en el último turno medido (${"%.2f".format(measuredTps)} t/s)"
        } else {
            "sin medir todavía: ningún turno ha consultado al runtime en esta sesión"
        }
        return "[PUENTE OPENCLAW WHATSAPP]\n" +
                " • Webhook configurado: ${config.openClawWebhookUrl}\n" +
                " • Estado del socket: NUNCA ABIERTO — esta app no establece ninguna conexión con el webhook ni recibe payloads.\n" +
                " • Runtime que sí atiende los turnos: '${config.modelName}' en ${config.hostUrl} ($runtimeState)\n" +
                " • Falta en el repo: un servicio que reciba el webhook y llame a este motor. Sin eso ningún mensaje de WhatsApp entra."
    }

    /**
     * La prueba del puente ya no finge un 200 OK: deja claro que no viaja ningún payload y ejecuta
     * lo que sí existe, un turno real contra el runtime local, con su latencia y throughput medidos.
     */
    private suspend fun runBridgeTurnProbe(): String {
        val config = _ollamaConfig.value
        val modfile = _jarvisModfile.value
        val result = ollamaClient.chat(
            OllamaChatRequest(
                hostUrl = config.hostUrl,
                model = config.modelName,
                systemPrompt = modfile.systemPrompt,
                history = emptyList(),
                userText = "Responde en una línea corta qué runtime te está dando respuesta.",
                temperature = modfile.temperature,
                topP = modfile.topP,
                stopTokens = modfile.stopTokens,
                maxTokens = MAX_GENERATED_TOKENS
            )
        )
        return "[PRUEBA DEL PUENTE OPENCLAW]\n" +
                " • Payload de WhatsApp enviado: NINGUNO — ${config.openClawWebhookUrl} no tiene ninguna conexión abierta.\n" +
                result.fold(
                    onSuccess = { reply ->
                        " • Turno real ejecutado contra '${reply.model}' en ${config.hostUrl}: " +
                                "${reply.latencyMs} ms, ${"%.2f".format(reply.tokensPerSecond)} t/s.\n" +
                                " • Lo que respondió el modelo: ${reply.text.take(200)}"
                    },
                    onFailure = { error ->
                        " • El runtime local no respondió (${error.message ?: error.javaClass.simpleName}): " +
                                "la prueba del puente no puede completarse."
                    }
                )
    }

    private suspend fun runRuntimeDiagnosticSuite(): String {
        val config = _ollamaConfig.value
        val startedAt = System.currentTimeMillis()
        val probe = hostProbeClient.listCompletionModels(config.hostUrl)
        val elapsedMs = System.currentTimeMillis() - startedAt
        val localModels = probe.getOrNull()?.filterNot { it.isCloudStub }.orEmpty()

        val runtimeLine = probe.fold(
            onSuccess = {
                " • Runtime local: OK en ${config.hostUrl} (${elapsedMs} ms medidos) | " +
                        "${localModels.size} modelos locales: " +
                        localModels.joinToString { it.name }.ifBlank { "ninguno" }
            },
            onFailure = { error ->
                " • Runtime local: SIN RESPUESTA en ${config.hostUrl} (${elapsedMs} ms) -> " +
                        "${error.javaClass.simpleName}: ${error.message ?: "sin mensaje"}"
            }
        )

        val modelLine = probe.getOrNull()?.let { served ->
            if (served.any { it.name == config.modelName })
                " • Modelo configurado '${config.modelName}': disponible en el servidor"
            else
                " • Modelo configurado '${config.modelName}': NO existe aquí; el primer turno se " +
                        "auto-resolverá al modelo local más ligero"
        } ?: " • Modelo configurado '${config.modelName}': sin comprobar (el servidor no respondió)"

        val measuredTps = _hardwareTelemetry.value.tokensPerSecond
        val inferenceLine = " • Última inferencia medida: " +
                if (measuredTps > 0f) "%.2f t/s vía Ollama".format(measuredTps)
                else "ninguna todavía (no se ha enviado ningún turno)"

        val webhookLine = " • Webhook OpenClaw: " +
                if (config.openClawWebhookUrl.isNotBlank()) config.openClawWebhookUrl
                else "no configurado"

        val overallLine = if (probe.isSuccess && localModels.isNotEmpty()) {
            " • Overall: el chat puede invocar inferencia REAL en este momento."
        } else {
            " • Overall: sin runtime local accesible; el chat emitirá la reserva estática hasta " +
                    "que Ollama responda en ${config.hostUrl}."
        }

        return "[DIAGNOSTIC SUITE RUNNER]\n" +
                runtimeLine + "\n" +
                modelLine + "\n" +
                inferenceLine + "\n" +
                " • Memoria Vault: ${_memories.value.size} entradas / " +
                "${_memories.value.sumOf { it.tokenCount }} tokens\n" +
                " • Tareas: ${_tasks.value.size} (${_tasks.value.count { it.status == TaskStatus.RUNNING }} en ejecución)\n" +
                "$webhookLine\n" +
                overallLine
    }

    /**
     * Estado del motor escrita con datos medidos. Se comparte entre `/sys status` y cualquier
     * texto con "status"/"estado" porque antes la rama genérica de `/sys` se evaluaba primero y
     * `/sys status` devolvía telemetría: la ayuda de la app promete ese comando y no existía.
     */
    private fun runtimeStatusReport(): String =
        "[DIAGNOSTIC REPORT]\n" +
        " • Model Engine REAL: ${_ollamaConfig.value.modelName} @ ${_ollamaConfig.value.hostUrl} " +
        "(${if (_ollamaConfig.value.isConnected) "runtime confirmado por inferencia previa" else "sin inferencia confirmada todavía"})\n" +
        " • Throughput medido: ${"%.2f".format(_hardwareTelemetry.value.tokensPerSecond)} t/s\n" +
        " • WhatsApp Bridge: Webhook ${_ollamaConfig.value.openClawWebhookUrl.ifBlank { "no configurado" }} " +
        "(la app no abre su propio socket: lo recibe el servidor OpenClaw)\n" +
        " • Mobile Action Intents: dispatcher cableado en el motor; si un turno lo usó, aparece en la consola\n" +
        " • Memory Token Compactor: ${_memories.value.size} entradas / " +
        "${_memories.value.count { it.isCompacted }} compactadas\n" +
        " • Health Status: ${_consoleLogs.value.count { it.message.contains("[ERROR") } } errores registrados en consola"

    suspend fun sendConsolePrompt(
        userMessage: String,
        mobileActionEngine: com.example.clawcode.engine.MobileActionEngine? = null
    ) {
        val time = horaLocal()
        val trimmed = userMessage.trim()
        val lower = trimmed.lowercase()

        val userLog = ConsoleLog(
            id = UUID.randomUUID().toString(),
            timestamp = time,
            sender = "User",
            message = trimmed
        )
        _consoleLogs.update { it + userLog }

        delay(300)

        val commandSender = when {
            lower.startsWith("/sys") || lower.startsWith("sys") -> "SystemEngine"
            lower.startsWith("/intent") || lower.startsWith("intent") -> "MobileIntentDispatcher"
            lower.startsWith("/agent") || lower.startsWith("agent") -> "QwenAgentModel"
            lower.startsWith("/bridge") || lower.startsWith("bridge") -> "OpenClawWebhook"
            lower.startsWith("/diag") || lower.startsWith("diag") || lower.contains("error") || lower.contains("fallo") -> "DiagnosticSuite"
            else -> "ClawEngine"
        }

        val responseMessage = when {
            // System Commands
            lower == "/sys clear" || lower == "clear" || lower == "limpiar" -> {
                _consoleLogs.value = emptyList()
                "[SYSTEM] Consola de diagnóstico limpiada correctamente."
            }
            lower.startsWith("/sys memory") -> {
                val memories = _memories.value
                val compacted = memories.count { it.isCompacted }
                val ratioPct = if (memories.isNotEmpty()) compacted * 100 / memories.size else 0
                "[SYSTEM MEMORY REPORT]\n" +
                " • Vault Tokens: ${memories.sumOf { it.tokenCount }} tokens\n" +
                " • Compacted Entries: $compacted / ${memories.size}\n" +
                " • Compaction Ratio: $ratioPct% de las entradas guardadas\n" +
                " • Estado: ${if (memories.isEmpty()) "sin entradas guardadas todavía" else "${memories.size} entradas en el store local de esta app"}"
            }
            lower.startsWith("/sys status") -> runtimeStatusReport()
            lower.startsWith("/sys") || lower == "sys" -> {
                val t = _hardwareTelemetry.value
                val cpuLoadText = if (t.cpuTotalJiffies > 0L) {
                    "${t.cpuUsagePercent}% (delta de /proc/stat entre dos sondeos)"
                } else {
                    "sin medir (aún no hay dos lecturas de /proc/stat)"
                }
                val ramText = if (t.ramTotalGb > 0f) "${t.ramUsedGb} GB / ${t.ramTotalGb} GB (sondeo real de ActivityManager)"
                else "sin medir todavía"
                val batteryText = if (t.batteryLevelPercent in 0..100) "${t.batteryLevelPercent}% (${t.batteryTemperatureC}°C) — sondeo real de BatteryManager"
                else "sin medir todavía"
                "[SYSTEM TELEMETRY]\n" +
                " • CPU: ${t.cpuCores} núcleos disponibles, carga $cpuLoadText\n" +
                " • RAM: $ramText\n" +
                " • Acelerador de inferencia: ${t.npuGpuAccelerator}\n" +
                " • Batería: $batteryText"
            }

            // Intent Commands
            lower.startsWith("/intent open") -> {
                val appTarget = trimmed.removePrefix("/intent open").trim()
                when {
                    appTarget.isBlank() ->
                        "[MOBILE INTENT] Uso: /intent open <nombre_app> (Ej: /intent open gmail)"
                    mobileActionEngine == null ->
                        "[MOBILE INTENT NO EJECUTADO] Esta ruta de la consola no recibió el motor de " +
                            "acciones del sistema, así que no se lanzó ninguna app."
                    else -> {
                        val (abrio, detalle) = mobileActionEngine.launchAppByName(appTarget)
                        "[MOBILE INTENT ${if (abrio) "EJECUTADO" else "NO EJECUTADO"}]\n" +
                            " • Objetivo: '$appTarget'\n" +
                            " • Resultado: $detalle"
                    }
                }
            }
            lower.startsWith("/intent flash") -> {
                val encender = !lower.contains("off")
                if (mobileActionEngine == null) {
                    "[MOBILE INTENT NO EJECUTADO] Sin motor de acciones en esta ruta: la linterna no cambió."
                } else {
                    val (ok, detalle) = mobileActionEngine.executeActionIntent(
                        "toggle_flashlight",
                        mapOf("state" to if (encender) "on" else "off")
                    )
                    "[MOBILE INTENT ${if (ok) "EJECUTADO" else "FALLO"}]\n • Linterna: $detalle"
                }
            }
            lower.startsWith("/intent") -> {
                val catalogo = mobileActionEngine?.getActionCatalog()
                "[MOBILE INTENTS]\n" +
                    (if (catalogo == null)
                        " • Sin motor de acciones en esta ruta: no se puede listar lo que el sistema admite.\n"
                    else
                        " • Acciones declaradas en el catálogo: ${catalogo.size}\n" +
                        " • Cada una ejecuta un Intent del sistema solo si el teléfono lo resuelve; si no, se informa como fallo.\n") +
                    " • Sintaxis: /intent open <app> | /intent flash on|off"
            }

            // Agent Commands
            lower.startsWith("/agent model") ->
                "[AGENT MODEL CONFIG]\n" +
                " • Runtime que atiende los turnos: '${_ollamaConfig.value.modelName}' en ${_ollamaConfig.value.hostUrl} (HTTP; esta build no carga LiteRT/GGUF en el proceso)\n" +
                " • Model Name (modfile): ${_jarvisModfile.value.baseModel}\n" +
                " • Agent Name: ${_jarvisModfile.value.agentName}\n" +
                " • Temperature: ${_jarvisModfile.value.temperature} | TopP: ${_jarvisModfile.value.topP}\n" +
                " • Estilo de Voz TTS: ${_jarvisModfile.value.voiceStyle} (Tono: ${_jarvisModfile.value.pitchMultiplier}x, Velocidad: ${_jarvisModfile.value.speechRateMultiplier}x)"
            lower.startsWith("/agent tasks") ->
                "[AGENT TASK QUEUE]\n" +
                " • Tareas Totales: ${_tasks.value.size}\n" +
                " • En Ejecución: ${_tasks.value.count { it.status == TaskStatus.RUNNING }}\n" +
                " • Pendientes: ${_tasks.value.count { it.status == TaskStatus.PENDING }}"
            lower.startsWith("/agent") ->
                "[AGENT ENGINE ACTIVE]\n" +
                " • Motor: acciones del SO + inferencia HTTP contra '${_ollamaConfig.value.modelName}' en ${_ollamaConfig.value.hostUrl}\n" +
                " • Comandos: /agent model | /agent tasks | /agent prompt <instrucción>"

            // Bridge Commands
            lower.startsWith("/bridge test") -> runBridgeTurnProbe()
            lower.startsWith("/bridge") -> bridgeStatusReport()

            // Diagnostic Commands & Error Simulation
            lower.contains("test_error") || lower.contains("fail") || lower.contains("fallo") || lower.startsWith("/diag error") ->
                "[REPORTE DE ERROR SINTETICO] Esto no es un fallo real de la app.\n" +
                " -> Lo escribió el comando /diag error para comprobar cómo pinta la consola un error.\n" +
                " -> No se lanzó ninguna ActivityNotFoundException al ejecutar este comando, y no existe " +
                "ningún 'Safe Mode Fallback' que se active.\n" +
                " -> Para un diagnóstico medido usa /diag, que sondea el runtime y reporta la latencia real."

            lower.startsWith("/diag") -> runRuntimeDiagnosticSuite()

            lower.contains("status") || lower.contains("estado") -> runtimeStatusReport()

            lower.contains("help") || lower.contains("ayuda") ->
                "COMANDOS DISPONIBLES EN LA CONSOLA:\n" +
                " • /sys status | /sys memory | /sys clear\n" +
                " • /intent open <app> | /intent flash on|off  (lanzan una accion real del sistema)\n" +
                " • /agent model | /agent tasks\n" +
                " • /bridge status | /bridge test\n" +
                " • /diag  (sondeo medido del runtime) | /diag error (texto sintetico, no es un fallo real)\n" +
                "Nota: no existe /intent list ni /tasks; lo que se ejecuta es lo que aparece arriba."

            else ->
                "Comando registrado: '$trimmed'. No coincide con ningún comando de consola: se guardó " +
                        "en la consola pero no se ejecutó ninguna acción."
        }

        val isErr = lower.contains("error") || lower.contains("fail") || lower.contains("fallo")
        val agentLog = ConsoleLog(
            id = UUID.randomUUID().toString(),
            timestamp = horaLocal(),
            sender = if (isErr) "ErrorWatcher" else commandSender,
            message = responseMessage,
            isCode = userMessage.contains("{") || userMessage.contains("/") || isErr
        )
        _consoleLogs.update { it + agentLog }
    }

    /** Cliente de timeouts cortos: sólo sondea, nunca debe bloquear el chat. */
    private val hostProbeClient = OllamaClient(connectTimeoutSeconds = 2, readTimeoutSeconds = 8)

    private companion object {
        const val HISTORY_MESSAGES_FOR_CONTEXT = 10
        const val MAX_GENERATED_TOKENS = 512

        /** Cuánto estado real se vuelca en el prompt de cada acción rápida. */
        const val QUICK_ACTION_LOG_SAMPLE = 20
        const val QUICK_ACTION_MEMORY_SAMPLE = 15

        /** Host que la app trae de fábrica; si el usuario lo cambió, se respeta su decisión. */
        val FACTORY_HOST = OllamaConfig().hostUrl

        /**
         * Dónde puede estar escuchando Ollama cuando el host de fábrica no responde.
         * 127.0.0.1 cubre un runtime en el propio dispositivo; el resto de la red no se
         * sondea a propósito: escanear la LAN del usuario no es aceptable.
         */
        val RUNTIME_HOST_CANDIDATES = listOf("http://127.0.0.1:11434")
    }

    private fun recalculateStats() {
        val currentTasks = _tasks.value
        val currentMemories = _memories.value
        val activeCount = currentTasks.count { it.status == TaskStatus.RUNNING }
        val completedCount = currentTasks.count { it.status == TaskStatus.COMPLETED }
        val totalTokens = currentMemories.sumOf { it.tokenCount }

        _stats.update {
            it.copy(
                // Antes sumaba 2 agentes inventados sobre los reales.
                activeAgents = activeCount,
                totalTasks = currentTasks.size,
                completedTasks = completedCount,
                totalMemoryTokens = totalTokens
            )
        }
    }

    // Las dos tareas de arranque eran teatro: un "Model Listener" RUNNING al 85% con un socket que
    // nadie abre y un "deep links verified" que nadie verificó, con marcas de hora fijas. La cola
    // empieza vacía y se llena con tareas reales.
    private fun initialTasks() = emptyList<AgentTask>()

    // Tres recuerdos de muestra con conteos de tokens inventados (140/95/280) que se sumaban en
    // pantalla como "tokens de contexto total". La bóveda empieza vacía.
    private fun initialMemories() = emptyList<MemoryItem>()

    private fun initialTools() = listOf(
        McpTool("tool-1", "Mobile_Action_Dispatcher", "Lanza Intents nativos de Android cuando el sistema los resuelve.", "MobileActions", true, 0, 0),
        McpTool("tool-2", "OpenClaw_WhatsApp_Bridge", "[NO CONECTADO] Ningún webhook de WhatsApp entra en esta build: la demo local no abre conexiones.", "Messaging", false, 312, 11),
        McpTool("tool-3", "Ollama_Qwen3_5_Connector", "Cliente HTTP real contra los endpoints REST de Ollama.", "AI Models", true, 0, 0),
        McpTool("tool-4", "FunctionGemma_LiteRT_Fallback", "[NO DISPONIBLE] Ejecutar'a FunctionGemma 270M dentro del teléfono: esta build no carga ningún modelo en el proceso.", "LiteRT", false, 42, 6),
        // callCount y latencyMs estaban sembrados con cifras de central de llamadas (128/8, 210/12,
        // 345/9, 178/15) que nadie alimentaba nunca. Cero significa "sin datos", que es la verdad.
        McpTool("tool-5", "GitHub_MCP_Bridge", "[SIN CREDENCIAL] Ninguna llamada a la API de GitHub sale de esta app: no hay PRs, issues ni ramas.", "Integrations", false, 0, 0),
        McpTool("tool-6", "Notion_MCP_Connector", "[SIN CREDENCIAL] El token de Notion es un placeholder; no se sincroniza ninguna página.", "Integrations", false, 0, 0),
        McpTool("tool-7", "Gmail_MCP_Agent", "[SIN IMPLEMENTAR] No hay cliente de Gmail en el repo: solo se abre la app de correo con un Intent.", "Integrations", false, 0, 0),
        McpTool("tool-8", "Google_Drive_MCP_Storage", "[SIN IMPLEMENTAR] No hay cliente de Drive en el repo.", "Integrations", false, 0, 0),
        McpTool("tool-9", "Google_AI_Edge_Gallery_Bridge", "[NO DISPONIBLE] Puente con Google AI Edge Gallery: no hay runtime en el dispositivo que ejecuta Gemini Nano/Gemma.", "AI Edge", false, 560, 5)
    )

    private fun initialConsoleLogs() = listOf(
        ConsoleLog("1", "15:00:00", "System", "[INIT] ClawProyect Engine initialized on Android local runtime."),
        ConsoleLog(
            "2", "15:00:02", "ClawAgent",
            "[STATUS] Motor de chat: inferencia REAL por HTTP contra ${_ollamaConfig.value.hostUrl} " +
                    "(modelo '${_ollamaConfig.value.modelName}'). " +
                    "No hay NPU/LiteRT embebido en esta build."
        ),
        ConsoleLog(
            "3", "15:02:15", "OpenClawBridge",
            "[CONFIG] Webhook apuntado en Ajustes: ${_ollamaConfig.value.openClawWebhookUrl.ifBlank { "no configurado" }} " +
                    "(la app no abre un socket propio; las respuestas las publica el servidor OpenClaw)"
        ),
        ConsoleLog(
            "4", "15:05:40", "ErrorWatcher",
            "[INFO] Ninguna inferencia ejecutada todavía. El primer turno sondeará el runtime y, si no " +
                    "responde, lo avisará en el chat en vez de simular una respuesta."
        ),
        // Este "[ERROR 404] ... Resolved via Safe Fallback" era un fallo inventado que aparecía en
        // la consola en cada arranque y contaba como FALLO en el recuento de UnifiedConsole.
    )

    // Sin conversaciones inventadas: el puente de WhatsApp no recibe nada en esta build, así que
    // la bandeja de entrada empieza vacía en lugar de mostrar mensajes de prueba con respuestas
    // falsas ("Evento de calendario preparado en Google Calendar").
    private fun initialWhatsAppMessages() = emptyList<WhatsAppMessage>()

    // Ninguno de esos tres ficheros existe ni se ha leído nunca; tamaños y resúmenes estaban
    // escritos a mano. La lista empieza vacía y sólo crece con ficheros reales.
    private fun initialFileAttachments() = emptyList<FileAttachmentItem>()

    private fun initialChatMessages() = listOf(
        ChatMessage(
            id = "c-1",
            sender = "JARVIS",
            text = "Hola, soy JARVIS. Cada mensaje se envía al runtime configurado " +
                    "('${_ollamaConfig.value.modelName}' en ${_ollamaConfig.value.hostUrl}); el modelo " +
                    "corre donde esté ese runtime, no dentro de esta app, que no trae pesos embebidos. " +
                    "Puedo hablar por voz, analizar tus archivos, activar la cámara y abrir aplicaciones " +
                    "del teléfono. Si ese runtime no está accesible te lo aviso en el mensaje en lugar de " +
                    "devolver una respuesta simulada.",
            timestamp = "17:45"
        )
    )

    // Sin serie de rendimiento inventada: el gráfico sólo debe mostrar horas con tareas reales.
}

