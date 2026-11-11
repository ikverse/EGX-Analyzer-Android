package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.model.NeedReason
import com.ikverse.egxanalyzer.model.Portfolio
import com.ikverse.egxanalyzer.model.PortfolioGroup
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.callIds
import com.ikverse.egxanalyzer.model.nearLevel
import com.ikverse.egxanalyzer.model.needsYou
import com.ikverse.egxanalyzer.model.plainWord
import com.ikverse.egxanalyzer.model.recordedSale
import com.ikverse.egxanalyzer.ui.kit.HoldMenu
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitChip
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.MoreRow
import com.ikverse.egxanalyzer.ui.kit.Skeleton
import com.ikverse.egxanalyzer.ui.kit.SplitColumns
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.popIn
import com.ikverse.egxanalyzer.ui.theme.palette
import kotlinx.coroutines.delay

/** Which of the Portfolio's lists the chips show: a view, like the sort order, never a filter. */
internal enum class TradeScope { OPEN, FINISHED, ALL }

/** Which trade dialog is open. */
internal enum class TradeDialogKind { SELL, EDIT, EDIT_SALE, KEEP_OPEN }

/** The trade dialog that is open, and on which trade. Held on PageState so a fold keeps it. */
internal data class TradeDialogRequest(val positionId: String, val kind: TradeDialogKind)

/** How the trade lists are drawn: as cards, or as rows in book posture's narrow list. */
internal enum class TradeListLayout { CARDS, COMPACT }

/**
 * What the user actually bought, and what it has done since.
 *
 * The other tabs are readings of the market; this is the only one holding anything of the user's
 * own, so every figure on it is measured from the prices they paid rather than from the levels a
 * channel printed. Since 5.1 the trades are two flat lists - Open now, then Finished - rather than
 * one card per session; the date filter and the three orders still apply to them.
 */
