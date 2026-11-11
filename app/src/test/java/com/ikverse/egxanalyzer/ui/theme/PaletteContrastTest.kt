package com.ikverse.egxanalyzer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.tileFill
import com.ikverse.egxanalyzer.ui.kit.tileSignalsFor
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every text colour against every surface it is documented to be read on, in all three palettes.
 *
 * Written for 5.1 after a reading of the dark theme found a lime figure at 1.03:1 on the ink tile.
 * Reading found a dozen; this is the sweep for the ones reading did not. Each test collects every
 * failure before asserting, so one run names all of them rather than the first.
 */
class PaletteContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val light = maxOf(a.luminance(), b.luminance()) + 0.05
        val dark = minOf(a.luminance(), b.luminance()) + 0.05
        return (light / dark).toDouble()
    }

    private val palettes = listOf("Light" to LightPalette, "Dark" to DarkPalette, "Black" to BlackPalette)

    private fun hex(c: Color) = "#%06X".format(c.value.toLong().shr(32).toInt() and 0xFFFFFF)

    private class Failures {
        val lines = mutableListOf<String>()
        fun check(where: String, fg: Color, bg: Color, min: Double, ratio: Double) {
            if (ratio < min) lines += "%s: %.2f:1, needs %.1f".format(where, ratio, min)
        }
        fun assertNone() = assertTrue("\n" + lines.joinToString("\n"), lines.isEmpty())
    }

    @Test
    fun `text roles hold on the surfaces they are read on`() {
        val f = Failures()
        palettes.forEach { (name, p) ->
            fun need(role: String, fg: Color, surface: String, bg: Color, min: Double = 4.5) =
                f.check("$name $role on $surface (${hex(fg)} on ${hex(bg)})", fg, bg, min, contrast(fg, bg))
            listOf("ground" to p.ground, "card" to p.card, "raised" to p.raised).forEach { (s, bg) ->
                need("ink", p.ink, s, bg)
            }
            listOf("ground" to p.ground, "card" to p.card).forEach { (s, bg) ->
                need("muted", p.muted, s, bg)
                need("heroInk", p.heroInk, s, bg)
                need("gain", p.gain, s, bg)
                need("loss", p.loss, s, bg)
                // Large text only, by its own KDoc.
                need("faint", p.faint, s, bg, min = 3.0)
            }
            need("onInk", p.onInk, "ink", p.ink)
            need("onHero", p.onHero, "hero", p.hero)
            listOf(
                "highlight" to p.highlight, "alert" to p.alert,
                "tile1" to p.tile1, "tile2" to p.tile2, "tile3" to p.tile3,
            ).forEach { (s, bg) -> need("onTile", p.onTile, s, bg) }
            // What a kit Tile is actually filled with, which at night is not the accent above.
            listOf(
                "tileHighlight" to p.tileHighlight, "tileAlert" to p.tileAlert,
                "tileOne" to p.tileOne, "tileTwo" to p.tileTwo, "tileThree" to p.tileThree,
                "tileBookFlat" to p.tileBookFlat,
            ).forEach { (s, bg) -> need("onTile", p.onTile, s, bg) }
            need("onTileInk", p.onTileInk, "tileInk", p.tileInk)
            need("onHero", p.onHero, "tileHero", p.tileHero)
        }
        f.assertNone()
    }

    /** The fill each tone draws, as `tileColors` gives it. */
    /**
     * The stock sheet's chart tile is not one of the kit's tones: navy in the light theme and the
     * raised card at night, with a lime and a coral that have to read on both.
     */
    @Test
    fun `the chart tile's text holds on the chart tile`() {
        val f = Failures()
        palettes.forEach { (name, p) ->
            val c = com.ikverse.egxanalyzer.ui.chartTileColors(p)
            fun need(role: String, fg: Color, surface: String, bg: Color) =
                f.check("$name chart $role on $surface (${hex(fg)} on ${hex(bg)})", fg, bg, 4.5, contrast(fg, bg))
            need("text", c.text, "tile", c.surface)
            need("captions", c.soft, "tile", c.surface)
            need("target", c.target, "tile", c.surface)
            need("stop", c.stopText, "tile", c.surface)
            need("text", c.text, "range track", c.track)
            need("now", c.nowText, "its tag", c.nowFill)
            need("chosen range", c.onChosen, "its segment", c.chosen)
            // The Levels button, filled: the tile's own colour as text on the tile's text colour.
            need("levels", c.surface, "its button", c.text)
        }
        f.assertNone()
    }

    private fun fill(p: AppPalette, tone: TileTone) = tileFill(p, tone)

    /** The dark tiles are mid-tone, not the light theme's fills: the glare reported on 2026-11-12. */
    @Test
    fun `dark tiles are dimmer than the light theme's`() {
        val f = Failures()
        listOf("Dark" to DarkPalette, "Black" to BlackPalette).forEach { (name, p) ->
            // The card and ink tiles swap with the theme, and cobalt is already a dark fill.
            TileTone.entries.filter { it !in setOf(TileTone.CARD, TileTone.INK, TileTone.HERO) }.forEach { tone ->
                val night = fill(p, tone).luminance()
                val day = fill(LightPalette, tone).luminance()
                if (night > day) f.lines += "$name $tone is brighter at night (${hex(fill(p, tone))})"
            }
            if (fill(p, TileTone.INK).luminance() >= p.ink.luminance()) f.lines += "$name ink tile is as bright as the page's text"
        }
        f.assertNone()
    }

    @Test
    fun `tile signals hold on their own tile`() {
        val f = Failures()
        palettes.forEach { (name, p) ->
            TileTone.entries.forEach { tone ->
                val bg = fill(p, tone)
                val s = tileSignalsFor(p, tone)
                listOf("gain" to s.gain, "loss" to s.loss, "accent" to s.accent).forEach { (role, fg) ->
                    f.check("$name $role on $tone (${hex(fg)} on ${hex(bg)})", fg, bg, 4.5, contrast(fg, bg))
                }
            }
        }
        f.assertNone()
    }
}
