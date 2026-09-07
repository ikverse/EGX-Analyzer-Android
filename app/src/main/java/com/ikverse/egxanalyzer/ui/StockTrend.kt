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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.DailySession
import java.time.LocalDate
import java.time.Period

/**
 * How far back the chart is drawn, as a reader thinks of it.
 *
 * **Calendar time, never a count of sessions.** A week means the last seven days however many of
 * them the exchange was open, so a holiday shortens the line rather than silently reaching an extra
 * session further back to make up the number - which would quietly disagree with the date printed
 * under the line's own left end.
 *
 * [Widest] is what is actually fetched, once, when the sheet opens; every shorter range is a slice
 * of it taken in the composition. Six ranges are five presses that cost nothing rather than five
 * queries, and the disk is read exactly as often as before this existed.
 */
internal enum class ChartRange(val label: String, private val period: Period) {
    WEEK("1W", Period.ofWeeks(1)),
    MONTH("1M", Period.ofMonths(1)),
    TWO_MONTHS("2M", Period.ofMonths(2)),
    THREE_MONTHS("3M", Period.ofMonths(3)),
    SIX_MONTHS("6M", Period.ofMonths(6)),
    ;

    fun since(today: LocalDate): LocalDate = today.minus(period)

    companion object {
        /**
         * A month, which is about two trade windows.
         *
         * The span a call in this app actually lives in - the offered window is ten sessions and
         * the judging horizon thirty - so it opens on the part of the line that has something to do
         * with the levels drawn across it, and six months is one press away for the reader asking a
         * different question.
         */
        val Default = MONTH

        /** What the sheet fetches, so no press ever waits on a query. */
        val Widest = SIX_MONTHS
    }
}

/**
 * The five numbers somebody printed about this stock, and the one the reader actually paid.
 *
 * [source] names whose they are, because that is the one thing a coloured line across a chart
 * cannot say for itself: a green line is a target, and whether it is *your* target or the newest
 * channel's is the difference between a level you are running under and a level you read about.
 */
internal data class ChartLevels(
    val source: String,
    val stopLoss: Double?,
    val entryLow: Double?,
    val entryHigh: Double?,
    val target1: Double?,
    val target2: Double?,
    /** What the reader paid, where they hold the stock. A fact, not a claim, so it is drawn plain. */
    val paid: Double?,
) {
    val values: List<Double>
        get() = listOfNotNull(stopLoss, entryLow, entryHigh, target1, target2, paid)

    val any: Boolean get() = values.isNotEmpty()
}

/**
 * Where a stock has been, with the levels it is being judged against drawn across it.
 *
 * The line alone answered one question and stopped: roughly up, roughly down. Every price anywhere
 * else in this app is a single figure, so "has this been climbing for a month or did it fall off a
 * cliff last Tuesday" was the one thing the record held and no screen drew - and once drawn, the
 * shape on its own still could not say whether a climb was three percent or forty.
 *
 * **The levels are what make it a decision rather than a picture.** The stop, the entry band and
 * both targets sit on the same scale as the price, so the gap between the line's right-hand end and
 * each of them is the distance the stock still has to travel, as a length rather than as arithmetic
 * done in the head over a column of figures on another card. The slope beside it says how fast it
 * has been covering that ground. They are drawn dashed and the price solid, because one is a claim
 * about the future and the other is what happened.
 *
 * **The scale may grow to fit the levels, but only so far.** Honouring a target the stock has been
 * nowhere near would squash a month of real movement into a flat line at the bottom of the box - so
 * the scale is the price range, extended by at most [MaxScaleGrowth] of its own span to take the
 * levels in. An ordinary call's levels sit inside that and are drawn where they really are; one far
 * outside is **pinned to the edge it passed and marked with an arrow**, which is the rule
 * [PriceLadder] already follows for a price beyond the levels, in the other direction.
 *
 * **A ring wherever a call was made**, in both states, because those belong to the stock rather
 * than to any one call's levels. Nothing else in the app can show whether the channels tend to name
 * this stock near its tops; here it is one glance.
 *
 * @param on the surface behind the chart, which the call rings are filled with so the price line
 * cannot be seen running through them.
 */
