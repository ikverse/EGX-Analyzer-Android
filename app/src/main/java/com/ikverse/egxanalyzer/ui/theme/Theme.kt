package com.ikverse.egxanalyzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ikverse.egxanalyzer.model.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF67C7D8),
    onPrimary = Color(0xFF002F36),
    secondary = Color(0xFF9A82F4),
    background = Color(0xFF0B0F14),
    surface = Color(0xFF151A21),
    surfaceVariant = Color(0xFF202731),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006879),
    secondary = Color(0xFF6650A4),
    background = Color(0xFFF6F8FA),
    surface = Color.White,
)

@Composable
fun EgxAnalyzerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
