package com.example.clawcode.engine

import com.example.clawcode.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*

class ClawEngine {

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _tasks = MutableStateFlow<List<AgentTask>>(initialTasks())
    val tasks: StateFlow<List<AgentTask>> = _tasks.asStateFlow()

    private val _memories = MutableStateFlow<List<MemoryItem>>(initialMemories())
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    private val _tools = MutableStateFlow<List<McpTool>>(initialTools())
    val tools: StateFlow<List<McpTool>> = _tools.asStateFlow()

    private val _stats = MutableStateFlow(AgentStats())
    val stats: StateFlow<AgentStats> = _stats.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<ConsoleLog>>(initialConsoleLogs())
    val consoleLogs: StateFlow<List<ConsoleLog>> = _consoleLogs.asStateFlow()

    private val _isCompacting = MutableStateFlow(false)
    val isCompacting: StateFlow<Boolean> = _isCompacting.asStateFlow()

    private val _ollamaConfig = MutableStateFlow(OllamaConfig())
    val ollamaConfig: StateFlow<OllamaConfig> = _ollamaConfig.asStateFlow()

    private val _whatsAppMessages = MutableStateFlow<List<WhatsAppMessage>>(initialWhatsAppMessages())
    val whatsAppMessages: StateFlow<List<WhatsAppMessage>> = _whatsAppMessages.asStateFlow()

    private val _jarvisModfile = MutableStateFlow(JarvisModfile())
    val jarvisModfile: StateFlow<JarvisModfile> = _jarvisModfile.asStateFlow()

    private val _fileAttachments = MutableStateFlow<List<FileAttachmentItem>>(initialFileAttachments())
    val fileAttachments: StateFlow<List<FileAttachmentItem>> = _fileAttachments.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(initialChatMessages())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _hardwareTelemetry = MutableStateFlow(HardwareTelemetry())
    val hardwareTelemetry: StateFlow<HardwareTelemetry> = _hardwareTelemetry.asStateFlow()

    suspend fun sendChatMessage(
        text: String,
        attachedFileName: String? = null,
        attachedImageUri: String? = null,
        mobileActionEngine: MobileActionEngine
    ) {
        val time = dateFormat.format(Date())
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = "USER",
            text = text,
            timestamp = time,
            attachedFile = attachedFileName,
            attachedImageUri = attachedImageUri
        )
        _chatMessages.update { it + userMsg }

        delay(600)

        // Try tool call matching
        val (actionName, params) = mobileActionEngine.parseNaturalLanguageToFunctionCall(text)
        val (success, resultMsg) = mobileActionEngine.executeActionIntent(actionName, params)

        val time2 = dateFormat.format(Date())
        val jarvisResponseText = if (actionName.isNotBlank() && success) {
            "Acción ejecutada en el dispositivo: '$actionName' (${params.values.firstOrNull() ?: ""}).\nResultado: $resultMsg"
        } else {
            "Comprendido. He procesado tu solicitud '${text.take(30)}...' usando Qwen 3.5 local (NPU/LiteRT). " +
                    if (attachedFileName != null) "He analizado el archivo '$attachedFileName'." else ""
        }

