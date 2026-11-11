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
    add(MenuItem("Chart") { chart() })
    if (view.keptOpen) {
        add(MenuItem("Stop keeping it open") { stopKeepingOpen() })
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
 * The trade's ladder: the price paid as the entry, unnamed because the card says it just above, and the arrow where it stands -
 * today's close while it runs, where it ended once it has. Nothing is plotted across a change of
 * scale: the levels are in the old money and the price in the new.
 */
@Composable
private fun TradeLadder(view: PositionView, modifier: Modifier = Modifier, compact: Boolean = false) {
    val position = view.position
    PriceLadder(
        stopLoss = position.stopLoss,
        entryLow = position.entryPrice,
        entryHigh = position.entryPrice,
        target1 = position.target1,
        target2 = position.target2,
        modifier = modifier,
        reached = if (view.priceScaleChanged) null else view.exitPrice ?: view.currentPrice,
        compact = compact,
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
 * One trade in full: what it is, its chart, the time left, what happened, three figures, the line
 * that says how much of the return is a fact, More numbers, and every action. The sheet, the book
 * pane and tabletop's upper half all draw this; [brief] cuts it to the header, ladder and time
 * left, and [showButtons] leaves the actions to the caller.
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
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(position.ticker, style = MaterialTheme.typography.headlineSmall)
            position.companyArabic?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatPercent(view.returnPct),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = TabularFigures),
                color = PriceRole.forReturn(view.returnPct),
            )
            Text(if (view.realized) "you made" else "since you bought", style = MaterialTheme.typography.labelSmall, color = palette.muted)
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        PositionStatusChip(view)
        position.channel?.takeIf(String::isNotBlank)?.let { KitTag(it, tone = palette.muted) }
    }
    // The full chart where a running trade has more than one close to draw; the ladder otherwise.
    val history = if (view.open && !brief) rememberPriceHistory(appState, position.ticker) else emptyList()
    Box(Modifier.fillMaxWidth().tradePanel()) {
        if (history.count { it.close != null } > 1) {
            PositionChartSection(position, MaterialTheme.colorScheme.surfaceContainerHighest, history)
        } else {
            TradeLadder(view)
        }
    }
    if (view.open) {
        Text(
            "Time left · ${view.sessionsRemaining} of ${position.windowSessions} trading days",
            style = MaterialTheme.typography.titleSmall,
        )
        DeadlineSteps(view)
    }
    if (brief) {
        if (showButtons) TradeButtons(view, actions)
        return
    }
    Timeline(view.timeline())
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        FigureCell(formatPercent(view.fromEntry(view.peakSinceEntry)), "Best so far", Modifier.weight(1f), PriceRole.target)
        FigureCell(formatPercent(view.fromEntry(view.troughSinceEntry)), "Worst dip", Modifier.weight(1f), PriceRole.stop)
        FigureCell(view.riskReward?.let { "${"%.1f".format(it)}×" } ?: Dash, "Gain vs risk", Modifier.weight(1f))
    }
    Text(view.profitLine(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
    position.keepOpenNote?.takeIf(String::isNotBlank)?.let { why ->
        Text(why, style = MaterialTheme.typography.bodySmall, color = palette.muted)
    }
    MoreRow("More numbers", appState.pages.portfolioMoreNumbers) {
        FigureGroup(
            "The numbers",
            listOf(
                {
                    Figure(
                        "Entry", formatPrice(position.entryPrice), Modifier.weight(1f), tone = PriceRole.entry,
                        caption = "bought ${shortDate(position.entryDate)}",
                    )
                },
                {
                    Figure(
                        "Stop loss", formatPrice(position.stopLoss), Modifier.weight(1f), tone = PriceRole.stop,
                        caption = view.fromEntry(position.stopLoss).distance(),
                    )
                },
                {
                    Figure(
                        "Target 1", formatPrice(position.target1), Modifier.weight(1f), tone = PriceRole.target,
                        caption = view.fromEntry(position.target1).distance(),
                    )
                },
                {
                    Figure(
                        "Target 2", formatPrice(position.target2), Modifier.weight(1f), tone = PriceRole.target,
                        caption = view.fromEntry(position.target2).distance(),
                    )
                },
                {
                    Figure(
                        "Last close", formatPrice(view.currentPrice), Modifier.weight(1f), tone = PriceRole.market,
                        caption = view.currentPriceOn?.let(::shortDate),
                    )
                },
                {
                    Figure(
                        "Peak", formatPrice(view.peakSinceEntry), Modifier.weight(1f), tone = PriceRole.market,
                        caption = view.extremeCaption(view.peakSinceEntry, view.peakOn),
                    )
                },
                {
                    Figure(
                        "Trough", formatPrice(view.troughSinceEntry), Modifier.weight(1f), tone = PriceRole.market,
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
            ),
        )
    }
    if (showButtons) TradeButtons(view, actions)
}

/** The trade's actions as buttons, in the order the hold menu lists them. */
@Composable
internal fun TradeButtons(view: PositionView, actions: TradeActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        if (view.awaitingSale) KitButton("I sold it", onClick = actions.sell, size = ButtonSize.SMALL)
        KitButton("Fix the trade", onClick = actions.edit, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
        actions.openCall?.let { KitButton("See the call", onClick = it, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL) }
        KitButton("Chart", onClick = actions.chart, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
        if (view.keptOpen) {
            KitButton("Stop keeping it open", onClick = actions.stopKeepingOpen, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
        } else if (!view.finished && view.awaitingSale) {
            KitButton("Keep it open", onClick = actions.keepOpen, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
        }
        if (view.realized) KitButton("Undo the sale", onClick = actions.undoSale, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
        KitButton("Remove", onClick = actions.remove, kind = ButtonKind.DANGER, size = ButtonSize.SMALL)
    }
}

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
private fun PositionView.timeline(): List<TimelineStep> {
    val p = position
    val dated = buildList {
        val band = if (p.entryLow != null && p.entryHigh != null) {
            " · the call said ${formatPrice(p.entryLow)}–${formatPrice(p.entryHigh)}"
        } else {
            ""
        }
        add(p.entryDate to TimelineStep("You bought at ${formatPrice(p.entryPrice)}$band", date = shortDate(p.entryDate)))
        if (soldInParts) {
            p.exitDate1?.let { add(it to TimelineStep("Sold ${formatPrice(p.exitSplitPct)}% at ${formatPrice(p.exitPrice1)}", date = shortDate(it))) }
            p.exitDate?.let { add(it to TimelineStep("Sold the rest at ${formatPrice(p.exitPrice2)}", date = shortDate(it))) }
        } else if (realized) {
            p.exitDate?.let { add(it to TimelineStep("You sold at ${formatPrice(exitPrice)}", date = shortDate(it))) }
        }
        when (marketStatus) {
            PositionStatus.STOPPED_OUT -> (stoppedOn ?: settledOn)?.let {
                add(it to TimelineStep("Hit the stop", date = shortDate(it), tone = StepTone.BAD))
            }
            PositionStatus.FULL_TARGET_HIT -> settledOn?.let {
                add(it to TimelineStep("Reached target 2", date = shortDate(it), tone = StepTone.GOOD))
            }
            else -> if (stoppedAfterPartial) {
                stoppedOn?.let { add(it to TimelineStep("Fell back to the stop", date = shortDate(it), tone = StepTone.BAD)) }
            }
        }
        deadlineDate?.let { add(it to TimelineStep("The deadline passed", date = shortDate(it))) }
    }.sortedBy { it.first }.map { it.second }
    val now = if (open) {
        val price = currentPrice
        val t1 = p.target1
        val where = if (price != null && t1 != null && price > 0) {
            val gap = (t1 - price) / price * 100
            if (gap > 0) " · ${"%.1f".format(gap)}% below target 1" else " · above target 1"
        } else {
            ""
        }
        listOf(TimelineStep("Now ${formatPrice(price)}$where", date = currentPriceOn?.let(::shortDate), tone = StepTone.ACTIVE))
    } else {
        emptyList()
    }
    return dated + now
}

/**
 * The full chart a running trade's header slides open: range chips, a levels toggle, and the
 * touch readout [StockSheet] already draws for the same [PriceChart] - built from the same pieces
 * rather than a smaller chart invented for the card, so a reader who has learned the sheet is not
 * taught a second, thinner version of it here.
 *
 * Deliberately without [DayRange] or the fault chips [StockSheet] draws beside its own chart: both
 * are about the stock in general, and this section is answering one narrower question, about the
 * one trade the card is already about.
 */
@Composable
private fun PositionChartSection(position: Position, on: Color, history: List<DailySession>) {
    // Local to this card rather than `PageState.stockChartRange` - that state is written for the
    // one stock sheet the app ever has open at a time, and every position card on the grid needs
    // its own range and its own toggle, not one shared by all of them at once.
    var range by remember(position.id) { mutableStateOf(ChartRange.Default) }
    var showLevels by remember(position.id) { mutableStateOf(true) }
    // Measured back from the newest session the app holds, exactly as `StockSheet` does it - see
    // its own comment: a feed running behind should shorten the line, not draw an empty box.
    val visible = remember(history, range) {
        val anchor = history.lastOrNull()?.date ?: return@remember history
        history.filter { !it.date.isBefore(range.since(anchor)) }
    }
    val move = remember(visible) { visible.rangeMove() }
    val levels = if (showLevels) {
        ChartLevels(
            source = "your trade",
            stopLoss = position.stopLoss,
            // No entry and no paid line here - the ladder above and the Entry figure below already
            // say what was paid, and at entryLow == entryHigh == paid the chart drew two labels,
            // "entry X" and "you paid X", stacked on the exact same price. What is left to ask the
            // chart is whether the close is drifting toward the stop or running for a target, which
            // needs only the three levels still ahead of the trade.
            entryLow = null,
            entryHigh = null,
            target1 = position.target1,
            target2 = position.target2,
            paid = null,
        )
    } else {
        null
    }
    var touched by remember(visible) { mutableStateOf<DailySession?>(null) }

    Column(
        Modifier.padding(top = Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SheetSectionLabel("Price chart")
            // What the visible line adds up to, which is the one thing its shape cannot say.
            if (move != null) {
                Text(
                    formatPercent(move),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                    color = PriceRole.forReturn(move),
                )
            }
        }
        if (visible.count { it.close != null } > 1) {
            PriceChart(
                sessions = visible,
                levels = levels,
                // No call-date rings here: the one date this section is about is the entry already
                // marked in the levels above, not every call anybody made on the stock. Wiring the
                // report in for that is a bigger change than this one.
                calls = emptySet(),
                on = on,
                selected = touched,
                onSelect = { touched = it },
                height = PositionChartHeight,
            )
            // The readout takes the dates' own line rather than appearing above it, exactly as it
            // does in the sheet: a caption that arrived on touch would push the chart up under the
            // finger that asked for it.
            val reading = touched
            if (reading?.close != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        shortDate(reading.date) + " · " + formatPrice(reading.close),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    visible.moveTo(reading)?.let {
                        ChartCaption(formatPercent(it) + " since " + shortDate(visible.first().date))
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ChartCaption(shortDate(visible.first().date))
                    ChartCaption(shortDate(visible.last().date))
                }
            }
            if (showLevels) ChartCaption("levels from your trade")
        } else {
            // Absent rather than an empty box, which reads as a chart that failed to load. A week
            // of a stock that has barely traded is a real answer and this is what it looks like.
            ChartCaption("No sessions stored in this range.")
        }
        ChartControls(
            range = range,
            onRange = { range = it },
            levels = showLevels,
            onLevels = { showLevels = it },
        )
    }
}

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
private fun PositionView.profitLine(): String {
    val at = formatPrice(exitPrice)
    val line = when {
        // The blend is not a price anybody typed, so the parts come first and it follows as what
        // they came to. Printing it alone would report the trade as done at a price it never was.
        realized && soldInParts -> "Realized · ${position.partsLine()} · average $at"
        realized -> "Realized · sold at $at" + position.exitDate.dated()
        open -> "Estimated · marked at $at" + currentPriceOn.dated()
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
private fun Position.partsLine(): String {
    val first = formatPrice(exitSplitPct)
    val rest = formatPrice(FULL_SPLIT_PCT - (exitSplitPct ?: FULL_SPLIT_PCT))
    return "sold $first% at ${formatPrice(exitPrice1)}" + exitDate1.dated() +
        " and $rest% at ${formatPrice(exitPrice2)}" + exitDate.dated()
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

/**
 * A group on the 5.0 trade card: a rounded block of the page's own ground inside the white card,
 * as on the canvas, rather than the hairline the 4.x card ruled its groups apart with.
 */
@Composable
private fun Modifier.tradePanel(): Modifier =
    clip(Kit.PanelShape).background(palette.ground).padding(horizontal = Space.m, vertical = Space.s)

/**
 * The deadline as steps: one per session of the window, filled for those already traded. Says
 * the same thing as "N of M left" beside it, at a glance.
 */
@Composable
private fun DeadlineSteps(view: PositionView, modifier: Modifier = Modifier) {
    val total = view.position.windowSessions.coerceIn(1, MaxDeadlineSteps)
    val done = view.sessionsElapsed.coerceIn(0, total)
    val p = palette
    Row(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$done of $total sessions of the window traded" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(Kit.Capsule)
                    .background(if (i < done) p.ink else p.line),
            )
        }
    }
}

/** Past this many sessions the steps would be slivers; the figure beside them still says it. */
private const val MaxDeadlineSteps = 20

/** The status edge down the left of a trade card. */