@Composable
internal fun PortfolioScreen(appState: AppState) {
    val portfolio = appState.portfolio

    Screen(
        appState = appState,
        destination = AppDestination.PORTFOLIO,
        onRefresh = { appState.startAction { appState.refreshPrices() } },
        refreshing = appState.pricesRefreshing,
    ) {
        // Above the early return below: the filter icon in the page header is there whether or not
        // a single trade has been recorded, and a sheet that composed only for a record with trades
        // in it would be an icon that opened nothing. Titled `Filter positions`: the date and the
        // order narrow the trade lists, and the top tiles are built from the whole portfolio.
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

        // One host for every trade dialog on the page, so the hold menu, the sheet and Needs you
        // all open the same one, and a fold does not close it.
        TradeDialogHost(appState)

        val posture = LocalFoldPosture.current
        var selected by appState.pages.portfolioSelectedTrade
        // The shade's Record sale: here rather than in a list, so it is heard in every posture.
        val pendingSell = appState.pendingSellPositionId
        LaunchedEffect(pendingSell) {
            pendingSell?.let { id ->
                appState.pages.portfolioDialog.value = TradeDialogRequest(id, TradeDialogKind.SELL)
                appState.consumePendingSell()
            }
        }
        // Unfolded and tabletop draw no card to scroll to and flash, so arriving at a trade opens
        // it - the sheet unfolded, the upper half on tabletop - and the arrival is done. The cover
        // screen and book posture keep TradeLists' own reveal.
        val pendingPosition = appState.pendingPositionId
        val opensOnArrival = posture is FoldPosture.Unfolded || posture is FoldPosture.Tabletop
        LaunchedEffect(pendingPosition, opensOnArrival) {
            val id = pendingPosition ?: return@LaunchedEffect
            if (!opensOnArrival) return@LaunchedEffect
            if (portfolio.positions.any { it.position.id == id }) selected = id
            appState.consumePendingPosition()
        }
        val positions = portfolio.positions
        val selectedView = positions.firstOrNull { it.position.id == selected }
        // Book and tabletop always show a trade in full: the one pressed, or the most urgent open
        // one. Derived, not written, so a fold back to the cover screen opens no sheet.
        val shownView = selectedView ?: positions.filter { it.open }.sortedWith(PortfolioOrder.URGENT.positions).firstOrNull()
            ?: positions.firstOrNull()
        when (posture) {
            is FoldPosture.Unfolded -> {
                SplitColumns(posture, unfoldedStart = 0.4f, start = {
                    PortfolioRecord(appState, portfolio, heroHeight = null, compact = false)
                    BestWorstTile(portfolio.stats)
                }, end = {
                    TradesTable(portfolio, appState)
                })
            }
            is FoldPosture.Book -> {
                // The list on the left, the trade on the right, split on the hinge. No sheet.
                SplitColumns(posture, unfoldedStart = 0.42f, start = {
                    PortfolioRecord(appState, portfolio, heroHeight = null, compact = true)
                    NeedsYouTile(
                        late = remember(positions) { overdueRoster(positions) },
                        appState = appState,
                        onOpen = { selected = it },
                        oneLine = true,
                    )
                    TradeLists(portfolio, appState, TradeListLayout.COMPACT)
                }, end = {
                    shownView?.let { view ->
                        // Each block of the trade is a tile of its own, on the page's ground.
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                            TradeDetail(view, appState, rememberTradeActions(view, appState))
                        }
                    }
                })
            }
            is FoldPosture.Tabletop -> {
                // The trade on the upright half, to the crease; what is pressed on the flat half.
                Column(
                    Modifier.fillMaxWidth().heightIn(min = tabletopHeroHeight(posture)),
                    verticalArrangement = Arrangement.spacedBy(Kit.TileGap),
                ) {
                    PortfolioRecord(appState, portfolio, heroHeight = null, compact = true)
                    shownView?.let { view ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                            TradeDetail(view, appState, rememberTradeActions(view, appState), showButtons = false, brief = true)
                        }
                    }
                }
                var scope by appState.pages.portfolioScope
                val open = positions.filter { it.open }
                val finished = positions.filterNot { it.open }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    KitChip("Open ${open.size}", selected = scope != TradeScope.FINISHED, onClick = { scope = TradeScope.OPEN })
                    KitChip("Finished ${finished.size}", selected = scope == TradeScope.FINISHED, onClick = { scope = TradeScope.FINISHED })
                }
                TradeKeys(if (scope == TradeScope.FINISHED) finished else open, shownView?.position?.id) { selected = it }
                shownView?.let { view -> TradeButtons(view, rememberTradeActions(view, appState)) }
            }
            else -> {
                PortfolioRecord(appState, portfolio, heroHeight = null, compact = false)
                TradeLists(portfolio, appState, TradeListLayout.CARDS)
            }
        }
        // The trade in full, as a sheet where the posture does not give it a half of its own.
        // Unfolded the kit's sheet is already a side panel.
        if (posture is FoldPosture.Cover || posture is FoldPosture.Unfolded) {
            selectedView?.let { view ->
                TradeSheet(view, appState, rememberTradeActions(view, appState), onDismiss = { selected = null })
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
 * What narrows the trade lists, in the sheet the page header's filter icon opens.
 *
 * Separate from [TradeLists] only because that is drawn below an early return for an empty record
 * and this cannot be - see the call site. It reads the same `PageState` entries, so the two cannot
 * drift.
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
 * The trades the Needs you tile names, in the order it names them.
 *
 * Most overdue first, then by ticker, which is [PortfolioOrder.URGENT]s own rule. Read off the
 * whole record rather than the filtered list - a date picked on the screen is a view of the
 * positions, not a statement about which of them are late, which is the same reason the filter is
 * kept out of `PortfolioCalculator` for the overdue notification.
 */
internal fun overdueRoster(positions: List<PositionView>): List<PositionView> =
    positions
        .filter(PositionView::overdue)
        .sortedWith(compareByDescending<PositionView> { it.overdueDays }.thenBy { it.ticker })

/**
 * Every action one trade offers, mapped once onto `AppState`: the dialogs go through
 * [PageState.portfolioDialog] and [TradeDialogHost], and Remove still goes through
 * `deletePosition`, which offers Undo on the status line.
 */
@Composable
internal fun rememberTradeActions(view: PositionView, appState: AppState): TradeActions {
    val id = view.position.id
    val openStock = LocalOpenStock.current
    // Every call the record still holds, so a trade whose analysis was deleted leads nowhere.
    val scoredCalls = remember(appState.performance) { appState.performance.callIds }
    val pages = appState.pages
    return TradeActions(
        sell = { pages.portfolioDialog.value = TradeDialogRequest(id, TradeDialogKind.SELL) },
        edit = {
            pages.portfolioDialog.value =
                TradeDialogRequest(id, if (view.realized) TradeDialogKind.EDIT_SALE else TradeDialogKind.EDIT)
        },
        // The trade being left is what back should come back to, so the press names it.
        openCall = if (id in scoredCalls) {
            { appState.openCall(id, NavStop(AppDestination.PORTFOLIO, positionId = id)) }
        } else {
            null
        },
        chart = { openStock(view.ticker) },
        keepOpen = { pages.portfolioDialog.value = TradeDialogRequest(id, TradeDialogKind.KEEP_OPEN) },
        stopKeepingOpen = { appState.setKeepOpen(view.position, keepOpen = false) },
        undoSale = { appState.clearSale(view.position) },
        showOnly = { pages.portfolioStock.value = view.ticker },
        remove = {
            if (pages.portfolioSelectedTrade.value == id) pages.portfolioSelectedTrade.value = null
            appState.deletePosition(view.position)
        },
    )
}

/** The one place the Portfolio's trade dialogs are drawn, for whichever trade asked. */
@Composable
private fun TradeDialogHost(appState: AppState) {
    var request by appState.pages.portfolioDialog
    val asked = request ?: return
    val view = appState.portfolio.positions.firstOrNull { it.position.id == asked.positionId }
    if (view == null) {
        LaunchedEffect(asked) { request = null }
        return
    }
    val position = view.position
    when (asked.kind) {
        TradeDialogKind.SELL -> SellDialog(
            held = view,
            // The estimate before the live price: once the deadline has closed a trade it is the
            // better guess at what the user actually got out at.
            suggestedExit = view.exitPrice ?: view.currentPrice,
            onDismiss = { request = null },
            onConfirm = { sale ->
                request = null
                appState.recordSale(position, sale)
            },
        )
        TradeDialogKind.EDIT_SALE -> SellDialog(
            held = view,
            suggestedExit = view.exitPrice,
            existing = position.recordedSale(),
            onDismiss = { request = null },
            onConfirm = { sale ->
                request = null
                appState.recordSale(position, sale)
            },
        )
        TradeDialogKind.EDIT -> TradeDialog(
            title = "Edit the trade",
            explanation = "Corrects what this trade was recorded at, and how long it runs. " +
                "Everything the position reports is measured from the entry; changing the window " +
                "moves the deadline, so it can close a running trade or reopen a finished one.",
            priceLabel = "Entry price",
            dateLabel = "Entry date",
            confirmLabel = "Save",
            initialPrice = position.entryPrice,
            initialDate = position.entryDate,
            initialWindow = position.windowSessions,
            windowHelp = "Trading sessions from ${position.recommendationDate}, the session this " +
                "call was made for.",
            onDismiss = { request = null },
            onConfirm = { price, date, window ->
                request = null
                appState.reprice(position, price, date, window ?: position.windowSessions)
            },
        )
        TradeDialogKind.KEEP_OPEN -> KeepOpenDialog(
            onDismiss = { request = null },
            onConfirm = { note ->
                request = null
                appState.setKeepOpen(position, true, note)
            },
        )
    }
}

/** The filtered, ordered trades, flattened: the session cards' order without the cards. */
@Composable
private fun rememberShownTrades(portfolio: Portfolio, appState: AppState): List<PositionView> {
    val dateFilter by appState.pages.portfolioDate
    val stockFilter by appState.pages.portfolioStock
    val order = appState.appPreferences.portfolioOrder
    // Filtered here rather than in the calculator. The whole record has other readers - the overdue
    // worker raises the daily reminder off it - and a date picked on this screen must not silence a
    // notification about a trade the reader scrolled past.
    return remember(portfolio.groups, dateFilter, stockFilter, order) {
        val wanted = StockSearch.query(stockFilter)
        portfolio.groups
            .filter { dateFilter.accepts(it.recommendationDate.toString()) }
            .sortedWith(order.groups)
            .flatMap { group -> group.positions.filter { it.matches(wanted) }.sortedWith(order.positions) }
    }
}

/** How many finished trades show before See all. */
private const val FinishedShown = 3

/**
 * The trades: Open now as cards, then Finished as rows, the first three and the rest behind See all.
 *
 * Arriving from a call pressed on Insights, or from a notification, reveals and flashes the trade:
 * a filter is cleared only when it is what hides it, for the reason Insights gives beside its own -
 * a link that lands on an empty screen is a broken link. A Record sale arrival opens the sale.
 */
@Composable
private fun ColumnScope.TradeLists(portfolio: Portfolio, appState: AppState, layout: TradeListLayout) {
    var dateFilter by appState.pages.portfolioDate
    var stockFilter by appState.pages.portfolioStock
    var finishedAll by appState.pages.portfolioFinishedAll
    var selected by appState.pages.portfolioSelectedTrade
    val shown = rememberShownTrades(portfolio, appState)
    val open = shown.filter { it.open }
    val finished = shown.filterNot { it.open }
    val needs = remember(portfolio.positions, appState.appPreferences.approachThresholdPercent) {
        nearLevel(needsYou(portfolio.positions, appState.appPreferences.approachThresholdPercent))
    }

    val pendingPosition = appState.pendingPositionId
    val reveal = remember { BringIntoViewRequester() }
    LaunchedEffect(pendingPosition, portfolio.groups, dateFilter, stockFilter) {
        if (pendingPosition == null) return@LaunchedEffect
        val target = portfolio.groups.firstOrNull { group -> group.positions.any { it.position.id == pendingPosition } }
        if (target == null) {
            // The trade was deleted between the press and the arrival. Dropped rather than left
            // waiting, or recording it again months later would flash a card nobody asked for.
            appState.consumePendingPosition()
            return@LaunchedEffect
        }
        if (!dateFilter.accepts(target.recommendationDate.toString())) {
            dateFilter = null
            return@LaunchedEffect
        }
        val held = target.positions.first { it.position.id == pendingPosition }
        if (!held.matches(StockSearch.query(stockFilter))) {
            stockFilter = ""
            return@LaunchedEffect
        }
        // A finished trade past the first few is behind See all; open it.
        if (!held.open && finished.indexOfFirst { it.position.id == pendingPosition } >= FinishedShown) {
            finishedAll = true
        }
        // Book posture shows the trade in full beside the list.
        if (layout == TradeListLayout.COMPACT) selected = pendingPosition
        delay(REVEAL_SETTLE_MS)
        reveal.revealIfOnScreen(appState, AppDestination.PORTFOLIO)
        // Only an open trade's card flashes, and it ends the arrival when it has. A row has no
        // flash, so the arrival ends here - left pending, every recompute would scroll back to it.
        if (!(held.open && layout == TradeListLayout.CARDS)) appState.consumePendingPosition()
    }

    if (shown.isEmpty()) {
        // The stock is named when it is what emptied the list, as it is in Results.
        val searching = stockFilter.isNotBlank()
        EmptyState(
            icon = Icons.Outlined.AccountBalanceWallet,
            title = if (searching) {
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
        return
    }

    @Composable
    fun trade(view: PositionView, asCard: Boolean) {
        val revealed = view.position.id == pendingPosition
        val actions = rememberTradeActions(view, appState)
        val modifier = if (revealed) Modifier.bringIntoViewRequester(reveal) else Modifier
        if (asCard) {
            PositionCard(
                view = view,
                near = needs[view.position.id],
                highlighted = revealed,
                onHighlightShown = appState::consumePendingPosition,
                onOpen = { selected = view.position.id },
                actions = actions.menu(view),
                modifier = modifier,
            )
        } else {
            FinishedRow(view, onOpen = { selected = view.position.id }, actions = actions.menu(view), modifier = modifier)
        }
    }

    if (open.isNotEmpty()) {
        Text("Open now", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
        open.forEach { trade(it, asCard = layout == TradeListLayout.CARDS) }
    }
    if (finished.isNotEmpty()) {
        Text("Finished", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
        finished.take(FinishedShown).forEach { trade(it, asCard = false) }
        if (finished.size > FinishedShown) {
            MoreRow("See all ${finished.size}", appState.pages.portfolioFinishedAll) {
                finished.drop(FinishedShown).forEach { trade(it, asCard = false) }
            }
        }
    }
}

/**
 * Unfolded: the trades as a table, under chips that pick Open, Finished or All. A row opens the
 * trade beside the page; a hold opens its menu.
 */
@Composable
private fun ColumnScope.TradesTable(portfolio: Portfolio, appState: AppState) {
    var scope by appState.pages.portfolioScope
    var selected by appState.pages.portfolioSelectedTrade
    val shown = rememberShownTrades(portfolio, appState)
    val open = shown.filter { it.open }
    val finished = shown.filterNot { it.open }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        KitChip("Open ${open.size}", selected = scope == TradeScope.OPEN, onClick = { scope = TradeScope.OPEN })
        KitChip("Finished ${finished.size}", selected = scope == TradeScope.FINISHED, onClick = { scope = TradeScope.FINISHED })
        KitChip("All", selected = scope == TradeScope.ALL, onClick = { scope = TradeScope.ALL })
    }
    Tile(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            TableHeading("Stock", Modifier.width(TableStockWidth))
            TableHeading("Stop to target", Modifier.weight(1f))
            TableHeading("Made", Modifier.width(TableMadeWidth), TextAlign.End)
            TableHeading("Time", Modifier.width(TableTimeWidth).padding(start = Space.s))
        }
        @Composable
        fun rows(views: List<PositionView>) = views.forEach { view ->
            val actions = rememberTradeActions(view, appState)
            TradeRow(view, onOpen = { selected = view.position.id }, actions = actions.menu(view))
        }
        when (scope) {
            TradeScope.OPEN -> rows(open)
            TradeScope.FINISHED -> rows(finished)
            TradeScope.ALL -> {
                rows(open)
                if (finished.isNotEmpty()) {
                    Text(
                        "Finished lately",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    rows(finished)
                }
            }
        }
    }
}

@Composable
private fun TableHeading(label: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = palette.muted, textAlign = align, modifier = modifier)
}

private val TableStockWidth = 132.dp
private val TableMadeWidth = 64.dp
private val TableTimeWidth = 120.dp

/** One trade in the table: the stock and paid → now, its ladder, what it made, and its time. */
@Composable
private fun TradeRow(view: PositionView, onOpen: () -> Unit, actions: List<com.ikverse.egxanalyzer.ui.kit.MenuItem>) {
    val position = view.position
    HoldMenu(items = actions, onTap = onOpen) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(TableStockWidth)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StockLogo(position.ticker, LogoSize.Row, Modifier.padding(end = Space.xs))
                    Text(position.ticker, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    "${formatPrice(position.entryPrice)} → ${formatPrice(view.currentPrice)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                    color = palette.muted,
                    maxLines = 1,
                )
            }
            Box(Modifier.weight(1f).padding(horizontal = Space.xs)) {
                PriceLadder(
                    stopLoss = position.stopLoss,
                    entryLow = position.entryPrice,
                    entryHigh = position.entryPrice,
                    target1 = position.target1,
                    target2 = position.target2,
                    reached = if (view.priceScaleChanged) null else view.exitPrice ?: view.currentPrice,
                    compact = true,
                )
            }
            Text(
                formatPercent(view.returnPct),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
                color = PriceRole.forReturn(view.returnPct),
                textAlign = TextAlign.End,
                modifier = Modifier.width(TableMadeWidth),
            )
            Column(Modifier.width(TableTimeWidth).padding(start = Space.s)) {
                Text(
                    when {
                        view.open -> "${view.sessionsRemaining} trading ${if (view.sessionsRemaining == 1) "day" else "days"} left"
                        view.ranOutOfTime && view.awaitingSale -> "time is up"
                        else -> "in ${view.sessionsHeld} ${if (view.sessionsHeld == 1) "day" else "days"}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(view.status.tone()))
                    Text(view.status.plainWord(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Space.xs), maxLines = 1)
                }
            }
        }
    }
}

/** Tabletop's keys: one chip per trade, its ticker, short word and return. Pressing one chooses it. */
@Composable
private fun TradeKeys(views: List<PositionView>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        views.forEach { view ->
            KitChip(
                "● ${view.ticker} ${view.status.plainWord()} ${formatPercent(view.returnPct)}",
                selected = view.position.id == selected,
                onClick = { onSelect(view.position.id) },
            )
        }
    }
}
