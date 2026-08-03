package com.example.clawcode.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.clawcode.ui.theme.*

data class PermissionItem(
    val name: String,
    val permissionKey: String,
    val category: String,
    val description: String,
    val icon: ImageVector,
    val isSpecial: Boolean = false
)

@Composable
fun PermissionsScreen() {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableStateOf(0) }

    val runtimePermissions = listOf(
        PermissionItem("Micrófono", Manifest.permission.RECORD_AUDIO, "Interacción", "Escuchar comandos de voz y activar el asistente con palabra clave.", Icons.Default.Mic),
        PermissionItem("Cámara", Manifest.permission.CAMERA, "Interacción", "Captura de fotografías y análisis visual del entorno.", Icons.Default.CameraAlt),
        PermissionItem("Ubicación Precisa", Manifest.permission.ACCESS_FINE_LOCATION, "Entorno", "Clima local, navegación, rutas y recordatorios contextuales.", Icons.Default.LocationOn),
        PermissionItem("Notificaciones", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else "android.permission.POST_NOTIFICATIONS", "Alertas", "Avisos en segundo plano de tareas asíncronas completadas.", Icons.Default.Notifications),
        PermissionItem("Contactos", Manifest.permission.READ_CONTACTS, "Productividad", "Reconocimiento de nombres para llamadas y mensajes.", Icons.Default.Contacts),
        PermissionItem("Calendario", Manifest.permission.READ_CALENDAR, "Productividad", "Lectura y programación de eventos y recordatorios diarios.", Icons.Default.CalendarToday),
        PermissionItem("Fotos y Multimedia", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE, "Archivos", "Procesamiento de archivos y medios locales.", Icons.Default.Folder)
    )

    val specialPermissions = listOf(
        PermissionItem("Acceso a Todos los Archivos", "MANAGE_EXTERNAL_STORAGE", "Especiales", "Lectura y análisis de cualquier archivo o documento en el almacenamiento interno.", Icons.Default.SdCard, isSpecial = true),
        PermissionItem("Mostrar Sobre Otras Apps", "SYSTEM_ALERT_WINDOW", "Especiales", "Desplegar ventanas flotantes e interfaces del agente sobre cualquier app.", Icons.Default.Layers, isSpecial = true),
        PermissionItem("Ajustes de Batería Sin Restricciones", "IGNORE_BATTERY_OPTIMIZATIONS", "Segundo Plano", "Evita que Android congele o detenga al agente en segundo plano.", Icons.Default.BatteryChargingFull, isSpecial = true),
        PermissionItem("Modificar Ajustes del Sistema", "WRITE_SETTINGS", "Automatización", "Alterar brillo, perfiles de sonido o conexiones automáticamente.", Icons.Default.Settings, isSpecial = true)
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTrigger++
    }

    fun isPermissionGranted(key: String, isSpecial: Boolean): Boolean {
        if (isSpecial) {
            return when (key) {
                "MANAGE_EXTERNAL_STORAGE" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
                "SYSTEM_ALERT_WINDOW" -> Settings.canDrawOverlays(context)
                "WRITE_SETTINGS" -> Settings.System.canWrite(context)
                "IGNORE_BATTERY_OPTIMIZATIONS" -> true
                else -> false
            }
        }
        return ContextCompat.checkSelfPermission(context, key) == PackageManager.PERMISSION_GRANTED
    }

    val grantedCount = remember(refreshTrigger) {
        runtimePermissions.count { isPermissionGranted(it.permissionKey, false) } +
                specialPermissions.count { isPermissionGranted(it.permissionKey, true) }
    }
    val totalCount = runtimePermissions.size + specialPermissions.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "GESTOR DE PERMISOS",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Control total sobre las capacidades del agente autónomo",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            Surface(
                color = NeonEmerald.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald)
            ) {
                Text(
                    text = "$grantedCount / $totalCount ACTIVOS",
                    color = NeonEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Grant Button
        Button(
            onClick = {
                val keysToRequest = runtimePermissions.map { it.permissionKey }.toTypedArray()
                launcher.launch(keysToRequest)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Verified, contentDescription = null, tint = DarkBackground)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Conceder Permisos de Tiempo de Ejecución", color = DarkBackground, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text("PERMISOS DE TIEMPO DE EJECUCIÓN", color = NeonEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }

            items(runtimePermissions) { perm ->
                val granted = isPermissionGranted(perm.permissionKey, false)
                PermissionCard(
                    item = perm,
                    isGranted = granted,
                    onRequest = {
                        launcher.launch(arrayOf(perm.permissionKey))
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("PERMISOS ESPECIALES Y SEGUNDO PLANO", color = NeonTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }

            items(specialPermissions) { perm ->
                val granted = isPermissionGranted(perm.permissionKey, true)
                PermissionCard(
                    item = perm,
                    isGranted = granted,
                    onRequest = {
                        openSpecialSettings(context, perm.permissionKey)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("INTEGRACIONES DE NUBE Y NAVEGACIÓN (OAUTH2 / APIS)", color = NeonPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }

            item {
                CloudIntegrationCard(
                    title = "Notion API & Workspaces",
                    description = "Acceso a bases de datos, notas, páginas y listas de tareas de Notion mediante token seguro u OAuth2.",
                    icon = Icons.Default.Description,
                    serviceName = "Notion"
                )
            }

            item {
                CloudIntegrationCard(
                    title = "Google Workspace (Gmail & Drive)",
                    description = "Búsqueda y lectura de correos en Gmail, gestión de archivos en Google Drive y actualización de hojas de cálculo.",
                    icon = Icons.Default.Email,
                    serviceName = "Google"
                )
            }

            item {
                CloudIntegrationCard(
                    title = "GitHub API & Repositorios",
                    description = "Clonación de repositorios, creación de issues y solicitudes de extracción (Pull Requests).",
                    icon = Icons.Default.Code,
                    serviceName = "GitHub"
                )
            }
        }
    }
}

@Composable
fun PermissionCard(item: PermissionItem, isGranted: Boolean, onRequest: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isGranted) NeonEmerald.copy(alpha = 0.4f) else DarkBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isGranted) NeonEmerald.copy(alpha = 0.15f) else DarkSurfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (isGranted) NeonEmerald else TextMuted
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (item.isSpecial) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("[ESPECIAL]", color = NeonAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(item.description, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(
                onClick = onRequest,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isGranted) NeonEmerald else RedError
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isGranted) "ACTIVO" else "ACTIVAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

fun openSpecialSettings(context: Context, key: String) {
    try {
        val intent = when (key) {
            "MANAGE_EXTERNAL_STORAGE" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            "SYSTEM_ALERT_WINDOW" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            "WRITE_SETTINGS" -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
            "IGNORE_BATTERY_OPTIMIZATIONS" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        context.startActivity(fallback)
    }
}

@Composable
fun CloudIntegrationCard(title: String, description: String, icon: ImageVector, serviceName: String) {
    var isConnected by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isConnected) NeonPurple.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(NeonPurple.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NeonPurple
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("[OAUTH2]", color = NeonTeal, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(
                onClick = { isConnected = !isConnected },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isConnected) NeonEmerald else NeonPurple
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.VpnKey else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isConnected) "AUTORIZADO" else "CONECTAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
