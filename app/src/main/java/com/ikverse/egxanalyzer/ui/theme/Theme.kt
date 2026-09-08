package com.ikverse.egxanalyzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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
private val DarkColors = darkColorScheme(
    // Overwritten per page by withAccent. The values here are Analyze's, so anything drawn outside
    // a destination - a sheet, a preview, a dialog raised by the shell - still gets a whole scheme
    // rather than a hole where one role should be.
    primary = Color(0xFF35D7F2),
    onPrimary = Color(0xFF04222A),
    primaryContainer = Color(0xFF004E5A),
    onPrimaryContainer = Color(0xFFB3ECF8),
    secondary = Color(0xFF35D7F2),
    onSecondary = Color(0xFF04222A),
    secondaryContainer = Color(0x2935D7F2),
    onSecondaryContainer = Color(0xFF35D7F2),
    // Deeper than the mint it replaces. A target and a price the market reached appear on one card,
    // and the old green sat close enough to the old cyan that the two read as one colour. Taken up
    // again in saturation with the rest of the signals: the page under these figures now carries a
    // blue-violet cast rather than a flat slate, and a signal that stays put while its ground warms
    // is a signal going quiet.
    tertiary = Color(0xFF2FE39B),
    onTertiary = Color(0xFF00391F),
    tertiaryContainer = Color(0xFF11512F),
    onTertiaryContainer = Color(0xFF89FAB5),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF4E0002),
    errorContainer = Color(0xFF6E1512),
    onErrorContainer = Color(0xFFFFDAD5),
    // A blue-violet cast rather than the near-hueless slate these were. The greys were most of why
    // a page of coloured cards still read as washed out: every accent on it was sitting on a ground
    // that had no opinion at all.
    background = Color(0xFF0A0E18),
    onBackground = Color(0xFFE8ECF4),
    surface = Color(0xFF0A0E18),
    onSurface = Color(0xFFE8ECF4),
    surfaceVariant = Color(0xFF1E2738),
    onSurfaceVariant = Color(0xFF9AA7BD),
    surfaceContainerLowest = Color(0xFF06090F),
    surfaceContainerLow = Color(0xFF101724),
    surfaceContainer = Color(0xFF141C2C),
    surfaceContainerHigh = Color(0xFF1C2638),
    surfaceContainerHighest = Color(0xFF243046),
    outline = Color(0xFF6B7684),
    outlineVariant = Color(0xFF2A364B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00788E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB0ECFB),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF00788E),
    onSecondary = Color.White,
    secondaryContainer = Color(0x2100788E),
    onSecondaryContainer = Color(0xFF00788E),
    tertiary = Color(0xFF00804F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9DF6BC),
    onTertiaryContainer = Color(0xFF00210F),
    error = Color(0xFFC42D3F),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF5F7FE),
    onBackground = Color(0xFF121926),
    surface = Color(0xFFF5F7FE),
    onSurface = Color(0xFF121926),
    surfaceVariant = Color(0xFFDCE3F2),
    onSurfaceVariant = Color(0xFF55617A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4FD),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDF1FC),
    surfaceContainerHighest = Color(0xFFE4EAF8),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFD7DFF0),
)

/**
 * The signals Material's roles have no slot left for.
 *
 * Every scheme colour above is already spoken for: green is a target, red is a stop, the greys are
 * context, and `primary` is now the page speaking rather than the app. A position that ran out of
 * time is none of those - it is waiting on the user, and it can perfectly well be up 5%, so
 * borrowing red would report a loss the trade never made.
 *
 * Amber is added rather than reassigned for the reason the palette is fixed in the first place: a
 * role that already means something must go on meaning it.
 *
 * **What used to live here and no longer does**: the action's fill and edge, the mark's aurora and
 * the Ask AI pill's fill. All four are the page's hue now, so they moved to [PageAccent]. What
 * stayed is what is true on every page at once.
 */
