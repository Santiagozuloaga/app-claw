package com.example.clawcode.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.ConsoleLog
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedConsoleScreen(viewModel: ClawViewModel) {
    var showPerformanceReport by remember { mutableStateOf(false) }

    if (showPerformanceReport) {
        SystemPerformanceReportScreen(
            viewModel = viewModel,
            onNavigateBack = { showPerformanceReport = false }
        )
        return
    }

    val hardware by viewModel.hardwareTelemetry.collectAsState()
    val consoleLogs by viewModel.consoleLogs.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("TODOS") } // TODOS, FALLOS, ACCIONES, SISTEMA
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                viewModel.sendConsolePrompt(spokenText)
            }
        }
    }

    fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Comando de Diagnóstico CLAW...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            viewModel.sendConsolePrompt("[ERROR] Reconocimiento de voz no disponible.")
        }
    }

    val errorLogsCount = remember(consoleLogs) {
        consoleLogs.count { log ->
            val msg = log.message.uppercase()
            msg.contains("ERROR") || msg.contains("FAIL") || msg.contains("FALLO") || msg.contains("WARN") || msg.contains("EXCEPTION")
        }
    }

    val filteredLogs = remember(consoleLogs, selectedFilter) {
        when (selectedFilter) {
            "FALLOS" -> consoleLogs.filter { log ->
                val msg = log.message.uppercase()
                msg.contains("ERROR") || msg.contains("FAIL") || msg.contains("FALLO") || msg.contains("WARN") || msg.contains("EXCEPTION")
            }
            "ACCIONES" -> consoleLogs.filter { log ->
                log.sender.contains("Action", ignoreCase = true) || log.message.contains("Intent", ignoreCase = true) || log.message.contains("Hook", ignoreCase = true)
            }
            "SISTEMA" -> consoleLogs.filter { log ->
                log.sender.contains("System", ignoreCase = true) || log.sender.contains("Claw", ignoreCase = true)
            }
            else -> consoleLogs
        }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CONSOLA DE DIAGNÓSTICO",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    color = if (errorLogsCount > 0) RedError.copy(alpha = 0.2f) else NeonEmerald.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (errorLogsCount > 0) RedError else NeonEmerald)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (errorLogsCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (errorLogsCount > 0) RedError else NeonEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (errorLogsCount > 0) "$errorLogsCount FALLO(S)" else "0 FALLOS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (errorLogsCount > 0) RedError else NeonEmerald
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hardware Telemetry Quick Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryMiniChip("BATERÍA", "${hardware.batteryLevelPercent}%", if (hardware.batteryLevelPercent > 20) NeonEmerald else RedError, Modifier.weight(1f))
                TelemetryMiniChip("RAM", "${"%.1f".format(hardware.ramUsedGb)} GB", NeonAmber, Modifier.weight(1f))
                TelemetryMiniChip("NPU / GPU", "100% On-Device", NeonTeal, Modifier.weight(1.2f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Command Presets Strip
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "COMANDOS RÁPIDOS",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        CommandPresetChip(
                            label = "📊 Reporte Rendimiento",
                            color = NeonEmerald,
                            onClick = { showPerformanceReport = true }
                        )
                    }
                    item {
                        CommandPresetChip(
                            label = "/sys status",
                            color = NeonTeal,
                            onClick = { viewModel.sendConsolePrompt("/sys status") }
                        )
                    }
                    item {
                        CommandPresetChip(
                            label = "/intent open gmail",
                            color = NeonEmerald,
                            onClick = { viewModel.sendConsolePrompt("/intent open gmail") }
                        )
                    }
                    item {
                        CommandPresetChip(
                            label = "/intent flash on",
                            color = NeonAmber,
                            onClick = { viewModel.sendConsolePrompt("/intent flash on") }
                        )
                    }
                    item {
                        CommandPresetChip(
                            label = "/agent model",
                            color = NeonPurple,
                            onClick = { viewModel.sendConsolePrompt("/agent model") }
                        )
                    }
                    item {
                        CommandPresetChip(
                            label = "/bridge test",
                            color = NeonEmerald,
                            onClick = { viewModel.sendConsolePrompt("/bridge test") }
                        )
                    }
                    item {
                        CommandPresetChip(
                            label = "/diag error",
                            color = RedError,
                            onClick = { viewModel.sendConsolePrompt("/diag error") }
                        )
                    }
                    item {
                        CommandPresetChip(
                            label = "/sys memory",
                            color = TextMuted,
                            onClick = { viewModel.sendConsolePrompt("/sys memory") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("TODOS", "SISTEMA", "ACCIONES", "FALLOS").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val chipColor = when {
                        filter == "FALLOS" && errorLogsCount > 0 -> RedError
                        isSelected -> NeonEmerald
                        else -> TextMuted
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = if (filter == "FALLOS") "FALLOS ($errorLogsCount)" else filter,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) DarkBackground else chipColor
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipColor,
                            containerColor = DarkSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) chipColor else DarkBorder,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }
        }

        HorizontalDivider(color = DarkBorder)

        // Log Viewer Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedFilter == "FALLOS") "No se detectaron errores ni fallos de ejecución." else "Consola vacía. Listo para recibir comandos.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredLogs) { log ->
                        DiagnosticLogCard(log = log)
                    }
                }
            }
        }

        // Auto-complete suggestion popup when typing '/'
        if (inputText.startsWith("/")) {
            val suggestions = listOf(
                "/sys status" to "Ver telemetría y estado de hardware",
                "/sys memory" to "Ver estado del buffer de memoria",
                "/intent open gmail" to "Lanzar app de correo Gmail",
                "/intent flash on" to "Encender linterna de cámara",
                "/agent model" to "Inspeccionar modelo LiteRT Qwen 3.5",
                "/agent tasks" to "Ver cola de tareas del agente",
                "/bridge test" to "Simular webhook entrante de WhatsApp",
                "/diag error" to "Simular fallo de Intent (Probador)",
                "/sys clear" to "Limpiar logs de consola"
            ).filter { it.first.contains(inputText, ignoreCase = true) }

            if (suggestions.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f)),
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("SUGERENCIAS DE COMANDOS", color = NeonEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        suggestions.take(4).forEach { (cmd, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        inputText = cmd
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cmd, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text(desc, color = TextMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Diagnostic Input Prompt Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(DarkSurface, RoundedCornerShape(24.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { startVoiceRecognition() }) {
                Icon(Icons.Default.Mic, contentDescription = "Voz", tint = NeonEmerald)
            }
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe / para ver lista de comandos...", color = TextMuted, fontSize = 13.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val text = inputText
                        inputText = ""
                        viewModel.sendConsolePrompt(text)
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(NeonEmerald.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = NeonEmerald)
            }
        }
    }
}

@Composable
fun CommandPresetChip(label: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.6f))
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun TelemetryMiniChip(title: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(DarkSurface, RoundedCornerShape(8.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(title, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DiagnosticLogCard(log: ConsoleLog) {
    val msgUpper = log.message.uppercase()
    val isError = msgUpper.contains("ERROR") || msgUpper.contains("FAIL") || msgUpper.contains("FALLO") || msgUpper.contains("EXCEPTION")
    val isWarning = msgUpper.contains("WARN") || msgUpper.contains("TIMEOUT") || msgUpper.contains("FALLBACK")
    val isAction = log.sender.contains("Action", ignoreCase = true) || msgUpper.contains("INTENT") || msgUpper.contains("HOOK")

    val containerBg = when {
        isError -> RedError.copy(alpha = 0.12f)
        isWarning -> NeonAmber.copy(alpha = 0.12f)
        isAction -> NeonEmerald.copy(alpha = 0.08f)
        else -> DarkSurface
    }

    val borderColor = when {
        isError -> RedError
        isWarning -> NeonAmber
        isAction -> NeonEmerald.copy(alpha = 0.5f)
        else -> DarkBorder
    }

    val tagColor = when {
        isError -> RedError
        isWarning -> NeonAmber
        isAction -> NeonEmerald
        log.sender == "User" -> NeonTeal
        else -> NeonPurple
    }

    val icon = when {
        isError -> Icons.Default.Error
        isWarning -> Icons.Default.Warning
        isAction -> Icons.Default.FlashOn
        log.sender == "User" -> Icons.Default.Person
        else -> Icons.Default.Memory
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerBg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tagColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[${log.sender.uppercase()}]",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = tagColor
                    )
                }

                Text(
                    text = log.timestamp,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = log.message,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = if (isError) RedError else TextPrimary,
                lineHeight = 16.sp
            )
        }
    }
}
