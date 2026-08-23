package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint

/**
 * Everything about one extracted occurrence.
 *
 * The folded table shows six columns, so this carries the fields that did not fit plus the
 * evidence a number is supposed to be traceable to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OccurrenceSheet(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    imagePath: String? = null,
    /** Highest the stock has traded since the call, drawn as the ladder's arrow. */
    peak: Double? = null,
    /** The channel behind this occurrence, recorded with a trade taken on it. */
    channel: String? = null,
    /** Records what the user did about this call. Absent, the sheet is read-only. */
    trades: TradeBook? = null,
    onDismiss: () -> Unit,
) {
    var viewingImage by remember { mutableStateOf(false) }
    val held = trades?.heldFor(stock, point)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(stock.stockCode, style = MaterialTheme.typography.headlineSmall)
                stock.stockNameArabic?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            PriceLadder(point, reached = peak)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                SheetFigure("Entry", entryText(point), PriceRole.entry)
                point.target1?.let { SheetFigure("Target 1", withPercent(it, point.returnTp1Pct), PriceRole.target) }
                point.target2?.let { SheetFigure("Target 2", withPercent(it, point.returnTp2Pct), PriceRole.target) }
                point.stopLoss?.let { SheetFigure("Stop loss", withPercent(it, point.riskPct), PriceRole.stop) }
                point.support?.let { SheetFigure("Support", plain(it), PriceRole.market) }
                point.resistance?.let { SheetFigure("Resistance", plain(it), PriceRole.market) }
            }

            point.riskRewardRatio()?.let {
                Text(
                    "Risk / reward  1 : ${"%.1f".format(it)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Above the source trace rather than below it: a decision to buy is made from the
            // figures, and the evidence underneath is what you read when you doubt them.
            if (trades != null && trades.dateOf(point) != null) {
                TradeAction(
                    held = held,
                    suggestedEntry = point.entryMidpoint(),
                    defaultWindow = trades.windowFor(point),
                    tPlusOne = point.isTPlusOne,
                    onBuy = { price, date, window ->
                        trades.buy(stock, point, channel, price, date, window)
                    },
                    onSell = { price, date -> held?.let { trades.sell(it, price, date) } },
                )
            }

            HorizontalDivider()

            Text(
                listOfNotNull(
                    point.visibleSourceDate?.let { "Source date $it" },
                    point.date?.let { "Target $it" },
                    point.sourceImageRef?.let { "Image $it" },
                    point.sourceMessageId?.let { "Message $it" },
                ).joinToString(" · ").ifBlank { "Source not recorded" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (imagePath != null) {
                SourceImageThumbnail(
                    path = imagePath,
                    reference = point.sourceImageRef,
                    size = 88.dp,
                    onOpen = { viewingImage = true },
                )
            }
            point.recommendationEvidence?.let {
                Text("“$it”", style = MaterialTheme.typography.bodyMedium)
            }
            point.timingEvidence?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            (point.notesArabic ?: stock.notesSummary)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (viewingImage) {
        SourceImageViewer(imagePath, point.sourceImageRef, onDismiss = { viewingImage = false })
    }
}

/**
 * The sheet's own weight on the shared figure.
 *
 * Larger than a card's, and the one place the digits are not monospaced: this sheet is about a
 * single call, so nothing here is being compared down a column - the reason the tabular figures
 * exist everywhere else.
 */
@Composable
private fun SheetFigure(label: String, value: String, tone: androidx.compose.ui.graphics.Color) =
    Figure(
        label,
        value,
        tone = tone,
        valueStyle = MaterialTheme.typography.titleMedium,
    )

private fun plain(value: Double): String =
    formatPrice(value)

private fun withPercent(value: Double, percent: Double?): String =
    if (percent == null) plain(value) else "${plain(value)}  (${formatPercent(percent)})"

private fun entryText(point: RecommendationDataPoint): String {
    val low = point.buyPriceLow
    val high = point.buyPriceHigh
    return when {
        low != null && high != null && low != high -> "${plain(low)} – ${plain(high)}"
        else -> (point.buyPrice ?: low ?: high)?.let(::plain) ?: "—"
    }
}
