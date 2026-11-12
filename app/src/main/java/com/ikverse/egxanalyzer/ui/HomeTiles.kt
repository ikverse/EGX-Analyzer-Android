package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.ui.graphics.graphicsLayer
import com.ikverse.egxanalyzer.ui.kit.KitIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.BookMood
import com.ikverse.egxanalyzer.model.Headline
import com.ikverse.egxanalyzer.model.IndexQuote
import com.ikverse.egxanalyzer.model.MarketBreadth
import com.ikverse.egxanalyzer.model.MarketBrief
import com.ikverse.egxanalyzer.model.MarketMovers
import com.ikverse.egxanalyzer.model.Mover
import com.ikverse.egxanalyzer.model.NeedReason
import com.ikverse.egxanalyzer.model.NeedsYouItem
import com.ikverse.egxanalyzer.model.OutsideQuote
import com.ikverse.egxanalyzer.model.PortfolioStats
import com.ikverse.egxanalyzer.model.SessionDigest
import com.ikverse.egxanalyzer.model.bookMood
import com.ikverse.egxanalyzer.model.headlineAge
import com.ikverse.egxanalyzer.model.leadHeadlines
import com.ikverse.egxanalyzer.ui.kit.BreadthBar
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.KitRule
import com.ikverse.egxanalyzer.ui.kit.KitSpinner
import com.ikverse.egxanalyzer.ui.kit.KitTag
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.rememberDrawIn
import com.ikverse.egxanalyzer.ui.kit.tileColors
import com.ikverse.egxanalyzer.ui.kit.tileSignals
import com.ikverse.egxanalyzer.ui.theme.palette
import java.util.Locale

/**
 * How far back the EGX30 tile's line reaches. The feed's hourly bars hold about three months.
 *
 * [DAY] is the newest session in five-minute bars - `IndexQuote.day` - and is offered only where
 * the feed answered with them; the other three are slices of the daily closes.
 */
enum class HomeRange(val label: String, val days: Long) {
    DAY("1D", 1), WEEK("1W", 7), MONTH("1M", 31), QUARTER("3M", 92),
}

/** An index level or an outside number as Home prints it: grouped, one decimal. */
internal fun formatLevel(value: Double, decimals: Int = 1): String =
    String.format(Locale.US, "%,.${decimals}f", value)

/**
 * The EGX30, leading the page: its level, its move on the day and its line over [rangeState].
 *
 * Drawn on the stock sheet's chart tile - navy in the light theme, the raised card at night - which
 * is the ground the owner asked for on 2026-11-12 in place of cobalt. It is not a kit tone, for the
 * reason [chartTileColors] gives, so this tile sets its own fill and content colour.
 *
 * @param compact tabletop's form: a shorter line and no range control, since the upright half is
 *   read and not pressed.
 */
