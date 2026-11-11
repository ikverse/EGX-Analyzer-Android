package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A trade the user keeps open past the point the call itself ended.
 *
 * Reported off a phone on 2026-11-11: a trade stopped out two months earlier showed a return of
 * -16.3% over a "worst dip" of -4.7%, because the scorer stops reading at the stop and its low
 * stopped there with it. The figures on such a trade run to the latest session, and it carries
 * what ending with the call would have returned.
 */
class KeptOpenPastCallTest {
    private val bought = LocalDate.of(2026, 9, 6)

    private fun session(day: Long, high: Double, low: Double, close: Double = low) = DailySession(
        ticker = "ELKA", date = bought.plusDays(day), high = high, low = low, close = close, volume = 1_000.0,
    )

    // Bought at 1.90 with a stop at 1.85. The second session breaks the stop by more than 2%, and
    // the stock goes on down to 1.42 before coming back to 1.59.
    private val sessions = listOf(
        session(0, high = 1.93, low = 1.88, close = 1.90),
        session(1, high = 1.90, low = 1.80),
        session(2, high = 1.80, low = 1.60),
        session(3, high = 1.62, low = 1.42),
        session(4, high = 1.60, low = 1.50, close = 1.59),
    )

    private fun position(keepOpen: Boolean) = Position(
        ticker = "ELKA", recommendationDate = bought, entryPrice = 1.90, entryDate = bought,
        stopLoss = 1.85, target1 = 2.10, target2 = 2.20, windowSessions = 10, keepOpen = keepOpen,
    )

    private fun evaluate(keepOpen: Boolean) = PortfolioCalculator.evaluate(
        position = position(keepOpen),
        sessions = sessions,
        currentPrice = 1.59,
        today = bought.plusDays(4),
        finalThrough = bought.plusDays(4),
    )

    @Test
    fun `the worst and the best run to the latest session, not to the stop`() {
        val view = evaluate(keepOpen = true)

        assertTrue(view.open)
        assertEquals(PositionStatus.STOPPED_OUT, view.marketStatus)
        // The low two sessions after the stop, which the scorer never read.
        assertEquals(1.42, view.troughSinceEntry!!, 1e-9)
        assertEquals(bought.plusDays(3), view.troughOn)
        assertEquals(1.93, view.peakSinceEntry!!, 1e-9)
        assertEquals(bought, view.peakOn)
        // So the worst can never be shallower than where the trade stands today.
        assertTrue(view.fromEntry(view.troughSinceEntry)!! <= view.returnPct!!)
    }

    @Test
    fun `it carries what ending at the stop would have returned`() {
        val view = evaluate(keepOpen = true)

        assertEquals(-2.63, view.callEndedReturnPct!!, 0.01)
        assertEquals(-16.32, view.returnPct!!, 0.01)
    }

    @Test
    fun `every session held is counted, past the stop as well`() {
        assertEquals(5, evaluate(keepOpen = true).sessionsHeld)
    }

    @Test
    fun `a trade the stop closed is measured to the stop, and carries no second return`() {
        val view = evaluate(keepOpen = false)

        assertEquals(false, view.open)
        assertNull(view.callEndedReturnPct)
        // Read by the scorer, which stops at the session that broke the stop.
        assertEquals(1.80, view.troughSinceEntry!!, 1e-9)
    }
}
