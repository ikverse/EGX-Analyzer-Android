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
 * One stock of an open report: its name and price once, then every call made on it, one under the
 * other, each with its own figures.
 *
 * A stock is often called twice in a report - by two channels, or once as a watch and once for a
 * named date - and those calls have different entries and different targets. They used to be cards
 * swiped through sideways, which hid all but the first behind a row of dots; listed, a stock two
 * channels named shows both calls at once and neither has to be found.
 *
 * Each call says its channel, where it stands, how far the price has moved from its entry, and the
 * four levels as numbers under a small ladder. A tap opens the call in full, a hold opens what can
 * be done about it ([holdItems]).
 */
@Composable
internal fun StockCallsCard(
    stock: ConsolidatedRecommendation,
    /** The channel behind an occurrence, looked up by the message the model cited. */
    channelFor: (String?) -> String?,
    /** Records what the user did about a call. Absent, the calls are read-only. */
    trades: TradeBook? = null,
    /** Corrects what the model read off the card. Absent, the figures cannot be changed. */
    editor: CallEditor? = null,
    /** The status word on a call's tag, and the group that colours it. */
    statusFor: (RecommendationDataPoint) -> Pair<String, CallGroup> = { "Running" to CallGroup.RUNNING },
    /** Where the stock is now, for the price beside the name and each ladder's arrow. */
    latest: LatestPrice? = null,
    /** A tap: the call in full. */
    onOpen: (RecommendationDataPoint) -> Unit = {},
    /** A hold: what can be done about the call. */
    holdItems: (RecommendationDataPoint) -> List<MenuItem> = { emptyList() },
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().callBlock(null).padding(Space.m)) {
        StockHeader(stock, latest, stock.dataPoints.size)
        stock.dataPoints.forEach { point ->
            Spacer(Modifier.height(Space.s))
            Hairline()
            CallLine(
                stock = stock,
                point = point,
                channel = channelFor(point.sourceMessageId),
                status = statusFor(point),
                close = latest?.session?.close,
                trades = trades,
                editor = editor,
                onOpen = { onOpen(point) },
                holdItems = holdItems(point),
            )
        }
    }
}

/**
 * One call under its stock: who made it and where it stands, then its levels as a ladder and as
 * the four numbers the ladder is drawn from. A held call keeps its ring, so a report can still be
 * read for what is held before any call is opened.
 */
@Composable
private fun CallLine(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    channel: String?,
    status: Pair<String, CallGroup>,
    close: Double?,
    trades: TradeBook?,
    editor: CallEditor?,
    onOpen: () -> Unit,
    holdItems: List<MenuItem>,
) {
    var editing by remember(point) { mutableStateOf(false) }
    val ring = heldBorder(trades?.heldFor(stock, point))
    val shape = RoundedCornerShape(14.dp)
    HoldMenu(items = holdItems, modifier = Modifier.fillMaxWidth(), onTap = onOpen) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.s)
                .then(if (ring != null) Modifier.border(ring, shape).padding(Space.s) else Modifier),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(
                    channel?.takeIf(String::isNotBlank) ?: "Channel not stated",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                KitTag(status.first, tone = status.second.tone())
                if (point.isTPlusOne) KitTag("T+1", tone = palette.muted)
                if (editor?.editFor(stock, point) != null) EditedChip { editing = true }
                // Against the entry: the app keeps no day's move behind AppState.
                if (!point.isWatching) {
                    moveVsEntry(point, close)?.let { move ->
                        Text(
                            formatPercent(move),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                            color = if (move >= 0) palette.gain else palette.loss,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (point.isWatching) {
                Text(
                    watchingSentence(point),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    modifier = Modifier.padding(top = Space.s),
                )
            } else {
                PriceLadder(point, Modifier.padding(top = Space.s), reached = close, compact = true)
                Row(Modifier.fillMaxWidth().padding(top = Space.xs)) {
                    LevelFigure("Stop", formatPrice(point.stopLoss), palette.loss, Modifier.weight(1f))
                    LevelFigure("Entry", entryText(point), palette.ink, Modifier.weight(EntryShare))
                    LevelFigure("Target 1", formatPrice(point.target1), palette.gain, Modifier.weight(1f))
                    LevelFigure("Target 2", formatPrice(point.target2), palette.gain, Modifier.weight(1f))
                }
            }
        }
    }
    if (editing && editor != null) {
        EditCallSheet(stock, point, editor, onDismiss = { editing = false })
    }
}

/** The entry is a band, two prices and a dash, so its column is wider than the single prices'. */
private const val EntryShare = 1.8f

/** One level of a call as a number under its name. */
@Composable
private fun LevelFigure(label: String, value: String, tone: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.muted, maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
            color = tone,
            maxLines = 1,
            softWrap = false,
        )
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
    calls: Int,
) {
    // Ticker style and name style match the identity block Insights draws on its own call card, so
    // one stock looks the same on two screens. The price is the stock's, said once for every call
    // under it; each call's own move is on its own line.
    Row(verticalAlignment = Alignment.CenterVertically) {
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
                val under = listOfNotNull(stock.stockNameArabic, "$calls calls".takeIf { calls > 1 })
                if (under.isNotEmpty()) {
                    Text(
                        under.joinToString(" · "),
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
                Text("now", style = MaterialTheme.typography.labelSmall, color = palette.muted)
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
