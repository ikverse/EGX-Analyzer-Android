package com.ikverse.egxanalyzer.next

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.Intent
import com.ikverse.egxanalyzer.data.exportIntent
import com.ikverse.egxanalyzer.data.saveToDownloads
import com.ikverse.egxanalyzer.data.stageExport
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.ui.AppState
import com.ikverse.egxanalyzer.ui.StatusMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Results - *what did the model actually extract, and can I trust it?*
 *
 * The audit trail: one card per saved run, holding what that run read and how it read it. Note the
 * division of labour this screen exists to keep - **Results is trust in the extraction, Insights is
 * trust in the channel.** The same recommendation appears on both answering different questions.
 *
 * The problem the redesign had to solve here was **disclosure depth**. A card opened, and inside it
 * the recommendations and the diagnostics opened separately: three levels, and the reader lost
 * track of which one they were in. So the card has two levels and no more - it is open or shut, and
 * when open its body is either the rows or where the rows came from. One switch, never nested.
 *
 * And the diagnostics are an investigation rather than a log dump, because the question being asked
 * is always the same one: *where did this row come from?*
 */
@Composable
internal fun NextResultsScreen(
    activity: Activity,
    appState: AppState,
    page: NextPageState,
    contentPadding: PaddingValues,
) {
    val colors = LocalNextColors.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sort = NextResultsSort.entries[page.resultsSort.coerceIn(0, NextResultsSort.entries.lastIndex)]

    val runs = remember(appState.savedResults, page.resultsStock, sort) {
        appState.savedResults
            .filter { saved -> saved.mentions(page.resultsStock) }
            .sortedWith(sort.comparator)
    }
    val filtered = page.resultsStock.isNotBlank()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Below this the table stops being a table: the context columns go first, and then the row
        // itself gives up and becomes a block. A dense grid squeezed into 411 is not a record.
        val tableWidth = maxWidth >= 620.dp
        val figureColumns = if (maxWidth >= 520.dp) 4 else 2

        LazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                NextScreenHeader(
                    title = "Results",
                    holding = "${appState.savedResults.size} saved " +
                        if (appState.savedResults.size == 1) "run" else "runs",
                    trailing = sort.label,
                )
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .ruleBottom(colors.ruleStrong)
                        .padding(vertical = NextMetrics.space4),
                    verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
                ) {
                    NextField(
                        label = "Stock",
                        value = page.resultsStock,
                        onValueChange = { page.resultsStock = it },
                        numeric = false,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
                    ) {
                        if (filtered) {
                            // A filtered list that looks unfiltered is how someone concludes their
                            // record has gone missing.
                            NextChip(
                                text = "Filters on",
                                tone = colors.accent,
                                style = ChipStyle.FILLED,
                                fill = colors.accentFill,
                            )
                            NextButton(
                                label = "Clear",
                                onClick = { page.resultsStock = "" },
                                tone = colors.rule,
                                minHeight = 40.dp,
                            )
                        }
                    }
                }
            }

            item {
                NextFilterBar(
                    leading = "Sort",
                    pickers = listOf(
                        NextPickerSpec(
                            label = sort.label,
                            active = sort != NextResultsSort.RUN_NEWEST,
                            options = NextResultsSort.entries.map(NextResultsSort::label),
                            selectedIndex = sort.ordinal,
                            onPick = { index -> page.resultsSort = index },
                        ),
                    ),
                    trailing = {
                        NextButton(
                            label = if (appState.pricesRefreshing) "Refreshing" else "Prices",
                            onClick = { scope.launch { appState.refreshPrices() } },
                            tone = colors.market,
                            labelColor = colors.market,
                            minHeight = 40.dp,
                            enabled = !appState.pricesRefreshing,
                        )
                    },
                )
            }

            if (runs.isEmpty()) {
                item {
                    when {
                        appState.savedResults.isEmpty() -> NextEmpty(
                            kind = EmptyKind.INVITE,
                            title = "No saved results yet",
                            body = "Every analysis you run is kept here, exactly as it came back.",
                            modifier = Modifier.padding(top = NextMetrics.space7),
                        )

                        filtered -> NextEmpty(
                            kind = EmptyKind.NO_MATCH,
                            title = "No runs mention ${page.resultsStock.trim()}",
                            body = "Clear the stock filter to see the rest.",
                            action = ShellAction("Clear the stock filter") {
                                page.resultsStock = ""
                            },
                            modifier = Modifier.padding(top = NextMetrics.space7),
                        )

                        else -> NextEmpty(
                            kind = EmptyKind.NO_MATCH,
                            title = "No runs match these filters",
                            body = "The record still holds ${appState.savedResults.size} runs.",
                            modifier = Modifier.padding(top = NextMetrics.space7),
                        )
                    }
                }
            }

            items(runs.size, key = { runs[it].id }) { index ->
                val saved = runs[index]
                // Placed rather than redrawn: when the fold changes the layout underneath them,
                // these travel to their new positions on the fold's own curve instead of being
                // drawn again somewhere else. This is the part of the fold the reader actually
                // watches, and it is the difference between a movement and a cut.
                Column(
                    Modifier.animateItem(
                        placementSpec = tween(
                            durationMillis = NextMotion.OPEN_MILLIS,
                            delayMillis = NextMotion.STAGGER_MILLIS * 2,
                            easing = NextMotion.hinge,
                        ),
                    ),
                ) {
                LaunchedEffect(appState.pendingResultId) {
                    if (appState.pendingResultId == saved.id) {
                        if (saved.id !in page.openRuns) page.openRuns.add(saved.id)
                        listState.animateScrollToItem(RESULTS_LEADING_ITEMS + index)
                        delay(NextMotion.ARRIVE_MILLIS.toLong())
                        appState.consumePendingResult()
                    }
                }
                RunCard(
                    saved = saved,
                    superseded = appState.savedResults.any { other ->
                        other.id != saved.id &&
                            other.result.recommendationTargetDate ==
                            saved.result.recommendationTargetDate &&
                            other.result.completedAt > saved.result.completedAt
                    },
                    open = saved.id in page.openRuns,
                    provenance = saved.id in page.runsShowingProvenance,
                    tableWidth = tableWidth,
                    figureColumns = figureColumns,
                    stockFilter = page.resultsStock,
                    appState = appState,
                    page = page,
                    activity = activity,
                    arriving = appState.pendingResultId == saved.id,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(NextMetrics.hairline)
                        .background(colors.ruleSoft),
                )
                }
            }
        }
    }
}

