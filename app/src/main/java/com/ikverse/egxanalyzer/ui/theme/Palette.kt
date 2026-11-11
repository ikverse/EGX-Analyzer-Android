package com.ikverse.egxanalyzer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The 5.0 palette, as the owner chose it on 2026-11-09 from the colour board on the design canvas.
 *
 * The B+ design is paper and ink with five tile colours. **A tile colour is a fill, never text**:
 * lime, coral, sunflower, lilac and sky are all too pale to read on paper, so anything drawn *on* one
 * of them is [onTile] (navy ink), and anything that has to be read as text on the page uses [ink],
 * [hero] or one of the two signals.
 *
 * Gain and loss are the only colours that mean money. Nothing else in the app may be drawn in them,
 * and they are never a tile fill: a lime "hit rate" tile is a highlight, not a gain.
 */
data class AppPalette(
    /** The page itself. Warm paper in light, Graphite in dark, true black in the pure-black mode. */
    val ground: Color,
    /** A card or tile that holds content without a colour of its own. White on paper. */
    val card: Color,
    /** A pressable thing sitting on a card: chip, secondary button, stepper. */
    val raised: Color,
    /** Hairlines and dividers. */
    val line: Color,
    /** Text and the dark tiles (the Today tile, a primary button). */
    val ink: Color,
    /** Text on [ink]. */
    val onInk: Color,
    /** Secondary text. Holds 4.5:1 on [ground] and [card]. */
    val muted: Color,
    /** The unselected tab names and other text that is deliberately quiet. */
    val faint: Color,

    /** The big top tile, charts, links and the selected state. Electric cobalt. */
    val hero: Color,
    /** Text on [hero]. */
    val onHero: Color,
    /** [hero] where it is read as text on [ground] or [card]: links, a selected label. */
    val heroInk: Color,

    /** Selection, good news, the chart line on [hero]. Acid lime. */
    val highlight: Color,
    /** "Needs you", falling stocks, a stop on a chart. Coral. */
    val alert: Color,
    /** Tile 1: best source, stock logos. Sunflower. */
    val tile1: Color,
    /** Tile 2: AI answers, stock logos. Lilac. */
    val tile2: Color,
    /** Tile 3: closed trades, stock logos. Sky. */
    val tile3: Color,
    /** Text on any of [highlight], [alert], [tile1], [tile2], [tile3]. Navy ink in both themes. */
    val onTile: Color,

    /**
     * The Home page's "Your book" tile while the open trades are ahead. Deep teal, with white on it.
     * These three are fills that follow the sign of one figure, added on 2026-11-12 when the owner
     * asked for something calmer than lime there; they are never text and never a gain or a loss.
     */
    val bookUp: Color,
    /** The same tile while the open trades are flat. Soft sage, with navy ink on it. */
    val bookFlat: Color,
    /** The same tile while the open trades are behind. Maroon, with white on it. */
    val bookDown: Color,

    /** Profit figures and bars. */
    val gain: Color,
    /** Losses and stops. */
    val loss: Color,

    /** The price ladder's stretch from the entry down to the stop. A fill under a line, never text. */
    val zoneLoss: Color,
    /** The ladder's stretch from the entry to target 1. */
    val zoneNear: Color,
    /** The ladder's stretch from target 1 to target 2, a step deeper than [zoneNear]. */
    val zoneFar: Color,
)

/** Navy ink, the one text colour on every tile fill in both themes. */
private val NavyInk = Color(0xFF101828)

internal val LightPalette = AppPalette(
    ground = Color(0xFFEFEDE6),
    card = Color(0xFFFFFFFF),
    raised = Color(0xFFE2DFD5),
    line = Color(0xFFD9D5CA),
    ink = NavyInk,
    onInk = Color(0xFFC8FF4D),
    muted = Color(0xFF5C5A54),
    // 3.9:1 on paper: for the unselected tab names, which are 20sp and count as large text. Never
    // for body copy; that is [muted].
    faint = Color(0xFF7A766D),
    hero = Color(0xFF1E2BFF),
    onHero = Color(0xFFFFFFFF),
    heroInk = Color(0xFF1E2BFF),
    highlight = Color(0xFFC8FF4D),
    alert = Color(0xFFFFB8A3),
    tile1 = Color(0xFFFFD12E),
    tile2 = Color(0xFFE2D4FF),
    tile3 = Color(0xFFBFE3FF),
    onTile = NavyInk,
    bookUp = Color(0xFF1F5F5B),
    bookFlat = Color(0xFFDCE6D2),
    bookDown = Color(0xFF6E1B27),
    // Forest green, one step darker than the #0B8A4A on the colour board: that shade is 3.8:1 on
    // paper and gain figures are often small text, which needs 4.5. This one is 4.6:1 on paper.
    gain = Color(0xFF087A40),
    loss = Color(0xFFC62020),
    zoneLoss = Color(0xFFF6C9C2),
    zoneNear = Color(0xFFCFEBDB),
    zoneFar = Color(0xFF9ED9B8),
)

internal val DarkPalette = AppPalette(
    ground = Color(0xFF141518),
    card = Color(0xFF1F2228),
    raised = Color(0xFF262A31),
    line = Color(0xFF3A3F47),
    ink = Color(0xFFE6EAF2),
    onInk = NavyInk,
    muted = Color(0xFFA3A8B3),
    // 4.9:1 on a card. The #6B6F78 it replaced was 3.2:1, under even the large-text bar.
    faint = Color(0xFF8A8F99),
    // A shade brighter than the light theme's cobalt, so the hero tile still carries white text
    // while sitting visibly apart from Graphite.
    hero = Color(0xFF2E3BFF),
    onHero = Color(0xFFFFFFFF),
    // Cobalt itself is 2.4:1 on Graphite; links and selected labels need the lifted shade.
    heroInk = Color(0xFF8E97FF),
    highlight = Color(0xFFC8FF4D),
    alert = Color(0xFFFFB8A3),
    tile1 = Color(0xFFFFD12E),
    tile2 = Color(0xFFE2D4FF),
    tile3 = Color(0xFFBFE3FF),
    onTile = NavyInk,
    // The same three fills at night, as every other tile colour is: a tile keeps its colour and
    // the ground changes under it.
    bookUp = Color(0xFF1F5F5B),
    bookFlat = Color(0xFFDCE6D2),
    bookDown = Color(0xFF6E1B27),
    gain = Color(0xFF4ADE80),
    loss = Color(0xFFFF4D4D),
    zoneLoss = Color(0xFF6B2A26),
    zoneNear = Color(0xFF245C3E),
    zoneFar = Color(0xFF3A8F63),
)

/** [DarkPalette] on true black. Only the grounds move; every signal and tile is copied verbatim. */
internal val BlackPalette = DarkPalette.copy(
    ground = Color(0xFF000000),
    card = Color(0xFF14161A),
    raised = Color(0xFF1E2126),
    line = Color(0xFF2C3038),
)

/** Provided by [EgxAnalyzerTheme], so it follows the app's own light/dark setting. */
val LocalAppPalette = staticCompositionLocalOf { LightPalette }

/** The palette for the theme in force. */
val palette: AppPalette
    @Composable get() = LocalAppPalette.current
