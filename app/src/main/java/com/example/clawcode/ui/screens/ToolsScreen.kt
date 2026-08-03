package com.example.clawcode.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.clawcode.AppConnectorManager
import com.example.clawcode.data.model.McpTool
import com.example.clawcode.ui.components.ToolItemCard
import com.example.clawcode.ui.theme.*

@Composable
fun ToolsScreen(
    tools: List<McpTool>,
    onToggleTool: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var verifyingTool by remember { mutableStateOf<McpTool?>(null) }
    
    val context = LocalContext.current
    val appConnector = remember { AppConnectorManager(context) }

    val categories = remember(tools) {
        tools.map { it.category }.distinct()
    }

    val filteredTools = remember(tools, selectedCategory) {
        if (selectedCategory == null) tools else tools.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    if (verifyingTool != null) {
        val tool = verifyingTool!!
        val estados = appConnector.verificarEstadosApps()
        
        val estadoActual = when {
            tool.name.contains("WhatsApp", ignoreCase = true) -> estados["WhatsApp"] ?: "Desconocido"
            tool.name.contains("Notion", ignoreCase = true) -> estados["Notion"] ?: "Desconocido"
            tool.name.contains("GitHub", ignoreCase = true) -> estados["GitHub"] ?: "Desconocido"
            tool.name.contains("Gmail", ignoreCase = true) || tool.name.contains("Drive", ignoreCase = true) -> estados["Google Workspace"] ?: "Desconocido"
            tool.name.contains("Edge", ignoreCase = true) -> estados["AI Edge (Gemini Nano)"] ?: "Desconocido"
            else -> "Verificada y Activa (Integración Interna)"
        }

        AlertDialog(
            onDismissRequest = { verifyingTool = null },
            title = { 
                Text("Estado de Integración", fontWeight = FontWeight.Bold, color = NeonEmerald) 
            },
            text = { 
                Column {
                    Text("Herramienta: ${tool.name}", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Estado actual:", color = TextSecondary)
                    Text(estadoActual, color = if (estadoActual.contains("Falta") || estadoActual.contains("No")) NeonAmber else NeonTeal, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = { verifyingTool = null }) {
                    Text("Aceptar", color = NeonEmerald)
                }
            },
            containerColor = DarkSurface
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Herramientas MCP e Integraciones",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "${tools.count { it.isEnabled }} de ${tools.size} herramientas activas (GitHub, Notion, Gmail, Drive, Edge AI)",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
        }

        // Scrollable Filter chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("Todas (${tools.size})") }
                )
            }
            items(categories) { cat ->
                val count = tools.count { it.category.equals(cat, ignoreCase = true) }
                FilterChip(
                    selected = selectedCategory.equals(cat, ignoreCase = true),
                    onClick = { selectedCategory = cat },
                    label = { Text("$cat ($count)") }
                )
            }
        }

        // Tools List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredTools) { tool ->
                ToolItemCard(
                    tool = tool,
                    onToggle = onToggleTool,
                    onVerify = { verifyingTool = it }
                )
            }
        }
    }
}
