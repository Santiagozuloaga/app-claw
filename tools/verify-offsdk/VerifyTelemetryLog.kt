package verify

import com.example.clawcode.engine.ClawEngine

fun main() {
    val engine = ClawEngine()
    println("--- antes ---")
    println("logs totales = ${engine.consoleLogs.value.size}")

    engine.logTelemetryFailure(SecurityException("prueba aislada"))

    println("--- despues de logTelemetryFailure ---")
    println("logs totales = ${engine.consoleLogs.value.size}")
    engine.consoleLogs.value.forEach { println("   [${it.sender}] ${it.message.take(120)}") }

    val hit = engine.consoleLogs.value.any { it.message.contains("[TELEMETRY_POLL_FAILED]") }
    println(if (hit) "DIAGNOSTICO: el log S aparece en el motor" else "DIAGNOSTICO: NO aparece")
}
