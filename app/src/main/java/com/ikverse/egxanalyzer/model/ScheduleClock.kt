package com.ikverse.egxanalyzer.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * When a scheduled job fires, and whether one is owed a run right now.
 *
 * Pure arithmetic over a zone and a clock, with no Android and no storage in it, because this is
 * the part of the feature that is impossible to check by hand: a rule about what happens at 06:00
 * next Sunday cannot be tested by waiting for next Sunday. Everything above it - alarms, workers,
 * the screen - only asks this file the two questions below.
 *
 * Times are Cairo's, always. A schedule belongs to the exchange the app follows, not to wherever
 * the phone happens to be: a user in Dubai who books a run for after the close means after the
 * close in Cairo, and a job that quietly shifted by an hour when they landed would run before the
 * session it was meant to read. The zone is a parameter only so the tests can drive it through a
 * daylight-saving boundary on purpose.
 */
object ScheduleClock {

    val ZONE: ZoneId = ZoneId.of("Africa/Cairo")

    /**
     * The week as this app reads it, Sunday first.
     *
     * [DayOfWeek] starts on Monday, which puts the EGX week - Sunday through Thursday - across a
     * wrap in every list and chip row that uses the enum's own order.
     */
    val weekOrder: List<DayOfWeek> = listOf(
        DayOfWeek.SUNDAY,
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
    )

    /** The days the exchange is open, which is what most schedules here want. */
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
     * fire past it costs a single fetch and leaves the day's row correct until the next session
     * opens, which is the whole promise of a schedule that only runs while the market is trading.
     */
    val sessionEnd: LocalTime = LocalTime.of(14, 45)

    /**
     * How rarely a repeating fire can be asked for before the system stops honouring it.
     *
     * Android holds `setExactAndAllowWhileIdle` to roughly one alarm every ten minutes while the
     * phone is dozing. A shorter gap than this is not refused, it is quietly stretched - so the
     * form says so rather than letting a schedule promise a frequency it cannot keep.
     */
    const val RELIABLE_INTERVAL_MINUTES = 15

    /**
     * The first fire strictly after [after], or null where the job has none left.
     *
     * Strictly after, so asking again the instant a job fires gives next week's slot rather than
     * the one just served.
     */
    fun nextFire(job: ScheduledJob, after: Instant, zone: ZoneId = ZONE): Instant? =
        when (val trigger = job.trigger) {
            is JobTrigger.Once -> trigger.at.atZone(zone).toInstant().takeIf { it.isAfter(after) }
            is JobTrigger.Repeat -> {
                if (trigger.days.isEmpty()) {
                    null
                } else {
                    val from = after.atZone(zone).toLocalDate()
                    // Eight candidates, not seven: today's own slot may already be past, so a job
                    // that runs on one weekday needs to reach the same weekday next week.
                    (0..7).asSequence()
                        .map { from.plusDays(it.toLong()) }
                        .filter { it.dayOfWeek in trigger.days }
                        // A local time that daylight saving skipped does not exist, and atZone
                        // moves it forward across the gap rather than throwing. That is the right
                        // answer for a schedule: the job runs once, an hour later than it reads.
                        .map { it.atTime(trigger.at).atZone(zone).toInstant() }
                        .firstOrNull { it.isAfter(after) }
                }
            }

            is JobTrigger.Interval -> {
                if (trigger.days.isEmpty()) {
                    null
                } else {
                    val times = slotTimes(trigger)
                    val from = after.atZone(zone).toLocalDate()
                    (0..7).asSequence()
                        .map { from.plusDays(it.toLong()) }
                        .filter { it.dayOfWeek in trigger.days }
                        .flatMap { date -> times.asSequence().map { date.atTime(it) } }
                        .map { it.atZone(zone).toInstant() }
                        .firstOrNull { it.isAfter(after) }
                }
            }
        }

    /** The most recent fire at or before [at], whether or not it was served. */
    fun previousFire(job: ScheduledJob, at: Instant, zone: ZoneId = ZONE): Instant? =
        when (val trigger = job.trigger) {
            is JobTrigger.Once -> trigger.at.atZone(zone).toInstant().takeIf { !it.isAfter(at) }
            is JobTrigger.Repeat -> {
                if (trigger.days.isEmpty()) {
                    null
                } else {
                    val from = at.atZone(zone).toLocalDate()
                    (0..7).asSequence()
                        .map { from.minusDays(it.toLong()) }
                        .filter { it.dayOfWeek in trigger.days }
                        .map { it.atTime(trigger.at).atZone(zone).toInstant() }
                        .firstOrNull { !it.isAfter(at) }
                }
            }

            is JobTrigger.Interval -> {
                if (trigger.days.isEmpty()) {
                    null
                } else {
                    // Days backwards and, inside each one, slots backwards: the first candidate
                    // that is not in the future is the most recent fire, which is the only one a
                    // catch-up ever serves.
                    val times = slotTimes(trigger).asReversed()
                    val from = at.atZone(zone).toLocalDate()
                    (0..7).asSequence()
                        .map { from.minusDays(it.toLong()) }
                        .filter { it.dayOfWeek in trigger.days }
                        .flatMap { date -> times.asSequence().map { date.atTime(it) } }
                        .map { it.atZone(zone).toInstant() }
                        .firstOrNull { !it.isAfter(at) }
                }
            }
        }

