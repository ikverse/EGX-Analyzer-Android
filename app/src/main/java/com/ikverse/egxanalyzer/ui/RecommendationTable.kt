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
import androidx.compose.material3.LocalContentColor
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
import com.ikverse.egxanalyzer.ui.kit.MenuItem
import com.ikverse.egxanalyzer.ui.theme.palette

/**
 * The report's calls as one table, one row per call.
 *
 * **It fits, and that is the whole of the design.** It was sixteen fixed-width columns behind a
 * horizontal scroll until 2026-09-09, and the width they came to was a function of how many had
 * been added: crossing a breakpoint *appended* columns, so the tablet's 682dp showed 49% of a row
 * where the smaller Fold's 614dp showed 64%. Only [StockWidth] and [StatusWidth] are fixed now and
 * every other column is weighted, so a wider window widens the columns rather than adding more.
 *
 * Since 5.1 it is one flat block rather than a block per stock. The per-stock heading carried the
 * latest price, which is a column of its own now (`Now`); the percentages under each level and the
 * support and resistance went to the sheet a row opens, where a single call is the whole subject.
 * At the narrowest container that earns a table, 600dp, the row spends [Space].m either side, 132dp
 * on the stock and 92dp on the status, and divides the rest over 7.6 shares - about 46dp a share,
 * with [AutoSizeText] holding the figures.
 *
 * A tap opens the call; a hold opens [holdItems], the same menu a card offers.
 */
@Composable
internal fun RecommendationTable(
    stocks: List<ConsolidatedRecommendation>,
    channelFor: (String?) -> String?,
    /**
     * Where a stock is now, for the `Now` column and the ladder's arrow. A lambda rather than a map,
     * so a price moving on one stock does not hand the table the whole record.
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
            var index = 0
            stocks.forEach { stock ->
                val latest = latestFor(stock.stockCode)
                stock.dataPoints.forEach { point ->
                    CallRow(
                        stock = stock,
                        point = point,
                        channel = channelFor(point.sourceMessageId) ?: point.sourceMessageId,
                        latest = latest,
                        status = statusFor(stock, point),
                        striped = index % 2 == 1,
                        onClick = { onSelectPoint(stock, point) },
                        holdItems = holdItems(stock, point),
                    )
                    index++
                }
            }
        }
    }
}

/** The logo, the ticker, and the channel under it. The one column that holds words. */
private val StockWidth = 132.dp

/** The status word, unlabelled: the word says what it is. */
private val StatusWidth = 92.dp

/**
 * What the entry column gets for every 1 the other figure columns get. Entry is the only cell
 * holding two prices and the dash between them, fifteen characters where a target holds six.
 */
private const val EntryWeight = 1.6f

/** The ladder's share: twice a figure, enough for the shape to read. */
private const val LadderWeight = 2f

/**
 * A minimum rather than a fixed height, so a large font scale grows the row instead of clipping it.
 * 64dp holds the tallest cell, an Arabic channel name under a ticker.
 */
private val RowHeight = 64.dp

@Composable
private fun ColumnHeader() {
    Row(
        Modifier.fillMaxWidth().padding(start = Space.m, end = Space.m, top = Space.s, bottom = Space.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        HeaderLabel("Stock", Modifier.width(StockWidth), TextAlign.Start)
        HeaderLabel("Now", Modifier.weight(1f), TextAlign.End)
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

@Composable
private fun CallRow(
    stock: ConsolidatedRecommendation,
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
            StockCell(stock, channel, Modifier.width(StockWidth))
            FigureCell(Modifier.weight(1f), formatPrice(close), if (close == null) PriceRole.muted else PriceRole.market)
            FigureCell(Modifier.weight(EntryWeight), entryText(point), PriceRole.entry)
            // A watched stock names no stop and no targets worth reading: the dash, not its levels.
            FigureCell(Modifier.weight(1f), if (watching) Dash else formatPrice(point.stopLoss), PriceRole.stop)
            FigureCell(Modifier.weight(1f), if (watching) Dash else formatPrice(point.target1), PriceRole.target)
            FigureCell(Modifier.weight(1f), if (watching) Dash else formatPrice(point.target2), PriceRole.target)
            Box(
                Modifier.weight(LadderWeight).padding(horizontal = Space.xs),
                contentAlignment = Alignment.Center,
            ) {
                if (watching) {
                    AutoSizeText(
                        watchLevel(point)?.let { "only above ${formatPrice(it)}" } ?: "watching",
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

/** On the ladder cell, so a test can tell a row that draws one from a row that does not. */
internal const val LadderCellTag = "call-ladder"

/** The logo and the ticker on one line, the channel under them. */
@Composable
private fun StockCell(stock: ConsolidatedRecommendation, channel: String?, modifier: Modifier) {
    Column(
        modifier.padding(horizontal = Space.xs, vertical = Space.xs),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StockLogo(stock.stockCode, LogoSize.Row, Modifier.padding(end = Space.xs))
            AutoSizeText(
                stock.stockCode,
                style = MaterialTheme.typography.titleSmall,
                color = palette.ink,
            )
        }
        // The one place the shrink's floor is reached in practice: channel names run long and this
        // column is fixed, so a name that will not fit still ends in a dot.
        AutoSizeText(
            channel ?: Dash,
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
        )
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
