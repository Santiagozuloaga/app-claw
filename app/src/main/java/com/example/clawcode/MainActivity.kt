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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clawcode.ui.screens.*
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel

enum class NavigationTab(val route: String, val title: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Inicio", Icons.Default.Dashboard),
    JARVIS_AGENT("jarvis_agent", "Agente JARVIS", Icons.Default.SmartToy),
    CONFIG_MODFILE("config_modfile", "Modfile & Memoria", Icons.Default.Settings),
    TASKS_LOGS("tasks_logs", "Ejecución & Logs", Icons.Default.Terminal)
}

class MainActivity : ComponentActivity() {

    private val viewModel: ClawViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ClawTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: ClawViewModel) {
    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }

    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val tools by viewModel.tools.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val hardware by viewModel.hardwareTelemetry.collectAsStateWithLifecycle()
    val consoleLogs by viewModel.consoleLogs.collectAsStateWithLifecycle()
    val isCompacting by viewModel.isCompacting.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            ClawTopAppBar(
                activeTab = currentTab,
                systemStatus = stats.systemStatus
            )
        },
        bottomBar = {
            ClawBottomBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
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
                    stats = stats,
                    hardware = hardware,
                    tasks = tasks,
                    onRunTask = { viewModel.runTask(it) },
                    onCompleteTask = { viewModel.completeTask(it) },
                    onSelectTask = { currentTab = NavigationTab.TASKS_LOGS },
                    onNavigateTerminal = { currentTab = NavigationTab.TASKS_LOGS }
                )
                NavigationTab.JARVIS_AGENT -> JarvisChatScreen(viewModel = viewModel)
                NavigationTab.CONFIG_MODFILE -> ConfigModfileScreen(
                    viewModel = viewModel,
                    memories = memories,
                    isCompacting = isCompacting,
                    tools = tools
                )
                NavigationTab.TASKS_LOGS -> TasksLogsScreen(
                    viewModel = viewModel,
                    tasks = tasks,
                    consoleLogs = consoleLogs
                )
            }
        }
    }
}

@Composable
fun ClawTopAppBar(
    activeTab: NavigationTab,
    systemStatus: String
) {
    Surface(
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "clawproyect",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonEmerald,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "/ ${activeTab.title}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeonEmerald.copy(alpha = 0.15f))
                    .border(1.dp, NeonEmerald.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "• ACTIVE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonEmerald
                )
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
        tonalElevation = 8.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .border(1.dp, DarkBorder)
    ) {
        NavigationTab.entries.forEach { tab ->
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
