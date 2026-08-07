package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint

/**
 * The desktop's recommendation table, grouped by stock.
 *
 * The ticker column is pinned outside the horizontally scrolling area and every row shares one
 * scroll state, so the columns stay aligned and the stock a number belongs to is never lost while
 * scrolling sideways - the same reason the desktop keeps Source in the first column.
 */
@Composable
internal fun RecommendationTable(
    stocks: List<ConsolidatedRecommendation>,
    channelFor: (String?) -> String?,
    imagePathFor: (Int?) -> String?,
    onOpenImage: (Int?) -> Unit,
    onSelectPoint: (ConsolidatedRecommendation, RecommendationDataPoint) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Drawn above the header and pinned with it.
     *
     * The controls that decide what the table shows belong to the table: reaching them used to mean
     * scrolling back past every row to the top of the report.
     */
    toolbar: (@Composable () -> Unit)? = null,
) {
    if (stocks.isEmpty()) return
    val scroll = rememberScrollState()
    // How far the table's own top has been scrolled past the top of the page's viewport. The header
    // is pushed back down by exactly that much, so it looks pinned without leaving the table: once
    // the last row is gone the header goes with it rather than hanging over the next card.
    val viewportTop = LocalViewportTop.current
    var pin by remember { mutableFloatStateOf(0f) }
    // The toolbar and the header travel together, so the clamp is measured over both.
    var pinnedHeight by remember { mutableFloatStateOf(0f) }

    // Measured here rather than from the window: the table sits inside a pane that is narrower than
    // the screen, so window width would promise room the table does not have.
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .onGloballyPositioned { coordinates ->
                val top = coordinates.positionInWindow().y
                val height = coordinates.size.height.toFloat()
                pin = (viewportTop - top).coerceIn(0f, max(0f, height - pinnedHeight))
            },
    ) {
        // Every width gets the trade columns, including both returns - they are the point of the
        // table. Context is added as room allows rather than a separate column set, so nothing a
        // decision needs is ever the thing that gets dropped.
        val columns = TradeColumns +
            (if (maxWidth >= ContextMinWidth) ContextColumns else emptyList()) +
            (if (maxWidth >= NotesMinWidth) listOf(NotesColumn) else emptyList()) +
            imageColumn(imagePathFor, onOpenImage)
        Column {
            Column(
                Modifier
                    .graphicsLayer { translationY = pin }
                    .zIndex(1f)
                    .onGloballyPositioned { pinnedHeight = it.size.height.toFloat() },
            ) {
                toolbar?.let {
                    // Opaque and full width, because it slides across the rows rather than pushing
                    // them: a background sized to its contents let the table show through beside it.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    ) { it() }
                }
                HeaderRow(columns, scroll)
            }
            stocks.forEach { stock ->
                StockHeadingRow(stock)
                stock.dataPoints.forEachIndexed { index, point ->
                    SourceRow(
                        point = point,
                        pinned = channelFor(point.sourceMessageId) ?: point.sourceMessageId,
                        columns = columns,
                        scroll = scroll,
                        // Alternating tint: with this many columns the eye loses the row it was
                        // following somewhere around the middle.
                        striped = index % 2 == 1,
                        onClick = { onSelectPoint(stock, point) },
                    )
                }
            }
        }
    }
}

/** Room for the levels a call implies without pushing the ones it states off the screen. */
private val ContextMinWidth = 620.dp

/** Arabic notes need real width or they truncate to nothing useful. */
private val NotesMinWidth = 900.dp

@Composable
private fun HeaderRow(
    columns: List<TableColumn>,
    scroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("Source", PinnedWidth, TextAlign.Start)
        VerticalRule()
        Row(Modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
            columns.forEach { column ->
                HeaderCell(column.label, column.width, column.align)
            }
        }
    }
}

