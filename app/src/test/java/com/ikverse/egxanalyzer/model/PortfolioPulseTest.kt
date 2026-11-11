package com.ikverse.egxanalyzer.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioPulseTest {

    private fun day(d: Int) = LocalDate.of(2026, 11, d)

    private fun view(
        ticker: String,
        entry: Double,
        entryOn: LocalDate = day(2),
        exit: Double? = null,
        exitOn: LocalDate? = null,
        current: Double? = null,
        open: Boolean = exit == null,
        status: PositionStatus = PositionStatus.OPEN,
        settledOn: LocalDate? = null,
        stop: Double? = null,
        target1: Double? = null,
        target2: Double? = null,
        overdueDays: Long = 0,
        keepOpen: Boolean = false,
    ) = PositionView(
        position = Position(
            ticker = ticker, recommendationDate = entryOn, entryPrice = entry, entryDate = entryOn,
            exitPrice = exit, exitDate = exitOn, stopLoss = stop, target1 = target1, target2 = target2,
            keepOpen = keepOpen,
        ),
        status = status, marketStatus = status, open = open, currentPrice = current, exitPrice = exit ?: current,
        realized = exit != null, returnPct = null, sessionsElapsed = 0, sessionsRemaining = 0,
        deadlineDate = null, settledOn = settledOn, ranOutOfTime = false, overdueDays = overdueDays,
    )

    private fun s(ticker: String, d: Int, close: Double?) =
        DailySession(ticker = ticker, date = day(d), high = null, low = null, close = close, volume = null)

    @Test
    fun `return by day averages the trades held each day`() {
        val points = returnByDay(
            listOf(view("A", 100.0), view("B", 50.0, entryOn = day(3))),
            mapOf(
                "A" to listOf(s("A", 2, 100.0), s("A", 3, 110.0)),
                "B" to listOf(s("B", 3, 55.0)),
            ),
        )
        assertEquals(listOf(day(2), day(3)), points.map(ReturnPoint::date))
        assertEquals(0.0, points[0].averageReturnPct, 1e-9)
        assertEquals(1, points[0].held)
        // A is up 10%, B is up 10%: the day averages 10 across two trades.
        assertEquals(10.0, points[1].averageReturnPct, 1e-9)
        assertEquals(2, points[1].held)
    }

    @Test
    fun `a sold trade is valued at its own price on its exit day and then drops out`() {
        val sold = view("A", 100.0, exit = 120.0, exitOn = day(3))
        val points = returnByDay(listOf(sold), mapOf("A" to listOf(s("A", 2, 100.0), s("A", 3, 105.0), s("A", 4, 90.0))))
        assertEquals(listOf(day(2), day(3)), points.map(ReturnPoint::date))
        assertEquals(20.0, points.last().averageReturnPct, 1e-9)
    }

    @Test
    fun `a day with no close for a held stock uses its last close`() {
        val points = returnByDay(
            listOf(view("A", 100.0), view("B", 100.0)),
            mapOf("A" to listOf(s("A", 2, 100.0), s("A", 3, 104.0)), "B" to listOf(s("B", 2, 110.0))),
        )
        assertEquals(7.0, points.last().averageReturnPct, 1e-9)
    }

    @Test
    fun `the range keeps only the days inside it`() {
        val points = (1..30).map { ReturnPoint(day(it), it.toDouble(), 1) }
        assertEquals(7, points.within(ReturnRange.WEEK, day(30)).size)
        assertEquals(30, points.within(ReturnRange.ALL, day(30)).size)
    }

    @Test
    fun `breadth counts the newest session against each listing's close before it`() {
        val breadth = marketBreadth(
            mapOf(
                "UP" to listOf(s("UP", 8, 10.0), s("UP", 9, 11.0)),
                "DOWN" to listOf(s("DOWN", 9, 9.0), s("DOWN", 8, 10.0)),
                "FLAT" to listOf(s("FLAT", 8, 5.0), s("FLAT", 9, 5.0)),
                "STALE" to listOf(s("STALE", 1, 5.0), s("STALE", 2, 9.0)),
                "ONE" to listOf(s("ONE", 9, 5.0)),
            ),
        )!!
        assertEquals(day(9), breadth.date)
        assertEquals(1, breadth.up)
        assertEquals(1, breadth.flat)
        assertEquals(1, breadth.down)
    }

    @Test
    fun `breadth with nothing to compare is nothing`() {
        assertNull(marketBreadth(mapOf("ONE" to listOf(s("ONE", 9, 5.0)))))
    }

    @Test
    fun `weekly hit rates count settled trades by the week they settled, Sunday first`() {
        // 2026-11-09 is a Monday; its week began Sunday the 8th.
        val rates = weeklyHitRates(
            listOf(
                view("A", 1.0, exit = 2.0, exitOn = day(9), status = PositionStatus.FULL_TARGET_HIT, settledOn = day(9)),
                view("B", 1.0, exit = 0.5, exitOn = day(10), status = PositionStatus.STOPPED_OUT, settledOn = day(10)),
                view("C", 1.0, exit = 2.0, exitOn = day(3), status = PositionStatus.PARTIAL_TARGET_HIT, settledOn = day(3)),
                view("D", 1.0, current = 1.1),
            ),
            weeks = 2,
            today = day(10),
        )
        assertEquals(listOf(day(1), day(8)), rates.map(WeekRate::weekStart))
        assertEquals(1, rates[0].settled); assertEquals(1.0, rates[0].rate!!, 0.0)
        assertEquals(2, rates[1].settled); assertEquals(0.5, rates[1].rate!!, 0.0)
    }

    @Test
    fun `needs you lists overdue first, then near the stop, then near target 2, each once`() {
        val items = needsYou(
            listOf(
                view("LATE", 10.0, current = 10.0, overdueDays = 3, keepOpen = true),
                view("NEARSTOP", 68.0, current = 66.1, stop = 64.7, target2 = 78.0),
                view("NEART2", 76.0, current = 82.4, stop = 72.0, target2 = 84.0),
                view("FAR", 50.0, current = 50.0, stop = 40.0, target2 = 60.0),
            ),
            thresholdPercent = 3,
        )
        val listed = items.map { it.view.ticker to it.reason }
        assertEquals("LATE" to NeedReason.OVERDUE, listed.first())
        assertTrue(("NEARSTOP" to NeedReason.NEAR_STOP) in listed)
        assertTrue(("NEART2" to NeedReason.NEAR_TARGET2) in listed)
        assertTrue(items.none { it.view.ticker == "FAR" })
        assertEquals(items.size, items.map { it.view.position.id }.toSet().size)
    }

    @Test
    fun `the target path runs from entry to target 2 with target 1 marked on it`() {
        val path = targetPath(view("COMI", 76.0, current = 82.0, target1 = 80.0, target2 = 86.0))!!
        assertEquals(0.6f, path.progress, 1e-6f)
        assertEquals(0.4f, path.target1At!!, 1e-6f)
        assertTrue(!path.losing)
        val down = targetPath(view("ABUK", 68.0, current = 66.0, target2 = 78.0))!!
        assertEquals(0f, down.progress, 0f)
        assertTrue(down.losing)
        assertNull(targetPath(view("NONE", 10.0, current = 10.0)))
    }

    @Test
    fun `won and lost count finished trades, and a flat one is lost`() {
        val positions = listOf(
            view("WIN", 10.0, exit = 11.0).copy(returnPct = 10.0),
            view("FLAT", 10.0, exit = 10.0).copy(returnPct = 0.0),
            view("LOSS", 10.0, exit = 9.0).copy(returnPct = -10.0),
            // Expired, never sold: still finished, and counted.
            view("LATE", 10.0, current = 10.5, open = false, status = PositionStatus.EXPIRED).copy(returnPct = 5.0),
            // Still running: neither won nor lost yet.
            view("OPEN", 10.0, current = 12.0).copy(returnPct = 20.0),
            // Finished with no return to judge it on: left out.
            view("NONE", 10.0, open = false, status = PositionStatus.EXPIRED),
        )
        assertEquals(2 to 2, wonLost(positions))
    }

    @Test
    fun `near level leaves the late trades to Needs you`() {
        val near = view("NEAR", 10.0, current = 9.9, stop = 9.8)
        val late = view("LATE", 10.0, current = 10.0, overdueDays = 2)
        val levels = nearLevel(needsYou(listOf(near, late), thresholdPercent = 2))
        assertEquals(mapOf(near.position.id to NeedReason.NEAR_STOP), levels)
    }
}
