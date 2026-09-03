package com.ikverse.egxanalyzer.next

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.ikverse.egxanalyzer.model.PerformanceCalculator
import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import com.ikverse.egxanalyzer.model.positionId
import com.ikverse.egxanalyzer.ui.AppState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Insights - *which channels are worth following?*
 *
 * Two problems decide this screen, and both are about not lying with a figure.
 *
 * 1. **A rate never appears without the count it was computed over.** A channel with three settled
 *    calls at 100% must not be allowed to lead ninety-four at 61%, and hiding its figure would be a
 *    different lie - so the rate is shown, the evidence bar underneath it is drawn from the *count*
 *    rather than from the rate, and a channel under the gate is filed below a dashed rule with its
 *    rank replaced by a dash.
 * 2. **A call's status has three tiers, not nine labels.** A verdict that counts, something still
 *    running, and something excluded from every rate on purpose. The tier is said by the chip's
 *    construction - filled, hollow, dashed - so the nine labels inside them never have to be
 *    memorised.
 */
@Composable
internal fun NextInsightsScreen(
    appState: AppState,
    page: NextPageState,
    contentPadding: PaddingValues,
) {
    val colors = LocalNextColors.current
    val report = appState.performance
    val listState = rememberLazyListState()

    val channels = remember(report) { report.channels }
    val ranked = remember(channels) {
        channels.filter { it.judged >= PerformanceCalculator.MINIMUM_JUDGED_TO_RANK }
    }
    val unranked = remember(channels) {
        channels.filter { it.judged < PerformanceCalculator.MINIMUM_JUDGED_TO_RANK }
    }
    val widestEvidence = remember(channels) {
        channels.maxOfOrNull(ChannelScore::judged)?.coerceAtLeast(1) ?: 1
    }

    val sessions = remember(report, page.insightsFilter, page.insightsChannel) {
        report.sessions
            .map { session ->
                session to session.calls.filter { call ->
                    (page.insightsFilter == null || tierOf(call.outcome) == page.insightsFilter) &&
                        (page.insightsChannel == null || call.channel == page.insightsChannel)
                }
            }
            .filter { (_, calls) -> calls.isNotEmpty() }
    }
    val scope = rememberCoroutineScope()

    // The trip from a Portfolio card. Open the session holding it, and scroll it into view; the
    // flash itself is drawn by the call block and expires on its own.
    LaunchedEffect(appState.pendingCallId, sessions.size) {
        val wanted = appState.pendingCallId ?: return@LaunchedEffect
        val index = sessions.indexOfFirst { (_, calls) -> calls.any { it.positionId == wanted } }
        if (index >= 0) {
            sessions[index].first.targetDate?.let(page::openCallSession)
            listState.animateScrollToItem(LEADING_ITEMS + index)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 520.dp
        val levelColumns = if (wide) 4 else 2

        LazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                NextScreenHeader(
                    title = "Insights",
                    holding = "${report.judged} settled · ${channels.size} channels",
                    trailing = "${report.tracked} scored",
                )
            }

            item { PriceStanding(appState) }

            item {
                Ranking(
                    ranked = ranked,
                    unranked = unranked,
                    widestEvidence = widestEvidence,
                    wide = wide,
                )
            }

            item {
                NextFilterBar(
                    leading = "Calls",
                    pickers = listOf(
                        NextPickerSpec(
                            label = page.insightsChannel ?: "All ${channels.size} channels",
                            active = page.insightsChannel != null,
                            options = listOf("All ${channels.size} channels") +
                                channels.map(ChannelScore::channel),
                            selectedIndex = page.insightsChannel
                                ?.let { name -> channels.indexOfFirst { it.channel == name } + 1 }
                                ?: 0,
                            onPick = { index ->
                                page.insightsChannel = channels.getOrNull(index - 1)?.channel
                            },
                        ),
                        NextPickerSpec(
                            label = page.insightsFilter ?: "All three tiers",
                            active = page.insightsFilter != null,
                            options = listOf(
                                "All three tiers",
                                TIER_VERDICT,
                                TIER_RUNNING,
                                TIER_EXCLUDED,
                            ),
                            selectedIndex = when (page.insightsFilter) {
                                TIER_VERDICT -> 1
                                TIER_RUNNING -> 2
                                TIER_EXCLUDED -> 3
                                else -> 0
                            },
                            onPick = { index ->
                                page.insightsFilter = when (index) {
                                    1 -> TIER_VERDICT
                                    2 -> TIER_RUNNING
                                    3 -> TIER_EXCLUDED
                                    else -> null
                                }
                            },
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

            item { TierLegend(report.sessions.flatMap(ScoredSession::calls)) }

            if (sessions.isEmpty()) {
                item {
                    if (report.tracked == 0) {
                        NextEmpty(
                            kind = EmptyKind.INVITE,
                            title = "No calls scored yet",
                            body = report.scoringSince?.let {
                                "The record starts on ${formatFullDay(it)}. Analyze a channel and " +
                                    "its calls are replayed against the tape from there."
                            } ?: "Analyze a channel, and its calls are replayed against the tape.",
                            modifier = Modifier.padding(top = NextMetrics.space7),
                        )
                    } else {
                        NextEmpty(
                            kind = EmptyKind.NO_MATCH,
                            title = "No calls in this tier",
                            body = "The record still holds ${report.tracked} scored calls.",
                            action = ShellAction("Show all three tiers") {
                                page.insightsFilter = null
                            },
                            modifier = Modifier.padding(top = NextMetrics.space7),
                        )
                    }
                }
            }

            items(sessions.size, key = { sessions[it].first.targetDate.toString() }) { index ->
                val (session, calls) = sessions[index]
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
                val date = session.targetDate
                SessionCallCard(
                    session = session,
                    calls = calls,
                    open = date != null && date in page.openCallSessions,
                    onToggle = { date?.let(page::toggleCallSession) },
                    levelColumns = levelColumns,
                    appState = appState,
                    page = page,
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

/** Header, price line, ranking, filter bar, legend - what a session card sits below. */
private const val LEADING_ITEMS = 5

/**
 * How current the prices behind every figure on this screen are.
 *
 * Read from the prices themselves rather than from the day a refresh last ran: a refresh records
 * that it went out, not that it came back with anything, and on a day the exchange did not trade
 * the two are days apart. Stocks with no price at all are named separately from calls whose own
 * sessions simply have not been published - a refresh fixes the first and cannot touch the second.
 */
@Composable
private fun PriceStanding(appState: AppState) {
    val colors = LocalNextColors.current
    val report = appState.performance
    Row(
        Modifier
            .fillMaxWidth()
            .ruleTop(colors.ruleSoft)
            .padding(vertical = NextMetrics.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        NextLabel("Prices to")
        NextFigure(formatFullDay(report.pricesTo), colors.market, style = NextType.meta)
        Spacer(Modifier.weight(1f))
        if (report.unpricedStocks > 0) {
            NextChip("${report.unpricedStocks} unpriced", colors.expired, style = ChipStyle.DASHED)
        }
        if (report.awaitingSessions > 0) {
            NextChip("${report.awaitingSessions} awaiting sessions", colors.figMuted, style = ChipStyle.DASHED)
        }
    }
}

private const val TIER_VERDICT = "Verdict · counted"
private const val TIER_RUNNING = "In progress"
private const val TIER_EXCLUDED = "Excluded · never counted"

/** Which of the three tiers an outcome belongs to. */
private fun tierOf(outcome: Outcome): String = when {
    outcome.judged -> TIER_VERDICT
    outcome == Outcome.OPEN -> TIER_RUNNING
    else -> TIER_EXCLUDED
}

/**
 * The sources, ordered - and the gate that keeps a lucky three off the top.
 *
 * The bar under each rate is the **evidence**, not the rate: its width is that channel's settled
 * calls against the widest column in the table, and the bone tick on it is where five settled
 * calls falls. So a channel whose rate is high on a bar that barely leaves the left edge is
 * legible as exactly that, before any of the words are read.
 */
@Composable
private fun Ranking(
    ranked: List<ChannelScore>,
    unranked: List<ChannelScore>,
    widestEvidence: Int,
    wide: Boolean,
) {
    val colors = LocalNextColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .ruleTop(colors.ruleStrong)
            .padding(top = NextMetrics.space5, bottom = NextMetrics.space5),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NextLabel("Sources ranked", color = colors.ink)
            NextText(
                "hit rate over settled calls · " +
                    "${PerformanceCalculator.MINIMUM_JUDGED_TO_RANK} needed to lead",
                NextType.meta,
                colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ranked.forEachIndexed { index, score ->
            RankRow(
                rank = "${index + 1}",
                score = score,
                widestEvidence = widestEvidence,
                wide = wide,
                qualified = true,
            )
        }

        if (unranked.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = NextMetrics.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
            ) {
                NextLabel(
                    "Not ranked · under ${PerformanceCalculator.MINIMUM_JUDGED_TO_RANK} settled",
                    color = colors.expired,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(NextMetrics.hairline)
                        .background(colors.ruleSoft),
                )
            }
            unranked.forEach { score ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.well)
                        .padding(NextMetrics.space4),
                    verticalArrangement = Arrangement.spacedBy(NextMetrics.space3),
                ) {
                    RankRow(
                        rank = DASH,
                        score = score,
                        widestEvidence = widestEvidence,
                        wide = wide,
                        qualified = false,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
                    ) {
                        NextChip(
                            text = "${score.judged} of " +
                                "${PerformanceCalculator.MINIMUM_JUDGED_TO_RANK} settled",
                            tone = colors.expired,
                            style = ChipStyle.DASHED,
                        )
                        NextText(
                            "Kept out of the order until it has " +
                                "${PerformanceCalculator.MINIMUM_JUDGED_TO_RANK}. Rate shown, not " +
                                "credited.",
                            NextType.meta,
                            colors.ink3,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankRow(
    rank: String,
    score: ChannelScore,
    widestEvidence: Int,
    wide: Boolean,
    qualified: Boolean,
) {
    val colors = LocalNextColors.current
    val rateTone = if (qualified) colors.entry else colors.figMuted
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (qualified) Modifier.ruleTop(colors.ruleSoft) else Modifier)
            .padding(vertical = NextMetrics.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        NextFigure(
            text = rank,
            color = if (qualified) colors.ink else colors.ruleStrong,
            modifier = Modifier.width(22.dp),
        )
        NextText(
            text = score.channel,
            style = NextType.name,
            color = if (qualified) colors.ink else colors.ink2,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(Modifier.width(118.dp)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space1),
            ) {
                NextFigure(
                    text = formatRate(score.anyTargetRate),
                    color = rateTone,
                    style = NextType.figure.copy(fontSize = 23.sp),
                    derived = true,
                )
                NextFigure(
                    text = "⁄${score.judged}",
                    color = if (qualified) colors.figMuted else colors.expired,
                    style = NextType.meta,
                )
            }
            Spacer(Modifier.height(NextMetrics.space2))
            EvidenceBar(
                judged = score.judged,
                widest = widestEvidence,
                qualified = qualified,
            )
        }
        if (wide) {
            NextFigure(
                text = formatRate(score.fullHitRate),
                color = if (qualified) colors.target else colors.figMuted,
                modifier = Modifier.width(56.dp),
                derived = true,
            )
            NextFigure(
                text = formatPercent(score.averageReturn),
                color = if (qualified) returnTone(score.averageReturn, colors) else colors.figMuted,
                modifier = Modifier.width(64.dp),
                derived = true,
            )
        }
    }
}

/** How much evidence a rate rests on, drawn against the widest column in the table. */
@Composable
private fun EvidenceBar(judged: Int, widest: Int, qualified: Boolean) {
    val colors = LocalNextColors.current
    val share = (judged.toFloat() / widest.toFloat()).coerceIn(0f, 1f)
    val gate = (PerformanceCalculator.MINIMUM_JUDGED_TO_RANK.toFloat() / widest.toFloat())
        .coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(colors.ruleSoft),
    ) {
        Box(
            Modifier
                .fillMaxWidth(share)
                .height(4.dp)
                .background(if (qualified) colors.market else colors.expired),
        )
        // Where five settled calls falls. A bar that has not reached it is a rate nobody should be
        // acting on, and the tick says so without a sentence.
        Box(
            Modifier
                .fillMaxWidth(gate)
                .height(4.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .width(NextMetrics.hairline)
                    .height(8.dp)
                    .background(colors.entry),
            )
        }
    }
}

/** The three tiers, with what is in each. Drawn once, above the list they explain. */
@Composable
private fun TierLegend(calls: List<ScoredCall>) {
    val colors = LocalNextColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .ruleBottom(colors.ruleStrong)
            .padding(vertical = NextMetrics.space4),
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TierMark(TIER_VERDICT, calls.count { it.outcome.judged }, colors.target, ChipStyle.FILLED)
        TierMark(
            TIER_RUNNING,
            calls.count { it.outcome == Outcome.OPEN },
            colors.market,
            ChipStyle.HOLLOW,
        )
        TierMark(
            TIER_EXCLUDED,
            calls.count { !it.outcome.judged && it.outcome != Outcome.OPEN },
            colors.figMuted,
            ChipStyle.DASHED,
        )
    }
}

@Composable
private fun TierMark(label: String, count: Int, tone: Color, style: ChipStyle) {
    val colors = LocalNextColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .then(
                    when (style) {
                        ChipStyle.FILLED -> Modifier.background(tone)
                        ChipStyle.HOLLOW -> Modifier.border(NextMetrics.hairline, tone)
                        ChipStyle.DASHED -> Modifier.dashedEdge(tone)
                    },
                ),
        )
        NextText(label.uppercase(Locale.ROOT), NextType.navLabel, colors.ink2)
        NextFigure("$count", colors.ink3, style = NextType.navLabel)
    }
}

/**
 * One session's calls, in the same card every other screen uses.
 *
 * The folded summary counts the three tiers rather than the three trade states, which is the same
 * strip doing the same job with this screen's own vocabulary.
 */
@Composable
private fun SessionCallCard(
    session: ScoredSession,
    calls: List<ScoredCall>,
    open: Boolean,
    onToggle: () -> Unit,
    levelColumns: Int,
    appState: AppState,
    page: NextPageState,
) {
    val colors = LocalNextColors.current
    val counted = calls.filter { it.outcome.judged }
    val running = calls.filter { it.outcome == Outcome.OPEN }
    val excluded = calls.filter { !it.outcome.judged && it.outcome != Outcome.OPEN }

    NextRecordCard(
        open = open,
        onToggle = onToggle,
        header = { chevron ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
            ) {
                NextFigure(
                    text = formatFullDay(session.targetDate),
                    color = colors.ink,
                    style = NextType.ticker,
                )
                NextChevron(chevron)
                Spacer(Modifier.weight(1f))
                NextText(
                    "${session.channelsTotal} ${if (session.channelsTotal == 1) "source" else "sources"}",
                    NextType.meta,
                    colors.figMuted,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = NextMetrics.space4)
                    .dottedRuleTop(colors.ruleSoft)
                    .padding(top = NextMetrics.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
            ) {
                TierCount(counted.size, "counted", colors.target)
                TierCount(running.size, "running", colors.market)
                TierCount(excluded.size, "excluded", colors.figMuted)
                Spacer(Modifier.weight(1f))
                NextText(
                    text = calls.joinToString(" ") { it.ticker }.take(28),
                    style = NextType.meta,
                    color = colors.figMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        body = {
            if (counted.isNotEmpty()) {
                NextSectionMark(TIER_VERDICT, counted.size, colors.target)
                counted.forEach { CallBlock(it, levelColumns, appState, page) }
            }
            if (running.isNotEmpty()) {
                NextSectionMark(TIER_RUNNING, running.size, colors.market)
                running.forEach { CallBlock(it, levelColumns, appState, page) }
            }
            if (excluded.isNotEmpty()) {
                NextSectionMark(TIER_EXCLUDED, excluded.size, colors.figMuted)
                excluded.forEach { CallBlock(it, levelColumns, appState, page) }
            }
        },
    )
}

@Composable
private fun TierCount(value: Int, label: String, tone: Color) {
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

/**
 * One call, as the channel printed it and as the tape answered it.
 *
 * A call the user actually traded is marked and leads to its trade; one they only watched is not
 * pressable, because there is nothing on the other end of the press.
 */
@Composable
private fun CallBlock(
    call: ScoredCall,
    levelColumns: Int,
    appState: AppState,
    page: NextPageState,
) {
    val colors = LocalNextColors.current
    val held = appState.heldFor(call.ticker, call.openedOn)
    val arriving = appState.pendingCallId == call.positionId

    // The arrival flash: 900ms of accent on the leading edge, and then gone. It is held by the
    // pending id rather than by an animation, so the row that was arrived at is the row that is
    // marked even if the list re-sorts underneath it.
    LaunchedEffect(arriving) {
        if (arriving) {
            delay(NextMotion.ARRIVE_MILLIS.toLong())
            appState.consumePendingCall()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .ruleTop(colors.rule)
            .then(if (arriving) Modifier.spine(colors.accent) else Modifier)
            .then(
                if (held != null) {
                    Modifier.clickable { appState.openPosition(held.position.id) }
                } else {
                    Modifier
                },
            )
            .padding(
                start = if (arriving) NextMetrics.space4 else 0.dp,
                top = NextMetrics.space4,
                bottom = NextMetrics.space5,
            ),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
        ) {
            NextFigure(call.ticker, colors.ink, style = NextType.ticker)
            NextText(
                text = call.companyArabic ?: call.companyEnglish ?: call.channel,
                style = NextType.name,
                color = colors.ink2,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            NextChip(
                text = call.outcome.label,
                tone = outcomeTone(call.outcome, colors),
                style = outcomeStyle(call.outcome),
                fill = if (call.outcome.judged) {
                    outcomeTone(call.outcome, colors).copy(alpha = 0.13f)
                } else {
                    null
                },
                glyph = outcomeGlyph(call.outcome),
            )
        }

        NextFigureGrid(
            columns = levelColumns,
            cells = listOf(
                {
                    NextFigureCell(
                        "Entry zone",
                        if (call.entryLow != null && call.entryHigh != null) {
                            "${formatPrice(call.entryLow)}–${formatPrice(call.entryHigh)}"
                        } else {
                            formatPrice(call.entryLow ?: call.entryHigh)
                        },
                        colors.entry,
                    )
                },
                { NextFigureCell("Target 1", formatPrice(call.target1), colors.target) },
                { NextFigureCell("Target 2", formatPrice(call.target2), colors.target) },
                { NextFigureCell("Stop", formatPrice(call.stopLoss), colors.stop) },
                {
                    NextFigureCell(
                        "Peak",
                        formatPrice(call.peakHigh),
                        colors.market,
                        derived = true,
                    )
                },
                {
                    NextFigureCell(
                        "Trough",
                        formatPrice(call.troughLow),
                        colors.market,
                        derived = true,
                    )
                },
                {
                    NextFigureCell(
                        "Return",
                        formatPercent(call.returnPct),
                        returnTone(call.returnPct, colors),
                        derived = true,
                    )
                },
                {
                    NextFigureCell(
                        "Sessions",
                        call.sessionsElapsed.toString(),
                        colors.figMuted,
                    )
                },
            ),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .dottedRuleTop(colors.ruleSoft)
                .padding(top = NextMetrics.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
        ) {
            if (held != null) {
                // A call the reader actually traded carries its own P/L and leads to the trade;
                // one they only watched offers to become one.
                NextChip(
                    text = "Held",
                    tone = colors.accent,
                    style = ChipStyle.FILLED,
                    fill = colors.accentFill,
                )
                NextFigure(
                    text = formatPercent(held.returnPct),
                    color = returnTone(held.returnPct, colors),
                    derived = !held.realized,
                )
                Spacer(Modifier.weight(1f))
                NextText("Open this trade →", NextType.meta, colors.accent)
            } else {
                Spacer(Modifier.weight(1f))
                NextButton(
                    label = "Bought",
                    onClick = { page.buyingCall = call.positionId },
                    tone = colors.accent,
                    labelColor = colors.accent,
                    fill = NextFill.WASH,
                    minHeight = 40.dp,
                )
            }
        }
    }
}

private fun outcomeTone(outcome: Outcome, colors: NextColors): Color = when (outcome) {
    Outcome.FULL_HIT, Outcome.PARTIAL_HIT -> colors.target
    Outcome.STOPPED -> colors.stop
    Outcome.EXPIRED -> colors.expired
    Outcome.OPEN -> colors.market
    else -> colors.figMuted
}

private fun outcomeStyle(outcome: Outcome): ChipStyle = when {
    outcome.judged -> ChipStyle.FILLED
    outcome == Outcome.OPEN -> ChipStyle.HOLLOW
    else -> ChipStyle.DASHED
}

private fun outcomeGlyph(outcome: Outcome): String? = when (outcome) {
    Outcome.FULL_HIT -> "▲"
    Outcome.PARTIAL_HIT -> "△"
    Outcome.STOPPED -> "▼"
    Outcome.EXPIRED -> "◇"
    Outcome.OPEN -> "◷"
    else -> null
}
