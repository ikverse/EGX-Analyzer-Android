package com.ikverse.egxanalyzer.next

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.model.PortfolioGroup
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.positionId
import com.ikverse.egxanalyzer.ui.AppState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Portfolio - *what am I holding, and what needs attention today?*
 *
 * The trades actually taken, filed one card per session, because a day's trades were one decision.
 * Three things decide how this screen is built:
 *
 * 1. **Every card starts folded**, so the folded summary is the screen's main job rather than a
 *    teaser for the card behind it. It names all three counts in their own colours, and a count of
 *    zero keeps its slot and loses its colour - so the three positions are a fixed grid the eye
 *    reads without counting.
 * 2. **Overdue has to surface without opening anything.** It is the only state in this app that
 *    earns an interruption, and it appears three times before a single card is touched: a ledger at
 *    the top naming the ticker, the day count and why; an amber spine down the card; and a chip in
 *    the folded header.
 * 3. **Realized and estimated are two constructions, never two colours.** A figure the user's own
 *    sale produced is a solid tag at full opacity; one the app marked to a price is a dashed tag at
 *    the derived alpha, with the price it was marked to said in words underneath.
 */
@Composable
internal fun NextPortfolioScreen(
    appState: AppState,
    page: NextPageState,
    contentPadding: PaddingValues,
) {
    val colors = LocalNextColors.current
    val portfolio = appState.portfolio
    val stats = portfolio.stats
    val order = appState.appPreferences.portfolioOrder

    val sessions = remember(portfolio) {
        portfolio.groups.map(PortfolioGroup::recommendationDate).distinct().sortedDescending()
    }
    val groups = remember(portfolio, page.portfolioDate, order) {
        portfolio.groups
            .filter { page.portfolioDate == null || it.recommendationDate == page.portfolioDate }
            .sortedWith(order.groups)
    }
    val overdue = remember(portfolio, order) {
        portfolio.positions.filter(PositionView::overdue).sortedByDescending { it.overdueDays }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // The trip back from an Insights card. Open the session holding the trade, scroll to it, and
    // let the flash expire on its own - the pending id is what draws it, so the row that was
    // arrived at stays marked even if the list re-sorts underneath it.
    val arriving = appState.pendingPositionId
    LaunchedEffect(arriving, groups.size) {
        val wanted = arriving ?: return@LaunchedEffect
        val index = groups.indexOfFirst { group ->
            group.positions.any { it.position.id == wanted }
        }
        if (index >= 0) {
            page.openSession(groups[index].recommendationDate)
            listState.animateScrollToItem(leadingItemsFor(overdue.isNotEmpty()) + index)
        }
        delay(NextMotion.ARRIVE_MILLIS.toLong())
        appState.consumePendingPosition()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Everything responsive on this screen is measured here, from the pane rather than the
        // window: at 411 the figures go two up, at 750 four, and none of the thresholds is above
        // 818 because no device here is wider than that.
        val figureColumns = if (maxWidth >= 520.dp) 4 else 2
        val levelColumns = if (maxWidth >= 520.dp) 3 else 2

        val leadingItems = leadingItemsFor(overdue.isNotEmpty())

        LazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                NextScreenHeader(
                    title = "Portfolio",
                    holding = "${stats.total} ${stats.total.tradeWord()} · " +
                        "${sessions.size} ${if (sessions.size == 1) "session" else "sessions"} held",
                    trailing = order.label,
                )
            }

            if (overdue.isNotEmpty()) {
                item {
                    OverdueLedger(overdue) { view ->
                        page.openSession(view.recommendationDate)
                        val index = groups.indexOfFirst {
                            it.recommendationDate == view.recommendationDate
                        }
                        if (index >= 0) {
                            scope.launch { listState.animateScrollToItem(leadingItems + index) }
                        }
                    }
                }
            }

            item { RecordStrip(appState, figureColumns) }

            item {
                NextFilterBar(
                    leading = "Sessions",
                    pickers = listOf(
                        NextPickerSpec(
                            label = page.portfolioDate?.let(::formatFullDay)
                                ?: "All ${sessions.size} held",
                            active = page.portfolioDate != null,
                            options = listOf("All ${sessions.size} held") +
                                sessions.map(::formatFullDay),
                            selectedIndex = page.portfolioDate
                                ?.let { sessions.indexOf(it) + 1 }
                                ?: 0,
                            onPick = { index ->
                                page.portfolioDate = sessions.getOrNull(index - 1)
                            },
                        ),
                        NextPickerSpec(
                            label = order.label,
                            active = order != PortfolioOrder.URGENT,
                            options = PortfolioOrder.entries.map(PortfolioOrder::label),
                            selectedIndex = order.ordinal,
                            onPick = { index ->
                                appState.updatePortfolioOrder(PortfolioOrder.entries[index])
                            },
                        ),
                    ),
                    trailing = {
                        NextButton(
                            label = if (appState.pricesRefreshing) "Refreshing" else "Prices",
                            onClick = { scope.launch { appState.refreshPrices() } },
                            tone = colors.market,
                            labelColor = colors.market,
                            minHeight = 40.dp,
                            enabled = !appState.pricesRefreshing,
                        )
                    },
                )
            }

            if (groups.isEmpty()) {
                item {
                    if (portfolio.isEmpty) {
                        NextEmpty(
                            kind = EmptyKind.INVITE,
                            title = "No trades recorded yet",
                            body = "Press Bought on a recommendation in Results or Insights, and " +
                                "it appears here as a trade of your own.",
                            modifier = Modifier.padding(top = NextMetrics.space7),
                        )
                    } else {
                        NextEmpty(
                            kind = EmptyKind.NO_MATCH,
                            title = "Nothing traded on ${formatFullDay(page.portfolioDate)}",
                            body = "The record still holds ${portfolio.groups.size} other " +
                                "${if (portfolio.groups.size == 1) "session" else "sessions"}.",
                            action = ShellAction("Show every session") { page.portfolioDate = null },
                            modifier = Modifier.padding(top = NextMetrics.space7),
                        )
                    }
                }
            }

            items(groups.size, key = { groups[it].recommendationDate.toString() }) { index ->
                val group = groups[index]
                // Placed rather than redrawn: when the fold changes the layout underneath them,
                // these travel to their new positions on the fold's own curve instead of being
                // drawn again somewhere else. This is the part of the fold the reader actually
                // watches, and it is the difference between a movement and a cut.
                Column(
                    Modifier.animateItem(
                        placementSpec = tween(
                            durationMillis = NextMotion.OPEN_MILLIS,
                            delayMillis = NextMotion.STAGGER_MILLIS * 2,
                            easing = NextMotion.hinge,
                        ),
                    ),
                ) {
                SessionCard(
                    group = group,
                    open = group.recommendationDate in page.openSessions,
                    onToggle = { page.toggleSession(group.recommendationDate) },
                    order = order,
                    levelColumns = levelColumns,
                    appState = appState,
                    page = page,
                    arrivingId = arriving,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(NextMetrics.hairline)
                        .background(colors.ruleSoft),
                )
                }
            }
        }
    }
}

