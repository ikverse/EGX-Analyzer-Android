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
     * The screen's own action, which is not the model speaking and no longer dressed as if it were.
     *
     * The violet went here first, on the argument that pressing this asks a model. What that missed
     * is size: on a call card the Ask AI pill is an accent 32dp tall, while this is 56dp of fill
     * across the whole width of the phone - the largest single field of colour in the app, and the
     * only object in that hue on a screen of cyan and slate. A hue reserved for one meaning still
     * has to survive being enlarged, and this one did not.
     *
     * So the action takes the app's own teal, and violet stays where it reads as an accent. At rest
     * this button is the app offering its action; the model has not been asked anything yet. Two
     * shades of one hue rather than a run between two, for the reason the violet was cut to two
     * stops: across a whole width, two colours meeting in the middle drift the label's contrast
     * along their length.
     *
     * Every stop here carries alpha **0.84** in the colour itself, ten points under the navigation
     * bar's own 0.94 - and the gap is deliberate now that the two behave differently. The bar tidies
     * itself away while a page is read; the action does not, so it is a permanent object over a page
     * still being scrolled, and at the bar's opacity it read as a slab parked on the page rather
     * than as a control floating above it. Letting the page show faintly through is what a piece of
     * chrome that never leaves has to do. [actionAuroraBase] carries the same figure, so the button
     * does not change weight the moment a run starts.
     *
     * Baked into the colour rather than passed to the draw call, so the fill cannot be painted at
     * full strength by a caller that forgets.
     */
    val actionFill: List<Color>,

    /**
     * The same family drawn as a line rather than a surface, for the hairline the action wears when
     * it is ready to be pressed.
     *
     * Its own stops rather than [actionFill]'s, for the reason [aiLine] has its own: the fill sits
     * *inside* this line, so a line in the fill's colours is a line against itself and disappears.
     * What it has to read against is the page scrolling behind the button - which is dark on one
     * theme and near-white on the other, so the stops invert while the hue does not.
     *
     * **Lighter-handed than the ground it surrounds**: 0.74 against [actionFill]'s 0.84. It shipped
     * opaque and at the aurora's own full-strength hues, on the argument that an edge letting the
     * page through stops holding the shape against whatever scrolls under it - and on the device
     * that read as a bright cyan wire around the button, loudest thing on a dark page and competing
     * with the label it was supposed to frame. The shape still holds at 0.74 because what draws it
     * is the *contrast* with the page rather than the weight of the line.
     *
     * The hues are the aurora's own taken down about a third, in the aurora's own order - cyan,
     * blue, teal - rather than a fourth set invented for the edge. Every stop carries the same
     * alpha: a ramp whose stops differ in opacity fades out along its length, which reads as a
     * gradient that has gone wrong rather than as one that was chosen. `ActionPaletteTest` pins
     * that, and that the edge shares no stop with the fill.
     */
    val actionLine: List<Color>,

    /** The label and the mark on [actionFill]. Pale cyan rather than white: it is a teal ground. */
    val onAction: Color,

    /** The halo under the action at rest. Cyan, because the action is the app's voice. */
    val actionGlow: Color,

    /**
     * The action while a model is working: a ground with light drifting through it.
     *
     * Also teal, and deliberately so. The first attempt at this made the running state violet on the
     * argument that a model genuinely is working by then - but that put back at slab size exactly
     * the hue that had just been taken off the button, and a state change is not worth importing a
     * colour the app does not otherwise own. So idle and running are one hue, and what separates
     * them is light moving through it, which the red hairline, the "Cancel analysis" label and the
     * elapsed clock all say again in words.
     *
     * [actionAurora] is drawn as three soft circles over [actionAuroraBase] on cycles that do not
     * divide into one another, so the movement never visibly repeats. Their alphas are low on
     * purpose: they land over a ground at 0.84, and anything stronger would make the busy parts of
     * the sweep read as more solid than the bar beneath it.
     *
     * **The ground is 0.84, which is [actionFill]'s figure too**, and the two match on purpose: the
     * action never hides now, so it is a permanent object over a page still being scrolled, and the
     * transparency that stops it reading as a slab is a property of the button rather than of one
     * of its states. A button that changed weight the moment a run started would report the run
     * twice over, once in a way nobody could name. Only the ground carries it: the circles are the
     * light inside the ground, and thinning those would dim the one thing on the button that says a
     * model is working.
     *
     * The middle stop is the blue that means a price the market reached. Borrowed knowingly: that
     * rule governs a hue labelling a figure, and a light moving inside a button labels nothing. It
     * is here because two adjacent hues read as one aurora, where a single hue reads as the resting
     * button with a gradient on it.
     */
    val actionAuroraBase: Color,
    val actionAurora: List<Color>,

    /**
     * The hairline the action wears while a run is going, in place of the neutral outline.
     *
     * One value for both themes, for the same reason [aiOnFill] is white in both: [aiAction] runs
     * dark either way. Material's light-theme `error` is #B3261E, which on this fill is a dark line
     * on a dark field and reports nothing.
     */
    val aiStop: Color,

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

