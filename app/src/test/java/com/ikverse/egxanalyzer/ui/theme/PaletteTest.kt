package com.ikverse.egxanalyzer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 5.0 palette's contrast, pinned in all three modes (light, dark, pure black).
 *
 * Every pair here is one the canvas actually draws: navy text on each tile, white on cobalt, links
 * and figures on the page and on a card. A colour nudged by eye later fails here rather than on the
 * phone.
 */
class PaletteTest {

    private val palettes = mapOf("light" to LightPalette, "dark" to DarkPalette, "black" to BlackPalette)

    private fun contrast(a: Color, b: Color): Double {
        val light = maxOf(a.luminance(), b.luminance()) + 0.05
        val dark = minOf(a.luminance(), b.luminance()) + 0.05
        return (light / dark).toDouble()
    }

    private fun assertAtLeast(min: Double, what: String, a: Color, b: Color) {
        val ratio = contrast(a, b)
        assertTrue("$what is %.2f:1, needs %.1f".format(ratio, min), ratio >= min)
    }

    @Test
    fun `text on every tile fill reads at AA`() {
        palettes.forEach { (mode, p) ->
            listOf("highlight" to p.highlight, "alert" to p.alert, "tile1" to p.tile1, "tile2" to p.tile2, "tile3" to p.tile3)
                .forEach { (name, fill) -> assertAtLeast(4.5, "$mode onTile on $name", p.onTile, fill) }
            assertAtLeast(4.5, "$mode onHero on hero", p.onHero, p.hero)
            assertAtLeast(4.5, "$mode onInk on ink", p.onInk, p.ink)
        }
    }

    @Test
    fun `page text reads at AA on the ground and on a card, and ink at AAA`() {
        palettes.forEach { (mode, p) ->
            listOf("ground" to p.ground, "card" to p.card).forEach { (surface, bg) ->
                assertAtLeast(7.0, "$mode ink on $surface", p.ink, bg)
                assertAtLeast(4.5, "$mode muted on $surface", p.muted, bg)
                assertAtLeast(4.5, "$mode heroInk on $surface", p.heroInk, bg)
                assertAtLeast(4.5, "$mode gain on $surface", p.gain, bg)
                assertAtLeast(4.5, "$mode loss on $surface", p.loss, bg)
            }
            assertAtLeast(4.5, "$mode muted on raised", p.muted, p.raised)
        }
    }

    /** The quiet tab names are 20sp, large text, where 3:1 is the bar. */
    @Test
    fun `faint text clears the large-text bar`() {
        palettes.forEach { (mode, p) -> assertAtLeast(3.0, "$mode faint on ground", p.faint, p.ground) }
    }

    /** Navy ink is the one colour on every tile, so it is one value, not five that could drift. */
    @Test
    fun `every mode draws tile text in the same navy`() {
        assertEquals(1, palettes.values.map { it.onTile }.toSet().size)
    }

    /** The tiles are the same five fills in every mode; only the grounds move. */
    @Test
    fun `the tile fills do not change between modes`() {
        val fills = palettes.values.map { listOf(it.highlight, it.alert, it.tile1, it.tile2, it.tile3) }.toSet()
        assertEquals(1, fills.size)
    }

    /** A gain and a highlight are different greens, so "good news" is never read as "money made". */
    @Test
    fun `gain is never the highlight`() {
        palettes.forEach { (mode, p) -> assertNotEquals("$mode gain is the highlight", p.highlight, p.gain) }
    }

    /** The scheme is built from the palette, so the two cannot disagree about the page. */
    @Test
    fun `the colour schemes are drawn from the palette`() {
        assertEquals(LightPalette.ground, LightColors.background)
        assertEquals(LightPalette.ink, LightColors.onSurface)
        assertEquals(LightPalette.gain, LightColors.tertiary)
        assertEquals(DarkPalette.ground, DarkColors.background)
        assertEquals(DarkPalette.gain, DarkColors.tertiary)
        assertEquals(BlackPalette.ground, BlackColors.background)
        assertEquals(LightPalette.loss, LightExtras.loss)
        assertEquals(DarkPalette.loss, DarkExtras.loss)
    }

    /** Each card slot maps to its tile; cobalt carries white and every other carries navy. */
    @Test
    fun `card hues map to the tile fills`() {
        val p = LightPalette
        assertEquals(p.hero, tileFor(CardHue.BLUE, p))
        assertEquals(p.highlight, tileFor(CardHue.GREEN, p))
        assertEquals(p.tile1, tileFor(CardHue.AMBER, p))
        assertEquals(p.alert, tileFor(CardHue.PINK, p))
        assertEquals(p.tile2, tileFor(CardHue.VIOLET, p))
        assertEquals(p.tile3, tileFor(CardHue.CYAN, p))
        CardHue.entries.forEach { hue ->
            assertAtLeast(4.5, "text on the ${hue.name} tile", onTileFor(hue, p), tileFor(hue, p))
        }
    }
}
