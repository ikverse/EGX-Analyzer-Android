package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Keeping up with the market while it is trading, as a fixed rule rather than a configured one.
 *
 * There is exactly one shape of this worth having - every quarter of an hour, on the days the
 * exchange is open, between the bell and a little past the close - and the form that used to let a
 * user assemble it out of days, windows and intervals was the whole of the complexity in the
 * feature for the one answer everybody would pick. So the window is a constant, the checkbox is
 * the entire configuration, and what is left is pure arithmetic that can be checked without
 * waiting for Sunday.
 *
 * No Android in here, for the reason [ScheduleClock] has none: a rule about what happens at 11:15
 * next Tuesday cannot be tested by waiting for next Tuesday.
 */
object MarketRefresh {

    /**
     * A quarter of an hour, and it cannot usefully be less.
     *
     * Android holds `setExactAndAllowWhileIdle` to roughly one alarm every ten minutes while the
     * phone is dozing, so a shorter gap is not refused - it is quietly stretched, and the app ends
     * up promising a frequency the system was never going to keep. Fifteen is the shortest
     * interval that is honest.
     */
    const val EVERY_MINUTES = 15

    /**
     * How late a slot may be served, and it is deliberately one slot.
     *
     * A phone that woke at 11:07 owed the 11:00 fetch and should do it. A phone that wakes at
     * 11:14 is a minute from the next slot, and fetching for a slot that is about to be superseded
     * is one wasted pass over a public feed the app is a guest on. Past the grace the next slot is
     * the retry - which, unlike a daily schedule, is never more than a quarter of an hour away.
     */
    const val GRACE_MINUTES = 15

    /**
     * Every moment this fires on a trading day, earliest first: 10:00, 10:15 ... 14:45.
     *
     * Counted in whole minutes from midnight rather than by adding to a [LocalTime], because
     * [LocalTime.plusMinutes] wraps silently at midnight and a window near the end of the day
     * would step past it, come back round as an earlier time, and satisfy the stop condition
     * forever. An [IntProgression] cannot wrap.
     *
     * Both ends are inclusive. The exchange stops at 14:30 and the day's figures settle over the
     * minutes after it, so the window runs to [ScheduleClock.sessionEnd] - a quarter past the
     * close - and the last fire of the day is the one that leaves the session's row final until
     * the next one opens.
     */
    val slots: List<LocalTime> = run {
        val start = ScheduleClock.sessionStart.toSecondOfDay() / 60
        val end = ScheduleClock.sessionEnd.toSecondOfDay() / 60
        (start..end step EVERY_MINUTES).map { LocalTime.ofSecondOfDay(it * 60L) }
    }

    /**
     * The first fire strictly after [after].
     *
     * Strictly after, so asking the instant a slot is served gives the next one rather than the
     * one just done. Never null: the exchange opens again eventually, and a checkbox that is on
     * always has a next time to name.
     */
    fun nextFire(after: Instant, zone: ZoneId = ScheduleClock.ZONE): Instant {
        val from = after.atZone(zone).toLocalDate()
        // Eight days, not seven: today's slots may all be behind us, so a Thursday evening has to
        // reach the Sunday after next week's Thursday to find one.
        return (0..7).asSequence()
            .map { from.plusDays(it.toLong()) }
            .filter { it.dayOfWeek in ScheduleClock.tradingDays }
            .flatMap { date -> slots.asSequence().map { date.atTime(it) } }
            // A local time daylight saving skipped does not exist, and atZone steps forward across
            // the gap rather than throwing - which for a fetch is the right answer.
            .map { it.atZone(zone).toInstant() }
            .first { it.isAfter(after) }
    }

    /** The most recent fire at or before [at], whether or not anything served it. */
    fun previousFire(at: Instant, zone: ZoneId = ScheduleClock.ZONE): Instant? {
        val from = at.atZone(zone).toLocalDate()
        val backwards = slots.asReversed()
        return (0..7).asSequence()
            .map { from.minusDays(it.toLong()) }
            .filter { it.dayOfWeek in ScheduleClock.tradingDays }
            .flatMap { date -> backwards.asSequence().map { date.atTime(it) } }
            .map { it.atZone(zone).toInstant() }
            .firstOrNull { !it.isAfter(at) }
    }

    /**
     * The slot that is owed a fetch right now, or null where none is.
     *
     * Three ways to owe nothing, and each is a real case rather than a guard for its own sake.
     * Outside a session there is no previous slot on the clock at all. Past the grace the slot has
     * been superseded by the next one. And a fetch that already happened after the slot came due
     * has done its work for it - which is what stops opening the app inside a missed slot's window
     * fetching every stock twice within seconds, once for the launch and once for the alarm.
     */
    fun dueFire(
        now: Instant,
        lastRefreshAt: Instant?,
        zone: ZoneId = ScheduleClock.ZONE,
    ): Instant? {
        val due = previousFire(now, zone) ?: return null
        if (now.isAfter(due.plusSeconds(GRACE_MINUTES * 60L))) return null
        if (lastRefreshAt != null && !lastRefreshAt.isBefore(due)) return null
        return due
    }
}
