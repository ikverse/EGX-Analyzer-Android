package com.ikverse.egxanalyzer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.ikverse.egxanalyzer.model.ChannelScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * The four segment colours as the bar actually draws them on a dark page.
 *
 * Sampled from the rendered screen rather than read from the theme, deliberately: the theme is only
 * reachable from a composition, and what these tests are about is whether a count is readable on the
 * pixels a phone puts up. If the palette moves, these move with it and the ratios below are what say
 * whether the new hues still carry a number.
 */
private val Target = Color(0xFF46C98A)
private val TargetSoftened = Color(0xFF2E7F5B)
private val Stop = Color(0xFFFF8A80)
private val Expired = Color(0xFFF3C264)

class OutcomeBarInkTest {

    @Test
    fun `every segment gets whichever ink actually contrasts better on it`() {
        listOf(Target, TargetSoftened, Stop, Expired).forEach { segment ->
            val chosen = inkOn(segment)
            val other = if (chosen.red > 0.5f) Color.Black.copy(alpha = 0.80f) else Color.White.copy(alpha = 0.92f)
            assertTrue(
                "ink on $segment scored ${contrast(chosen, segment)} against ${contrast(other, segment)}",
                contrast(chosen, segment) >= contrast(other, segment),
            )
        }
    }

    /**
     * The two the old 0.45 luminance threshold got wrong.
     *
     * Both sit under it - 0.449 and 0.410 - so both took white and drew their count at 2.1:1 and
     * 2.3:1, which is not a number anyone reads. Named one at a time rather than left to the
     * property above, because the property would still pass if the bar stopped drawing counts.
     */
    @Test
    fun `the two light segments carry black, not the white they used to`() {
        assertEquals(Color.Black.copy(alpha = 0.80f), inkOn(Target))
        assertEquals(Color.Black.copy(alpha = 0.80f), inkOn(Stop))
    }

    @Test
    fun `the softened target is dark enough to carry white`() {
        assertEquals(Color.White.copy(alpha = 0.92f), inkOn(TargetSoftened))
    }

    /**
     * The floor, named as the number it actually is.
     *
     * The softened target is the worst of the four at **4.42:1** - clear of the 3:1 that AA asks of
     * text this size and weight, short of the 4.5:1 it asks of body text. It is where it is because
     * it is one hue at 60% over the page rather than a colour picked for contrast, and lifting it
     * means changing the palette rather than the ink. The other three are near 9:1 or better. Pinned
     * so that a palette change has to look at this rather than quietly drop below it.
     */
    @Test
    fun `every count clears 4 to 1, and the softened target is the floor`() {
        listOf(Target, TargetSoftened, Stop, Expired).forEach { segment ->
            val ratio = contrast(inkOn(segment), segment)
            assertTrue("$segment carries its count at only $ratio", ratio >= 4.0f)
        }
        assertEquals(4.42f, contrast(inkOn(TargetSoftened), TargetSoftened), 0.01f)
    }

    private fun contrast(ink: Color, segment: Color): Float {
        val drawn = ink.compositeOver(segment).luminance()
        val behind = segment.luminance()
        return (max(drawn, behind) + 0.05f) / (min(drawn, behind) + 0.05f)
    }
}

class OutcomeBarOrderTest {

    /**
     * The spoken description is the one place the order is readable without a screen, so it is where
     * the order is pinned. Target 1 comes first: the bar reads in the order a call passes through
     * the levels, not best outcome to worst.
     */
    @Test
    fun `target 1 is named before target 2`() {
        assertEquals(
            "18 settled calls, 6 reached target 1 only, 2 reached target 2, 2 stopped, 8 ran out of time",
            score(fullHits = 2, partialHits = 6, stopped = 2, expired = 8).spoken(),
        )
    }

    @Test
    fun `a verdict nothing landed on is left out rather than said as zero`() {
        assertEquals(
            "8 settled calls, 6 reached target 1 only, 2 reached target 2",
            score(fullHits = 2, partialHits = 6, stopped = 0, expired = 0).spoken(),
        )
    }

    @Test
    fun `one settled call is a call, not calls`() {
        assertEquals(
            "1 settled call, 1 stopped",
            score(fullHits = 0, partialHits = 0, stopped = 1, expired = 0).spoken(),
        )
    }

    private fun score(fullHits: Int, partialHits: Int, stopped: Int, expired: Int) = ChannelScore(
        channel = "CFI Egypt",
        calls = 39,
        judged = fullHits + partialHits + stopped + expired,
        fullHits = fullHits,
        partialHits = partialHits,
        stopped = stopped,
        expired = expired,
        notTradable = 6,
        fullHitRate = null,
        anyTargetRate = null,
        averageReturn = null,
        medianSessionsToHit = null,
    )
}
