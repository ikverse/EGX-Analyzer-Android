package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint

/**
 * One stock, with every occurrence the model extracted for it.
 *
 * Collapsed it answers "what is the trade"; expanded it answers "where did this come from",
 * which matters because the app's whole claim is that every number is traceable to a source.
 */
@Composable
internal fun RecommendationCard(stock: ConsolidatedRecommendation, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val headline = stock.dataPoints.firstOrNull()

    Card(
        modifier = modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            StockHeader(stock)

            if (headline != null) {
                PriceLadder(headline)
                LevelRow(headline)
                headline.riskRewardRatio()?.let { ratio ->
                    Text(
                        "Risk / reward  1 : ${"%.1f".format(ratio)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            stock.notesSummary?.let { notes ->
                Text(
                    notes,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                if (expanded) "Hide sources" else "${stock.dataPoints.size} source occurrence" +
                    if (stock.dataPoints.size == 1) "" else "s",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    stock.dataPoints.forEach { point ->
                        HorizontalDivider()
                        OccurrenceDetail(point)
                    }
                }
            }
        }
    }
}

@Composable
private fun StockHeader(stock: ConsolidatedRecommendation) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stock.stockCode,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            // The Arabic name is the one printed in the source, so it is the reliable identity.
            stock.stockNameArabic?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            stock.stockNameEnglish?.takeIf { it != stock.stockCode }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            SignalChip(stock)
            if (stock.dataPoints.any(RecommendationDataPoint::isWatching)) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "Watching",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun SignalChip(stock: ConsolidatedRecommendation) {
    val types = stock.dataPoints.mapNotNull { it.recommendationType?.lowercase() }.toSet()
    val label = when {
        types.contains("sell") && !types.contains("buy") -> "SELL"
        types.contains("buy") -> "BUY"
        else -> "HOLD"
    }
    val tone = when (label) {
        "BUY" -> MaterialTheme.colorScheme.tertiaryContainer
        "SELL" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label, fontWeight = FontWeight.Bold) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = tone,
            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/** Entry, targets and stop as labelled figures, each with its percentage where the source gave one. */
@Composable
private fun LevelRow(point: RecommendationDataPoint) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Level("Entry", entryText(point), PriceRole.entry)
        point.target1?.let {
            Level("Target 1", figure(it, point.returnTp1Pct ?: impliedReturn(point, it)), PriceRole.target)
        }
        point.target2?.let {
            Level("Target 2", figure(it, point.returnTp2Pct ?: impliedReturn(point, it)), PriceRole.target)
        }
        point.stopLoss?.let { Level("Stop loss", figure(it, point.riskPct), PriceRole.stop) }
    }
}

@Composable
private fun Level(label: String, value: String, tone: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tone)
    }
}

@Composable
private fun OccurrenceDetail(point: RecommendationDataPoint) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            listOfNotNull(
                point.visibleSourceDate?.let { "Source date $it" },
                point.sourceImageRef?.let { "Image $it" },
                point.sourceMessageId?.let { "Message $it" },
            ).joinToString(" · ").ifBlank { "Source not recorded" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        point.recommendationEvidence?.let {
            Text("“$it”", style = MaterialTheme.typography.bodySmall)
        }
        point.timingEvidence?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            point.support?.let {
                Text("Support ${formatPrice(it)}", style = MaterialTheme.typography.labelMedium, color = PriceRole.market)
            }
            point.resistance?.let {
                Text("Resistance ${formatPrice(it)}", style = MaterialTheme.typography.labelMedium, color = PriceRole.market)
            }
        }
        point.notesArabic?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun entryText(point: RecommendationDataPoint): String {
    val low = point.buyPriceLow
    val high = point.buyPriceHigh
    return when {
        low != null && high != null && low != high -> "${formatPrice(low)} – ${formatPrice(high)}"
        else -> formatPrice(point.buyPrice ?: low ?: high)
    }
}

private fun figure(value: Double, percent: Double?): String =
    if (percent == null) formatPrice(value) else "${formatPrice(value)}  (${formatPercent(percent)})"

/** Entry midpoint to target, so a card shows the upside even when the source never printed it. */
private fun impliedReturn(point: RecommendationDataPoint, target: Double?): Double? {
    if (target == null) return null
    val low = point.buyPriceLow ?: point.buyPrice
    val high = point.buyPriceHigh ?: point.buyPrice
    val entry = when {
        low != null && high != null -> (low + high) / 2
        else -> low ?: high ?: return null
    }
    return if (entry == 0.0) null else (target - entry) / entry * 100
}
