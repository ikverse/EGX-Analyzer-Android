package com.ikverse.egxanalyzer.ui

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.theme.palette

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
 * @param reached how far the stock actually got, drawn as an arrow beside the axis. What that
 * means depends on what is being drawn: on a call it is the peak inside the judged window, and on
 * a trade in the Portfolio it is where the price stands now - one question, "where did this end up
 * against the levels", asked of two things. The axis is still scaled by the levels themselves, so
 * the risk-to-reward shape does not change with the market; a price beyond them is pinned to the
 * end it passed and points out.
 * @param compact no printed prices and a lower band, for a table row or a list row where the
 * figures already sit in their own columns and the shape is all the row has room for.
 * @param travelled the lowest and highest the price reached, drawn as a stripe along the line. It
 * is clamped to the levels like [reached], so a stock that ran past target 2 shows a stripe to the
 * end rather than stretching the axis.
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

    // The palette's own since 5.0: loss red for the stop, ink for the entry the rest are read
    // against, gain green for the targets, cobalt for how far the market actually got - the
    // lighter cobalt the palette keeps for text at night, since the deep one sinks into a dark card.
    val p = palette
    val stopColor = p.loss
    val entryColor = p.ink
    val targetColor = p.gain
    val trackColor = p.raised
    val reachedColor = p.heroInk
    val grow = com.ikverse.egxanalyzer.ui.kit.rememberDrawIn(key = listOf(levels, reached, travelled), durationMs = 900)
    val zones = ladderZones(stopLoss, entryLow, entryHigh, target1, target2)
    val travelledOnAxis = travelled?.let { clampToAxis(it, low, high) }

    // The group is what has to stay together when labels are pushed around: the two ends of the
    // entry band are one fact, and a range printed with its floor below the line and its ceiling
    // above it stops reading as a range at all.
    val marks = buildList {
        stopLoss?.let { add(Mark(it, stopColor, STOP_GROUP, MarkSide.BELOW)) }
        entryLow?.let { add(Mark(it, entryColor, ENTRY_GROUP, MarkSide.CENTRE)) }
        entryHigh?.takeIf { it != entryLow }
            ?.let { add(Mark(it, entryColor, ENTRY_GROUP, MarkSide.CENTRE)) }
        target1?.let { add(Mark(it, targetColor, TARGET1_GROUP, MarkSide.ABOVE)) }
        target2?.let { add(Mark(it, targetColor, TARGET2_GROUP, MarkSide.ABOVE)) }
        // Drawn as an arrow rather than a tick, so its side is never read. See drawReached.
        reached?.let { add(Mark(it, reachedColor, REACHED_GROUP, MarkSide.CENTRE)) }
    }

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures)
    val labels = ladderLabelTexts(
        marks.map { it.value },
        compact,
        entryIndex = marks.indexOfFirst { it.group == ENTRY_GROUP }.takeIf { it >= 0 },
        entryLabel = entryLabel,
        reachedIndex = marks.indexOfFirst { it.group == REACHED_GROUP }.takeIf { it >= 0 },
    ).mapIndexed { index, text ->
        measurer.measure(text, labelStyle) to marks[index].color
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val insetPx = with(density) { LadderInset.toPx() }
        val usable = widthPx - insetPx * 2
        if (usable <= 0f) return@BoxWithConstraints

        fun axis(value: Double) = insetPx + ((value.coerceIn(low, high) - low) / span).toFloat() * usable

        // A compact ladder prints nothing, so there is nothing to lay out and every row count
        // below comes to zero.
        val slots = if (labels.isEmpty()) emptyList() else layoutPriceLabels(
            marks.map { axis(it.value) },
            labels.map { it.first.size.width.toFloat() },
            widthPx,
            with(density) { LabelGap.toPx() },
            marks.map { it.group },
            pinnedBelow = marks.indexOfFirst { it.group == REACHED_GROUP }.takeIf { it >= 0 },
        )
        val rowsAbove = slots.filter { it.above }.maxOfOrNull { it.row + 1 } ?: 0
        val rowsBelow = slots.filterNot { it.above }.maxOfOrNull { it.row + 1 } ?: 0

        // Where the price stands is always under the line, arrow and figure together, so it is
        // found in the same place on every card rather than wherever the levels left room.
        val reachedAbove = false

        val band = if (compact) CompactTrackBand else TrackBand
        val rowHeight = with(density) { LabelRowHeight.toPx() }
        val trackHeight = with(density) { band.toPx() }
        val reachedBand = with(density) { ReachedBand.toPx() }

        val above = LabelRowHeight * rowsAbove + if (reached != null && reachedAbove) ReachedBand else 0.dp
        val below = LabelRowHeight * rowsBelow + if (reached != null && !reachedAbove) ReachedBand else 0.dp

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(above + band + below)
                // Drawn in from the low end, the way the kit's price lines arrive.
                .drawWithContent {
                    clipRect(right = size.width * grow.value) { this@drawWithContent.drawContent() }
                },
        ) {
            val top = with(density) { above.toPx() }
            val y = top + trackHeight / 2f

            val track = 6.dp.toPx()
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(insetPx, y - track / 2),
                size = Size(usable, track),
                cornerRadius = CornerRadius(track / 2),
            )

            // What lies between the levels: the ground given up to the stop, the stretch to the
            // first target, and the one beyond it. Drawn on the line's own thickness, under the
            // entry band and the ticks, so the levels still read as the edges of the zones.
            zones.forEach { zone ->
                val start = axis(min(zone.from, zone.to))
                val end = axis(max(zone.from, zone.to))
                if (end - start <= 0f) return@forEach
                drawRect(
                    color = when (zone.kind) {
                        ZoneKind.LOSS -> p.zoneLoss
                        ZoneKind.NEAR -> p.zoneNear
                        ZoneKind.FAR -> p.zoneFar
                    },
                    topLeft = Offset(start, y - track / 2),
                    size = Size(end - start, track),
                )
            }

            travelledOnAxis?.let { range ->
                val start = axis(range.start)
                val end = axis(range.endInclusive)
                val stripe = 2.dp.toPx()
                drawRoundRect(
                    color = reachedColor,
                    topLeft = Offset(start, y - stripe / 2),
                    size = Size(max(end - start, stripe), stripe),
                    cornerRadius = CornerRadius(stripe / 2),
                )
            }

            if (entryLow != null && entryHigh != null) {
                val start = min(axis(entryLow), axis(entryHigh))
                val end = max(axis(entryLow), axis(entryHigh))
                drawRoundRect(
                    color = p.heroInk.copy(alpha = 0.28f),
                    topLeft = Offset(start, y - BAND_HEIGHT / 2),
                    // A single entry price collapses to zero width, so keep it visible.
                    size = Size(max(end - start, 2f), BAND_HEIGHT),
                    cornerRadius = CornerRadius(BAND_HEIGHT / 2),
                )
            }

            // Never longer than half the band, so a tick on one side of the line cannot reach the
            // price labels above or below it at a low display density. At the two densities this
            // app actually runs at the clamp does nothing; it is what keeps a third from breaking.
            val markLength = min(MARKER_HEIGHT, trackHeight / 2f)
            marks.forEach { mark ->
                if (mark.value == reached) return@forEach
                drawMarker(axis(mark.value), y, mark.color, mark.side, markLength)
            }

            reached?.let {
                drawReached(axis(it), top, trackHeight, reachedBand, reachedAbove, it > high, it < low, reachedColor)
            }

            val belowTop = top + trackHeight + if (reached != null && !reachedAbove) reachedBand else 0f
            val aboveBottom = top - if (reached != null && reachedAbove) reachedBand else 0f
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

