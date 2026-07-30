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
import com.ikverse.egxanalyzer.model.round
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
    ): PerformanceReport {
        val window = Scoring.clampWindow(windowSessions)
        if (pricesFrom == null) return PerformanceReport(windowSessions = window)

        val calls = uniqueCalls(analyses, pricesFrom).map { call ->
            val scored = Scoring.score(
                sessions = sessionsFor(call.ticker, call.openedOn),
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
                returnPct = scored.returnPct,
                sessionsElapsed = scored.sessionsElapsed,
            )
        }

        val judged = calls.count { it.outcome.judged }
        val hits = calls.count { it.outcome.isHit }
        return PerformanceReport(
            windowSessions = window,
            // The earliest call that was actually scored, so the figure describes the calls rather
            // than how far back the price history happens to reach.
            scoringSince = calls.minOfOrNull(ScoredCall::openedOn),
            unpricedStocks = calls.filter { it.outcome == Outcome.UNPRICED }
                .map(ScoredCall::ticker)
                .distinct()
                .size,
            tracked = calls.size,
            judged = judged,
            hits = hits,
            // Only calls that could be judged count toward the rate, so a stock with no price data
            // or an entry that never traded neither helps nor hurts it.
            hitRate = if (judged > 0) (hits.toDouble() / judged * 100).round(1) else null,
            byOutcome = calls.groupingBy(ScoredCall::outcome).eachCount(),
            channels = channelScores(calls),
            calls = calls.sortedWith(
                compareByDescending(ScoredCall::openedOn).thenBy(ScoredCall::ticker),
            ),
        )
    }

    /**
     * One entry per call, not per time a call was written down.
     *
     * Re-running the analysis on the same day saves another result listing the same
     * recommendations, so the raw rows count a single call once per run. Left alone that inflates
     * every total and quietly gives extra weight to whichever channel happened to be analysed most
     * often. Calls are therefore keyed by stock, channel and date, keeping whichever copy states
     * the most price levels, since runs differ mainly in how much of the message the model read.
     */
    private fun uniqueCalls(
        analyses: List<SavedAnalysis>,
        since: LocalDate,
    ): List<ScoredCall> {
        val best = linkedMapOf<Triple<String, String, LocalDate>, Pair<Int, ScoredCall>>()
        analyses.forEach { saved ->
            val channelNames = saved.result.sources
                .filter { it.messageId != null }
                .associate { it.messageId.toString() to it.channelName }
            saved.result.consolidated.forEach { stock ->
                stock.dataPoints.forEach { point ->
                    val call = point.toCall(stock, channelNames) ?: return@forEach
                    if (call.openedOn < since) return@forEach
                    val levels = listOf(
                        call.entryLow, call.entryHigh, call.target1, call.target2, call.stopLoss,
                    ).count { it != null }
                    val key = Triple(call.ticker, call.channel, call.openedOn)
                    val existing = best[key]
                    if (existing == null || levels > existing.first) best[key] = levels to call
                }
            }
        }
        return best.values.map { (_, call) -> call }
    }

    private fun RecommendationDataPoint.toCall(
        stock: ConsolidatedRecommendation,
        channelNames: Map<String, String>,
    ): ScoredCall? {
        val ticker = Scoring.normalizeTicker(stock.stockCode)
        // A call with no date cannot be scored: there is nothing to say which session it starts at.
        val openedOn = date ?: visibleSourceDate?.let { value ->
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
            returnPct = null,
            sessionsElapsed = 0,
        )
    }

    private fun channelScores(calls: List<ScoredCall>): List<ChannelScore> = calls
        .groupBy(ScoredCall::channel)
        .map { (channel, rows) ->
            val judged = rows.filter { it.outcome.judged }
            val hits = judged.filter { it.outcome.isHit }
            val returns = judged.mapNotNull(ScoredCall::returnPct)
            ChannelScore(
                channel = channel,
                calls = rows.size,
                judged = judged.size,
                hits = hits.size,
                stopped = judged.count { it.outcome == Outcome.STOPPED },
                expired = judged.count { it.outcome == Outcome.EXPIRED },
                notTradable = rows.count {
                    it.outcome == Outcome.ENTRY_NOT_REACHED || it.outcome == Outcome.UNPRICED
                },
                hitRate = judged.size
                    .takeIf { it > 0 }
                    ?.let { (hits.size.toDouble() / it * 100).round(1) },
                averageReturn = returns.takeIf(List<Double>::isNotEmpty)?.average()?.round(2),
                medianSessionsToHit = median(hits.map(ScoredCall::sessionsElapsed)),
            )
        }
        // Channels with nothing judged sort last: a perfect record over zero calls is not a record.
        .sortedWith(
            compareByDescending<ChannelScore> { it.judged > 0 }
                .thenByDescending { it.hitRate ?: 0.0 }
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
