package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.PortfolioGroup
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.PortfolioStats
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.callIds
import com.ikverse.egxanalyzer.ui.theme.extraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What the user actually bought, and what it has done since.
 *
 * The other tabs are readings of the market; this is the only one holding anything of the user's
 * own, so every figure on it is measured from the prices they paid rather than from the levels a
 * channel printed. Positions are grouped by the session their recommendation was for, because that
 * is what dates them - not when the user got round to buying.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PortfolioScreen(appState: AppState) {
    val scope = rememberCoroutineScope()
    val portfolio = appState.portfolio

    Screen(
        title = "Portfolio",
        onRefresh = { scope.launch { appState.refreshPrices() } },
        refreshing = appState.pricesRefreshing,
    ) {
        if (portfolio.isEmpty) {
            EmptyState(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = "No trades recorded yet",
                detail = "Press Bought on a recommendation - on its card in Results, or in the " +
                    "sheet a table row opens - and the position appears here with its deadline, " +
                    "its targets and its running profit.",
            )
            return@Screen
        }

        // Above the record rather than below it: this is the one thing on the screen asking to be
        // acted on, and the record is reference. Built from the whole portfolio, so a date chosen
        // in the filter below cannot hide a trade that is late.
        OverdueCard(
            overdue = remember(portfolio) { overdueRoster(portfolio.positions) },
            onOpen = appState::openPosition,
        )

        // Under Overdue and above the record, which is where it belongs in the order those two
        // already establish: Overdue is the one thing here asking to be acted on, this is what
        // happened since the reader last looked, and the record underneath is reference. Built off
        // the whole portfolio like Overdue, so a date chosen in the filter below cannot hide an
        // event - a date picked on screen is a view of the trades, not a claim about the session.
        //
        // Held scope: on this tab the card reports the reader's own trades and nothing else. What
        // the channels' calls did that session is a question for Insights, where the same card
        // answers it in full.
        TodayCard(appState, heldOnly = true)

        PortfolioSummary(portfolio.stats)

        PositionSection(groups = portfolio.groups, appState = appState)
    }
}

/**
 * The record as a handful of figures.
 *
 * Percentages rather than pounds, and labelled as averages, because no trade size is recorded: a
 * total in money would be a number the app has no way of knowing. Adding sizes later fills these in
 * without moving anything on the screen.
 */
