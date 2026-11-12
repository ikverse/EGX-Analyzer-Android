package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.callsInPlay
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.Skeleton
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.SplitColumns
import com.ikverse.egxanalyzer.model.FoldPosture
import androidx.compose.ui.unit.Dp
import com.ikverse.egxanalyzer.ui.kit.Kit
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.Role
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.popIn
import com.ikverse.egxanalyzer.ui.theme.palette

import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.ui.theme.CardHue
import com.ikverse.egxanalyzer.ui.theme.color
import com.ikverse.egxanalyzer.model.PerformanceCalculator
import com.ikverse.egxanalyzer.model.Ambiguity
import com.ikverse.egxanalyzer.model.CallOrder
import com.ikverse.egxanalyzer.model.CallSignal
import com.ikverse.egxanalyzer.model.CallTally
import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.DayBar
import com.ikverse.egxanalyzer.model.dayMove
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.StockOpinion
import com.ikverse.egxanalyzer.model.believableReturn
import com.ikverse.egxanalyzer.model.callSlot
import com.ikverse.egxanalyzer.model.opinionId
import com.ikverse.egxanalyzer.model.positionId
import com.ikverse.egxanalyzer.model.reachedTarget1
import com.ikverse.egxanalyzer.model.riskReward
import com.ikverse.egxanalyzer.model.sessionFor
import com.ikverse.egxanalyzer.model.CallStep
import com.ikverse.egxanalyzer.model.CallStepKind
import com.ikverse.egxanalyzer.model.timeline
import com.ikverse.egxanalyzer.model.plainWord
import com.ikverse.egxanalyzer.model.relativeDay
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.ui.kit.HoldMenu
import com.ikverse.egxanalyzer.ui.kit.MenuItem
import com.ikverse.egxanalyzer.ui.kit.MoreRow
import com.ikverse.egxanalyzer.ui.kit.Timeline
import com.ikverse.egxanalyzer.ui.kit.TimelineStep
import com.ikverse.egxanalyzer.ui.kit.StepTone
import com.ikverse.egxanalyzer.ui.kit.KitTag
import com.ikverse.egxanalyzer.ui.kit.KitChip
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.SheetFooterSpacer
import com.ikverse.egxanalyzer.ui.kit.OutcomeStrip
import com.ikverse.egxanalyzer.ui.kit.Outcomes
import com.ikverse.egxanalyzer.ui.kit.tileSignals
import com.ikverse.egxanalyzer.ui.theme.extraColors
import com.ikverse.egxanalyzer.ui.theme.pageAccent
import kotlinx.coroutines.delay
import java.time.ZoneId

