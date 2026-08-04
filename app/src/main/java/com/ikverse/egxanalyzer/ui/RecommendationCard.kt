package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import java.time.LocalDate

/**
 * Every occurrence of one stock, one card each, swiped through sideways.
 *
 * A stock is often called twice in a report - by two channels, or once as a watch and once for a
 * named date - and those calls have different entries and different targets. Folding them into a
 * single card showed the first and silently dropped the rest, which is the one thing a table row
 * never did.
 */
@Composable
internal fun RecommendationCards(
    stock: ConsolidatedRecommendation,
    /** The channel behind an occurrence, looked up by the message the model cited. */
    channelFor: (String?) -> String?,
    /** Highest the stock has traded since a given call date, drawn as the ladder's arrow. */
    peakFor: (String, LocalDate?) -> Double?,
    modifier: Modifier = Modifier,
) {
    val points = stock.dataPoints
    if (points.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(),
            elevation = CardDefaults.elevatedCardElevation(),
        ) {
            Column(Modifier.padding(Space.l)) { StockHeader(stock, point = null, channel = null) }
        }
        return
    }

    val pager = rememberPagerState(pageCount = { points.size })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        HorizontalPager(
            state = pager,
            // The peek is what makes the swipe discoverable: a card that filled the width would
            // look like the only one there is. A lone occurrence keeps the full width.
            contentPadding = PaddingValues(horizontal = if (points.size > 1) CardPeek else 0.dp),
            pageSpacing = Space.s,
            verticalAlignment = Alignment.Top,
        ) { page ->
            val point = points[page]
            RecommendationCard(
                stock = stock,
                point = point,
                channel = channelFor(point.sourceMessageId),
                peak = peakFor(stock.stockCode, point.date),
            )
        }
        if (points.size > 1) PagerFooter(pager.currentPage, points.size)
    }
}

/** How much of the neighbouring cards shows at each edge. */
private val CardPeek = 24.dp

@Composable
private fun PagerFooter(current: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (index == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
        }
        Spacer(Modifier.width(Space.s))
        Text(
            "${current + 1} of $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One occurrence: what this source, on this date, actually said.
 *
 * Collapsed it answers "what is the trade"; expanded it answers "where did this come from",
 * which matters because the app's whole claim is that every number is traceable to a source.
 */
@Composable
private fun RecommendationCard(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    channel: String?,
    peak: Double?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(point) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            StockHeader(stock, point, channel)

            PriceLadder(point, peak = peak)
            LevelRow(point)
            point.riskRewardRatio()?.let { ratio ->
                Text(
                    "Risk / reward  1 : ${"%.1f".format(ratio)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // This occurrence's own note where it has one; the stock's summary is the fallback,
            // not the default, or three cards would repeat the same paragraph.
            (point.notesArabic ?: stock.notesSummary)?.let { notes ->
                Text(
                    notes,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                if (expanded) "Hide source" else "Source",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    HorizontalDivider()
                    OccurrenceDetail(point)
                }
            }
        }
    }
}

@Composable
private fun StockHeader(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint?,
    channel: String?,
) {
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
            // Two cards for one stock can be identical apart from who said it.
            channel?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (point != null) TimingChip(point)
    }
}

/**
 * What dated the call, in place of the buy/sell signal.
 *
 * Every occurrence in a report is a buy, so the old chip said the same word on every card; which
 * session a call is for is the thing that actually differs between two rows of the same stock.
 */
@Composable
private fun TimingChip(point: RecommendationDataPoint) {
    // Falls back to the signal only when the model recorded no basis at all, so the chip is
    // never blank.
    val label = timing(point) ?: point.recommendationType?.uppercase() ?: "-"
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label, fontWeight = FontWeight.Bold) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
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
