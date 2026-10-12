package com.example.clawcode.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.TaskPerformanceMetrics
import com.example.clawcode.data.model.TaskStatus
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: ClawViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToLive: (() -> Unit)? = null,
    onNavigateToConfig: (() -> Unit)? = null,
    onNavigateToConsole: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val palette = LocalDynamicThemePalette.current
    
    val hardware by viewModel.hardwareTelemetry.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val modfile by viewModel.jarvisModfile.collectAsState()
    val ollamaConfig by viewModel.ollamaConfig.collectAsState()
    val consoleLogs by viewModel.consoleLogs.collectAsState()
    
    val activeTasks = tasks.filter { it.status == TaskStatus.RUNNING }
    var isVoiceListeningShowing by remember { mutableStateOf(false) }
    var recognizedSpeechText by remember { mutableStateOf("") }
    
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isVoiceListeningShowing = true
        } else {
            Toast.makeText(context, "Permiso de micrófono requerido", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Welcome Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Bienvenido, Santiago",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Sistema CLAW en línea y listo.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary
                    )
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(DarkSurfaceVariant, CircleShape)
                    .border(1.dp, DarkBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = "Perfil", tint = NeonPrimary)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // System Status Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                title = "CPU",
                value = "${hardware.cpuUsagePercent}%",
                subtitle = "${hardware.cpuCores} Núcleos",
                icon = Icons.Default.Memory,
                iconColor = NeonPrimary,
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "GPU/NPU",
                value = "ACTIVO",
                subtitle = hardware.npuGpuAccelerator.take(15),
                icon = Icons.Default.DeveloperBoard,
                iconColor = NeonSecondary,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                title = "RAM",
                value = "${"%.1f".format(hardware.ramUsedGb)} GB",
                subtitle = "de ${hardware.ramTotalGb} GB",
                icon = Icons.Default.Storage,
                iconColor = WarningAmber,
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "Tokens",
                value = "${hardware.totalContextTokens}",
                subtitle = "${hardware.tokensPerSecond} t/s",
                icon = Icons.Default.GraphicEq,
                iconColor = NeonTeal,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                title = "Batería",
                value = "${hardware.batteryLevelPercent}%",
                subtitle = "${hardware.batteryTemperatureC}°C",
                icon = Icons.Default.BatteryChargingFull,
                iconColor = if (hardware.batteryLevelPercent > 20) NeonPrimary else RedError,
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "Modelo",
                value = if (ollamaConfig.useOnDeviceLiteRTFallback) "SIN RUNTIME" else "HTTP",
                subtitle = modfile.baseModel,
                icon = Icons.Default.CloudSync,
                iconColor = NeonPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Conversation
        SectionTitle("ÚLTIMA CONVERSACIÓN")
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { onNavigateToChat() },
            color = DarkSurfaceVariant,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(DarkBackground, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat", tint = NeonPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Análisis de código reciente", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Revisé los logs y encontré 2 advertencias menores.", color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Abrir", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Actions
        SectionTitle("ACCIONES RÁPIDAS")
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionButton(
                title = "Chat",
                icon = Icons.AutoMirrored.Filled.Chat,
                color = NeonPrimary,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToChat() }
            )
            QuickActionButton(
                title = "Jarvis",
                icon = Icons.Default.GraphicEq,
                color = WarningAmber,
                modifier = Modifier.weight(1f),
                onClick = { if (onNavigateToLive != null) onNavigateToLive() }
            )
            QuickActionButton(
                title = "Consola",
                icon = Icons.Default.Terminal,
                color = NeonTeal,
                modifier = Modifier.weight(1f),
                onClick = { if (onNavigateToConsole != null) onNavigateToConsole() }
            )
            QuickActionButton(
                title = "Config",
                icon = Icons.Default.Settings,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
                onClick = { if (onNavigateToConfig != null) onNavigateToConfig() }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Diagnostic Console Feed Component
        com.example.clawcode.ui.components.DiagnosticConsole(
            logs = consoleLogs,
            onClearLogs = { viewModel.clearConsoleLogs() }
        )
    }
    
    if (isVoiceListeningShowing) {
        VoiceInputRecognitionDialog(
            currentText = recognizedSpeechText,
            isListening = true,
            palette = palette,
            onDismiss = { isVoiceListeningShowing = false },
            onSendVoiceCommand = { 
                isVoiceListeningShowing = false
                viewModel.sendChatMessage(it)
                Toast.makeText(context, "Ejecutando: $it", Toast.LENGTH_SHORT).show()
                onNavigateToChat()
            }
        )
    }
}

@Composable
fun StatusCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun QuickActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
fun VoiceInputRecognitionDialog(
    currentText: String,
    isListening: Boolean,
    palette: DynamicThemePalette,
    onDismiss: () -> Unit,
    onSendVoiceCommand: (String) -> Unit
) {
    val context = LocalContext.current
    var speechInputText by remember { mutableStateOf(currentText) }
    var listeningState by remember { mutableStateOf(isListening) }

    DisposableEffect(Unit) {
        var speechRecognizer: SpeechRecognizer? = null
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        listeningState = true
                        speechInputText = "Escuchando... Di una orden para el agente."
                    }
                    override fun onBeginningOfSpeech() {
                        speechInputText = "Procesando voz..."
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        listeningState = false
                    }
                    override fun onError(error: Int) {
                        listeningState = false
                        speechInputText = "Simulación por voz lista: 'Analizar logs y resumir errores del sistema'"
                    }
                    override fun onResults(results: Bundle?) {
                        listeningState = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            speechInputText = text
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Da una orden a JARVIS")
            }
            try {
                speechRecognizer.startListening(intent)
            } catch (e: Exception) {
                speechInputText = "Simulación por voz lista: 'Analizar logs y resumir errores del sistema'"
            }
        } else {
            speechInputText = "Simulación por voz lista: 'Analizar logs y resumir errores del sistema'"
        }
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = NeonPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Entrada de Voz a Texto", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (listeningState) NeonPrimary.copy(alpha = 0.2f) else DarkBackground)
                        .border(2.dp, NeonPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Waveform",
                        tint = NeonPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = speechInputText,
                    onValueChange = { speechInputText = it },
                    label = { Text("Texto Reconocido por Voz", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSendVoiceCommand(speechInputText) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPrimary, contentColor = DarkBackground)
            ) {
                Text("Ejecutar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextMuted)
            }
        }
    )
}
