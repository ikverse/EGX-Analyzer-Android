package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.CallGroup
import com.ikverse.egxanalyzer.model.CallStep
import com.ikverse.egxanalyzer.model.CallStepKind
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.timeline
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.KitTag
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.MoreRow
import com.ikverse.egxanalyzer.ui.kit.StepTone
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.Timeline
import com.ikverse.egxanalyzer.ui.kit.TimelineStep
import com.ikverse.egxanalyzer.ui.kit.tileColors
import com.ikverse.egxanalyzer.ui.kit.tileSignals
import com.ikverse.egxanalyzer.ui.theme.palette
import java.time.LocalDate
import kotlin.math.abs

/**
 * Everything about one extracted occurrence, opened by pressing a call in a report.
 *
 * **Rebuilt on 2026-11-11 to read in the order the trade's own sheet reads in** - who it is, where
 * it stands, the levels, what happened, what it was read off - out of the same pieces
 * (`SheetPieces.kt`). What the old sheet got wrong, each of which was reported off a phone:
 *
 * - **Both names were cut off.** A PEAK block and its percentage shared the header with them, so
 *   "El- Nile Co. for ..." was all there was room for. The names have the full width now, and how
 *   far the stock has been is the sheet's leading figure instead.
 * - **It never said the call's verdict or how long it had left.** The row that opened it carried
 *   "Running" or "Target 1 reached"; the sheet showed only where the date came from. [status] and
 *   [scored] bring both, and the timeline Insights already draws for the same call.
 * - **It showed the peak and called it nothing.** [latest] is the price now, which is the first
 *   thing a reader deciding whether to buy asks, and the peak is said beside it in words.
 * - **The same four prices were printed twice**, on the ladder and again in a six-cell grid with a
 *   coloured bar down each cell and a row of dashes where the call gave no support or resistance.
 *   The ladder's columns carry the percentages the grid was for.
 * - **A watching call's condition was a quote in the footer.** "Buy only on a break of the price"
 *   is the whole of such a call, and it leads the sheet now, with what the app can and cannot say
 *   about it underneath.
 *
 * It scrolls as one column, which is what lets a call with every level, a long Arabic note and a
 * large font scale still reach its trade button.
 */
@Composable
internal fun OccurrenceSheet(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    imagePath: String? = null,
    /** Highest the stock has traded since the call. */
    peak: Double? = null,
    /** The channel behind this occurrence, recorded with a trade taken on it. */
    channel: String? = null,
    /** Records what the user did about this call. Absent, the sheet is read-only. */
    trades: TradeBook? = null,
    /** Corrects what the model read off the card. Absent, the figures cannot be changed. */
    editor: CallEditor? = null,
    /** The newest price the app holds for the stock, which is where the call stands now. */
    latest: LatestPrice? = null,
    /** The word on the call's tag in the report, and the group that colours it. */
    status: Pair<String, CallGroup>? = null,
    /** How the scorer has judged the call so far. Absent on a call it has not been handed. */
    scored: ScoredCall? = null,
    onDismiss: () -> Unit,
) {
    var viewingImage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val held = trades?.heldFor(stock, point)
    val session = trades?.dateOf(point) ?: editor?.sessionOf(point)
    // The kit's sheet: a bottom sheet folded, a panel beside the page unfolded or in book posture.
    KitSheet(onDismiss = onDismiss, posture = LocalFoldPosture.current, title = stock.stockCode) {
        OccurrenceContent(
            stock = stock,
            point = point,
            imagePath = imagePath,
            peak = peak,
            channel = channel,
            session = session,
            latest = latest,
            status = status,
            scored = scored,
            editor = editor,
            onEdit = { editing = true },
            onOpenImage = { viewingImage = true },
        )
        // Only where the call has a session to belong to, since an occurrence the model left
        // undated cannot be scored, so a trade filed against it would have no deadline to run to.
        if (trades != null && session != null) {
            OccurrenceActions(stock, point, channel, trades, held)
        }
    }
    if (editing && editor != null) {
        EditCallSheet(stock, point, editor, onDismiss = { editing = false })
    }
    if (viewingImage) {
        SourceImageViewer(imagePath, point.sourceImageRef, onDismiss = { viewingImage = false })
    }
}

