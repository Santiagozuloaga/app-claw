package com.example.clawcode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.clawcode.data.model.MemoryItem
import com.example.clawcode.data.model.McpTool
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigModfileScreen(
    viewModel: ClawViewModel,
    memories: List<MemoryItem>,
    isCompacting: Boolean,
    tools: List<McpTool>
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("CLAW Installer & Modelo", "Memoria Contextual", "Herramientas MCP")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(12.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = DarkSurface,
            contentColor = NeonEmerald,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedSubTab == index) NeonEmerald else TextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (selectedSubTab) {
                0 -> JarvisAgentDesignerScreen(viewModel = viewModel)
                1 -> MemoryScreen(
                    memories = memories,
                    isCompacting = isCompacting,
                    onCompactMemory = { viewModel.compactMemory() },
                    onAddMemory = { key, cat, value -> viewModel.addMemory(key, cat, value) }
                )
                2 -> ToolsScreen(
                    tools = tools,
                    onToggleTool = { viewModel.toggleTool(it) }
                )
            }
        }
    }
}
