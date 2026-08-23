package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The two windows a call carries, and the fact that they are two.
 *
 * The scorer reads [judgingWindow] and the buy dialog reads [offeredTradeWindow], and they answer
 * different questions on purpose: how long a source is followed for before its call is called a
 * dud, against how long this reader means to hold. They agree about exactly one thing, the T+1
 * card, because that is the one call whose deadline the channel printed itself.
 */
class TradeWindowTest {

    private fun point(basis: String?) = RecommendationDataPoint(
        date = LocalDate.of(2026, 8, 24),
        effectiveDateBasis = basis,
        visibleSourceDate = "2026-08-23",
        dateEvidence = null,
        timingEvidence = null,
        sourceMessageId = "1",
        sourceImageRef = null,
        recommendationEvidence = null,
        recommendationType = "buy",
        buyPrice = null,
        buyPriceLow = 9.8,
        buyPriceHigh = 10.0,
        target1 = 12.0,
        returnTp1Pct = null,
        target2 = 14.0,
        returnTp2Pct = null,
        stopLoss = 9.0,
        support = null,
        resistance = null,
        riskPct = null,
        notesArabic = null,
    )

    @Test
    fun `a T plus one call carries its own deadline, judged or traded`() {
        // Buy on the session it names, sell on the next. The horizon is about how long to follow a
        // call that did not say - this one said, so neither side overrides it.
        val expected = TradeWindow(
            sessions = Scoring.T_PLUS_ONE_WINDOW_SESSIONS,
            entrySessions = Scoring.T_PLUS_ONE_ENTRY_SESSIONS,
        )

        assertEquals(expected, point("t_plus_1").judgingWindow())
        assertEquals(expected, point("t_plus_1").offeredTradeWindow(10))
        assertEquals(expected, point("t_plus_1").offeredTradeWindow(30))
        assertEquals(expected, point("t_plus_1").offeredTradeWindow(1))
    }

    @Test
    fun `the T plus one window is two sessions and its entry is only the first`() {
        // Two rather than one because the count is inclusive of the buy session. The entry being
        // the shorter of the two is what marks a T+1 call to everything downstream, so it is not
        // an implementation detail anything is free to change.
        assertEquals(2, Scoring.T_PLUS_ONE_WINDOW_SESSIONS)
        assertEquals(1, Scoring.T_PLUS_ONE_ENTRY_SESSIONS)
        assertTrue(Scoring.T_PLUS_ONE_ENTRY_SESSIONS < Scoring.T_PLUS_ONE_WINDOW_SESSIONS)
    }

    @Test
    fun `every other call is offered the setting, entry included`() {
        assertEquals(TradeWindow(10, 10), point("explicit_date").offeredTradeWindow(10))
        assertEquals(TradeWindow(15, 15), point("watching").offeredTradeWindow(15))
        assertEquals(TradeWindow(10, 10), point(null).offeredTradeWindow(10))
    }

    @Test
    fun `a setting outside the allowed range is clamped rather than honoured`() {
        val ceiling = Scoring.MAX_WINDOW_SESSIONS
        val floor = Scoring.MIN_WINDOW_SESSIONS

        assertEquals(TradeWindow(ceiling, ceiling), point("explicit_date").offeredTradeWindow(500))
        assertEquals(TradeWindow(floor, floor), point("explicit_date").offeredTradeWindow(0))
    }

    @Test
    fun `an ordinary call is judged over the horizon, whatever the trade setting is`() {
        // The setting is the reader's own deadline and reaches nothing here. A reader who wants to
        // be out in five sessions must not turn every call a source made into a call that reached
        // nothing in five - which is what one number doing both jobs did.
        val horizon = TradeWindow(
            Scoring.JUDGING_HORIZON_SESSIONS,
            Scoring.JUDGING_HORIZON_SESSIONS,
        )

        assertEquals(horizon, point("explicit_date").judgingWindow())
        assertEquals(horizon, point("watching").judgingWindow())
        assertEquals(horizon, point(null).judgingWindow())
        assertTrue(Scoring.JUDGING_HORIZON_SESSIONS > Scoring.DEFAULT_WINDOW_SESSIONS)
    }
}
