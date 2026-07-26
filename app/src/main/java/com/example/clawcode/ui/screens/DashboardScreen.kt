package com.example.clawcode.ui.screens

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.AgentTask
import com.example.clawcode.data.model.AgentStats
import com.example.clawcode.data.model.HardwareTelemetry
import com.example.clawcode.ui.components.MetricCard
import com.example.clawcode.ui.components.TaskItemCard
import com.example.clawcode.ui.theme.*

@Composable
fun DashboardScreen(
    stats: AgentStats,
    hardware: HardwareTelemetry = HardwareTelemetry(),
    tasks: List<AgentTask>,
    onRunTask: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onSelectTask: (AgentTask) -> Unit,
    onNavigateTerminal: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // System Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(NeonEmerald)
                            )
                            Text(
                                text = "CLAW ASSISTANT • EN LÍNEA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.5.sp,
                                    color = NeonEmerald
                                )
                            )
                        }

                        Surface(
                            color = NeonEmerald.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "100% LOCAL NPU",
                                color = NeonEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "¡Hola, Santiago!",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Tu asistente personal CLAW Standard está preparado y ejecutándose de forma privada en el chip del teléfono.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Executive Summary Cards / Bullets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).border(0.5.dp, DarkBorder, RoundedCornerShape(8.dp))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("• Estado", fontSize = 10.sp, color = TextMuted)
                                Text("Activo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                            }
                        }

                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).border(0.5.dp, DarkBorder, RoundedCornerShape(8.dp))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("• Tareas Colas", fontSize = 10.sp, color = TextMuted)
                                Text("${tasks.size} Tareas", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonTeal)
                            }
                        }

                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).border(0.5.dp, DarkBorder, RoundedCornerShape(8.dp))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("• Modelo", fontSize = 10.sp, color = TextMuted)
                                Text("CLAW Standard", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonAmber)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onNavigateTerminal,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald,
                            contentColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_launch_terminal")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Abrir Consola de Comandos CLI",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Hardware Telemetry Section
        item {
            Text(
                text = "Telemetría de Hardware & Recarga de Tokens",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Row 1: CPU & GPU/NPU
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricCard(
                        title = "Uso CPU / Cores",
                        value = "${hardware.cpuUsagePercent}%",
                        subtitle = "${hardware.cpuCores} Cores Activos",
                        icon = Icons.Default.Memory,
                        accentColor = NeonEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Acelerador GPU/NPU",
                        value = "Acelerado",
                        subtitle = hardware.npuGpuAccelerator,
                        icon = Icons.Default.Speed,
                        accentColor = NeonTeal,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: RAM & Batería
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricCard(
                        title = "Memoria RAM",
                        value = "${hardware.ramUsedGb} GB",
                        subtitle = "de ${hardware.ramTotalGb} GB (${(hardware.ramUsedGb / hardware.ramTotalGb * 100).toInt()}% libre)",
                        icon = Icons.Default.SdCard,
                        accentColor = NeonAmber,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Batería del Teléfono",
                        value = "${hardware.batteryLevelPercent}%",
                        subtitle = "Temp: ${hardware.batteryTemperatureC}°C",
                        icon = Icons.Default.BatteryChargingFull,
                        accentColor = NeonPurple,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 3: Tokens Speed & Context Window
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricCard(
                        title = "Tasa Inferencia Tokens",
                        value = "${hardware.tokensPerSecond} t/s",
                        subtitle = "Qwen 3.5 4-bit LiteRT",
                        icon = Icons.Default.AutoAwesome,
                        accentColor = NeonEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Ventana de Contexto",
                        value = "${hardware.totalContextTokens}",
                        subtitle = "Tokens Máximos",
                        icon = Icons.Default.DataArray,
                        accentColor = NeonPink,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Sub-agents explanation banner & Historical Aggregated Analytics
        item {
            val context = LocalContext.current
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonEmerald.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Histórico & Rendimiento Acumulado",
                            color = NeonEmerald,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Export Report Button
                        OutlinedButton(
                            onClick = {
                                val reportText = """
                                    === REPORTE DE EJECUCIÓN AGENTE JARVIS (Qwen 3.5) ===
                                    Fecha: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}
                                    Dispositivo: 100% Local NPU / LiteRT Vulkan
                                    Tasa Inferencia: ${hardware.tokensPerSecond} t/s
                                    Tasa de Éxito en Tareas: 96.4%
                                    Tiempo Total de Cómputo: 14h 28m
                                    Costo Total NPU: $0.00 USD (On-device)
                                    RAM Utilizada: ${hardware.ramUsedGb} GB / ${hardware.ramTotalGb} GB
                                    Batería Nivel: ${hardware.batteryLevelPercent}%
                                    ===================================================
                                """.trimIndent()

                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, reportText)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Compartir Reporte de Agente"))
                                Toast.makeText(context, "Reporte generado y listo para exportar", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonEmerald),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Exportar Reporte", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Tasa de Éxito Tareas", fontSize = 10.sp, color = TextMuted)
                            Text("96.4%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                        }
                        Column {
                            Text("Cómputo Acumulado", fontSize = 10.sp, color = TextMuted)
                            Text("14h 28m", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonTeal)
                        }
                        Column {
                            Text("Tokens Procesados", fontSize = 10.sp, color = TextMuted)
                            Text("1.82M", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonAmber)
                        }
                        Column {
                            Text("Costo Cloud Ahorrado", fontSize = 10.sp, color = TextMuted)
                            Text("\$36.40", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonPurple)
                        }
                    }
                }
            }
        }

        // Active Tasks Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tareas Autónomas Activas",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "${tasks.size} Procesos",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextMuted)
                )
            }
        }

        // Tasks List
        items(tasks.take(4)) { task ->
            TaskItemCard(
                task = task,
                onRunTask = onRunTask,
                onCompleteTask = onCompleteTask,
                onSelectTask = onSelectTask
            )
        }
    }
}

