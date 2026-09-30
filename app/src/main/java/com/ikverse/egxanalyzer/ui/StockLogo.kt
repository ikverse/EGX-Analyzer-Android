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
 * The ticker is cleaned before it is used for either half, so a stock reached as `COMI.CA` and the
 * same stock reached as `COMI` are one disc in one colour rather than two.
 */
@Composable
private fun MonogramLogo(
    ticker: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val cleaned = ticker.trim().uppercase().removeSuffix(".CA")
    Monogram(cleaned.take(2), seed = cleaned, size = size, modifier = modifier)
}

/**
 * The source that made a call, drawn as a disc beside its name.
 *
 * The ranking on Insights is a list of sources and was the one list of things in this app with no
 * mark against them, while every stock anywhere gets [StockLogo]. Telegram's own picture is what it
 * draws where there is one, out of the same cache `ChannelsSection` reads - so the card that judges
 * a channel shows the face the picker that chose it already showed.
 *
 * **The fallback is not a nicety here, it is the ordinary case.** A path points into Telegram's
 * storage, which prunes itself; a report synced from the other phone names channels this device may
 * never have been signed in to; and a run imported on-device has no chat behind it at all. All
 * three arrive as a null path and all three are drawn as the channel's own initials, which is the
 * same answer [MonogramLogo] gives for the one company with no bundled logo.
 *
 * No `contentDescription`: the channel's name is immediately beside this everywhere it is drawn.
 */
@Composable
internal fun ChannelAvatar(
    channel: String,
    photoPath: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val photo = rememberTelegramImage(photoPath, maxPixels = ChannelAvatarPixels)
    if (photo == null) {
        // Not uppercased, unlike a ticker: these names are usually Arabic, which has no case, and
        // uppercasing a Latin channel name would make it shout where the picture it stands in for
        // would not.
        val cleaned = channel.trim()
        Monogram(cleaned.take(2), seed = cleaned, size = size, modifier = modifier)
        return
    }
    Image(
        bitmap = photo,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            // The same hairline [StockLogo] gives a mark drawn on white, and for the same reason: a
            // profile photo with a pale background has no edge against the light theme's surface.
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

/**
 * Enough for a disc this size on a 420dpi screen, and no more.
 *
 * A chat photo is a full-size image in Telegram's cache and every card in the ranking decodes one,
 * so this is the figure that decides what a scroll costs. `ChannelsSection` decodes at its own
 * size for the same reason.
 */
private const val ChannelAvatarPixels = 128

/**
 * A name's first letters on a disc of its own colour.
 *
 * Shared by the two things that have no picture to draw - a company with no bundled logo and a
 * channel whose photo Telegram has pruned - because they are one object with two callers rather
 * than two that happen to look alike. [seed] is separate from [text] so the colour is stable across
 * the whole identity rather than across the two letters that happen to show: two channels opening
 * on the same word would otherwise be the same colour.
 *
 * Lightness is fixed rather than taken from the theme: one mid-dark disc with white letters clears
 * contrast on both the dark surface and the light one, where a value tuned to either would fail on
 * the other.
 */
@Composable
private fun Monogram(
    text: String,
    seed: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(monogramColor(seed))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            // Sized from the circle rather than from the type scale, so one composable serves every
            // size it is drawn at without a table of font sizes to keep in step with them.
            fontSize = (size.value * 0.34f).sp,
            lineHeight = (size.value * 0.34f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/**
 * A stable colour for a name.
 *
 * [String.hashCode] rather than a random or a rotating index: the same subject has to land on the
 * same colour in the table, on its card and in the sheet, and those are separate compositions with
 * no shared counter between them.
 */
private fun monogramColor(seed: String): Color {
    val hue = ((seed.hashCode() % 360) + 360) % 360
    return Color.hsl(hue.toFloat(), saturation = 0.45f, lightness = 0.34f)
}
