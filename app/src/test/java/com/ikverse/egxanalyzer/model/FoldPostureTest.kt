package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four ways the Fold 7 is held, read from the numbers the platform reports.
 *
 * Widths are the real ones: the cover screen at 411dp, the inner screen at 750dp. The inner screen
 * is 1968x2184 px, so the hinge sits around the middle of either axis.
 */
class FoldPostureTest {

    @Test
    fun `the cover screen with no hinge is the cover layout`() {
        assertEquals(FoldPosture.Cover, foldPosture(411f, hinge = null))
    }

    @Test
    fun `the inner screen lying flat is the unfolded layout`() {
        val flat = HingeReport(halfOpened = false, runsAcross = false, start = 984f, end = 984f, windowExtent = 1968f)
        assertEquals(FoldPosture.Unfolded, foldPosture(750f, flat))
    }

    @Test
    fun `a wide window with no hinge, the tablet, is unfolded`() {
        assertEquals(FoldPosture.Unfolded, foldPosture(818f, hinge = null))
    }

    @Test
    fun `half open with the hinge across the screen is tabletop, split where the hinge is`() {
        val across = HingeReport(halfOpened = true, runsAcross = true, start = 1082f, end = 1102f, windowExtent = 2184f)
        val posture = foldPosture(750f, across)
        assertTrue(posture is FoldPosture.Tabletop)
        posture as FoldPosture.Tabletop
        assertEquals(1082f / 2184f, posture.hingeStartFraction, 0.0001f)
        assertEquals(1102f / 2184f, posture.hingeEndFraction, 0.0001f)
    }

    @Test
    fun `half open with the hinge top to bottom is book posture`() {
        val down = HingeReport(halfOpened = true, runsAcross = false, start = 984f, end = 984f, windowExtent = 1968f)
        val posture = foldPosture(750f, down)
        assertEquals(FoldPosture.Book(0.5f, 0.5f), posture)
    }

    /** The posture comes from the hinge, not the width: tabletop is tabletop at any width. */
    @Test
    fun `a half-open hinge wins over a narrow width`() {
        val across = HingeReport(halfOpened = true, runsAcross = true, start = 500f, end = 520f, windowExtent = 1000f)
        assertTrue(foldPosture(540f, across) is FoldPosture.Tabletop)
    }

    @Test
    fun `sheets open as a panel unfolded and in book posture, and from the bottom otherwise`() {
        assertTrue(FoldPosture.Unfolded.opensSheetsAsPanel)
        assertTrue(FoldPosture.Book(0.5f, 0.5f).opensSheetsAsPanel)
        assertFalse(FoldPosture.Cover.opensSheetsAsPanel)
        assertFalse(FoldPosture.Tabletop(0.5f, 0.51f).opensSheetsAsPanel)
    }

    /** In book posture the panel starts past the hinge, so nothing it shows sits across the crease. */
    @Test
    fun `in book posture the panel starts where the hinge ends`() {
        assertEquals(0.52f, FoldPosture.Book(0.49f, 0.52f).panelStartFraction, 0.0001f)
        assertEquals(0.5f, FoldPosture.Unfolded.panelStartFraction, 0.0001f)
    }

    @Test
    fun `nonsense bounds are clamped into the window`() {
        val wild = HingeReport(halfOpened = true, runsAcross = true, start = -40f, end = 5000f, windowExtent = 2184f)
        val posture = foldPosture(750f, wild) as FoldPosture.Tabletop
        assertEquals(0f, posture.hingeStartFraction, 0f)
        assertEquals(1f, posture.hingeEndFraction, 0f)
    }

    @Test
    fun `a report with no window extent is ignored rather than divided by`() {
        val empty = HingeReport(halfOpened = true, runsAcross = true, start = 0f, end = 0f, windowExtent = 0f)
        assertEquals(FoldPosture.Unfolded, foldPosture(750f, empty))
    }
}
