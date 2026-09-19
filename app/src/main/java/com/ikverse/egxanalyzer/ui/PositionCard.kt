package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import com.ikverse.egxanalyzer.model.Scoring
import java.time.LocalDate

/**
 * A position card answers three questions at a glance, and a fourth on request: what did I buy,
 * where is it now, and how long is left, plus - open the chart, and only then - how did it get
 * here. The status outline says the third of those before any of it is read.
 *
 * The prices are drawn before they are listed. Eight figures at equal weight said what every level
 * was and nothing about how they stood against each other - whether the stop was a whisker away or
 * a mile off, whether the price had crept most of the way to target 1 or none of it. [PriceLadder]
 * answers that for every trade on the tab, at no cost in height. On a running trade the header
 * slides a full [PriceChart] open beneath it on a press - the same drawing [StockSheet] uses, range
 * chips and all - which is the one thing the ladder cannot say: whether a close sitting near the
 * stop got there over three flat weeks or on one bad session. Hidden until asked for, because that
 * is a question worth a press rather than height every card on the tab spends whether or not it is
 * read. A trade with nothing left to run - sold, stopped, expired - opens no chart at all; there is
 * no "since" left for one to answer, and its outcome is already told in full by the figures below.
 *
 * Then two groups rather than two unlabelled rows - what the trade is, and where it stands. The
 * split is what lets each figure carry a line of its own underneath, a distance from the entry or
 * the session a high was set on, without the card reading as a table of ten loose numbers.
 */