@Composable
internal fun IndexHeroTile(
    quote: IndexQuote?,
    rangeState: MutableState<HomeRange>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val p = palette
    val c = remember(p) { chartTileColors(p) }
    // The same money colours the kit gives a dark tile and a card, whichever this is tonight.
    val signals = tileSignals(if (p.ink.luminance() < 0.5f) TileTone.INK else TileTone.CARD)
    var picked by rangeState
    // 1D needs the session's five-minute bars. A quote kept from before they were asked for, or
    // one the feed did not answer, has none: the option is absent and a stored 1D is a month.
    val dayCloses = remember(quote) { quote?.day.orEmpty().mapNotNull { it.close } }
    val options = remember(dayCloses) { HomeRange.entries.filter { it != HomeRange.DAY || dayCloses.size >= 2 } }
    val range = if (picked in options) picked else HomeRange.MONTH
    CompositionLocalProvider(LocalContentColor provides c.text) {
        Column(modifier.fillMaxWidth().clip(Kit.TileShape).background(c.surface).padding(vertical = 18.dp)) {
            Text(
                "EGX30" + (quote?.closes?.lastOrNull()?.let { " · ${shortDate(it.date)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = c.soft,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (quote == null) {
                Text(
                    "—",
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = TabularFigures),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    "Pull down to fetch the market.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.soft,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                return@Column
            }
            Text(
                formatLevel(quote.level),
                style = (if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium)
                    .copy(fontFamily = TabularFigures),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            val change = quote.change
            val percent = quote.changePercent
            if (change != null && percent != null) {
                Text(
                    "${if (change >= 0) "▲" else "▼"} ${formatLevel(kotlin.math.abs(change))} · ${formatPercent(percent)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures),
                    color = if (change >= 0) signals.gain else signals.loss,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            val last = quote.closes.lastOrNull()?.date
            val shown = remember(quote, range, dayCloses) {
                when {
                    range == HomeRange.DAY -> dayCloses
                    last == null -> emptyList()
                    else -> quote.closes.filter { it.date > last.minusDays(range.days) }.map { it.close }
                }
            }
            if (shown.size >= 2) {
                Sparkline(
                    shown,
                    c.target,
                    Modifier.fillMaxWidth().height(if (compact) 64.dp else 104.dp).padding(top = 14.dp),
                    key = range,
                    width = 2.5.dp,
                    description = "EGX30 over ${range.label}, ${shown.size} " +
                        if (range == HomeRange.DAY) "five-minute bars" else "sessions",
                )
            }
            if (!compact && quote.closes.size >= 2) {
                // The chart tile's own range track: the kit's segmented control is drawn for the
                // page's ground and disappears on navy.
                Row(
                    Modifier
                        .padding(horizontal = 14.dp)
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(Kit.Capsule)
                        .background(c.track)
                        .padding(3.dp),
                ) {
                    options.forEach { option ->
                        val chosen = option == range
                        Box(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 36.dp)
                                .clip(Kit.Capsule)
                                .background(if (chosen) c.chosen else Color.Transparent)
                                .clickable(role = Role.Tab, onClickLabel = "Show ${option.label}") { picked = option },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                                color = if (chosen) c.onChosen else c.text,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A line through [values], oldest first, drawn in once and again when [key] changes. */
@Composable
internal fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    key: Any? = Unit,
    width: Dp = 2.dp,
    description: String? = null,
) {
    val drawn = rememberDrawIn(key = key to values.size)
    Canvas(if (description == null) modifier else modifier.semantics { contentDescription = description }) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val span = (values.max() - lo).takeIf { it > 0 } ?: 1.0
        val inset = width.toPx()
        val step = size.width / (values.size - 1)
        val visible = (values.size * drawn.value).toInt().coerceAtLeast(1)
        val path = Path()
        values.take(visible + 1).forEachIndexed { i, value ->
            val y = (size.height - inset - ((value - lo) / span * (size.height - 2 * inset))).toFloat()
            if (i == 0) path.moveTo(0f, y) else path.lineTo(i * step, y)
        }
        drawPath(path, color, style = Stroke(width = inset, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The other three indices on one row: sunflower, lilac, sky, each a level, a move and a line. */
@Composable
internal fun IndexRow(quotes: List<IndexQuote>, modifier: Modifier = Modifier) {
    if (quotes.isEmpty()) return
    val tones = listOf(TileTone.TILE1, TileTone.TILE2, TileTone.TILE3)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        quotes.take(3).forEachIndexed { i, quote ->
            val tone = tones[i]
            val signals = tileSignals(tone)
            Tile(Modifier.weight(1f), tone = tone, contentPadding = PaddingValues(14.dp)) {
                Text(quote.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formatLevel(quote.level),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                    maxLines = 1,
                )
                quote.changePercent?.let { percent ->
                    Text(
                        formatPercent(percent),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                        color = if (percent >= 0) signals.gain else signals.loss,
                    )
                }
                val closes = remember(quote) { quote.closes.takeLast(22).map { it.close } }
                if (closes.size >= 2) {
                    Sparkline(closes, LocalContentColor.current, Modifier.fillMaxWidth().height(34.dp).padding(top = 6.dp))
                }
            }
        }
    }
}

/**
 * "Your book": what the open trades are making on average, on a tile whose colour follows the sign
 * of that figure - deep teal ahead, soft sage flat, maroon behind. See [bookMood], which judges the
 * figure as it is printed so the colour can never disagree with the number.
 */
@Composable
internal fun BookTile(
    stats: PortfolioStats,
    needs: List<NeedsYouItem>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = when (bookMood(stats.openReturnPct)) {
        BookMood.UP -> TileTone.BOOK_UP
        BookMood.FLAT -> TileTone.BOOK_FLAT
        BookMood.DOWN -> TileTone.BOOK_DOWN
    }
    val signals = tileSignals(tone)
    val ink = tileColors(tone).second
    Tile(modifier.fillMaxWidth(), tone = tone, onClick = onOpen, onClickLabel = "Open Portfolio") {
        Text("Your book", style = MaterialTheme.typography.titleSmall, color = ink.copy(alpha = 0.8f))
        val figure = stats.openReturnPct
        Text(
            figure?.let { formatPercent(it) } ?: "—",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = TabularFigures),
            color = when (tone) {
                TileTone.BOOK_UP -> signals.gain
                TileTone.BOOK_DOWN -> signals.loss
                else -> ink
            },
        )
        Text(
            when {
                stats.openCount == 0 && stats.total == 0 -> "No trades recorded yet"
                stats.openCount == 0 -> "No open trades"
                figure == null -> "${stats.openCount} open · waiting for a price"
                else -> "average per open trade · ${stats.openCount} open"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        val nearStop = needs.count { it.reason == NeedReason.NEAR_STOP }
        val nearTarget = needs.count { it.reason == NeedReason.NEAR_TARGET2 }
        val tags = buildList {
            if (nearTarget > 0) add("$nearTarget near target 2")
            if (nearStop > 0) add("$nearStop near a stop")
            if (stats.overdueCount > 0) add("${stats.overdueCount} overdue")
        }
        if (tags.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = Space.s),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                tags.forEach { KitTag(it, tone = ink.copy(alpha = 0.14f), ink = ink) }
            }
        }
    }
}

/** What one entry on the coral tile says after the ticker. */
internal fun needWords(item: NeedsYouItem): String = when (item.reason) {
    NeedReason.OVERDUE -> "overdue ${item.view.overdueDays}d"
    NeedReason.NEAR_STOP -> item.distancePercent?.let { "${formatPercent(it, signed = false)} above stop" } ?: "near its stop"
    NeedReason.NEAR_TARGET2 -> item.distancePercent?.let { "${formatPercent(it, signed = false)} to target 2" } ?: "near target 2"
}

/**
 * The trades that want a decision, up to three, each pressed through to the trade. Absent with none.
 *
 * **Each trade is two lines, the ticker over what is wrong with it**, since 2026-11-12. They were
 * one row, the words at their full width and the ticker weighted into what was left - and on the
 * cover screen the tile is half of 411dp, so "1.2% above stop" took nearly all of it and NIPH was
 * drawn one letter per line. Reported from the Fold. Stacked, neither can squeeze the other.
 */
@Composable
internal fun NeedsLookTile(needs: List<NeedsYouItem>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    if (needs.isEmpty()) return
    Tile(modifier, tone = TileTone.ALERT, contentPadding = PaddingValues(14.dp)) {
        Text("Needs a look", style = MaterialTheme.typography.titleSmall)
        val shown = needs.take(3)
        shown.forEachIndexed { i, item ->
            if (i > 0) KitRule(color = LocalContentColor.current.copy(alpha = 0.14f))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open ${item.view.ticker}") { onOpen(item.view.position.id) }
                    .padding(vertical = Space.xs),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    item.view.ticker,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    needWords(item),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (needs.size > 3) Text("and ${needs.size - 3} more", style = MaterialTheme.typography.labelMedium)
    }
}

/** How many calls this session brought, pressed through to Insights. */
@Composable
internal fun CallsTodayTile(digest: SessionDigest?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Tile(modifier, onClick = onOpen, onClickLabel = "Open Insights", contentPadding = PaddingValues(14.dp)) {
        Text(
            "Calls" + (digest?.let { " · ${shortDate(it.date)}" } ?: ""),
            style = MaterialTheme.typography.titleSmall,
            color = palette.muted,
        )
        Text(
            (digest?.newCalls ?: 0).toString(),
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TabularFigures),
        )
        Text(
            when {
                digest == null || digest.newCalls == 0 -> "None published for this session yet"
                else -> "from ${digest.newCallSources} ${if (digest.newCallSources == 1) "source" else "sources"}" +
                    if (digest.inRange > 0) " · ${digest.inRange} in a buy zone" else ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
        )
    }
}

/** The sentence under the breadth bar. Says only what the three counts say. */
internal fun breadthWords(breadth: MarketBreadth): String = when {
    breadth.down == 0 && breadth.up > 0 -> "Nothing fell"
    breadth.up == 0 && breadth.down > 0 -> "Nothing rose"
    breadth.up >= breadth.down * 2 -> "At least twice as many rose as fell"
    breadth.down >= breadth.up * 2 -> "At least twice as many fell as rose"
    breadth.up > breadth.down -> "More rose than fell"
    breadth.down > breadth.up -> "More fell than rose"
    else -> "As many rose as fell"
}

/** How the whole market split in the newest session. */
@Composable
internal fun MarketTile(breadth: MarketBreadth?, modifier: Modifier = Modifier) {
    Tile(modifier.fillMaxWidth()) {
        Text(
            breadth?.let { "Market · ${it.total} stocks · ${shortDate(it.date)}" } ?: "Market",
            style = MaterialTheme.typography.titleSmall,
            color = palette.muted,
        )
        if (breadth == null) {
            Text("The split appears once two sessions of prices are stored.", style = MaterialTheme.typography.bodyMedium)
            return@Tile
        }
        Text(
            "${breadth.up}↑  ${breadth.flat}·  ${breadth.down}↓",
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = TabularFigures),
        )
        BreadthBar(breadth.up, breadth.flat, breadth.down, Modifier.padding(vertical = Space.s))
        Text(breadthWords(breadth), style = MaterialTheme.typography.bodySmall, color = palette.muted)
    }
}

/** The session's top gainers and top losers, side by side. A press on a stock opens its sheet. */
@Composable
internal fun MoversRow(movers: MarketMovers?, modifier: Modifier = Modifier) {
    if (movers == null || (movers.gainers.isEmpty() && movers.losers.isEmpty())) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        MoversTile("Top gainers", movers.gainers, Modifier.weight(1f))
        MoversTile("Top losers", movers.losers, Modifier.weight(1f))
    }
}

@Composable
private fun MoversTile(title: String, list: List<Mover>, modifier: Modifier = Modifier) {
    val p = palette
    val openStock = LocalOpenStock.current
    Tile(modifier, contentPadding = PaddingValues(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = p.muted)
        if (list.isEmpty()) Text("None", style = MaterialTheme.typography.bodyMedium)
        list.forEachIndexed { i, mover ->
            if (i > 0) KitRule()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open ${mover.ticker}") { openStock(mover.ticker) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StockLogo(mover.ticker, 22.dp)
                Spacer(Modifier.width(Space.s))
                Text(
                    mover.ticker,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatPercent(mover.changePercent),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                    color = if (mover.changePercent >= 0) p.gain else p.loss,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The dollar, gold and oil. The move is printed in the page's ink, not in green and red: a dearer
 * dollar is not a gain to somebody holding Egyptian stocks, and the app's two money colours are
 * kept for money that is the reader's.
 */
@Composable
internal fun OutsideRow(quotes: List<OutsideQuote>, modifier: Modifier = Modifier) {
    if (quotes.isEmpty()) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        quotes.take(3).forEach { quote ->
            Tile(Modifier.weight(1f), contentPadding = PaddingValues(14.dp)) {
                Text(quote.name, style = MaterialTheme.typography.labelMedium, color = palette.muted, maxLines = 1)
                Text(
                    formatLevel(quote.value, decimals = 2),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                    maxLines = 1,
                )
                quote.changePercent?.let {
                    Text(
                        formatPercent(it),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                    )
                }
            }
        }
    }
}

/**
 * The headlines: Arabic first, then English, each a title, its source and its age, and a press
 * that opens the article in the reader's browser.
 *
 * @param limit how many of each language the tile shows before Show all, as (Arabic, English).
 */
@Composable
internal fun NewsTile(
    headlines: List<Headline>,
    loading: Boolean,
    fetchedAt: Long,
    expanded: MutableState<Boolean>,
    onOpen: (Headline) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    limit: Pair<Int, Int> = 5 to 3,
) {
    val p = palette
    val now = remember(headlines, fetchedAt) { System.currentTimeMillis() }
    var open by expanded
    Tile(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("News", style = MaterialTheme.typography.titleSmall, color = p.muted, modifier = Modifier.weight(1f))
            if (loading) {
                Text("refreshing…", style = MaterialTheme.typography.labelMedium, color = p.muted)
            } else if (fetchedAt > 0L) {
                Text(
                    "updated ${headlineAge(fetchedAt, now).let { if (it == "now") "just now" else "$it ago" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = p.muted,
                )
            }
            NewsRefreshButton(loading, onRefresh, Modifier.padding(start = Space.xs))
        }
        if (headlines.isEmpty()) {
            Text(
                if (loading) "Fetching headlines…" else "No headlines yet. Pull down to fetch them.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Space.s),
            )
            return@Tile
        }
        val lead = remember(headlines, limit) { leadHeadlines(headlines, limit.first, limit.second) }
        (if (open) headlines else lead).forEachIndexed { i, headline ->
            if (i > 0) KitRule()
            HeadlineRow(headline, now) { onOpen(headline) }
        }
        if (headlines.size > lead.size) {
            KitButton(
                if (open) "Show fewer" else "Show all ${headlines.size}",
                onClick = { open = !open },
                kind = ButtonKind.SECONDARY,
                size = ButtonSize.SMALL,
                modifier = Modifier.padding(top = Space.s),
            )
        }
    }
}

/**
 * Fetches the four feeds again, and nothing else: no market figures and never the paid brief.
 * While a fetch is out the glyph turns and a press does nothing; reduce motion holds it still.
 */
@Composable
private fun NewsRefreshButton(loading: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val still = LocalReduceMotion.current
    val turn = if (loading && !still) {
        val spin = rememberInfiniteTransition(label = "news-refresh")
        spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "news-refresh").value
    } else {
        0f
    }
    KitIconButton(
        Icons.Rounded.Refresh,
        label = if (loading) "Refreshing news" else "Refresh news",
        onClick = { if (!loading) onRefresh() },
        modifier = modifier.graphicsLayer { rotationZ = turn },
        size = 36.dp,
    )
}

@Composable
private fun HeadlineRow(headline: Headline, now: Long, onOpen: () -> Unit) {
    // An Arabic headline reads from the right and starts at the right, whatever the phone is set to.
    val direction = if (headline.arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Open the article", onClick = onOpen)
                .padding(vertical = 10.dp),
        ) {
            Text(
                headline.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val age = headlineAge(headline.publishedAt, now)
            Text(
                headline.source + if (age.isEmpty()) "" else " · $age",
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The model's read of the day, on the lilac that means a model speaking.
 *
 * Pressed anywhere, it opens and closes: shut it is the title, the summary and a chevron; open it
 * adds the points, what to watch and who wrote it when. [expanded] lives in `PageState` so a fold
 * keeps it.
 */
@Composable
internal fun BriefTile(brief: MarketBrief, expanded: MutableState<Boolean>, modifier: Modifier = Modifier) {
    val now = remember(brief) { System.currentTimeMillis() }
    var open by expanded
    val turn by animateFloatAsState(if (open) 1f else 0f, label = "brief")
    Tile(
        modifier.fillMaxWidth(),
        tone = TileTone.TILE2,
        onClick = { open = !open },
        onClickLabel = if (open) "Close the brief" else "Open the brief",
    ) {
        val ink = LocalContentColor.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AI market brief", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            // The same chevron `KitExpandable` draws: down when shut, turned up when open.
            Canvas(Modifier.size(14.dp).graphicsLayer { rotationZ = 180f * turn }) {
                val w = size.width
                drawPath(
                    Path().apply {
                        moveTo(w * 0.14f, w * 0.34f)
                        lineTo(w * 0.5f, w * 0.68f)
                        lineTo(w * 0.86f, w * 0.34f)
                    },
                    ink,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        Text(brief.summary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.xs))
        AnimatedVisibility(open, enter = ExpandIn, exit = CollapseOut) {
            Column {
                brief.points.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs))
                }
                if (brief.watch.isNotEmpty()) {
                    Text("Watch next", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Space.s))
                    brief.watch.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs))
                    }
                }
                val age = headlineAge(brief.askedAt, now)
                Text(
                    listOf(brief.model, if (age.isEmpty()) "" else if (age == "now") "just now" else "$age ago")
                        .filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = Space.s),
                )
            }
        }
    }
}
