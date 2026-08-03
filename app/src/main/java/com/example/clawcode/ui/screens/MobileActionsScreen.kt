package com.example.clawcode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow

@Composable
fun MobileActionsScreen(viewModel: ClawViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppActionCard(
            title = "WhatsApp Bridge",
            description = "Leer y responder mensajes de WhatsApp autónomamente.",
            initialState = true,
            onExecute = { viewModel.sendChatMessage("Abre WhatsApp") }
        )
        AppActionCard(
            title = "Gmail API",
            description = "Procesar, categorizar y redactar borradores de correo.",
            initialState = false,
            onExecute = { viewModel.sendChatMessage("Abre Gmail") }
        )
        AppActionCard(
            title = "Notion / Memoria",
            description = "Sincronizar apuntes y contexto a largo plazo.",
            initialState = true,
            onExecute = { viewModel.sendChatMessage("Abre Notion") }
        )
        AppActionCard(
            title = "Control de Dispositivo",
            description = "Ajustar brillo, volumen, wifi, y modos de energía.",
            initialState = false,
            onExecute = { viewModel.sendChatMessage("Abre ajustes") }
        )
    }
}

@Composable
fun AppActionCard(title: String, description: String, initialState: Boolean, onExecute: () -> Unit) {
    var isEnabled by remember { mutableStateOf(initialState) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(16.dp))
            .border(1.dp, if (isEnabled) NeonEmerald.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, color = TextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = isEnabled,
                onCheckedChange = { isEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonEmerald,
                    checkedTrackColor = NeonEmerald.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkSurfaceVariant
                )
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = onExecute,
            enabled = isEnabled,
            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald.copy(alpha = 0.2f), contentColor = NeonEmerald),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Probar Acción")
        }
    }
}
