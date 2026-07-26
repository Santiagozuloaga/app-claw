package com.example.clawcode.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.ChatMessage
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisChatScreen(viewModel: ClawViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val modfile by viewModel.jarvisModfile.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var selectedFileForAttachment by remember { mutableStateOf<String?>(null) }
    var isListeningVoice by remember { mutableStateOf(false) }
    var isCameraActive by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(NeonEmerald)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "JARVIS Chat & Voz Local",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${modfile.agentName} (${modfile.baseModel}) • 100% On-Device",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Voice TTS Toggle indicator
            IconButton(
                onClick = {
                    val newTts = !modfile.enableVoiceTTS
                    viewModel.updateJarvisModfile(
                        modfile.agentName,
                        modfile.baseModel,
                        modfile.systemPrompt,
                        modfile.temperature,
                        modfile.topP,
                        modfile.wakeWord,
                        newTts
                    )
                    Toast.makeText(context, if (newTts) "Voz TTS Activada" else "Voz TTS Desactivada", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = if (modfile.enableVoiceTTS) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = "Voz TTS",
                    tint = if (modfile.enableVoiceTTS) NeonEmerald else TextMuted
                )
            }
        }

        // Camera Frame Snapshot Overlay Bar (if activated)
        if (isCameraActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.dp, NeonTeal, RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Videocam, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Cámara Visión OCR Lista: Capturando fotograma...", color = TextPrimary, fontSize = 11.sp)
                    }

                    TextButton(
                        onClick = {
                            isCameraActive = false
                            selectedFileForAttachment = "camera_snapshot.jpg"
                            Toast.makeText(context, "Fotograma capturado y listo para análisis por JARVIS", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Capturar", color = NeonTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Attachment Bar Indicator
        selectedFileForAttachment?.let { fileName ->
            Surface(
                color = NeonTeal.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.AttachFile, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Adjunto: $fileName", color = NeonTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = { selectedFileForAttachment = null },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = NeonTeal)
                    }
                }
            }
        }

        // Chat Message History List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(chatMessages) { message ->
                ChatMessageBubble(message, onExecuteAction = { actionName ->
                    viewModel.executeMobileAction(actionName, mapOf())
                })
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick Action Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Spotify", "Gmail", "Notion", "Calendario", "YouTube", "Ajustes").forEach { appChip ->
                FilterChip(
                    selected = false,
                    onClick = {
                        inputText = "Abre $appChip"
                    },
                    label = { Text(appChip, fontSize = 10.sp, color = TextPrimary) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = DarkSurface),
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false, borderColor = DarkBorder)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Input Box Row (Text, Mic, Camera, Attachment, Send)
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Attachment Button
                IconButton(
                    onClick = {
                        selectedFileForAttachment = "documento_analisis.pdf"
                        Toast.makeText(context, "Archivo 'documento_analisis.pdf' seleccionado", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Adjuntar", tint = NeonTeal)
                }

                // Camera Vision Button
                IconButton(
                    onClick = {
                        isCameraActive = !isCameraActive
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Cámara Visión",
                        tint = if (isCameraActive) NeonAmber else TextSecondary
                    )
                }

                // Input Text Field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Escribe o habla a JARVIS...", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    maxLines = 3
                )

                // Push to Talk Mic Button
                IconButton(
                    onClick = {
                        isListeningVoice = !isListeningVoice
                        if (isListeningVoice) {
                            inputText = "Abre Spotify y reproduce playlist de concentración"
                            Toast.makeText(context, "Escuchando comando de voz...", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Micrófono",
                        tint = if (isListeningVoice) NeonPink else TextSecondary
                    )
                }

                // Send Button
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val textToSend = inputText
                            val attachedToSend = selectedFileForAttachment
                            inputText = ""
                            selectedFileForAttachment = null
                            isListeningVoice = false

                            viewModel.sendChatMessage(textToSend, attachedToSend)
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = NeonEmerald)
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Enviar", tint = DarkBackground)
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onExecuteAction: (String) -> Unit
) {
    val isUser = message.sender == "USER"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Text(
                text = if (isUser) "Tú" else "JARVIS (Qwen 3.5)",
                color = if (isUser) NeonTeal else NeonEmerald,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = message.timestamp,
                color = TextMuted,
                fontSize = 9.sp
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) CodeBackground else DarkSurface
            ),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 12.dp
            ),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .border(
                    1.dp,
                    if (isUser) NeonTeal.copy(alpha = 0.4f) else NeonEmerald.copy(alpha = 0.4f),
                    RoundedCornerShape(12.dp)
                )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                message.attachedFile?.let { file ->
                    Surface(
                        color = NeonTeal.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Row(modifier = Modifier.padding( horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AttachFile, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = file, color = NeonTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = message.text,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                if (message.isActionExecuted && message.actionExecutedName != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = NeonEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Función Invocada: ${message.actionExecutedName}",
                                color = NeonEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