/**
 * How every saved recommendation actually turned out.
 *
 * The point of the screen is one question - was this source right - so the hit rate leads and
 * everything else explains it. Calls that could not be judged are shown apart from the rate rather
 * than folded into it: a stock with no price history says nothing about the source that named it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InsightsScreen(appState: AppState) {
    val full = appState.performance
    // Read once for the page rather than per card: both are one SharedPreferences lookup, and a
    // card asking on every recomposition would do it a hundred times a scroll. Keyed on the
    // revision so changing either in Settings reaches a card that is already on screen.
    val askModel = remember(appState.opinionSettingsRevision, appState.cloudConfiguration) {
        appState.opinionModel()
    }
    val searching = remember(appState.opinionSettingsRevision) { appState.opinionSearchEnabled() }
    val newsWindow = remember(appState.opinionSettingsRevision) { appState.opinionNewsWindowDays() }

    Screen(
        appState = appState,
        destination = AppDestination.INSIGHTS,
        onRefresh = { appState.startAction { appState.refreshPrices() } },
        refreshing = appState.pricesRefreshing,
    ) {
        if (full.tracked == 0) {
            if (!appState.initialDataLoaded) {
                // The shape of the tiles while the record is read, so nothing jumps when it lands.
                Skeleton(Modifier.fillMaxWidth().height(260.dp))
                Skeleton(Modifier.fillMaxWidth().height(220.dp))
                return@Screen
            }
            Tile(Modifier.fillMaxWidth().popIn(0), tone = TileTone.HIGHLIGHT) {
                Text(
                    if (full.scoringSince == null) "No prices stored yet" else "Nothing scored yet",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    if (full.scoringSince == null) {
                        "Fetch prices to start scoring. Every stock your analyses have named is priced " +
                            "for the last few trading sessions, and scoring begins at the earliest one."
                    } else {
                        "Saved analyses name no stock dated on or after ${full.scoringSince}, which " +
                            "is where scoring starts. Anything called before it is left out on purpose."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Space.s),
                )
            }
            // Under the empty state: a stock worth watching is most often one nobody has called
            // yet, and that is exactly the record this page otherwise has nothing to say about.
            WatchlistCard(appState)
            return@Screen
        }

        // Session-only: a filter that outlived a restart would quietly shrink the record someone
        // came back to read. On AppState so that folding the phone, which rebuilds this page from
        // nothing, is not mistaken for a restart. See PageState.
        var channels by appState.pages.insightsChannels
        var outcomes by appState.pages.insightsOutcomes
        var stock by appState.pages.insightsStock
        // Kept across restarts where the filters above are not, and the difference is what each one
        // does: an order hides nothing. See AppPreferences.callOrder.
        val callOrder = appState.appPreferences.callOrder
        // Read once for the whole page, so every card is judged against one reading of when
        // the reader last looked. See DayCard's `seenAt`.
        val seenAt = appState.insightsSeenAt
        val everyChannel = remember(full.channels) { full.channels.map(ChannelScore::channel).sorted() }

        // Recomputed, not merely hidden: a rate or a session count still describing calls the
        // screen is filtering out would be worse than showing no filter at all.
        //
        // Worked out above the controls rather than below them, because the hero reads from it and
        // the hero is the first thing on the page.
        //
        // Normalized once for the whole record rather than once per call, and through the same rule
        // Results and Portfolio search by: this box used to match tickers alone, so typing a company
        // name on the tab that ranks the sources found nothing. See StockSearch.
        val wanted = StockSearch.query(stock)
        val report = remember(full, channels, outcomes, wanted) {
            if (channels.isEmpty() && outcomes.isEmpty() && wanted.isEmpty()) {
                full
            } else {
                PerformanceCalculator.refine(full) { call ->
                    (channels.isEmpty() || call.channel in channels) &&
                        (outcomes.isEmpty() || outcomes.any { OutcomeFilters[it]?.invoke(call) == true }) &&
                        call.matches(wanted)
                }
            }
        }

        val play = remember(full, appState.portfolio) {
            callsInPlay(full, held = { call -> appState.heldFor(call.ticker, call.openedOn) != null })
        }

        // The answer leads, and the controls that narrow it come after. This tab exists to settle
        // one argument - which source is worth reading - and it used to open on a filter row.

        // The sheet is drawn from the page rather than beside the list it narrows - it is modal,
        // so where it is composed decides nothing about where it appears, only that the page that
        // owns these controls is the page that states them. Its icon is in the header, and carries
        // a dot whenever one of these is on, which is what used to be a chip reading "Filters on"
        // above the empty state.
        FilterSheet(
            open = appState.pages.insightsFiltersOpen,
            // The shell asks the same question to decide what a back press means, so the predicate
            // lives on PageState and both read it there. See PageState.filtersActive.
            active = appState.pages.filtersActive(AppDestination.INSIGHTS),
            onClearAll = { appState.pages.clearFilters(AppDestination.INSIGHTS) },
        ) {
            MultiSelectSection(
                label = "Channels",
                options = everyChannel,
                selected = channels,
                onToggle = { name ->
                    channels = if (name in channels) channels - name else channels + name
                },
                onClear = { channels = emptySet() },
            )
            MultiSelectSection(
                label = "Outcomes",
                options = OutcomeFilters.keys.toList(),
                selected = outcomes,
                onToggle = { name ->
                    outcomes = if (name in outcomes) outcomes - name else outcomes + name
                },
                onClear = { outcomes = emptySet() },
            )
            // Below the rule, outside the clear-all, exactly as on Results and the Portfolio: an
            // order is not something a list can be cleared of, and resetting it would look like a
            // filter had silently gone missing.
            SortSection(
                options = CallOrder.entries,
                selected = callOrder,
                label = CallOrder::label,
                onSelect = appState::updateCallOrder,
            )
        }

        // Arriving from a trade pressed on the Portfolio tab. The filters this screen was left on
        // know nothing about where the reader has just come from, and a link that lands on "nothing
        // matches these filters" is a broken link. Cleared only when they actually hide the call,
        // and only when the record still holds it, so pressing back and forth between the two cards
        // does not throw away a filter that was set on purpose.
        val pendingCall = appState.pendingCallId
        LaunchedEffect(pendingCall, report) {
            if (pendingCall != null &&
                report.sessionFor(pendingCall) == null &&
                full.sessionFor(pendingCall) != null
            ) {
                channels = emptySet()
                outcomes = emptySet()
                stock = ""
                // The day's own chip too: it hides calls as surely as a filter in the sheet does.
                appState.pages.insightsDayFilter.value = null
            }
        }

        if (report.tracked == 0) {
            // The stock is named when it is what emptied the page, as it is on Results and the
            // Portfolio: "nothing matches these filters" beside a box naming a company reads as
            // though the app had not noticed which one was picked. See TickerPicker.name.
            val searching = stock.isNotBlank()
            Tile(Modifier.fillMaxWidth().popIn(0)) {
                Text(
                    if (searching) "No calls on ${TickerPicker.name(stock)}" else "Nothing matches these filters",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    if (searching) {
                        "No source has called that stock in the record. Clear the stock filter to see " +
                            "the rest of your calls."
                    } else {
                        "Clear a filter to see the rest of your calls."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    modifier = Modifier.padding(top = Space.s),
                )
                KitButton(
                    "Clear filters",
                    onClick = { appState.pages.clearFilters(AppDestination.INSIGHTS) },
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                    modifier = Modifier.padding(top = Space.m),
                )
            }
            // What the market did is true whatever the filters say, so it stays.
            InPlayRow(play)
            TodayCard(appState)
            return@Screen
        }

        // Built once for the page, not once per card: `channels` is every chat this device has
        // loaded from Telegram, and a card searching it on each recomposition would walk the whole
        // list a hundred times a scroll. Keyed on the list, so signing in - which is what fills it -
        // reaches cards already on screen.
        //
        // Matched on the name rather than on the chat id, because that is what the record holds: a
        // ChannelScore is grouped by name so a renamed channel keeps one record, and both sides of
        // this map have been through `cleanChannelName`. A name with no chat behind it is the
        // ordinary case rather than a failure - see ChannelAvatar.
        val channelPhotos = remember(appState.channels) {
            appState.channels
                .filter { it.photoPath != null }
                .associate { it.displayName to it.photoPath }
        }

        // Built once for the page rather than searched per card. Keyed on the filtered report, so a
        // channel filter narrows the record a card quotes to the same calls the page is counting.
        val scoreByChannel = remember(report.channels) {
            report.channels.associateBy(ChannelScore::channel)
        }
        val env = CallEnv(
            appState = appState,
            latestFor = { ticker -> report.latestPrices[ticker] },
            scoreFor = { channel -> scoreByChannel[channel] },
            askModel = askModel,
            searching = searching,
            newsWindow = newsWindow,
        )
        val posture = LocalFoldPosture.current
        val book = posture is FoldPosture.Book
        var openChannel by appState.pages.insightsOpenChannel
        // In book posture the record beside the ranking always shows a source: the one pressed, or
        // the one that leads. Elsewhere it is a sheet, and only while one is open.
        val recordChannel = report.channels.firstOrNull { it.channel == openChannel }
            ?: if (book) report.channels.firstOrNull() else null

        // Collapsed cards share a row; an open one takes the whole width.
        var openSession by appState.pages.openInsightsSession
        // How many of the filtered sessions are drawn - the filters and the stock search above
        // have already run over the whole record, so this only trims what they left.
        var visibleCount by appState.pages.insightsVisibleCount
        var dayFilter by appState.pages.insightsDayFilter

        /** A source's newest calls, repeats out, for the record's Latest calls. */
        fun latestCallsOf(channel: String): List<ScoredCall> = report.sessions.asSequence()
            .flatMap { it.calls.asSequence() }
            .filter { it.channel == channel && it.repeatOf == null }
            .sortedByDescending { it.openedOn }
            .take(LatestCallsShown)
            .toList()

        // A call pressed on a source's record opens its day and its sheet.
        val onOpenCall: (ScoredCall) -> Unit = { call ->
            report.sessions.firstOrNull { session -> session.calls.any { it === call } }?.let { session ->
                val index = report.sessions.indexOf(session)
                if (index >= visibleCount) visibleCount = (index / PAGE_SIZE + 1) * PAGE_SIZE
                openSession = session.key()
            }
            dayFilter = null
            appState.pages.insightsOpenCall.value = call.opinionKey()
            if (!book) openChannel = null
        }

        // The ranking follows the filters, as the rates under it do. What is still in play, the
        // last session and the feed are true of the whole record, so they read [full].
        @Composable
        fun ColumnScope.tiles() {
            TodayCard(appState)
            OutcomeKey()
            RankingTiles(
                report.channels,
                tracked = report.tracked,
                photoFor = { channelPhotos[it] },
                selected = if (book) recordChannel?.channel else null,
                onOpen = { openChannel = it.channel },
                onExport = { appState.startAction { appState.saveRankingToDownloads() } },
                showAll = appState.pages.insightsRankingShowAll,
                modifier = Modifier.popIn(0),
            )
            InPlayRow(play, Modifier.popIn(1))
            FeedNote(appState.priceHealth)
            WatchlistCard(appState)
        }
        if (!book) {
            recordChannel?.let { channel ->
                ChannelScoreSheet(
                    channel,
                    photoPath = channelPhotos[channel.channel],
                    latestCalls = latestCallsOf(channel.channel),
                    onOpenCall = onOpenCall,
                    rowState = { appState.pages.insightsRow(it) },
                    onDismiss = { openChannel = null },
                )
            }
        }

        // Where the call being pointed at sits, so the page can scroll to it inside its day.
        val reveal = remember { BringIntoViewRequester() }
        LaunchedEffect(pendingCall, report) {
            if (pendingCall == null) return@LaunchedEffect
            val session = report.sessionFor(pendingCall)
            if (session == null) {
                // Hidden by a filter, which the effect above is already clearing - or gone, if the
                // analysis behind that trade was deleted between the press and the arrival.
                if (full.sessionFor(pendingCall) == null) appState.consumePendingCall()
                return@LaunchedEffect
            }
            // Expand the window first if this session is not currently shown, so a jump from the
            // Portfolio tab or a notification can still reach a session paged out of view.
            val index = report.sessions.indexOf(session)
            if (index >= visibleCount) {
                visibleCount = (index / PAGE_SIZE + 1) * PAGE_SIZE
            }
            openSession = session.key()
            // The card is unfolding as this runs, and a scroll measured against a height it is
            // about to leave behind stops short of the card that was asked for.
            delay(REVEAL_SETTLE_MS)
            reveal.revealIfOnScreen(appState, AppDestination.INSIGHTS)
        }

        @Composable
        fun dayCard(session: ScoredSession, expanded: Boolean, modifier: Modifier = Modifier) {
            DayCard(
                session,
                env,
                expanded = expanded,
                onExpandedChange = { open ->
                    openSession = if (open) session.key() else null
                    dayFilter = null
                },
                order = callOrder,
                seenAt = seenAt,
                revealCall = pendingCall,
                onRevealShown = appState::consumePendingCall,
                reveal = reveal,
                modifier = modifier,
            )
        }

        @Composable
        fun record() {
            Text("Day by day", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
            BoxWithConstraints {
                val columns = responsiveColumns(minColumnWidth = SessionCardMinWidth, maxColumns = 3)
                val shownSessions = remember(report.sessions, visibleCount) { report.sessions.take(visibleCount) }
                // Grouped before rendering rather than while: the open day interrupts the grid.
                val bands = remember(shownSessions, openSession) {
                    expandableBands(shownSessions) { openSession == it.key() }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    bands.forEach { (band, open) ->
                        if (open) {
                            dayCard(band.single(), expanded = true)
                        } else {
                            ResponsiveRows(band, columns) { session, cardModifier ->
                                dayCard(session, expanded = false, modifier = cardModifier)
                            }
                        }
                    }
                    if (report.sessions.size > visibleCount) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            KitButton(
                                "Show older",
                                onClick = { visibleCount += PAGE_SIZE },
                                kind = ButtonKind.SECONDARY,
                                size = ButtonSize.SMALL,
                            )
                        }
                    }
                }
            }
        }

        /** The days as chips, for the postures that show one day at a time; the newest by default. */
        val shownSessions = report.sessions.take(visibleCount)
        val chosenDay = shownSessions.firstOrNull { it.key() == openSession } ?: shownSessions.firstOrNull()

        @Composable
        fun dayPicker() {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                shownSessions.forEach { session ->
                    KitChip(
                        session.dayName(),
                        selected = session === chosenDay,
                        onClick = {
                            openSession = session.key()
                            dayFilter = null
                        },
                    )
                }
                if (report.sessions.size > visibleCount) {
                    KitChip("Older", selected = false, onClick = { visibleCount += PAGE_SIZE })
                }
            }
        }

        when (posture) {
            is FoldPosture.Unfolded -> {
                // The tiles on one half; on the other, one day at a time as a table.
                SplitColumns(posture, unfoldedStart = 0.45f, start = {
                    tiles()
                }, end = {
                    dayPicker()
                    chosenDay?.let { run ->
                        DayOpened(run, remember(run.calls) { PerformanceCalculator.tally(run.calls) })
                        DayCallTable(run, env, callOrder)
                    }
                })
            }
            is FoldPosture.Book -> {
                // The ranking and the record of days on the left; the source's record on the right,
                // split on the hinge, with no sheet.
                SplitColumns(posture, unfoldedStart = 0.45f, start = {
                    tiles()
                    record()
                }, end = {
                    recordChannel?.let { channel ->
                        ChannelRecord(
                            channel,
                            photoPath = channelPhotos[channel.channel],
                            latestCalls = latestCallsOf(channel.channel),
                            onOpenCall = onOpenCall,
                            rowState = { appState.pages.insightsRow(it) },
                        )
                    }
                })
            }
            is FoldPosture.Tabletop -> {
                // The chosen day and call on the upright half, what is pressed on the flat one, and
                // the rest of the page after it.
                chosenDay?.let { TabletopDay(it, env, posture, callOrder) { dayPicker() } }
                tiles()
                record()
            }
            else -> {
                tiles()
                record()
            }
        }
    }
}

/** How many of a source's calls its record lists. */
private const val LatestCallsShown = 4

/**
 * The one line that says why this call ended up where it did.
 *
 * Names the session it settled on rather than speaking generally, because "reached target 1" and
 * "reached target 1 on 3 Aug" answer different questions, and the second is the one being asked.
 * How long it took is said beside the date for the same reason: a source reaching its target is
 * one fact and reaching it in three sessions rather than nineteen is another, and the record is
 * being read to find out which kind of source this is.
 */
