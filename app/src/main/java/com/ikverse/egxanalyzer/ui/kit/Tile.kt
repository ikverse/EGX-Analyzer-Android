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
enum class TileTone { CARD, INK, HERO, HIGHLIGHT, ALERT, TILE1, TILE2, TILE3 }

/** The fill and the content colour of [tone] in the palette in force. */
@Composable
fun tileColors(tone: TileTone): Pair<Color, Color> {
    val p = palette
    return when (tone) {
        TileTone.CARD -> p.card to p.ink
        TileTone.INK -> p.ink to p.ground
        TileTone.HERO -> p.hero to p.onHero
        TileTone.HIGHLIGHT -> p.highlight to p.onTile
        TileTone.ALERT -> p.alert to p.onTile
        TileTone.TILE1 -> p.tile1 to p.onTile
        TileTone.TILE2 -> p.tile2 to p.onTile
        TileTone.TILE3 -> p.tile3 to p.onTile
    }
}

/**
 * The money colours that read on one tile: profit, loss, and an accent for a figure or a mark.
 *
 * The palette's own [AppPalette.gain] and [AppPalette.loss] are tuned for the page, and a tile is
 * not the page. [TileTone.INK] swaps lightness with the theme - a dark tile in light, a light one
 * in dark - so the dark theme's bright green on it was 1.03:1. Every coloured fill is pale in both
 * themes and takes the light theme's deep colours. On coral neither red nor green holds, so both
 * are navy and the sign carries the meaning.
 */
data class TileSignals(val gain: Color, val loss: Color, val accent: Color)

/** [TileSignals] for [tone] in the palette in force. */
@Composable
fun tileSignals(tone: TileTone): TileSignals = tileSignalsFor(palette, tone)

/** [tileSignals] for a given palette, outside composition, so the contrast test can sweep it. */
internal fun tileSignalsFor(p: AppPalette, tone: TileTone): TileSignals {
    // A light ink tile is the dark theme's: read the fill rather than a flag, so the pure-black
    // palette and any later one follow without being named.
    val inkIsLight = p.ink.luminance() > 0.5f
    return when (tone) {
        TileTone.CARD -> TileSignals(p.gain, p.loss, p.heroInk)
        TileTone.INK -> if (inkIsLight) OnPaleFill else OnDarkFill
        TileTone.HIGHLIGHT -> OnPaleFill
        TileTone.TILE1, TileTone.TILE2, TileTone.TILE3 -> OnPastelFill
        TileTone.ALERT -> TileSignals(p.onTile, p.onTile, p.onTile)
        TileTone.HERO -> TileSignals(p.highlight, p.tile1, Color.White)
    }
}

/** The light theme's deep green, red and cobalt: 4.5:1 or better on every pale fill. */
private val OnPaleFill = TileSignals(Color(0xFF087A40), Color(0xFFC62020), Color(0xFF1E2BFF))

/**
 * Sunflower, lilac and sky are darker than lime, and the light theme's green and red fall to 3.7
 * to 4.3:1 on them. A step deeper holds 5:1 on sunflower, the worst of the three.
 */
private val OnPastelFill = TileSignals(Color(0xFF05642F), Color(0xFFA81B1B), Color(0xFF1E2BFF))

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
