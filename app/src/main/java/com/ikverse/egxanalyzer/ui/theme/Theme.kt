package com.ikverse.egxanalyzer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ThemeMode

/**
 * Fixed palette rather than a wallpaper-derived one.
 *
 * These screens carry financial signals, so green must mean target and red must mean stop on every
 * device. A dynamic scheme would reassign those roles to whatever the wallpaper suggests, which is
 * the one thing this app cannot let vary.
 *
 * **`primary` and `secondary` are the exception, and they are not fixed: they are the page's own
 * hue**, swapped per destination by [withAccent]. See [PageAccent] for why that is safe here and
 * would not have been before `market` was given a role of its own.
 */
/**
 * **The app's surfaces are paper and ink, not glass.** Since the 2026-11 redesign (an M3 Expressive
 * and Swiss editorial mix) every container is opaque and tonal: a section is one step off the page,
 * a card one step more, a tile one more again. Information sits flat; anything pressable sits in a
 * rounded container. The three constants below stayed, at 1, so every reader of them keeps compiling
 * and `GlassContrastTest` now checks plain opaque steps.
 */
internal const val GlassSection = 1f
internal const val GlassCard = 1f
internal const val GlassTile = 1f

internal const val LightGlassSection = 1f
internal const val LightGlassCard = 1f
internal const val LightGlassTile = 1f

/**
 * **Since 5.0 the scheme is derived from [AppPalette]** (see `Palette.kt`): paper and navy ink, cobalt
 * as the one accent, and the five tile colours kept off the scheme entirely because they are fills
 * and never text. Every role below is a palette colour or a step between two of them, so the scheme
 * and the palette cannot drift apart.
 *
 * Before 5.0 the accent was amber (4.7.0), burnt orange and vermilion before that.
 */
internal val DarkColors = darkColorScheme(
    primary = DarkPalette.heroInk,
    onPrimary = DarkPalette.onTile,
    primaryContainer = Color(0xFF232A6B),
    onPrimaryContainer = Color(0xFFDADDFF),
    secondary = DarkPalette.heroInk,
    onSecondary = DarkPalette.onTile,
    secondaryContainer = Color(0xFF232A6B),
    onSecondaryContainer = Color(0xFFDADDFF),
    // Gain. Never the accent.
    tertiary = DarkPalette.gain,
    onTertiary = Color(0xFF00391F),
    tertiaryContainer = Color(0xFF11512F),
    onTertiaryContainer = Color(0xFFA6F5C4),
    // A fault, kept apart from the loss red so a failed run never reads as money lost.
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4E0002),
    errorContainer = Color(0xFF6E1512),
    onErrorContainer = Color(0xFFFFDAD5),
    background = DarkPalette.ground,
    onBackground = DarkPalette.ink,
    surface = DarkPalette.ground,
    onSurface = DarkPalette.ink,
    surfaceVariant = DarkPalette.raised,
    onSurfaceVariant = DarkPalette.muted,
    surfaceContainerLowest = Color(0xFF0E0F11),
    surfaceContainerLow = Color(0xFF1A1C20),
    surfaceContainer = DarkPalette.card,
    surfaceContainerHigh = DarkPalette.raised,
    surfaceContainerHighest = Color(0xFF2E333B),
    outline = Color(0xFF7E838D),
    outlineVariant = DarkPalette.line,
)

/** [DarkColors] on true black. Only the grounds change; every signal is copied verbatim. */
internal val BlackColors = DarkColors.copy(
    background = BlackPalette.ground,
    surface = BlackPalette.ground,
    surfaceVariant = BlackPalette.raised,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0C0D0F),
    surfaceContainer = BlackPalette.card,
    surfaceContainerHigh = BlackPalette.raised,
    surfaceContainerHighest = Color(0xFF282C33),
    outlineVariant = BlackPalette.line,
)

