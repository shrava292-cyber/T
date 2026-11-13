package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val MeshDarkColorScheme = darkColorScheme(
    primary = MeshCyan,
    onPrimary = Color(0xFF002821),
    primaryContainer = MeshCyanContainerDark,
    onPrimaryContainer = MeshCyanOnContainerDark,
    secondary = SignalCoral,
    onSecondary = Color(0xFF330A00),
    secondaryContainer = SignalCoralContainerDark,
    onSecondaryContainer = SignalCoralOnContainerDark,
    tertiary = AmberPulse,
    onTertiary = Color(0xFF2A1D00),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = Color(0xFFFFDF9B),
    background = ObsidianBg,
    onBackground = ChalkText,
    surface = ObsidianSurface,
    onSurface = ChalkText,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = SlateMutedText,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1F2A38),
    error = ErrorRedDark,
    onError = Color(0xFF3B0004)
)

private val MeshLightColorScheme = lightColorScheme(
    primary = MeshTealLight,
    onPrimary = Color.White,
    primaryContainer = MeshTealContainerLight,
    onPrimaryContainer = MeshTealOnContainerLight,
    secondary = SignalCoralLight,
    onSecondary = Color.White,
    secondaryContainer = SignalCoralContainerLight,
    onSecondaryContainer = SignalCoralOnContainerLight,
    tertiary = Color(0xFFB36B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE4BA),
    onTertiaryContainer = Color(0xFF2B1700),
    background = AlabasterBg,
    onBackground = DeepInk,
    surface = CrispSurface,
    onSurface = DeepInk,
    surfaceVariant = SoftSlateSurface,
    onSurfaceVariant = CoolSlateText,
    surfaceContainerLowest = CrispSurface,
    surfaceContainerLow = AlabasterBg,
    surfaceContainer = SoftSlateSurface,
    surfaceContainerHigh = SoftSlateHighest,
    outline = LightOutline,
    outlineVariant = Color(0xFFDDE4EE),
    error = ErrorRedLight,
    onError = Color.White
)

val MeshShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

@Composable
fun MeshAudioTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MeshDarkColorScheme
        else -> MeshLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MeshTypography,
        shapes = MeshShapes,
        content = content
    )
}
