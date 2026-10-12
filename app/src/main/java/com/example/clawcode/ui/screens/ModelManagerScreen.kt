package com.example.clawcode.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.core.model.DownloadStatus
import com.example.clawcode.core.model.ModelDescriptor
import com.example.clawcode.core.repository.ModelRepository
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel
import kotlinx.coroutines.launch

@Composable
fun ModelManagerScreen(viewModel: ClawViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // viewModel.ollamaConfig es un stateIn(WhileSubscribed): leer su .value sin coleccionarlo
    // devuelve la foto del arranque, no la configuración vigente. Coleccionarlo mantiene el
    // puente apuntando al runtime real aunque el host se haya descubierto o editado después.
    val ollamaConfig by viewModel.ollamaConfig.collectAsState()

    // Create or access ModelRepository & Qwen Bridge Service from context
    val modelRepo = remember { ModelRepository(context) }
    val qwenBridge = remember {
        com.example.clawcode.core.model.QwenModelBridgeService(
            context = context,
            configProvider = { ollamaConfig }
        )
    }
    val bridgeState by qwenBridge.bridgeState.collectAsState()
    val lastTestResult by qwenBridge.lastTestResult.collectAsState()

    val availableModels by modelRepo.availableModels.collectAsState()
    val runtimeConfig by modelRepo.runtimeConfig.collectAsState()
    val downloadProgresses by modelRepo.downloadManager.downloadProgresses.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = Catálogo Hugging Face, 1 = Configuración, 2 = Prueba de Puente Qwen
    var testPromptInput by remember { mutableStateOf("Hola JARVIS, ¿cuál es tu estado en mi Vivo V25?") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Top Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "GESTOR DE MODELOS DESCARGABLES (sin runtime en el dispositivo)",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Google AI Edge Gallery (LiteRT) & Hugging Face (GGUF): descarga; esta build no los ejecuta en el dispositivo",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            Surface(
                color = NeonEmerald.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(NeonEmerald, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AUTÓNOMO",
                        color = NeonEmerald,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tab Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                label = { Text("Modelos Hugging Face", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonEmerald.copy(alpha = 0.2f),
                    selectedLabelColor = NeonEmerald
                )
            )
            FilterChip(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                label = { Text("Parámetros GGUF", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonTeal.copy(alpha = 0.2f),
                    selectedLabelColor = NeonTeal
                )
            )
            FilterChip(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                label = { Text("Prueba Qwen 3.5/3.6", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonPurple.copy(alpha = 0.2f),
                    selectedLabelColor = NeonPurple
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (activeTab) {
            0 -> {
                // Active Model Summary Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("MODELO ACTIVO ACTUALMENTE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(runtimeConfig.activeModelName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Contexto: ${runtimeConfig.contextLength} tokens | Temp: ${runtimeConfig.temperature}", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Models List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(availableModels) { model ->
                        val status = modelRepo.getDownloadStatus(model.id)
                        val progress = downloadProgresses[model.id]

                        ModelCardItem(
                            model = model,
                            status = status,
                            progress = progress,
                            isActive = runtimeConfig.activeModelId == model.id,
                            onDownload = {
                                modelRepo.startDownload(model, scope)
                                Toast.makeText(context, "Iniciando descarga por partes de ${model.name}", Toast.LENGTH_SHORT).show()
                            },
                            onPause = {
                                modelRepo.pauseDownload(model.id)
                                Toast.makeText(context, "Descarga pausada", Toast.LENGTH_SHORT).show()
                            },
                            onActivate = {
                                modelRepo.setActiveModel(model)
                                viewModel.updateJarvisModfile(
                                    agentName = "Qwen AI Agent",
                                    baseModel = model.name,
                                    systemPrompt = runtimeConfig.systemPrompt,
                                    temperature = runtimeConfig.temperature,
                                    topP = runtimeConfig.topP,
                                    wakeWord = "Jarvis",
                                    enableVoiceTTS = true
                                )
                                Toast.makeText(context, "Modelo ${model.name} activado como motor local", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                modelRepo.deleteModel(model.id)
                                Toast.makeText(context, "Modelo eliminado de la memoria", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
            1 -> {
                // Inference Config Tab
                InferenceSettingsContent(
                    runtimeConfig = runtimeConfig,
                    onUpdate = { newPrompt, newTemp, newTopP, newPenalty, newCtx, powerSave ->
                        modelRepo.updateRuntimeConfig(
                            systemPrompt = newPrompt,
                            temperature = newTemp,
                            topP = newTopP,
                            repeatPenalty = newPenalty,
                            contextLength = newCtx,
                            powerSavingMode = powerSave
                        )
                        Toast.makeText(context, "Ajustes de inferencia GGUF guardados", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            2 -> {
                // Qwen Bridge Tester Tab
                QwenBridgeTestingContent(
                    qwenBridge = qwenBridge,
                    bridgeState = bridgeState,
                    lastTestResult = lastTestResult,
                    testPromptInput = testPromptInput,
                    onPromptChange = { testPromptInput = it },
                    scope = scope
                )
            }
        }
    }
}

@Composable
fun ModelCardItem(
    model: ModelDescriptor,
    status: DownloadStatus,
    progress: com.example.clawcode.core.model.ModelDownloadProgress?,
    isActive: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    val sizeMb = model.sizeBytes / (1024 * 1024)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isActive) NeonEmerald else DarkBorder,
                RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (isActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = NeonEmerald, shape = RoundedCornerShape(4.dp)) {
                                Text("EN USO", color = DarkBackground, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Text("Hugging Face: ${model.authorHuggingFace}", color = NeonPurple, fontSize = 11.sp)
                }

                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("$sizeMb MB", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(model.description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)

            Spacer(modifier = Modifier.height(10.dp))

            // Spec chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SpecTag("Formato: ${model.type.uppercase()}", NeonTeal)
                SpecTag("Cuantización: ${model.quantType}", NeonAmber)
                SpecTag("RAM Req: ${model.recommendedRamMb} MB", TextMuted)
            }

            // Download progress indicator
            if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PAUSED || status == DownloadStatus.VERIFYING) {
                Spacer(modifier = Modifier.height(12.dp))
                val pct = progress?.progressPercent ?: 0f
                val speed = progress?.downloadSpeedKbps ?: 0L

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (status == DownloadStatus.VERIFYING) "Verificando Hash SHA256..." else "Descargando por partes (${"%.1f".format(pct)}%)",
                            color = NeonEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (status == DownloadStatus.DOWNLOADING) {
                            Text("${speed / 1024} MB/s", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = pct / 100f,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = NeonEmerald,
                        trackColor = DarkSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (status) {
                    DownloadStatus.NOT_DOWNLOADED -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Descargar GGUF", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    DownloadStatus.DOWNLOADING -> {
                        OutlinedButton(
                            onClick = onPause,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonAmber)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pausar", color = NeonAmber, fontSize = 12.sp)
                        }
                    }

                    DownloadStatus.PAUSED -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonAmber),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reanudar", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    DownloadStatus.INSTALLED, DownloadStatus.ACTIVE -> {
                        if (!isActive) {
                            Button(
                                onClick = onActivate,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Activar Modelo", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = RedError)
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
fun SpecTag(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
fun InferenceSettingsContent(
    runtimeConfig: com.example.clawcode.core.config.ModelRuntimeConfig,
    onUpdate: (String, Float, Float, Float, Int, Boolean) -> Unit
) {
    var prompt by remember(runtimeConfig) { mutableStateOf(runtimeConfig.systemPrompt) }
    var temp by remember(runtimeConfig) { mutableStateOf(runtimeConfig.temperature) }
    var topP by remember(runtimeConfig) { mutableStateOf(runtimeConfig.topP) }
    var penalty by remember(runtimeConfig) { mutableStateOf(runtimeConfig.repeatPenalty) }
    var ctx by remember(runtimeConfig) { mutableStateOf(runtimeConfig.contextLength) }
    var powerSave by remember(runtimeConfig) { mutableStateOf(runtimeConfig.powerSavingMode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        Text("System Prompt (Modfile Lógico)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth().height(110.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedIndicatorColor = NeonEmerald
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text("Temperatura (${"%.2f".format(temp)})", color = TextSecondary, fontSize = 12.sp)
        Slider(
            value = temp,
            onValueChange = { temp = it },
            valueRange = 0.1f..1.5f,
            colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
        )

        Text("Top-P (${"%.2f".format(topP)})", color = TextSecondary, fontSize = 12.sp)
        Slider(
            value = topP,
            onValueChange = { topP = it },
            valueRange = 0.1f..1.0f,
            colors = SliderDefaults.colors(thumbColor = NeonTeal, activeTrackColor = NeonTeal)
        )

        Text("Longitud de Contexto Token Buffer (${ctx} tokens)", color = TextSecondary, fontSize = 12.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            listOf(2048, 4096, 8192, 16384).forEach { size ->
                FilterChip(
                    selected = ctx == size,
                    onClick = { ctx = size },
                    label = { Text("$size") }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Modo Ahorro de Batería (NPU Dynamic Throttle)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Reduce el consumo térmico en sesiones de inferencia largas", color = TextMuted, fontSize = 11.sp)
            }
            Switch(
                checked = powerSave,
                onCheckedChange = { powerSave = it },
                colors = SwitchDefaults.colors(checkedThumbColor = NeonEmerald)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = { onUpdate(prompt, temp, topP, penalty, ctx, powerSave) },
            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, tint = DarkBackground)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Guardar Parámetros GGUF", color = DarkBackground, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun QwenBridgeTestingContent(
    qwenBridge: com.example.clawcode.core.model.QwenModelBridgeService,
    bridgeState: com.example.clawcode.core.model.QwenModelBridgeState,
    lastTestResult: com.example.clawcode.core.model.QwenTestInferenceResult?,
    testPromptInput: String,
    onPromptChange: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status Card
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Puente de Carga Asíncrona Qwen 3.5/3.6 GGUF", color = NeonPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                
                val statusText = when (bridgeState) {
                    is com.example.clawcode.core.model.QwenModelBridgeState.Unloaded -> "Estado: Modelo No Cargado en Memoria"
                    is com.example.clawcode.core.model.QwenModelBridgeState.Loading -> "Cargando Tensores: ${(bridgeState as com.example.clawcode.core.model.QwenModelBridgeState.Loading).stepMessage}"
                    is com.example.clawcode.core.model.QwenModelBridgeState.Ready -> "Modelo Listo: ${(bridgeState as com.example.clawcode.core.model.QwenModelBridgeState.Ready).modelName} (${(bridgeState as com.example.clawcode.core.model.QwenModelBridgeState.Ready).loadedSizeMb} MB)"
                    is com.example.clawcode.core.model.QwenModelBridgeState.Inferring -> "Generando Tokens: ${(bridgeState as com.example.clawcode.core.model.QwenModelBridgeState.Inferring).currentTokens} tokens (${String.format("%.1f", (bridgeState as com.example.clawcode.core.model.QwenModelBridgeState.Inferring).tokensPerSecond)} t/s)"
                    is com.example.clawcode.core.model.QwenModelBridgeState.Error -> "Error: ${(bridgeState as com.example.clawcode.core.model.QwenModelBridgeState.Error).message}"
                }
                Text(statusText, color = TextPrimary, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                val descriptor = ModelDescriptor(
                                    id = "qwen-3.5-3b-gguf",
                                    name = "Qwen 3.5 3B GGUF",
                                    version = "3.5",
                                    type = "gguf",
                                    sizeBytes = 1940000000L,
                                    sha256 = "f3a2b...",
                                    downloadUrl = "https://huggingface.co/Qwen/Qwen3.5-3B-GGUF"
                                )
                                qwenBridge.loadQwenModelAsync(descriptor)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cargar Tensores Async", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch { qwenBridge.unloadModelAsync() }
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TextMuted)
                    ) {
                        Text("Descargar Memoria", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        // Test Inference Prompt Card
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Probar Función de Inferencia del Modelo", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = testPromptInput,
                    onValueChange = onPromptChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Escribe un prompt de prueba...", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        scope.launch {
                            qwenBridge.runTestInference(testPromptInput)
                        }
                    },
                    enabled = bridgeState !is com.example.clawcode.core.model.QwenModelBridgeState.Inferring,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Probar", tint = DarkBackground)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("EJECUTAR PRUEBA DE INFERENCIA", color = DarkBackground, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                }
            }
        }

        // Output Result Card
        lastTestResult?.let { res ->
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Resultado Inferencia Qwen 3.5", color = NeonEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${res.latencyMs} ms • ${String.format("%.1f", res.tokensPerSecond)} t/s", color = TextMuted, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(res.outputText, color = TextPrimary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

