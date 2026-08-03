package com.example.clawcode.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.AgentPersonality
import com.example.clawcode.data.model.CustomThemeColorPreset
import com.example.clawcode.data.model.ModelTaskMode
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel

data class SettingPage(
    val id: String,
    val title: String,
    val category: String,
    val tags: List<String>,
    val content: @Composable (ClawViewModel) -> Unit
)

@Composable
fun ConfigModfileScreen(viewModel: ClawViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    var activePageId by remember { mutableStateOf<String?>(null) }
    
    val categories = listOf("Agent Configuration", "Network & API", "UI Preferences", "Memory Management")
    
    val allPages = listOf(
        SettingPage(
            id = "agent_personality",
            title = "Personalidad y Voz",
            category = "Agent Configuration",
            tags = listOf("voz", "tts", "personalidad", "temperatura", "casual", "modelo"),
            content = { AgentPersonalitySettings(it) }
        ),
        SettingPage(
            id = "agent_designer",
            title = "Diseñador Modfile",
            category = "Agent Configuration",
            tags = listOf("modfile", "prompt", "sistema", "comportamiento"),
            content = { JarvisAgentDesignerScreen(it) }
        ),
        SettingPage(
            id = "agent_avatar",
            title = "Avatar AI",
            category = "Agent Configuration",
            tags = listOf("avatar", "imagen", "visual", "apariencia"),
            content = { AvatarSelectionScreen(it) }
        ),
        SettingPage(
            id = "agent_diagnostic",
            title = "Consola de Diagnóstico",
            category = "Agent Configuration",
            tags = listOf("diagnóstico", "consola", "logs", "inferencia", "telemetría", "trazas"),
            content = { DiagnosticConsole(it) }
        ),
        SettingPage(
            id = "agent_tools",
            title = "Herramientas MCP",
            category = "Agent Configuration",
            tags = listOf("mcp", "tools", "herramientas", "integración", "plugins"),
            content = { 
                val tools by it.tools.collectAsState()
                ToolsScreen(tools = tools, onToggleTool = { tool -> it.toggleTool(tool) }) 
            }
        ),
        SettingPage(
            id = "network_ollama",
            title = "Puente Ollama",
            category = "Network & API",
            tags = listOf("ollama", "host", "api", "red", "conexión", "servidor"),
            content = { OpenClawOllamaScreen(it) }
        ),
        SettingPage(
            id = "ui_theme",
            title = "Motor Dinámico de Temas",
            category = "UI Preferences",
            tags = listOf("tema", "color", "dinámico", "ui", "visual", "paleta"),
            content = { 
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    DynamicThemeConfigSection(it) 
                }
            }
        ),
        SettingPage(
            id = "mem_models",
            title = "Model Manager GGUF",
            category = "Memory Management",
            tags = listOf("gguf", "modelos", "descarga", "gestor", "memoria", "litert"),
            content = { ModelManagerScreen(it) }
        ),
        SettingPage(
            id = "mem_disk_space",
            title = "Gestor de Disco y Almacenamiento",
            category = "Memory Management",
            tags = listOf("disco", "almacenamiento", "limpiar", "caché", "modelos", "peso", "memoria"),
            content = { DiskSpaceManagerScreen(it) }
        ),
        SettingPage(
            id = "mem_permissions",
            title = "Permisos OS",
            category = "Memory Management",
            tags = listOf("permisos", "os", "sistema", "acceso", "micrófono", "almacenamiento"),
            content = { PermissionsScreen() }
        ),
        SettingPage(
            id = "mem_security",
            title = "Seguridad y Telemetría",
            category = "Memory Management",
            tags = listOf("seguridad", "biométrico", "telemetría", "limpieza", "hz"),
            content = { SecurityTelemetrySettings(it) }
        )
    )

    val filteredPages = if (searchQuery.isNotBlank()) {
        allPages.filter { 
            it.title.contains(searchQuery, ignoreCase = true) || 
            it.tags.any { tag -> tag.contains(searchQuery, ignoreCase = true) } 
        }
    } else {
        allPages
    }
    
    val activePage = allPages.find { it.id == activePageId }

    if (activePage != null) {
        Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
            Surface(
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { activePageId = null },
                        modifier = Modifier.size(32.dp).background(DarkBackground, RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = NeonEmerald, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = activePage.title,
                        color = NeonEmerald,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                activePage.content(viewModel)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar ajustes (ej. 'voz', 'tema')...", color = TextMuted, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = NeonEmerald) },
                    trailingIcon = { 
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = RedError)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = NeonEmerald
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            if (searchQuery.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Resultados de Búsqueda", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    if (filteredPages.isEmpty()) {
                        Text("No se encontraron ajustes.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                    } else {
                        filteredPages.forEach { page ->
                            SettingCard(page = page, onClick = { activePageId = page.id })
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            } else {
                ScrollableTabRow(
                    selectedTabIndex = selectedCategoryIndex,
                    containerColor = DarkSurface,
                    contentColor = NeonEmerald,
                    edgePadding = 12.dp,
                    indicator = { tabPositions ->
                        if (selectedCategoryIndex < tabPositions.size) {
                            TabRowDefaults.Indicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedCategoryIndex]),
                                color = NeonEmerald
                            )
                        }
                    }
                ) {
                    categories.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedCategoryIndex == index,
                            onClick = { selectedCategoryIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    color = if (selectedCategoryIndex == index) NeonEmerald else TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                val currentCategory = categories[selectedCategoryIndex]
                val categoryPages = allPages.filter { it.category == currentCategory }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    categoryPages.forEach { page ->
                        SettingCard(page = page, onClick = { activePageId = page.id })
                    }
                }
            }
        }
    }
}

