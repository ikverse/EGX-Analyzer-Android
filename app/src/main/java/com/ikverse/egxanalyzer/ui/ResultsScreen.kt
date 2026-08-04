package com.ikverse.egxanalyzer.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ikverse.egxanalyzer.data.RequestTrace
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.AnalysisReport
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.RecommendationResult
import com.ikverse.egxanalyzer.model.SavedAnalysis
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ResultsScreen(activity: Activity, appState: AppState) {
    val scope = rememberCoroutineScope()
    Screen(
        title = "Results",
        subtitle = "Saved recommendations and their exact source traces, on this device and in your Telegram sync channel.",
        onRefresh = { scope.launch { appState.refreshPrices() } },
        refreshing = appState.pricesRefreshing,
        refreshHint = "Pull down to refresh prices",
    ) {
        UnreadableNotice(appState.unreadableResults)
        // Session-only, deliberately: a filter that survived a restart would hide runs from someone
        // who had forgotten it was on.
        var channelFilter by remember { mutableStateOf(emptySet<String>()) }
        var dateFilter by remember { mutableStateOf<String?>(null) }
        var order by remember { mutableStateOf(RunOrder.RUN_NEWEST) }
        val allChannels = remember(appState.savedResults) {
            appState.savedResults.flatMap { it.channelNames() }.distinct().sorted()
        }
        val allDates = remember(appState.savedResults) {
            appState.savedResults.mapNotNull { it.result.recommendationTargetDate?.toString() }
                .distinct()
                .sortedDescending()
        }
        if (appState.savedResults.isNotEmpty()) {
            FilterRow(
                active = channelFilter.isNotEmpty() || dateFilter != null,
                onClearAll = {
                    channelFilter = emptySet()
                    dateFilter = null
                },
            ) {
                MultiSelectFilter(
                    label = "channels",
                    options = allChannels,
                    selected = channelFilter,
                    onToggle = { name ->
                        channelFilter = if (name in channelFilter) {
                            channelFilter - name
                        } else {
                            channelFilter + name
                        }
                    },
                    onClear = { channelFilter = emptySet() },
                )
                SingleSelectFilter(
                    label = "dates",
                    options = allDates,
                    selected = dateFilter,
                    onSelect = { dateFilter = it },
                )
                // Deliberately outside the filters' clear-all: an order is not something a list
                // can be cleared of, and resetting it would look like a filter had gone missing.
                SortFilter(
                    options = RunOrder.entries,
                    selected = order,
                    label = RunOrder::label,
                    onSelect = { order = it },
                )
            }
        }
        val shown = remember(appState.savedResults, dateFilter, channelFilter, order) {
            appState.savedResults
                .filter { saved ->
                    (
                        dateFilter == null ||
                            saved.result.recommendationTargetDate?.toString() == dateFilter
                        ) &&
                        (channelFilter.isEmpty() || saved.channelNames().any { it in channelFilter })
                }
                .sortedWith(order.comparator)
        }
        if (shown.isEmpty() && appState.savedResults.isNotEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Assessment,
                title = "No runs match these filters",
                detail = "Clear a filter to see the rest of your saved analyses.",
            )
        }
        if (appState.savedResults.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Assessment,
                title = "No saved results yet",
                detail = "Run an analysis and it will be stored here with the sources behind it.",
            )
        } else {
            // Which run is open lives here rather than in the card, because a report needs the whole
            // row to show its table - half of one is under the width the table needs and falls back
            // to cards - and a card cannot give itself a row.
            var openRun by remember { mutableStateOf(appState.pendingResultId) }
            // A run arriving from a notification opens itself, whether the screen was already
            // showing or not.
            LaunchedEffect(appState.pendingResultId) {
                appState.pendingResultId?.let { openRun = it }
            }
            BoxWithConstraints {
                val columns = responsiveColumns(minColumnWidth = SavedRunMinWidth, maxColumns = 2)
                // Grouped before rendering rather than while: which cards share a row depends on
                // where the open one sits, and that cannot be decided one card at a time.
                val bands = remember(shown, openRun, columns) {
                    expandableBands(shown, columns) { it.id == openRun }
                }

                @Composable
                fun card(saved: SavedAnalysis, expanded: Boolean, cardModifier: Modifier) {
                    SavedAnalysisCard(
                        modifier = cardModifier,
                        saved = saved,
                        expanded = expanded,
                        onExpandedChange = { open ->
                            openRun = if (open) saved.id else null
                            // Expanding also selects, so the companion pane follows what is open.
                            if (open) appState.selectResult(saved)
                        },
                        highlighted = saved.id == appState.pendingResultId,
                        onHighlightShown = { appState.consumePendingResult() },
                        onShare = { shareReport(activity, appState.reportFor(saved)) },
                        onDelete = { appState.deleteResult(saved) },
                        report = { appState.reportFor(saved) },
                        peakFor = appState::peakSince,
                    )
                }

                // Opening a report puts its top at the top of the view, rather than leaving you
                // halfway down a table you just asked for.
                val reveal = remember { BringIntoViewRequester() }
                LaunchedEffect(openRun) {
                    if (openRun != null) reveal.bringIntoView()
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    bands.forEach { (band, open) ->
                        if (open) {
                            card(
                                band.single(),
                                expanded = true,
                                cardModifier = Modifier.fillMaxWidth().bringIntoViewRequester(reveal),
                            )
                        } else {
                            ResponsiveRows(band, columns) { saved, cardModifier ->
                                card(saved, expanded = false, cardModifier = cardModifier)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * What a saved run needs before two of them share a row.
 *
 * An unfolded Fold is 750dp, which leaves 638dp once the rail and page padding are taken, so the
 * old 380dp minimum asked for 760dp and never once got it on the screen it was meant for.
 */
private val SavedRunMinWidth = 300.dp

/**
 * The orders a saved run can be listed in.
 *
 * Two dates matter and they disagree: the session a report is about, and when it was actually run.
 * A late run for an earlier session sits at the top under one and in the middle under the other.
 *
 * Runs with no target date go last whichever way the target-date orders point, newest run first
 * among themselves, so that block reads the same either way instead of flipping with the arrow.
 */
internal enum class RunOrder(val label: String, val comparator: Comparator<SavedAnalysis>) {
    RUN_NEWEST("Run date, newest", compareByDescending { it.result.completedAt }),
    RUN_OLDEST("Run date, oldest", compareBy { it.result.completedAt }),
    TARGET_NEWEST(
        "Target date, newest",
        compareBy<SavedAnalysis> { it.result.recommendationTargetDate == null }
            .thenByDescending { it.result.recommendationTargetDate }
            .thenByDescending { it.result.completedAt },
    ),
    TARGET_OLDEST(
        "Target date, oldest",
        compareBy<SavedAnalysis> { it.result.recommendationTargetDate == null }
            .thenBy { it.result.recommendationTargetDate }
            .thenByDescending { it.result.completedAt },
    ),
}

/**
 * Which chats a saved run covered.
 *
 * Taken from the selection the run recorded. Analyses saved before that was stored fall back to the
 * chats their rows actually name, which understates a run that read a chat and found nothing.
 */
/** What the model recorded as dating an occurrence, named so it can be ticked off a list. */
internal fun RecommendationDataPoint.timingLabel(): String = timing(this) ?: "Not stated"

/** Every timing this report actually contains, in the order the table lists them. */
private fun AnalysisResult.timings(): List<String> =
    consolidated.flatMap(ConsolidatedRecommendation::dataPoints)
        .map(RecommendationDataPoint::timingLabel)
        .distinct()

internal fun SavedAnalysis.channelNames(): List<String> =
    result.selectedChannels.map { it.name }.filter(String::isNotBlank).distinct()
        .ifEmpty {
            result.recommendations.mapNotNull { it.sourceName.takeIf(String::isNotBlank) }.distinct()
        }

/**
 * Saved runs the store could not read back.
 *
 * These used to be dropped in silence, which left an older report sitting at the top of the list
 * looking like the newest one - the surest way to read a stale report and not know it.
 */
@Composable
private fun UnreadableNotice(count: Int) {
    if (count <= 0) return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Space.l),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(IconSize.Action),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    if (count == 1) "1 saved analysis cannot be read" else "$count saved analyses cannot be read",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    if (count == 1) {
                        "Its stored report is damaged, so it is missing from the list below and " +
                            "the newest report here may be an earlier run."
                    } else {
                        "Their stored reports are damaged, so they are missing from the list " +
                            "below and the newest report here may be an earlier run."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun SavedAnalysisCard(
    modifier: Modifier = Modifier,
    saved: SavedAnalysis,
    /** Held by the screen, which needs it to give an open report a row of its own. */
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    /** Opened from a notification: its edge flashes briefly. */
    highlighted: Boolean = false,
    onHighlightShown: () -> Unit = {},
    onShare: () -> Unit,
    onDelete: () -> Unit,
    report: () -> AnalysisReport,
    /** Highest a stock has traded since the call, for the ladder's arrow. */
    peakFor: (String, LocalDate?) -> Double? = { _, _ -> null },
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    // Asked for, because deleting is no longer local: it removes the report from the sync channel
    // and from every other device, and there is nothing left to restore it from.
    var confirmDelete by remember { mutableStateOf(false) }
    val stockCount = saved.result.consolidated.size.takeIf { it > 0 }
        ?: saved.result.recommendations.map(RecommendationResult::ticker).distinct().size

    // A flash rather than a permanent tint: it answers "which one" on arrival and then gets out
    // of the way, instead of leaving a card looking special long after the reason has passed.
    val flash = rememberInfiniteTransition(label = "arrival")
    val edge by flash.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(FlashHalfCycleMs), RepeatMode.Reverse),
        label = "edge",
    )
    LaunchedEffect(highlighted) {
        if (highlighted) {
            delay(FlashDurationMs)
            onHighlightShown()
        }
    }

    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (highlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = edge))
        } else {
            null
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        saved.result.recommendationTargetDate?.toString() ?: "Target not recorded",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${saved.provider.displayName} · ${saved.model}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (showReport) "Hide report" else "Show report") },
                            leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            onClick = { showReport = !showReport; menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = { menuOpen = false; onShare() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                StatTile(stockCount.toString(), "stocks", tone = MaterialTheme.colorScheme.primary)
                StatTile(saved.result.sources.size.toString(), "sources")
                StatTile("${saved.result.diagnostics.durationMilliseconds / 1000}s", "took")
            }

            saved.channelNames().takeIf(List<String>::isNotEmpty)?.let { names ->
                Text(
                    // Two names and a count: the full list of a six-chat run would wrap to three
                    // lines and push the button off a half-width card.
                    if (names.size <= 2) {
                        names.joinToString(" · ")
                    } else {
                        names.take(2).joinToString(" · ") + " +${names.size - 2}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                saved.result.completedAt.atZone(ZoneId.systemDefault()).format(COMPLETED_FORMAT),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FilledTonalButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (expanded) "Hide recommendations" else "View recommendations")
            }

            AnimatedVisibility(showReport) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    HorizontalDivider()
                    Text(report().title, style = MaterialTheme.typography.titleSmall)
                    Text(report().markdown, style = MaterialTheme.typography.bodySmall)
                }
            }

            AnimatedVisibility(expanded) { ResultDetail(saved, peakFor) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this analysis?") },
            text = {
                Text(
                    "It will be removed from this device and from your Telegram sync channel, so " +
                        "every device drops it too. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) { Text("Delete everywhere") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            },
        )
    }
}

private fun shareReport(activity: Activity, report: AnalysisReport) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_SUBJECT, report.title)
        putExtra(Intent.EXTRA_TEXT, report.markdown)
    }
    activity.startActivity(Intent.createChooser(intent, "Share EGX analysis report"))
}

@Composable
private fun ResultDetail(saved: SavedAnalysis, peakFor: (String, LocalDate?) -> Double?) {
    var detail by remember { mutableStateOf<Pair<ConsolidatedRecommendation, RecommendationDataPoint>?>(null) }
    var showTrace by remember { mutableStateOf(false) }
    var openImage by remember { mutableStateOf<Int?>(null) }
    // The model cites sources by Telegram id; the channel name lives on the stored trace.
    val channelNames = remember(saved.id) {
        saved.result.sources
            .filter { it.messageId != null }
            .associate { it.messageId.toString() to it.channelName }
    }

    // Session-only, and per report: a timing hidden here is a row someone chose not to read now,
    // not a preference about every report they open later.
    val timings = remember(saved.id) { saved.result.timings() }
    var shownTimings by remember(saved.id) { mutableStateOf(timings.toSet()) }
    val stocks = remember(saved.id, shownTimings) {
        saved.result.consolidated
            .map { stock -> stock.copy(dataPoints = stock.dataPoints.filter { it.timingLabel() in shownTimings }) }
            .filter { it.dataPoints.isNotEmpty() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        FilterRow(
            active = shownTimings.size < timings.size,
            onClearAll = { shownTimings = timings.toSet() },
        ) {
            CheckedSetFilter(
                label = "timings",
                options = timings,
                shown = shownTimings,
                onToggle = { name ->
                    shownTimings = if (name in shownTimings) shownTimings - name else shownTimings + name
                },
                onSelectAll = { shownTimings = timings.toSet() },
            )
        }
        if (stocks.isEmpty() && saved.result.consolidated.isNotEmpty()) {
            Text(
                "No recommendations match these timings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Analyses saved before the consolidated contract have no nested occurrences, so they fall
        // back to a flat list rather than showing an empty table.
        if (saved.result.consolidated.isNotEmpty()) {
            BoxWithConstraints {
                if (maxWidth >= TableMinWidth) {
                    RecommendationTable(
                        stocks = stocks,
                        channelFor = { messageId -> channelNames[messageId] },
                        imagePathFor = { ref -> saved.result.imagePathFor(ref) },
                        onOpenImage = { ref -> openImage = ref },
                        onSelectPoint = { stock, point -> detail = stock to point },
                    )
                } else {
                    // A sixteen-column table on a cover screen is a scroll bar with numbers behind
                    // it. The same figures as cards stay readable without any horizontal scrolling.
                    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                        stocks.forEach { stock ->
                            RecommendationCards(
                                stock = stock,
                                channelFor = { messageId -> channelNames[messageId] },
                                peakFor = peakFor,
                            )
                        }
                    }
                }
            }
        } else {
            saved.result.recommendations.forEach { LegacyDetail(it) }
        }

        TextButton(onClick = { showTrace = !showTrace }) {
            Text(if (showTrace) "Hide source trace" else "Source trace and diagnostics")
        }
        AnimatedVisibility(showTrace) { TraceAndDiagnostics(saved) }
    }

    detail?.let { (stock, point) ->
        OccurrenceSheet(
            stock = stock,
            point = point,
            imagePath = saved.result.imagePathFor(point.sourceImageRef),
            peak = peakFor(stock.stockCode, point.date),
            onDismiss = { detail = null },
        )
    }
    openImage?.let { ref ->
        SourceImageViewer(saved.result.imagePathFor(ref), ref, onDismiss = { openImage = null })
    }
}

/** The raw trace, kept collapsed because it is for checking the app rather than reading results. */
@Composable
private fun TraceAndDiagnostics(saved: SavedAnalysis) {
    val diagnostics = saved.result.diagnostics
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text("Sources", style = MaterialTheme.typography.titleSmall)
        saved.result.sources.forEach { source ->
            Text(
                "${source.channelName} · ${source.contentType} · ${source.preview.take(60)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (saved.result.modelExclusions.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            // What the model says it dropped. Worth as much attention as what it kept: an
            // over-eager exclusion is invisible everywhere else.
            Text("Excluded by the model", style = MaterialTheme.typography.titleSmall)
            saved.result.modelExclusions.forEach { dropped ->
                Text(
                    listOfNotNull(
                        dropped.stockCode,
                        dropped.visibleSourceDate,
                        dropped.reason.replace('_', ' '),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (diagnostics.unaccountedImages.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            // Neither kept nor rejected. The gap is shown because its absence is what let a card
            // headed with the target session disappear from a report without a word.
            Text("Images the model never mentioned", style = MaterialTheme.typography.titleSmall)
            diagnostics.unaccountedImages.forEach { missing ->
                Text(
                    "Image ${missing.reference}: ${missing.caption?.take(80) ?: "no caption"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text("Diagnostics", style = MaterialTheme.typography.titleSmall)
        Text(
            "${diagnostics.acceptedInputCount}/${diagnostics.inputCount} inputs accepted · " +
                "${diagnostics.excludedSources.size} filtered · " +
                "${diagnostics.validationWarnings.size} warnings · " +
                "${diagnostics.durationMilliseconds} ms",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val traceDirectory = java.io.File(
            java.io.File(LocalContext.current.filesDir, RequestTrace.TRACE_ROOT),
            saved.result.requestId,
        )
        if (traceDirectory.isDirectory) {
            // What was actually sent, not what we believe was sent. Reconstructing a request from
            // the sources table is how a mis-cited image went unnoticed for two runs.
            Text(
                "Request trace: ${traceDirectory.listFiles()?.size ?: 0} file(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                traceDirectory.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (diagnostics.requestCount > 0) {
            Text(
                "${diagnostics.requestCount} model requests · ${diagnostics.imagesSent} images sent · " +
                    "${diagnostics.unaccountedImages.size} unaccounted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        diagnostics.excludedSources.forEach {
            Text(
                "Excluded ${it.sourceId}: ${it.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        diagnostics.validationWarnings.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Detail for analyses saved before the consolidated contract existed. */
@Composable
private fun LegacyDetail(recommendation: RecommendationResult) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            "${recommendation.ticker} · ${recommendation.companyName}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "${recommendation.signal}" +
                (recommendation.confidence?.let { " · ${"%.0f".format(it * 100)}%" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        recommendation.entryLow?.let {
            Text(
                "Entry $it – ${recommendation.entryHigh ?: it}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        recommendation.takeProfit1?.let {
            Text("Target $it", style = MaterialTheme.typography.bodySmall)
        }
        recommendation.stopLoss?.let {
            Text("Stop $it", style = MaterialTheme.typography.bodySmall)
        }
        recommendation.notesArabic?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider()
}

private val COMPLETED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")

/** IMAGE_REF is one-based over the images sent with the request. */
private fun AnalysisResult.imagePathFor(reference: Int?): String? =
    reference?.let { imagePaths.getOrNull(it - 1) }

/** Long enough to catch the eye on arrival, short enough not to become decoration. */
private const val FlashHalfCycleMs = 420
private const val FlashDurationMs = 2_400L

/** Below this a table can only be read by scrolling it sideways, which is not reading. */
private val TableMinWidth = 600.dp
