package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 1D range's arithmetic: which bars are "the newest session", what a session adds up to, and
 * how several stocks' sessions become one line beside the index's.
 */
class DayBarTest {
    private val thursday = LocalDate.of(2026, 11, 12)

    /** A bar at a time on the exchange's own clock. */
    private fun bar(date: LocalDate, time: String, close: Double?, open: Double? = null): DayBar =
        DayBar(at(date, time), open, high = null, low = null, close = close)

    private fun at(date: LocalDate, time: String): Instant =
        date.atTime(LocalTime.parse(time)).atZone(ScheduleClock.ZONE).toInstant()

    @Test
    fun `the newest session is the last day's bars alone, in order, without the empty ones`() {
        val bars = listOf(
            bar(thursday, "10:05", 101.0),
            bar(thursday.minusDays(1), "14:25", 99.0),
            bar(thursday, "10:00", 100.0),
            bar(thursday, "10:10", null),
        )
        val newest = bars.newestSession()
        assertEquals(listOf(100.0, 101.0), newest.map { it.close })
        assertTrue(newest.all { it.session() == thursday })
    }

    @Test
    fun `a weekend answers with the last session held`() {
        val bars = listOf(bar(thursday, "10:00", 100.0), bar(thursday, "14:25", 102.0))
        // Asked on the Saturday after: the list is the same, and so is the answer.
        assertEquals(2, bars.newestSession().size)
        assertTrue(emptyList<DayBar>().newestSession().isEmpty())
    }

    @Test
    fun `a session's move is measured from its first open`() {
        val bars = listOf(bar(thursday, "10:00", 101.0, open = 100.0), bar(thursday, "10:05", 103.0))
        assertEquals(3.0, bars.dayMove()!!, 1e-9)
        assertEquals(1.0, bars.dayMoveTo(bars.first())!!, 1e-9)
    }

    @Test
    fun `a session with one bar has no move`() {
        assertNull(listOf(bar(thursday, "10:00", 100.0)).dayMove())
    }

    @Test
    fun `the book's line averages its stocks and holds a quiet one flat until it trades`() {
        val early = listOf(bar(thursday, "10:00", 100.0, open = 100.0), bar(thursday, "10:10", 104.0))
        // Its first trade is at 10:05, already two percent up on its own open.
        val late = listOf(bar(thursday, "10:05", 51.0, open = 50.0), bar(thursday, "10:10", 52.0))
        val line = bookDayLine(listOf(early, late))
        assertEquals(listOf(at(thursday, "10:00"), at(thursday, "10:05"), at(thursday, "10:10")), line.map { it.at })
        assertEquals(0.0, line[0].changePct, 1e-9)
        // (0 + 2) / 2: the early stock has printed nothing new, the late one its first bar.
        assertEquals(1.0, line[1].changePct, 1e-9)
        assertEquals(4.0, line[2].changePct, 1e-9)
    }

    @Test
    fun `a stock that last traded on an earlier session is left out of the day`() {
        val today = listOf(bar(thursday, "10:00", 100.0, open = 100.0), bar(thursday, "10:05", 102.0))
        val stale = listOf(bar(thursday.minusDays(2), "10:00", 10.0, open = 10.0), bar(thursday.minusDays(2), "10:05", 20.0))
        val line = bookDayLine(listOf(today, stale))
        assertEquals(2.0, line.last().changePct, 1e-9)
        assertTrue(bookDayLine(emptyList()).isEmpty())
    }

    @Test
    fun `the index is placed on the book's own clock`() {
        val book = listOf(DayPoint(at(thursday, "10:00"), 0.0), DayPoint(at(thursday, "10:05"), 1.0), DayPoint(at(thursday, "10:10"), 2.0))
        val index = listOf(DayPoint(at(thursday, "10:00"), 0.0), DayPoint(at(thursday, "10:07"), 0.5))
        assertEquals(listOf(0 to 0.0, 1 to 0.0, 2 to 0.5), marketAlongDay(book, index))
    }

    @Test
    fun `an index from another session is not drawn beside today's book`() {
        val book = listOf(DayPoint(at(thursday, "10:00"), 0.0), DayPoint(at(thursday, "10:05"), 1.0))
        val index = listOf(DayPoint(at(thursday.minusDays(1), "10:00"), 0.0), DayPoint(at(thursday.minusDays(1), "10:05"), 0.5))
        assertTrue(marketAlongDay(book, index).isEmpty())
    }
}
