package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.timing

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                StockHeader(stock, point = null, channel = null, page = 0, pageCount = 0, session = null)
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
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            // The session the call was made for, from the same source the Bought button
            // reads it from, so the copied text and the trade agree about which day.
            StockHeader(stock, point, channel, page, pageCount, trades?.dateOf(point))

            // No ladder here, deliberately. This card is a row of the report that would not fit as
            // a row: what it owes the reader is the call's figures, and a drawing of the same five
            // numbers doubled the card's height to say what LevelGrid says underneath it. The
            // occurrence sheet and the Portfolio card still draw it, where a single call is the
            // whole subject rather than one of a dozen being scanned.
            LevelGrid(point)
            point.riskRewardRatio()?.let { ratio ->
                Text(
                    "Risk / reward  1 : ${"%.1f".format(ratio)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // A line under the figures, because the two things below it are the only parts of this
            // card that answer a press. Everything above is the call as the channel printed it.
            HorizontalDivider()

            // One row, and the two ends of it are the two kinds of press this card offers: what it
            // records, and what it opens. They sat on separate lines and read as two afterthoughts.
            //
            // A FlowRow rather than a Row, for the reason the position card's controls are one: a
            // held call puts its status, its overdue and its price-scale pills on the left of this
            // line, and on a cover screen three of those beside the source button is a line that
            // has to wrap rather than one that may clip.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                // Only where the call has a session to belong to: an occurrence the model left
                // undated cannot be scored, so a trade filed against it would have no deadline to
                // run to. A spacer holds the source button on the right where there is no trade
                // control, rather than letting it slide into the ticker's column.
                if (trades != null && trades.dateOf(point) != null) {
                    TradeAction(
                        held = held,
                        suggestedEntry = point.entryMidpoint(),
                        defaultWindow = trades.windowFor(point),
                        tPlusOne = point.isTPlusOne,
                        onBuy = { price, date, window ->
                            trades.buy(stock, point, channel, price, date, window)
                        },
                        onSell = { sale -> held?.let { trades.sell(it, sale) } },
                        // Recording a purchase belongs on the card the call was read off; closing a
                        // position does not. A sale is the end of a trade, and it is made where the
                        // trade lives - Portfolio, or the occurrence sheet - not off a card being
                        // scanned for what to buy next. The held and overdue chips still show here.
                        canSell = false,
                    )
                } else {
                    Spacer(Modifier.width(0.dp))
                }
                // The same press the whole card already answers, said in a place the reader can
                // aim at - and the arrow is what makes the card's own press discoverable at all.
                DisclosureButton("Source", expanded) { expanded = !expanded }
            }

            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
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
    /** The session this occurrence was made for, which the copied text names. */
    session: java.time.LocalDate?,
) {
    // Top-aligned so the right-hand column starts level with the ticker rather than floating
    // against the middle of however many name lines this stock happens to have.
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            // The logo rides the ticker's own line rather than sitting beside the whole column.
            // Beside the column it left a hole under itself the height of the names below the
            // ticker, and it pushed those names in past the ladder and the levels, which start at
            // the card's edge - one card, two left edges. Here the names stay flush with them.
            // The logo and the ticker press together as one target rather than the text alone:
            // a 12sp glyph beside a headline is two touch targets where the reader sees one thing.
            // See LocalOpenStock.
            val openStock = LocalOpenStock.current
            Row(
                Modifier.clickable { openStock(stock.stockCode) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StockLogo(stock.stockCode, LogoSize.Row, Modifier.padding(end = Space.s))
                Text(
                    stock.stockCode,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Egx33Badge(stock.stockCode, Modifier.padding(start = Space.s))
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
                        MaterialTheme.typography.titleLarge.lineHeight.toDp()
                    }
                    Box(
                        Modifier.height(tickerLine),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Under the timing chip rather than below the card: the header already stands
                        // two lines tall to keep names level, so this costs no height, and the chip is
                        // exactly what differs between one occurrence and the next.
                        PageDots(page, pageCount)
                    }
                    Spacer(Modifier.height(Space.xs))
                }
                TimingChip(point)
            }
            // The ⋮ the position card has carried since it was built, arriving on the other card
            // that holds a call. One item, because there is one thing to do with a call that the
            // card cannot already do: get its numbers out of the app intact. A report exports as a
            // spreadsheet, which is the right shape for a record and the wrong one for the four
            // figures somebody is about to retype into an order ticket.
            CopyCallMenu(stock, point, channel, session)
        }
    }
}

/**
 * Copies this call as plain text. See [CallText] for what it says and why.
 *
 * `ClipboardManager` through the composition local rather than the system service: it is what
 * Compose offers, and it is what puts the copy on the same clipboard the text fields in this app
 * paste from. Android 13 and later show their own confirmation of a copy, so the status line is
 * deliberately left alone - two announcements of one press is one too many, and the system's own
 * cannot be turned off.
 */
@Composable
private fun CopyCallMenu(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    channel: String?,
    session: java.time.LocalDate?,
) {
    var open by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(IconSize.Action)) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "More actions",
                modifier = Modifier.size(IconSize.Inline),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Copy call") },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = {
                    open = false
                    clipboard.setText(
                        AnnotatedString(CallText.of(stock, point, channel, session)),
                    )
                },
            )
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
 * Entry beside its stop, then the two targets, as labelled figures, each with its percentage where
 * the source gave one, followed by the two market levels the call was drawn against.
 *
 * Six fixed slots in two columns, not a flow. A tile is as wide as its number happens to print, so
 * flowing them put the same figure in a different place on every card - support trailing the stop
 * on one, alone on a line of its own on the next - and a column of cards read as six loose numbers
 * rather than one shape repeated. Fixed slots mean a card can be read down as well as across, and
 * the market levels always land last. Each row pairs the levels that are read against one another
 * - the entry with the stop it risks, then the two targets it is aiming at - which is how the
 * portfolio card already sets a held position out. The table, the occurrence sheet and the export
 * still print left to right in their own order; a card is looked at one at a time, and this is the
 * pairing that reads there.
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
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        LevelPair(
            { Level("Entry", entryText(point), PriceRole.entry, it) },
            { Level("Stop loss", figure(point.stopLoss, point.riskPct), PriceRole.stop, it) },
        )
        LevelPair(
            { Level("Target 1", figure(point.target1, returnPct(point, point.target1, point.returnTp1Pct)), PriceRole.target, it) },
            { Level("Target 2", figure(point.target2, returnPct(point, point.target2, point.returnTp2Pct)), PriceRole.target, it) },
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
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = LevelFigure,
                lineHeight = LevelFigureLine,
            ),
            fontWeight = FontWeight.SemiBold,
            color = tone,
        )
    }
}

/**
 * A step under `titleMedium`, on a line tighter than the scale gives it.
 *
 * Six of these are the tallest thing on the card, and the card is one of a dozen being scanned.
 * The scale's line heights are deliberately generous because Arabic carries marks above and below
 * the line - see `AppTypography` - and a price never does, so this is the one place where taking
 * the line in clips nothing. Set as a copy of the role rather than as a role of its own: the face
 * and the weight are still the scale's, and only the size is this card's business.
 */
private val LevelFigure = 15.sp
private val LevelFigureLine = 18.sp

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
