package com.example.clawcode.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.example.clawcode.data.model.MobileActionCategory
import com.example.clawcode.data.model.MobileActionDef
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileActionsScreen(viewModel: ClawViewModel) {
    val context = LocalContext.current
    var userPrompt by remember { mutableStateOf("Redacta un correo a carlos@empresa.com para coordinar la reunión de proyecto") }
    var parsedAction by remember { mutableStateOf<Pair<String, Map<String, String>>?>(null) }
    var lastExecutionResult by remember { mutableStateOf("") }

    val catalog = remember { viewModel.mobileActionEngine.getActionCatalog() }

    LaunchedEffect(userPrompt) {
        if (userPrompt.isNotBlank()) {
            parsedAction = viewModel.mobileActionEngine.parseNaturalLanguageToFunctionCall(userPrompt)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.FlashOn,
                contentDescription = null,
                tint = NeonEmerald,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Google Edge Mobile Actions",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Function Calling para Gmail, Notion, WhatsApp, Calendar, Maps",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Natural Language Tester Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NeonTeal.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = NeonTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Intérprete de Lenguaje Natural (FunctionGemma / Qwen 3.5)",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    placeholder = { Text("Escribe un comando (ej: Abre Notion en proyectos...)", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(10.dp))

                parsedAction?.let { (actionName, params) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CodeBackground),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Parsed Function Call JSON:",
                                color = NeonAmber,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "{\n  \"name\": \"$actionName\",\n  \"parameters\": $params\n}",
                                color = NeonEmerald,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val (success, logText) = viewModel.executeMobileAction(actionName, params)
                            lastExecutionResult = logText
                            Toast.makeText(context, logText, Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = DarkBackground,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ejecutar Acción Nativa en Android", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }

                if (lastExecutionResult.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Resultado: $lastExecutionResult",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Apps Direct Launcher Grid/List
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NeonEmerald.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lanzador de Aplicaciones del Sistema",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "${viewModel.getInstalledApps().size} Apps",
                        color = NeonEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val installedApps = remember { viewModel.getInstalledApps() }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(installedApps) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBackground, RoundedCornerShape(6.dp))
                                .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when {
                                        app.packageName.contains("gm") || app.appName.contains("Gmail") -> Icons.Default.Email
                                        app.packageName.contains("notion") -> Icons.Default.Description
                                        app.packageName.contains("whatsapp") -> Icons.Default.Chat
                                        app.packageName.contains("spotify") -> Icons.Default.MusicNote
                                        app.packageName.contains("youtube") -> Icons.Default.PlayCircle
                                        app.packageName.contains("camera") -> Icons.Default.CameraAlt
                                        app.packageName.contains("chrome") -> Icons.Default.Language
                                        else -> Icons.Default.Android
                                    },
                                    contentDescription = null,
                                    tint = NeonTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = app.appName,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = app.packageName,
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val (success, message) = viewModel.launchAppByName(app.appName)
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald.copy(alpha = 0.2f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Abrir", color = NeonEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Catálogo de Acciones Disponibles",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(catalog) { action ->
                ActionCard(action = action, onTrigger = {
                    userPrompt = action.sampleUtterance
                })
            }
        }
    }
}

@Composable
private fun ActionCard(action: MobileActionDef, onTrigger: () -> Unit) {
    val categoryColor = when (action.category) {
        MobileActionCategory.EMAIL -> NeonTeal
        MobileActionCategory.PRODUCTIVITY -> NeonPurple
        MobileActionCategory.MESSAGING -> NeonEmerald
        MobileActionCategory.CALENDAR -> NeonAmber
        MobileActionCategory.NAVIGATION -> NeonPink
        MobileActionCategory.CONTACTS -> NeonTeal
        MobileActionCategory.SYSTEM -> TextSecondary
        MobileActionCategory.APP_LAUNCHER -> NeonEmerald
        MobileActionCategory.MULTIMEDIA -> NeonPink
        MobileActionCategory.AUTOMATION -> NeonTeal
    }

    val iconVector = when (action.category) {
        MobileActionCategory.EMAIL -> Icons.Default.Email
        MobileActionCategory.PRODUCTIVITY -> Icons.Default.Description
        MobileActionCategory.MESSAGING -> Icons.Default.Chat
        MobileActionCategory.CALENDAR -> Icons.Default.Event
        MobileActionCategory.NAVIGATION -> Icons.Default.Map
        MobileActionCategory.CONTACTS -> Icons.Default.Contacts
        MobileActionCategory.SYSTEM -> Icons.Default.Settings
        MobileActionCategory.APP_LAUNCHER -> Icons.Default.Apps
        MobileActionCategory.MULTIMEDIA -> Icons.Default.MusicNote
        MobileActionCategory.AUTOMATION -> Icons.Default.SmartToy
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = action.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    color = categoryColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = action.category.name,
                        color = categoryColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = action.description,
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Ejemplo: \"${action.sampleUtterance}\"",
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onTrigger,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonTeal),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(NeonTeal.copy(alpha = 0.5f))),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Probar Prompt", fontSize = 11.sp)
            }
        }
    }
}
