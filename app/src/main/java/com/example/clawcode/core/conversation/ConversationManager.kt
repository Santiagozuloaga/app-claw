package com.example.clawcode.core.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.clawcode.core.personality.PersonalityManager

/**
 * Módulo 3 - Conversation
 * Encargado de Prompt, Historial, Streaming, Tokens y Contexto.
 */
class ConversationManager(private val personalityManager: PersonalityManager) {
    private val _history = MutableStateFlow<List<Message>>(emptyList())
    val history: StateFlow<List<Message>> = _history.asStateFlow()

    private var _totalTokens = MutableStateFlow(0)
    val totalTokens: StateFlow<Int> = _totalTokens.asStateFlow()

    init {
        initializeConversation()
    }

    private fun initializeConversation() {
        val systemPrompt = personalityManager.getSystemPrompt()
        val sysMsg = Message(Role.SYSTEM, systemPrompt)
        _history.value = listOf(sysMsg)
        recalculateTokens()
    }

    fun addUserMessage(content: String) {
        val current = _history.value.toMutableList()
        current.add(Message(Role.USER, content))
        _history.value = current
        recalculateTokens()
    }

    fun addModelMessage(content: String) {
        val current = _history.value.toMutableList()
        current.add(Message(Role.MODEL, content))
        _history.value = current
        recalculateTokens()
    }

    fun clearHistory() {
        initializeConversation()
    }

    /**
     * Estimación simple de tokens (aprox 4 caracteres por token)
     */
    private fun recalculateTokens() {
        var tokens = 0
        _history.value.forEach {
            tokens += it.content.length / 4
        }
        _totalTokens.value = tokens
    }
}
