package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.ui.theme.palette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Draws stop loss, entry, both targets and where the price is on one axis.
 *
 * The numbers alone do not show whether a trade risks little for a lot or the reverse; placing
 * them proportionally makes the balance readable at a glance, which is the question a
 * recommendation is actually answering.
 *
 * Redrawn on 2026-11-11 to the owner's mockup: one plain line, four upright ticks of one size
 * (red for the stop, ink for the entry, green for the targets), a round dot on the line where the
 * price is with its figure above it, and every level's price on one row beneath. The zones that
 * coloured the line between the levels, the ticks that pointed up or down and the arrow under the
 * line all went with it.
 *
 * @param reached where the dot sits. What that means depends on what is being drawn: on a call it
 * is the peak inside the judged window, and on a trade in the Portfolio it is where the price
 * stands now. The axis is still scaled by the levels themselves, so the risk-to-reward shape does
 * not change with the market; a price beyond them is pinned to the end it passed and its dot
 * becomes an arrowhead pointing out.
 * @param compact no printed prices and a lower band, for a table row or a list row where the
 * figures already sit in their own columns and the shape is all the row has room for.
 * @param travelled the lowest and highest the price reached, drawn as a pale stripe on the line.
 * It is clamped to the levels like [reached], so a stock that ran past target 2 shows a stripe to
 * the end rather than stretching the axis.
 * @param entryLabel words printed before the entry price, "You paid" on a trade. The ladder prints
 * no word for any level otherwise, so this is the one place a level is named on it.
 */
@Composable
internal fun PriceLadder(
    point: RecommendationDataPoint,
    modifier: Modifier = Modifier,
    reached: Double? = null,
    compact: Boolean = false,
    travelled: ClosedFloatingPointRange<Double>? = null,
    entryLabel: String? = null,
) = PriceLadder(
    stopLoss = point.stopLoss,
    entryLow = point.buyPriceLow ?: point.buyPrice,
    entryHigh = point.buyPriceHigh ?: point.buyPrice,
    target1 = point.target1,
    target2 = point.target2,
    modifier = modifier,
    reached = reached,
    compact = compact,
    travelled = travelled,
    entryLabel = entryLabel,
)

/**
 * The same ladder, taking the levels themselves.
 *
 * A scored call carries the same five numbers under different names and is not a
 * [RecommendationDataPoint] - it is what one became after the prices caught up with it. The drawing
 * never needed the source object, only the levels, so this is what actually draws and the overload
 * above is the Results tab's way in.
 */
