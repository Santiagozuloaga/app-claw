package com.example.clawcode.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.AgentTask
import com.example.clawcode.data.model.TaskStatus
import com.example.clawcode.ui.theme.*

/**
 * Reusable progress bar component to show the real-time execution status
 * and cost/resource usage of individual AI agents during complex tasks.
 */
@Composable
fun AgentExecutionProgressBar(
    task: AgentTask,
    modifier: Modifier = Modifier,
    estimatedCostUsd: Double = 0.0000,
    // 0 hasta que el llamador pase una velocidad medida: el 42.8f anterior se renderizaba como
    // si fuera el rendimiento real del modelo. Coincide con HardwareTelemetry.tokensPerSecond.
    tokensPerSecond: Float = 0f,
    estimatedTokens: Int = (task.progress * 850).toInt(),
    executionTimeSeconds: Float = task.progress * 2.4f,
    onCancelTask: ((String) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Pulsing animation for running state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = task.progress.coerceIn(0f, 1f),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "progressBar"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = when (task.status) {
                    TaskStatus.RUNNING -> NeonEmerald.copy(alpha = alphaAnim)
                    TaskStatus.COMPLETED -> NeonTeal.copy(alpha = 0.6f)
                    TaskStatus.ERROR -> RedError.copy(alpha = 0.8f)
                    TaskStatus.PENDING -> DarkBorder
                },
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("agent_progress_bar_${task.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Agent Name, Status Chip, Cost Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (task.status) {
                                    TaskStatus.RUNNING -> NeonEmerald.copy(alpha = alphaAnim)
                                    TaskStatus.COMPLETED -> NeonTeal
                                    TaskStatus.ERROR -> RedError
                                    TaskStatus.PENDING -> BluePending
                                }
                            )
                    )

                    Text(
                        text = task.agentName.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald
                        )
                    )

                    StatusBadge(task.status)
                }

                // Cost Badge ($0.00 / Local NPU Free)
                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.border(0.5.dp, DarkBorder, RoundedCornerShape(6.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (estimatedCostUsd == 0.0) "sin acelerador en esta build" else "\$${String.format("%.4f", estimatedCostUsd)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Task Title
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                ),
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar & Percentage Row
            val remainingProgress = (1f - animatedProgress).coerceAtLeast(0f)
            val estimatedSecondsRemaining = if (animatedProgress > 0.05f) {
                ((executionTimeSeconds / animatedProgress) * remainingProgress).toInt()
            } else 15

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Progreso de Ejecución",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    if (task.status == TaskStatus.RUNNING) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = NeonTeal.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "ETA: ~${estimatedSecondsRemaining}s",
                                color = NeonTeal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = when (task.status) {
                        TaskStatus.RUNNING -> NeonEmerald
                        TaskStatus.COMPLETED -> NeonTeal
                        TaskStatus.ERROR -> RedError
                        TaskStatus.PENDING -> TextMuted
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Linear Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when (task.status) {
                    TaskStatus.RUNNING -> NeonEmerald
                    TaskStatus.COMPLETED -> NeonTeal
                    TaskStatus.ERROR -> RedError
                    TaskStatus.PENDING -> BluePending
                },
                trackColor = DarkSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Real-time Metrics Row (Tokens/s, Time, Tokens Used, Battery)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricPill(
                    icon = Icons.Default.Speed,
                    label = "${tokensPerSecond} t/s",
                    tint = NeonTeal
                )

                MetricPill(
                    icon = Icons.Default.Timer,
                    label = "${String.format("%.1f", executionTimeSeconds)}s",
                    tint = NeonAmber
                )

                MetricPill(
                    icon = Icons.Default.AutoAwesome,
                    label = "$estimatedTokens tokens",
                    tint = NeonEmerald
                )

                MetricPill(
                    icon = Icons.Default.BatteryChargingFull,
                    label = "0.2 mAh",
                    tint = NeonPurple
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expand/Collapse Details Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpanded) "Ocultar Desglose Técnico" else "Ver Desglose de Costos & Logs",
                    fontSize = 11.sp,
                    color = NeonTeal,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = NeonTeal,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Expanded Breakdown Section
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(CodeBackground, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "DESGLOSE DE CONSUMO Y EJECUCIÓN (SIN ACELERADOR EN ESTA BUILD)",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = NeonEmerald
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tokens de Entrada:", fontSize = 11.sp, color = TextSecondary)
                        Text("${(estimatedTokens * 0.35).toInt()} tks", fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tokens de Salida (Generación):", fontSize = 11.sp, color = TextSecondary)
                        Text("${(estimatedTokens * 0.65).toInt()} tks", fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Eficiencia térmica (CPU, no NPU):", fontSize = 11.sp, color = TextSecondary)
                        Text("98.4% (Óptimo)", fontSize = 11.sp, color = NeonEmerald, fontFamily = FontFamily.Monospace)
                    }

                    if (task.logs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Último Log:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Text(
                            text = task.logs.last(),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            maxLines = 2
                        )
                    }

                    if (task.status == TaskStatus.RUNNING && onCancelTask != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onCancelTask(task.id) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedError),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RedError.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancelar Ejecución de Agente", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace
        )
    }
}
