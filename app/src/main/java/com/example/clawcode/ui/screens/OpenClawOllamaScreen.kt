package com.example.clawcode.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.WhatsAppMessage
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenClawOllamaScreen(viewModel: ClawViewModel) {
    val context = LocalContext.current
    val config by viewModel.ollamaConfig.collectAsState()
    val waMessages by viewModel.whatsAppMessages.collectAsState()

    var hostUrl by remember(config) { mutableStateOf(config.hostUrl) }
    var modelName by remember(config) { mutableStateOf(config.modelName) }
    var webhookUrl by remember(config) { mutableStateOf(config.openClawWebhookUrl) }
    // Sin estado local para el fallback: la preferencia OllamaConfig.useOnDeviceLiteRTFallback se
    // lee y se reescribe tal cual, porque no hay runtime local que activar.

    var simSenderName by remember { mutableStateOf("David Botero") }
    var simMessageText by remember { mutableStateOf("Abre los ajustes de wifi y crea un contacto para Juan") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Top Banner
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = null,
                tint = NeonTeal,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    // El nombre del modelo y el host salen del config real (OllamaConfig, persistido
                    // en PrefsOllamaConfigStore); el rótulo ya no fija "Qwen 3.5" ni pesos locales.
                    text = "Ollama por HTTP · '${config.modelName}'",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    // No hay "inferencia híbrida LiteRT-LM" ni webhook abierto: se dice lo que existe.
                    text = "Puente WhatsApp: sin conexión establecida · LiteRT-LM: no implementado",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Explanation Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Arquitectura actual: sólo runtime HTTP externo (no hay ejecución en el dispositivo)",
                        color = NeonPurple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    // El webhook no entrega ni recibe payloads: simulateWhatsAppIncoming lo declara
                    // en el propio registro ("WHATSAPP_DEMO_LOCAL"). Eso es lo que se dice aquí.
                    text = "• Modelos Locales en el teléfono: NO disponibles en esta build (ninguna ruta carga pesos en el proceso); cada turno va al runtime HTTP configurado.\n" +
                           "• Modelo remoto: el que esté en 'modelName' contesta por HTTP desde este teléfono (OllamaClient). " +
                           "El webhook de OpenClaw no tiene ninguna conexión abierta: ni entra WhatsApp real ni sale mensaje alguno.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Configuration Form
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Configuración del Servidor Ollama & Webhook",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Experimental PC Server setup
                Surface(
                    color = NeonAmber.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Science, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Experimental: Conectar PC Local (Host) como Servidor",
                                color = NeonAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Si estás ejecutando Ollama en tu PC mientras usas el emulador de Android, usa la IP del host y el puerto oficial de Ollama.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { hostUrl = "http://10.0.2.2:11434" },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonAmber.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, NeonAmber),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Usar Puerto Ollama PC (10.0.2.2:11434)", color = NeonAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = hostUrl,
                    onValueChange = { hostUrl = it },
                    label = { Text("Ollama Host Endpoint", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonTeal,
                        unfocusedBorderColor = DarkBorder
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Ollama Model Name (ej: qwen3.5:latest)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonTeal,
                        unfocusedBorderColor = DarkBorder
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = webhookUrl,
                    onValueChange = { webhookUrl = it },
                    label = { Text("OpenClaw WhatsApp Webhook URL", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonTeal,
                        unfocusedBorderColor = DarkBorder
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Se retira el Switch de "Fallback a LiteRT-LM": guardaba una preferencia que la
                // propia pantalla etiqueta como sin runtime. El valor real (OllamaConfig) se muestra
                // y se conserva intacto al guardar; quien quiera marcarlo sigue pudiendo hacerlo en
                // Ajustes > "Preferencias del Runtime y Seguridad".
                Text(
                    text = "Fallback a LiteRT-LM: no implementado (ninguna ruta carga pesos en el proceso). " +
                            "Preferencia guardada ahora: " +
                            if (config.useOnDeviceLiteRTFallback) "ON, sin runtime detrás." else "OFF.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        // updateOllamaConfig sí persiste (PrefsOllamaConfigStore); lo que no hace es
                        // probar la conexión, así que el aviso no puede decir "con éxito".
                        viewModel.updateOllamaConfig(
                            hostUrl,
                            modelName,
                            webhookUrl,
                            config.useOnDeviceLiteRTFallback
                        )
                        Toast.makeText(
                            context,
                            "Guardado en preferencias locales (host/modelo/webhook). Conexión no comprobada.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guardar Parámetros del Runtime HTTP", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // WhatsApp Webhook Simulator
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(NeonEmerald, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Simulador de Mensaje Entrante OpenClaw WhatsApp",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = simSenderName,
                        onValueChange = { simSenderName = it },
                        label = { Text("Remitente", color = TextMuted) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = DarkBorder
                        ),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = simMessageText,
                    onValueChange = { simMessageText = it },
                    label = { Text("Texto del Mensaje WhatsApp", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = DarkBorder
                    ),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (simMessageText.isNotBlank()) {
                            // simulateWhatsAppIncoming es local: no entra payload de ningún webhook y
                            // la propia entrada se rotula "NO ENVIADA por WhatsApp" en el historial.
                            viewModel.simulateWhatsAppIncoming(simSenderName, simMessageText)
                            Toast.makeText(
                                context,
                                "Demo local: no llegó ningún webhook. Se envía el texto al runtime HTTP y se registra la respuesta como NO ENVIADA.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simular Recibir WhatsApp vía OpenClaw", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            // Ninguno de estos mensajes "procesados" vino de WhatsApp: se crean con el botón de demo.
            text = "Historial de la Demo (mensajes simulados, ninguno recibido de WhatsApp)",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(waMessages) { msg ->
                WhatsAppMessageItem(msg)
            }
        }
    }
}

@Composable
private fun WhatsAppMessageItem(msg: WhatsAppMessage) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = msg.senderName,
                    color = NeonEmerald,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = msg.timestamp,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = msg.content,
                color = TextPrimary,
                fontSize = 13.sp
            )

            msg.actionTriggered?.let { action ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = CodeBackground,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Acción Móvil Activada: $action",
                            color = NeonAmber,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        msg.responseSent?.let { resp ->
                            Text(
                                text = resp,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