@Composable
internal fun PriceLadder(
    stopLoss: Double?,
    entryLow: Double?,
    entryHigh: Double?,
    target1: Double?,
    target2: Double?,
    modifier: Modifier = Modifier,
    reached: Double? = null,
    compact: Boolean = false,
    travelled: ClosedFloatingPointRange<Double>? = null,
    entryLabel: String? = null,
) {
    val levels = listOfNotNull(stopLoss, entryLow, entryHigh, target1, target2)
    if (levels.size < 2) return

    val low = levels.min()
    val high = levels.max()
    val span = high - low
    if (span <= 0.0) return

    // Loss red for the stop, ink for the entry the rest are read against, gain green for the
    // targets, cobalt for where the price is - the lighter cobalt the palette keeps for text at
    // night, since the deep one sinks into a dark card.
    val p = palette
    val nowColor = p.heroInk
    val grow = com.ikverse.egxanalyzer.ui.kit.rememberDrawIn(key = listOf(levels, reached, travelled), durationMs = 900)
    val travelledOnAxis = travelled?.let { clampToAxis(it, low, high) }

    // The group is what has to stay together when labels are pushed around: the two ends of an
    // entry range are one fact, and they print side by side.
    val marks = buildList {
        stopLoss?.let { add(Mark(it, p.loss, STOP_GROUP)) }
        entryLow?.let { add(Mark(it, p.ink, ENTRY_GROUP)) }
        entryHigh?.takeIf { it != entryLow }?.let { add(Mark(it, p.ink, ENTRY_GROUP)) }
        target1?.let { add(Mark(it, p.gain, TARGET1_GROUP)) }
        target2?.let { add(Mark(it, p.gain, TARGET2_GROUP)) }
    }

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labels = ladderLabelTexts(
        marks.map { it.value },
        compact,
        entryIndex = marks.indexOfFirst { it.group == ENTRY_GROUP }.takeIf { it >= 0 },
        entryLabel = entryLabel,
    ).map { text ->
        // The word in the app's face and the price in the figures' one, as everywhere else.
        val words = entryLabel?.let { "$it " }?.takeIf { text.startsWith(it) }.orEmpty()
        measurer.measure(
            buildAnnotatedString {
                append(words)
                figure(text.removePrefix(words))
            },
            labelStyle,
        )
    }
    // Where the price is, printed over its dot. A compact ladder prints nothing at all.
    val nowLabel = reached?.takeUnless { compact }?.let {
        measurer.measure(
            formatPrice(it),
            labelStyle.copy(fontFamily = TabularFigures, fontWeight = FontWeight.SemiBold),
        )
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val insetPx = with(density) { LadderInset.toPx() }
        val usable = widthPx - insetPx * 2
        if (usable <= 0f) return@BoxWithConstraints

        fun axis(value: Double) = insetPx + ((value.coerceIn(low, high) - low) / span).toFloat() * usable

        // A compact ladder prints nothing, so there is nothing to lay out and no rows below.
        val slots = if (labels.isEmpty()) emptyList() else layoutPriceLabels(
            marks.map { axis(it.value) },
            labels.map { it.size.width.toFloat() },
            widthPx,
            with(density) { LabelGap.toPx() },
            marks.map { it.group },
        )
        val rowsBelow = slots.maxOfOrNull { it.row + 1 } ?: 0

        val band = if (compact) CompactTrackBand else TrackBand
        val above = if (nowLabel != null) LabelRowHeight else 0.dp

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(above + band + LabelRowHeight * rowsBelow)
                // Its own layer, so the ring round the dot can be cut out of the line under it
                // and show whatever the ladder is drawn on, card or tile.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                // Drawn in from the low end, the way the kit's price lines arrive.
                .drawWithContent {
                    clipRect(right = size.width * grow.value) { this@drawWithContent.drawContent() }
                },
        ) {
            val top = above.toPx()
            val bandPx = band.toPx()
            val y = top + bandPx / 2f

            val track = TrackThickness.toPx()
            drawRoundRect(
                color = p.raised,
                topLeft = Offset(insetPx, y - track / 2),
                size = Size(usable, track),
                cornerRadius = CornerRadius(track / 2),
            )

            // The lowest to the highest the price reached, on the line's own thickness.
            travelledOnAxis?.let { range ->
                val start = axis(range.start)
                val end = axis(range.endInclusive)
                drawRoundRect(
                    color = nowColor.copy(alpha = TravelledAlpha),
                    topLeft = Offset(start, y - track / 2),
                    size = Size(max(end - start, track), track),
                    cornerRadius = CornerRadius(track / 2),
                )
            }

            val tick = (if (compact) CompactTickLength else TickLength).toPx()

            // A call that names a range to buy in: a pale band between its two ticks. A single
            // entry price is one tick and needs no band.
            if (entryLow != null && entryHigh != null && entryLow != entryHigh) {
                val start = min(axis(entryLow), axis(entryHigh))
                val end = max(axis(entryLow), axis(entryHigh))
                val height = tick * 0.7f
                drawRect(
                    color = p.ink.copy(alpha = EntryBandAlpha),
                    topLeft = Offset(start, y - height / 2),
                    size = Size(end - start, height),
                )
            }

            val tickWidth = TickWidth.toPx()
            marks.forEach { mark ->
                drawRoundRect(
                    color = mark.color,
                    topLeft = Offset(axis(mark.value) - tickWidth / 2, y - tick / 2),
                    size = Size(tickWidth, tick),
                    cornerRadius = CornerRadius(tickWidth / 2),
                )
            }

            reached?.let {
                drawNow(
                    x = axis(it),
                    y = y,
                    radius = (if (compact) CompactDotRadius else DotRadius).toPx(),
                    ring = DotRing.toPx(),
                    beyondTop = it > high,
                    beyondBottom = it < low,
                    color = nowColor,
                )
            }

            nowLabel?.let { layout ->
                val left = (axis(reached ?: low) - layout.size.width / 2f)
                    .coerceIn(0f, max(0f, size.width - layout.size.width))
                drawText(textLayoutResult = layout, color = nowColor, topLeft = Offset(left, 0f))
            }

            val belowTop = top + bandPx
            val rowHeight = LabelRowHeight.toPx()
            labels.forEachIndexed { index, layout ->
                val slot = slots[index]
                drawText(
                    textLayoutResult = layout,
                    color = p.muted,
                    topLeft = Offset(slot.left, belowTop + slot.row * rowHeight),
                )
            }
        }
    }
}