/**
 * The prices printed under the marks, in mark order; none at all on a compact ladder.
 *
 * [entryLabel] goes before the price at [entryIndex] only - the low end of a band - so a range
 * reads "You paid 10.00  10.40" rather than naming itself twice.
 */
internal fun ladderLabelTexts(
    values: List<Double>,
    compact: Boolean,
    entryIndex: Int? = null,
    entryLabel: String? = null,
    reachedIndex: Int? = null,
): List<String> =
    if (compact) {
        emptyList()
    } else {
        // A level that prints the same figure as where the price stands is left blank: its tick is
        // still drawn, and the figure is read once, under the arrow.
        val reachedText = reachedIndex?.let { values.getOrNull(it) }?.let(::formatPrice)
        values.mapIndexed { index, value ->
            val price = formatPrice(value)
            when {
                index != reachedIndex && price == reachedText -> ""
                index == entryIndex && entryLabel != null -> "$entryLabel $price"
                else -> price
            }
        }
    }

/** [range] held inside the axis, the way [PriceLadder] pins a price past either end. */
internal fun clampToAxis(
    range: ClosedFloatingPointRange<Double>,
    low: Double,
    high: Double,
): ClosedFloatingPointRange<Double> = range.start.coerceIn(low, high)..range.endInclusive.coerceIn(low, high)

/** Which stretch of a call a zone is: given up to the stop, on the way to target 1, or beyond it. */
internal enum class ZoneKind { LOSS, NEAR, FAR }

internal data class LadderZone(val from: Double, val to: Double, val kind: ZoneKind)

