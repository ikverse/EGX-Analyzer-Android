package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.DailySession

/**
 * Where a stock has been, as one line.
 *
 * The record has stored every session it ever downloaded and drawn none of them. Every price on
 * every card in this app is a single figure - the last close, the peak inside a window, the level a
 * channel named - and a column of single figures cannot say whether a stock has been climbing for a
 * month or fell off a cliff last Tuesday and has been flat since. That is the question a reader
 * opening a stock arrives with, and it is the one thing here the app knew and never showed.
 *
 * **Closes only, and no axis.** A candle chart would be a second way of reading prices in an app
 * whose every other price is a plain figure, and gridlines under a 56dp line are furniture. The
 * shape is the whole claim; the figures that matter are printed under it, where they are read as
 * figures.
 *
 * Scaled to its own range rather than to zero, because a stock that has moved between 84 and 88 has
 * a shape and a chart anchored at zero would draw it as a flat line. A range of nothing at all -
 * every close identical, which a suspended stock really does look like - draws down the middle
 * rather than dividing by zero.
 *
 * Nothing is drawn for fewer than two closes: one point is not a line, and an empty box under a
 * heading reads as a chart that failed rather than as a stock the feed has never carried.
 */
@Composable
internal fun Sparkline(
    sessions: List<DailySession>,
    modifier: Modifier = Modifier,
    height: Dp = SparklineHeight,
) {
    val closes = sessions.mapNotNull(DailySession::close)
    if (closes.size < 2) return
    val line = PriceRole.market
    val low = closes.min()
    val high = closes.max()
    val span = (high - low).takeIf { it > 0.0 }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            // A picture of a series, so a reader who cannot see it is told what it did.
            .semantics {
                contentDescription = "${closes.size} sessions, " +
                    formatPrice(closes.first()) + " to " + formatPrice(closes.last())
            },
    ) {
        val stroke = SparklineStroke.toPx()
        // Inset by half the stroke top and bottom, so the highest and lowest points are drawn whole
        // rather than shaved off by the edge of the canvas.
        val top = stroke / 2
        val usable = size.height - stroke
        val step = size.width / (closes.size - 1)
        val points = closes.mapIndexed { index, close ->
            val fraction = span?.let { (close - low) / it } ?: 0.5
            Offset(index * step, top + ((1.0 - fraction) * usable).toFloat())
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path,
            color = line,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * Where the close sits inside the session it closed in.
 *
 * The high and the low were already printed as two figures beside the close, and three prices in a
 * row is arithmetic left to the reader: a close of 86.40 between 85.20 and 87.10 means the stock
 * finished near the top of its day, and that sentence is what the bar says without being read. The
 * open takes a second mark wherever the feed carries one, which turns the same bar into where the
 * day started and where it ended.
 *
 * Absent rather than empty wherever the session is missing any of the three, or where the high and
 * the low are the same price. A stock that did not really trade has no range to draw, and a bar
 * with its marker pinned at one end would be stating something nobody measured.
 */
@Composable
internal fun DayRange(session: DailySession, modifier: Modifier = Modifier) {
    val low = session.low
    val high = session.high
    val close = session.close
    if (low == null || high == null || close == null || high <= low) return
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val closeMark = PriceRole.market
    val openMark = MaterialTheme.colorScheme.outline
    val at = ((close - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()
    val openAt = session.open?.let { ((it - low) / (high - low)).coerceIn(0.0, 1.0).toFloat() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(RangeHeight)
                .semantics {
                    contentDescription = "Closed at " + formatPrice(close) +
                        ", between " + formatPrice(low) + " and " + formatPrice(high)
                },
        ) {
            val bar = RangeBarHeight.toPx()
            val middle = (size.height - bar) / 2
            drawRoundRect(
                color = track,
                topLeft = Offset(0f, middle),
                size = Size(size.width, bar),
                cornerRadius = CornerRadius(bar / 2),
            )
            val markWidth = RangeMarkWidth.toPx()
            // Marks are pulled inside the track at the extremes rather than centred on their point,
            // so a close exactly at the day's high is drawn against the end instead of half outside
            // it.
            fun x(fraction: Float) =
                (fraction * (size.width - markWidth)).coerceIn(0f, size.width - markWidth)
            if (openAt != null) {
                drawRect(
                    color = openMark,
                    topLeft = Offset(x(openAt), middle),
                    size = Size(markWidth, bar),
                )
            }
            // Taller than the track it sits in, because it is the figure being read: the open is
            // context and the close is the price.
            drawRoundRect(
                color = closeMark,
                topLeft = Offset(x(at), 0f),
                size = Size(markWidth, size.height),
                cornerRadius = CornerRadius(markWidth / 2),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RangeCaption("low " + formatPrice(low))
            session.open?.let { RangeCaption("open " + formatPrice(it)) }
            RangeCaption("high " + formatPrice(high))
        }
    }
}

@Composable
private fun RangeCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Tall enough for a month of closes to have a shape, short enough to sit above the figures. */
private val SparklineHeight = 56.dp

private val SparklineStroke = 2.dp

/** The track, plus the overhang the close marker is drawn with above and below it. */
private val RangeHeight = 14.dp

private val RangeBarHeight = 6.dp

private val RangeMarkWidth = 3.dp
