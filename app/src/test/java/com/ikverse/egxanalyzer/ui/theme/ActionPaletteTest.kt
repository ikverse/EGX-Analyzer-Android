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
     * Nothing the action is drawn with is solid.
     *
     * It never leaves the screen, so a solid anything reads as a slab parked on a page still being
     * scrolled - the ground carries 0.84 for that reason and the edge 0.74. The exact figures are
     * not asserted, because a test repeating a constant back proves nothing; what is asserted is
     * that neither has quietly become opaque, which is what happens when a stop is copied from a
     * palette that was not built to float.
     */
    @Test
    fun `neither the action's fill nor its edge is solid`() {
        themes.forEach { (name, extras) ->
            (extras.actionFill + extras.actionLine).forEach { stop ->
                assertTrue("$name has a solid stop", stop.alpha < 1f)
            }
        }
    }

    /**
     * One alpha across a ramp.
     *
     * A gradient whose stops differ in opacity fades out along its length as well as changing hue,
     * which reads as a gradient that has gone wrong rather than one that was chosen - and it is an
     * easy slip, because the hues are edited one at a time and the alpha rides along in the same
     * literal.
     */
    @Test
    fun `every stop in a ramp carries the same alpha`() {
        themes.forEach { (name, extras) ->
            assertEquals("$name fill fades along its length", 1, extras.actionFill.map(Color::alpha).toSet().size)
            assertEquals("$name edge fades along its length", 1, extras.actionLine.map(Color::alpha).toSet().size)
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

    /**
     * The mark's aurora is solid, where the action's is not.
     *
     * The action's stops are three soft circles over a ground and are kept low deliberately. The
     * mark's are painted through a 24dp glyph with `SrcIn`, so there is nothing behind them to show
     * through and an alpha carried over from the action is simply a dimmer mark - which is the exact
     * mistake this pins, because copying `actionAurora` is the obvious way to reach for these hues.
     */
    @Test
    fun `the mark's aurora is opaque where the action's is not`() {
        themes.forEach { (name, extras) ->
            extras.markAurora.forEach { stop ->
                assertTrue("$name draws the mark with a transparent stop", stop.alpha == 1f)
            }
            assertTrue(
                "$name draws the action's aurora solid",
                extras.actionAurora.all { it.alpha < 1f },
            )
        }
    }

    /**
     * The mark carries the aurora's own hues, in the aurora's own order.
     *
     * The palette is fixed because every family in it means something, and the mark is the app's own
     * name - so it may wear the app's own voice and nothing else. Compared on hue alone, since the
     * alphas are the one thing the two are meant to disagree about.
     */
    @Test
    fun `the mark and the action draw the same aurora`() {
        themes.forEach { (name, extras) ->
            assertEquals(
                "$name gives the mark its own hues",
                extras.actionAurora.map { it.copy(alpha = 1f).value },
                extras.markAurora.map(Color::value),
            )
        }
    }
}
