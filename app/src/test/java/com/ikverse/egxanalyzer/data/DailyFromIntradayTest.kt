package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * A daily history built out of finer bars, for a stock no daily feed carries.
 *
 * This is the one place in the app that produces a price row the exchange never reported, so the
 * bar for it is higher than for anything that merely reads one. Two things have to hold: the
 * arithmetic is the definition of a daily bar rather than an approximation of one, and every row it
 * makes is marked as built rather than read.
 */
class DailyFromIntradayTest {

    /** 2026-08-10, inside the EGX session, which sits in the middle of a UTC day. */
    private fun at(hour: Int, minute: Int = 0): Instant =
        Instant.parse("2026-08-10T%02d:%02d:00Z".format(hour, minute))

    private fun bar(
        at: Instant,
        open: Double? = null,
        high: Double? = null,
        low: Double? = null,
        close: Double? = null,
        volume: Double? = null,
    ) = SessionBar(at, open, high, low, close, volume)

    @Test
    fun `a session takes its ends from the ends and its extremes from all of it`() {
        val session = DailyFromIntraday.aggregate(
            "VLMRA",
            listOf(
                bar(at(8), open = 10.0, high = 10.4, low = 9.9, close = 10.2, volume = 100.0),
                bar(at(9), open = 10.2, high = 11.0, low = 10.1, close = 10.8, volume = 250.0),
                bar(at(10), open = 10.8, high = 10.9, low = 9.5, close = 9.7, volume = 150.0),
            ),
        ).single()

        assertEquals(LocalDate.of(2026, 8, 10), session.date)
        // The first bar's open and the last bar's close - not the highest open or the last high.
        assertEquals(10.0, session.open!!, 1e-9)
        assertEquals(9.7, session.close!!, 1e-9)
        // The extremes across the whole day, which is what a stop and a target are checked against.
        assertEquals(11.0, session.high!!, 1e-9)
        assertEquals(9.5, session.low!!, 1e-9)
        assertEquals(500.0, session.volume!!, 1e-9)
        assertTrue(session.derived)
    }

    @Test
    fun `bars are ordered before the ends are taken`() {
        // A response is not promised in order, and taking the first and last of the list rather
        // than of the day would put the open and close on whichever bars happened to be at the ends
        // of the array - a wrong figure, not a rounded one.
        val session = DailyFromIntraday.aggregate(
            "VLMRA",
            listOf(
                bar(at(10), open = 10.8, high = 10.9, low = 9.5, close = 9.7),
                bar(at(8), open = 10.0, high = 10.4, low = 9.9, close = 10.2),
                bar(at(9), open = 10.2, high = 11.0, low = 10.1, close = 10.8),
            ),
        ).single()

        assertEquals(10.0, session.open!!, 1e-9)
        assertEquals(9.7, session.close!!, 1e-9)
    }

    @Test
    fun `a day of bars that never traded is dropped rather than stored as a session`() {
        // A bar in which nothing traded reports nulls, and sometimes zeros. A low of zero sits
        // under every stop loss ever printed, which is the bug that put twelve such rows on disk in
        // August and judged every call on those stocks as stopped.
        val built = DailyFromIntraday.aggregate(
            "VLMRA",
            listOf(
                bar(at(8), open = 0.0, high = 0.0, low = 0.0, close = 0.0),
                bar(at(9), high = null, low = null),
            ),
        )

        assertTrue(built.isEmpty())
    }

    @Test
    fun `a session keeps the last close it actually has`() {
        val session = DailyFromIntraday.aggregate(
            "VLMRA",
            listOf(
                bar(at(8), open = 10.0, high = 10.4, low = 9.9, close = 10.2),
                // The feed's last bar of the day carries no close. Taking the last bar's close
                // outright would leave the session with none, which reads as one still trading.
                bar(at(9), high = 10.5, low = 10.0, close = null),
            ),
        ).single()

        assertEquals(10.2, session.close!!, 1e-9)
        assertEquals(10.5, session.high!!, 1e-9)
    }

    @Test
    fun `volume is absent rather than zero when no bar reported any`() {
        val session = DailyFromIntraday.aggregate(
            "VLMRA",
            listOf(bar(at(8), open = 10.0, high = 10.4, low = 9.9, close = 10.2)),
        ).single()

        // A session recorded as having traded nothing is a claim about the market. Absent is the
        // honest answer, and it is what the daily feed's own parse produces for the same case.
        assertNull(session.volume)
    }

    @Test
    fun `bars from several days become one session each, in date order`() {
        val built = DailyFromIntraday.aggregate(
            "VLMRA",
            listOf(
                bar(Instant.parse("2026-08-12T09:00:00Z"), open = 3.0, high = 3.1, low = 2.9, close = 3.0),
                bar(Instant.parse("2026-08-10T09:00:00Z"), open = 1.0, high = 1.1, low = 0.9, close = 1.0),
                bar(Instant.parse("2026-08-11T09:00:00Z"), open = 2.0, high = 2.1, low = 1.9, close = 2.0),
            ),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 12),
            ),
            built.map(DailySession::date),
        )
        assertTrue(built.all(DailySession::derived))
    }

    @Test
    fun `a response at the wrong granularity is refused outright`() {
        // The legacy `SYMBOL.CA` symbols ignore `interval` and answer with daily rows. Aggregating
        // those would produce a "derived" session built from one daily bar - identical to the row
        // it came from, and marked as though finer evidence stood behind it.
        val daily = payload(granularity = "1d")

        assertNull(parseSessionBars(daily, "1h"))
        // The same body at the granularity it claims is read normally, so the refusal is about the
        // mismatch and not about the shape of the response.
        assertEquals(1, parseSessionBars(payload(granularity = "1h"), "1h")?.size)
    }

    @Test
    fun `a parsed response carries the ends and the volume the aggregation needs`() {
        val bars = parseSessionBars(payload(granularity = "1h"), "1h")!!

        val bar = bars.single()
        assertEquals(10.0, bar.open!!, 1e-9)
        assertEquals(11.0, bar.high!!, 1e-9)
        assertEquals(9.0, bar.low!!, 1e-9)
        assertEquals(10.5, bar.close!!, 1e-9)
        assertEquals(1234.0, bar.volume!!, 1e-9)
    }

    private fun payload(granularity: String) = """
        {"chart":{"result":[{
          "meta":{"dataGranularity":"$granularity"},
          "timestamp":[1786000000],
          "indicators":{"quote":[{
            "open":[10.0],"high":[11.0],"low":[9.0],"close":[10.5],"volume":[1234]
          }]}
        }]}}
    """.trimIndent()
}
