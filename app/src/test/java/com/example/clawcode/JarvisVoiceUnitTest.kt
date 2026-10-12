package com.example.clawcode

import com.example.clawcode.engine.ClawEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JarvisVoiceUnitTest {

    private lateinit var clawEngine: ClawEngine

    @Before
    fun setUp() {
        clawEngine = ClawEngine()
    }

    @Test
    fun testJarvisVoiceInputTriggerAndResponse() = runBlocking {
        // Given a voice command input for JARVIS
        val voiceCommand = "JARVIS, abre la aplicación de correo electrónico"

        // When sending prompt to engine
        clawEngine.sendConsolePrompt(voiceCommand)

        // Then console logs should contain the user command and engine response
        val logs = clawEngine.consoleLogs.first()
        assertTrue("Console logs should not be empty", logs.isNotEmpty())

        val userLog = logs.find { it.sender == "User" && it.message == voiceCommand }
        assertNotNull("User command log should exist", userLog)

        val responseLog = logs.last()
        assertTrue("Response log should exist", responseLog.message.isNotBlank())
    }

    @Test
    fun testSimulationModeMockLogInjection() = runBlocking {
        // Given initial logs
        val initialLogCount = clawEngine.consoleLogs.value.size

        // When injecting a simulated log event
        clawEngine.injectSimulatedLog("SIMULATED_TEST", "Inferencia de prueba Qwen GGUF verifcada")

        // Then logs should increase by 1
        val newLogs = clawEngine.consoleLogs.value
        assertEquals(initialLogCount + 1, newLogs.size)

        val injectedLog = newLogs.last()
        assertEquals("SIMULATED_TEST", injectedLog.sender)
        assertTrue(injectedLog.message.contains("Qwen GGUF"))
    }

    @Test
    fun testSystemTelemetryInitialization() = runBlocking {
        val telemetry = clawEngine.hardwareTelemetry.value
        assertNotNull("El objeto de telemetría debe existir", telemetry)
        // Un motor recién creado no ha sondeado nada: -1 es "sin medir" y dejaba de ser válido
        // cuando la fábrica venía con 88 %. Sólo tras el primer sondeo cae en 0..100.
        assertTrue(
            "La batería debe estar sin medir (-1) o medida (0..100), nunca un valor de fábrica",
            telemetry.batteryLevelPercent == -1 || telemetry.batteryLevelPercent in 0..100
        )
        assertEquals(
            "Sin sondeo previo la carga de CPU es 0, no un número al azar",
            0, telemetry.cpuUsagePercent
        )
        assertEquals(
            "Sin dos lecturas de /proc/stat no hay instantánea guardada",
            0L, telemetry.cpuTotalJiffies
        )
        assertTrue("Los núcleos sí se consultan a la JVM", telemetry.cpuCores > 0)
    }
}
