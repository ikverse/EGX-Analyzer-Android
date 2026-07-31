package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredRun
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

        Headline(report)
        OutcomeBreakdown(report)
        ChannelRanking(report.channels)
        report.runs.forEach { run -> RunCard(run, report.windowSessions) }
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
private fun Headline(report: PerformanceReport) {
    SectionCard {
        BoxWithConstraints {
            // Four tiles need room to breathe; on a phone they wrap to two rows instead of being
            // squeezed until the figures truncate.
            val perRow = if (maxWidth >= WideHeadlineWidth) 4 else 2
            val tiles = listOf(
                report.fullHitRate?.let { "$it%" }.orDash() to "Reached target 2",
                report.anyTargetRate?.let { "$it%" }.orDash() to "Reached target 1+",
                report.judged.toString() to "Calls judged",
                report.tracked.toString() to "Calls tracked",
            )
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                tiles.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (value, label) ->
                            StatTile(
                                value = value,
                                label = label,
                                modifier = Modifier.weight(1f),
                                tone = if (label.startsWith("Reached")) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        repeat(perRow - row.size) { Column(Modifier.weight(1f)) {} }
                    }
                }
            }
        }
        HorizontalDivider()
        Text(
            buildString {
                append(report.scoringSince?.let { "Oldest call scored: $it. " }.orEmpty())
                append(
                    "Only calls that could be judged count toward the hit rate: a stock with no " +
                        "price history, or one whose entry never traded, counts for nobody.",
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Two different reasons a call reads as unpriced, and only one of them is worth acting on.
        if (report.unpricedStocks > 0) {
            StatusPill(
                "${report.unpricedStocks} " +
                    (if (report.unpricedStocks == 1) "stock has" else "stocks have") +
                    " no stored prices — refresh to score them",
                StatusTone.BAD,
            )
        }
        if (report.awaitingSessions > 0) {
            StatusPill(
                "${report.awaitingSessions} " +
                    (if (report.awaitingSessions == 1) "call is" else "calls are") +
                    " waiting for the exchange to publish their sessions",
                StatusTone.NEUTRAL,
            )
        }
    }
}

@Composable
private fun OutcomeBreakdown(report: PerformanceReport) {
    val judged = report.byOutcome.filterKeys(Outcome::judged).values.sum()
    ExpandableSection(
        title = "Outcomes",
        icon = Icons.Outlined.Timeline,
        summary = "${report.fullHits} full · ${report.partialHits} partial · " +
            "${report.byOutcome[Outcome.STOPPED] ?: 0} stopped · ${report.tracked - judged} pending",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Outcome.entries.forEach { outcome ->
                OutcomeChip(outcome, report.byOutcome[outcome] ?: 0)
            }
        }
    }
}

@Composable
private fun OutcomeChip(outcome: Outcome, count: Int) {
    Surface(color = outcome.container(), shape = CircleShape) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                outcome.label,
                style = MaterialTheme.typography.labelMedium,
                color = outcome.onContainer(),
            )
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = outcome.onContainer(),
            )
        }
    }
}