/**
 * The one urgent thing in the app, said before anything is opened.
 *
 * Not a banner: each entry is a 40dp target carrying the ticker, the day count and *why* - kept
 * open, or expired - and pressing it opens that trade's card. Amber-hollow means one thing on this
 * screen and nothing else in the app means it: ran out of time while you were still in it.
 */
@Composable
private fun OverdueLedger(overdue: List<PositionView>, onJump: (PositionView) -> Unit) {
    val colors = LocalNextColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.expired.copy(alpha = 0.07f))
            .ruleTop(colors.expired, NextMetrics.spine)
            .padding(horizontal = NextMetrics.space5, vertical = NextMetrics.space4),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NextMetrics.space1)) {
            NextLabel("Overdue · needs a decision", color = colors.expired)
            NextText(
                "past deadline, never sold, neither target 2 nor the stop",
                NextType.meta,
                colors.ink3,
            )
        }
        overdue.forEach { view ->
            NextButton(
                label = "${view.ticker}  ${view.overdueDays.overdueShort()}  " +
                    if (view.keptOpen) "kept open" else "expired",
                onClick = { onJump(view) },
                tone = colors.expired,
                labelColor = colors.ink,
                minHeight = 40.dp,
            )
        }
    }
}

/** What the whole record adds up to. Ignores the date filter, because it means the whole record. */
@Composable
private fun RecordStrip(appState: AppState, columns: Int) {
    val colors = LocalNextColors.current
    val stats = appState.portfolio.stats
    Column(
        Modifier
            .fillMaxWidth()
            .ruleTop(colors.ruleStrong)
            .padding(top = NextMetrics.space5, bottom = NextMetrics.space5),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space5),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NextLabel("Your record", color = colors.ink)
            NextText(
                "every settled trade · ignores the date filter",
                NextType.meta,
                colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NextFigureGrid(
            columns = columns,
            cells = listOf(
                {
                    Column {
                        NextLabel("Win rate")
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
                        ) {
                            NextFigure(
                                text = formatRate(stats.winRate),
                                color = colors.entry,
                                style = NextType.statFigure,
                                derived = true,
                            )
                            NextText(
                                "/ ${stats.settledCount} settled",
                                NextType.meta,
                                colors.figMuted,
                            )
                        }
                    }
                },
                {
                    NextFigureCell(
                        label = "Avg return",
                        value = formatPercent(stats.settledReturnPct),
                        color = returnTone(stats.settledReturnPct, colors),
                        derived = true,
                        style = NextType.statFigure,
                    )
                },
                { BestWorst("Best", stats.best, colors.target) },
                { BestWorst("Worst", stats.worst, colors.stop) },
            ),
        )
    }
}

