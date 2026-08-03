package com.example.clawcode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.AgentTask
import com.example.clawcode.data.model.Priority
import com.example.clawcode.data.model.TaskStatus
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel

@Composable
fun TasksScreen(viewModel: ClawViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    val palette = LocalDynamicThemePalette.current

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("En Progreso", "Pendientes", "Completadas")
    
    var showAddTaskDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = palette.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTaskDialog = true },
                containerColor = palette.primary,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Tarea")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(palette.background)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = palette.background,
                contentColor = palette.primary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = palette.primary
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTab == index) palette.primary else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }
            
            val filteredTasks = tasks.filter {
                when (selectedTab) {
                    0 -> it.status == TaskStatus.RUNNING
                    1 -> it.status == TaskStatus.PENDING
                    2 -> it.status == TaskStatus.COMPLETED || it.status == TaskStatus.ERROR
                    else -> true
                }
            }
            
            if (filteredTasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay tareas en esta categoría.", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTasks) { task ->
                        TaskCard(task, palette)
                    }
                }
            }
        }
    }

    if (showAddTaskDialog) {
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            containerColor = palette.surface,
            title = { Text("Nueva Tarea Autónoma", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título de la tarea") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.border
                        )
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Instrucción / Prompt") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.border
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            viewModel.createNewTask(title, description, "JARVIS", Priority.MEDIUM)
                            showAddTaskDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) {
                    Text("Crear Tarea", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) {
                    Text("Cancelar", color = TextMuted)
                }
            }
        )
    }
}

@Composable
fun TaskCard(task: AgentTask, palette: DynamicThemePalette) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.border, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(task.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                
                val statusColor = when (task.status) {
                    TaskStatus.RUNNING -> palette.primary
                    TaskStatus.PENDING -> NeonAmber
                    TaskStatus.COMPLETED -> NeonEmerald
                    TaskStatus.ERROR -> RedError
                }
                
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val icon = when(task.status) {
                            TaskStatus.RUNNING -> Icons.Default.PlayArrow
                            TaskStatus.PENDING -> Icons.Default.HourglassEmpty
                            TaskStatus.COMPLETED -> Icons.Default.CheckCircle
                            TaskStatus.ERROR -> Icons.Default.Error
                        }
                        Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(10.dp))
                        Text(
                            text = task.status.name,
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(task.description, color = TextSecondary, fontSize = 13.sp)
            
            if (task.status == TaskStatus.RUNNING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = palette.primary,
                    trackColor = palette.primary.copy(alpha = 0.15f)
                )
            }
        }
    }
}
