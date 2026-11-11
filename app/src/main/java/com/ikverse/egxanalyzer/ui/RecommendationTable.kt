package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.CallGroup
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.ui.kit.HoldMenu
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.MenuItem
import com.ikverse.egxanalyzer.ui.kit.popIn
import com.ikverse.egxanalyzer.ui.theme.palette

/**
 * The report's calls on a wide screen: a tile per stock, and under its heading one line of figures
 * per call.
 *
 * **It is for comparing the calls on one stock, in numbers.** Each stock gets its own tile - logo,
 * ticker, name, how many calls from how many channels, and the latest price, said once - and each of
 * its calls a line led by the channel that made it. The figure columns are the same in every tile,
 * so calls compare down within a stock and across stocks alike.
 *
 * **No ladder, since 2026-11-11.** The owner asked for numbers here: the ladder is in the sheet a
 * line opens. What took its place is what the ladder only implied - under the stop and each target,
 * how far it is from the entry, in that level's own colour; then where the price is against the
 * entry, and how far it still has to go to target 1.
 *
 * **No shared header either.** Until that day one card held every stock under a spreadsheet's row of
 * column names, which the owner called out as the old design. The names now sit above the figures
 * of a tile's first call, and a second call lines up under them without repeating them.
 *
 * **Live calls come first within a stock**, a watched one after them, each kind in the report's own
 * order.
 *
 * **It fits, and that is the rest of the design.** It was sixteen fixed-width columns behind a
 * horizontal scroll until 2026-09-09, and crossing a breakpoint *appended* columns, so the tablet's
 * 682dp showed less of a line than the smaller Fold's 614dp. Only [WhoWidth] is fixed and every
 * other column is weighted, so a wider window widens the columns rather than adding more.
 * `RecommendationTableTest` holds that.
 *
 * A tap opens the call; a hold opens [holdItems], the same menu a card offers.
 */
@Composable
internal fun RecommendationTable(
    stocks: List<ConsolidatedRecommendation>,
    channelFor: (String?) -> String?,
    /**
     * Where a stock is now, for its heading and each call's live figures. A lambda rather than a
     * map, so a price moving on one stock does not hand the table the whole record.
     */
    latestFor: (String) -> LatestPrice?,
    onSelectPoint: (ConsolidatedRecommendation, RecommendationDataPoint) -> Unit,
    modifier: Modifier = Modifier,
    /** The status word under the channel, and the group that colours its dot. */
    statusFor: (ConsolidatedRecommendation, RecommendationDataPoint) -> Pair<String, CallGroup> =
        { _, _ -> "Running" to CallGroup.RUNNING },
    /** A hold on a line: what can be done about that call. */
    holdItems: (ConsolidatedRecommendation, RecommendationDataPoint) -> List<MenuItem> = { _, _ -> emptyList() },
) {
    if (stocks.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        stocks.forEachIndexed { stockIndex, stock ->
            // Keyed, so a change of order moves a tile rather than popping every one in again.
            key(stock.stockCode) {
                val latest = latestFor(stock.stockCode)
                Column(
                    Modifier
                        .popIn(stockIndex)
                        .fillMaxWidth()
                        .clip(Kit.TileShape)
                        .background(palette.card)
                        .padding(vertical = Space.s),
                ) {
                    StockHeading(
                        stock = stock,
                        latest = latest,
                        calls = stock.dataPoints.size,
                        channels = stock.dataPoints
                            .mapNotNull { channelFor(it.sourceMessageId) ?: it.sourceMessageId }
                            .distinct().size,
                    )
                    liveFirst(stock.dataPoints).forEachIndexed { index, point ->
                        if (index > 0) Box(Modifier.padding(horizontal = TileInset)) { Hairline() }
                        CallLine(
                            point = point,
                            channel = channelFor(point.sourceMessageId) ?: point.sourceMessageId,
                            close = latest?.session?.close,
                            status = statusFor(stock, point),
                            // The names are said once a tile, above its first call.
                            labelled = index == 0,
                            onClick = { onSelectPoint(stock, point) },
                            holdItems = holdItems(stock, point),
                        )
                    }
                }
            }
        }
    }
}

