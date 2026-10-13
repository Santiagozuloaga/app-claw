package com.example.clawcode.core.config

data class ModelRuntimeConfig(
    // Con este valor de fabrica la tarjeta "MODELO ACTIVO" y el badge "EN USO" se encendian en el
    // primer arranque sobre un modelo que no esta instalado ni se ejecuta en el proceso.
    val activeModelId: String = "",
    val activeModelName: String = "sin modelo activo (la app no ejecuta GGUF en su proceso)",
    val systemPrompt: String = "Eres CLAW, un asistente de Inteligencia Artificial autónomo totalmente independiente en Android.",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val repeatPenalty: Float = 1.1f,
    val contextLength: Int = 8192,
    val powerSavingMode: Boolean = false,
    val cpuThreads: Int = 4,
    // HardwareTelemetry.npuGpuAccelerator reporta "Ninguno" en esta build.
    val useNpuGpuAccelerator: Boolean = false
)
