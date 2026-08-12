package com.ikverse.egxanalyzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.model.ThemeMode

/**
 * Fixed palette rather than a wallpaper-derived one.
 *
 * These screens carry financial signals, so green must mean target and red must mean stop on every
 * device. A dynamic scheme would reassign those roles to whatever the wallpaper suggests, which is
 * the one thing this app cannot let vary.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF5DD4E8),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004E5A),
    onPrimaryContainer = Color(0xFFB3ECF8),
    secondary = Color(0xFFA894F5),
    onSecondary = Color(0xFF2A1A5E),
    secondaryContainer = Color(0xFF3F2E7A),
    onSecondaryContainer = Color(0xFFE7DEFF),
    tertiary = Color(0xFF6BDD9A),
    onTertiary = Color(0xFF00391F),
    tertiaryContainer = Color(0xFF11512F),
    onTertiaryContainer = Color(0xFF89FAB5),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4E0002),
    errorContainer = Color(0xFF6E1512),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE2E6EB),
    surface = Color(0xFF0B0F14),
    onSurface = Color(0xFFE2E6EB),
    surfaceVariant = Color(0xFF1E262F),
    onSurfaceVariant = Color(0xFFB6C0CC),
    surfaceContainerLowest = Color(0xFF070A0E),
    surfaceContainerLow = Color(0xFF11161C),
    surfaceContainer = Color(0xFF151A21),
    surfaceContainerHigh = Color(0xFF1B222A),
    surfaceContainerHighest = Color(0xFF222A34),
    outline = Color(0xFF6B7684),
    outlineVariant = Color(0xFF2C353F),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00697A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB0ECFB),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF5B4AA8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5DEFF),
    onSecondaryContainer = Color(0xFF190066),
    tertiary = Color(0xFF13683D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9DF6BC),
    onTertiaryContainer = Color(0xFF00210F),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FB),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDCE4EC),
    onSurfaceVariant = Color(0xFF41484F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F8),
    surfaceContainer = Color(0xFFEBEFF4),
    surfaceContainerHigh = Color(0xFFE5EAF0),
    surfaceContainerHighest = Color(0xFFDFE5EC),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
)

/**
 * The signals Material's roles have no slot left for.
 *
 * Every scheme colour above is already spoken for: cyan is a running position, green is a target,
 * red is a stop, the greys are context. A position that ran out of time is none of those - it is
 * waiting on the user, and it can perfectly well be up 5%, so borrowing red would report a loss the
 * trade never made and borrowing purple gave it a hue that means nothing here.
 *
 * Amber is added rather than reassigned for the reason the palette is fixed in the first place: a
 * role that already means something must go on meaning it.
 */
data class ExtraColors(
    /** Text, labels, and the outline a card gets when the trade on it has run out of time. */
    val expired: Color,
    val expiredContainer: Color,
    val onExpiredContainer: Color,
)

private val DarkExtras = ExtraColors(
    expired = Color(0xFFF3C264),
    expiredContainer = Color(0xFF5A4318),
    onExpiredContainer = Color(0xFFFFE0A3),
)

private val LightExtras = ExtraColors(
    expired = Color(0xFF8A5A00),
    expiredContainer = Color(0xFFFFDFA6),
    onExpiredContainer = Color(0xFF2A1A00),
)

/**
 * Provided by [EgxAnalyzerTheme], so these follow the app's own light/dark setting.
 *
 * Reading `isSystemInDarkTheme()` at the point of use would ignore a user who has forced light or
 * dark in Settings, and put a dark-theme amber on a light page.
 */
val LocalExtraColors = staticCompositionLocalOf { LightExtras }

/** The extra roles for the theme in force, beside `MaterialTheme.colorScheme`. */
val extraColors: ExtraColors
    @Composable get() = LocalExtraColors.current

/** Rounder than the M3 default; these screens are card-dense and softer corners keep them calm. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.6.sp),
    )
}

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
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
