package com.example.clawcode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.core.storage.DiskSpaceManager
import com.example.clawcode.core.storage.StorageOverview
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel

@Composable
fun DiskSpaceManagerScreen(viewModel: ClawViewModel) {
    val context = LocalContext.current
    val diskSpaceManager = remember { DiskSpaceManager(context) }
    var storageOverview by remember { mutableStateOf(diskSpaceManager.getStorageOverview()) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    fun refreshStorage() {
        storageOverview = diskSpaceManager.getStorageOverview()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Gestor de Almacenamiento y Modelos",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Optimiza el espacio utilizado por modelos local GGUF en tu dispositivo",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }

            IconButton(onClick = { refreshStorage() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Recargar", tint = NeonEmerald)
            }
        }

        actionMessage?.let { msg ->
            Surface(
                color = NeonEmerald.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = msg,
                    color = NeonEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Vivo V25 & Storage Banner Notice
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = "Info", tint = NeonSecondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Información de Peso y Datos (Vivo V25 / Android)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonSecondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    // "~25 MB" era un literal que nadie leía del APK, y LiteRT/Llama no corren en esta
                    // build: sólo se listan archivos GGUF descargados, y eso sí se mide en disco.
                    text = "El tamaño del APK no se mide en esta pantalla: no hay lectura del paquete que lo respalde, así que se muestra sin valor. Lo que sí se mide son las carpetas de esta app (modelos, caché y datos internos), listadas abajo con su peso real; puedes eliminar modelos no utilizados para recuperar espacio.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        // Main Storage Breakdown Card
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Resumen de Memoria en Disco", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                    Text(
                        if (storageOverview.freeDeviceStorageGb >= 0f)
                            "${String.format("%.1f", storageOverview.freeDeviceStorageGb)} GB libres en el dispositivo"
                        else "Almacenamiento libre sin medir",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                // Antes el gestor caía en 16.4/128.0 GB "típicos" si StatFs fallaba y aquí se
                // mostraban como medidos. Ahora -1 significa sin medir y se dice tal cual.
                if (storageOverview.totalDeviceStorageGb < 0f) {
                    Text(
                        text = "StatFs no devolvió la capacidad del dispositivo: las cifras de GB no " +
                                "están disponibles. Los MB de Modelos/Caché/Datos sí son suma real de archivos.",
                        fontSize = 10.sp,
                        color = WarningAmber,
                        lineHeight = 14.sp
                    )
                }

                // Storage Progress Bar
                // Antes: (totalUsedMb / 5000f).coerceIn(0.05f, 1f). 5000 MB era una capacidad inventada
                // y el suelo 0.05 pintaba uso aunque la carpeta estuviera vacía. Ahora la fracción real
                // de uso de la app sobre la capacidad que StatFs declara (totalDeviceStorageGb).
                val totalUsedMb = storageOverview.modelsStorageMb + storageOverview.cacheStorageMb + storageOverview.internalDataStorageMb
                val capacidadDeclaradaMb = storageOverview.totalDeviceStorageGb * 1024f
                val fraccionDeUso = if (capacidadDeclaradaMb > 0f) {
                    (totalUsedMb / capacidadDeclaradaMb).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(
                    progress = fraccionDeUso,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = NeonEmerald,
                    trackColor = DarkBackground
                )
                Text(
                    text = if (storageOverview.totalDeviceStorageGb >= 0f)
                        "Uso de la app (${String.format("%.1f", totalUsedMb)} MB) sobre los " +
                            "${String.format("%.1f", storageOverview.totalDeviceStorageGb)} GB declarados por StatFs."
                    else
                        "Uso de la app: ${String.format("%.1f", totalUsedMb)} MB. Capacidad total del " +
                            "dispositivo sin medir, así que el porcentaje no está disponible.",
                    fontSize = 10.sp,
                    color = TextMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StorageMetricBadge(
                        label = "Modelos GGUF",
                        value = "${String.format("%.1f", storageOverview.modelsStorageMb)} MB",
                        color = NeonEmerald
                    )
                    StorageMetricBadge(
                        label = "Caché Temporal",
                        value = "${String.format("%.1f", storageOverview.cacheStorageMb)} MB",
                        color = WarningAmber
                    )
                    StorageMetricBadge(
                        label = "Datos Internos",
                        value = "${String.format("%.1f", storageOverview.internalDataStorageMb)} MB",
                        color = NeonSecondary
                    )
                }

                Divider(color = DarkBorder)

                // Quick Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val success = diskSpaceManager.clearCache()
                            refreshStorage()
                            actionMessage = if (success) "✓ Caché temporal eliminada exitosamente." else "Error al limpiar caché."
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = "Limpiar", tint = DarkBackground, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Limpiar Caché", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { refreshStorage() },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = "Escanear", tint = NeonEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Escanear Disco", color = NeonEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Installed Model Files Section
        Text(
            text = "Archivos de Modelos Locales (${storageOverview.modelItems.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        if (storageOverview.modelItems.isEmpty()) {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ModelTraining, contentDescription = "No models", tint = TextMuted, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No hay modelos pesados descargados localmente.", color = TextMuted, fontSize = 12.sp)
                    Text("El APK no trae pesos embebidos: los turnos salen al runtime HTTP configurado, y en esta build no hay modelo local cargado en el dispositivo.", color = TextSecondary, fontSize = 11.sp)
                }
            }
        } else {
            storageOverview.modelItems.forEach { modelItem ->
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.SdStorage, contentDescription = "GGUF", tint = NeonEmerald, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(modelItem.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("${String.format("%.1f", modelItem.sizeMb)} MB • Modificado: ${modelItem.lastModified}", fontSize = 10.sp, color = TextMuted)
                            }
                        }

                        IconButton(
                            onClick = {
                                val success = diskSpaceManager.deleteModel(modelItem.id)
                                refreshStorage()
                                actionMessage = if (success) "✓ Modelo '${modelItem.name}' eliminado." else "Error al eliminar modelo."
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = RedError)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun StorageMetricBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}
