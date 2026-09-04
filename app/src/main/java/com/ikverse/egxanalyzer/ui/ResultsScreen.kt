package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.timing


import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ikverse.egxanalyzer.model.AnalysisDiagnostics
import com.ikverse.egxanalyzer.model.AnalysisReport
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.RecommendationResult
import com.ikverse.egxanalyzer.model.SavedAnalysis
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ResultsScreen(appState: AppState) {
    val scope = rememberCoroutineScope()
    Screen(
        title = "Results",
        onRefresh = { scope.launch { appState.refreshPrices() } },
        refreshing = appState.pricesRefreshing,
    ) {
        UnreadableNotice(appState.unreadableResults)
        // Session-only, deliberately: a filter that survived a restart would hide runs from someone
        // who had forgotten it was on. Held on AppState rather than remembered here, so that folding
        // the phone - which rebuilds this whole page from nothing - does not reset it either. It
        // still dies with the process, which is the sense in which it was always session-only. See
        // PageState.
        var channelFilter by appState.pages.resultsChannels
        var dateFilter by appState.pages.resultsDate
        var stockFilter by appState.pages.resultsStock
        var order by appState.pages.resultsOrder
        val allChannels = remember(appState.savedResults) {
            appState.savedResults.flatMap { it.channelNames() }.distinct().sorted()
        }
        val allDates = remember(appState.savedResults) {
            appState.savedResults.mapNotNull { it.result.recommendationTargetDate?.toString() }
                .distinct()
                .sortedDescending()
        }
        if (appState.savedResults.isNotEmpty()) {
            // Room between the page's title and the shelf under it. On the column's own 12 the bar
            // sat right beneath the headline and read as part of it rather than as the first thing
            // on the page - and it is the one row here that never scrolls away, so it is the row
            // that can least afford to look crowded. A spacer rather than padding on the bar: the
            // bar measures its own position to pin itself, and anything wrapped around it either
            // travels with it or becomes the parent it clamps against.
            Spacer(Modifier.height(Space.xs))
            FilterBar(
                // The shell asks the same question to decide what a back press means, so the
                // predicate lives on PageState and both read it there. See PageState.filtersActive.
                active = appState.pages.filtersActive(AppDestination.RESULTS),
                // Only the folded pair. The search box is on show even on a cover screen, so a chip
                // lit by it would be reporting something the reader is already looking at.
                folded = channelFilter.isNotEmpty() || dateFilter != null,
                onClearAll = { appState.pages.clearFilters(AppDestination.RESULTS) },
                // Never folded away, for the reason it leads inside a report too: it is the control
                // someone arrives at the screen already knowing they want, and the only one that
                // can empty the list on a single keystroke.
                search = { m ->
                    StockFilterField(stockFilter, { stockFilter = it }, modifier = m)
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
        val shown = remember(appState.savedResults, dateFilter, channelFilter, stockFilter, order) {
            // Normalized once for the whole list rather than once per run: the same question is put
            // to every stock of every saved analysis.
            val wanted = StockSearch.query(stockFilter)
            appState.savedResults
                .filter { saved ->
                    (
                        dateFilter == null ||
                            saved.result.recommendationTargetDate?.toString() == dateFilter
                        ) &&
                        (channelFilter.isEmpty() || saved.channelNames().any { it in channelFilter }) &&
                        saved.result.consolidated.hasStockMatching(wanted)
                }
                .sortedWith(order.comparator)
        }
        if (shown.isEmpty() && appState.savedResults.isNotEmpty()) {
            // The stock is named when it is what emptied the list: "no runs match these filters"
            // beside a box holding COMI reads as though the app had not noticed what was typed.
            val searching = stockFilter.isNotBlank()
            EmptyState(
                icon = Icons.Outlined.Assessment,
                title = if (searching) "No runs mention ${stockFilter.trim()}" else "No runs match these filters",
                detail = if (searching) {
                    "No saved analysis holds a stock by that code or name. " +
                        "Clear the stock filter to see the rest."
                } else {
                    "Clear a filter to see the rest of your saved analyses."
                },
            )
        }
        if (appState.savedResults.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Assessment,
                title = "No saved results yet",
                detail = "Run an analysis and it will be stored here with the sources behind it.",
            )
        } else {
            // Which run is open lives outside the card, because a report needs the whole row to show
            // its table - half of one is under the width the table needs and falls back to cards -
            // and a card cannot give itself a row. On AppState rather than in a `remember` because
            // this is the one the reader notices going: opening a report on the cover screen and
            // unfolding the phone put them back on the list of runs.
            //
            // Not seeded from pendingResultId any more. The effect below opens a run arriving from a
            // notification and runs on first composition, so the seed only ever repeated it.
            var openRun by appState.pages.openResultId
            var openReports by appState.pages.openReportMarkdown
            // The newest run held for each session, so a card can tell whether it is the current
            // reading of its session or an earlier one a re-run has since covered.
            val newestRunFor = remember(appState.savedResults) {
                appState.savedResults
                    .mapNotNull { saved ->
                        saved.result.recommendationTargetDate?.let { it to saved.result.completedAt }
                    }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, runAt) -> runAt.max() }
            }
            // A run arriving from a notification opens itself, whether the screen was already
            // showing or not.
            LaunchedEffect(appState.pendingResultId) {
                appState.pendingResultId?.let { openRun = it }
            }
            BoxWithConstraints {
                val columns = responsiveColumns(minColumnWidth = SavedRunMinWidth, maxColumns = 2)
                // One entry per session rather than per run: a session re-read three times is one
                // row of the list, swiped through, newest reading in front. Done before the bands
                // are cut because an open run takes the whole width and its whole stack goes with
                // it - a day cannot be half in a grid row and half out of it.
                val days = remember(shown) { groupRunsByDay(shown) }
                // Grouped before rendering rather than while: the open run interrupts the grid, and
                // where it does so cannot be decided one card at a time.
                val bands = remember(days, openRun) {
                    expandableBands(days) { day -> day.any { it.id == openRun } }
                }

                @Composable
                fun card(
                    saved: SavedAnalysis,
                    expanded: Boolean,
                    stack: StackPosition?,
                    cardModifier: Modifier,
                ) {
                    SavedAnalysisCard(
                        modifier = cardModifier,
                        saved = saved,
                        stack = stack,
                        // Built per run: every card inside it dates its call from this run's target
                        // session, which is what the scorer does too.
                        trades = remember(appState, saved.id) {
                            TradeBook(appState, saved.result.recommendationTargetDate)
                        },
                        expanded = expanded,
                        onExpandedChange = { open ->
                            openRun = if (open) saved.id else null
                            // Expanding also selects, so the companion pane follows what is open.
                            if (open) appState.selectResult(saved)
                        },
                        // Hoisted for the same reason `expanded` is, and kept by run id so that
                        // re-sorting the list cannot hand one card's open report to another.
                        showReport = saved.id in openReports,
                        onShowReportChange = { show ->
                            openReports =
                                if (show) openReports + saved.id else openReports - saved.id
                        },
                        highlighted = saved.id == appState.pendingResultId,
                        onHighlightShown = { appState.consumePendingResult() },
                        newerRunExists = saved.result.recommendationTargetDate
                            ?.let { newestRunFor[it]?.isAfter(saved.result.completedAt) } == true,
                        onShare = { appState.shareReport(saved) },
                        onSaveLocally = { scope.launch { appState.saveReportToDownloads(saved) } },
                        onExport = { scope.launch { appState.exportReport(saved) } },
                        onDelete = { appState.deleteResult(saved) },
                        report = { appState.reportFor(saved) },
                        peakFor = appState::peakSince,
                        traceRoot = appState.traceRoot(),
                        stockFilter = stockFilter,
                    )
                }

                @Composable
                fun stack(day: List<SavedAnalysis>, open: Boolean, stackModifier: Modifier) {
                    SavedRunStack(
                        runs = day,
                        // Only while this day is the open one. A stack that is shut has no open
                        // run to keep in step with, and its pager is free to be swiped.
                        openRunId = openRun.takeIf { open },
                        modifier = stackModifier,
                    ) { saved, expanded, stack, cardModifier ->
                        card(saved, expanded, stack, cardModifier)
                    }
                }

                // Opening a report puts its top at the top of the view, rather than leaving you
                // halfway down a table you just asked for.
                val reveal = remember { BringIntoViewRequester() }
                val settled = LocalTabsSettled.current
                LaunchedEffect(openRun) {
                    if (openRun != null) {
                        reveal.revealIfOnScreen(appState, AppDestination.RESULTS, settled)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    bands.forEach { (band, open) ->
                        if (open) {
                            val day = band.single()
                            // Keyed by the run in front, so that re-sorting the list, or a new run
                            // arriving for this session, cannot hand one day's page to another.
                            key(day.first().id) {
                                stack(
                                    day,
                                    open = true,
                                    stackModifier = Modifier.fillMaxWidth()
                                        .bringIntoViewRequester(reveal),
                                )
                            }
                        } else {
                            ResponsiveRows(band, columns) { day, cardModifier ->
                                key(day.first().id) { stack(day, open = false, cardModifier) }
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
 * How much of the card behind shows past the side and the foot of the one in front.
 *
 * Enough to read as a second card and no more. The inset is what makes it read as behind rather
 * than beside: an edge running the full height of the card in front is a border, not a card under
 * it, so the card behind is held down from the top while it shows past the bottom.
 *
 * It was 6dp and 8dp and read as neither. Both cards are `surfaceContainer` with the same hairline,
 * so six units of a second one against the page is a slab with a line through it - the mistake the
 * filter shelf made with `surfaceContainerLow` on this palette, where six units of difference reads
 * as one continuous ground rather than two surfaces. Widening alone would have made a fatter
 * border; what separates them is [StackBackAlpha].
 */
private val StackEdgeDepth = 10.dp
private val StackEdgeInset = 10.dp

/**
 * How much of the row's width each card gives up so the peek has somewhere to sit.
 *
 * Half the depth on each side rather than the whole of it on the right. Reserved on the right
 * alone, the front card sat ten units nearer the left edge of the screen than the right and read
 * as hung crooked - the peek is drawn behind the card, so nothing filled the gap it left. Taken
 * evenly the card is centred in its row again, and the card behind still shows down the right
 * because the offset that puts it there is measured from the front card, not from the screen.
 *
 * Charged to every day and not only the ones that were run twice: a card ten units wider than the
 * one under it is the mismatch the pager's own comment refuses a peek to avoid.
 */
private val StackSideInset = StackEdgeDepth / 2

/**
 * How solid the card behind is drawn, once it is a whole page back.
 *
 * Depth is dimness. It is the half of the peek that says the strip is a card lying under this one
 * rather than an edge drawn around it, and it is what stops two identical fills meeting at a
 * hairline. Interpolated on the same distance the offset and the shrink use, so a card coming
 * forward gains its weight back over exactly the travel it gains its size back over.
 */
private const val StackBackAlpha = 0.6f

/**
 * How big the card behind is drawn, as a fraction of the one in front, and how far the card being
 * turned swells as it leaves.
 *
 * Both are small on purpose. A card further back is a smaller card, and that is most of what
 * separates a stack from a border drawn down one side - but a shrink deep enough to notice on its
 * own turns a stack of readings into a fanned deck of playing cards, and the type on the card
 * behind starts to look as though it is set at a different size to the type on the one in front.
 */
private const val StackBackScale = 0.94f
private const val StackFrontLift = 1.03f

/**
 * The gap the pager leaves between two readings of a session.
 *
 * Named rather than written twice: a card behind cancels the pager's own travel to hold its place
 * in the stack, and travel is a page plus this gap. The two drifting apart would leave the card
 * creeping sideways under the thumb.
 */
private val StackPageSpacing = Space.s

/**
 * The orders a saved run can be listed in.
 *
 * Two dates matter and they disagree: the session a report is about, and when it was actually run.
 * A late run for an earlier session sits at the top under one and in the middle under the other.
 *
 * Runs with no target date go last whichever way the target-date orders point, newest run first
 * among themselves, so that block reads the same either way instead of flipping with the arrow.
 */
enum class RunOrder(val label: String, val comparator: Comparator<SavedAnalysis>) {
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
 * Every run of one session, gathered into a single row of the list.
 *
 * The session is the target date, not the day the run happened: a session read again the next
 * morning is a second reading of the same day's calls, which is the thing worth putting behind one
 * card. Two runs made in one evening for two different sessions are two rows, as they should be.
 *
 * Order is never touched. A day sits where its first run sat, and the runs inside it stay in the
 * order the chosen sort put them - which is newest run first under three of the four sorts, and
 * oldest first under "Run date, oldest", where first is exactly what was asked for.
 *
 * A run with no target date is a row of its own. There is no day to gather it into, and quietly
 * heaping the undated ones together would claim they belong to one session.
 */
internal fun groupRunsByDay(runs: List<SavedAnalysis>): List<List<SavedAnalysis>> {
    val days = mutableListOf<MutableList<SavedAnalysis>>()
    val byTarget = mutableMapOf<LocalDate, MutableList<SavedAnalysis>>()
    runs.forEach { run ->
        val target = run.result.recommendationTargetDate
        if (target == null) {
            days += mutableListOf(run)
            return@forEach
        }
        val started = byTarget[target]
        if (started != null) {
            started += run
        } else {
            val fresh = mutableListOf(run)
            byTarget[target] = fresh
            days += fresh
        }
    }
    return days
}

/** The channel behind an occurrence, named so it can be ticked off a list. */
internal fun RecommendationDataPoint.channelLabel(channelFor: Map<String, String>): String =
    channelFor[sourceMessageId]?.takeIf(String::isNotBlank) ?: "Not stated"

/** Every channel this report actually contains, in the order the table lists them. */
private fun AnalysisResult.channelLabels(channelFor: Map<String, String>): List<String> =
    consolidated.flatMap(ConsolidatedRecommendation::dataPoints)
        .map { it.channelLabel(channelFor) }
        .distinct()

/** What the model recorded as dating an occurrence, named so it can be ticked off a list. */
internal fun RecommendationDataPoint.timingLabel(): String = timing(this) ?: "Not stated"

/** Every timing this report actually contains, in the order the table lists them. */
private fun AnalysisResult.timings(): List<String> =
    consolidated.flatMap(ConsolidatedRecommendation::dataPoints)
        .map(RecommendationDataPoint::timingLabel)
        .distinct()

/**
 * Which chats a saved run covered.
 *
 * Taken from the selection the run recorded. Analyses saved before that was stored fall back to the
 * chats their rows actually name, which understates a run that read a chat and found nothing.
 */
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
        border = cardOutline,
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

/**
 * Every reading of one session, as a stack of cards the reader swipes through.
 *
 * A session re-run three times used to fill three cards of the grid with the same date, and the
 * only thing separating them was a line of small print naming the time each was run at. One row per
 * session, with the readings behind it, is what the list was always describing.
 *
 * A day with a single run draws exactly as it always did: no dots, no edge. Most days are that day,
 * and a stack drawn around a stack of one is chrome reporting nothing.
 *
 * The card behind shows down its side and along its foot, offset down-and-right and drawn a little
 * smaller, like a card laid under this one rather than a border drawn around it. Sitting it back at
 * full size is what made it read as a border: a card further away is a smaller card, and the shrink
 * is most of the difference. The edge to the right is the one that says which way to push; the edge
 * below says there is more of the same behind it.
 *
 * The peek is the real card behind, not a blank standing in for one, so there is nothing for the
 * card to land on and no step where a ghost is swapped for the thing it stood in for. Under the
 * thumb it grows into the front slot; the card being turned lifts fractionally and fades on a
 * squared curve, so it has gone before it has crossed the one behind rather than dragging across it.
 */
@Composable
private fun SavedRunStack(
    runs: List<SavedAnalysis>,
    /** The run whose report is open, when it is one of these. Null while the whole stack is shut. */
    openRunId: Long?,
    modifier: Modifier = Modifier,
    card: @Composable (SavedAnalysis, Boolean, StackPosition?, Modifier) -> Unit,
) {
    if (runs.size == 1) {
        // The same side inset a stacked day takes, so one width runs down the whole list.
        val inset = if (openRunId != null) Modifier else Modifier.padding(horizontal = StackSideInset)
        card(runs.single(), openRunId != null, null, modifier.then(inset))
        return
    }
    val open = openRunId != null
    val openIndex = runs.indexOfFirst { it.id == openRunId }
    val pager = rememberPagerState(
        // Opening the day at whichever run is open, rather than at the front: a report opened from
        // a notification can be any reading of the session, including one behind the front card.
        initialPage = openIndex.coerceAtLeast(0),
        pageCount = { runs.size },
    )
    LaunchedEffect(openIndex) {
        if (openIndex >= 0 && openIndex != pager.currentPage) pager.scrollToPage(openIndex)
    }
    // How tall the tallest shut card of this session stands, which every other one is then held to.
    // A run naming six chats is two lines taller than one naming two, and a stack that changed
    // height under the thumb read as the page moving rather than as a card being turned.
    //
    // Measured rather than asked for. `IntrinsicSize` is the obvious way to write this and it
    // throws: these cards contain a `SubcomposeLayout`, and intrinsic measurement of one is
    // unsupported - the trap `ResponsiveRows` and `AdaptivePanes` both carry a warning about. Read
    // back this way the floor can only grow, so it settles in one pass.
    var tallest by remember(runs) { mutableIntStateOf(0) }
    val floor = with(LocalDensity.current) { tallest.toDp() }
    // How the card settles once the thumb is off it. The stock snap runs a card to its stop and
    // halts it dead, which on a short flick - the way a stack of two or three is actually read - is
    // most of what read as stiff. A spring damped just under one lands it without a bounce a reader
    // would have to watch twice to be sure of.
    // The one number the whole stack is drawn from: the cards' transforms, the dots' marker and the
    // line of type beside it all read this, so nothing on screen can report a different swipe from
    // anything else. Built once per stack rather than per card - three cards each holding their own
    // reader of one pager is three things to keep in step.
    val stackPosition = remember(pager, runs.size) {
        StackPosition(runs.size) { pager.currentPage + pager.currentPageOffsetFraction }
    }
    val fling = PagerDefaults.flingBehavior(
        state = pager,
        snapAnimationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow,
        ),
    )
    Box(modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            flingBehavior = fling,
            // Open, the report takes back the strip the card behind was showing through, so it
            // runs out to the edge rather than stopping short of one for a card that is no longer
            // drawn.
            modifier = if (open) {
                Modifier
            } else {
                Modifier.padding(
                    start = StackSideInset,
                    end = StackSideInset,
                    bottom = StackEdgeInset,
                )
            },
            // No peek. Insetting only the sessions that were run twice would make their cards
            // narrower than the rest, and a grid row of cards that do not match reads as a fault
            // before it reads as a hint. The dots and the edge carry the hint instead.
            pageSpacing = StackPageSpacing,
            verticalAlignment = Alignment.Top,
            // Nothing is swiped past an open report. It holds its own sideways pager over a stock's
            // occurrences and tables that scroll sideways, and a drag landing on either would be
            // caught by two things at once. Closing it is what puts the stack back under the thumb.
            userScrollEnabled = !open,
        ) { page ->
            val saved = runs[page]
            val expanded = saved.id == openRunId
            card(
                saved,
                expanded,
                // The stack's own position and not this card's page, so every card in it reads the
                // same gesture: what the dots report is where the stack stands, which is the one
                // thing a card behind and a card in front cannot disagree about.
                stackPosition,
                if (expanded) {
                    // An open report is as tall as its report. Holding it to the shut cards' floor
                    // would be measuring it against something it is not.
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth()
                        // A lower run number is always the card on top. Left to the pager, page 2
                        // draws over page 1, and the card that is meant to be underneath swipes
                        // across the front of the one it is behind.
                        .zIndex(-page.toFloat())
                        .graphicsLayer {
                            // How far this card is from the front, in pages: 0 while it is the
                            // one in front, 1 where it sits one back, and fractional under the
                            // thumb. Read here rather than in the composable body - read at
                            // composition every card of every stack would recompose for each
                            // frame of a drag; read in the layer they only redraw.
                            val distance =
                                (page - pager.currentPage) + pager.currentPageOffsetFraction
                            // Every card grows and shrinks about the middle of its own top edge.
                            // About its centre the shrink pulls the right-hand side inwards and
                            // eats the very peek the offset is drawn to show; held at the top, the
                            // card sits down and to the side and keeps its edge proud.
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            if (distance > 0f) {
                                // Behind: this card does not travel with the pager at all. Its
                                // translation cancels the scroll and puts it at the resting offset
                                // instead, so it grows into the front slot as the card over it
                                // leaves rather than sliding in from off-screen.
                                val t = distance.coerceAtMost(1f)
                                val travelled = distance * (size.width + StackPageSpacing.toPx())
                                translationX = StackEdgeDepth.toPx() * t - travelled
                                translationY = StackEdgeInset.toPx() * t
                                val shrunk = 1f - (1f - StackBackScale) * t
                                scaleX = shrunk
                                scaleY = shrunk
                                // Everything further back than the first card behind sits at that
                                // same resting offset, exactly covered by it. Drawing the rest of a
                                // long stack there is a pile of full-height cards redrawn every
                                // frame for pixels nobody can see. What is still drawn dims as it
                                // goes back, which is what makes the strip read as a card under
                                // this one rather than a border around it.
                                alpha = if (distance > 1.5f) {
                                    0f
                                } else {
                                    1f - (1f - StackBackAlpha) * t
                                }
                            } else {
                                // In front, on its way out: it slides as the pager takes it, lifts
                                // fractionally, and fades on a squared curve so it has gone by
                                // about two thirds of the travel. Faded flat it stayed legible
                                // across the card behind for the whole swipe, and two readings of
                                // the same session laid over each other is unreadable in the way
                                // that reads as a fault before it reads as motion. The lift is what
                                // makes the leaving read as a card taken off the top rather than a
                                // page shoved sideways.
                                val gone = (-distance).coerceIn(0f, 1f)
                                val remaining = 1f - gone
                                alpha = remaining * remaining
                                val lifted = 1f + (StackFrontLift - 1f) * gone
                                scaleX = lifted
                                scaleY = lifted
                            }
                        }
                        .onSizeChanged { if (it.height > tallest) tallest = it.height }
                        .heightIn(min = floor)
                },
            )
        }
    }
}

/**
 * Where the stack stands, for the dots and the line of type beside them.
 *
 * A live position rather than this card's own page number, because the readout is the gesture: the
 * marker travels with the cards on the same fraction they move on, and the words turn over as it
 * crosses the halfway mark. A lambda for the reason the cards' transforms are read in their layer -
 * read as a value, every card of every stack would recompose for each frame of a drag.
 */
private class StackPosition(val count: Int, val position: () -> Float)

@Composable
private fun SavedAnalysisCard(
    modifier: Modifier = Modifier,
    saved: SavedAnalysis,
    /** Which reading of its session this is, where the session was read more than once. */
    stack: StackPosition? = null,
    /** Records what the user did about the calls in this run. */
    trades: TradeBook,
    /** Held by the screen, which needs it to give an open report a row of its own. */
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    /** Whether the written report is showing. Held by the screen so a fold cannot close it. */
    showReport: Boolean,
    onShowReportChange: (Boolean) -> Unit,
    /** Opened from a notification: its edge flashes briefly. */
    highlighted: Boolean = false,
    onHighlightShown: () -> Unit = {},
    /** Whether a later run covered the same session, which makes this report the older reading. */
    newerRunExists: Boolean = false,
    onShare: () -> Unit,
    /** The same table as a spreadsheet, written to the phone's own Downloads folder. */
    onSaveLocally: () -> Unit,
    /** The same file again, handed to whatever the user picks to send it with. */
    onExport: () -> Unit,
    onDelete: () -> Unit,
    report: () -> AnalysisReport,
    /** Highest a stock has traded since the call, for the ladder's arrow. */
    peakFor: (String, LocalDate?) -> Double? = { _, _ -> null },
    /** Where request traces are kept, so the diagnostics list can count this run's. */
    traceRoot: File,
    /** What the screen is searching for, which the report opens already narrowed to. */
    stockFilter: String = "",
) {
    // Stays local and dies with the card, deliberately: a dropdown left hanging over a page that has
    // just been rebuilt into a different shape is not where the reader left anything.
    var menuOpen by remember { mutableStateOf(false) }
    // Asked for, because deleting is no longer local: it removes the report from the sync channel
    // and from every other device, and there is nothing left to restore it from.
    var confirmDelete by remember { mutableStateOf(false) }
    val stockCount = saved.result.consolidated.size.takeIf { it > 0 }
        ?: saved.result.recommendations.map(RecommendationResult::ticker).distinct().size
    // Every occurrence in the report, which is what the table below actually lists: one stock named
    // by three channels is three readings to check, not one.
    val callCount = saved.result.consolidated.sumOf { it.dataPoints.size }
        .takeIf { it > 0 } ?: saved.result.recommendations.size
    // Positions rather than occurrences: a stock two channels called on the same session is one
    // trade, because a position's identity is the call it was taken on. Deliberately not
    // remembered - recording a trade has to change the figure while the card is on screen.
    val tradedCount = saved.result.consolidated
        .flatMap { stock -> stock.dataPoints.mapNotNull { trades.heldFor(stock, it)?.position?.id } }
        .distinct()
        .size

    Card(
        // The whole card opens the run, and only while it is shut. Open, it holds the report's own
        // toolbar, its call cards and the source trace, so a card-wide toggle would close the whole
        // report on a tap that landed in the gap between any two of them - and the reader would have
        // no idea what they had pressed. The footer row below is what closes it again.
        onClick = { onExpandedChange(true) },
        modifier = modifier.fillMaxWidth(),
        enabled = !expanded,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            // Pinned to the same fill: "disabled" here means the report is open, which is not a
            // state a card should go grey for.
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = arrivalFlash(highlighted, onHighlightShown) ?: cardOutline,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            // Top-aligned: the heading below runs to two lines and a menu centred against both sits
            // level with neither. The floor is what keeps two cards in a grid row level: a report
            // older than a week gets no relative word, and would otherwise stand a line shorter
            // than the one beside it.
            Row(Modifier.heightIn(min = RunHeaderHeight), verticalAlignment = Alignment.Top) {
                // Spaced rather than butted together. Three lines of type at three sizes with
                // nothing between them read as one block to be picked apart, which is what made
                // the head of the card feel packed while the foot sat empty.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    // The session the report is about, which is the first thing anyone reads a
                    // card for and was previously the raw stored date.
                    Text(
                        saved.result.recommendationTargetDate?.format(TARGET_FORMAT)
                            ?: "Target not recorded",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    // Always its own line, never beside the date. Sharing a line meant the date was
                    // measured first and the word took what was left, which broke "Yesterday" in
                    // half on the narrower cards.
                    saved.result.recommendationTargetDate?.let(::relativeSession)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                    // Both dates on one line and each labelled. The run time used to sit four rows
                    // below the target session with nothing to say which was which.
                    Text(
                        "${saved.provider.displayName} · ${saved.model} · ran " +
                            saved.result.completedAt.atZone(ZoneId.systemDefault())
                                .format(COMPLETED_FORMAT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // One line, always. Wrapped, it took a second line to show three more
                        // characters of a model name and snapped the run date mid-word - two lines
                        // of small print that said less than the one line does. Whatever will not
                        // fit is the tail of the timestamp, which the reader has the target date
                        // above for anyway.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Only where a later run covered the same session. A re-run leaves an older
                    // report looking exactly as current as the newest one, which is the same trap
                    // the unreadable notice exists for. Worded as a fact rather than "superseded":
                    // an older run keeps the chats the newer one never read, which is how the
                    // scoring treats it too.
                    if (newerRunExists) {
                        Box(Modifier.padding(top = Space.xs)) { StatusPill("Newer run exists") }
                    }
                }
                // Which reading of the session this card is, kept up here beside the menu rather
                // than under the dates. At the top right it reads as the card's place in the stack
                // it sits in, and it stays at one height whatever the heading below it does, so two
                // cards in a grid row line up. Centred against the menu button, not top-aligned
                // with it, because the dots are shorter than the icon and would otherwise ride high
                // of it. One line always: the heading column has the weight, so a narrow card takes
                // room from the dates rather than wrapping this.
                stack?.let {
                    // Which run the words name, which is the one more than half in front. Derived
                    // rather than read: the position moves every frame of a drag and this changes
                    // once, so the line recomposes when it has something new to say and not before.
                    val showing by remember(it) {
                        derivedStateOf { it.position().roundToInt() + 1 }
                    }
                    Row(
                        Modifier.height(MenuButtonHeight),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PageDots(it.count, it.position)
                        Text(
                            // "Run 1 of 3" spelled out took sixty units off the heading column
                            // beside it, which is what forced the run line under the date to wrap.
                            "Run $showing/${it.count}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (showReport) "Hide report" else "Show report") },
                            leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            onClick = { onShowReportChange(!showReport); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = { menuOpen = false; onShare() },
                        )
                        DropdownMenuItem(
                            text = { Text("Save to Downloads") },
                            leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                            onClick = { menuOpen = false; onSaveLocally() },
                        )
                        DropdownMenuItem(
                            text = { Text("Send as Excel") },
                            leadingIcon = { Icon(Icons.Outlined.TableChart, contentDescription = null) },
                            onClick = { menuOpen = false; onExport() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            }

            // The first of three equal shares of whatever height this card has spare, and it has
            // spare height whenever it is not the tallest shut card of its session's stack: every
            // card in a stack is held to that one so the pile does not change depth under the
            // thumb. Left to the column the whole surplus fell below the last row, and a card
            // carrying one line less than the one behind it ended in a band of nothing under the
            // button. Divided between the gaps it reads as a roomier card rather than an
            // unfinished one. All three collapse to zero on the card that set the floor, and an
            // open report is measured by its own contents rather than held to anything.
            if (!expanded) Spacer(Modifier.weight(1f))

            // What the run actually read, above the figures rather than under them. Set in the
            // same small grey as the provider line and placed below the counts, it read as a
            // caption on them - and which chats a report came out of is the one thing that
            // decides whether its calls are worth anything. Same size and ink as body copy now,
            // and the icon is what stops a line of chat names reading as another date.
            saved.channelNames().takeIf(List<String>::isNotEmpty)?.let { names ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        // Three names and a count: on its own line above a full-width row it has
                        // the room the old position under the figures did not.
                        if (names.size <= 3) {
                            names.joinToString(" · ")
                        } else {
                            names.take(3).joinToString(" · ") + " +${names.size - 3}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!expanded) Spacer(Modifier.weight(1f))

            ReportFigures(
                stocks = stockCount,
                calls = callCount,
                sources = saved.result.sources.size,
                traded = tradedCount,
            )

            if (!expanded) Spacer(Modifier.weight(1f))

            // A row rather than a button, now that the card itself opens the run: a filled button
            // across the whole width was a second control doing the same job, and the loudest thing
            // on a card whose subject is the figures above it. The arrow is what says the card
            // presses at all, and it is still the way back out of an open report.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                DisclosureButton(
                    if (expanded) "Hide recommendations" else "View recommendations",
                    expanded = expanded,
                ) { onExpandedChange(!expanded) }
            }

            AnimatedVisibility(showReport) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    HorizontalDivider()
                    Text(report().title, style = MaterialTheme.typography.titleSmall)
                    Text(report().markdown, style = MaterialTheme.typography.bodySmall)
                }
            }

            AnimatedVisibility(expanded) {
                ResultDetail(
                    saved,
                    peakFor,
                    traceRoot,
                    trades,
                    stockFilter = stockFilter,
                    onHide = { onExpandedChange(false) },
                )
            }
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

/**
 * What the run amounts to, as one instrument rather than four loose numbers.
 *
 * Bounded and divided because these figures are read against each other - twelve stocks from
 * twenty-eight readings is a different report from twelve out of twelve - and numbers floating in
 * open space with 28dp between them read as three unrelated facts.
 *
 * [traded] is dropped entirely when the user is in none of the run's calls, rather than shown as a
 * zero: a nought under a label is a figure to work out, where an absent cell is nothing to read.
 */
@Composable
private fun ReportFigures(stocks: Int, calls: Int, sources: Int, traded: Int) {
    val figures = buildList {
        add(stocks.toString() to "stocks")
        add(calls.toString() to "calls")
        add(sources.toString() to "sources")
        if (traded > 0) add(traded.toString() to "traded")
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        border = cardOutline,
    ) {
        // Intrinsic height so the dividers run the full depth of the row rather than the height
        // Material would otherwise give a divider with nothing to measure against.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            figures.forEachIndexed { index, (value, label) ->
                if (index > 0) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StatTile(
                    value = value,
                    label = label,
                    modifier = Modifier.weight(1f).padding(vertical = Space.xs, horizontal = Space.xs),
                    // The stock count leads, as it did before: it is the figure that says how much
                    // report there is.
                    tone = if (index == 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    alignment = Alignment.CenterHorizontally,
                    // A strip that says how much report there is, over a card being scanned in a
                    // grid of them - not the record itself, which is what the Portfolio's tiles are.
                    dense = true,
                )
            }
        }
    }
}

/**
 * A session named the way it would be said aloud, where that is shorter than the date.
 *
 * Only within the week either way. A next-day run targets a session that has not happened yet, so
 * this reads forwards as well as back; past a week "in 43 days" is noise beside the date itself,
 * and nothing is drawn.
 */
private fun relativeSession(target: LocalDate): String? {
    val today = LocalDate.now(ZoneId.of(EGX_ZONE))
    return when (val days = ChronoUnit.DAYS.between(today, target)) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        in 2L..6L -> "in $days days"
        in -6L..-2L -> "${-days} days ago"
        else -> null
    }
}

@Composable
private fun ResultDetail(
    saved: SavedAnalysis,
    peakFor: (String, LocalDate?) -> Double?,
    traceRoot: File,
    trades: TradeBook,
    /** What the screen is searching for, which this report opens already narrowed to. */
    stockFilter: String,
    onHide: () -> Unit,
) {
    var detail by remember { mutableStateOf<Pair<ConsolidatedRecommendation, RecommendationDataPoint>?>(null) }
    var showTrace by remember { mutableStateOf(false) }
    var openImage by remember { mutableStateOf<Int?>(null) }
    // The model cites sources by Telegram id; the channel name lives on the stored trace.
    val channelNames = remember(saved.id) {
        saved.result.sources
            .filter { it.messageId != null }
            .associate { it.messageId.toString() to it.channelName }
    }

    // Session-only, and per report: a row hidden here is one someone chose not to read now, not a
    // preference about every report they open later.
    val timings = remember(saved.id) { saved.result.timings() }
    val channels = remember(saved.id, channelNames) { saved.result.channelLabels(channelNames) }
    var shownTimings by remember(saved.id) { mutableStateOf(timings.toSet()) }
    var shownChannels by remember(saved.id) { mutableStateOf(channels.toSet()) }
    // Seeded from the screen's search, and re-seeded when it changes, so a report opened under one
    // opens narrowed to it. Held rather than applied behind the toolbar: the box then shows the
    // query that is hiding rows, which is what makes it clearable here.
    var search by remember(saved.id, stockFilter) { mutableStateOf(stockFilter) }
    var filtersOpen by remember(saved.id) { mutableStateOf(false) }

    val stocks = remember(saved.id, shownTimings, shownChannels, search, channelNames) {
        // Timing and channel narrow the rows; the search narrows the stocks, because a name belongs
        // to the stock rather than to any one occurrence of it.
        val wanted = StockSearch.query(search)
        saved.result.consolidated
            .map { stock ->
                stock.copy(
                    dataPoints = stock.dataPoints.filter {
                        it.timingLabel() in shownTimings &&
                            it.channelLabel(channelNames) in shownChannels
                    },
                )
            }
            .filter { it.dataPoints.isNotEmpty() && it.matches(wanted) }
    }

    val narrowed = shownTimings.size < timings.size ||
        shownChannels.size < channels.size ||
        search.isNotBlank()
    val clearFilters = {
        shownTimings = timings.toSet()
        shownChannels = channels.toSet()
        search = ""
    }

    @Composable
    fun Controls() {
        // Search leads: it is the one control someone arrives at the toolbar already knowing they
        // want, and it is also the only one that can empty the table on a single keystroke. The
        // two dropdowns follow, in the order the table's own columns run.
        StockFilterField(value = search, onValueChange = { search = it })
        CheckedSetFilter(
            label = "timings",
            options = timings,
            shown = shownTimings,
            onToggle = { name ->
                shownTimings = if (name in shownTimings) shownTimings - name else shownTimings + name
            },
            onSelectAll = { shownTimings = timings.toSet() },
        )
        CheckedSetFilter(
            label = "channels",
            options = channels,
            shown = shownChannels,
            onToggle = { name ->
                shownChannels = if (name in shownChannels) shownChannels - name else shownChannels + name
            },
            onSelectAll = { shownChannels = channels.toSet() },
        )
    }

    /**
     * The controls that decide what the report shows, on one line.
     *
     * [compact] folds the three filters behind a chip: a cover screen cannot hold them beside the
     * button, and a toolbar that wraps to three lines is worse pinned than not pinned at all.
     */
    @Composable
    fun Toolbar(compact: Boolean) {
        Column(
            Modifier.padding(vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Weighted rather than left to its own width, so the filters wrap against the
                // button instead of running underneath it.
                Box(Modifier.weight(1f)) {
                    FilterRow(active = narrowed && !compact, onClearAll = clearFilters) {
                        if (compact) {
                            FilterChip(
                                selected = narrowed,
                                onClick = { filtersOpen = !filtersOpen },
                                label = { Text(if (narrowed) "Filters on" else "Filters") },
                                trailingIcon = {
                                    Icon(
                                        if (filtersOpen) {
                                            Icons.Outlined.ExpandLess
                                        } else {
                                            Icons.Outlined.ExpandMore
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(IconSize.Inline),
                                    )
                                },
                            )
                        } else {
                            Controls()
                        }
                    }
                }
                // Hard against the right edge and apart from the filters: it closes the report
                // rather than narrowing it, and sitting in the row with them it held the leftmost
                // slot - the one the eye starts at, which belongs to what the table is searched by.
                // A button rather than a text link, and short: it sits beside the filters all the
                // way down a long table, so every pixel it takes is one the controls lose.
                OutlinedButton(
                    onClick = onHide,
                    contentPadding = PaddingValues(horizontal = Space.m),
                    modifier = Modifier.height(FilterControlHeight),
                ) { Text("Hide") }
            }
            if (compact) {
                AnimatedVisibility(filtersOpen) {
                    FilterRow(active = narrowed, onClearAll = clearFilters) { Controls() }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        if (stocks.isEmpty() && saved.result.consolidated.isNotEmpty()) {
            Text(
                "Nothing in this report matches those filters.",
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
                        toolbar = { Toolbar(compact = false) },
                    )
                } else {
                    // A sixteen-column table on a cover screen is a scroll bar with numbers behind
                    // it. The same figures as cards stay readable without any horizontal scrolling.
                    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                        Toolbar(compact = true)
                        stocks.forEach { stock ->
                            RecommendationCards(
                                stock = stock,
                                channelFor = { messageId -> channelNames[messageId] },
                                imagePathFor = { ref -> saved.result.imagePathFor(ref) },
                                trades = trades,
                            )
                        }
                    }
                }
            }
        } else {
            Toolbar(compact = true)
            saved.result.recommendations.forEach { LegacyDetail(it) }
        }

        DisclosureButton(
            if (showTrace) "Hide source trace" else "Source trace and diagnostics",
            expanded = showTrace,
        ) { showTrace = !showTrace }
        AnimatedVisibility(showTrace) { TraceAndDiagnostics(saved, traceRoot) }
    }


    detail?.let { (stock, point) ->
        OccurrenceSheet(
            stock = stock,
            point = point,
            imagePath = saved.result.imagePathFor(point.sourceImageRef),
            peak = peakFor(stock.stockCode, point.date),
            channel = channelNames[point.sourceMessageId],
            trades = trades,
            onDismiss = { detail = null },
        )
    }
    openImage?.let { ref ->
        SourceImageViewer(saved.result.imagePathFor(ref), ref, onDismiss = { openImage = null })
    }
}

/** The raw trace, kept collapsed because it is for checking the app rather than reading results. */
@Composable
private fun TraceAndDiagnostics(saved: SavedAnalysis, traceRoot: File) {
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
        val traceDirectory = File(traceRoot, saved.result.requestId)
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
        tokenLine(diagnostics)?.let {
            Text(
                it,
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

/**
 * A card header's floor, so two of them in a grid row line up.
 *
 * The worst case at default font scale, which every card is then held to: the 28dp target date, the
 * 16dp relative word under it, and 32dp for the provider line where it wraps to its second.
 */
private val RunHeaderHeight = 76.dp

/** An `IconButton`'s own size, so the run index beside the menu centres on the icon, not above it. */
private val MenuButtonHeight = 48.dp

/** Weekday first: which session a report is about is read as a day before it is read as a date. */
private val TARGET_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

/** Sits inside a sentence now, so the interpunct that used to separate it would read as a divider. */
private val COMPLETED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

/** IMAGE_REF is one-based over the images sent with the request. */
private fun AnalysisResult.imagePathFor(reference: Int?): String? =
    reference?.let { imagePaths.getOrNull(it - 1) }

/** Below this a table can only be read by scrolling it sideways, which is not reading. */
private val TableMinWidth = 600.dp

/**
 * What this run cost, in the provider's own numbers.
 *
 * Null for a run saved before this was recorded: an old report has nothing to say here, and a line
 * of zeroes would read as a run that cost nothing. A provider that reported usage for only some of
 * its requests says so rather than printing a total that is quietly short.
 */
private fun tokenLine(diagnostics: AnalysisDiagnostics): String? {
    val short = diagnostics.unreportedTokenRequests
        .takeIf { it > 0 }
        ?.let { " · $it request(s) reported none" }
        .orEmpty()
    if (diagnostics.totalTokens <= 0) {
        return if (short.isEmpty()) null else "The provider reported no token usage for this run."
    }
    return "${diagnostics.totalTokens.grouped()} tokens · " +
        "${diagnostics.promptTokens.grouped()} in / ${diagnostics.completionTokens.grouped()} out" +
        short
}

private fun Long.grouped(): String = String.format(java.util.Locale.US, "%,d", this)
