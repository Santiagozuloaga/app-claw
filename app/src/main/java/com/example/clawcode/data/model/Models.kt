package com.example.clawcode.data.model

enum class TaskStatus {
    RUNNING, COMPLETED, PENDING, ERROR
}

enum class Priority {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class AgentTask(
    val id: String,
    val title: String,
    val description: String,
    val agentName: String,
    val status: TaskStatus,
    val priority: Priority,
    val progress: Float, // 0.0f to 1.0f
    val createdAt: String,
    val logs: List<String> = emptyList()
)

data class MemoryItem(
    val id: String,
    val key: String,
    val category: String, // Core, Context, Skill, Session
    val value: String,
    val tokenCount: Int,
    val isCompacted: Boolean = false,
    val updatedAt: String
)

data class McpTool(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val isEnabled: Boolean = true,
    val callCount: Int = 0,
    val latencyMs: Int = 12
)

data class AgentStats(
    val activeAgents: Int = 4,
    val totalTasks: Int = 28,
    val completedTasks: Int = 24,
    val totalMemoryTokens: Int = 14250,
    val compactionRatio: Float = 0.68f,
    val systemStatus: String = "OPERATIONAL"
)

data class ConsoleLog(
    val id: String,
    val timestamp: String,
    val sender: String, // User, ClawAgent, ToolExecutor, MemoryEngine
    val message: String,
    val isCode: Boolean = false
)

enum class MobileActionCategory {
    MESSAGING, EMAIL, PRODUCTIVITY, CALENDAR, SYSTEM, NAVIGATION, CONTACTS, APP_LAUNCHER, MULTIMEDIA, AUTOMATION
}

data class InstalledApp(
    val appName: String,
    val packageName: String,
    val categoryName: String = "Application",
    val isSystemApp: Boolean = false
)

enum class ModelTaskMode(val displayName: String) {
    CODING_TERMINAL("Desarrollo y Terminal"),
    REASONING_CHAT("Razonamiento y Chat"),
    MULTIMEDIA_VISION("Análisis Visión"),
    AUTOMATION_MOBILE("Acciones y OS"),
    IDLE_STANDBY("Modo Reposo")
}

enum class AgentPersonality(val displayName: String, val tag: String) {
    JARVIS_ANALYST("JARVIS Analista", "Equilibrado, Cian y Púrpura"),
    CYBERPUNK_HACKER("Cyberpunk Hacker", "Verde Matrix Terminal"),
    ZEN_COUNSELOR("Consejero Zen", "Lila y Azul Mente Calma"),
    TURBO_OPTIMIZER("Optimizador Turbo", "Ámbar Fuego y Carmesí")
}

enum class CustomThemeColorPreset(
    val displayName: String,
    val hexColor: String,
    val description: String
) {
    CYAN("Cian Eléctrico", "#00F0FF", "Derivados suaves (Cian Mente) y fuertes (Cobalto Profundo)"),
    EMERALD("Esmeralda Matrix", "#00FF66", "Derivados suaves (Menta Liger) y fuertes (Bosque Oscuro)"),
    PURPLE("Púrpura Neón", "#9D4EDD", "Derivados suaves (Lila Lavanda) y fuertes (Violeta Noche)"),
    AMBER("Ámbar Fuego", "#FF9100", "Derivados suaves (Crema Dorado) y fuertes (Naranja Escarlata)"),
    CRIMSON("Rojo Carmesí", "#FF1744", "Derivados suaves (Coral Rosado) y fuertes (Borgoña Intenso)"),
    PINK("Rosa Neón", "#FF2A6D", "Derivados suaves (Fucsia Pastel) y fuertes (Magenta Oscuro)"),
    ROYAL_BLUE("Azul Real", "#0088FF", "Derivados suaves (Celeste Agua) y fuertes (Azul Marino)"),
    GOLD("Dorado Neón", "#FFD700", "Derivados suaves (Amarillo Sol) y fuertes (Ámbar Oscuro)")
}

enum class ThermalState(val displayName: String) {
    COOL("Temperatura Óptima (< 35°C)"),
    WARM("Dispositivo Tibio (35 - 41.9°C)"),
    HOT_THROTTLE("Alerta Térmica (>= 42°C)")
}

data class JarvisModfile(
    val id: String = "modfile-1",
    val agentName: String = "JARVIS_Claw_Standard",
    val baseModel: String = "CLAW Standard (Qwen 3.5 4-bit LiteRT)",
    val systemPrompt: String = """
    You are JARVIS, an autonomous AI agent running locally on Android using the unified CLAW Standard engine.
    You have full access to Google Edge Mobile Actions, OpenClaw WhatsApp bridge, and local system intents.
    When asked to open apps, send emails, schedule meetings, create notion notes, or speak, execute the corresponding Mobile Action.
    """.trimIndent(),
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val stopTokens: List<String> = listOf("<|im_end|>", "</s>", "[END]"),
    val enableVoiceTTS: Boolean = true,
    val enableWakeWord: Boolean = true,
    val wakeWord: String = "Jarvis",
    val voiceStyle: String = "Voz 1",
    val pitchMultiplier: Float = 1.0f,
    val speechRateMultiplier: Float = 1.0f,
    val personality: AgentPersonality = AgentPersonality.JARVIS_ANALYST,
    val currentTaskMode: ModelTaskMode = ModelTaskMode.IDLE_STANDBY,
    val useAutoDynamicTheme: Boolean = true,
    val customThemeColorPreset: CustomThemeColorPreset = CustomThemeColorPreset.CYAN,
    val isDarkMode: Boolean = true,
    val selectedAvatarId: Int = 1
)

data class TaskPerformanceMetrics(
    val timeLabel: String,
    val successRatePct: Float, // 0 to 100
    val avgDurationSeconds: Float,
    val totalCompleted: Int,
    val totalFailed: Int
)

data class FileAttachmentItem(
    val id: String,
    val fileName: String,
    val fileType: String, // PDF, Image, Code, JSON, Audio
    val sizeKb: Int,
    val contentSummary: String,
    val isParsedByJarvis: Boolean = true
)

data class MobileActionDef(
    val id: String,
    val name: String,
    val category: MobileActionCategory,
    val description: String,
    val sampleUtterance: String,
    val functionSchemaJson: String,
    val intentAction: String
)

data class WhatsAppMessage(
    val id: String,
    val senderPhone: String,
    val senderName: String,
    val content: String,
    val timestamp: String,
    val actionTriggered: String? = null,
    val responseSent: String? = null
)

data class ChatMessage(
    val id: String,
    val sender: String, // "USER" or "JARVIS"
    val text: String,
    val timestamp: String,
    val attachedFile: String? = null,
    val attachedImageUri: String? = null,
    val isActionExecuted: Boolean = false,
    val actionExecutedName: String? = null
)

data class HardwareTelemetry(
    val cpuUsagePercent: Int = 24,
    val cpuCores: Int = 8,
    val npuGpuAccelerator: String = "LiteRT Vulkan (NPU/Adreno)",
    val ramUsedGb: Float = 3.6f,
    val ramTotalGb: Float = 8.0f,
    val batteryLevelPercent: Int = 88,
    val batteryTemperatureC: Float = 33.5f,
    val tokensPerSecond: Float = 42.8f,
    val totalContextTokens: Int = 16384
)

data class SearchGroundingSource(
    val title: String,
    val url: String,
    val snippet: String
)

data class SearchGroundingResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val query: String,
    val summaryText: String,
    val sources: List<SearchGroundingSource>,
    val timestamp: String,
    val isGroundingActive: Boolean = true
)

data class OllamaConfig(
    val hostUrl: String = "http://10.0.2.2:11434",
    val modelName: String = "qwen3.5:custom-claw",
    val openClawWebhookUrl: String = "https://api.openclaw.io/v1/whatsapp/webhook",
    val useOnDeviceLiteRTFallback: Boolean = true,
    val isConnected: Boolean = true
)