internal val LightColors = lightColorScheme(
    primary = LightPalette.heroInk,
    onPrimary = LightPalette.onHero,
    primaryContainer = Color(0xFFDADDFF),
    onPrimaryContainer = Color(0xFF0B1490),
    secondary = LightPalette.heroInk,
    onSecondary = LightPalette.onHero,
    secondaryContainer = Color(0xFFDADDFF),
    onSecondaryContainer = Color(0xFF0B1490),
    tertiary = LightPalette.gain,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDEBD6),
    onTertiaryContainer = Color(0xFF062B15),
    // A fault, deliberately deeper than the loss red, so a failed run never reads as money lost.
    error = Color(0xFF8C1D18),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = LightPalette.ground,
    onBackground = LightPalette.ink,
    surface = LightPalette.ground,
    onSurface = LightPalette.ink,
    surfaceVariant = LightPalette.raised,
    onSurfaceVariant = LightPalette.muted,
    surfaceContainerLowest = LightPalette.card,
    surfaceContainerLow = Color(0xFFF7F6F2),
    // Cards are white on paper, as on the canvas; the two nested steps go back toward the paper.
    surfaceContainer = LightPalette.card,
    surfaceContainerHigh = Color(0xFFF6F5F0),
    surfaceContainerHighest = Color(0xFFECE9E1),
    outline = LightPalette.faint,
    outlineVariant = LightPalette.line,
)

/**
 * The signals Material's roles have no slot left for.
 *
 * Every scheme colour above is already spoken for: green is a target, `error` is an ordinary fault,
 * the greys are context, and `primary` is now the page speaking rather than the app. A position
 * that ran out of time is none of those - it is waiting on the user, and it can perfectly well be
 * up 5%, so borrowing red would report a loss the trade never made.
 *
 * Its own role rather than a reassigned one, for the reason the palette is fixed in the first place:
 * a role that already means something must go on meaning it. A warm grey since 4.7.0, when amber
 * became the accent.
 *
 * **What used to live here and no longer does**: the action's fill and edge, the mark's aurora, and
 * the Ask AI pill's violet, glow and gold, which went with the pill's redesign in 4.7.0.
 */
data class ExtraColors(
    /** Text, labels, and the outline a card gets when the trade on it has run out of time. */
    val expired: Color,
    val expiredContainer: Color,
    val onExpiredContainer: Color,

    /**
     * A deeper red than Material's `error`, reserved for a trade or a price actually losing:
     * stop-loss, stopped out, a negative return, the AI opinion sheet's bearish verdicts.
     *
     * `error` stays the app's ordinary red for everything that is a fault rather than a loss - a
     * rejected key, a failed run, a stale feed - so the one colour a reader could mistake for money
     * lost is spent on nothing else.
     */
    val loss: Color,
    val lossContainer: Color,
    val onLossContainer: Color,

    /**
     * A price the market actually reached, rather than one a channel chose.
     *
     * Its own hue rather than `primary`, and that split is what makes the page accents below
     * possible at all. While `primary` meant both "the market got here" and "this is the app
     * speaking", nothing could be done to `primary` without moving a price - and a page hue that
     * moved a price is exactly the collision the accents are chosen to avoid.
     *
     * Blue rather than another green, so target, stop, market and expired are four hues a
     * red/green colour-blind reader can still hold apart.
     */
    val market: Color,
)

internal val DarkExtras = ExtraColors(
    // A warm grey since 4.7.0. It was amber, which is the accent now, and an expired trade must not
    // look like something to press. Grey also says "this is over" better than a warning hue did.
    expired = Color(0xFFB5AEA3),
    expiredContainer = Color(0xFF3A3833),
    onExpiredContainer = Color(0xFFE6E1D8),
    // Signal red, the palette's dark-theme loss.
    loss = DarkPalette.loss,
    lossContainer = Color(0xFF4A1212),
    onLossContainer = Color(0xFFFFDAD6),
    market = Color(0xFF5AA9FF),
)

internal val LightExtras = ExtraColors(
    expired = Color(0xFF6B655C),
    expiredContainer = Color(0xFFE6E2DA),
    onExpiredContainer = Color(0xFF2C2A26),
    // Signal red, 5.2:1 on paper. `error` is the deeper #8C1D18 so a fault and a loss differ.
    loss = LightPalette.loss,
    lossContainer = Color(0xFFF9DEDC),
    onLossContainer = Color(0xFF410E0B),
    // Darkened rather than reused. The dark theme's blue comes out at 2.4:1 on a light card, where
    // these figures are actually drawn, against the 4.5:1 body text needs - and every one of them
    // is a price.
    market = Color(0xFF1668C7),
)

