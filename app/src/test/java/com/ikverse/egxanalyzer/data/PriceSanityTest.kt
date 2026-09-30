package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * That a split is recognised as a split rather than scored as a collapse.
 *
 * The failure this exists to stop is silent and retroactive: a company hands out bonus shares, the
 * printed price halves overnight, and every open call on that stock is recorded as a stop-out. The
 * channel that made them loses its rate for something it had no part in, and nothing on screen looks
 * wrong - a distorted ranking reads exactly like a true one.
 */
class PriceSanityTest {
    private val start = LocalDate.of(2026, 8, 2)

    /** Consecutive sessions, one per day, at the prices given. */
    private fun series(vararg closes: Double, from: LocalDate = start): List<DailySession> =
        closes.mapIndexed { index, close ->
            DailySession(
                ticker = TICKER,
                date = from.plusDays(index.toLong()),
                high = close,
                low = close,
                close = close,
                volume = 1.0,
                open = close,
            )
        }

    @Test
    fun `an ordinary week has nothing wrong with it`() {
        val found = PriceSanity.breaks(TICKER, series(10.0, 10.4, 10.2, 10.9, 10.6))

        assertTrue(found.isEmpty())
    }

    @Test
    fun `a two-for-one split is a change of scale, not a fifty percent loss`() {
        val found = PriceSanity.breaks(TICKER, series(10.0, 10.2, 5.1, 5.2))

        val event = found.single()
        assertEquals(start.plusDays(2), event.date)
        assertEquals(10.2, event.previousClose, 0.0001)
        assertEquals(5.1, event.openingPrice, 0.0001)
        assertEquals(0.5, event.ratio, 0.01)
    }

    @Test
    fun `a hard session inside what the exchange permits is left alone`() {
        // The exchange caps how far a stock may move in a session, and this is a bad day rather
        // than a data event. Flagging it would put a real loss beyond the reach of every rate.
        val found = PriceSanity.breaks(TICKER, series(10.0, 9.0))

        assertTrue(found.isEmpty())
    }

    @Test
    fun `a move across a hole in the history is not read as a split`() {
        // Three months apart. The app cannot say what happened in between, and a stock that
        // doubled over a quarter is ordinary - calling it a split would flag every stock the app
        // had not priced in a while.
        val old = series(10.0)
        val recent = series(21.0, from = start.plusMonths(3))

        assertTrue(PriceSanity.breaks(TICKER, old + recent).isEmpty())
    }

    @Test
    fun `the break between what is stored and what was just fetched is the one that matters`() {
        // Where a split actually shows up: a refresh asks only for what it is missing, so the
        // stored half stays in the old prices while the fetched half arrives in the new ones.
        // Checking only inside the fetched window would miss precisely this.
        val stored = series(10.0, 10.2)
        val fetched = series(5.1, 5.15, from = start.plusDays(2))

        val event = PriceSanity.breaks(TICKER, stored + fetched).single()

        assertEquals(start.plusDays(2), event.date)
    }

    @Test
    fun `a session still in progress does not hide the split behind it`() {
        // A row with no close cannot anchor a comparison, but it must not break the chain either -
        // the split sits on the far side of it.
        val trading = DailySession(TICKER, start.plusDays(1), null, null, null, null, null)
        val series = series(10.0) + trading + series(5.0, from = start.plusDays(2))

        assertEquals(start.plusDays(2), PriceSanity.breaks(TICKER, series).single().date)
    }

    @Test
    fun `a zero price is not a price, and not a split either`() {
        // The zero-price bug this app already fixed once: a session in progress arriving with
        // zeros. Read as a real price it is a 100% collapse, which is the shape of a split and
        // would now be recorded as one.
        val zeroed = DailySession(TICKER, start.plusDays(1), 0.0, 0.0, 0.0, 0.0, 0.0)

        val found = PriceSanity.breaks(TICKER, series(10.0) + zeroed + series(10.1, from = start.plusDays(2)))

        assertTrue(found.isEmpty())
    }

    @Test
    fun `one split is reported once, not on every session after it`() {
        val found = PriceSanity.breaks(TICKER, series(10.0, 10.2, 5.1, 5.2, 5.15, 5.3))

        assertEquals(1, found.size)
    }

    @Test
    fun `a feed still reporting last month is stale, a weekend is not`() {
        val friday = LocalDate.of(2026, 8, 7)
        val sunday = LocalDate.of(2026, 8, 9)

        // EGX rests on Friday and Saturday. Two days of silence is the calendar, not a fault.
        assertFalse(PriceSanity.isStale(series(10.0, from = friday.minusDays(1)), sunday))
        assertTrue(PriceSanity.isStale(series(10.0, from = friday.minusMonths(1)), sunday))
    }

    @Test
    fun `a series with nothing in it is unpriced rather than stale`() {
        // The two are different findings and want different words: no history at all is already
        // reported, and calling it stale as well would say the feed had stopped when it had never
        // started.
        assertFalse(PriceSanity.isStale(emptyList(), start))
        assertEquals(null, PriceSanity.ageInDays(emptyList(), start))
    }

    private companion object {
        const val TICKER = "AMOC"
    }

    @Test
    fun `a day the exchange was shut is not a session`() {
        // Yahoo does not omit an EGX holiday. It answers with the previous close repeated across
        // the high, the low and the close, and nothing traded against it - and ten such dates in a
        // year of prices came back that way on ninety-one of ninety-two stocks at once.
        assertTrue(neverTraded(high = 5.9, low = 5.9, close = 5.9, volume = 0.0))
        assertTrue(neverTraded(high = 5.9, low = 5.9, close = 5.9, volume = null))
    }

    @Test
    fun `a real session that printed once is kept`() {
        // The three prices being one number is not enough on its own: a stock so illiquid it traded
        // at a single price looks identical, and it did trade. The volume is the whole difference,
        // and dropping those would take a stock's real sessions out of every window on it.
        assertFalse(neverTraded(high = 4.2, low = 4.2, close = 4.2, volume = 900.0))
    }

    @Test
    fun `a session that moved is never mistaken for a closed day`() {
        assertFalse(neverTraded(high = 6.18, low = 5.81, close = 5.9, volume = 0.0))
        // A session still trading reports no close yet, which is not the same as not having traded.
        assertFalse(neverTraded(high = 5.9, low = 5.9, close = null, volume = 0.0))
    }
}
