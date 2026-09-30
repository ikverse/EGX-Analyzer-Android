package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * That keeping prices fresh through a session actually lands on the session.
 *
 * The whole feature is a checkbox now, which means nothing about its timing is visible to the user
 * before it either works or does not - there is no form left in which a wrong day or a wrong hour
 * could be spotted. So the arithmetic is checked here instead, on the cases that cost something:
 * the weekend, the tail after the close, and a phone that woke up late.
 *
 * 2026-08-20 is a Thursday, which makes it the last trading day of its week - the one date where
 * "the next fire" has to step over two closed days.
 */
class MarketRefreshTest {

    @Test
    fun `fires every quarter hour from the bell to a little past the close`() {
        assertEquals(ScheduleClock.sessionStart, MarketRefresh.slots.first())
        assertEquals(ScheduleClock.sessionEnd, MarketRefresh.slots.last())
        assertEquals(LocalTime.of(10, 15), MarketRefresh.slots[1])
        // 10:00 to 14:45 inclusive, a quarter of an hour apart.
        assertEquals(20, MarketRefresh.slots.size)
    }

    @Test
    fun `before the open the first fire of the day is the bell`() {
        assertEquals(
            at("2026-08-20", "10:00"),
            MarketRefresh.nextFire(at("2026-08-20", "09:00")),
        )
    }

    @Test
    fun `inside a session the next fire is the next quarter`() {
        assertEquals(
            at("2026-08-20", "10:15"),
            MarketRefresh.nextFire(at("2026-08-20", "10:00")),
        )
        assertEquals(
            at("2026-08-20", "12:15"),
            MarketRefresh.nextFire(at("2026-08-20", "12:07")),
        )
    }

    @Test
    fun `the last fire of a Thursday is followed by Sunday, not Friday`() {
        assertEquals(
            at("2026-08-23", "10:00"),
            MarketRefresh.nextFire(at("2026-08-20", "14:45")),
        )
        assertEquals(
            at("2026-08-23", "10:00"),
            MarketRefresh.nextFire(at("2026-08-22", "23:00")),
        )
    }

    @Test
    fun `the fire owed is the quarter just gone`() {
        assertEquals(
            at("2026-08-20", "11:00"),
            MarketRefresh.dueFire(
                now = at("2026-08-20", "11:07"),
                lastRefreshAt = at("2026-08-20", "10:50"),
            ),
        )
    }

    /**
     * The case that stops the feed being asked for the same thing twice within seconds: a phone
     * opened inside a slot's window has already fetched for it on the way in.
     */
    @Test
    fun `a fetch since the fire came due has done its work`() {
        assertNull(
            MarketRefresh.dueFire(
                now = at("2026-08-20", "11:07"),
                lastRefreshAt = at("2026-08-20", "11:02"),
            ),
        )
    }

    @Test
    fun `nothing is owed with the market shut`() {
        assertNull(
            MarketRefresh.dueFire(now = at("2026-08-21", "12:00"), lastRefreshAt = null),
        )
        assertNull(
            MarketRefresh.dueFire(now = at("2026-08-20", "09:30"), lastRefreshAt = null),
        )
    }

    /**
     * Past the close the grace is the only thing bounding a fire, because inside a session every
     * moment is within a quarter of an hour of a slot by construction.
     */
    @Test
    fun `the last fire of the day expires a quarter of an hour after it`() {
        assertEquals(
            at("2026-08-20", "14:45"),
            MarketRefresh.dueFire(now = at("2026-08-20", "14:55"), lastRefreshAt = null),
        )
        assertNull(
            MarketRefresh.dueFire(now = at("2026-08-20", "15:05"), lastRefreshAt = null),
        )
    }

    /**
     * A fire keeps the clock time it reads, whatever the offset underneath it is doing.
     *
     * Egypt moves its clocks, and a fetch that quietly shifted by an hour would read a session
     * that had not opened - or stop a quarter of an hour before the one that had.
     */
    @Test
    fun `fires keep Cairo clock time across a daylight saving change`() {
        val autumn = MarketRefresh.nextFire(at("2026-10-25", "09:00"))
        assertEquals(
            LocalTime.of(10, 0),
            autumn.atZone(ScheduleClock.ZONE).toLocalTime(),
        )
        val afterTheChange = MarketRefresh.nextFire(at("2026-11-01", "09:00"))
        assertEquals(
            LocalTime.of(10, 0),
            afterTheChange.atZone(ScheduleClock.ZONE).toLocalTime(),
        )
    }

    private fun at(date: String, time: String): Instant =
        LocalDateTime.parse("${date}T$time").atZone(ScheduleClock.ZONE).toInstant()
}
