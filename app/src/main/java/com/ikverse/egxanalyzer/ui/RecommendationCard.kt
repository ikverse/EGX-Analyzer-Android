package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.timing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint

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

            // No ladder here, deliberately. This card is a row of the report that would not fit as
            // a row: what it owes the reader is the call's figures, and a drawing of the same five
            // numbers doubled the card's height to say what LevelGrid says underneath it. The
            // occurrence sheet and the Portfolio card still draw it, where a single call is the
            // whole subject rather than one of a dozen being scanned.
            LevelGrid(point)
            point.riskRewardRatio()?.let { ratio ->
                Text(
                    "Risk / reward  1 : ${"%.1f".format(ratio)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Only where the call has a session to belong to: an occurrence the model left undated
            // cannot be scored, so a trade filed against it would have no deadline to run to.
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
                    // Recording a purchase belongs on the card the call was read off; closing a
                    // position does not. A sale is the end of a trade, and it is made where the
                    // trade lives - Portfolio, or the occurrence sheet - not off a card being
                    // scanned for what to buy next. The held and overdue chips still show here.
                    canSell = false,
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
            // The logo rides the ticker's own line rather than sitting beside the whole column.
            // Beside the column it left a hole under itself the height of the names below the
            // ticker, and it pushed those names in past the ladder and the levels, which start at
            // the card's edge - one card, two left edges. Here the names stay flush with them.
            Row(verticalAlignment = Alignment.CenterVertically) {
                StockLogo(stock.stockCode, LogoSize.Header, Modifier.padding(end = Space.s))
                Text(
                    stock.stockCode,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
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

/**
 * Entry, targets and stop as labelled figures, each with its percentage where the source gave one,
 * followed by the two market levels the call was drawn against.
 *
 * Six fixed slots in two columns, not a flow. A tile is as wide as its number happens to print, so
 * flowing them put the same figure in a different place on every card - support trailing the stop
 * on one, alone on a line of its own on the next - and a column of cards read as six loose numbers
 * rather than one shape repeated. Fixed slots mean a card can be read down as well as across, and
 * the market levels always land last. Left to right then down, which is the order the table, the
 * occurrence sheet and the export all print these in.
 *
 * Every slot is drawn whether or not the source filled it: a card whose rows move depending on what
 * the channel happened to publish is the thing this layout exists to stop, and a dash says "no
 * figure given" where a closed gap says nothing at all.
 *
 * Support and resistance belong here rather than under the source trace: they are figures the call
 * was made on, not evidence for where it came from. A stop sitting a hair under support is the
 * reason that stop is where it is, and that only reads when the two are a glance apart.
 */
@Composable
private fun LevelGrid(point: RecommendationDataPoint) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        LevelPair(
            { Level("Entry", entryText(point), PriceRole.entry, it) },
            { Level("Target 1", figure(point.target1, returnPct(point, point.target1, point.returnTp1Pct)), PriceRole.target, it) },
        )
        LevelPair(
            { Level("Target 2", figure(point.target2, returnPct(point, point.target2, point.returnTp2Pct)), PriceRole.target, it) },
            { Level("Stop loss", figure(point.stopLoss, point.riskPct), PriceRole.stop, it) },
        )
        // Plain, with no percentage beside them. The other four are distances from the entry, which
        // is what a percentage measures here; a support is simply a price the stock has held at.
        LevelPair(
            { Level("Support", formatPrice(point.support), PriceRole.market, it) },
            { Level("Resistance", formatPrice(point.resistance), PriceRole.market, it) },
        )
    }
}

/**
 * One row of the grid: two slots of equal width, whatever their numbers print at.
 *
 * The width is handed to each slot rather than taken by it, so the columns are the row's business
 * and a [Level] stays a label over a figure.
 */
@Composable
private fun LevelPair(
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        left(Modifier.weight(1f))
        right(Modifier.weight(1f))
    }
}

@Composable
private fun Level(
    label: String,
    value: String,
    tone: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
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

/** A price with its percentage, or the dash where the source gave no such level at all. */
private fun figure(value: Double?, percent: Double?): String =
    if (value == null || percent == null) {
        formatPrice(value)
    } else {
        "${formatPrice(value)}  (${formatPercent(percent)})"
    }

/** What a source printed against a target, or what the entry implies where it printed nothing. */
private fun returnPct(point: RecommendationDataPoint, target: Double?, stated: Double?): Double? =
    stated ?: impliedReturn(point, target)

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
