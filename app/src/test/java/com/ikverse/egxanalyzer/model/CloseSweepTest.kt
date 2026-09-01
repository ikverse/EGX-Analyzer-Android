package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/**
 * The one wake a trading day that lets the phone say what the session did to a trade.
 *
 * A rule about what happens at 14:45 next Tuesday cannot be checked by waiting for next Tuesday.
 * The cases that cost something are the weekend, the fire that is late rather than missed, and the
 * refresh that has already done this fire's work.
 *
 * 2026-08-20 is a Thursday - the last trading day of its week. 2026-08-23 is the Sunday after it.
 */
class CloseSweepTest {

    @Test
    fun `it fires at the close, a quarter of an hour past the bell`() {
        assertEquals(
            at("2026-08-20", "14:45"),
            CloseSweep.nextFire(at("2026-08-20", "09:00")),
        )
    }

    @Test
    fun `a fire just served gives the next trading day, not the same close again`() {
        assertEquals(
            at("2026-08-23", "14:45"),
            CloseSweep.nextFire(at("2026-08-20", "14:45")),
        )
    }

    @Test
    fun `the weekend is stepped over`() {
        // Friday and Saturday, when nothing trades and there is nothing to announce.
        assertEquals(
            at("2026-08-23", "14:45"),
            CloseSweep.nextFire(at("2026-08-21", "18:00")),
        )
    }

    @Test
    fun `the close is owed once the prices on disk are older than it`() {
        val due = CloseSweep.dueFire(
            now = at("2026-08-20", "14:46"),
            lastRefreshAt = at("2026-08-20", "08:00"),
        )
        assertEquals(at("2026-08-20", "14:45"), due)
    }

    @Test
    fun `a refresh after the close has already done the work`() {
        // The 14:45 slot on a phone that keeps prices fresh, or the button pressed at four o'clock.
        // Either way the day's figures are in and the record has been re-scored off them.
        assertNull(
            CloseSweep.dueFire(
                now = at("2026-08-20", "17:00"),
                lastRefreshAt = at("2026-08-20", "14:45"),
            ),
        )
    }

    @Test
    fun `a refresh from the morning has not`() {
        // The reason this is asked of a moment and not of a day. Prices fetched at breakfast say
        // nothing about the session that closed after lunch, and a day-level guard would have this
        // fire talked out of running by them.
        assertEquals(
            at("2026-08-20", "14:45"),
            CloseSweep.dueFire(
                now = at("2026-08-20", "23:30"),
                lastRefreshAt = at("2026-08-20", "09:15"),
            ),
        )
    }

    @Test
    fun `a phone that was asleep at the close still owes it that evening`() {
        // Deliberately no grace window. A refresh slot that is late has been superseded by the next
        // one fifteen minutes behind it; this fire has no successor for a day, and its whole
        // promise is that the session's endings are announced on the day they happened.
        assertEquals(
            at("2026-08-20", "14:45"),
            CloseSweep.dueFire(
                now = at("2026-08-20", "22:00"),
                lastRefreshAt = at("2026-08-19", "14:45"),
            ),
        )
    }

    @Test
    fun `a phone that missed two closes owes exactly one`() {
        // Back on Wednesday having been off since Sunday. One fetch answers all three: prices come
        // back for every session in the gap, so catching up on the sweep would be three passes over
        // the feed to reach one answer.
        assertEquals(
            at("2026-08-26", "14:45"),
            CloseSweep.dueFire(
                now = at("2026-08-26", "16:00"),
                lastRefreshAt = at("2026-08-23", "14:45"),
            ),
        )
    }

    @Test
    fun `nothing is owed before the close`() {
        // Thursday morning, with Wednesday's close already served. The next fire is this afternoon.
        assertNull(
            CloseSweep.dueFire(
                now = at("2026-08-20", "11:00"),
                lastRefreshAt = at("2026-08-19", "14:45"),
            ),
        )
    }

    @Test
    fun `a phone that has never fetched anything owes the last close`() {
        assertEquals(
            at("2026-08-20", "14:45"),
            CloseSweep.dueFire(now = at("2026-08-20", "15:00"), lastRefreshAt = null),
        )
    }

    @Test
    fun `the weekend still owes the close that ended the week`() {
        // Saturday, on a phone that was off on Thursday afternoon. The endings of Thursday's
        // session are a day old and have never been announced, and Sunday's close is a day away.
        assertEquals(
            at("2026-08-20", "14:45"),
            CloseSweep.dueFire(
                now = at("2026-08-22", "12:00"),
                lastRefreshAt = at("2026-08-20", "10:00"),
            ),
        )
    }

    private fun at(date: String, time: String): Instant =
        LocalDateTime.parse("${date}T$time").atZone(ScheduleClock.ZONE).toInstant()
}
