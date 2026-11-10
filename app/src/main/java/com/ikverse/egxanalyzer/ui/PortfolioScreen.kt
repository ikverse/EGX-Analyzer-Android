package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.height
import com.ikverse.egxanalyzer.ui.kit.popIn
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.Skeleton
import androidx.compose.foundation.layout.PaddingValues
import com.ikverse.egxanalyzer.ui.kit.Tile
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import com.ikverse.egxanalyzer.ui.theme.palette
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.SplitColumns
import com.ikverse.egxanalyzer.model.FoldPosture
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.theme.CardHue
import com.ikverse.egxanalyzer.ui.theme.color
import com.ikverse.egxanalyzer.model.PortfolioGroup
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.PortfolioStats
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.callIds
import com.ikverse.egxanalyzer.ui.theme.extraColors
import kotlinx.coroutines.delay

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
    val portfolio = appState.portfolio

    Screen(
        appState = appState,
        destination = AppDestination.PORTFOLIO,
        onRefresh = { appState.startAction { appState.refreshPrices() } },
        refreshing = appState.pricesRefreshing,
    ) {
        // Above the early return below, which is the whole reason it is composed this high up: the
        // filter icon in the page header is there whether or not a single trade has been recorded,
        // and a sheet that composed only for a record with trades in it would be an icon that
        // opened nothing. With nothing recorded the dates section drops itself and the sheet is the
        // order alone. See FilterSheet.
        //
        // **Titled `Filter positions`, and that is not decoration.** The date and the order narrow
        // the Positions card and nothing else: Your record and Overdue are built from the whole
        // portfolio on purpose, so a date picked here cannot hide a trade that is late. The shelf
        // this replaced said as much by sitting inside the card it filtered; a sheet opened from
        // the page header would be claiming the page, so the title is what says it now.
        PositionFilterSheet(portfolio.groups, appState)

        if (portfolio.isEmpty) {
            if (!appState.initialDataLoaded) {
                // Placeholders in the shape of the tiles that are coming, so nothing jumps when
                // the record arrives.
                Skeleton(Modifier.fillMaxWidth().height(200.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                    Skeleton(Modifier.weight(1f).height(160.dp))
                    Skeleton(Modifier.weight(1f).height(160.dp))
                }
                Skeleton(Modifier.fillMaxWidth().height(240.dp))
                return@Screen
            }
            Tile(modifier = Modifier.fillMaxWidth().popIn(0), tone = TileTone.HIGHLIGHT) {
                Text("No trades recorded yet", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Press I bought it on a recommendation - on its card in Results, or in the " +
                        "sheet a table row opens - and the position appears here with its deadline, " +
                        "its targets and its running return.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Space.s),
                )
            }
            return@Screen
        }

        // **The 5.0 page**: tiles first (the hero, hit rate, what needs you, open positions, today,
        // best source, the AI's last read, closed this month), then the full record. The tiles are
        // built from the whole portfolio, so a date chosen in the filter cannot hide a trade that is
        // late or open; the filter narrows the record underneath, as it always did.
        val posture = LocalFoldPosture.current
        var selected by appState.pages.portfolioSelectedTrade
        when (posture) {
            is FoldPosture.Unfolded, is FoldPosture.Book -> {
                // Side by side: the tiles on one half, the trade a row was pressed on and the record
                // on the other. In book posture the split falls on the hinge.
                SplitColumns(posture, unfoldedStart = 0.5f, start = {
                        PortfolioTiles(appState, portfolio, heroHeight = null, onSelectTrade = { selected = it })
                    }, end = {
                        val picked = selected?.let { id -> portfolio.positions.firstOrNull { it.position.id == id } }
                        if (picked != null) {
                            SelectedTrade(picked, appState, onClose = { selected = null })
                        }
                        PositionSection(groups = portfolio.groups, appState = appState)
                    })
            }
            else -> {
                // On the cover screen, and in tabletop, one column. In tabletop the hero ends at the
                // crease, so its chart stands on the upright half and everything pressed is on the
                // flat one.
                val heroHeight = (posture as? FoldPosture.Tabletop)?.let { tabletopHeroHeight(it) }
                PortfolioTiles(appState, portfolio, heroHeight = heroHeight, onSelectTrade = appState::openPosition)
                PositionSection(groups = portfolio.groups, appState = appState)
            }
        }
    }
}

/**
 * The hero's height in tabletop: from the top of the scrolling area to just above the crease.
 * Never under a usable minimum, so a window with a hinge unusually high still draws a chart.
 */
@Composable
internal fun tabletopHeroHeight(posture: FoldPosture.Tabletop): Dp {
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val top = LocalViewportTop.current
    val crease = windowHeight * posture.hingeStartFraction
    return with(density) { (crease - top).toDp() - Space.l - Space.s }.coerceAtLeast(220.dp)
}

/**
 * The trade a row was pressed on, unfolded, above the record: its full card, with a way to put it
 * away. The same card and the same actions as in the record below.
 */
