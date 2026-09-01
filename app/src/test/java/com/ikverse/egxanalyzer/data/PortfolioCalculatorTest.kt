package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.PortfolioGroup
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.Quote
import com.ikverse.egxanalyzer.model.Scoring
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
            latestQuoteFor = { quote(10.5) },
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
            latestQuoteFor = { null },
        )

        assertEquals(listOf(called.plusDays(1), called), portfolio.groups.map { it.recommendationDate })
        assertEquals(listOf("AMOC", "COMI"), portfolio.groups.last().positions.map { it.ticker })
    }

    @Test
    fun `a held stock is found by the call it was bought on`() {
        val portfolio = PortfolioCalculator.build(
            positions = listOf(position(ticker = "AMOC")),
            sessionsFor = { _, _ -> emptyList() },
            latestQuoteFor = { null },
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
            finalThrough = called.plusDays(13),
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
            finalThrough = called.plusDays(2),
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
            finalThrough = called.plusDays(89),
        )

        assertEquals(0L, view.overdueDays)
        assertFalse(view.overdue)
    }

    @Test
    fun `a trade the deadline closed is expired, and never chased for it`() {
        // Not kept open: the window simply ran out and the app closed it, which is an answer in
        // itself. The trade sits in the Expired section saying so, and the overdue clock stays at
        // zero - there is no decision left for the user to make about it.
        val sessions = (0 until 5).map { day ->
            session(called.plusDays(day.toLong()), high = 10.2, low = 9.8)
        }

        val view = PortfolioCalculator.evaluate(
            position = position(windowSessions = 5, target1 = 20.0, target2 = 22.0, stopLoss = 1.0),
            sessions = sessions,
            currentPrice = 10.0,
            today = called.plusDays(11),
            finalThrough = called.plusDays(10),
        )

        assertFalse(view.open)
        assertTrue(view.awaitingSale)
        // Still expired, which is what puts it in that section rather than among the closed.
        assertTrue(view.ranOutOfTime)
        assertEquals(0L, view.overdueDays)
        assertFalse(view.overdue)
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
            finalThrough = called.plusDays(5),
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
            finalThrough = called.plusDays(29),
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
            finalThrough = called.plusDays(89),
        )
        val stopped = PortfolioCalculator.evaluate(
            position = position(entryPrice = 10.0, stopLoss = 9.5, target2 = 99.0, windowSessions = 3),
            sessions = (0 until 3).map { session(called.plusDays(it.toLong()), high = 10.1, low = 9.0) },
            currentPrice = 9.1,
            today = called.plusDays(90),
            finalThrough = called.plusDays(89),
        )

        assertEquals(PositionStatus.FULL_TARGET_HIT, hit.status)
        assertFalse(hit.ranOutOfTime)
        assertEquals(0L, hit.overdueDays)

        assertEquals(PositionStatus.STOPPED_OUT, stopped.status)
        assertFalse(stopped.ranOutOfTime)
        assertEquals(0L, stopped.overdueDays)
    }

    @Test
    fun `a partial hit that ran to the deadline keeps its own verdict, and is not chased`() {
        // Target 1 was banked and target 2 never came. The window is what ended this, so the card
        // goes on saying the trade got somewhere, which "Expired" would not - and with Keep Open
        // unset, the window closing it is the end of the matter rather than the start of a count.
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
            finalThrough = called.plusDays(8),
        )

        assertEquals(PositionStatus.PARTIAL_TARGET_HIT, view.status)
        assertFalse(view.open)
        assertTrue(view.ranOutOfTime)
        assertEquals(0L, view.overdueDays)
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
            // The morning after the third session, which is when this window is first past. On the
            // third session itself it was still trading, and a window is not spent by a session
            // that has merely opened.
            today = called.plusDays(3),
            finalThrough = called.plusDays(2),
        )

        // It belongs in the expired section from the moment the window closes. What makes a trade
        // late is the clock that runs on one the user is holding on purpose, and this is not one.
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
            latestQuoteFor = { quote(10.0) },
            today = called.plusDays(8),
            finalThrough = called.plusDays(7),
        )

        val group = portfolio.groups.single()
        // One card for the session, whatever became of the trades inside it.
        assertEquals(4, group.positions.size)
        // A kept-open trade leads the open section on being overdue, ahead of one that is fine.
        assertEquals(listOf("BBBB", "DDDD"), group.open.map(PositionView::ticker))
        assertEquals(listOf("AAAA"), group.expired.map(PositionView::ticker))
        assertEquals(listOf("CCCC"), group.closed.map(PositionView::ticker))
        // One trade is chased, not two: BBBB is past its deadline because the user is holding it
        // there, while AAAA's window closed it, and a closed trade is not asked to account for
        // itself. Which section they are drawn in still does not come into it.
        assertEquals(1, portfolio.stats.overdueCount)
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
            finalThrough = called.plusDays(1),
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
            finalThrough = called.plusDays(20),
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

        val before = PortfolioCalculator.evaluate(
            trade,
            sessions,
            currentPrice = 10.0,
            today = today,
            finalThrough = today.minusDays(1),
        )
        val after = PortfolioCalculator.evaluate(
            position = trade.copy(windowSessions = 10, windowCustom = true),
            sessions = sessions,
            currentPrice = 10.0,
            today = today,
            finalThrough = today.minusDays(1),
        )
        // The clock only runs on a trade the user is holding on purpose, so the days being cleared
        // are read off a kept-open copy of the same call, judged over the same two windows.
        val held = PortfolioCalculator.evaluate(
            position = trade.copy(keepOpen = true),
            sessions = sessions,
            currentPrice = 10.0,
            today = today,
            finalThrough = today.minusDays(1),
        )
        val heldLonger = PortfolioCalculator.evaluate(
            position = trade.copy(windowSessions = 10, windowCustom = true, keepOpen = true),
            sessions = sessions,
            currentPrice = 10.0,
            today = today,
            finalThrough = today.minusDays(1),
        )

        assertFalse(before.open)
        // Closed by its own window, so nothing is counted against it either way.
        assertEquals(0L, before.overdueDays)
        assertEquals(5L, held.overdueDays)
        assertEquals(0L, heldLonger.overdueDays)

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
            latestQuoteFor = { quote(10.0) },
            today = recent.plusDays(5),
            finalThrough = recent.plusDays(4),
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
            latestQuoteFor = { quote(10.0) },
            today = called.plusDays(10),
            finalThrough = called.plusDays(9),
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
                // Ran out of time long ago and kept open through it, so it is overdue and
                // urgent-first lifts its session above a newer one.
                position(
                    ticker = "AMOC",
                    recommendationDate = called,
                    windowSessions = 3,
                    target1 = 20.0,
                    target2 = 22.0,
                    stopLoss = 1.0,
                    keepOpen = true,
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
            latestQuoteFor = { quote(10.0) },
            today = recent.plusDays(10),
            finalThrough = recent.plusDays(9),
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
            latestQuoteFor = { quote(10.0) },
            today = recent.plusDays(30),
            finalThrough = recent.plusDays(29),
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

    @Test
    fun `a split under a trade neither closes it nor values it`() {
        // Bought at 10 and the stock splits two-for-one on the third session. The prices from there
        // on are half what they were for reasons that have nothing to do with this trade, and the
        // stop sits above all of them - so without this the trade is closed as a stop-out and
        // reported at roughly -50%, a loss the user never took.
        val sessions = listOf(
            session(called, high = 10.4, low = 9.8),
            session(called.plusDays(1), high = 10.6, low = 10.1),
            session(called.plusDays(2), high = 5.4, low = 5.2),
            session(called.plusDays(3), high = 5.5, low = 5.3),
        )

        val stopped = PortfolioCalculator.evaluate(
            position = position(windowSessions = 4),
            sessions = sessions,
            currentPrice = 5.4,
        )
        assertEquals(PositionStatus.STOPPED_OUT, stopped.status)

        val view = PortfolioCalculator.evaluate(
            position = position(windowSessions = 4),
            sessions = sessions,
            currentPrice = 5.4,
            priceBreaks = setOf(called.plusDays(2)),
        )

        assertTrue(view.priceScaleChanged)
        assertTrue(view.open)
        assertEquals(PositionStatus.OPEN, view.status)
        // No return at all rather than a wrong one: the entry is in the old money and every price
        // since is in the new, so any percentage would be of two different things.
        assertNull(view.returnPct)
        assertNull(view.exitPrice)
    }

    @Test
    fun `a trade the app cannot read is not chased for being overdue`() {
        // The deadline has passed and nothing closed the trade, which is ordinarily exactly what
        // the overdue reminder exists for. Asking the user daily to account for a trade the app
        // has just said it cannot read would be handing them the feed's problem.
        val sessions = (0 until 4).map { day ->
            if (day < 2) {
                session(called.plusDays(day.toLong()), high = 10.4, low = 9.9)
            } else {
                session(called.plusDays(day.toLong()), high = 5.2, low = 5.0)
            }
        }

        val view = PortfolioCalculator.evaluate(
            position = position(windowSessions = 4, stopLoss = 1.0, target1 = 50.0, target2 = 60.0),
            sessions = sessions,
            currentPrice = 5.1,
            today = called.plusDays(30),
            finalThrough = called.plusDays(29),
            priceBreaks = setOf(called.plusDays(2)),
        )

        assertTrue(view.priceScaleChanged)
        assertFalse(view.ranOutOfTime)
        assertFalse(view.overdue)
        assertEquals(0L, view.overdueDays)
    }

    /** Sessions that go nowhere, for the cases where only the entry and the mark matter. */
    @Test
    fun `a T plus one trade is closed by the session after the one it was called for`() {
        // Bought on the session the card named and sold on the next: the trade the card described,
        // and the whole of what the deadline is allowed to cover. The third session is priced here
        // precisely so that a window still running into it would show.
        val sessions = (0 until 3).map { day ->
            session(called.plusDays(day.toLong()), high = 10.5, low = 9.5)
        }

        val view = PortfolioCalculator.evaluate(
            position = position(
                windowSessions = Scoring.T_PLUS_ONE_WINDOW_SESSIONS,
                target1 = 20.0,
                target2 = 22.0,
                stopLoss = 1.0,
            ),
            sessions = sessions,
            currentPrice = 10.0,
        )

        assertEquals(PositionStatus.EXPIRED, view.status)
        assertFalse(view.open)
        assertEquals(2, view.sessionsElapsed)
        assertEquals(0, view.sessionsRemaining)
        assertEquals(called.plusDays(1), view.deadlineDate)
    }

    /** A close and the session it was set on. Only the price matters to anything scored here. */
    private fun quote(price: Double, on: LocalDate = called) = Quote(price, on)

    @Test
    fun `the extremes are read from the sessions held, not from the whole window`() {
        // The stock spiked on the session the call was made for and slumped on the one after, both
        // before the user bought on the third. What they have actually lived through is the last
        // two sessions, so those are the only two the peak and the trough may come from - the same
        // rule that already keeps a target reached before the entry out of the verdict.
        val sessions = listOf(
            session(called, high = 14.0, low = 9.8),
            session(called.plusDays(1), high = 10.0, low = 7.0),
            session(called.plusDays(2), high = 10.6, low = 9.9),
            session(called.plusDays(3), high = 11.4, low = 10.1),
        )

        val view = PortfolioCalculator.evaluate(
            position = position(
                entryPrice = 10.0,
                entryDate = called.plusDays(2),
                windowSessions = 10,
                target1 = 20.0,
                target2 = 22.0,
                stopLoss = 1.0,
            ),
            sessions = sessions,
            currentPrice = 11.0,
        )

        assertEquals(11.4, view.peakSinceEntry!!, 0.001)
        assertEquals(called.plusDays(3), view.peakOn)
        assertEquals(9.9, view.troughSinceEntry!!, 0.001)
        assertEquals(called.plusDays(2), view.troughOn)
        // Four sessions of the call have traded and the user was in the trade for two of them.
        assertEquals(4, view.sessionsElapsed)
        assertEquals(2, view.sessionsHeld)
    }

    @Test
    fun `a split leaves the extremes unsaid rather than measured across it`() {
        // The prices changed scale under the trade, so the entry was paid in the old money and
        // every high since is quoted in the new. The return is already withheld for this; a peak
        // is the same percentage of two different things and goes the same way.
        val sessions = (0 until 4).map { day ->
            session(called.plusDays(day.toLong()), high = 10.5, low = 9.5)
        }

        val view = PortfolioCalculator.evaluate(
            position = position(windowSessions = 10, target1 = 20.0, target2 = 22.0, stopLoss = 1.0),
            sessions = sessions,
            currentPrice = 10.0,
            priceBreaks = setOf(called.plusDays(2)),
        )

        assertTrue(view.priceScaleChanged)
        assertNull(view.peakSinceEntry)
        assertNull(view.peakOn)
        assertNull(view.troughSinceEntry)
        assertNull(view.troughOn)
    }

    @Test
    fun `the quote carries the session its price closed on`() {
        // The feed settles once a day and a phone can be a week behind it. The date travels with
        // the price so a card cannot print a stale close as though it were today's.
        val portfolio = PortfolioCalculator.build(
            positions = listOf(position(entryPrice = 10.0)),
            sessionsFor = { _, _ -> flat(10.5) },
            latestQuoteFor = { quote(10.5, called.plusDays(9)) },
            today = called.plusDays(12),
            finalThrough = called.plusDays(11),
        )

        val view = portfolio.positions.single()
        assertEquals(10.5, view.currentPrice!!, 0.001)
        assertEquals(called.plusDays(9), view.currentPriceOn)
    }

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

    @Test
    fun `a T plus one trade is not expired while its sell session is still trading`() {
        // Two sessions, the second of them today's. It is in the price table from the opening bell,
        // and counting it spent dropped the trade into the Expired section - and started the
        // overdue clock - at 10:00 on the one session the user was still meant to be selling in.
        val prices = listOf(
            session(called, high = 10.5, low = 9.5),
            session(called.plusDays(1), high = 10.6, low = 10.1),
        )
        val trade = position(
            windowSessions = Scoring.T_PLUS_ONE_WINDOW_SESSIONS,
            target1 = 20.0,
            target2 = 22.0,
            stopLoss = 1.0,
        )

        val stillTrading = PortfolioCalculator.evaluate(
            position = trade,
            sessions = prices,
            currentPrice = 10.3,
            today = called.plusDays(1),
            finalThrough = called,
        )
        val closed = PortfolioCalculator.evaluate(
            position = trade,
            sessions = prices,
            currentPrice = 10.3,
            today = called.plusDays(2),
            finalThrough = called.plusDays(1),
        )

        assertEquals(PositionStatus.OPEN, stillTrading.status)
        assertTrue(stillTrading.open)
        assertFalse(stillTrading.ranOutOfTime)
        assertNull(stillTrading.deadlineDate)
        // The sell session is owed, not spent: the card reads "1 of 2 left" rather than none.
        assertEquals(1, stillTrading.sessionsRemaining)

        // And the verdict itself is unchanged once that session has closed.
        assertEquals(PositionStatus.EXPIRED, closed.status)
        assertFalse(closed.open)
        assertTrue(closed.ranOutOfTime)
        assertEquals(called.plusDays(1), closed.deadlineDate)
        assertEquals(0, closed.sessionsRemaining)
    }

    @Test
    fun `the ARCC trade this rule was written for`() {
        // The trade taken on that call, off the phone: bought at 77.00 on the 26th - the session
        // the T+1 was to be sold in - against a call dated the 25th. The entry lands on the last
        // session of the window, so the scorer is handed one session and the old count called that
        // window spent the moment it opened: the trade was retired as out of time on the same
        // morning it was bought, which is what Keep Open was reached for.
        val prices = listOf(
            DailySession(
                ticker = "ARCC", date = LocalDate.of(2026, 8, 25),
                high = 78.49, low = 75.98, close = 77.00, volume = 899_325.0, open = 75.59,
            ),
            DailySession(
                ticker = "ARCC", date = LocalDate.of(2026, 8, 26),
                high = 77.30, low = 75.30, close = 75.63, volume = 271_863.0, open = 77.00,
            ),
        )
        val trade = Position(
            ticker = "ARCC",
            recommendationDate = LocalDate.of(2026, 8, 25),
            entryPrice = 77.00,
            entryDate = LocalDate.of(2026, 8, 26),
            entryLow = 76.50,
            entryHigh = 77.00,
            target1 = 79.50,
            target2 = 81.00,
            stopLoss = 75.00,
            windowSessions = Scoring.T_PLUS_ONE_WINDOW_SESSIONS,
        )

        // The morning of the sell session: the same calendar day, and the exchange has finished
        // with nothing later than the 25th.
        val onTheSellSession = PortfolioCalculator.evaluate(
            position = trade,
            sessions = prices,
            currentPrice = 75.63,
            today = LocalDate.of(2026, 8, 26),
            finalThrough = LocalDate.of(2026, 8, 25),
        )
        // The same afternoon, from 14:45, with that session closed and its figures settled.
        val atTheClose = PortfolioCalculator.evaluate(
            position = trade,
            sessions = prices,
            currentPrice = 75.63,
            today = LocalDate.of(2026, 8, 26),
            finalThrough = LocalDate.of(2026, 8, 26),
        )

        assertEquals(PositionStatus.OPEN, onTheSellSession.status)
        assertTrue(onTheSellSession.open)
        assertFalse(onTheSellSession.ranOutOfTime)
        assertNull(onTheSellSession.deadlineDate)
        assertEquals(1, onTheSellSession.sessionsRemaining)

        // And it expires on the afternoon of the session it was to be sold in, not at midnight and
        // not the next morning: the market has finished with it, so the app says so.
        assertEquals(PositionStatus.EXPIRED, atTheClose.status)
        assertTrue(atTheClose.ranOutOfTime)
        assertEquals(LocalDate.of(2026, 8, 26), atTheClose.deadlineDate)
        // Nothing is late yet, though. The deadline landed today, and the overdue clock counts
        // whole days past it.
        assertEquals(0L, atTheClose.overdueDays)
    }
}
