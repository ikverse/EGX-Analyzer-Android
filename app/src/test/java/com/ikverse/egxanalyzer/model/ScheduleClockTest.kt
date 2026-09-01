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
 * When the scheduled analysis fires, and when it is owed one.
 *
 * A rule about what happens at 07:00 next Sunday cannot be checked by waiting for next Sunday,
 * which is the whole reason this arithmetic lives away from Android. The cases below are the ones
 * that cost something: the weekend the exchange is shut for, a schedule switched on after its own
 * hour has gone by, and a slot that has already been served.
 *
 * 2026-08-20 is a Thursday - the last trading day of its week.
 */
class ScheduleClockTest {

    private val morning = LocalTime.of(7, 0)
    private val everyDay = ScheduleClock.tradingDays

    @Test
    fun `the next fire is today when its hour is still ahead`() {
        assertEquals(
            at("2026-08-20", "07:00"),
            ScheduleClock.nextFire(morning, everyDay, at("2026-08-20", "06:00")),
        )
    }

    @Test
    fun `a fire just served gives the next trading day, not the same slot again`() {
        assertEquals(
            at("2026-08-23", "07:00"),
            ScheduleClock.nextFire(morning, everyDay, at("2026-08-20", "07:00")),
        )
    }

    @Test
    fun `the weekend is stepped over`() {
        assertEquals(
            at("2026-08-23", "07:00"),
            ScheduleClock.nextFire(morning, everyDay, at("2026-08-21", "09:00")),
        )
        assertEquals(
            at("2026-08-20", "07:00"),
            ScheduleClock.previousFire(morning, everyDay, at("2026-08-22", "12:00")),
        )
    }

    /**
     * The days are the whole point of being able to keep more than one schedule: a Sunday run
     * before the week opens answers a different question from a Wednesday lunchtime one, and
     * before this there was no way to ask either without paying for the other three days too.
     */
    @Test
    fun `a schedule only keeps the days it was given`() {
        val sundays = setOf(DayOfWeek.SUNDAY)
        assertEquals(
            at("2026-08-23", "07:00"),
            ScheduleClock.nextFire(morning, sundays, at("2026-08-20", "06:00")),
        )
        // A week later, which eight candidate days is exactly enough to reach.
        assertEquals(
            at("2026-08-30", "07:00"),
            ScheduleClock.nextFire(morning, sundays, at("2026-08-23", "07:00")),
        )
    }

    /**
     * The weekend is a day like any other to the clock. It looks at first as though a Friday fire
     * has no session to read, which is why it is not offered by default - but see the case below
     * for what such a run is actually aimed at.
     */
    @Test
    fun `a weekend day fires when it has been chosen`() {
        assertEquals(
            at("2026-08-21", "07:00"),
            ScheduleClock.nextFire(morning, setOf(DayOfWeek.FRIDAY), at("2026-08-20", "06:00")),
        )
        assertEquals(
            at("2026-08-22", "07:00"),
            ScheduleClock.nextFire(
                morning,
                setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
                at("2026-08-21", "07:00"),
            ),
        )
    }

    /**
     * Why a weekend schedule is worth offering at all, and the reason the run itself needs no
     * special case: a fire on a shut day is aimed at the next session that exists, and reads from
     * the last one that closed. So the Friday run is the one that picks up what was posted over
     * the weekend and has Sunday's report ready before Sunday opens.
     *
     * This is also the pairing the scheduled run's own guard checks - the session a fire was
     * booked for against the session a run now would cover - so a disagreement here would be a
     * weekend schedule that fires and then always skips itself.
     */
    @Test
    fun `a weekend fire is aimed at the session that follows it`() {
        val friday = at("2026-08-21", "07:00").atZone(ScheduleClock.ZONE)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)

        val sunday = LocalDate.parse("2026-08-23")
        assertEquals(sunday, egxTargetSession(friday))

