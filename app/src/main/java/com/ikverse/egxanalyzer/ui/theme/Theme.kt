package com.ikverse.egxanalyzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    // Purple is gone. Nothing in this app ever meant it - it was the hue left over - and Material
    // spends `secondary` on exactly the things that should read as the app's own voice: a selected
    // filter chip, the navigation indicator. Pitched a step quieter than `primary` so the two can
    // sit beside each other without competing, but unmistakably the same family.
    secondary = Color(0xFF7FC5D6),
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF0E3F49),
    onSecondaryContainer = Color(0xFFB3ECF8),
    // Deeper than the mint it replaces. A target and a price the market reached appear on one card,
    // and the old green sat close enough to the old cyan that the two read as one colour.
    tertiary = Color(0xFF46C98A),
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
    secondary = Color(0xFF00697A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEDF6),
    onSecondaryContainer = Color(0xFF001F26),
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

    /**
     * A price the market actually reached, rather than one a channel chose.
     *
     * Added for the same reason amber was, and it is the second time the same argument has come up.
     * This used to be `primary` - so cyan meant both "the market got here" and "this is the app
     * speaking": the navigation indicator, a running position, a hit rate worth noticing. One hue
     * cannot carry a provenance and a voice at once, and the cost was paid on the call cards, where
     * a peak in cyan sat beside a target in green close enough in hue that the two read as one.
     *
     * Blue rather than another green, so target, stop, market and expired are four hues a
     * red/green colour-blind reader can still hold apart.
     */
    val market: Color,

    /**
     * The model speaking, which is the one thing on these screens that is not a measurement.
     *
     * Violet through magenta, and the third time the "a role that already means something must go
     * on meaning it" argument has decided a colour here. Every hue above is a claim about a price -
     * green a target, red a stop, blue a level the market reached, amber a window that closed - and
     * an opinion is none of them. Purple was retired from this palette for having no meaning; it is
     * given one back, and only one.
     *
     * [aiFill] is the pill itself and runs dark, so [aiOnFill] can be white in both themes.
     */
    val aiFill: List<Color>,
    val aiOnFill: Color,

    /**
     * The same family drawn as a line rather than a surface, for the button that only reopens a
     * saved answer.
     *
     * Its own stops rather than [aiFill]'s, because an outline has to contrast with the card behind
     * it while a fill has to contrast with the label on top of it - opposite requirements on a dark
     * theme, where the fill goes darker than the card and the line has to go lighter.
     */
    val aiLine: List<Color>,
    val aiText: Color,

    /** The halo under the pill. Kept bright: a dark glow on a dark page is not a glow. */
    val aiGlow: Color,

    /**
     * The mark, in gold.
     *
     * Two sets because it is drawn on two different grounds: [aiSpark] on the dark pill, where gold
     * can be bright, and [aiSparkOnCard] on the card itself, where the light theme's pale surface
     * would swallow the same colour whole.
     */
    val aiSpark: List<Color>,
    val aiSparkOnCard: List<Color>,
)

/** Gold, on a violet pill. The one place this app spends a colour on delight rather than meaning. */
private val BrightGold = listOf(Color(0xFFFFE9B8), Color(0xFFFFD37A), Color(0xFFE8A53C))

private val DarkExtras = ExtraColors(
    expired = Color(0xFFF3C264),
    expiredContainer = Color(0xFF5A4318),
    onExpiredContainer = Color(0xFFFFE0A3),
    market = Color(0xFF4C9DF0),
    aiFill = listOf(Color(0xFF4A3FBE), Color(0xFF7548AE), Color(0xFF9C4478)),
    aiOnFill = Color.White,
    aiLine = listOf(Color(0xFF9E8AF0), Color(0xFFB98ADD), Color(0xFFDE86B4)),
    aiText = Color(0xFFB9A3F2),
    aiGlow = Color(0xFF845FD6),
    aiSpark = BrightGold,
    // The card is dark here too, so the mark needs no softening.
    aiSparkOnCard = BrightGold,
)

private val LightExtras = ExtraColors(
    expired = Color(0xFF8A5A00),
    expiredContainer = Color(0xFFFFDFA6),
    onExpiredContainer = Color(0xFF2A1A00),
    // Darkened rather than reused. The dark theme's #4C9DF0 comes out at 2.4:1 on a light card,
    // where these figures are actually drawn, against the 4.5:1 body text needs - and every one of
    // them is a price. This lands at 4.5:1 there and 5.2:1 on the page.
    market = Color(0xFF1668C7),
    // A shade under the dark theme's, because these sit on a pale card rather than a black page.
    aiFill = listOf(Color(0xFF3E349E), Color(0xFF653B96), Color(0xFF883A66)),
    aiOnFill = Color.White,
    // The fill's own stops, unlike the dark theme: here the line has to be darker than the card,
    // which is the same direction the fill goes.
    aiLine = listOf(Color(0xFF3E349E), Color(0xFF653B96), Color(0xFF883A66)),
    aiText = Color(0xFF55338F),
    aiGlow = Color(0xFF653B96),
    aiSpark = BrightGold,
    // Bright gold on a near-white card is barely there. Deepened until it draws at 3:1 on it.
    aiSparkOnCard = listOf(Color(0xFFD9A44E), Color(0xFFC2872A), Color(0xFF9E6A17)),
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