/**
 * Which hue a destination speaks in.
 *
 * Not a decoration. Every one of these was picked to be a family the figures on that page never
 * draw, so a page's identity can never be read as one of its own signals - which is the whole
 * reason a per-page hue is safe in an app where colour means something:
 *
 * - [CYAN] on Analyze, which draws no prices at all, and whose action already wore this.
 * - [VIOLET] on Results. Violet means the model speaking and a report is exactly that; `AiButton`
 *   is never drawn on that screen, so the family is unclaimed there.
 * - [ROSE] on Insights, the most signal-dense page in the app - target, stop, market, expired *and*
 *   the violet pill - so it takes the one family no figure anywhere uses.
 * - [INDIGO] on Portfolio, which draws no market blue, so the blue side of the wheel is free while
 *   green, red and amber are busy.
 * - [BRONZE] on Settings, which draws no prices, so amber is unclaimed there.
 */
enum class AccentKey { CYAN, VIOLET, ROSE, INDIGO, BRONZE }

/**
 * One page's hue, and everything the page draws with it.
 *
 * **The rule this palette is built to keep: an accent is chrome, never a figure.** It goes on the
 * header wash, the app's mark, a section card's icon tile and edge, a selected control, the
 * destination's own indicator, and the two buttons below. It never colours a price, a return, a
 * status pill or an outcome bar - those read from [ExtraColors] and the tertiary/error roles, which
 * are the same on all five pages.
 *
 * Most of this is derived from the seven colours in [accentSeeds] rather than written out five
 * times. A ramp written by hand per page is five places for one relationship to drift.
 */
data class PageAccent(
    /** The hue itself: the mark, an icon on a dark card, the indicator's ink. */
    val base: Color,
    /** [base] where it has to be read against a card. The same colour on dark; darkened on light. */
    val ink: Color,
    /** Text drawn on a filled [base]: the Analyze action, a filled settings button. Dark in both themes. */
    val onBase: Color,
    /**
     * Ink drawn on a filled [ink] - a checkbox tick, a Material button, anything reading `onPrimary`.
     *
     * Its own value since 4.7.0, when the light theme's [base] became a pale amber: dark text is
     * right on that fill and wrong on the much darker [ink], which `primary` still is.
     */
    val onInk: Color,
    /** The tile behind a section icon, a selected chip, the navigation indicator. */
    val soft: Color,
    /** A card's accent edge, and the hairline on a selected control. */
    val line: Color,
    /** The top of the page, faded to nothing. An arrival cue, gone as soon as anything is scrolled. */
    val wash: Color,

    /**
     * The screen's own action, in the page's hue.
     *
     * Every stop carries alpha **0.94** in the colour itself, the navigation bar's own figure.
     * It was 0.84, ten points under the bar, so that a permanent object over a scrolling page read
     * as a control floating above it rather than a slab parked on it. On the 411dp cover screen
     * that was too thin: the heading of the card behind it ran straight through the label
     * (reported 2026-10-05 from a screenshot). [actionAuroraBase] carries the same figure, so the
     * button does not change weight the moment a run starts.
     *
     * Baked into the colour rather than passed to the draw call, so the fill cannot be painted at
     * full strength by a caller that forgets.
     */
    val actionFill: List<Color>,

    /**
     * The same family drawn as a line rather than a surface.
     *
     * Its own stops rather than [actionFill]'s, because the fill sits *inside* this line: a line in
     * the fill's colours is a line against itself and disappears. What it has to read against is the
     * page scrolling behind the button.
     *
     * **Lighter-handed than the ground it surrounds**: 0.74 against [actionFill]'s 0.94. It shipped
     * opaque once and read as a bright wire around the button, loudest thing on a dark page and
     * competing with the label it was supposed to frame. The shape still holds because what draws
     * it is the *contrast* with the page rather than the weight of the line.
     *
     * The hues are the aurora's own taken down about a third, in the aurora's own order. Every stop
     * carries the same alpha: a ramp whose stops differ in opacity fades out along its length, which
     * reads as a gradient that has gone wrong rather than one that was chosen. `ActionPaletteTest`
     * pins that, and that the edge shares no stop with the fill.
     */
    val actionLine: List<Color>,

    /** The label and the mark on [actionFill]. The page's hue at near-white, not white itself. */
    val onAction: Color,

    /** The halo under the action at rest. */
    val actionGlow: Color,

    /**
     * The action while a model is working: a ground with light drifting through it.
     *
     * [actionAurora] is drawn as three soft circles over [actionAuroraBase] on cycles that do not
     * divide into one another, so the movement never visibly repeats. Their alphas are low on
     * purpose: they land over a ground at 0.94, and anything stronger would make the busy parts of
     * the sweep read as more solid than the bar beneath it.
     */
    val actionAuroraBase: Color,
    val actionAurora: List<Color>,

    /**
     * The same aurora, painted through the app's own mark in the header and the rail.
     *
     * **Opaque, where [actionAurora] is not**, and that is the one difference. Those stops are three
     * soft circles drifting over a ground at 0.94, so they are kept low. These are painted *through*
     * a 24dp glyph with `SrcIn`: there is nothing behind them to show through, and a mark at two
     * thirds strength is simply a dimmer mark. `ActionPaletteTest` pins both halves of that.
     */
    val markAurora: List<Color>,
)