/** The same chip without a figure, for a row that already names the one call it describes. */
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
private fun ChannelRanking(channels: List<ChannelScore>) {
    if (channels.isEmpty()) return
    val best = channels.firstOrNull { it.judged > 0 }
    ExpandableSection(
        title = "Sources ranked",
        icon = Icons.Outlined.Leaderboard,
        summary = best?.let { "Best: ${it.channel} · ${it.anyTargetRate}% reached a target" }
            ?: "${channels.size} sources, none judged yet",
        summaryTone = best?.anyTargetRate.rateTone(),
    ) {
        channels.forEachIndexed { index, channel ->
            if (index > 0) HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        channel.channel,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        channel.anyTargetRate?.let { "$it%" }.orDash(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = channel.anyTargetRate.rateTone(),
                    )
                }
                Text(
                    "${channel.fullHits} full · ${channel.partialHits} partial of " +
                        "${channel.judged} judged · ${channel.calls} calls · " +
                        "avg ${channel.averageReturn.signedPercent()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${channel.stopped} stopped · ${channel.expired} expired · " +
                        "${channel.notTradable} not tradable · " +
                        "target 2 rate ${channel.fullHitRate?.let { "$it%" }.orDash()} · " +
                        "median ${channel.medianSessionsToHit?.trimZero().orDash()} sessions to a target",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One analysis run: what it recommended and how each call turned out. */
@Composable
private fun RunCard(run: ScoredRun, windowSessions: Int) {
    val ranAt = remember(run.completedAt) {
        DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(run.completedAt)
    }
    // Closed by default: a run is a summary line until asked for, so a page of them stays
    // readable however many analyses have been saved.
    // Titled by the session it is about. The run's own timestamp is bookkeeping: two runs of the
    // same session are the same subject, and the date being judged is what the reader is after.
    ExpandableSection(
        title = run.targetDate?.toString() ?: "Target not recorded",
        icon = Icons.Outlined.Assessment,
        summary = "${run.calls.size} ${if (run.calls.size == 1) "call" else "calls"} · " +
            "${run.fullHits} full · ${run.partialHits} partial · ${run.stopped} stopped" +
            if (run.pending > 0) " · ${run.pending} pending" else "",
        summaryTone = if (run.fullHits > 0) MaterialTheme.colorScheme.primary else null,
    ) {
        Text(
            "Run $ranAt · ${run.model}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        run.calls.forEach { call -> ScoredCallRow(call, windowSessions) }
    }
}

@Composable
private fun ScoredCallRow(call: ScoredCall, windowSessions: Int) {
    var expanded by remember(call.ticker, call.openedOn) { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(call.ticker, style = MaterialTheme.typography.titleSmall)
                    // Both names, as the results table shows them: the Arabic name is what the
                    // source wrote, the English one is what the catalog calls the same company.
                    listOfNotNull(call.companyArabic, call.companyEnglish)
                        .filter(String::isNotBlank)
                        .distinct()
                        .takeIf(List<String>::isNotEmpty)
                        ?.let {
                            Text(
                                it.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Figure("Entry", call.entryRange(), Modifier.weight(1f))
                Figure("Target 1", call.target1.orDash(), Modifier.weight(1f))
                Figure("Target 2", call.target2.orDash(), Modifier.weight(1f))
                Figure("Stop", call.stopLoss.orDash(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Figure("High since", call.peakHigh.orDash(), Modifier.weight(1f), on = call.peakOn)
                Figure("Low since", call.troughLow.orDash(), Modifier.weight(1f), on = call.troughOn)
                Figure("Sessions", call.sessionsElapsed.toString(), Modifier.weight(1f))
                Figure(
                    "Return",
                    call.returnPct.signedPercent(),
                    Modifier.weight(1f),
                    tone = call.returnPct.returnTone(),
                )
            }
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
        Row(Modifier.horizontalScroll(scroll)) {
            Column {
                SessionRow("Date", "Open", "High", "Low", "Close", "Volume", header = true)
                sessions.forEach {
                    SessionRow(
                        it.date.toString(),
                        it.open.orDash(),
                        it.high.orDash(),
                        it.low.orDash(),
                        it.close.orDash(),
                        it.volume?.toLong()?.toString().orDash(),
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
    entryHigh != null && entryLow != null && entryHigh != entryLow -> "$entryLow – $entryHigh"
    else -> (entryLow ?: entryHigh).toString()
}

private fun Double?.signedPercent(): String =
    this?.let { "${if (it > 0) "+" else ""}$it%" } ?: Dash

private fun Double.trimZero(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()

private fun Any?.orDash(): String = this?.toString() ?: Dash

/** Four headline tiles fit across only once the pane is wider than a phone in portrait. */
private val WideHeadlineWidth = 520.dp
private const val Dash = "—"
