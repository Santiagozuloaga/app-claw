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

    // Estado REAL de una herramienta: `verificarEstadosApps()` sólo sondea paquetes instalados y si
    // el token salió del placeholder. Cualquier herramienta sin entrada aquí devuelve `null`, que se
    // lee como "sin verificar"; antes el `else` afirmaba "Verificada y Activa" sin ninguna sonda.
    fun estadoHerramienta(tool: McpTool): String? {
        val estados = appConnector.verificarEstadosApps()
        return when {
            tool.name.contains("WhatsApp", ignoreCase = true) -> estados["WhatsApp"]
            tool.name.contains("Notion", ignoreCase = true) -> estados["Notion"]
            tool.name.contains("GitHub", ignoreCase = true) -> estados["GitHub"]
            tool.name.contains("Gmail", ignoreCase = true) || tool.name.contains("Drive", ignoreCase = true) -> estados["Google Workspace"]
            tool.name.contains("Edge", ignoreCase = true) -> estados["AI Edge (Gemini Nano)"]
            else -> null
        }
    }

    // Un interruptor activado no es una conexión. Sólo cuenta lo que la sonda anterior confirmó.
    val verificadasPositivas = remember(tools) {
        tools.count { tool ->
            val estado = estadoHerramienta(tool)
            estado != null && (estado == "Verificada (Instalada)" || estado == "Token propio configurado")
        }
    }
    val interruptoresActivados = tools.count { it.isEnabled }

    if (verifyingTool != null) {
        val tool = verifyingTool!!
        val estadoActual = estadoHerramienta(tool)
            ?: "Sin verificar: ${tool.name} no tiene ninguna comprobación en esta build"

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
                    // Verde sólo cuando la sonda confirmó; ámbar para todo lo demás (incl. sin medir).
                    Text(
                        estadoActual,
                        color = if (estadoActual == "Verificada (Instalada)" || estadoActual == "Token propio configurado") NeonTeal else NeonAmber,
                        fontWeight = FontWeight.Bold
                    )
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
                    // El interruptor es sólo una preferencia guardada: no afirma conexiones.
                    text = "$verificadasPositivas de ${tools.size} con estado verificado por la app " +
                            "(el resto: sin verificar) · $interruptoresActivados interruptores activados (preferencia guardada)",
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
