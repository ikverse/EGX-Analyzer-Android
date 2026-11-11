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
import com.ikverse.egxanalyzer.model.CallGroup
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.callGroup
import com.ikverse.egxanalyzer.model.scoredCallFor
import com.ikverse.egxanalyzer.model.plainWord
import com.ikverse.egxanalyzer.model.relativeDay
import com.ikverse.egxanalyzer.model.opinionId
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.KitTag
import com.ikverse.egxanalyzer.ui.kit.MoreRow
import com.ikverse.egxanalyzer.ui.kit.tileSignals
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
            // Which run is open lives on PageState rather than in a tile: an open report replaces
            // the list, or sits beside it in book posture, and opening one on the cover screen and
            // then unfolding the phone must not put the reader back on the list of runs. Written
            // through openReport, which clears what belonged to the report that was open before.
            val openRun by appState.pages.openResultId
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
                    appState.pages.openReport(id)
                    // Opening the report is the arrival. Left pending, it would wait for a tile the
                    // open report has replaced, and open the report again on the next visit.
                    appState.consumePendingResult()
                }
            }
            // The run tiles fade in one after another the first time the tab draws them, and only
            // then: a tile that faded in again every time would read as the list reloading.
            var introDone by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(ReportIntroMillis)
                introDone = true
            }
            BoxWithConstraints {
                val columns = responsiveColumns(minColumnWidth = SavedRunMinWidth, maxColumns = 2)
                // One entry per session rather than per run: a session re-read three times is one
                // row of the list, swiped through, newest reading in front.
                val allDays = remember(shown) { groupRunsByDay(shown) }
                val days = remember(allDays, visibleCount) { allDays.take(visibleCount) }

                @Composable
                fun tile(
                    saved: SavedAnalysis,
                    stack: StackPosition?,
                    onBringForward: (() -> Unit)?,
                    cardModifier: Modifier,
                    leadAllowed: Boolean,
                ) {
                    val dayIndex = days.indexOfFirst { day -> day.any { it.id == saved.id } }
                    RunTile(
                        saved = saved,
                        // Cobalt, lime, white, down the list, so neighbouring runs never share a fill.
                        tone = RunTones[dayIndex.coerceAtLeast(0) % RunTones.size],
                        outcomes = remember(saved, appState.performance) {
                            runOutcomes(saved, appState.performance)
                        },
                        lead = leadAllowed && dayIndex == 0,
                        isOpen = saved.id == openRun,
                        stack = stack,
                        onBringForward = onBringForward,
                        onOpen = {
                            appState.pages.openReport(saved.id)
                            // Opening also selects, so the companion pane follows what is open.
                            appState.selectResult(saved)
                        },
                        highlighted = saved.id == appState.pendingResultId,
                        onHighlightShown = { appState.consumePendingResult() },
                        modifier = cardModifier.staggerIn(dayIndex, enabled = !introDone),
                    )
                }

                @Composable
                fun stack(day: List<SavedAnalysis>, stackModifier: Modifier, leadAllowed: Boolean = true) {
                    // A tile only opens now, so no stack is ever holding an open run in step.
                    SavedRunStack(runs = day, openRunId = null, modifier = stackModifier) {
                            saved, _, stackAt, onBringForward, cardModifier ->
                        tile(saved, stackAt, onBringForward, cardModifier, leadAllowed)
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

                // Opening a report puts its top at the top of the view.
                val reveal = remember { BringIntoViewRequester() }
                LaunchedEffect(openRun) {
                    if (openRun != null) {
                        reveal.revealIfOnScreen(appState, AppDestination.RESULTS)
                    }
                }

                val posture = LocalFoldPosture.current
                val openSaved = shown.firstOrNull { it.id == openRun }
                // A report deleted while open, or hidden by a filter, is closed rather than left
                // open off screen, where the first back press would close it and seem to do nothing.
                LaunchedEffect(openRun, openSaved == null) {
                    if (openRun != null && openSaved == null) appState.pages.openReport(null)
                }
                val closeReport = { appState.pages.openReport(null) }
                val reportModifier = Modifier.fillMaxWidth().bringIntoViewRequester(reveal)

                @Composable
                fun report(layout: ReportLayout) {
                    // Keyed by the run, so one report's remembered pieces never serve another's.
                    key(openSaved!!.id) { OpenReport(openSaved, appState, layout, closeReport, reportModifier) }
                }

                when {
                    openSaved == null && (posture is FoldPosture.Unfolded || posture is FoldPosture.Book) -> {
                        // Side by side while nothing is open: the runs down one half, and a word on
                        // the other about what opens there. In book posture the gap is the crease.
                        SplitColumns(posture, unfoldedStart = 0.42f, start = {
                            days.forEach { day ->
                                key(day.first().id) { stack(day, Modifier.fillMaxWidth()) }
                            }
                            showOlder()
                        }, end = {
                            Tile(Modifier.fillMaxWidth(), tone = TileTone.TILE3) {
                                Text("Pick a run", style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    "Its report opens across the page: every call and the figures " +
                                        "it was read off.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = Space.s),
                                )
                            }
                        })
                    }
                    openSaved == null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                            ResponsiveRows(days, columns) { day, cardModifier ->
                                key(day.first().id) { stack(day, cardModifier) }
                            }
                            showOlder()
                        }
                    }
                    // Book: the runs stay on the left half and the open report reads on the right,
                    // the crease between them. The tiles are the short form there.
                    posture is FoldPosture.Book -> SplitColumns(posture, unfoldedStart = 0.42f, start = {
                        days.forEach { day ->
                            key(day.first().id) { stack(day, Modifier.fillMaxWidth(), leadAllowed = false) }
                        }
                        showOlder()
                    }, end = { report(ReportLayout.CARDS) })
                    // Unfolded the 614dp container clears the table's 600dp, so the report gets
                    // the whole row and the table.
                    posture is FoldPosture.Unfolded -> report(ReportLayout.TABLE)
                    posture is FoldPosture.Tabletop -> report(ReportLayout.TABLETOP)
                    else -> report(ReportLayout.CARDS)
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
    // **Since 2026-11-11 there is no destination pager above this deck** - tabs change in place, see
    // `DestinationSwitch` - so what this connection lets through now reaches nothing and a drag past
    // the deck's last card simply stops. The note below is how it stood while there was one.
    //
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

/**
 * One run in the list: which session it was for, and how its calls went. A tile only opens; what
 * the run's menu used to offer is on About this run, inside the report.
 *
 * [lead] is the first day's run, which says the most - how many calls, from where, and the three
 * outcomes as figures. Every other tile says one figure, so the list reads as one story with
 * footnotes rather than a column of equal boxes. [isOpen] is book posture's, where the list stays
 * beside the report it opened.
 */
@Composable
private fun RunTile(
    saved: SavedAnalysis,
    tone: TileTone,
    outcomes: RunOutcomes,
    lead: Boolean,
    isOpen: Boolean,
    /** Which reading of its session this is, where the session was read more than once. */
    stack: StackPosition?,
    /**
     * Set on a reading sitting behind the front of its day's deck: a press brings it forward
     * instead of opening it. The reader can only read the card in front, so that is the only one a
     * press can honestly mean.
     */
    onBringForward: (() -> Unit)?,
    onOpen: () -> Unit,
    /** Opened from a notification: its edge flashes briefly. */
    highlighted: Boolean,
    onHighlightShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val callCount = saved.callCount()
    val flash = arrivalFlash(highlighted, onHighlightShown)
    Tile(
        modifier = modifier
            .fillMaxWidth()
            .then(if (flash != null) Modifier.border(flash, Kit.TileShape) else Modifier),
        tone = tone,
        onClick = onBringForward ?: onOpen,
        onClickLabel = "Open report",
        contentPadding = PaddingValues(Space.l),
    ) {
        val soft = LocalContentColor.current.copy(alpha = 0.66f)
        val target = saved.result.recommendationTargetDate
        val label = runLabel(target, LocalDate.now(ScheduleClock.ZONE), ScheduleClock.lastFinalSession())
        val shownLabel = if (isOpen) listOfNotNull(label, "open on the right").joinToString(" · ") else label
        shownLabel?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = soft, maxLines = 1)
        }
        Text(
            target?.format(RUN_DATE_FORMAT) ?: runTitle(saved.result.completedAt),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (lead) {
            Text(
                runSentence(callCount, saved.stockCount(), saved.channelNames().size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Space.s),
            )
            Row(Modifier.fillMaxWidth().padding(top = Space.m)) {
                RunFigure(outcomes.hit, "reached target", Modifier.weight(1f))
                RunFigure(outcomes.open, "still running", Modifier.weight(1f))
                RunFigure(outcomes.stopped, "stopped", Modifier.weight(1f))
            }
        } else {
            Row(Modifier.padding(top = Space.m), verticalAlignment = Alignment.Bottom) {
                Text(
                    "${outcomes.hit}/$callCount",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TabularFigures),
                    maxLines = 1,
                )
                Text(
                    "reached target",
                    style = MaterialTheme.typography.labelMedium,
                    color = soft,
                    modifier = Modifier.padding(start = Space.s, bottom = 4.dp),
                )
            }
        }
        if (outcomes.total > 0) {
            val strip = Outcomes(outcomes.hit, outcomes.stopped, outcomes.expired, outcomes.open)
            // On the cobalt tile the bar's own open colour is close to the tile's, so it sits on a
            // white track there rather than vanishing into its ground.
            if (tone == TileTone.HERO) {
                Box(
                    Modifier
                        .padding(top = Space.s)
                        .fillMaxWidth()
                        .clip(Kit.Capsule)
                        .background(palette.card)
                        .padding(3.dp),
                ) { OutcomeStrip(strip) }
            } else {
                OutcomeStrip(strip, Modifier.padding(top = Space.s))
            }
        }
        // Which reading of the session this tile is, under the figures.
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
    }
}