@Composable
internal fun PriceChart(
    sessions: List<DailySession>,
    levels: ChartLevels?,
    calls: Set<LocalDate>,
    on: Color,
    modifier: Modifier = Modifier,
    height: Dp = ChartHeight,
) {
    val points = remember(sessions) { sessions.filter { it.close != null } }
    if (points.size < 2) return
    val closes = points.map { it.close!! }

    val priceLow = closes.min()
    val priceHigh = closes.max()
    // A stock whose every close is identical is a real state - a suspended one - and dividing by
    // its span would be the chart failing rather than saying so. Two percent of the price gives it
    // a scale to be flat inside.
    val priceSpan = (priceHigh - priceLow).takeIf { it > 0.0 } ?: (priceHigh * 0.02).takeIf { it > 0.0 } ?: 1.0
    val room = priceSpan * (MaxScaleGrowth - 1) / 2
    val wanted = levels?.values.orEmpty()
    val low = maxOf(priceLow - room, minOf(priceLow, wanted.minOrNull() ?: priceLow))
    val high = minOf(priceHigh + room, maxOf(priceHigh, wanted.maxOrNull() ?: priceHigh))
    val span = (high - low).takeIf { it > 0.0 } ?: 1.0

    val lineColor = PriceRole.market
    val stopColor = PriceRole.stop
    val targetColor = PriceRole.target
    val entryColor = MaterialTheme.colorScheme.onSurface
    val paidColor = MaterialTheme.colorScheme.outline
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Right-aligned against the chart's own edge, in the colour of the line each names. Measured
    // here rather than in the draw, which cannot compose.
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labels = remember(levels, low, high, labelStyle, measurer, stopColor, targetColor, entryColor, paidColor) {
        buildList {
            levels ?: return@buildList
            levels.stopLoss?.let { add(ChartLabel(it, "stop " + formatPrice(it), stopColor)) }
            val bandLow = levels.entryLow
            val bandHigh = levels.entryHigh
            when {
                bandLow != null && bandHigh != null && bandLow != bandHigh -> add(
                    ChartLabel(
                        (bandLow + bandHigh) / 2,
                        "entry " + formatPrice(bandLow) + "–" + formatPrice(bandHigh),
                        entryColor,
                    ),
                )

                else -> (bandLow ?: bandHigh)?.let {
                    add(ChartLabel(it, "entry " + formatPrice(it), entryColor))
                }
            }
            levels.paid?.let { add(ChartLabel(it, "you paid " + formatPrice(it), paidColor)) }
            levels.target1?.let { add(ChartLabel(it, "target 1 " + formatPrice(it), targetColor)) }
            levels.target2?.let { add(ChartLabel(it, "target 2 " + formatPrice(it), targetColor)) }
        }.map { label ->
            // The arrow is what separates a level pinned to the edge from one that really sits
            // there, which is the whole risk of a capped scale.
            val text = when {
                label.value > high -> "↑ " + label.text
                label.value < low -> "↓ " + label.text
                else -> label.text
            }
            MeasuredLabel(label.value, measurer.measure(text, labelStyle), label.color)
        }
    }
    // With no levels drawn, the two figures worth printing are the ends of what is on screen: a
    // shape with no numbers on it cannot say whether a climb was three percent or forty.
    val edges = remember(levels, priceLow, priceHigh, labelStyle, measurer, markColor) {
        if (levels != null) {
            emptyList()
        } else {
            listOf(
                MeasuredLabel(priceHigh, measurer.measure(formatPrice(priceHigh) + " high", labelStyle), markColor),
                MeasuredLabel(priceLow, measurer.measure(formatPrice(priceLow) + " low", labelStyle), markColor),
            )
        }
    }

    val density = LocalDensity.current
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = "${points.size} sessions, " +
                    formatPrice(closes.first()) + " to " + formatPrice(closes.last()) +
                    (levels?.let { ", with levels from " + it.source } ?: "")
            },
    ) {
        val stroke = LineStroke.toPx()
        val top = stroke / 2
        val usable = size.height - stroke
        fun y(value: Double) =
            top + ((1.0 - ((value.coerceIn(low, high) - low) / span)) * usable).toFloat()

        val step = size.width / (points.size - 1)

        if (levels != null) {
            val dash = PathEffect.dashPathEffect(
                floatArrayOf(DashOn.toPx(), DashOff.toPx()),
                0f,
            )
            val bandLow = levels.entryLow
            val bandHigh = levels.entryHigh
            if (bandLow != null && bandHigh != null && bandLow != bandHigh) {
                val topY = y(maxOf(bandLow, bandHigh))
                val bottomY = y(minOf(bandLow, bandHigh))
                drawRect(
                    color = entryColor.copy(alpha = BandAlpha),
                    topLeft = Offset(0f, topY),
                    // A band collapsed by the scale is still a band, so it keeps a visible height.
                    size = Size(size.width, maxOf(bottomY - topY, MinBand.toPx())),
                )
            } else {
                (bandLow ?: bandHigh)?.let { guide(y(it), entryColor, dash, size.width) }
            }
            levels.stopLoss?.let { guide(y(it), stopColor, dash, size.width) }
            levels.target1?.let { guide(y(it), targetColor, dash, size.width) }
            levels.target2?.let { guide(y(it), targetColor, dash, size.width) }
            // Solid, and alone in that among the levels: what the reader paid is a fact, where
            // every other line here is somebody's claim about where the price should go.
            levels.paid?.let { paid ->
                drawLine(
                    color = paidColor,
                    start = Offset(0f, y(paid)),
                    end = Offset(size.width, y(paid)),
                    strokeWidth = GuideStroke.toPx(),
                )
            }
        }

        val path = Path()
        points.forEachIndexed { index, session ->
            val at = Offset(index * step, y(session.close!!))
            if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
        }
        drawPath(
            path,
            color = lineColor,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val ring = CallRing.toPx()
        points.forEachIndexed { index, session ->
            if (session.date !in calls) return@forEachIndexed
            val at = Offset(index * step, y(session.close!!))
            drawCircle(color = on, radius = ring, center = at)
            drawCircle(
                color = markColor,
                radius = ring,
                center = at,
                style = Stroke(width = RingStroke.toPx()),
            )
        }
        // Where it stands now, which is the point on the line every distance is measured from.
        drawCircle(
            color = lineColor,
            radius = LatestDot.toPx(),
            center = Offset(size.width, y(closes.last())),
        )

        val placed = layoutChartLabels(
            (labels + edges).map { y(it.value) },
            with(density) { LabelHeight.toPx() },
            size.height,
        )
        (labels + edges).forEachIndexed { index, label ->
            val left = size.width - label.layout.size.width - LabelInset.toPx()
            // A pad of the surface behind it, so a label never has to be read through the price
            // line or a guide passing under it.
            drawRoundRect(
                color = on,
                topLeft = Offset(left - LabelPad.toPx(), placed[index]),
                size = Size(
                    label.layout.size.width + LabelPad.toPx() * 2,
                    label.layout.size.height.toFloat(),
                ),
                cornerRadius = CornerRadius(LabelPad.toPx()),
            )
            drawText(label.layout, color = label.color, topLeft = Offset(left, placed[index]))
        }
    }
}

