package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.ui.theme.extraColors
import kotlin.math.max
import kotlin.math.min

/**
 * How a source's settled calls actually divided, as one band.
 *
 * This is the screen's whole argument in one object. A hit rate is a single number that hides the
 * two things a reader most needs: how much evidence is behind it, and how the failures divide. Four
 * printed counts said the second and not the first, and said it one number at a time - nothing on
 * the card related 94 judged calls to the 3 behind the channel underneath.
 *
 * So **length is the sample and the segments are the verdicts**. [scale] is the largest judged count
 * among the sources being compared, which makes every bar on the screen measurable against every
 * other: the leader runs the full width, a source with a third of the record runs a third of it, and
 * a source with three settled calls is a stub that no rate printed beside it can talk up.
 *
 * The four counts partition [ChannelScore.judged] exactly - those are the only four outcomes
 * `Outcome.judged` is true for - so the segments fill the bar's own length with nothing left over
 * and nothing counted twice.
 *
 * Read without colour three ways over: the order is fixed whatever the data does, the two target
 * segments are one hue at two weights rather than two hues, and each segment carries its count.
 */
@Composable
internal fun OutcomeBar(
    score: ChannelScore,
    /** The largest judged count on screen. Length is meaningless without something to measure against. */
    scale: Int,
    modifier: Modifier = Modifier,
    height: Dp = OutcomeBarHeight,
    /** The surface behind the bar, which the softened segment is mixed onto. */
    on: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    if (scale <= 0) return
    val parts = score.segments(on)
    val remainder = (scale - score.judged).coerceAtLeast(0)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(OutcomeBarCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            // The bar is a picture of the counts, so a reader that cannot see it is told them.
            .semantics { contentDescription = score.spoken() },
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            parts.forEach { part -> Segment(part, height) }
            // What this source has not settled, next to the one that has settled most. Left as the
            // track colour rather than filled, because it is an absence rather than an outcome.
            if (remainder > 0) Box(Modifier.weight(remainder.toFloat()))
        }
    }
}

@Composable
private fun RowScope.Segment(part: OutcomeSegment, height: Dp) {
    Box(
        Modifier
            .weight(part.count.toFloat())
            .fillMaxHeight()
            .background(part.color),
        contentAlignment = Alignment.Center,
    ) {
        // Derived rather than guessed at a breakpoint: whether a count fits depends on the width
        // this segment actually got, which depends on every other segment and on the card it is in.
        BoxWithConstraints {
            if (maxWidth >= MinCountWidth && height >= MinCountHeight) {
                Text(
                    part.count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                    color = inkOn(part.color),
                    maxLines = 1,
                )
            }
        }
    }
}

/** One verdict's share of the bar. */
private data class OutcomeSegment(val count: Int, val color: Color, val label: String)

/**
 * The four verdicts, always in this order.
 *
 * Order is what lets the bar be read without colour, so it is fixed rather than sorted by size:
 * **the order the call itself passes through them**, left to right - target 1, then target 2, then
 * the stop, then the window closing - on every bar in the app. A reader learns it once, and it is
 * the same order the levels are printed in on every card and every table in the app, so the bar
 * cannot disagree with the report it summarises.
 */
@Composable
private fun ChannelScore.segments(on: Color): List<OutcomeSegment> {
    val target = PriceRole.target
    return listOf(
        OutcomeSegment(partialHits, target, "reached target 1 only"),
        // The same hue softened, which is how this app already marks a figure as a lesser claim -
        // see PriceRole.derived. A second green would have been a fifth colour to learn, for a
        // distinction that is one of degree. Mixed onto the surface rather than left transparent, so
        // the count drawn on it can be given a colour that actually contrasts.
        OutcomeSegment(fullHits, PriceRole.derived(target).compositeOver(on), "reached target 2"),
        OutcomeSegment(stopped, PriceRole.stop, "stopped"),
        OutcomeSegment(expired, extraColors.expired, "out of time"),
    ).filter { it.count > 0 }
}

/**
 * Black or white on the segment, whichever the segment can actually carry.
 *
 * Computed rather than tabulated because these hues invert between themes - the target is a light
 * mint on a dark page and a deep green on a light one - and a fixed pair would be unreadable on one
 * of them. Every segment colour is opaque by the time it gets here, so the measurement is of what
 * is really drawn.
 *
 * The two candidates are **compared by contrast rather than sorted by a threshold**. A threshold is
 * a guess at where the crossover falls, and the guess was 0.45 - two and a half times the 0.179 at
 * which black and white actually draw level. Both light segments fell under it and took white: the
 * count on the target read at 2.1:1 and the one on the stop at 2.3:1, against the 9.98:1 and 9.20:1
 * black would have given them, so two of the four counts on every bar were effectively not there.
 * Neither the alpha below nor a hue's own weight can push this the wrong way, because the ratios are
 * measured on the composited text against the segment it is drawn on - what the eye is given.
 */
internal fun inkOn(segment: Color): Color {
    val black = Color.Black.copy(alpha = 0.80f)
    val white = Color.White.copy(alpha = 0.92f)
    return if (contrast(black, segment) >= contrast(white, segment)) black else white
}

/** WCAG contrast of [ink] once it is drawn on [segment], against the segment itself. */
private fun contrast(ink: Color, segment: Color): Float {
    val drawn = ink.compositeOver(segment).luminance()
    val behind = segment.luminance()
    return (max(drawn, behind) + 0.05f) / (min(drawn, behind) + 0.05f)
}

/** What the bar says out loud, for a reader who cannot see it. */
internal fun ChannelScore.spoken(): String = buildString {
    append("$judged settled ${if (judged == 1) "call" else "calls"}")
    listOf(
        partialHits to "reached target 1 only",
        fullHits to "reached target 2",
        stopped to "stopped",
        expired to "ran out of time",
    ).filter { it.first > 0 }.forEach { (count, what) -> append(", $count $what") }
}

/**
 * The key to every bar on the screen, drawn once.
 *
 * Once rather than per card: four names repeated down a column of sources is most of the column, and
 * the order never changes, so it is learned in one reading.
 */
@Composable
internal fun OutcomeLegend(modifier: Modifier = Modifier) {
    val target = PriceRole.target
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val entries = listOf(
        target to "target 1 only",
        PriceRole.derived(target).compositeOver(surface) to "target 2",
        PriceRole.stop to "stopped",
        extraColors.expired to "out of time",
    )
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        entries.forEach { (color, label) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(LegendChip)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Tall enough to carry a count, short enough that a card is not mostly bar. */
internal val OutcomeBarHeight = 22.dp

/** On a source card, where the bar is a comparison rather than the headline. */
internal val OutcomeBarCompact = 16.dp

private val OutcomeBarCorner = 4.dp

/** Two digits at labelSmall, plus enough either side that the number is not touching an edge. */
private val MinCountWidth = 26.dp

/** Below this the bar is a rule rather than a band, and a number in it would be clipped. */
private val MinCountHeight = 14.dp

private val LegendChip = 10.dp
