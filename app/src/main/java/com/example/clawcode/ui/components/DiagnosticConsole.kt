package com.example.clawcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.ConsoleLog
import com.example.clawcode.ui.theme.*

@Composable
fun DiagnosticConsole(
    logs: List<ConsoleLog>,
    onClearLogs: () -> Unit,
    onInjectSimulatedLog: ((sender: String, message: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    selectedAvatarId: Int = 1
) {
    var filterText by remember { mutableStateOf("") }
    var isSimulationModeActive by remember { mutableStateOf(false) }

    val drawerAvatarIcon = remember(selectedAvatarId) {
        when (selectedAvatarId) {
            1 -> Icons.Default.SmartToy
            2 -> Icons.Default.Psychology
            3 -> Icons.Default.SupportAgent
            4 -> Icons.Default.Memory
            5 -> Icons.Default.Face
            6 -> Icons.Default.AutoAwesome
            else -> Icons.Default.Terminal
        }
    }

    // Simulation Mode coroutine stream
    LaunchedEffect(isSimulationModeActive) {
        if (isSimulationModeActive && onInjectSimulatedLog != null) {
            // Su único fin es ejercitar el stream cuando el usuario activa el chip; por eso
            // ningún texto afirma una medición del dispositivo ni una ruta de inferencia.
            val sampleEvents = listOf(
                Pair("SIMULATED_QWEN_GGUF", "[SIMULATION][NO MEDIDO] Turno sintético de prueba: no se ejecutó ninguna inferencia ni se verificó ninguna ruta NPU; los t/s sólo los informa la última inferencia real."),
                Pair("SIMULATED_OCR_CAMERA", "[SIMULATION][NO MEDIDO] Evento sintético de OCR: no llegó ninguna imagen y no hay texto reconocido que informar."),
                Pair("SIMULATED_MEMORY_CLEAN", "[SIMULATION][NO MEDIDO] Evento sintético de caché: no se compactó ninguna caché ni se liberó RAM medible."),
                Pair("SIMULATED_OPENCLAW_WEBHOOK", "[SIMULATION][NO MEDIDO] Evento sintético de webhook: no se abrió ninguna conexión y ningún payload llegó al teléfono."),
                Pair("SIMULATED_SYSTEM_MONITOR", "[SIMULATION][NO MEDIDO] Evento sintético de telemetría: CPU, batería y RAM no fueron leídos del dispositivo en este evento.")
            )
            var index = 0
            while (isSimulationModeActive) {
                kotlinx.coroutines.delay(3500)
                val event = sampleEvents[index % sampleEvents.size]
                onInjectSimulatedLog(event.first, event.second)
                index++
            }
        }
    }

    val filteredLogs = if (filterText.isNotBlank()) {
        logs.filter {
            it.message.contains(filterText, ignoreCase = true) ||
            it.sender.contains(filterText, ignoreCase = true)
        }
    } else {
        logs
    }

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = drawerAvatarIcon,
                        contentDescription = "Console",
                        tint = NeonPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DIAGNOSTIC STREAM",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Simulation Mode FilterChip
                    FilterChip(
                        selected = isSimulationModeActive,
                        onClick = { isSimulationModeActive = !isSimulationModeActive },
                        label = {
                            Text(
                                if (isSimulationModeActive) "🧪 Simulación ON" else "Simulación OFF",
                                fontSize = 10.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WarningAmber.copy(alpha = 0.25f),
                            selectedLabelColor = WarningAmber
                        ),
                        modifier = Modifier.height(28.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = onClearLogs, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpiar Logs",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Log Feed Window
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(DarkBackground, RoundedCornerShape(8.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (filteredLogs.isEmpty()) {
                    Text(
                        text = "> Esperando eventos de inferencia y telemetría...",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredLogs) { log ->
                            val isError = log.sender.contains("Error", ignoreCase = true) || log.message.contains("error", ignoreCase = true)
                            val textColor = if (isError) RedError else if (log.sender == "User") NeonPrimary else TextPrimary
                            
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "[${log.timestamp}] ",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${log.sender}: ",
                                    color = if (isError) RedError else NeonSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = log.message,
                                    color = textColor,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