/** A figure over its word, on the lead run tile. */
@Composable
private fun RunFigure(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TabularFigures),
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = 0.66f),
            maxLines = 1,
        )
    }
}

/** Every occurrence in the report: one stock named by three channels is three calls to check. */
private fun SavedAnalysis.callCount(): Int =
    result.consolidated.sumOf { it.dataPoints.size }.takeIf { it > 0 } ?: result.recommendations.size

private fun SavedAnalysis.stockCount(): Int =
    result.consolidated.size.takeIf { it > 0 }
        ?: result.recommendations.map(RecommendationResult::ticker).distinct().size

private fun plural(count: Int, one: String, many: String = one + "s") = "$count ${if (count == 1) one else many}"

/** "14 calls on 9 stocks, from 3 channels", dropping the channels where the run names none. */
internal fun runSentence(calls: Int, stocks: Int, channels: Int): String =
    plural(calls, "call") + " on " + plural(stocks, "stock") +
        (if (channels > 0) ", from " + plural(channels, "channel") else "")

/**
 * The small label above a run's date: "Yesterday", "Last week" - or, for a session still trading,
 * "Today · session open", because a run for today is read differently before the close than after.
 */
internal fun runLabel(target: LocalDate?, today: LocalDate, lastFinal: LocalDate): String? {
    target ?: return null
    if (target == today && lastFinal < target) return "Today · session open"
    return relativeDay(target, today)
}

