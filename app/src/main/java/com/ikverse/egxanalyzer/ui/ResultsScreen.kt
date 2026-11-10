package com.ikverse.egxanalyzer.ui

import kotlinx.coroutines.delay
import com.ikverse.egxanalyzer.model.timing


import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.draw.clip
import com.ikverse.egxanalyzer.model.RunOutcomes
import com.ikverse.egxanalyzer.model.runOutcomes
import com.ikverse.egxanalyzer.model.runTitle
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitDialog
import com.ikverse.egxanalyzer.ui.kit.KitChip
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.KitIconButton
import com.ikverse.egxanalyzer.ui.kit.KitMenu
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.SplitColumns
import com.ikverse.egxanalyzer.ui.kit.MenuItem
import com.ikverse.egxanalyzer.ui.kit.OutcomeStrip
import com.ikverse.egxanalyzer.ui.kit.Outcomes
import com.ikverse.egxanalyzer.ui.kit.Panel
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.ui.kit.Skeleton
import com.ikverse.egxanalyzer.ui.kit.popIn
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.theme.palette
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Undo
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
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.RecommendationResult
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.callSlot
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
    Screen(
        appState = appState,
        destination = AppDestination.RESULTS,
        onRefresh = { appState.startAction { appState.refreshPrices() } },
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
                    dateFilter.accepts(saved.result.recommendationTargetDate?.toString()) &&
                        (channelFilter.isEmpty() || saved.channelNames().any { it in channelFilter }) &&
                        saved.result.consolidated.hasStockMatching(wanted)
                }
                .sortedWith(order.comparator)
        }
        if (shown.isEmpty() && appState.savedResults.isNotEmpty()) {
            // The stock is named when it is what emptied the list: "no runs match these filters"
            // beside a box holding COMI reads as though the app had not noticed what was typed.
            val searching = stockFilter.isNotBlank()
            Tile(Modifier.fillMaxWidth().popIn(0)) {
                // The company, not the code the filter holds: the reader picked a listing from the
                // catalog, so the app can say back what they picked. See TickerPicker.name.
                Text(
                    if (searching) {
                        "No runs mention ${TickerPicker.name(stockFilter)}"
                    } else {
                        "No runs match these filters"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    if (searching) {
                        "No saved analysis holds that stock. Clear the stock filter to see the rest."
                    } else {
                        "Clear a filter to see the rest of your saved analyses."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    modifier = Modifier.padding(top = Space.s),
                )
                KitButton(
                    "Clear filters",
                    onClick = { appState.pages.clearFilters(AppDestination.RESULTS) },
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                    modifier = Modifier.padding(top = Space.m),
                )
            }
        }
        if (appState.savedResults.isEmpty()) {
            if (!appState.initialDataLoaded) {
                // The shape of the list while the record is read, so nothing jumps when it lands.
                Column(verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                    repeat(3) { Skeleton(Modifier.fillMaxWidth().height(168.dp)) }
                }
            } else {
                Tile(Modifier.fillMaxWidth().popIn(0), tone = TileTone.HIGHLIGHT) {
                    Text("No saved results yet", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Run an analysis and it will be stored here with the sources behind it.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = Space.s),
                    )
                    KitButton(
                        "Go to Analyze",
                        onClick = { appState.navigate(AppDestination.ANALYZE) },
                        kind = ButtonKind.ON_TONE,
                        size = ButtonSize.SMALL,
                        modifier = Modifier.padding(top = Space.m),
                    )
                }
            }
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
            // How many of the filtered day-groups are drawn - the filters and the stock search
            // above have already run over the whole record, so this only trims what they left.
            var visibleCount by appState.pages.resultsVisibleCount
            // A run arriving from a notification opens itself, whether the screen was already
            // showing or not - expanding the window first if the day it belongs to is not
            // currently shown, so a jump from outside the page can still reach it.
            LaunchedEffect(appState.pendingResultId) {
                appState.pendingResultId?.let { id ->
                    val index = groupRunsByDay(shown).indexOfFirst { day -> day.any { it.id == id } }
                    if (index >= visibleCount) {
                        visibleCount = (index / PAGE_SIZE + 1) * PAGE_SIZE
                    }
                    openRun = id
                }
            }
            // The report cards fade in one after another the first time the tab draws them (2026-11
            // redesign), and only then: opening a report reflows the list into new places, and a
            // card that faded in again every time would read as the list reloading.
            var introDone by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(ReportIntroMillis)
                introDone = true
            }
            BoxWithConstraints {
                val columns = responsiveColumns(minColumnWidth = SavedRunMinWidth, maxColumns = 2)
                // One entry per session rather than per run: a session re-read three times is one
                // row of the list, swiped through, newest reading in front. Done before the bands
                // are cut because an open run takes the whole width and its whole stack goes with
                // it - a day cannot be half in a grid row and half out of it.
                val allDays = remember(shown) { groupRunsByDay(shown) }
                val days = remember(allDays, visibleCount) { allDays.take(visibleCount) }
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
                    headerMinHeight: Dp = 0.dp,
                ) {
                    val dayIndex = days.indexOfFirst { day -> day.any { it.id == saved.id } }
                    SavedAnalysisCard(
                        modifier = cardModifier.staggerIn(dayIndex, enabled = !introDone),
                        saved = saved,
                        headerMinHeight = headerMinHeight,
                        // Cobalt, lime, white, down the list, so neighbouring runs never share a fill.
                        tone = RunTones[dayIndex.coerceAtLeast(0) % RunTones.size],
                        outcomes = remember(saved, appState.performance) {
                            runOutcomes(saved, appState.performance)
                        },
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
                        highlighted = saved.id == appState.pendingResultId,
                        onHighlightShown = { appState.consumePendingResult() },
                        onShare = { appState.shareReport(saved) },
                        onSaveLocally = { appState.startAction { appState.saveReportToDownloads(saved) } },
                        onExport = { appState.startAction { appState.exportReport(saved) } },
                        onDelete = { appState.deleteResult(saved) },
                        peakFor = appState::peakSince,
                        // Normalized, because the record keys prices on the bare ticker and a
                        // report can name the same stock as COMI or COMI.CA - the grouping every
                        // other reader of `latestPrices` already does.
                        latestFor = { code ->
                            appState.performance.latestPrices[Scoring.normalizeTicker(code)]
                        },
                        traceRoot = appState.traceRoot(),
                        stockFilter = stockFilter,
                        onFilterToTicker = { ticker -> appState.pages.resultsStock.value = ticker },
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
                @Composable
                fun showOlder() {
                    if (allDays.size > visibleCount) {
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

                val posture = LocalFoldPosture.current
                val openSaved = shown.firstOrNull { it.id == openRun }
                if ((posture is FoldPosture.Unfolded || posture is FoldPosture.Book) && openSaved == null) {
                    // Side by side while nothing is open: the runs down one half, and a word on the
                    // other about what opens there. In book posture the gap falls on the crease.
                    SplitColumns(posture, unfoldedStart = 0.42f, start = {
                        days.forEach { day ->
                            key(day.first().id) { stack(day, open = false, Modifier.fillMaxWidth()) }
                        }
                        showOlder()
                    }, end = {
                        Tile(Modifier.fillMaxWidth(), tone = TileTone.TILE3) {
                            Text("Pick a run", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Its report opens across the page: every call, the figures it was " +
                                    "read off, and the source trace.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = Space.s),
                            )
                        }
                    })
                } else {
                    // An open report takes the whole row, in every posture. Beside the list it had
                    // half of one, which is under the 600dp the table needs on every phone here, so
                    // unfolded it always fell back to cards - and the cards do not swallow a press
                    // in a gap, so tapping between them closed the report.
                        // In tabletop the open report's heading and figures end at the crease, so
                        // they stand on the upright half and its calls start on the flat one.
                        val headerFloor = (posture as? FoldPosture.Tabletop)?.let { tabletopHeroHeight(it) } ?: 0.dp
                        Column(verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                            bands.forEach { (band, open) ->
                                if (open) {
                                    val day = band.single()
                                    // Keyed by the run in front, so that re-sorting the list, or a new
                                    // run arriving for this session, cannot hand one day's page to another.
                                    key(day.first().id) {
                                        SavedRunStack(
                                            runs = day,
                                            openRunId = openRun,
                                            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(reveal),
                                        ) { saved, expanded, stackAt, onBringForward, cardModifier ->
                                            card(saved, expanded, stackAt, onBringForward, cardModifier, headerFloor)
                                        }
                                    }
                                } else {
                                    ResponsiveRows(band, columns) { day, cardModifier ->
                                        key(day.first().id) { stack(day, open = false, cardModifier) }
                                    }
                                }
                            }
                            showOlder()
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
    Tile(Modifier.fillMaxWidth(), tone = TileTone.ALERT) {
        Text(
            if (count == 1) "1 saved analysis cannot be read" else "$count saved analyses cannot be read",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (count == 1) {
                "Its stored report is damaged, so it is missing from the list below and the newest " +
                    "report here may be an earlier run."
            } else {
                "Their stored reports are damaged, so they are missing from the list below and the " +
                    "newest report here may be an earlier run."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Space.xs),
        )
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
    val cardShape = Kit.TileShape
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
    /**
     * How tall the open report's heading and figures stand at least - the height down to the crease
     * in tabletop, so the calls start on the flat half. Zero everywhere else.
     */
    headerMinHeight: Dp = 0.dp,
    /** The shut tile's fill. An open report is always the white card, whatever it was shut. */
    tone: TileTone = TileTone.CARD,
    /** How the run's calls have turned out so far, for the counts and the bar. */
    outcomes: RunOutcomes = RunOutcomes(0, 0, 0, 0),
    /** Records what the user did about the calls in this run. */
    trades: TradeBook,
    /** Corrects what the model read off the cards in this run. */
    editor: CallEditor,
    /** Held by the screen, which needs it to give an open report a row of its own. */
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    /** Opened from a notification: its edge flashes briefly. */
    highlighted: Boolean = false,
    onHighlightShown: () -> Unit = {},
    onShare: () -> Unit,
    /** The same table as a spreadsheet, written to the phone's own Downloads folder. */
    onSaveLocally: () -> Unit,
    /** The same file again, handed to whatever the user picks to send it with. */
    onExport: () -> Unit,
    onDelete: () -> Unit,
    /** Highest a stock has traded since the call, for the ladder's arrow. */
    peakFor: (String, LocalDate?) -> Double? = { _, _ -> null },
    /** Where a stock stands now, for the heading over its block of the table. */
    latestFor: (String) -> LatestPrice? = { null },
    /** Where request traces are kept, so the diagnostics list can count this run's. */
    traceRoot: File,
    /** What the screen is searching for, which the report opens already narrowed to. */
    stockFilter: String = "",
    /** Holding a call's ticker offers this; narrows the page's own stock filter to it. */
    onFilterToTicker: (String) -> Unit = {},
) {
    // Stays local and dies with the card, deliberately: a menu left hanging over a page that has
    // just been rebuilt into a different shape is not where the reader left anything.
    var menuOpen by remember { mutableStateOf(false) }
    // Asked for, because deleting is no longer local: it removes the report from the sync channel
    // and from every other device, and there is nothing left to restore it from.
    var confirmDelete by remember { mutableStateOf(false) }
    // Asked for too: it throws away every correction in the report in one press, not just the one
    // the reader is looking at.
    var confirmUndoAll by remember { mutableStateOf(false) }
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
    val p = palette
    val shownTone = if (expanded) TileTone.CARD else tone
    val flash = arrivalFlash(highlighted, onHighlightShown)

    Tile(
        // The whole tile opens the run. Open, a press in a gap of the report closes it again, the
        // same one press path the card always had - but without giving under the finger, since an
        // open report is a screenful of other controls and all of it shrinking would be the page
        // flinching. Behind another reading in its deck, the press means come forward instead.
        modifier = modifier
            .fillMaxWidth()
            .then(if (flash != null) Modifier.border(flash, Kit.TileShape) else Modifier)
            .then(
                if (expanded) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Hide report",
                    ) { onExpandedChange(false) }
                } else {
                    Modifier
                },
            ),
        tone = shownTone,
        onClick = if (expanded) null else (onBringForward ?: { onExpandedChange(true) }),
        onClickLabel = if (expanded) null else "Open report",
        contentPadding = PaddingValues(Space.l),
    ) {
        val ink = LocalContentColor.current
        val soft = ink.copy(alpha = 0.66f)
        Column(Modifier.heightIn(min = if (expanded) headerMinHeight else 0.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // A name a person would give the run, then its number and exact time in figures.
                Text(
                    runTitle(saved.result.completedAt),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "#${saved.id} · " + saved.result.completedAt.atZone(ScheduleClock.ZONE).format(RUN_META_FORMAT),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
                    color = soft,
                    maxLines = 1,
                )
            }
            Box {
                KitIconButton(
                    Icons.Outlined.MoreVert,
                    label = "Report options",
                    onClick = { menuOpen = true },
                    filled = shownTone == TileTone.CARD,
                    size = 40.dp,
                )
                KitMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    items = listOfNotNull(
                        MenuItem("Share as text") { onShare() },
                        MenuItem("Share as Excel") { onExport() },
                        MenuItem("Save Excel to Downloads") { onSaveLocally() },
                        // Only where there is something to undo, and its own confirmation rather
                        // than the quiet Undo a single correction gets.
                        if (editor.hasEdits) MenuItem("Undo all corrections") { confirmUndoAll = true } else null,
                        // The one thing on the menu that cannot be done again, so it is last, red,
                        // and the only one that asks first.
                        MenuItem("Delete", danger = true) { confirmDelete = true },
                    ),
                )
            }
        }

        // The session the calls are for, which is what decides whether they are still live.
        val target = saved.result.recommendationTargetDate
        Text(
            if (target == null) {
                "Target not recorded"
            } else {
                "For " + target.format(TARGET_FORMAT) + (relativeSession(target)?.let { " · $it" } ?: "")
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = Space.m),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Which chats a report came out of is the one thing that decides whether its calls are worth
        // anything, so it is body type rather than a caption.
        saved.channelNames().takeIf(List<String>::isNotEmpty)?.let { names ->
            Text(
                if (names.size <= 3) {
                    names.joinToString(" · ")
                } else {
                    names.take(3).joinToString(" · ") + " +${names.size - 3}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = soft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
        Text(
            saved.provider.displayName + " · " + saved.model,
            style = MaterialTheme.typography.bodySmall,
            color = soft,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )

        if (expanded) {
            // Takes up whatever the tabletop floor leaves, so the figures sit on the crease.
            Spacer(Modifier.weight(1f))
            ReportFigures(
                stocks = stockCount,
                calls = callCount,
                sources = saved.result.sources.size,
                traded = tradedCount,
                modifier = Modifier.padding(top = Space.m),
            )
        } else {
            // Shut, what a run amounts to is how its calls went: the count, the hits and the stops
            // in figures, and the bar under them.
            Text(
                buildString {
                    append(if (callCount == 1) "1 call" else "$callCount calls")
                    if (outcomes.hit > 0) append(" · ${outcomes.hit} hit")
                    if (outcomes.stopped > 0) append(" · ${outcomes.stopped} stopped")
                },
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                modifier = Modifier.padding(top = Space.m),
                maxLines = 1,
            )
            if (outcomes.total > 0) {
                val strip = Outcomes(outcomes.hit, outcomes.stopped, outcomes.expired, outcomes.open)
                // On the cobalt tile the bar's own open colour is the tile's, so it sits on a white
                // track there rather than vanishing into its ground.
                if (shownTone == TileTone.HERO) {
                    Box(
                        Modifier
                            .padding(top = Space.s)
                            .fillMaxWidth()
                            .clip(Kit.Capsule)
                            .background(p.card)
                            .padding(3.dp),
                    ) { OutcomeStrip(strip) }
                } else {
                    OutcomeStrip(strip, Modifier.padding(top = Space.s))
                }
            }
        }
        }

        // Which reading of the session this card is, under the figures.
        stack?.let {
            val showing by remember(it) { derivedStateOf { it.position().roundToInt() + 1 } }
            Column(
                Modifier.fillMaxWidth().padding(top = Space.m),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                PageDots(it.count, it.position)
                Text(
                    "Run $showing/${it.count}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                    color = soft,
                    maxLines = 1,
                )
            }
        }

        AnimatedVisibility(expanded, enter = ExpandIn, exit = CollapseOut) {
            Column(Modifier.padding(top = Space.m)) {
                ResultDetail(
                    saved,
                    peakFor,
                    latestFor,
                    traceRoot,
                    trades,
                    editor,
                    stockFilter = stockFilter,
                    onHide = { onExpandedChange(false) },
                    onFilterToTicker = onFilterToTicker,
                )
            }
        }
    }

    val posture = LocalFoldPosture.current
    if (confirmDelete) {
        KitDialog(
            title = "Delete this analysis?",
            text = "It will be removed from this device and from your Telegram sync channel, so " +
                "every device drops it too, along with any Ask AI answers saved on its calls. Its " +
                "calls also stop counting toward every rate on Insights. This cannot be undone.",
            confirmLabel = "Delete everywhere",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            dismissLabel = "Keep",
            onDismiss = { confirmDelete = false },
            posture = posture,
            destructive = true,
        )
    }
    if (confirmUndoAll) {
        KitDialog(
            title = "Undo every correction in this report?",
            text = "Every call goes back to exactly what the model read, and any trade a correction " +
                "moved moves back with it. This cannot be undone.",
            confirmLabel = "Undo all",
            onConfirm = {
                confirmUndoAll = false
                editor.undoAll()
            },
            dismissLabel = "Keep",
            onDismiss = { confirmUndoAll = false },
            posture = posture,
            glyph = "↺",
        )
    }
}

/**
 * What the open run amounts to: four figures in one panel, each a mono number over its key.
 *
 * [traded] is dropped entirely when the user is in none of the run's calls, rather than shown as a
 * zero: a nought under a label is a figure to work out, where an absent cell is nothing to read.
 */
@Composable
private fun ReportFigures(stocks: Int, calls: Int, sources: Int, traded: Int, modifier: Modifier = Modifier) {
    val cells = buildList {
        add(stocks to "stocks")
        add(calls to "calls")
        add(sources to "sources")
        if (traded > 0) add(traded to "traded")
    }
    Panel(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            cells.forEach { (value, label) ->
                Column(Modifier.weight(1f)) {
                    Text(
                        value.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TabularFigures),
                        maxLines = 1,
                    )
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        maxLines = 1,
                    )
                }
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
    val today = LocalDate.now(ScheduleClock.ZONE)
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
    /** Holding a call's ticker offers this; narrows the page's own stock filter to it. */
    onFilterToTicker: (String) -> Unit = {},
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
                            KitChip(
                                label = if (narrowed) "Filters on" else "Filters",
                                selected = narrowed || filtersOpen,
                                onClick = { filtersOpen = !filtersOpen },
                            )
                        } else {
                            Controls()
                        }
                    }
                }
                // Only beside the table. The compact layout draws cards, which carry every figure
                // already, so a toggle for four of them would report a state nothing acts on.
                if (!compact) {
                    KitChip(
                        label = "Context",
                        selected = showContext,
                        onClick = { showContext = !showContext },
                    )
                }
                // Hard against the right edge and apart from the filters: it closes the report
                // rather than narrowing it, and sitting in the row with them it held the leftmost
                // slot - the one the eye starts at, which belongs to what the table is searched by.
                // A button rather than a text link, and short: it sits beside the filters all the
                // way down a long table, so every pixel it takes is one the controls lose.
                KitButton("Hide", onClick = onHide, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
            }
            if (compact) {
                AnimatedVisibility(filtersOpen, enter = ExpandIn, exit = CollapseOut) {
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
                color = palette.muted,
            )
        }
        // Analyses saved before the consolidated contract have no nested occurrences, so they fall
        // back to a flat list rather than showing an empty table.
        if (saved.result.consolidated.isNotEmpty()) {
            BoxWithConstraints {
                if (maxWidth >= TableMinWidth) {
                    RecommendationTable(
                        // Taps on the table that nothing in it answers - a stock's name or price
                        // area, the column titles, the padding - stop here. Left alone they fall
                        // through to the report card's own press and close the whole report.
                        // Rows, the ticker, the toolbar and Hide answer their own taps first.
                        modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
                        stocks = stocks,
                        channelFor = { messageId -> channelNames[messageId] },
                        latestFor = latestFor,
                        onSelectPoint = { stock, point ->
                            detail = stock.anchorOf(point) to point.parseIndex
                        },
                        imagePathFor = { ref -> saved.result.imagePathFor(ref) },
                        showContext = showContext,
                        toolbar = { Toolbar(compact = false) },
                        onFilterToTicker = onFilterToTicker,
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
                                onFilterToTicker = onFilterToTicker,
                            )
                        }
                    }
                }
            }
        } else {
            Toolbar(compact = true)
            saved.result.recommendations.forEach { LegacyDetail(it) }
        }

        KitButton(
            if (showTrace) "Hide source trace" else "Source trace and diagnostics",
            onClick = { showTrace = !showTrace },
            kind = ButtonKind.SECONDARY,
            size = ButtonSize.SMALL,
        )
        AnimatedVisibility(showTrace, enter = ExpandIn, exit = CollapseOut) { TraceAndDiagnostics(saved, traceRoot) }
    }


    detail?.let { (code, slot) ->
        // Resolved against the whole report rather than the filtered list, so a filter cannot close
        // a sheet the reader has open - and re-resolved on every recomposition, which is what makes
        // a correction visible in the sheet it was made from. Searched across every entry, since a
        // corrected ticker carries the occurrence into whichever entry holds that code now.
        val found = saved.result.callSlot(code, slot)
        if (found == null) {
            // The occurrence has gone - the only way that happens is a correction being undone
            // while its sheet is open. Closing is the honest answer; there is nothing left to draw.
            LaunchedEffect(code, slot) { detail = null }
        } else {
            val (stock, point) = found
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
    Panel(Modifier.fillMaxWidth()) {
        Text("Sources", style = MaterialTheme.typography.titleSmall)
        saved.result.sources.forEach { source ->
            Text(
                "${source.channelName} · ${source.contentType} · ${source.preview.take(60)}",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
        if (saved.result.modelExclusions.isNotEmpty()) {
            Spacer(Modifier.height(Space.s))
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
                    color = palette.muted,
                )
            }
        }
        if (diagnostics.unaccountedImages.isNotEmpty()) {
            Spacer(Modifier.height(Space.s))
            // Neither kept nor rejected. The gap is shown because its absence is what let a card
            // headed with the target session disappear from a report without a word.
            Text("Images the model never mentioned", style = MaterialTheme.typography.titleSmall)
            diagnostics.unaccountedImages.forEach { missing ->
                Text(
                    "Image ${missing.reference}: ${missing.caption?.take(80) ?: "no caption"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )
            }
        }
        Spacer(Modifier.height(Space.s))
        Text("Diagnostics", style = MaterialTheme.typography.titleSmall)
        Text(
            "${diagnostics.acceptedInputCount}/${diagnostics.inputCount} inputs accepted · " +
                "${diagnostics.excludedSources.size} filtered · " +
                "${diagnostics.validationWarnings.size} warnings · " +
                "${diagnostics.durationMilliseconds} ms",
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
        )
        val traceDirectory = File(traceRoot, saved.result.requestId)
        if (traceDirectory.isDirectory) {
            // What was actually sent, not what we believe was sent. Reconstructing a request from
            // the sources table is how a mis-cited image went unnoticed for two runs.
            Text(
                "Request trace: ${traceDirectory.listFiles()?.size ?: 0} file(s)",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
            Text(
                traceDirectory.path,
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
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
                color = palette.muted,
            )
        }
        tokenLine(diagnostics)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
        diagnostics.excludedSources.forEach {
            Text(
                "Excluded ${it.sourceId}: ${it.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
        diagnostics.validationWarnings.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = palette.loss)
        }
    }
}

/** Detail for analyses saved before the consolidated contract existed. */
@Composable
private fun LegacyDetail(recommendation: RecommendationResult) {
    Panel(Modifier.fillMaxWidth()) {
        Text(
            "${recommendation.ticker} · ${recommendation.companyName}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "${recommendation.signal}" +
                (recommendation.confidence?.let { " · ${"%.0f".format(it * 100)}%" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = palette.ink,
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
}

/** Weekday first: which session a report is about is read as a day before it is read as a date. */
private val TARGET_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

/** The run's number and exact time on its tile: `#31 · 9 Nov 09:12`. */
private val RUN_META_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm")

/** The fills the run tiles cycle through, in list order. */
private val RunTones = listOf(TileTone.HERO, TileTone.HIGHLIGHT, TileTone.CARD)

/** IMAGE_REF is one-based over the images sent with the request. */
internal fun AnalysisResult.imagePathFor(reference: Int?): String? =
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

/** Long enough for the first screenful of report cards to finish arriving. */
private const val ReportIntroMillis = 900L
