package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A holding that goes out in two parts is still one position closing at one price.
 *
 * That is the whole design, and it is what these pin. The blend is the only figure the rest of the
 * app ever sees - the return, the ladder, the win rate and the record's averages all read
 * `Position.exitPrice` and none of them knows a sale can have parts - so the arithmetic behind it
 * and the day it is filed under are the two things that must not be wrong.
 */
class SaleTest {
    private val bought = LocalDate.of(2026, 7, 20)

    @Test
    fun `an even split closes at the midpoint of the two prices`() {
        val sale = Sale(
            price1 = 11.0,
            date1 = bought.plusDays(3),
            price2 = 12.0,
            date2 = bought.plusDays(9),
            splitPct = 50.0,
        )

        assertTrue(sale.inTwoParts)
        assertEquals(11.5, sale.blended, 0.0001)
    }

    @Test
    fun `an uneven split weights the price the larger part went at`() {
        // Three quarters at target 1 and the rest at target 2, which is a real way out of a call
        // the reader has stopped believing in: 0.75 x 11 + 0.25 x 15 = 12.
        val sale = Sale(
            price1 = 11.0,
            date1 = bought.plusDays(3),
            price2 = 15.0,
            date2 = bought.plusDays(9),
            splitPct = 75.0,
        )

        assertEquals(12.0, sale.blended, 0.0001)
    }

    @Test
    fun `the whole holding at one price is the sale it has always been`() {
        // The dialog collapses to one price and one day at a hundred percent, and this is what it
        // must produce: exactly what a single-price sale produced before parts existed, with
        // nothing left over to store.
        val sale = Sale(price1 = 11.4, date1 = bought.plusDays(3))

        assertFalse(sale.inTwoParts)
        assertEquals(11.4, sale.blended, 0.0001)
        assertEquals(bought.plusDays(3), sale.closedOn)
        assertNull(sale.openedOn)
    }

    @Test
    fun `a second price with no share of the holding is not a second part`() {
        // A hundred percent at the first price leaves the second nothing to apply to. Reading it
        // anyway would store a leg that moved no shares and a blend nobody could arrive at.
        val sale = Sale(
            price1 = 11.0,
            date1 = bought.plusDays(3),
            price2 = 99.0,
            date2 = bought.plusDays(9),
            splitPct = FULL_SPLIT_PCT,
        )

        assertFalse(sale.inTwoParts)
        assertEquals(11.0, sale.blended, 0.0001)
    }

    @Test
    fun `the position is flat on the day the second part goes`() {
        val sale = Sale(
            price1 = 11.0,
            date1 = bought.plusDays(3),
            price2 = 12.0,
            date2 = bought.plusDays(9),
            splitPct = 50.0,
        )

        assertEquals(bought.plusDays(9), sale.closedOn)
        assertEquals(bought.plusDays(3), sale.openedOn)
    }

    @Test
    fun `dates typed the wrong way round still close on the later of them`() {
        // The dialog refuses this, and the model refuses to depend on the dialog: the day the
        // position was flat is the later day whichever field it was typed into, or a trade would
        // be filed as having closed before half of it was sold.
        val sale = Sale(
            price1 = 11.0,
            date1 = bought.plusDays(9),
            price2 = 12.0,
            date2 = bought.plusDays(3),
            splitPct = 50.0,
        )

        assertEquals(bought.plusDays(9), sale.closedOn)
        assertEquals(bought.plusDays(3), sale.openedOn)
    }

    @Test
    fun `the blend is rounded to the three decimals every price here is quoted in`() {
        // A third at 10 and two thirds at 11 is 10.6666..., and a record carrying float noise
        // would print one figure on the card and store another.
        val sale = Sale(
            price1 = 10.0,
            date1 = bought,
            price2 = 11.0,
            date2 = bought.plusDays(1),
            splitPct = 33.333,
        )

        assertEquals(10.667, sale.blended, 0.0000001)
    }
}