/** Two dates, and they are constantly confused - so they are never set the same way twice. */
internal enum class NextResultsSort(val label: String, val comparator: Comparator<SavedAnalysis>) {
    RUN_NEWEST(
        "Run date, newest",
        compareByDescending { it.result.completedAt },
    ),
    RUN_OLDEST(
        "Run date, oldest",
        compareBy { it.result.completedAt },
    ),
    TARGET_NEWEST(
        "Target session, newest",
        compareByDescending<SavedAnalysis> { it.result.recommendationTargetDate ?: LocalDate.MIN }
            .thenByDescending { it.result.completedAt },
    ),
    TARGET_OLDEST(
        "Target session, oldest",
        compareBy<SavedAnalysis> { it.result.recommendationTargetDate ?: LocalDate.MAX }
            .thenBy { it.result.completedAt },
    ),
}

/** Matches a ticker or a name, in either script, or everything when nothing was typed. */
private fun SavedAnalysis.mentions(query: String): Boolean {
    val wanted = query.trim()
    if (wanted.isEmpty()) return true
    return result.consolidated.any { stock ->
        stock.stockCode.contains(wanted, ignoreCase = true) ||
            stock.stockNameEnglish?.contains(wanted, ignoreCase = true) == true ||
            stock.stockNameArabic?.contains(wanted) == true
    }
}