private fun DrawScope.guide(y: Float, color: Color, dash: PathEffect, width: Float) {
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(width, y),
        strokeWidth = GuideStroke.toPx(),
        pathEffect = dash,
    )
}

/**
 * Where each right-hand label actually sits, once none of them overlap.
 *
 * Labels are placed at their own level and then pushed **down** in one pass, in the order they are
 * given: a chart carrying five levels routinely has two within a couple of percent of each other,
 * and two labels drawn on top of one another are worse than no labels at all. The last one is
 * pulled back inside the bottom edge and the pass runs once more upward, so a crowd at the foot of
 * the chart cannot push a label off it.
 */
private fun layoutChartLabels(wanted: List<Float>, rowHeight: Float, height: Float): List<Float> {
    if (wanted.isEmpty()) return emptyList()
    val order = wanted.indices.sortedBy { wanted[it] }
    val placed = FloatArray(wanted.size)
    var previous = Float.NEGATIVE_INFINITY
    order.forEach { index ->
        // Centred on its own line, then pushed clear of whatever is already above it.
        val at = maxOf(wanted[index] - rowHeight / 2, previous + rowHeight, 0f)
        placed[index] = at
        previous = at
    }
    val bottom = height - rowHeight
    var ceiling = bottom
    order.asReversed().forEach { index ->
        if (placed[index] > ceiling) placed[index] = ceiling
        ceiling = placed[index] - rowHeight
    }
    return placed.toList()
}

private data class ChartLabel(val value: Double, val text: String, val color: Color)

private data class MeasuredLabel(val value: Double, val layout: TextLayoutResult, val color: Color)

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

/** Tall enough to carry five guides and a price line without either becoming furniture. */
private val ChartHeight = 150.dp

/**
 * How far the scale may grow beyond the price's own range to take the levels in.
 *
 * Two and a half times the span the stock actually covered. An ordinary call's stop and targets sit
 * within that of a month's trading, so they are drawn where they really are; a level far outside it
 * is pinned rather than allowed to flatten the line the chart exists to show.
 */
private const val MaxScaleGrowth = 2.5

private val LineStroke = 2.dp

private val GuideStroke = 1.dp

private val DashOn = 4.dp

private val DashOff = 4.dp

/** Faint enough to read the price line through, strong enough to find the band's edges. */
private const val BandAlpha = 0.14f

private val MinBand = 2.dp

private val CallRing = 4.dp

private val RingStroke = 1.5.dp

private val LatestDot = 3.5.dp

private val LabelInset = 2.dp

private val LabelPad = 3.dp

/** A `labelSmall` line plus the gap that keeps two of them apart. */
private val LabelHeight = 15.dp

/** The track, plus the overhang the close marker is drawn with above and below it. */
private val RangeHeight = 14.dp

private val RangeBarHeight = 6.dp

private val RangeMarkWidth = 3.dp
