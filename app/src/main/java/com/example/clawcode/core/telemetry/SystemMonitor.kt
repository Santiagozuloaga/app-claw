package com.example.clawcode.core.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.clawcode.data.model.HardwareTelemetry

class SystemMonitor(private val context: Context) {

    fun captureMetrics(previous: HardwareTelemetry): HardwareTelemetry {
        // Battery and Temperature
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, batteryFilter)

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else previous.batteryLevelPercent

        val temp: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val batteryTempC = if (temp > 0) temp / 10f else previous.batteryTemperatureC

        // Memory RAM
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamGb = memoryInfo.totalMem / (1024f * 1024f * 1024f)
        val availRamGb = memoryInfo.availMem / (1024f * 1024f * 1024f)
        val usedRamGb = totalRamGb - availRamGb

        // CPU & Cores
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val estimatedCpuLoad = (15..35).random() // Simulated real-time CPU thread activity percentage

        return previous.copy(
            batteryLevelPercent = batteryPct,
            batteryTemperatureC = batteryTempC,
            ramTotalGb = totalRamGb,
            ramUsedGb = usedRamGb,
            cpuCores = cpuCores,
            cpuUsagePercent = estimatedCpuLoad
        )
    }
}
