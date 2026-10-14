package com.example.clawcode.ui.screens

import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.FileAttachmentItem
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisAgentDesignerScreen(viewModel: ClawViewModel) {
    val context = LocalContext.current
    val modfile by viewModel.jarvisModfile.collectAsState()
    val fileAttachments by viewModel.fileAttachments.collectAsState()
    // Telemetría real: HardwareTelemetry usa 0 como marca de "sin medir".
    val hardware by viewModel.hardwareTelemetry.collectAsState()

    // Espacio libre: lectura real de StatFs sobre el directorio de archivos de la app (el mismo
    // sondeo que hace DiskSpaceManager). Si la llamada falla, no se imprime ninguna cifra.
    val freeStorageGb = remember(context) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        runCatching {
            val stat = StatFs(dir.absolutePath)
            (stat.availableBlocksLong * stat.blockSizeLong) / (1024f * 1024f * 1024f)
        }.getOrNull()
    }

    // Ruta de modelos que usa la app (ModelStorage/DiskSpaceManager). Se muestra sólo como ruta:
    // aquí no se ha escrito ningún peso, así que no se puede verificar integridad de nada.
    val modelsDirPath = remember(context) {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        java.io.File(base, "models").absolutePath
    }

    // Texto ya resuelto para no repetir la comprobación de nullabilidad en la composición.
    val measuredFreeGb = freeStorageGb?.takeIf { it > 0f }
    val hasFreeStorage = measuredFreeGb != null
    val freeStorageText = if (measuredFreeGb != null) "%.0f GB".format(measuredFreeGb) else "sin medir"
    val ramMeasured = hardware.ramTotalGb > 0f
    val ramTotalText = if (ramMeasured) "%.1f GB".format(hardware.ramTotalGb) else "sin medir"

    var agentName by remember(modfile) { mutableStateOf(modfile.agentName) }
    var baseModel by remember(modfile) { mutableStateOf(modfile.baseModel) }
    var systemPrompt by remember(modfile) { mutableStateOf(modfile.systemPrompt) }
    var temperature by remember(modfile) { mutableStateOf(modfile.temperature) }
    var topP by remember(modfile) { mutableStateOf(modfile.topP) }
    var wakeWord by remember(modfile) { mutableStateOf(modfile.wakeWord) }
    var enableVoiceTTS by remember(modfile) { mutableStateOf(modfile.enableVoiceTTS) }

    var newFileName by remember { mutableStateOf("especificacion_agente.json") }
    var newFileType by remember { mutableStateOf("JSON") }
    // Resumen honesto: esta pantalla no abre ningún archivo, así que no puede describir su contenido.
    var newFileSummary by remember { mutableStateOf("Sólo un nombre escrito en la app: no se ha abierto ningún archivo ni leído ningún byte") }

    var isVoiceSpeaking by remember { mutableStateOf(false) }

    val rawModfileCode = remember(agentName, baseModel, systemPrompt, temperature, topP, wakeWord) {
        """
        # JARVIS Modfile (agente para el runtime Ollama que configures)
        FROM $baseModel

        # System Prompt definition
        SYSTEM ""${'"'}
        $systemPrompt
        ""${'"'}

        # Model Parameters
        PARAMETER temperature $temperature
        PARAMETER top_p $topP
        PARAMETER stop "<|im_end|>"
        PARAMETER stop "</s>"

        # Voice & Wake-word Config
        PARAMETER wake_word "$wakeWord"
        PARAMETER tts_enabled $enableVoiceTTS
        """.trimIndent()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = NeonEmerald,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Diseñador de Agente JARVIS & Modfile",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Crea tu Modfile para el runtime Ollama que configures, con voz, visión y archivos",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // CLAW Installer & Hardware Verification Module Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NeonEmerald, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CLAW Installer & Gestor de Modelo Único",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = NeonEmerald.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "CLAW STANDARD (etiqueta sin pesos)",
                            color = NeonEmerald,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hardware Diagnostic Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).border(0.5.dp, DarkBorder, RoundedCornerShape(6.dp))
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Text("RAM Detectada", fontSize = 9.sp, color = TextMuted)
                            Text(
                                text = ramTotalText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (ramMeasured) NeonEmerald else TextMuted
                            )
                        }
                    }

                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).border(0.5.dp, DarkBorder, RoundedCornerShape(6.dp))
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Text("Espacio Libre", fontSize = 9.sp, color = TextMuted)
                            Text(
                                text = freeStorageText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasFreeStorage) NeonEmerald else TextMuted
                            )
                        }
                    }

                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).border(0.5.dp, DarkBorder, RoundedCornerShape(6.dp))
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Text("Acelerador Local", fontSize = 9.sp, color = TextMuted)
                            // Ninguna build acelera inferencia aquí: se imprime el valor del sondeo,
                            // que ya declara que no hay NPU/GPU, en lugar de anunciar "NPU Vulkan".
                            Text(
                                text = hardware.npuGpuAccelerator.ifBlank { "sin medir" },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Verification details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CodeBackground, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "VERIFICACIÓN DE INTEGRIDAD SHA-256",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = NeonTeal,
                        fontWeight = FontWeight.Bold
                    )
                    // Nada se ha hasheado en esta pantalla: el hash literal que aparecía aquí era
                    // el de la cadena vacía y ningún archivo de modelo existe en el dispositivo.
                    Text(
                        text = "Sin ejecutar: en esta sesión no se ha calculado ningún hash, así que no hay MATCH que mostrar.",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                    Text(
                        text = "El único verificador real (Sha256Verifier) corre al terminar una descarga en ModelDownloadManager; aquí no se ha descargado ningún peso.",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                    Text(
                        text = "Ruta de modelos (sin archivos escritos por esta pantalla): $modelsDirPath",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Standalone On-Device Import Banner Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NeonEmerald.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Motor Único: el runtime que configures (Ollama por HTTP)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = NeonEmerald.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "SIN RUNTIME LOCAL",
                            color = NeonEmerald,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "El APK se instala ligero y no trae pesos de modelo embebidos: cada turno se envía por HTTP al runtime que configures en Ajustes de Ollama (host + modelo), y `/agent model` informa cuál está atendiendo. La personalidad no se carga desde assets/: el APK no incluye ninguna carpeta assets/ con system_prompt.txt, personality.json, config.json ni capabilities.json, así que la única personalidad que llega al modelo es el System Prompt que escribas en este formulario. Para inferencia dentro del teléfono todavía falta un runtime local empaquetado: ninguna ruta de esta build carga GGUF ni LiteRT en el proceso.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            // Antes esta pulsación creaba tres adjuntos con tamaños escritos a mano
                            // (12/4/8 KB) para archivos que la app no lee: ahora no fabrica nada.
                            Toast.makeText(
                                context,
                                "No se ha cargado ningún asset: el APK no trae assets/ ni hay selector de archivos, así que esta pulsación no añade adjuntos ni lee bytes",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.FolderZip, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Comprobar Assets de Personalidad", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            // Sin bytes leídos: sizeKb = 0 es la marca "sin medir" del modelo.
                            viewModel.addFileAttachment("claw_standard.referencia.txt", "Text", 0, "Referencia del modelo base: no se descargó ningún peso y esta build no trae runtime local")
                            Toast.makeText(context, "Sólo se añadió la referencia al modfile: no se descargó ningún peso (ningún byte salió de la red) y esta build no ejecuta inferencia local", Toast.LENGTH_LONG).show()
                        },
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonTeal),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.DownloadForOffline, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Referenciar modelo base", color = NeonTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Async Background Autonomous Agent Loop Tester
        var asyncTestPrompt by remember { mutableStateOf("Abre Spotify y reproduce playlist de concentración") }
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SyncProblem,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Prueba de Bucle Asíncrono del Agente",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Ejecución Asíncrona",
                        color = NeonEmerald,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = asyncTestPrompt,
                    onValueChange = { asyncTestPrompt = it },
                    label = { Text("Comando Asíncrono Autónomo", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = DarkBorder
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        viewModel.runAsyncAgentTaskLoop(asyncTestPrompt)
                        Toast.makeText(context, "Lanzando tarea asíncrona autónoma en segundo plano...", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lanzar Tarea Asíncrona Autónoma", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Modfile Configurator Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Parámetros del Modfile",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Voice TTS Test Button
                    IconButton(
                        onClick = {
                            // La voz la pone el sintetizador del sistema: no hay modelo JARVIS propio
                            // ni integración con "todas las aplicaciones Android".
                            viewModel.speakVoice("Hola, soy JARVIS. Esta la lee el sintetizador de voz de Android; la personalidad es el Modfile que estés editando.")
                            Toast.makeText(context, "Probando la voz del sistema con un texto fijo", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Probar Voz JARVIS",
                            tint = NeonEmerald
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = agentName,
                        onValueChange = { agentName = it },
                        label = { Text("Nombre del Agente", color = TextMuted) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = DarkBorder
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = baseModel,
                        onValueChange = { baseModel = it },
                        label = { Text("Modelo Base (Ollama)", color = TextMuted) },
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
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt del Modfile", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = DarkBorder
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(text = "Temperatura: ${"%.2f".format(temperature)}", color = TextSecondary, fontSize = 12.sp)
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0.0f..1.0f,
                            modifier = Modifier.width(150.dp),
                            colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Voz TTS", color = TextPrimary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = enableVoiceTTS,
                            onCheckedChange = { enableVoiceTTS = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonEmerald)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.updateJarvisModfile(agentName, baseModel, systemPrompt, temperature, topP, wakeWord, enableVoiceTTS)
                        // updateJarvisModfile sólo actualiza el estado de la sesión: no escribe ningún
                        // archivo ni compila un Modfile, pero el System Prompt sí viaja en el próximo turno.
                        Toast.makeText(context, "Modfile aplicado al estado de la sesión: se usará en el próximo turno; no se ha escrito ningún archivo ni compilado nada", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aplicar Modfile (sin compilar)", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Live Generated Modfile Code Preview
        Card(
            colors = CardDefaults.cardColors(containerColor = CodeBackground),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Vista Previa de Archivo Modfile Gerado:",
                    color = NeonAmber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rawModfileCode,
                    color = NeonTeal,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                // El txt de AI Studio prometio un boton "Copiar Modfile" que nunca se escribio.
                // Copia el texto que se ve arriba, que es el que de verdad se genera en memoria.
                val portapapeles = LocalClipboardManager.current
                OutlinedButton(
                    onClick = { portapapeles.setText(AnnotatedString(rawModfileCode)) },
                    border = BorderStroke(1.dp, NeonAmber),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Copiar Modfile", color = NeonAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Multimodal File Inspector Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Archivos y Documentos Adjuntos",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = {
                    // No hay selector de archivos: sizeKb = 0 es "sin medir" y la tarjeta lo muestra así.
                    viewModel.addFileAttachment(newFileName, newFileType, 0, newFileSummary)
                    Toast.makeText(context, "Entrada añadida a la lista: no se ha abierto ningún archivo, así que el tamaño está sin medir", Toast.LENGTH_LONG).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Adjuntar Archivo",
                    tint = NeonTeal
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(fileAttachments) { file ->
                FileAttachmentCard(file)
            }
        }
    }
}

@Composable
private fun FileAttachmentCard(file: FileAttachmentItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = when (file.fileType) {
                        "Modfile" -> Icons.Default.Code
                        "JSON" -> Icons.Default.DataObject
                        "Image" -> Icons.Default.Image
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = NeonTeal,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = file.fileName,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = file.contentSummary,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Surface(
                color = if (file.sizeKb > 0) NeonEmerald.copy(alpha = 0.15f) else DarkSurfaceVariant,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    // sizeKb == 0 es la marca "sin medir": ningún archivo real se ha leído en la app.
                    text = if (file.sizeKb > 0) "${file.sizeKb} KB" else "tamaño sin medir",
                    color = if (file.sizeKb > 0) NeonEmerald else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
