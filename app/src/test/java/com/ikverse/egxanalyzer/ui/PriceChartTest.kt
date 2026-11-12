package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.DayBar
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import com.ikverse.egxanalyzer.ui.theme.LightPalette
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The chart as it was rebuilt on 2026-11-11: every figure in a column beside the plot, and every
 * range in one track.
 *
 * What it replaced was reported from a phone as "odd": four level labels at four places on the
 * line, and a row of range chips whose last one was cut off at the tile's edge. The two properties
 * held here are the two that report was about - no tag overlaps another, and no range is ever off
 * the end at the narrowest width this app is drawn at.
 */
@RunWith(RobolectricTestRunner::class)
class PriceChartTest {

    @get:Rule
    val compose = createComposeRule()

    private val colors = chartTileColors(LightPalette)

    /** The tile's inner width on the Fold's cover screen: 411dp, less the page's and the tile's insets. */
    private val coverWidth = 347.dp

    private fun assertStacked(tops: List<Float>, heights: List<Float>, total: Float) {
        tops.indices.forEach { index ->
            assertTrue("tag $index starts above the chart: ${tops[index]}", tops[index] >= 0f)
            assertTrue("tag $index ends below the chart", tops[index] + heights[index] <= total + 0.01f)
            if (index > 0) {
                assertTrue(
                    "tag $index overlaps the one above it: ${tops[index - 1]} + ${heights[index - 1]} > ${tops[index]}",
                    tops[index - 1] + heights[index - 1] <= tops[index] + 0.01f,
                )
            }
        }
    }

    @Test
    fun `levels a couple of percent apart get a row each`() {
        // The reported chart: two targets, the latest close, an entry range and the stop, all
        // within 45 pixels of each other on a 220-pixel chart.
        val centres = listOf(139f, 148f, 159f, 165f, 178f)
        val heights = listOf(20f, 20f, 20f, 40f, 20f)
        val tops = stackTags(centres, heights, total = 220f)
        assertStacked(tops, heights, 220f)
        // Spread about the cluster rather than trailing off below it: the first tag moved up.
        assertTrue("the cluster did not spread upward: ${tops.first()}", tops.first() < 139f - 10f)
    }

    @Test
    fun `a tag with room sits level with its own line`() {
        val tops = stackTags(listOf(40f, 150f), listOf(20f, 20f), total = 220f)
        assertEquals(listOf(30f, 140f), tops)
    }

    @Test
    fun `tags pinned to an edge stay inside the chart`() {
        // Two levels beyond the top of the scale and one beyond the foot, all drawn at the edge.
        val heights = listOf(20f, 20f, 20f, 20f)
        val tops = stackTags(listOf(0f, 0f, 110f, 220f), heights, total = 220f)
        assertStacked(tops, heights, 220f)
        assertEquals(0f, tops.first())
        assertEquals(200f, tops.last())
    }

    @Test
    fun `a chart with levels tags each of them and the latest close`() {
        val levels = ChartLevels("a channel", stopLoss = 10.69, entryLow = 10.85, entryHigh = 10.9, target1 = 11.12, target2 = 11.25, paid = null)
        val tags = chartTags(levels, latest = 10.96, priceLow = 10.4, priceHigh = 13.1, colors = colors)
        assertEquals(listOf("Target 2", "Target 1", "Entry", "Stop", "Now"), tags.map { it.word })
        // A range takes two rows, so its width does not set the column's for every other tag.
        assertTrue(tags.single { it.word == "Entry" }.twoRows)
        assertTrue(tags.single { it.word == "Now" }.filled)
    }

    @Test
    fun `a chart with no levels tags the ends of what is on screen`() {
        val tags = chartTags(null, latest = 10.96, priceLow = 10.4, priceHigh = 13.1, colors = colors)
        assertEquals(listOf("High", "Low", "Now"), tags.map { it.word })
    }

