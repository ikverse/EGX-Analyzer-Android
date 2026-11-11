package com.ikverse.egxanalyzer.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
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

/**
 * One reading of a session: a run, how much it found, and what it found that the reading before it
 * had not. [newStocks] is null on the first reading of the day, which has nothing to be new against.
 */
data class Reading(val saved: SavedAnalysis, val calls: Int, val stocks: Int, val newStocks: List<String>?)

/** The reading a session's card is drawn from: the one made last. */
fun newestReading(runs: List<SavedAnalysis>): SavedAnalysis = runs.maxBy { it.result.completedAt }

/**
 * Every reading of one session, newest first, each saying which stocks it named that the reading
 * before it did not. A session read three times is one card in the list; this is what the sheet
 * behind that card's "read 3 times" lists.
 */
fun readingsOf(runs: List<SavedAnalysis>): List<Reading> {
    val oldestFirst = runs.sortedBy { it.result.completedAt }
    return oldestFirst.mapIndexed { index, saved ->
        val tickers = saved.result.consolidated.map { it.stockCode }
        val before = oldestFirst.getOrNull(index - 1)
            ?.result?.consolidated?.map { Scoring.normalizeTicker(it.stockCode) }?.toSet()
        Reading(
            saved = saved,
            calls = saved.result.consolidated.sumOf { it.dataPoints.size },
            stocks = tickers.size,
            newStocks = before?.let { seen -> tickers.filter { Scoring.normalizeTicker(it) !in seen } },
        )
    }.reversed()
}

/** "read once at 21:40", "read twice, last at 13:20", "read 5 times, last at 14:30". Cairo time. */
fun readLine(count: Int, lastAt: Instant, zone: ZoneId = ScheduleClock.ZONE): String {
    val time = lastAt.atZone(zone).format(READ_TIME)
    return when (count) {
        1 -> "read once at $time"
        2 -> "read twice, last at $time"
        else -> "read $count times, last at $time"
    }
}

/** A reading's own time, `13:20`. */
fun readingTime(at: Instant, zone: ZoneId = ScheduleClock.ZONE): String = at.atZone(zone).format(READ_TIME)

private val READ_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val WEEK_OF: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/**
 * The heading a session is listed under. The exchange's week runs Sunday to Thursday, so a week
 * starts on Sunday here.
 */
fun weekHeading(date: LocalDate, today: LocalDate): String {
    fun start(day: LocalDate) = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val from = start(date)
    return when (ChronoUnit.WEEKS.between(start(today), from)) {
        0L -> "This week"
        -1L -> "Last week"
        1L -> "Next week"
        else -> "Week of ${from.format(WEEK_OF)}"
    }
}

/** Where one stock of a report stands, for the tag on its session's card. */
data class StockStanding(val ticker: String, val group: CallGroup)

/**
 * Each stock of [saved] with the best thing any of its calls has done: reached a target, else been
 * stopped, else still running. Reached first, then stopped, then the rest, each in report order.
 */
fun stockStandings(saved: SavedAnalysis, report: PerformanceReport): List<StockStanding> =
    saved.result.consolidated.map { stock ->
        val groups = stock.dataPoints.map { callGroup(it, scoredCallFor(saved, stock, it, report)) }
        StockStanding(
            stock.stockCode,
            when {
                CallGroup.REACHED in groups -> CallGroup.REACHED
                CallGroup.STOPPED in groups -> CallGroup.STOPPED
                else -> CallGroup.RUNNING
            },
        )
    }.sortedBy {
        when (it.group) {
            CallGroup.REACHED -> 0
            CallGroup.STOPPED -> 1
            else -> 2
        }
    }

/** The orders an open report's stocks can be read in. */
enum class CallSort(val label: String, val sentence: String) {
    CLOSEST("Closest to target", "closest to target first"),
    MOVE("Biggest move", "biggest move first"),
    REPORT("Report order", "in the report's order"),
}

/**
 * How near a call is to its first target, smaller being nearer: the fraction of the price still to
 * travel, which is nought or less once the target is reached. A running call with no price sorts
 * after every one that has one; a call the market has finished with after those; a watch last.
 */
fun closenessKey(group: CallGroup, target1: Double?, close: Double?): Double = when (group) {
    CallGroup.WATCHING -> 1e9
    CallGroup.STOPPED, CallGroup.ENDED -> 1e8
    CallGroup.REACHED, CallGroup.RUNNING ->
        if (target1 != null && close != null && close != 0.0) (target1 - close) / close
        else if (group == CallGroup.REACHED) 0.0
        else 1e7
}
