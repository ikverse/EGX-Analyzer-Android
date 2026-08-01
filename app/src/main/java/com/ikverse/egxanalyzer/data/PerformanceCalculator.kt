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
                        peakHigh = scored.peakHigh,
                        peakOn = scored.peakOn,
                        troughLow = scored.troughLow,
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
                        channelsFromLatest = run.channelsFromLatest,
                        channelsTotal = run.channelsTotal,
                        calls = run.calls.sortedBy(ScoredCall::ticker),
                    )
                }
                .sortedByDescending { it.targetDate },
        )
    }

    /** What one target session ends up holding, and how many runs it came from. */
    private data class RunCalls(
        val targetDate: LocalDate?,
        val completedAt: Instant,
        val model: String,
        val runCount: Int,
        val channelsFromLatest: Int,
        val channelsTotal: Int,
        val calls: List<ScoredCall>,
    )

    /**
     * For each session and each chat, the newest run that actually covered that chat.
     *
     * A run can only speak about what it read. Taking the newest run wholesale let an unrelated
     * re-run over one chat erase four other chats' calls for that day, quietly shrinking their
     * records; merging runs indiscriminately did the opposite and resurrected calls a re-run had
     * already dropped. Keyed per chat, a re-run replaces the chats it covered - a dropped stock
     * stays dropped - while chats it never looked at keep their last real reading.
     *
     * Coverage comes from the recorded selection. Analyses saved before that was stored fall back
     * to the chats that produced sources, which understates them, so an older run can only lose a
     * chat to a newer one that demonstrably read it.
     */
    private fun runs(
        analyses: List<SavedAnalysis>,
        since: LocalDate,
    ): List<RunCalls> = analyses
        .groupBy { it.result.recommendationTargetDate }
        .mapNotNull { (targetDate, forSession) ->
            val reads = forSession
                .sortedByDescending { it.result.completedAt }
                .map { saved -> saved to callsByChannel(saved, targetDate, since) }

            val claimed = mutableSetOf<Long?>()
            val chosen = linkedMapOf<SavedAnalysis, List<ScoredCall>>()
            for ((saved, byChannel) in reads) {
                val covered = coverage(saved)
                val mine = byChannel.filterKeys { it !in claimed && (covered.isEmpty() || it in covered) }
                if (mine.isEmpty()) continue
                claimed += mine.keys
                chosen[saved] = mine.values.flatten()
            }
            if (chosen.isEmpty()) return@mapNotNull null

            val newest = chosen.keys.first()
            RunCalls(
                targetDate = targetDate,
                completedAt = newest.result.completedAt,
                model = newest.model,
                runCount = chosen.size,
                channelsFromLatest = chosen.getValue(newest).map(ScoredCall::channelId).distinct().size,
                channelsTotal = claimed.size,
                calls = chosen.values.flatten(),
            )
        }

    /** The chats a run was pointed at, or the ones it heard from when that was not recorded. */
    private fun coverage(saved: SavedAnalysis): Set<Long?> =
        saved.result.selectedChannels.map { it.id as Long? }.toSet().ifEmpty {
            saved.result.sources.map { it.channelId }.toSet()
        }

    /**
     * One run's calls, grouped by the chat that made them.
     *
     * A stock described several times in one run collapses to the reading stating the most price
     * levels, since runs differ mainly in how much of a message the model managed to read.
     */
    private fun callsByChannel(
        saved: SavedAnalysis,
        targetDate: LocalDate?,
        since: LocalDate,
    ): Map<Long?, List<ScoredCall>> {
        val traces = saved.result.sources.filter { it.messageId != null }
        val channelNames = traces.associate { it.messageId.toString() to it.channelName }
        val channelIds = traces.associate { it.messageId.toString() to it.channelId }

        val best = linkedMapOf<Pair<String, Long?>, Pair<Int, ScoredCall>>()
        saved.result.consolidated.forEach { stock ->
            stock.dataPoints.forEach { point ->
                val call = point.toCall(stock, channelNames, channelIds, targetDate) ?: return@forEach
                if (call.openedOn < since) return@forEach
                val levels = listOf(
                    call.entryLow, call.entryHigh, call.target1, call.target2, call.stopLoss,
                ).count { it != null }
                val key = call.ticker to call.channelId
                val existing = best[key]
                if (existing == null || levels > existing.first) best[key] = levels to call
            }
        }
        return best.values.map { (_, call) -> call }.groupBy(ScoredCall::channelId)
    }

    private fun RecommendationDataPoint.toCall(
        stock: ConsolidatedRecommendation,
        channelNames: Map<String, String>,
        channelIds: Map<String, Long?>,
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
            channelId = channelIds[sourceMessageId],
            openedOn = openedOn,
            // Kept exactly as the source printed them. Rounding here reached the scorer, not just
            // the screen: on a stock trading at 0.243 a target of 0.275 became 0.28, and the run
            // then waited for a price nobody had called.
            entryLow = buyPriceLow ?: buyPrice,
            entryHigh = buyPriceHigh ?: buyPrice,
            target1 = target1,
            target2 = target2,
            stopLoss = stopLoss,
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