/**
 * The seven colours each page is built from.
 *
 * Everything in [PageAccent] is one of these or derived from them, so a page is described in one
 * place and the relationships between its ramps cannot drift from another page's.
 */
private class AccentSeed(
    /** The hue at full strength. */
    val base: Color,
    /** [base] made readable on this theme's card: itself on dark, darkened on light. */
    val ink: Color,
    /** Ink on a filled [base]. */
    val onBase: Color,
    /** Ink on a filled [ink]. See [PageAccent.onInk]. */
    val onInk: Color,
    /**
     * The hue next door, so a gradient in one family still moves.
     *
     * Two adjacent hues read as one aurora, where a single hue reads as the resting button with a
     * gradient on it.
     */
    val neighbour: Color,
    /** The dark end of the action's fill. */
    val deep: Color,
    /** The fill's other stop, and the aurora's last: dark enough to carry white, light enough to move. */
    val mid: Color,
    /** The ground the aurora drifts over. Darker than [deep] - it is behind the lights, not beside them. */
    val ground: Color,
)

/**
 * Electric cobalt, since 5.0 (amber in 4.7.0).
 *
 * On paper the one cobalt is both the fill and the text: it carries white and reads at well over
 * 4.5:1 on paper. On Graphite it does not read as text, so the dark column lifts `ink` to a pale
 * cobalt and keeps the saturated one for fills, which carry white in both themes.
 */
private val DarkAccentSeed = AccentSeed(
    base = DarkPalette.hero, ink = DarkPalette.heroInk,
    onBase = DarkPalette.onHero, onInk = DarkPalette.onTile,
    neighbour = Color(0xFF5A66FF), deep = Color(0xFF121A8C), mid = Color(0xFF2430D8),
    ground = Color(0xFF080C40),
)

private val LightAccentSeed = AccentSeed(
    base = LightPalette.hero, ink = LightPalette.heroInk,
    onBase = LightPalette.onHero, onInk = LightPalette.onHero,
    neighbour = Color(0xFF4A56FF), deep = Color(0xFF0B1490), mid = Color(0xFF1824D6),
    ground = Color(0xFF070B4A),
)

/**
 * **One accent on every page.** The five keys are kept so the shell and every caller still name a
 * destination, but each resolves to the same cobalt seed: a page's identity is its title, and the
 * tile colours are what give a screen its rhythm.
 */
private val darkSeeds = AccentKey.entries.associateWith { DarkAccentSeed }
private val lightSeeds = AccentKey.entries.associateWith { LightAccentSeed }

/** How far the edge's hues are taken down from the aurora's own. About a third, in both themes. */
private const val EdgeDim = 0.66f

/** The ground the action floats at, and the figure [PageAccent.actionAuroraBase] matches. */
private const val ActionAlpha = 0.94f

/** A step lighter-handed than the ground it surrounds. See [PageAccent.actionLine]. */
private const val EdgeAlpha = 0.74f

private fun Color.dim(factor: Float) = lerp(Color.Black, this, factor)

private fun accent(seed: AccentSeed, dark: Boolean): PageAccent {
    // The aurora's three, and the one list the mark and the action both read from - the test that
    // pins them equal is pinning that this stays one list.
    val aurora = listOf(seed.base, seed.neighbour, seed.mid)
    return PageAccent(
        base = seed.base,
        ink = seed.ink,
        onBase = seed.onBase,
        onInk = seed.onInk,
        // A shade lighter-handed on the light theme: the same alpha over near-white reads heavier
        // than it does over near-black.
        soft = seed.ink.copy(alpha = if (dark) 0.16f else 0.13f),
        line = seed.ink.copy(alpha = 0.42f),
        wash = seed.base.copy(alpha = if (dark) 0.11f else 0.10f),
        actionFill = listOf(
            seed.deep.copy(alpha = ActionAlpha),
            seed.mid.copy(alpha = ActionAlpha),
        ),
        actionLine = aurora.map { it.dim(EdgeDim).copy(alpha = EdgeAlpha) },
        onAction = lerp(seed.base, Color.White, if (dark) 0.82f else 0.92f),
        actionGlow = if (dark) seed.base else seed.ink,
        actionAuroraBase = seed.ground.copy(alpha = ActionAlpha),
        // Three different alphas on purpose, unlike every other ramp here: these are not stops along
        // one gradient but three separate circles, and they are lit to different strengths.
        actionAurora = listOf(
            aurora[0].copy(alpha = 0.42f),
            aurora[1].copy(alpha = 0.36f),
            aurora[2].copy(alpha = 0.62f),
        ),
        markAurora = aurora,
    )
}