/**
 * The prices printed under the marks, in mark order; none at all on a compact ladder.
 *
 * [entryLabel] goes before the price at [entryIndex] only - the low end of a range - so a range
 * reads "You paid 10.00  10.40" rather than naming itself twice.
 */
internal fun ladderLabelTexts(
    values: List<Double>,
    compact: Boolean,
    entryIndex: Int? = null,
    entryLabel: String? = null,
): List<String> =
    if (compact) {
        emptyList()
    } else {
        values.mapIndexed { index, value ->
            val price = formatPrice(value)
            if (index == entryIndex && entryLabel != null) "$entryLabel $price" else price
        }
    }

/** [range] held inside the axis, the way [PriceLadder] pins a price past either end. */
internal fun clampToAxis(
    range: ClosedFloatingPointRange<Double>,
    low: Double,
    high: Double,
): ClosedFloatingPointRange<Double> = range.start.coerceIn(low, high)..range.endInclusive.coerceIn(low, high)

/** Where a price label sits: its left edge, and which row under the line. */
internal data class LabelSlot(val left: Float, val row: Int)

/**
 * Places each label under its mark, with no two ever touching.
 *
 * Every price is on the first row under the line wherever it fits, which on an ordinary call is
 * all of them. One that would touch a neighbour drops to the next row down, and so on: since
 * 2026-11-11 nothing is printed over the line but where the price is, so the crowding has only
 * one way to go.
 *
 * Labels sharing a [groups] entry describe one fact and are placed as **one block, side by side**,
 * centred on the span of their own marks - see [packGroup]. A range is a pair of numbers read
 * together, so the two have to sit on one line; centring each on its own mark instead put them on
 * top of each other whenever the range was narrow. Packing costs each end a few pixels of drift
 * off its own tick, which a range can afford precisely because the two ends are one fact.
 *
 * A group of one packs to exactly the same place centring it would give, so an ordinary level is
 * unaffected by any of this.
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

    // Each row under the line, as the stretches already taken in it.
    val rows = mutableListOf<MutableList<ClosedFloatingPointRange<Float>>>()

    // A blank label takes no room, so it never costs the ladder a row.
    centers.indices.filter { widths[it] <= 0f }.forEach { slots[it] = LabelSlot(centers[it], row = 0) }

    // Groups in the order their earliest mark sits on the axis, so the ladder still reads
    // left to right.
    centers.indices
        .groupBy { groups.getOrElse(it) { _ -> it } }
        .values
        .sortedBy { members -> members.minOf { centers[it] } }
        .forEach { group ->
            val members = group.filter { slots[it] == null }
            if (members.isEmpty()) return@forEach
            val ordered = members.sortedBy { centers[it] }
            // Packed once and then placed as a unit, so the group occupies one row wherever it
            // lands: half a range on one row and half on the next is worse than all of it lower.
            val packed = packGroup(ordered, centers, widths, trackWidth, gap)
            val blockLeft = packed.first()
            val blockEnd = packed.last() + widths[ordered.last()] + gap
            var row = 0
            while (row < rows.size && rows[row].any { blockLeft < it.endInclusive && blockEnd > it.start }) row++
            if (row == rows.size) rows += mutableListOf<ClosedFloatingPointRange<Float>>()
            rows[row] += blockLeft..blockEnd
            ordered.forEachIndexed { position, index ->
                slots[index] = LabelSlot(packed[position], row = row)
            }
        }
    return slots.map { requireNotNull(it) }
}

/**
 * One group's labels laid side by side, as a block centred on the span of its marks.
 *
 * Returns a left edge per member, in the order given - which must be axis order, so the prices
 * still read left to right. The block is clamped inside the track like any single label, so a range
 * at either end of the ladder stays readable instead of running off it.
 */
