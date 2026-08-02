package com.ikverse.egxanalyzer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the ladder prints its prices.
 *
 * Every label is centred under its own mark, which is the whole point of putting them there, so
 * the interesting cases are the ones where that is impossible: the ends of the track, and levels
 * close enough that two labels would sit on top of each other.
 */
class PriceLadderTest {

    private fun place(
        centers: List<Float>,
        widths: List<Float>,
        trackWidth: Float = 300f,
        gap: Float = 6f,
    ) = layoutPriceLabels(centers, widths, trackWidth, gap)

    @Test
    fun `well spaced labels are centred on their marks and share one row`() {
        val slots = place(centers = listOf(50f, 150f, 250f), widths = listOf(30f, 30f, 30f))

        assertEquals(listOf(35f, 135f, 235f), slots.map { it.left })
        assertTrue(slots.all { it.row == 0 })
    }

    @Test
    fun `a label that would overlap drops a row`() {
        // Two marks ten pixels apart cannot both print a thirty pixel wide price.
        val slots = place(centers = listOf(100f, 110f), widths = listOf(30f, 30f))

        assertEquals(0, slots[0].row)
        assertEquals(1, slots[1].row)
    }

    @Test
    fun `a third label returns to the first row once there is room`() {
        val slots = place(centers = listOf(100f, 110f, 260f), widths = listOf(30f, 30f, 30f))

        assertEquals(listOf(0, 1, 0), slots.map { it.row })
    }

    @Test
    fun `the outermost prices stay inside the track`() {
        val slots = place(centers = listOf(0f, 300f), widths = listOf(40f, 40f))

        assertEquals(0f, slots[0].left, 0.01f)
        assertEquals(260f, slots[1].left, 0.01f)
    }

    @Test
    fun `placement follows the axis, not the order the marks arrive in`() {
        val ascending = place(centers = listOf(100f, 110f), widths = listOf(30f, 30f))
        val descending = place(centers = listOf(110f, 100f), widths = listOf(30f, 30f))

        assertEquals(ascending[0].row, descending[1].row)
        assertEquals(ascending[1].row, descending[0].row)
    }

    @Test
    fun `a label wider than the track still starts at the left edge`() {
        val slots = place(centers = listOf(150f), widths = listOf(400f))

        assertEquals(0f, slots.single().left, 0.01f)
    }
}
