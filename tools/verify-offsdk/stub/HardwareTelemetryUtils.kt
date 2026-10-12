package com.example.clawcode.engine

/**
 * STUB SOLO PARA VERIFICACIÓN FUERA DE ANDROID. NO forma parte de la app.
 *
 * El real lee ActivityManager/BatteryManager a través de SystemMonitor. Nada de eso existe fuera
 * de Android, pero este stub NO es un passthrough: una versión anterior devolvía
 * `previousTelemetry` tal cual y con eso tapaba por completo el bucle de telemetría del
 * ClawViewModel, de modo que el arnés daba verde sin comprobar nada.
 *
 * Imita el comportamiento real de SystemMonitor.captureMetrics(): devuelve previous.copy(...)
 * actualizando cpuUsagePercent y SIN tocar tokensPerSecond (verificado en SystemMonitor.kt:37-44).
 * Además puede lanzar, que es lo que hay que poder probar.
 */

import android.content.Context
import com.example.clawcode.data.model.HardwareTelemetry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

object HardwareTelemetryUtils {
    val calls = AtomicInteger(0)
    val thrown = CopyOnWriteArrayList<String>()

    /** nº de sondeos consecutivos que deben fallar al intentar leer el sistema. */
    @Volatile var failNext: Int = 0
    @Volatile var exceptionKind: String = "SecurityException"

    fun reset() {
        calls.set(0)
        thrown.clear()
        failNext = 0
        exceptionKind = "SecurityException"
    }

    fun getRealTelemetry(context: Context, previousTelemetry: HardwareTelemetry): HardwareTelemetry {
        val n = calls.incrementAndGet()
        if (failNext > 0) {
            failNext--
            thrown += "sondeo#$n"
            throw SecurityException("stub: $exceptionKind al consultar BatteryManager/ActivityManager")
        }
        return previousTelemetry.copy(cpuUsagePercent = n % 30)
    }
}