/** How an open report is laid out, which is the posture's business. */
internal enum class ReportLayout { CARDS, TABLE, TABLETOP }

/** One call in an open report, with what the screen works out about it, worked out once. */
private class ReportCall(
    val stock: ConsolidatedRecommendation,
    val point: RecommendationDataPoint,
    val channel: String?,
    val scored: ScoredCall?,
    val group: CallGroup,
) {
    /** Where it is in the report, which outlives a correction - see [AnalysisResult.callSlot]. */
    val anchor: Pair<String, Int> = stock.anchorOf(point) to point.parseIndex

    /**
     * The word on its tag. The group's own word where the group is all a reader needs - a call the
     * scorer has not judged is running, whatever technicality kept it unjudged - and the outcome's
     * where it says more than the group does: which target, or how it ended.
     */
    val status: Pair<String, CallGroup> = when (group) {
        CallGroup.RUNNING -> "Running"
        CallGroup.WATCHING -> "Watching"
        else -> scored?.outcome?.plainWord() ?: "Running"
    } to group
}

/** What a call's hold menu offers, so tabletop can draw some of the same items as buttons. */
private enum class CallAction { BUY, ASK, POST, CHART, FIX, COPY, ONLY }

/** The order the chips run in, and the word each carries. */
private val ChipGroups = listOf(
    CallGroup.RUNNING to "Running",
    CallGroup.REACHED to "Reached",
    CallGroup.STOPPED to "Stopped",
    CallGroup.WATCHING to "Watching",
    CallGroup.ENDED to "Ended",
)

/**
 * An open report: its bar, what it amounts to, the chips that narrow it, its calls, and About this
 * run at the foot.
 *
 * Not inside a tile any more, and that is what retired the old fault where a tap in any gap of an
 * open report closed it: there is no tile around it to answer that tap. It closes from its own bar
 * and from system back.
 */
