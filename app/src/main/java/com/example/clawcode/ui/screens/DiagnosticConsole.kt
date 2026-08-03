package com.example.clawcode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.ConsoleLog
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel
import kotlinx.coroutines.launch

@Composable
fun DiagnosticConsole(
    viewModel: ClawViewModel,
    modifier: Modifier = Modifier
) {
    val consoleLogs by viewModel.consoleLogs.collectAsState()
    val hardware by viewModel.hardwareTelemetry.collectAsState()
    val modfile by viewModel.jarvisModfile.collectAsState()
    val ollamaConfig by viewModel.ollamaConfig.collectAsState()

    var selectedCategory by remember { mutableStateOf("TODOS") }
    var commandInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val filteredLogs = remember(consoleLogs, selectedCategory) {
        when (selectedCategory) {
            "JARVIS VOZ" -> consoleLogs.filter { it.message.contains("VOICE", ignoreCase = true) || it.message.contains("STT", ignoreCase = true) || it.message.contains("TTS", ignoreCase = true) }
            "INFERENCIA AI" -> consoleLogs.filter { it.message.contains("INFERENCE", ignoreCase = true) || it.message.contains("MODEL", ignoreCase = true) || it.message.contains("PROMPT", ignoreCase = true) }
            "ACCIONES" -> consoleLogs.filter { it.message.contains("INTENT", ignoreCase = true) || it.message.contains("ACTION", ignoreCase = true) || it.sender.contains("MobileAction", ignoreCase = true) }
            "SISTEMA" -> consoleLogs.filter { it.sender.contains("System", ignoreCase = true) || it.message.contains("RAM", ignoreCase = true) || it.message.contains("CPU", ignoreCase = true) }
            else -> consoleLogs
        }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Live Model & System Telemetry Diagnostic Header Card
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(NeonEmerald, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DIAGNOSTIC CONSOLE & INFERENCE TRACER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "Modelo: ${modfile.baseModel}",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DiagnosticChip(label = "CPU", value = "${hardware.cpuUsagePercent}%")
                    DiagnosticChip(label = "RAM", value = "${String.format("%.1f", hardware.ramUsedGb)} / ${String.format("%.1f", hardware.ramTotalGb)} GB")
                    DiagnosticChip(label = "Velocidad", value = "${hardware.tokensPerSecond} t/s")
                    DiagnosticChip(label = "Batería", value = "${hardware.batteryLevelPercent}% (${hardware.batteryTemperatureC}°C)")
                }
            }
        }

        // Diagnostic Category Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("TODOS", "JARVIS VOZ", "INFERENCIA AI", "ACCIONES", "SISTEMA").forEach { category ->
                val isSelected = selectedCategory == category
                Surface(
                    color = if (isSelected) NeonEmerald.copy(alpha = 0.2f) else DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NeonEmerald else DarkBorder),
                    onClick = { selectedCategory = category },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = category,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isSelected) NeonEmerald else TextMuted,
                        modifier = Modifier.padding(vertical = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        // Real-Time Diagnostic Log Stream
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No hay registros en la categoría '$selectedCategory'. Envia un comando o habla con JARVIS.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLogs) { log ->
                        DiagnosticLogItemRow(log = log)
                    }
                }
            }
        }

        // Command Input & Trigger Test
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = { Text("Comando de prueba / diagnostico...", fontSize = 11.sp, color = TextMuted) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonEmerald,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )

            IconButton(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        val cmd = commandInput
                        commandInput = ""
                        viewModel.sendConsolePrompt(cmd)
                    }
                },
                modifier = Modifier
                    .size(46.dp)
                    .background(NeonEmerald, CircleShape)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Enviar", tint = DarkBackground)
            }

            IconButton(
                onClick = { viewModel.clearConsoleLogs() },
                modifier = Modifier
                    .size(46.dp)
                    .background(DarkSurface, CircleShape)
                    .border(1.dp, DarkBorder, CircleShape)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Limpiar Logs", tint = RedError)
            }
        }
    }
}

@Composable
fun DiagnosticChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = 8.sp, color = TextMuted)
    }
}

@Composable
fun DiagnosticLogItemRow(log: ConsoleLog) {
    val isError = log.message.contains("ERROR", ignoreCase = true) || log.message.contains("FAIL", ignoreCase = true)
    val isVoice = log.message.contains("VOICE", ignoreCase = true) || log.message.contains("STT", ignoreCase = true) || log.message.contains("TTS", ignoreCase = true)
    val isAction = log.message.contains("INTENT", ignoreCase = true) || log.message.contains("ACTION", ignoreCase = true)

    val tagColor = when {
        isError -> RedError
        isVoice -> NeonSecondary
        isAction -> WarningAmber
        else -> NeonEmerald
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = log.timestamp,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMuted,
            modifier = Modifier.width(60.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .background(tagColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .border(1.dp, tagColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = log.sender.uppercase(),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = tagColor,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = log.message,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isError) RedError else TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
