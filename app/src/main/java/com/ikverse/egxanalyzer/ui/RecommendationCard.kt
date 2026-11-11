package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.timing
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.MoreVert
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.KitIconButton
import com.ikverse.egxanalyzer.ui.kit.KitMenu
import com.ikverse.egxanalyzer.ui.kit.MenuItem
import com.ikverse.egxanalyzer.ui.kit.Panel
import com.ikverse.egxanalyzer.ui.theme.palette

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.CallGroup
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.ui.kit.HoldMenu
import com.ikverse.egxanalyzer.ui.kit.KitTag

/**
 * Every occurrence of one stock, one card each, swiped through sideways.
 *
 * A stock is often called twice in a report - by two channels, or once as a watch and once for a
 * named date - and those calls have different entries and different targets. Folding them into a
 * single card showed the first and silently dropped the rest, which is the one thing a table row
 * never did. So two channels are still two cards here, each with its own channel tag, rather than
 * one card wearing "2 channels".
 *
 * Since 5.1 a card answers the one question a page of them is scanned for - where does this call
 * stand - and everything else is a press away: a tap opens the call in full, a hold opens what can
 * be done about it ([holdItems]).
 */
@Composable
internal fun RecommendationCards(
    stock: ConsolidatedRecommendation,
    /** The channel behind an occurrence, looked up by the message the model cited. */
    channelFor: (String?) -> String?,
    /** Records what the user did about a call. Absent, the cards are read-only. */
    trades: TradeBook? = null,
    /** Corrects what the model read off the card. Absent, the figures cannot be changed. */
    editor: CallEditor? = null,
    /** The status word on a call's tag, and the group that colours it. */
    statusFor: (RecommendationDataPoint) -> Pair<String, CallGroup> = { "Running" to CallGroup.RUNNING },
    /** Where the stock is now, for the price beside the name and the ladder's arrow. */
    latest: LatestPrice? = null,
    /** A tap: the call in full. */
    onOpen: (RecommendationDataPoint) -> Unit = {},
    /** A hold: what can be done about the call. */
    holdItems: (RecommendationDataPoint) -> List<MenuItem> = { emptyList() },
    modifier: Modifier = Modifier,
) {
    val points = stock.dataPoints
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().callBlock(null)) {
            Column(Modifier.padding(Space.l)) { StockHeader(stock, latest, point = null) }
        }
        return
    }

    val pager = rememberPagerState(pageCount = { points.size })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        HorizontalPager(
            state = pager,
            // No peek, deliberately: insetting only the stocks with several occurrences made their
            // cards narrower than the rest, and a row of cards that do not match reads as a fault
            // before it reads as a hint. The dots under the card carry the hint instead.
            pageSpacing = Space.s,
            verticalAlignment = Alignment.Top,
        ) { page ->
            val point = points[page]
            RecommendationCard(
                stock = stock,
                point = point,
                channel = channelFor(point.sourceMessageId),
                status = statusFor(point),
                latest = latest,
                trades = trades,
                editor = editor,
                page = page,
                pageCount = points.size,
                onOpen = { onOpen(point) },
                holdItems = holdItems(point),
            )
        }
    }
}

/**
 * One occurrence: the stock, where it is now, what the call is, and where it stands on its ladder.
 *
 * The levels grid, risk to reward, the source and the trade controls all moved to the sheet a tap
 * opens. The held ring stays, so a page of calls can still be read for what is held before any card
 * is opened.
 */
@Composable
private fun RecommendationCard(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    channel: String?,
    status: Pair<String, CallGroup>,
    latest: LatestPrice?,
    trades: TradeBook?,
    editor: CallEditor?,
    page: Int,
    pageCount: Int,
    onOpen: () -> Unit,
    holdItems: List<MenuItem>,
    modifier: Modifier = Modifier,
) {
    var editing by remember(point) { mutableStateOf(false) }
    val held = trades?.heldFor(stock, point)
    val close = latest?.session?.close

    HoldMenu(items = holdItems, modifier = modifier.fillMaxWidth(), onTap = onOpen) {
        Box(Modifier.fillMaxWidth().callBlock(heldBorder(held))) {
            Column(Modifier.padding(Space.m)) {
                StockHeader(stock, latest, point)
                Spacer(Modifier.height(Space.s))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    KitTag(status.first, tone = status.second.tone())
                    channel?.takeIf(String::isNotBlank)?.let { KitTag(it, tone = palette.muted) }
                    if (point.isTPlusOne) KitTag("T+1", tone = palette.muted)
                    if (editor?.editFor(stock, point) != null) EditedChip { editing = true }
                }
                Spacer(Modifier.height(Space.m))
                if (point.isWatching) {
                    Text(
                        watchingSentence(point),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted,
                    )
                } else {
                    PriceLadder(point, reached = close)
                }
                if (pageCount > 1) {
                    Spacer(Modifier.height(Space.s))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PageDots(page, pageCount)
                    }
                }
            }
        }
    }
    if (editing && editor != null) {
        EditCallSheet(stock, point, editor, onDismiss = { editing = false })
    }
}