@Composable
private fun OpenReport(
    saved: SavedAnalysis,
    appState: AppState,
    layout: ReportLayout,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = appState.pages
    // Everything derived from the report's own contents is keyed on which reading of it this is,
    // not on its id: correcting a call rewrites the stored run in place, and a memo keyed on the id
    // alone went on handing back the stocks as the model first read them.
    val reading = saved.result.editRevision
    // The model cites sources by Telegram id; the channel name lives on the stored trace.
    val channelNames = remember(saved.id, reading) {
        saved.result.sources
            .filter { it.messageId != null }
            .associate { it.messageId.toString() to it.channelName }
    }
    // Built per run: every call inside it dates from this run's target session, as the scorer does.
    val trades = remember(appState, saved.id) { TradeBook(appState, saved.result.recommendationTargetDate) }
    val editor = remember(appState, saved.id, reading) { CallEditor(appState, saved) }
    val report = appState.performance
    val calls = remember(saved.id, reading, report, channelNames) {
        saved.result.consolidated.flatMap { stock ->
            stock.dataPoints.map { point ->
                val channel = channelNames[point.sourceMessageId]
                val scored = scoredCallFor(saved, stock, point, report, channel)
                ReportCall(stock, point, channel, scored, callGroup(point, scored))
            }
        }
    }
    val byAnchor = remember(calls) { calls.associateBy { it.anchor } }
    var hidden by pages.resultsHiddenGroups
    var kept by pages.resultsKeptVisible
    // The page's stock filter still narrows the report, read straight off the page.
    val stockFilter by pages.resultsStock
    val wanted = remember(stockFilter) { StockSearch.query(stockFilter) }
    val shownCalls = calls.filter { (it.group !in hidden || it.anchor in kept) && it.stock.matches(wanted) }
    val counts = remember(calls) { calls.groupingBy { it.group }.eachCount() }
    val latestFor = { code: String -> report.latestPrices[Scoring.normalizeTicker(code)] }
    val outcomes = remember(saved, report) { runOutcomes(saved, report) }
    // Positions rather than occurrences, and deliberately not remembered - recording a trade has to
    // change the figure while the report is on screen.
    val tradedCount = saved.result.consolidated
        .flatMap { stock -> stock.dataPoints.mapNotNull { trades.heldFor(stock, it)?.position?.id } }
        .distinct()
        .size

    // What a hold or a tabletop button asked for. Transient chrome, so a `remember`: a dialog left
    // hanging over a page a fold has just rebuilt is not where the reader left anything.
    var buying by remember(saved.id) { mutableStateOf<Pair<String, Int>?>(null) }
    var asking by remember(saved.id) { mutableStateOf<Pair<String, Int>?>(null) }
    var viewing by remember(saved.id) { mutableStateOf<Pair<String, Int>?>(null) }
    var editing by remember(saved.id) { mutableStateOf<Pair<String, Int>?>(null) }
    var openCall by pages.resultsOpenCall
    val openStock = LocalOpenStock.current
    val clipboard = LocalClipboardManager.current

    fun actions(call: ReportCall): List<Pair<CallAction, MenuItem>> = buildList {
        // Only where the call has a session to belong to and nothing is held on it yet: the sheet
        // keeps the full control for a trade that exists.
        if (trades.dateOf(call.point) != null && trades.heldFor(call.stock, call.point) == null) {
            add(CallAction.BUY to MenuItem("I bought this") { buying = call.anchor })
        }
        // Ask AI costs money and needs a scored call, so it is offered only where one resolves,
        // and it goes through the same confirmation Insights uses. Not offered while it is asking.
        call.scored?.let { scored ->
            if (appState.opinionPending != opinionId(scored.ticker, scored.openedOn, scored.channel)) {
                val label = if (appState.opinionFor(scored) != null) "AI Response" else "Ask AI"
                add(CallAction.ASK to MenuItem(label) { asking = call.anchor })
            }
        }
        if (call.point.sourceImageRef != null) {
            add(CallAction.POST to MenuItem("See the original post") { viewing = call.anchor })
        }
        add(CallAction.CHART to MenuItem("See the chart") { openStock(call.stock.stockCode) })
        add(
            CallAction.FIX to MenuItem("Fix a wrong number") {
                // A chip narrowing the report could hide the call the moment it is corrected into
                // another group, so it is kept on screen until a chip is pressed again.
                if (hidden.isNotEmpty()) kept = kept + call.anchor
                editing = call.anchor
            },
        )
        add(
            CallAction.COPY to MenuItem("Copy the call") {
                clipboard.setText(
                    AnnotatedString(CallText.of(call.stock, call.point, call.channel, trades.dateOf(call.point))),
                )
            },
        )
        // The filter shortcut that used to be a hold on the ticker.
        add(CallAction.ONLY to MenuItem("Show only ${call.stock.stockCode}") { pages.resultsStock.value = call.stock.stockCode })
    }

    val onToggleGroup: (CallGroup) -> Unit = { group ->
        val present = counts.keys
        hidden = when {
            // From everything, a press means "only this".
            hidden.isEmpty() -> present - group
            group in hidden -> hidden - group
            else -> hidden + group
        }.let { next -> if (present.all { it in next } || present.none { it in next }) emptySet() else next }
        kept = emptySet()
    }
    val onAllGroups = {
        hidden = emptySet()
        kept = emptySet()
    }

    @Composable
    fun bar() = ReportBar(
        onBack = onClose,
        onShare = { appState.shareReport(saved) },
        onAbout = { pages.resultsAboutOpen.value = true },
    )

    @Composable
    fun about() = MoreRow(
        "About this run",
        pages.resultsAboutRowOpen,
        summary = madeLine(saved),
    ) {
        AboutRunContent(saved, appState, editor, pages.resultsCuriousOpen)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        val legacy = saved.result.consolidated.isEmpty()
        if (layout == ReportLayout.TABLETOP && !legacy) {
            TabletopReport(
                appState = appState,
                calls = shownCalls.ifEmpty { calls },
                latestFor = latestFor,
                counts = counts,
                hidden = hidden,
                onToggleGroup = onToggleGroup,
                onAllGroups = onAllGroups,
                bar = { bar() },
                actions = ::actions,
            )
            about()
            return@Column
        }
        bar()
        val header: @Composable (Modifier) -> Unit = { m ->
            ReportHeader(saved, calls, counts, tradedCount, outcomes, m)
        }
        val best = if (layout == ReportLayout.TABLE) bestSoFar(calls, latestFor) else null
        if (best != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                header(Modifier.weight(1f))
                BestSoFarTile(best.first, best.second, Modifier.width(BestTileWidth))
            }
        } else {
            header(Modifier.fillMaxWidth())
        }
        if (legacy) {
            // Analyses saved before the consolidated contract have no nested occurrences, so they
            // fall back to a flat list, with no chips: there is nothing to sort into groups.
            saved.result.recommendations.forEach { LegacyDetail(it) }
        } else {
            StatusChips(counts, hidden, onToggleGroup, onAllGroups)
            if (shownCalls.isEmpty()) {
                Text(
                    "Nothing in this report matches those filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                )
            }
            // The report's stocks in their own order, each carrying only its calls still shown.
            val stocks = saved.result.consolidated.mapNotNull { stock ->
                shownCalls.filter { it.stock === stock }.map(ReportCall::point)
                    .takeIf(List<RecommendationDataPoint>::isNotEmpty)
                    ?.let { stock.copy(dataPoints = it) }
            }
            fun callOf(stock: ConsolidatedRecommendation, point: RecommendationDataPoint) =
                byAnchor[stock.anchorOf(point) to point.parseIndex]
            BoxWithConstraints {
                if (layout == ReportLayout.TABLE && maxWidth >= TableMinWidth) {
                    RecommendationTable(
                        stocks = stocks,
                        channelFor = { messageId -> channelNames[messageId] },
                        latestFor = latestFor,
                        onSelectPoint = { stock, point -> openCall = stock.anchorOf(point) to point.parseIndex },
                        statusFor = { stock, point -> callOf(stock, point)?.status ?: ("Running" to CallGroup.RUNNING) },
                        holdItems = { stock, point -> callOf(stock, point)?.let { actions(it).map { a -> a.second } }.orEmpty() },
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                        stocks.forEach { stock ->
                            RecommendationCards(
                                stock = stock,
                                channelFor = { messageId -> channelNames[messageId] },
                                trades = trades,
                                editor = editor,
                                statusFor = { point -> callOf(stock, point)?.status ?: ("Running" to CallGroup.RUNNING) },
                                latest = latestFor(stock.stockCode),
                                onOpen = { point -> openCall = stock.anchorOf(point) to point.parseIndex },
                                holdItems = { point -> callOf(stock, point)?.let { actions(it).map { a -> a.second } }.orEmpty() },
                            )
                        }
                    }
                }
            }
        }
        about()
    }

    // The call sheet, held as where the call is rather than what it held when opened, so a
    // correction made from inside it shows in it. Resolved against the whole report, so a chip
    // cannot close a sheet the reader has open.
    openCall?.let { (code, slot) ->
        val found = saved.result.callSlot(code, slot)
        if (found == null) {
            // The occurrence has gone - a correction undone while its sheet was open.
            LaunchedEffect(code, slot) { openCall = null }
        } else {
            val (stock, point) = found
            OccurrenceSheet(
                stock = stock,
                point = point,
                imagePath = saved.result.imagePathFor(point.sourceImageRef),
                peak = appState.peakSince(stock.stockCode, point.date),
                channel = channelNames[point.sourceMessageId],
                trades = trades,
                editor = editor,
                onDismiss = { openCall = null },
            )
        }
    }
    buying?.let { anchor ->
        byAnchor[anchor]?.let { call ->
            PurchaseDialog(
                suggestedEntry = call.point.entryMidpoint(),
                defaultWindow = trades.windowFor(call.point),
                tPlusOne = call.point.isTPlusOne,
                onDismiss = { buying = null },
                onBuy = { price, date, window ->
                    buying = null
                    trades.buy(call.stock, call.point, call.channel, price, date, window)
                },
            )
        } ?: LaunchedEffect(anchor) { buying = null }
    }
    asking?.let { anchor ->
        val scored = byAnchor[anchor]?.scored
        val opinion = scored?.let(appState::opinionFor)
        when {
            scored == null -> LaunchedEffect(anchor) { asking = null }
            opinion != null -> StockOpinionSheet(
                call = scored,
                opinion = opinion,
                onAskAgain = {
                    asking = null
                    appState.askAboutCall(scored, true)
                },
                onDismiss = { asking = null },
            )
            else -> AskAiDialog(
                call = scored,
                model = remember(appState.opinionSettingsRevision) { appState.opinionModel() },
                searching = remember(appState.opinionSettingsRevision) { appState.opinionSearchEnabled() },
                newsWindowDays = remember(appState.opinionSettingsRevision) { appState.opinionNewsWindowDays() },
                onConfirm = {
                    asking = null
                    appState.askAboutCall(scored, false)
                },
                onDismiss = { asking = null },
            )
        }
    }
    viewing?.let { anchor ->
        val point = byAnchor[anchor]?.point
        SourceImageViewer(
            saved.result.imagePathFor(point?.sourceImageRef),
            point?.sourceImageRef,
            onDismiss = { viewing = null },
        )
    }
    editing?.let { (code, slot) ->
        val found = saved.result.callSlot(code, slot)
        if (found == null) {
            LaunchedEffect(code, slot) { editing = null }
        } else {
            val (stock, point) = found
            EditCallSheet(stock, point, editor, onDismiss = { editing = null })
        }
    }
    if (pages.resultsAboutOpen.value) {
        KitSheet(
            onDismiss = { pages.resultsAboutOpen.value = false },
            posture = LocalFoldPosture.current,
            title = "About this run",
        ) {
            AboutRunContent(saved, appState, editor, pages.resultsCuriousOpen)
        }
    }
}

