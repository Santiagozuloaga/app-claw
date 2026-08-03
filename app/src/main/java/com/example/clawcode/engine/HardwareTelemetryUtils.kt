package com.example.clawcode.engine

import android.content.Context
import com.example.clawcode.core.telemetry.SystemMonitor
import com.example.clawcode.data.model.HardwareTelemetry

object HardwareTelemetryUtils {
    fun getRealTelemetry(context: Context, previousTelemetry: HardwareTelemetry): HardwareTelemetry {
        val monitor = SystemMonitor(context)
        return monitor.captureMetrics(previousTelemetry)
    }
}
