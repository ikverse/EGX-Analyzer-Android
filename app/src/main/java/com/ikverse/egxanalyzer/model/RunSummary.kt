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
