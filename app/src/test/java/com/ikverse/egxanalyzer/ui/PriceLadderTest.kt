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
     * A range is one fact, so both its ends print on one side and on one line.
     *
     * An entry band with its floor under the line and its ceiling over it is two prices that happen
     * to be near each other, which is not what it means - and the same is true of one end sitting a
     * row above the other.
     */
    @Test
    fun `both ends of a range share a row`() {
        val slots = place(
            centers = listOf(100f, 108f, 116f),
            widths = listOf(30f, 30f, 30f),
            groups = listOf(0, 1, 1),
        )

        assertEquals(slots[1].above, slots[2].above)
        assertEquals(slots[1].row, slots[2].row)
        // Side by side, in axis order, clear of each other by the gap.
        assertTrue(slots[2].left >= slots[1].left + 30f + 6f)
    }

    /**
     * The real case: an entry band a fifth of a piastre wide.
     *
     * Centred on their own marks its two ends would overlap, and the one that lost used to be
     * bumped to a second row above - so the ladder carried a third line for a single price with the
     * space beside it empty. Packed side by side the pair fits the row below, beside the stop.
     */
    @Test
    fun `a band too narrow to centre is packed side by side, not stacked`() {
        val slots = place(
            // stop, entry low, entry high, target 1, target 2
            centers = listOf(10f, 60f, 63f, 160f, 290f),
            widths = listOf(34f, 34f, 34f, 34f, 34f),
            groups = listOf(0, 1, 1, 2, 3),
        )

        // The pair still goes over the line - the stop has the room below - but on one row, which
        // is the whole point. Nothing sits on a second row any more.
        assertEquals(listOf(false, true, true, false, false), slots.map { it.above })
        assertEquals(List(5) { 0 }, slots.map { it.row })
        // Side by side: one label width plus the gap between their left edges.
        assertEquals(34f + 6f, slots[2].left - slots[1].left, 0.01f)
        // And the pair straddles the band rather than each end sitting on its own mark.
        assertTrue(slots[1].left < 61.5f && slots[2].left > 61.5f)
    }

    /** A group of one is placed exactly where centring it on its mark would. */
    @Test
    fun `a lone label is untouched by the packing`() {
        val grouped = place(centers = listOf(150f), widths = listOf(30f), groups = listOf(7))
        val ungrouped = place(centers = listOf(150f), widths = listOf(30f))

        assertEquals(135f, grouped.single().left, 0.01f)
        assertEquals(ungrouped.single().left, grouped.single().left, 0.01f)
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

    // 5.1: the compact form, the travelled stripe and the zones between the levels.

    @Test
    fun `a compact ladder prints no prices`() {
        assertTrue(ladderLabelTexts(listOf(9.5, 10.0, 11.0, 12.0), compact = true).isEmpty())
        assertEquals(4, ladderLabelTexts(listOf(9.5, 10.0, 11.0, 12.0), compact = false).size)
    }

    @Test
    fun `the travelled stripe is held inside the axis`() {
        val clamped = clampToAxis(8.0..14.0, low = 9.5, high = 12.0)
        assertEquals(9.5, clamped.start, 0.0)
        assertEquals(12.0, clamped.endInclusive, 0.0)
        val inside = clampToAxis(10.2..11.4, low = 9.5, high = 12.0)
        assertEquals(10.2, inside.start, 0.0)
        assertEquals(11.4, inside.endInclusive, 0.0)
    }

    @Test
    fun `a full call has three zones meeting the entry band at its own ends`() {
        val zones = ladderZones(stopLoss = 9.0, entryLow = 10.0, entryHigh = 10.4, target1 = 11.5, target2 = 13.0)
        assertEquals(
            listOf(
                LadderZone(9.0, 10.0, ZoneKind.LOSS),
                LadderZone(10.4, 11.5, ZoneKind.NEAR),
                LadderZone(11.5, 13.0, ZoneKind.FAR),
            ),
            zones,
        )
    }

    @Test
    fun `a single entry price and one target still lay out`() {
        val zones = ladderZones(stopLoss = 9.0, entryLow = 10.0, entryHigh = 10.0, target1 = null, target2 = 12.0)
        assertEquals(listOf(LadderZone(9.0, 10.0, ZoneKind.LOSS), LadderZone(10.0, 12.0, ZoneKind.NEAR)), zones)
    }

    @Test
    fun `a call with no stop has no loss zone, and one with no entry has none at all`() {
        val noStop = ladderZones(stopLoss = null, entryLow = 10.0, entryHigh = 10.0, target1 = 11.0, target2 = 12.0)
        assertTrue(noStop.none { it.kind == ZoneKind.LOSS })
        assertEquals(2, noStop.size)
        assertTrue(ladderZones(9.0, null, null, 11.0, 12.0).isEmpty())
    }

    @Test
    fun `an entry label goes before the entry price and nowhere else`() {
        val texts = ladderLabelTexts(listOf(9.0, 10.0, 10.4, 12.0), compact = false, entryIndex = 1, entryLabel = "You paid")
        assertTrue(texts[1].startsWith("You paid "))
        assertTrue(texts.filterIndexed { i, _ -> i != 1 }.none { it.contains("You paid") })
        assertTrue(ladderLabelTexts(listOf(9.0, 10.0), compact = true, entryIndex = 1, entryLabel = "You paid").isEmpty())
    }
}
