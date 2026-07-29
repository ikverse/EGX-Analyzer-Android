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
    onDismiss: () -> Unit,
) {
    var viewingImage by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stock.stockCode, style = MaterialTheme.typography.headlineSmall)
                stock.stockNameArabic?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            PriceLadder(point)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Figure("Entry", entryText(point), MaterialTheme.colorScheme.primary)
                point.target1?.let { Figure("Target 1", withPercent(it, point.returnTp1Pct), MaterialTheme.colorScheme.tertiary) }
                point.target2?.let { Figure("Target 2", withPercent(it, point.returnTp2Pct), MaterialTheme.colorScheme.tertiary) }
                point.stopLoss?.let { Figure("Stop", withPercent(it, point.riskPct), MaterialTheme.colorScheme.error) }
                point.support?.let { Figure("Support", plain(it), MaterialTheme.colorScheme.onSurface) }
                point.resistance?.let { Figure("Resistance", plain(it), MaterialTheme.colorScheme.onSurface) }
            }

            point.riskRewardRatio()?.let {
                Text(
                    "Risk / reward  1 : ${"%.1f".format(it)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun Figure(label: String, value: String, tone: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = tone)
    }
}

private fun plain(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.2f".format(value)

private fun withPercent(value: Double, percent: Double?): String =
    if (percent == null) plain(value) else "${plain(value)}  (${"%+.1f".format(percent)}%)"

private fun entryText(point: RecommendationDataPoint): String {
    val low = point.buyPriceLow
    val high = point.buyPriceHigh
    return when {
        low != null && high != null && low != high -> "${plain(low)} – ${plain(high)}"
        else -> (point.buyPrice ?: low ?: high)?.let(::plain) ?: "—"
    }
}
