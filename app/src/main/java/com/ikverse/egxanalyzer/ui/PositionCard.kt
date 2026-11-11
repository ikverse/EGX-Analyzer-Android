package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.MoreVert
import com.ikverse.egxanalyzer.ui.theme.palette
import com.ikverse.egxanalyzer.ui.kit.MenuItem
import com.ikverse.egxanalyzer.ui.kit.KitMenu
import com.ikverse.egxanalyzer.ui.kit.KitIconButton
import com.ikverse.egxanalyzer.ui.kit.Kit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.FULL_SPLIT_PCT
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.Sale
import com.ikverse.egxanalyzer.model.recordedSale
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.NeedReason
import com.ikverse.egxanalyzer.model.plainWord
import com.ikverse.egxanalyzer.ui.kit.HoldMenu
import com.ikverse.egxanalyzer.ui.kit.KitTag
import com.ikverse.egxanalyzer.ui.kit.KitChip
import com.ikverse.egxanalyzer.ui.kit.Panel
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.tileSignals
import com.ikverse.egxanalyzer.ui.kit.MoreRow
import com.ikverse.egxanalyzer.ui.kit.Timeline
import com.ikverse.egxanalyzer.ui.kit.TimelineStep
import com.ikverse.egxanalyzer.ui.kit.StepTone
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.SheetFooterSpacer
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.theme.pageAccent
import java.time.LocalDate

/**
 * What can be done about one trade, gathered once by `rememberTradeActions` so the hold menu, the
 * sheet, the book pane and tabletop's buttons all do the same thing.
 */
internal class TradeActions(
    /** Opens the sale dialog. */
    val sell: () -> Unit,
    /** Opens the right correction: the sale on a sold trade, the entry on any other. */
    val edit: () -> Unit,
    /** The call this trade was taken on, in Insights. Absent once its analysis is gone. */
    val openCall: (() -> Unit)?,
    val chart: () -> Unit,
    /** Opens the Keep open dialog. */
    val keepOpen: () -> Unit,
    val stopKeepingOpen: () -> Unit,
    val undoSale: () -> Unit,
    val showOnly: () -> Unit,
    val remove: () -> Unit,
)

/** The hold menu for [view]: every action that applies to it, Remove last and red. */
internal fun TradeActions.menu(view: PositionView): List<MenuItem> = buildList {
    if (view.awaitingSale) add(MenuItem("I sold it") { sell() })
    add(MenuItem("Fix the trade") { edit() })
    openCall?.let { add(MenuItem("See the call") { it() }) }
    add(MenuItem("Full chart") { chart() })
    if (view.keptOpen) {
        add(MenuItem(LetItClose) { stopKeepingOpen() })
    } else if (!view.finished && view.awaitingSale) {
        add(MenuItem("Keep it open") { keepOpen() })
    }
    if (view.realized) add(MenuItem("Undo the sale") { undoSale() })
    add(MenuItem("Show only ${view.ticker}") { showOnly() })
    add(MenuItem("Remove", danger = true) { remove() })
}

/**
 * A trade, short: what it is, what was paid against where it is now, the return, its tags, and its
 * ladder. A tap opens it in full; a hold opens what can be done about it.
 *
 * Until 5.1 this card was the whole trade - a chart toggle, two figure panels, a menu, the Sold and
 * Keep open buttons. All of that is in [TradeDetail] now, a press away, so a list of open trades can
 * be read down for where each stands.
 */
