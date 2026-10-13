package com.example.clawcode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.data.model.MemoryItem
import com.example.clawcode.ui.components.MemoryItemCard
import com.example.clawcode.ui.theme.*

@Composable
fun MemoryScreen(
    memories: List<MemoryItem>,
    isCompacting: Boolean,
    onCompactMemory: () -> Unit,
    onAddMemory: (String, String, String) -> Unit
) {
    val palette = LocalDynamicThemePalette.current
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredMemories = remember(memories, selectedCategory) {
        if (selectedCategory == null) memories else memories.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.border, RoundedCornerShape(16.dp)),
                color = palette.surface,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = palette.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "BÓVEDA DE MEMORIA",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                // tokenCount es value.length * 4 (ClawEngine.addMemory): ninguna
                                // tokenización real detrás. Se muestra el número guardado rotulado como
                                // estimación, junto con el conteo de caracteres que sí es medido.
                                text = "${memories.sumOf { it.value.length }} caracteres guardados • " +
                                        "'${memories.sumOf { it.tokenCount }} tokens' es estimación por caracteres (×4), no tokens medidos",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                        IconButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.primary.copy(alpha = 0.15f))
                                .border(1.dp, palette.primary, RoundedCornerShape(10.dp))
                                .size(36.dp)
                                .testTag("btn_add_memory")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Añadir Memoria", tint = palette.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onCompactMemory,
                        enabled = !isCompacting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.secondary.copy(alpha = 0.2f),
                            contentColor = palette.secondary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, palette.secondary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_compact_memory")
                    ) {
                        if (isCompacting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = palette.secondary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // compactMemory() ya no multiplica nada: declara que no hay backend.
                            // ningún texto se resume ni se elimina, así que el rótulo no puede prometerlo.
                            Text("Sin backend de resumen: ningún texto se compacta...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            Icon(imageVector = Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compactar: sin modelo de resumen, no libera nada", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Category Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("Todo (${memories.size})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.primary.copy(alpha = 0.2f),
                        selectedLabelColor = palette.primary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedCategory == null,
                        borderColor = palette.border,
                        selectedBorderColor = palette.primary
                    )
                )
                listOf("Core", "Context", "Skill", "Session").forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.primary.copy(alpha = 0.2f),
                            selectedLabelColor = palette.primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategory == cat,
                            borderColor = palette.border,
                            selectedBorderColor = palette.primary
                        )
                    )
                }
            }

            // Memory List
            if (filteredMemories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bóveda vacía para esta categoría.", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredMemories) { item ->
                        MemoryItemCard(item = item)
                    }
                }
            }
        }

        if (showAddDialog) {
            AddMemoryDialog(
                palette = palette,
                onDismiss = { showAddDialog = false },
                onConfirm = { key, category, value ->
                    onAddMemory(key, category, value)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun AddMemoryDialog(
    palette: DynamicThemePalette,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Context") }
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Text(
                text = "Nueva Llave de Contexto",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Identificador de Llave", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("input_mem_key"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.border
                    )
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Categoría", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("input_mem_cat"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.border
                    )
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Valor", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("input_mem_val"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.border
                    ),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (key.isNotBlank() && value.isNotBlank()) {
                        onConfirm(key, category, value)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                modifier = Modifier.testTag("btn_save_memory")
            ) {
                Text("Guardar", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextMuted)
            }
        }
    )
}