/** Back to the list on the left, and the report's two actions on the right. */
@Composable
private fun ReportBar(onBack: () -> Unit, onShare: () -> Unit, onAbout: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KitButton(
            "Results",
            onClick = onBack,
            kind = ButtonKind.SECONDARY,
            size = ButtonSize.SMALL,
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            modifier = Modifier.semantics { contentDescription = "Back to all runs" },
        )
        Spacer(Modifier.weight(1f))
        KitIconButton(Icons.Outlined.Share, label = "Share this report", onClick = onShare, filled = true, size = 40.dp)
        Spacer(Modifier.width(Space.s))
        KitIconButton(Icons.Outlined.Info, label = "About this run", onClick = onAbout, filled = true, size = 40.dp)
    }
}

/**
 * What the open report amounts to, on one ink tile: when, how much, how it has gone. Its money
 * colours come from [tileSignals], because the ink tile swaps lightness with the theme.
 */
@Composable
private fun ReportHeader(
    saved: SavedAnalysis,
    calls: List<ReportCall>,
    counts: Map<CallGroup, Int>,
    tradedCount: Int,
    outcomes: RunOutcomes,
    modifier: Modifier = Modifier,
) {
    val signals = tileSignals(TileTone.INK)
    Tile(modifier, tone = TileTone.INK, contentPadding = PaddingValues(Space.l)) {
        val soft = LocalContentColor.current.copy(alpha = 0.7f)
        val target = saved.result.recommendationTargetDate
        runLabel(target, LocalDate.now(ScheduleClock.ZONE), ScheduleClock.lastFinalSession())?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = soft, maxLines = 1)
        }
        Text(
            target?.format(RUN_DATE_FORMAT) ?: runTitle(saved.result.completedAt),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
        )
        Text(
            reportSentence(
                calls = calls.count { it.group != CallGroup.WATCHING },
                watching = counts[CallGroup.WATCHING] ?: 0,
                stocks = saved.stockCount(),
                channels = saved.channelNames().size,
                bought = tradedCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Space.s),
        )
        if (outcomes.total > 0) {
            OutcomeStrip(
                Outcomes(outcomes.hit, outcomes.stopped, outcomes.expired, outcomes.open),
                Modifier.padding(top = Space.m),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Text("${counts[CallGroup.REACHED] ?: 0} reached target", style = MaterialTheme.typography.labelLarge, color = signals.gain)
            Text("${counts[CallGroup.RUNNING] ?: 0} running", style = MaterialTheme.typography.labelLarge, color = signals.accent)
            Text("${counts[CallGroup.STOPPED] ?: 0} stopped", style = MaterialTheme.typography.labelLarge, color = signals.loss)
        }
    }
}

