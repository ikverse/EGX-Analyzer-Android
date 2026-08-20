package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The deadline a call carries, which the scorer and the buy dialog both read from here.
 *
 * One definition rather than one each: a trade running to a date the rate judging it had never
 * heard of is the failure this exists to prevent, and it would not show up as a crash - only as two
 * screens quietly disagreeing about the same call.
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
    fun `a T plus one call carries its own deadline whatever the setting says`() {
        // Buy on the session it names, sell on the next. The scoring setting is about how long to
        // give a call that did not say - this one said.
        val expected = TradeWindow(
            sessions = Scoring.T_PLUS_ONE_WINDOW_SESSIONS,
            entrySessions = Scoring.T_PLUS_ONE_ENTRY_SESSIONS,
        )

        assertEquals(expected, point("t_plus_1").tradeWindow(10))
        assertEquals(expected, point("t_plus_1").tradeWindow(30))
        assertEquals(expected, point("t_plus_1").tradeWindow(1))
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
    fun `every other call takes the scoring setting, entry included`() {
        assertEquals(TradeWindow(10, 10), point("explicit_date").tradeWindow(10))
        assertEquals(TradeWindow(15, 15), point("watching").tradeWindow(15))
        assertEquals(TradeWindow(10, 10), point(null).tradeWindow(10))
    }

    @Test
    fun `a setting outside the allowed range is clamped rather than honoured`() {
        val ceiling = Scoring.MAX_WINDOW_SESSIONS
        val floor = Scoring.MIN_WINDOW_SESSIONS

        assertEquals(TradeWindow(ceiling, ceiling), point("explicit_date").tradeWindow(500))
        assertEquals(TradeWindow(floor, floor), point("explicit_date").tradeWindow(0))
    }
}
