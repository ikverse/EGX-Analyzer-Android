package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.PortfolioGroup
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
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

        assertEquals(PositionStatus.EXPIRED, view.status)
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
    fun `the record splits open from settled and counts the wins among the settled`() {
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
        assertEquals(2, stats.settledCount)
        assertEquals(50.0, stats.winRate!!, 0.001)
        // Settled positions average out to nothing; the open one is up 5%.
        assertEquals(0.0, stats.settledReturnPct!!, 0.001)
        assertEquals(5.0, stats.openReturnPct!!, 0.001)
        assertEquals("AMOC", stats.best!!.ticker)
        assertEquals("COMI", stats.worst!!.ticker)
        // All three were called on the same session, so they share one card, split into its
        // sections. Sold by hand is closed however the market was going: neither ran out of time.
        val group = portfolio.groups.single()
        assertEquals(listOf("SWDY"), group.open.map(PositionView::ticker))
        assertEquals(listOf("AMOC", "COMI"), group.closed.map(PositionView::ticker))
        assertTrue(group.expired.isEmpty())
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

        assertEquals(listOf(called.plusDays(1), called), portfolio.groups.map { it.recommendationDate })
        assertEquals(listOf("AMOC", "COMI"), portfolio.groups.last().positions.map { it.ticker })
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

    @Test
    fun `a deadline that has passed counts the days since, and only once it is known`() {
        // Five sessions, all traded, so the deadline is the fifth: 24 July. Ten days later the
        // trade is ten days late, whatever the feed has stored since.
        val sessions = (0 until 5).map { day ->
            session(called.plusDays(day.toLong()), high = 10.2, low = 9.8)
        }

        val view = PortfolioCalculator.evaluate(
            position = position(windowSessions = 5, keepOpen = true),
            sessions = sessions,
            currentPrice = 10.0,
            today = called.plusDays(14),
        )

        assertEquals(called.plusDays(4), view.deadlineDate)
        assertEquals(10L, view.overdueDays)
        assertTrue(view.overdue)
    }

    @Test
    fun `a trade inside its window is not overdue`() {
        val view = PortfolioCalculator.evaluate(
            position = position(windowSessions = 10),
            sessions = listOf(session(called, high = 10.2, low = 9.8)),
            currentPrice = 10.0,
            today = called.plusDays(3),
        )

        // Nine sessions still owed, so there is no deadline yet to be late against.
        assertNull(view.deadlineDate)
        assertEquals(0L, view.overdueDays)
        assertFalse(view.overdue)
    }

    @Test
    fun `a trade sold long ago is never overdue`() {
        // The deadline passed months back. The user did record the sale, so there is nothing here
        // to chase them about.
        val sessions = (0 until 5).map { day ->
            session(called.plusDays(day.toLong()), high = 10.2, low = 9.8)
        }
        val sold = position(windowSessions = 5)
            .copy(exitPrice = 10.5, exitDate = called.plusDays(2), closedManually = true)

        val view = PortfolioCalculator.evaluate(
            position = sold,
            sessions = sessions,
            currentPrice = 10.0,
            today = called.plusDays(90),
        )

        assertEquals(0L, view.overdueDays)
        assertFalse(view.overdue)
    }

    @Test
    fun `a trade the deadline closed is overdue while no sale is recorded`() {
        // Not kept open: the window simply ran out and the app closed it. The user never said what
        // they did, and that is exactly what the count is for.
        val sessions = (0 until 5).map { day ->
            session(called.plusDays(day.toLong()), high = 10.2, low = 9.8)
        }

        val view = PortfolioCalculator.evaluate(
            position = position(windowSessions = 5, target1 = 20.0, target2 = 22.0, stopLoss = 1.0),
            sessions = sessions,
            currentPrice = 10.0,
            today = called.plusDays(11),
        )

        assertFalse(view.open)
        assertTrue(view.awaitingSale)
        assertEquals(7L, view.overdueDays)
    }

    @Test
    fun `keep open survives a window that has run out`() {
        // The same five-session call that closes in `a later entry does not extend the deadline`,
        // held open instead.
        val sessions = (0 until 5).map { day ->
            session(called.plusDays(day.toLong()), high = 10.5, low = 9.5)
        }

        val view = PortfolioCalculator.evaluate(
            position = position(
                windowSessions = 5,
                target1 = 20.0,
                target2 = 22.0,
                stopLoss = 1.0,
                keepOpen = true,
            ),
            sessions = sessions,
            currentPrice = 10.4,
            today = called.plusDays(6),
        )

        assertTrue(view.open)
        assertTrue(view.awaitingSale)
        // Open under the user's own instruction, whatever the window says. The chip must not read
        // "Closed" above a trade sitting under Open positions.
        assertEquals(PositionStatus.OPEN, view.status)
        // The call itself still expired, and the record goes on saying so.
        assertEquals(PositionStatus.EXPIRED, view.marketStatus)
        // Marked to market rather than valued at the window's last close, because it is still held.
        assertEquals(4.0, view.returnPct!!, 0.001)
    }

    @Test
    fun `keep open does not survive target 2`() {
        // The one ending Keep Open cannot argue with. A stop is the call's opinion and a deadline is
        // the app's; target 2 is the trade doing the thing it was bought to do, and there is nothing
        // left to hold it open for.
        val sessions = listOf(
            session(called, high = 10.4, low = 10.0),
            session(called.plusDays(1), high = 12.0, low = 10.5),
        )

        val view = PortfolioCalculator.evaluate(
            position = position(
                entryPrice = 10.0,
                target1 = 11.0,
                target2 = 12.0,
                windowSessions = 5,
                keepOpen = true,
            ),
            sessions = sessions,
            currentPrice = 11.8,
            today = called.plusDays(30),
        )

        assertFalse(view.open)
        assertEquals(PositionStatus.FULL_TARGET_HIT, view.status)
        // Valued at the target rather than marked to market: this is where the trade ended.
        assertEquals(20.0, view.returnPct!!, 0.001)
        // The flag is still on the row, but nothing on the card may claim the trade is being kept
        // open while it is closed.
        assertFalse(view.keptOpen)
        assertTrue(view.finished)
    }

    @Test
    fun `a trade that ended somewhere is never overdue, however long ago`() {
        // Neither of these ran out of time: one reached its target, the other was taken by the stop.
        // Chasing the user over a trade that already has an answer is the whole thing being fixed.
        val hit = PortfolioCalculator.evaluate(
            position = position(entryPrice = 10.0, target1 = 11.0, target2 = 12.0, windowSessions = 3),
            sessions = (0 until 3).map { session(called.plusDays(it.toLong()), high = 12.5, low = 9.9) },
            currentPrice = 12.4,
            today = called.plusDays(90),
        )
        val stopped = PortfolioCalculator.evaluate(
            position = position(entryPrice = 10.0, stopLoss = 9.5, target2 = 99.0, windowSessions = 3),
            sessions = (0 until 3).map { session(called.plusDays(it.toLong()), high = 10.1, low = 9.0) },
            currentPrice = 9.1,
            today = called.plusDays(90),
        )

        assertEquals(PositionStatus.FULL_TARGET_HIT, hit.status)
        assertFalse(hit.ranOutOfTime)
        assertEquals(0L, hit.overdueDays)

        assertEquals(PositionStatus.STOPPED_OUT, stopped.status)
        assertFalse(stopped.ranOutOfTime)
        assertEquals(0L, stopped.overdueDays)
    }

    @Test
    fun `a partial hit that ran to the deadline is overdue and keeps its own verdict`() {
        // Target 1 was banked and target 2 never came. The window is what ended this, so it is
        // overdue - but the card goes on saying the trade got somewhere, which "Expired" would not.
        val sessions = listOf(
            session(called, high = 11.2, low = 10.0),
            session(called.plusDays(1), high = 11.1, low = 10.4),
            session(called.plusDays(2), high = 11.0, low = 10.3),
        )

        val view = PortfolioCalculator.evaluate(
            position = position(entryPrice = 10.0, target1 = 11.0, target2 = 13.0, windowSessions = 3),
            sessions = sessions,
            currentPrice = 10.9,
            today = called.plusDays(9),
        )

        assertEquals(PositionStatus.PARTIAL_TARGET_HIT, view.status)
        assertFalse(view.open)
        assertTrue(view.ranOutOfTime)
        assertEquals(7L, view.overdueDays)
    }

    @Test
    fun `a trade is not late on the day it expires`() {
        val sessions = (0 until 3).map {
            session(called.plusDays(it.toLong()), high = 10.2, low = 9.8)
        }

        val view = PortfolioCalculator.evaluate(
            position = position(windowSessions = 3, target1 = 20.0, target2 = 22.0, stopLoss = 1.0),
            sessions = sessions,
            currentPrice = 10.0,
            today = called.plusDays(2),
        )

        // It belongs in the expired section from the moment the window closes, but it is not late
        // until a day has gone by without the user saying what they did.
        assertTrue(view.ranOutOfTime)
        assertEquals(0L, view.overdueDays)
        assertFalse(view.overdue)
    }

    @Test
    fun `a session's card splits its trades into open, expired and closed`() {
        val sessions = (0 until 4).map {
            session(called.plusDays(it.toLong()), high = 10.2, low = 9.8)
        }

        val portfolio = PortfolioCalculator.build(
            positions = listOf(
                // Ran out of time with nothing hit, which is the whole of the expired section.
                position(ticker = "AAAA", windowSessions = 3, target1 = 20.0, target2 = 22.0, stopLoss = 1.0),
                // Same window, but the user is still holding it on purpose.
                position(ticker = "BBBB", windowSessions = 3, target1 = 20.0, target2 = 22.0, stopLoss = 1.0)
                    .copy(keepOpen = true),
                // Sold by hand, so it ended where the user says it did.
                position(ticker = "CCCC", windowSessions = 3, target1 = 20.0, target2 = 22.0, stopLoss = 1.0)
                    .copy(exitPrice = 10.1, exitDate = called.plusDays(1), closedManually = true),
                // Still inside its window.
                position(ticker = "DDDD", windowSessions = 20, target1 = 20.0, target2 = 22.0, stopLoss = 1.0),
            ),
            sessionsFor = { _, _ -> sessions },
            latestCloseFor = { 10.0 },
            today = called.plusDays(8),
        )

        val group = portfolio.groups.single()
        // One card for the session, whatever became of the trades inside it.
        assertEquals(4, group.positions.size)
        // A kept-open trade leads the open section on being overdue, ahead of one that is fine.
        assertEquals(listOf("BBBB", "DDDD"), group.open.map(PositionView::ticker))
        assertEquals(listOf("AAAA"), group.expired.map(PositionView::ticker))
        assertEquals(listOf("CCCC"), group.closed.map(PositionView::ticker))
        assertTrue(group.hasOpen)
        // Two trades are past a deadline with nothing recorded: the expired one and the kept-open
        // one. Which section they are drawn in does not change that.
        assertEquals(2, portfolio.stats.overdueCount)
    }

    @Test
    fun `keep open survives a broken stop and still marks to market`() {
        val sessions = listOf(
            session(called, high = 10.4, low = 10.0),
            // More than 2% below the stop, which is a break by the app's own rule.
            session(called.plusDays(1), high = 10.0, low = 9.0),
        )

        val view = PortfolioCalculator.evaluate(
            position = position(entryPrice = 10.0, stopLoss = 9.5, windowSessions = 5, keepOpen = true),
            sessions = sessions,
            currentPrice = 9.8,
            today = called.plusDays(2),
        )

        assertTrue(view.open)
        // The stop-out is what the market did and the card goes on showing it; what it no longer
        // does is end the trade.
        assertEquals(PositionStatus.STOPPED_OUT, view.status)
        assertEquals(PositionStatus.STOPPED_OUT, view.marketStatus)
        // Valued at the last close, not at the stop: the user did not get out there.
        assertEquals(-2.0, view.returnPct!!, 0.001)
    }

    @Test
    fun `selling a kept-open trade closes it and realizes the return`() {
        val sessions = (0 until 5).map { day ->
            session(called.plusDays(day.toLong()), high = 10.5, low = 9.5)
        }
        val sold = position(entryPrice = 10.0, windowSessions = 5, keepOpen = true)
            .copy(exitPrice = 11.0, exitDate = called.plusDays(20), closedManually = true)

        val view = PortfolioCalculator.evaluate(
            position = sold,
            sessions = sessions,
            currentPrice = 10.2,
            today = called.plusDays(21),
        )

        // Keep Open defeats every automatic close; a recorded sale is not one.
        assertFalse(view.open)
        assertTrue(view.realized)
        assertEquals(PositionStatus.CLOSED_MANUALLY, view.status)
        assertEquals(10.0, view.returnPct!!, 0.001)
    }

    @Test
    fun `lengthening the window reopens an expired trade and clears its overdue days`() {
        // Eight sessions have traded. Judged over five the call expired three sessions ago; given
        // ten it is running again, and there is no deadline left to be late against.
        val sessions = (0 until 8).map { day ->
            session(called.plusDays(day.toLong()), high = 10.2, low = 9.8)
        }
        val trade = position(windowSessions = 5, target1 = 20.0, target2 = 22.0, stopLoss = 1.0)
        val today = called.plusDays(9)

        val before = PortfolioCalculator.evaluate(trade, sessions, currentPrice = 10.0, today = today)
        val after = PortfolioCalculator.evaluate(
            position = trade.copy(windowSessions = 10, windowCustom = true),
            sessions = sessions,
            currentPrice = 10.0,
            today = today,
        )

        assertFalse(before.open)
        assertEquals(5L, before.overdueDays)

        assertTrue(after.open)
        assertNull(after.deadlineDate)
        assertEquals(0L, after.overdueDays)
        assertEquals(2, after.sessionsRemaining)
    }

    @Test
    fun `an overdue trade leads the open list, above newer calls that are fine`() {
        // Date order alone buries this: the overdue trade is overdue *because* its call is old, so
        // sorting newest-first put the one needing a decision at the very bottom of the screen.
        val old = called
        val recent = called.plusDays(30)
        val traded = { from: LocalDate, count: Int ->
            (0 until count).map { session(from.plusDays(it.toLong()), high = 10.2, low = 9.8) }
        }

        val portfolio = PortfolioCalculator.build(
            positions = listOf(
                position(ticker = "SWDY", recommendationDate = recent, windowSessions = 10),
                position(
                    ticker = "AMOC",
                    recommendationDate = old,
                    windowSessions = 3,
                    keepOpen = true,
                ),
            ),
            sessionsFor = { _, from -> traded(from, if (from == old) 5 else 1) },
            latestCloseFor = { 10.0 },
            today = recent.plusDays(5),
        )

        assertEquals(listOf(old, recent), portfolio.groups.map { it.recommendationDate })
        assertTrue(portfolio.groups.first().hasOverdue)
        assertEquals(1, portfolio.stats.overdueCount)
    }

    @Test
    fun `an overdue position leads its own group`() {
        val sessions = (0 until 6).map { session(called.plusDays(it.toLong()), high = 10.2, low = 9.8) }

        val portfolio = PortfolioCalculator.build(
            positions = listOf(
                // Alphabetically first, and not overdue: a long window still has sessions to run.
                position(ticker = "AMOC", windowSessions = 20, keepOpen = true),
                position(ticker = "ZZZZ", windowSessions = 3, keepOpen = true),
            ),
            sessionsFor = { _, _ -> sessions },
            latestCloseFor = { 10.0 },
            today = called.plusDays(10),
        )

        // Ticker order would have put AMOC first; being overdue outranks it. Both are kept open, so
        // both sit in the card's open section however long ago their deadlines went.
        assertEquals(listOf("ZZZZ", "AMOC"), portfolio.groups.single().open.map { it.ticker })
    }

    @Test
    fun `the date orders drop the urgent-first override and sort trades by entry date`() {
        val recent = called.plusDays(30)

        val portfolio = PortfolioCalculator.build(
            positions = listOf(
                // Ran out of time long ago, so urgent-first lifts its session above a newer one.
                position(
                    ticker = "AMOC",
                    recommendationDate = called,
                    windowSessions = 3,
                    target1 = 20.0,
                    target2 = 22.0,
                    stopLoss = 1.0,
                ),
                // Same session, bought two days apart, which is the only date that separates them.
                position(ticker = "SWDY", recommendationDate = recent, entryDate = recent, windowSessions = 20),
                position(
                    ticker = "ZZZZ",
                    recommendationDate = recent,
                    entryDate = recent.plusDays(2),
                    windowSessions = 20,
                ),
            ),
            sessionsFor = { _, from ->
                (0 until 5).map { session(from.plusDays(it.toLong()), high = 10.2, low = 9.8) }
            },
            latestCloseFor = { 10.0 },
            today = recent.plusDays(10),
        )

        // What the calculator hands back, and what the screen opens on.
        assertEquals(listOf(called, recent), portfolio.groups.map { it.recommendationDate })

        val newest = portfolio.groups.reordered(PortfolioOrder.NEWEST)
        // Pure date order: the overdue session goes back where its own date puts it, because a
        // control that says "newest" and then shows something else reads as broken.
        assertEquals(listOf(recent, called), newest.map { it.recommendationDate })
        assertEquals(listOf("ZZZZ", "SWDY"), newest.first().positions.map(PositionView::ticker))

        val oldest = portfolio.groups.reordered(PortfolioOrder.OLDEST)
        assertEquals(listOf(called, recent), oldest.map { it.recommendationDate })
        assertEquals(listOf("SWDY", "ZZZZ"), oldest.last().positions.map(PositionView::ticker))
    }

    /** What the screen does with an order the user picked, at both levels at once. */
    private fun List<PortfolioGroup>.reordered(order: PortfolioOrder): List<PortfolioGroup> =
        map { it.copy(positions = it.positions.sortedWith(order.positions)) }
            .sortedWith(order.groups)

    @Test
    fun `closed positions stay in date order however overdue they are`() {
        val old = called
        val recent = called.plusDays(30)

        val portfolio = PortfolioCalculator.build(
            positions = listOf(
                position(ticker = "AMOC", recommendationDate = old, windowSessions = 3)
                    .copy(exitPrice = 11.0, exitDate = old, closedManually = true),
                position(ticker = "SWDY", recommendationDate = recent, windowSessions = 3)
                    .copy(exitPrice = 11.0, exitDate = recent, closedManually = true),
            ),
            sessionsFor = { _, from ->
                (0 until 5).map { session(from.plusDays(it.toLong()), high = 10.2, low = 9.8) }
            },
            latestCloseFor = { 10.0 },
            today = recent.plusDays(30),
        )

        // The record is a record: newest first, and nothing jumps the queue. A sale recorded long
        // after the deadline is still a sale, so neither is overdue and neither reordering rule
        // fires.
        assertEquals(listOf(recent, old), portfolio.groups.map { it.recommendationDate })
        assertEquals(0, portfolio.stats.overdueCount)
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
        keepOpen: Boolean = false,
    ) = Position(
        ticker = ticker,
        recommendationDate = recommendationDate,
        entryPrice = entryPrice,
        entryDate = entryDate,
        target1 = target1,
        target2 = target2,
        stopLoss = stopLoss,
        windowSessions = windowSessions,
        keepOpen = keepOpen,
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
