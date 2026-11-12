package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * One five-minute bar of one session, for the 1D range on a chart.
 *
 * Fetched when a reader presses 1D and held in memory only. Nothing here is stored, scored or
 * synced: the daily record is what a call is judged on, and this is a closer look at one day of it.
 */
data class DayBar(
    val at: Instant,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val close: Double?,
    val volume: Double? = null,
) {
    /** The session this bar belongs to, read on the exchange's own clock. */
    fun session(zone: ZoneId = ScheduleClock.ZONE): LocalDate = at.atZone(zone).toLocalDate()

    /** The time on the exchange's clock, which is what the axis under a day's line prints. */
    fun time(zone: ZoneId = ScheduleClock.ZONE): LocalTime = at.atZone(zone).toLocalTime()

    companion object {
        /** What the EGX30 is asked for by, where a ticker is expected. Not a listing. */
        const val INDEX = "EGX30"
    }
}

/** How far a day's line had moved by one moment, in percent, from where the session started. */
data class DayPoint(val at: Instant, val changePct: Double)

/**
 * The bars of the newest session in the list, oldest first, and only those with a close.
 *
 * A request for one day is made across a week, so that a weekend or a holiday still answers with
 * the last session the exchange held instead of with nothing.
 */
fun List<DayBar>.newestSession(zone: ZoneId = ScheduleClock.ZONE): List<DayBar> {
    val priced = filter { it.close != null }.sortedBy { it.at }
    val newest = priced.lastOrNull()?.session(zone) ?: return emptyList()
    return priced.filter { it.session(zone) == newest }
}

/** Where a session started: its first bar's open, or that bar's close where the feed gave no open. */
private fun List<DayBar>.startPrice(): Double? =
    firstOrNull()?.let { it.open ?: it.close }?.takeIf { it > 0.0 }

/** What the session adds up to, first bar to last, in percent. */
fun List<DayBar>.dayMove(): Double? {
    if (size < 2) return null
    val start = startPrice() ?: return null
    val end = last().close ?: return null
    return (end - start) / start * 100
}

/** What the session had done by [bar], in percent from its start. */
fun List<DayBar>.dayMoveTo(bar: DayBar): Double? {
    val start = startPrice() ?: return null
    val close = bar.close ?: return null
    return (close - start) / start * 100
}

/** One session as a line of percentages from its start. Empty where it has no usable start. */
fun List<DayBar>.dayLine(): List<DayPoint> {
    val start = startPrice() ?: return emptyList()
    return mapNotNull { bar -> bar.close?.let { DayPoint(bar.at, (it - start) / start * 100) } }
}

/**
 * The average move of several stocks through one session, for the Portfolio chart's 1D range.
 *
 * Only stocks whose newest bars fall on the newest session any of them has are counted: a stock
 * that last traded on Tuesday says nothing about Thursday. A stock that has not traded yet at a
 * given moment counts as unmoved until its first bar, so the average is over the same stocks from
 * the left edge to the right and does not jump when a quiet one prints its first trade.
 *
 * @param sessions each stock's bars for its own newest session, as [newestSession] gives them.
 */
fun bookDayLine(sessions: Collection<List<DayBar>>, zone: ZoneId = ScheduleClock.ZONE): List<DayPoint> {
    val lines = sessions.filter { it.isNotEmpty() }
    val newest = lines.maxOfOrNull { it.last().session(zone) } ?: return emptyList()
    val counted = lines.filter { it.last().session(zone) == newest }.map { it.dayLine() }.filter { it.isNotEmpty() }
    if (counted.isEmpty()) return emptyList()
    val moments = counted.flatMap { line -> line.map { it.at } }.toSortedSet()
    return moments.map { at ->
        val moves = counted.map { line -> line.lastOrNull { !it.at.isAfter(at) }?.changePct ?: 0.0 }
        DayPoint(at, moves.average())
    }
}

/**
 * The index along a day's [book] line, as (place on the line, value) pairs.
 *
 * Each of the book's moments takes the index's last figure at or before it, so the two lines share
 * one clock. Empty where the index's bars are from another session than the book's - yesterday's
 * market beside today's trades would be a comparison of nothing with nothing.
 */
fun marketAlongDay(book: List<DayPoint>, index: List<DayPoint>, zone: ZoneId = ScheduleClock.ZONE): List<Pair<Int, Double>> {
    val session = book.lastOrNull()?.at?.atZone(zone)?.toLocalDate() ?: return emptyList()
    val same = index.filter { it.at.atZone(zone).toLocalDate() == session }
    if (same.isEmpty()) return emptyList()
    return book.mapIndexedNotNull { i, point -> same.lastOrNull { !it.at.isAfter(point.at) }?.let { i to it.changePct } }
}