/** The sheet's blocks, apart from the sheet itself so they can be drawn and tested without one. */
@Composable
internal fun ColumnScope.OccurrenceContent(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    imagePath: String?,
    peak: Double?,
    channel: String?,
    session: LocalDate?,
    latest: LatestPrice?,
    status: Pair<String, CallGroup>?,
    scored: ScoredCall?,
    editor: CallEditor?,
    onEdit: () -> Unit,
    onOpenImage: () -> Unit,
) {
    val openStock = LocalOpenStock.current
    val entryLow = point.buyPriceLow ?: point.buyPrice
    val entryHigh = point.buyPriceHigh ?: point.buyPrice
    val entry = point.entryMidpoint()
    val close = latest?.session?.close
    // One precision for every price on the sheet: 320, 335 and 325.01 are one stock's money.
    val decimals = priceDecimals(point.stopLoss, entryLow, entryHigh, point.target1, point.target2, close, peak)

    SheetIdentity(
        ticker = stock.stockCode,
        arabic = stock.stockNameArabic,
        english = stock.stockNameEnglish,
        onOpenStock = { openStock(stock.stockCode) },
    ) {
        CallMenu(stock, point, channel, session, editor, onEdit)
    }
    // Every pill on one row under the header: the verdict, whether it was corrected, where its
    // date came from, who made it and for which session.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        // A watching call's verdict is the word its timing pill already says.
        status?.takeIf { it.second != CallGroup.WATCHING }?.let { (word, group) -> KitTag(word, tone = group.tone()) }
        if (editor?.editFor(stock, point) != null) EditedChip(onEdit)
        TimingChip(point)
        // "from", so the channel reads as where the call came from rather than as a second status.
        channel?.takeIf(String::isNotBlank)?.let { KitTag("from ⁨$it⁩", tone = palette.muted) }
        session?.let { KitTag(AppDates.DayMonth.format(it), tone = palette.muted) }
    }

    // Where it stands: the price now where the app holds one, the peak where it holds only that.
    val standing = close ?: peak
    if (standing != null) {
        val signals = tileSignals(TileTone.HERO)
        val move = fromEntry(standing, entry)
        StandsTile(
            tone = TileTone.HERO,
            figure = formatPrice(standing, decimals),
            figureColor = tileColors(TileTone.HERO).second,
            beside = when {
                close == null -> "highest since the call"
                latest.provisional -> "so far today"
                else -> "now · " + shortDate(latest.session.date)
            },
        ) {
            if (move != null) {
                Text(
                    buildAnnotatedString {
                        val size = formatPercent(abs(move), signed = false)
                        when {
                            move > 0 -> { figure("+$size", signals.gain); append(" above the entry") }
                            move < 0 -> { figure("-$size", signals.loss); append(" below the entry") }
                            else -> append("At the entry")
                        }
                        // A range has no one price to name; its ends are on the ladder below.
                        if (entryLow == entryHigh && entry != null) {
                            append(" of ")
                            figure(formatPrice(entry, decimals))
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (close != null && peak != null) {
                Text(
                    buildAnnotatedString {
                        if (formatPrice(peak, decimals) == formatPrice(close, decimals) || peak < close) {
                            append("The highest it has been since the call.")
                        } else {
                            append("Highest since the call ")
                            figure(formatPrice(peak, decimals))
                            fromEntry(peak, entry)?.let { append(", ${formatPercent(it)} from the entry") }
                            append(".")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = QuietOnTile),
                )
            }
        }
    }

    // A watching call is its condition: what has to happen before it is a call to buy at all.
    val condition = point.recommendationEvidence?.takeIf { point.isWatching && it.isNotBlank() }
    if (condition != null) {
        Tile(Modifier.fillMaxWidth(), tone = TileTone.TILE1) {
            Text(
                "THE CALL'S OWN CONDITION",
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = QuietOnTile),
            )
            Spacer(Modifier.height(Space.s))
            SourceText(condition, MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Space.s))
            Text(conditionWords(close, entryLow, entryHigh), style = MaterialTheme.typography.bodySmall)
        }
    }

    Tile(Modifier.fillMaxWidth()) {
        SheetSectionLabel("The levels")
        Spacer(Modifier.height(Space.s))
        LevelsBlock(
            stopLoss = point.stopLoss,
            entryLow = entryLow,
            entryHigh = entryHigh,
            target1 = point.target1,
            target2 = point.target2,
            now = standing,
            standing = if (close != null) "now" else "at its highest since the call",
        )
        point.riskRewardRatio()?.let { ratio ->
            Spacer(Modifier.height(Space.m))
            Hairline()
            Spacer(Modifier.height(Space.m))
            // What the call risks against what it seeks, as a sentence and as a length: 335 against
            // a stop at 310 means one thing at 1 to 3 and another at 1 to 0.4.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildAnnotatedString {
                        append("Risks ")
                        figure("1")
                        append(" to make ")
                        figure("%.1f".format(ratio))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                RiskRewardBar(ratio, Modifier.weight(1f))
            }
        }
        // Only where the call gave one. A support is a price the stock has held at, not a
        // distance from the entry, so neither carries a percentage.
        if (point.support != null || point.resistance != null) {
            Spacer(Modifier.height(Space.s))
            Text(
                buildAnnotatedString {
                    point.support?.let { append("Support "); figure(formatPrice(it, decimals)) }
                    if (point.support != null && point.resistance != null) append(" · ")
                    point.resistance?.let { append("Resistance "); figure(formatPrice(it, decimals)) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
    }

    // How long it has left, on a call still running.
    scored?.takeIf { it.outcome == Outcome.OPEN }?.let { call ->
        val left = (call.windowSessions - call.sessionsElapsed).coerceAtLeast(0)
        Tile(Modifier.fillMaxWidth()) {
            Text(
                "$left of ${call.windowSessions} trading days left",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(Space.s))
            DaySteps(call.sessionsElapsed, call.windowSessions)
        }
    }

    scored?.let { call ->
        Tile(Modifier.fillMaxWidth()) {
            SheetSectionLabel("What happened")
            Spacer(Modifier.height(Space.s))
            Timeline(call.timeline().map { it.asSheetStep() })
        }
    }

    OccurrenceSource(stock, point, imagePath, quote = condition == null, onOpenImage = onOpenImage)

    // The ids are for checking the app against a report, not for reading a call, so they wait
    // behind a closed row. Transient, like any other disclosure a fold may shut.
    val detailsOpen = remember(point) { mutableStateOf(false) }
    MoreRow("Source details", detailsOpen) {
        Text(
            listOfNotNull(
                point.sourceImageRef?.let { "image $it" },
                point.sourceMessageId?.let { "message $it" },
            ).joinToString(" · ").ifBlank { "source not recorded" },
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
            color = palette.muted,
        )
    }
}

/**
 * What the app can say about a watching call's condition, which is where the last close stands
 * against the entry, and what it cannot: a card that asks for two hours above a price is asking
 * for something no daily close can show.
 */
internal fun conditionWords(close: Double?, entryLow: Double?, entryHigh: Double?): String {
    val low = entryLow ?: entryHigh
    val high = entryHigh ?: entryLow
    val where = when {
        close == null || low == null || high == null -> null
        close > high -> "The last close is above the entry"
        close < low -> "The last close is below the entry"
        low != high -> "The last close is inside the entry range"
        else -> "The last close is at the entry"
    }
    return if (where == null) {
        "Read off the card. The app cannot check it for you."
    } else {
        "Read off the card. $where; the app cannot check anything else it asks for."
    }
}

/** A step of the model's timeline as the kit draws it. */
private fun CallStep.asSheetStep(): TimelineStep = TimelineStep(
    text = text,
    detail = detail,
    date = date?.format(AppDates.DayMonth),
    tone = when (kind) {
        CallStepKind.DONE -> StepTone.DONE
        CallStepKind.ACTIVE -> StepTone.ACTIVE
        CallStepKind.WAITING -> StepTone.WAITING
        CallStepKind.GOOD -> StepTone.GOOD
        CallStepKind.BAD -> StepTone.BAD
    },
)

/**
 * The card the numbers were read off, and what the model says it read there.
 *
 * The screenshot is the evidence every figure above it rests on, so it leads, with when the card
 * was printed, the session it was for and how it was marked beside it - three labelled facts where
 * there was one middot-joined line that wrapped and ended on its own separator. The quote is drawn
 * here only where the sheet has not already led with it as the call's condition.
 */
@Composable
private fun OccurrenceSource(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    imagePath: String?,
    quote: Boolean,
    onOpenImage: () -> Unit,
) {
    Tile(Modifier.fillMaxWidth()) {
        SheetSectionLabel("What it was read off")
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.Top) {
            // The thumbnail draws `#13` where the photo has gone from Telegram's cache; absent
            // altogether, Source details still names the image the model cited.
            if (imagePath != null) {
                SourceImageThumbnail(
                    path = imagePath,
                    reference = point.sourceImageRef,
                    size = SourceShotSize,
                    onOpen = onOpenImage,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                point.visibleSourceDate?.takeIf(String::isNotBlank)?.let { SourceFact("Printed", printedWords(it)) }
                // "Target 11 Nov" until 2026-11-11, which on a sheet of price targets read as one.
                point.date?.let { SourceFact("For the session of", AppDates.DayMonth.format(it)) }
                point.timingEvidence?.takeIf(String::isNotBlank)?.let { SourceFact("Marked", "⁨$it⁩") }
            }
        }
        if (quote) {
            point.recommendationEvidence?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(Space.s))
                SourceText("“$it”", MaterialTheme.typography.bodyMedium)
            }
        }
        (point.notesArabic ?: stock.notesSummary)?.takeIf(String::isNotBlank)?.let {
            Spacer(Modifier.height(Space.s))
            SourceText(it, MaterialTheme.typography.bodyMedium)
        }
    }
}

/** One fact about the source card: what it is, muted, then what it says. */
@Composable
private fun SourceFact(label: String, value: String) {
    val muted = palette.muted
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = muted)) { append("$label ") }
            append(value)
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * The one thing a reader does about a call, on the edge their thumb is already on.
 *
 * [TradeAction] is the app's only way of recording a purchase and this is one more surface asking
 * it, not a second way of asking. One more item in the sheet's own scrollable column, so it scrolls
 * with the rest of a long call - the trade a reader who wanted it pinned there made. Asked for on
 * 2026-09-20.
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