data class ExtraColors(
    /** Text, labels, and the outline a card gets when the trade on it has run out of time. */
    val expired: Color,
    val expiredContainer: Color,
    val onExpiredContainer: Color,

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

    /** The label on an [PageAccent.aiFill] pill. White in both themes: the pill runs dark either way. */
    val aiOnFill: Color,

    /**
     * The hairline the action wears while a run is going, in place of the neutral outline.
     *
     * One value for both themes, for the same reason [aiOnFill] is white in both: the fill under it
     * runs dark either way. Material's light-theme `error` on that fill is a dark line on a dark
     * field and reports nothing.
     */
    val aiStop: Color,

    /** The model's own text, where a label rather than a pill has to say a model produced this. */
    val aiText: Color,

    /**
     * The halo under the pill. Kept bright: a dark glow on a dark page is not a glow.
     *
     * Violet on every page, where the pill's fill now ends in the page's hue. The halo is the model
     * announcing itself and the fill is where you asked it from - see [PageAccent.aiFill].
     */
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

/** Gold, on a dark pill. The one place this app spends a colour on delight rather than meaning. */
private val BrightGold = listOf(Color(0xFFFFE9B8), Color(0xFFFFD37A), Color(0xFFE8A53C))

/** Where every Ask AI gradient starts, on every page. See [PageAccent.aiFill]. */
private val DarkAiViolet = Color(0xFF6E5BE8)
private val LightAiViolet = Color(0xFF4A3BB8)

internal val DarkExtras = ExtraColors(
    expired = Color(0xFFFFB74D),
    expiredContainer = Color(0xFF5A4318),
    onExpiredContainer = Color(0xFFFFE0A3),
    market = Color(0xFF5AA9FF),
    aiOnFill = Color.White,
    aiStop = Color(0xFFFF6B7A),
    aiText = Color(0xFFB9A3F2),
    aiGlow = Color(0xFF845FD6),
    aiSpark = BrightGold,
    // The card is dark here too, so the mark needs no softening.
    aiSparkOnCard = BrightGold,
)

internal val LightExtras = ExtraColors(
    expired = Color(0xFF9A6206),
    expiredContainer = Color(0xFFFFDFA6),
    onExpiredContainer = Color(0xFF2A1A00),
    // Darkened rather than reused. The dark theme's blue comes out at 2.4:1 on a light card, where
    // these figures are actually drawn, against the 4.5:1 body text needs - and every one of them
    // is a price.
    market = Color(0xFF1668C7),
    aiOnFill = Color.White,
    aiStop = Color(0xFFFF6B7A),
    aiText = Color(0xFF55338F),
    aiGlow = Color(0xFF653B96),
    aiSpark = BrightGold,
    // Bright gold on a near-white card is barely there. Deepened until it draws at 3:1 on it.
    aiSparkOnCard = listOf(Color(0xFFD9A44E), Color(0xFFC2872A), Color(0xFF9E6A17)),
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
    /** Ink drawn on top of a filled [base] - a checkbox tick, a filled chip. */
    val onBase: Color,
    /** The tile behind a section icon, a selected chip, the navigation indicator. */
    val soft: Color,
    /** A card's accent edge, and the hairline on a selected control. */
    val line: Color,
    /** The top of the page, faded to nothing. An arrival cue, gone as soon as anything is scrolled. */
    val wash: Color,

    /**
     * The screen's own action, in the page's hue.
     *
     * Every stop carries alpha **0.84** in the colour itself, ten points under the navigation bar's
     * own 0.94 - and the gap is deliberate. The bar tidies itself away while a page is read; the
     * action does not, so it is a permanent object over a page still being scrolled, and at the
     * bar's opacity it read as a slab parked on the page rather than as a control floating above
     * it. [actionAuroraBase] carries the same figure, so the button does not change weight the
     * moment a run starts.
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
     * **Lighter-handed than the ground it surrounds**: 0.74 against [actionFill]'s 0.84. It shipped
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
     * purpose: they land over a ground at 0.84, and anything stronger would make the busy parts of
     * the sweep read as more solid than the bar beneath it.
     */
    val actionAuroraBase: Color,
    val actionAurora: List<Color>,

    /**
     * The same aurora, painted through the app's own mark in the header and the rail.
     *
     * **Opaque, where [actionAurora] is not**, and that is the one difference. Those stops are three
     * soft circles drifting over a ground at 0.84, so they are kept low. These are painted *through*
     * a 24dp glyph with `SrcIn`: there is nothing behind them to show through, and a mark at two
     * thirds strength is simply a dimmer mark. `ActionPaletteTest` pins both halves of that.
     */
    val markAurora: List<Color>,

    /**
     * The model speaking, running into the page it was asked from.
     *
     * Violet at the first stop on every page, because the thing being announced is the same thing
     * every time - and then it travels into this page's hue, so a pill pressed on Insights lands in
     * rose and one opened from a Portfolio sheet lands in indigo.
     *
     * That movement is also what keeps the pill legible on the one page whose own hue is violet.
     * A flat violet page and a flat violet pill would be the same colour saying two things; a pill
     * that is the only object *travelling out of* violet is still unmistakably itself.
     *
     * Runs dark in both themes, so [ExtraColors.aiOnFill] can be white in both.
     */
    val aiFill: List<Color>,

    /**
     * The same family drawn as a line, for the button that only reopens a saved answer.
     *
     * Its own stops rather than [aiFill]'s, because an outline has to contrast with the card behind
     * it while a fill has to contrast with the label on top of it - opposite requirements on a dark
     * theme, where the fill goes darker than the card and the line has to go lighter.
     */
    val aiLine: List<Color>,
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
 * Analyze keeps the teal the action already wore; the other four are new.
 *
 * The light column is not the dark one reused. Every one of these is drawn on a near-white page
 * there and on a near-black one here, so `ink` darkens, `onBase` flips to white, and the edge hues
 * run dark where the dark theme's run light.
 */
private val darkSeeds = mapOf(
    AccentKey.CYAN to AccentSeed(
        base = Color(0xFF35D7F2), ink = Color(0xFF35D7F2), onBase = Color(0xFF04222A),
        neighbour = Color(0xFF5AA9FF), deep = Color(0xFF004E5A), mid = Color(0xFF0B6D7F),
        ground = Color(0xFF072A3E),
    ),
    AccentKey.VIOLET to AccentSeed(
        base = Color(0xFF9B7CFF), ink = Color(0xFF9B7CFF), onBase = Color(0xFF160B33),
        neighbour = Color(0xFFC24A93), deep = Color(0xFF3A2E8C), mid = Color(0xFF5B45D6),
        ground = Color(0xFF1A1042),
    ),
    AccentKey.ROSE to AccentSeed(
        base = Color(0xFFF2569C), ink = Color(0xFFF2569C), onBase = Color(0xFF33091D),
        neighbour = Color(0xFF9B7CFF), deep = Color(0xFF7A1F4C), mid = Color(0xFFB93372),
        ground = Color(0xFF2A0A1B),
    ),
    AccentKey.INDIGO to AccentSeed(
        base = Color(0xFF5C7CFA), ink = Color(0xFF5C7CFA), onBase = Color(0xFF0A1440),
        neighbour = Color(0xFF9B7CFF), deep = Color(0xFF23306E), mid = Color(0xFF3A4FB5),
        ground = Color(0xFF0D1436),
    ),
    AccentKey.BRONZE to AccentSeed(
        base = Color(0xFFD9A24A), ink = Color(0xFFD9A24A), onBase = Color(0xFF2A1A00),
        neighbour = Color(0xFFFFD37A), deep = Color(0xFF5A3A0C), mid = Color(0xFF8A5A18),
        ground = Color(0xFF2A1A06),
    ),
)

private val lightSeeds = mapOf(
    AccentKey.CYAN to AccentSeed(
        base = Color(0xFF0090AA), ink = Color(0xFF00788E), onBase = Color.White,
        neighbour = Color(0xFF1668C7), deep = Color(0xFF00414C), mid = Color(0xFF0A5C6C),
        ground = Color(0xFF05303F),
    ),
    AccentKey.VIOLET to AccentSeed(
        base = Color(0xFF6B4EE0), ink = Color(0xFF5B41C9), onBase = Color.White,
        neighbour = Color(0xFFC93379), deep = Color(0xFF2E2278), mid = Color(0xFF4A3BB8),
        ground = Color(0xFF170F3A),
    ),
    AccentKey.ROSE to AccentSeed(
        base = Color(0xFFC93379), ink = Color(0xFFB32C6C), onBase = Color.White,
        neighbour = Color(0xFF6B4EE0), deep = Color(0xFF6B1440), mid = Color(0xFF9E2258),
        ground = Color(0xFF260A18),
    ),
    AccentKey.INDIGO to AccentSeed(
        base = Color(0xFF3D5BD9), ink = Color(0xFF3450C4), onBase = Color.White,
        neighbour = Color(0xFF6B4EE0), deep = Color(0xFF1B2660), mid = Color(0xFF2C3E9E),
        ground = Color(0xFF0C1236),
    ),
    AccentKey.BRONZE to AccentSeed(
        base = Color(0xFFB4700A), ink = Color(0xFF96600A), onBase = Color.White,
        neighbour = Color(0xFFB98511), deep = Color(0xFF4A2E00), mid = Color(0xFF7A4A00),
        ground = Color(0xFF241703),
    ),
)

/** How far the edge's hues are taken down from the aurora's own. About a third, in both themes. */
private const val EdgeDim = 0.66f

/** The ground the action floats at, and the figure [PageAccent.actionAuroraBase] matches. */
private const val ActionAlpha = 0.84f

/** A step lighter-handed than the ground it surrounds. See [PageAccent.actionLine]. */
private const val EdgeAlpha = 0.74f

private fun Color.dim(factor: Float) = lerp(Color.Black, this, factor)

private fun accent(seed: AccentSeed, dark: Boolean, aiStart: Color): PageAccent {
    // The aurora's three, and the one list the mark and the action both read from - the test that
    // pins them equal is pinning that this stays one list.
    val aurora = listOf(seed.base, seed.neighbour, seed.mid)
    return PageAccent(
        base = seed.base,
        ink = seed.ink,
        onBase = seed.onBase,
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
        aiFill = listOf(aiStart, lerp(aiStart, seed.mid, 0.5f), seed.mid),
        // Lighter than the fill on the dark theme and darker on the light one, because the line has
        // to hold against the card while the fill has to hold up a white label.
        aiLine = if (dark) {
            listOf(lerp(aiStart, Color.White, 0.28f), lerp(aiStart, seed.base, 0.5f), seed.base)
        } else {
            listOf(aiStart, lerp(aiStart, seed.mid, 0.5f), seed.mid)
        },
    )
}

private val DarkAccents = darkSeeds.mapValues { (_, seed) -> accent(seed, dark = true, DarkAiViolet) }
private val LightAccents = lightSeeds.mapValues { (_, seed) -> accent(seed, dark = false, LightAiViolet) }

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

private val DarkCardHues = mapOf(
    CardHue.BLUE to Color(0xFF5AA9FF),
    CardHue.GREEN to Color(0xFF2FE39B),
    CardHue.AMBER to Color(0xFFFFB74D),
    CardHue.PINK to Color(0xFFFF5FA2),
    CardHue.VIOLET to Color(0xFF9B7CFF),
    CardHue.CYAN to Color(0xFF35D7F2),
)

/**
 * Not the dark theme's values reused.
 *
 * These are drawn on a near-white card, where the dark theme's come out between 1.5:1 and 2.5:1 -
 * the same argument that darkened `market` for the light theme, applied to every hue at once.
 */
private val LightCardHues = mapOf(
    CardHue.BLUE to Color(0xFF1668C7),
    CardHue.GREEN to Color(0xFF00804F),
    CardHue.AMBER to Color(0xFF9A6206),
    CardHue.PINK to Color(0xFFB32C6C),
    CardHue.VIOLET to Color(0xFF5B41C9),
    CardHue.CYAN to Color(0xFF00788E),
)

/** This hue in the theme in force. */
val CardHue.color: Color
    @Composable get() = (if (LocalDarkTheme.current) DarkCardHues else LightCardHues).getValue(this)

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
 * **Only these roles move.** `tertiary` is a target, `error` is a stop, `ExtraColors.market` is a
 * price the market reached and `onSurface` is an entry - none of them are touched, which is why a
 * page can have a hue at all without a figure changing meaning between two screens.
 */
internal fun ColorScheme.withAccent(accent: PageAccent): ColorScheme = copy(
    primary = accent.ink,
    onPrimary = accent.onBase,
    primaryContainer = accent.soft,
    onPrimaryContainer = accent.ink,
    secondary = accent.ink,
    onSecondary = accent.onBase,
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
 * Tighter than the M3 default from `medium` up - 12/16/28 there against 10/14/18 here.
 *
 * These screens are card-dense, and a large radius repeated down a column of cards curves away more
 * of each card's own top row than it settles. Squarer corners let a stack read as a list rather than
 * as a pile of lozenges, and the page well and the navigation pill - both on `large` - stop reading
 * as capsules laid over the chrome.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
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
    CompositionLocalProvider(
        LocalExtraColors provides if (darkTheme) DarkExtras else LightExtras,
        LocalDarkTheme provides darkTheme,
        // The shell re-provides this per destination; this is what everything outside one gets.
        LocalPageAccent provides accentFor(AccentKey.CYAN, darkTheme),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
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
