package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Draws stop loss, entry band, and both targets on one price axis.
 *
 * The numbers alone do not show whether a trade risks little for a lot or the reverse; placing
 * them proportionally makes the balance readable at a glance, which is the question a
 * recommendation is actually answering.
 */
@Composable
internal fun PriceLadder(point: RecommendationDataPoint, modifier: Modifier = Modifier) {
    val entryLow = point.buyPriceLow ?: point.buyPrice
    val entryHigh = point.buyPriceHigh ?: point.buyPrice
    val marks = listOfNotNull(point.stopLoss, entryLow, entryHigh, point.target1, point.target2)
    if (marks.size < 2) return

    val low = marks.min()
    val high = marks.max()
    val span = high - low
    if (span <= 0.0) return

    val stopColor = MaterialTheme.colorScheme.error
    val entryColor = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val y = size.height / 2f
            val usable = size.width - HORIZONTAL_INSET * 2
            fun x(value: Double) = HORIZONTAL_INSET + ((value - low) / span).toFloat() * usable

            drawTrack(y, usable, trackColor)

            if (entryLow != null && entryHigh != null) {
                val start = min(x(entryLow), x(entryHigh))
                val end = max(x(entryLow), x(entryHigh))
                drawRect(
                    color = entryColor.copy(alpha = 0.28f),
                    topLeft = Offset(start, y - BAND_HEIGHT / 2),
                    // A single entry price collapses to zero width, so keep it visible.
                    size = Size(max(end - start, 2f), BAND_HEIGHT),
                )
            }
            point.stopLoss?.let { drawMarker(x(it), y, stopColor) }
            entryLow?.let { drawMarker(x(it), y, entryColor) }
            entryHigh?.takeIf { it != entryLow }?.let { drawMarker(x(it), y, entryColor) }
            point.target1?.let { drawMarker(x(it), y, targetColor) }
            point.target2?.let { drawMarker(x(it), y, targetColor) }
        }
    }
}

private fun DrawScope.drawTrack(y: Float, usable: Float, color: Color) {
    drawRect(
        color = color,
        topLeft = Offset(HORIZONTAL_INSET, y - TRACK_HEIGHT / 2),
        size = Size(usable, TRACK_HEIGHT),
    )
}

private fun DrawScope.drawMarker(x: Float, y: Float, color: Color) {
    drawRect(
        color = color,
        topLeft = Offset(x - MARKER_WIDTH / 2, y - MARKER_HEIGHT / 2),
        size = Size(MARKER_WIDTH, MARKER_HEIGHT),
    )
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

private const val HORIZONTAL_INSET = 6f
private const val TRACK_HEIGHT = 4f
private const val BAND_HEIGHT = 18f
private const val MARKER_WIDTH = 3f
private const val MARKER_HEIGHT = 26f