    @Test
    fun `every range fits the cover screen and a press picks it`() {
        var range by mutableStateOf(ChartRange.MONTH)
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.width(coverWidth)) {
                    ChartRanges(ChartRange.entries, range, { range = it }, { it.label }, colors)
                }
            }
        }
        ChartRange.entries.forEach { option ->
            val node = compose.onNodeWithText(option.label)
            node.assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("${option.label} starts off the track", bounds.left >= 0.dp)
            assertTrue("${option.label} runs off the track: ${bounds.right}", bounds.right <= coverWidth)
        }
        compose.onNodeWithText("6M").performClick()
        assertEquals(ChartRange.SIX_MONTHS, range)
        compose.onNodeWithText("6M").assertIsSelected()
    }

    /** A session of five-minute bars from 10:00 on the exchange's clock. */
    private fun session(count: Int): List<DayBar> {
        val open = LocalDate.of(2026, 11, 12).atTime(10, 0).atZone(ScheduleClock.ZONE).toInstant()
        return List(count) { i -> DayBar(open.plusSeconds(i * 300L), open = 50.0 + i, high = null, low = null, close = 50.5 + i) }
    }

    @Test
    fun `1D leads the ranges, and is not offered for a stock with no day's bars`() {
        assertEquals(ChartRange.DAY, ChartRange.entries.first())
        assertEquals("1D", ChartRange.DAY.label)
        // The default is still a month: 1D is one press away, never where a chart opens.
        assertEquals(ChartRange.MONTH, ChartRange.Default)
        val state = FakeAppState(dayBarsByTicker = mapOf("COMI" to session(3)))
        assertTrue(ChartRange.DAY in ChartRange.entries.offeredFor(state, "COMI"))
        assertFalse(ChartRange.DAY in ChartRange.entries.offeredFor(state, "VLMRA"))
        // Nothing else is taken with it.
        assertEquals(ChartRange.entries.size - 1, ChartRange.entries.offeredFor(state, "VLMRA").size)
    }

    @Test
    fun `a touched bar reads as its time and what the session had done by then`() {
        val bars = session(4)
        val reading = dayReading(bars, bars[2])!!
        // 10:10, and (52.5 - 50) / 50 from the first bar's open.
        assertEquals("10:10 · ${formatPercent(5.0)} since the open", reading.first)
        assertEquals(formatPrice(52.5), reading.second)
        assertNull(dayReading(bars, null))
    }

    @Test
    fun `the day's headline says it is fetching, then which session it is`() {
        assertEquals("Fetching the session…", dayTitle(null))
        assertTrue(dayTitle(session(2)).endsWith("five-minute bars"))
    }

    @Test
    fun `a day's line draws at the cover width with its levels`() {
        val bars = session(41)
        val levels = ChartLevels("a channel", stopLoss = 48.0, entryLow = null, entryHigh = null, target1 = 95.0, target2 = null, paid = 51.0)
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.width(coverWidth)) {
                    DayChartOrNote(bars, levels, colors, selected = bars[5], onSelect = {})
                }
            }
        }
        compose.onNodeWithContentDescription("41 five-minute bars", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a day still on its way keeps the chart's place and says so`() {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.width(coverWidth)) { DayChartOrNote(null, null, colors, selected = null, onSelect = {}) }
            }
        }
        compose.onNodeWithText("Fetching the newest session…").assertIsDisplayed()
    }

    @Test
    fun `the chart draws at the cover width with every level on it`() {
        val start = LocalDate.of(2026, 10, 11)
        val sessions = List(22) { day ->
            val close = 13.1 - day * 0.1
            DailySession("EGCH", start.plusDays(day.toLong()), high = close + 0.1, low = close - 0.1, close = close, volume = 1.0)
        }
        val levels = ChartLevels("a channel", stopLoss = 10.69, entryLow = 10.85, entryHigh = 10.9, target1 = 11.12, target2 = 11.25, paid = 10.8)
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.width(coverWidth)) {
                    PriceChart(sessions, levels, setOf(start.plusDays(20)), colors, selected = sessions[5], onSelect = {})
                }
            }
        }
        compose.onNodeWithContentDescription("22 sessions", substring = true).assertIsDisplayed()
    }
}
