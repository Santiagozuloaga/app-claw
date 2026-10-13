package com.example.clawcode.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.ClawApplication
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel

@Composable
fun SystemPerformanceReportScreen(
    viewModel: ClawViewModel? = null,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    val hardware = viewModel?.hardwareTelemetry?.collectAsState()?.value
        ?: com.example.clawcode.data.model.HardwareTelemetry()

    var crashLogs by remember { mutableStateOf(ClawApplication.getCrashLogs(context)) }
    var stressTestResult by remember { mutableStateOf<String?>(null) }

    // Memory status calculation
    val ramUsed = hardware.ramUsedGb
    val ramTotal = hardware.ramTotalGb
    // ramTotal == 0 es la marca "sin medir" del sondeo: antes se rellenaba con un 65 % inventado.
    val measuredRamPercent: Int? = if (ramTotal > 0f) (ramUsed / ramTotal * 100).toInt() else null
    val isHighMemoryPressure = measuredRamPercent != null && measuredRamPercent > 80
    val ramSummary = if (measuredRamPercent != null) {
        "%.2f GB / %.2f GB (%d%%)".format(ramUsed, ramTotal, measuredRamPercent)
    } else {
        "sin medir (el sondeo de ActivityManager no ha devuelto valores)"
    }

    // Thermal status calculation
    val batteryTemp = hardware.batteryTemperatureC
    val isThermalWarning = batteryTemp > 38.0f
    val temperaturaTexto = if (batteryTemp >= 0f) {
        "%.1f°C • %d Cores".format(batteryTemp, hardware.cpuCores)
    } else {
        "sin medir • %d Cores".format(hardware.cpuCores)
    }

    // El 0 de cpuUsagePercent y el -1 de batería también son centinelas de "sin medir".
    val sinLecturas = measuredRamPercent == null && batteryTemp < 0f && hardware.cpuUsagePercent == 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = NeonPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "DIAGNÓSTICO DE RENDIMIENTO",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Monitoreo de Hardware, Memoria RAM y Registro de Cierres",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                        )
                    }
                }

                // Health Badge
                val badgeBg = when {
                    isHighMemoryPressure || isThermalWarning -> WarningAmber
                    sinLecturas -> TextMuted
                    else -> NeonEmerald
                }
                val badgeText = when {
                    isHighMemoryPressure || isThermalWarning -> "PRESIÓN ALTA"
                    // Sin lecturas no se puede emitir un veredicto de "SISTEMA ÓPTIMO".
                    sinLecturas -> "SIN MEDIR"
                    else -> "DENTRO DE LO MEDIDO"
                }

                Surface(
                    color = badgeBg.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeBg)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(badgeBg)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            color = badgeBg,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // RAM Memory Health Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "RAM",
                                tint = NeonTeal,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ESTADO DE MEMORIA RAM",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = ramSummary,
                            color = if (isHighMemoryPressure) WarningAmber else NeonEmerald,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { ((measuredRamPercent ?: 0) / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (isHighMemoryPressure) WarningAmber else NeonEmerald,
                        trackColor = DarkBackground
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = when {
                            isHighMemoryPressure ->
                                "⚠️ Presión de memoria según el sondeo: la app ocupa una fracción alta de la RAM y Android puede cerrar el proceso en segundo plano. Nota: 'Limpiar Caché' en Ajustes borra archivos de disco, no libera RAM."
                            measuredRamPercent == null ->
                                "SIN MEDIR: el sondeo de ActivityManager todavía no ha devuelto RAM usada ni total en esta sesión, así que no hay margen que evaluar."
                            else ->
                                "Lectura del sondeo dentro del rango habitual. Ningún modelo corre dentro de esta app: la inferencia viaja por HTTP al runtime configurado, así que aquí no hay ningún peso local que necesitaría ese espacio."
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Thermal & CPU Health Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Thermostat,
                                contentDescription = "Thermal",
                                tint = if (isThermalWarning) WarningAmber else NeonSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TEMPERATURA Y CPU",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = temperaturaTexto,
                            color = if (isThermalWarning) WarningAmber else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricMiniBox(
                            label = "Batería",
                            // batteryLevelPercent = -1 es el centinela de "sin medir" del modelo.
                            value = if (hardware.batteryLevelPercent >= 0) "${hardware.batteryLevelPercent}%" else "sin medir",
                            icon = Icons.Default.BatteryChargingFull,
                            color = NeonEmerald
                        )
                        MetricMiniBox(
                            label = "Carga CPU",
                            // cpuUsagePercent = 0 significa que aún no hay dos lecturas de /proc/stat.
                            value = if (hardware.cpuUsagePercent > 0) "${hardware.cpuUsagePercent}%" else "sin medir",
                            icon = Icons.Default.Speed,
                            color = NeonTeal
                        )
                        MetricMiniBox(
                            label = "Acelerador",
                            value = hardware.npuGpuAccelerator,
                            icon = Icons.Default.AutoAwesome,
                            color = NeonPurple
                        )
                    }
                }
            }
        }

        // Telemetría real de la última inferencia (antes: stress test con números inventados)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Telemetría medida",
                                tint = WarningAmber,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MÉTRICAS REALES DEL ÚLTIMO TURNO",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Button(
                            onClick = {
                                // Al pulsar no se mide nada: se vuelcan las lecturas reales que la sesión
                                // ya tenga en HardwareTelemetry. Sin ningún turno enviado no hay latencia,
                                // throughput ni pico de RAM, así que se dice en lugar de inventarlos.
                                val measuredTps = hardware.tokensPerSecond
                                stressTestResult = if (measuredTps > 0f) {
                                    "Vuelco de telemetría medida:\n" +
                                            " • Throughput del último turno: ${"%.2f".format(measuredTps)} t/s (lo publica el runtime HTTP; ningún modelo corre en el teléfono)\n" +
                                            " • Latencia del turno: sin medir (HardwareTelemetry no guarda latencia; sólo la consola del cliente Ollama la imprime)\n" +
                                            " • RAM usada: ${if (ramUsed > 0f) "%.2f GB".format(ramUsed) else "sin medir"}\n" +
                                            " • Acelerador: ${hardware.npuGpuAccelerator}\n" +
                                            " • No se ha ejecutado ninguna prueba de estrés: no hay rango operativo que calificar."
                                } else {
                                    "Nada medido: en esta sesión no se ha enviado ningún turno al runtime, así que no hay throughput, latencia ni consumo de RAM que mostrar."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Volcar telemetría medida", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Esta pantalla no mide nada por sí misma: muestra las lecturas reales que la app haya acumulado. El throughput lo publica el runtime HTTP en la última respuesta. No existe ningún puente GGUF/LiteRT en esta build, así que no hay latencia local que medir.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    stressTestResult?.let { result ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = result,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Global Crash Logs Viewer Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Crash Logs",
                                tint = RedError,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "REGISTRO DE EXCEPCIONES Y CIERRES",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                ClawApplication.clearCrashLogs(context)
                                crashLogs = ClawApplication.getCrashLogs(context)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Borrar Logs",
                                tint = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 220.dp)
                            .background(DarkBackground, RoundedCornerShape(8.dp))
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = crashLogs,
                            color = if (crashLogs.contains("EXCEPCION") || crashLogs.contains("Exception")) RedError else TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricMiniBox(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Surface(
        color = DarkBackground,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text(text = label, color = TextMuted, fontSize = 10.sp)
        }
    }
}