/**
 * The stretches between a call's levels: stop to entry, entry to target 1, target 1 to target 2.
 *
 * Each zone starts from the end of the entry band facing it, so the band itself is never coloured
 * as loss or gain - it is where the trade opens, not a place it went. A call with no first target
 * runs the near zone to target 2; one with no stop simply has no loss zone.
 */
internal fun ladderZones(
    stopLoss: Double?,
    entryLow: Double?,
    entryHigh: Double?,
    target1: Double?,
    target2: Double?,
): List<LadderZone> {
    val ends = listOfNotNull(entryLow, entryHigh)
    if (ends.isEmpty()) return emptyList()
    fun nearest(to: Double) = ends.minBy { abs(it - to) }
    return buildList {
        stopLoss?.let { add(LadderZone(it, nearest(it), ZoneKind.LOSS)) }
        val first = target1 ?: target2
        first?.let { add(LadderZone(nearest(it), it, ZoneKind.NEAR)) }
        if (target1 != null && target2 != null) add(LadderZone(target1, target2, ZoneKind.FAR))
    }
}

/** Where a price label sits: its left edge, which side of the track, and how far out. */
internal data class LabelSlot(val left: Float, val row: Int, val above: Boolean = false)

/**
 * Places each label beside its mark, with no two ever touching.
 *
 * One row below the line, and everything that will not fit in it goes above. [pinnedBelow] - where
 * the price stands - takes that row first, so it is always in the same place. There is deliberately
 * no second row below: a label pushed down sits further from the mark it belongs to than from its
 * neighbour's, and the eye reads it as belonging to the wrong price.
 *
 * Labels sharing a [groups] entry describe one fact and are placed as **one block, side by side**,
 * centred on the span of their own marks - see [packGroup]. A range is a pair of numbers read
 * together, so the two have to sit on one line; centring each on its own mark instead put them on
 * top of each other whenever the band was narrow, and the loser was bumped to a row of its own. A
 * ladder then grew a third line to carry one price, with the space beside it empty. Packing costs
 * each end a few pixels of drift off its own tick, which a range can afford precisely because the
 * two ends are one fact.
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
    pinnedBelow: Int? = null,
): List<LabelSlot> {
    val slots = arrayOfNulls<LabelSlot>(centers.size)

    // The single row under the line, as the stretches already taken, and as many rows above it as
    // the crowding needs.
    val below = mutableListOf<ClosedFloatingPointRange<Float>>()
    val aboveEnds = mutableListOf<Float>()

    // A blank label takes no room and sits under the line, so it never costs the ladder a row.
    centers.indices.filter { widths[it] <= 0f }.forEach { slots[it] = LabelSlot(centers[it], row = 0) }

    // The pinned label has the row under the line first, so everything else makes room for it.
    pinnedBelow?.takeIf { slots[it] == null }?.let { index ->
        val left = (centers[index] - widths[index] / 2f).coerceIn(0f, max(0f, trackWidth - widths[index]))
        slots[index] = LabelSlot(left, row = 0, above = false)
        below += left..(left + widths[index] + gap)
    }

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
            // lands. The row below takes the whole block or none of it: half a range down there is
            // worse than all of it out of the way.
            val packed = packGroup(ordered, centers, widths, trackWidth, gap)
            val blockLeft = packed.first()
            val blockEnd = packed.last() + widths[ordered.last()] + gap
            if (below.none { blockLeft < it.endInclusive && blockEnd > it.start }) {
                ordered.forEachIndexed { position, index ->
                    slots[index] = LabelSlot(packed[position], row = 0, above = false)
                }
                below += blockLeft..blockEnd
                return@forEach
            }
            var row = 0
            while (row < aboveEnds.size && blockLeft < aboveEnds[row]) row++
            if (row == aboveEnds.size) aboveEnds += 0f
            aboveEnds[row] = blockEnd
            ordered.forEachIndexed { position, index ->
                slots[index] = LabelSlot(packed[position], row = row, above = true)
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
private fun DrawScope.drawReached(
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

/** The compact ladder's band: room for the ticks and nothing else, for a row. */
private val CompactTrackBand = 14.dp

/** Just enough room beside the line for the arrow, without pushing the prices away. */
private val ReachedBand = 9.dp
private val LabelRowHeight = 15.dp
private val LabelGap = 6.dp

private const val BAND_HEIGHT = 18f
private const val MARKER_WIDTH = 3f
private const val MARKER_HEIGHT = 26f

// Which labels describe one fact. Only the entry band has two ends; the rest stand alone.
private const val STOP_GROUP = 0
private const val ENTRY_GROUP = 1
private const val TARGET1_GROUP = 2
private const val TARGET2_GROUP = 3
private const val REACHED_GROUP = 4
