package com.example.clawcode.core.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.clawcode.data.model.HardwareTelemetry
import java.io.File

/**
 * Sondeo de hardware con datos que se pueden señalar: batería y RAM salen de las APIs del
 * sistema y la carga de CPU se calcula como delta de /proc/stat entre dos lecturas.
 *
 * Antes la carga era `(15..35).random()`: un número al azar que el Dashboard y `/sys` enseñaban
 * como si fuera actividad real del teléfono.
 */
class SystemMonitor(
    private val context: Context,
    private val procStatPath: String = "/proc/stat"
) {

    private data class CpuSample(val totalJiffies: Long, val idleJiffies: Long)

    fun captureMetrics(previous: HardwareTelemetry): HardwareTelemetry {
        // Battery and Temperature
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, batteryFilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1 && scale > 0) {
            (level * 100 / scale)
        } else previous.batteryLevelPercent

        val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val batteryTempC = if (temp > 0) temp / 10f else previous.batteryTemperatureC

        // Memory RAM
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val totalRamGb = memoryInfo.totalMem / (1024f * 1024f * 1024f)
        val availRamGb = memoryInfo.availMem / (1024f * 1024f * 1024f)
        val usedRamGb = if (memoryInfo.totalMem > 0L) totalRamGb - availRamGb else previous.ramUsedGb

        // CPU: delta real de /proc/stat. Sin lectura previa no hay delta, y se conserva el valor
        // anterior (0 = "sin medir") en lugar de inventar uno.
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val sample = readCpuSample()
        val cpuLoad = if (sample == null || previous.cpuTotalJiffies <= 0L) {
            previous.cpuUsagePercent
        } else {
            val totalDelta = sample.totalJiffies - previous.cpuTotalJiffies
            val idleDelta = sample.idleJiffies - previous.cpuIdleJiffies
            if (totalDelta > 0L) {
                ((totalDelta - idleDelta) * 100 / totalDelta).toInt().coerceIn(0, 100)
            } else {
                previous.cpuUsagePercent
            }
        }

        return previous.copy(
            batteryLevelPercent = batteryPct,
            batteryTemperatureC = batteryTempC,
            ramTotalGb = if (memoryInfo.totalMem > 0L) totalRamGb else previous.ramTotalGb,
            ramUsedGb = usedRamGb,
            cpuCores = cpuCores,
            cpuUsagePercent = cpuLoad,
            cpuTotalJiffies = sample?.totalJiffies ?: previous.cpuTotalJiffies,
            cpuIdleJiffies = sample?.idleJiffies ?: previous.cpuIdleJiffies
        )
    }

    /** Primera fila "cpu ..." de /proc/stat: usuario, nice, sistema, idle, iowait, irq, ... */
    private fun readCpuSample(): CpuSample? {
        val line = runCatching {
            File(procStatPath).bufferedReader().use { reader ->
            generateSequence { reader.readLine() }.firstOrNull { it.startsWith("cpu ") }
            }
        }.getOrNull() ?: return null

        val values = line.trim().split(WHITESPACE).drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 5) return null
        val idle = values[3] + values[4]
        return CpuSample(totalJiffies = values.sum(), idleJiffies = idle)
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
