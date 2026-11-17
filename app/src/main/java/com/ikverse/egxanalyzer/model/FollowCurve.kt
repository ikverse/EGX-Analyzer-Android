package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/** One point on a "had you followed it" line: 100 at the start. */
data class FollowPoint(val date: LocalDate, val value: Double)

/**
 * What following every one of a channel's calls would have done, as a line that starts at 100 and
 * moves by each settled call's return in the order they settled. In percent, never in money: 112
 * is twelve percent up on wherever the reader started.
 *
 * Each call is taken whole at its entry and out at its scored result, one after another, so two
 * calls open at once compound as if made in turn - a reading of the record, not a backtest of a
 * book. Repeats are left out and a return `CallSanity` refuses is skipped, as in every rate here.
 * The line starts on the first counted call's own day, at 100.
 */
fun followCurve(calls: List<ScoredCall>): List<FollowPoint> {
    val counted = calls
        .filter { it.repeatOf == null && it.outcome.judged && it.believableReturn != null }
        .sortedWith(compareBy({ it.settledOn ?: it.openedOn }, { it.openedOn }))
    if (counted.isEmpty()) return emptyList()
    var value = 100.0
    val out = mutableListOf(FollowPoint(counted.first().openedOn, value))
    for (call in counted) {
        value *= 1 + call.believableReturn!! / 100.0
        val day = call.settledOn ?: call.openedOn
        // One point per day: the last call settled on it carries the line.
        if (out.last().date == day) out[out.lastIndex] = FollowPoint(day, value) else out += FollowPoint(day, value)
    }
    return out
}

/**
 * The EGX30 over the same days as [curve], started at the curve's own value on the first day both
 * have, so the gap between the two lines is the channel against the market. Days before the
 * index's first close are left out rather than invented, as `indexByDay` does.
 */
fun marketAlongCurve(curve: List<FollowPoint>, index: List<DatedClose>): List<FollowPoint> {
    val closes = index.sortedBy(DatedClose::date)
    if (curve.isEmpty() || closes.isEmpty()) return emptyList()
    fun closeOn(day: LocalDate): Double? = closes.lastOrNull { it.date <= day }?.close
    val start = curve.firstOrNull { closeOn(it.date) != null } ?: return emptyList()
    val base = closeOn(start.date)!!.takeIf { it > 0 } ?: return emptyList()
    return curve.filter { it.date >= start.date }.map { point ->
        FollowPoint(point.date, start.value * closeOn(point.date)!! / base)
    }
}
