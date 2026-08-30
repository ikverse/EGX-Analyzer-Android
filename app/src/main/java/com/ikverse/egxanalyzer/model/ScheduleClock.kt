package com.ikverse.egxanalyzer.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * When the analysis schedule fires, and whether one is owed a run right now.
 *
 * Pure arithmetic over a zone and a clock, with no Android and no storage in it, because this is
 * the part of the feature that is impossible to check by hand: a rule about what happens at 07:00
 * next Sunday cannot be tested by waiting for next Sunday. Everything above it - the alarm, the
 * worker, the card - only asks this file the two questions below.
 *
 * Times are Cairo's, always. A schedule belongs to the exchange the app follows, not to wherever
 * the phone happens to be. The zone is a parameter only so the tests can drive it through a
 * daylight-saving boundary on purpose.
 */
object ScheduleClock {

    val ZONE: ZoneId = ZoneId.of("Africa/Cairo")

    /**
     * The days the exchange is open, which is the only set anything here fires on.
     *
     * Fixed rather than chosen. A schedule that reads a trading session has nothing to say about
     * Friday, and every day-picker this app ever drew was a way of getting that wrong.
     */
    val tradingDays: Set<DayOfWeek> = setOf(
        DayOfWeek.SUNDAY,
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
    )

    /** The bell, Cairo time. */
    val sessionStart: LocalTime = LocalTime.of(10, 0)

    /**
     * A quarter of an hour after the close, not the close itself.
     *
     * The exchange stops at 14:30 and the day's figures settle over the minutes after it, so a
     * window ending on the bell stores a session that is very nearly but not quite final. One more
     * fetch past it costs a single pass and leaves the day's row correct until the next session
     * opens, which is the whole promise of keeping prices fresh while the market is trading.
     */
    val sessionEnd: LocalTime = LocalTime.of(14, 45)

    /**
     * The first fire of [at] on a trading day strictly after [after].
     *
     * Strictly after, so asking again the instant a schedule fires gives tomorrow's slot rather
     * than the one just served. Never null: there is always another trading day.
     */
    fun nextFire(at: LocalTime, after: Instant, zone: ZoneId = ZONE): Instant {
        val from = after.atZone(zone).toLocalDate()
        // Eight candidates, not seven: today's own slot may already be past, so a Thursday has to
        // be able to reach the Sunday that follows the weekend.
        return (0..7).asSequence()
            .map { from.plusDays(it.toLong()) }
            .filter { it.dayOfWeek in tradingDays }
            // A local time that daylight saving skipped does not exist, and atZone moves it
            // forward across the gap rather than throwing. That is the right answer for a
            // schedule: the run happens once, an hour later than it reads.
            .map { it.atTime(at).atZone(zone).toInstant() }
            .first { it.isAfter(after) }
    }

    /** The most recent fire of [at] at or before [moment], whether or not it was served. */
    fun previousFire(at: LocalTime, moment: Instant, zone: ZoneId = ZONE): Instant? {
        val from = moment.atZone(zone).toLocalDate()
        return (0..7).asSequence()
            .map { from.minusDays(it.toLong()) }
            .filter { it.dayOfWeek in tradingDays }
            .map { it.atTime(at).atZone(zone).toInstant() }
            .firstOrNull { !it.isAfter(moment) }
    }

    /**
     * The fire this schedule owes a run for, or null where it owes none.
     *
     * Owed rather than due-right-now: this deliberately still answers with a fire whose grace has
     * run out, so the caller can record that it was missed instead of leaving the schedule looking
     * as though it had never been booked. Whether it may still be run is [withinGrace].
     *
     * Compared against the fire last served rather than against the wall clock, which is what
     * stops one slot running twice on a phone whose clock jumped.
     */
    fun unservedFire(
        schedule: AnalysisSchedule,
        now: Instant,
        zone: ZoneId = ZONE,
    ): Instant? {
        if (!schedule.enabled) return null
        val due = previousFire(schedule.at, now, zone) ?: return null
        // A fire from before this schedule was armed was never its to serve: switching it on at
        // 07:30 for 07:00 must not run it on the spot through the grace window.
        if (!due.isAfter(schedule.armedAt)) return null
        if (schedule.lastFiredAt != null && !due.isAfter(schedule.lastFiredAt)) return null
        return due
    }

    /** Whether a fire that came due at [due] may still be run at [now]. */
    fun withinGrace(
        due: Instant,
        now: Instant,
        graceMinutes: Int = AnalysisSchedule.GRACE_MINUTES,
    ): Boolean = !now.isAfter(due.plusSeconds(graceMinutes * 60L))

    /** 24-hour, because 18:00 cannot be read as the morning and "6:00 PM" is three characters longer. */
    fun clock(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)
}
