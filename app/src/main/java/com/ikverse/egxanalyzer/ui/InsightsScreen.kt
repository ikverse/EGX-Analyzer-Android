package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.ikverse.egxanalyzer.data.PerformanceCalculator
import com.ikverse.egxanalyzer.model.Ambiguity
import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import com.ikverse.egxanalyzer.model.StockOpinion
import com.ikverse.egxanalyzer.model.opinionId
import com.ikverse.egxanalyzer.model.positionId
import com.ikverse.egxanalyzer.model.riskReward
import com.ikverse.egxanalyzer.model.sessionFor
import com.ikverse.egxanalyzer.ui.theme.extraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val scope = rememberCoroutineScope()
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
        title = "Insights",
        onRefresh = { scope.launch { appState.refreshPrices() } },
        refreshing = appState.pricesRefreshing,
    ) {
        if (full.tracked == 0) {
            EmptyState(
                icon = Icons.Outlined.Insights,
                title = if (full.scoringSince == null) "No prices stored yet" else "Nothing scored yet",
                detail = if (full.scoringSince == null) {
                    "Fetch prices to start scoring. Every stock your analyses have named is priced " +
                        "for the last few trading sessions, and scoring begins at the earliest one."
                } else {
                    "Saved analyses name no stock dated on or after ${full.scoringSince}, which " +
                        "is where scoring starts. Anything called before it is left out on purpose."
                },
            )
            return@Screen
        }

        // Session-only: a filter that outlived a restart would quietly shrink the record someone
        // came back to read. On AppState so that folding the phone, which rebuilds this page from
        // nothing, is not mistaken for a restart. See PageState.
        var channels by appState.pages.insightsChannels
        var outcomes by appState.pages.insightsOutcomes
        var stock by appState.pages.insightsStock
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

        // The answer leads, and the controls that narrow it come after. This tab exists to settle
        // one argument - which source is worth reading - and it used to open on a filter row.
        if (report.tracked > 0) InsightsHero(report)

        // Above the empty state below on purpose: filters that vanish when they match nothing leave
        // the reader looking at "nothing matches" with no way to undo it.
        FilterRow(
            active = channels.isNotEmpty() || outcomes.isNotEmpty() || stock.isNotBlank(),
            onClearAll = {
                channels = emptySet()
                outcomes = emptySet()
                stock = ""
            },
        ) {
            MultiSelectFilter(
                label = "channels",
                options = everyChannel,
                selected = channels,
                onToggle = { name ->
                    channels = if (name in channels) channels - name else channels + name
                },
                onClear = { channels = emptySet() },
            )
            MultiSelectFilter(
                label = "outcomes",
                options = OutcomeFilters.keys.toList(),
                selected = outcomes,
                onToggle = { name ->
                    outcomes = if (name in outcomes) outcomes - name else outcomes + name
                },
                onClear = { outcomes = emptySet() },
            )
            StockFilterField(value = stock, onValueChange = { stock = it })
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
            }
        }

        if (report.tracked == 0) {
            EmptyState(
                icon = Icons.Outlined.Insights,
                title = "Nothing matches these filters",
                detail = "Clear a filter to see the rest of your calls.",
            )
            return@Screen
        }

        // The ranking leads. It is the question the tab exists to answer - which source is worth
        // reading - and it used to sit below two summary cards as a collapsed line.
        ChannelRanking(report.channels)
        // One collapsed card per row wasted most of a wide screen: each held a single line of
        // text across the full width. The count is derived, so an untested width still behaves.
        // Collapsed cards share a row; an open one takes the whole width, because its contents are
        // a table of figures and half a row squeezes every price onto two lines.
        var openSession by appState.pages.openInsightsSession
        // Where the call being pointed at sits, so the page can scroll to it inside whichever
        // session card holds it.
        val reveal = remember { BringIntoViewRequester() }
        LaunchedEffect(pendingCall, report) {
            if (pendingCall == null) return@LaunchedEffect
            val session = report.sessionFor(pendingCall)
            if (session == null) {
                // Hidden by a filter, which the effect above is already clearing - or gone, if the
                // analysis behind that trade was deleted between the press and the arrival. Gone is
                // dropped rather than left waiting: a re-run of that session months later would
                // otherwise flash a card for a press nobody remembers making.
                if (full.sessionFor(pendingCall) == null) appState.consumePendingCall()
                return@LaunchedEffect
            }
            openSession = session.key()
            // The card is unfolding as this runs, and a scroll measured against a height it is
            // about to leave behind stops short of the card that was asked for.
            delay(REVEAL_SETTLE_MS)
            reveal.bringIntoView()
        }
        BoxWithConstraints {
            val columns = responsiveColumns(minColumnWidth = SessionCardMinWidth, maxColumns = 3)
            // Grouped before rendering rather than while: the open session interrupts the grid, and
            // where it does so cannot be decided one card at a time.
            val bands = remember(report.sessions, openSession) {
                expandableBands(report.sessions) { openSession == it.key() }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                bands.forEach { (band, open) ->
                    if (open) {
                        SessionCard(
                            band.single(),
                            expanded = true,
                            onExpandedChange = { openSession = null },
                            heldFor = appState::heldFor,
                            latestFor = { ticker -> report.latestPrices[ticker] },
                            opinionFor = appState::opinionFor,
                            askingFor = { call -> appState.opinionPending == call.opinionKey() },
                            askModel = askModel,
                            searching = searching,
                            newsWindow = newsWindow,
                            onAsk = { call, again -> appState.askAboutCall(call, again) },
                            onOpenTrade = appState::openPosition,
                            revealCall = pendingCall,
                            onRevealShown = appState::consumePendingCall,
                            reveal = reveal,
                        )
                    } else {
                        ResponsiveRows(band, columns) { session, cardModifier ->
                            SessionCard(
                                session,
                                expanded = false,
                                onExpandedChange = { openSession = session.key() },
                                heldFor = appState::heldFor,
                                latestFor = { ticker -> report.latestPrices[ticker] },
                                opinionFor = appState::opinionFor,
                                askingFor = { call -> appState.opinionPending == call.opinionKey() },
                                askModel = askModel,
                                searching = searching,
                                newsWindow = newsWindow,
                                onAsk = { call, again -> appState.askAboutCall(call, again) },
                                onOpenTrade = appState::openPosition,
                                // A folded card draws none of its calls, so the highlight only ever
                                // lands on the one this arrival opened.
                                revealCall = pendingCall,
                                onRevealShown = appState::consumePendingCall,
                                reveal = reveal,
                                modifier = cardModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeLabel(call: ScoredCall) {
    // Every label explains itself, not just the puzzling one: a chip that is sometimes tappable
    // teaches nobody that it can be tapped.
    var showing by remember(call.ticker, call.openedOn) { mutableStateOf(false) }
    Surface(
        color = call.outcome.container(),
        shape = CircleShape,
        onClick = { showing = true },
    ) {
        Text(
            call.outcome.label,
            style = MaterialTheme.typography.labelMedium,
            color = call.outcome.onContainer(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    if (showing) {
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text("${call.ticker} · ${call.outcome.label}") },
            text = { Text(call.reason()) },
            confirmButton = {
                TextButton(onClick = { showing = false }) { Text("Close") }
            },
        )
    }
}

/**
 * The one line that says why this call ended up where it did.
 *
 * Names the session it settled on rather than speaking generally, because "reached target 1" and
 * "reached target 1 on 3 Aug" answer different questions, and the second is the one being asked.
 */
private fun ScoredCall.reason(): String {
    val on = settledOn?.format(OUTCOME_DATE)
    return when (outcome) {
        Outcome.FULL_HIT -> "Reached target 2 on $on."
        Outcome.PARTIAL_HIT -> when {
            // Two dates, because they are two sessions. This used to name the target's date as the
            // day the stop broke, for want of any other date to name - the call is scored on the
            // session it reached the target, and the stop that undid it came later.
            stoppedAfterPartial -> when (val stop = stoppedOn?.format(OUTCOME_DATE)) {
                // Scored before the stop's own date was recorded, so only the fact survives.
                null -> "Reached target 1 on $on, then fell back to the stop."
                on -> "Reached target 1 and fell back to the stop on $on."
                else -> "Reached target 1 on $on, then fell back to the stop on $stop."
            }
            // Not final yet: target 2 is still in reach, and a card that said only "reached
            // target 1" would read as the verdict.
            !windowComplete ->
                "Reached target 1 on $on; may still reach target 2 before the " +
                    "$windowSessions-session window closes."
            else -> "Reached target 1 on $on."
        }
        Outcome.STOPPED -> "Broke the stop by more than 2% on $on."
        Outcome.EXPIRED -> "The $windowSessions-session window closed with no target or stop reached."
        // A shortened entry is what marks a T+1 call, and this is the case it was shortened for:
        // the band was never offered on the one session the card said to buy on.
        Outcome.ENTRY_NOT_REACHED -> if (entrySessions < windowSessions) {
            "The buy zone never traded on the session this call was made for, so there was no " +
                "T+1 trade to take. Not counted for or against."
        } else {
            "The buy zone never traded in the window."
        }
        Outcome.OPEN -> "Still inside its window, nothing settled yet."
        Outcome.UNPRICED -> "No stored prices for this stock yet."
        // Named as the company's doing rather than the feed's, because that is what it usually is,
        // and because a line blaming the data would read as the app apologising for itself when the
        // stock has simply split.
        Outcome.PRICE_BREAK ->
            "The share price changed scale inside the window - a split or a bonus issue - so the " +
                "levels and the prices are in different money. Not counted for or against."
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

private val OUTCOME_DATE = DateTimeFormatter.ofPattern("d MMM")

/**
 * The same date carrying its year, for the line that dates the call itself.
 *
 * A caption under a figure is read against the card it sits on and can drop the year; the line that
 * says when a call was made is the one place a reader is placing it in time, and a record going back
 * more than a year has two 3 Augusts in it. This line used to print the raw ISO date, which was the
 * only date on the card not in this app's own format.
 */
private val CALL_DATE = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * The source with the best record, and what that record is actually made of.
 *
 * The page opens on this rather than on a filter row, because the tab exists to settle one argument.
 * The bar is the argument: a rate on its own hides how the calls behind it divided, and the bar
 * under it does not. How much evidence that rests on is the line of counts beside it.
 *
 * Nothing here is drawn in a card. The hero is the page rather than something on it, and a card
 * around it would put an edge between the reader and the first thing they came for.
 */
@Composable
private fun ColumnScope.InsightsHero(report: PerformanceReport) {
    val ranked = report.channels
    // The floor decides who leads, exactly as PerformanceCalculator's own ordering does. A source
    // with three settled calls at 100% is not the best record in the file, and naming it as one -
    // beside its own card that greys the figure as too thin to rank - is the screen contradicting
    // itself in two places at once.
    val best = ranked.firstOrNull { it.judged >= PerformanceCalculator.MINIMUM_JUDGED_TO_RANK }
    val window = "${report.windowSessions}-${report.windowSessions.sessionWord().removeSuffix("s")} window"
    // A T+1 call is judged over its own two sessions, so the setting no longer describes every call
    // on the page. Said only where the record actually holds one, rather than qualifying a figure
    // that nothing on screen contradicts.
    val holdsTPlusOne = remember(report.sessions) {
        report.sessions.any { session ->
            session.calls.any { it.windowSessions != report.windowSessions }
        }
    }

    Column(
        Modifier.padding(start = PageTextInset, end = Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text(
            if (best == null) "NOT ENOUGH SETTLED YET" else "BEST RECORD",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (best == null) {
            // Honest rather than empty. Every source is still being measured; none has the
            // MINIMUM_JUDGED_TO_RANK settled calls it takes to lead, and saying which is furthest
            // along would be the ranking this rule exists to prevent.
            Text(
                "No source has ${PerformanceCalculator.MINIMUM_JUDGED_TO_RANK} settled calls yet, " +
                    "so none of them leads. The rates below are measured exactly; they are just " +
                    "resting on too little to rank.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // The name is Arabic and the figures are not, so a first-strong isolate keeps each in
            // its own direction rather than letting a digit drift into the name.
            Text(
                "⁨${best.channel}⁩",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            OutcomeBar(best, on = MaterialTheme.colorScheme.background)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.Bottom,
            ) {
                // How often following this source worked, split at the two levels it printed. The
                // average return used to stand here and stands beside it now: what one call was
                // worth is the other half of the answer, and neither figure is readable alone -
                // a source printing its target 2% above the entry reaches it nearly every time and
                // hands it all back on the call that goes wrong.
                Text(
                    best.winRateSplit(HeroRateSecondary),
                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = TabularFigures),
                    color = best.anyTargetRate.rateTone(),
                )
                Text(
                    "reached target 1 / target 2 · " +
                        "${best.averageReturn.signedPercent()} per call",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = HeroLabelBaseline),
                )
            }
        }
        // What the figures above rest on, and where the window that produced them is changed. This
        // is the line the page used to open with, which said the window and nothing else.
        Text(
            listOfNotNull(
                "${report.tracked} scored",
                ranked.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "source" else "sources"}" },
                window,
                "T+1 calls ${Scoring.T_PLUS_ONE_WINDOW_SESSIONS}".takeIf { holdsTPlusOne },
                // How current every price on this page is, in one place rather than repeated on
                // each card. Read off the prices themselves, so a refresh that ran and came back
                // with nothing cannot make the page look fresher than it is.
                report.pricesTo?.let { "prices to $it" },
                "change it in Settings",
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Lifts the label off the baseline of the figure beside it, which is four times its size. */
private val HeroLabelBaseline = 4.dp

@Composable
private fun ColumnScope.ChannelRanking(channels: List<ChannelScore>) {
    if (channels.isEmpty()) return
    // The order PerformanceCalculator already produced, which puts the MINIMUM_JUDGED_TO_RANK floor
    // first and the rate second. This used to re-sort on the rate alone, which floated a source with
    // three settled calls to the top of the list and into the summary line as "Best".
    val best = channels.firstOrNull { it.judged >= PerformanceCalculator.MINIMUM_JUDGED_TO_RANK }
    ExpandableSection(
        title = "Sources ranked",
        icon = Icons.Outlined.Leaderboard,
        // Open by default: this is the question the tab exists to answer, not a detail to go
        // looking for.
        initiallyExpanded = true,
        summary = best?.let {
            "${channels.size} ${if (channels.size == 1) "source" else "sources"} · " +
                "led by ⁨${it.channel}⁩"
        } ?: "${channels.size} sources, none settled enough to rank",
    ) {
        // The key to every bar below, drawn once. Four names repeated on each card would be most of
        // the card, and the order never changes.
        OutcomeLegend(Modifier.padding(bottom = Space.xs))
        BoxWithConstraints {
            val columns = responsiveColumns(minColumnWidth = ChannelCardMinWidth, maxColumns = 2)
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                ResponsiveRows(channels, columns) { channel, cardModifier ->
                    ChannelCard(channel, cardModifier)
                }
            }
        }
    }
}

/** One source, and everything known about how it has done. */
@Composable
private fun ChannelCard(channel: ChannelScore, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            // A fixed height so a long Arabic channel name does not make its card taller than the
            // one beside it.
            // A figure resting on two settled calls is still measured exactly, and still worth
            // nothing as a verdict. It keeps its figure and loses its colour.
            val thin = channel.judged < PerformanceCalculator.MINIMUM_JUDGED_TO_RANK
            Row(
                Modifier.heightIn(min = ChannelHeaderHeight),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    channel.channel,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // How often this source worked, split at the two levels it printed. What one call
                // was worth moved down into the figures, where it is still what the cards are
                // ordered on - the two answer different questions and neither is the whole verdict.
                Text(
                    channel.winRateSplit(ChannelRateSecondary),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = TabularFigures),
                    fontWeight = FontWeight.Bold,
                    color = if (thin) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        channel.anyTargetRate.rateTone()
                    },
                )
            }
            Text(
                listOfNotNull(
                    if (thin) "too few judged to rank" else "reached target 1 / target 2",
                    // The rate the evidence will bear, under the rate that was measured. Six of six
                    // is a true 100% and a floor of 61%; a long record at 80% floors above that,
                    // which is why the order is not the rate. It qualifies the first figure only -
                    // target 2 is a rate this line does not bound, and saying which is the point.
                    channel.anyTargetRateFloor?.let {
                        "target 1 at least ${formatPercent(it, signed = false)}"
                    },
                    "${channel.judged} of ${channel.calls} calls judged",
                    // Said out loud rather than left as a smaller number of calls than the sessions
                    // below plainly show. The same call posted again is the same bet, and a channel
                    // re-posting every morning would otherwise carry one idea several times over.
                    channel.repeats.takeIf { it > 0 }?.let { "$it re-posted, counted once" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // The four counts these figures used to print are inside the bar. They partition the
            // judged calls, so they were always a division of one quantity rather than four
            // separate ones - and printed apart, nothing said so and nothing said how the failures
            // divided. The bar says both. How much record is behind them is the line above it.
            OutcomeBar(channel, height = OutcomeBarCompact)
            FigureGroup(
                "The record behind it",
                listOf(
                    {
                        // Still what the list is ordered on, so the card has to keep saying it.
                        // A rate cannot answer whether following a source pays: reach for +2%
                        // against a -10% stop and nine calls in ten get there, and the tenth takes
                        // back more than the nine made.
                        Figure(
                            "Per judged call", channel.averageReturn.signedPercent(),
                            Modifier.weight(1f),
                            tone = if (thin) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                PriceRole.forReturn(channel.averageReturn)
                            },
                            caption = "what one call was worth",
                        )
                    },
                    {
                        Figure(
                            "Risk : reward", channel.averageRiskReward.asRatio(),
                            Modifier.weight(1f),
                            // Without this the rate above cannot be read at all: reaching a target
                            // nine times in ten at 0.3 to 1 gives it all back on the tenth.
                            caption = "target 1 against the stop",
                        )
                    },
                    {
                        Figure(
                            "Sessions to a target", formatPrice(channel.medianSessionsToHit),
                            Modifier.weight(1f),
                        )
                    },
                    {
                        Figure(
                            "Not tradable", channel.notTradable.toString(),
                            Modifier.weight(1f), tone = PriceRole.muted,
                        )
                    },
                ),
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    run: ScoredSession,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    /** The position taken on a call, so a stock actually held is marked as such. */
    heldFor: (String, java.time.LocalDate?) -> PositionView?,
    /** Where a stock stands as of the last refresh, by ticker. */
    latestFor: (String) -> LatestPrice?,
    /** What Ask AI has said about a call, if anything. */
    opinionFor: (ScoredCall) -> StockOpinion?,
    /** Whether that call's own request is currently out. */
    askingFor: (ScoredCall) -> Boolean,
    askModel: String,
    searching: Boolean,
    /** How far back a searched request would look, which the confirmation names. */
    newsWindow: Int,
    onAsk: (ScoredCall, Boolean) -> Unit,
    /** Opens a held call's trade on the Portfolio tab, by the id the two share. */
    onOpenTrade: (String) -> Unit,
    /** The trade the reader has just pressed their way here from, if this is that arrival. */
    revealCall: String?,
    onRevealShown: () -> Unit,
    reveal: BringIntoViewRequester,
    modifier: Modifier = Modifier,
) {
    val ranAt = remember(run.lastRunAt) {
        DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(run.lastRunAt)
    }
    // Closed by default: a run is a summary line until asked for, so a page of them stays
    // readable however many analyses have been saved.
    ExpandableSection(
        title = run.targetDate?.toString() ?: "Target not recorded",
        icon = Icons.Outlined.Assessment,
        summary = "${run.calls.size} ${if (run.calls.size == 1) "call" else "calls"} · " +
            // Target 1 before target 2, the same order the bar above reads in. A summary that
            // counted them the other way round would be the one line on the card disagreeing.
            "${run.partialHits} partial · ${run.fullHits} full · ${run.stopped} stopped" +
            if (run.pending > 0) " · ${run.pending} pending" else "",
        summaryTone = if (run.fullHits > 0) MaterialTheme.colorScheme.primary else null,
        expandedState = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
    ) {
        // Where the card's contents came from. Built from more than one run it is not a single
        // analysis, and reading it as one would misplace the responsibility for a call.
        Text(
            if (run.runCount > 1) {
                "${run.channelsTotal} chats · ${run.channelsFromLatest} from the run at $ranAt · " +
                    "the rest from ${run.runCount - 1} earlier " +
                    (if (run.runCount == 2) "run" else "runs")
            } else {
                "${run.channelsTotal} ${if (run.channelsTotal == 1) "chat" else "chats"} · " +
                    "run $ranAt · ${run.model}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Two stocks across on an unfolded screen: a call card is a heading and eight figures, and
        // one per row leaves half the session card empty.
        BoxWithConstraints {
            val columns = responsiveColumns(minColumnWidth = CallCardMinWidth, maxColumns = 2)
            // Two channels naming one stock for one session are two cards and one holding, so both
            // of them flash - each really is a call behind that trade. Only the first is scrolled
            // to, because a requester pointed at two places would travel to one and then the other.
            val scrollTo = remember(run.calls, revealCall) {
                revealCall?.let { id -> run.calls.firstOrNull { it.positionId == id } }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                ResponsiveRows(run.calls, columns, spacing = Space.s) { call, cardModifier ->
                    val held = heldFor(call.ticker, call.openedOn)
                    ScoredCallRow(
                        call,
                        latestFor(call.ticker),
                        opinionFor(call),
                        asking = askingFor(call),
                        askModel = askModel,
                        searching = searching,
                        newsWindow = newsWindow,
                        onAsk = { again -> onAsk(call, again) },
                        held = held,
                        // Only a call the user is actually in leads anywhere: there is no trade to
                        // open for one they read and left alone.
                        onOpenTrade = held?.let { { onOpenTrade(it.position.id) } },
                        highlighted = revealCall != null && call.positionId == revealCall,
                        onHighlightShown = onRevealShown,
                        modifier = if (call === scrollTo) {
                            cardModifier.bringIntoViewRequester(reveal)
                        } else {
                            cardModifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoredCallRow(
    call: ScoredCall,
    /** Where this stock stands as of the last refresh. Absent for a stock with no prices at all. */
    latest: LatestPrice?,
    /** What Ask AI has already said about this call, if it has been asked. */
    opinion: StockOpinion?,
    /** True while this card's own request is out. */
    asking: Boolean,
    /** Named in the confirmation, because it is what the request will be billed against. */
    askModel: String,
    /** Whether a live search would be attached, which the confirmation also has to say. */
    searching: Boolean,
    /** The news lookback that search would be given, in days. Named in the confirmation too. */
    newsWindow: Int,
    /** Sends the question. The flag is a deliberate re-ask, the only way to pay for one twice. */
    onAsk: (askAgain: Boolean) -> Unit,
    held: PositionView?,
    /** Opens the trade taken on this call. Absent where none was, which is most cards. */
    onOpenTrade: (() -> Unit)?,
    highlighted: Boolean,
    onHighlightShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(call.ticker, call.openedOn) { mutableStateOf(false) }
    // Chrome, so a `remember` is the right home for all three: a fold closes the sheet, and the
    // answer behind it is on disk rather than in the composition. See PageState.
    var confirming by remember(call.ticker, call.openedOn) { mutableStateOf(false) }
    var showing by remember(call.ticker, call.openedOn) { mutableStateOf(false) }
    // Set while this card's own request is out, so the answer opens itself when it lands. Without
    // it every card already holding an opinion would spring open the moment any request finished.
    var awaiting by remember(call.ticker, call.openedOn) { mutableStateOf(false) }
    LaunchedEffect(opinion) {
        if (awaiting && opinion != null) {
            awaiting = false
            showing = true
        }
    }
    if (confirming) {
        AskAiDialog(
            call = call,
            model = askModel,
            searching = searching,
            newsWindowDays = newsWindow,
            onConfirm = {
                confirming = false
                awaiting = true
                onAsk(false)
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
                    onAsk(true)
                }
            },
            onDismiss = { showing = false },
        )
    }
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    // The arrival flash takes the edge for as long as it runs, then the held outline has it back.
    // Outlined where the user is actually in the trade, in the colour of where that stands: the
    // figures on this card judge the channel, and the outline says what it cost or made you.
    val border = arrivalFlash(highlighted, onHighlightShown) ?: heldBorder(held)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            // A fixed two lines for the name, so a company whose name wraps does not make its card
            // taller than the one beside it. This is what left the pair ragged when they sat
            // side by side.
            Row(
                Modifier.heightIn(min = CardHeaderHeight),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    // On the ticker's line, not beside the block: this row is held at a minimum
                    // height, so a logo beside the column left a fixed gap under itself on every
                    // card however short the name was.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StockLogo(call.ticker, LogoSize.Row, Modifier.padding(end = Space.s))
                        Text(call.ticker, style = MaterialTheme.typography.titleSmall)
                    }
                    listOfNotNull(call.companyArabic, call.companyEnglish)
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
                OutcomeLabel(call)
            }
            Text(
                "${call.channel} · called ${call.openedOn.format(CALL_DATE)}" +
                    (call.settledOn?.let { " · settled ${it.format(CALL_DATE)}" } ?: "") +
                    // The channel did post it that day, so the card stays; it is the same bet as
                    // the call it repeats, so no rate counts it twice.
                    (call.repeatOf?.let { " · repeat of ${it.format(CALL_DATE)}, counted once" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // What the outline means, in one line. Everything else on this card judges the channel
            // on the levels it printed; this is the only figure here measured from what was paid.
            held?.let { position ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${position.status.label} · bought at " +
                            "${formatPrice(position.position.entryPrice)} · " +
                            formatPercent(position.returnPct),
                        style = MaterialTheme.typography.labelMedium,
                        color = position.status.tone(),
                        // fill = false so the arrow sits against the end of the line rather than
                        // out at the card's edge, where it would read as unrelated to it.
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // The one hint that the card leads somewhere. A whole card being pressable is
                    // invisible otherwise, and this is the line the trade is named on.
                    if (onOpenTrade != null) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            // The press is described where it is declared; a reader announcing the
                            // glyph as well would say it twice.
                            contentDescription = null,
                            tint = position.status.tone(),
                            modifier = Modifier.size(IconSize.Inline),
                        )
                    }
                }
            }
            // The shape of the call, which four prices in a column cannot show: whether it risked a
            // little for a lot or the reverse, and how far up that scale the stock actually got.
            // The same drawing the Results tab has always used for the same five levels, so a call
            // read on one tab and again on the other is the same picture in both places.
            PriceLadder(
                stopLoss = call.stopLoss,
                entryLow = call.entryLow,
                entryHigh = call.entryHigh,
                target1 = call.target1,
                target2 = call.target2,
                // The extreme inside the judged window, which is what the figure below it says too.
                peak = call.peakHigh,
            )
            // Two groups rather than eight loose figures: what the channel asked for, and what the
            // market did about it. Colour says which is which without reading the labels.
            FigureGroup(
                // On the heading rather than in a figure of its own, because it is not a fifth
                // level - it is what the four below come to. Without it the card prints four prices
                // and leaves the reader dividing them to find out what the call was actually worth.
                "The call" + (call.riskReward?.let { " · risk : reward ${it.asRatio()}" } ?: ""),
                listOf(
                    // Paid and risked first, aimed at second: wrapped two-up this keeps the targets
                    // side by side in reading order rather than splitting them across rows.
                    {
                        Figure(
                            "Entry", call.entryRange(), Modifier.weight(1f), tone = PriceRole.entry,
                            // The base the three percentages below are measured from, said once
                            // where they can all be read against it.
                            caption = call.entryMidCaption(),
                        )
                    },
                    // A price on its own says nothing across stocks: 59.80 is a wide stop on one
                    // share and a tight one on another, and the distance is the half that compares.
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
                ),
            )
            // The heading below is a boundary the eye loses once a group wraps onto three rows, so
            // the two groups are ruled apart. Nothing separates the header from the figures: the
            // group headings already do that work, and a third hairline starts to read as a table.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FigureGroup(
                "What happened",
                listOf(
                    // "In window" rather than "since call": the scorer stops at the session a call
                    // settles on, so on a call that settled three weeks ago these are the extremes
                    // up to that day and not up to today. The label used to promise today.
                    {
                        Figure(
                            "Peak in window", formatPrice(call.peakHigh), Modifier.weight(1f),
                            tone = PriceRole.market, on = call.peakOn,
                        )
                    },
                    {
                        Figure(
                            "Trough in window", formatPrice(call.troughLow), Modifier.weight(1f),
                            tone = PriceRole.market, on = call.troughOn,
                        )
                    },
                    {
                        Figure(
                            // Two different facts under one label: sessions to settlement on a call
                            // that settled, sessions so far on one still running. Said, rather than
                            // left to the reader to infer from the outcome chip.
                            if (call.settledOn != null) "Sessions to settle" else "Sessions elapsed",
                            // The denominator is the point. Twelve sessions against a twenty-session
                            // window is a call with room left; twelve of twelve is a call out of it,
                            // and the bare count read the same either way.
                            "${call.sessionsElapsed} of ${call.windowSessions}",
                            Modifier.weight(1f),
                            // The one call whose window is its own rather than the setting's, so
                            // the figure above is not read against the window named on the page.
                            caption = "T+1 call".takeIf { call.entrySessions < call.windowSessions },
                        )
                    },
                    {
                        Figure(
                            "Return",
                            call.returnPct.signedPercent(),
                            Modifier.weight(1f),
                            // Amber, not green. The figure is the return to target 1 and it is
                            // correct, but the trade gave it back - and drawn in the target's own
                            // colour it read as a win on a card whose only other word for it was
                            // hidden behind the chip.
                            tone = if (call.stoppedAfterPartial) {
                                extraColors.expired
                            } else {
                                PriceRole.forReturn(call.returnPct)
                            },
                            // A call that ran out of time reached no level it named, so its return
                            // is measured to wherever the window left it. Said on the card, because
                            // that is a different kind of figure from a return to a target the
                            // market actually got to.
                            caption = when {
                                call.stoppedAfterPartial -> "to target 1, then stopped"
                                call.outcome == Outcome.EXPIRED -> "to the last close"
                                else -> null
                            },
                        )
                    },
                    // Where the stock actually is, which every other figure here stops short of
                    // saying: peak and trough are the extremes of the window and the return is
                    // measured to wherever the call settled, so a card could report a target hit
                    // three weeks ago and give no clue what the price has done since.
                    {
                        Figure(
                            "Latest close",
                            formatPrice(latest?.session?.close),
                            Modifier.weight(1f),
                            tone = PriceRole.market,
                            caption = latest?.let { price ->
                                listOfNotNull(
                                    price.session.date.format(OUTCOME_DATE),
                                    call.fromEntry(price.session.close).distance(),
                                    // Named as what it is rather than as a warning: the session has
                                    // not closed, so the figure beside it is going to move.
                                    if (price.provisional) "still trading" else null,
                                ).joinToString(" · ")
                            },
                        )
                    },
                ),
            )
            // Ruled off the figures above: a filled pill hard against the last row of a group reads
            // as another figure in it, and one of these two spends money.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Wrapped rather than laid in a row: at 280dp the two labels together are wider than
            // the card, and a button pushed off the edge is a button nobody can press.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                AskAiButton(
                    label = when {
                        asking -> "Asking\u2026"
                        // Named for what it holds once there is something to open. The button that
                        // spends money and the button that reopens a saved answer must not read
                        // alike, or the free press looks like the paid one - which is why the saved
                        // one is drawn as a ring rather than a filled pill, not only worded apart.
                        opinion != null -> "AI Response"
                        else -> "Ask AI"
                    },
                    onClick = { if (opinion == null) confirming = true else showing = true },
                    look = if (opinion != null && !asking) AiLook.Outlined else AiLook.Filled,
                    // A second request while the first is still out would be paid for twice and
                    // answer the same question.
                    enabled = !asking,
                    working = asking,
                    // Stable per card, so the halo does not restart its cycle on every scroll.
                    phaseKey = call.ticker,
                )
                if (call.sessions.isNotEmpty()) {
                    PriceFeedButton(expanded) { expanded = !expanded }
                }
            }
            if (call.sessions.isNotEmpty()) {
                AnimatedVisibility(expanded) { SessionTable(call.sessions) }
            }
        }
    }

    // Two overloads over one body rather than a clickable wrapped round the card: Material's own
    // pressable card is what keeps the ripple inside the corners, and an unheld call must not
    // answer a press at all - there is nothing to open.
    if (onOpenTrade == null) {
        Card(
            modifier.fillMaxWidth(),
            colors = colors,
            border = border,
            shape = MaterialTheme.shapes.medium,
            content = body,
        )
    } else {
        Card(
            onClick = onOpenTrade,
            // A pressable card announces itself as "activate" and nothing more, which says nothing
            // about where the press goes. The action itself is Material's; only its name is ours.
            modifier = modifier.fillMaxWidth()
                .semantics { onClick(label = "Open this trade in the Portfolio", action = null) },
            colors = colors,
            border = border,
            shape = MaterialTheme.shapes.medium,
            content = body,
        )
    }
}

/**
 * Opens the session table, and says which way it is about to go.
 *
 * The label used to carry the count - "12 sessions from the price feed" - which made the widest
 * control on the card the one with the least to say: the number is in the first column of the
 * table it opens, and it changed the button's width on every card. The arrow carries the state
 * instead, so the label can stay still and stay short.
 */
@Composable
private fun PriceFeedButton(expanded: Boolean, onClick: () -> Unit) {
    // Turned rather than swapped for an up arrow: the rotation is what shows which of the two
    // states the press moved between, where two glyphs only show where it landed.
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "priceFeed")
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .height(PillHeight)
            // The card's own hairline, not a heavier one. Beside a filled pill that spends money
            // this is the quiet half of the row, and it should read as an outline of a control
            // rather than a second call to press something.
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                onClick(label = if (expanded) "Hide the price feed" else "Show the price feed", action = null)
            }
            .padding(start = Space.m, end = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text("Price feed", style = MaterialTheme.typography.labelMedium, color = ink)
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(IconSize.Inline).graphicsLayer { rotationZ = turn },
            tint = ink,
        )
    }
}

/** Exactly what the price feed reported for each session the call was judged on. */
@Composable
private fun SessionTable(sessions: List<DailySession>) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fadingScrollbar(scroll, horizontal = true).horizontalScroll(scroll)) {
            Column {
                SessionRow("Date", "Open", "High", "Low", "Close", "Volume", header = true)
                sessions.forEach {
                    SessionRow(
                        it.date.toString(),
                        it.open.orDash(),
                        it.high.orDash(),
                        it.low.orDash(),
                        it.close.orDash(),
                        it.volume?.toLong()?.toString() ?: Dash,
                    )
                }
            }
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
) {
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    val tone = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.padding(vertical = 3.dp)) {
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

/**
 * Figures that belong together, laid out two-up when the width cannot take four.
 *
 * The cover screen has plenty of height and little width, so wrapping beats shrinking: four
 * columns at 443dp truncates every price it is supposed to show.
 *
 * Four was once the whole story and is now only the cap - "What happened" carries five, and a group
 * whose last row is short spreads that row across the width rather than padding it out to the
 * column count.
 */
@Composable
private fun FigureGroup(title: String, figures: List<@Composable RowScope.() -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints {
            // Taken as a list rather than a row of slots, because wrapping means splitting them -
            // and a lambda that draws four figures cannot be cut in half.
            //
            // Capped rather than "all of them on a wide screen": the threshold below was measured
            // for four prices, and a group of five would put all five on one row at a width that
            // was only ever proved to hold four.
            val perRow = if (maxWidth >= FourFiguresMinWidth) {
                minOf(figures.size, MaxFiguresPerRow)
            } else {
                2
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                figures.chunked(perRow).forEach { row ->
                    // A short last row spreads across the width rather than being padded out to the
                    // column count. Every figure here already carries `weight(1f)`, so dropping the
                    // spacers is what lets the odd one out have the room: "Latest close" was left
                    // on half a row with its caption broken over three lines and the other half
                    // of the row empty.
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        row.forEach { figure -> figure() }
                    }
                }
            }
        }
    }
}

/** Four prices need this much before the digits start truncating. */
private val FourFiguresMinWidth = 420.dp

/** What [FourFiguresMinWidth] was measured against, so a longer group wraps rather than shrinks. */
private const val MaxFiguresPerRow = 4

/*
 * Column thresholds, set against the narrowest screen meant to show two of something.
 *
 * A Fold unfolded is 750dp, not the 851dp an emulator's inner display reports. After the rail and
 * the page padding that leaves 638dp, and less again inside a card - so a 320dp minimum, which
 * looks generous, silently gives one column on the device it was written for.
 */
private val ChannelCardMinWidth = 280.dp
private val SessionCardMinWidth = 290.dp
private val CallCardMinWidth = 280.dp

@Composable
private fun Figure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.onSurface,
    /** The session a high or low was set on: a price without its date says half of it. */
    on: java.time.LocalDate? = null,
    /** Takes the place of [on] where the line under a figure has more to say than a date. */
    caption: String? = null,
) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Every figure this draws is a number, and this screen's whole job is comparing them down a
        // column. Proportional digits put the same price at a different width on every card.
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
            color = tone,
            textAlign = TextAlign.Start,
        )
        val note = caption ?: on?.format(OUTCOME_DATE)
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Expired takes the amber the palette gained for exactly this, not the purple it used to.
 *
 * A call that ran out of time is the same fact here as it is on a trade in the Portfolio, which has
 * drawn it amber since the colour was added - and `secondary` is a hue that means nothing in this
 * app, which is the reason amber was added rather than borrowed in the first place. Two tabs
 * disagreeing about the colour of one outcome is the reader's problem, not the palette's.
 */
@Composable
private fun Outcome.container(): Color = when (this) {
    Outcome.FULL_HIT, Outcome.PARTIAL_HIT -> MaterialTheme.colorScheme.tertiaryContainer
    Outcome.STOPPED -> MaterialTheme.colorScheme.errorContainer
    Outcome.EXPIRED -> extraColors.expiredContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun Outcome.onContainer(): Color = when (this) {
    Outcome.FULL_HIT, Outcome.PARTIAL_HIT -> MaterialTheme.colorScheme.onTertiaryContainer
    Outcome.STOPPED -> MaterialTheme.colorScheme.onErrorContainer
    Outcome.EXPIRED -> extraColors.onExpiredContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
/**
 * A hit rate is a finding, not a fault.
 *
 * The error colour is reserved for the app failing at something - a rejected key, prices that
 * would not load. A source reaching its targets half the time is highlighted; below that the
 * number speaks for itself in the ordinary text colour.
 */
private fun Double?.rateTone(): Color = when {
    this == null -> MaterialTheme.colorScheme.onSurfaceVariant
    this >= 50.0 -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** The target-2 figure beside a channel card's `headlineSmall` target-1 rate. */
private val ChannelRateSecondary = 16.sp

/** The same, beside the hero's `headlineLarge`. */
private val HeroRateSecondary = 22.sp

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
private fun ChannelScore.winRateSplit(secondary: TextUnit): AnnotatedString {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * A distance, named as one.
 *
 * A bare percentage under a price is read as the day's move, which is the one thing it is not.
 */
private fun Double?.distance(): String? = this?.let { "${formatPercent(it)} from entry" }

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

/**
 * A risk-to-reward ratio, always written against a risk of one.
 *
 * "1 : 2.4" rather than a bare 2.4, which on a card of prices reads as one - and which way round
 * it goes is the whole meaning of the figure.
 */
private fun Double?.asRatio(): String =
    if (this == null || isNaN()) Dash else "1 : ${formatPrice(this)}"

private fun Double?.orDash(): String = formatPrice(this)


/** Ticker plus two lines of company name, so every call card starts the same height. */
private val CardHeaderHeight = 76.dp

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
