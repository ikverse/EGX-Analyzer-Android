package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SourceDateGateTest {

    /** Thursday 30 July 2026; the session before it is Wednesday the 29th. */
    private val target = LocalDate.of(2026, 7, 30)

    @Test
    fun `a card printed with the target session is kept`() {
        assertTrue(SourceDateGate.accepts("30 JULY 2026", target))
        assertTrue(SourceDateGate.accepts("30/7/2026", target))
    }

    @Test
    fun `a card printed the session before is kept`() {
        // This is where a genuine call for the session is published - the T+1 cards.
        assertTrue(SourceDateGate.accepts("29/07/2026", target))
    }

    @Test
    fun `an older card is rejected however recently it was posted`() {
        // The failure this exists for: a 13 July screenshot re-posted on 30 July.
        assertFalse(SourceDateGate.accepts("13/7/2026", target))
        assertFalse(SourceDateGate.accepts("28 July 2026", target))
    }

    @Test
    fun `the weekend does not count as a session`() {
        // Sunday 2 August: the session before it is Thursday the 30th, not Saturday the 1st.
        val sunday = LocalDate.of(2026, 8, 2)
        assertEquals(LocalDate.of(2026, 7, 30), SourceDateGate.previousTradingSession(sunday))
        assertTrue(SourceDateGate.accepts("30/7/2026", sunday))
        assertFalse(SourceDateGate.accepts("29/7/2026", sunday))
    }

    @Test
    fun `a card for a later session is rejected`() {
        // These channels publish tomorrow's card in the afternoon, so it sits inside today's
        // window while belonging to tomorrow's analysis.
        assertFalse(SourceDateGate.accepts("30 JULY 2026", LocalDate.of(2026, 7, 29)))
        assertFalse(SourceDateGate.accepts("31/7/2026", target))
    }

    @Test
    fun `a card printed over the weekend counts for the session that follows it`() {
        // Sunday 2 August opens after a Friday and Saturday the exchange does not trade.
        val sunday = LocalDate.of(2026, 8, 2)
        assertTrue(SourceDateGate.accepts("31/7/2026", sunday))
        assertTrue(SourceDateGate.accepts("1/8/2026", sunday))
    }

    @Test
    fun `an unreadable date is not treated as stale`() {
        assertTrue(SourceDateGate.accepts(null, target))
        assertTrue(SourceDateGate.accepts("", target))
        assertTrue(SourceDateGate.accepts("last week", target))
    }

    @Test
    fun `dates are read the way sources actually print them`() {
        assertEquals(LocalDate.of(2026, 7, 13), SourceDateGate.parse("13/7/2026"))
        assertEquals(LocalDate.of(2026, 7, 29), SourceDateGate.parse("29/07/2026"))
        assertEquals(LocalDate.of(2026, 7, 30), SourceDateGate.parse("30 JULY 2026"))
        assertEquals(LocalDate.of(2026, 7, 30), SourceDateGate.parse("2026-07-30"))
        assertEquals(LocalDate.of(2026, 7, 28), SourceDateGate.parse("٢٨ يوليو ٢٠٢٦"))
        assertEquals(LocalDate.of(2026, 7, 30), SourceDateGate.parse("30 يوليو 2026"))
        assertNull(SourceDateGate.parse("سعر السهم"))
    }

    @Test
    fun `an impossible date is not invented`() {
        assertNull(SourceDateGate.parse("32/7/2026"))
        assertNull(SourceDateGate.parse("30/13/2026"))
    }
}