@Composable
internal fun PositionCard(
    view: PositionView,
    appState: AppState,
    /** Opens the call this trade was taken on, in Insights. Absent once its analysis is gone. */
    onOpenCall: (() -> Unit)?,
    highlighted: Boolean,
    onHighlightShown: () -> Unit,
    onSell: (Sale) -> Unit,
    onEditTrade: (Double, LocalDate, Int?) -> Unit,
    onKeepOpen: (keep: Boolean, note: String?) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    /** True on the one card a Record sale action in the shade named. See `TradeStatusNotifier`. */
    startSelling: Boolean = false,
    onSellingShown: () -> Unit = {},
) {
    val position = view.position
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    // Hidden by default: the chart is the deeper "how did it get here", worth a press rather than
    // height every card on the tab spends whether or not it is read. Closed for a trade with
    // nothing left to run - see the chart section below.
    var chartExpanded by remember(position.id) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (chartExpanded) 180f else 0f, label = "chartChevron")

    // A **second-level** surface: it sits inside the session card, so it takes the container role's
    // own alpha - see GlassSection - and none of the lighting a card standing on the page gets.
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    // The arrival flash takes the edge for as long as it runs, then the status outline has it back.
    val border = arrivalFlash(highlighted, onHighlightShown) ?: heldBorder(view)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            // A fixed two lines for the name, so a company whose name wraps does not make its card
            // taller than the one beside it. Clickable on a running trade - anywhere outside the
            // ticker row's own press, which still opens the stock sheet - to slide the chart open
            // beneath it; a settled trade has no chart to open, so the header stays inert.
            Row(
                Modifier
                    .heightIn(min = PositionHeaderHeight)
                    .clickable(
                        enabled = view.open,
                        onClickLabel = if (chartExpanded) "Hide price chart" else "Show price chart",
                    ) { chartExpanded = !chartExpanded },
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    // One target for the logo and the ticker, as on the call card. The card
                    // itself carries no press, so this takes none away. See LocalOpenStock.
                    val openStock = LocalOpenStock.current
                    Row(
                        Modifier.clickable { openStock(position.ticker) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StockLogo(position.ticker, LogoSize.Row, Modifier.padding(end = Space.s))
                        Text(position.ticker, style = MaterialTheme.typography.titleSmall)
                        Egx33Badge(position.ticker, Modifier.padding(start = Space.s))
                    }
                    listOfNotNull(position.companyArabic, position.companyEnglish)
                        .filter(String::isNotBlank)
                        .distinct()
                        .takeIf(List<String>::isNotEmpty)
                        ?.let {
                            Text(
                                it.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    // Every pill on this card, still in the one row the card says them all in -
                    // moved up into the header itself, so identity and status read as one block
                    // above the rule that now separates them from the trade's own facts below it.
                    // Scrolls sideways rather than wrapping to a second line when it does not fit -
                    // the same `scrollableRow` a chip row already uses in the stock sheet's own
                    // chart controls, and for the same reason: a card carrying five chips at once
                    // (status, T+1, overdue, kept open, price scale) next to a ticker and a badge
                    // has nowhere to wrap to without pushing the company name down.
                    //
                    // Every chip inside is named in the condition. Price scale was not, and a split
                    // under a trade that was neither overdue nor kept open had its chip written and
                    // never drawn.
                    Row(
                        Modifier.padding(top = Space.xs).scrollableRow(),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        // Where the trade stands leads, because it is the one fact here that is
                        // true of every trade and the one the rest of the row qualifies.
                        PositionStatusChip(view)
                        // Then the fact that was true the day the trade was taken, which is what
                        // the deadline further down the card is measured by.
                        if (position.isTPlusOne) TPlusOneChip(position)
                        if (view.overdue) OverdueChip(view.overdueDays)
                        // One chip, not two saying the same thing: a trade can only be overdue by
                        // being kept open now, so Overdue already carries the state and adds how
                        // late it is. The instruction the chip also held is not lost - the Sell
                        // button below is on the card for as long as no sale has been recorded.
                        if (view.keptOpen && !view.overdue) KeptOpenChip()
                        if (view.priceScaleChanged) PriceScaleChip()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Points at what the header's own press does, and flips with it - the same
                    // "arrow that flips with the section" a `DisclosureButton` draws, borrowed here
                    // because the trigger is the header itself rather than a button of its own.
                    if (view.open) {
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(end = Space.xs)
                                .size(IconSize.Inline)
                                .rotate(chevronRotation),
                        )
                    }
                    Box {
                        MoreButton(onClick = { menuOpen = true })
                        AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        AppMenuItem(
                            "Edit trade",
                            Icons.Outlined.Edit,
                            onClick = { menuOpen = false; editing = true },
                        )
                        // Undoing Keep Open lives here rather than beside Sold. The pill already
                        // says the trade is being kept open, and a button repeating it took the
                        // place where the user looks for the one action that ends a position. It
                        // has to stay reachable somewhere, though: without it a mistaken press
                        // could only be undone by deleting the trade and recording it again.
                        if (view.keptOpen) {
                            AppMenuItem(
                                "Follow the deadline again",
                                Icons.Outlined.HourglassEmpty,
                                onClick = { menuOpen = false; onKeepOpen(false, null) },
                            )
                        }
                        // The one press on this card that cannot be undone, and until now the one
                        // press drawn in exactly the ink of Edit above it.
                        AppMenuItem(
                            "Remove",
                            Icons.Outlined.Delete,
                            onClick = { menuOpen = false; confirmRemove = true },
                            destructive = true,
                        )
                    }
                    }
                }
            }

            // Separates identity and status, read together above it, from the trade's own facts
            // below - the same rule the two figure groups further down are already ruled apart by.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Slides open under the header on a running trade - see the header's own `clickable`.
            // Hidden by default, because the ladder below already answers "where do the levels
            // sit" for every trade on the tab at no cost in height; this is the deeper "how did it
            // get here" a reader presses for. A settled trade never gets one: `chartExpanded` can
            // only be true on a trade that was open when it was pressed, and `view.open` is checked
            // again here rather than trusted, since a trade can settle while its card is expanded.
            if (view.open) {
                AnimatedVisibility(
                    visible = chartExpanded,
                    enter = expandVertically(clip = false) + fadeIn(),
                    exit = shrinkVertically(clip = false) + fadeOut(),
                ) {
                    PositionChartSection(appState, position, colors.containerColor)
                }
            }

            // The line that names the call, which is exactly what a press on this card opens. The
            // date is the app's own short form rather than the raw ISO one this line used to
            // print: the tiles on the Overdue card above already date a trade "14 Aug", and this
            // was the only card in the app where two dates disagreed about how to look.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    listOfNotNull(
                        position.channel?.takeIf(String::isNotBlank),
                        "called ${shortDate(position.recommendationDate)}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // fill = false so the arrow sits against the end of the line rather than out at
                    // the card's edge, where it would read as unrelated to it.
                    modifier = Modifier.weight(1f, fill = false),
                )
                // The one hint that the card leads somewhere: a whole card being pressable is
                // invisible otherwise.
                if (onOpenCall != null) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        // The press is described where it is declared; a reader announcing the
                        // glyph as well would say it twice.
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Inline),
                    )
                }
            }

            position.keepOpenNote?.takeIf(String::isNotBlank)?.let { why ->
                Text(
                    why,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            PriceLadder(
                stopLoss = position.stopLoss,
                // One price, not a band. The trade opened where it opened, and the drawing keeps a
                // zero-width band visible rather than losing the mark; the levels either side of it
                // are still the call's, which is what makes the picture worth reading at all.
                entryLow = position.entryPrice,
                entryHigh = position.entryPrice,
                target1 = position.target1,
                target2 = position.target2,
                // The arrow is where this trade stands: today's close while it runs, and where it
                // ended once it has. Nothing is plotted across a change of scale - the levels are
                // quoted in the old money and the price in the new, so the arrow would point at a
                // place on the axis that does not exist.
                reached = if (view.priceScaleChanged) null else view.exitPrice ?: view.currentPrice,
            )

            FigureGroup(
                // On the heading rather than in a figure of its own, because it is not a fifth
                // level - it is what the four below come to. Worked out from the price actually
                // paid, which is what makes it a different figure from the one Insights prints for
                // the same call: buying above the band buys a worse trade out of the same advice.
                "Your trade" + (view.riskReward?.let { " · risk : reward ${it.asRatio()}" } ?: ""),
                listOf(
                    {
                        Figure(
                            "Entry",
                            formatPrice(position.entryPrice),
                            Modifier.weight(1f),
                            tone = PriceRole.entry,
                            // The one date that says how long the trade has actually been held.
                            // The session this card sits under is titled by the call's date, and on
                            // a trade bought late the two are not the same day.
                            caption = "bought ${shortDate(position.entryDate)}",
                        )
                    },
                    // A price on its own says nothing across stocks: 7.95 is a wide stop on one
                    // share and a tight one on another, and the distance is the half that compares.
                    // Measured from what was paid, so it is the room this trade actually has left.
                    {
                        Figure(
                            "Stop loss",
                            formatPrice(position.stopLoss),
                            Modifier.weight(1f),
                            tone = PriceRole.stop,
                            caption = view.fromEntry(position.stopLoss).distance(),
                        )
                    },
                    {
                        Figure(
                            "Target 1",
                            formatPrice(position.target1),
                            Modifier.weight(1f),
                            tone = PriceRole.target,
                            caption = view.fromEntry(position.target1).distance(),
                        )
                    },
                    {
                        Figure(
                            "Target 2",
                            formatPrice(position.target2),
                            Modifier.weight(1f),
                            tone = PriceRole.target,
                            caption = view.fromEntry(position.target2).distance(),
                        )
                    },
                ),
            )
            // The heading below is a boundary the eye loses once a group wraps onto three rows, so
            // the two groups are ruled apart, exactly as they are on the call card in Insights.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FigureGroup(
                "Where it stands",
                listOf(
                    {
                        Figure(
                            if (view.realized) "Return" else "Return so far",
                            formatPercent(view.returnPct),
                            Modifier.weight(1f),
                            tone = PriceRole.forReturn(view.returnPct),
                        )
                    },
                    {
                        Figure(
                            // "Last close" rather than "Market". The daily feed settles once a day
                            // and can be several sessions behind on a phone that has not
                            // refreshed; undated, the figure claimed to be today's, which is the
                            // one thing it is not.
                            "Last close",
                            formatPrice(view.currentPrice),
                            Modifier.weight(1f),
                            tone = PriceRole.market,
                            // Through the card's own short date rather than the shared `on` slot,
                            // so every date on this card follows one rule: the year comes back for
                            // a session in another one, and stays away for all the rest.
                            caption = view.currentPriceOn?.let(::shortDate),
                        )
                    },
                    // How far the trade actually got, which this card could not say at all. The
                    // scorer has always worked both out across the held sessions and they were
                    // thrown away, so a trade that ran up 7% and gave it all back read exactly
                    // like one that never moved.
                    {
                        Figure(
                            "Peak since entry",
                            formatPrice(view.peakSinceEntry),
                            Modifier.weight(1f),
                            tone = PriceRole.market,
                            caption = view.extremeCaption(view.peakSinceEntry, view.peakOn),
                        )
                    },
                    {
                        Figure(
                            "Trough since entry",
                            formatPrice(view.troughSinceEntry),
                            Modifier.weight(1f),
                            tone = PriceRole.market,
                            caption = view.extremeCaption(view.troughSinceEntry, view.troughOn),
                        )
                    },
                    {
                        Figure(
                            "Deadline",
                            view.deadline(),
                            Modifier.weight(1f),
                            tone = PriceRole.muted,
                            // Sessions held, not sessions elapsed: the deadline counts from the
                            // call and this counts from the entry, and on a trade bought late the
                            // two are answering different questions about one window.
                            caption = "${view.sessionsHeld} ${view.sessionsHeld.sessionWord()} held",
                            // Prose rather than a price. "3 of 10 left" in monospaced digits sets
                            // it apart from the figures beside it for no reason.
                            valueStyle = MaterialTheme.typography.bodyMedium,
                        )
                    },
                ),
            )

            Text(
                view.profitLine(),
                style = MaterialTheme.typography.bodySmall,
                // The figure this line used to carry is a figure above it now, and the colour went
                // with it. What is left says whether the return is a fact or an estimate and where
                // it was struck, and that is not in itself good news or bad.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Selling early is the point of the button, so it stays available for as long as no
            // sale has been recorded - including on a trade that reached target 2, where recording
            // what the user actually got out at turns an estimate into a fact, and on one the
            // deadline closed while they were still holding it.
            if (view.awaitingSale) {
                // The rule a call card ends with, in the one place it means the same thing: what is
                // above it is the trade as it stands, and what is below it is what can be done about
                // it. It used to sit a line higher, above the estimate - which reads as a figure and
                // belongs with the figures it qualifies - and that left the pills ending the card
                // with nothing between them and the record. Inside the `if`, so a settled trade with
                // nothing to press does not finish on a rule under no buttons.
                HorizontalDivider()
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    // The estimate rather than today's close. While the trade is open the two are
                    // the same thing; once the deadline has closed it, today's price is the least
                    // likely figure the user sold at, and the estimate is already marked at the
                    // stop, the target, or the last close of the window.
                    SellButton(
                        held = view,
                        suggestedExit = view.exitPrice ?: view.currentPrice,
                        onSell = onSell,
                        openNow = startSelling,
                        onOpened = onSellingShown,
                    )
                    // Not on a trade already being kept open - the pill says that, and the menu
                    // undoes it - and not on one that reached target 2, which is the single ending
                    // Keep Open cannot argue with.
                    if (!view.keptOpen && !view.finished) KeepOpenButton(onKeepOpen = onKeepOpen)
                }
            }
        }
    }

    // Two overloads over one body rather than a clickable wrapped round the card: Material's own
    // pressable card is what keeps the ripple inside the corners, and a trade whose call is gone
    // must not answer a press at all. The menu, Sold and Keep Open take their own taps as before.
    if (onOpenCall == null) {
        Card(
            modifier.fillMaxWidth(),
            colors = colors,
            border = border,
            shape = MaterialTheme.shapes.medium,
            content = body,
        )
    } else {
        Card(
            onClick = onOpenCall,
            // A pressable card announces itself as "activate" and nothing more, which says nothing
            // about where the press goes. The action itself is Material's; only its name is ours.
            modifier = modifier.fillMaxWidth()
                .semantics { onClick(label = "Open this call in Insights", action = null) },
            colors = colors,
            border = border,
            shape = MaterialTheme.shapes.medium,
            content = body,
        )
    }

    if (editing) {
        TradeDialog(
            title = "Edit the trade",
            explanation = "Corrects what this trade was recorded at, and how long it runs. " +
                "Everything the position reports is measured from the entry; changing the window " +
                "moves the deadline, so it can close a running trade or reopen a finished one.",
            priceLabel = "Entry price",
            dateLabel = "Entry date",
            confirmLabel = "Save",
            initialPrice = position.entryPrice,
            initialWindow = position.windowSessions,
            windowHelp = "Trading sessions from ${position.recommendationDate}, the session this " +
                "call was made for.",
            onDismiss = { editing = false },
            onConfirm = { price, date, window ->
                editing = false
                onEditTrade(price, date, window)
            },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            containerColor = Glass.solid(MaterialTheme.colorScheme.surfaceContainerHigh),
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this position?") },
            text = {
                Text(
                    "It stops being counted in your portfolio. The analysis it came from is not " +
                        "touched, so the recommendation itself stays where it is.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onRemove()
                    },
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
        )
    }
}