/** A stock's calls with the watched ones last. Stable, so each kind keeps the report's own order. */
internal fun liveFirst(points: List<RecommendationDataPoint>): List<RecommendationDataPoint> =
    points.sortedBy { it.isWatching }

/** How far [level] is from the call's entry, as a percentage. Null where either is missing. */
internal fun fromEntry(point: RecommendationDataPoint, level: Double?): Double? {
    val entry = point.entryMidpoint() ?: return null
    if (level == null || entry == 0.0) return null
    return (level - entry) / entry * 100
}

/** How far the price still has to rise from [close] to reach target 1. Null where either is missing. */
internal fun toTarget1(point: RecommendationDataPoint, close: Double?): Double? {
    val target = point.target1 ?: return null
    if (close == null || close == 0.0) return null
    return (target - close) / close * 100
}

/** A tile's inset, left and right: the heading, every line and the rules between them share it. */
private val TileInset = 20.dp

/** The channel that made the call, with its status under it. The one column of words. */
private val WhoWidth = 124.dp

/**
 * What the entry column gets for every 1 the other figure columns get. Entry is the only cell
 * holding two prices and the dash between them, fifteen characters where a target holds six.
 */
private const val EntryWeight = 1.4f

/** A minimum rather than a fixed height, so a large font scale grows the cell instead of clipping it. */
private val CellHeight = 38.dp

/** The system font scale past which a several-word column name is stacked. */
private const val StackLabelsAbove = 1.15f

/**
 * A stock's own line above its calls: who it is, how many calls from how many channels, and where
 * it is now. The price is here once rather than on every line, because it is a fact about the stock;
 * a stock the feed has no price for says so rather than leaving the corner empty.
 */
@Composable
private fun StockHeading(stock: ConsolidatedRecommendation, latest: LatestPrice?, calls: Int, channels: Int) {
    val close = latest?.session?.close
    val count = buildString {
        append(if (calls == 1) "1 call" else "$calls calls")
        if (channels > 1) append(" · $channels channels")
    }
    Row(
        Modifier.fillMaxWidth().padding(start = TileInset, end = TileInset, top = Space.s, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StockLogo(stock.stockCode, LogoSize.Row, Modifier.padding(end = Space.s))
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
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = TabularFigures),
                    color = PriceRole.market,
                )
                AutoSizeText("now", style = MaterialTheme.typography.labelSmall, color = palette.muted)
            }
        } else {
            AutoSizeText(
                "no price",
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.padding(start = Space.s),
            )
        }
    }
}

@Composable
private fun CallLine(
    point: RecommendationDataPoint,
    channel: String?,
    close: Double?,
    status: Pair<String, CallGroup>,
    labelled: Boolean,
    onClick: () -> Unit,
    holdItems: List<MenuItem>,
) {
    HoldMenu(items = holdItems, onTap = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TileInset, vertical = Space.s),
            verticalAlignment = Alignment.Bottom,
        ) {
            WhoCell(channel, status, point.isTPlusOne, Modifier.width(WhoWidth))
            FigureCell(Modifier.weight(EntryWeight), "Entry".takeIf { labelled }, entryText(point), PriceRole.entry)
            // A watched stock shows whatever levels its post gave, like any call. They were dashes
            // until 2026-11-11, which made a line that had data look empty.
            FigureCell(
                Modifier.weight(1f), "Stop".takeIf { labelled },
                formatPrice(point.stopLoss), PriceRole.stop, fromEntry(point, point.stopLoss),
            )
            FigureCell(
                Modifier.weight(1f), "Target 1".takeIf { labelled },
                formatPrice(point.target1), PriceRole.target, fromEntry(point, point.target1),
            )
            FigureCell(
                Modifier.weight(1f), "Target 2".takeIf { labelled },
                formatPrice(point.target2), PriceRole.target, fromEntry(point, point.target2),
            )
            if (point.isWatching) {
                // Nothing was bought, so there is no move to report: the two live columns are the
                // room its note needs to be read whole.
                Box(
                    Modifier.weight(2f).heightIn(min = CellHeight).padding(start = Space.s),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    BasicText(
                        watchingNote(point),
                        style = MaterialTheme.typography.labelSmall.merge(color = palette.muted, textAlign = TextAlign.End),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                MoveCell(Modifier.weight(1f), "Since entry".takeIf { labelled }, moveVsEntry(point, close))
                val left = toTarget1(point, close)
                // At or past the target there is no distance left to state.
                val there = left != null && left <= 0.0
                FigureCell(
                    Modifier.weight(1f), "To target 1".takeIf { labelled },
                    if (there) "reached" else formatPercent(left, signed = false),
                    if (there) palette.gain else palette.ink,
                )
            }
        }
    }
}

