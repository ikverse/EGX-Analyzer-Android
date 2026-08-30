package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

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

    @Test
    fun `the next fire is today when its hour is still ahead`() {
        assertEquals(
            at("2026-08-20", "07:00"),
            ScheduleClock.nextFire(morning, at("2026-08-20", "06:00")),
        )
    }

    @Test
    fun `a fire just served gives the next trading day, not the same slot again`() {
        assertEquals(
            at("2026-08-23", "07:00"),
            ScheduleClock.nextFire(morning, at("2026-08-20", "07:00")),
        )
    }

    @Test
    fun `the weekend is stepped over`() {
        assertEquals(
            at("2026-08-23", "07:00"),
            ScheduleClock.nextFire(morning, at("2026-08-21", "09:00")),
        )
        assertEquals(
            at("2026-08-20", "07:00"),
            ScheduleClock.previousFire(morning, at("2026-08-22", "12:00")),
        )
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
        val fire = ScheduleClock.nextFire(morning, at("2026-11-01", "06:00"))
        assertEquals(morning, fire.atZone(ScheduleClock.ZONE).toLocalTime())
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
    ) = AnalysisSchedule(
        enabled = enabled,
        at = morning,
        channels = listOf(AnalysedChannel(1, "Signals")),
        contentTypes = setOf(AnalysisContentType.entries.first()),
        lastFiredAt = lastFiredAt,
        armedAt = armedAt,
    )

    private fun at(date: String, time: String): Instant =
        LocalDateTime.parse("${date}T$time").atZone(ScheduleClock.ZONE).toInstant()
}
