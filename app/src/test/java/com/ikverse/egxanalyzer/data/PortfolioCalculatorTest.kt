package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PortfolioCalculatorTest {
    private val called = LocalDate.of(2026, 7, 20)

    @Test
    fun `return is measured from what was paid, not from what the call asked for`() {
        // The whole point of the feature: the channel said 10, the fill was 11, and a position
        // reporting the channel's return would be reporting someone else's trade.
        val view = PortfolioCalculator.evaluate(
            position = position(entryPrice = 11.0),
            sessions = flat(11.5),
            currentPrice = 12.1,
        )

        assertEquals(10.0, view.returnPct!!, 0.001)
        assertFalse(view.realized)
    }

    @Test
    fun `a later entry does not extend the deadline`() {
        // Bought three sessions late on a five-session call. The window still ends where the
        // recommendation ended, so two sessions of holding close it rather than five.
        val sessions = (0 until 5).map { day -> session(called.plusDays(day.toLong()), high = 10.5, low = 9.5) }

        val view = PortfolioCalculator.evaluate(
            position = position(
                entryDate = called.plusDays(3),
                windowSessions = 5,
                target1 = 20.0,
                target2 = 22.0,
                stopLoss = 1.0,
            ),
            sessions = sessions,
            currentPrice = 10.0,
        )

        assertEquals(PositionStatus.CLOSED, view.status)
        assertFalse(view.open)
        assertEquals(5, view.sessionsElapsed)
        assertEquals(0, view.sessionsRemaining)
        assertEquals(called.plusDays(4), view.deadlineDate)
    }

    @Test
    fun `a target reached before the entry is not the holder's gain`() {
        // The stock hit target 2 on the first session and drifted afterwards. The user bought on
        // the second session, so the call was right and this trade still is not.
        val sessions = listOf(
            session(called, high = 20.0, low = 9.0),
            session(called.plusDays(1), high = 10.2, low = 9.8),
            session(called.plusDays(2), high = 10.3, low = 9.9),
        )

        val view = PortfolioCalculator.evaluate(
            position = position(entryDate = called.plusDays(1), windowSessions = 5),
            sessions = sessions,
            currentPrice = 10.1,
        )

        assertEquals(PositionStatus.OPEN, view.status)
        assertTrue(view.open)
        // Three of the window's five sessions have traded, whoever was holding through them.
        assertEquals(3, view.sessionsElapsed)
        assertEquals(2, view.sessionsRemaining)
    }

    @Test
    fun `a stop broken by more than two percent closes the position at the stop`() {
        val sessions = listOf(
            session(called, high = 10.4, low = 10.0),
            // 9.0 is more than 2% below a stop of 9.5, which is the app's own rule for a break.
            session(called.plusDays(1), high = 10.0, low = 9.0),
        )

        val view = PortfolioCalculator.evaluate(
            position = position(entryPrice = 10.0, stopLoss = 9.5, windowSessions = 5),
            sessions = sessions,
            currentPrice = 9.1,
        )

        assertEquals(PositionStatus.STOPPED_OUT, view.status)
        assertFalse(view.open)
        // Valued at the stop rather than at the day's low: that is where the call said to get out.
        assertEquals(9.5, view.exitPrice!!, 0.001)
        assertEquals(-5.0, view.returnPct!!, 0.001)
    }

    @Test
    fun `selling by hand closes the position mid-window and realizes the return`() {
        val sold = position(entryPrice = 10.0, windowSessions = 10)
            .copy(exitPrice = 10.8, exitDate = called.plusDays(1), closedManually = true)

        val view = PortfolioCalculator.evaluate(
            position = sold,
            sessions = flat(10.2),
            currentPrice = 10.2,
        )

        assertEquals(PositionStatus.CLOSED_MANUALLY, view.status)
        assertFalse(view.open)
        assertTrue(view.realized)
        assertFalse(view.awaitingSale)
        // The user's own prices, not the market's: 10.0 to 10.8.
        assertEquals(8.0, view.returnPct!!, 0.001)
        // The call itself was still running, and saying so is the point of keeping both.
        assertEquals(PositionStatus.OPEN, view.marketStatus)
    }

    @Test
    fun `a full target hit closes the position at the target`() {
        val sessions = listOf(
            session(called, high = 10.4, low = 10.0),
            session(called.plusDays(1), high = 12.0, low = 10.5),
        )

        val view = PortfolioCalculator.evaluate(
            position = position(entryPrice = 10.0, target1 = 11.0, target2 = 12.0, windowSessions = 5),
            sessions = sessions,
            currentPrice = 11.8,
        )

        assertEquals(PositionStatus.FULL_TARGET_HIT, view.status)
        assertFalse(view.open)
        assertEquals(20.0, view.returnPct!!, 0.001)
        assertEquals(called.plusDays(1), view.settledOn)
    }

    @Test
    fun `a partial hit keeps running while the window is open`() {
        val sessions = listOf(
            session(called, high = 11.2, low = 10.0),
        )

        val view = PortfolioCalculator.evaluate(
            position = position(entryPrice = 10.0, target1 = 11.0, target2 = 13.0, windowSessions = 5),
            sessions = sessions,
            currentPrice = 11.1,
        )

        assertEquals(PositionStatus.PARTIAL_TARGET_HIT, view.status)
        assertTrue(view.open)
        // Still marked to market, because target 2 may yet arrive.
        assertEquals(11.0, view.returnPct!!, 0.001)
    }

    @Test
    fun `a position with no stored sessions is open and unpriced`() {
        val view = PortfolioCalculator.evaluate(
            position = position(),
            sessions = emptyList(),
            currentPrice = null,
        )

        assertEquals(PositionStatus.OPEN, view.status)
        assertTrue(view.open)
        assertNull(view.returnPct)
        assertEquals(0, view.sessionsElapsed)
    }

    @Test
    fun `the record splits open from closed and counts the wins among the closed`() {
        val portfolio = PortfolioCalculator.build(
            positions = listOf(
                // Closed by hand at a profit.
                position(ticker = "AMOC", entryPrice = 10.0)
                    .copy(exitPrice = 11.0, exitDate = called, closedManually = true),
                // Closed by hand at a loss.
                position(ticker = "COMI", entryPrice = 10.0)
                    .copy(exitPrice = 9.0, exitDate = called, closedManually = true),
                // Still running.
                position(ticker = "SWDY", entryPrice = 10.0),
            ),
            sessionsFor = { _, _ -> flat(10.5) },
            latestCloseFor = { 10.5 },
        )

        val stats = portfolio.stats
        assertEquals(3, stats.total)
        assertEquals(1, stats.openCount)
        assertEquals(2, stats.closedCount)
        assertEquals(50.0, stats.winRate!!, 0.001)
        // Closed positions average out to nothing; the open one is up 5%.
        assertEquals(0.0, stats.realizedReturnPct!!, 0.001)
        assertEquals(5.0, stats.openReturnPct!!, 0.001)
        assertEquals("AMOC", stats.best!!.ticker)
        assertEquals("COMI", stats.worst!!.ticker)
        // Closed trades stay in the record whether the market or the user ended them.
        assertEquals(2, portfolio.closed.single().positions.size)
    }

    @Test
    fun `positions are grouped by the session their recommendation was for`() {
        val portfolio = PortfolioCalculator.build(
            positions = listOf(
                position(ticker = "AMOC"),
                position(ticker = "COMI"),
                position(ticker = "SWDY", recommendationDate = called.plusDays(1)),
            ),
            sessionsFor = { _, _ -> emptyList() },
            latestCloseFor = { null },
        )

        assertEquals(listOf(called.plusDays(1), called), portfolio.open.map { it.recommendationDate })
        assertEquals(listOf("AMOC", "COMI"), portfolio.open.last().positions.map { it.ticker })
    }

    @Test
    fun `a held stock is found by the call it was bought on`() {
        val portfolio = PortfolioCalculator.build(
            positions = listOf(position(ticker = "AMOC")),
            sessionsFor = { _, _ -> emptyList() },
            latestCloseFor = { null },
        )

        // The suffix the sources sometimes print is not a different stock.
        assertEquals("AMOC", portfolio.heldFor("amoc.ca", called)?.ticker)
        // A different session is a different call, and must not light up this one's card.
        assertNull(portfolio.heldFor("AMOC", called.plusDays(1)))
        assertNull(portfolio.heldFor("AMOC", null))
    }

    private fun position(
        ticker: String = "AMOC",
        entryPrice: Double = 10.0,
        entryDate: LocalDate = called,
        recommendationDate: LocalDate = called,
        target1: Double? = 11.0,
        target2: Double? = 12.0,
        stopLoss: Double? = 9.0,
        windowSessions: Int = 10,
    ) = Position(
        ticker = ticker,
        recommendationDate = recommendationDate,
        entryPrice = entryPrice,
        entryDate = entryDate,
        target1 = target1,
        target2 = target2,
        stopLoss = stopLoss,
        windowSessions = windowSessions,
    )

    /** Sessions that go nowhere, for the cases where only the entry and the mark matter. */
    private fun flat(price: Double) = listOf(session(called, high = price, low = price))

    private fun session(date: LocalDate, high: Double, low: Double) = DailySession(
        ticker = "AMOC",
        date = date,
        high = high,
        low = low,
        close = (high + low) / 2,
        volume = 1_000.0,
        open = low,
    )
}