@Composable
private fun SelectedTrade(view: PositionView, appState: AppState, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Selected trade", style = MaterialTheme.typography.titleSmall, color = palette.muted, modifier = Modifier.weight(1f))
        KitButton("Close", onClick = onClose, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
    }
    PositionCard(
        view = view,
        appState = appState,
        onOpenCall = { appState.openCall(view.position.id, NavStop(AppDestination.PORTFOLIO, positionId = view.position.id)) },
        highlighted = false,
        onHighlightShown = {},
        startSelling = false,
        onSellingShown = {},
        onSell = { sale -> appState.recordSale(view.position, sale) },
        onEditTrade = { price, date, window ->
            appState.reprice(view.position, price, date, window ?: view.position.windowSessions)
        },
        onKeepOpen = { keep, note -> appState.setKeepOpen(view.position, keep, note) },
        onRemove = {
            onClose()
            appState.deletePosition(view.position)
        },
        onReopen = { appState.clearSale(view.position) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * What narrows the Positions card, in the sheet the page header's filter icon opens.
 *
 * Separate from [PositionSection] only because that section is drawn below an early return for an
 * empty record and this cannot be - see the call site. It reads the same `PageState` entries the
 * section reads, so the two cannot drift.
 */
@Composable
private fun PositionFilterSheet(groups: List<PortfolioGroup>, appState: AppState) {
    var dateFilter by appState.pages.portfolioDate
    val allDates = remember(groups) {
        groups.map { it.recommendationDate.toString() }.distinct().sortedDescending()
    }
    FilterSheet(
        open = appState.pages.portfolioFiltersOpen,
        // The shell asks the same question to decide what a back press means, so the predicate
        // lives on PageState and both read it there. See PageState.filtersActive.
        active = appState.pages.filtersActive(AppDestination.PORTFOLIO),
        onClearAll = { appState.pages.clearFilters(AppDestination.PORTFOLIO) },
        title = "Filter positions",
    ) {
        DateFilterSection(
            label = "Dates",
            dates = allDates,
            selected = dateFilter,
            onSelect = { dateFilter = it },
        )
        // Below the rule, outside the clear-all, exactly as in Results: an order is not something a
        // list can be cleared of, and resetting it would look like a filter had gone missing.
        SortSection(
            options = PortfolioOrder.entries,
            selected = appState.appPreferences.portfolioOrder,
            label = PortfolioOrder::label,
            onSelect = appState::updatePortfolioOrder,
        )
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

    // One card holding the whole section - its heading and every session card inside. It was a
    // loose heading, then a filter shelf, then a run of cards, all sitting directly on the page,
    // and the shelf's fill was close enough to a card's that the two read as one background: the
    // section had no edge of its own to say where it began or ended. A card gives it one. The
    // filters themselves have left for the sheet above, which is titled after this card for the
    // reason written there.
    // The one card on the tab whose children are cards, so it holds them in by Space.s rather than
    // the Space.l a card of controls uses. Three frames stood between the page and a trade - this
    // card, the session card, the trade card - each paying a full inset and each drawing its own
    // hairline, which left the trade 285dp of a 411dp cover screen. See SectionCard.contentInset.
    // The record, in a plain card tile since 5.0: a heading and every session card inside it.
    Tile(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.s)) {
        Text(
            "All positions",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.s),
        )
        if (groups.isEmpty()) {
            Text(
                "Nothing recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Tile
        }

        // Filtered here rather than in the calculator. The whole record has other readers - the overdue
        // worker raises the daily reminder off it - and a date someone picked on this screen must not
        // silence a notification about a trade they scrolled past.
        val shown = remember(groups, dateFilter, stockFilter, order) {
            // Normalized once for the whole record rather than once per trade, through the rule the
            // other two tabs search by. See StockSearch.
            val wanted = StockSearch.query(stockFilter)
            groups
                .filter { dateFilter.accepts(it.recommendationDate.toString()) }
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
        val settled = LocalTabsSettled.current
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
            if (!dateFilter.accepts(target.recommendationDate.toString())) {
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
            reveal.revealIfOnScreen(appState, AppDestination.PORTFOLIO, settled)
        }

        if (shown.isEmpty()) {
            // The stock is named when it is what emptied the list, as it is in Results: "nothing called
            // on that date" beside a box holding COMI reads as though the app had not noticed what was
            // typed.
            val searching = stockFilter.isNotBlank()
            EmptyState(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = if (searching) {
                    // The company rather than the code, as Results says it: the filter holds a
                    // listing the reader picked. See TickerPicker.name.
                    "No trades in ${TickerPicker.name(stockFilter)}"
                } else {
                    "Nothing called ${dateFilter?.describe().orEmpty()}"
                },
                detail = if (searching) {
                    "Nothing you recorded is a holding in that stock. Clear the stock filter to " +
                        "see the rest of your positions."
                } else {
                    "No trade you recorded belongs to that session. Clear the filter to see the " +
                        "rest of your positions."
                },
                actionLabel = "Clear filters",
                onAction = { appState.pages.clearFilters(AppDestination.PORTFOLIO) },
            )
            return@Tile
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
                // Its children are the trade cards, so it holds them in by the same Space.s the
                // card around it uses, and for the same reason.
                contentInset = Space.s,
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
                showAccentEdge = false,
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
    // One position card per row, on every width - a two-column grid here used to leave a card,
    // its two figure panels included, about 300dp to draw itself in once the Fold opened; a
    // single full-width card spends that width instead, inside PositionCard's own width check.
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        views.forEach { view ->
            val revealed = view.position.id == jump.revealPosition
            PositionCard(
                view = view,
                appState = appState,
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
                onSell = { sale -> appState.recordSale(view.position, sale) },
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
                onReopen = { appState.clearSale(view.position) },
                modifier = if (revealed) {
                    Modifier.fillMaxWidth().bringIntoViewRequester(jump.reveal)
                } else {
                    Modifier.fillMaxWidth()
                },
            )
        }
    }
}
