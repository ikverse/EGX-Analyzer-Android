package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The reverse of what a sale writes onto a position, which is what lets an already-sold trade be
 * edited: the dialog has to open on the same figures [recordSale] would have written, or a
 * correction would silently replace one leg's price with the other's.
 */
class RecordedSaleTest {
    private val bought = LocalDate.of(2026, 7, 20)

    private fun position(
        exitPrice: Double? = null,
        exitDate: LocalDate? = null,
        exitPrice1: Double? = null,
        exitDate1: LocalDate? = null,
        exitPrice2: Double? = null,
        exitSplitPct: Double? = null,
    ) = Position(
        ticker = "COMI",
        recommendationDate = bought,
        entryPrice = 10.0,
        entryDate = bought,
        exitPrice = exitPrice,
        exitDate = exitDate,
        exitPrice1 = exitPrice1,
        exitDate1 = exitDate1,
        exitPrice2 = exitPrice2,
        exitSplitPct = exitSplitPct,
    )

    /** The same field-writing [LiveAppState.recordSale] does, so the round trip is the real one. */
    private fun Position.withSale(sale: Sale) = copy(
        exitPrice = sale.blended,
        exitDate = sale.closedOn,
        exitPrice1 = sale.price1.takeIf { sale.inTwoParts },
        exitDate1 = sale.openedOn,
        exitPrice2 = sale.price2.takeIf { sale.inTwoParts },
        exitSplitPct = sale.splitPct.takeIf { sale.inTwoParts },
    )

    @Test
    fun `a trade with no sale has none to reopen`() {
        assertNull(position().recordedSale())
    }

    @Test
    fun `a single-price sale round-trips to the price and day it was sold at`() {
        val sale = Sale(price1 = 11.4, date1 = bought.plusDays(3))
        val recorded = requireNotNull(position().withSale(sale).recordedSale())

        assertEquals(sale.price1, recorded.price1, 0.0001)
        assertEquals(sale.date1, recorded.date1)
        assertNull(recorded.price2)
        assertNull(recorded.date2)
        assertEquals(FULL_SPLIT_PCT, recorded.splitPct, 0.0001)
        // What every figure on the card is actually measured from - if this drifts from the sale it
        // was built from, editing a single-price sale would silently change what it closed at.
        assertEquals(sale.blended, recorded.blended, 0.0001)
    }

    @Test
    fun `a two-part sale round-trips both legs and their own days`() {
        val sale = Sale(
            price1 = 11.0,
            date1 = bought.plusDays(3),
            price2 = 12.0,
            date2 = bought.plusDays(9),
            splitPct = 75.0,
        )
        val recorded = requireNotNull(position().withSale(sale).recordedSale())

        assertEquals(sale.price1, recorded.price1, 0.0001)
        assertEquals(sale.date1, recorded.date1)
        assertEquals(sale.price2!!, recorded.price2!!, 0.0001)
        assertEquals(sale.date2, recorded.date2)
        assertEquals(sale.splitPct, recorded.splitPct, 0.0001)
        assertEquals(sale.blended, recorded.blended, 0.0001)
    }
}
