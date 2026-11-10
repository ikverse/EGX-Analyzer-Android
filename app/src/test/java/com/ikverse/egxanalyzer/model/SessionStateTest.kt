package com.ikverse.egxanalyzer.model

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the header's live dot says, at the moments that decide it. Cairo time throughout. */
class SessionStateTest {

    private val cairo: ZoneId = ScheduleClock.ZONE

    private fun at(text: String) = LocalDateTime.parse(text).atZone(cairo).toInstant()

    // 2026-11-09 is a Monday.

    @Test
    fun `a trading morning after the bell is open, closing at 14 30 the same day`() {
        val state = ScheduleClock.sessionState(at("2026-11-09T11:15:00"))
        assertTrue(state.open)
        assertEquals(at("2026-11-09T14:30:00"), state.nextChange)
    }

    @Test
    fun `the bell itself opens the session`() {
        assertTrue(ScheduleClock.sessionState(at("2026-11-09T10:00:00")).open)
    }

    @Test
    fun `the close itself ends it, and the next open is the next trading morning`() {
        val state = ScheduleClock.sessionState(at("2026-11-09T14:30:00"))
        assertFalse(state.open)
        assertEquals(at("2026-11-10T10:00:00"), state.nextChange)
    }

    @Test
    fun `before the bell it is closed and opens at ten today`() {
        val state = ScheduleClock.sessionState(at("2026-11-09T08:00:00"))
        assertFalse(state.open)
        assertEquals(at("2026-11-09T10:00:00"), state.nextChange)
    }

    /** Friday and Saturday are the weekend; Thursday's close opens again on Sunday. */
    @Test
    fun `after Thursday's close the next open is Sunday`() {
        val state = ScheduleClock.sessionState(at("2026-11-12T15:00:00"))
        assertFalse(state.open)
        assertEquals(at("2026-11-15T10:00:00"), state.nextChange)
    }

    @Test
    fun `a Friday at midday is closed`() {
        val state = ScheduleClock.sessionState(at("2026-11-13T12:00:00"))
        assertFalse(state.open)
        assertEquals(at("2026-11-15T10:00:00"), state.nextChange)
    }
}