@Composable
private fun RunCard(
    saved: SavedAnalysis,
    superseded: Boolean,
    open: Boolean,
    provenance: Boolean,
    tableWidth: Boolean,
    figureColumns: Int,
    stockFilter: String,
    appState: AppState,
    page: NextPageState,
    activity: Activity,
    arriving: Boolean,
) {
    val colors = LocalNextColors.current
    val scope = rememberCoroutineScope()
    val result = saved.result
    val stocks = result.consolidated
    val calls = stocks.sumOf { it.dataPoints.size }
    val traded = stocks.count { stock ->
        appState.heldFor(stock.stockCode, result.recommendationTargetDate) != null
    }
    val runDay = remember(saved.id) {
        result.completedAt.atZone(ZoneId.systemDefault()).toLocalDate()
    }

    NextRecordCard(
        open = open,
        onToggle = { page.toggleRun(saved.id) },
        spineColor = if (arriving) colors.accent else null,
        header = { chevron ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
            ) {
                // The target session leads, because it is what the run is *about*; the day it was
                // made is bookkeeping and sits behind it in muted ink.
                NextFigure(
                    text = targetWords(result.recommendationTargetDate),
                    color = colors.ink,
                    style = NextType.ticker,
                )
                NextChevron(chevron)
                Spacer(Modifier.weight(1f))
                if (superseded) {
                    NextChip("Newer run exists", colors.expired, style = ChipStyle.DASHED)
                }
                NextText("run ${formatDay(runDay)}", NextType.meta, colors.figMuted)
                NextButton(
                    label = "⋮",
                    onClick = {
                        page.openMenu = if (page.openMenu == saved.id) null else saved.id
                    },
                    tone = colors.rule,
                    labelColor = colors.ink3,
                    minHeight = 40.dp,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = NextMetrics.space4)
                    .dottedRuleTop(colors.ruleSoft)
                    .padding(top = NextMetrics.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space5),
            ) {
                SummaryCount(stocks.size, "stocks", colors.entry)
                SummaryCount(calls, "calls", colors.market)
                SummaryCount(result.selectedChannels.size, "sources", colors.figMuted)
                SummaryCount(traded, "traded", colors.accent)
                Spacer(Modifier.weight(1f))
            }
        },
        body = {
            if (page.openMenu == saved.id) {
                NextInlineMenu(
                    actions = listOf(
                        ShellAction("Save to Downloads") {
                            page.openMenu = null
                            scope.launch { saveRun(activity, appState, saved) }
                        },
                        ShellAction("Send as Excel") {
                            page.openMenu = null
                            scope.launch { sendRun(activity, appState, saved) }
                        },
                        ShellAction("Share as text") {
                            page.openMenu = null
                            shareRun(activity, appState, saved)
                        },
                    ),
                    destructive = listOf(
                        ShellAction("Delete this run") {
                            page.openMenu = null
                            page.deletingRun = saved.id
                        },
                    ),
                    modifier = Modifier.padding(bottom = NextMetrics.space4),
                )
            }

            // The one switch. Two levels of disclosure and no more: the card is open or shut, and
            // its body is either the rows or where they came from.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = NextMetrics.space4),
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
            ) {
                NextButton(
                    label = "The rows",
                    onClick = { if (provenance) page.toggleProvenance(saved.id) },
                    tone = if (!provenance) colors.accent else colors.rule,
                    labelColor = if (!provenance) colors.accent else colors.ink3,
                    fill = if (!provenance) NextFill.WASH else NextFill.NONE,
                    minHeight = 40.dp,
                )
                NextButton(
                    label = "Where from",
                    onClick = { if (!provenance) page.toggleProvenance(saved.id) },
                    tone = if (provenance) colors.accent else colors.rule,
                    labelColor = if (provenance) colors.accent else colors.ink3,
                    fill = if (provenance) NextFill.WASH else NextFill.NONE,
                    minHeight = 40.dp,
                )
                Spacer(Modifier.weight(1f))
            }

            if (provenance) {
                Provenance(saved)
            } else {
                val shown = stocks.filter { stock ->
                    stockFilter.isBlank() ||
                        stock.stockCode.contains(stockFilter.trim(), ignoreCase = true) ||
                        stock.stockNameEnglish?.contains(stockFilter.trim(), true) == true ||
                        stock.stockNameArabic?.contains(stockFilter.trim()) == true
                }
                if (shown.isEmpty()) {
                    NextEmpty(
                        kind = EmptyKind.INLINE,
                        title = "Nothing in this report matches those filters.",
                    )
                } else if (tableWidth) {
                    RowsAsTable(shown)
                } else {
                    shown.forEach { RowsAsBlocks(it, figureColumns, appState::peakSince) }
                }
            }
        },
    )
}

@Composable
private fun SummaryCount(value: Int, label: String, tone: Color) {
    val colors = LocalNextColors.current
    val dead = value == 0
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space2),
    ) {
        NextFigure(
            text = "$value",
            color = if (dead) colors.ruleStrong else tone,
            style = NextType.figure.copy(fontSize = 16.sp),
        )
        NextText(
            text = label.uppercase(Locale.ROOT),
            style = NextType.navLabel,
            color = if (dead) colors.ink3.copy(alpha = 0.55f) else colors.ink2,
        )
    }
}

