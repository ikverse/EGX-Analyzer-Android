package com.ikverse.egxanalyzer.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Analyze action's fill and its edge answer opposite requirements, and must not be given the
 * same answer.
 *
 * Neither of these is a test that the colours are *nice*, which no test can be. They pin the two
 * properties that decide whether the edge is an edge at all - and both are mistakes that look
 * completely fine in the source and only show up on a device, on one theme, with a page scrolling
 * underneath.
 */
class ActionPaletteTest {

    private val themes = mapOf("dark" to DarkExtras, "light" to LightExtras)

    /**
     * The fill lets the page through; the edge does not.
     *
     * [ExtraColors.actionFill] carries 0.84 in the colour itself because the action never leaves the
     * screen, and a solid one reads as a slab parked on a page still being scrolled. An edge doing
     * the same stops holding the shape against whatever passes under it, which is the one job it
     * has - so the two figures are deliberately opposite, and the pair is asserted together because
     * either alone reads as an arbitrary number.
     */
    @Test
    fun `the action's fill is see-through and its edge is not`() {
        themes.forEach { (name, extras) ->
            extras.actionFill.forEach { stop ->
                assertTrue("$name fill stop is opaque", stop.alpha < 1f)
            }
            extras.actionLine.forEach { stop ->
                assertEquals("$name edge stop is see-through", 1f, stop.alpha, 0f)
            }
        }
    }

    /**
     * The edge is not the fill.
     *
     * The fill sits *inside* this line, so a line in the fill's colours is a line against itself and
     * disappears - and reaching for `actionFill` is the obvious thing to do when adding a gradient
     * edge, because it is right there and already the right family. `aiLine` exists beside `aiFill`
     * for the same reason and the comment there says so.
     */
    @Test
    fun `the edge is drawn from its own stops rather than the fill's`() {
        themes.forEach { (name, extras) ->
            assertNotEquals("$name draws its edge with the fill", extras.actionFill, extras.actionLine)
            assertTrue(
                "$name shares a stop between fill and edge",
                extras.actionFill.map(Color::value).intersect(extras.actionLine.map(Color::value).toSet()).isEmpty(),
            )
        }
    }

    /**
     * A gradient with three stops on one theme and two on the other is two different gradients.
     *
     * The hues invert between the themes and the shape of the ramp must not, or switching theme
     * changes where the colour turns rather than only which colour it is.
     */
    @Test
    fun `both themes describe the edge with the same ramp`() {
        assertEquals(DarkExtras.actionLine.size, LightExtras.actionLine.size)
        assertEquals(DarkExtras.actionFill.size, LightExtras.actionFill.size)
    }
}
