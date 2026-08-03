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
        assertNotNull("Telemetry object should not be null", telemetry)
        assertTrue("Battery level should be valid", telemetry.batteryLevelPercent in 0..100)
        assertTrue("CPU cores should be greater than 0", telemetry.cpuCores > 0)
    }
}
