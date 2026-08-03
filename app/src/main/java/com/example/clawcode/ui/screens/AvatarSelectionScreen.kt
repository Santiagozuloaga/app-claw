package com.example.clawcode.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clawcode.R
import com.example.clawcode.ui.theme.*
import com.example.clawcode.viewmodel.ClawViewModel

data class VectorAvatarOption(
    val id: Int,
    val name: String,
    val vectorIcon: ImageVector,
    val resId: Int? = null,
    val accentColor: Color
)

@Composable
fun AvatarSelectionScreen(viewModel: ClawViewModel? = null) {
    val modfileState = viewModel?.jarvisModfile?.collectAsState()
    val currentSavedId = modfileState?.value?.selectedAvatarId ?: 1
    
    var selectedAvatarId by remember(currentSavedId) { mutableStateOf(currentSavedId) }
    var isSavedMessageVisible by remember { mutableStateOf(false) }
    
    val avatarOptions = listOf(
        VectorAvatarOption(1, "JARVIS Core (Vector)", Icons.Default.SmartToy, R.drawable.avatar_1, NeonEmerald),
        VectorAvatarOption(2, "Red Neural AI", Icons.Default.Psychology, R.drawable.avatar_2, NeonTeal),
        VectorAvatarOption(3, "Asistente Agente", Icons.Default.SupportAgent, R.drawable.avatar_3, NeonSecondary),
        VectorAvatarOption(4, "Procesador Quantum", Icons.Default.Memory, R.drawable.claw_logo_1, NeonPurple),
        VectorAvatarOption(5, "Interfaz Humanoide", Icons.Default.Face, R.drawable.claw_logo_2, WarningAmber),
        VectorAvatarOption(6, "Motor Neon Cyber", Icons.Default.AutoAwesome, R.drawable.claw_blue_neon, NeonTeal)
    )
    
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Seleccionar Avatar CLAW",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
        
        Text(
            text = "Elige la representación visual de tu asistente. Optimizado en vectores para cero consumo de memoria y máxima fluidez.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextSecondary
            )
        )

        if (isSavedMessageVisible) {
            Surface(
                color = NeonEmerald.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "✓ Avatar guardado correctamente. Aplicado a Chat, Dashboard y Consola.",
                    color = NeonEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            avatarOptions.chunked(2).forEach { rowAvatars ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowAvatars.forEach { avatar ->
                        Box(modifier = Modifier.weight(1f)) {
                            AvatarOptionCard(
                                avatar = avatar,
                                isSelected = selectedAvatarId == avatar.id,
                                onSelect = { 
                                    selectedAvatarId = avatar.id
                                    viewModel?.setSelectedAvatarId(avatar.id)
                                    isSavedMessageVisible = true
                                }
                            )
                        }
                    }
                    if (rowAvatars.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { 
                isSavedMessageVisible = true
                viewModel?.let { vm ->
                    vm.setSelectedAvatarId(selectedAvatarId)
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "APLICAR CAMBIOS DE AVATAR",
                color = DarkBackground,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun AvatarOptionCard(
    avatar: VectorAvatarOption,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) avatar.accentColor else DarkBorder
    val bgColor = if (isSelected) avatar.accentColor.copy(alpha = 0.12f) else DarkSurface
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(DarkBackground)
                    .border(1.5.dp, avatar.accentColor.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = avatar.vectorIcon,
                    contentDescription = avatar.name,
                    tint = avatar.accentColor,
                    modifier = Modifier.size(42.dp)
                )
                
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(avatar.accentColor.copy(alpha = 0.25f))
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = avatar.accentColor,
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.Center)
                    )
                }
            }
            
            Text(
                text = avatar.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) avatar.accentColor else TextPrimary
                ),
                maxLines = 1
            )
        }
    }
}


