package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import java.time.LocalDate

/**
 * Everything about one extracted occurrence.
 *
 * The table shows six columns and the card below 600dp shows the figures; this is where the rest of
 * one call lives - the levels the row could not hold, and the evidence every number on it is
 * supposed to be traceable to.
 *
 * **A header, a scroller and an action bar**, since 2026-09-11. It was one column of dividers, which
 * is the shape [StockSheet] left behind when it was built and the shape every other surface in this
 * app has moved off. Three things came of that rebuild and the first is a bug rather than a look:
 *
 * - **It had no scroller at all.** No `verticalScroll` anywhere, alone among the sheets here - so a
 *   call carrying all six levels, a long Arabic note, or an ordinary one at a large font scale ran
 *   off the bottom of the screen with no way to reach it. It takes `sheetDragSlop().scrollableColumn()`
 *   now, the pair the other six sheets carry, which is also what stops a pull at the top of the
 *   record closing the sheet under the reader's finger.
 * - **The identity band no longer scrolls.** The logo, the ticker, the EGX 33 mark and both names
 *   are what the sheet was opened to look at, and the ticker opens [StockSheet] through
 *   [LocalOpenStock] the way every other full-width card naming a stock already does. The channel
 *   is drawn at all for the first time - it has been a parameter of this function since it was
 *   written and reached the screen nowhere, so two calls on one stock were told apart by nothing.
 * - **The figures are [LevelGrid] rather than a `FlowRow`.** Six tiles flowed at whatever width
 *   their numbers happened to print, which is why Resistance sat alone on a line under five
 *   figures; the grid pairs the entry with its stop and the two targets with each other, and lands
 *   the market levels last on every call.
 *
 * `Edit` was a text button in the top-right corner, the loudest thing on the sheet, and is now the
 * `⋮` the recommendation card already carries - which brings **Copy call** to a surface that had no
 * way of getting a call's numbers out of the app. The `Edited` pill still opens the editor directly.
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
    /** Corrects what the model read off the card. Absent, the figures cannot be changed. */
    editor: CallEditor? = null,
    onDismiss: () -> Unit,
) {
    var viewingImage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val held = trades?.heldFor(stock, point)
    val session = trades?.dateOf(point) ?: editor?.sessionOf(point)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        OccurrenceHeading(stock, point, channel, peak, editor, session) { editing = true }
        Column(
            Modifier
                .fillMaxWidth()
                // Bounded by what the header and the action bar leave, and no taller than its own
                // content: a call with two levels and no screenshot must not stretch the sheet to
                // the full screen with the trade button stranded at the bottom of it.
                .weight(1f, fill = false)
                .sheetDragSlop()
                .scrollableColumn()
                .padding(horizontal = Space.l)
                .padding(top = Space.s, bottom = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            // On its own now, tinted the way Insights and the recommendation card tint a grouped
            // panel - it used to open "The call" section, which buried the one drawing on this
            // sheet inside a card whose label was really about the six figures under it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, SectionPanelShape)
                    .padding(Space.m),
            ) {
                PriceLadder(point, reached = peak)
            }
            SheetSection {
                SheetSectionLabel("The call")
                LevelGrid(point)
                point.riskRewardRatio()?.let { RiskRewardRow(it) }
            }
            OccurrenceSource(stock, point, imagePath) { viewingImage = true }
            // One more object in the same list rather than a bar pinned below it - only where the
            // call has a session to belong to, since an occurrence the model left undated cannot be
            // scored, so a trade filed against it would have no deadline to run to.
            if (trades != null && session != null) {
                OccurrenceActions(stock, point, channel, trades, held)
            }
        }
    }
    if (editing && editor != null) {
        EditCallSheet(stock, point, editor, onDismiss = { editing = false })
    }
    if (viewingImage) {
        SourceImageViewer(imagePath, point.sourceImageRef, onDismiss = { viewingImage = false })
    }
}

/**
 * Who the call is about, who made it, and how far the stock has been since - on one fixed band.
 *
 * The same shape [StockSheet] opens with and the recommendation card's header repeats: identity on
 * the left, the `⋮` on its right, every pill on one row beneath at the sheet's own inset. The
 * Arabic and English names each get their own line now, since 2026-09-20, and the logo centers
 * against the three lines it stands beside - the ticker, then each name - rather than sitting
 * top-aligned against the ticker alone. The channel and the date it was called for get one more
 * line of their own, between the identity block and the pill row.
 */