        val window = resolveAnalysisWindow(AnalysisMode.NEXT_DAY, selectedDate = null, now = friday)
        assertEquals(sunday, window.targetDate)
        // Back to the Thursday that closed the week, so nothing posted after the last session is
        // missed by a run booked for the weekend.
        assertEquals(
            LocalDate.parse("2026-08-20"),
            window.start.atZone(ScheduleClock.ZONE).toLocalDate(),
        )
    }

    @Test
    fun `a schedule with no days has no next fire and owes nothing`() {
        assertNull(ScheduleClock.nextFire(morning, emptySet(), at("2026-08-20", "06:00")))
        assertNull(
            ScheduleClock.unservedFire(
                schedule(armedAt = at("2026-08-19", "12:00"), days = emptySet()),
                at("2026-08-20", "07:20"),
            ),
        )
    }

    /**
     * One alarm is booked for the whole list, so the only fire that matters is the earliest any of
     * them will reach. A schedule that is switched off has none, which is what takes it out of the
     * answer without taking the others with it.
     */
    @Test
    fun `the list is next at the earliest fire any of it will reach`() {
        val early = schedule(armedAt = at("2026-08-19", "12:00")).copy(id = 1, at = LocalTime.of(7, 0))
        val late = schedule(armedAt = at("2026-08-19", "12:00"))
            .copy(id = 2, at = LocalTime.of(12, 0))
        val off = schedule(armedAt = at("2026-08-19", "12:00"), enabled = false)
            .copy(id = 3, at = LocalTime.of(6, 0))
        assertEquals(
            at("2026-08-20", "07:00"),
            ScheduleClock.nextFireOf(listOf(late, early, off), at("2026-08-20", "06:30")),
        )
        assertNull(ScheduleClock.nextFireOf(listOf(off), at("2026-08-20", "06:30")))
        assertNull(ScheduleClock.nextFireOf(emptyList(), at("2026-08-20", "06:30")))
    }

    @Test
    fun `an enabled schedule owes the fire that has gone unanswered`() {
        val schedule = schedule(armedAt = at("2026-08-19", "12:00"))
        assertEquals(
            at("2026-08-20", "07:00"),
            ScheduleClock.unservedFire(schedule, at("2026-08-20", "07:20")),
        )
    }

    @Test
    fun `a schedule that is off owes nothing`() {
        val schedule = schedule(armedAt = at("2026-08-19", "12:00"), enabled = false)
        assertNull(ScheduleClock.unservedFire(schedule, at("2026-08-20", "07:20")))
    }

    /**
     * Switching a schedule on at 07:30 for 07:00 must not run it on the spot. The grace window is
     * there to forgive a sleeping phone, not to turn saving a setting into starting a paid run.
     */
    @Test
    fun `a fire from before the schedule was armed was never its to serve`() {
        val schedule = schedule(armedAt = at("2026-08-20", "07:30"))
        assertNull(ScheduleClock.unservedFire(schedule, at("2026-08-20", "07:40")))
    }

    @Test
    fun `a fire already served is not owed twice`() {
        val schedule = schedule(
            armedAt = at("2026-08-19", "12:00"),
            lastFiredAt = at("2026-08-20", "07:00"),
        )
        assertNull(ScheduleClock.unservedFire(schedule, at("2026-08-20", "09:00")))
    }

    @Test
    fun `grace forgives a late phone and then stops`() {
        val due = at("2026-08-20", "07:00")
        assertTrue(ScheduleClock.withinGrace(due, at("2026-08-20", "08:59")))
        assertFalse(ScheduleClock.withinGrace(due, at("2026-08-20", "09:01")))
    }

    /**
     * A fire keeps the clock time it reads whatever the offset underneath it is doing: a run that
     * shifted by an hour would read the chats over the wrong window.
     */
    @Test
    fun `fires keep Cairo clock time across a daylight saving change`() {
        val fire = ScheduleClock.nextFire(morning, everyDay, at("2026-11-01", "06:00"))!!
        assertEquals(morning, fire.atZone(ScheduleClock.ZONE).toLocalTime())
    }

    @Test
    fun `a session is final from the close, not from midnight`() {
        // The whole of the bug this was written for. At 14:44 the exchange has finished with
        // yesterday; a minute later the day's own figures have settled and it has finished with
        // today - which is when a window that ended today runs out, and when the phone can say so.
        assertEquals(
            LocalDate.parse("2026-08-19"),
            ScheduleClock.lastFinalSession(at("2026-08-20", "14:44")),
        )
        assertEquals(
            LocalDate.parse("2026-08-20"),
            ScheduleClock.lastFinalSession(at("2026-08-20", "14:45")),
        )
    }

    @Test
    fun `the morning belongs to the session before it`() {
        // A session is in the price table from the opening bell and overwrites itself as it trades,
        // so nothing it says about itself may be believed until it has closed.
        assertEquals(
            LocalDate.parse("2026-08-19"),
            ScheduleClock.lastFinalSession(at("2026-08-20", "10:00")),
        )
        // And the evening still belongs to the session that closed in it, right up to midnight.
        assertEquals(
            LocalDate.parse("2026-08-20"),
            ScheduleClock.lastFinalSession(at("2026-08-20", "23:59")),
        )
        assertEquals(
            LocalDate.parse("2026-08-20"),
            ScheduleClock.lastFinalSession(at("2026-08-21", "00:01")),
        )
    }

    @Test
    fun `a day the exchange never opened is still answered with`() {
        // Saturday afternoon answers with Saturday, which named no session at all. That is the
        // honest answer and it costs nothing: the comparison downstream is against the sessions the
        // price table actually holds, and the newest of those is Thursday's.
        assertEquals(
            LocalDate.parse("2026-08-22"),
            ScheduleClock.lastFinalSession(at("2026-08-22", "18:00")),
        )
    }

    @Test
    fun `it is read in Cairo, wherever the phone is`() {
        // One instant, two zones. Half past three in Cairo is half past one in London, so a phone
        // reading its own clock would still be calling the session unfinished two hours after the
        // exchange had shut. The zone is a parameter only so this can be shown; nothing in the app
        // passes anything but Cairo.
        val afterTheClose = at("2026-08-20", "15:30")
        assertEquals(
            LocalDate.parse("2026-08-20"),
            ScheduleClock.lastFinalSession(afterTheClose),
        )
        assertEquals(
            LocalDate.parse("2026-08-19"),
            ScheduleClock.lastFinalSession(afterTheClose, ZoneId.of("Europe/London")),
        )
    }

    @Test
    fun `times read as 24 hour`() {
        assertEquals("07:00", ScheduleClock.clock(LocalTime.of(7, 0)))
        assertEquals("18:05", ScheduleClock.clock(LocalTime.of(18, 5)))
    }

    private fun schedule(
        armedAt: Instant,
        enabled: Boolean = true,
        lastFiredAt: Instant? = null,
        days: Set<DayOfWeek> = everyDay,
    ) = AnalysisSchedule(
        enabled = enabled,
        at = morning,
        days = days,
        channels = listOf(AnalysedChannel(1, "Signals")),
        contentTypes = setOf(AnalysisContentType.entries.first()),
        lastFiredAt = lastFiredAt,
        armedAt = armedAt,
    )

    private fun at(date: String, time: String): Instant =
        LocalDateTime.parse("${date}T$time").atZone(ScheduleClock.ZONE).toInstant()
}