internal val DarkExtras = ExtraColors(
    expired = Color(0xFFF3C264),
    expiredContainer = Color(0xFF5A4318),
    onExpiredContainer = Color(0xFFFFE0A3),
    market = Color(0xFF4C9DF0),
    aiFill = listOf(Color(0xFF4A3FBE), Color(0xFF7548AE), Color(0xFF9C4478)),
    aiOnFill = Color.White,
    actionFill = listOf(Color(0xD6004E5A), Color(0xD60B6D7F)),
    // [actionGlow] and the aurora's own two hues, taken down about a third from the values this
    // shipped with. Still lighter than the fill it surrounds, because on this theme the page behind
    // the button is dark and an edge has to lift off it - but no longer a bright wire around it.
    actionLine = listOf(Color(0xBD4194A2), Color(0xBD356EA8), Color(0xBD2C7E90)),
    onAction = Color(0xFFCFF3FA),
    actionGlow = Color(0xFF5DD4E8),
    actionAuroraBase = Color(0xD6072A3E),
    actionAurora = listOf(Color(0x6B5DD4E8), Color(0x5C4C9DF0), Color(0x9E0B6D7F)),
    aiStop = Color(0xFFFF8A80),
    aiLine = listOf(Color(0xFF9E8AF0), Color(0xFFB98ADD), Color(0xFFDE86B4)),
    aiText = Color(0xFFB9A3F2),
    aiGlow = Color(0xFF845FD6),
    aiSpark = BrightGold,
    // The card is dark here too, so the mark needs no softening.
    aiSparkOnCard = BrightGold,
)

internal val LightExtras = ExtraColors(
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
    // A shade under the dark theme's, the same way the pill's fill is: these sit on a pale page, and
    // the teal still has to run dark enough to carry [onAction] on top of it.
    actionFill = listOf(Color(0xD600414C), Color(0xD60A5C6C)),
    // The same three roles as the dark theme's, taken from this theme's own values rather than
    // reused: the page here is near-white, so the edge runs dark where the dark theme's runs light.
    // #5DD4E8 on this page is the glow problem again - a line that pale does not read as a line.
    // Deepened by the same third as the dark theme's, so the two keep the same relationship to
    // their own grounds; on this page the effect is a firmer line rather than a quieter one.
    actionLine = listOf(Color(0xBD004A55), Color(0xBD0F498B), Color(0xBD07404C)),
    onAction = Color(0xFFE8FAFF),
    // Not the dark theme's #5DD4E8: a glow that pale on a near-white page is not a glow.
    actionGlow = Color(0xFF00697A),
    actionAuroraBase = Color(0xD605303F),
    // The light theme's market blue in the middle stop, for the reason the dark theme's is its own:
    // #4C9DF0 was picked to read on black.
    actionAurora = listOf(Color(0x6B3FB4CE), Color(0x5C1668C7), Color(0x9E0A5C6C)),
    aiStop = Color(0xFFFF8A80),
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