/** "14 calls and 2 to watch · 9 stocks · 3 channels · 2 bought", each part only where it is not nought. */
internal fun reportSentence(calls: Int, watching: Int, stocks: Int, channels: Int, bought: Int): String =
    buildList {
        add(plural(calls, "call") + if (watching > 0) " and $watching to watch" else "")
        add(plural(stocks, "stock"))
        if (channels > 0) add(plural(channels, "channel"))
        if (bought > 0) add("$bought bought")
    }.joinToString(" · ")

/** The call that has gone furthest from its entry, and how far, for unfolded's lime tile. */
private fun bestSoFar(calls: List<ReportCall>, latestFor: (String) -> LatestPrice?): Pair<String, Double>? =
    calls.asSequence()
        .filter { it.group != CallGroup.WATCHING }
        .mapNotNull { call -> moveVsEntry(call.point, latestFor(call.stock.stockCode)?.session?.close)?.let { call.stock.stockCode to it } }
        .maxByOrNull { it.second }

@Composable
private fun BestSoFarTile(ticker: String, move: Double, modifier: Modifier = Modifier) {
    Tile(modifier, tone = TileTone.HIGHLIGHT, contentPadding = PaddingValues(Space.l)) {
        Text("Best so far", style = MaterialTheme.typography.labelMedium)
        Text(ticker, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = Space.s))
        Text(
            formatPercent(move),
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TabularFigures),
            color = if (move >= 0) tileSignals(TileTone.HIGHLIGHT).gain else tileSignals(TileTone.HIGHLIGHT).loss,
        )
        Text("since the call", style = MaterialTheme.typography.labelSmall)
    }
}