private fun ScoredCall.reason(): String {
    val on = settledOn?.format(AppDates.DayMonth)
    val took = "$sessionsElapsed ${sessionsElapsed.sessionWord()}"
    return when (outcome) {
        Outcome.FULL_HIT -> "Reached target 2 on $on, in $took."
        Outcome.PARTIAL_HIT -> when {
            // Two dates, because they are two sessions. This used to name the target's date as the
            // day the stop broke, for want of any other date to name - the call is scored on the
            // session it reached the target, and the stop that undid it came later.
            stoppedAfterPartial -> when (val stop = stoppedOn?.format(AppDates.DayMonth)) {
                // Scored before the stop's own date was recorded, so only the fact survives.
                null -> "Reached target 1 on $on, then fell back to the stop."
                on -> "Reached target 1 and fell back to the stop on $on."
                else -> "Reached target 1 on $on, then fell back to the stop on $stop."
            }
            // Not final yet: target 2 is still in reach, and a card that said only "reached
            // target 1" would read as the verdict.
            !windowComplete -> "Reached target 1 on $on, in $took. Target 2 is still in reach."
            else -> "Reached target 1 on $on, in $took."
        }
        Outcome.STOPPED -> "Broke the stop by more than 2% on $on, $took in."
        // The horizon, named only here - on the one call it actually caught. It is a backstop and
        // not a deadline anybody set, so putting it on the page would invite every other figure to
        // be read against it. A T+1 card is the exception twice over: two sessions is short enough
        // that expiring is ordinary, and it is the call's own deadline rather than the backstop.
        Outcome.EXPIRED -> if (isTPlusOne) {
            "The T+1 trade was over after $windowSessions sessions with neither a target nor the " +
                "stop reached."
        } else {
            "Reached neither a target nor the stop in $windowSessions sessions, which is as long " +
                "as a call is followed for."
        }
        // Both of a T+1's sessions, since the band is on offer for the whole of the trade the card
        // described. It said "the session this call was made for" while the entry closed after one.
        Outcome.ENTRY_NOT_REACHED -> if (isTPlusOne) {
            "The buy zone never traded in either session of this T+1, so there was no trade to " +
                "take. Not counted for or against."
        } else {
            "The buy zone never traded."
        }
        Outcome.OPEN -> "Nothing settled yet: $took so far, and neither a target nor the stop " +
            "has been reached."
        Outcome.UNPRICED -> "No stored prices for this stock yet."
        // Named as the company's doing rather than the feed's, because that is what it usually is,
        // and because a line blaming the data would read as the app apologising for itself when the
        // stock has simply split.
        Outcome.PRICE_BREAK ->
            "The share price changed scale while this call was running - a split or a bonus " +
                "issue - so the levels and the prices are in different money. Not counted for or " +
                "against."
        Outcome.AMBIGUOUS -> when (ambiguity) {
            Ambiguity.ENTRY_AND_TARGET ->
                "Opened above the buy zone, target hit that day. Five-minute bars would settle it, " +
                    "and the feed keeps only 60 days of them - so a session older than that stays " +
                    "unordered for good."
            Ambiguity.SAME_INTRADAY_BAR ->
                "The buy zone and the target were both reached inside the same five-minute bar. " +
                    "This is as fine as the feed goes, so nothing further will separate them."
            // Scored before the reason was recorded, so only the fact survives.
            null -> "Two levels were reached in one session and cannot be ordered."
        }
    }
}

/**
 * Identifies a session across recompositions, so the layout knows which card is open.
 *
 * A string rather than the date or the instant itself, so which card is open is something a
 * `Bundle` can carry and the card can still be open after the page is rebuilt. Both `toString`
 * forms are unambiguous and cannot be mistaken for each other.
 */
/** The call an opinion is filed under, which is the ticker, the session and the channel. */
private fun ScoredCall.opinionKey(): String = opinionId(ticker, openedOn, channel)

private fun ScoredSession.key(): String = (targetDate ?: lastRunAt).toString()

/** Two lines of channel name, so a row of source cards stays level. */
private val ChannelHeaderHeight = 44.dp

/** What every call on the page needs from the page, gathered once so each call is handed one thing. */
private class CallEnv(
    val appState: AppState,
    /** Where a stock stands as of the last refresh, by ticker. */
    val latestFor: (String) -> LatestPrice?,
    /** The record of the source that made a call, by channel name. */
    val scoreFor: (String) -> ChannelScore?,
    /** Named in the Ask AI confirmation, because it is what the request will be billed against. */
    val askModel: String,
    val searching: Boolean,
    val newsWindow: Int,
)

/** Which item of a call's hold menu this is, so tabletop and the sheet can draw some as buttons. */
private enum class CallMenuKind { TRADE, ASK, CHART, POST, COPY, REPORT, ONLY }

/** Outcomes that say nothing about the source, drawn as a dashed "Not counted" card. */
private val NotCountedOutcomes = setOf(Outcome.ENTRY_NOT_REACHED, Outcome.UNPRICED, Outcome.PRICE_BREAK, Outcome.AMBIGUOUS)

private fun ScoredCall.notCounted(): Boolean = outcome in NotCountedOutcomes

/** The chip a call sits under in an opened day; null for one that is not counted. */
private fun ScoredCall.dayGroup(): String? = when (outcome) {
    Outcome.FULL_HIT, Outcome.PARTIAL_HIT -> "Reached"
    Outcome.STOPPED -> "Stopped"
    Outcome.EXPIRED -> "Out of time"
    Outcome.OPEN -> "Running"
    else -> null
}

private val DayGroups = listOf("Reached", "Stopped", "Out of time", "Running")

/**
 * Everything one call can do, held in one place: its hold menu, the Ask AI confirmation and answer,
 * the screenshot, recording a purchase, and its sheet. [content] draws the call however the posture
 * wants it - a card, a table row, tabletop's pane - and is handed what a tap and a hold do.
 *
 * The sheet opens when the page's [PageState.insightsOpenCall] names this call, so a fold keeps it
 * open; [showSheet] is false on tabletop, where the call is drawn on the upper half instead.
 */
@Composable
private fun InsightsCall(
    call: ScoredCall,
    env: CallEnv,
    showSheet: Boolean = true,
    content: @Composable (onOpen: () -> Unit, menu: List<Pair<CallMenuKind, MenuItem>>) -> Unit,
) {
    val appState = env.appState
    val key = call.opinionKey()
    // Chrome, so a `remember` is the right home: a fold closes a dialog, and the answer behind it
    // is on disk rather than in the composition.
    var confirming by remember(key) { mutableStateOf(false) }
    var showing by remember(key) { mutableStateOf(false) }
    // Set while this call's own request is out, so the answer opens itself when it lands. Without
    // it every call already holding an opinion would spring open the moment any request finished.
    var awaiting by remember(key) { mutableStateOf(false) }
    var viewingImage by remember(key) { mutableStateOf(false) }
    var buying by remember(key) { mutableStateOf(false) }
    val occurrence = remember(call, appState.savedResults) { appState.occurrenceFor(call) }
    val held = appState.heldFor(call.ticker, call.openedOn)
    val opinion = appState.opinionFor(call)
    val asking = appState.opinionPending == key
    var openCall by appState.pages.insightsOpenCall
    val openStock = LocalOpenStock.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(opinion) {
        if (awaiting && opinion != null) {
            awaiting = false
            showing = true
        }
    }
    val menu = buildList {
        if (held != null) {
            add(
                CallMenuKind.TRADE to MenuItem("Open your trade") {
                    // The call being left is what back should come back to, so the press names it.
                    appState.openPosition(held.position.id, NavStop(AppDestination.INSIGHTS, callId = held.position.id))
                },
            )
        } else {
            add(CallMenuKind.TRADE to MenuItem("I bought this") { buying = true })
        }
        // A second request while the first is out would be paid for twice, so none is offered.
        if (!asking) {
            add(
                CallMenuKind.ASK to MenuItem(if (opinion != null) "AI Response" else "Ask AI") {
                    if (opinion == null) confirming = true else showing = true
                },
            )
        }
        add(CallMenuKind.CHART to MenuItem("Open the chart") { openStock(call.ticker) })
        if (occurrence?.let { it.saved.result.imagePathFor(it.point.sourceImageRef) } != null) {
            add(CallMenuKind.POST to MenuItem("See the original post") { viewingImage = true })
        }
        add(CallMenuKind.COPY to MenuItem("Copy") { clipboard.setText(AnnotatedString(call.asCopyText())) })
        call.reportId?.let { id -> add(CallMenuKind.REPORT to MenuItem("Open its report") { appState.openSavedResult(id) }) }
        add(CallMenuKind.ONLY to MenuItem("Show only ${call.ticker}") { appState.pages.insightsStock.value = call.ticker })
    }

    content({ openCall = key }, menu)

    if (confirming) {
        AskAiDialog(
            call = call,
            model = env.askModel,
            searching = env.searching,
            newsWindowDays = env.newsWindow,
            onConfirm = {
                confirming = false
                awaiting = true
                appState.askAboutCall(call, false)
            },
            onDismiss = { confirming = false },
        )
    }
    if (showing && opinion != null) {
        StockOpinionSheet(
            call = call,
            opinion = opinion,
            onAskAgain = if (asking) {
                null
            } else {
                {
                    showing = false
                    awaiting = true
                    appState.askAboutCall(call, true)
                }
            },
            onDismiss = { showing = false },
        )
    }
    if (viewingImage && occurrence != null) {
        SourceImageViewer(
            occurrence.saved.result.imagePathFor(occurrence.point.sourceImageRef),
            occurrence.point.sourceImageRef,
            onDismiss = { viewingImage = false },
        )
    }
    if (buying) {
        val offeredWindow = if (call.isTPlusOne) call.windowSessions else appState.appPreferences.defaultTradeWindowSessions
        TradeDialog(
            title = "Record the purchase",
            explanation = "The price you actually paid, which is what every figure for this " +
                "position is measured from. The deadline still runs from the session the call " +
                "was made for - buying late does not buy extra time - but how many sessions it " +
                "runs for is yours to set.",
            priceLabel = "Entry price",
            dateLabel = "Entry date",
            confirmLabel = "Save",
            initialPrice = call.entryMidpoint(),
            initialWindow = offeredWindow,
            windowHelp = if (call.isTPlusOne) {
                "This call is T+1: the session it was made for, and the next one. Change it to " +
                    "give this trade longer."
            } else {
                "Your default, from Settings. Change it to give this trade its own deadline - " +
                    "it decides when this trade expires and nothing else."
            },
            onDismiss = { buying = false },
            onConfirm = { price, date, window ->
                buying = false
                appState.recordPurchase(
                    ticker = call.ticker,
                    companyEnglish = call.companyEnglish,
                    companyArabic = call.companyArabic,
                    channel = call.channel,
                    recommendationDate = call.openedOn,
                    entryPrice = price,
                    entryDate = date,
                    entryLow = call.entryLow,
                    entryHigh = call.entryHigh,
                    target1 = call.target1,
                    target2 = call.target2,
                    stopLoss = call.stopLoss,
                    windowSessions = window ?: offeredWindow,
                    offeredWindow = offeredWindow,
                    isTPlusOne = call.isTPlusOne,
                )
            },
        )
    }
    if (showSheet && openCall == key && LocalFoldPosture.current !is FoldPosture.Tabletop) {
        CallSheet(call, env, held, menu, onDismiss = { openCall = null })
    }
}

