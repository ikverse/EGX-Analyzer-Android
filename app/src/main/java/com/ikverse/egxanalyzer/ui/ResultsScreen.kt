package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.timing


import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.RecommendationResult
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.Scoring
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ResultsScreen(appState: AppState) {
    val scope = rememberCoroutineScope()
    Screen(
        appState = appState,
        destination = AppDestination.RESULTS,
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
        // Outside the "are there any runs" guard the shelf was inside, because the control that
        // opens it is now in the header and is there whether or not the page has anything on it -
        // an icon that opened nothing would be a dead control. With no runs the sections have no
        // options and drop themselves, and the sheet is the order alone.
        FilterSheet(
            open = appState.pages.resultsFiltersOpen,
            // The shell asks the same question to decide what a back press means, so the
            // predicate lives on PageState and both read it there. See PageState.filtersActive.
            active = appState.pages.filtersActive(AppDestination.RESULTS),
            onClearAll = { appState.pages.clearFilters(AppDestination.RESULTS) },
        ) {
            MultiSelectSection(
                label = "Channels",
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
            DateFilterSection(
                label = "Dates",
                dates = allDates,
                selected = dateFilter,
                onSelect = { dateFilter = it },
            )
            // Below the rule, outside the filters' clear-all: an order is not something a list can
            // be cleared of, and resetting it would look like a filter had gone missing.
            SortSection(
                options = RunOrder.entries,
                selected = order,
                label = RunOrder::label,
                onSelect = { order = it },
            )
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
                // The company, not the code the filter holds: the reader picked a listing from the
                // catalog, so the app can say back what they picked. See TickerPicker.name.
                title = if (searching) {
                    "No runs mention ${TickerPicker.name(stockFilter)}"
                } else {
                    "No runs match these filters"
                },
                detail = if (searching) {
                    "No saved analysis holds that stock. Clear the stock filter to see the rest."
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
                    onBringForward: (() -> Unit)?,
                    cardModifier: Modifier,
                ) {
                    SavedAnalysisCard(
                        modifier = cardModifier,
                        saved = saved,
                        stack = stack,
                        onBringForward = onBringForward,
                        // Built per run: every card inside it dates its call from this run's target
                        // session, which is what the scorer does too.
                        trades = remember(appState, saved.id) {
                            TradeBook(appState, saved.result.recommendationTargetDate)
                        },
                        // Keyed on which reading of the report this is, not on its id alone: an
                        // edit rewrites the stored run, and a holder remembered across that would
                        // go on offering the reader the values they have just corrected. The
                        // revision rather than the report itself, so this is an integer comparison
                        // per recomposition rather than a deep compare of every call in the run.
                        editor = remember(appState, saved.id, saved.result.editRevision) {
                            CallEditor(appState, saved)
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
                        // Normalized, because the record keys prices on the bare ticker and a
                        // report can name the same stock as COMI or COMI.CA - the grouping every
                        // other reader of `latestPrices` already does.
                        latestFor = { code ->
                            appState.performance.latestPrices[Scoring.normalizeTicker(code)]
                        },
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
                    ) { saved, expanded, stack, onBringForward, cardModifier ->
                        card(saved, expanded, stack, onBringForward, cardModifier)
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
 * How much of the card behind shows below the one in front, per step of the deck.
 *
 * Below rather than beside, which is most of why this reads at all. Offset to the side, the card
 * behind showed a vertical strip down the edge of the one in front - which is exactly where every
 * card in the app already draws a hairline, so it read as a border before it read as a card. It
 * also charged the width to the wrong card: the front one was inset to make room for it. Stepped
 * down out of the foot the strip sits where the list has a gap anyway, the front card gives up
 * nothing, and the shrink takes the width off the cards behind, where it belongs.
 */
private val StackStep = 9.dp

/**
 * How many cards are drawn behind the front one.
 *
 * Two, so that a session read twice and a session read three times do not look the same: the depth
 * of the deck is the count, up to here, and past it the dots and `Run 2/5` carry it. Each further
 * step costs [StackStep] of the row's height and another [StackShrink] off that card's width - at
 * two the deepest card is 9% narrower, which still reads as a card further away, and at three it is
 * 13% and reads as a fanned hand of playing cards.
 *
 * Everything deeper than the last step rests exactly under it, covered by it and drawn at nothing.
 * A long stack drawn there is a pile of full-height cards redrawn on every frame of a drag for
 * pixels nobody can see.
 */
private const val StackDepth = 2

/**
 * How much narrower each step back is drawn, as a fraction.
 *
 * A card further away is a smaller card, and that is what separates a deck from a rule drawn under
 * one card. Small on purpose: a shrink deep enough to notice on its own makes the type on the card
 * behind look as though it is set at a different size from the type on the one in front.
 */
private const val StackShrink = 0.045f

/**
 * How far past the last drawn step a card is dropped rather than drawn.
 *
 * Cut outright rather than dimmed: nothing in this deck fades, ever, because a fade is the exact
 * fault the opaque backing exists to rule out. Everything past this sits exactly under the last
 * drawn step, covered rather than faded away, so the cut only has to clear the fractional overshoot
 * a live drag can still put a deep card through.
 */
private const val StackFadeTail = 0.2f

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
 * A day with a single run draws exactly as it always did: no deck, no dots, and still glass. Most
 * days are that day, and a deck drawn around a stack of one is chrome reporting nothing.
 *
 * **The cards behind step down out of the foot**, each one narrower than the one over it, so the
 * depth of the deck is the number of readings and the front card keeps its full width. See
 * [StackStep] for why they are not offset to the side, which is what this replaced.
 *
 * **A deck is opaque, the card in front included.** Glass shows what is behind a card, and behind
 * a card in a deck is another card - so the material would be showing one reading's figures through
 * another's, which is the one thing a page of figures cannot do. The fill is the page's own colour
 * laid under each card, so its glass resolves to exactly the value it resolves to anywhere else on
 * the page and a deck sits in the list at the same weight as the cards beside it.
 *
 * The peek is the real card behind, not a blank standing in for one, so there is nothing for the
 * card to land on and no step where a ghost is swapped for the thing it stood in for. Under the
 * thumb it rises into the front slot; the card being turned lifts fractionally and fades on a
 * squared curve, so it has gone before it has crossed the one behind rather than dragging across it.
 *
 * **A card behind is brought forward, never opened.** A press on the strip below the front card
 * used to open whichever reading was under the thumb, which is never the one the reader meant - the
 * card they can read is the one in front.
 */
@Composable
internal fun SavedRunStack(
    runs: List<SavedAnalysis>,
    /** The run whose report is open, when it is one of these. Null while the whole stack is shut. */
    openRunId: Long?,
    modifier: Modifier = Modifier,
    card: @Composable (SavedAnalysis, Boolean, StackPosition?, (() -> Unit)?, Modifier) -> Unit,
) {
    if (runs.size == 1) {
        // Untouched: no deck to make room for, so the card keeps the page's full width and the
        // glass every other card on the page is drawn in.
        card(runs.single(), openRunId != null, null, null, modifier)
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
    val stackPosition = remember(pager, runs.size, floor) {
        StackPosition(
            runs.size,
            { pager.currentPage + pager.currentPageOffsetFraction },
            floor,
        ) { height -> if (height > tallest) tallest = height }
    }
    val fling = PagerDefaults.flingBehavior(
        state = pager,
        snapAnimationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = Spring.StiffnessMedium,
        ),
    )
    val scope = rememberCoroutineScope()
    // How many steps of the deck are actually drawn, which is what the row is made taller by. A
    // session read twice holds one step of room below itself and a session read six times holds two.
    val steps = (runs.size - 1).coerceAtMost(StackDepth)
    // The page's own colour, laid under every card in the deck so that its glass resolves over the
    // page rather than over the reading behind it. See this function's note on why a deck is opaque.
    val backing = MaterialTheme.colorScheme.background
    val cardShape = MaterialTheme.shapes.large
    // This page's own width, so a drag that runs past the deck's last card can be handed to the
    // destination pager but never by more than the one page this deck fills - the same overshoot
    // `sidewaysGesturesStayOnThePage` exists to prevent, measured locally because this connection
    // sits closer to the gesture than that one does. -1 until laid out once; nothing is passed on
    // before then.
    var pageWidthPx by remember { mutableIntStateOf(-1) }
    // **Only past the deck's own edge, and only the drag, never the fling.** Every other sideways
    // gesture inside a page is eaten whole by `sidewaysGesturesStayOnThePage` because there is
    // always page margin to start a tab-turn from instead - this deck is the page, on a multi-run
    // day, so the same blanket swallow left nothing to swipe from at all. `canScrollForward` /
    // `canScrollBackward` say whether the deck itself has anywhere left to go in the drag's
    // direction; only once it does not is anything let through, and only up to one page width, so
    // a fast swipe that clears the deck in one motion can turn the tab it was headed for and no
    // further. Fling is swallowed outright rather than passed on capped: its velocity alone can
    // clear more than a page, and it is `DestinationPager`'s own fling that should decide how far
    // a hand-off travels. That leaves one gap - a flick thrown right at the deck's last card, with
    // too little drag distance for the clamp above to carry, does not turn the page - accepted
    // because it fails by doing nothing rather than by overshooting.
    val edgeAware = remember(pager) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val exhausted = (available.x < 0f && !pager.canScrollForward) ||
                    (available.x > 0f && !pager.canScrollBackward)
                if (!exhausted || pageWidthPx < 0) return Offset(available.x, 0f)
                val freed = available.x.coerceIn(-pageWidthPx.toFloat(), pageWidthPx.toFloat())
                return Offset(available.x - freed, 0f)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity) =
                Velocity(available.x, 0f)
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { pageWidthPx = it.width }
            .then(if (open) Modifier else Modifier.nestedScroll(edgeAware)),
    ) {
        HorizontalPager(
            state = pager,
            flingBehavior = fling,
            // No horizontal peek: the deck steps down rather than across, so every card in the list
            // is the same width whether its session was read once or four times. A grid row of
            // cards that do not match reads as a fault before it reads as a hint.
            pageSpacing = StackPageSpacing,
            // Without this there is no deck, and that is the fault this whole change began as.
            // Every card behind is laid out a full page away and carried back under the front one
            // by its own layer, so as far as the pager is concerned not one of them is in the
            // viewport - it composed exactly one page, the layers of the rest never ran, and the
            // strip a reader was meant to see was never drawn at any colour. Nothing about that
            // reads as broken on a device: the row draws, the dots draw, and a session read twice
            // simply never says so. `SavedRunStackTest` is the net under it.
            beyondViewportPageCount = StackDepth,
            verticalAlignment = Alignment.Top,
            // Nothing is swiped past an open report. It holds its own sideways pager over a stock's
            // occurrences and tables that scroll sideways, and a drag landing on either would be
            // caught by two things at once. Closing it is what puts the stack back under the thumb.
            userScrollEnabled = !open,
        ) { page ->
            val saved = runs[page]
            val expanded = saved.id == openRunId
            // Nothing behind an open report is drawn at all - not dimmed, not invisible-but-still-
            // there, simply absent. A shut sibling still sits close behind the front slot the open
            // report now occupies, since its own position is measured off its own collapsed height,
            // not the report's; left drawn there, its opaque backing shows through the top of the
            // report's ordinary glass fill, which is the report's own material working exactly as
            // every other open report's does and disagreeing with what is supposed to be behind it.
            // Skipped rather than hidden with alpha, or the sibling's `Card` would still answer a
            // touch meant for whatever the report draws in the same place.
            if (open && !expanded) return@HorizontalPager
            card(
                saved,
                expanded,
                // The stack's own position and not this card's page, so every card in it reads the
                // same gesture: what the dots report is where the stack stands, which is the one
                // thing a card behind and a card in front cannot disagree about.
                stackPosition,
                // A reading behind the front of the deck answers a press by coming forward. Read
                // against the settled page rather than the live position, so a press landing
                // mid-swipe cannot be answered by a card that is already on its way to the front.
                // Null while a report is open: nothing in the deck is pressable behind one.
                if (!open && page != pager.settledPage) {
                    { scope.launch { pager.animateScrollToPage(page) } }
                } else {
                    null
                },
                if (expanded) {
                    // An open report is as tall as its report. Holding it to the shut cards' floor
                    // would be measuring it against something it is not.
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth()
                        // The room the deck steps down into, held by the page itself rather than
                        // taken off the pager. A pager clips its pages, and the slack it leaves on
                        // the cross axis is an implementation detail of the platform (30dp, for
                        // shadows) - a deck that fitted inside it would be a deck drawn at the
                        // mercy of a constant nobody here chose. Held by the page, the steps are
                        // inside the bounds they are drawn in whatever the depth.
                        .padding(bottom = StackStep * steps)
                        // A lower run number is always the card on top. Left to the pager, page 2
                        // draws over page 1, and the card that is meant to be underneath swipes
                        // across the front of the one it is behind.
                        .zIndex(-page.toFloat())
                        // Applied unconditionally, every page, every frame - never branched on in
                        // the composable body. Until 2026-09-22 a page was drawn one of three ways
                        // - a step waiting behind, the page under the thumb, or a page already
                        // passed - and which of the three applied was itself read from
                        // `pager.currentPage`/`currentPageOffsetFraction` up here, outside the
                        // layer. Two faults came of that, one in what it drew and one in what it
                        // cost. What it drew: a page's drawing moved between three separate blocks
                        // of maths as `currentPage` rolled over mid-drag, and two of those three
                        // disagreed with each other at the seam - a step's shrink disagreed with
                        // its own screen position and with the front card's own sink, so a page's
                        // size and position both snapped at the exact instant it became the front
                        // card, which is what read as the animation changing shape mid-swipe and a
                        // further card popping into the deck rather than sliding into it. What it
                        // cost: choosing *which* modifier applies is a composition-time decision,
                        // and the discarded first attempt at fixing the drawing fault above picked
                        // the branch from a `distance` computed from `currentPageOffsetFraction` -
                        // a value that changes every frame of a drag - straight in the composable
                        // body, which is exactly the trap this file's own comments warn about
                        // elsewhere on this same pager: it recomposed every card of every stack on
                        // every frame of any drag anywhere on the page, which is what made a drag
                        // feel dead rather than merely wrong. One continuous `distance`, computed
                        // fresh inside the layer below on every frame without recomposing anything,
                        // and applied the same way whichever side of the front it lands on, has
                        // neither fault: nothing about a page's own drawing changes shape at any
                        // instant, and nothing above this layer ever reads a value that changes
                        // faster than a page turning.
                        .graphicsLayer {
                            // How far this page sits from the front, in pages: 0 once it is the
                            // page under the thumb, positive while it is still a step waiting
                            // behind, negative once it has been swiped past.
                            val distance =
                                (page - pager.currentPage) - pager.currentPageOffsetFraction
                            // Every card grows and shrinks about the middle of its own top edge.
                            // About its centre the shrink would pull the foot up by half of what
                            // it takes and eat the very step the deck is drawn to show; held at
                            // the top, a card's head stays where its neighbour's is and the whole
                            // of the shrink is spent below, which is where it can be seen.
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            // Held at its own step regardless of how far a drag has gone - the
                            // pager's own placement for this page is cancelled outright by this
                            // same distance and replaced with a much smaller, constant-width step,
                            // because a page still waiting is carried further along by a drag
                            // *toward* it, not pulled back by one.
                            //
                            // **Only while still waiting.** A page already passed keeps the
                            // pager's own placement instead - this cancellation is what pins a
                            // step near the centre, and a passed page pinned there the same way
                            // sits back at the centre it just left, on top of the very page that
                            // replaced it (a lower run number always draws over a higher one,
                            // steps included, and a passed page is a lower number than whichever
                            // step or front card is now in front of it). That shipped for a few
                            // hours on 2026-09-22 as "the next card doesn't show in front" - the
                            // old front card, held to zero by this same cancellation, never left.
                            translationX = if (distance >= 0f) {
                                -distance * (size.width + StackPageSpacing.toPx())
                            } else {
                                0f
                            }
                            // Symmetric about the front: a step still waiting (positive distance)
                            // and a card that has just sunk into the deck (negative) shrink by the
                            // same amount at the same remove, which is what makes leaving read as
                            // joining the stack it came from rather than sliding off the edge.
                            val t = distance.absoluteValue.coerceAtMost(StackDepth.toFloat())
                            val shrunk = 1f - StackShrink * t
                            scaleX = shrunk
                            scaleY = shrunk
                            // The shrink lifts this card's foot by its own height times what was
                            // taken off it; adding that back is what makes the step below the card
                            // in front exactly StackStep whatever height the deck settled on,
                            // rather than a figure that shrinks as the cards do.
                            translationY = size.height * (1f - shrunk) + StackStep.toPx() * t
                            // The step a reader can actually see is never dimmed. Fully opaque
                            // throughout - nothing in this deck fades, ever, because a fade is the
                            // exact fault the opaque backing below exists to rule out: one card
                            // and the reading behind it both readable at once. Everything past the
                            // last drawn step sits under it at nothing, exactly covered rather
                            // than faded away.
                            // Against the uncapped distance: `t` stops at StackDepth, so it could
                            // never pass this line and nothing past the last step was ever hidden.
                            alpha = if (distance.absoluteValue > StackDepth + StackFadeTail) 0f else 1f
                        }
                        // The card's own glass resolves over the page's colour rather than over
                        // the reading behind it, which is what makes the deck opaque.
                        .background(backing, cardShape)
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
 *
 * [floor] and [reportHeight] ride along for a second reason entirely: they are what a shut card
 * needs to pin its own footer under its own header rather than under whatever a taller sibling
 * reading needed, while staying in step with those siblings. [reportHeight] must be fed this
 * card's own *natural* height - the sum of its header-group and footer-group, measured, never the
 * gap [SavedAnalysisCard] inserts between them - because that gap is derived from [floor] itself,
 * and a card already sitting at the floor would otherwise report the floor straight back as if it
 * were new content, growing without bound one recomposition at a time. A card genuinely taller
 * than every other reading still pushes the floor up to meet it; a card already covered by it
 * reports a number [floor] already accounts for and changes nothing.
 */
internal class StackPosition(
    val count: Int,
    val position: () -> Float,
    val floor: Dp,
    val reportHeight: (Int) -> Unit,
)

@Composable
private fun SavedAnalysisCard(
    modifier: Modifier = Modifier,
    saved: SavedAnalysis,
    /** Which reading of its session this is, where the session was read more than once. */
    stack: StackPosition? = null,
    /**
     * Set on a reading sitting behind the front of its day's deck: a press brings it forward
     * instead of opening it. The reader can only read the card in front, so that is the only one a
     * press can honestly mean.
     */
    onBringForward: (() -> Unit)? = null,
    /** Records what the user did about the calls in this run. */
    trades: TradeBook,
    /** Corrects what the model read off the cards in this run. */
    editor: CallEditor,
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
    /** Where a stock stands now, for the heading over its block of the table. */
    latestFor: (String) -> LatestPrice? = { null },
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
        // The whole card opens the run, and the same whole card closes it again once it is open -
        // one press path, whichever direction it means. A tap landing in a gap between the report's
        // own content (its call cards, its table, the space around them) closes it same as pressing
        // the footer button does; only the interactive pieces inside - buttons, the menu, table
        // rows - capture their own tap first and never reach this one.
        //
        // Behind another reading in its deck, the same press means come forward rather than toggle:
        // one press path, so a card cannot be pressable in one place and dead in another.
        onClick = onBringForward ?: { onExpandedChange(!expanded) },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = arrivalFlash(highlighted, onHighlightShown) ?: Glass.outline,
        shape = MaterialTheme.shapes.large,
    ) {
        // Local rather than shared, so the shut layout (a Box pinning a footer to the card's own
        // bottom edge) and the open one (a plain top-to-bottom flow) can each call the same content
        // without restating it. See the branch on `expanded` below.
        @Composable
        fun header() {
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
                        // Space.s, the same air the table's timing pill stands on. A 20dp ring
                        // set 4dp under a line of small print reads as hanging off it rather
                        // than as a mark beside it, and the two are one object.
                        Box(Modifier.padding(top = Space.s)) { StatusPill("Newer run exists") }
                    }
                }
                Box {
                    MoreButton(onClick = { menuOpen = true })
                    AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        AppMenuItem(
                            if (showReport) "Hide report" else "Show report",
                            Icons.Outlined.Description,
                            onClick = { onShowReportChange(!showReport); menuOpen = false },
                        )
                        AppMenuItem(
                            "Share",
                            Icons.Outlined.Share,
                            onClick = { menuOpen = false; onShare() },
                        )
                        AppMenuItem(
                            "Save to Downloads",
                            Icons.Outlined.Download,
                            onClick = { menuOpen = false; onSaveLocally() },
                        )
                        AppMenuItem(
                            "Send as Excel",
                            Icons.Outlined.TableChart,
                            onClick = { menuOpen = false; onExport() },
                        )
                        // Four things that can be done again and one that cannot, and the fifth is
                        // the only one that needs saying before it is pressed.
                        AppMenuItem(
                            "Delete",
                            Icons.Outlined.Delete,
                            onClick = { menuOpen = false; confirmDelete = true },
                            destructive = true,
                        )
                    }
                }
            }
        }

        // What the run actually read, above the figures rather than under them. Set in the same
        // small grey as the provider line and placed below the counts, it read as a caption on
        // them - and which chats a report came out of is the one thing that decides whether its
        // calls are worth anything. Same size and ink as body copy now, and the icon is what stops
        // a line of chat names reading as another date.
        @Composable
        fun channelRow() {
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
        }

        @Composable
        fun figures() {
            ReportFigures(
                stocks = stockCount,
                calls = callCount,
                sources = saved.result.sources.size,
                traded = tradedCount,
            )
        }

        // A plain hint rather than a button, now that the whole card is the one thing that opens
        // and closes it: a second tappable control doing the same job as the card under it was a
        // control that only sometimes mattered. This is a label, not a control - no clickable of
        // its own - so the tap it names is answered by the card.
        @Composable
        fun tapHint() {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ink = MaterialTheme.colorScheme.primary
                Text(
                    if (expanded) "Tap to hide report" else "Tap to open report",
                    style = MaterialTheme.typography.labelMedium,
                    color = ink,
                    maxLines = 1,
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.padding(start = Space.xs).size(IconSize.Hint),
                )
            }
        }

        // Which reading of the session this card is. Text under dots reads as one object, where
        // dots beside text read as two things sharing a line.
        @Composable
        fun dots() {
            stack?.let {
                // Which run the words name, which is the one more than half in front. Derived
                // rather than read: the position moves every frame of a drag and this changes
                // once, so the line recomposes when it has something new to say and not before.
                val showing by remember(it) {
                    derivedStateOf { it.position().roundToInt() + 1 }
                }
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    PageDots(it.count, it.position)
                    Text(
                        "Run $showing/${it.count}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        Column(
            Modifier.padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (expanded) {
                // Open, this card answers to nothing else - only shut cards in a stack are held
                // to a shared floor (see SavedRunStack.tallest) - so it is a plain top-to-bottom
                // flow with nothing to pin.
                header()
                channelRow()
                figures()
                tapHint()
                dots()
            } else {
                // Shut, this card may need to be stretched taller than its own content to match
                // the tallest shut card in its stack, with the surplus landing above the footer
                // rather than below it. A `Box` aligning a top group and a bottom group was tried
                // first and overlapped them: a `Box`'s own height is the *taller* of its children,
                // not their sum, so whenever the floor fell short of this card's own header-plus-
                // footer height the footer landed on top of the header instead of below it - which
                // is exactly what shipped in 3.8.7. A plain sequential `Column` can only ever place
                // the footer after the header, so it cannot overlap; the one thing left to get
                // right is how tall the gap between them is, and that is measured rather than
                // asked for, for the same reason `tallest` above is: intrinsic measurement of a
                // `SubcomposeLayout` is unsupported, and these cards contain one.
                //
                // Each half reports its own natural height - never the gap, which would feed a
                // figure back into itself - so a card already at or past the floor reports a
                // number `tallest` already covers and changes nothing, and only a card that turns
                // out taller than every other reading pushes the floor up to meet it.
                var topHeightPx by remember { mutableIntStateOf(0) }
                var footerHeightPx by remember { mutableIntStateOf(0) }
                val gap = with(LocalDensity.current) {
                    ((stack?.floor?.roundToPx() ?: 0) - topHeightPx - footerHeightPx)
                        .coerceAtLeast(0)
                        .toDp()
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged {
                            topHeightPx = it.height
                            stack?.reportHeight?.invoke(topHeightPx + footerHeightPx)
                        },
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    header()
                    channelRow()
                    figures()
                }
                Spacer(Modifier.height(gap))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged {
                            footerHeightPx = it.height
                            stack?.reportHeight?.invoke(topHeightPx + footerHeightPx)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    tapHint()
                    dots()
                }
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
                    latestFor,
                    traceRoot,
                    trades,
                    editor,
                    stockFilter = stockFilter,
                    onHide = { onExpandedChange(false) },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            containerColor = Glass.solid(MaterialTheme.colorScheme.surfaceContainerHigh),
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this analysis?") },
            text = {
                Text(
                    "It will be removed from this device and from your Telegram sync channel, so " +
                        "every device drops it too, along with any Ask AI answers saved on its " +
                        "calls. Its calls also stop counting toward every rate on Insights. This " +
                        "cannot be undone.",
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
 * The instrument itself is [StatStrip], which this used to be: it was lifted into `CommonUi` when
 * the token usage section wanted the same shape, so what is left here is only which four figures a
 * report puts in it. The stock count leads and so takes the page's hue - it is the figure that says
 * how much report there is.
 *
 * [traded] is dropped entirely when the user is in none of the run's calls, rather than shown as a
 * zero: a nought under a label is a figure to work out, where an absent cell is nothing to read.
 */
@Composable
private fun ReportFigures(stocks: Int, calls: Int, sources: Int, traded: Int) {
    StatStrip(
        buildList {
            add(stocks.toString() to "stocks")
            add(calls.toString() to "calls")
            add(sources.toString() to "sources")
            if (traded > 0) add(traded.toString() to "traded")
        },
    )
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
    latestFor: (String) -> LatestPrice?,
    traceRoot: File,
    trades: TradeBook,
    editor: CallEditor,
    /** What the screen is searching for, which this report opens already narrowed to. */
    stockFilter: String,
    onHide: () -> Unit,
) {
    // The occurrence the sheet is showing, held as **where it is** rather than as what it held when
    // it was opened. A correction made from inside the sheet rewrites the report underneath it, and
    // a captured pair would go on drawing the figures the reader has just replaced.
    var detail by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var showTrace by remember { mutableStateOf(false) }
    // Everything derived from the report's own contents is keyed on **which reading of it this
    // is**, not on its id. A report's id never changes and its contents now do: correcting a call
    // rewrites the stored run in place, and a memo keyed on the id alone went on handing back the
    // stocks as the model first read them. That is why an edit used to reach Insights - which
    // rebuilds from the record - and not the report it was made on, until the screen was left and
    // come back to. `editRevision` rises on every correction and on every corrected copy adopted
    // from another device, which is exactly the set of events that move these.
    val reading = saved.result.editRevision

    // The model cites sources by Telegram id; the channel name lives on the stored trace.
    val channelNames = remember(saved.id, reading) {
        saved.result.sources
            .filter { it.messageId != null }
            .associate { it.messageId.toString() to it.channelName }
    }

    val timings = remember(saved.id, reading) { saved.result.timings() }
    val channels = remember(saved.id, reading, channelNames) {
        saved.result.channelLabels(channelNames)
    }
    // Session-only, and per report: a row hidden here is one someone chose not to read now, not a
    // preference about every report they open later.
    //
    // **What is hidden, rather than what is shown**, and keyed on the report rather than on this
    // reading of it. Two things had to be true at once: a correction must not throw away filters
    // the reader set on purpose, and a timing a correction has just *created* - re-dating a card as
    // Watching makes one - must not arrive already hidden, which is what a stored set of shown
    // names would do to it. Storing the exclusions gives both: the reader's choices survive the
    // edit, and anything new is shown because nobody ever chose to hide it.
    var hiddenTimings by remember(saved.id) { mutableStateOf(emptySet<String>()) }
    var hiddenChannels by remember(saved.id) { mutableStateOf(emptySet<String>()) }
    val shownTimings = timings.toSet() - hiddenTimings
    val shownChannels = channels.toSet() - hiddenChannels
    // Seeded from the screen's search, and re-seeded when it changes, so a report opened under one
    // opens narrowed to it. Held rather than applied behind the toolbar: the box then shows the
    // query that is hiding rows, which is what makes it clearable here.
    var search by remember(saved.id, stockFilter) { mutableStateOf(stockFilter) }
    var filtersOpen by remember(saved.id) { mutableStateOf(false) }
    // Support, resistance and the two dates, under every row. Session-only and per report, like the
    // filters above it: asking to see them is about the report being read now, not a preference.
    var showContext by remember(saved.id) { mutableStateOf(false) }

    val stocks = remember(saved.id, reading, shownTimings, shownChannels, search, channelNames) {
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

    // Measured against what the report actually offers, not against the exclusion sets: a timing a
    // correction has removed from the report entirely is still named in `hiddenTimings` and is
    // hiding nothing, so counting it would light the chip over a report nothing is narrowing.
    val narrowed = shownTimings.size < timings.size ||
        shownChannels.size < channels.size ||
        search.isNotBlank()
    val clearFilters = {
        hiddenTimings = emptySet()
        hiddenChannels = emptySet()
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
                hiddenTimings =
                    if (name in hiddenTimings) hiddenTimings - name else hiddenTimings + name
            },
            onSelectAll = { hiddenTimings = emptySet() },
        )
        CheckedSetFilter(
            label = "channels",
            options = channels,
            shown = shownChannels,
            onToggle = { name ->
                hiddenChannels =
                    if (name in hiddenChannels) hiddenChannels - name else hiddenChannels + name
            },
            onSelectAll = { hiddenChannels = emptySet() },
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
                // Only beside the table. The compact layout draws cards, which carry every figure
                // already, so a toggle for four of them would report a state nothing acts on.
                if (!compact) {
                    FilterChip(
                        selected = showContext,
                        onClick = { showContext = !showContext },
                        label = { Text("Context") },
                        modifier = Modifier.height(FilterControlHeight),
                    )
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
                        latestFor = latestFor,
                        onSelectPoint = { stock, point ->
                            detail = stock.originalStockCode to point.parseIndex
                        },
                        imagePathFor = { ref -> saved.result.imagePathFor(ref) },
                        showContext = showContext,
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
                                editor = editor,
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


    detail?.let { (code, slot) ->
        // Resolved against the whole report rather than the filtered list, so a filter cannot close
        // a sheet the reader has open - and re-resolved on every recomposition, which is what makes
        // a correction visible in the sheet it was made from.
        val stock = saved.result.consolidated.firstOrNull { it.originalStockCode == code }
        val point = stock?.dataPoints?.firstOrNull { it.parseIndex == slot }
        if (stock == null || point == null) {
            // The occurrence has gone - the only way that happens is a correction being undone
            // while its sheet is open. Closing is the honest answer; there is nothing left to draw.
            LaunchedEffect(code, slot) { detail = null }
        } else {
            OccurrenceSheet(
                stock = stock,
                point = point,
                imagePath = saved.result.imagePathFor(point.sourceImageRef),
                peak = peakFor(stock.stockCode, point.date),
                channel = channelNames[point.sourceMessageId],
                trades = trades,
                editor = editor,
                onDismiss = { detail = null },
            )
        }
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
            // Named only where there were any: it is what says why a run's requests and images are
            // fewer than the sources it covered, and on an ordinary run there is nothing to explain.
            val reused = diagnostics.reusedSources
                .takeIf { it > 0 }
                ?.let { " · $it read before" }
                .orEmpty()
            Text(
                "${diagnostics.requestCount} model requests · ${diagnostics.imagesSent} images sent · " +
                    "${diagnostics.unaccountedImages.size} unaccounted$reused",
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
