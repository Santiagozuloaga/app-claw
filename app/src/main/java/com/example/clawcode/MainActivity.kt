package com.example.clawcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.clawcode.ui.screens.*
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel

enum class NavigationTab(val route: String, val title: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Inicio", Icons.Default.Dashboard),
    JARVIS_AGENT("jarvis_agent", "Chat", Icons.Default.BubbleChart),
    CLAW_LIVE("claw_live", "Jarvis", Icons.Default.GraphicEq),
    TASKS_LOGS("tasks_logs", "Tareas", Icons.Default.Checklist),
    MEMORY("memory", "Memoria", Icons.Default.Memory),
    CONFIG_MODFILE("config_modfile", "Sistema", Icons.Default.Settings),
    CONSOLE("console", "Consola", Icons.Default.Terminal)
}

class MainActivity : ComponentActivity() {
    private val viewModel: ClawViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val jarvisModfile by viewModel.jarvisModfile.collectAsStateWithLifecycle()
            val hardwareTelemetry by viewModel.hardwareTelemetry.collectAsStateWithLifecycle()

            ClawTheme(
                useAutoDynamicTheme = jarvisModfile.useAutoDynamicTheme,
                customThemeColorPreset = jarvisModfile.customThemeColorPreset,
                personality = jarvisModfile.personality,
                taskMode = jarvisModfile.currentTaskMode,
                temperatureC = hardwareTelemetry.batteryTemperatureC,
                isDarkMode = jarvisModfile.isDarkMode
            ) {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: ClawViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Check launch intent tab override (e.g. from Widget or Notification)
    val initialTab = remember {
        val navExtra = activity?.intent?.getStringExtra("navigate_to")
        when (navExtra) {
            "claw_live" -> NavigationTab.CLAW_LIVE
            "jarvis_agent" -> NavigationTab.JARVIS_AGENT
            "memory" -> NavigationTab.MEMORY
            else -> NavigationTab.DASHBOARD
        }
    }

    var currentTab by remember { mutableStateOf(initialTab) }
    var tabStack by remember { mutableStateOf(listOf(initialTab)) }

    val jarvisModfile by viewModel.jarvisModfile.collectAsStateWithLifecycle()
    // El rótulo del cajón y la lista de "recientes" salen ahora del estado real del motor:
    // ollamaConfig.modelName (persistido por PrefsOllamaConfigStore) y chatMessages.
    val ollamaConfig by viewModel.ollamaConfig.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val drawerAvatarIcon = remember(jarvisModfile.selectedAvatarId) {
        when (jarvisModfile.selectedAvatarId) {
            1 -> Icons.Default.SmartToy
            2 -> Icons.Default.Psychology
            3 -> Icons.Default.SupportAgent
            4 -> Icons.Default.Memory
            5 -> Icons.Default.Face
            6 -> Icons.Default.AutoAwesome
            else -> Icons.Default.Terminal
        }
    }

    fun navigateTo(tab: NavigationTab) {
        if (currentTab != tab) {
            currentTab = tab
            tabStack = tabStack + tab
        }
        coroutineScope.launch { drawerState.close() }
    }

    // Android System Back Button Handling (<) - Minimizes app like Claude instead of closing process
    androidx.activity.compose.BackHandler(enabled = true) {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else if (tabStack.size > 1 && currentTab != NavigationTab.DASHBOARD) {
            val newStack = tabStack.dropLast(1)
            tabStack = newStack
            currentTab = newStack.last()
        } else {
            // Move task to back (minimize app) so process remains alive in background
            activity?.moveTaskToBack(true)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkSurface,
                drawerContentColor = TextPrimary,
                modifier = Modifier.width(310.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Drawer Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NeonEmerald.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald)
                        ) {
                            Icon(
                                imageVector = drawerAvatarIcon,
                                contentDescription = "Avatar",
                                tint = NeonEmerald,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CLAW ASSISTANT",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                // "Gemini & Qwen 3.5 Local" prometía pesos locales que ninguna ruta
                                // carga: el nombre mostrado es ahora el modelo HTTP realmente configurado.
                                text = "v2.5 • runtime HTTP: ${ollamaConfig.modelName}",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                            )
                        }
                    }

                    Divider(color = DarkBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // "+ Nuevo Chat" Button (Gemini / Claude Style)
                    Button(
                        onClick = {
                            navigateTo(NavigationTab.JARVIS_AGENT)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Nuevo Chat", tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Nuevo Chat", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Historial real del chat. Esta app tiene UN solo hilo (engine.chatMessages):
                    // antes la lista traía 4 títulos inventados y pulsarlos no cargaba nada, así que
                    // el rótulo deja de prometer "conversaciones" y los títulos salen de los mensajes.
                    Text(
                        text = "💬 ÚLTIMOS MENSAJES ENVIADOS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )

                    val recentChats = chatMessages
                        .filter { it.sender == "USER" }
                        .takeLast(4)
                        .reversed()
                        .map { it.text.replace("\n", " ").take(46) }

                    if (recentChats.isEmpty()) {
                        Text(
                            text = "Sin mensajes en el historial todavía.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    recentChats.forEach { chatTitle ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = chatTitle,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = TextSecondary
                                )
                            },
                            icon = { Icon(imageVector = Icons.Default.ChatBubbleOutline, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                            selected = false,
                            onClick = {
                                // Abre el único chat que existe; no "carga una conversación" por título.
                                navigateTo(NavigationTab.JARVIS_AGENT)
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.height(40.dp)
                        )
                    }

                    Divider(color = DarkBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                    // Secciones y Herramientas del Sistema
                    Text(
                        text = "🧭 SECCIONES Y HERRAMIENTAS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )

                    NavigationTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) NeonEmerald else TextPrimary
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (isSelected) NeonEmerald else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            selected = isSelected,
                            onClick = { navigateTo(tab) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = NeonEmerald.copy(alpha = 0.15f),
                                unselectedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.height(44.dp)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBackground,
            topBar = {
                ClawTopAppBar(
                    activeTab = currentTab,
                    viewModel = viewModel,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onNavigateToTab = { navigateTo(it) }
                )
            },
            bottomBar = {
                ClawBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { navigateTo(it) }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    NavigationTab.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToChat = { navigateTo(NavigationTab.JARVIS_AGENT) },
                        onNavigateToLive = { navigateTo(NavigationTab.CLAW_LIVE) },
                        onNavigateToConfig = { navigateTo(NavigationTab.CONFIG_MODFILE) },
                        onNavigateToConsole = { navigateTo(NavigationTab.CONSOLE) }
                    )
                    NavigationTab.JARVIS_AGENT -> JarvisChatScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navigateTo(NavigationTab.DASHBOARD) },
                        onNavigateToLive = { navigateTo(NavigationTab.CLAW_LIVE) }
                    )
                    NavigationTab.CLAW_LIVE -> ClawLiveScreen(
                        viewModel = viewModel,
                        onNavigateToChat = { navigateTo(NavigationTab.JARVIS_AGENT) },
                        onNavigateBack = { navigateTo(NavigationTab.DASHBOARD) }
                    )
                    NavigationTab.TASKS_LOGS -> TasksScreen(
                        viewModel = viewModel
                    )
                    NavigationTab.MEMORY -> {
                        val memories by viewModel.memories.collectAsStateWithLifecycle()
                        val isCompacting by viewModel.isCompacting.collectAsStateWithLifecycle()
                        MemoryScreen(
                            memories = memories,
                            isCompacting = isCompacting,
                            onCompactMemory = { viewModel.compactMemory() },
                            onAddMemory = { k, c, v -> viewModel.addMemory(k, c, v) }
                        )
                    }
                    NavigationTab.CONFIG_MODFILE -> ConfigModfileScreen(
                        viewModel = viewModel
                    )
                    NavigationTab.CONSOLE -> UnifiedConsoleScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun ClawTopAppBar(
    activeTab: NavigationTab,
    viewModel: ClawViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToTab: (NavigationTab) -> Unit
) {
    val palette = LocalDynamicThemePalette.current
    val jarvisModfile by viewModel.jarvisModfile.collectAsStateWithLifecycle()
    val activeGrounding by viewModel.activeGrounding.collectAsStateWithLifecycle()
    val ollamaConfig by viewModel.ollamaConfig.collectAsStateWithLifecycle()
    
    // La bandera de procesamiento local es una preferencia sin runtime detrás: mostrarla como
    // "SAFE MODE (Local)" vendía una ejecución en el dispositivo que no ocurre. El color ámbar se
    // conserva: sigue señalando el estado que requiere atención.
    val systemState = if (ollamaConfig.useOnDeviceLiteRTFallback) "PREFERENCIA LOCAL SIN RUNTIME" else "RUNTIME HTTP"
    val isSafeMode = ollamaConfig.useOnDeviceLiteRTFallback

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = palette.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.border)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Menu Hamburger Button & Logo
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Abrir Menú Principal",
                                tint = palette.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CLAW",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = palette.primary,
                                letterSpacing = 2.sp
                            )
                        )
                    }

                    // Middle: Indicators (System Status, Safe Mode, Grounding)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // System Status
                        Box(
                            modifier = Modifier
                                .background(DarkBackground, RoundedCornerShape(6.dp))
                                .border(1.dp, if (isSafeMode) WarningAmber else NeonSecondary, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = systemState,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSafeMode) WarningAmber else NeonSecondary
                            )
                        }

                        // Grounding Status
                        if (activeGrounding != null) {
                            Box(
                                modifier = Modifier
                                    .background(DarkBackground, RoundedCornerShape(6.dp))
                                    .border(1.dp, NeonPrimary, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "GROUNDING ACTIVO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonPrimary
                                )
                            }
                        }
                    }

                    // Right: Console & Config Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onNavigateToTab(NavigationTab.CONSOLE) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Consola de Diagnóstico",
                                tint = if (activeTab == NavigationTab.CONSOLE) NeonEmerald else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = { onNavigateToTab(NavigationTab.CONFIG_MODFILE) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configuración",
                                tint = if (activeTab == NavigationTab.CONFIG_MODFILE) NeonEmerald else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ClawBottomBar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit
) {
    NavigationBar(
        containerColor = DarkSurface,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .border(1.dp, DarkBorder)
    ) {
        val bottomTabs = listOf(
            NavigationTab.DASHBOARD,
            NavigationTab.JARVIS_AGENT,
            NavigationTab.CLAW_LIVE,
            NavigationTab.TASKS_LOGS,
            NavigationTab.MEMORY
        )
        bottomTabs.forEach { tab ->
            val isSelected = currentTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title
                    )
                },
                label = {
                    Text(
                        text = tab.title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DarkBackground,
                    selectedTextColor = NeonEmerald,
                    indicatorColor = NeonEmerald,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                ),
                modifier = Modifier.testTag("nav_tab_${tab.route}")
            )
        }
    }
}