private val BestTileWidth = 200.dp

/**
 * The chips that narrow a report to one kind of call. `All` leads and is selected while nothing is
 * hidden; a chip is drawn only where there is a call in it, so a report never offers an empty one.
 */
@Composable
private fun StatusChips(
    counts: Map<CallGroup, Int>,
    hidden: Set<CallGroup>,
    onToggle: (CallGroup) -> Unit,
    onAll: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        KitChip("All", selected = hidden.isEmpty(), onClick = onAll)
        ChipGroups.forEach { (group, word) ->
            val count = counts[group] ?: 0
            if (count > 0) {
                KitChip("$word $count", selected = hidden.isNotEmpty() && group !in hidden, onClick = { onToggle(group) })
            }
        }
    }
}

/**
 * Tabletop: the chosen call large on the upright half, to its crease, and what is pressed - the
 * chips, a key per call, and four actions - on the flat half. The first call is chosen until the
 * reader picks another.
 */
@Composable
private fun TabletopReport(
    appState: AppState,
    calls: List<ReportCall>,
    latestFor: (String) -> LatestPrice?,
    counts: Map<CallGroup, Int>,
    hidden: Set<CallGroup>,
    onToggleGroup: (CallGroup) -> Unit,
    onAllGroups: () -> Unit,
    bar: @Composable () -> Unit,
    actions: (ReportCall) -> List<Pair<CallAction, MenuItem>>,
) {
    var selected by appState.pages.resultsSelectedCall
    val current = calls.firstOrNull { it.anchor == selected } ?: calls.firstOrNull()
    val hero = (LocalFoldPosture.current as? FoldPosture.Tabletop)?.let { tabletopHeroHeight(it) } ?: 0.dp
    Column(
        Modifier.fillMaxWidth().heightIn(min = hero),
        verticalArrangement = Arrangement.spacedBy(Kit.TileGap),
    ) {
        bar()
        current?.let { call ->
            SelectedCallPane(
                call,
                latestFor(call.stock.stockCode),
                peak = call.scored?.peakHigh ?: appState.peakSince(call.stock.stockCode, call.point.date),
            )
        }
    }
    StatusChips(counts, hidden, onToggleGroup, onAllGroups)
    CallKeys(calls, current?.anchor, latestFor) { selected = it }
    current?.let { call ->
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            actions(call).forEach { (kind, item) ->
                val label = when (kind) {
                    CallAction.BUY, CallAction.ASK -> item.label
                    CallAction.POST -> "Original post"
                    CallAction.CHART -> "Chart"
                    else -> null
                }
                if (label != null) {
                    KitButton(label, onClick = item.onClick, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
                }
            }
        }
    }
}

/** The chosen call on tabletop's upper half: who it is, where it is, and where it stands. */
@Composable
private fun SelectedCallPane(call: ReportCall, latest: LatestPrice?, peak: Double?) {
    val close = latest?.session?.close
    Tile(Modifier.fillMaxWidth(), tone = TileTone.INK, contentPadding = PaddingValues(Space.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StockLogo(call.stock.stockCode, LogoSize.Header, Modifier.padding(end = Space.s))
            Column(Modifier.weight(1f)) {
                Text(call.stock.stockCode, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                call.stock.stockNameArabic?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (close != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Now", style = MaterialTheme.typography.labelSmall)
                    Text(formatPrice(close), style = MaterialTheme.typography.titleLarge.copy(fontFamily = TabularFigures))
                }
            }
        }
        FlowRow(
            Modifier.padding(top = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            // The status in its own colour, as on every other card; the word in the tile's ink.
            KitTag(call.status.first, tone = call.status.second.tone())
            call.channel?.let { KitTag(it, tone = LocalContentColor.current) }
        }
    }
    Tile(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.l)) {
        Text("Where it stands", style = MaterialTheme.typography.titleMedium)
        if (call.point.isWatching) {
            Text(watchingSentence(call.point), style = MaterialTheme.typography.bodyMedium, color = palette.muted)
        } else {
            PriceLadder(call.point, Modifier.padding(vertical = Space.s), reached = close)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            val toTarget = call.point.target1?.let { t -> close?.takeIf { it != 0.0 }?.let { (t - it) / it * 100 } }
            PaneFigure("To target 1", toTarget?.let(::formatPercent) ?: Dash, Modifier.weight(1f))
            PaneFigure("Highest since", formatPrice(peak), Modifier.weight(1f))
            PaneFigure(
                "Gain vs risk",
                call.point.riskRewardRatio()?.let { "1 : ${"%.1f".format(it)}" } ?: Dash,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PaneFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.muted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures), maxLines = 1)
    }
}

/** A key per call on tabletop's flat half: its dot, ticker and price. Pressing one chooses it. */
@Composable
private fun CallKeys(
    calls: List<ReportCall>,
    selected: Pair<String, Int>?,
    latestFor: (String) -> LatestPrice?,
    onSelect: (Pair<String, Int>) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        calls.forEach { call ->
            val price = latestFor(call.stock.stockCode)?.session?.close?.let { " " + formatPrice(it) }.orEmpty()
            KitChip(
                "● ${call.stock.stockCode}$price",
                selected = call.anchor == selected,
                onClick = { onSelect(call.anchor) },
            )
        }
    }
}

