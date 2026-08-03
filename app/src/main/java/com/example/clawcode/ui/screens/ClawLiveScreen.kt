package com.example.clawcode.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.ChatMessage
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ClawLiveScreen(
    viewModel: ClawViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val palette = LocalDynamicThemePalette.current
    val context = LocalContext.current
    val hardware by viewModel.hardwareTelemetry.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val tasks by viewModel.tasks.collectAsState()

    // Waveform Animation state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    var isMicMuted by remember { mutableStateOf(false) }
    var isCameraActive by remember { mutableStateOf(false) }
    var isListeningVoice by remember { mutableStateOf(false) }
    var customVoiceInput by remember { mutableStateOf("") }
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Android Speech Recognition Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListeningVoice = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.sendChatMessage(spokenText)
            }
        }
    }

    fun startSpeechRecognition() {
        try {
            isListeningVoice = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Escuchando... Habla con JARVIS")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            isListeningVoice = false
            // Fallback notification or toast if STT engine is absent in emulator
            viewModel.mobileActionEngine.speak("Iniciando modo de entrada por voz. Puedes escribir o presionar los accesos directos.")
        }
    }

    // Auto-scroll on new messages
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    fun handleUserVoicePrompt(prompt: String) {
        viewModel.sendChatMessage(prompt)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Status Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(palette.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "JARVIS VOICE CORE EN VIVO",
                    color = palette.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            // Background Live Service Toggle
            var isBackgroundModeActive by remember { mutableStateOf(false) }
            FilterChip(
                selected = isBackgroundModeActive,
                onClick = {
                    isBackgroundModeActive = !isBackgroundModeActive
                    try {
                        if (isBackgroundModeActive) {
                            com.example.clawcode.service.ClawLiveBackgroundService.startService(context)
                            viewModel.mobileActionEngine.speak("Servicio Claw Live activado en segundo plano")
                            android.widget.Toast.makeText(context, "🎙️ Service Activo en segundo plano", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            com.example.clawcode.service.ClawLiveBackgroundService.stopService(context)
                            android.widget.Toast.makeText(context, "Service desactivado", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        android.widget.Toast.makeText(context, "Estado: Modo segundo plano listo (${e.localizedMessage})", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                label = { Text(if (isBackgroundModeActive) " 🎙️ Fuera de App (ON)" else "Fuera de App (OFF)", fontSize = 10.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.primary.copy(alpha = 0.25f),
                    selectedLabelColor = palette.primary
                )
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))

        // Center Pulsing Orb / Waveform
        Box(
            modifier = Modifier
                .size(160.dp)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulse rings when listening or speaking
            if (!isMicMuted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(pulseScale)
                        .border(2.dp, palette.primary.copy(alpha = pulseAlpha), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale * 1.15f)
                        .border(1.dp, palette.secondary.copy(alpha = pulseAlpha), CircleShape)
                )
            }
            
            // Core circle
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .border(2.dp, if (isMicMuted) RedError else palette.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.GraphicEq,
                    contentDescription = "Voice Indicator",
                    tint = if (isMicMuted) RedError else palette.primary,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = if (isMicMuted) "MICRÓFONO DESACTIVADO" else if (isListeningVoice) "ESCUCHANDO VOZ DEL OPERADOR..." else "JARVIS LISTO Y ESCUCHANDO",
            color = if (isMicMuted) RedError else palette.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        
        Spacer(modifier = Modifier.height(20.dp))

        // Transcripts Window connected directly to chatMessages
        Surface(
            color = palette.surface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.border),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TRANSCRIPCIÓN EN TIEMPO REAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text("${chatMessages.size} Mensajes", fontSize = 9.sp, color = palette.primary)
                }
                Spacer(modifier = Modifier.height(10.dp))
                
                if (chatMessages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Habla o selecciona un comando rápido para iniciar la conversación con JARVIS.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(chatMessages) { msg ->
                            val isJarvis = msg.sender == "JARVIS" || msg.sender == "CLAW"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isJarvis) Arrangement.Start else Arrangement.End
                            ) {
                                Surface(
                                    color = if (isJarvis) palette.primary.copy(alpha = 0.15f) else palette.secondary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isJarvis) palette.primary.copy(alpha = 0.3f) else palette.secondary.copy(alpha = 0.3f)),
                                    modifier = Modifier.widthIn(max = 290.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = if (isJarvis) "JARVIS AI" else "OPERADOR",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isJarvis) palette.primary else palette.secondary
                                            )
                                            Text(
                                                text = msg.timestamp,
                                                fontSize = 8.sp,
                                                color = TextMuted
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = msg.text,
                                            fontSize = 12.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Actions & Input
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Estado Sistema", "Reporte RAM", "Tomar Foto", "Escanear OCR").forEach { prompt ->
                    Surface(
                        color = palette.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, palette.border),
                        onClick = {
                            when (prompt) {
                                "Tomar Foto" -> {
                                    isCameraActive = true
                                    viewModel.mobileActionEngine.executeActionIntent("take_photo_camera", emptyMap())
                                    handleUserVoicePrompt("Toma una foto con la cámara")
                                }
                                "Escanear OCR" -> {
                                    viewModel.mobileActionEngine.executeActionIntent("scan_image_ocr", emptyMap())
                                    handleUserVoicePrompt("Extrae texto con OCR")
                                }
                                "Estado Sistema" -> {
                                    handleUserVoicePrompt("Dame un informe del estado actual del sistema y la batería")
                                }
                                else -> {
                                    handleUserVoicePrompt(prompt)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = prompt,
                            fontSize = 9.sp,
                            color = palette.primary,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customVoiceInput,
                    onValueChange = { customVoiceInput = it },
                    placeholder = { Text("Escribe o habla a JARVIS...", fontSize = 11.sp, color = TextMuted) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.border,
                        focusedContainerColor = palette.surface,
                        unfocusedContainerColor = palette.surface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        if (customVoiceInput.isNotBlank()) {
                            val textToSend = customVoiceInput
                            customVoiceInput = ""
                            handleUserVoicePrompt(textToSend)
                        } else {
                            startSpeechRecognition()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(palette.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = if (customVoiceInput.isNotBlank()) Icons.Default.Send else Icons.Default.Mic,
                        contentDescription = "Action",
                        tint = DarkBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Controls
        Surface(
            color = palette.surface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic Toggle / Trigger STT
                IconButton(
                    onClick = {
                        isMicMuted = !isMicMuted
                        if (!isMicMuted) {
                            startSpeechRecognition()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isMicMuted) RedError.copy(alpha = 0.2f) else palette.primary.copy(alpha = 0.2f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mic",
                        tint = if (isMicMuted) RedError else palette.primary
                    )
                }
                
                // Camera
                IconButton(
                    onClick = { 
                        isCameraActive = !isCameraActive 
                        if (isCameraActive) {
                            viewModel.mobileActionEngine.executeActionIntent("take_photo_camera", emptyMap())
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isCameraActive) palette.secondary.copy(alpha = 0.2f) else palette.surface,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isCameraActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Cam",
                        tint = if (isCameraActive) palette.secondary else TextSecondary
                    )
                }
                
                // End Call
                IconButton(
                    onClick = {
                        viewModel.mobileActionEngine.speak("Sesión de voz JARVIS finalizada.")
                        onNavigateBack()
                    },
                    modifier = Modifier.size(48.dp).background(RedError, CircleShape)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "End", tint = Color.White)
                }
            }
        }
    }
}

