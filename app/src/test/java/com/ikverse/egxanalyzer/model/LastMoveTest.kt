package com.ikverse.egxanalyzer.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastMoveTest {

    private fun session(day: Int, close: Double?) =
        DailySession(ticker = "COMI", date = LocalDate.of(2026, 11, day), high = null, low = null, close = close, volume = null)

    @Test
    fun `the move is from the close before the last one`() {
        val move = lastMove(listOf(session(5, 80.0), session(6, 82.0)))!!
        assertEquals(82.0, move.close, 0.0)
        assertEquals(2.5, move.changePercent, 1e-9)
    }

    @Test
    fun `sessions are read in date order whatever order they arrive in`() {
        val move = lastMove(listOf(session(6, 82.0), session(5, 80.0)))!!
        assertEquals(82.0, move.close, 0.0)
    }

    @Test
    fun `a session with no close is skipped, not read as zero`() {
        val move = lastMove(listOf(session(4, 80.0), session(5, null), session(6, 76.0)))!!
        assertEquals(-5.0, move.changePercent, 1e-9)
    }

    @Test
    fun `one priced session is a close with no move`() {
        val move = lastMove(listOf(session(6, 82.0)))!!
        assertEquals(0.0, move.changePercent, 0.0)
    }

    @Test
    fun `no priced session is nothing to show`() {
        assertNull(lastMove(listOf(session(6, null))))
        assertNull(lastMove(emptyList()))
    }
}
