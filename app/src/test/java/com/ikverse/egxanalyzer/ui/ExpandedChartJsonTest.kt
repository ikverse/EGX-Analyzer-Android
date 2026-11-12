package com.ikverse.egxanalyzer.ui

import androidx.compose.ui.graphics.Color
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.DayBar
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
        style: ChartStyle = ChartStyle.CANDLES,
        showLevels: Boolean = true,
        indicators: Set<String> = setOf("VOL"),
        drawings: String? = null,
    ) = JSONObject(ExpandedChartJson.build(sessions, levels, calls, style, showLevels, theme, "k", indicators, drawings))

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
    fun `on 1D the candles are the day's bars at the moments they traded, with no call marks`() {
        val first = java.time.Instant.parse("2026-10-02T08:00:00Z")
        val bars = listOf(
            // Given out of order, and one of them empty.
            DayBar(first.plusSeconds(300), open = 101.0, high = 103.0, low = 100.5, close = 102.0, volume = 40.0),
            DayBar(first, open = null, high = 101.5, low = 99.0, close = 101.0, volume = 10.0),
            DayBar(first.plusSeconds(600), open = null, high = null, low = null, close = null),
        )
        val json = JSONObject(
            ExpandedChartJson.build(
                // The stored sessions are passed as they always are and must not be drawn.
                sessions = listOf(day("2026-10-01", 90.0), day("2026-10-02", 102.0)),
                levels = null,
                calls = setOf(LocalDate.parse("2026-10-02")),
                style = ChartStyle.CANDLES,
                showLevels = false,
                theme = theme,
                key = "k",
                bars = bars,
            ),
        )
        val candles = json.getJSONArray("candles")

        assertTrue(json.getBoolean("intraday"))
        assertEquals(2, candles.length())
        assertEquals(first.toEpochMilli(), candles.getJSONArray(0).getLong(0))
        // No open on the first bar: drawn from its close, like a session with none.
        assertEquals(101.0, candles.getJSONArray(0).getDouble(1), 0.0)
        assertEquals(first.plusSeconds(300).toEpochMilli(), candles.getJSONArray(1).getLong(0))
        assertEquals(40.0, candles.getJSONArray(1).getDouble(5), 0.0)
        assertEquals(0, json.getJSONArray("calls").length())
    }

    @Test
    fun `every other range says it is not intraday`() {
        assertFalse(build(listOf(day("2026-10-01", 101.0))).getBoolean("intraday"))
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
        val json = build(listOf(day("2026-10-01", 100.0)), levels = null, style = ChartStyle.LINE, showLevels = false)

        assertEquals(0, json.getJSONArray("levels").length())
        assertEquals("line", json.getString("style"))
        assertFalse(json.getBoolean("showLevels"))
        assertEquals("k", json.getString("key"))
    }

    @Test
    fun `the page is told the same cap on the scale the native chart uses`() {
        assertEquals(2.5, build(listOf(day("2026-10-01", 100.0))).getDouble("growth"), 0.0)
    }

    @Test
    fun `indicators travel in the chart's own order and unknown names are dropped`() {
        val json = build(listOf(day("2026-10-01", 100.0)), indicators = setOf("RSI", "NOPE", "MA", "VOL"))

        val list = json.getJSONArray("indicators")
        assertEquals(listOf("MA", "VOL", "RSI"), (0 until list.length()).map { list.getString(it) })
    }

    @Test
    fun `stored drawings are handed over as a list and a broken one as none`() {
        val kept = """[{"name":"segment","points":[{"timestamp":1,"value":2}],"text":null}]"""
        assertEquals(1, build(listOf(day("2026-10-01", 100.0)), drawings = kept).getJSONArray("drawings").length())
        assertEquals(0, build(listOf(day("2026-10-01", 100.0)), drawings = "{not json").getJSONArray("drawings").length())
        assertEquals(0, build(listOf(day("2026-10-01", 100.0))).getJSONArray("drawings").length())
    }

    @Test
    fun `every chart type has a word the page knows`() {
        assertEquals(listOf("candles", "hollow", "bars", "line", "area"), ChartStyle.entries.map { it.id })
    }

    @Test
    fun `the header's move is the last close against the one before it`() {
        val move = listOf(day("2026-10-01", 100.0), day("2026-10-02", null), day("2026-10-03", 105.0)).lastMove()!!

        assertEquals(105.0, move.close, 0.0)
        assertEquals(5.0, move.change, 1e-9)
        assertEquals(5.0, move.percent, 1e-9)
        assertEquals(null, listOf(day("2026-10-01", 100.0)).lastMove())
    }
}