/** The payload, where there is room for it to be a table. */
@Composable
private fun RowsAsTable(stocks: List<ConsolidatedRecommendation>) {
    val colors = LocalNextColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .ruleBottom(colors.rule)
                .padding(bottom = NextMetrics.space2),
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
        ) {
            NextLabel("Stock", modifier = Modifier.width(64.dp))
            NextLabel("Source", modifier = Modifier.weight(1f))
            NextLabel("Entry", modifier = Modifier.width(96.dp))
            NextLabel("T1", modifier = Modifier.width(60.dp))
            NextLabel("T2", modifier = Modifier.width(60.dp))
            NextLabel("Stop", modifier = Modifier.width(60.dp))
        }
        stocks.forEach { stock ->
            stock.dataPoints.forEach { point ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .ruleBottom(colors.ruleSoft)
                        .padding(vertical = NextMetrics.space4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
                ) {
                    NextFigure(stock.stockCode, colors.ink, modifier = Modifier.width(64.dp))
                    NextText(
                        text = stock.stockNameArabic ?: stock.stockNameEnglish.orEmpty(),
                        style = NextType.name,
                        color = colors.ink2,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    NextFigure(
                        text = entryWords(point),
                        color = colors.entry,
                        modifier = Modifier.width(96.dp),
                    )
                    NextFigure(
                        formatPrice(point.target1),
                        colors.target,
                        modifier = Modifier.width(60.dp),
                    )
                    NextFigure(
                        formatPrice(point.target2),
                        colors.target,
                        modifier = Modifier.width(60.dp),
                    )
                    NextFigure(
                        formatPrice(point.stopLoss),
                        colors.stop,
                        modifier = Modifier.width(60.dp),
                    )
                }
            }
        }
    }
}

/** The same payload where a table would be a straw: one block per stock. */
@Composable
private fun RowsAsBlocks(
    stock: ConsolidatedRecommendation,
    columns: Int,
    peakFor: (String, java.time.LocalDate?) -> Double?,
) {
    val colors = LocalNextColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .ruleTop(colors.rule)
            .padding(vertical = NextMetrics.space4),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
        ) {
            NextFigure(stock.stockCode, colors.ink, style = NextType.ticker)
            NextText(
                text = stock.stockNameArabic ?: stock.stockNameEnglish.orEmpty(),
                style = NextType.name,
                color = colors.ink2,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (stock.mentionCount > 1) {
                NextChip("${stock.mentionCount} sources", colors.market)
            }
        }
        stock.dataPoints.forEach { point ->
            val peak = peakFor(stock.stockCode, point.date)
            NextFigureGrid(
                columns = columns,
                cells = listOf(
                    { NextFigureCell("Entry", entryWords(point), colors.entry) },
                    { NextFigureCell("Target 1", formatPrice(point.target1), colors.target) },
                    { NextFigureCell("Target 2", formatPrice(point.target2), colors.target) },
                    { NextFigureCell("Stop", formatPrice(point.stopLoss), colors.stop) },
                    {
                        // What the stock has actually done since, which no other figure on this
                        // row says: the levels are what was asked for, not what happened.
                        NextFigureCell(
                            "Peak since",
                            formatPrice(peak),
                            colors.market,
                            derived = true,
                        )
                    },
                ),
            )
            if (point.isTPlusOne || point.isWatching) {
                NextChip(
                    text = if (point.isTPlusOne) "T+1" else "Watching",
                    tone = colors.expired,
                    style = ChipStyle.DASHED,
                )
            }
        }
    }
}

/**
 * Where the rows came from, as one ledger rather than four log dumps.
 *
 * Every line answers the same question in the same shape: a mark saying what kind of event this
 * was, what it concerned, and the model's own words about it. A reader who came here because a
 * figure looked wrong can read down one column.
 */
