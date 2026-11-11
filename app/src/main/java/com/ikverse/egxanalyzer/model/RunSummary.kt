package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * How a report's calls have turned out, for the bar on its tile in Results.
 *
 * Read from Insights' scoring rather than worked out again: a report's calls are the scored calls of
 * the session it was for whose stock the report named. Nothing is fetched or stored for it, and a
 * call Insights has not scored (no price yet, entry never traded) counts as still open.
 */
data class RunOutcomes(val hit: Int, val stopped: Int, val expired: Int, val open: Int) {
    val total: Int get() = hit + stopped + expired + open
}

fun runOutcomes(saved: SavedAnalysis, report: PerformanceReport): RunOutcomes {
    val date = saved.result.recommendationTargetDate
    val tickers = saved.result.consolidated.map { Scoring.normalizeTicker(it.stockCode) }.toSet()
    val calls = report.sessions
        .filter { it.targetDate == date }
        .flatMap(ScoredSession::calls)
        .filter { Scoring.normalizeTicker(it.ticker) in tickers }
    var hit = 0; var stopped = 0; var expired = 0; var open = 0
    calls.forEach { call ->
        when (call.outcome) {
            Outcome.FULL_HIT, Outcome.PARTIAL_HIT -> hit++
            Outcome.STOPPED -> stopped++
            Outcome.EXPIRED -> expired++
            else -> open++
        }
    }
    return RunOutcomes(hit, stopped, expired, open)
}

/**
 * A name a person would give a run: the weekday it finished on and the part of the day, so a list of
 * runs reads "Sunday morning", "Thursday close" rather than a column of timestamps. The report's
 * number and exact time stay beside it; this is only the heading.
 *
 * Cairo time, the exchange's, like every other time in the app. "Close" from the bell at 14:30.
 */
fun runTitle(completedAt: Instant, zone: ZoneId = ScheduleClock.ZONE): String {
    val at = completedAt.atZone(zone)
    val day = at.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val time = at.toLocalTime()
    val part = when {
        time.hour < 5 -> "night"
        time < java.time.LocalTime.NOON -> "morning"
        time < ScheduleClock.sessionClose -> "midday"
        time.hour < 18 -> "close"
        else -> "evening"
    }
    return "$day $part"
}

/**
 * Where a call in an open report stands, as the report's chips sort it.
 *
 * Five groups for nine outcomes, on purpose: the reader is asking "which of these is still live",
 * not which way the scorer declined to judge one. Anything the scorer has not judged - no price yet,
 * an ambiguous session, a change of scale - counts as running, which is what [runOutcomes] already
 * calls open. A watched stock is its own group whatever the scorer made of it, because it was never
 * a call to take.
 */
enum class CallGroup { RUNNING, REACHED, STOPPED, WATCHING, ENDED }

/** [point]'s group, given the scored call it became, if it became one. */
fun callGroup(point: RecommendationDataPoint, scored: ScoredCall?): CallGroup = when {
    point.isWatching -> CallGroup.WATCHING
    scored == null -> CallGroup.RUNNING
    else -> when (scored.outcome) {
        Outcome.FULL_HIT, Outcome.PARTIAL_HIT -> CallGroup.REACHED
        Outcome.STOPPED -> CallGroup.STOPPED
        Outcome.EXPIRED -> CallGroup.ENDED
        else -> CallGroup.RUNNING
    }
}

/**
 * The scored call one occurrence in [saved] became.
 *
 * By identity first - the report, the code the model filed it under, and its parse index, which is
 * what an Insights card uses to reach back to its occurrence. Then by what it is about: the ticker,
 * the session it was called for and, where known, the channel. The fallback is not a nicety.
 * Sessions merge re-runs, so the scored call for a stock read twice belongs to the newer report and
 * an older reading of the same call owns no scored call of its own; without the fallback every call
 * in every older report would read as still running.
 */
fun scoredCallFor(
    saved: SavedAnalysis,
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    report: PerformanceReport,
    channel: String? = null,
): ScoredCall? {
    val calls = report.sessions.asSequence().flatMap { it.calls.asSequence() }
    val anchor = stock.anchorOf(point)
    calls.firstOrNull { it.reportId == saved.id && it.originalStockCode == anchor && it.pointIndex == point.parseIndex }
        ?.let { return it }
    val ticker = Scoring.normalizeTicker(stock.stockCode)
    val on = point.callDate(saved.result.recommendationTargetDate) ?: return null
    val candidates = calls.filter { Scoring.normalizeTicker(it.ticker) == ticker && it.openedOn == on }.toList()
    return candidates.firstOrNull { channel != null && it.channel == channel } ?: candidates.firstOrNull()
}