    /**
     * The fire this job owes a run for, or null where it owes none.
     *
     * Owed rather than due-right-now: this deliberately still answers with a fire whose grace has
     * run out, so the caller can record that it was missed instead of leaving the job looking as
     * though it had never been booked. Whether it may still be run is [withinGrace].
     *
     * A job is compared against the fire it last served rather than against the wall clock, which
     * is what stops one slot running twice on a phone whose clock jumped.
     */
    fun unservedFire(job: ScheduledJob, now: Instant, zone: ZoneId = ZONE): Instant? {
        if (!job.enabled || !job.runnable) return null
        val due = previousFire(job, now, zone) ?: return null
        // A fire from before this schedule was armed was never its to serve: saving a job at 19:00
        // for 18:00 must not run it on the spot through the grace window.
        if (!due.isAfter(job.armedAt)) return null
        if (job.lastFiredAt != null && !due.isAfter(job.lastFiredAt)) return null
        return due
    }

    /** Whether a fire that came due at [due] may still be run at [now]. */
    fun withinGrace(job: ScheduledJob, due: Instant, now: Instant): Boolean =
        !now.isAfter(due.plusSeconds(job.graceMinutes * 60L))

    /**
     * The earliest moment any of these jobs next wants the app awake.
     *
     * One alarm covers every schedule: whatever is booked, only the nearest one matters, and the
     * run that answers it re-books for whatever is next. A job per alarm would mean managing a
     * pending intent per row for no gain.
     */
    fun earliestFire(
        jobs: List<ScheduledJob>,
        after: Instant,
        zone: ZoneId = ZONE,
    ): Instant? = jobs
        .filter { it.enabled && it.runnable }
        .mapNotNull { nextFire(it, after, zone) }
        .minOrNull()

    /**
     * How a trigger reads on screen: "Trading days 18:00", "Sun-Wed 09:30", "Tue, Thu 07:00".
     *
     * Here rather than in the UI because compacting a set of days into a range is a rule with
     * corners - a set that wraps the weekend, a single day, the whole week - and a rule with
     * corners belongs where it can be tested.
     */
    fun describe(trigger: JobTrigger): String = when (trigger) {
        is JobTrigger.Once -> "Once, ${trigger.at.toLocalDate()} at ${clock(trigger.at.toLocalTime())}"
        is JobTrigger.Repeat -> "${describeDays(trigger.days)} ${clock(trigger.at)}"
        is JobTrigger.Interval ->
            "${describeDays(trigger.days)}, every ${describeEvery(trigger.everyMinutes)} " +
                "${clock(trigger.from)}-${clock(trigger.until)}"
    }

    /** "15 min", "2 h", "1 h 30 min" - the same words the dropdown that set it uses. */
    fun describeEvery(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

    /**
     * Every fire an interval makes in one day, earliest first.
     *
     * Counted in whole minutes from midnight rather than by adding to a [LocalTime], because
     * [LocalTime.plusMinutes] wraps silently at midnight: a window whose end sits near the end of
     * the day would step past it, come back round as an earlier time, and satisfy the stop
     * condition forever. An [IntProgression] cannot wrap.
     *
     * A window with its end before its start is refused by the form; here it yields the single
     * fire at [JobTrigger.Interval.from], because a stored row that somehow holds one must produce
     * a finite answer rather than none at all.
     */
    internal fun slotTimes(trigger: JobTrigger.Interval): List<LocalTime> {
        val start = trigger.from.toSecondOfDay() / 60
        val end = trigger.until.toSecondOfDay() / 60
        if (end < start) return listOf(trigger.from)
        val step = trigger.everyMinutes.coerceAtLeast(1)
        return (start..end step step).map { LocalTime.ofSecondOfDay(it * 60L) }
    }

    fun describeDays(days: Set<DayOfWeek>): String {
        if (days.isEmpty()) return "No days"
        if (days.size == 7) return "Every day"
        if (days == tradingDays) return "Trading days"
        val chosen = weekOrder.filter { it in days }
        val positions = chosen.map(weekOrder::indexOf)
        val contiguous = positions.zipWithNext().all { (a, b) -> b == a + 1 }
        return if (contiguous && chosen.size > 2) {
            "${short(chosen.first())}-${short(chosen.last())}"
        } else {
            chosen.joinToString(", ", transform = ::short)
        }
    }

    /** 24-hour, because 18:00 cannot be read as the morning and "6:00 PM" is three characters longer. */
    fun clock(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    private fun short(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
}
