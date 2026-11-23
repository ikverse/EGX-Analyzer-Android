package com.ikverse.egxanalyzer.model

import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Calls for which day" picker, on Monday 23 November 2026 either side of the close. The
 * reported fault: after 14:30 the next trading day is the 24th, and a calendar stopped at today
 * could never offer it.
 */
class TargetDatePickTest {
    private fun cairo(hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 11, 23, hour, minute, 0, 0, ScheduleClock.ZONE)

    private val monday = LocalDate.of(2026, 11, 23)
    private val tuesday = LocalDate.of(2026, 11, 24)

    @Test
    fun theTargetMovesOnOnceTheCloseHasPassed() {
        assertEquals(monday, egxTargetSession(cairo(10, 0)))
        assertEquals(tuesday, egxTargetSession(cairo(14, 31)))
    }

    @Test
    fun beforeTheCloseTheCalendarStopsAtToday() {
        val now = cairo(10, 0)
        assertTrue(targetDatePickable(monday, now))
        assertFalse(targetDatePickable(tuesday, now))
        assertEquals(monday, latestPickableTarget(now))
    }

    @Test
    fun afterTheCloseTheNextTradingDayCanBePicked() {
        val now = cairo(15, 0)
        assertTrue(targetDatePickable(monday, now))
        assertTrue(targetDatePickable(tuesday, now))
        assertFalse(targetDatePickable(tuesday.plusDays(1), now))
        assertEquals(tuesday, latestPickableTarget(now))
    }

    @Test
    fun onAWeekendTheSundayAheadCanBePickedButNotTheDaysBetween() {
        val friday = ZonedDateTime.of(2026, 11, 27, 12, 0, 0, 0, ScheduleClock.ZONE)
        val sunday = LocalDate.of(2026, 11, 29)
        assertTrue(targetDatePickable(sunday, friday))
        assertFalse(targetDatePickable(LocalDate.of(2026, 11, 28), friday))
    }
}
