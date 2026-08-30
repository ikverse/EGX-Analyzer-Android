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
     * The days the exchange is open.
     *
     * What the price refresh's slots are built from, and what a schedule keeping exactly these
     * days is called on screen. It is deliberately **not** a bound on what an analysis may be
     * booked for: a run on a Friday reads what the chats posted over the weekend and files it
     * against the Sunday session, which is a real question and not a wasted request. See
     * [AnalysisSchedule.days].
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
     * The first fire of [at] on one of [days] strictly after [after].
     *
     * Strictly after, so asking again the instant a schedule fires gives its next day rather than
     * the slot just served. Null only where [days] is empty, which is a schedule the user has
     * emptied: there is no honest time to name for it, and naming one anyway is how a card ends up
     * promising a run that will never come.
     */
    fun nextFire(
        at: LocalTime,
        days: Set<DayOfWeek>,
        after: Instant,
        zone: ZoneId = ZONE,
    ): Instant? {
        if (days.isEmpty()) return null
        val from = after.atZone(zone).toLocalDate()
        // Eight candidates, not seven: today's own slot may already be past, so a schedule that
        // keeps only Thursdays has to be able to reach the Thursday of the following week.
        return (0..7).asSequence()
            .map { from.plusDays(it.toLong()) }
            .filter { it.dayOfWeek in days }
            // A local time that daylight saving skipped does not exist, and atZone moves it
            // forward across the gap rather than throwing. That is the right answer for a
            // schedule: the run happens once, an hour later than it reads.
            .map { it.atTime(at).atZone(zone).toInstant() }
            .firstOrNull { it.isAfter(after) }
    }

    /** The most recent fire of [at] on one of [days] at or before [moment], served or not. */
    fun previousFire(
        at: LocalTime,
        days: Set<DayOfWeek>,
        moment: Instant,
        zone: ZoneId = ZONE,
    ): Instant? {
        if (days.isEmpty()) return null
        val from = moment.atZone(zone).toLocalDate()
        return (0..7).asSequence()
            .map { from.minusDays(it.toLong()) }
            .filter { it.dayOfWeek in days }
            .map { it.atTime(at).atZone(zone).toInstant() }
            .firstOrNull { !it.isAfter(moment) }
    }

    /**
     * The earliest fire any of [schedules] has left, or null where none of them has one.
     *
     * The alarm books one moment for the whole list, so this is the question it asks. A schedule
     * that is switched off has no next fire at all, which is what takes it out of the answer
     * without taking down the ones beside it.
     */
    fun nextFireOf(
        schedules: List<AnalysisSchedule>,
        now: Instant,
        zone: ZoneId = ZONE,
    ): Instant? = schedules
        .filter { it.enabled }
        .mapNotNull { nextFire(it.at, it.days, now, zone) }
        .minOrNull()

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
        val due = previousFire(schedule.at, schedule.days, now, zone) ?: return null
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
