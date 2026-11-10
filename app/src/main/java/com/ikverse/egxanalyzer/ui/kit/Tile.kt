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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.pressScale
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