/** "Made Wed 11 Nov at 09:12", in Cairo time like every other time here. */
private fun madeLine(saved: SavedAnalysis): String =
    "Made " + saved.result.completedAt.atZone(ScheduleClock.ZONE).format(MADE_FORMAT)

/** A run's duration the way it would be said: "41s", "2m 5s". */
internal fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}

/**
 * About this run: when it was made, the four figures that say what it did, why posts were left
 * out, the technical lines behind a closed row, and the run's own actions. The same content in the
 * sheet the bar opens and the row at the report's foot.
 */
@Composable
private fun AboutRunContent(
    saved: SavedAnalysis,
    appState: AppState,
    editor: CallEditor,
    curious: androidx.compose.runtime.MutableState<Boolean>,
) {
    val diagnostics = saved.result.diagnostics
    // Asked for, because deleting is no longer local: it removes the report from the sync channel
    // and from every other device, and there is nothing left to restore it from.
    var confirmDelete by remember { mutableStateOf(false) }
    // Asked for too: it throws away every correction in the report in one press.
    var confirmUndoAll by remember { mutableStateOf(false) }
    var excelOpen by remember { mutableStateOf(false) }
    val watching = saved.result.consolidated.sumOf { s -> s.dataPoints.count { it.isWatching } }
    val calls = saved.callCount() - watching
    val reasons = (diagnostics.excludedSources.map { it.reason } + saved.result.modelExclusions.map { it.reason })
        .map { it.replace('_', ' ') }
    val leftOut = reasons.size

    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Text(madeLine(saved), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            AboutFigure(
                diagnostics.inputCount.toString(), "posts read",
                saved.channelNames().size.takeIf { it > 0 }?.let { "from " + plural(it, "channel") },
                Modifier.weight(1f),
            )
            AboutFigure(calls.toString(), "calls kept", if (watching > 0) "and $watching to watch" else null, Modifier.weight(1f))
            AboutFigure(leftOut.toString(), "posts left out", null, Modifier.weight(1f))
            AboutFigure(
                formatDuration(diagnostics.durationMilliseconds), "time taken",
                diagnostics.reusedSources.takeIf { it > 0 }?.let { plural(it, "post") + " " + (if (it == 1) "was" else "were") + " already read" },
                Modifier.weight(1f),
            )
        }
        if (leftOut > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Why $leftOut ${if (leftOut == 1) "was" else "were"} left out", style = MaterialTheme.typography.titleSmall)
                reasons.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.forEach { (reason, count) ->
                    Text("$reason · $count", style = MaterialTheme.typography.bodySmall, color = palette.muted)
                }
            }
        }
        MoreRow("For the curious", curious) {
            CuriousDetail(saved, appState.traceRoot())
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            KitButton("Share", onClick = { appState.shareReport(saved) }, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
            Box {
                KitButton("Save as Excel", onClick = { excelOpen = true }, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
                KitMenu(
                    expanded = excelOpen,
                    onDismiss = { excelOpen = false },
                    items = listOf(
                        MenuItem("Share as Excel") { appState.startAction { appState.exportReport(saved) } },
                        MenuItem("Save Excel to Downloads") { appState.startAction { appState.saveReportToDownloads(saved) } },
                    ),
                )
            }
            if (editor.hasEdits) {
                KitButton("Undo all corrections", onClick = { confirmUndoAll = true }, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
            }
            KitButton("Delete this run", onClick = { confirmDelete = true }, kind = ButtonKind.DANGER, size = ButtonSize.SMALL)
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
                appState.deleteResult(saved)
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

@Composable
private fun AboutFigure(value: String, label: String, detail: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = TabularFigures), maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.muted)
        detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = palette.muted) }
    }
}

/** The raw trace and the run's technical lines, for checking the app rather than reading results. */
@Composable
private fun CuriousDetail(saved: SavedAnalysis, traceRoot: File) {
    val diagnostics = saved.result.diagnostics
    Panel(Modifier.fillMaxWidth()) {
        Text(
            saved.provider.displayName + " · " + saved.model,
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
        )
        Spacer(Modifier.height(Space.s))
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

/** A run's session on its tile and its report: `Mon 9 Nov`. */
private val RUN_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

/** When a run was made, in About this run: `Wed 11 Nov at 09:12`. */
private val MADE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM 'at' HH:mm")

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
