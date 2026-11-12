package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/**
 * When the one-minute loop runs and which stocks it asks about.
 *
 * Both are rules about a clock and a list, so they are checked here rather than by watching a
 * phone through a session. 2026-11-12 is a Thursday, the last trading day of its week.
 */
class LivePricesTest {

    @Test
    fun `runs from the bell until the session is final`() {
        assertFalse(LivePrices.inSession(at("2026-11-12", "09:59")))
        assertTrue(LivePrices.inSession(at("2026-11-12", "10:00")))
        assertTrue(LivePrices.inSession(at("2026-11-12", "14:44")))
        // 14:45 is where the session becomes final and the reported close owns the row.
        assertFalse(LivePrices.inSession(at("2026-11-12", "14:45")))
        assertFalse(LivePrices.inSession(at("2026-11-12", "20:00")))
    }

    @Test
    fun `does not run on a day the exchange is shut`() {
        // Friday and Saturday, at an hour that would be mid-session on any other day.
        assertFalse(LivePrices.inSession(at("2026-11-13", "12:00")))
        assertFalse(LivePrices.inSession(at("2026-11-14", "12:00")))
        assertTrue(LivePrices.inSession(at("2026-11-15", "12:00")))
    }

    @Test
    fun `open trades lead, then open calls, then the watchlist`() {
        assertEquals(
            listOf("COMI", "SWDY", "ETEL", "FWRY"),
            LivePrices.watched(
                openTrades = listOf("COMI", "SWDY"),
                openCalls = listOf("ETEL"),
                watchlist = listOf("FWRY"),
            ),
        )
    }

    @Test
    fun `a stock named twice is asked about once, where it first appears`() {
        assertEquals(
            listOf("COMI", "ETEL"),
            LivePrices.watched(
                openTrades = listOf("COMI"),
                openCalls = listOf("ETEL", "comi"),
                watchlist = listOf("ETEL", " "),
            ),
        )
    }

    @Test
    fun `the cap drops the watchlist before it drops a trade`() {
        val trades = (1..30).map { "T$it" }
        val calls = (1..30).map { "C$it" }
        val watched = LivePrices.watched(trades, calls, listOf("WATCHED"))

        assertEquals(LivePrices.MAX_STOCKS, watched.size)
        assertTrue(watched.containsAll(trades))
        assertFalse("WATCHED" in watched)
    }

    @Test
    fun `only a call the market has not finished with is followed`() {
        assertTrue(LivePrices.stillMoving(Outcome.OPEN))
        assertTrue(LivePrices.stillMoving(Outcome.AMBIGUOUS))
        assertFalse(LivePrices.stillMoving(Outcome.FULL_HIT))
        assertFalse(LivePrices.stillMoving(Outcome.STOPPED))
        assertFalse(LivePrices.stillMoving(Outcome.EXPIRED))
        assertFalse(LivePrices.stillMoving(Outcome.ENTRY_NOT_REACHED))
    }

    private fun at(date: String, time: String): Instant =
        LocalDateTime.parse("${date}T$time").atZone(ScheduleClock.ZONE).toInstant()
}
