package com.ikverse.egxanalyzer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * How strongly a page's ground is lit, in **luminance** rather than in alpha.
 *
 * One alpha for all five hues is the obvious way to write this and it is wrong, because the hues
 * are nowhere near equally bright: Analyze's cyan is `#35D7F2` and the Portfolio's indigo is
 * `#5C7CFA`, so the same 9% of each lifts the page about twice as far on one page as on the other.
 * At the figure that made indigo visible, cyan lifted the page **past the section card standing on
 * it** - the card disappeared into the light exactly where the light was strongest, which is the
 * failure `GlassContrastTest` was written to catch and did.
 *
 * So the ground is specified as the lift itself and the alpha is solved for it. Every page is lit
 * to the same brightness and only the colour of the light changes, which is also what it looks like
 * it ought to do.
 *
 * @param lift how far above the page's own luminance the lit ground may reach. [GroundLift] on the
 *   dark theme; the light theme's accents are darker than its page, so there the same figure is a
 *   *darkening* of that size - see [LightGroundLift].
 */
internal fun groundAlpha(hue: Color, page: Color, lift: Float): Float {
    val target = lift
    var low = 0f
    var high = 0.6f
    // The lights overlap, so what is solved for is two of them composited: the alpha that lands on
    // the lift once would overshoot it wherever the second light crosses the first, and the one
    // place a ground must not be brighter than planned is where two of them meet.
    repeat(SolveSteps) {
        val middle = (low + high) / 2f
        val lit = hue.copy(alpha = middle).compositeOver(hue.copy(alpha = middle).compositeOver(page))
        if (kotlin.math.abs(lit.luminance() - page.luminance()) < target) low = middle else high = middle
    }
    return (low + high) / 2f
}

/**
 * Enough that the answer is settled to about five decimal places, which is far finer than an 8-bit
 * channel can show. It runs once per change of destination, not per frame.
 */
private const val SolveSteps = 40

/**
 * The lift, dark theme.
 *
 * Small, and the smallness is the finding rather than a preference: a ground is the only thing on
 * screen that every surface above it is composited over, so it spends the contrast of every card,
 * tile and figure at once. At 0.09 - eyeballed, shipped, and looked at on a device - the band
 * behind a page title read as a coloured header and the first card on the page lost its edge into
 * it. This is what is left after the levels above it have been given what they need.
 */
internal const val GroundLift = 0.004f

/**
 * The lift, light theme, and it is larger because it is doing the opposite thing.
 *
 * These accents are darker than the near-white page, so the light is a shadow: it has the whole
 * range beneath the page to work in, where the dark theme's has only the little above it before it
 * reaches the cards.
 */
internal const val LightGroundLift = 0.03f
