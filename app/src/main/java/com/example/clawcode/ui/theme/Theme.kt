package com.example.clawcode.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.clawcode.data.model.AgentPersonality
import com.example.clawcode.data.model.CustomThemeColorPreset
import com.example.clawcode.data.model.ModelTaskMode

data class DynamicThemePalette(
    val primary: Color = NeonEmerald,
    val secondary: Color = NeonTeal,
    val tertiary: Color = NeonPurple,
    val border: Color = DarkBorder,
    val background: Color = DarkBackground,
    val surface: Color = DarkSurface,
    val thermalWarningText: String? = null,
    val activeTaskLabel: String = "Modo Reposo",
    val activePersonalityLabel: String = "JARVIS Analista"
)

val LocalDynamicThemePalette = staticCompositionLocalOf { DynamicThemePalette() }

fun Color.softerDerivative(factor: Float = 0.35f): Color {
    return Color(
        red = red + (1f - red) * factor,
        green = green + (1f - green) * factor,
        blue = blue + (1f - blue) * factor,
        alpha = alpha
    )
}

fun Color.strongerDerivative(factor: Float = 0.35f): Color {
    return Color(
        red = red * (1f - factor),
        green = green * (1f - factor),
        blue = blue * (1f - factor),
        alpha = alpha
    )
}

fun getDynamicThemePalette(
    useAutoDynamicTheme: Boolean = true,
    customThemeColorPreset: CustomThemeColorPreset = CustomThemeColorPreset.CYAN,
    personality: AgentPersonality = AgentPersonality.JARVIS_ANALYST,
    taskMode: ModelTaskMode = ModelTaskMode.IDLE_STANDBY,
    temperatureC: Float = 33.5f
): DynamicThemePalette {
    var basePrimary: Color
    var baseSecondary: Color
    var baseTertiary: Color

    if (useAutoDynamicTheme) {
        // Enforce strict identity colors as requested
        basePrimary = NeonPrimary
        baseSecondary = NeonSecondary
        baseTertiary = NeonPrimary
    } else {
        val userBaseColor = Color(android.graphics.Color.parseColor(customThemeColorPreset.hexColor))
        basePrimary = userBaseColor
        baseSecondary = userBaseColor.softerDerivative(0.38f)
        baseTertiary = userBaseColor.strongerDerivative(0.35f)
    }

    val thermalWarningText = when {
        temperatureC >= 42.0f -> {
            basePrimary = RedError
            "ALERTA TÉRMICA SOBRECALENTAMIENTO (${"%.1f".format(temperatureC)}°C) - FRENO TÉRMICO ACTIVO"
        }
        temperatureC >= 35.0f -> {
            if (useAutoDynamicTheme) baseSecondary = WarningAmber
            "DISPOSITIVO TEMPLADO (${"%.1f".format(temperatureC)}°C)"
        }
        else -> null
    }

    val bg = DarkBackground
    val surf = DarkSurface
    val dynamicBorder = DarkBorder

    return DynamicThemePalette(
        primary = basePrimary,
        secondary = baseSecondary,
        tertiary = baseTertiary,
        border = dynamicBorder,
        background = bg,
        surface = surf,
        thermalWarningText = thermalWarningText,
        activeTaskLabel = if (useAutoDynamicTheme) taskMode.displayName else "Manual: ${customThemeColorPreset.displayName}",
        activePersonalityLabel = personality.displayName
    )
}

val AppShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun ClawTheme(
    useAutoDynamicTheme: Boolean = true,
    customThemeColorPreset: CustomThemeColorPreset = CustomThemeColorPreset.CYAN,
    personality: AgentPersonality = AgentPersonality.JARVIS_ANALYST,
    taskMode: ModelTaskMode = ModelTaskMode.IDLE_STANDBY,
    temperatureC: Float = 33.5f,
    content: @Composable () -> Unit
) {
    // La app solo esta pintada en oscuro: antes esto era un parametro que llegaba desde el modfile
    // y se descartaba dos lineas mas abajo. Se queda constante para no simular un ajuste.
    val isDarkMode = true
    val palette = getDynamicThemePalette(
        useAutoDynamicTheme = useAutoDynamicTheme,
        customThemeColorPreset = customThemeColorPreset,
        personality = personality,
        taskMode = taskMode,
        temperatureC = temperatureC
    )

    val onBgText = if (isDarkMode) TextPrimary else Color(0xFF0F172A)
    val onSurfText = if (isDarkMode) TextPrimary else Color(0xFF1E293B)
    val surfVar = if (isDarkMode) DarkSurfaceVariant else Color(0xFFE2E8F0)

    val colorScheme = if (isDarkMode) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = Color.Black,
            primaryContainer = palette.primary.copy(alpha = 0.2f),
            onPrimaryContainer = palette.primary,
            secondary = palette.secondary,
            onSecondary = Color.Black,
            secondaryContainer = palette.secondary.copy(alpha = 0.2f),
            onSecondaryContainer = palette.secondary,
            tertiary = palette.tertiary,
            onTertiary = Color.White,
            background = palette.background,
            onBackground = onBgText,
            surface = palette.surface,
            onSurface = onSurfText,
            surfaceVariant = surfVar,
            onSurfaceVariant = TextSecondary,
            outline = palette.border,
            error = RedError,
            onError = Color.White
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primary.copy(alpha = 0.15f),
            onPrimaryContainer = palette.primary,
            secondary = palette.secondary,
            onSecondary = Color.White,
            secondaryContainer = palette.secondary.copy(alpha = 0.15f),
            onSecondaryContainer = palette.secondary,
            tertiary = palette.tertiary,
            onTertiary = Color.White,
            background = palette.background,
            onBackground = onBgText,
            surface = palette.surface,
            onSurface = onSurfText,
            surfaceVariant = surfVar,
            onSurfaceVariant = Color(0xFF64748B),
            outline = palette.border,
            error = RedError,
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalDynamicThemePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

