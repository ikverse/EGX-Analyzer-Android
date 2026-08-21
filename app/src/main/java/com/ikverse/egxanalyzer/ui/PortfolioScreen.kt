package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    SectionCard(title = "Overdue", icon = Icons.Outlined.HourglassEmpty) {
        Text(
            "Still running past their deadline because you chose to keep them open.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        append(" · ${shortEntryDate(view.position.entryDate)} · ")
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
        // The logo leads the tile and both lines sit in one column beside it, which is the one
        // arrangement that gives this card a single left edge: with the logo inside the top row,
        // the measured line underneath it began at the card's padding while the ticker began past
        // the logo, and the two disagreed by the width of it.
        Row(
            Modifier.padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.Top,
        ) {
            StockLogo(view.ticker, LogoSize.Row, Modifier.padding(end = Space.s))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        view.ticker,
                        Modifier.weight(1f),
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
                        modifier = Modifier.padding(start = Space.xs).size(IconSize.Inline),
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

    // Set in like the page name above it: both are text on the page rather than in a card, and a
    // heading that starts left of the one above it is what makes a page look unaligned.
    Text(
        "Positions",
        Modifier.padding(start = PageTextInset),
        style = MaterialTheme.typography.titleLarge,
    )
    if (groups.isEmpty()) {
        Text(
            "Nothing recorded yet.",
            Modifier.padding(start = PageTextInset),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val allDates = remember(groups) {
        groups.map { it.recommendationDate.toString() }.distinct().sortedDescending()
    }
    FilterRow(
        active = dateFilter != null || stockFilter.isNotBlank(),
        onClearAll = {
            dateFilter = null
            stockFilter = ""
        },
    ) {
        // Search leads, as it does in Results: it is the control someone arrives at the screen
        // already knowing they want, and the only one that can empty the list on a keystroke.
        StockFilterField(value = stockFilter, onValueChange = { stockFilter = it })
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
        return
    }

    // Built once for the whole screen rather than per card. A fresh bundle each time would be a new
    // argument to every section below it, which is a list of positions redrawn on every
    // recomposition of the page around them.
    val jump = remember(appState, scoredCalls, pendingPosition, reveal) {
        PositionJump(
            scoredCalls = scoredCalls,
            onOpenCall = appState::openCall,
            revealPosition = pendingPosition,
            onRevealShown = appState::consumePendingPosition,
            reveal = reveal,
        )
    }
    shown.forEach { group ->
        ExpandableSection(
            title = group.recommendationDate.toString(),
            icon = Icons.Outlined.AccountBalanceWallet,
            summaryContent = { GroupSummary(group) },
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
 * A position card answers three questions in order: what did I buy, where is it now, and how long
 * is left. The status outline says the third at a glance before any of it is read.
 */
@Composable
private fun PositionCard(
    view: PositionView,
    /** Opens the call this trade was taken on, in Insights. Absent once its analysis is gone. */
    onOpenCall: (() -> Unit)?,
    highlighted: Boolean,
    onHighlightShown: () -> Unit,
    onSell: (Double, LocalDate) -> Unit,
    onEditTrade: (Double, LocalDate, Int?) -> Unit,
    onKeepOpen: (keep: Boolean, note: String?) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = view.position
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    // The arrival flash takes the edge for as long as it runs, then the status outline has it back.
    val border = arrivalFlash(highlighted, onHighlightShown) ?: heldBorder(view)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            // A fixed two lines for the name, so a company whose name wraps does not make its card
            // taller than the one beside it.
            Row(Modifier.heightIn(min = PositionHeaderHeight), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StockLogo(position.ticker, LogoSize.Row, Modifier.padding(end = Space.s))
                        Text(position.ticker, style = MaterialTheme.typography.titleSmall)
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
                }
                PositionStatusChip(view)
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit trade") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; editing = true },
                        )
                        // Undoing Keep Open lives here rather than beside Sold. The pill already
                        // says the trade is being kept open, and a button repeating it took the
                        // place where the user looks for the one action that ends a position. It
                        // has to stay reachable somewhere, though: without it a mistaken press
                        // could only be undone by deleting the trade and recording it again.
                        if (view.keptOpen) {
                            DropdownMenuItem(
                                text = { Text("Follow the deadline again") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.HourglassEmpty, contentDescription = null)
                                },
                                onClick = { menuOpen = false; onKeepOpen(false, null) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmRemove = true },
                        )
                    }
                }
            }

            // The line that names the call, which is exactly what a press on this card opens.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    listOfNotNull(
                        position.channel?.takeIf(String::isNotBlank),
                        "called ${position.recommendationDate}",
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

            // Its own row rather than the header, which is held to a fixed height so cards beside
            // each other start level. Drawn only when there is something to say, so an ordinary
            // position is exactly as tall as it was.
            if (view.overdue || view.keptOpen) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    if (view.overdue) OverdueChip(view.overdueDays)
                    // One chip, not two saying the same thing: a trade can only be overdue by being
                    // kept open now, so Overdue already carries the state and adds how late it is.
                    // The instruction the chip also held is not lost - the Sell button below is on
                    // the card for as long as no sale has been recorded.
                    if (view.keptOpen && !view.overdue) KeptOpenChip()
                    if (view.priceScaleChanged) PriceScaleChip()
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
            }

            FigureRow(
                listOf(
                    Cell("Entry", formatPrice(position.entryPrice), PriceRole.entry),
                    Cell("Entry date", position.entryDate.toString(), PriceRole.muted),
                    Cell("Market", formatPrice(view.currentPrice), PriceRole.market),
                    Cell(
                        if (view.realized) "Return" else "Return so far",
                        formatPercent(view.returnPct),
                        PriceRole.forReturn(view.returnPct),
                    ),
                ),
            )
            FigureRow(
                listOf(
                    Cell("Target 1", formatPrice(position.target1), PriceRole.target),
                    Cell("Target 2", formatPrice(position.target2), PriceRole.target),
                    Cell("Stop loss", formatPrice(position.stopLoss), PriceRole.stop),
                    Cell("Deadline", view.deadline(), PriceRole.muted, tabular = false),
                ),
            )

            HorizontalDivider()

            Text(
                view.profitLine(),
                style = MaterialTheme.typography.bodySmall,
                color = PriceRole.forReturn(view.returnPct),
            )
            // Selling early is the point of the button, so it stays available for as long as no
            // sale has been recorded - including on a trade that reached target 2, where recording
            // what the user actually got out at turns an estimate into a fact, and on one the
            // deadline closed while they were still holding it.
            if (view.awaitingSale) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                    modifier = Modifier.padding(top = Space.xs),
                ) {
                    // The estimate rather than today's close. While the trade is open the two are
                    // the same thing; once the deadline has closed it, today's price is the least
                    // likely figure the user sold at, and the estimate is already marked at the
                    // stop, the target, or the last close of the window.
                    SellButton(
                        suggestedExit = view.exitPrice ?: view.currentPrice,
                        onSell = onSell,
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
 * How long the recommendation has left, counted in the sessions it is judged in.
 *
 * Trading sessions rather than days, and counted from the session the call was made for: a stock
 * does not move at the weekend, and the user's entry date never enters into it. A window the user
 * set themselves says so here rather than in a chip of its own - the figure it qualifies is this
 * one, and a reader wondering why a trade has fifteen sessions is already looking at it.
 */
private fun PositionView.deadline(): String {
    val left = deadlineDate?.let { "passed $it" }
        ?: "$sessionsRemaining of ${position.windowSessions} left"
    return if (position.windowCustom) "$left · custom" else left
}

/**
 * Says why a trade is still running, on the trades where Overdue is not already saying it.
 *
 * Only up before the deadline passes: after it, Overdue means kept open and this would be the same
 * fact twice on one row. The instruction in it is the point while it is up: the way to end a trade
 * being kept open is to sell it, and the card's menu holds the way to hand it back to its deadline
 * instead.
 */
@Composable
private fun KeptOpenChip() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape) {
        Text(
            "Keep open · sell to close",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * The one line that says what the position is worth, and how much of that is a fact.
 *
 * A closed position the user reported selling is realized; everything else is an estimate, and
 * saying which is which matters more than the figure itself.
 */
private fun PositionView.profitLine(): String {
    val amount = formatPercent(returnPct)
    val at = formatPrice(exitPrice)
    return when {
        realized -> "Realized $amount · sold at $at" +
            (position.exitDate?.let { " on $it" } ?: "")
        open -> "Estimated $amount · marked at $at"
        status == PositionStatus.STOPPED_OUT -> "Estimated $amount · stopped at $at"
        status == PositionStatus.FULL_TARGET_HIT -> "Estimated $amount · target reached at $at"
        else -> "Estimated $amount · expired at $at"
    } + if (marketStatus != status) " · the call itself: ${marketStatus.label.lowercase()}" else ""
}

/**
 * One labelled figure, matching how Insights lays a call's numbers out.
 *
 * [tabular] is off only where the value is a sentence rather than a figure - the deadline reads
 * "3 of 10 left · custom", and monospacing prose sets it apart from the prices for no reason.
 */
private data class Cell(
    val label: String,
    val value: String,
    val tone: Color,
    val tabular: Boolean = true,
)

/**
 * Four figures across, or two when the width cannot take four.
 *
 * The same rule the Insights cards use: a cover screen has height and no width, so wrapping beats
 * shrinking - four columns at 443dp truncates every price they are supposed to show.
 */
@Composable
private fun FigureRow(cells: List<Cell>) {
    BoxWithConstraints {
        val perRow = if (maxWidth >= FourFiguresWidth) cells.size else 2
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            cells.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    row.forEach { cell ->
                        Column(Modifier.weight(1f)) {
                            Text(
                                cell.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                cell.value,
                                style = MaterialTheme.typography.bodyMedium.let {
                                    if (cell.tabular) it.copy(fontFamily = TabularFigures) else it
                                },
                                color = cell.tone,
                            )
                        }
                    }
                    repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private val FourFiguresWidth = 420.dp

/** Ticker plus two lines of company name, so every position card starts the same height. */
private val PositionHeaderHeight = 52.dp

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

/**
 * The entry date on an overdue tile, short enough to sit beside the two facts either side of it.
 *
 * `2026-08-14` was the longest thing on a row that already carries the day count and the state
 * word, and on a cover-screen tile it was what pushed that row into an ellipsis. `14 Aug` is the
 * same date in six characters, and the same shape Insights dates an outcome in.
 *
 * The year comes back only for a trade bought in another one, which is the only time its absence
 * can be read wrong. Every tile in a normal season stays at the short form.
 */
private fun shortEntryDate(date: LocalDate): String =
    if (date.year == LocalDate.now().year) {
        ShortDate.format(date)
    } else {
        ShortDateWithYear.format(date)
    }

private val ShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private val ShortDateWithYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yy")
