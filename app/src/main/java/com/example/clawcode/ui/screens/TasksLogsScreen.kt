package com.example.clawcode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.clawcode.data.model.AgentTask
import com.example.clawcode.data.model.ConsoleLog
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel

@Composable
fun TasksLogsScreen(
    viewModel: ClawViewModel,
    tasks: List<AgentTask>,
    consoleLogs: List<ConsoleLog>
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tareas Asíncronas", "Puente WhatsApp", "Consola CLI")

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
                0 -> TasksScreen(
                    tasks = tasks,
                    onCreateTask = { title, desc, agent, priority ->
                        viewModel.createNewTask(title, desc, agent, priority)
                    },
                    onRunTask = { viewModel.runTask(it) },
                    onCompleteTask = { viewModel.completeTask(it) }
                )
                1 -> OpenClawOllamaScreen(viewModel = viewModel)
                2 -> TerminalScreen(
                    consoleLogs = consoleLogs,
                    onSendPrompt = { viewModel.sendConsolePrompt(it) }
                )
            }
        }
    }
}
