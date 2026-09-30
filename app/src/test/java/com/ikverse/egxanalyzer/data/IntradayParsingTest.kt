package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a chart response as intraday bars, and refusing to when it is not one.
 *
 * The refusal is the point. Ordering an entry against a target is the one judgement in the app made
 * from a sequence rather than from arithmetic, so a response that quietly is not a sequence has to
 * be caught here - by the time it reaches the scorer it looks exactly like a real answer.
 */
class IntradayParsingTest {

    @Test
    fun `bars come back in the order the feed sent them`() {
        val bars = parseIntradayBars("COMI", chart("5m", listOf(0L to (10.0 to 9.5), 300L to (12.0 to 11.0))), "5m")

        assertEquals(listOf(0L, 300L), bars!!.map { it.at.epochSecond })
        assertEquals(10.0, bars.first().high!!, 0.0001)
        assertEquals("COMI", bars.first().ticker)
    }

    @Test
    fun `a daily answer to an intraday request is refused`() {
        // The legacy `SYMBOL.CA` symbols ignore `interval` and send daily rows instead. Accepted,
        // one daily bar would be read as the whole session and would place the entry and the target
        // in the same instant - which is a verdict, not an absence of one.
        val payload = chart("1d", listOf(0L to (12.5 to 9.5)))

        assertNull(parseIntradayBars("COMI", payload, "5m"))
    }

    @Test
    fun `a session the feed has no bars for is empty rather than unusable`() {
        // Empty is an answer worth recording: it stops the session being asked about every refresh
        // for the rest of the record's life. Null is a response that cannot be trusted at all.
        val payload = """{"chart":{"result":[{"meta":{"dataGranularity":"5m"}}]}}"""

        assertEquals(emptyList<Any>(), parseIntradayBars("COMI", payload, "5m"))
    }

    @Test
    fun `a bar in which nothing traded is dropped`() {
        // Five minutes with no trade reports nulls. It cannot have touched a level, so keeping it
        // would only give the ordering an index that means nothing.
        val payload = """
            {"chart":{"result":[{
              "meta":{"dataGranularity":"5m"},
              "timestamp":[0,300,600],
              "indicators":{"quote":[{"high":[10.0,null,12.0],"low":[9.5,null,11.0]}]}
            }]}}
        """.trimIndent()

        val bars = parseIntradayBars("COMI", payload, "5m")

        assertEquals(listOf(0L, 600L), bars!!.map { it.at.epochSecond })
    }

    @Test
    fun `a response that is not a chart at all is refused`() {
        assertNull(parseIntradayBars("COMI", """{"chart":{"result":null,"error":"nope"}}""", "5m"))
    }

    @Test
    fun `a zero price is not a price`() {
        // A session still being written reports zeros where its extremes belong, and a low of zero
        // sits under every level anyone has ever printed.
        val payload = chart("5m", listOf(0L to (0.0 to 0.0), 300L to (12.0 to 11.0)))

        val bars = parseIntradayBars("COMI", payload, "5m")

        assertTrue(bars!!.single().at.epochSecond == 300L)
    }

    /** A chart response carrying one quote series, shaped as Yahoo sends it. */
    private fun chart(granularity: String, bars: List<Pair<Long, Pair<Double, Double>>>): String {
        val stamps = bars.joinToString(",") { it.first.toString() }
        val highs = bars.joinToString(",") { it.second.first.toString() }
        val lows = bars.joinToString(",") { it.second.second.toString() }
        return """
            {"chart":{"result":[{
              "meta":{"dataGranularity":"$granularity"},
              "timestamp":[$stamps],
              "indicators":{"quote":[{"high":[$highs],"low":[$lows]}]}
            }]}}
        """.trimIndent()
    }
}
