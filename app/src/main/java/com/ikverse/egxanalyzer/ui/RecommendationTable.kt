package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.tileColors
import com.ikverse.egxanalyzer.ui.kit.tileSignals
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
 * The report's calls on a wide screen: one table, a band per stock, and under it one line of
 * figures per call.
 *
 * **Three levels, each its own fill (2026-11-12).** The column names are said once, on an ink band
 * across the top - the live figures' names in the ink tile's accent, so the two halves of a line
 * read as two groups. Each stock is a band in the raised tone, holding who it is and its price now.
 * Each call is a line on the card, led by the channel that made it. Until that day every stock was
 * a tile of its own with the names over its first call, which the owner found too close to the
 * cards: the levels did not read as a table.
 *
 * **It is for comparing in numbers, and there is no ladder.** The ladder is in the sheet a line
 * opens. Under the stop and each target is how far it is from the entry, in that level's own colour;
 * then the move since entry, the peak since the call, and how far the price is from the stop and
 * from each target.
 *
 * **It fits, and that is the rest of the design.** It was sixteen fixed-width columns behind a
 * horizontal scroll until 2026-09-09, and crossing a breakpoint *appended* columns, so the tablet's
 * 682dp showed less of a line than the smaller Fold's 614dp. Only [WhoWidth] is fixed and every
 * other column is weighted, so a wider window widens the columns rather than adding more.
 * `RecommendationTableTest` holds that.
 *
 * **Live calls come first within a stock**, a watched one after them, each kind in the report's own
 * order. A tap opens the call; a hold opens [holdItems], the same menu a card offers.
 */