@Composable
private fun BestWorst(label: String, view: PositionView?, tone: Color) {
    val colors = LocalNextColors.current
    Column {
        NextLabel(label)
        Spacer(Modifier.height(NextMetrics.space2))
        if (view == null) {
            NextFigure(DASH, colors.figMuted)
        } else {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
            ) {
                NextFigure(view.ticker, colors.ink)
                NextFigure(
                    text = formatPercent(view.returnPct),
                    color = tone,
                    style = NextType.ticker,
                    derived = !view.realized,
                )
            }
        }
    }
}

/**
 * One session, holding that session's trades in every state.
 *
 * Open, Expired and Closed are sections *inside* the card rather than three lists on the screen,
 * and they are not nested cards: a state mark, a tracked word, a count and a rule to the edge.
 */
@Composable
private fun SessionCard(
    group: PortfolioGroup,
    open: Boolean,
    onToggle: () -> Unit,
    order: PortfolioOrder,
    levelColumns: Int,
    appState: AppState,
    page: NextPageState,
    arrivingId: String?,
) {
    val colors = LocalNextColors.current
    val overdueHere = group.positions.filter(PositionView::overdue)

    NextRecordCard(
        open = open,
        onToggle = onToggle,
        spineColor = if (group.hasOverdue) colors.expired else null,
        header = { chevron ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
            ) {
                NextFigure(
                    text = formatFullDay(group.recommendationDate),
                    color = colors.ink,
                    style = NextType.ticker,
                )
                NextChevron(chevron)
                Spacer(Modifier.weight(1f))
                overdueHere.firstOrNull()?.let { view ->
                    NextChip(
                        text = "${view.ticker} ${view.overdueDays.overdueShort()} overdue",
                        tone = colors.expired,
                        glyph = "◇",
                    )
                }
            }
            SummaryStrip(group)
        },
        body = {
            Section("Open", group.open, colors.market, order, levelColumns, appState, page, arrivingId)
            Section("Expired", group.expired, colors.expired, order, levelColumns, appState, page, arrivingId)
            Section("Closed", group.closed, colors.entry, order, levelColumns, appState, page, arrivingId)
        },
    )
}

/**
 * The folded summary, which is this screen's hardest problem.
 *
 * Figure first and label after, at four times the label's size, so the three counts read as figures
 * rather than as a sentence. A zero keeps its slot and loses its colour - present, and dead - which
 * makes the strip a fixed grid rather than a line that has to be parsed.
 */
@Composable
private fun SummaryStrip(group: PortfolioGroup) {
    val colors = LocalNextColors.current
    val tickers = group.positions.joinToString(" ") { it.ticker }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = NextMetrics.space4)
            .dottedRuleTop(colors.ruleSoft)
            .padding(top = NextMetrics.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        Count(group.open.size, "open", colors.market)
        StripDivider()
        Count(group.expired.size, "expired", colors.expired)
        StripDivider()
        Count(group.closed.size, "closed", colors.entry)
        Spacer(Modifier.weight(1f))
        NextText(
            text = tickers,
            style = NextType.meta,
            color = if (group.hasOverdue) colors.expired else colors.figMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Count(value: Int, label: String, tone: Color) {
    val colors = LocalNextColors.current
    val dead = value == 0
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space2),
    ) {
        NextFigure(
            text = "$value",
            color = if (dead) colors.ruleStrong else tone,
            style = NextType.figure.copy(fontSize = 16.sp),
        )
        NextText(
            text = label.uppercase(Locale.ROOT),
            style = NextType.navLabel,
            color = if (dead) colors.ink3.copy(alpha = 0.55f) else colors.ink2,
        )
    }
}

