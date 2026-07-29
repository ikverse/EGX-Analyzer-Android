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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
) {
    if (stocks.isEmpty()) return
    val scroll = rememberScrollState()

    // Measured here rather than from the window: the table sits inside a pane that is narrower than
    // the screen, so window width would promise room the table does not have.
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        val columns = (if (maxWidth >= WideTableMinWidth) WideColumns else CompactColumns) +
            imageColumn(imagePathFor, onOpenImage)
        Column {
            HeaderRow(columns, scroll)
            stocks.forEach { stock ->
                StockHeadingRow(stock)
                stock.dataPoints.forEach { point ->
                    SourceRow(
                        point = point,
                        pinned = channelFor(point.sourceMessageId) ?: point.sourceMessageId,
                        columns = columns,
                        scroll = scroll,
                        onClick = { onSelectPoint(stock, point) },
                    )
                }
            }
        }
    }
}

/** Enough room for the full desktop column set without the pinned column crowding it. */
private val WideTableMinWidth = 620.dp

@Composable
private fun HeaderRow(columns: List<TableColumn>, scroll: androidx.compose.foundation.ScrollState) {
    Row(
        Modifier
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
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stock.stockCode,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "  ${stock.stockNameArabic ?: stock.stockNameEnglish.orEmpty()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (stock.dataPoints.any(RecommendationDataPoint::isWatching)) {
            Text(
                "WATCHING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun SourceRow(
    point: RecommendationDataPoint,
    pinned: String?,
    columns: List<TableColumn>,
    scroll: androidx.compose.foundation.ScrollState,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        Row(Modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
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
) {
    Text(
        value?.takeIf(String::isNotBlank) ?: "—",
        style = if (emphasis) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
        color = tone ?: MaterialTheme.colorScheme.onSurface,
        textAlign = align,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

private fun number(value: Double?): String? = value?.let {
    if (it == it.toLong().toDouble()) it.toLong().toString() else "%.2f".format(it)
}

private fun percent(value: Double?): String? = value?.let { "%+.1f%%".format(it) }

private fun entry(point: RecommendationDataPoint): String? {
    val low = point.buyPriceLow
    val high = point.buyPriceHigh
    return when {
        low != null && high != null && low != high -> "${number(low)} – ${number(high)}"
        else -> number(point.buyPrice ?: low ?: high)
    }
}

private fun timing(point: RecommendationDataPoint): String? = when {
    point.isWatching -> "Watching"
    point.effectiveDateBasis == "explicit_date" -> "Explicit date"
    else -> point.effectiveDateBasis
}

private val PinnedWidth = 116.dp

/** Folded: the six fields that decide a trade. The rest stay reachable by scrolling. */
private val CompactColumns: List<TableColumn> = listOf(
    TableColumn("Timing", 92.dp, TextAlign.Start) { p -> TextCell(timing(p), 92.dp) },
    TableColumn("Entry", 104.dp, TextAlign.End) { p ->
        TextCell(entry(p), 104.dp, TextAlign.End, MaterialTheme.colorScheme.primary, emphasis = true)
    },
    TableColumn("TP1", 78.dp, TextAlign.End) { p ->
        TextCell(number(p.target1), 78.dp, TextAlign.End, MaterialTheme.colorScheme.tertiary)
    },
    TableColumn("TP2", 78.dp, TextAlign.End) { p ->
        TextCell(number(p.target2), 78.dp, TextAlign.End, MaterialTheme.colorScheme.tertiary)
    },
    TableColumn("Stop", 78.dp, TextAlign.End) { p ->
        TextCell(number(p.stopLoss), 78.dp, TextAlign.End, MaterialTheme.colorScheme.error)
    },
    TableColumn("Risk %", 72.dp, TextAlign.End) { p ->
        TextCell(percent(p.riskPct), 72.dp, TextAlign.End)
    },
    TableColumn("Source date", 104.dp, TextAlign.Start) { p ->
        TextCell(p.visibleSourceDate, 104.dp)
    },
    TableColumn("Target date", 104.dp, TextAlign.Start) { p ->
        TextCell(p.date?.toString(), 104.dp)
    },
    TableColumn("Support", 80.dp, TextAlign.End) { p ->
        TextCell(number(p.support), 80.dp, TextAlign.End)
    },
    TableColumn("Resistance", 88.dp, TextAlign.End) { p ->
        TextCell(number(p.resistance), 88.dp, TextAlign.End)
    },
)

/** Unfolded: every desktop column, in the desktop's order, with notes inline. */
private val WideColumns: List<TableColumn> = listOf(
    TableColumn("Target date", 104.dp, TextAlign.Start) { p -> TextCell(p.date?.toString(), 104.dp) },
    TableColumn("Source date", 108.dp, TextAlign.Start) { p -> TextCell(p.visibleSourceDate, 108.dp) },
    TableColumn("Timing", 96.dp, TextAlign.Start) { p -> TextCell(timing(p), 96.dp) },
    TableColumn("Entry", 116.dp, TextAlign.End) { p ->
        TextCell(entry(p), 116.dp, TextAlign.End, MaterialTheme.colorScheme.primary, emphasis = true)
    },
    TableColumn("TP1", 80.dp, TextAlign.End) { p ->
        TextCell(number(p.target1), 80.dp, TextAlign.End, MaterialTheme.colorScheme.tertiary)
    },
    TableColumn("TP1 Return %", 92.dp, TextAlign.End) { p ->
        TextCell(percent(p.returnTp1Pct), 92.dp, TextAlign.End, MaterialTheme.colorScheme.tertiary)
    },
    TableColumn("TP2", 80.dp, TextAlign.End) { p ->
        TextCell(number(p.target2), 80.dp, TextAlign.End, MaterialTheme.colorScheme.tertiary)
    },
    TableColumn("TP2 Return %", 92.dp, TextAlign.End) { p ->
        TextCell(percent(p.returnTp2Pct), 92.dp, TextAlign.End, MaterialTheme.colorScheme.tertiary)
    },
    TableColumn("Stop", 80.dp, TextAlign.End) { p ->
        TextCell(number(p.stopLoss), 80.dp, TextAlign.End, MaterialTheme.colorScheme.error)
    },
    TableColumn("Support", 84.dp, TextAlign.End) { p -> TextCell(number(p.support), 84.dp, TextAlign.End) },
    TableColumn("Resistance", 92.dp, TextAlign.End) { p -> TextCell(number(p.resistance), 92.dp, TextAlign.End) },
    TableColumn("Risk %", 76.dp, TextAlign.End) { p -> TextCell(percent(p.riskPct), 76.dp, TextAlign.End) },
    TableColumn("Notes", 260.dp, TextAlign.End) { p -> TextCell(p.notesArabic, 260.dp, TextAlign.End) },
)

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