/**
 * What a watched stock is waiting for, said in place of a ladder it does not have.
 *
 * The level is the top of the entry band: a watch is a stock worth buying once it clears it.
 */
internal fun watchingSentence(point: RecommendationDataPoint): String {
    val level = watchLevel(point)
    return if (level != null) {
        "Worth buying only if it closes above ${formatPrice(level)}. Not a call to take yet."
    } else {
        "On the watch list. Not a call to take yet."
    }
}

/** The price a watched stock has to clear: the top of its entry band. */
internal fun watchLevel(point: RecommendationDataPoint): Double? =
    point.buyPriceHigh ?: point.buyPrice ?: point.buyPriceLow

/** The colour a call's status tag is drawn in. */
@Composable
internal fun CallGroup.tone(): androidx.compose.ui.graphics.Color = when (this) {
    CallGroup.RUNNING -> palette.heroInk
    CallGroup.REACHED -> palette.gain
    CallGroup.STOPPED -> palette.loss
    CallGroup.WATCHING, CallGroup.ENDED -> palette.muted
}

/** The move from the entry's midpoint to [close], as a percentage. Null where either is missing. */
internal fun moveVsEntry(point: RecommendationDataPoint, close: Double?): Double? {
    val entry = point.entryMidpoint() ?: return null
    if (close == null || entry == 0.0) return null
    return (close - entry) / entry * 100
}

