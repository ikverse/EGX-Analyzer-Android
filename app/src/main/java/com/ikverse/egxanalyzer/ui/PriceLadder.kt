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

    val marks = buildList {
        point.stopLoss?.let { add(it to stopColor) }
        entryLow?.let { add(it to entryColor) }
        entryHigh?.takeIf { it != entryLow }?.let { add(it to entryColor) }
        point.target1?.let { add(it to targetColor) }
        point.target2?.let { add(it to targetColor) }
        peak?.let { add(it to peakColor) }
    }

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labels = marks.map { (value, color) ->
        measurer.measure(formatPrice(value), labelStyle) to color
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val insetPx = with(density) { LadderInset.toPx() }
        val usable = widthPx - insetPx * 2
        if (usable <= 0f) return@BoxWithConstraints

        fun axis(value: Double) = insetPx + ((value.coerceIn(low, high) - low) / span).toFloat() * usable

        val centers = marks.map { axis(it.first) }
        val widths = labels.map { it.first.size.width.toFloat() }
        val slots = layoutPriceLabels(centers, widths, widthPx, with(density) { LabelGap.toPx() })
        val rows = (slots.maxOfOrNull { it.row } ?: 0) + 1

        val rowHeight = with(density) { LabelRowHeight.toPx() }
        val trackHeight = with(density) { TrackBand.toPx() }
        val peakBand = with(density) { PeakBand.toPx() }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(TrackBand + PeakBand + LabelRowHeight * rows),
        ) {
            val y = trackHeight / 2f

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

            marks.forEach { (value, color) ->
                if (value == peak) return@forEach
                drawMarker(axis(value), y, color)
            }

            peak?.let { drawPeak(axis(it), trackHeight, peakBand, it > high, it < low, peakColor) }

            val labelTop = trackHeight + peakBand
            labels.forEachIndexed { index, (layout, color) ->
                drawText(
                    textLayoutResult = layout,
                    color = color,
                    topLeft = Offset(slots[index].left, labelTop + slots[index].row * rowHeight),
                )
            }
        }
    }
}

/** Where a price label sits: its left edge, and the row it was pushed to. */
internal data class LabelSlot(val left: Float, val row: Int)

/**
 * Places each label centred under its mark, with no two ever touching.
 *
 * Clamped inside the track so the outermost prices stay readable rather than running off the edge,
 * and pushed down a row when they would still collide - a stop and an entry a few piastres apart
 * print on top of each other otherwise. Rows fill from the top, so the common case stays one line.
 */
internal fun layoutPriceLabels(
    centers: List<Float>,
    widths: List<Float>,
    trackWidth: Float,
    gap: Float,
): List<LabelSlot> {
    val slots = arrayOfNulls<LabelSlot>(centers.size)
    val rowEnds = mutableListOf<Float>()
    centers.indices.sortedBy { centers[it] }.forEach { index ->
        val width = widths[index]
        val left = (centers[index] - width / 2f).coerceIn(0f, max(0f, trackWidth - width))
        var row = 0
        while (row < rowEnds.size && left < rowEnds[row]) row++
        if (row == rowEnds.size) rowEnds += 0f
        rowEnds[row] = left + width + gap
        slots[index] = LabelSlot(left, row)
    }
    return slots.map { requireNotNull(it) }
}

private fun DrawScope.drawMarker(x: Float, y: Float, color: Color) {
    drawRect(
        color = color,
        topLeft = Offset(x - MARKER_WIDTH / 2, y - MARKER_HEIGHT / 2),
        size = Size(MARKER_WIDTH, MARKER_HEIGHT),
    )
}

/**
 * The small arrow under the axis marking how far the stock actually got.
 *
 * It sits below the line and points up at the price, out of the way of the levels themselves. Past
 * the top or the bottom it turns to point that way instead, because an upward arrow pinned to the
 * end cannot say "beyond this" - only "exactly here", which would be a lie.
 */
private fun DrawScope.drawPeak(
    x: Float,
    trackHeight: Float,
    band: Float,
    beyondTop: Boolean,
    beyondBottom: Boolean,
    color: Color,
) {
    val tipY = trackHeight + 1f
    val baseY = tipY + band - 2f
    val half = (baseY - tipY) * 0.62f
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

/** Just enough room under the line for the peak arrow, without pushing the prices down. */
private val PeakBand = 9.dp
private val LabelRowHeight = 15.dp
private val LabelGap = 6.dp

private const val TRACK_THICKNESS = 4f
private const val BAND_HEIGHT = 18f
private const val MARKER_WIDTH = 3f
private const val MARKER_HEIGHT = 26f
