package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The reader's habits over finished trades, against what the calls did. */
class TradeHabitsTest {
    private val day = LocalDate.of(2026, 10, 1)

    private fun sold(
        ticker: String, exit: Double, byHand: Boolean = false,
        market: PositionStatus = PositionStatus.EXPIRED, open: Boolean = false,
    ) = PositionView(
        position = Position(
            ticker = ticker, recommendationDate = day, entryPrice = 10.0, entryDate = day,
            exitPrice = exit, exitDate = day.plusDays(5), closedManually = byHand,
            stopLoss = 9.0, target1 = 11.0, target2 = 12.0,
        ),
        status = PositionStatus.CLOSED_MANUALLY, marketStatus = market, open = open,
        currentPrice = exit, exitPrice = exit, realized = !open,
        returnPct = (exit - 10.0) / 10.0 * 100, sessionsElapsed = 5, sessionsHeld = 5, sessionsRemaining = 0,
        deadlineDate = null, settledOn = null, ranOutOfTime = false, overdueDays = 0,
    )

    @Test
    fun eachHabitIsCountedWithBothReturns() {
        val trades = listOf(
            sold("AAAA", 10.5, byHand = true),
            sold("BBBB", 8.5, byHand = true),
            sold("CCCC", 11.2),
            // Sold under target 1 after the call had already reached it: not "early".
            sold("DDDD", 10.8, market = PositionStatus.PARTIAL_TARGET_HIT),
            sold("EEEE", 10.2, open = true),
        )
        val calls = mapOf("AAAA" to 8.0, "BBBB" to -10.0)
        val habits = tradeHabits(trades) { calls[it.ticker] }
        assertEquals(4, habits.finished)
        assertEquals(1, habits.soldEarly.trades)
        assertEquals(5.0, habits.soldEarly.yourReturn!!, 1e-9)
        assertEquals(8.0, habits.soldEarly.callReturn!!, 1e-9)
        assertEquals(1, habits.heldPastStop.trades)
        assertEquals(-15.0, habits.heldPastStop.yourReturn!!, 1e-9)
        assertEquals(2, habits.closedByHand.trades)
    }

    @Test
    fun nothingSoldIsNothingToSay() {
        assertTrue(tradeHabits(listOf(sold("AAAA", 10.5, open = true))) { null }.isEmpty)
    }
}
