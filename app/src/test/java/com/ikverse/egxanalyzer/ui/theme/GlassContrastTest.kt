package com.ikverse.egxanalyzer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The app's surfaces are see-through, and this is what stops them being unreadable.**
 *
 * Every card, tile and field reads one of the three container roles, and those roles carry alpha -
 * see the block above `DarkColors`. That means a figure on the smallest tile in the app is not
 * standing on the colour the palette names: it is standing on that colour composited over its
 * parent, over its parent's parent, and finally over a page with the accent lights on it. Nothing
 * about the numbers in `Theme.kt` says what comes out of that, and the difference between a page
 * that reads and one that does not is a few hundredths of alpha nobody can judge by eye.
 *
 * So the composites are computed here, at the **worst point of the page** - the brightest the
 * ground is ever lit - for every accent and both themes, and the contrast under them is pinned.
 *
 * It is a guard and not a description. It failed once already in the only way that matters: the
 * ground shipped at 0.20, a page of cards looked handsome in a screenshot, and the secondary type
 * on it had lost a third of its contrast. The numbers below are what that cost, turned into
 * something a build can refuse.
 */
class GlassContrastTest {

    /** WCAG's own ratio, on colours that are already opaque by the time they reach it. */
    private fun contrast(foreground: Color, background: Color): Double {
        val light = maxOf(foreground.luminance(), background.luminance()) + 0.05
        val dark = minOf(foreground.luminance(), background.luminance()) + 0.05
        return (light / dark).toDouble()
    }

    /**
     * The page as it is under the lights, which is the ground every surface is finally composited
     * over.
     *
     * Both lights at once and at full strength: they overlap nowhere on a real window, so this is
     * darker-than-worst rather than typical, which is the direction a guard should err in.
     */
    private fun litGround(accent: PageAccent, scheme: SchemeColors): Color {
        val alpha = groundAlpha(accent.base, scheme.background, scheme.lift)
        val lit = accent.base.copy(alpha = alpha).compositeOver(scheme.background)
        return accent.base.copy(alpha = alpha).compositeOver(lit)
    }

    /** A surface on the ground, and then whatever is nested on that, in order. */
    private fun stack(ground: Color, levels: List<Color>): Color =
        levels.fold(ground) { under, level -> level.compositeOver(under) }

    private data class SchemeColors(
        val background: Color,
        /** How far the ground may be lifted off the page, which is what the alpha is solved for. */
        val lift: Float,
        val section: Color,
        val card: Color,
        val tile: Color,
        val onSurfaceVariant: Color,
        val onSurface: Color,
    )

    private val schemes = mapOf(
        "dark" to SchemeColors(
            background = Color(0xFF0A0E18),
            lift = GroundLift,
            // The scheme's own figures, read rather than repeated: a guard restating the numbers it
            // guards passes whatever those numbers become.
            section = Color(0xFF141C2C).copy(alpha = GlassSection),
            card = Color(0xFF182030).copy(alpha = GlassCard),
            tile = Color(0xFF243046).copy(alpha = GlassTile),
            onSurfaceVariant = Color(0xFF9AA7BD),
            onSurface = Color(0xFFE8ECF4),
        ),
        "light" to SchemeColors(
            background = Color(0xFFF5F7FE),
            lift = LightGroundLift,
            section = Color(0xFFFFFFFF).copy(alpha = LightGlassSection),
            card = Color(0xFFF4F6FE).copy(alpha = LightGlassCard),
            tile = Color(0xFFE4EAF8).copy(alpha = LightGlassTile),
            onSurfaceVariant = Color(0xFF55617A),
            onSurface = Color(0xFF121926),
        ),
    )

    private fun eachSurface(body: (name: String, surface: Color, scheme: SchemeColors) -> Unit) {
        schemes.forEach { (theme, scheme) ->
            AccentKey.entries.forEach { key ->
                val accent = accentFor(key, dark = theme == "dark")
                val ground = litGround(accent, scheme)
                val levels = listOf(
                    "section" to stack(ground, listOf(scheme.section)),
                    "card" to stack(ground, listOf(scheme.section, scheme.card)),
                    "tile" to stack(ground, listOf(scheme.section, scheme.card, scheme.tile)),
                )
                levels.forEach { (level, surface) ->
                    body("$theme ${key.name.lowercase()} $level", surface, scheme)
                }
            }
        }
    }

    /**
     * Body text holds AA on every surface, at every depth, over the lit ground.
     *
     * `onSurfaceVariant` and not `onSurface`: the secondary role is the one that runs out first,
     * it carries most of the small type in the app - labels, dates, the line under a figure - and
     * it is the role a see-through surface costs the most.
     */
    @Test
    fun `secondary text holds AA on every level of glass`() {
        eachSurface { name, surface, scheme ->
            val ratio = contrast(scheme.onSurfaceVariant, surface)
            assertTrue("$name drops secondary text to %.2f:1".format(ratio), ratio >= 4.5)
        }
    }

    /** What a figure is read against, which may never be worse than the type beside it. */
    @Test
    fun `primary text holds AAA on every level of glass`() {
        eachSurface { name, surface, scheme ->
            val ratio = contrast(scheme.onSurface, surface)
            assertTrue("$name drops primary text to %.2f:1".format(ratio), ratio >= 7.0)
        }
    }

    /**
     * The levels stay apart.
     *
     * The reason the alphas rise with depth rather than falling: a nested surface that let more
     * through than its parent drifts toward its parent's colour, and three levels of that is a tile
     * sitting on the page it looks like it is three cards above. The step is asserted in luminance
     * rather than in RGB so it means the same thing on both themes.
     */
    @Test
    fun `each level separates from the one above it`() {
        schemes.forEach { (theme, scheme) ->
            AccentKey.entries.forEach { key ->
                val ground = litGround(accentFor(key, dark = theme == "dark"), scheme)
                val section = stack(ground, listOf(scheme.section))
                val card = stack(ground, listOf(scheme.section, scheme.card))
                val tile = stack(ground, listOf(scheme.section, scheme.card, scheme.tile))
                listOf(
                    "section over page" to (section.luminance() - ground.luminance()),
                    "card over section" to (card.luminance() - section.luminance()),
                    "tile over card" to (tile.luminance() - card.luminance()),
                ).forEach { (step, difference) ->
                    val name = "$theme ${key.name.lowercase()} $step"
                    // Light theme steps downward - its containers are darker than its page - so it
                    // is the size of the step that is pinned and not its direction.
                    assertTrue(
                        "$name does not separate: ${"%.4f".format(difference)}",
                        kotlin.math.abs(difference) >= MinimumSeparation,
                    )
                }
            }
        }
    }
}

/**
 * How far apart two surfaces have to be before a reader can see that one is on the other.
 *
 * In luminance rather than in RGB, so it means the same thing on both themes - the light theme's
 * containers are darker than its page and the dark theme's are lighter, and a rule written in
 * channel values would be two rules. The figure is a floor and not a target: at the values in the
 * theme the narrowest step is a shade over it and the rest are several times it.
 */
private const val MinimumSeparation = 0.0015
