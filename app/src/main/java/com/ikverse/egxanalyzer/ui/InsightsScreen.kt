package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.data.PerformanceCalculator
import com.ikverse.egxanalyzer.model.Ambiguity
import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import com.ikverse.egxanalyzer.model.positionId
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
                        "is the first session with stored prices."
                },
            )
            return@Screen
        }

        // Session-only: a filter that outlived a restart would quietly shrink the record someone
        // came back to read. Saveable is a different question and answers a different complaint -
        // see Results, which explains it beside its own.
        var channels by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet()) }
        var outcomes by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet()) }
        var stock by rememberSaveable { mutableStateOf("") }
        val everyChannel = remember(full.channels) { full.channels.map(ChannelScore::channel).sorted() }

        // Recomputed, not merely hidden: a rate or a session count still describing calls the
        // screen is filtering out would be worse than showing no filter at all.
        //
        // Worked out above the controls rather than below them, because the hero reads from it and
        // the hero is the first thing on the page.
        val wanted = stock.trim().uppercase()
        val report = remember(full, channels, outcomes, wanted) {
            if (channels.isEmpty() && outcomes.isEmpty() && wanted.isEmpty()) {
                full
            } else {
                PerformanceCalculator.refine(full) { call ->
                    (channels.isEmpty() || call.channel in channels) &&
                        (outcomes.isEmpty() || outcomes.any { OutcomeFilters[it]?.invoke(call) == true }) &&
                        (wanted.isEmpty() || call.ticker.contains(wanted))
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
        var openSession by rememberSaveable { mutableStateOf<String?>(null) }
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
                            band.single(), report.windowSessions,
                            expanded = true,
                            onExpandedChange = { openSession = null },
                            heldFor = appState::heldFor,
                            onOpenTrade = appState::openPosition,
                            revealCall = pendingCall,
                            onRevealShown = appState::consumePendingCall,
                            reveal = reveal,
                        )
                    } else {
                        ResponsiveRows(band, columns) { session, cardModifier ->
                            SessionCard(
                                session, report.windowSessions,
                                expanded = false,
                                onExpandedChange = { openSession = session.key() },
                                heldFor = appState::heldFor,
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
private fun OutcomeLabel(call: ScoredCall, windowSessions: Int) {
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
            text = { Text(call.reason(windowSessions)) },
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
private fun ScoredCall.reason(windowSessions: Int): String {
    val on = settledOn?.format(OUTCOME_DATE)
    return when (outcome) {
        Outcome.FULL_HIT -> "Reached target 2 on $on."
        Outcome.PARTIAL_HIT -> when {
            stoppedAfterPartial -> "Reached target 1, then fell back to the stop on $on."
            // Not final yet: target 2 is still in reach, and a card that said only "reached
            // target 1" would read as the verdict.
            !windowComplete ->
                "Reached target 1 on $on; may still reach target 2 before the " +
                    "$windowSessions-session window closes."
            else -> "Reached target 1 on $on."
        }
        Outcome.STOPPED -> "Broke the stop by more than 2% on $on."
        Outcome.EXPIRED -> "The window closed with no target or stop reached."
        Outcome.ENTRY_NOT_REACHED -> "The buy zone never traded in the window."
        Outcome.OPEN -> "Still inside its window, nothing settled yet."
        Outcome.UNPRICED -> "No stored prices for this stock yet."
        // Named as the company's doing rather than the feed's, because that is what it usually is,
        // and because a line blaming the data would read as the app apologising for itself when the
        // stock has simply split.
        Outcome.PRICE_BREAK ->
            "The share price changed scale inside the window - a split or a bonus issue - so the " +
                "levels and the prices are in different money. Not counted for or against."
        Outcome.AMBIGUOUS -> when (ambiguity) {
            Ambiguity.ENTRY_AND_TARGET -> "Opened above the buy zone, target hit that day."
            // Scored before the reason was recorded, so only the fact survives.
            null -> "Two levels were reached in one session and cannot be ordered."
        }
    }
}

private val OUTCOME_DATE = DateTimeFormatter.ofPattern("d MMM")

/**
 * The source with the best record, and what that record is actually made of.
 *
 * The page opens on this rather than on a filter row, because the tab exists to settle one argument.
 * The bar is the argument: a rate on its own hides how much evidence is behind it, and a bar whose
 * length is the sample size cannot.
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
    val scale = ranked.maxOfOrNull(ChannelScore::judged) ?: 0
    val window = "${report.windowSessions}-${report.windowSessions.sessionWord().removeSuffix("s")} window"

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
            OutcomeBar(best, scale = scale, on = MaterialTheme.colorScheme.background)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    formatPercent(best.anyTargetRate, signed = false),
                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = TabularFigures),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "reached a target",
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
    val scale = channels.maxOfOrNull(ChannelScore::judged) ?: 0
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
                    ChannelCard(channel, scale, cardModifier)
                }
            }
        }
    }
}

/** One source, and everything known about how it has done. */
@Composable
private fun ChannelCard(channel: ChannelScore, scale: Int, modifier: Modifier = Modifier) {
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
                // A rate resting on two settled calls is still measured exactly, and still worth
                // nothing as a verdict. It keeps its figure and loses its colour.
                val thin = channel.judged < PerformanceCalculator.MINIMUM_JUDGED_TO_RANK
                Text(
                    formatPercent(channel.anyTargetRate, signed = false),
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
                (if (channel.judged < PerformanceCalculator.MINIMUM_JUDGED_TO_RANK) {
                    "too few judged to rank"
                } else {
                    "reached a target"
                }) + " · ${channel.judged} of ${channel.calls} calls judged",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // The four counts these figures used to print are inside the bar. They partition the
            // judged calls, so they were always a division of one quantity rather than four
            // separate ones - and printed apart, nothing said so, nothing said how the failures
            // divided, and nothing related this source's 94 settled calls to the 3 behind the card
            // underneath it. The bar says all three.
            OutcomeBar(channel, scale = scale, height = OutcomeBarCompact)
            FigureGroup(
                "What that was worth",
                listOf(
                    {
                        Figure(
                            "Target 2 rate", formatPercent(channel.fullHitRate, signed = false),
                            Modifier.weight(1f), tone = channel.fullHitRate.rateTone(),
                        )
                    },
                    {
                        Figure(
                            "Average return", channel.averageReturn.signedPercent(),
                            Modifier.weight(1f), tone = PriceRole.forReturn(channel.averageReturn),
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
private fun ScoredSession.key(): String = (targetDate ?: lastRunAt).toString()

/** Two lines of channel name, so a row of source cards stays level. */
private val ChannelHeaderHeight = 44.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    run: ScoredSession,
    windowSessions: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    /** The position taken on a call, so a stock actually held is marked as such. */
    heldFor: (String, java.time.LocalDate?) -> PositionView?,
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
                        windowSessions,
                        held,
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
    windowSessions: Int,
    held: PositionView?,
    /** Opens the trade taken on this call. Absent where none was, which is most cards. */
    onOpenTrade: (() -> Unit)?,
    highlighted: Boolean,
    onHighlightShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(call.ticker, call.openedOn) { mutableStateOf(false) }
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
                    Text(call.ticker, style = MaterialTheme.typography.titleSmall)
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
                OutcomeLabel(call, windowSessions)
            }
            Text(
                "${call.channel} · called ${call.openedOn}" +
                    (call.settledOn?.let { " · settled $it" } ?: ""),
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
            // Two groups rather than eight loose figures: what the channel asked for, and what the
            // market did about it. Colour says which is which without reading the labels.
            FigureGroup(
                "The call",
                listOf(
                    // Paid and risked first, aimed at second: wrapped two-up this keeps the targets
                    // side by side in reading order rather than splitting them across rows.
                    { Figure("Entry", call.entryRange(), Modifier.weight(1f), tone = PriceRole.entry) },
                    { Figure("Stop loss", formatPrice(call.stopLoss), Modifier.weight(1f), tone = PriceRole.stop) },
                    { Figure("Target 1", formatPrice(call.target1), Modifier.weight(1f), tone = PriceRole.target) },
                    { Figure("Target 2", formatPrice(call.target2), Modifier.weight(1f), tone = PriceRole.target) },
                ),
            )
            FigureGroup(
                "What happened",
                listOf(
                    {
                        Figure(
                            "Peak since call", formatPrice(call.peakHigh), Modifier.weight(1f),
                            tone = PriceRole.market, on = call.peakOn,
                        )
                    },
                    {
                        Figure(
                            "Trough since call", formatPrice(call.troughLow), Modifier.weight(1f),
                            tone = PriceRole.market, on = call.troughOn,
                        )
                    },
                    { Figure("Sessions elapsed", call.sessionsElapsed.toString(), Modifier.weight(1f)) },
                    {
                        Figure(
                            "Return",
                            call.returnPct.signedPercent(),
                            Modifier.weight(1f),
                            tone = PriceRole.forReturn(call.returnPct),
                        )
                    },
                ),
            )
            if (call.sessions.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) {
                            "Hide sessions"
                        } else {
                            "${call.sessions.size} " +
                                (if (call.sessions.size == 1) "session" else "sessions") +
                                " from the price feed"
                        },
                    )
                }
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
 * Four figures that belong together, laid out two-up when the width cannot take four.
 *
 * The cover screen has plenty of height and little width, so wrapping beats shrinking: four
 * columns at 443dp truncates every price it is supposed to show.
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
            val perRow = if (maxWidth >= FourFiguresMinWidth) figures.size else 2
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                figures.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        row.forEach { figure -> figure() }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Four prices need this much before the digits start truncating. */
private val FourFiguresMinWidth = 420.dp

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
        if (on != null) {
            Text(
                on.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")),
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

private fun ScoredCall.entryRange(): String = when {
    entryLow == null && entryHigh == null -> Dash
    entryHigh != null && entryLow != null && entryHigh != entryLow ->
        "${formatPrice(entryLow)} – ${formatPrice(entryHigh)}"
    else -> formatPrice(entryLow ?: entryHigh)
}

private fun Double?.signedPercent(): String = formatPercent(this)

private fun Double.trimZero(): String = formatPrice(this)

private fun Double?.orDash(): String = formatPrice(this)

private fun Int?.orDash(): String = this?.toString() ?: Dash

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
