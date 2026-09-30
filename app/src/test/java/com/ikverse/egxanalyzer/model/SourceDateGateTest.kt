package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SourceDateGateTest {

    /** Thursday 30 July 2026. */
    private val target = LocalDate.of(2026, 7, 30)

    @Test
    fun `a card printed with the target session is kept`() {
        assertTrue(SourceDateGate.accepts("30 JULY 2026", target))
        assertTrue(SourceDateGate.accepts("30/7/2026", target))
        assertTrue(SourceDateGate.accepts("30-Jul-2026", target))
    }

    @Test
    fun `a T plus one card needs no exception`() {
        // Published after Wednesday's close for Thursday's session, and printed with Thursday's
        // date. When it was posted does not enter into it - the card names the session it calls.
        assertTrue(SourceDateGate.accepts("30 JULY 2026", target))
        // The same card a day later belongs to the next report, not this one, so no call is
        // counted twice.
        assertFalse(SourceDateGate.accepts("31/7/2026", target))
    }

    @Test
    fun `a card printed the session before is rejected`() {
        // A call made on Wednesday for Wednesday. Being inside the window that collected it is not
        // the same as belonging to this session.
        assertFalse(SourceDateGate.accepts("29/07/2026", target))
    }

    @Test
    fun `an older card is rejected however recently it was posted`() {
        // The failure this exists for: a 13 July screenshot re-posted on 30 July.
        assertFalse(SourceDateGate.accepts("13/7/2026", target))
        assertFalse(SourceDateGate.accepts("28 July 2026", target))
    }

    @Test
    fun `a later card is rejected`() {
        assertFalse(SourceDateGate.accepts("30 JULY 2026", LocalDate.of(2026, 7, 29)))
        assertFalse(SourceDateGate.accepts("1/8/2026", target))
    }

    @Test
    fun `a weekend date is neither special nor exempt`() {
        // Sunday 2 August opens after a Friday and Saturday the exchange does not trade, but the
        // rule does not care about sessions - only about the date printed on the card.
        val sunday = LocalDate.of(2026, 8, 2)
        assertTrue(SourceDateGate.accepts("2/8/2026", sunday))
        assertFalse(SourceDateGate.accepts("30/7/2026", sunday))
        assertFalse(SourceDateGate.accepts("31/7/2026", sunday))
        assertFalse(SourceDateGate.accepts("1/8/2026", sunday))
    }

    @Test
    fun `an unreadable date is rejected`() {
        // It cannot be shown to belong here, and the model was already told to exclude an undated
        // card. One arriving anyway means its own gate did not hold.
        assertFalse(SourceDateGate.accepts(null, target))
        assertFalse(SourceDateGate.accepts("", target))
        assertFalse(SourceDateGate.accepts("last week", target))
        assertFalse(SourceDateGate.accepts("سعر السهم", target))
    }

    @Test
    fun `without a target session nothing is filtered`() {
        assertTrue(SourceDateGate.accepts("13/7/2026", null))
        assertTrue(SourceDateGate.accepts(null, null))
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