private val DarkAccents = darkSeeds.mapValues { (_, seed) -> accent(seed, dark = true) }
private val LightAccents = lightSeeds.mapValues { (_, seed) -> accent(seed, dark = false) }

/**
 * The hues a card may take, beside the page's own.
 *
 * A page's cards are not one colour: a column of six identical headings is a column nobody scans,
 * and telling one card from another at the speed they are actually read is what these are for. The
 * **first card on a page takes the page's hue** - passing no accent is how - and the rest name one
 * of these, in this order, so the rhythm is the same on every page rather than a choice remade five
 * times.
 *
 * Chrome only, exactly as [PageAccent] is: a card hue reaches the tile behind an icon and the edge
 * down the card's left side, and never a figure on it. Every value clears 4.5:1 on both themes'
 * card fills, so one may be used for a label without a second check.
 */
enum class CardHue { BLUE, GREEN, AMBER, PINK, VIOLET, CYAN }

/**
 * This hue in the theme in force: **the one accent, whichever hue is named**, since the 2026-11
 * redesign. A Swiss page has one accent, so the six card hues now all resolve to the page's ink. The
 * enum and every call site naming a hue are kept, so a card's place in the rhythm is still stated
 * and a later design can give the hues back without touching a screen.
 */
@Suppress("UnusedReceiverParameter")
val CardHue.color: Color
    @Composable get() = LocalPageAccent.current.ink

/**
 * This hue as a **tile fill** in the 5.0 palette, which is what each card slot maps to now.
 *
 * Kept apart from [color] on purpose: [color] is read as text and as an edge by the screens that
 * predate 5.0 and has to hold 4.5:1, which lime, sunflower and sky never do on paper. A fill is read
 * with [AppPalette.onTile] on it - or [AppPalette.onHero] on [CardHue.BLUE] - and never as text.
 */
val CardHue.tile: Color
    @Composable get() = tileFor(this, LocalAppPalette.current)

/** [CardHue.tile] for a given palette, so a test can read it without a composition. */
internal fun tileFor(hue: CardHue, palette: AppPalette): Color = when (hue) {
    CardHue.BLUE -> palette.hero
    CardHue.GREEN -> palette.highlight
    CardHue.AMBER -> palette.tile1
    CardHue.PINK -> palette.alert
    CardHue.VIOLET -> palette.tile2
    CardHue.CYAN -> palette.tile3
}

/** What is drawn on [CardHue.tile]: white on the cobalt tile, navy ink on every other. */
internal fun onTileFor(hue: CardHue, palette: AppPalette): Color =
    if (hue == CardHue.BLUE) palette.onHero else palette.onTile

/** The accent for [key] in the theme in force. */
internal fun accentFor(key: AccentKey, dark: Boolean): PageAccent =
    (if (dark) DarkAccents else LightAccents).getValue(key)

/**
 * `primary` and `secondary`, replaced by the page's own hue.
 *
 * This is what makes the accent reach the whole page without being threaded through every screen:
 * `FilterChip`, `Checkbox`, `RadioButton`, the navigation indicator and `SectionCard`'s icon all
 * read these roles already, so swapping them here is the same change in twenty places at once.
 *
 * **Only these roles move.** `tertiary` is a target, `ExtraColors.loss` is a stop, `ExtraColors.market`
 * is a price the market reached and `onSurface` is an entry - none of them are touched, which is why
 * a page can have a hue at all without a figure changing meaning between two screens.
 */
internal fun ColorScheme.withAccent(accent: PageAccent): ColorScheme = copy(
    primary = accent.ink,
    onPrimary = accent.onInk,
    primaryContainer = accent.soft,
    onPrimaryContainer = accent.ink,
    secondary = accent.ink,
    onSecondary = accent.onInk,
    secondaryContainer = accent.soft,
    onSecondaryContainer = accent.ink,
)