@Composable
private fun StockHeadingRow(stock: ConsolidatedRecommendation) {
    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The anchor for everything under it, so it carries more weight than a row does.
        Text(
            stock.stockCode,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            stock.stockNameArabic?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Dimmer than the Arabic name because the model supplies it from its own knowledge
            // rather than reading it from the source, and it is regularly wrong.
            stock.stockNameEnglish?.takeIf { it != stock.stockCode }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (stock.dataPoints.isNotEmpty() && stock.dataPoints.all(RecommendationDataPoint::isWatching)) {
            // Every occurrence, not any: a stock called for a buy by one source and merely watched
            // by another was labelled Watch list, which reads as though nobody had called it.
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    "Watch list",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    point: RecommendationDataPoint,
    pinned: String?,
    columns: List<TableColumn>,
    scroll: androidx.compose.foundation.ScrollState,
    striped: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (striped) {
                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.4f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            // A minimum rather than a fixed height, so a large font scale grows the row instead of
            // clipping it.
            .heightIn(min = 46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(PinnedWidth).padding(horizontal = 10.dp)) {
            Text(
                pinned ?: "—",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VerticalRule()
        Row(
            Modifier.fadingScrollbar(scroll, horizontal = true).horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            columns.forEach { column -> column.cell(point) }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun HeaderCell(label: String, width: Dp, align: TextAlign) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 2,
        modifier = Modifier.width(width).padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun VerticalRule() {
    Box(
        Modifier
            .width(1.dp)
            .height(40.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** One column: how wide, how aligned, and how to render a cell for a given occurrence. */
private class TableColumn(
    val label: String,
    val width: Dp,
    val align: TextAlign,
    val cell: @Composable (RecommendationDataPoint) -> Unit,
)

@Composable
private fun TextCell(
    value: String?,
    width: Dp,
    align: TextAlign = TextAlign.Start,
    tone: Color? = null,
    emphasis: Boolean = false,
    /** Numbers get monospaced digits so a column can be compared down, not just read across. */
    tabular: Boolean = false,
) {
    val base = if (emphasis) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall
    Text(
        value?.takeIf(String::isNotBlank) ?: Dash,
        style = if (tabular) base.copy(fontFamily = TabularFigures) else base,
        color = tone ?: MaterialTheme.colorScheme.onSurface,
        textAlign = align,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = Space.s, vertical = Space.s),
    )
}

/** A price cell: right-aligned, monospaced, coloured by what the figure is for. */
@Composable
private fun PriceCell(value: Double?, width: Dp, tone: Color, emphasis: Boolean = false) {
    TextCell(formatPrice(value), width, TextAlign.End, tone, emphasis, tabular = true)
}

/**
 * A return percentage, calculated when the source did not print one.
 *
 * The prompt tells the model to leave it null unless the card states it, and says the application
 * works out the rest - which it never did, so the column was blank on most rows. A derived figure
 * is shown dimmer than a printed one, because the two are not the same claim.
 */
@Composable
private fun ReturnCell(point: RecommendationDataPoint, stated: Double?, target: Double?, width: Dp) {
    val derived = stated == null
    val value = stated ?: returnFrom(point, target)
    TextCell(
        formatPercent(value),
        width,
        TextAlign.End,
        if (derived) PriceRole.derived else PriceRole.forReturn(value),
        tabular = true,
    )
}

/** Entry midpoint to target, the same basis the scorer uses, so the two never disagree. */
private fun returnFrom(point: RecommendationDataPoint, target: Double?): Double? {
    if (target == null) return null
    val low = point.buyPriceLow ?: point.buyPrice
    val high = point.buyPriceHigh ?: point.buyPrice
    val entry = when {
        low != null && high != null -> (low + high) / 2
        else -> low ?: high ?: return null
    }
    if (entry == 0.0) return null
    return (target - entry) / entry * 100
}

private fun entry(point: RecommendationDataPoint): String {
    val low = point.buyPriceLow
    val high = point.buyPriceHigh
    return when {
        low != null && high != null && low != high -> "${formatPrice(low)} – ${formatPrice(high)}"
        else -> formatPrice(point.buyPrice ?: low ?: high)
    }
}

/** What dated this occurrence: the first thing read on a row, and the label on a card. */
internal fun timing(point: RecommendationDataPoint): String? = when {
    point.isWatching -> "Watching"
    point.isTPlusOne -> "T+1"
    point.effectiveDateBasis == "explicit_date" -> "Explicit date"
    else -> point.effectiveDateBasis
}

private val PinnedWidth = 116.dp

/**
 * The columns, in the order a row is actually read: what kind of call it is, what it asks you to
 * pay, what it is worth, what it risks, then the context behind it.
 *
 * Every figure a decision needs sits before the scroll on a wide screen, and the two return columns
 * sit immediately beside their targets rather than at the far end, so a target and its upside are
 * one glance rather than two.
 */
private val TradeColumns: List<TableColumn> = listOf(
    TableColumn("Timing", 96.dp, TextAlign.Start) { p ->
        TextCell(timing(p), 96.dp, tone = PriceRole.muted)
    },
    TableColumn("Entry", 118.dp, TextAlign.End) { p ->
        TextCell(entry(p), 118.dp, TextAlign.End, PriceRole.entry, emphasis = true, tabular = true)
    },
    TableColumn("Target 1", 84.dp, TextAlign.End) { p -> PriceCell(p.target1, 84.dp, PriceRole.target) },
    TableColumn("TP1 %", 78.dp, TextAlign.End) { p -> ReturnCell(p, p.returnTp1Pct, p.target1, 78.dp) },
    TableColumn("Target 2", 84.dp, TextAlign.End) { p -> PriceCell(p.target2, 84.dp, PriceRole.target) },
    TableColumn("TP2 %", 78.dp, TextAlign.End) { p -> ReturnCell(p, p.returnTp2Pct, p.target2, 78.dp) },
    TableColumn("Stop loss", 88.dp, TextAlign.End) { p -> PriceCell(p.stopLoss, 88.dp, PriceRole.stop) },
    TableColumn("Risk %", 74.dp, TextAlign.End) { p ->
        TextCell(formatPercent(p.riskPct), 74.dp, TextAlign.End, PriceRole.stop, tabular = true)
    },
)

/** Context: true of the call, but not what you look at to judge it. */
private val ContextColumns: List<TableColumn> = listOf(
    TableColumn("Support", 84.dp, TextAlign.End) { p -> PriceCell(p.support, 84.dp, PriceRole.market) },
    TableColumn("Resistance", 92.dp, TextAlign.End) { p -> PriceCell(p.resistance, 92.dp, PriceRole.market) },
    TableColumn("Target date", 104.dp, TextAlign.Start) { p ->
        TextCell(p.date?.toString(), 104.dp, tone = PriceRole.muted)
    },
    TableColumn("Source date", 108.dp, TextAlign.Start) { p ->
        TextCell(p.visibleSourceDate, 108.dp, tone = PriceRole.muted)
    },
)

private val NotesColumn = TableColumn("Notes", 260.dp, TextAlign.Start) { p ->
    TextCell(p.notesArabic, 260.dp, tone = PriceRole.muted)
}

/** Trailing column so the thumbnail is beside the numbers it came from, in both column sets. */
private fun imageColumn(
    imagePathFor: (Int?) -> String?,
    onOpenImage: (Int?) -> Unit,
): List<TableColumn> = listOf(
    TableColumn("Source image", 72.dp, TextAlign.Start) { point ->
        Box(Modifier.width(72.dp).padding(horizontal = 8.dp, vertical = 6.dp)) {
            SourceImageThumbnail(
                path = imagePathFor(point.sourceImageRef),
                reference = point.sourceImageRef,
                onOpen = { onOpenImage(point.sourceImageRef) },
            )
        }
    },
)
