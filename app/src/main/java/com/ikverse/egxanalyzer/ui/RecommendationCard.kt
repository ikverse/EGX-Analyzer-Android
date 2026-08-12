package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalDensity
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
    /** The stored photo behind an occurrence, looked up by the reference the model cited. */
    imagePathFor: (Int?) -> String?,
    /** Records what the user did about a call. Absent, the cards are read-only. */
    trades: TradeBook? = null,
    modifier: Modifier = Modifier,
) {
    val points = stock.dataPoints
    if (points.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = cardOutline,
        ) {
            Column(Modifier.padding(Space.l)) {
                StockHeader(stock, point = null, channel = null, page = 0, pageCount = 0)
            }
        }
        return
    }

    val pager = rememberPagerState(pageCount = { points.size })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        HorizontalPager(
            state = pager,
            // No peek, deliberately: insetting only the stocks with several occurrences made their
            // cards narrower than the rest, and a row of cards that do not match reads as a fault
            // before it reads as a hint. The dots in the header carry the hint instead.
            pageSpacing = Space.s,
            verticalAlignment = Alignment.Top,
        ) { page ->
            val point = points[page]
            RecommendationCard(
                stock = stock,
                point = point,
                channel = channelFor(point.sourceMessageId),
                peak = peakFor(stock.stockCode, point.date),
                imagePath = imagePathFor(point.sourceImageRef),
                trades = trades,
                page = page,
                pageCount = points.size,
            )
        }
    }
}

/**
 * Which occurrence is showing, and how many there are.
 *
 * Drawn under the timing chip rather than below the card: the header already stands two lines tall
 * to keep names level, so this costs no height, and the chip is exactly what differs between one
 * occurrence and the next - `Watching` on this page, `Explicit date` on the one after. Dots beneath
 * it read as "this chip is one of four", which is what they mean.
 */
@Composable
private fun PageDots(current: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { index ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(if (index == current) 7.dp else 5.dp)
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
    imagePath: String?,
    trades: TradeBook?,
    page: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(point) { mutableStateOf(false) }
    var viewingImage by remember(point) { mutableStateOf(false) }
    val held = trades?.heldFor(stock, point)

    Card(
        modifier = modifier.fillMaxWidth().clickable { expanded = !expanded },
        // A step up in container rather than a shadow. These sit inside the report's own card, which
        // is why they were elevated; the step is what separates them now that nothing on the page
        // casts a shadow.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        // A stock the user is actually in is outlined in the colour of where it stands, so a page
        // of calls can be read for what is held before any card is opened. Everything else carries
        // the hairline every other card on the page is drawn with.
        border = heldBorder(held) ?: cardOutline,
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            StockHeader(stock, point, channel, page, pageCount)

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

            // Only where the call has a session to belong to: an occurrence the model left undated
            // cannot be scored, so a trade filed against it would have no deadline to run to.
            if (trades != null && trades.dateOf(point) != null) {
                TradeAction(
                    held = held,
                    suggestedEntry = point.entryMidpoint(),
                    defaultWindow = trades.defaultWindowSessions,
                    onBuy = { price, date, window ->
                        trades.buy(stock, point, channel, price, date, window)
                    },
                    onSell = { price, date -> held?.let { trades.sell(it, price, date) } },
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
                    OccurrenceDetail(point, imagePath) { viewingImage = true }
                }
            }
        }
    }

    if (viewingImage) {
        SourceImageViewer(imagePath, point.sourceImageRef, onDismiss = { viewingImage = false })
    }
}

@Composable
private fun StockHeader(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint?,
    channel: String?,
    page: Int,
    pageCount: Int,
) {
    // Top-aligned so the right-hand column starts level with the ticker rather than floating
    // against the middle of however many name lines this stock happens to have.
    Row(verticalAlignment = Alignment.Top) {
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
        if (point != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Only where there is something to page through. One occurrence needs no map.
                if (pageCount > 1) {
                    // Held in a box the height of the ticker's own line and centred in it, so the
                    // dots sit against the ticker at any font scale rather than at one guessed size.
                    val tickerLine = with(LocalDensity.current) {
                        MaterialTheme.typography.headlineSmall.lineHeight.toDp()
                    }
                    Box(
                        Modifier.height(tickerLine),
                        contentAlignment = Alignment.Center,
                    ) { PageDots(page, pageCount) }
                    Spacer(Modifier.height(Space.xs))
                }
                TimingChip(point)
            }
        }
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
private fun OccurrenceDetail(
    point: RecommendationDataPoint,
    imagePath: String?,
    onOpenImage: () -> Unit,
) {
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
        // The card the numbers were read off, where a pressed table row would have shown it. The
        // thumbnail says `#4` when the photo has gone from Telegram's cache rather than a blank.
        SourceImageThumbnail(
            path = imagePath,
            reference = point.sourceImageRef,
            size = 88.dp,
            onOpen = onOpenImage,
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