/**
 * Provided by [EgxAnalyzerTheme], so these follow the app's own light/dark setting.
 *
 * Reading `isSystemInDarkTheme()` at the point of use would ignore a user who has forced light or
 * dark in Settings, and put a dark-theme amber on a light page.
 */
val LocalExtraColors = staticCompositionLocalOf { LightExtras }

/**
 * The page's own hue. Re-provided per destination by the shell.
 *
 * Analyze's by default rather than a null or a grey: anything drawn outside a destination - a sheet
 * raised over the shell, a dialog, a @Preview of one card - still has a whole accent to read from,
 * and cyan is the app's own voice, which is the right thing for something not on a page.
 */
val LocalPageAccent = staticCompositionLocalOf { accentFor(AccentKey.CYAN, dark = false) }

/**
 * Which theme is in force, as the app decided it rather than as the system reports it.
 *
 * [PageTheme] needs the answer to pick a page's hue, and it cannot ask `isSystemInDarkTheme()` for
 * the reason [LocalExtraColors] exists: a user who has forced light or dark in Settings would get
 * the other theme's accents on their page.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** The extra roles for the theme in force, beside `MaterialTheme.colorScheme`. */
val extraColors: ExtraColors
    @Composable get() = LocalExtraColors.current

/** The hue of the page being drawn. */
val pageAccent: PageAccent
    @Composable get() = LocalPageAccent.current

/**
 * M3 Expressive's soft containers (2026-11 redesign): anything that holds or is pressed is rounded
 * generously, while information sits flat on the page in rows divided by hairlines.
 *
 * What follows is the reasoning of the earlier, squarer set, kept for history:
 * tighter than the M3 default from `medium` up - 12/16/28 there against 10/14/18 here.
 *
 * These screens are card-dense, and a large radius repeated down a column of cards curves away more
 * of each card's own top row than it settles. Squarer corners let a stack read as a list rather than
 * as a pile of lozenges, and the page well and the navigation pill - both on `large` - stop reading
 * as capsules laid over the chrome.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun EgxAnalyzerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    pureBlackDarkMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // **The system bars read this app's theme, not the phone's.** The window is edge to edge and
    // the page now runs up behind the status bar, so the platform's clock and battery are drawn on
    // whatever this app has painted there and have to be legible against it.
    //
    // `enableEdgeToEdge()` with no arguments follows `isSystemInDarkTheme` - the *system's* setting
    // - which is right until somebody uses the app's own Theme setting. Forcing Light on a phone
    // set to dark left white glyphs on a near-white page: an invisible clock, and no way to tell
    // from the code that anything was wrong. `themeMode` is resolved a few lines up and is the only
    // answer that is ever correct here.
    //
    // The activity comes from the composition rather than a parameter, the way the fold does in
    // `EgxAnalyzerApp`: null in a `@Preview`, which simply means there is no window to set.
    val activity = LocalContext.current as? Activity
    if (activity != null) {
        SideEffect {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    CompositionLocalProvider(
        LocalExtraColors provides if (darkTheme) DarkExtras else LightExtras,
        LocalAppPalette provides when {
            !darkTheme -> LightPalette
            pureBlackDarkMode -> BlackPalette
            else -> DarkPalette
        },
        LocalDarkTheme provides darkTheme,
        // The shell re-provides this per destination; this is what everything outside one gets.
        LocalPageAccent provides accentFor(AccentKey.CYAN, darkTheme),
    ) {
        MaterialTheme(
            colorScheme = when {
                !darkTheme -> LightColors
                pureBlackDarkMode -> BlackColors
                else -> DarkColors
            },
            shapes = AppShapes,
            typography = rememberAppTypography(),
        ) {
            // Text outside a tile or a surface takes the palette's ink, not Material's black: in
            // dark mode that default drew names and headings black on the graphite page.
            CompositionLocalProvider(LocalContentColor provides palette.ink, content = content)
        }
    }
}

/**
 * The same theme, with one destination's hue in `primary` and `secondary`.
 *
 * Wrapped around a page rather than folded into [EgxAnalyzerTheme], because the shell draws chrome
 * that is not on any one page - the header's status line, a sheet over the top - and that chrome
 * should not take the hue of whatever happens to be behind it.
 */
@Composable
fun PageTheme(key: AccentKey, content: @Composable () -> Unit) {
    val accent = accentFor(key, LocalDarkTheme.current)
    CompositionLocalProvider(LocalPageAccent provides accent) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.withAccent(accent),
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
