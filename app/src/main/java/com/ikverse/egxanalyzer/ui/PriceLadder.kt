package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Draws stop loss, entry band, both targets and the price reached on one axis.
 *
 * The numbers alone do not show whether a trade risks little for a lot or the reverse; placing
 * them proportionally makes the balance readable at a glance, which is the question a
 * recommendation is actually answering. Each level prints its own price beneath its mark, so the
 * shape and the figures are read in one place rather than against the row below.
 *
 * @param peak the highest the stock has traded since the call, when prices are known for it. The
 * axis is still scaled by the recommendation's own levels, so the risk-to-reward shape does not
 * change with the market; a peak beyond the levels is pinned to the end it passed and points out.
 */
@Composable
internal fun PriceLadder(
    point: RecommendationDataPoint,
    modifier: Modifier = Modifier,
    peak: Double? = null,
) {
    val entryLow = point.buyPriceLow ?: point.buyPrice
    val entryHigh = point.buyPriceHigh ?: point.buyPrice
    val levels = listOfNotNull(point.stopLoss, entryLow, entryHigh, point.target1, point.target2)
    if (levels.size < 2) return

    val low = levels.min()
    val high = levels.max()
    val span = high - low
    if (span <= 0.0) return

    val stopColor = MaterialTheme.colorScheme.error
    // Entry is the reference the other bands are read against, so it stays neutral.
    val entryColor = MaterialTheme.colorScheme.onSurface
    val targetColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val peakColor = PriceRole.market

    // The group is what has to stay together when labels are pushed around: the two ends of the
    // entry band are one fact, and a range printed with its floor below the line and its ceiling
    // above it stops reading as a range at all.
    val marks = buildList {
        point.stopLoss?.let { add(Mark(it, stopColor, STOP_GROUP, MarkSide.BELOW)) }
        entryLow?.let { add(Mark(it, entryColor, ENTRY_GROUP, MarkSide.CENTRE)) }
        entryHigh?.takeIf { it != entryLow }
            ?.let { add(Mark(it, entryColor, ENTRY_GROUP, MarkSide.CENTRE)) }
        point.target1?.let { add(Mark(it, targetColor, TARGET1_GROUP, MarkSide.ABOVE)) }
        point.target2?.let { add(Mark(it, targetColor, TARGET2_GROUP, MarkSide.ABOVE)) }
        // Drawn as an arrow rather than a tick, so its side is never read. See drawPeak.
        peak?.let { add(Mark(it, peakColor, PEAK_GROUP, MarkSide.CENTRE)) }
    }

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labels = marks.map { mark ->
        measurer.measure(formatPrice(mark.value), labelStyle) to mark.color
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val insetPx = with(density) { LadderInset.toPx() }
        val usable = widthPx - insetPx * 2
        if (usable <= 0f) return@BoxWithConstraints

        fun axis(value: Double) = insetPx + ((value.coerceIn(low, high) - low) / span).toFloat() * usable

        val centers = marks.map { axis(it.value) }
        val widths = labels.map { it.first.size.width.toFloat() }
        val slots = layoutPriceLabels(
            centers,
            widths,
            widthPx,
            with(density) { LabelGap.toPx() },
            marks.map { it.group },
        )
        val rowsAbove = slots.filter { it.above }.maxOfOrNull { it.row + 1 } ?: 0
        val rowsBelow = slots.filterNot { it.above }.maxOfOrNull { it.row + 1 } ?: 0

        // The peak's arrow goes on the side its own price printed on, so the two are read together
        // rather than pointing at each other across the line.
        val peakAbove = peak?.let { value ->
            slots.getOrNull(marks.indexOfFirst { it.value == value })?.above
        } ?: false

        val rowHeight = with(density) { LabelRowHeight.toPx() }
        val trackHeight = with(density) { TrackBand.toPx() }
        val peakBand = with(density) { PeakBand.toPx() }

        val above = LabelRowHeight * rowsAbove + if (peak != null && peakAbove) PeakBand else 0.dp
        val below = LabelRowHeight * rowsBelow + if (peak != null && !peakAbove) PeakBand else 0.dp

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(above + TrackBand + below),
        ) {
            val top = with(density) { above.toPx() }
            val y = top + trackHeight / 2f

            drawRect(
                color = trackColor,
                topLeft = Offset(insetPx, y - TRACK_THICKNESS / 2),
                size = Size(usable, TRACK_THICKNESS),
            )

            if (entryLow != null && entryHigh != null) {
                val start = min(axis(entryLow), axis(entryHigh))
                val end = max(axis(entryLow), axis(entryHigh))
                drawRect(
                    color = entryColor.copy(alpha = 0.28f),
                    topLeft = Offset(start, y - BAND_HEIGHT / 2),
                    // A single entry price collapses to zero width, so keep it visible.
                    size = Size(max(end - start, 2f), BAND_HEIGHT),
                )
            }

            // Never longer than half the band, so a tick on one side of the line cannot reach the
            // price labels above or below it at a low display density. At the two densities this
            // app actually runs at the clamp does nothing; it is what keeps a third from breaking.
            val markLength = min(MARKER_HEIGHT, trackHeight / 2f)
            marks.forEach { mark ->
                if (mark.value == peak) return@forEach
                drawMarker(axis(mark.value), y, mark.color, mark.side, markLength)
            }

            peak?.let {
                drawPeak(axis(it), top, trackHeight, peakBand, peakAbove, it > high, it < low, peakColor)
            }

            val belowTop = top + trackHeight + if (peak != null && !peakAbove) peakBand else 0f
            val aboveBottom = top - if (peak != null && peakAbove) peakBand else 0f
            labels.forEachIndexed { index, (layout, color) ->
                val slot = slots[index]
                val lineTop = if (slot.above) {
                    // Rows above stack outward from the track, so the nearest row is the first one.
                    aboveBottom - (slot.row + 1) * rowHeight
                } else {
                    belowTop + slot.row * rowHeight
                }
                drawText(
                    textLayoutResult = layout,
                    color = color,
                    topLeft = Offset(slot.left, lineTop),
                )
            }
        }
    }
}

