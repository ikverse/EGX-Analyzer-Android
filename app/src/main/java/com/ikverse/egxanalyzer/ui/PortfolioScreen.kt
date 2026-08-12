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
            // Only when there is one. A permanent "0 overdue" is a figure nobody reads, and this
            // tile earns its place by being unusual.
            if (stats.overdueCount > 0) {
                StatTile(
                    stats.overdueCount.toString(),
                    "overdue",
                    tone = MaterialTheme.colorScheme.error,
                )
            }
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
    var dateFilter by remember { mutableStateOf<String?>(null) }
    val order = appState.appPreferences.portfolioOrder
    // Which session cards are open, held here rather than inside each card. A card cannot open
    // itself on someone else's behalf, and arriving from a call on the Insights tab has to open the
    // one holding that trade. Keyed by session date rather than by position in the list, so
    // re-sorting no longer moves which card is open onto whichever card took its place.
    var openGroups by remember { mutableStateOf(emptySet<LocalDate>()) }
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
    FilterRow(active = dateFilter != null, onClearAll = { dateFilter = null }) {
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
    val shown = remember(groups, dateFilter, order) {
        groups
            .filter { dateFilter == null || it.recommendationDate.toString() == dateFilter }
            .map { it.copy(positions = it.positions.sortedWith(order.positions)) }
            .sortedWith(order.groups)
    }

    // Arriving from a call pressed on the Insights tab: open the session holding that trade and
    // scroll to it. The date filter is cleared only when it is what hides the trade, for the reason
    // Insights gives beside its own - a link that lands on an empty screen is a broken link, and a
    // filter thrown away on a trip the reader is about to make back is a filter they have to set
    // again.
    val pendingPosition = appState.pendingPositionId
    val reveal = remember { BringIntoViewRequester() }
    LaunchedEffect(pendingPosition, groups, dateFilter) {
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
        openGroups = openGroups + target.recommendationDate
        // The card is unfolding as this runs, and a scroll measured against a height it is about to
        // leave behind stops short of the trade that was asked for.
        delay(REVEAL_SETTLE_MS)
        reveal.bringIntoView()
    }

    if (shown.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.AccountBalanceWallet,
            title = "Nothing called on $dateFilter",
            detail = "No trade you recorded belongs to that session. Clear the filter to see the " +
                "rest of your positions.",
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
 * One line saying what a session's trades came to, so a folded card still informs.
 *
 * All three states are named and counted rather than two of them, so the line accounts for every
 * position in the card: a reader who wants to know how many expired should not have to subtract.
 * Each count carries its own state's colour, which is the same colour the section below it and the
 * chip on the trade itself are drawn in.
 */
@Composable
private fun GroupSummary(group: PortfolioGroup) {
    val counts = listOf(
        Triple(group.open.size, "open", OpenTone),
        Triple(group.expired.size, "expired", ExpiredTone),
        Triple(group.closed.size, "closed", ClosedTone),
    )
    val averageTone = PriceRole.forReturn(group.averageReturnPct)
    val line = buildAnnotatedString {
        val held = group.positions.size
        append("$held ${if (held == 1) "position" else "positions"}")
        counts.forEach { (size, word, tone) ->
            if (size == 0) return@forEach
            append(" · ")
            withStyle(SpanStyle(color = tone)) { append("$size $word") }
        }
        append(" · ")
        withStyle(SpanStyle(color = averageTone)) {
            append("average ${formatPercent(group.averageReturnPct)}")
        }
    }
    Text(
        line,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

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
                    Text(position.ticker, style = MaterialTheme.typography.titleSmall)
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
                    if (view.keptOpen) KeptOpenChip()
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
                    Cell("Deadline", view.deadline(), PriceRole.muted),
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
 * Says why a trade is still running after its deadline, next to how late it is.
 *
 * This carries the state on its own - there is no button repeating it. The instruction in it is the
 * point: the way to end a trade being kept open is to sell it, and the card's menu holds the way to
 * hand it back to its deadline instead.
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

/** One labelled figure, matching how Insights lays a call's numbers out. */
private data class Cell(val label: String, val value: String, val tone: Color)

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
                                style = MaterialTheme.typography.bodyMedium,
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
