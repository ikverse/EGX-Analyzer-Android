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
        groups: List<Int> = centers.indices.toList(),
    ) = layoutPriceLabels(centers, widths, trackWidth, gap, groups)

    @Test
    fun `well spaced labels are centred on their marks and share one row`() {
        val slots = place(centers = listOf(50f, 150f, 250f), widths = listOf(30f, 30f, 30f))

        assertEquals(listOf(35f, 135f, 235f), slots.map { it.left })
        assertTrue(slots.all { it.row == 0 })
        // The ordinary ladder is one line under the track, exactly as it always was.
        assertTrue(slots.none { it.above })
    }

    /**
     * The second choice is the other side of the line, not a second line under it.
     *
     * A label pushed down a row sits further from the mark it belongs to than from its neighbour's,
     * which is the wrong thing to read at a glance.
     */
    @Test
    fun `a label that would overlap moves above the line`() {
        // Two marks ten pixels apart cannot both print a thirty pixel wide price.
        val slots = place(centers = listOf(100f, 110f), widths = listOf(30f, 30f))

        assertEquals(0, slots[0].row)
        assertTrue(!slots[0].above)
        assertEquals(0, slots[1].row)
        assertTrue(slots[1].above)
    }

    @Test
    fun `a third label returns below once there is room`() {
        val slots = place(centers = listOf(100f, 110f, 260f), widths = listOf(30f, 30f, 30f))

        assertEquals(listOf(0, 0, 0), slots.map { it.row })
        assertEquals(listOf(false, true, false), slots.map { it.above })
    }

    /**
     * There is exactly one row under the line, and everything else stacks over it.
     *
     * A label pushed to a second row below sits further from the mark it belongs to than from its
     * neighbour's, which is the wrong price to read it against.
     */
    @Test
    fun `crowded labels stack above rather than under`() {
        val slots = place(
            centers = listOf(100f, 105f, 110f, 115f),
            widths = listOf(30f, 30f, 30f, 30f),
        )

        assertEquals(listOf(false, true, true, true), slots.map { it.above })
        assertEquals(listOf(0, 0, 1, 2), slots.map { it.row })
        assertTrue(slots.none { it.row > 0 && !it.above })
    }

    /**
     * A range is one fact, so both its ends print on one side.
     *
     * An entry band with its floor under the line and its ceiling over it is two prices that happen
     * to be near each other, which is not what it means.
     */
    @Test
    fun `both ends of a range move together`() {
        val slots = place(
            centers = listOf(100f, 108f, 116f),
            widths = listOf(30f, 30f, 30f),
            groups = listOf(0, 1, 1),
        )

        // The stop took the row below; the pair could not both follow it, so both went above.
        assertTrue(!slots[0].above)
        assertTrue(slots[1].above)
        assertTrue(slots[2].above)
    }

    /**
     * The real case: an entry band a fifth of a piastre wide.
     *
     * Its two ends can never sit side by side, so the row below keeps the stop and the targets and
     * the band takes two rows over the line - rather than one end below and the other stranded.
     */
    @Test
    fun `an entry band too narrow to fit takes both ends above`() {
        val slots = place(
            // stop, entry low, entry high, target 1, target 2
            centers = listOf(10f, 60f, 63f, 160f, 290f),
            widths = listOf(34f, 34f, 34f, 34f, 34f),
            groups = listOf(0, 1, 1, 2, 3),
        )

        assertEquals(listOf(false, true, true, false, false), slots.map { it.above })
        assertEquals(listOf(0, 0, 1, 0, 0), slots.map { it.row })
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

        assertEquals(ascending[0].above, descending[1].above)
        assertEquals(ascending[1].above, descending[0].above)
    }

    @Test
    fun `a label wider than the track still starts at the left edge`() {
        val slots = place(centers = listOf(150f), widths = listOf(400f))

        assertEquals(0f, slots.single().left, 0.01f)
    }
}