/** Where a price label sits: its left edge, which side of the track, and how far out. */
internal data class LabelSlot(val left: Float, val row: Int, val above: Boolean = false)

/**
 * Places each label beside its mark, with no two ever touching.
 *
 * One row below the line, and everything that will not fit in it goes above. There is deliberately
 * no second row below: a label pushed down sits further from the mark it belongs to than from its
 * neighbour's, and the eye reads it as belonging to the wrong price.
 *
 * Labels sharing a [groups] entry describe one fact and move as a unit. An entry band with its
 * floor printed below the line and its ceiling above it is two prices, not a range - and a band
 * whose ends cannot sit side by side takes both of them above rather than leaving one behind.
 *
 * Everything is clamped inside the track, so the outermost prices stay readable rather than running
 * off the edge.
 */
internal fun layoutPriceLabels(
    centers: List<Float>,
    widths: List<Float>,
    trackWidth: Float,
    gap: Float,
    groups: List<Int> = centers.indices.toList(),
): List<LabelSlot> {
    val slots = arrayOfNulls<LabelSlot>(centers.size)
    fun left(index: Int) =
        (centers[index] - widths[index] / 2f).coerceIn(0f, max(0f, trackWidth - widths[index]))

    // The single row under the line, and as many above it as the crowding needs.
    var belowEnd = 0f
    val aboveEnds = mutableListOf<Float>()

    // Groups in the order their earliest mark sits on the axis, so the ladder still reads
    // left to right.
    centers.indices
        .groupBy { groups.getOrElse(it) { _ -> it } }
        .values
        .sortedBy { members -> members.minOf { centers[it] } }
        .forEach { members ->
            val ordered = members.sortedBy { centers[it] }
            // The row below takes the whole group or none of it: half a range down there is worse
            // than all of it out of the way.
            val fitsBelow = ordered.fold(belowEnd) { end, index ->
                if (left(index) < end) return@fold Float.MAX_VALUE
                left(index) + widths[index] + gap
            }
            if (fitsBelow != Float.MAX_VALUE) {
                ordered.forEach { index ->
                    slots[index] = LabelSlot(left(index), row = 0, above = false)
                }
                belowEnd = fitsBelow
                return@forEach
            }
            ordered.forEach { index ->
                var row = 0
                while (row < aboveEnds.size && left(index) < aboveEnds[row]) row++
                if (row == aboveEnds.size) aboveEnds += 0f
                aboveEnds[row] = left(index) + widths[index] + gap
                slots[index] = LabelSlot(left(index), row = row, above = true)
            }
        }
    return slots.map { requireNotNull(it) }
}

