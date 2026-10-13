package com.example.clawcode.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.*
import com.example.clawcode.ui.theme.*

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        letterSpacing = 1.2.sp
                    )
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = accentColor)
            )
        }
    }
}

@Composable
fun StatusBadge(status: TaskStatus) {
    val (bgColor, textColor, label) = when (status) {
        TaskStatus.RUNNING -> Triple(GreenRunning.copy(alpha = 0.2f), GreenRunning, "RUNNING")
        TaskStatus.COMPLETED -> Triple(NeonTeal.copy(alpha = 0.2f), NeonTeal, "COMPLETED")
        TaskStatus.PENDING -> Triple(BluePending.copy(alpha = 0.2f), BluePending, "PENDING")
        TaskStatus.ERROR -> Triple(RedError.copy(alpha = 0.2f), RedError, "ERROR")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun PriorityBadge(priority: Priority) {
    val (color, label) = when (priority) {
        Priority.LOW -> Pair(TextSecondary, "LOW")
        Priority.MEDIUM -> Pair(NeonAmber, "MED")
        Priority.HIGH -> Pair(NeonTeal, "HIGH")
        Priority.CRITICAL -> Pair(NeonPink, "CRITICAL")
    }

    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun TaskItemCard(
    task: AgentTask,
    onRunTask: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onSelectTask: (AgentTask) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .clickable { onSelectTask(task) }
            .testTag("task_item_${task.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(task.status)
                    PriorityBadge(task.priority)
                }
                Text(
                    text = task.createdAt,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = task.description,
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subagent Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Agent: ${task.agentName}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = NeonTeal
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (task.status == TaskStatus.PENDING) {
                        IconButton(
                            onClick = { onRunTask(task.id) },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("run_task_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run Task",
                                tint = NeonEmerald
                            )
                        }
                    } else if (task.status == TaskStatus.RUNNING) {
                        IconButton(
                            onClick = { onCompleteTask(task.id) },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("complete_task_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Mark Complete",
                                tint = NeonTeal
                            )
                        }
                    }
                }
            }

            if (task.status == TaskStatus.RUNNING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = NeonEmerald,
                    trackColor = DarkSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MemoryItemCard(item: MemoryItem) {
    val palette = LocalDynamicThemePalette.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, palette.border, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.key,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = palette.primary
                    )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.secondary.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.category.uppercase(),
                        fontSize = 10.sp,
                        color = palette.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.value,
                style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${item.tokenCount} tokens",
                    fontSize = 11.sp,
                    color = if (item.isCompacted) NeonTeal else TextMuted
                )
                Text(
                    text = if (item.isCompacted) "COMPACTED (${item.updatedAt})" else "RAW (${item.updatedAt})",
                    fontSize = 11.sp,
                    color = if (item.isCompacted) NeonEmerald else NeonAmber
                )
            }
        }
    }
}

@Composable
fun ToolItemCard(
    tool: McpTool,
    onToggle: (String) -> Unit,
    onVerify: ((McpTool) -> Unit)? = null
) {
    val borderAnim by animateColorAsState(
        if (tool.isEnabled) NeonTeal.copy(alpha = 0.6f) else DarkBorder,
        label = "toolBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderAnim, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tool.category,
                            fontSize = 10.sp,
                            color = NeonTeal
                        )
                    }
                    if (tool.isEnabled) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeonAmber.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .border(1.dp, NeonAmber.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(NeonAmber)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                // `tool.isEnabled` es sólo la preferencia del interruptor: ClawEngine.toggleTool
                                // hace `copy(isEnabled = !isEnabled)` y no abre ninguna conexión. Aquí no hay
                                // sonda de red, así que ya no se afirma "CONECTADO / ONLINE".
                                Text(
                                    text = "ACTIVADA / SIN VERIFICAR CONEXIÓN",
                                    fontSize = 9.sp,
                                    color = NeonAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(6.dp))
                // `callCount` y `latencyMs` de McpTool sólo se escriben en los seeds de ClawEngine
                // (189, 312, 94, 42, 128, 210, 345, 178, 560 / 4..18 ms) y nada los incrementa jamás,
                // así que mostrarlos daría a unas cifras inventadas el aspecto de una medida real.
                Text(
                    text = "Calls: sin datos | Ping: sin medir",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                )
                
                if (tool.isEnabled && onVerify != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { onVerify(tool) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp).border(1.dp, NeonEmerald.copy(alpha=0.5f), RoundedCornerShape(4.dp))
                    ) {
                        // El diálogo solo puede consultar paquetes instalados y placeholders: si no
                        // hay nada que medir, responderá "Sin verificar", así que el botón no promete éxito.
                        Text("VERIFICAR ESTADO (SI ES MEDIBLE)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                    }
                }
            }

            Switch(
                checked = tool.isEnabled,
                onCheckedChange = { onToggle(tool.id) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = NeonTeal,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkSurfaceVariant
                ),
                modifier = Modifier.testTag("switch_tool_${tool.id}")
            )
        }
    }
}