/**
 * Six months of closes for one ticker, fetched once per card rather than held on [PositionView].
 *
 * Off the disk exactly as [StockSheet] reads it - a local read through [AppState.priceHistory],
 * no network - and only for a card that actually needs it: every other position on the tab keeps
 * whatever it already had, rather than every card on the grid paying for a query at once. The
 * widest range rather than the default one, for the same reason [StockSheet] fetches it once: the
 * chart's own range chips slice this in the composition, so no press ever waits on a second query.
 */
@Composable
private fun rememberPositionHistory(appState: AppState, ticker: String): List<DailySession> {
    val key = remember(ticker) { Scoring.normalizeTicker(ticker) }
    var history by remember(key) { mutableStateOf(emptyList<DailySession>()) }
    LaunchedEffect(key) {
        history = appState.priceHistory(key, ChartRange.Widest.since(LocalDate.now()))
    }
    return history
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
private fun PositionChartSection(appState: AppState, position: Position, on: Color) {
    val history = rememberPositionHistory(appState, position.ticker)
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
            // One price, not a band - the same reasoning PriceLadder draws this way: the trade
            // opened where it opened, and the levels either side of it are still the call's.
            entryLow = position.entryPrice,
            entryHigh = position.entryPrice,
            target1 = position.target1,
            target2 = position.target2,
            paid = position.entryPrice,
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
    OutlinePill(
        "T+1",
        outline = MaterialTheme.colorScheme.outline,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = { showing = true },
    )
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
        AlertDialog(
            containerColor = Glass.solid(MaterialTheme.colorScheme.surfaceContainerHigh),
            onDismissRequest = { showing = false },
            title = { Text("${position.ticker} · a T+1 trade") },
            text = { Text("$call $deadline") },
            confirmButton = {
                TextButton(onClick = { showing = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun KeptOpenChip() {
    OutlinePill(
        "Keep open · sell to close",
        outline = MaterialTheme.colorScheme.tertiary,
        textColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
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
private val PositionHeaderHeight = 52.dp

/**
 * Shorter than [StockSheet]'s 220dp - that height was tuned for a sheet with nothing else on the
 * page; this chart shares a card with two figure groups and a menu's worth of chrome besides, and
 * a running position can carry as many as five levels within a few percent of each other. 180dp
 * gives that cluster a full row before crowding the way the sheet's own 150dp used to.
 */
private val PositionChartHeight = 180.dp