@Composable
private fun OccurrenceHeading(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    channel: String?,
    peak: Double?,
    editor: CallEditor?,
    session: LocalDate?,
    onEdit: () -> Unit,
) {
    val openStock = LocalOpenStock.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.l)
            .padding(bottom = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        // The logo centers against the whole three-line block - ticker, Arabic name, English
        // name - rather than sitting top-aligned against the ticker alone. Asked for on 2026-09-20.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            StockLogo(stock.stockCode, LogoSize.Header, Modifier.padding(end = Space.s))
            Column {
                // The logo and the ticker press together as one target, as they do on the card: a
                // 12sp glyph beside a headline is two touch targets where the reader sees one thing.
                Row(
                    Modifier.clickable { openStock(stock.stockCode) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stock.stockCode,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Egx33Badge(stock.stockCode, Modifier.padding(start = Space.s))
                }
                // Arabic and English each on their own line rather than joined by a middot - the
                // combined line was the widest thing under the ticker and the first to clip.
                stock.stockNameArabic?.let {
                    Text(
                        "⁨$it⁩",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                stock.stockNameEnglish?.takeIf { it != stock.stockCode }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (peak != null) PeakSinceTheCall(point, peak)
        CallMenu(stock, point, channel, session, editor, onEdit)
    }
    // The channel and the session it was called for, on one line under the identity block - what
    // used to be said only in the caption under the buy button, which repeated it a screen away
    // from the ticker it was about. Two occurrences of one stock in one report are identical apart
    // from who said it and when, and until 2026-09-11 this sheet named neither of them at all.
    if (channel?.isNotBlank() == true || session != null) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.l)
                .padding(bottom = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            channel?.takeIf(String::isNotBlank)?.let {
                Text(
                    "Source:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "⁨$it⁩",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            session?.let {
                if (channel?.isNotBlank() == true) {
                    Text(
                        "·",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    AppDates.DayMonth.format(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    // Every pill on one line under the header and starting at the sheet's own inset - level with
    // the ticker above it and with ENTRY below it. The same row the two call cards carry.
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.l)
            .padding(bottom = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        // Edited beside Timing rather than over it: what dated a call and whether anybody has
        // corrected it are two different facts about it, and stacked, the pill that is on every
        // call sat under the one that is on almost none.
        if (editor?.editFor(stock, point) != null) EditedChip(onEdit)
        TimingChip(point)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * How far the stock has been since the call was made, which is the one figure this sheet is handed
 * and never printed as a number.
 *
 * It is already the ladder's arrow, and that is a position on a scale rather than a price - the
 * same relationship [StockSheet] draws between its header's close and the line under it. Measured
 * against the entry midpoint, which is the basis the scorer measures every return from.
 */
@Composable
private fun PeakSinceTheCall(point: RecommendationDataPoint, peak: Double) {
    val entry = point.entryMidpoint()
    val over = entry?.takeIf { it > 0.0 }?.let { (peak - it) / it * 100 }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            "PEAK",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatPrice(peak),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
            color = PriceRole.market,
        )
        Text(
            if (over == null) "since the call" else "${formatPercent(over)} since the call",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
            color = if (over == null) PriceRole.muted else PriceRole.forReturn(over),
            textAlign = TextAlign.End,
        )
    }
}

/**
 * What the call risks against what it seeks, as a sentence and as a length.
 *
 * It was one muted line of text here, and the ratio is the context a target cannot be read
 * without - 2.55 against a stop at 2.15 means one thing at 1 : 3 and another at 1 : 0.4, and the
 * arithmetic is exactly what a reader does not do in their head while deciding. [RiskRewardBar]
 * draws the proportion; this is the one surface where a single call is the whole subject, so there
 * is room for both.
 */
@Composable
private fun RiskRewardRow(ratio: Double) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "RISK / REWARD",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "1 : ${"%.1f".format(ratio)}",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
            color = PriceRole.entry,
        )
        RiskRewardBar(ratio, Modifier.weight(1f))
    }
}

/**
 * The card the numbers were read off, and what the model says it read there.
 *
 * A section of its own rather than a footer under a rule. The screenshot is the evidence every
 * figure above it rests on, and it was drawn at 88dp under a run-on line whose loudest content was
 * a nineteen-digit Telegram message id. The picture comes up beside the quote, and the ids drop to
 * a monospace last line - they are for checking the app against a report, not for reading a call.
 */
@Composable
private fun OccurrenceSource(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    imagePath: String?,
    onOpenImage: () -> Unit,
) {
    SheetSection {
        SheetSectionLabel("What it was read off")
        // The quote first and right-aligned, the thumbnail last - the quote is Arabic, and reads
        // toward the photo beside it the same way the notes paragraph below reads toward its own
        // trailing edge. Asked for on 2026-09-20.
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            point.recommendationEvidence?.let {
                Text(
                    "“$it”",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            // The thumbnail draws `#13` where the photo has gone from Telegram's cache; absent
            // altogether, the trace line below still names the image the model cited.
            if (imagePath != null) {
                SourceImageThumbnail(
                    path = imagePath,
                    reference = point.sourceImageRef,
                    size = SourceShotSize,
                    onOpen = onOpenImage,
                )
            }
        }
        // Timing, when the card was printed and the session it targets - one middot-joined line,
        // the pattern the recommendation card's own source facts already read in, rather than
        // three stacked rows that ran together with nothing to tell one fact from the next.
        val facts = listOfNotNull(
            point.timingEvidence?.let { it to MaterialTheme.colorScheme.tertiary },
            point.visibleSourceDate?.takeIf(String::isNotBlank)
                ?.let { ("Printed $it") to MaterialTheme.colorScheme.onSurfaceVariant },
            point.date?.let {
                ("Target " + AppDates.DayMonth.format(it)) to MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (facts.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                facts.forEachIndexed { index, (text, color) ->
                    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
                    if (index != facts.lastIndex) {
                        Text(
                            "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        (point.notesArabic ?: stock.notesSummary)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // The ids are for checking the app against a report, not for reading a call - below a rule
        // now so they read as a footnote rather than running straight on from the notes above them.
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            listOfNotNull(
                point.sourceImageRef?.let { "image $it" },
                point.sourceMessageId?.let { "message $it" },
            ).joinToString(" · ").ifBlank { "source not recorded" },
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The one thing a reader does about a call, on the edge their thumb is already on.
 *
 * [TradeAction] is the app's only way of recording a purchase and this is one more surface asking
 * it, not a second way of asking. One more item in the sheet's own scrollable column now, alongside
 * "The call" and "What it was read off" rather than a bar pinned below them - which means it scrolls
 * with the rest of a long call instead of staying reachable, the trade a reader who wanted it
 * pinned there made. Asked for on 2026-09-20.
 *
 * Selling is offered here and not on the recommendation card, which is the split that already
 * exists: a card being scanned for what to buy next is not where a position is closed, and this
 * sheet is one call being decided about.
 */
@Composable
private fun OccurrenceActions(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    channel: String?,
    trades: TradeBook,
    held: PositionView?,
) {
    // No divider and no caption naming the channel and the session - the header says both of
    // those now, and a rule above a lone button drew a boundary nothing else on this list has.
    Row(Modifier.fillMaxWidth()) {
        TradeAction(
            held = held,
            suggestedEntry = point.entryMidpoint(),
            defaultWindow = trades.windowFor(point),
            tPlusOne = point.isTPlusOne,
            onBuy = { price, date, window ->
                trades.buy(stock, point, channel, price, date, window)
            },
            onSell = { sale -> held?.let { trades.sell(it, sale) } },
        )
    }
}

/**
 * How big the source screenshot is drawn.
 *
 * 104dp against the 88 it had. These are dense Arabic price cards photographed at whatever
 * resolution the channel posted, so the thumbnail is never readable at any size that fits a sheet -
 * what it has to do is show *which* card, which is a matter of the shape and the colour of it, and
 * 88dp beside nothing was smaller than the gap it sat in.
 */
private val SourceShotSize: Dp = 104.dp
