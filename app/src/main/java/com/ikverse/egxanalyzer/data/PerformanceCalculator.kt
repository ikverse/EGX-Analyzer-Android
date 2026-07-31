package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import com.ikverse.egxanalyzer.model.round
import java.time.Instant
import java.time.LocalDate

/**
 * Turns saved analyses plus stored sessions into a record for each source.
 *
 * Anything predating the first stored session is skipped rather than reported as unpriced, since a
 * call made before there was any price history says nothing about the source that made it.
 */
object PerformanceCalculator {

    fun report(
        analyses: List<SavedAnalysis>,
        pricesFrom: LocalDate?,
        windowSessions: Int,
        sessionsFor: (ticker: String, from: LocalDate) -> List<DailySession>,
        pricedTickers: Set<String> = emptySet(),
    ): PerformanceReport {
        val window = Scoring.clampWindow(windowSessions)
        if (pricesFrom == null) return PerformanceReport(windowSessions = window)

        val scoredRuns = runs(analyses, pricesFrom).map { run ->
            run.copy(
                calls = run.calls.map { call ->
                    val sessions = sessionsFor(call.ticker, call.openedOn).take(window)
                    val scored = Scoring.score(
                        sessions = sessions,
                        entryLow = call.entryLow,
                        entryHigh = call.entryHigh,
                        target1 = call.target1,
                        target2 = call.target2,
                        stopLoss = call.stopLoss,
                        windowSessions = window,
                    )
                    call.copy(
                        outcome = scored.outcome,
                        settledOn = scored.settledOn,
                        peakHigh = scored.peakHigh?.round(2),
                        peakOn = scored.peakOn,
                        troughLow = scored.troughLow?.round(2),
                        troughOn = scored.troughOn,
                        returnPct = scored.returnPct,
                        sessionsElapsed = scored.sessionsElapsed,
                        stoppedAfterPartial = scored.stoppedAfterPartial,
                        windowComplete = scored.windowComplete,
                        sessions = sessions,
                    )
                },
            )
        }
        val calls = scoredRuns.flatMap(RunCalls::calls)

        val judged = calls.count { it.outcome.judged }
        val full = calls.count { it.outcome.isFullHit }
        val any = calls.count { it.outcome.reachedATarget }
        return PerformanceReport(
            windowSessions = window,
            // The earliest call that was actually scored, so the figure describes the calls rather
            // than how far back the price history happens to reach.
            scoringSince = calls.minOfOrNull(ScoredCall::openedOn),
            unpricedStocks = calls.filter { it.outcome == Outcome.UNPRICED }
                .map(ScoredCall::ticker)
                .distinct()
                .count { it !in pricedTickers },
            awaitingSessions = calls.count {
                it.outcome == Outcome.UNPRICED && it.ticker in pricedTickers
            },
            tracked = calls.size,
            judged = judged,
            fullHits = full,
            partialHits = any - full,
            // Only calls that could be judged count toward either rate, so a stock with no price
            // data or an entry that never traded neither helps nor hurts them.
            fullHitRate = judged.takeIf { it > 0 }?.let { (full.toDouble() / it * 100).round(1) },
            anyTargetRate = judged.takeIf { it > 0 }?.let { (any.toDouble() / it * 100).round(1) },
            byOutcome = calls.groupingBy(ScoredCall::outcome).eachCount(),
            channels = channelScores(calls),
            sessions = scoredRuns
                .map { run ->
                    ScoredSession(
                        targetDate = run.targetDate,
                        lastRunAt = run.completedAt,
                        model = run.model,
                        runCount = run.runCount,
                        calls = run.calls.sortedBy(ScoredCall::ticker),
                    )
                }
                .sortedByDescending { it.targetDate },
        )
    }

    /** The analysis that speaks for one target session, and what it recommended. */
    private data class RunCalls(
        val targetDate: LocalDate?,
        val completedAt: Instant,
        val model: String,
        /** How many analyses exist for this session, of which only this one is read. */
        val runCount: Int,
        val calls: List<ScoredCall>,
    )