/**
 * Which side of the line a level's tick sits on, and therefore what it is without reading its hue.
 *
 * Target and stop appear on nearly every ladder and telling them apart is the most common read on
 * the screen - so the distinction cannot rest on green against red, which is the one pair a
 * colour-blind reader cannot make. Everywhere else in the app a text label carries it; here there
 * are only prices, so the line itself does: what the call is aiming at ticks up, what it gives up
 * on ticks down.
 *
 * Entry stays centred, and is the only thing that does. It is the reference the other two are read
 * against rather than an outcome, and it already has the band drawn through it.
 */
private enum class MarkSide { ABOVE, CENTRE, BELOW }

/** One level on the axis: where it sits, what colour it is, what it groups with, and which side. */
private data class Mark(
    val value: Double,
    val color: Color,
    /** Labels sharing this describe one fact and move together. See [layoutPriceLabels]. */
    val group: Int,
    val side: MarkSide,
)

private fun DrawScope.drawMarker(x: Float, y: Float, color: Color, side: MarkSide, length: Float) {
    // Every side overlaps the track by the line's own half-thickness, so a tick reads as rising out
    // of the line rather than floating beside it.
    val top = when (side) {
        MarkSide.ABOVE -> y - length
        MarkSide.CENTRE -> y - length / 2
        MarkSide.BELOW -> y
    }
    drawRect(
        color = color,
        topLeft = Offset(x - MARKER_WIDTH / 2, top),
        size = Size(MARKER_WIDTH, length),
    )
}

/**
 * The small arrow beside the axis marking how far the stock actually got.
 *
 * It sits on whichever side its own price label printed on and points back at the line, out of the
 * way of the levels themselves. Past the top or the bottom it turns to point that way instead,
 * because an arrow pinned to the end cannot say "beyond this" - only "exactly here", which would be
 * a lie.
 */
private fun DrawScope.drawPeak(
    x: Float,
    top: Float,
    trackHeight: Float,
    band: Float,
    above: Boolean,
    beyondTop: Boolean,
    beyondBottom: Boolean,
    color: Color,
) {
    // The tip always touches the line; the base is the outer edge, whichever way that is.
    val tipY = if (above) top - 1f else top + trackHeight + 1f
    val baseY = if (above) tipY - band + 2f else tipY + band - 2f
    val half = abs(baseY - tipY) * 0.62f
    val midY = (tipY + baseY) / 2f
    val path = Path().apply {
        when {
            beyondTop -> {
                moveTo(x + half, midY)
                lineTo(x - half, tipY)
                lineTo(x - half, baseY)
            }
            beyondBottom -> {
                moveTo(x - half, midY)
                lineTo(x + half, tipY)
                lineTo(x + half, baseY)
            }
            else -> {
                moveTo(x, tipY)
                lineTo(x - half, baseY)
                lineTo(x + half, baseY)
            }
        }
        close()
    }
    drawPath(path, color)
}

/**
 * Reward divided by risk, using the nearer target and the entry the trade actually opens at.
 * Null when the source did not supply enough levels to say.
 */
internal fun RecommendationDataPoint.riskRewardRatio(): Double? {
    val entry = buyPrice ?: buyPriceLow ?: buyPriceHigh ?: return null
    val stop = stopLoss ?: return null
    val target = target1 ?: target2 ?: return null
    val risk = abs(entry - stop)
    if (risk <= 0.0) return null
    return abs(target - entry) / risk
}

private val LadderInset = 8.dp
private val TrackBand = 26.dp

/** Just enough room beside the line for the peak arrow, without pushing the prices away. */
private val PeakBand = 9.dp
private val LabelRowHeight = 15.dp
private val LabelGap = 6.dp

private const val TRACK_THICKNESS = 4f
private const val BAND_HEIGHT = 18f
private const val MARKER_WIDTH = 3f
private const val MARKER_HEIGHT = 26f

// Which labels describe one fact. Only the entry band has two ends; the rest stand alone.
private const val STOP_GROUP = 0
private const val ENTRY_GROUP = 1
private const val TARGET1_GROUP = 2
private const val TARGET2_GROUP = 3
private const val PEAK_GROUP = 4