@Composable
internal fun PositionCard(
    view: PositionView,
    /** Whether the trade is close to its stop or to target 2, for the tag that says so. */
    near: NeedReason?,
    highlighted: Boolean,
    onHighlightShown: () -> Unit,
    onOpen: () -> Unit,
    actions: List<MenuItem>,
    modifier: Modifier = Modifier,
) {
    val position = view.position
    // Set by the menu's Remove. The card closes up first and the trade is removed when it has, so
    // the rows below slide into the gap rather than snapping into it - and there is no question,
    // because the status line offers Undo for the next ten seconds (see `deletePosition`).
    var leaving by remember { mutableStateOf(false) }
    val remove = actions.firstOrNull { it.danger }?.onClick
    LaunchedEffect(leaving) {
        if (leaving) {
            delay(ExitMs.toLong())
            remove?.invoke()
        }
    }
    val items = actions.map { if (it.danger) it.copy(onClick = { leaving = true }) else it }
    // Only the arrival flash outlines the card; its status is said by the pill, never by an edge.
    val border = arrivalFlash(highlighted, onHighlightShown, view.status.tone())
    AnimatedVisibility(visible = !leaving, exit = CollapseOut) {
        HoldMenu(items = items, modifier = modifier.fillMaxWidth(), onTap = onOpen) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Kit.TileShape)
                    .background(palette.card)
                    .then(if (border != null) Modifier.border(border, Kit.TileShape) else Modifier)
                    .padding(Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StockLogo(position.ticker, LogoSize.Row, Modifier.padding(end = Space.s))
                    Text(position.ticker, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            formatPercent(view.returnPct),
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = TabularFigures),
                            color = PriceRole.forReturn(view.returnPct),
                        )
                        if (view.open) Text("so far", style = MaterialTheme.typography.labelSmall, color = palette.muted)
                    }
                }
                Text(
                    "You paid ${formatPrice(position.entryPrice)} · now ${formatPrice(view.currentPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    PositionStatusChip(view)
                    if (view.open && view.sessionsRemaining > 0) {
                        KitTag(
                            "${view.sessionsRemaining} trading ${if (view.sessionsRemaining == 1) "day" else "days"} left",
                            tone = palette.muted,
                        )
                    }
                    when (near) {
                        NeedReason.NEAR_STOP -> KitTag("Close to its stop", tone = palette.loss)
                        NeedReason.NEAR_TARGET2 -> KitTag("Close to target 2", tone = palette.gain)
                        else -> Unit
                    }
                    if (view.overdue) OverdueChip(view.overdueDays)
                    // A trade can only be overdue by being kept open, so Overdue already says it.
                    if (view.keptOpen && !view.overdue) KitTag("Kept open", tone = palette.muted)
                    if (position.isTPlusOne) TPlusOneChip(position)
                    if (view.priceScaleChanged) PriceScaleChip()
                }
                TradeLadder(view)
            }
        }
    }
}

/**
 * The trade's ladder: the price paid as the entry, named "You paid", the dot where it stands -
 * today's close while it runs, where it ended once it has - and a stripe from the lowest to the
 * highest it has been since it was bought. Nothing is plotted across a change of scale: the levels
 * are in the old money and the price in the new.
 */
@Composable
private fun TradeLadder(view: PositionView, modifier: Modifier = Modifier, compact: Boolean = false) {
    val position = view.position
    val low = view.troughSinceEntry
    val high = view.peakSinceEntry
    PriceLadder(
        stopLoss = position.stopLoss,
        entryLow = position.entryPrice,
        entryHigh = position.entryPrice,
        target1 = position.target1,
        target2 = position.target2,
        modifier = modifier,
        reached = if (view.priceScaleChanged) null else view.exitPrice ?: view.currentPrice,
        compact = compact,
        travelled = if (!view.priceScaleChanged && low != null && high != null && low <= high) low..high else null,
        entryLabel = "You paid",
    )
}

/** A finished trade as one row: the stock, how it ended, and what it made. */
@Composable
internal fun FinishedRow(view: PositionView, onOpen: () -> Unit, actions: List<MenuItem>, modifier: Modifier = Modifier) {
    HoldMenu(items = actions, modifier = modifier.fillMaxWidth(), onTap = onOpen) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Kit.PanelShape)
                .background(palette.card)
                .padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            StockLogo(view.ticker, LogoSize.Row)
            Text(view.ticker, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            KitTag(view.status.plainWord(), tone = view.status.tone())
            Text(
                formatPercent(view.returnPct),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures),
                color = PriceRole.forReturn(view.returnPct),
            )
        }
    }
}

/**
 * One trade in full, in the order both call sheets read in since 2026-11-11: who it is, where it
 * stands, the levels, the price since it was bought, what happened, three figures, More numbers,
 * and every action. The sheet, the book pane and tabletop's upper half all draw this; [brief] cuts
 * it to the header, where it stands, the levels and the time left, and [showButtons] leaves the
 * actions to the caller.
 *
 * Each block is a tile of its own, so the caller gives this a plain column on the page's ground
 * rather than a card to sit in.
 */
