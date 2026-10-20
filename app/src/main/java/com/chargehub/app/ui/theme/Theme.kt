package com.chargehub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006D5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8DF8D5),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF386A5C),
    secondaryContainer = Color(0xFFB9F1DC),
    tertiary = Color(0xFF2C6598),
    tertiaryContainer = Color(0xFFCDE5FF),
    background = Color(0xFFF7FBF7),
    surface = Color(0xFFF7FBF7),
    surfaceVariant = Color(0xFFDDE5DF),
    onSurface = Color(0xFF181D1A),
    onSurfaceVariant = Color(0xFF404944)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF71DBBA),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF00513F),
    secondary = Color(0xFF9DD4C0),
    secondaryContainer = Color(0xFF1E5144),
    tertiary = Color(0xFF9CCBFF),
    tertiaryContainer = Color(0xFF004A76),
    background = Color(0xFF101512),
    surface = Color(0xFF101512),
    surfaceVariant = Color(0xFF404944),
    onSurface = Color(0xFFE0E4DF),
    onSurfaceVariant = Color(0xFFC0C9C2)
)

@Composable
fun ChargeHubTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
