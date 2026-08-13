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
 * Read without colour three ways over: the order is fixed whatever the data does, target 1 is the
 * target hue softened rather than a hue of its own, and each segment carries its count.
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
                    color = part.onColor(),
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
 * Order is what lets the bar be read without colour, so it is fixed rather than sorted by size: best
 * outcome to worst, left to right, on every bar in the app. A reader learns it once.
 */
@Composable
private fun ChannelScore.segments(on: Color): List<OutcomeSegment> {
    val target = PriceRole.target
    return listOf(
        OutcomeSegment(fullHits, target, "reached target 2"),
        // The same hue softened, which is how this app already marks a figure as a lesser claim -
        // see PriceRole.derived. A second green would have been a fifth colour to learn, for a
        // distinction that is one of degree. Mixed onto the surface rather than left transparent, so
        // the count drawn on it can be given a colour that actually contrasts.
        OutcomeSegment(partialHits, PriceRole.derived(target).compositeOver(on), "reached target 1 only"),
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
 */
private fun OutcomeSegment.onColor(): Color =
    if (color.luminance() > 0.45f) Color.Black.copy(alpha = 0.80f) else Color.White.copy(alpha = 0.92f)

/** What the bar says out loud, for a reader who cannot see it. */
private fun ChannelScore.spoken(): String = buildString {
    append("$judged settled ${if (judged == 1) "call" else "calls"}")
    listOf(
        fullHits to "reached target 2",
        partialHits to "reached target 1 only",
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
        target to "target 2",
        PriceRole.derived(target).compositeOver(surface) to "target 1 only",
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
