package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * When a schedule fires, which is the one part of this feature nobody can check by hand.
 *
 * A rule about what happens at 18:00 next Sunday cannot be tested by waiting for next Sunday, and
 * a scheduler that is wrong is wrong silently - it simply does nothing on a morning it should have
 * run, which looks exactly like a phone that was asleep. Everything below drives the clock instead.
 */
class ScheduleClockTest {

    private val cairo: ZoneId = ScheduleClock.ZONE

    /** Cairo wall-clock, which is what every schedule in this app is set in. */
    private fun at(date: String, time: String): Instant =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(cairo).toInstant()

    private fun job(
        trigger: JobTrigger,
        enabled: Boolean = true,
        lastFiredAt: Instant? = null,
        graceMinutes: Int = ScheduledJob.DEFAULT_GRACE_MINUTES,
        work: JobWork = JobWork.PriceRefresh,
    ) = ScheduledJob(
        id = "job",
        name = "Evening prices",
        enabled = enabled,
        trigger = trigger,
        work = work,
        graceMinutes = graceMinutes,
        lastFiredAt = lastFiredAt,
        createdAt = Instant.EPOCH,
    )

    /** Sunday through Thursday at 18:00, which is the shape almost every job here has. */
    private val evenings = JobTrigger.Repeat(ScheduleClock.tradingDays, LocalTime.of(18, 0))

    @Test
    fun `the dates the rest of this file leans on are the days it says they are`() {
        assertEquals(DayOfWeek.THURSDAY, LocalDate.parse("2026-08-20").dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, LocalDate.parse("2026-08-23").dayOfWeek)
        assertEquals(DayOfWeek.MONDAY, LocalDate.parse("2026-08-24").dayOfWeek)
    }

    @Test
    fun `today's own slot is the next one until it has passed`() {
        assertEquals(
            at("2026-08-20", "18:00"),
            ScheduleClock.nextFire(job(evenings), at("2026-08-20", "09:00")),
        )
    }

    @Test
    fun `the fire just served is never offered again`() {
        // Strictly after, or re-booking the alarm the moment a job finishes would book the fire it
        // has this second finished serving, and the phone would spin on one slot forever.
        assertEquals(
            at("2026-08-23", "18:00"),
            ScheduleClock.nextFire(job(evenings), at("2026-08-20", "18:00")),
        )
    }

    @Test
    fun `the weekend is stepped over rather than run through`() {
        // Thursday evening, past the fire. Friday and Saturday are not trading days, so the next
        // one is Sunday - three days later, not one.
        assertEquals(
            at("2026-08-23", "18:00"),
            ScheduleClock.nextFire(job(evenings), at("2026-08-20", "19:00")),
        )
    }

    @Test
    fun `a job that runs on one weekday reaches the same day next week`() {
        val mondays = JobTrigger.Repeat(setOf(DayOfWeek.MONDAY), LocalTime.of(7, 30))
        assertEquals(
            at("2026-08-31", "07:30"),
            ScheduleClock.nextFire(job(mondays), at("2026-08-24", "07:30")),
        )
    }

    @Test
    fun `a schedule with no days chosen never fires`() {
        val nothing = JobTrigger.Repeat(emptySet(), LocalTime.of(18, 0))
        assertNull(ScheduleClock.nextFire(job(nothing), at("2026-08-20", "09:00")))
        assertNull(ScheduleClock.previousFire(job(nothing), at("2026-08-20", "09:00")))
    }

    @Test
    fun `a one-shot fires once and then has nothing left`() {
        val once = job(JobTrigger.Once(LocalDateTime.parse("2026-08-21T06:00")))
        assertEquals(
            at("2026-08-21", "06:00"),
            ScheduleClock.nextFire(once, at("2026-08-20", "23:00")),
        )
        val served = once.copy(lastFiredAt = at("2026-08-21", "06:00"))
        assertNull(ScheduleClock.nextFire(served, at("2026-08-21", "07:00")))
        assertTrue(served.spent)
    }

    @Test
    fun `a job owes a run for a fire it has not served`() {
        assertEquals(
            at("2026-08-20", "18:00"),
            ScheduleClock.unservedFire(job(evenings), at("2026-08-20", "18:05")),
        )
    }

    @Test
    fun `a job owes nothing for the fire it has already served`() {
        val served = job(evenings, lastFiredAt = at("2026-08-20", "18:00"))
        assertNull(ScheduleClock.unservedFire(served, at("2026-08-20", "18:05")))
        // The next day's fire is a different slot and is owed on its own.
        assertEquals(
            at("2026-08-23", "18:00"),
            ScheduleClock.unservedFire(served, at("2026-08-23", "18:01")),
        )
    }

    @Test
    fun `a week with the phone off comes back owing one run, not seven`() {
        val stale = job(evenings, lastFiredAt = at("2026-08-13", "18:00"))
        // Six fires went by between those two dates. Only the last is owed: catching up on a
        // schedule is not the same thing as keeping it.
        assertEquals(
            at("2026-08-23", "18:00"),
            ScheduleClock.unservedFire(stale, at("2026-08-23", "20:00")),
        )
    }

