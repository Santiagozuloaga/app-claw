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
    val modfileVoz by viewModel.jarvisModfile.collectAsState()

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
    // No existe "cámara activa": esta pantalla nunca muestra vista previa. Se guarda el resultado
    // real del Intent de captura (Pair<Boolean,String> de executeActionIntent) para rotular el botón.
    var resultadoCapturaCamara by remember { mutableStateOf<String?>(null) }
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
            // El Intent del sistema no está disponible (sin motor STT): no se escucha nada, hay que escribir.
            viewModel.mobileActionEngine.speak("El dictado del sistema no está disponible: escribe o presiona los accesos directos.")
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
                    // No hay escucha continua: cada pulsación abre el Intent de dictado del sistema
                    // (RecognizerIntent) para UNA frase y se cierra.
                    text = "VOZ: CAPTURA ÚNICA POR EL SISTEMA",
                    color = palette.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            // Escucha continua real. Antes este chip arrancaba un servicio que sólo publicaba una
            // notificación; ahora abre SpeechRecognizer del sistema y sólo actúa si la frase
            // transcrita empieza por la palabra clave del modfile.
            var isBackgroundModeActive by remember { mutableStateOf(false) }
            var estadoEscucha by remember { mutableStateOf("apagada") }
            // minSdk 24, asi que se puede preguntar directo al Context sin androidx ContextCompat.
            val permisoAudioConcedido = context.checkSelfPermission(
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            FilterChip(
                selected = isBackgroundModeActive,
                onClick = {
                    if (!isBackgroundModeActive && !permisoAudioConcedido) {
                        // Sin permiso no hay micro: decir "escuchando" sería exactamente la mentira
                        // que esta pantalla tenía antes.
                        android.widget.Toast.makeText(
                            context,
                            "No se puede escuchar: falta el permiso de micrófono de CLAW en Ajustes > Aplicaciones",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        isBackgroundModeActive = !isBackgroundModeActive
                        try {
                            if (isBackgroundModeActive) {
                                com.example.clawcode.service.ClawLiveBackgroundService.startService(
                                    context, modfileVoz.wakeWord
                                )
                                estadoEscucha = "arrancando…"
                                viewModel.mobileActionEngine.speak("Escucha continua activada")
                                android.widget.Toast.makeText(
                                    context,
                                    "🎙️ Escuchando: di \"${modfileVoz.wakeWord}\" seguido de la orden",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } else {
                                com.example.clawcode.service.ClawLiveBackgroundService.stopService(context)
                                estadoEscucha = "apagada"
                                android.widget.Toast.makeText(
                                    context, "Escucha continua detenida: el micrófono se cierra",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            isBackgroundModeActive = false
                            estadoEscucha = "no arrancó"
                            android.widget.Toast.makeText(
                                context, "No se pudo controlar la escucha: ${e.localizedMessage}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                label = {
                    Text(
                        if (isBackgroundModeActive) " 🎙️ ${modfileVoz.wakeWord} · $estadoEscucha"
                        else "Escucha continua (OFF)",
                        fontSize = 10.sp
                    )
                },
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
            // "LISTO Y ESCUCHANDO" afirmaba una escucha continua que no existe: el micrófono sólo se
            // abre con el Intent de dictado del sistema, y para una sola frase.
            text = when {
                isMicMuted -> "CAPTURA DE VOZ SIN SOLICITUD PENDIENTE"
                isListeningVoice -> "DICTADO DEL SISTEMA ABIERTO: UNA FRASE"
                else -> "NO ESCUCHA CONTINUAMENTE: PULSA EL MICRÓFONO PARA UNA FRASE"
            },
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
                    // Renderiza chatMessages (historial del chat); nunca fue transcripción de audio.
                    Text("HISTORIAL DEL CHAT (NO TRANSCRIPCIÓN DE AUDIO)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
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
                                    val (lanzada, detalle) = viewModel.mobileActionEngine.executeActionIntent("take_photo_camera", emptyMap())
                                    resultadoCapturaCamara = if (lanzada) detalle else "Nada lanzado: $detalle"
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
                
                // Cámara: no hay vista previa en esta pantalla, así que el botón no "enciende" nada;
                // lanza el Intent de captura del sistema y conserva el resultado real que devuelve
                // executeActionIntent (Pair<Boolean, String>).
                IconButton(
                    onClick = {
                        val (lanzada, detalle) = viewModel.mobileActionEngine.executeActionIntent("take_photo_camera", emptyMap())
                        resultadoCapturaCamara = if (lanzada) detalle else "Nada lanzado: $detalle"
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (resultadoCapturaCamara != null) palette.secondary.copy(alpha = 0.2f) else palette.surface,
                            CircleShape
                        )
                ) {
                    Icon(
                        // Videocam/VideocamOff insinuaban un stream en vivo que no existe: es un solo
                        // disparo IMAGE_CAPTURE y el rótulo dice lo que pasó con ese disparo.
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = resultadoCapturaCamara
                            ?: "Lanzar captura de cámara (no hay vista previa en esta pantalla)",
                        tint = if (resultadoCapturaCamara != null) palette.secondary else TextSecondary
                    )
                }
                
                // End Call
                IconButton(
                    onClick = {
                        viewModel.mobileActionEngine.speak("Modo de voz terminado: no queda ninguna escucha abierta.")
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