@Composable
private fun ColumnScope.PortfolioSummary(stats: PortfolioStats) {
    SectionCard(title = "Your record", icon = Icons.Outlined.AccountBalanceWallet) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            StatTile(stats.openCount.toString(), "open", tone = MaterialTheme.colorScheme.primary)
            // Settled rather than closed: it counts the expired trades too, and the card below this
            // one now keeps "closed" for the ones that ended somewhere in particular.
            StatTile(stats.settledCount.toString(), "settled")
            // No overdue tile. The card above this one names the trades themselves, in the same
            // view, and a count of the same thing beside it is a figure the reader has to
            // reconcile against a list that already says more than it does.
            StatTile(
                formatPercent(stats.settledReturnPct),
                "average settled",
                tone = PriceRole.forReturn(stats.settledReturnPct),
            )
            StatTile(
                formatPercent(stats.openReturnPct),
                "average open",
                tone = PriceRole.forReturn(stats.openReturnPct),
            )
            StatTile(
                formatPercent(stats.winRate, signed = false),
                "win rate",
                tone = if ((stats.winRate ?: 0.0) >= 50.0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        stats.best?.let { best ->
            Text(
                "Best ${best.ticker} ${formatPercent(best.returnPct)}" +
                    (stats.worst?.let { " · worst ${it.ticker} ${formatPercent(it.returnPct)}" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The trades that ran out of time, said before anything on this screen has been opened.
 *
 * Overdue is the only state in the app that asks the user for something, and until now it was only
 * ever found: a count in the record card, then a scroll through folded sessions looking for the
 * cards it was counting. Each tile carries what a decision needs - which stock, how late, and when
 * it was bought - and presses through to the trade itself.
 *
 * Absent entirely when nothing is late, exactly as the tile it replaces was. A card reading "no
 * overdue trades" is a permanent reminder of a state the app is not in.
 */
@Composable
private fun OverdueCard(overdue: List<PositionView>, onOpen: (String) -> Unit) {
    if (overdue.isEmpty()) return
    SectionCard(
        title = "Overdue",
        icon = Icons.Outlined.HourglassEmpty,
        about = infoNote(
            "Overdue",
            "Still running past their deadline because you chose to keep them open.",
        ),
    ) {
        BoxWithConstraints {
            // Tiles rather than full-width rows, through the same helper the position cards use:
            // two across on the cover screen, four on the Fold and the tablet. A trade that is late
            // is not more urgent for being drawn a screen wide.
            val columns = responsiveColumns(minColumnWidth = OverdueTileMinWidth, maxColumns = 4)
            Column {
                ResponsiveRows(overdue, columns, spacing = Space.s) { view, tileModifier ->
                    OverdueTile(
                        view = view,
                        onOpen = { onOpen(view.position.id) },
                        modifier = tileModifier,
                    )
                }
            }
        }
    }
}

/**
 * One late trade, in three facts and a press.
 *
 * The mark and the ticker have the top line to themselves, and everything measured sits under them.
 * Four things on one row is what the logo made impossible: the ticker was the only one of them that
 * could give way, so at four tiles across it wrapped a letter to a line. Nothing up there competes
 * for the width now, and the ticker is held to a single line so it cannot stack again whatever the
 * screen does.
 *
 * The day count carries no word beside it: "6d" in red under a heading that already says Overdue
 * cannot be read as anything else. Beside it the entry date, which is the one figure saying how
 * long the trade has actually been held - the session date the card opens into does not - and then
 * the return, in the same green or red it wears everywhere else in the app.
 *
 * The press is the trip a call in Insights already makes: [AppState.openPosition] hands the id to
 * the arrival effect in [PositionSection], which clears the date filter only if it is what hides
 * the trade, unfolds the session card, scrolls to it and flashes its edge. No second path to
 * maintain, and no way for the two entrances to disagree about where a trade is.
 */
@Composable
private fun OverdueTile(view: PositionView, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    // Read before the builder rather than inside it: the colour is a composable lookup and the line
    // it belongs to is plain text.
    val returnColor = PriceRole.forReturn(view.returnPct)
    val meta = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
            append("${view.overdueDays}d")
        }
        append(" · ${shortDate(view.position.entryDate)} · ")
        withStyle(SpanStyle(color = returnColor)) {
            append(formatPercent(view.returnPct))
        }
    }
    Card(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .semantics { onClick(label = "Open this trade", action = null) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = cardOutline,
        shape = MaterialTheme.shapes.medium,
    ) {
        // The measured line takes the tile's own width rather than the column beside the logo. The
        // logo is one line tall and that column was two, so it left a hole under the mark that the
        // eye reads before any of the figures; run under it, the line also gets the width back -
        // at two tiles across on the cover screen the return was being ellipsed away.
        Column(Modifier.padding(horizontal = Space.m, vertical = Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StockLogo(view.ticker, LogoSize.Row, Modifier.padding(end = Space.s))
                Text(
                    view.ticker,
                    // fill = false so the arrow sits against the end of the ticker rather than out
                    // at the tile's edge, where it reads as unrelated to it - the same way the
                    // arrows on the position card and the Insights call card sit.
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    // Named by the press it belongs to, one level up; a reader announcing the
                    // glyph as well would say it twice.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xs).size(IconSize.Hint),
                )
            }
            // Everything measured, on the line that has room for it. No word for the state:
            // every trade on this card is one the user is keeping open, so saying so would be
            // the card's own heading repeated on every tile. One line, ellipsis as the guard.
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The trades the card names, in the order it names them.
 *
 * Most overdue first, then by ticker, which is [PortfolioOrder.URGENT]s own rule for the positions
 * inside a section: a card leading with a different one would disagree with the list beneath it.
 * Read off the whole record rather than the filtered list - a date picked on the screen is a view
 * of the positions, not a statement about which of them are late, which is the same reason the
 * filter is kept out of `PortfolioCalculator` for the overdue notification.
 */
internal fun overdueRoster(positions: List<PositionView>): List<PositionView> =
    positions
        .filter(PositionView::overdue)
        .sortedWith(compareByDescending<PositionView> { it.overdueDays }.thenBy { it.ticker })

/**
 * The trades, one card per session the calls were made for.
 *
 * One card rather than the same date appearing under an open heading and a closed one: a day's
 * trades were a single decision, and reading how it went meant scrolling past everything else to
 * find its other half. Open, expired and closed are sections inside the card now, and every card
 * folds away exactly as a settled analysis does in Insights, so the record stays readable however
 * many sessions have been traded.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.PositionSection(groups: List<PortfolioGroup>, appState: AppState) {
    // Session-only, deliberately, and for the reason Results gives beside its own: a filter that
    // survived a restart would hide trades from someone who had forgotten it was on. The order is
    // kept instead - it hides nothing, so finding it as you left it costs a moment's thought rather
    // than a search for trades that look as though they have gone.
    var dateFilter by appState.pages.portfolioDate
    var stockFilter by appState.pages.portfolioStock
    val order = appState.appPreferences.portfolioOrder
    // Which session cards are open, held here rather than inside each card. A card cannot open
    // itself on someone else's behalf, and arriving from a call on the Insights tab has to open the
    // one holding that trade. Keyed by session date rather than by position in the list, so
    // re-sorting no longer moves which card is open onto whichever card took its place.
    var openGroups by appState.pages.openPortfolioGroups
    // Every call the record still holds, so a card knows whether it has anywhere to go back to.
    // Asked once per report: a trade whose analysis was deleted has no call left to open.
    val scoredCalls = remember(appState.performance) { appState.performance.callIds }

    // One card holding the whole section - its heading, the filters at the top of it, and every
    // session card inside. It was a loose heading, then a filter shelf, then a run of cards, all
    // sitting directly on the page, and the shelf's fill was close enough to a card's that the two
    // read as one background: the section had no edge of its own to say where it began or ended.
    // A card gives it one, and the filters are unambiguously *its* filters rather than something
    // floating above the next thing down.
    SectionCard(title = "Positions", icon = Icons.Outlined.AccountBalanceWallet) {
        if (groups.isEmpty()) {
            Text(
                "Nothing recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        val allDates = remember(groups) {
            groups.map { it.recommendationDate.toString() }.distinct().sortedDescending()
        }
        FilterBar(
            // The shell asks the same question to decide what a back press means, so the predicate
            // lives on PageState and both read it there. See PageState.filtersActive.
            active = appState.pages.filtersActive(AppDestination.PORTFOLIO),
            // The date alone. The search box is on show even on a cover screen, so a chip lit by it
            // would be reporting something the reader is already looking at.
            folded = dateFilter != null,
            onClearAll = { appState.pages.clearFilters(AppDestination.PORTFOLIO) },
            // Never folded away: it is the control someone arrives at the screen already knowing they
            // want, and the only one that can empty the list on a keystroke.
            search = { m -> StockFilterField(stockFilter, { stockFilter = it }, modifier = m) },
        ) {
            SingleSelectFilter(
                label = "dates",
                options = allDates,
                selected = dateFilter,
                onSelect = { dateFilter = it },
            )
            // Outside the clear-all, exactly as in Results: an order is not something a list can be
            // cleared of, and resetting it would look like a filter had gone missing.
            SortFilter(
                options = PortfolioOrder.entries,
                selected = order,
                label = PortfolioOrder::label,
                onSelect = appState::updatePortfolioOrder,
            )
        }

        // Filtered here rather than in the calculator. The whole record has other readers - the overdue
        // worker raises the daily reminder off it - and a date someone picked on this screen must not
        // silence a notification about a trade they scrolled past.
        val shown = remember(groups, dateFilter, stockFilter, order) {
            // Normalized once for the whole record rather than once per trade, through the rule the
            // other two tabs search by. See StockSearch.
            val wanted = StockSearch.query(stockFilter)
            groups
                .filter { dateFilter == null || it.recommendationDate.toString() == dateFilter }
                // A search narrows the trades inside a card and then empties the card itself, rather
                // than leaving a session heading standing over nothing. The summary line is read off
                // the positions, so it recounts itself around what is left and cannot end up
                // describing trades the search has taken away.
                .mapNotNull { group ->
                    val kept = group.positions.filter { it.matches(wanted) }
                    if (kept.isEmpty()) null else group.copy(positions = kept.sortedWith(order.positions))
                }
                .sortedWith(order.groups)
        }

        // Arriving from a call pressed on the Insights tab: open the session holding that trade and
        // scroll to it. Either filter is cleared only when it is what hides the trade, for the reason
        // Insights gives beside its own - a link that lands on an empty screen is a broken link, and a
        // filter thrown away on a trip the reader is about to make back is a filter they have to set
        // again.
        val pendingPosition = appState.pendingPositionId
        // The trade a Record sale action named. Read beside the reveal because the two
        // arrive together: that path sets both, so the card is found, flashed, and asked
        // for the price in one arrival.
        val pendingSell = appState.pendingSellPositionId
        val reveal = remember { BringIntoViewRequester() }
        LaunchedEffect(pendingPosition, groups, dateFilter, stockFilter) {
            if (pendingPosition == null) return@LaunchedEffect
            val target = groups.firstOrNull { group ->
                group.positions.any { it.position.id == pendingPosition }
            }
            if (target == null) {
                // The trade was deleted - here or on another device - between the press and the
                // arrival. Dropped rather than left waiting, or recording it again months later would
                // flash a card for a press nobody remembers making.
                appState.consumePendingPosition()
                return@LaunchedEffect
            }
            if (dateFilter != null && dateFilter != target.recommendationDate.toString()) {
                dateFilter = null
                // The list is about to be rebuilt around the cleared filter; this effect restarts on it.
                return@LaunchedEffect
            }
            // The search goes the same way as the date, and for the same reason: cleared when it is
            // what hides the trade, kept when the trade answers to it anyway.
            val held = target.positions.first { it.position.id == pendingPosition }
            if (!held.matches(StockSearch.query(stockFilter))) {
                stockFilter = ""
                return@LaunchedEffect
            }
            openGroups = openGroups + target.recommendationDate
            // The card is unfolding as this runs, and a scroll measured against a height it is about to
            // leave behind stops short of the trade that was asked for.
            delay(REVEAL_SETTLE_MS)
            reveal.bringIntoView()
        }

        if (shown.isEmpty()) {
            // The stock is named when it is what emptied the list, as it is in Results: "nothing called
            // on that date" beside a box holding COMI reads as though the app had not noticed what was
            // typed.
            val searching = stockFilter.isNotBlank()
            EmptyState(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = if (searching) {
                    "No trades in ${stockFilter.trim()}"
                } else {
                    "Nothing called on $dateFilter"
                },
                detail = if (searching) {
                    "Nothing you recorded is a holding in a stock by that code or name. Clear the " +
                        "stock filter to see the rest of your positions."
                } else {
                    "No trade you recorded belongs to that session. Clear the filter to see the " +
                        "rest of your positions."
                },
            )
            return@SectionCard
        }

        // Built once for the whole screen rather than per card. A fresh bundle each time would be a new
        // argument to every section below it, which is a list of positions redrawn on every
        // recomposition of the page around them.
        val jump = remember(appState, scoredCalls, pendingPosition, pendingSell, reveal) {
            PositionJump(
                scoredCalls = scoredCalls,
                // The trade being left is what back should come back to, so the press names it.
                // Both tabs key on ScoredCall.positionId, which is why one id serves as the call
                // to open and the trade to return to. See NavStop.
                onOpenCall = { id ->
                    appState.openCall(id, NavStop(AppDestination.PORTFOLIO, positionId = id))
                },
                revealPosition = pendingPosition,
                onRevealShown = appState::consumePendingPosition,
                reveal = reveal,
                sellPosition = pendingSell,
                onSellShown = appState::consumePendingSell,
            )
        }
        shown.forEach { group ->
            ExpandableSection(
                title = group.recommendationDate.toString(),
                icon = Icons.Outlined.AccountBalanceWallet,
                summaryContent = { GroupSummary(group) },
                // A card inside a card takes the step above its parent, exactly as the Overdue and
                // session-event tiles do. At the page's own `surfaceContainer` it would be the same
                // fill as the card holding it and would have no edge but its hairline.
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                // Every card starts folded, a session still running included. Once enough sessions
                // have been traded, the ones that opened themselves were most of the screen, and the
                // list of dates is what makes the record readable - the summary line says how each
                // session went, so nothing is hidden that a fold does not offer back in one tap.
                expandedState = group.recommendationDate in openGroups,
                onExpandedChange = { open ->
                    openGroups = if (open) {
                        openGroups + group.recommendationDate
                    } else {
                        openGroups - group.recommendationDate
                    }
                },
            ) {
                // Expired above closed: everything in it is a trade the app stopped tracking without
                // being told how it ended, which is the only part of a session still asking for
                // something. Closed is the record and goes last.
                PositionSubSection("Open", group.open, OpenTone, appState, jump)
                PositionSubSection("Expired", group.expired, ExpiredTone, appState, jump)
                PositionSubSection("Closed", group.closed, ClosedTone, appState, jump)
            }
        }
    }
}

/**
 * What a position card needs to lead back to the call it was taken on.
 *
 * Five parameters that always travel together, through two layers that do nothing with them but
 * hand them on. Bundled so those two layers say "the jump" rather than repeating the list, and
 * marked immutable so passing it down does not cost those layers the ability to skip.
 */
@Immutable
private class PositionJump(
    /** Every call the Insights record still holds, by trade key. */
    val scoredCalls: Set<String>,
    val onOpenCall: (String) -> Unit,
    /** The trade the reader has just pressed their way here from, if this is that arrival. */
    val revealPosition: String?,
    val onRevealShown: () -> Unit,
    val reveal: BringIntoViewRequester,
    /**
     * The trade a Record sale action in the shade named, whose dialog should open on arrival.
     *
     * Beside the reveal rather than folded into it: the two arrive together on that path - the card
     * is scrolled to and flashed *and* its dialog opens - but a press inside the app reveals without
     * selling, and one entrance that always did both would open a price field at every arrival.
     */
    val sellPosition: String?,
    val onSellShown: () -> Unit,
)

/**
 * One line naming the states a session's trades are in, so a folded card still informs.
 *
 * Every verdict is named and counted rather than the three sections alone: a session that ended on
 * its targets and one the stops took both read as "closed" from the outside, and telling those two
 * apart is worth more than the position count and the average that used to stand here. Each count
 * carries the colour of the chip on the trade itself, so the line and the cards under it say one
 * thing twice rather than two different things.
 *
 * Counted by each trade's own verdict, which is why a partial hit still running is a partial hit
 * here while still sitting under Open below: the count and the chip agree, and the sections answer
 * a different question - what can I still do about it - than the counts do.
 */
@Composable
private fun GroupSummary(group: PortfolioGroup) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        SummaryStates.forEach { (status, word) ->
            val held = group.positions.count { it.status == status }
            if (held == 0) return@forEach
            Text(
                "$held $word",
                style = MaterialTheme.typography.bodySmall,
                color = status.tone(),
            )
        }
    }
}

/**
 * The verdicts the summary names, in the order the card's own sections run.
 *
 * Open and expired lead because those are the two sections standing above the record, and the rest
 * are the ways a trade actually ended. Sold is here for the same reason the counts exist at all:
 * leave it out and a hand-sold trade vanishes from the line while still sitting in the card.
 *
 * Shorter words than the chips carry - "full hit" rather than "Full target hit" - because six of
 * these share one line under a date, and every one of them is already coloured as its own chip.
 */
private val SummaryStates = listOf(
    PositionStatus.OPEN to "open",
    PositionStatus.EXPIRED to "expired",
    PositionStatus.PARTIAL_TARGET_HIT to "partial hit",
    PositionStatus.FULL_TARGET_HIT to "full hit",
    PositionStatus.STOPPED_OUT to "stopped",
    PositionStatus.CLOSED_MANUALLY to "sold",
)

/**
 * One state's trades inside a session's card, under a label saying which state it is.
 *
 * Drawn only when it holds something: an empty "Expired" heading on a session that went perfectly
 * is three words of nothing, on every card, forever.
 */
@Composable
private fun ColumnScope.PositionSubSection(
    label: String,
    views: List<PositionView>,
    tone: Color,
    appState: AppState,
    jump: PositionJump,
) {
    if (views.isEmpty()) return
    Text(
        "$label · ${views.size}".uppercase(),
        Modifier.padding(top = Space.s),
        style = MaterialTheme.typography.labelSmall,
        color = tone,
    )
    PositionGrid(views, appState, jump)
}

/**
 * A colour per section, borrowed from the status chips rather than invented beside them.
 *
 * Open is the primary hue every running figure in the app uses, expired is the amber the palette
 * gained for exactly this, and closed is the muted one that says finished business. Read as
 * properties so they resolve against the theme in force, light or dark.
 */
private val OpenTone: Color
    @Composable get() = MaterialTheme.colorScheme.primary
private val ExpiredTone: Color
    @Composable get() = extraColors.expired
private val ClosedTone: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.PositionGrid(
    views: List<PositionView>,
    appState: AppState,
    jump: PositionJump,
) {
    BoxWithConstraints {
        val columns = responsiveColumns(minColumnWidth = PositionCardMinWidth, maxColumns = 2)
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            ResponsiveRows(views, columns) { view, cardModifier ->
                val revealed = view.position.id == jump.revealPosition
                PositionCard(
                    view = view,
                    // A trade whose analysis has since been deleted leads nowhere, so it does not
                    // answer a press: the call it was taken on is no longer in the record.
                    onOpenCall = if (view.position.id in jump.scoredCalls) {
                        { jump.onOpenCall(view.position.id) }
                    } else {
                        null
                    },
                    highlighted = revealed,
                    onHighlightShown = jump.onRevealShown,
                    startSelling = view.position.id == jump.sellPosition,
                    onSellingShown = jump.onSellShown,
                    onSell = { price, date -> appState.recordSale(view.position, price, date) },
                    onEditTrade = { price, date, window ->
                        // The dialog cannot confirm with an unparsable window while it is showing
                        // the field, so the fallback is only ever reached if it stops showing one.
                        appState.reprice(
                            view.position,
                            price,
                            date,
                            window ?: view.position.windowSessions,
                        )
                    },
                    onKeepOpen = { keep, note ->
                        appState.setKeepOpen(view.position, keep, note)
                    },
                    onRemove = { appState.deletePosition(view.position) },
                    modifier = if (revealed) {
                        cardModifier.bringIntoViewRequester(jump.reveal)
                    } else {
                        cardModifier
                    },
                )
            }
        }
    }
}

/**
 * What a position needs before two share a row.
 *
 * Set against the 750dp an unfolded Fold actually reports, not the 851dp an emulator claims: after
 * the rail and the page padding a generous-looking minimum quietly gives one column on the device
 * this was written for.
 */
private val PositionCardMinWidth = 300.dp

/**
 * Narrow enough for two tiles on the 411dp cover screen, which is the tightest this app is read on.
 *
 * The page holds its content in by [Space.l] and the card by another, so two tiles land at about
 * 165dp there - the same width they take on the Fold, where four fit across instead of two.
 */
private val OverdueTileMinWidth = 150.dp