/** The logo, ticker, badge and Arabic name. The logo and ticker press together, to the stock. */
@Composable
private fun CallName(call: ScoredCall, modifier: Modifier = Modifier) {
    val openStock = LocalOpenStock.current
    Row(
        modifier.clickable(onClickLabel = "Open ${call.ticker}") { openStock(call.ticker) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StockLogo(call.ticker, LogoSize.Row, Modifier.padding(end = Space.s))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(call.ticker, style = MaterialTheme.typography.titleSmall)
                Egx33Badge(call.ticker, Modifier.padding(start = Space.s))
            }
            call.companyArabic?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The return, in the card's top-right corner, with what it is measured to underneath. */
@Composable
private fun CallReturn(call: ScoredCall) {
    // Amber, not green, where the trade gave target 1 back: drawn in the target's own colour it
    // read as a win on a card whose only other word for it was hidden behind the chip.
    val tone = if (call.stoppedAfterPartial) extraColors.expired else PriceRole.forReturn(call.believableReturn)
    val caption = when {
        call.believableReturn == null -> null
        call.stoppedAfterPartial -> "to target 1, then stopped"
        call.isTPlusOne -> "next-day call"
        call.outcome == Outcome.EXPIRED -> "at the last close"
        !call.outcome.judged -> "so far"
        else -> "in ${call.sessionsElapsed} trading ${if (call.sessionsElapsed == 1) "day" else "days"}"
    }
    Column(horizontalAlignment = Alignment.End) {
        // Withheld, not corrected, where the level it was measured at is one the fault warning
        // already says cannot be right. See CallSanity.invalidatesReturn.
        Text(
            call.believableReturn.signedPercent(),
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = TabularFigures),
            color = tone,
        )
        caption?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = palette.muted) }
    }
}

/** What the outline means, in one line: the one figure here measured from what was paid. */
@Composable
private fun HeldLine(position: PositionView) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${position.status.label} · bought at ${formatPrice(position.position.entryPrice)} · " +
                formatPercent(position.returnPct),
            style = MaterialTheme.typography.labelMedium,
            color = position.status.tone(),
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = position.status.tone(),
            modifier = Modifier.size(IconSize.Inline),
        )
    }
}

/** The ladder with the stretch the price travelled drawn along it, and the peak's arrow. */
@Composable
private fun CallLadder(call: ScoredCall, modifier: Modifier = Modifier, compact: Boolean = false) {
    val low = call.troughLow
    val high = call.peakHigh
    PriceLadder(
        stopLoss = call.stopLoss,
        entryLow = call.entryLow,
        entryHigh = call.entryHigh,
        target1 = call.target1,
        target2 = call.target2,
        modifier = modifier,
        reached = high,
        compact = compact,
        travelled = if (low != null && high != null && low <= high) low..high else null,
    )
}

/**
 * A finished - or still running - call: who and what, its return, its status, and where it went on
 * its ladder. A tap opens the call in full; a hold opens what can be done about it. Until 5.1 a tap
 * opened the trade and an unheld card answered none; the trade is in the hold menu and the sheet.
 */
@Composable
private fun FinishedCallCard(
    call: ScoredCall,
    appState: AppState,
    held: PositionView?,
    highlighted: Boolean,
    onHighlightShown: () -> Unit,
    onOpen: () -> Unit,
    menu: List<MenuItem>,
    modifier: Modifier = Modifier,
) {
    // The arrival flash takes the edge for as long as it runs, then the held outline has it back.
    val border = arrivalFlash(highlighted, onHighlightShown, held?.status?.tone()) ?: heldBorder(held)
    HoldMenu(items = menu, modifier = modifier.fillMaxWidth(), onTap = onOpen) {
        Column(
            Modifier.fillMaxWidth().callTile(border).padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                CallName(call, Modifier.weight(1f))
                Spacer(Modifier.width(Space.s))
                CallReturn(call)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                KitTag(call.outcome.plainWord(), tone = call.outcome.tone())
                KitTag(call.channel, tone = palette.muted)
                TimingLabel(call)
            }
            ExtractionWarning(call, appState)
            held?.let { HeldLine(it) }
            CallLadder(call)
        }
    }
}

/**
 * A call the scorer set aside - the entry never traded, no prices, a change of scale, or two levels
 * that cannot be ordered. Dashed, with the reason and no return or ladder: it says nothing about
 * the source, and a figure on it would be read as though it did.
 */
@Composable
private fun NotCountedCard(call: ScoredCall, onOpen: () -> Unit, menu: List<MenuItem>, modifier: Modifier = Modifier) {
    HoldMenu(items = menu, modifier = modifier.fillMaxWidth(), onTap = onOpen) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Kit.TileShape)
                .dashedOutline(palette.line)
                .padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CallName(call, Modifier.weight(1f))
                KitTag("Not counted", tone = palette.muted)
            }
            Text(call.notCountedReason(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
        }
    }
}

/** The first sentence of [reason], said plainly for the one case it words technically. */
private fun ScoredCall.notCountedReason(): String =
    if (outcome == Outcome.ENTRY_NOT_REACHED) {
        "The price never came into the buying range"
    } else {
        reason().substringBefore(". ").removeSuffix(".")
    }

/**
 * One call in full: its chart, what happened in order, three figures, and everything else behind
 * More numbers. Opened by a tap on any call; a side panel unfolded and in book posture.
 */
@Composable
private fun CallSheet(
    call: ScoredCall,
    env: CallEnv,
    held: PositionView?,
    menu: List<Pair<CallMenuKind, MenuItem>>,
    onDismiss: () -> Unit,
) {
    val appState = env.appState
    val latest = env.latestFor(call.ticker)
    KitSheet(onDismiss = onDismiss, posture = LocalFoldPosture.current, title = call.ticker) {
        Row(verticalAlignment = Alignment.Top) {
            CallName(call, Modifier.weight(1f))
            CallReturn(call)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            KitTag(call.outcome.plainWord(), tone = call.outcome.tone())
            KitTag(call.channel, tone = palette.muted)
            TimingLabel(call)
        }
        held?.let { HeldLine(it) }
        // The chart where there is more than one close to draw a line through, else the ladder.
        val history = rememberPriceHistory(appState, call.ticker)
        Box(Modifier.fillMaxWidth().kitPanel()) {
            if (history.count { it.close != null } > 1) CallChartSection(call, history, appState) else CallLadder(call)
        }
        Timeline(call.timeline().map { it.asStep() })
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            FigureCell(call.fromEntry(call.troughLow).signedPercent(), "Lowest dip", Modifier.weight(1f), PriceRole.stop)
            FigureCell(call.riskReward?.asRatio() ?: Dash, "Gain vs risk", Modifier.weight(1f))
            FigureCell(formatPrice(latest?.session?.close), "Now", Modifier.weight(1f), PriceRole.market)
        }
        MoreRow("More numbers", appState.pages.insightsRow("call:${call.opinionKey()}")) {
            TheCallPanel(call, Modifier.fillMaxWidth())
            WhatHappenedPanel(call, latest, Modifier.fillMaxWidth())
            CallSourceLines(call)
            SourceRecord(env.scoreFor(call.channel))
            CallContext(call)
            if (call.sessions.isNotEmpty()) SessionTable(call.sessions)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            menu.forEach { (kind, item) ->
                val label = when (kind) {
                    CallMenuKind.ASK, CallMenuKind.TRADE -> item.label
                    CallMenuKind.POST -> "Original post"
                    CallMenuKind.CHART -> "Chart"
                    else -> null
                }
                label?.let { KitButton(it, onClick = item.onClick, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL) }
            }
        }
        SheetFooterSpacer()
    }
}

/** A step of the model's timeline as the kit draws it. */
private fun CallStep.asStep(): TimelineStep = TimelineStep(
    text = text,
    detail = detail,
    date = date?.format(AppDates.DayMonth),
    tone = when (kind) {
        CallStepKind.DONE -> StepTone.DONE
        CallStepKind.ACTIVE -> StepTone.ACTIVE
        CallStepKind.WAITING -> StepTone.WAITING
        CallStepKind.GOOD -> StepTone.GOOD
        CallStepKind.BAD -> StepTone.BAD
    },
)

/** Who made the call and when, and when it settled - the lines the card used to carry. */
@Composable
private fun CallSourceLines(call: ScoredCall) {
    Text(
        "${call.channel} · called ${call.openedOn.format(AppDates.DayMonthYear)}",
        style = MaterialTheme.typography.bodySmall,
        color = palette.muted,
    )
    val segments = listOfNotNull(
        call.settledOn?.let { "settled ${it.format(AppDates.DayMonthYear)}" },
        // The channel did post it that day, so the card stays; it is the same bet as the call it
        // repeats, so no rate counts it twice.
        call.repeatOf?.let { "repeat of ${it.format(AppDates.DayMonthYear)}, counted once" },
    )
    if (segments.isNotEmpty()) {
        Text(segments.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = palette.muted)
    }
}

