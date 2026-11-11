package com.ikverse.egxanalyzer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ikverse.egxanalyzer.ui.kit.TileTone
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
        }
        f.assertNone()
    }

    /** The fill each tone draws, as `tileColors` gives it. */
    private fun fill(p: AppPalette, tone: TileTone) = when (tone) {
        TileTone.CARD -> p.card
        TileTone.INK -> p.ink
        TileTone.HERO -> p.hero
        TileTone.HIGHLIGHT -> p.highlight
        TileTone.ALERT -> p.alert
        TileTone.TILE1 -> p.tile1
        TileTone.TILE2 -> p.tile2
        TileTone.TILE3 -> p.tile3
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