private fun packGroup(
    ordered: List<Int>,
    centers: List<Float>,
    widths: List<Float>,
    trackWidth: Float,
    gap: Float,
): List<Float> {
    val blockWidth = ordered.sumOf { widths[it].toDouble() }.toFloat() + gap * (ordered.size - 1)
    val centre = (centers[ordered.first()] + centers[ordered.last()]) / 2f
    var left = (centre - blockWidth / 2f).coerceIn(0f, max(0f, trackWidth - blockWidth))
    return ordered.map { index ->
        val start = left
        left += widths[index] + gap
        start
    }
}

/** One level on the axis: where it sits, what colour it is, and what it groups with. */
private data class Mark(
    val value: Double,
    val color: Color,
    /** Labels sharing this describe one fact and move together. See [layoutPriceLabels]. */
    val group: Int,
)

/**
 * Where the price is: a round dot on the line, with a ring cut out round it so it stands clear of
 * the line, the stripe and any tick it lands on.
 *
 * Past the top or the bottom the dot becomes an arrowhead pointing that way, because a dot pinned
 * to the end cannot say "beyond this" - only "exactly here", which would be a lie.
 */
private fun DrawScope.drawNow(
    x: Float,
    y: Float,
    radius: Float,
    ring: Float,
    beyondTop: Boolean,
    beyondBottom: Boolean,
    color: Color,
) {
    // Cut out of this canvas's own layer, so the ring is whatever the ladder is drawn on.
    drawCircle(Color.Black, radius + ring, Offset(x, y), blendMode = BlendMode.Clear)
    if (!beyondTop && !beyondBottom) {
        drawCircle(color, radius, Offset(x, y))
        return
    }
    val reach = radius + ring * 0.5f
    val way = if (beyondTop) 1f else -1f
    val path = Path().apply {
        moveTo(x + way * reach, y)
        lineTo(x - way * reach * 0.6f, y - reach)
        lineTo(x - way * reach * 0.6f, y + reach)
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

/** Room for the ticks and the dot's ring, and nothing else. */
private val TrackBand = 20.dp

/** The compact ladder's band, for a row. */
private val CompactTrackBand = 14.dp
private val LabelRowHeight = 15.dp
private val LabelGap = 6.dp

private val TrackThickness = 6.dp
private val TickWidth = 3.dp
private val TickLength = 16.dp
private val CompactTickLength = 12.dp
private val DotRadius = 4.dp
private val CompactDotRadius = 3.5.dp
private val DotRing = 3.dp

private const val TravelledAlpha = 0.32f
private const val EntryBandAlpha = 0.16f

// Which labels describe one fact. Only an entry range has two ends; the rest stand alone.
private const val STOP_GROUP = 0
private const val ENTRY_GROUP = 1
private const val TARGET1_GROUP = 2
private const val TARGET2_GROUP = 3
