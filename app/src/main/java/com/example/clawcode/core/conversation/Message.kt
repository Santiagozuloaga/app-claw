package com.example.clawcode.core.conversation

data class Message(
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class Role {
    USER, MODEL, SYSTEM
}
