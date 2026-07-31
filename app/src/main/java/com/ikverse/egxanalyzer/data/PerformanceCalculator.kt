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

        val scoredRuns = runs(analyses, pricesFrom).map { (run, rawCalls) ->
            run.copy(
                calls = rawCalls.map { call ->
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
            // Grouped into one record per session: two analyses of the same day are the same
            // subject, and showing them as separate cards headed by the same date said nothing.
            sessions = scoredRuns
                .groupBy { it.targetDate }
                .map { (date, group) ->
                    val latest = group.maxBy { it.completedAt }
                    ScoredSession(
                        targetDate = date,
                        lastRunAt = latest.completedAt,
                        model = latest.model,
                        runCount = group.size,
                        calls = group.flatMap(RunCalls::calls).sortedBy(ScoredCall::ticker),
                    )
                }
                .sortedByDescending { it.targetDate },
        )
    }

    /** One analysis and the calls that survived deduplication from it. */
    private data class RunCalls(
        val targetDate: LocalDate?,
        val completedAt: Instant,
        val model: String,
        val calls: List<ScoredCall>,
    )

    /** Calls grouped by the analysis that produced them. */
    private fun runs(
        analyses: List<SavedAnalysis>,
        since: LocalDate,
    ): List<Pair<RunCalls, List<ScoredCall>>> {
        // Deduplication still spans runs: a call repeated by a later analysis of the same day is
        // one call, and it belongs to whichever run described it most completely.
        val owner = mutableMapOf<Triple<String, String, LocalDate>, Long>()
        val unique = uniqueCalls(analyses, since, owner)
        val byRun = unique.groupBy { call ->
            owner.getValue(Triple(call.ticker, call.channel, call.openedOn))
        }
        return analyses.mapNotNull { saved ->
            val calls = byRun[saved.id] ?: return@mapNotNull null
            RunCalls(
                targetDate = saved.result.recommendationTargetDate,
                completedAt = saved.result.completedAt,
                model = saved.model,
                calls = emptyList(),
            ) to calls
        }
    }

    /**
     * One entry per call, not per time a call was written down.
     *
     * Re-running an analysis for the same session saves another result listing the same
     * recommendations, so the raw rows count a single call once per run. Left alone that inflates
     * every total and quietly gives extra weight to whichever channel was analysed most often.
     *
     * Calls are keyed by stock, channel and target session, and the most recent run wins. A later
     * run is the considered view - it read the same sources with whatever was fixed since - so a
     * re-run replaces its predecessor rather than competing with it.
     */
    private fun uniqueCalls(
        analyses: List<SavedAnalysis>,
        since: LocalDate,
        owner: MutableMap<Triple<String, String, LocalDate>, Long>,
    ): List<ScoredCall> {
        val best = linkedMapOf<Triple<String, String, LocalDate>, Pair<Instant, ScoredCall>>()
        analyses.forEach { saved ->
            val channelNames = saved.result.sources
                .filter { it.messageId != null }
                .associate { it.messageId.toString() to it.channelName }
            val targetDate = saved.result.recommendationTargetDate
            val ranAt = saved.result.completedAt
            saved.result.consolidated.forEach { stock ->
                stock.dataPoints.forEach { point ->
                    val call = point.toCall(stock, channelNames, targetDate) ?: return@forEach
                    if (call.openedOn < since) return@forEach
                    val key = Triple(call.ticker, call.channel, call.openedOn)
                    val existing = best[key]
                    if (existing == null || ranAt >= existing.first) {
                        best[key] = ranAt to call
                        owner[key] = saved.id
                    }
                }
            }
        }
        return best.values.map { (_, call) -> call }
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