@Composable
internal fun RecommendationTable(
    stocks: List<ConsolidatedRecommendation>,
    channelFor: (String?) -> String?,
    /**
     * Where a stock is now, for its band and each call's live figures. A lambda rather than a
     * map, so a price moving on one stock does not hand the table the whole record.
     */
    latestFor: (String) -> LatestPrice?,
    onSelectPoint: (ConsolidatedRecommendation, RecommendationDataPoint) -> Unit,
    modifier: Modifier = Modifier,
    /** The status word under the channel, and the group that colours its dot. */
    statusFor: (ConsolidatedRecommendation, RecommendationDataPoint) -> Pair<String, CallGroup> =
        { _, _ -> "Running" to CallGroup.RUNNING },
    /** The highest the stock has traded since the call, or null where nothing has been judged yet. */
    peakFor: (ConsolidatedRecommendation, RecommendationDataPoint) -> Double? = { _, _ -> null },
    /** A hold on a line: what can be done about that call. */
    holdItems: (ConsolidatedRecommendation, RecommendationDataPoint) -> List<MenuItem> = { _, _ -> emptyList() },
) {
    if (stocks.isEmpty()) return
    Column(
        modifier
            .popIn(0)
            .fillMaxWidth()
            .clip(Kit.TileShape)
            .background(palette.card),
    ) {
        ColumnNames()
        stocks.forEach { stock ->
            // Keyed, so a change of order moves a stock's rows rather than rebuilding every one.
            key(stock.stockCode) {
                val latest = latestFor(stock.stockCode)
                StockBand(
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
                        peak = peakFor(stock, point),
                        status = statusFor(stock, point),
                        onClick = { onSelectPoint(stock, point) },
                        holdItems = holdItems(stock, point),
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.s))
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
internal fun toTarget1(point: RecommendationDataPoint, close: Double?): Double? = toLevel(point.target1, close)

/** How far the price still has to rise from [close] to reach target 2. Null where either is missing. */
internal fun toTarget2(point: RecommendationDataPoint, close: Double?): Double? = toLevel(point.target2, close)

/** How far [close] still sits above the stop, as a share of the price. Null where either is missing. */
internal fun aboveStop(point: RecommendationDataPoint, close: Double?): Double? {
    val stop = point.stopLoss ?: return null
    if (close == null || close == 0.0) return null
    return (close - stop) / close * 100
}

private fun toLevel(level: Double?, close: Double?): Double? {
    if (level == null || close == null || close == 0.0) return null
    return (level - close) / close * 100
}

/** A tile's inset, left and right: the heading, every line and the rules between them share it. */
private val TileInset = 20.dp

/** The channel that made the call, with its status under it. The one column of words. */
private val WhoWidth = 124.dp

/** How many live figure columns close a line, each weighted 1. */
private const val LiveColumns = 5

/** The column names after the channel, in order. The last [LiveColumns] are the live figures. */
internal val TableColumns = listOf(
    "Entry", "Stop", "Target 1", "Target 2", "Since entry", "Peak", "To stop", "To T1", "To T2",
)

/** The ink band's height: one line of names, or a stack of them at a large font scale. */
private val NamesHeight = 40.dp

/**
 * What the entry column gets for every 1 the other figure columns get. Entry is the only cell
 * holding two prices and the dash between them, fifteen characters where a target holds six.
 */
private const val EntryWeight = 1.4f

/** A minimum rather than a fixed height, so a large font scale grows the cell instead of clipping it. */
private val CellHeight = 36.dp

/** The system font scale past which a several-word column name is stacked. */
private const val StackLabelsAbove = 1.15f

/**
 * The column names, once, on an ink band across the top of the table. The live figures' names take
 * the ink tile's accent, so a line reads as what was posted and then where it stands now.
 */
@Composable
private fun ColumnNames() {
    val (fill, onFill) = tileColors(TileTone.INK)
    val accent = tileSignals(TileTone.INK).accent
    val quiet = onFill.copy(alpha = 0.72f)
    Row(
        Modifier
            .fillMaxWidth()
            .background(fill)
            .heightIn(min = NamesHeight)
            .padding(horizontal = TileInset, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(WhoWidth)) { NameCell("Channel", quiet, TextAlign.Start) }
        TableColumns.forEachIndexed { index, name ->
            val live = index >= TableColumns.size - LiveColumns
            Box(Modifier.weight(if (index == 0) EntryWeight else 1f).padding(horizontal = Space.xs)) {
                NameCell(name, if (live) accent else quiet, TextAlign.End)
            }
        }
    }
}

/**
 * A column's name. At a large font scale a several-word name is stacked one word to a line, each
 * still shrinking on its own: at the shrink's floor "Target 1" and "Target 2" both end in the same
 * five letters, two columns the reader can no longer tell apart (seen at 1.6x, 2026-10-05).
 */
@Composable
private fun NameCell(name: String, color: Color, align: TextAlign) {
    val stacked = LocalDensity.current.fontScale > StackLabelsAbove
    if (!stacked) {
        NameWord(name, color, align)
    } else {
        Column(Modifier.fillMaxWidth()) { name.split(' ').forEach { NameWord(it, color, align) } }
    }
}

@Composable
private fun NameWord(word: String, color: Color, align: TextAlign) {
    AutoSizeText(
        word.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
        color = color,
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A stock's band above its calls, in the raised tone: who it is, how many calls from how many
 * channels, and where it is now. The price is here once rather than on every line, because it is a
 * fact about the stock; a stock the feed has no price for says so rather than leaving it empty.
 */
@Composable
private fun StockBand(stock: ConsolidatedRecommendation, latest: LatestPrice?, calls: Int, channels: Int) {
    val close = latest?.session?.close
    val count = buildString {
        append(if (calls == 1) "1 call" else "$calls calls")
        if (channels > 1) append(" · $channels channels")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.raised)
            .padding(horizontal = TileInset, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StockLogo(stock.stockCode, LogoSize.Row, Modifier.padding(end = Space.s))
        Column(Modifier.weight(1f)) {
            AutoSizeText(stock.stockCode, style = MaterialTheme.typography.titleMedium, color = palette.ink)
            AutoSizeText(
                // The Arabic name where the post gave one, as the cards draw it.
                listOfNotNull(
                    (stock.stockNameArabic ?: stock.stockNameEnglish)?.takeIf(String::isNotBlank),
                    count,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (close != null) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = Space.s)) {
                AutoSizeText(
                    formatPrice(close),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                    color = PriceRole.market,
                )
                AutoSizeText(
                    "now",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    modifier = Modifier.padding(start = Space.xs, bottom = 2.dp),
                )
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
    peak: Double?,
    status: Pair<String, CallGroup>,
    onClick: () -> Unit,
    holdItems: List<MenuItem>,
) {
    HoldMenu(items = holdItems, onTap = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TileInset, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WhoCell(channel, status, point.isTPlusOne, Modifier.width(WhoWidth))
            FigureCell(Modifier.weight(EntryWeight), entryText(point), PriceRole.entry)
            // A watched stock shows whatever levels its post gave, like any call.
            FigureCell(Modifier.weight(1f), formatPrice(point.stopLoss), PriceRole.stop, fromEntry(point, point.stopLoss))
            FigureCell(Modifier.weight(1f), formatPrice(point.target1), PriceRole.target, fromEntry(point, point.target1))
            FigureCell(Modifier.weight(1f), formatPrice(point.target2), PriceRole.target, fromEntry(point, point.target2))
            if (point.isWatching) {
                // Nothing was bought, so there is no move to report: the live columns are the room
                // its note needs to be read whole.
                Box(
                    Modifier.weight(LiveColumns.toFloat()).heightIn(min = CellHeight).padding(start = Space.s),
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
                MoveCell(Modifier.weight(1f), moveVsEntry(point, close))
                // The peak in the ink, and under it how far above the entry it went.
                FigureCell(Modifier.weight(1f), formatPrice(peak), palette.ink, fromEntry(point, peak), underTone = palette.gain)
                val stopLeft = aboveStop(point, close)
                val stopped = status.second == CallGroup.STOPPED || (stopLeft != null && stopLeft <= 0.0)
                FigureCell(
                    Modifier.weight(1f),
                    if (stopped) "hit" else formatPercent(stopLeft, signed = false),
                    if (stopped) palette.loss else palette.ink,
                )
                LeftCell(Modifier.weight(1f), toTarget1(point, close))
                LeftCell(Modifier.weight(1f), toTarget2(point, close))
            }
        }
    }
}

/** What is left to a target: the distance, or "reached" in the gain's colour once there is none. */
@Composable
private fun LeftCell(modifier: Modifier, left: Double?) {
    val there = left != null && left <= 0.0
    FigureCell(modifier, if (there) "reached" else formatPercent(left, signed = false), if (there) palette.gain else palette.ink)
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

/**
 * A figure, right-aligned and monospaced, so a column can be compared down and not only across,
 * with [fromEntry] under it in the same colour where the call has an entry to measure from.
 */
@Composable
private fun FigureCell(
    modifier: Modifier,
    value: String,
    tone: Color,
    fromEntry: Double? = null,
    /** The colour of the line under the figure, where it differs from the figure's own. */
    underTone: Color = tone,
) {
    val shown = if (value == Dash) PriceRole.muted else tone
    Column(
        modifier.heightIn(min = CellHeight).padding(horizontal = Space.xs),
        verticalArrangement = Arrangement.Center,
    ) {
        AutoSizeText(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
            color = shown,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
        if (fromEntry != null) {
            AutoSizeText(
                formatPercent(fromEntry),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                color = if (value == Dash) shown else underTone,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Where the price is against the entry, in a pill washed with its own colour: gain above, loss
 * below, and neutral where the move rounds to nothing. A bare dash where there is no price.
 */
@Composable
private fun MoveCell(modifier: Modifier, move: Double?) {
    val text = formatPercent(move)
    // Judged on what is printed, so a move that reads "0%" is never drawn as a gain or a loss.
    val flat = move == null || text == "0%"
    val tone = when {
        flat -> palette.muted
        move!! > 0 -> palette.gain
        else -> palette.loss
    }
    Column(modifier.padding(horizontal = Space.xs)) {
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
