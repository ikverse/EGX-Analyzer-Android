package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
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
@Composable
internal fun InsightsScreen(appState: AppState) {
    val scope = rememberCoroutineScope()
    val report = appState.performance

    Screen(
        title = "Insights",
        subtitle = "How every saved recommendation turned out, judged against the sessions that followed it.",
    ) {
        PricesBar(
            windowSessions = report.windowSessions,
            refreshing = appState.pricesRefreshing,
            onRefreshPrices = { scope.launch { appState.refreshPrices() } },
        )

        if (report.tracked == 0) {
            EmptyState(
                icon = Icons.Outlined.Insights,
                title = if (report.scoringSince == null) "No prices stored yet" else "Nothing scored yet",
                detail = if (report.scoringSince == null) {
                    "Fetch prices to start scoring. Every stock your analyses have named is priced " +
                        "for the last few trading sessions, and scoring begins at the earliest one."
                } else {
                    "Saved analyses name no stock dated on or after ${report.scoringSince}, which " +
                        "is the first session with stored prices."
                },
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
        var openSession by remember { mutableStateOf<Any?>(null) }
        BoxWithConstraints {
            val columns = responsiveColumns(minColumnWidth = 360.dp, maxColumns = 3)
            // Grouped before rendering rather than while: the run of collapsed cards between two
            // open ones is what forms a row, and that cannot be decided one card at a time.
            val bands = remember(report.sessions, openSession) {
                buildList {
                    val run = mutableListOf<ScoredSession>()
                    report.sessions.forEach { session ->
                        if (openSession == session.key()) {
                            if (run.isNotEmpty()) add(run.toList() to false)
                            run.clear()
                            add(listOf(session) to true)
                        } else {
                            run += session
                        }
                    }
                    if (run.isNotEmpty()) add(run.toList() to false)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                bands.forEach { (band, open) ->
                    if (open) {
                        SessionCard(
                            band.single(), report.windowSessions,
                            expanded = true,
                            onExpandedChange = { openSession = null },
                        )
                    } else {
                        ResponsiveRows(band, columns) { session, cardModifier ->
                            SessionCard(
                                session, report.windowSessions,
                                expanded = false,
                                onExpandedChange = { openSession = session.key() },
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
private fun PricesBar(windowSessions: Int, refreshing: Boolean, onRefreshPrices: () -> Unit) {
    // The window itself lives in Settings; here it is only context for the figures, so one line is
    // enough and the space goes to the results.
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$windowSessions trading ${if (windowSessions == 1) "session" else "sessions"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Scoring window · change it in Settings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onRefreshPrices, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    if (refreshing) "Fetching…" else "Refresh prices",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun OutcomeLabel(outcome: Outcome) {
    Surface(color = outcome.container(), shape = CircleShape) {
        Text(
            outcome.label,
            style = MaterialTheme.typography.labelMedium,
            color = outcome.onContainer(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ColumnScope.ChannelRanking(channels: List<ChannelScore>) {
    if (channels.isEmpty()) return
    // Best first, and a source with nothing judged sinks below one that has been: an untested
    // channel is not a good channel.
    val ordered = channels.sortedWith(
        compareByDescending<ChannelScore> { it.judged > 0 }
            .thenByDescending { it.anyTargetRate ?: -1.0 }
            .thenByDescending { it.judged },
    )
    val best = ordered.firstOrNull { it.judged > 0 }
    ExpandableSection(
        title = "Sources ranked",
        icon = Icons.Outlined.Leaderboard,
        // Open by default: this is the question the tab exists to answer, not a detail to go
        // looking for.
        initiallyExpanded = true,
        // The channel name is Arabic and the figure is not, so a first-strong isolate keeps each
        // in its own direction rather than letting the percent sign drift into the name.
        summary = best?.let {
            "Best: ⁨${it.channel}⁩ · ${formatPercent(it.anyTargetRate, signed = false)} reached a target"
        } ?: "${channels.size} sources, none judged yet",
        summaryTone = best?.anyTargetRate.rateTone(),
    ) {
        BoxWithConstraints {
            val columns = responsiveColumns(minColumnWidth = 320.dp, maxColumns = 2)
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                ResponsiveRows(ordered, columns) { channel, cardModifier ->
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
                Text(
                    formatPercent(channel.anyTargetRate, signed = false),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = channel.anyTargetRate.rateTone(),
                )
            }
            Text(
                "reached a target · ${channel.judged} of ${channel.calls} calls judged",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FigureGroup(
                "How they turned out",
                listOf(
                    { Figure("Target 2", channel.fullHits.toString(), Modifier.weight(1f), tone = PriceRole.target) },
                    { Figure("Target 1", channel.partialHits.toString(), Modifier.weight(1f), tone = PriceRole.target) },
                    { Figure("Stopped", channel.stopped.toString(), Modifier.weight(1f), tone = PriceRole.stop) },
                    { Figure("Expired", channel.expired.toString(), Modifier.weight(1f), tone = PriceRole.muted) },
                ),
            )
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
                            Modifier.weight(1f), tone = channel.averageReturn.returnTone(),
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

/** Identifies a session across recompositions, so the layout knows which card is open. */
private fun ScoredSession.key(): Any = targetDate ?: lastRunAt

/** Two lines of channel name, so a row of source cards stays level. */
private val ChannelHeaderHeight = 44.dp

@Composable
private fun SessionCard(
    run: ScoredSession,
    windowSessions: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
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
            "${run.fullHits} full · ${run.partialHits} partial · ${run.stopped} stopped" +
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
            val columns = responsiveColumns(minColumnWidth = 340.dp, maxColumns = 2)
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                ResponsiveRows(run.calls, columns, spacing = Space.s) { call, cardModifier ->
                    ScoredCallRow(call, windowSessions, cardModifier)
                }
            }
        }
    }
}

@Composable
private fun ScoredCallRow(call: ScoredCall, windowSessions: Int, modifier: Modifier = Modifier) {
    var expanded by remember(call.ticker, call.openedOn) { mutableStateOf(false) }
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
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
                OutcomeLabel(call.outcome)
            }
            Text(
                "${call.channel} · called ${call.openedOn}" +
                    (call.settledOn?.let { " · settled $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            call.qualifier(windowSessions)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            // Two groups rather than eight loose figures: what the channel asked for, and what the
            // market did about it. Colour says which is which without reading the labels.
            FigureGroup(
                "The call",
                listOf(
                    { Figure("Entry", call.entryRange(), Modifier.weight(1f), tone = PriceRole.entry) },
                    { Figure("Target 1", formatPrice(call.target1), Modifier.weight(1f), tone = PriceRole.target) },
                    { Figure("Target 2", formatPrice(call.target2), Modifier.weight(1f), tone = PriceRole.target) },
                    { Figure("Stop loss", formatPrice(call.stopLoss), Modifier.weight(1f), tone = PriceRole.stop) },
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
                            tone = call.returnPct.returnTone(),
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
}

/** Exactly what the price feed reported for each session the call was judged on. */
@Composable
private fun SessionTable(sessions: List<DailySession>) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(scroll).fadingScrollbar(scroll, horizontal = true)) {
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

/** The one-line caveat a card needs, or nothing when the outcome speaks for itself. */
private fun ScoredCall.qualifier(windowSessions: Int): String? = when {
    stoppedAfterPartial -> "Reached target 1, then fell back to the stop."
    outcome == Outcome.PARTIAL_HIT && !windowComplete ->
        "May still reach target 2 within the $windowSessions-session window."
    outcome == Outcome.AMBIGUOUS ->
        "The session's own figures cannot say which level was reached first."
    else -> null
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
        Text(value, style = MaterialTheme.typography.bodyMedium, color = tone, textAlign = TextAlign.Start)
        if (on != null) {
            Text(
                on.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Outcome.container(): Color = when (this) {
    Outcome.FULL_HIT, Outcome.PARTIAL_HIT -> MaterialTheme.colorScheme.tertiaryContainer
    Outcome.STOPPED -> MaterialTheme.colorScheme.errorContainer
    Outcome.EXPIRED -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun Outcome.onContainer(): Color = when (this) {
    Outcome.FULL_HIT, Outcome.PARTIAL_HIT -> MaterialTheme.colorScheme.onTertiaryContainer
    Outcome.STOPPED -> MaterialTheme.colorScheme.onErrorContainer
    Outcome.EXPIRED -> MaterialTheme.colorScheme.onSecondaryContainer
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

@Composable
private fun Double?.returnTone(): Color = when {
    this == null -> MaterialTheme.colorScheme.onSurface
    this > 0 -> MaterialTheme.colorScheme.primary
    this < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
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