@Composable
private fun StockHeader(
    stock: ConsolidatedRecommendation,
    latest: LatestPrice?,
    point: RecommendationDataPoint?,
) {
    // Ticker style and name style match the identity block Insights draws on its own call card, so
    // one stock looks the same on two screens. The price takes the corner the pills used to: the
    // tags row under the header says what they said.
    Row(verticalAlignment = Alignment.Top) {
        val openStock = LocalOpenStock.current
        Row(
            Modifier.weight(1f).clickable { openStock(stock.stockCode) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StockLogo(stock.stockCode, LogoSize.Row, Modifier.padding(end = Space.s))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stock.stockCode, style = MaterialTheme.typography.titleLarge)
                    Egx33Badge(stock.stockCode, Modifier.padding(start = Space.s))
                }
                stock.stockNameArabic?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val close = latest?.session?.close
        if (close != null) {
            Column(Modifier.padding(start = Space.s), horizontalAlignment = Alignment.End) {
                Text(
                    formatPrice(close),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                )
                // Against the entry, and said so: the app keeps no day's move behind AppState.
                point?.let { moveVsEntry(it, close) }?.let { move ->
                    Text(
                        "${formatPercent(move)} vs entry",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                        color = if (move >= 0) palette.gain else palette.loss,
                    )
                }
            }
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
internal fun CallMenu(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    channel: String?,
    session: java.time.LocalDate?,
    editor: CallEditor?,
    onEdit: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Box {
        KitIconButton(Icons.Outlined.MoreVert, label = "Call options", onClick = { open = true }, size = 40.dp)
        KitMenu(
            expanded = open,
            onDismiss = { open = false },
            items = buildList {
                add(
                    MenuItem("Copy call") {
                        clipboard.setText(AnnotatedString(CallText.of(stock, point, channel, session)))
                    },
                )
                // The second thing worth doing to a call the card cannot already do: correcting
                // what the model read off the screenshot.
                if (editor != null) {
                    add(MenuItem("Edit call") { onEdit() })
                    if (editor.editFor(stock, point) != null) {
                        add(MenuItem("Undo this call's edits") { editor.undo(stock, point) })
                    }
                }
            },
        )
    }
}

/**
 * What dated the call, in place of the buy/sell signal.
 *
 * Every occurrence in a report is a buy, so the old chip said the same word on every card; which
 * session a call is for is the thing that actually differs between two rows of the same stock.
 */
@Composable
internal fun TimingChip(point: RecommendationDataPoint) {
    // Falls back to the signal only when the model recorded no basis at all, so the chip is
    // never blank.
    val label = timing(point) ?: point.recommendationType?.uppercase() ?: "-"
    // `KitTag`, which is what every other card in this app annotates itself with. As an
    // AssistChip this stood 32dp tall in 14sp of filled surfaceVariant beside a 24dp button, so the
    // note about where a date came from was the largest object in the corner of the card and the
    // heaviest thing on a header whose figures are the point. A small tag says the same word.
    //
    // Neutral, like every other note pill on this card. It was `primary` - the app's own voice,
    // on the reasoning that a T+1 changes what the reader has to do - and that made one pill in
    // the app a different colour from its neighbours for a reason none of them showed. The
    // wording is what says this call names its own deadline; the hue was saying it twice, in a
    // language the card spends on prices everywhere else. Asked for on 2026-09-11.
    //
    // Tappable since 2026-09-24, like every other pill on this card that used to draw a fact and
    // nothing else: a reader who has never seen "Watching" or "T+1" on a card before had no way to
    // ask what either meant.
    var showing by remember(point) { mutableStateOf(false) }
    KitTag(label, tone = palette.muted, onClick = { showing = true })
    if (showing) {
        // `InfoNote`'s own constructor rather than the checked `infoNote` factory: that helper's
        // callers are checked by InfoNoteTest for static prose, and this label and explanation are
        // both read off the point rather than typed here.
        InfoSheet(InfoNote(label, listOf(timingExplanation(point)))) { showing = false }
    }
}

/** What decided the date this call is judged from, in the reader's own words. */
internal fun timingExplanation(point: RecommendationDataPoint): String = when {
    point.isWatching -> "The source named this stock with no date to act on - a level worth " +
        "watching rather than a call to take now."
    point.isTPlusOne -> "The source printed this as a T+1 call: buy on the session it was made " +
        "for, and be out on the next one, rather than the thirty sessions an ordinary call runs."
    point.effectiveDateBasis == "explicit_date" ->
        "The source named a specific date for this call, rather than leaving it to the day it " +
            "was posted."
    else -> "How this call's date was worked out, off what the source actually printed."
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
 * portfolio card already sets a held position out. The table and the export still print left to
 * right in their own order, because both are read down a column; **the occurrence sheet draws this
 * grid**, and did not until 2026-09-11 - it flowed six figures at whatever width they happened to
 * print, which left Resistance stranded on a line of its own. A sheet is the one surface where a
 * single call is the whole subject, so if this pairing reads on a card being scanned it reads
 * there.
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
internal fun LevelGrid(point: RecommendationDataPoint) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        LevelPair(
            // Words where the source gave no entry at all: a lone dash cannot tell "not given" from
            // "not loaded" (2026-10-05). The table keeps its dash, where there is no room for words.
            { Level("Entry", entryText(point).takeUnless { it == Dash } ?: "Not given", PriceRole.entry, it) },
            { Level("Stop loss", levelText(point.stopLoss, point.riskPct), PriceRole.stop, it) },
        )
        LevelPair(
            { Level("Target 1", levelText(point.target1, targetReturn(point, point.target1, point.returnTp1Pct)), PriceRole.target, it) },
            { Level("Target 2", levelText(point.target2, targetReturn(point, point.target2, point.returnTp2Pct)), PriceRole.target, it) },
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
    // IntrinsicSize.Min on the row plus fillMaxHeight on each side is what makes the shorter tile's
    // bar stretch to match the taller one - without it, a value that wraps to two lines (more
    // likely at the folded, narrower width) leaves its neighbour's bar shorter than it should be.
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        left(Modifier.weight(1f).fillMaxHeight())
        right(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun Level(
    label: String,
    value: String,
    tone: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    // A key rather than a plain label: six of these are read down the card in a fixed order, and
    // the colour is what lets that order be skipped - the reader's eye can go straight to the
    // green targets or the red stop. The height comes entirely from the caller now: LevelPair
    // shares one height across both its tiles via IntrinsicSize.Min, and the caller drawing this
    // alone (the risk/reward row) supplies its own IntrinsicSize.Min instead.
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(LevelKeyWidth)
                .background(tone, RoundedCornerShape(percent = 50)),
        )
        Column {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = TabularFigures,
                    fontSize = LevelFigure,
                    lineHeight = LevelFigureLine,
                ),
                fontWeight = FontWeight.SemiBold,
                color = tone,
            )
        }
    }
}

private val LevelKeyWidth = 3.dp

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

/**
 * The buy band as one string, or the single price where the source printed one.
 *
 * Internal because the occurrence sheet kept its own copy of this until 2026-09-11, and two
 * functions printing one call's entry are two that agree until somebody changes the dash.
 */
internal fun entryText(point: RecommendationDataPoint): String {
    val low = point.buyPriceLow
    val high = point.buyPriceHigh
    return when {
        low != null && high != null && low != high -> "${formatPrice(low)} – ${formatPrice(high)}"
        else -> formatPrice(point.buyPrice ?: low ?: high)
    }
}

/** A price with its percentage, or the dash where the source gave no such level at all. */
internal fun levelText(value: Double?, percent: Double?): String =
    if (value == null || percent == null) {
        formatPrice(value)
    } else {
        "${formatPrice(value)}  (${formatPercent(percent)})"
    }

/**
 * What a source printed against a target, or what the entry implies where it printed nothing.
 *
 * The occurrence sheet read `returnTp1Pct` and `returnTp2Pct` straight until 2026-09-11, so the
 * card showed a percentage beside a target and the sheet showed the bare price - two readings of
 * one call, differing only on whether the channel had happened to print the figure.
 */
internal fun targetReturn(point: RecommendationDataPoint, target: Double?, stated: Double?): Double? =
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

/**
 * A call's block on the page: a white card since 5.1, when the report stopped being drawn inside a
 * tile of its own, with the tile's corners and the held ring where there is one.
 */
@Composable
private fun Modifier.callBlock(edge: androidx.compose.foundation.BorderStroke?): Modifier =
    this
        .clip(Kit.TileShape)
        .background(palette.card)
        .then(if (edge != null) Modifier.border(edge, Kit.TileShape) else Modifier)

/** The one rule a call card draws between what was printed and what can be pressed. */
@Composable
internal fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
}
