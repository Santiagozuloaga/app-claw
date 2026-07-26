package com.example.clawcode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.AgentTask
import com.example.clawcode.data.model.Priority
import com.example.clawcode.data.model.TaskStatus
import com.example.clawcode.ui.components.AgentExecutionProgressBar
import com.example.clawcode.ui.components.TaskItemCard
import com.example.clawcode.ui.theme.*

@Composable
fun TasksScreen(
    tasks: List<AgentTask>,
    onCreateTask: (String, String, String, Priority) -> Unit,
    onRunTask: (String) -> Unit,
    onCompleteTask: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf<TaskStatus?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedTaskForDetails by remember { mutableStateOf<AgentTask?>(null) }

    val filteredTasks = remember(tasks, selectedFilter) {
        if (selectedFilter == null) tasks else tasks.filter { it.status == selectedFilter }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header & Filter Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gestor de Tareas & Agentes",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonEmerald)
                        .size(38.dp)
                        .testTag("btn_add_task")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Task",
                        tint = DarkBackground
                    )
                }
            }

            // Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("Todas (${tasks.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonTeal,
                        selectedLabelColor = DarkBackground
                    )
                )

                FilterChip(
                    selected = selectedFilter == TaskStatus.RUNNING,
                    onClick = { selectedFilter = TaskStatus.RUNNING },
                    label = { Text("En Ejecución") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GreenRunning,
                        selectedLabelColor = DarkBackground
                    )
                )

                FilterChip(
                    selected = selectedFilter == TaskStatus.PENDING,
                    onClick = { selectedFilter = TaskStatus.PENDING },
                    label = { Text("Pendientes") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BluePending,
                        selectedLabelColor = DarkBackground
                    )
                )

                FilterChip(
                    selected = selectedFilter == TaskStatus.COMPLETED,
                    onClick = { selectedFilter = TaskStatus.COMPLETED },
                    label = { Text("Completadas") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonEmerald,
                        selectedLabelColor = DarkBackground
                    )
                )
            }

            // Task List
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se encontraron tareas con este filtro",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTasks) { task ->
                        if (task.status == TaskStatus.RUNNING) {
                            AgentExecutionProgressBar(
                                task = task,
                                estimatedCostUsd = 0.0000, // 100% On-device
                                tokensPerSecond = 42.8f,
                                onCancelTask = { taskId -> onCompleteTask(taskId) }
                            )
                        } else {
                            TaskItemCard(
                                task = task,
                                onRunTask = onRunTask,
                                onCompleteTask = onCompleteTask,
                                onSelectTask = { selectedTaskForDetails = it }
                            )
                        }
                    }
                }
            }
        }

        // Create Task Dialog
        if (showCreateDialog) {
            CreateTaskDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { title, desc, agent, priority ->
                    onCreateTask(title, desc, agent, priority)
                    showCreateDialog = false
                }
            )
        }

        // Task Detail Sheet / Dialog
        selectedTaskForDetails?.let { task ->
            TaskLogsDialog(
                task = task,
                onDismiss = { selectedTaskForDetails = null }
            )
        }
    }
}

@Composable
fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Priority) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var agentName by remember { mutableStateOf("Claw_Agent_v1") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = "Queue New Agent Task",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_task_title"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Task Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_task_desc")
                )

                OutlinedTextField(
                    value = agentName,
                    onValueChange = { agentName = it },
                    label = { Text("Subagent Identifier") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_agent_name"),
                    singleLine = true
                )

                Text(
                    text = "Priority Level",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Priority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.name, fontSize = 10.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title, description, agentName, priority)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                modifier = Modifier.testTag("btn_submit_task")
            ) {
                Text("Enqueue Task", color = DarkBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
fun TaskLogsDialog(
    task: AgentTask,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Task ID: ${task.id} | Agent: ${task.agentName}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = NeonTeal
                    )
                )

                Text(
                    text = "Execution Traceback Logs:",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CodeBackground)
                        .padding(10.dp)
                ) {
                    task.logs.forEach { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = NeonEmerald
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = NeonTeal)
            }
        }
    )
}