    @Test
    fun `a schedule created after today's fire waits for the next one`() {
        // Saved at 19:00 for 18:00 on trading days. Today's fire is an hour past and well inside a
        // two-hour grace, so without an armed-at the schedule would run the moment it was created -
        // and creating a schedule is not a way of asking for it to happen now.
        val justSaved = job(evenings).copy(armedAt = at("2026-08-20", "19:00"))
        assertNull(ScheduleClock.unservedFire(justSaved, at("2026-08-20", "19:01")))
        assertEquals(
            at("2026-08-23", "18:00"),
            ScheduleClock.nextFire(justSaved, at("2026-08-20", "19:01")),
        )
    }

    @Test
    fun `a schedule armed before its fire still runs on it`() {
        val armedThisMorning = job(evenings).copy(armedAt = at("2026-08-20", "08:00"))
        assertEquals(
            at("2026-08-20", "18:00"),
            ScheduleClock.unservedFire(armedThisMorning, at("2026-08-20", "18:30")),
        )
    }

    @Test
    fun `a switched-off job owes nothing`() {
        assertNull(
            ScheduleClock.unservedFire(job(evenings, enabled = false), at("2026-08-20", "18:05")),
        )
    }

    @Test
    fun `a job this build cannot run owes nothing`() {
        val foreign = job(evenings, work = JobWork.Unsupported("ANALYSIS"))
        assertNull(ScheduleClock.unservedFire(foreign, at("2026-08-20", "18:05")))
        assertFalse(foreign.runnable)
    }

    @Test
    fun `grace is what makes a late phone still keep its schedule`() {
        val late = job(evenings, graceMinutes = 120)
        val due = at("2026-08-20", "18:00")
        assertTrue(ScheduleClock.withinGrace(late, due, at("2026-08-20", "19:59")))
        assertTrue(ScheduleClock.withinGrace(late, due, at("2026-08-20", "20:00")))
        assertFalse(ScheduleClock.withinGrace(late, due, at("2026-08-20", "20:01")))
    }

    @Test
    fun `the alarm is booked for the nearest fire any job still has`() {
        val evening = job(evenings).copy(id = "evening")
        val morning = job(JobTrigger.Repeat(ScheduleClock.tradingDays, LocalTime.of(7, 0)))
            .copy(id = "morning")
        val off = job(
            JobTrigger.Repeat(ScheduleClock.tradingDays, LocalTime.of(3, 0)),
            enabled = false,
        ).copy(id = "off")
        val all = listOf(evening, morning, off)
        assertEquals(
            at("2026-08-20", "18:00"),
            ScheduleClock.earliestFire(all, at("2026-08-20", "09:00")),
        )
        // Past the evening one the nearest is Sunday morning, and the 03:00 job never counts:
        // a switched-off schedule must not be the thing that wakes the phone.
        assertEquals(
            at("2026-08-23", "07:00"),
            ScheduleClock.earliestFire(all, at("2026-08-20", "19:00")),
        )
    }

    @Test
    fun `a clock time daylight saving skipped still fires exactly once`() {
        // Driven through New York rather than Cairo on purpose: the gap there - 02:00 to 03:00 on
        // the second Sunday of March - is the most stable one in the world's time zone data, and a
        // test of this rule must not start failing because Egypt moved its own dates again.
        val newYork = ZoneId.of("America/New_York")
        val inTheGap = job(JobTrigger.Repeat(setOf(DayOfWeek.SUNDAY), LocalTime.of(2, 30)))
        val fire = ScheduleClock.nextFire(
            inTheGap,
            LocalDate.parse("2026-03-07").atStartOfDay(newYork).toInstant(),
            newYork,
        )
        // 02:30 did not exist that morning, so the job runs at 03:30 rather than being dropped for
        // the week or throwing on a date nobody would have thought to try.
        assertEquals(
            LocalDateTime.parse("2026-03-08T03:30"),
            fire!!.atZone(newYork).toLocalDateTime(),
        )
    }

    @Test
    fun `the days read the way the exchange's week does`() {
        assertEquals("Trading days", ScheduleClock.describeDays(ScheduleClock.tradingDays))
        assertEquals("Every day", ScheduleClock.describeDays(DayOfWeek.entries.toSet()))
        assertEquals(
            "Sun-Tue",
            ScheduleClock.describeDays(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY)),
        )
        // Not contiguous in a week that starts on Sunday, so each one is named.
        assertEquals(
            "Sun, Wed",
            ScheduleClock.describeDays(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)),
        )
        assertEquals("No days", ScheduleClock.describeDays(emptySet()))
    }

    @Test
    fun `a trigger says when it runs in one line`() {
        assertEquals("Trading days 18:00", ScheduleClock.describe(evenings))
        assertEquals(
            "Once, 2026-08-21 at 06:00",
            ScheduleClock.describe(JobTrigger.Once(LocalDateTime.parse("2026-08-21T06:00"))),
        )
    }
}