@Composable
fun SettingCard(page: SettingPage, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(page.title, color = NeonEmerald, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(page.category, color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    page.tags.take(3).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(DarkBackground, RoundedCornerShape(4.dp))
                                .border(1.dp, DarkBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("#$tag", color = TextMuted, fontSize = 9.sp)
                        }
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Abrir", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun AgentPersonalitySettings(viewModel: ClawViewModel) {
    val jarvisModfile by viewModel.jarvisModfile.collectAsState()
    val selectedModel = when {
        jarvisModfile.baseModel.contains("Lite") -> "Lite"
        jarvisModfile.baseModel.contains("Pro") -> "Pro"
        else -> "Standard"
    }
    val isCasual = jarvisModfile.temperature > 0.8f
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ConfigSection("AJUSTES DE MODELO") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ModelOption("Lite", selectedModel == "Lite") { 
                    viewModel.updateJarvisModfile(jarvisModfile.agentName, "CLAW Lite (Qwen 3.5)", jarvisModfile.systemPrompt, jarvisModfile.temperature, jarvisModfile.topP, jarvisModfile.wakeWord, jarvisModfile.enableVoiceTTS)
                }
                ModelOption("Standard", selectedModel == "Standard") { 
                    viewModel.updateJarvisModfile(jarvisModfile.agentName, "CLAW Standard (Qwen 3.5 4-bit LiteRT)", jarvisModfile.systemPrompt, jarvisModfile.temperature, jarvisModfile.topP, jarvisModfile.wakeWord, jarvisModfile.enableVoiceTTS)
                }
                ModelOption("Pro", selectedModel == "Pro") { 
                    viewModel.updateJarvisModfile(jarvisModfile.agentName, "CLAW Pro (Gemma 7B)", jarvisModfile.systemPrompt, jarvisModfile.temperature, jarvisModfile.topP, jarvisModfile.wakeWord, jarvisModfile.enableVoiceTTS)
                }
            }
        }
        
        ConfigSection("PERSONALIDAD Y SINTETIZADOR DE VOZ") {
            ConfigSwitchRow("Modo Casual", isCasual) { 
                val newTemp = if (it) 1.2f else 0.7f
                viewModel.updateJarvisModfile(
                    jarvisModfile.agentName, jarvisModfile.baseModel, jarvisModfile.systemPrompt, newTemp, jarvisModfile.topP, jarvisModfile.wakeWord, jarvisModfile.enableVoiceTTS, jarvisModfile.voiceStyle, jarvisModfile.pitchMultiplier, jarvisModfile.speechRateMultiplier
                )
            }
            ConfigSwitchRow("Respuestas por Voz (TTS)", jarvisModfile.enableVoiceTTS) { 
                viewModel.updateJarvisModfile(
                    jarvisModfile.agentName, jarvisModfile.baseModel, jarvisModfile.systemPrompt, jarvisModfile.temperature, jarvisModfile.topP, jarvisModfile.wakeWord, it, jarvisModfile.voiceStyle, jarvisModfile.pitchMultiplier, jarvisModfile.speechRateMultiplier
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Text("Estilo y Timbre de Voz", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ModelOption("Voz 1", jarvisModfile.voiceStyle == "Voz 1") { 
                    viewModel.updateJarvisModfile(jarvisModfile.agentName, jarvisModfile.baseModel, jarvisModfile.systemPrompt, jarvisModfile.temperature, jarvisModfile.topP, jarvisModfile.wakeWord, jarvisModfile.enableVoiceTTS, "Voz 1", jarvisModfile.pitchMultiplier, jarvisModfile.speechRateMultiplier)
                }
                ModelOption("Voz 2", jarvisModfile.voiceStyle == "Voz 2") { 
                    viewModel.updateJarvisModfile(jarvisModfile.agentName, jarvisModfile.baseModel, jarvisModfile.systemPrompt, jarvisModfile.temperature, jarvisModfile.topP, jarvisModfile.wakeWord, jarvisModfile.enableVoiceTTS, "Voz 2", jarvisModfile.pitchMultiplier, jarvisModfile.speechRateMultiplier)
                }
                ModelOption("Robot", jarvisModfile.voiceStyle == "Robot") { 
                    viewModel.updateJarvisModfile(jarvisModfile.agentName, jarvisModfile.baseModel, jarvisModfile.systemPrompt, jarvisModfile.temperature, jarvisModfile.topP, jarvisModfile.wakeWord, jarvisModfile.enableVoiceTTS, "Robot", jarvisModfile.pitchMultiplier, jarvisModfile.speechRateMultiplier)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Text("Tono de Voz (${"%.2f".format(jarvisModfile.pitchMultiplier)}x)", color = TextSecondary, fontSize = 13.sp)
            Slider(
                value = jarvisModfile.pitchMultiplier,
                onValueChange = { newPitch ->
                    viewModel.updateJarvisModfile(jarvisModfile.agentName, jarvisModfile.baseModel, jarvisModfile.systemPrompt, jarvisModfile.temperature, jarvisModfile.topP, jarvisModfile.wakeWord, jarvisModfile.enableVoiceTTS, jarvisModfile.voiceStyle, newPitch, jarvisModfile.speechRateMultiplier)
                },
                valueRange = 0.5f..1.8f,
                colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald)
            )
            
            Text("Velocidad (${"%.2f".format(jarvisModfile.speechRateMultiplier)}x)", color = TextSecondary, fontSize = 13.sp)
            Slider(
                value = jarvisModfile.speechRateMultiplier,
                onValueChange = { newRate ->
                    viewModel.updateJarvisModfile(jarvisModfile.agentName, jarvisModfile.baseModel, jarvisModfile.systemPrompt, jarvisModfile.temperature, jarvisModfile.topP, jarvisModfile.wakeWord, jarvisModfile.enableVoiceTTS, jarvisModfile.voiceStyle, jarvisModfile.pitchMultiplier, newRate)
                },
                valueRange = 0.5f..1.8f,
                colors = SliderDefaults.colors(thumbColor = NeonTeal, activeTrackColor = NeonTeal)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.speakVoice("Hola, he actualizado mi voz a ${jarvisModfile.voiceStyle}", jarvisModfile.voiceStyle, jarvisModfile.pitchMultiplier, jarvisModfile.speechRateMultiplier)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🔊 Probar Sintetizador", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SecurityTelemetrySettings(viewModel: ClawViewModel) {
    val ollamaConfig by viewModel.ollamaConfig.collectAsState()
    var biometricLock by remember { mutableStateOf(false) }
    var memoryCleanup by remember { mutableStateOf(true) }
    var telemetryHz by remember { mutableStateOf(5f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ConfigSection("SEGURIDAD") {
            ConfigSwitchRow("Procesamiento Local", ollamaConfig.useOnDeviceLiteRTFallback) { 
                viewModel.updateOllamaConfig(ollamaConfig.hostUrl, ollamaConfig.modelName, ollamaConfig.openClawWebhookUrl, it)
            }
            ConfigSwitchRow("Bloqueo Biométrico", biometricLock) { biometricLock = it }
            ConfigSwitchRow("Limpieza de Memoria", memoryCleanup) { memoryCleanup = it }
        }
        
        ConfigSection("TELEMETRÍA") {
            Text("Frecuencia de actualización (${telemetryHz.toInt()} Hz)", color = TextPrimary, fontSize = 14.sp)
            Slider(
                value = telemetryHz,
                onValueChange = { telemetryHz = it },
                valueRange = 1f..60f,
                colors = SliderDefaults.colors(thumbColor = NeonTeal, activeTrackColor = NeonTeal)
            )
        }
    }
}

@Composable
fun ConfigSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(16.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(title, color = NeonEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
fun ConfigSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextPrimary, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = NeonEmerald, checkedTrackColor = NeonEmerald.copy(alpha = 0.3f))
        )
    }
}

@Composable
fun ModelOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (selected) NeonEmerald.copy(alpha = 0.2f) else DarkSurface
    val borderColor = if (selected) NeonEmerald else DarkBorder
    val textColor = if (selected) NeonEmerald else TextMuted
    
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor),
        onClick = onClick,
        modifier = Modifier.padding(4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun DynamicThemeConfigSection(viewModel: ClawViewModel) {
    val jarvisModfile by viewModel.jarvisModfile.collectAsState()
    val hardwareTelemetry by viewModel.hardwareTelemetry.collectAsState()
    val palette = LocalDynamicThemePalette.current

    ConfigSection("MOTOR DINÁMICO DE TEMAS (3-FASE)") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tema Inteligente (Dinámico)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Switch(
                checked = jarvisModfile.useAutoDynamicTheme,
                onCheckedChange = { viewModel.setUseAutoDynamicTheme(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = palette.primary, checkedTrackColor = palette.primary.copy(alpha = 0.3f))
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        if (jarvisModfile.useAutoDynamicTheme) {
            Text("Modo Automático: La app adapta los colores según las tareas, personalidad y procesador.", color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("1. Personalidad", color = palette.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AgentPersonality.values().forEach { personality ->
                    FilterChip(
                        selected = jarvisModfile.personality == personality,
                        onClick = { viewModel.setPersonality(personality) },
                        label = { Text(personality.displayName, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.primary.copy(alpha = 0.25f), selectedLabelColor = palette.primary)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text("2. Tarea Actual", color = palette.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ModelTaskMode.values().toList().chunked(2).forEach { rowModes ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowModes.forEach { taskMode ->
                            FilterChip(
                                selected = jarvisModfile.currentTaskMode == taskMode,
                                onClick = { viewModel.setTaskMode(taskMode) },
                                label = { Text(taskMode.displayName, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.secondary.copy(alpha = 0.25f), selectedLabelColor = palette.secondary)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text("3. Estado Térmico (${"%.1f".format(hardwareTelemetry.batteryTemperatureC)}°C)", color = if (hardwareTelemetry.batteryTemperatureC >= 42f) RedError else palette.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { viewModel.setSimulatedTemperature(32.0f) }, colors = ButtonDefaults.buttonColors(containerColor = DarkBackground), border = BorderStroke(1.dp, NeonEmerald), shape = RoundedCornerShape(8.dp)) { Text("Óptimo (32°C)", fontSize = 10.sp, color = NeonEmerald) }
                Button(onClick = { viewModel.setSimulatedTemperature(38.0f) }, colors = ButtonDefaults.buttonColors(containerColor = DarkBackground), border = BorderStroke(1.dp, NeonAmber), shape = RoundedCornerShape(8.dp)) { Text("Tibio (38°C)", fontSize = 10.sp, color = NeonAmber) }
                Button(onClick = { viewModel.setSimulatedTemperature(44.0f) }, colors = ButtonDefaults.buttonColors(containerColor = DarkBackground), border = BorderStroke(1.dp, RedError), shape = RoundedCornerShape(8.dp)) { Text("Alerta (44°C)", fontSize = 10.sp, color = RedError) }
            }
        } else {
            Text("Modo Personalizado: Selecciona un color base.", color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Ajuste de Color Base", color = palette.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CustomThemeColorPreset.values().toList().chunked(2).forEach { rowPresets ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowPresets.forEach { preset ->
                            val isSelected = jarvisModfile.customThemeColorPreset == preset
                            val presetColor = Color(android.graphics.Color.parseColor(preset.hexColor))
                            Surface(
                                modifier = Modifier.weight(1f).border(1.dp, if (isSelected) presetColor else DarkBorder, RoundedCornerShape(10.dp)),
                                color = if (isSelected) presetColor.copy(alpha = 0.2f) else DarkBackground,
                                shape = RoundedCornerShape(10.dp),
                                onClick = { viewModel.setCustomThemeColorPreset(preset) }
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(18.dp).background(presetColor, RoundedCornerShape(4.dp)))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(preset.displayName, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(preset.hexColor, color = TextMuted, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Derivados generados para: ${jarvisModfile.customThemeColorPreset.displayName}", color = NeonTeal, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(12.dp)).border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Vista Previa de la Paleta Calculada", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(if (jarvisModfile.useAutoDynamicTheme) "SISTEMA AUTOMÁTICO" else "SISTEMA MANUAL", color = palette.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ColorSwatch("Principal Base", palette.primary)
                    ColorSwatch("Derivado Suave", palette.secondary)
                    ColorSwatch("Derivado Intenso", palette.tertiary)
                    ColorSwatch("Borde Neón", palette.border)
                }
            }
        }
    }
}

@Composable
fun ColorSwatch(label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(24.dp).background(color, RoundedCornerShape(6.dp)).border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 9.sp, color = TextMuted)
    }
}
