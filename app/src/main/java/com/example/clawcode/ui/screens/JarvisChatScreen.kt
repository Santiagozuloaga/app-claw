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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.ChatMessage
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun JarvisChatScreen(
    viewModel: ClawViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLive: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsState()
    val modfile by viewModel.jarvisModfile.collectAsState()
    val selectedAvatarId = modfile.selectedAvatarId
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val palette = LocalDynamicThemePalette.current
    val context = LocalContext.current

    val activeGrounding by viewModel.activeGrounding.collectAsState()

    // Voice setup
    var isVoiceListeningShowing by remember { mutableStateOf(false) }
    var recognizedSpeechText by remember { mutableStateOf("") }
    var isSpeechRecognizerListening by remember { mutableStateOf(false) }
    
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isVoiceListeningShowing = true
            isSpeechRecognizerListening = true
            recognizedSpeechText = "Escuchando entrada de voz..."
        } else {
            Toast.makeText(context, "Permiso de micrófono denegado", Toast.LENGTH_SHORT).show()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.sendChatMessage("Archivo adjunto: ${uri.lastPathSegment}", attachedFile = uri.toString())
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Grounding Context Banner
            if (activeGrounding != null) {
                Surface(
                    color = palette.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.TravelExplore, contentDescription = null, tint = palette.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Contexto Activo: '${activeGrounding!!.query}'",
                                color = palette.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        TextButton(
                            onClick = { viewModel.clearActiveGrounding() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("QUITAR", color = RedError, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Chat Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = palette.primary, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("CLAW OS", style = MaterialTheme.typography.titleLarge.copy(color = palette.primary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("¿En qué te puedo ayudar hoy, Operador?", color = TextSecondary, fontSize = 14.sp)
                            }
                        }
                    }
                }

                items(messages) { msg ->
                    ChatBubble(msg, palette, selectedAvatarId)
                }

                if (messages.isNotEmpty() && messages.last().sender != "USER") {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickReplyChip("Sugerir acciones", palette) { viewModel.sendChatMessage("Sugiere 3 acciones útiles") }
                            QuickReplyChip("Resumir", palette) { viewModel.sendChatMessage("Resume esta conversación") }
                        }
                    }
                }
            }

            // Input Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .background(palette.surface, RoundedCornerShape(28.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(28.dp))
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Adjuntar", tint = TextSecondary)
                }
                
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Mensaje a CLAW...", color = TextMuted) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = palette.primary
                    ),
                    maxLines = 5
                )

                if (inputText.isBlank()) {
                    IconButton(onClick = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voz", tint = TextSecondary)
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendChatMessage(inputText)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(palette.primary, CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = DarkBackground)
                    }
                }
            }
        }
        
        if (isVoiceListeningShowing) {
            VoiceInputRecognitionDialog(
                currentText = recognizedSpeechText,
                isListening = isSpeechRecognizerListening,
                palette = palette,
                onDismiss = {
                    isVoiceListeningShowing = false
                    isSpeechRecognizerListening = false
                },
                onSendVoiceCommand = { text ->
                    isVoiceListeningShowing = false
                    isSpeechRecognizerListening = false
                    if (text.isNotBlank()) {
                        viewModel.sendChatMessage(text)
                    }
                }
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, palette: DynamicThemePalette, selectedAvatarId: Int = 1) {
    val isUser = message.sender == "USER"

    val avatarIcon = remember(selectedAvatarId) {
        when (selectedAvatarId) {
            1 -> Icons.Default.SmartToy
            2 -> Icons.Default.Psychology
            3 -> Icons.Default.SupportAgent
            4 -> Icons.Default.Memory
            5 -> Icons.Default.Face
            6 -> Icons.Default.AutoAwesome
            else -> Icons.Default.SmartToy
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(palette.surface, CircleShape)
                    .border(1.dp, palette.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(avatarIcon, contentDescription = "CLAW Avatar", tint = palette.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .background(
                    if (isUser) palette.primary.copy(alpha = 0.15f) else Color.Transparent,
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .padding(horizontal = if (isUser) 16.dp else 0.dp, vertical = if (isUser) 12.dp else 4.dp)
        ) {
            Text(
                text = message.text,
                color = TextPrimary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun QuickReplyChip(text: String, palette: DynamicThemePalette, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = palette.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
    ) {
        Text(
            text = text,
            color = palette.secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
