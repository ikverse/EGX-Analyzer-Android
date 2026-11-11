package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.CallGroup
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.ui.kit.HoldMenu
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitTag
import com.ikverse.egxanalyzer.ui.kit.MenuItem
import com.ikverse.egxanalyzer.ui.theme.palette

/**
 * The report's calls as one table, grouped by stock: a heading per stock, one row per call under it.
 *
 * **It is for comparing the calls on one stock.** Two channels calling RAYA print two entries, two
 * stops and two pairs of targets, and the reader wants them one above the other. So each stock gets
 * a heading - logo, ticker, name, how many calls from how many channels, and the latest price, said
 * once - and each of its calls a row led by the channel that made it. The figure columns are the
 * same down the whole table, so calls compare down within a stock and across stocks alike.
 *
 * From 5.1 until 2026-11-11 this was one flat block: ticker and channel on every row, and a `Now`
 * column repeating a stock's price on each of its rows. That read as a transcript of the posts and
 * hid which rows belonged together.
 *
 * **It fits, and that is the rest of the design.** It was sixteen fixed-width columns behind a
 * horizontal scroll until 2026-09-09, and crossing a breakpoint *appended* columns, so the tablet's
 * 682dp showed less of a row than the smaller Fold's 614dp. Only [ChannelWidth] and [StatusWidth]
 * are fixed and every other column is weighted, so a wider window widens the columns rather than
 * adding more. `RecommendationTableTest` holds that.
 *
 * A tap opens the call; a hold opens [holdItems], the same menu a card offers.
 */
@Composable
internal fun RecommendationTable(
    stocks: List<ConsolidatedRecommendation>,
    channelFor: (String?) -> String?,
    /**
     * Where a stock is now, for its heading and the ladder's arrow. A lambda rather than a map, so
     * a price moving on one stock does not hand the table the whole record.
     */
    latestFor: (String) -> LatestPrice?,
    onSelectPoint: (ConsolidatedRecommendation, RecommendationDataPoint) -> Unit,
    modifier: Modifier = Modifier,
    /** The status word in the last column, and the group that colours it. */
    statusFor: (ConsolidatedRecommendation, RecommendationDataPoint) -> Pair<String, CallGroup> =
        { _, _ -> "Running" to CallGroup.RUNNING },
    /** A hold on a row: what can be done about that call. */
    holdItems: (ConsolidatedRecommendation, RecommendationDataPoint) -> List<MenuItem> = { _, _ -> emptyList() },
) {
    if (stocks.isEmpty()) return
    Box(
        modifier
            .fillMaxWidth()
            .clip(Kit.TileShape)
            .background(palette.card),
    ) {
        Column {
            ColumnHeader()
            stocks.forEachIndexed { stockIndex, stock ->
                val latest = latestFor(stock.stockCode)
                val channels = stock.dataPoints.map { channelFor(it.sourceMessageId) ?: it.sourceMessageId }
                if (stockIndex > 0) Hairline()
                StockHeading(stock, latest, calls = stock.dataPoints.size, channels = channels.filterNotNull().distinct().size)
                stock.dataPoints.forEachIndexed { index, point ->
                    CallRow(
                        point = point,
                        channel = channels[index],
                        latest = latest,
                        status = statusFor(stock, point),
                        // The stripes restart under each heading, so a stock's first call is plain.
                        striped = index % 2 == 1,
                        onClick = { onSelectPoint(stock, point) },
                        holdItems = holdItems(stock, point),
                    )
                }
            }
        }
    }
}

/** The channel that made the call, and the T+1 tag where the call is one. The one column of words. */
private val ChannelWidth = 132.dp

/** The status word, unlabelled: the word says what it is. */
private val StatusWidth = 92.dp

/**
 * What the entry column gets for every 1 the other figure columns get. Entry is the only cell
 * holding two prices and the dash between them, fifteen characters where a target holds six.
 */
private const val EntryWeight = 1.6f

/** The ladder's share: twice a figure, enough for the shape to read. */
private const val LadderWeight = 2f

/** A minimum rather than a fixed height, so a large font scale grows the row instead of clipping it. */
private val RowHeight = 52.dp