/** What the channel asked for: the four levels, each with its distance from the entry. */
@Composable
private fun TheCallPanel(call: ScoredCall, modifier: Modifier = Modifier) {
    Box(modifier.kitPanel()) {
        FigureGroup(
            "The call",
            listOfNotNull(
                {
                    Figure(
                        "Entry", call.entryRange(), Modifier.weight(1f), tone = PriceRole.entry,
                        caption = call.entryMidCaption(),
                    )
                },
                {
                    Figure(
                        "Stop loss", formatPrice(call.stopLoss), Modifier.weight(1f),
                        tone = PriceRole.stop, caption = call.fromEntry(call.stopLoss).distance(),
                    )
                },
                {
                    Figure(
                        "Target 1", formatPrice(call.target1), Modifier.weight(1f),
                        tone = PriceRole.target, caption = call.fromEntry(call.target1).distance(),
                    )
                },
                {
                    Figure(
                        "Target 2", formatPrice(call.target2), Modifier.weight(1f),
                        tone = PriceRole.target, caption = call.fromEntry(call.target2).distance(),
                    )
                },
                call.riskReward?.let { ratio ->
                    { Figure("Risk : reward", ratio.asRatio(), Modifier.weight(1f), tone = PriceRole.market) }
                },
            ),
        )
    }
}

/** What the market did about it, measured rather than printed. */
@Composable
private fun WhatHappenedPanel(call: ScoredCall, latest: LatestPrice?, modifier: Modifier = Modifier) {
    Box(modifier.kitPanel()) {
        FigureGroup(
            "What happened",
            listOf(
                { Figure("Peak", formatPrice(call.peakHigh), Modifier.weight(1f), tone = PriceRole.market, on = call.peakOn) },
                { Figure("Trough", formatPrice(call.troughLow), Modifier.weight(1f), tone = PriceRole.market, on = call.troughOn) },
                {
                    Figure(
                        if (call.settledOn != null) "Sessions to settle" else "Sessions elapsed",
                        call.sessionsElapsed.toString(),
                        Modifier.weight(1f),
                        // The one call that does have a deadline, printed by the channel itself.
                        caption = "${call.windowSessions} in all".takeIf { call.isTPlusOne },
                    )
                },
                {
                    Figure(
                        "Return",
                        call.believableReturn.signedPercent(),
                        Modifier.weight(1f),
                        tone = if (call.stoppedAfterPartial) extraColors.expired else PriceRole.forReturn(call.believableReturn),
                        caption = when {
                            call.believableReturn == null -> "not measurable from these levels"
                            call.stoppedAfterPartial -> "to target 1, then stopped"
                            call.outcome == Outcome.EXPIRED -> "to the last close"
                            else -> null
                        },
                    )
                },
                {
                    Figure(
                        "Latest close",
                        formatPrice(latest?.session?.close),
                        Modifier.weight(1f),
                        tone = PriceRole.market,
                        caption = latest?.let { price ->
                            listOfNotNull(
                                price.session.date.format(AppDates.DayMonth),
                                call.fromEntry(price.session.close).distance(),
                                if (price.provisional) "still trading" else null,
                            ).joinToString(" · ")
                        },
                    )
                },
            ),
        )
    }
}

/**
 * A session's calls as the result bar divides them. Open is the calls still running and nothing
 * else: one the record does not count is not running, and calling it so made the head say 11 over
 * a Running chip of 6.
 */
internal fun ScoredSession.outcomeSplit(tally: CallTally): Outcomes = Outcomes(
    hit = tally.partialHits + tally.fullHits,
    stopped = tally.stopped,
    expired = tally.expired,
    open = calls.count { it.repeatOf == null && it.outcome == Outcome.OPEN },
)

/** The session's calls that are neither judged nor running: what the record leaves out. */
internal fun ScoredSession.notCountedCalls(): Int =
    calls.count { it.repeatOf == null && !it.outcome.judged && it.outcome != Outcome.OPEN }

/** "Mon 9 Nov", the way a day of the record is named everywhere on the page. */
private fun ScoredSession.dayName(): String = targetDate?.format(DayFormat) ?: "Target not recorded"

private val DayFormat: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * An opened day's head, on lime: the date, how many calls from how many channels, what a call was
 * worth, the bar, and every outcome counted. [onClose] makes it the press that folds the day away.
 */
@Composable
private fun DayOpened(run: ScoredSession, tally: CallTally, onClose: (() -> Unit)? = null) {
    val channels = run.calls.map(ScoredCall::channel).distinct().size
    Tile(
        Modifier.fillMaxWidth(),
        tone = TileTone.HIGHLIGHT,
        onClick = onClose,
        onClickLabel = onClose?.let { "Close this day" },
    ) {
        Text(run.dayName(), style = MaterialTheme.typography.headlineSmall)
        Text(
            "${tally.calls} ${if (tally.calls == 1) "call" else "calls"} from $channels " +
                (if (channels == 1) "channel" else "channels"),
            style = MaterialTheme.typography.bodyMedium,
        )
        tally.averageReturn?.let {
            val signals = tileSignals(TileTone.HIGHLIGHT)
            Text(
                "${it.signedPercent()} per call",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = TabularFigures),
                color = if (it >= 0) signals.gain else signals.loss,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
        val split = run.outcomeSplit(tally)
        if (split.total > 0) OutcomeStrip(split, Modifier.padding(top = Space.s))
        Text(
            "${tally.partialHits + tally.fullHits} reached target · ${tally.expired} ran out of time · " +
                "${tally.stopped} stopped · ${split.open} running" +
                run.notCountedCalls().let { if (it > 0) " · $it not counted" else "" },
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
            modifier = Modifier.padding(top = Space.s),
        )
    }
}

/** The chips that narrow an opened day, `All` first; a chip is drawn only where it has calls. */
@Composable
private fun DayChips(calls: List<ScoredCall>, selected: String?, onSelect: (String?) -> Unit) {
    val counts = calls.groupingBy { it.dayGroup() }.eachCount()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        KitChip("All", selected = selected == null, onClick = { onSelect(null) })
        DayGroups.forEach { group ->
            val count = counts[group] ?: 0
            if (count > 0) KitChip("$group $count", selected = selected == group, onClick = { onSelect(group) })
        }
    }
}

/**
 * One day of the record. Shut, it says the day, how many calls reached a target, what a call was
 * worth and the bar; opened, it is the lime head, the day's report, the chips, and its calls.
 */
