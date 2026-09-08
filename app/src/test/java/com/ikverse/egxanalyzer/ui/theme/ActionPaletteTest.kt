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
 * Neither of these is a test that the colours are *nice*, which no test can be. They pin the
 * properties that decide whether the edge is an edge at all - and every one is a mistake that looks
 * completely fine in the source and only shows up on a device, on one theme, with a page scrolling
 * underneath.
 *
 * **Every case sweeps all five accents in both themes.** These used to read two constants, which was
 * the whole palette when the action was teal on every page. Now the ramps are derived per page, so a
 * property that held for the one hue somebody looked at is exactly the kind that quietly fails on
 * the other four.
 */
class ActionPaletteTest {

    private val accents: Map<String, PageAccent> = buildMap {
        AccentKey.entries.forEach { key ->
            put("dark ${key.name.lowercase()}", accentFor(key, dark = true))
            put("light ${key.name.lowercase()}", accentFor(key, dark = false))
        }
    }

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
        accents.forEach { (name, accent) ->
            (accent.actionFill + accent.actionLine).forEach { stop ->
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
        accents.forEach { (name, accent) ->
            assertEquals("$name fill fades along its length", 1, accent.actionFill.map(Color::alpha).toSet().size)
            assertEquals("$name edge fades along its length", 1, accent.actionLine.map(Color::alpha).toSet().size)
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
        accents.forEach { (name, accent) ->
            assertNotEquals("$name draws its edge with the fill", accent.actionFill, accent.actionLine)
            assertTrue(
                "$name shares a stop between fill and edge",
                accent.actionFill.map(Color::value).intersect(accent.actionLine.map(Color::value).toSet()).isEmpty(),
            )
        }
    }

    /**
     * A gradient with three stops on one theme and two on the other is two different gradients.
     *
     * The hues invert between the themes and the shape of the ramp must not, or switching theme
     * changes where the colour turns rather than only which colour it is. The same argument now
     * applies across pages: a Results button that turned colour in a different place from the
     * Analyze one would be two buttons.
     */
    @Test
    fun `every page and both themes describe a ramp with the same shape`() {
        val fills = accents.values.map { it.actionFill.size }.toSet()
        val edges = accents.values.map { it.actionLine.size }.toSet()
        val auroras = accents.values.map { it.actionAurora.size }.toSet()
        val pills = accents.values.map { it.aiFill.size }.toSet()
        assertEquals("the fill changes shape between pages or themes", 1, fills.size)
        assertEquals("the edge changes shape between pages or themes", 1, edges.size)
        assertEquals("the aurora changes shape between pages or themes", 1, auroras.size)
        assertEquals("the pill changes shape between pages or themes", 1, pills.size)
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
        accents.forEach { (name, accent) ->
            accent.markAurora.forEach { stop ->
                assertTrue("$name draws the mark with a transparent stop", stop.alpha == 1f)
            }
            assertTrue(
                "$name draws the action's aurora solid",
                accent.actionAurora.all { it.alpha < 1f },
            )
        }
    }

    /**
     * The mark carries the aurora's own hues, in the aurora's own order.
     *
     * The mark is the app's own name, so it may wear the page's own voice and nothing else. Compared
     * on hue alone, since the alphas are the one thing the two are meant to disagree about.
     */
    @Test
    fun `the mark and the action draw the same aurora`() {
        accents.forEach { (name, accent) ->
            assertEquals(
                "$name gives the mark its own hues",
                accent.actionAurora.map { it.copy(alpha = 1f).value },
                accent.markAurora.map(Color::value),
            )
        }
    }

    /**
     * Five pages, five hues.
     *
     * The whole point of a page accent is that it identifies the page, so two destinations sharing
     * one is the feature silently not working - and it is an easy slip, because the seeds are a
     * table and a row is copied to start the next one.
     */
    @Test
    fun `no two pages speak in the same hue`() {
        listOf(true, false).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            val bases = AccentKey.entries.map { accentFor(it, dark).base.value }
            assertEquals("$theme reuses a hue between pages", AccentKey.entries.size, bases.toSet().size)
            val inks = AccentKey.entries.map { accentFor(it, dark).ink.value }
            assertEquals("$theme reuses an ink between pages", AccentKey.entries.size, inks.toSet().size)
        }
    }

    /**
     * The Ask AI pill starts in violet everywhere and ends where it was pressed.
     *
     * Both halves matter and they pull in opposite directions. The first stop is the model
     * announcing itself, which is the same announcement on every page; the last is the page it was
     * asked from, which is what stops five pages' pills being one pill. A change that made the whole
     * ramp follow the accent would read as five unrelated buttons, and one that pinned the whole
     * ramp would undo the feature - this fails on either.
     */
    @Test
    fun `the pill begins in violet on every page and ends in the page's own hue`() {
        listOf(true, false).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            val starts = AccentKey.entries.map { accentFor(it, dark).aiFill.first().value }
            assertEquals("$theme starts the pill somewhere other than violet", 1, starts.toSet().size)
            val ends = AccentKey.entries.map { accentFor(it, dark).aiFill.last().value }
            assertEquals("$theme ends two pages' pills in one hue", AccentKey.entries.size, ends.toSet().size)
        }
    }

    /**
     * A page accent is chrome, so it must never be handed a signal's hue.
     *
     * This is the rule the whole scheme rests on: green means a target, red a stop, blue a price the
     * market reached and amber a window that closed, on all five pages. An accent that *equalled* one
     * of them would put a page's identity and a figure's meaning in one colour, which is the reading
     * every hue here was chosen to make impossible.
     */
    @Test
    fun `no page accent is one of the signal colours`() {
        listOf(true to DarkExtras, false to LightExtras).forEach { (dark, extras) ->
            val theme = if (dark) "dark" else "light"
            val scheme = if (dark) darkSignals() else lightSignals()
            val signals = (scheme + listOf(extras.market, extras.expired)).map(Color::value).toSet()
            AccentKey.entries.forEach { key ->
                val accent = accentFor(key, dark)
                assertTrue(
                    "$theme ${key.name.lowercase()} takes a signal's own hue",
                    accent.base.value !in signals && accent.ink.value !in signals,
                )
            }
        }
    }

    /** The two signal roles that live on the scheme rather than in [ExtraColors]. */
    private fun darkSignals() = listOf(Color(0xFF2FE39B), Color(0xFFFF6B7A))

    private fun lightSignals() = listOf(Color(0xFF00804F), Color(0xFFC42D3F))
}
