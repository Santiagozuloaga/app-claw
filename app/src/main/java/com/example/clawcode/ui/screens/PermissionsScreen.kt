package com.example.clawcode.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
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

    // Estado real de un permiso. `null` significa que NO es medible en esta API: la pantalla lo
    // reporta como "NO APLICA" en vez de concederlo por decreto (antes MANAGE_EXTERNAL_STORAGE
    // devolvía `true` en API < 30 e IGNORE_BATTERY_OPTIMIZATIONS devolvía `true` siempre).
    fun permissionState(key: String, isSpecial: Boolean): Boolean? {
        if (isSpecial) {
            return when (key) {
                // Sólo existe desde API 30 (R); antes no se puede conceder ni comprobar.
                "MANAGE_EXTERNAL_STORAGE" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else null
                "SYSTEM_ALERT_WINDOW" -> Settings.canDrawOverlays(context)
                "WRITE_SETTINGS" -> Settings.System.canWrite(context)
                // Pregunta real al PowerManager: ¿está el paquete exento de la optimización?
                "IGNORE_BATTERY_OPTIMIZATIONS" -> {
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                }
                else -> false
            }
        }
        return ContextCompat.checkSelfPermission(context, key) == PackageManager.PERMISSION_GRANTED
    }

    // Se mide una sola vez por refresco; los `null` (no aplicables) quedan fuera del numerador y
    // del denominador para que el contador no se infle con valores afirmados.
    val estadosPermisos = remember(refreshTrigger) {
        runtimePermissions.map { permissionState(it.permissionKey, false) } +
                specialPermissions.map { permissionState(it.permissionKey, true) }
    }
    val grantedCount = estadosPermisos.count { it == true }
    val totalCount = estadosPermisos.count { it != null }
    val noAplicables = estadosPermisos.count { it == null }

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
                    text = "Permisos leídos del sistema; las integraciones de nube no están autorizadas",
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
                    // "verificados por el sistema" = sólo los que devolvieron true/false real.
                    text = "$grantedCount / $totalCount CONCEDIDOS" +
                            (if (noAplicables > 0) " · $noAplicables NO APLICA" else ""),
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
                val granted = permissionState(perm.permissionKey, false)
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
                val granted = permissionState(perm.permissionKey, true)
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
                Text("INTEGRACIONES DE NUBE (SIN OAUTH2 NI CREDENCIALES REALES EN ESTA BUILD)", color = NeonPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }

            item {
                CloudIntegrationCard(
                    title = "Notion API & Workspaces",
                    description = "Nada autorizado: no hay OAuth2 ni token propio. AppConnectorManager.notionCrearPagina " +
                            "existe pero no se llama desde ninguna ruta y usaría el placeholder 'secret_tu_token_aqui'.",
                    icon = Icons.Default.Description,
                    serviceName = "Notion"
                )
            }

            item {
                CloudIntegrationCard(
                    title = "Google Workspace (Gmail & Drive)",
                    description = "Sin implementación: esta build no lee correos, ni archivos de Drive, ni escribe hojas de " +
                            "cálculo, y no hay ningún flujo de autorización de Google.",
                    icon = Icons.Default.Email,
                    serviceName = "Google"
                )
            }

            item {
                CloudIntegrationCard(
                    title = "GitHub API & Repositorios",
                    description = "Nada autorizado: githubVerRepositorios haría un GET con el placeholder 'ghp_tu_token_aqui' " +
                            "y no se invoca desde ninguna ruta. No se crean issues ni Pull Requests.",
                    icon = Icons.Default.Code,
                    serviceName = "GitHub"
                )
            }
        }
    }
}

@Composable
fun PermissionCard(item: PermissionItem, isGranted: Boolean?, onRequest: () -> Unit) {
    // `isGranted == null` = el permiso no existe o no es medible en esta API de Android.
    val concedido = isGranted == true
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (concedido) NeonEmerald.copy(alpha = 0.4f) else DarkBorder, RoundedCornerShape(14.dp)),
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
                    .background(if (concedido) NeonEmerald.copy(alpha = 0.15f) else DarkSurfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (concedido) NeonEmerald else TextMuted
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
                    contentColor = if (concedido) NeonEmerald else RedError
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (concedido) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        // "NO APLICA" se usa cuando el sistema no puede devolver un estado real.
                        text = when (isGranted) {
                            true -> "ACTIVO"
                            false -> "ACTIVAR"
                            null -> "NO APLICA"
                        },
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
    // Estado REAL: en esta build no existe ningún OAuth2 ni credencial propia, así que ninguna
    // integración está autorizada. El helper `esCredencialReal` de AppConnectorManager es `private`
    // (y sus tokens también), así que no se puede consultar desde aquí: se queda en `false` y el
    // botón ya no puede voltear el rótulo a "AUTORIZADO" con un clic, como hacía antes.
    val autorizada = false

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (autorizada) NeonPurple.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(14.dp)),
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
                    Text("[SIN OAUTH2]", color = NeonAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Etiqueta de estado (inmutable): sólo dice lo que se sabe, no ofrece autorizar.
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonAmber.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = NeonAmber
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (autorizada) "AUTORIZADO" else "SIN AUTORIZAR",
                        color = NeonAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
