package com.example.clawcode.core

sealed class AppEvent {
    // Conversación
    data class UserMessageSent(val message: String) : AppEvent()
    data class ClawMessageGenerated(val message: String) : AppEvent()
    
    // Archivos
    data class FileOpened(val uri: String, val fileType: String) : AppEvent()
    data class FileParsed(val uri: String, val content: String) : AppEvent()
    
    // Modelos
    data class ModelChanged(val modelName: String) : AppEvent()
    data class ModelDownloadStarted(val modelName: String) : AppEvent()
    
    // Sistema
    data class HardwareStatusUpdated(val battery: Int, val isCharging: Boolean) : AppEvent()
    
    // Plugins
    data class PluginTriggered(val pluginId: String, val payload: String) : AppEvent()
}