    /**
     * The newest analysis of each target session, and nothing else.
     *
     * Re-running a session supersedes the earlier attempt rather than adding to it: the later run
     * read the same sources with whatever had been fixed since, so its answer stands alone. Merging
     * the two would resurrect calls the newer run had already decided against, and counting both
     * would weight a session by how many times it happened to be analysed.
     */
    private fun runs(
        analyses: List<SavedAnalysis>,
        since: LocalDate,
    ): List<RunCalls> = analyses
        .groupBy { it.result.recommendationTargetDate }
        .mapNotNull { (targetDate, forSession) ->
            val latest = forSession.maxByOrNull { it.result.completedAt } ?: return@mapNotNull null
            val channelNames = latest.result.sources
                .filter { it.messageId != null }
                .associate { it.messageId.toString() to it.channelName }

            // Within the one run a stock can still be described by several occurrences; the copy
            // stating the most price levels is the fullest reading of it.
            val best = linkedMapOf<Pair<String, String>, Pair<Int, ScoredCall>>()
            latest.result.consolidated.forEach { stock ->
                stock.dataPoints.forEach { point ->
                    val call = point.toCall(stock, channelNames, targetDate) ?: return@forEach
                    if (call.openedOn < since) return@forEach
                    val levels = listOf(
                        call.entryLow, call.entryHigh, call.target1, call.target2, call.stopLoss,
                    ).count { it != null }
                    val key = call.ticker to call.channel
                    val existing = best[key]
                    if (existing == null || levels > existing.first) best[key] = levels to call
                }
            }
            if (best.isEmpty()) {
                null
            } else {
                RunCalls(
                    targetDate = targetDate,
                    completedAt = latest.result.completedAt,
                    model = latest.model,
                    runCount = forSession.size,
                    calls = best.values.map { (_, call) -> call },
                )
            }
        }

    private fun RecommendationDataPoint.toCall(
        stock: ConsolidatedRecommendation,
        channelNames: Map<String, String>,
        targetDate: LocalDate?,
    ): ScoredCall? {
        val ticker = Scoring.normalizeTicker(stock.stockCode)
        // The session the run was aimed at, which is the one the recommendation is for. Reading the
        // date off the extraction instead put a back-dated analysis under whatever day its sources
        // happened to be printed with, so scoring started from the wrong session.
        val openedOn = targetDate ?: date ?: visibleSourceDate?.let { value ->
            runCatching { LocalDate.parse(value.trim().take(10)) }.getOrNull()
        } ?: return null
        if (ticker.isBlank()) return null
        return ScoredCall(
            ticker = ticker,
            companyEnglish = stock.stockNameEnglish,
            companyArabic = stock.stockNameArabic,
            channel = channelNames[sourceMessageId]?.trim()?.takeIf(String::isNotBlank) ?: UNKNOWN,
            openedOn = openedOn,
            entryLow = (buyPriceLow ?: buyPrice)?.round(2),
            entryHigh = (buyPriceHigh ?: buyPrice)?.round(2),
            target1 = target1?.round(2),
            target2 = target2?.round(2),
            stopLoss = stopLoss?.round(2),
            outcome = Outcome.UNPRICED,
            settledOn = null,
            peakHigh = null,
            troughLow = null,
            returnPct = null,
            sessionsElapsed = 0,
        )
    }

    private fun channelScores(calls: List<ScoredCall>): List<ChannelScore> = calls
        .groupBy(ScoredCall::channel)
        .map { (channel, rows) ->
            val judged = rows.filter { it.outcome.judged }
            val full = judged.filter { it.outcome.isFullHit }
            val any = judged.filter { it.outcome.reachedATarget }
            val returns = judged.mapNotNull(ScoredCall::returnPct)
            ChannelScore(
                channel = channel,
                calls = rows.size,
                judged = judged.size,
                fullHits = full.size,
                partialHits = any.size - full.size,
                stopped = judged.count { it.outcome == Outcome.STOPPED },
                expired = judged.count { it.outcome == Outcome.EXPIRED },
                notTradable = rows.count {
                    it.outcome == Outcome.ENTRY_NOT_REACHED || it.outcome == Outcome.UNPRICED
                },
                fullHitRate = judged.size
                    .takeIf { it > 0 }
                    ?.let { (full.size.toDouble() / it * 100).round(1) },
                anyTargetRate = judged.size
                    .takeIf { it > 0 }
                    ?.let { (any.size.toDouble() / it * 100).round(1) },
                averageReturn = returns.takeIf(List<Double>::isNotEmpty)?.average()?.round(2),
                medianSessionsToHit = median(any.map(ScoredCall::sessionsElapsed)),
            )
        }
        // Channels with nothing judged sort last: a perfect record over zero calls is not a record.
        .sortedWith(
            compareByDescending<ChannelScore> { it.judged > 0 }
                .thenByDescending { it.anyTargetRate ?: 0.0 }
                .thenByDescending(ChannelScore::judged),
        )

    private fun median(values: List<Int>): Double? {
        if (values.isEmpty()) return null
        val ordered = values.sorted()
        val middle = ordered.size / 2
        return if (ordered.size % 2 == 1) {
            ordered[middle].toDouble()
        } else {
            (ordered[middle - 1] + ordered[middle]) / 2.0
        }
    }

    private const val UNKNOWN = "Unknown"
}

/** Every ticker named by any saved analysis; the rest cannot be scored, so pricing them is waste. */
fun List<SavedAnalysis>.recommendedTickers(): Set<String> = flatMap { saved ->
    saved.result.consolidated.map { Scoring.normalizeTicker(it.stockCode) } +
        saved.result.recommendations.map { Scoring.normalizeTicker(it.ticker) }
}.filter(String::isNotBlank).toSet()