@Composable
private fun DayCard(
    run: ScoredSession,
    env: CallEnv,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    /** How the calls inside are laid out. Orders them; never hides one. */
    order: CallOrder,
    /** When the reader last had this tab open, for the New mark, read once for the page. */
    seenAt: Long,
    revealCall: String?,
    onRevealShown: () -> Unit,
    reveal: BringIntoViewRequester,
    modifier: Modifier = Modifier,
) {
    val appState = env.appState
    // One piece of arithmetic for every figure here, and the same one every rate is built from.
    val tally = remember(run.calls) { PerformanceCalculator.tally(run.calls) }
    // Analysed since the reader last had this tab open; nothing is new to a reader who never has.
    val isNew = seenAt > 0L && run.lastRunAt.toEpochMilli() > seenAt
    if (!expanded) {
        Tile(
            modifier.fillMaxWidth(),
            onClick = { onExpandedChange(true) },
            onClickLabel = "Open this day",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val word = run.targetDate?.let { relativeDay(it, java.time.LocalDate.now(ScheduleClock.ZONE)) }
                word?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = palette.muted) }
                if (isNew) {
                    Text(
                        "New",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.heroInk,
                        modifier = Modifier.padding(start = if (word != null) Space.s else 0.dp),
                    )
                }
            }
            Text(run.dayName(), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.padding(top = Space.xs), verticalAlignment = Alignment.Bottom) {
                Text(
                    "${tally.partialHits + tally.fullHits}/${tally.calls}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = TabularFigures),
                )
                Spacer(Modifier.weight(1f))
                tally.averageReturn?.let {
                    Text(
                        it.signedPercent(),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                        color = PriceRole.forReturn(it),
                    )
                }
            }
            val split = run.outcomeSplit(tally)
            if (split.total > 0) OutcomeStrip(split, Modifier.padding(top = Space.s))
        }
        return
    }
    var dayFilter by appState.pages.insightsDayFilter
    val ranAt = remember(run.lastRunAt) { AppDates.Stamp.withZone(ZoneId.systemDefault()).format(run.lastRunAt) }
    Tile(modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            DayOpened(run, tally, onClose = { onExpandedChange(false) })
            run.reportId?.let { id ->
                KitButton(
                    "Open that day's report",
                    onClick = { appState.openSavedResult(id) },
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                )
            }
            DayChips(run.calls, dayFilter) { dayFilter = it }
            Text(
                "The blue line on each bar is how far the price travelled.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
            // Two channels naming one stock for one session are two cards and one holding, so both
            // flash; only the first is scrolled to.
            val scrollTo = remember(run.calls, revealCall) {
                revealCall?.let { id -> run.calls.firstOrNull { it.positionId == id } }
            }
            order.sort(run.calls, env.scoreFor)
                .filter { dayFilter == null || it.dayGroup() == dayFilter }
                .forEach { call ->
                    val cardModifier = if (call === scrollTo) Modifier.bringIntoViewRequester(reveal) else Modifier
                    InsightsCall(call, env) { onOpen, menu ->
                        if (call.notCounted()) {
                            NotCountedCard(call, onOpen, menu.map { it.second }, cardModifier)
                        } else {
                            FinishedCallCard(
                                call,
                                appState,
                                held = appState.heldFor(call.ticker, call.openedOn),
                                highlighted = revealCall != null && call.positionId == revealCall,
                                onHighlightShown = onRevealShown,
                                onOpen = onOpen,
                                menu = menu.map { it.second },
                                modifier = cardModifier,
                            )
                        }
                    }
                }
            // Where the day's calls came from, and what was counted once. Said at the foot, because
            // built from more than one run the day is not a single analysis.
            Text(
                listOfNotNull(
                    if (run.runCount > 1) {
                        "${run.channelsTotal} chats · ${run.channelsFromLatest} from the run at $ranAt · " +
                            "the rest from ${run.runCount - 1} earlier " + (if (run.runCount == 2) "run" else "runs")
                    } else {
                        "${run.channelsTotal} ${if (run.channelsTotal == 1) "chat" else "chats"} · run $ranAt · ${run.model}"
                    },
                    tally.repeats.takeIf { it > 0 }?.let { "$it re-posted, counted once" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
        }
    }
}

/**
 * Unfolded, a day's calls as a table: the stock, where it went on its ladder, what it made, and
 * how it stands. A row opens the call's sheet beside the page; a hold opens its menu.
 */
@Composable
private fun DayCallTable(run: ScoredSession, env: CallEnv, order: CallOrder) {
    Tile(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            TableHead("Stock", Modifier.width(DayTableStockWidth))
            TableHead("Stop to target", Modifier.weight(1f))
            TableHead("Made", Modifier.width(DayTableMadeWidth))
            Spacer(Modifier.width(DayTableStatusWidth))
        }
        order.sort(run.calls, env.scoreFor).forEach { call ->
            InsightsCall(call, env) { onOpen, menu ->
                HoldMenu(items = menu.map { it.second }, onTap = onOpen) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(DayTableStockWidth)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StockLogo(call.ticker, LogoSize.Row, Modifier.padding(end = Space.xs))
                                Text(call.ticker, style = MaterialTheme.typography.titleSmall)
                            }
                            Text(call.channel, style = MaterialTheme.typography.labelSmall, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.weight(1f).padding(horizontal = Space.xs)) {
                            if (call.notCounted()) {
                                Text(Dash, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                            } else {
                                CallLadder(call, compact = true)
                            }
                        }
                        Text(
                            if (call.notCounted()) Dash else call.believableReturn.signedPercent(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
                            color = PriceRole.forReturn(call.believableReturn),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(DayTableMadeWidth),
                        )
                        Row(Modifier.width(DayTableStatusWidth).padding(start = Space.s), verticalAlignment = Alignment.CenterVertically) {
                            val word = if (call.notCounted()) "Not counted" else call.outcome.plainWord()
                            Box(Modifier.size(8.dp).clip(CircleShape).background(call.outcome.tone()))
                            AutoSizeText(word, style = MaterialTheme.typography.labelMedium, color = palette.ink, modifier = Modifier.padding(start = Space.xs).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHead(label: String, modifier: Modifier) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = palette.muted, modifier = modifier)
}

private val DayTableStockWidth = 120.dp
private val DayTableMadeWidth = 64.dp
private val DayTableStatusWidth = 104.dp

/**
 * Tabletop: on the upright half, the day's head, the chosen call and its story as tags; on the
 * flat half, the days, a key per call and the call's actions. No sheet opens in this posture.
 */
@Composable
private fun TabletopDay(
    run: ScoredSession,
    env: CallEnv,
    posture: FoldPosture.Tabletop,
    order: CallOrder,
    dayPicker: @Composable () -> Unit,
) {
    val appState = env.appState
    val calls = order.sort(run.calls, env.scoreFor)
    var openCall by appState.pages.insightsOpenCall
    val chosen = calls.firstOrNull { it.opinionKey() == openCall } ?: calls.firstOrNull { !it.notCounted() } ?: calls.firstOrNull()
    val tally = remember(run.calls) { PerformanceCalculator.tally(run.calls) }
    if (chosen == null) {
        DayOpened(run, tally)
        dayPicker()
        return
    }
    InsightsCall(chosen, env, showSheet = false) { _, menu ->
        Column(verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = tabletopHeroHeight(posture)),
                verticalArrangement = Arrangement.spacedBy(Kit.TileGap),
            ) {
                DayOpened(run, tally)
                if (chosen.notCounted()) {
                    NotCountedCard(chosen, onOpen = {}, menu = menu.map { it.second })
                } else {
                    FinishedCallCard(
                        chosen, appState, appState.heldFor(chosen.ticker, chosen.openedOn),
                        highlighted = false, onHighlightShown = {}, onOpen = {}, menu = menu.map { it.second },
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    chosen.timeline().forEach { KitTag(it.text, tone = palette.muted) }
                }
            }
            dayPicker()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                calls.forEach { call ->
                    KitChip(
                        "● ${call.ticker} ${if (call.notCounted()) Dash else call.believableReturn.signedPercent()}",
                        selected = call === chosen,
                        onClick = { openCall = call.opinionKey() },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                menu.forEach { (kind, item) ->
                    val label = when (kind) {
                        CallMenuKind.ASK -> item.label
                        CallMenuKind.POST -> "Original post"
                        CallMenuKind.CHART -> "Chart"
                        else -> null
                    }
                    label?.let { KitButton(it, onClick = item.onClick, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL) }
                }
                run.reportId?.let { id ->
                    KitButton("That day's report", onClick = { appState.openSavedResult(id) }, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
                }
            }
        }
    }
}

/**
 * The stocks this phone follows, with where each stands.
 *
 * One row per ticker and nothing derived: the close and its date are the latest the record holds,
 * read from the same `latestPrices` every card on the page reads, so this card can never disagree
 * with a call card about the same stock. Absent entirely with nothing watched.
 */
@Composable
private fun WatchlistCard(appState: AppState) {
    val watched = appState.watchlist.sorted()
    if (watched.isEmpty()) return
    val latest = appState.performance.latestPrices
    Tile(Modifier.fillMaxWidth()) {
        Text("Watching", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Space.s))
        watched.forEach { ticker ->
            val price = latest[ticker]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Open $ticker") { appState.openStock(ticker) }
                    .heightIn(min = 48.dp)
                    .padding(vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StockLogo(ticker, LogoSize.Row)
                Text(
                    ticker,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatPrice(price?.session?.close),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
                        color = PriceRole.market,
                    )
                    Text(
                        when {
                            price == null -> "no price yet · fetch prices"
                            price.provisional -> "still trading"
                            else -> AppDates.DayMonth.format(price.session.date)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                }
            }
        }
    }
}

/** The middle of the buy band, which is what a fill is usually nearest - offered as the default. */
private fun ScoredCall.entryMidpoint(): Double? {
    val low = entryLow
    val high = entryHigh
    return when {
        low != null && high != null -> (low + high) / 2
        else -> low ?: high
    }
}

/** A call as plain text, for Copy call - the same shape [CallText.of] gives a report's own card. */
private fun ScoredCall.asCopyText(): String = buildList {
    add(listOfNotNull(ticker, companyArabic).joinToString(" · "))
    val entry = when {
        entryLow != null && entryHigh != null && entryLow != entryHigh ->
            "${formatPrice(entryLow)} – ${formatPrice(entryHigh)}"
        else -> (entryLow ?: entryHigh)?.let(::formatPrice)
    }
    entry?.let { add("Entry $it") }
    target1?.let { add("Target 1 ${formatPrice(it)}") }
    target2?.let { add("Target 2 ${formatPrice(it)}") }
    stopLoss?.let { add("Stop ${formatPrice(it)}") }
    channel.takeIf(String::isNotBlank)?.let { add("Source $it") }
    add("For ${AppDates.DayMonth.format(openedOn)}")
}.joinToString("\n")

/**
 * Opens the session table, and says which way it is about to go.
 *
 * The label used to carry the count - "12 sessions from the price feed" - which made the widest
 * control on the card the one with the least to say: the number is in the first column of the
 * table it opens, and it changed the button's width on every card. The arrow carries the state
 * instead, so the label can stay still and stay short.
 */
/**
 * What the source has delivered on its other calls, in one line under the channel's name.
 *
 * Every other figure on this card judges this one call. This is the only thing on it that says
 * anything about who made it - and the record behind it was already measured for every channel and
 * reached only the ranking, which is a separate page a reader has to think to go and read. The
 * decision is made here.
 *
 * The average per judged call rather than the hit rate, for the same reason the ranking is ordered
 * on it: a rate is bought by moving the target closer to the entry, so what one of this source's
 * calls has been worth is the figure that answers "is this worth reading".
 *
 * Below [PerformanceCalculator.MINIMUM_JUDGED_TO_RANK] the figure **keeps its number and loses its
 * colour**, which is the rule the channel card already follows - a mean off three calls is measured
 * exactly and is worth nothing as a verdict. The words are added here and not there because a card
 * in a session has no ranking around it to say so.
 *
 * Absent, not blank, for a source with nothing judged: a line reading "no record yet" on every card
 * of a fresh install is a line on every card.
 */
@Composable
private fun SourceRecord(score: ChannelScore?) {
    if (score == null || score.judged == 0) return
    val thin = score.judged < PerformanceCalculator.MINIMUM_JUDGED_TO_RANK
    val quiet = palette.muted
    val tone = if (thin) quiet else PriceRole.forReturn(score.averageReturn)
    Text(
        buildAnnotatedString {
            append("This source: ")
            withStyle(SpanStyle(color = tone, fontWeight = FontWeight.Medium)) {
                append(score.averageReturn.signedPercent())
            }
            append(" per call over ${score.judged} judged")
            if (thin) append(" · too few to rank")
        },
        style = MaterialTheme.typography.labelSmall,
        color = quiet,
    )
}

/**
 * The two things about a call that only the calls around it can say, and its local signals.
 *
 * **Crowding, not confirmation**, and the wording is the point: several channels reading one chart
 * on one morning is one idea going round, not three independent readings of it. Whether it is worth
 * anything is a question for the record and not a claim a card makes. The app has detected this
 * since the two cards learned to press through to each other, and counted it nowhere.
 *
 * **Kept standing** is the other side of `repeatOf`, which exists to stop a daily table outweighing
 * a source that posts when it has something to say. Correct, and it threw the information away: a
 * call re-posted five mornings running is a different claim from one posted once.
 *
 * The signals are named rather than scored - see [CallSignal]. A bare number here would be a
 * recommendation wearing a statistic's clothes, and nothing else on this page does that.
 */
@Composable
private fun CallContext(call: ScoredCall) {
    val parts = listOfNotNull(
        call.alsoCalledBy.takeIf { it > 0 }?.let {
            "also called by $it other ${if (it == 1) "source" else "sources"}"
        },
        call.repostings.takeIf { it > 0 }?.let {
            "kept standing over $it more ${it.sessionWord()}"
        },
        call.signals.takeIf(Set<CallSignal>::isNotEmpty)
            ?.joinToString(", ", transform = CallSignal::label),
    )
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = palette.muted,
    )
}

/**
 * The one call that came with a deadline, said out loud.
 *
 * A T+1 card is not a call to hold and see: the channel printed it as buy on this session and out
 * on the next, and the app judges it over exactly those two sessions rather than the thirty every
 * other call gets. That was the single most consequential thing about a card that the card never
 * said - it reached the screen only as a caption under a figure and inside the outcome sentence
 * behind the chip, so a two-session call and a thirty-session one looked identical until one of
 * them expired.
 *
 * Tappable and outlined like the outcome chip it sits under, for the reason the status tags give:
 * a chip that is only sometimes tappable teaches nobody that it can be tapped. Neutral since
 * 2026-09-11: it was `primary` on the reasoning that it is neither a verdict nor a warning, which
 * is true and is not a reason to be the one differently coloured ring in a row of them.
 */
@Composable
private fun TimingLabel(call: ScoredCall) {
    if (!call.isTPlusOne) return
    var showing by remember(call.ticker, call.openedOn) { mutableStateOf(false) }
    // Neutral, like every other note pill on this card. It was `primary` - the app's own voice,
    // on the reasoning that a T+1 changes what the reader has to do - and that made one pill in
    // the app a different colour from its neighbours for a reason none of them showed. The
    // wording is what says this call names its own deadline; the hue was saying it twice, in a
    // language the card spends on prices everywhere else. Asked for on 2026-09-11.
    KitTag("T+1", tone = palette.muted, onClick = { showing = true })
    if (showing) {
        InfoSheet(
            infoNote(
                "${call.ticker} · a T+1 trade",
                "${call.channel} printed this as a T+1 call: buy on the session it was made " +
                    "for, and be out on the next one. So it is judged over those " +
                    "${call.windowSessions} sessions and no more, where every other call runs " +
                    "up to ${Scoring.JUDGING_HORIZON_SESSIONS} sessions before it counts as " +
                    "expired with neither a target nor the stop reached. The buy zone counts " +
                    "on both sessions - if the price never traded into it in either one, " +
                    "there was no trade to take, and the call is counted neither for nor " +
                    "against the channel.",
            ),
        ) { showing = false }
    }
}

/**
 * What does not add up about the levels this call was read out of a screenshot with.
 *
 * `PriceSanity` has guarded the price feed since the beginning; nothing has ever guarded the
 * **extraction**, which is the other half of every judgement on this page. A stop read into the
 * target row scores perfectly plausibly and counts against whichever channel happened to be
 * misread.
 *
 * A caption and not a subtraction. The outcome above it, the return beside it and every rate on the
 * page are untouched - see `CallSanity` for why a heuristic may caption a card and not move a
 * published figure. It was amber until 5.1.4, when every warning pill took the loss red; the
 * wording, not the hue, is what tells it apart from a stop-out.
 *
 * Tappable, and it says so by looking the same as the status tag - a chip that is only sometimes
 * tappable teaches nobody that it can be tapped.
 *
 * The sheet also offers View screenshot and Edit call, where [AppState.occurrenceFor] can still
 * resolve the report this call was read out of - absent once that report is gone, which a scored
 * call otherwise has no way to notice.
 */
@Composable
private fun ExtractionWarning(call: ScoredCall, appState: AppState) {
    if (call.faults.isEmpty()) return
    var showing by remember(call.ticker, call.openedOn) { mutableStateOf(false) }
    var viewingImage by remember(call.ticker, call.openedOn) { mutableStateOf(false) }
    var editing by remember(call.ticker, call.openedOn) { mutableStateOf(false) }
    KitTag(
        if (call.faults.size == 1) call.faults.first().label else "${call.faults.size} levels look misread",
        tone = palette.loss,
        onClick = { showing = true },
    )
    val occurrence = remember(call, appState.savedResults) { appState.occurrenceFor(call) }
    if (showing) {
        InfoSheet(
            note = infoNote(
                "${call.ticker} · levels to check",
                "This call was read out of a screenshot, and these levels do not hang together " +
                    "the way a printed card would. Nothing about the record has been changed: the " +
                    "call is still scored, and every rate still counts it. Worth reading the " +
                    "original card against.",
            ),
            content = {
                call.faults.forEach { fault ->
                    Column {
                        Text(fault.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            fault.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted,
                        )
                    }
                }
                if (occurrence != null) {
                    Spacer(Modifier.height(Space.xs))
                    val imagePath =
                        occurrence.saved.result.imagePathFor(occurrence.point.sourceImageRef)
                    if (imagePath != null) {
                        KitButton(
                            "View screenshot",
                            onClick = { showing = false; viewingImage = true },
                            kind = ButtonKind.SECONDARY,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    KitButton(
                        "Edit call",
                        onClick = { showing = false; editing = true },
                        kind = ButtonKind.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            onDismiss = { showing = false },
        )
    }
    if (viewingImage && occurrence != null) {
        SourceImageViewer(
            occurrence.saved.result.imagePathFor(occurrence.point.sourceImageRef),
            occurrence.point.sourceImageRef,
            onDismiss = { viewingImage = false },
        )
    }
    if (editing && occurrence != null) {
        val editor = remember(appState, occurrence.saved.id, occurrence.saved.result.editRevision) {
            CallEditor(appState, occurrence.saved)
        }
        EditCallSheet(occurrence.stock, occurrence.point, editor, onDismiss = { editing = false })
    }
}

/** One call's parsed occurrence, resolved back from a [ScoredCall] - see [AppState.occurrenceFor]. */
internal data class CallOccurrence(
    val saved: SavedAnalysis,
    val stock: ConsolidatedRecommendation,
    val point: RecommendationDataPoint,
)

/**
 * The exact report and occurrence a scored call was read out of, or null once that report is gone.
 *
 * A [ScoredCall] is a reading of the record, not the record itself: it holds no image path, no
 * editable levels, no report id to jump to directly by its own type. [ScoredCall.reportId],
 * [ScoredCall.originalStockCode] and [ScoredCall.pointIndex] are exactly the identity a correction
 * is already anchored by - see [EditCallSheet] - so this is the one place that walk back to the
 * report is made, rather than every call site that needs it making its own.
 */
internal fun AppState.occurrenceFor(call: ScoredCall): CallOccurrence? {
    val reportId = call.reportId ?: return null
    val saved = savedResults.firstOrNull { it.id == reportId } ?: return null
    val code = call.originalStockCode ?: return null
    val slot = saved.result.callSlot(code, call.pointIndex ?: return null) ?: return null
    return CallOccurrence(saved, slot.stock, slot.point)
}

/**
 * The chart [PriceLadder]'s five ticks cannot draw: how the stock actually got from the entry to
 * wherever it settled, rather than only where the five levels sit. Drawn in the ladder's place once
 * a card has the width for it - see `CallSheet`, which decides whether
 * there is enough of [history] to draw a line from at all before calling this.
 *
 * Reads the same widest-range price history the position card's own chart does - [history] is
 * fetched once by the caller, not here, so a card that ends up drawing the ladder instead never
 * pays for the query. No Levels toggle and no "you paid" line, unlike the same chart on a
 * position: every level here is the call's own, drawn or not at all.
 */
@Composable
private fun CallChartSection(call: ScoredCall, history: List<DailySession>, appState: AppState) {
    // Local to this card, exactly as the position card's own chart range is: every call card on
    // the page needs its own range and its own toggle, not one shared by all of them at once.
    var range by remember(call.ticker, call.openedOn) { mutableStateOf(ChartRange.Default) }
    // Measured back from the newest session on record, exactly as the stock sheet does it - a
    // feed running behind should shorten the line, not draw an empty box.
    val visible = remember(history, range) {
        val anchor = history.lastOrNull()?.date ?: return@remember history
        history.filter { !it.date.isBefore(range.since(anchor)) }
    }
    // 1D is the newest session's five-minute bars, fetched when it is pressed and not before.
    val day = range == ChartRange.DAY
    val bars = rememberDayBars(appState, call.ticker, wanted = day)
    val offered = remember(call.ticker) {
        ChartRange.entries.filter { it != ChartRange.CALL }.offeredFor(appState, call.ticker)
    }
    var touchedBar by remember(bars) { mutableStateOf<DayBar?>(null) }
    val move = remember(visible, bars, day) { if (day) bars?.dayMove() else visible.rangeMove() }
    val levels = remember(call) {
        ChartLevels(
            source = call.channel,
            stopLoss = call.stopLoss,
            entryLow = call.entryLow,
            entryHigh = call.entryHigh,
            target1 = call.target1,
            target2 = call.target2,
            paid = null,
        )
    }
    var touched by remember(visible) { mutableStateOf<DailySession?>(null) }
    // It sits on the sheet's panel rather than on a card, so the range track takes the hairline
    // tone: the raised one is the panel's own colour and would not show against it.
    val p = palette
    val colors = remember(p) { chartCardColors(p, surface = p.card, track = p.line) }

    Column {
        // The reading takes the headline's two places rather than a line of its own: a caption
        // that arrived on touch would push the chart about under the finger that asked for it.
        val reading = if (day) dayReading(bars.orEmpty(), touchedBar) else chartReading(visible, touched)
        ChartHeadline(
            title = reading?.first ?: if (day) dayTitle(bars) else range.title,
            // What the visible line adds up to, which is the one thing its shape cannot say.
            figure = reading?.second ?: move?.let { formatPercent(it) }.orEmpty(),
            colors = colors,
            figureColor = if (reading == null && move != null) PriceRole.forReturn(move) else colors.text,
        )
        if (day) {
            DayChartOrNote(
                bars,
                levels,
                colors,
                touchedBar,
                { touchedBar = it },
                Modifier.padding(top = Space.s),
                CallChartHeight,
            )
            ChartLegend("Levels from " + call.channel, colors, Modifier.padding(top = Space.s), dot = false)
        } else if (visible.count { it.close != null } > 1) {
            PriceChart(
                sessions = visible,
                levels = levels,
                // The one date this section is about is the call itself, marked the same way the
                // stock sheet marks every call on a stock.
                calls = setOf(call.openedOn),
                colors = colors,
                selected = touched,
                onSelect = { touched = it },
                modifier = Modifier.padding(top = Space.s),
                height = CallChartHeight,
            )
            ChartLegend("The call · levels from " + call.channel, colors, Modifier.padding(top = Space.s))
        } else {
            // Absent rather than an empty box, which reads as a chart that failed to load.
            ChartLegend("No sessions stored in this range.", colors, Modifier.padding(top = Space.s), dot = false)
        }
        // No Levels button: unlike the stock sheet, there is nothing to turn off - the call's own
        // levels are the reason this chart exists. No Call range either: the call is the subject.
        ChartRanges(
            options = offered,
            selected = range,
            onSelect = { range = it },
            label = { it.label },
            colors = colors,
            modifier = Modifier.padding(top = Space.m),
        )
    }
}

/** Exactly what the price feed reported for each session the call was judged on. */
@Composable
private fun SessionTable(sessions: List<DailySession>) {
    val scroll = rememberScrollState()
    // A white panel of mono figures since 5.0, the header in the muted key, every other row on the
    // page's ground so a line of six prices can be followed across.
    Column(Modifier.fillMaxWidth().clip(Kit.PanelShape).background(palette.card).padding(12.dp)) {
        Row(Modifier.fadingScrollbar(scroll, horizontal = true, color = palette.faint).horizontalScroll(scroll)) {
            Column {
                SessionRow("Date", "Open", "High", "Low", "Close", "Volume", header = true)
                sessions.forEachIndexed { index, it ->
                    SessionRow(
                        it.date.toString(),
                        it.open.orDash(),
                        it.high.orDash(),
                        it.low.orDash(),
                        it.close.orDash(),
                        it.volume?.toLong()?.toString() ?: Dash,
                        striped = index % 2 == 0,
                    )
                }
            }
        }
        // Said here rather than marked per row, which would cost the table a seventh column and
        // shift every figure in it. A derived session is a real session - a day's bars carry its
        // extremes and its two ends exactly - but it is one the app built rather than read, and
        // this app says so everywhere it does that.
        val rebuilt = sessions.count(DailySession::derived)
        if (rebuilt > 0) {
            Text(
                "$rebuilt of these ${sessions.size} sessions were rebuilt from hourly bars: no " +
                    "daily feed carries this stock, so without them it would have no history to " +
                    "be judged against at all.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

@Composable
private fun SessionRow(
    date: String,
    open: String,
    high: String,
    low: String,
    close: String,
    volume: String,
    header: Boolean = false,
    striped: Boolean = false,
) {
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFigures)
    val tone = if (header) palette.muted else palette.ink
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (striped) palette.ground else Color.Transparent)
            .padding(vertical = 5.dp, horizontal = 6.dp),
    ) {
        listOf(date to 96.dp, open to 68.dp, high to 68.dp, low to 68.dp, close to 68.dp, volume to 88.dp)
            .forEach { (value, width) ->
                Text(
                    value,
                    style = style,
                    color = tone,
                    modifier = Modifier.width(width),
                    textAlign = if (value === date) TextAlign.Start else TextAlign.End,
                )
            }
    }
}

/*
 * Column thresholds, set against the narrowest screen meant to show two of something.
 *
 * A Fold unfolded is 750dp, not the 851dp an emulator's inner display reports. After the rail and
 * the page padding that leaves 638dp, and less again inside a card - so a 320dp minimum, which
 * looks generous, silently gives one column on the device it was written for.
 */
private val SessionCardMinWidth = 290.dp

/**
 * Expired takes the amber the palette gained for exactly this, not the purple it used to.
 *
 * A call that ran out of time is the same fact here as it is on a trade in the Portfolio, which has
 * drawn it amber since the colour was added - and `secondary` is a hue that means nothing in this
 * app, which is the reason amber was added rather than borrowed in the first place. Two tabs
 * disagreeing about the colour of one outcome is the reader's problem, not the palette's.
 */
@Composable
private fun Outcome.outlineTone(): Color = when (this) {
    Outcome.FULL_HIT, Outcome.PARTIAL_HIT -> palette.gain
    Outcome.STOPPED -> palette.loss
    Outcome.EXPIRED -> extraColors.expired
    // The unjudged outcomes say nothing about the channel, so their ring says nothing either.
    else -> palette.muted
}

@Composable
/**
 * A hit rate is a finding, not a fault.
 *
 * The error colour is reserved for the app failing at something - a rejected key, prices that
 * would not load. A source reaching its targets half the time is highlighted; below that the
 * number speaks for itself in the ordinary text colour.
 *
 * Highlighted in full ink since the 2026-11 redesign, not the accent: vermilion is a red, and a
 * good rate drawn in it would read as a loss.
 */
internal fun Double?.rateTone(): Color = when {
    this == null -> palette.muted
    this >= 50.0 -> palette.ink
    else -> palette.muted
}

/** The target-2 figure beside a channel card's `headlineSmall` target-1 rate. */
private val ChannelRateSecondary = 18.sp

/** The same, beside the hero's `headlineLarge`. */
private val HeroRateSecondary = 26.sp

/**
 * The win rate, divided at the two levels the source printed: how often a call reached target 1,
 * and how often it went all the way to target 2.
 *
 * **Nested, not disjoint.** The second figure is a subset of the first, so "62% / 35%" reads as
 * "reached a target on 62% of judged calls, and ran the whole way on 35%". Slicing them apart into
 * target-1-only and target-2 would make them sum to the win rate and would print a first number
 * *lower* than the rate the channel actually achieved - a source that keeps reaching target 2 would
 * show a shrinking target-1 figure for doing better, which is the wrong way round.
 *
 * The second is drawn smaller and quieter because it is the deeper cut of the same rate rather than
 * a rival to it - and because two full-size numbers beside a two-line Arabic name do not fit a card
 * at [ChannelCardMinWidth]. The colour of the first is the caller's, so a thin record loses its
 * highlight; this one is fixed, because the quiet colour is what makes it read as the sub-figure.
 */
@Composable
internal fun ChannelScore.winRateSplit(secondary: TextUnit): AnnotatedString {
    val quiet = palette.muted
    return buildAnnotatedString {
        append(formatPercent(anyTargetRate, signed = false))
        withStyle(SpanStyle(fontSize = secondary, color = quiet)) {
            append(" / ")
            append(formatPercent(fullHitRate, signed = false))
        }
    }
}

/**
 * The middle of the buy zone: the one place every percentage on a call card is measured from.
 *
 * The same base the scored return uses, so the figures on one card differ only in their end points.
 */
private fun ScoredCall.entryMid(): Double? = if (entryLow != null && entryHigh != null) {
    (entryLow + entryHigh) / 2
} else {
    entryLow ?: entryHigh
}

/**
 * Where a price sits against [entryMid], as a percentage.
 *
 * One rule for the levels the call named and for where the stock has since got to: a stop 4% below
 * the entry and a close 7% below it are then two figures on one scale, and the card can be read
 * down rather than worked out.
 */
private fun ScoredCall.fromEntry(price: Double?): Double? {
    val entry = entryMid() ?: return null
    if (entry == 0.0 || price == null) return null
    return (price - entry) / entry * 100
}

/** The midpoint, where the zone is a range. A single price is its own middle and says so already. */
private fun ScoredCall.entryMidCaption(): String? =
    entryMid()
        ?.takeIf { entryLow != null && entryHigh != null && entryLow != entryHigh }
        ?.let { "mid ${formatPrice(it)}" }

private fun ScoredCall.entryRange(): String = when {
    entryLow == null && entryHigh == null -> Dash
    entryHigh != null && entryLow != null && entryHigh != entryLow ->
        "${formatPrice(entryLow)} – ${formatPrice(entryHigh)}"
    else -> formatPrice(entryLow ?: entryHigh)
}

private fun Double?.signedPercent(): String = formatPercent(this)

private fun Double?.orDash(): String = formatPrice(this)


/** [PriceChart]'s height inside [CallChartSection] - shorter than the stock sheet's own 220dp, since
 *  this card still has the two figure panels to show underneath it. */
private val CallChartHeight = 160.dp

/**
 * The outcome groups worth filtering by, in the order they are worth asking about.
 *
 * Coarser than the outcomes themselves: "reached a target" is one question, not two, and the
 * several ways a call can end up unjudged are one answer - nothing is known yet.
 */
private val OutcomeFilters: Map<String, (ScoredCall) -> Boolean> = linkedMapOf(
    "Reached a target" to { call -> call.outcome.reachedATarget },
    "Stopped" to { call -> call.outcome == Outcome.STOPPED },
    "Expired" to { call -> call.outcome == Outcome.EXPIRED },
    "Not judged" to { call -> !call.outcome.judged },
)

/** A group of figures inside a call card: a white panel on the card's ground. */
@Composable
private fun Modifier.kitPanel(): Modifier =
    this.clip(Kit.PanelShape).background(palette.card).padding(12.dp)

/** A call card inside a session tile: the page's ground, the tile's corners, the held or arrival ring. */
@Composable
private fun Modifier.callTile(edge: BorderStroke?): Modifier =
    this
        .clip(Kit.TileShape)
        .background(palette.ground)
        .then(if (edge != null) Modifier.border(edge, Kit.TileShape) else Modifier)

