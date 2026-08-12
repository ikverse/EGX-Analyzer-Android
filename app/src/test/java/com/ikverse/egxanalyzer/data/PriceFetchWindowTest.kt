package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which sessions a refresh actually asks for.
 *
 * This used to be a fixed five days, and the hole that left was permanent: a phone shut for a
 * fortnight came back, asked for five days, and never saw the nine it had missed - so a call whose
 * window covered them could never complete, never expire, and never show as overdue. The window has
 * to be as wide as the gap, which means it has to be computed rather than chosen once.
 */
class PriceFetchWindowTest {
    private val today = LocalDate.of(2026, 8, 11)
    private val utc = ZoneId.of("UTC")

    @Test
    fun `a stock with no stored history asks for everything`() {
        assertNull(PriceRepository.fetchFrom(latestStored = null, neededFrom = null, today = today))
        assertEquals("range=1y", PriceRepository.window(from = null, today = today))
    }

    @Test
    fun `a stock refreshed yesterday asks for a few days, not a year`() {
        val from = PriceRepository.fetchFrom(
            latestStored = today.minusDays(1),
            neededFrom = null,
            today = today,
        )

        // Three days of overlap: the newest stored session may have been saved mid-trade, and a
        // weekend sits behind it either way. Re-storing them costs nothing - they overwrite.
        assertEquals(today.minusDays(4), from)
    }

    @Test
    fun `a fortnight of not opening the app is a fortnight of prices`() {
        // The case the fixed five-day range could not reach, and the reason this exists.
        val from = PriceRepository.fetchFrom(
            latestStored = today.minusDays(14),
            neededFrom = null,
            today = today,
        )

        assertEquals(today.minusDays(17), from)
    }

    @Test
    fun `an open trade drags the window back over a hole that already exists`() {
        // History stops two days ago, so the incremental fetch would start there and step straight
        // over the gap in the middle of this trade's window. The trade's own call date wins.
        val from = PriceRepository.fetchFrom(
            latestStored = today.minusDays(2),
            neededFrom = today.minusDays(40),
            today = today,
        )

        assertEquals(today.minusDays(40), from)
    }

    @Test
    fun `a call more recent than the stored history does not narrow the window`() {
        // A trade taken yesterday must not stop the app fetching the week it missed.
        val from = PriceRepository.fetchFrom(
            latestStored = today.minusDays(9),
            neededFrom = today.minusDays(1),
            today = today,
        )

        assertEquals(today.minusDays(12), from)
    }

    @Test
    fun `history stored ahead of today never asks for a window that ends before it starts`() {
        val from = PriceRepository.fetchFrom(
            latestStored = today.plusDays(30),
            neededFrom = null,
            today = today,
        )

        assertEquals(today, from)
        assertTrue(from!! <= today)
    }

    @Test
    fun `a dated window names both ends, and includes the session that settled today`() {
        val query = PriceRepository.window(from = today.minusDays(4), today = today)

        val start = today.minusDays(4).atStartOfDay(utc).toEpochSecond()
        // Tomorrow, because period2 is exclusive of anything later in the same day: ending at
        // today's start would drop the session that has just settled.
        val end = today.plusDays(1).atStartOfDay(utc).toEpochSecond()
        assertEquals("period1=$start&period2=$end", query)
    }
}