@Composable
internal fun ColumnScope.TradeDetail(
    view: PositionView,
    appState: AppState,
    actions: TradeActions,
    showButtons: Boolean = true,
    brief: Boolean = false,
) {
    val position = view.position
    val openStock = LocalOpenStock.current
    // One precision for every price on the sheet: 1.9, 2.1 and 1.85 are one stock's money.
    val decimals = priceDecimals(
        position.entryPrice, position.stopLoss, position.target1, position.target2,
        view.currentPrice, view.exitPrice,
    )
    SheetIdentity(
        ticker = position.ticker,
        arabic = position.companyArabic,
        english = position.companyEnglish,
        onOpenStock = { openStock(position.ticker) },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        PositionStatusChip(view)
        if (view.overdue) OverdueChip(view.overdueDays)
        // A trade can only be overdue by being kept open, so Overdue already says it.
        if (view.keptOpen && !view.overdue) KitTag("Kept open", tone = palette.muted)
        if (position.isTPlusOne) TPlusOneChip(position)
        if (view.priceScaleChanged) PriceScaleChip()
        // "from", so the channel reads as where the call came from rather than as a second status.
        position.channel?.takeIf(String::isNotBlank)?.let { KitTag("from $it", tone = palette.muted) }
    }

    val signals = tileSignals(TileTone.INK)
    StandsTile(
        tone = TileTone.INK,
        figure = formatPercent(view.returnPct),
        figureColor = returnTone(view.returnPct, signals),
    ) {
        Text(
            buildAnnotatedString {
                append(if (view.realized) "you made, on a buy at " else "since you bought at ")
                figure(formatPrice(position.entryPrice, decimals))
                append(" on ${shortDate(position.entryDate)}")
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            view.profitLine(decimals),
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = QuietOnTile),
        )
        position.keepOpenNote?.takeIf(String::isNotBlank)?.let { why ->
            Text(why, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = QuietOnTile))
        }
    }

    // What staying in past the call's own end has cost or made, on the trades where that happened.
    view.callEndedReturnPct?.let { ended ->
        val stopped = view.marketStatus == PositionStatus.STOPPED_OUT || view.stoppedAfterPartial
        Tile(Modifier.fillMaxWidth(), tone = TileTone.ALERT) {
            Text(
                if (stopped) "You kept it open past the stop" else "You kept it open past the deadline",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(keptPastWords(ended, view.returnPct, stopped), style = MaterialTheme.typography.bodyMedium)
        }
    }

    Tile(Modifier.fillMaxWidth()) {
        SheetSectionLabel("The levels")
        Spacer(Modifier.height(Space.s))
        LevelsBlock(
            stopLoss = position.stopLoss,
            entryLow = position.entryPrice,
            entryHigh = position.entryPrice,
            target1 = position.target1,
            target2 = position.target2,
            // Nothing is placed across a change of scale: the levels are in the old money.
            now = if (view.priceScaleChanged) null else view.exitPrice ?: view.currentPrice,
            entryWord = "You paid",
            entryNote = shortDate(position.entryDate),
            standing = when {
                view.open -> "now"
                view.realized -> "where you sold"
                else -> "where it ended"
            },
        )
    }

    if (view.open && view.sessionsRemaining > 0) {
        Tile(Modifier.fillMaxWidth()) {
            Text(
                "Time left · ${view.sessionsRemaining} of ${position.windowSessions} trading days",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(Space.s))
            DaySteps(view.sessionsElapsed, position.windowSessions)
        }
    }
    if (brief) {
        // The timeline is below the fold in tabletop, so the one thing it would have said about
        // a trade past its deadline is said here.
        if (view.open && view.sessionsRemaining == 0) {
            view.deadlineDate?.let {
                Text(
                    "The call's days ran out on ${shortDate(it)}. You are keeping it open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )
            }
        }
        if (showButtons) TradeButtons(view, actions)
        return
    }

    // The chart where a running trade has more than one close to draw.
    val history = if (view.open) rememberPriceHistory(appState, position.ticker) else emptyList()
    if (history.count { it.close != null } > 1) {
        Tile(Modifier.fillMaxWidth()) { TradeChart(position, palette.card, history, decimals) }
    }

    Tile(Modifier.fillMaxWidth()) {
        SheetSectionLabel("What happened")
        Spacer(Modifier.height(Space.s))
        Timeline(view.timeline(decimals))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        SmallFigure(
            formatPercent(view.fromEntry(view.peakSinceEntry)),
            listOfNotNull("Best", view.peakOn?.let(::shortDate)).joinToString(" · "),
            Modifier.weight(1f),
            palette.gain,
        )
        SmallFigure(
            formatPercent(view.fromEntry(view.troughSinceEntry)),
            listOfNotNull("Worst", view.troughOn?.let(::shortDate)).joinToString(" · "),
            Modifier.weight(1f),
            palette.loss,
        )
        SmallFigure(view.sessionsHeld.toString(), "Trading days held", Modifier.weight(1f), palette.ink)
    }

    MoreRow("More numbers", appState.pages.portfolioMoreNumbers) {
        FigureGroup(
            "The numbers",
            listOf(
                {
                    Figure(
                        "Entry", formatPrice(position.entryPrice, decimals), Modifier.weight(1f), tone = PriceRole.entry,
                        caption = "bought ${shortDate(position.entryDate)}",
                    )
                },
                {
                    Figure(
                        "Stop loss", formatPrice(position.stopLoss, decimals), Modifier.weight(1f), tone = PriceRole.stop,
                        caption = view.fromEntry(position.stopLoss).distance(),
                    )
                },
                {
                    Figure(
                        "Target 1", formatPrice(position.target1, decimals), Modifier.weight(1f), tone = PriceRole.target,
                        caption = view.fromEntry(position.target1).distance(),
                    )
                },
                {
                    Figure(
                        "Target 2", formatPrice(position.target2, decimals), Modifier.weight(1f), tone = PriceRole.target,
                        caption = view.fromEntry(position.target2).distance(),
                    )
                },
                {
                    Figure(
                        "Last close", formatPrice(view.currentPrice, decimals), Modifier.weight(1f), tone = PriceRole.market,
                        caption = view.currentPriceOn?.let(::shortDate),
                    )
                },
                {
                    Figure(
                        "Peak", formatPrice(view.peakSinceEntry, decimals), Modifier.weight(1f), tone = PriceRole.market,
                        caption = view.extremeCaption(view.peakSinceEntry, view.peakOn),
                    )
                },
                {
                    Figure(
                        "Trough", formatPrice(view.troughSinceEntry, decimals), Modifier.weight(1f), tone = PriceRole.market,
                        caption = view.extremeCaption(view.troughSinceEntry, view.troughOn),
                    )
                },
                {
                    Figure(
                        "Deadline", view.deadline(), Modifier.weight(1f), tone = PriceRole.muted,
                        // Sessions held, not elapsed: the deadline counts from the call and this
                        // from the entry, and on a trade bought late they differ.
                        caption = "${view.sessionsHeld} ${view.sessionsHeld.sessionWord()} held",
                        valueStyle = MaterialTheme.typography.bodyMedium,
                    )
                },
                {
                    // The call's plan, not the trade's result: what target 1 offered against what
                    // the stop risked, from the price actually paid.
                    Figure(
                        "Planned gain vs risk",
                        view.riskReward?.let { "${"%.1f".format(it)}×" } ?: Dash,
                        Modifier.weight(1f),
                        tone = PriceRole.muted,
                        caption = "to target 1, against the stop",
                    )
                },
            ),
        )
    }
    if (showButtons) TradeButtons(view, actions)
}

/** One figure and what it is, on a small tile of its own. */
@Composable
private fun SmallFigure(value: String, label: String, modifier: Modifier, tone: Color) {
    Panel(modifier, color = palette.card, contentPadding = PaddingValues(12.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
            color = if (value == Dash) palette.muted else tone,
            maxLines = 1,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.muted)
    }
}

/**
 * What ending with the call would have returned, and what staying in has cost or made since.
 *
 * [ended] is the return at the stop or at the deadline's close; [now] is the trade's return today.
 * The difference is in points of return, which is what two percentages of one entry differ by.
 */
internal fun keptPastWords(ended: Double, now: Double?, stopped: Boolean): String {
    val first = if (stopped) {
        "Selling at the stop would have ended it at ${formatPercent(ended)}."
    } else {
        "Selling at the deadline would have ended it at ${formatPercent(ended)}."
    }
    if (now == null) return first
    val points = formatPercent(kotlin.math.abs(now - ended), signed = false).removeSuffix("%")
    return when {
        now < ended -> "$first Holding since has cost $points points more."
        now > ended -> "$first Holding since has made $points points more."
        else -> "$first It stands at the same return today."
    }
}

/**
 * The trade's actions as buttons: the sale across the full width, the three that look at or correct
 * the trade under it, and the two that end it last. The same actions the hold menu lists.
 */
@Composable
internal fun TradeButtons(view: PositionView, actions: TradeActions) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        if (view.awaitingSale) KitButton("I sold it", onClick = actions.sell, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            KitButton("Fix the trade", onClick = actions.edit, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
            actions.openCall?.let { KitButton("See the call", onClick = it, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL) }
            KitButton("Full chart", onClick = actions.chart, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            if (view.keptOpen) {
                KitButton(LetItClose, onClick = actions.stopKeepingOpen, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
            } else if (!view.finished && view.awaitingSale) {
                KitButton("Keep it open", onClick = actions.keepOpen, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
            }
            if (view.realized) KitButton("Undo the sale", onClick = actions.undoSale, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
            KitButton("Remove trade", onClick = actions.remove, kind = ButtonKind.DANGER, size = ButtonSize.SMALL)
        }
    }
}

/**
 * Hands a trade the user is keeping open back to the call: it closes where the stop or the deadline
 * closed it. It read "Stop keeping it open" until 2026-11-11, which beside a "Stopped out" tag was
 * two different stops in one glance.
 */
internal const val LetItClose = "Let it close"

/** [TradeDetail] in the kit's sheet, titled with the ticker: a side panel unfolded. */
@Composable
internal fun TradeSheet(view: PositionView, appState: AppState, actions: TradeActions, onDismiss: () -> Unit) {
    KitSheet(onDismiss = onDismiss, posture = LocalFoldPosture.current, title = view.ticker) {
        TradeDetail(view, appState, actions)
        SheetFooterSpacer()
    }
}

/**
 * What happened to the trade, from dated facts only: the purchase, each leg of a sale, the stop,
 * target 2, the deadline, and where it is now. The day target 1 was reached is not kept on a trade,
 * so it has no step; the status tag says it.
 */
internal fun PositionView.timeline(decimals: Int = priceDecimals(position.entryPrice, position.stopLoss, currentPrice)): List<TimelineStep> {
    val p = position
    fun price(value: Double?) = formatPrice(value, decimals)
    val dated = buildList {
        val low = p.entryLow
        val high = p.entryHigh
        // Only where the call asked for something other than what was paid, and as one number
        // where its range was one: "the call said 1.9-1.9" was a range with a single price in it.
        val band = when {
            low == null || high == null -> ""
            low == high && low == p.entryPrice -> ""
            low == high -> " · the call said ${price(low)}"
            else -> " · the call said ${price(low)}–${price(high)}"
        }
        add(p.entryDate to TimelineStep("You bought at ${price(p.entryPrice)}$band", date = shortDate(p.entryDate)))
        if (soldInParts) {
            p.exitDate1?.let { add(it to TimelineStep("Sold ${formatPrice(p.exitSplitPct)}% at ${price(p.exitPrice1)}", date = shortDate(it))) }
            p.exitDate?.let { add(it to TimelineStep("Sold the rest at ${price(p.exitPrice2)}", date = shortDate(it))) }
        } else if (realized) {
            p.exitDate?.let { add(it to TimelineStep("You sold at ${price(exitPrice)}", date = shortDate(it))) }
        }
        val atStop = p.stopLoss?.let { " at ${price(it)}" }.orEmpty()
        when (marketStatus) {
            PositionStatus.STOPPED_OUT -> (stoppedOn ?: settledOn)?.let {
                add(it to TimelineStep("Hit the stop$atStop", date = shortDate(it), tone = StepTone.BAD))
            }
            PositionStatus.FULL_TARGET_HIT -> settledOn?.let {
                add(it to TimelineStep("Reached target 2", date = shortDate(it), tone = StepTone.GOOD))
            }
            else -> if (stoppedAfterPartial) {
                stoppedOn?.let { add(it to TimelineStep("Fell back to the stop$atStop", date = shortDate(it), tone = StepTone.BAD)) }
            }
        }
        deadlineDate?.let {
            add(it to TimelineStep("The call's ${p.windowSessions} trading days ran out", date = shortDate(it)))
        }
    }.sortedBy { it.first }.map { it.second }
    val now = if (open) {
        val current = currentPrice
        // Past the call's own end the targets are no longer what the trade is measured against:
        // what the reader paid is. Inside the window the distance to target 1 is the useful one.
        val pastCall = marketStatus == PositionStatus.STOPPED_OUT || stoppedAfterPartial || deadlineDate != null
        val t1 = p.target1
        val where = when {
            current == null || current <= 0 -> ""
            pastCall -> returnPct?.let { pct ->
                val size = formatPercent(kotlin.math.abs(pct), signed = false)
                when {
                    pct < 0 -> ", $size below what you paid"
                    pct > 0 -> ", $size above what you paid"
                    else -> ", at what you paid"
                }
            }.orEmpty()
            t1 != null -> {
                val gap = (t1 - current) / current * 100
                if (gap > 0) ", ${"%.1f".format(gap)}% below target 1" else ", above target 1"
            }
            else -> ""
        }
        listOf(TimelineStep("Now ${price(current)}$where", date = currentPriceOn?.let(::shortDate), tone = StepTone.ACTIVE))
    } else {
        emptyList()
    }
    return dated + now
}

/**
 * The price since the trade was bought, with what was paid and the stop drawn across it.
 *
 * It opened on a month until 2026-11-11, which on a trade bought two months ago left the purchase
 * off the left edge and drew every level above the line's own range - three labels pinned to the
 * top edge over two overlapping dashed lines. The levels are on the ladder above now, so this chart
 * carries only the two a holder watches the line against, and only while they are near enough to
 * be drawn where they really are; and it opens on the range the trade has actually lived in.
 *
 * The range and the touch are local to this card rather than `PageState.stockChartRange` - that
 * state is written for the one stock sheet the app has open at a time.
 */
@Composable
private fun TradeChart(position: Position, on: Color, history: List<DailySession>, decimals: Int) {
    // Null is "since you bought", which is not one of the stock sheet's ranges.
    var picked by remember(position.id) { mutableStateOf<ChartRange?>(null) }
    val sinceBought = remember(history, position.entryDate) {
        history.filter { !it.date.isBefore(position.entryDate) }
    }
    // A trade bought on the newest session has one close to its name; a month says more than a dot.
    val canShowSinceBought = sinceBought.count { it.close != null } > 1
    val range = picked ?: if (canShowSinceBought) null else ChartRange.Default
    val visible = remember(history, range, sinceBought) {
        if (range == null) {
            sinceBought
        } else {
            // Measured back from the newest session the app holds, exactly as `StockSheet` does
            // it: a feed running behind should shorten the line, not draw an empty box.
            val anchor = history.lastOrNull()?.date ?: return@remember history
            history.filter { !it.date.isBefore(range.since(anchor)) }
        }
    }
    val closes = remember(visible) { visible.mapNotNull { it.close } }
    val move = remember(visible) { visible.rangeMove() }
    val levels = remember(closes, position) {
        val low = closes.minOrNull()
        val high = closes.maxOrNull()
        if (low == null || high == null) {
            null
        } else {
            // Within one span of the line's own range, which the chart can draw without pinning.
            val reach = (high - low).coerceAtLeast(high * 0.02)
            fun near(level: Double?) = level?.takeIf { it >= low - reach && it <= high + reach }
            ChartLevels(
                source = "your trade",
                stopLoss = near(position.stopLoss),
                entryLow = null,
                entryHigh = null,
                target1 = null,
                target2 = null,
                paid = near(position.entryPrice),
            ).takeIf { it.any }
        }
    }
    var touched by remember(visible) { mutableStateOf<DailySession?>(null) }
    val p = palette
    val colors = remember(p, on) { chartCardColors(p, surface = on) }

    Column {
        // The reading takes the headline's two places rather than a line of its own: a caption
        // that arrived on touch would push the chart about under the finger that asked for it.
        val reading = chartReading(visible, touched) { formatPrice(it, decimals) }
        ChartHeadline(
            title = reading?.first ?: if (range == null) {
                "Since you bought · ${closes.size} sessions"
            } else {
                range.title
            },
            // What the visible line adds up to, which is the one thing its shape cannot say.
            figure = reading?.second ?: move?.let { formatPercent(it) }.orEmpty(),
            colors = colors,
            figureColor = if (reading == null && move != null) PriceRole.forReturn(move) else colors.text,
        )
        if (closes.size > 1) {
            PriceChart(
                sessions = visible,
                levels = levels,
                // The one dot on this chart is the day the trade was bought.
                calls = setOf(position.entryDate),
                colors = colors,
                selected = touched,
                onSelect = { touched = it },
                modifier = Modifier.padding(top = Space.s),
                height = PositionChartHeight,
            )
            if (visible.any { it.date == position.entryDate }) {
                ChartLegend("The day you bought", colors, Modifier.padding(top = Space.s))
            }
            // The scale the line is drawn on. With no level near enough to draw, the chart's own
            // High and Low tags already say it.
            if (levels != null) {
                Text(
                    buildAnnotatedString {
                        append("Lowest close ")
                        figure(formatPrice(closes.min(), decimals))
                        append(" · highest ")
                        figure(formatPrice(closes.max(), decimals))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        } else {
            // Absent rather than an empty box, which reads as a chart that failed to load.
            ChartLegend("No sessions stored in this range.", colors, Modifier.padding(top = Space.s), dot = false)
        }
        // Null is "since you bought", which leads the track where the trade has a line to show.
        ChartRanges(
            options = buildList<ChartRange?> {
                if (canShowSinceBought) add(null)
                addAll(TradeChartRanges)
            },
            selected = range,
            onSelect = { picked = it },
            label = { it?.label ?: "Since buy" },
            colors = colors,
            modifier = Modifier.padding(top = Space.m),
        )
    }
}

/** The ranges offered beside "Since you bought": four, so the row fits the cover screen whole. */
private val TradeChartRanges = listOf(ChartRange.WEEK, ChartRange.MONTH, ChartRange.THREE_MONTHS, ChartRange.SIX_MONTHS)

/**
 * How long the recommendation has left, counted in the sessions it is judged in.
 *
 * Trading sessions rather than days, and counted from the session the call was made for: a stock
 * does not move at the weekend, and the user's entry date never enters into it. A window the user
 * set themselves says so here rather than in a chip of its own - the figure it qualifies is this
 * one, and a reader wondering why a trade has fifteen sessions is already looking at it.
 */
private fun PositionView.deadline(): String {
    val left = deadlineDate?.let { "passed ${shortDate(it)}" }
        ?: "$sessionsRemaining of ${position.windowSessions} left"
    return if (position.windowCustom) "$left · custom" else left
}

/**
 * A high or a low, dated and measured from what was paid.
 *
 * Both halves, or the figure says half of it. The date alone leaves the reader dividing two prices
 * to find out whether the peak was ever worth taking; the percentage alone leaves them wondering
 * whether it happened last week or on the first morning.
 */
private fun PositionView.extremeCaption(price: Double?, on: LocalDate?): String? {
    if (price == null) return null
    return listOfNotNull(on?.let(::shortDate), fromEntry(price)?.let { formatPercent(it) })
        .joinToString(" · ")
        .ifBlank { null }
}

/**
 * Says why a trade is still running, on the trades where Overdue is not already saying it.
 *
 * Only up before the deadline passes: after it, Overdue means kept open and this would be the same
 * fact twice on one row. The instruction in it is the point while it is up: the way to end a trade
 * being kept open is to sell it, and the card's menu holds the way to hand it back to its deadline
 * instead.
 */
/**
 * Says that the call this trade was taken on named its own deadline, and what that deadline was.
 *
 * The reason for the shortest window on the screen. A T+1 trade reached this card as a bare "2 of 2
 * left" - a figure that reads as a trade about to run out rather than as a trade that was always
 * meant to last two sessions, which is the difference between a deadline the user should act on and
 * one that was the point of the call. Insights has said this on the call for as long as the pill has
 * existed; the Portfolio, where the trade actually is, said nothing.
 *
 * Tappable for the reason the pill in Insights gives - a pill that is only sometimes pressable
 * teaches nobody that it can be pressed - and neutral since 2026-09-11, where it was `primary`.
 * The sentence behind it is about this trade rather than about the call, which is the one thing
 * the two screens are entitled to word differently.
 */
@Composable
private fun TPlusOneChip(position: Position) {
    var showing by remember(position.id) { mutableStateOf(false) }
    // Neutral, like every other note pill on this card. It was `primary` - the app's own voice,
    // on the reasoning that a T+1 changes what the reader has to do - and that made one pill in
    // the app a different colour from its neighbours for a reason none of them showed. The
    // wording is what says this call names its own deadline; the hue was saying it twice, in a
    // language the card spends on prices everywhere else. Asked for on 2026-09-11.
    KitTag("T+1", tone = palette.muted, onClick = { showing = true })
    if (showing) {
        val who = position.channel?.takeIf(String::isNotBlank) ?: "The channel"
        val call = "$who printed this as a T+1 call: buy on the session it was made for, and be " +
            "out on the next one."
        // The deadline on the card is the user's own, and the pill must not claim the channel set
        // it. Typing a longer window over the two it offered is a decision worth naming here - it
        // is the reason this card's figures and the channel's record can disagree about the trade.
        val deadline = if (position.windowCustom) {
            "You gave this trade ${position.windowSessions} " +
                "${position.windowSessions.sessionWord()} instead, and every figure on this card " +
                "follows yours."
        } else {
            "That is where the ${position.windowSessions} " +
                "${position.windowSessions.sessionWord()} below came from: the call named this " +
                "trade's deadline, not your default."
        }
        // `InfoNote`'s own constructor rather than the checked `infoNote` factory: that helper's
        // callers are checked by InfoNoteTest for static prose, and both paragraphs here are built
        // from this trade's own figures.
        InfoSheet(InfoNote("${position.ticker} · a T+1 trade", listOf(call, deadline))) {
            showing = false
        }
    }
}

/**
 * Says the trade is being followed past its own deadline instead of closed at it, on the user's
 * own decision - see [KeepOpenButton]. Tappable since 2026-09-24, like every other note pill on
 * this card: the wording says what happened but not why the reader would choose it.
 */
@Composable
private fun KeptOpenChip() {
    var showing by remember { mutableStateOf(false) }
    KitTag("Keep open · sell to close", tone = palette.muted, onClick = { showing = true })
    if (showing) {
        InfoSheet(
            infoNote(
                "Keep open · sell to close",
                "You chose to follow this trade past its own deadline rather than close it there. " +
                    "It stays open, and every figure on this card keeps measuring it, until you " +
                    "record a sale or choose Follow the deadline again from the card's menu.",
            ),
        ) { showing = false }
    }
}

/**
 * The one line that says how much of the return above it is a fact, and where it was struck.
 *
 * A closed position the user reported selling is realized; everything else is an estimate, and
 * saying which is which matters more than the figure itself - which is why the figure is no longer
 * here. It was printed twice, once as Return and again in this line, and one number in two places
 * on one card is a number the reader checks against itself.
 *
 * Every ending now carries the day it happened on. "Stopped at 7.95" left the reader to guess
 * whether that was a fortnight ago or this morning, and the session was something the scorer had
 * known all along.
 */
private fun PositionView.profitLine(decimals: Int): String {
    val at = formatPrice(exitPrice, decimals)
    val line = when {
        // The blend is not a price anybody typed, so the parts come first and it follows as what
        // they came to. Printing it alone would report the trade as done at a price it never was.
        realized && soldInParts -> "Realized · ${position.partsLine(decimals)} · average $at"
        realized -> "Realized · sold at $at" + position.exitDate.dated()
        open -> "Estimated · valued at $at" + currentPriceOn.dated()
        status == PositionStatus.STOPPED_OUT -> "Estimated · stopped at $at" + settledOn.dated()
        status == PositionStatus.FULL_TARGET_HIT ->
            "Estimated · target reached at $at" + settledOn.dated()
        else -> "Estimated · expired at $at" + deadlineDate.dated()
    }
    val disagrees = marketStatus != status
    return line + if (disagrees) " · the call itself: ${marketStatus.label.lowercase()}" else ""
}

/**
 * The two halves of a sale, each with what it went at and the day it went.
 *
 * Reads in the order it happened, which is also the order the dialog asked for it: the target 1
 * part, then the rest. Both dates are printed - the whole reason a sale has parts is that they
 * usually happen a week or more away from each other.
 */
private fun Position.partsLine(decimals: Int): String {
    val first = formatPrice(exitSplitPct)
    val rest = formatPrice(FULL_SPLIT_PCT - (exitSplitPct ?: FULL_SPLIT_PCT))
    return "sold $first% at ${formatPrice(exitPrice1, decimals)}" + exitDate1.dated() +
        " and $rest% at ${formatPrice(exitPrice2, decimals)}" + exitDate.dated()
}

/** " on 14 Aug", or nothing at all where the session behind a price was never recorded. */
private fun LocalDate?.dated(): String = this?.let { " on ${shortDate(it)}" }.orEmpty()

/** Ticker plus two lines of company name, so every position card starts the same height. */
private val PositionHeaderHeight = 60.dp

/**
 * Shorter than [StockSheet]'s 220dp - that height was tuned for a sheet with nothing else on the
 * page; this chart shares a card with two figure groups and a menu's worth of chrome besides, and
 * a running position can carry as many as five levels within a few percent of each other. 180dp
 * gives that cluster a full row before crowding the way the sheet's own 150dp used to.
 */
private val PositionChartHeight = 180.dp


/** The status edge down the left of a trade card. */
