package com.ikverse.egxanalyzer.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The words and figures the two call sheets are built from. Functions of their arguments. */
class SheetPiecesTest {

    @Test
    fun `a sheet prints every price at the precision its finest one needs`() {
        // 2.1, 2.2, 1.85 and 1.59 printed as four kinds of number until 2026-11-11.
        val decimals = priceDecimals(1.9, 2.1, 2.2, 1.85, 1.59)
        assertEquals(2, decimals)
        assertEquals("1.90", formatPrice(1.9, decimals))
        assertEquals("2.10", formatPrice(2.1, decimals))
    }

    @Test
    fun `whole prices still carry two decimals, and thousandths keep their third`() {
        assertEquals(2, priceDecimals(320.0, 335.0, 350.0))
        assertEquals("320.00", formatPrice(320.0, 2))
        assertEquals(3, priceDecimals(0.415, 0.5))
        assertEquals("0.500", formatPrice(0.5, 3))
    }

    @Test
    fun `no prices at all is two decimals, and no price is the dash`() {
        assertEquals(2, priceDecimals(null, null))
        assertEquals(Dash, formatPrice(null, 2))
    }

    @Test
    fun `the price says which end of the ladder it has passed`() {
        assertEquals("1.59 now, below the stop", nowWords(1.59, 1.85, 2.1, 2.2, 2))
        assertEquals("2.30 now, above target 2", nowWords(2.3, 1.85, 2.1, 2.2, 2))
        assertEquals("2.30 now, above target 1", nowWords(2.3, 1.85, 2.1, null, 2))
        assertEquals("325.01 now", nowWords(325.01, 310.0, 335.0, 350.0, 2))
    }

    @Test
    fun `a trade that is over says where it ended rather than now`() {
        assertEquals("1.85 where it ended", nowWords(1.85, 1.85, 2.1, 2.2, 2, standing = "where it ended"))
    }

    @Test
    fun `a level is measured from the entry`() {
        assertEquals(4.6875, fromEntry(335.0, 320.0)!!, 1e-9)
        assertEquals(-3.125, fromEntry(310.0, 320.0)!!, 1e-9)
        assertNull(fromEntry(335.0, null))
        assertNull(fromEntry(null, 320.0))
    }

    @Test
    fun `a printed stamp reads as the app writes every other date`() {
        val today = LocalDate.of(2026, 11, 11)
        assertEquals("11 Nov, 10:54", printedWords("11-Nov-2026 10:54", today))
        assertEquals("11 Nov", printedWords("11-Nov-2026", today))
        assertEquals("9 Nov, 09:05", printedWords("2026-11-09 09:05", today))
    }

    @Test
    fun `a stamp it cannot read is left exactly as the card had it`() {
        assertEquals("الأربعاء ١١ نوفمبر", printedWords("الأربعاء ١١ نوفمبر"))
        assertEquals("yesterday's close", printedWords("  yesterday's close "))
    }

    @Test
    fun `keeping a trade past its stop says what that has cost`() {
        assertEquals(
            "Selling at the stop would have ended it at -2.6%. Holding since has cost 13.7 points more.",
            keptPastWords(ended = -2.63, now = -16.32, stopped = true),
        )
    }

    @Test
    fun `keeping a trade past its deadline can also have made money`() {
        assertEquals(
            "Selling at the deadline would have ended it at +1.2%. Holding since has made 3 points more.",
            keptPastWords(ended = 1.2, now = 4.2, stopped = false),
        )
    }

    @Test
    fun `with no price today it says only where the call ended`() {
        assertEquals(
            "Selling at the stop would have ended it at -2.6%.",
            keptPastWords(ended = -2.63, now = null, stopped = true),
        )
    }

    @Test
    fun `a watching call's condition says where the last close stands, and no more`() {
        assertEquals(
            "Read off the card. The last close is above the entry; the app cannot check anything else it asks for.",
            conditionWords(325.01, 320.0, 320.0),
        )
        assertEquals(
            "Read off the card. The last close is inside the entry range; the app cannot check anything else it asks for.",
            conditionWords(321.0, 320.0, 322.0),
        )
        assertEquals(
            "Read off the card. The last close is below the entry; the app cannot check anything else it asks for.",
            conditionWords(318.0, 320.0, 322.0),
        )
        assertEquals("Read off the card. The app cannot check it for you.", conditionWords(null, 320.0, 320.0))
    }
}
