package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Following every call in turn from 100, and the EGX30 started at the same spot. */
class FollowCurveTest {
    private val start = LocalDate.of(2026, 9, 1)

    private fun call(opened: Long, settled: Long, outcome: Outcome, returnPct: Double?, repeat: Boolean = false) = ScoredCall(
        ticker = "COMI", companyEnglish = null, companyArabic = null, channel = "A", channelId = null,
        openedOn = start.plusDays(opened), entryLow = 10.0, entryHigh = 10.2, target1 = 11.0, target2 = 12.0,
        stopLoss = 9.0, outcome = outcome, settledOn = start.plusDays(settled), peakHigh = null, troughLow = null,
        returnPct = returnPct, sessionsElapsed = 5, repeatOf = if (repeat) start else null,
    )

    @Test
    fun eachSettledCallMovesTheLineInTurn() {
        val curve = followCurve(
            listOf(
                call(0, 10, Outcome.PARTIAL_HIT, 10.0),
                call(2, 20, Outcome.STOPPED, -5.0),
                call(3, 20, Outcome.FULL_HIT, 20.0),
                call(4, 25, Outcome.OPEN, null),
                call(5, 26, Outcome.STOPPED, -50.0, repeat = true),
            ),
        )
        assertEquals(listOf(start, start.plusDays(10), start.plusDays(20)), curve.map { it.date })
        assertEquals(100.0, curve[0].value, 1e-9)
        assertEquals(110.0, curve[1].value, 1e-9)
        // Two calls settled the same day share one point: 110 x 0.95 x 1.2.
        assertEquals(125.4, curve[2].value, 1e-9)
    }

    @Test
    fun nothingSettledIsNoLine() {
        assertTrue(followCurve(listOf(call(0, 5, Outcome.OPEN, null))).isEmpty())
    }

    @Test
    fun theMarketStartsWhereItsCloses() {
        val curve = listOf(FollowPoint(start, 100.0), FollowPoint(start.plusDays(10), 110.0), FollowPoint(start.plusDays(20), 120.0))
        // The index begins after the curve does, so it is started at the curve's value on its first day.
        val index = listOf(DatedClose(start.plusDays(9), 1000.0), DatedClose(start.plusDays(20), 1100.0))
        val market = marketAlongCurve(curve, index)
        assertEquals(listOf(start.plusDays(10), start.plusDays(20)), market.map { it.date })
        assertEquals(110.0, market[0].value, 1e-9)
        assertEquals(121.0, market[1].value, 1e-9)
    }
}
