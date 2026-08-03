package com.example.clawcode.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event Bus Centralizado para CLAW OS.
 * Desacopla los módulos (Core, Built-in, Plugins) permitiendo que reaccionen 
 * a eventos del sistema sin conocerse directamente.
 */
object EventBus {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    suspend fun publish(event: AppEvent) {
        _events.emit(event)
    }
    
    fun tryPublish(event: AppEvent) {
        _events.tryEmit(event)
    }
}
