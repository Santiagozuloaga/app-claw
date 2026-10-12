package com.example.clawcode.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.clawcode.data.model.Priority
import com.example.clawcode.engine.ClawEngine
import com.example.clawcode.engine.MobileActionEngine
import com.example.clawcode.engine.HardwareTelemetryUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ClawViewModel(application: Application) : AndroidViewModel(application) {

    val engine = ClawEngine(
        configStore = com.example.clawcode.core.storage.PrefsOllamaConfigStore(application),
        chatStore = com.example.clawcode.core.storage.PrefsChatHistoryStore(application)
    )
    val mobileActionEngine = MobileActionEngine(application.applicationContext)

    val tasks = engine.tasks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val memories = engine.memories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val tools = engine.tools.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val stats = engine.stats.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        engine.stats.value
    )

    val consoleLogs = engine.consoleLogs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val isCompacting = engine.isCompacting.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val ollamaConfig = engine.ollamaConfig.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        engine.ollamaConfig.value
    )

    val whatsAppMessages = engine.whatsAppMessages.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val jarvisModfile = engine.jarvisModfile.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        engine.jarvisModfile.value
    )

    val fileAttachments = engine.fileAttachments.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val chatMessages = engine.chatMessages.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val chatAwaitingRuntime = engine.chatAwaitingRuntime.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val hardwareTelemetry = engine.hardwareTelemetry.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        engine.hardwareTelemetry.value
    )

    val activeGrounding = engine.activeGrounding.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        engine.activeGrounding.value
    )

    val isGroundingEnabled = engine.isGroundingEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        engine.isGroundingEnabled.value
    )

    val groundingHistory = engine.groundingHistory.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val taskPerformanceMetrics = engine.taskPerformanceMetrics.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        engine.taskPerformanceMetrics.value
    )

    init {
        // Start periodic telemetry updates
        viewModelScope.launch {
            while (isActive) {
                try {
                    engine.updateHardwareTelemetry(
                        HardwareTelemetryUtils.getRealTelemetry(
                            application.applicationContext,
                            engine.hardwareTelemetry.value
                        )
                    )
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    // La cancelación del viewModelScope debe propagarse; no es un fallo.
                    throw cancelled
                } catch (error: Exception) {
                    // El sondeo usa APIs de plataforma (registerReceiver, getMemoryInfo) que pueden
                    // lanzar: un SecurityException o un dispositivo con restricciones en segundo
                    // plano lo dispararía aquí dentro. Sin capturar, la excepción mata el proceso
                    // cada 5 s y se lleva por delante el chat. La telemetría es informativa, así
                    // que se registra el fallo y el bucle sigue intentándolo.
                    engine.logTelemetryFailure(error)
                }
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    fun performSearchGrounding(query: String) {
        viewModelScope.launch {
            engine.performSearchGrounding(query)
        }
    }

    fun clearActiveGrounding() {
        engine.clearActiveGrounding()
    }

    fun setGroundingEnabled(enabled: Boolean) {
        engine.setGroundingEnabled(enabled)
    }

    fun createTaskFromGrounding(grounding: com.example.clawcode.data.model.SearchGroundingResult) {
        engine.createTaskFromGrounding(grounding)
    }


    fun sendChatMessage(text: String, attachedFile: String? = null, attachedImageUri: String? = null) {
        viewModelScope.launch {
            engine.sendChatMessage(text, attachedFile, attachedImageUri, mobileActionEngine)
        }
    }

    fun setSelectedAvatarId(avatarId: Int) {
        engine.setSelectedAvatarId(avatarId)
    }

    fun toggleTool(tool: com.example.clawcode.data.model.McpTool) {
        engine.toggleTool(tool)
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
        personality: com.example.clawcode.data.model.AgentPersonality = jarvisModfile.value.personality,
        currentTaskMode: com.example.clawcode.data.model.ModelTaskMode = jarvisModfile.value.currentTaskMode,
        useAutoDynamicTheme: Boolean = jarvisModfile.value.useAutoDynamicTheme,
        customThemeColorPreset: com.example.clawcode.data.model.CustomThemeColorPreset = jarvisModfile.value.customThemeColorPreset,
        selectedAvatarId: Int = jarvisModfile.value.selectedAvatarId
    ) {
        engine.updateJarvisModfile(
            agentName, baseModel, systemPrompt, temperature, topP, wakeWord,
            enableVoiceTTS, voiceStyle, pitchMultiplier, speechRateMultiplier,
            personality, currentTaskMode, useAutoDynamicTheme, customThemeColorPreset,
            selectedAvatarId
        )
    }

    fun setPersonality(personality: com.example.clawcode.data.model.AgentPersonality) {
        engine.setPersonality(personality)
    }

    fun setTaskMode(taskMode: com.example.clawcode.data.model.ModelTaskMode) {
        engine.setTaskMode(taskMode)
    }

    fun setUseAutoDynamicTheme(auto: Boolean) {
        engine.setUseAutoDynamicTheme(auto)
    }

    fun setCustomThemeColorPreset(preset: com.example.clawcode.data.model.CustomThemeColorPreset) {
        engine.setCustomThemeColorPreset(preset)
    }

    fun setDarkMode(isDark: Boolean) {
        engine.setDarkMode(isDark)
    }

    fun executeQuickActionSummarizeLogs() {
        viewModelScope.launch {
            engine.executeQuickActionSummarizeLogs()
        }
    }

    fun executeQuickActionAnalyzeCode() {
        viewModelScope.launch {
            engine.executeQuickActionAnalyzeCode()
        }
    }

    fun executeQuickActionCheckMemory() {
        viewModelScope.launch {
            engine.executeQuickActionCheckMemory()
        }
    }

    fun setSimulatedTemperature(tempC: Float) {
        engine.setSimulatedTemperature(tempC)
    }

    fun addFileAttachment(fileName: String, fileType: String, sizeKb: Int, summary: String) {
        engine.addFileAttachment(fileName, fileType, sizeKb, summary)
    }

    fun getInstalledApps() = mobileActionEngine.getInstalledApps()

    fun launchAppByName(appName: String) = mobileActionEngine.launchAppByName(appName)

    fun speakVoice(
        text: String,
        voiceStyle: String = jarvisModfile.value.voiceStyle,
        pitchMultiplier: Float = jarvisModfile.value.pitchMultiplier,
        speechRateMultiplier: Float = jarvisModfile.value.speechRateMultiplier
    ) = mobileActionEngine.speak(text, voiceStyle, pitchMultiplier, speechRateMultiplier)

    fun runAsyncAgentTaskLoop(prompt: String) {
        viewModelScope.launch {
            engine.runAsyncAgentTaskLoop(prompt, mobileActionEngine)
        }
    }

    fun executeMobileAction(actionName: String, params: Map<String, String>): Pair<Boolean, String> {
        return mobileActionEngine.executeActionIntent(actionName, params)
    }

    fun parseAndExecuteNaturalLanguageAction(prompt: String): Pair<String, Pair<Boolean, String>> {
        val (actionName, params) = mobileActionEngine.parseNaturalLanguageToFunctionCall(prompt)
        val result = mobileActionEngine.executeActionIntent(actionName, params)
        return Pair(actionName, result)
    }

    fun updateOllamaConfig(hostUrl: String, modelName: String, webhookUrl: String, useFallback: Boolean) {
        engine.updateOllamaConfig(hostUrl, modelName, webhookUrl, useFallback)
    }

    fun simulateWhatsAppIncoming(phoneName: String, messageText: String) {
        viewModelScope.launch {
            engine.simulateWhatsAppIncoming(phoneName, messageText, mobileActionEngine)
        }
    }

    fun createNewTask(title: String, description: String, agentName: String, priority: Priority) {
        engine.addTask(title, description, agentName, priority)
    }

    fun runTask(taskId: String) {
        engine.runTask(taskId)
    }

    fun completeTask(taskId: String) {
        engine.completeTask(taskId)
    }

    fun toggleTool(toolId: String) {
        engine.toggleTool(toolId)
    }

    fun compactMemory() {
        viewModelScope.launch {
            engine.compactMemory()
        }
    }

    fun addMemory(key: String, category: String, value: String) {
        engine.addMemory(key, category, value)
    }

    fun sendConsolePrompt(prompt: String) {
        viewModelScope.launch {
            engine.sendConsolePrompt(prompt)
        }
    }

    fun clearConsoleLogs() {
        engine.clearConsoleLogs()
    }

    fun injectSimulatedLog(sender: String, message: String) {
        engine.injectSimulatedLog(sender, message)
    }
}