@Composable
private fun ColumnHeader() {
    Row(
        Modifier.fillMaxWidth().padding(start = Space.m, end = Space.m, top = Space.s, bottom = Space.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        HeaderLabel("Channel", Modifier.width(ChannelWidth), TextAlign.Start)
        HeaderLabel("Entry", Modifier.weight(EntryWeight), TextAlign.End)
        HeaderLabel("Stop", Modifier.weight(1f), TextAlign.End)
        HeaderLabel("Target 1", Modifier.weight(1f), TextAlign.End)
        HeaderLabel("Target 2", Modifier.weight(1f), TextAlign.End)
        HeaderLabel("Stop to target", Modifier.weight(LadderWeight), TextAlign.Center)
        Spacer(Modifier.width(StatusWidth))
    }
    Hairline()
}

/**
 * A column's name, which shrinks with the same rule its figures do.
 *
 * Above [StackHeadersAbove] a name made of several words is stacked one word to a line, each still
 * shrinking on its own: at the shrink's floor "Target 1" and "Target 2" both end in `Targe…`, which
 * is two columns the reader can no longer tell apart (seen at 1.6x, 2026-10-05).
 */
@Composable
private fun HeaderLabel(label: String, modifier: Modifier, align: TextAlign) {
    val large = LocalDensity.current.fontScale > StackHeadersAbove
    val words = label.split(' ')
    if (large && words.size > 1) {
        Column(
            modifier.fillMaxWidth().padding(horizontal = Space.xs),
            horizontalAlignment = when (align) {
                TextAlign.Start -> Alignment.Start
                TextAlign.Center -> Alignment.CenterHorizontally
                else -> Alignment.End
            },
        ) {
            words.forEach { word ->
                AutoSizeText(
                    word,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    textAlign = align,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        AutoSizeText(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
            textAlign = align,
            modifier = modifier.fillMaxWidth().padding(horizontal = Space.xs),
        )
    }
}

/** The system font scale past which a several-word column name is stacked. */
private const val StackHeadersAbove = 1.15f

/**
 * A stock's own line above its calls: who it is, how many calls from how many channels, and where
 * it is now. The price is here once rather than on every row, because it is a fact about the stock.
 */
@Composable
private fun StockHeading(stock: ConsolidatedRecommendation, latest: LatestPrice?, calls: Int, channels: Int) {
    val close = latest?.session?.close
    val count = buildString {
        append(if (calls == 1) "1 call" else "$calls calls")
        if (channels > 1) append(" · $channels channels")
    }
    Row(
        Modifier.fillMaxWidth().padding(start = Space.m, end = Space.m, top = Space.m, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StockLogo(stock.stockCode, LogoSize.Row, Modifier.padding(start = Space.xs, end = Space.s))
        Column(Modifier.weight(1f)) {
            AutoSizeText(stock.stockCode, style = MaterialTheme.typography.titleMedium, color = palette.ink)
            AutoSizeText(
                listOfNotNull(stock.stockNameEnglish?.takeIf(String::isNotBlank), count).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (close != null) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = Space.s)) {
                AutoSizeText(
                    formatPrice(close),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                    color = PriceRole.market,
                )
                AutoSizeText("now", style = MaterialTheme.typography.labelSmall, color = palette.muted)
            }
        }
    }
}

@Composable
private fun CallRow(
    point: RecommendationDataPoint,
    channel: String?,
    latest: LatestPrice?,
    status: Pair<String, CallGroup>,
    striped: Boolean,
    onClick: () -> Unit,
    holdItems: List<MenuItem>,
) {
    val close = latest?.session?.close
    val watching = point.isWatching
    HoldMenu(items = holdItems, onTap = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                // A low-alpha tint of the ink rather than a surface tier: it moves the stripe the
                // same visible amount in both themes, lighter in dark and darker in light.
                .background(if (striped) palette.ink.copy(alpha = 0.04f) else Color.Transparent)
                .heightIn(min = RowHeight)
                .padding(horizontal = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelCell(channel, point.isTPlusOne, Modifier.width(ChannelWidth))
            FigureCell(Modifier.weight(EntryWeight), entryText(point), PriceRole.entry)
            // A watched stock shows whatever levels its post gave, like any call. They were dashes
            // until 2026-11-11, which made a row that had data look empty.
            FigureCell(Modifier.weight(1f), formatPrice(point.stopLoss), PriceRole.stop)
            FigureCell(Modifier.weight(1f), formatPrice(point.target1), PriceRole.target)
            FigureCell(Modifier.weight(1f), formatPrice(point.target2), PriceRole.target)
            Box(
                Modifier.weight(LadderWeight).padding(horizontal = Space.xs),
                contentAlignment = Alignment.Center,
            ) {
                if (watching) {
                    AutoSizeText(
                        watchingNote(point),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PriceLadder(point, Modifier.testTag(LadderCellTag), reached = close, compact = true)
                }
            }
            StatusCell(status, Modifier.width(StatusWidth))
        }
    }
}

/**
 * What a watched stock says where a call draws its ladder: the level it has to clear, else its
 * support and resistance, else the post's own note - so the cell is never a bare "watching".
 */
internal fun watchingNote(point: RecommendationDataPoint): String {
    watchLevel(point)?.let { return "buy only above ${formatPrice(it)}" }
    val levels = listOfNotNull(
        point.support?.let { "support ${formatPrice(it)}" },
        point.resistance?.let { "resistance ${formatPrice(it)}" },
    )
    if (levels.isNotEmpty()) return levels.joinToString(" · ")
    return point.notesArabic?.takeIf(String::isNotBlank)
        ?: point.recommendationEvidence?.takeIf(String::isNotBlank)
        ?: "no levels given"
}

/** On the ladder cell, so a test can tell a row that draws one from a row that does not. */
internal const val LadderCellTag = "call-ladder"

/** The channel that made the call, with the T+1 tag after it where the post named it one. */
@Composable
private fun ChannelCell(channel: String?, tPlusOne: Boolean, modifier: Modifier) {
    Row(
        modifier.padding(horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        // Channel names run long and this column is fixed, so the name shrinks before it is cut.
        AutoSizeText(
            channel ?: Dash,
            style = MaterialTheme.typography.labelMedium,
            color = palette.ink,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (tPlusOne) KitTag("T+1", tone = palette.muted)
    }
}

/** A figure, right-aligned and monospaced, so a column can be compared down and not only across. */
@Composable
private fun FigureCell(modifier: Modifier, value: String, tone: Color) {
    AutoSizeText(
        value,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
        color = if (value == Dash) PriceRole.muted else tone,
        textAlign = TextAlign.End,
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.xs),
    )
}

/** A dot in the group's colour and the status word, the word shrinking before it is cut. */
@Composable
private fun StatusCell(status: Pair<String, CallGroup>, modifier: Modifier) {
    val tone = status.second.tone()
    Row(modifier.padding(start = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tone))
        AutoSizeText(
            status.first,
            style = MaterialTheme.typography.labelMedium,
            color = palette.ink,
            modifier = Modifier.padding(start = Space.xs).fillMaxWidth(),
        )
    }
}
