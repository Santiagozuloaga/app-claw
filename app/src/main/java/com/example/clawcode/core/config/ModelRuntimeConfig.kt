package com.example.clawcode.core.config

data class ModelRuntimeConfig(
    val activeModelId: String = "qwen-3.5-3b-gguf",
    val activeModelName: String = "sin modelo activo (la app no ejecuta GGUF en su proceso)",
    val systemPrompt: String = "Eres CLAW, un asistente de Inteligencia Artificial autónomo totalmente independiente en Android.",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val repeatPenalty: Float = 1.1f,
    val contextLength: Int = 8192,
    val powerSavingMode: Boolean = false,
    val cpuThreads: Int = 4,
    val useNpuGpuAccelerator: Boolean = true
)