@Composable
private fun Provenance(saved: SavedAnalysis) {
    val colors = LocalNextColors.current
    val result = saved.result
    val diagnostics = result.diagnostics
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        NextFigureGrid(
            columns = 2,
            cells = listOf(
                {
                    NextFigureCell(
                        "Sources read",
                        "${diagnostics.acceptedInputCount}/${diagnostics.inputCount}",
                        colors.market,
                    )
                },
                {
                    NextFigureCell(
                        "Images sent",
                        "${diagnostics.imagesSent}",
                        colors.figMuted,
                    )
                },
                {
                    NextFigureCell(
                        "Requests",
                        "${diagnostics.requestCount}",
                        colors.figMuted,
                    )
                },
                {
                    NextFigureCell(
                        "Model",
                        saved.model,
                        colors.figMuted,
                    )
                },
            ),
        )

        if (result.modelExclusions.isNotEmpty()) {
            NextSectionMark("Excluded by the model", result.modelExclusions.size, colors.figMuted)
            result.modelExclusions.forEach { exclusion ->
                LedgerLine(
                    mark = "−",
                    markTone = colors.figMuted,
                    subject = exclusion.stockCode ?: exclusion.sourceMessageId ?: DASH,
                    words = exclusion.reason,
                )
            }
        }

        if (diagnostics.unaccountedImages.isNotEmpty()) {
            NextSectionMark(
                "Images the model never mentioned",
                diagnostics.unaccountedImages.size,
                colors.expired,
            )
            diagnostics.unaccountedImages.forEachIndexed { index, _ ->
                LedgerLine(
                    mark = "?",
                    markTone = colors.expired,
                    subject = "image ${index + 1}",
                    words = "went into the request and came back unreferenced — usually a card the " +
                        "model missed",
                )
            }
        }

        if (diagnostics.excludedSources.isNotEmpty()) {
            NextSectionMark("Unreadable", diagnostics.excludedSources.size, colors.stop)
            diagnostics.excludedSources.forEach { excluded ->
                LedgerLine(
                    mark = "×",
                    markTone = colors.stop,
                    subject = excluded.sourceId,
                    words = excluded.reason,
                )
            }
        }

        if (result.sources.isNotEmpty()) {
            NextSectionMark("Source trace", result.sources.size, colors.market)
            result.sources.forEach { trace ->
                LedgerLine(
                    mark = "→",
                    markTone = colors.market,
                    subject = trace.sourceId,
                    words = "${trace.channelName} · ${trace.preview.take(90)}",
                )
            }
        }

        if (result.modelExclusions.isEmpty() &&
            diagnostics.unaccountedImages.isEmpty() &&
            diagnostics.excludedSources.isEmpty() &&
            result.sources.isEmpty()
        ) {
            NextEmpty(
                kind = EmptyKind.INLINE,
                title = "Nothing to investigate.",
                body = "Every source was read, and the model left nothing out.",
            )
        }
    }
}

@Composable
private fun LedgerLine(mark: String, markTone: Color, subject: String, words: String) {
    val colors = LocalNextColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .ruleTop(colors.ruleSoft)
            .padding(vertical = NextMetrics.space4),
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
        verticalAlignment = Alignment.Top,
    ) {
        NextText(mark, NextType.meta, markTone, modifier = Modifier.width(14.dp))
        NextFigure(subject, colors.ink, style = NextType.meta, modifier = Modifier.width(78.dp))
        NextText(
            text = words,
            style = NextType.meta,
            color = colors.ink3,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}

/** Entry as a band where the call gave one, and as a single level where it did not. */
private fun entryWords(point: RecommendationDataPoint): String {
    val low = point.buyPriceLow
    val high = point.buyPriceHigh
    return when {
        low != null && high != null -> "${formatPrice(low)}–${formatPrice(high)}"
        else -> formatPrice(point.buyPrice ?: low ?: high)
    }
}

/** Today and Tomorrow by name, because on a fresh run that is what the session actually is. */
internal fun targetWordsFor(date: LocalDate?): String = targetWords(date)

private fun targetWords(date: LocalDate?): String {
    if (date == null) return "No session"
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> formatFullDay(date)
    }
}

/** Header, stock field, sort bar - what a run card sits below. */
private const val RESULTS_LEADING_ITEMS = 3

/**
 * Writes the run as a spreadsheet into the phone's own Downloads folder.
 *
 * Off the main thread, because it builds and zips the whole table. Nothing opens afterwards, so the
 * toast is the only sign it happened - and it names the file Downloads actually created rather than
 * the one that was asked for, which are not the same when a session has been exported before.
 */
private suspend fun saveRun(activity: Activity, appState: AppState, saved: SavedAnalysis) {
    runCatching { withContext(Dispatchers.IO) { saveToDownloads(activity, saved) } }
        .onSuccess {
            appState.statusMessage = StatusMessage("Saved to Downloads/$it", succeeded = true)
        }
        .onFailure {
            appState.statusMessage = StatusMessage(
                it.message ?: "Could not save the spreadsheet",
                succeeded = false,
            )
        }
}

/** The same spreadsheet, handed to whatever the phone offers to open it. */
private suspend fun sendRun(activity: Activity, appState: AppState, saved: SavedAnalysis) {
    runCatching { withContext(Dispatchers.IO) { stageExport(activity, saved) } }
        .onSuccess { file ->
            activity.startActivity(
                Intent.createChooser(exportIntent(activity, file), "Send EGX analysis"),
            )
        }
        .onFailure {
            appState.statusMessage = StatusMessage(
                it.message ?: "Could not build the spreadsheet",
                succeeded = false,
            )
        }
}

/** The report as text, for the places a spreadsheet is the wrong shape. */
private fun shareRun(activity: Activity, appState: AppState, saved: SavedAnalysis) {
    val report = appState.reportFor(saved)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_SUBJECT, report.title)
        putExtra(Intent.EXTRA_TEXT, report.markdown)
    }
    activity.startActivity(Intent.createChooser(intent, "Share EGX analysis report"))
}
