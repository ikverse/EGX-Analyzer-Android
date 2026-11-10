package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/** One day of the EGX30 line on the Portfolio chart: its move since the chart's first day. */
data class IndexPoint(val date: LocalDate, val changePct: Double)

/**
 * The EGX30 as a percentage move over the same days as the Portfolio chart, so the two lines start
 * together and the gap between them is how the trades did against the market.
 *
 * Measured from the index's close on or before the first of [days]. A day the index has no close
 * for (a session the feed missed) takes the last close before it, as the chart does for a quiet
 * stock. With no close on or before the first day the line starts at the index's first close
 * instead, and days before that are left out rather than invented.
 *
 * @param index the index's daily sessions, in any order - built from hourly bars, since the feed's
 *   daily endpoint answers this index with its newest session alone.
 */
fun indexByDay(index: List<DailySession>, days: List<LocalDate>): List<IndexPoint> {
    val closes = index.mapNotNull { s -> s.close?.let { s.date to it } }.sortedBy { it.first }
    if (closes.isEmpty() || days.isEmpty()) return emptyList()
    val ordered = days.sorted()
    fun closeOn(day: LocalDate): Double? = closes.lastOrNull { it.first <= day }?.second
    val base = closeOn(ordered.first()) ?: closes.first().second
    if (base <= 0.0) return emptyList()
    return ordered.mapNotNull { day ->
        closeOn(day)?.let { IndexPoint(day, (it - base) / base * 100.0) }
    }
}