        val jarvisMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = "JARVIS",
            text = jarvisResponseText,
            timestamp = time2,
            isActionExecuted = success,
            actionExecutedName = actionName
        )
        _chatMessages.update { it + jarvisMsg }

        // Speak if TTS enabled
        if (_jarvisModfile.value.enableVoiceTTS) {
            mobileActionEngine.speak(jarvisResponseText)
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

    fun updateJarvisModfile(
        agentName: String,
        baseModel: String,
        systemPrompt: String,
        temperature: Float,
        topP: Float,
        wakeWord: String,
        enableVoiceTTS: Boolean
    ) {
        _jarvisModfile.update {
            it.copy(
                agentName = agentName,
                baseModel = baseModel,
                systemPrompt = systemPrompt,
                temperature = temperature,
                topP = topP,
                wakeWord = wakeWord,
                enableVoiceTTS = enableVoiceTTS
            )
        }
    }

    fun addFileAttachment(fileName: String, fileType: String, sizeKb: Int, summary: String) {
        val newFile = FileAttachmentItem(
            id = "file-" + UUID.randomUUID().toString().take(6),
            fileName = fileName,
            fileType = fileType,
            sizeKb = sizeKb,
            contentSummary = summary,
            isParsedByJarvis = true
        )
        _fileAttachments.update { listOf(newFile) + it }
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
    }

    suspend fun simulateWhatsAppIncoming(
        phoneName: String,
        messageText: String,
        mobileActionEngine: MobileActionEngine
    ) {
        val time = dateFormat.format(Date())
        val (actionName, params) = mobileActionEngine.parseNaturalLanguageToFunctionCall(messageText)

        val newMsg = WhatsAppMessage(
            id = "wa-" + UUID.randomUUID().toString().take(6),
            senderPhone = "+573009988776",
            senderName = phoneName,
            content = messageText,
            timestamp = time,
            actionTriggered = actionName,
            responseSent = "Respuesta enviada vía OpenClaw (Qwen 3.5): Función ejecutada -> $actionName"
        )

        _whatsAppMessages.update { listOf(newMsg) + it }

        // Execute action intent
        val (success, logText) = mobileActionEngine.executeActionIntent(actionName, params)

        val log = ConsoleLog(
            id = UUID.randomUUID().toString(),
            timestamp = time,
            sender = "OpenClawBridge",
            message = "[WHATSAPP HOOK] Recibido de '$phoneName': '$messageText' -> Parsed Function: $actionName | Status: $logText"
        )
        _consoleLogs.update { it + log }
    }

    suspend fun runAsyncAgentTaskLoop(
        prompt: String,
        mobileActionEngine: MobileActionEngine
    ) {
        val time = dateFormat.format(Date())
        val taskId = "task-" + UUID.randomUUID().toString().take(6)

        val newTask = AgentTask(
            id = taskId,
            title = "JARVIS Async Loop: $prompt",
            description = "Ejecución autónoma asíncrona local (Qwen 3.5 LiteRT-LM / NPU)",
            agentName = _jarvisModfile.value.agentName,
            status = TaskStatus.RUNNING,
            priority = Priority.HIGH,
            progress = 0.1f,
            createdAt = time,
            logs = listOf("[$time][ASYNC_INIT] Agente autónomo despertado por wake-word o prompt local.")
        )
        _tasks.update { listOf(newTask) + it }
        recalculateStats()

        delay(800)
        val time2 = dateFormat.format(Date())
        _tasks.update { list ->
            list.map {
                if (it.id == taskId) {
                    it.copy(
                        progress = 0.4f,
                        logs = it.logs + "[$time2][TOOL_CALL_DISPATCH] Analizando intención NLU y verificando conectividad de sensores..."
                    )
                } else it
            }
        }

        val (actionName, params) = mobileActionEngine.parseNaturalLanguageToFunctionCall(prompt)
        val (success, resultMsg) = mobileActionEngine.executeActionIntent(actionName, params)

        delay(1000)
        val time3 = dateFormat.format(Date())
        _tasks.update { list ->
            list.map {
                if (it.id == taskId) {
                    it.copy(
                        status = TaskStatus.COMPLETED,
                        progress = 1.0f,
                        logs = it.logs +
                                "[$time3][MOBILE_ACTION_EXECUTED] Acción '$actionName' invocada de forma asíncrona." +
                                "[$time3][ASYNC_DONE] Resultado: $resultMsg. Memoria sintetizada."
                    )
                } else it
            }
        }

        // Log to console
        val consoleLog = ConsoleLog(
            id = UUID.randomUUID().toString(),
            timestamp = time3,
            sender = "JARVIS_ASYNC_WORKER",
            message = "[LOCAL_NPU_AGENT] Tarea asíncrona completada de forma autónoma (Sin Ollama / 100% On-Device): $prompt -> $resultMsg"
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
            createdAt = dateFormat.format(Date()),
            logs = listOf("[INIT] Task queued in Claw Engine pipeline.")
        )
        _tasks.update { listOf(newTask) + it }
        recalculateStats()
    }

    fun runTask(taskId: String) {
        _tasks.update { list ->
            list.map { task ->
                if (task.id == taskId) {
                    task.copy(
                        status = TaskStatus.RUNNING,
                        progress = 0.25f,
                        logs = task.logs + "[EXEC] Subagent ${task.agentName} activated. Processing payload..."
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
        _isCompacting.value = true
        delay(1200)
        _memories.update { list ->
            list.map { item ->
                if (!item.isCompacted) {
                    item.copy(
                        tokenCount = (item.tokenCount * 0.45).toInt(),
                        isCompacted = true,
                        updatedAt = dateFormat.format(Date())
                    )
                } else item
            }
        }
        _isCompacting.value = false
        recalculateStats()
    }

    fun addMemory(key: String, category: String, value: String) {
        val newMem = MemoryItem(
            id = "mem-" + UUID.randomUUID().toString().take(6),
            key = key,
            category = category,
            value = value,
            tokenCount = value.length * 4,
            isCompacted = false,
            updatedAt = dateFormat.format(Date())
        )
        _memories.update { listOf(newMem) + it }
        recalculateStats()
    }

    suspend fun sendConsolePrompt(userMessage: String) {
        val time = dateFormat.format(Date())
        val userLog = ConsoleLog(
            id = UUID.randomUUID().toString(),
            timestamp = time,
            sender = "User",
            message = userMessage
        )
        _consoleLogs.update { it + userLog }

        delay(500)

        val thinkingLog = ConsoleLog(
            id = UUID.randomUUID().toString(),
            timestamp = dateFormat.format(Date()),
            sender = "ClawAgent",
            message = "[THINKING] Processing prompt with Ollama (${_ollamaConfig.value.modelName}). Checking Mobile Actions & OpenClaw WhatsApp bridge..."
        )
        _consoleLogs.update { it + thinkingLog }

        delay(800)

        val responseMessage = when {
            userMessage.lowercase().contains("status") || userMessage.lowercase().contains("estado") ->
                "Claw System Operational. Connected to Ollama host '${_ollamaConfig.value.hostUrl}' (Model: ${_ollamaConfig.value.modelName}). OpenClaw Webhook active."
            userMessage.lowercase().contains("task") || userMessage.lowercase().contains("tarea") ->
                "Current queue: ${_tasks.value.size} total tasks. Recommended action: run pending tasks from the Task Manager tab."
            userMessage.lowercase().contains("help") || userMessage.lowercase().contains("ayuda") ->
                "ClawProyect AI Agent Command Center:\n• Mobile Actions for Gmail, Notion, WhatsApp, Calendar, Maps, Contacts, System Settings\n• OpenClaw WhatsApp Webhook & Ollama Qwen 3.5 Bridge\n• Context Memory Compaction"
            else ->
                "Received instruction: '$userMessage'. Dispatched via OpenClaw / Mobile Action Dispatcher."
        }

        val agentLog = ConsoleLog(
            id = UUID.randomUUID().toString(),
            timestamp = dateFormat.format(Date()),
            sender = "ClawAgent",
            message = responseMessage,
            isCode = userMessage.contains("{") || userMessage.contains("code")
        )
        _consoleLogs.update { it + agentLog }
    }

    private fun recalculateStats() {
        val currentTasks = _tasks.value
        val currentMemories = _memories.value
        val activeCount = currentTasks.count { it.status == TaskStatus.RUNNING }
        val completedCount = currentTasks.count { it.status == TaskStatus.COMPLETED }
        val totalTokens = currentMemories.sumOf { it.tokenCount }

        _stats.update {
            it.copy(
                activeAgents = if (activeCount > 0) activeCount + 2 else 2,
                totalTasks = currentTasks.size,
                completedTasks = completedCount,
                totalMemoryTokens = totalTokens
            )
        }
    }

    private fun initialTasks() = listOf(
        AgentTask(
            id = "task-101",
            title = "Ollama Qwen 3.5 Model Listener",
            description = "Listening on http://10.0.2.2:11434 for function calling prompts from WhatsApp OpenClaw.",
            agentName = "Qwen3.5_OllamaBridge",
            status = TaskStatus.RUNNING,
            priority = Priority.HIGH,
            progress = 0.85f,
            createdAt = "15:40:12",
            logs = listOf("[INIT] Ollama socket opened", "[MODEL] Loaded qwen3.5:custom-claw", "[LISTENING] Active on OpenClaw Webhook")
        ),
        AgentTask(
            id = "task-102",
            title = "Mobile Actions Intent Dispatcher",
            description = "Handles FunctionGemma / Qwen JSON function calls for Gmail, Notion, Calendar & System Settings.",
            agentName = "MobileActionDispatcher",
            status = TaskStatus.COMPLETED,
            priority = Priority.MEDIUM,
            progress = 1.0f,
            createdAt = "15:22:04",
            logs = listOf("[INIT] Mobile Actions registered", "[TEST] Gmail & Notion deep links verified", "[READY] Intent launcher armed")
        )
    )

    private fun initialMemories() = listOf(
        MemoryItem("mem-1", "ollama_model_target", "Core", "qwen3.5:custom-claw running on local host", 140, true, "15:30"),
        MemoryItem("mem-2", "openclaw_whatsapp_webhook", "Context", "https://api.openclaw.io/v1/whatsapp/webhook", 95, true, "15:15"),
        MemoryItem("mem-3", "mobile_actions_list", "Skill", "Gmail Compose, Notion Deep-link, Calendar Event, WhatsApp Direct, Map Search", 280, false, "15:45")
    )

    private fun initialTools() = listOf(
        McpTool("tool-1", "Mobile_Action_Dispatcher", "Launches native Android Intents for Gmail, Notion, Calendar, Maps.", "MobileActions", true, 189, 4),
        McpTool("tool-2", "OpenClaw_WhatsApp_Bridge", "Routes incoming WhatsApp webhooks to Qwen 3.5 Function Caller.", "Messaging", true, 312, 11),
        McpTool("tool-3", "Ollama_Qwen3_5_Connector", "Connects to local/remote Ollama REST API endpoints.", "AI Models", true, 94, 18),
        McpTool("tool-4", "FunctionGemma_LiteRT_Fallback", "Runs 270M FunctionGemma on-device when offline.", "LiteRT", true, 42, 6)
    )

    private fun initialConsoleLogs() = listOf(
        ConsoleLog("1", "15:00:00", "System", "ClawProyect Engine Initialized."),
        ConsoleLog("2", "15:00:02", "ClawAgent", "Ready! OpenClaw WhatsApp bridge & Mobile Actions active.")
    )

    private fun initialWhatsAppMessages() = listOf(
        WhatsAppMessage(
            id = "wa-101",
            senderPhone = "+573001112233",
            senderName = "Carlos Mendoza",
            content = "Hola agente, por favor crea un evento en mi calendario para la reunión con inversores mañana a las 10am",
            timestamp = "15:42:10",
            actionTriggered = "create_calendar_event",
            responseSent = "Evento de calendario preparado en Google Calendar."
        ),
        WhatsAppMessage(
            id = "wa-102",
            senderPhone = "+573154445566",
            senderName = "Ana Gómez",
            content = "Necesito abrir Notion en la página de Proyectos de IA",
            timestamp = "15:48:33",
            actionTriggered = "open_notion_page",
            responseSent = "Página de Notion abierta mediante deep-link."
        )
    )

    private fun initialFileAttachments() = listOf(
        FileAttachmentItem("f-1", "qwen3.5_modfile.Modfile", "Modfile", 12, "Modfile con System Prompt de JARVIS y esquemas de Mobile Actions"),
        FileAttachmentItem("f-2", "mobile_actions_dataset.json", "JSON", 240, "Dataset de entrenamiento para llamadas a funciones en Android"),
        FileAttachmentItem("f-3", "camera_screen_snapshot.png", "Image", 1024, "Captura de visión OCR analizada por JARVIS")
    )

    private fun initialChatMessages() = listOf(
        ChatMessage(
            id = "c-1",
            sender = "JARVIS",
            text = "Hola, soy JARVIS, tu Agente IA Autónomo 100% On-Device (Qwen 3.5 LiteRT-LM). Puedo hablar por voz, analizar tus archivos, activar la cámara y abrir cualquier aplicación en tu teléfono.",
            timestamp = "17:45"
        )
    )
}
