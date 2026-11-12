package com.ikverse.egxanalyzer.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.pressScale
import com.ikverse.egxanalyzer.ui.theme.AppPalette
import com.ikverse.egxanalyzer.ui.theme.palette

/** Which fill a [Tile] takes. Each names the palette colour and what is drawn on it. */
enum class TileTone { CARD, INK, HERO, HIGHLIGHT, ALERT, TILE1, TILE2, TILE3, BOOK_UP, BOOK_FLAT, BOOK_DOWN }

/** The fill and the content colour of [tone] in the palette in force. */
@Composable
fun tileColors(tone: TileTone): Pair<Color, Color> {
    val p = palette
    return when (tone) {
        TileTone.CARD -> p.card to p.ink
        TileTone.INK -> p.tileInk to p.onTileInk
        TileTone.HERO -> p.tileHero to p.onHero
        TileTone.HIGHLIGHT -> p.tileHighlight to p.onTileHighlight
        TileTone.ALERT -> p.tileAlert to p.onTileAlert
        TileTone.TILE1 -> p.tileOne to p.onTileOne
        TileTone.TILE2 -> p.tileTwo to p.onTileTwo
        TileTone.TILE3 -> p.tileThree to p.onTileThree
        TileTone.BOOK_UP -> p.bookUp to Color.White
        TileTone.BOOK_FLAT -> p.tileBookFlat to p.onTileBookFlat
        TileTone.BOOK_DOWN -> p.bookDown to Color.White
    }
}

/** The fill [tone] draws in [p], outside composition, so the contrast tests read what a tile shows. */
internal fun tileFill(p: AppPalette, tone: TileTone): Color = when (tone) {
    TileTone.CARD -> p.card
    TileTone.INK -> p.tileInk
    TileTone.HERO -> p.tileHero
    TileTone.HIGHLIGHT -> p.tileHighlight
    TileTone.ALERT -> p.tileAlert
    TileTone.TILE1 -> p.tileOne
    TileTone.TILE2 -> p.tileTwo
    TileTone.TILE3 -> p.tileThree
    TileTone.BOOK_UP -> p.bookUp
    TileTone.BOOK_FLAT -> p.tileBookFlat
    TileTone.BOOK_DOWN -> p.bookDown
}

/**
 * The money colours that read on one tile: profit, loss, and an accent for a figure or a mark.
 *
 * The palette's own [AppPalette.gain] and [AppPalette.loss] are tuned for the page, and a tile is
 * not the page. Coloured fills are pale in the light theme and take deep colours; they are deep in
 * the dark theme and take pale ones. On coral neither red nor green holds, so both are its text
 * colour and the sign carries the meaning.
 */
data class TileSignals(val gain: Color, val loss: Color, val accent: Color)

/** [TileSignals] for [tone] in the palette in force. */
@Composable
fun tileSignals(tone: TileTone): TileSignals = tileSignalsFor(palette, tone)

/** [tileSignals] for a given palette, outside composition, so the contrast test can sweep it. */
internal fun tileSignalsFor(p: AppPalette, tone: TileTone): TileSignals {
    // Read the fill rather than a flag, so the pure-black palette and any later one follow without
    // being named. A dark fill takes pale signals: the bright ones on the light theme's navy ink
    // tile, the book tile's paler ones on every deep fill of the dark theme, where the bright red
    // falls under 4.5:1. A pale fill takes deep ones, a step deeper unless it is as light as lime.
    val fill = tileFill(p, tone).luminance()
    val deep = fill < 0.2f
    val darkTheme = p.ink.luminance() > 0.5f
    val onPale = when {
        deep -> if (darkTheme) OnDeepFill else OnDarkFill
        fill > 0.8f -> OnPaleFill
        else -> OnPastelFill
    }
    return when (tone) {
        TileTone.CARD -> TileSignals(p.gain, p.loss, p.heroInk)
        TileTone.INK, TileTone.HIGHLIGHT, TileTone.TILE1, TileTone.TILE2, TileTone.TILE3, TileTone.BOOK_FLAT -> onPale
        // On coral neither red nor green holds in either theme, so all three are its own text.
        TileTone.ALERT -> TileSignals(p.onTileAlert, p.onTileAlert, p.onTileAlert)
        TileTone.HERO -> TileSignals(p.highlight, p.tile1, Color.White)
        TileTone.BOOK_UP, TileTone.BOOK_DOWN -> OnDeepFill
    }
}

/** The light theme's deep green, red and cobalt: 4.5:1 or better on every pale fill. */
private val OnPaleFill = TileSignals(Color(0xFF087A40), Color(0xFFC62020), Color(0xFF1E2BFF))

/**
 * Sunflower, lilac and sky are darker than lime, and the light theme's green and red fall to 3.7
 * to 4.3:1 on them. A step deeper holds 5:1 on sunflower, the worst of the three.
 */
private val OnPastelFill = TileSignals(Color(0xFF05642F), Color(0xFFA81B1B), Color(0xFF1E2BFF))

/**
 * Pale green and pale red for the deep teal and the maroon of the Home page's book tile: 4.8:1 or
 * better on both, where the page's own green and red disappear into either. The dark theme's deep
 * tile fills take the same three.
 */
private val OnDeepFill = TileSignals(Color(0xFFB9F5C8), Color(0xFFFFC2B8), Color.White)

/** The dark theme's bright green and red, and lime, for the light theme's navy ink tile. */
private val OnDarkFill = TileSignals(Color(0xFF4ADE80), Color(0xFFFF4D4D), Color(0xFFC8FF4D))

/**
 * The block every page is built from.
 *
 * A tile is information unless it is given [onClick], in which case it is one pressable thing and
 * gives under the finger like every other pressable. [LocalContentColor] is set to the tone's own
 * content colour, so text inside reads correctly on lime and on cobalt alike without each call site
 * choosing.
 */
@Composable
fun Tile(
    modifier: Modifier = Modifier,
    tone: TileTone = TileTone.CARD,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val (fill, onFill) = tileColors(tone)
    val press = remember { MutableInteractionSource() }
    val base = modifier
        .then(if (onClick != null) Modifier.pressScale(press) else Modifier)
        .clip(Kit.TileShape)
        .background(fill)
    val pressable = if (onClick != null) {
        base.clickable(
            interactionSource = press,
            indication = null,
            role = Role.Button,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
    } else {
        base
    }
    CompositionLocalProvider(LocalContentColor provides onFill) {
        Column(pressable.padding(contentPadding), content = content)
    }
}

/** A lighter group inside a tile or a sheet: a panel of rows, a figure grid. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = palette.raised.copy(alpha = 0.55f),
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.clip(Kit.PanelShape).background(color)) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}