/**
 * What a watched stock says where a live call shows its move: the level it has to clear, else its
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

/**
 * The channel that made the call, and under it the status: a dot in the group's colour, the word,
 * and `T+1` after it where the post named the call one - in the figures' face and the ink, on the
 * call it belongs to and never on the stock.
 */
@Composable
private fun WhoCell(channel: String?, status: Pair<String, CallGroup>, tPlusOne: Boolean, modifier: Modifier) {
    Column(modifier.heightIn(min = CellHeight).padding(end = Space.s), verticalArrangement = Arrangement.Center) {
        // Channel names run long and this column is fixed, so the name shrinks before it is cut.
        AutoSizeText(channel ?: Dash, style = MaterialTheme.typography.labelMedium, color = palette.ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(status.second.tone()))
            AutoSizeText(
                status.first,
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.padding(start = Space.xs).weight(1f, fill = false),
            )
            if (tPlusOne) {
                AutoSizeText(
                    " · T+1",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                    color = palette.ink,
                )
            }
        }
    }
}

/** A column's name over its cell, on a tile's first call only. */
@Composable
private fun CellLabel(label: String?) {
    if (label == null) return
    // At a large font scale a several-word name is stacked one word to a line, each still shrinking
    // on its own: at the shrink's floor "Target 1" and "Target 2" both end in `Targe…`, which is
    // two columns the reader can no longer tell apart (seen at 1.6x, 2026-10-05).
    val stacked = LocalDensity.current.fontScale > StackLabelsAbove
    if (!stacked) {
        LabelWord(label)
    } else {
        Column { label.split(' ').forEach { LabelWord(it) } }
    }
}

@Composable
private fun LabelWord(word: String) {
    AutoSizeText(
        word,
        style = MaterialTheme.typography.labelSmall,
        color = palette.faint,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A figure, right-aligned and monospaced, so a column can be compared down and not only across,
 * with [fromEntry] under it in the same colour where the call has an entry to measure from.
 */
@Composable
private fun FigureCell(modifier: Modifier, label: String?, value: String, tone: Color, fromEntry: Double? = null) {
    val shown = if (value == Dash) PriceRole.muted else tone
    Column(modifier.padding(horizontal = Space.xs)) {
        CellLabel(label)
        Column(Modifier.fillMaxWidth().heightIn(min = CellHeight), verticalArrangement = Arrangement.Center) {
            AutoSizeText(
                value,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = TabularFigures),
                color = shown,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
            if (fromEntry != null) {
                AutoSizeText(
                    formatPercent(fromEntry),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                    color = shown,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Where the price is against the entry, in a pill washed with its own colour: gain above, loss
 * below, and neutral where the move rounds to nothing. A bare dash where there is no price.
 */
@Composable
private fun MoveCell(modifier: Modifier, label: String?, move: Double?) {
    val text = formatPercent(move)
    // Judged on what is printed, so a move that reads "0%" is never drawn as a gain or a loss.
    val flat = move == null || text == "0%"
    val tone = when {
        flat -> palette.muted
        move!! > 0 -> palette.gain
        else -> palette.loss
    }
    Column(modifier.padding(horizontal = Space.xs)) {
        CellLabel(label)
        Box(Modifier.fillMaxWidth().heightIn(min = CellHeight), contentAlignment = Alignment.CenterEnd) {
            AutoSizeText(
                text,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
                color = tone,
                modifier = if (move == null) Modifier else Modifier
                    .clip(CircleShape)
                    .background(if (flat) palette.raised else tone.copy(alpha = 0.12f))
                    .padding(horizontal = Space.s, vertical = Space.xs),
            )
        }
    }
}