@Composable
private fun StripDivider() {
    val colors = LocalNextColors.current
    Box(
        Modifier
            .width(NextMetrics.hairline)
            .height(13.dp)
            .background(colors.ruleSoft),
    )
}

@Composable
private fun Section(
    label: String,
    positions: List<PositionView>,
    tone: Color,
    order: PortfolioOrder,
    levelColumns: Int,
    appState: AppState,
    page: NextPageState,
    arrivingId: String?,
) {
    if (positions.isEmpty()) return
    NextSectionMark(label, positions.size, tone)
    positions.sortedWith(order.positions).forEach { view ->
        PositionBlock(view, levelColumns, appState, page, view.position.id == arrivingId)
    }
}

/** One trade, and every figure measured from the user's own prices rather than the call's. */
@Composable
private fun PositionBlock(
    view: PositionView,
    levelColumns: Int,
    appState: AppState,
    page: NextPageState,
    arriving: Boolean,
) {
    val colors = LocalNextColors.current
    var menuOpen by remember(view.position.id) { mutableStateOf(false) }
    val position = view.position

    Column(
        Modifier
            .fillMaxWidth()
            .ruleTop(colors.rule)
            .then(if (arriving) Modifier.spine(colors.accent) else Modifier)
            .padding(
                start = if (arriving) NextMetrics.space4 else 0.dp,
                top = NextMetrics.space4,
                bottom = NextMetrics.space5,
            ),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
        ) {
            NextFigure(view.ticker, colors.ink, style = NextType.ticker)
            NextChip(
                text = statusWords(view),
                tone = statusTone(view, colors),
                glyph = statusGlyph(view),
            )
            if (view.overdue) {
                NextChip(
                    text = "${view.overdueDays.overdueShort()} overdue",
                    tone = colors.expired,
                    glyph = "◇",
                )
            }
        }

        NextFigureGrid(
            columns = levelColumns,
            cells = listOf(
                {
                    NextFigureCell(
                        "Bought at",
                        formatPrice(position.entryPrice),
                        colors.entry,
                    )
                },
                {
                    NextFigureCell(
                        "Bought on",
                        formatDay(position.entryDate),
                        colors.figMuted,
                    )
                },
                {
                    NextFigureCell(
                        "Deadline",
                        formatDay(view.deadlineDate),
                        if (view.overdue) colors.expired else colors.figMuted,
                    )
                },
                {
                    NextFigureCell(
                        "Target 1",
                        formatPrice(position.target1),
                        colors.target,
                    )
                },
                {
                    NextFigureCell(
                        "Target 2",
                        formatPrice(position.target2),
                        colors.target,
                    )
                },
                {
                    NextFigureCell(
                        "Stop",
                        formatPrice(position.stopLoss),
                        colors.stop,
                    )
                },
            ),
        )

        ProfitLine(view)

        if (view.priceScaleChanged) {
            NextText(
                "Prices changed scale inside this window — a split or a bonus issue. The entry was " +
                    "paid in the old money, so no return is quoted.",
                NextType.name,
                colors.ink3,
            )
        } else if (position.target1 == null && position.target2 == null && position.stopLoss == null) {
            NextText(
                "No levels were snapshotted with this trade.",
                NextType.meta,
                colors.ink3,
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
        ) {
            if (view.awaitingSale) {
                NextButton(
                    label = "Sold",
                    onClick = { page.tradeSheet = TradeSheet.Sell(position.id) },
                    tone = colors.ruleStrong,
                    labelColor = colors.ink,
                    fill = NextFill.WASH,
                )
            }
            when {
                view.keptOpen -> NextButton(
                    label = "Follow the deadline again",
                    onClick = { appState.setKeepOpen(position, false) },
                    tone = colors.expired,
                    labelColor = colors.expired,
                )

                !view.open && !view.finished && view.awaitingSale -> NextButton(
                    label = "Keep open",
                    onClick = { appState.setKeepOpen(position, true) },
                    tone = colors.market,
                    labelColor = colors.market,
                )
            }
            NextButton(
                label = "Edit trade",
                onClick = { page.tradeSheet = TradeSheet.Edit(position.id) },
                tone = colors.rule,
            )
            if (appState.performance.sessions.any { session ->
                    session.calls.any { it.positionId == position.id }
                }
            ) {
                NextButton(
                    label = "Open the call",
                    onClick = { appState.openCall(position.id) },
                    tone = colors.accent,
                    labelColor = colors.accent,
                )
            }
            NextButton(
                label = "⋮",
                onClick = { menuOpen = !menuOpen },
                tone = colors.rule,
                labelColor = colors.ink3,
                modifier = Modifier.width(NextMetrics.tapMinimum),
            )
        }

        if (menuOpen) {
            NextButton(
                label = "Remove this position",
                onClick = {
                    menuOpen = false
                    page.tradeSheet = TradeSheet.Remove(position.id)
                },
                tone = colors.stop,
                labelColor = colors.stop,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The profit line, which has to state its own provenance.
 *
 * An estimate must never be able to pass as a realized figure. Two constructions rather than two
 * colours: a solid tag at full opacity where the user's own sale set the price, a dashed tag at the
 * derived alpha where the app marked it to one - and then the price itself, in bone when a person
 * set it and steel when the market did.
 */
@Composable
private fun ProfitLine(view: PositionView) {
    val colors = LocalNextColors.current
    val realized = view.realized
    val tone = returnTone(view.returnPct, colors)
    Row(
        Modifier
            .fillMaxWidth()
            .dottedRuleTop(colors.ruleSoft)
            .padding(top = NextMetrics.space4),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
    ) {
        NextChip(
            text = if (realized) "Realized" else "Estimated",
            tone = if (realized) colors.entry else colors.figMuted,
            style = if (realized) ChipStyle.FILLED else ChipStyle.DASHED,
            fill = if (realized) colors.entry.copy(alpha = 0.12f) else null,
        )
        NextFigure(
            text = formatPercent(view.returnPct),
            color = tone,
            style = NextType.figure.copy(fontSize = 22.sp, letterSpacing = (-0.01).em),
            derived = !realized,
        )
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NextMetrics.space2)) {
                NextText(provenanceWords(view), NextType.meta, colors.ink3)
                NextFigure(
                    text = formatPrice(view.exitPrice ?: view.currentPrice),
                    color = if (realized) colors.entry else colors.market,
                    style = NextType.meta,
                )
            }
        }
    }
}

/** Where the figure was measured to, in words rather than in a colour. */
private fun provenanceWords(view: PositionView): String = when {
    view.realized -> "sold at"
    view.marketStatus == PositionStatus.FULL_TARGET_HIT ||
        view.marketStatus == PositionStatus.PARTIAL_TARGET_HIT -> "target reached at"

    view.marketStatus == PositionStatus.STOPPED_OUT -> "stopped at"
    view.ranOutOfTime -> "expired at"
    else -> "marked at last close"
}

/**
 * The state, and the lateness, as two facts.
 *
 * A trade the user deliberately kept open past its deadline says so - it does not become "expired",
 * which is the word the app uses for a trade it stopped tracking. Merging the two would undo the
 * feature.
 */
private fun statusWords(view: PositionView): String = when {
    view.keptOpen -> "Open · kept open"
    view.open -> "Open"
    else -> view.status.label
}

private fun statusTone(view: PositionView, colors: NextColors): Color = when {
    view.open -> colors.market
    view.status == PositionStatus.FULL_TARGET_HIT -> colors.target
    view.status == PositionStatus.PARTIAL_TARGET_HIT -> colors.target
    view.status == PositionStatus.STOPPED_OUT -> colors.stop
    view.ranOutOfTime -> colors.expired
    else -> colors.figMuted
}

private fun statusGlyph(view: PositionView): String = when {
    view.open -> "◷"
    view.status == PositionStatus.FULL_TARGET_HIT -> "▲"
    view.status == PositionStatus.PARTIAL_TARGET_HIT -> "△"
    view.status == PositionStatus.STOPPED_OUT -> "▼"
    view.ranOutOfTime -> "◇"
    else -> "·"
}

/** A return's colour, and the one case where zero is neither. */
internal fun returnTone(value: Double?, colors: NextColors): Color = when {
    value == null -> colors.figMuted
    value > 0 -> colors.target
    value < 0 -> colors.stop
    else -> colors.figMuted
}

/** Looks a trade back up after a sheet was opened against its id. */
internal fun AppState.positionById(id: String): PositionView? =
    portfolio.positions.firstOrNull { it.position.id == id }

/** Header, overdue ledger, record strip and filter bar - what a session card sits below. */
private fun leadingItemsFor(hasOverdue: Boolean): Int = 3 + if (hasOverdue) 1 else 0
