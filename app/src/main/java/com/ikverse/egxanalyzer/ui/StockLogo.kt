package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How big a logo is drawn: as tall as the ticker line it sits on, rounded to 4dp.
 *
 * Two sizes because there are two ticker styles, and the rule is the whole of it. It was four, and
 * the rule was "as tall as the name block" - which put a 40dp logo beside 82dp of names and left
 * 42dp of nothing under it, on every card. Worse, a logo beside the block indents the block: the
 * names started 52dp in while the ladder, the levels and the buttons under them started at the
 * card's edge, so one card had two left edges. On the ticker's own line it has neither problem, and
 * no card is taller than it was before logos existed.
 *
 * Named for the type style rather than the surface, because that is what actually decides the
 * number - change the typography and these are what to re-measure.
 */
internal object LogoSize {
    /** Beside a 27dp `headlineSmall` ticker: the results card header, and the opinion sheet. */
    val Header: Dp = 28.dp

    /** Beside a `titleMedium` or `titleSmall` ticker: table rows, trade tiles, and cards. */
    val Row: Dp = 24.dp
}

/**
 * The company's mark, drawn as a circle beside its name.
 *
 * Bundled rather than fetched. Every screen that lists stocks is a list - a logo arriving over the
 * network would pop in row by row on each scroll, and the app is regularly opened on a phone with
 * no signal to read a report that is already synced. 222 of the 223 companies in the catalog have
 * one; see [StockLogos].
 *
 * The 223rd, and any ticker a later catalog refresh introduces, gets [MonogramLogo] instead. That
 * fallback is not a nicety: `EgxCatalog` merges a remote list into its seeds, so the set of tickers
 * the app can show is open-ended, and without it those rows would draw a hole where every other row
 * has a mark.
 *
 * No `contentDescription`: the ticker and the company name sit immediately beside this in all six
 * places it is used, so a reader announcing the logo as well would say the company twice.
 */
@Composable
internal fun StockLogo(
    ticker: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val logo = StockLogos.forTicker(ticker)
    if (logo == null) {
        MonogramLogo(ticker, size, modifier)
        return
    }
    Image(
        painter = painterResource(logo),
        contentDescription = null,
        // Every logo is square at source, so this crops nothing; it is here to stop a non-square
        // one added later from being stretched into the circle.
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            // Many of these marks are drawn on white. Against the light theme's near-white surface
            // that leaves them floating with no edge, so the same hairline the cards use gives them
            // one. On a logo that fills its square in colour it is invisible.
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

/**
 * The stand-in for a company with no bundled logo: its first two letters on a coloured disc.
 *
 * The colour comes from the ticker, so a given company is always the same colour and two adjacent
 * rows are reliably different - which is the only job it has, since the letters already carry the
 * identity. Lightness is fixed rather than taken from the theme: one mid-dark disc with white
 * letters clears contrast on both the dark surface and the light one, where a value tuned to either
 * would fail on the other.
 */
@Composable
private fun MonogramLogo(
    ticker: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val cleaned = ticker.trim().uppercase().removeSuffix(".CA")
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(monogramColor(cleaned))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            cleaned.take(2),
            // Sized from the circle rather than from the type scale, so one composable serves all
            // four of [LogoSize] without a table of font sizes to keep in step with them.
            fontSize = (size.value * 0.34f).sp,
            lineHeight = (size.value * 0.34f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/**
 * A stable colour for a ticker.
 *
 * [String.hashCode] rather than a random or a rotating index: the same ticker has to land on the
 * same colour in the table, on its card and in the sheet, and those are three separate compositions
 * with no shared counter between them.
 */
private fun monogramColor(ticker: String): Color {
    val hue = ((ticker.hashCode() % 360) + 360) % 360
    return Color.hsl(hue.toFloat(), saturation = 0.45f, lightness = 0.34f)
}
