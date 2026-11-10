package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Book posture's two columns: the gap between them falls on the crease, wherever the row starts. */
class HingeColumnsTest {

    private val gap = 30f
    private val min = 420f

    @Test
    fun `a row inset by the page margins still splits on the hinge`() {
        // A 1968px-wide inner screen folded at its middle, the row inset 42px either side.
        val (left, right) = hingeColumns(rowStart = 42f, rowWidth = 1884f, hingeStart = 984f, hingeEnd = 984f, minGap = gap, minColumn = min)!!
        // The left column ends half a gap short of the crease and the right one starts half a gap past it.
        assertEquals(984f - gap / 2 - 42f, left, 0.01f)
        assertEquals(42f + 1884f - (984f + gap / 2), right, 0.01f)
    }

    @Test
    fun `a row that starts off centre gives its columns unequal widths`() {
        val (left, right) = hingeColumns(rowStart = 300f, rowWidth = 1600f, hingeStart = 984f, hingeEnd = 984f, minGap = gap, minColumn = min)!!
        assertEquals(669f, left, 0.01f)
        assertEquals(901f, right, 0.01f)
    }

    @Test
    fun `a hinge with width of its own is the gap`() {
        val (left, right) = hingeColumns(rowStart = 0f, rowWidth = 2000f, hingeStart = 960f, hingeEnd = 1040f, minGap = gap, minColumn = min)!!
        assertEquals(960f, left, 0.01f)
        assertEquals(960f, right, 0.01f)
    }

    @Test
    fun `a hinge outside the row, or at its very edge, gives no split`() {
        assertNull(hingeColumns(rowStart = 0f, rowWidth = 900f, hingeStart = 984f, hingeEnd = 984f, minGap = gap, minColumn = min))
        assertNull(hingeColumns(rowStart = 0f, rowWidth = 2000f, hingeStart = 0f, hingeEnd = 0f, minGap = gap, minColumn = min))
        assertNull(hingeColumns(rowStart = 0f, rowWidth = 0f, hingeStart = 984f, hingeEnd = 984f, minGap = gap, minColumn = min))
    }
}
