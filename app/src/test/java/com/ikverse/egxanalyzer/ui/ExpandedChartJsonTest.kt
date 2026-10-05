package com.ikverse.egxanalyzer.ui

import androidx.compose.ui.graphics.Color
import com.ikverse.egxanalyzer.model.DailySession
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the expanded chart's page is sent.
 *
 * The page draws whatever it is given, so the places this can be wrong are the ones the feed makes
 * easy: a row with no open, a row whose extremes do not enclose its own body, a session still
 * trading with a zero where its low belongs, and a day that lands on the wrong side of midnight.
 */
class ExpandedChartJsonTest {
    private val theme = ChartTheme(
        surface = Color(0xFF101010),
        text = "#EEEEEE", muted = "#888888", rule = "#222222", up = "#00AA00", down = "#CC0000",
        line = "#2222FF", stop = "#CC0000", target = "#00AA00", entry = "#EEEEEE", paid = "#888888",
    )

    private fun day(
        date: String,
        close: Double?,
        open: Double? = null,
        high: Double? = null,
        low: Double? = null,
        volume: Double? = 1000.0,
    ) = DailySession("COMI", LocalDate.parse(date), high, low, close, volume, open)

    private fun build(
        sessions: List<DailySession>,
        levels: ChartLevels? = null,
        calls: Set<LocalDate> = emptySet(),
        line: Boolean = false,
        showLevels: Boolean = true,
    ) = JSONObject(ExpandedChartJson.build(sessions, levels, calls, line, showLevels, theme, "k"))

    @Test
    fun `a session missing its open is drawn from its close`() {
        val candle = build(listOf(day("2026-10-01", close = 100.0, high = 101.0, low = 99.0)))
            .getJSONArray("candles").getJSONArray(0)

        assertEquals(100.0, candle.getDouble(1), 0.0)
        assertEquals(101.0, candle.getDouble(2), 0.0)
        assertEquals(99.0, candle.getDouble(3), 0.0)
    }

    @Test
    fun `extremes that do not enclose the body are widened to the body`() {
        val candle = build(listOf(day("2026-10-01", close = 105.0, open = 95.0, high = 100.0, low = 98.0)))
            .getJSONArray("candles").getJSONArray(0)

        assertEquals(105.0, candle.getDouble(2), 0.0)
        assertEquals(95.0, candle.getDouble(3), 0.0)
    }

    @Test
    fun `a zero low is the feed saying the session was still trading and not a price`() {
        val candle = build(listOf(day("2026-10-01", close = 100.0, open = 99.0, high = 101.0, low = 0.0)))
            .getJSONArray("candles").getJSONArray(0)

        assertEquals(99.0, candle.getDouble(3), 0.0)
    }

    @Test
    fun `sessions with no close are left out and the rest come in date order`() {
        val rows = build(
            listOf(day("2026-10-02", 102.0), day("2026-10-03", null), day("2026-10-01", 101.0)),
        ).getJSONArray("candles")

        assertEquals(2, rows.length())
        assertEquals(101.0, rows.getJSONArray(0).getDouble(4), 0.0)
        assertEquals(102.0, rows.getJSONArray(1).getDouble(4), 0.0)
    }

    @Test
    fun `a session is sent at noon UTC so its day is the same in every timezone`() {
        val stamp = ExpandedChartJson.timestamp(LocalDate.parse("2026-10-01"))

        assertEquals(java.time.Instant.parse("2026-10-01T12:00:00Z").toEpochMilli(), stamp)
    }

    @Test
    fun `calls are sent only for sessions that are on the chart`() {
        val json = build(
            listOf(day("2026-10-01", 101.0), day("2026-10-02", 102.0)),
            calls = setOf(LocalDate.parse("2026-10-02"), LocalDate.parse("2026-09-01")),
        )

        assertEquals(
            listOf(ExpandedChartJson.timestamp(LocalDate.parse("2026-10-02"))),
            (0 until json.getJSONArray("calls").length()).map { json.getJSONArray("calls").getLong(it) },
        )
    }

    @Test
    fun `levels are worded as the sheet words them and absent ones are not sent`() {
        val levels = ChartLevels(
            source = "x", stopLoss = 119.37, entryLow = 125.0, entryHigh = 127.5,
            target1 = 134.61, target2 = null, paid = null,
        )

        val rows = ExpandedChartJson.levelRows(levels)

        assertEquals(
            listOf("stop 119.37", "entry 125–127.5", "t1 134.61"),
            rows.map { it.getString("label") },
        )
        assertEquals(listOf("stop", "entry", "target"), rows.map { it.getString("kind") })
        // The band is one line, drawn at its middle.
        assertEquals(126.25, rows[1].getDouble("price"), 0.0)
    }

    @Test
    fun `a single entry price is one plain figure`() {
        val rows = ExpandedChartJson.levelRows(
            ChartLevels("x", null, 126.0, 126.0, null, null, paid = 120.0),
        )

        assertEquals(listOf("entry 126", "you paid 120"), rows.map { it.getString("label") })
    }

    @Test
    fun `no levels means an empty list and the switch and mode travel with it`() {
        val json = build(listOf(day("2026-10-01", 100.0)), levels = null, line = true, showLevels = false)

        assertEquals(0, json.getJSONArray("levels").length())
        assertTrue(json.getBoolean("line"))
        assertFalse(json.getBoolean("showLevels"))
        assertEquals("k", json.getString("key"))
    }

    @Test
    fun `the page is told the same cap on the scale the native chart uses`() {
        assertEquals(2.5, build(listOf(day("2026-10-01", 100.0))).getDouble("growth"), 0.0)
    }
}
