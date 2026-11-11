package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.core.animateFloat

import com.ikverse.egxanalyzer.ui.theme.palette

import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.theme.AppPalette
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Scoring
import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt

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
internal enum class ChartRange(val label: String, val title: String, private val period: Period) {
    WEEK("1W", "Past week", Period.ofWeeks(1)),
    /**
     * Since the newest call on the stock, with a few sessions before it so the line arrives at the
     * call rather than starting on it. Offered only where there is a call; without one it falls back
     * to a month, so a stored choice can never draw an empty box.
     */
    CALL("Call", "Since the call", Period.ofMonths(1)),
    MONTH("1M", "Past month", Period.ofMonths(1)),
    TWO_MONTHS("2M", "Past 2 months", Period.ofMonths(2)),
    THREE_MONTHS("3M", "Past 3 months", Period.ofMonths(3)),
    SIX_MONTHS("6M", "Past 6 months", Period.ofMonths(6)),
    ;

    fun since(today: LocalDate, callOn: LocalDate? = null): LocalDate =
        if (this == CALL && callOn != null) callOn.minusDays(CallLeadDays) else today.minus(period)

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

        /** The days of line drawn before the call, so the reader sees what it was called into. */
        const val CallLeadDays = 7L
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
 * What the visible line adds up to, end to end, in percent.
 *
 * The figure the shape cannot carry: a chart scaled to its own range draws the same climb whether
 * the stock moved three percent or forty, which is the first thing a reader asks of it. Shared by
 * every screen that draws [PriceChart] over a range a reader can change, rather than computed once
 * for the sheet and left there for the next screen to work out again.
 */
internal fun List<DailySession>.rangeMove(): Double? {
    val priced = filter { it.close != null }
    if (priced.size < 2) return null
    val first = priced.first().close!!
    val last = priced.last().close!!
    if (first <= 0.0) return null
    return (last - first) / first * 100
}

/** What the line had done by the touched session, measured from the left-hand end of the range. */
internal fun List<DailySession>.moveTo(session: DailySession): Double? {
    val first = firstOrNull { it.close != null }?.close ?: return null
    val close = session.close ?: return null
    if (first <= 0.0) return null
    return (close - first) / first * 100
}

/**
 * Six months of closes for one ticker, fetched once per card rather than held on the record it is
 * about.
 *
 * Off the disk exactly as [StockSheet] reads it - a local read through [AppState.priceHistory], no
 * network - and only for a card that actually needs it: every other card on a tab keeps whatever it
 * already had, rather than every one paying for a query at once. The widest range rather than the
 * default one, for the same reason [StockSheet] fetches it once: a chart's own range chips slice
 * this in the composition, so no press ever waits on a second query.
 *
 * Shared between the position card and the Insights call card's own chart, which is what it is
 * named for rather than for either caller - a ticker's price history is the same six months
 * whichever card is asking.
 *
 * Refetched on every [AppState.performance] as well as on the ticker, which is what [StockSheet]'s
 * own fetch already keys on: `performance` is a fresh instance on every recompute, so keying on it
 * is what notices a price refresh landed and asks again, rather than going on showing whatever this
 * card fetched once. Without it, this was the one chart in the app - beside the stock sheet's own -
 * that kept drawing stale prices after every refresh.
 */
@Composable
internal fun rememberPriceHistory(appState: AppState, ticker: String): List<DailySession> {
    val key = remember(ticker) { Scoring.normalizeTicker(ticker) }
    var history by remember(key) { mutableStateOf(emptyList<DailySession>()) }
    LaunchedEffect(key, appState.performance) {
        history = appState.priceHistory(key, ChartRange.Widest.since(LocalDate.now()))
    }
    return history
}

/**
 * Every colour one chart is drawn in, so the same drawing sits on the stock sheet's dark tile and
 * on a plain card without a flag threaded through it.
 *
 * [surface] is what the chart sits on: the rings round a call's dot, the latest close and the
 * touched session are punched through in it, so the price line cannot be seen running through them.
 */
internal data class ChartColors(
    val surface: Color,
    val text: Color,
    /** Captions, the tag words and the date axis. */
    val soft: Color,
    val line: Color,
    /** A call's dot and the crosshair. */
    val mark: Color,
    val target: Color,
    val stop: Color,
    /** The stop's figure, which on the dark tile needs a paler coral than its line. */
    val stopText: Color,
    val entry: Color,
    val paid: Color,
    val nowFill: Color,
    val nowText: Color,
    /** The range selector's track, and a quiet button on the tile. */
    val track: Color,
    val chosen: Color,
    val onChosen: Color,
    /** Whether the latest close breathes: the market's own motion, on the stock sheet alone. */
    val breathes: Boolean,
)

/**
 * The stock sheet's chart tile: navy ink in the light theme, the raised graphite card in the dark
 * ones, and the same lime and coral on both.
 *
 * It was the cobalt hero tile until 2026-11-11, when the owner asked for a quieter ground.
 * [TileTone.INK] is not it: that tone swaps lightness with the theme and is a pale tile at night,
 * and this one is dark in every theme so the lime and the coral mean the same thing on both.
 */
internal fun chartTileColors(p: AppPalette): ChartColors {
    val light = p.ink.luminance() < 0.5f
    val text = if (light) Color.White else p.ink
    val soft = if (light) Color(0xFFC5CBD8) else p.muted
    return ChartColors(
        surface = if (light) p.ink else p.card,
        text = text,
        soft = soft,
        line = text,
        mark = text,
        target = p.highlight,
        stop = p.alert,
        stopText = Color(0xFFFFB3A1),
        entry = text,
        paid = soft,
        nowFill = p.highlight,
        nowText = p.onTile,
        track = if (light) Color(0xFF26314A) else p.raised,
        chosen = p.highlight,
        onChosen = p.onTile,
        breathes = true,
    )
}

/** The chart on a plain surface: a position's card, a call's sheet. */
internal fun chartCardColors(p: AppPalette, surface: Color = p.card, track: Color = p.raised) = ChartColors(
    surface = surface,
    text = p.ink,
    soft = p.muted,
    line = p.heroInk,
    mark = p.ink,
    target = p.gain,
    stop = p.loss,
    stopText = p.loss,
    entry = p.ink,
    paid = p.muted,
    nowFill = p.ink,
    nowText = p.onInk,
    track = track,
    chosen = p.ink,
    onChosen = p.onInk,
    breathes = false,
)

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
 * done in the head over a column of figures on another card.
 *
 * **Every figure is in a column of its own on the right, never on the plot** (2026-11-11). The
 * labels used to sit on the line's own ground, each on a patch of the surface, and slid sideways to
 * dodge a call's ring - so four levels landed at four places and each patch cut the line. A level
 * is a hairline across the plot now, and its name and price are a tag in the column, joined to the
 * hairline by a short leader. Levels a couple of percent apart are the ordinary case, so the tags
 * are spread by [stackTags] and the leaders say which is which. The latest close has a tag of its
 * own, filled, because it is the figure every distance is measured from.
 *
 * **The scale may grow to fit the levels, but only so far.** Honouring a target the stock has been
 * nowhere near would squash a month of real movement into a flat line at the bottom of the box - so
 * the scale is the price range, extended by at most [MaxScaleGrowth] of its own span to take the
 * levels in. An ordinary call's levels sit inside that and are drawn where they really are; one far
 * outside is **pinned to the edge it passed and its figure marked with an arrow**, which is the
 * rule [PriceLadder] already follows for a price beyond the levels, in the other direction.
 *
 * **A dot wherever a call was made**, with or without levels, because those belong to the stock
 * rather than to any one call's levels. Nothing else in the app can show whether the channels tend
 * to name this stock near its tops; here it is one glance.
 *
 * The three dates under the plot are drawn here, in the same canvas, so they end where the plot
 * ends rather than where the tag column does.
 */
@Composable
internal fun PriceChart(
    sessions: List<DailySession>,
    levels: ChartLevels?,
    calls: Set<LocalDate>,
    colors: ChartColors,
    /** The session under the reader's thumb, drawn with a crosshair. Null until one is touched. */
    selected: DailySession?,
    /** Reports what was touched, so the headline over the chart can read it out. */
    onSelect: (DailySession) -> Unit,
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

    // The latest close breathes on the stock sheet, a market's motion rather than an arrival's;
    // still with reduce motion on.
    val breath = if (colors.breathes && !LocalReduceMotion.current) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "latest").animateFloat(
            initialValue = 1f,
            targetValue = 1.3f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                tween(900, easing = UiEase),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "breath",
        ).value
    } else {
        1f
    }

    // Measured here rather than in the draw, which cannot compose.
    val measurer = rememberTextMeasurer()
    val wordStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
    val figureStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures, fontWeight = FontWeight.Bold)
    val latest = closes.last()
    val tags = remember(levels, latest, priceLow, priceHigh, low, high, colors, wordStyle, figureStyle, measurer) {
        chartTags(levels, latest, priceLow, priceHigh, colors)
            // Top to bottom, which is the order [stackTags] spreads them in.
            .sortedByDescending { it.value }
            .map { tag ->
                // The arrow is what separates a level pinned to the edge from one that really sits
                // there, which is the whole risk of a capped scale.
                val figure = when {
                    tag.value > high -> "↑ " + tag.figure
                    tag.value < low -> "↓ " + tag.figure
                    else -> tag.figure
                }
                MeasuredTag(tag, measurer.measure(tag.word, wordStyle), measurer.measure(figure, figureStyle))
            }
    }
    val dateStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
    val dates = remember(points, dateStyle, measurer) {
        listOf(points.first(), points[points.size / 2], points.last())
            .map { measurer.measure(AppDates.DayMonth.format(it.date), dateStyle) }
    }

    val density = LocalDensity.current
    // The column's width is the widest tag's, so the plot keeps every pixel the words do not need.
    // A range is two rows - its word, then its two prices - or it would set the width for all.
    val gutter = with(density) {
        val widest = tags.maxOfOrNull { tag ->
            if (tag.tag.twoRows) {
                maxOf(tag.word.size.width, tag.figure.size.width).toFloat()
            } else {
                tag.word.size.width + TagGap.toPx() + tag.figure.size.width
            }
        } ?: 0f
        TagLeader.toPx() + widest + TagPad.toPx() * 2
    }

    // The line draws itself from the left on first show and on every new range (2026-11 redesign).
    // Keyed on the points, so picking 3M after 1M draws the new line in; read only in the draw
    // lambda, so the half second costs repaints and no recomposition.
    val still = LocalReduceMotion.current
    val drawn = remember(points) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(drawn) {
        if (drawn.value < 1f) drawn.animateTo(1f, tween(LineDrawMillis, easing = UiEase))
    }

    val haptics = LocalHapticFeedback.current
    val chosen = remember(points, selected) {
        selected?.let { session -> points.indexOfFirst { it.date == session.date } }
            ?.takeIf { it >= 0 }
    }
    // Snapped to whole sessions rather than slid between them: a close is one figure a day, and a
    // price read off the gap between two of them is a price nobody quoted.
    // Read through the composition rather than captured by the gesture blocks. `pointerInput` is
    // keyed on the points, so it does not restart when the selection changes - a lambda captured
    // when the chart was first drawn would go on comparing against the index chosen *then*, which
    // is a haptic tick and a state write on every pixel of a drag rather than on every session.
    val pick by rememberUpdatedState<(Float, Float) -> Unit> { x, width ->
        val step = plotWidth(width, gutter) / (points.size - 1)
        val index = (x / step).roundToInt().coerceIn(points.indices)
        if (index != chosen) {
            // The tick the platform uses for dragging a text handle, which is what this is: a
            // marker being moved along a track, one stop at a time.
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(points[index])
        }
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height + AxisHeight)
            // Two detectors rather than one gesture loop, and the split is what keeps the sheet
            // scrollable. A tap reads without claiming anything; a drag is claimed only once the
            // finger has moved **horizontally** past touch slop, so a straight-down swipe over the
            // chart still scrolls the sheet behind it instead of scrubbing the line.
            .pointerInput(points) {
                detectTapGestures { offset -> pick(offset.x, size.width.toFloat()) }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> pick(offset.x, size.width.toFloat()) },
                ) { change, _ -> pick(change.position.x, size.width.toFloat()) }
            }
            .semantics {
                contentDescription = "${points.size} sessions, " +
                    formatPrice(closes.first()) + " to " + formatPrice(closes.last()) +
                    (levels?.let { ", with levels from " + it.source } ?: "")
            },
    ) {
        val stroke = LineStroke.toPx()
        val plotW = plotWidth(size.width, gutter)
        val plotH = height.toPx()
        val top = stroke / 2
        val usable = plotH - stroke
        fun y(value: Double) =
            top + ((1.0 - ((value.coerceIn(low, high) - low) / span)) * usable).toFloat()

        val step = plotW / (points.size - 1)
        val hair = GuideStroke.toPx()
        fun level(value: Double, color: Color) = drawLine(
            color = color.copy(alpha = LevelAlpha),
            start = Offset(0f, y(value)),
            end = Offset(plotW, y(value)),
            strokeWidth = hair,
        )

        if (levels != null) {
            val bandLow = levels.entryLow
            val bandHigh = levels.entryHigh
            if (bandLow != null && bandHigh != null && bandLow != bandHigh) {
                val topY = y(maxOf(bandLow, bandHigh))
                val bottomY = y(minOf(bandLow, bandHigh))
                drawRect(
                    color = colors.entry.copy(alpha = BandAlpha),
                    topLeft = Offset(0f, topY),
                    // A band collapsed by the scale is still a band, so it keeps a visible height.
                    size = Size(plotW, maxOf(bottomY - topY, MinBand.toPx())),
                )
            } else {
                (bandLow ?: bandHigh)?.let { level(it, colors.entry) }
            }
            levels.stopLoss?.let { level(it, colors.stop) }
            levels.target1?.let { level(it, colors.target) }
            levels.target2?.let { level(it, colors.target) }
            levels.paid?.let { level(it, colors.paid) }
        }

        val path = Path()
        points.forEachIndexed { index, session ->
            val at = Offset(index * step, y(session.close!!))
            if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
        }
        // The ground under the line, faint: it is what makes a fall read as a fall at a glance.
        val area = Path().apply {
            addPath(path)
            lineTo(plotW, plotH)
            lineTo(0f, plotH)
            close()
        }
        clipRect(right = plotW * drawn.value + stroke, bottom = plotH) {
            drawPath(area, color = colors.line.copy(alpha = AreaAlpha))
            drawPath(
                path,
                color = colors.line,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        val ring = RingStroke.toPx()
        points.forEachIndexed { index, session ->
            if (session.date in calls) {
                val at = Offset(index * step, y(session.close!!))
                drawCircle(color = colors.surface, radius = CallDot.toPx() + ring, center = at)
                drawCircle(color = colors.mark, radius = CallDot.toPx(), center = at)
            }
        }
        // Where it stands now, which is the point on the line every distance is measured from.
        val nowAt = Offset(plotW, y(latest))
        drawCircle(color = colors.surface, radius = LatestDot.toPx() * breath + ring, center = nowAt)
        drawCircle(color = colors.nowFill, radius = LatestDot.toPx() * breath, center = nowAt)

        // The crosshair is drawn over everything on the plot: it is the one thing on this chart
        // that is under the reader's own finger.
        chosen?.let { index ->
            val at = Offset(index * step, y(closes[index]))
            drawLine(
                color = colors.mark,
                start = Offset(at.x, 0f),
                end = Offset(at.x, plotH),
                strokeWidth = CrosshairStroke.toPx(),
            )
            drawCircle(color = colors.surface, radius = TouchDot.toPx() + ring, center = at)
            drawCircle(color = colors.line, radius = TouchDot.toPx(), center = at)
        }

        val row = TagRow.toPx()
        val pad = TagPad.toPx()
        val left = plotW + TagLeader.toPx()
        val tops = stackTags(
            centres = tags.map { y(it.tag.value) },
            heights = tags.map { if (it.tag.twoRows) row * 2 else row },
            total = plotH,
        )
        tags.forEachIndexed { index, measured ->
            val tag = measured.tag
            val rowTop = tops[index]
            val middle = rowTop + row / 2
            fun centred(layout: TextLayoutResult, rowAt: Float) = rowAt + (row - layout.size.height) / 2
            if (tag.filled) {
                val inset = TagFillInset.toPx()
                drawRoundRect(
                    color = colors.nowFill,
                    topLeft = Offset(left, rowTop + inset),
                    size = Size(size.width - left, row - inset * 2),
                    cornerRadius = CornerRadius(row / 2),
                )
                drawText(measured.word, color = colors.nowText, topLeft = Offset(left + pad, centred(measured.word, rowTop)))
                drawText(
                    measured.figure,
                    color = colors.nowText,
                    topLeft = Offset(size.width - pad - measured.figure.size.width, centred(measured.figure, rowTop)),
                )
            } else {
                // The leader: from where the level really is to the row its tag was given.
                val elbow = Path().apply {
                    moveTo(plotW, y(tag.value))
                    lineTo(plotW + TagLeader.toPx() / 2, middle)
                    lineTo(left + pad / 2 - hair * 2, middle)
                }
                drawPath(elbow, color = tag.color.copy(alpha = LeaderAlpha), style = Stroke(width = hair))
                drawText(measured.word, color = colors.soft, topLeft = Offset(left + pad, centred(measured.word, rowTop)))
                val figureRow = if (tag.twoRows) rowTop + row else rowTop
                drawText(
                    measured.figure,
                    color = tag.color,
                    topLeft = Offset(size.width - measured.figure.size.width, centred(measured.figure, figureRow)),
                )
            }
        }

        // The dates the line runs between, ending where the plot ends. The middle one is left out
        // rather than squeezed where the three would touch.
        val axisTop = plotH + AxisGap.toPx()
        val (first, mid, last) = dates
        drawText(first, color = colors.soft, topLeft = Offset(0f, axisTop))
        val lastLeft = plotW - last.size.width
        drawText(last, color = colors.soft, topLeft = Offset(lastLeft, axisTop))
        val midLeft = (points.size / 2) * step - mid.size.width / 2
        val clearance = pad * 2
        if (midLeft > first.size.width + clearance && midLeft + mid.size.width < lastLeft - clearance) {
            drawText(mid, color = colors.soft, topLeft = Offset(midLeft, axisTop))
        }
    }
}

/** The width left for the line once the tag column has taken its share, and never under half. */
private fun plotWidth(width: Float, gutter: Float): Float = maxOf(width - gutter, width * MinPlotShare)

/**
 * One line of the column on the right of the chart: what the level is called, and its price.
 *
 * [twoRows] is a range - an entry from one price to another - which takes a row for its word and a
 * row for its two prices, so its width does not set the column's for every other tag.
 */
internal data class ChartTag(
    val value: Double,
    val word: String,
    val figure: String,
    val color: Color,
    val filled: Boolean = false,
    val twoRows: Boolean = false,
)

private class MeasuredTag(val tag: ChartTag, val word: TextLayoutResult, val figure: TextLayoutResult)

/**
 * The tags a chart carries: its levels where it has any, the ends of what is on screen where it
 * has none, and the latest close always.
 *
 * With no levels drawn, the two figures worth printing are the highest and lowest close: a shape
 * with no numbers on it cannot say whether a climb was three percent or forty.
 */
internal fun chartTags(
    levels: ChartLevels?,
    latest: Double,
    priceLow: Double,
    priceHigh: Double,
    colors: ChartColors,
): List<ChartTag> = buildList {
    if (levels == null) {
        add(ChartTag(priceHigh, "High", formatPrice(priceHigh), colors.text))
        add(ChartTag(priceLow, "Low", formatPrice(priceLow), colors.text))
    } else {
        levels.target2?.let { add(ChartTag(it, "Target 2", formatPrice(it), colors.target)) }
        levels.target1?.let { add(ChartTag(it, "Target 1", formatPrice(it), colors.target)) }
        val bandLow = levels.entryLow
        val bandHigh = levels.entryHigh
        if (bandLow != null && bandHigh != null && bandLow != bandHigh) {
            add(
                ChartTag(
                    (bandLow + bandHigh) / 2,
                    "Entry",
                    formatPrice(minOf(bandLow, bandHigh)) + "–" + formatPrice(maxOf(bandLow, bandHigh)),
                    colors.entry,
                    twoRows = true,
                ),
            )
        } else {
            (bandLow ?: bandHigh)?.let { add(ChartTag(it, "Entry", formatPrice(it), colors.entry)) }
        }
        levels.paid?.let { add(ChartTag(it, "You paid", formatPrice(it), colors.text)) }
        levels.stopLoss?.let { add(ChartTag(it, "Stop", formatPrice(it), colors.stopText)) }
    }
    add(ChartTag(latest, "Now", formatPrice(latest), colors.nowText, filled = true))
}

/**
 * Where each tag's top edge goes, so that none overlaps another and none leaves the chart.
 *
 * [centres] is where each tag would sit if it were alone - level with its own line - given top to
 * bottom, and [heights] is what each needs. Tags that would overlap are joined into one block and
 * the block is centred on where its members wanted to be, so a cluster of four levels spreads both
 * up and down from the cluster rather than trailing off below it; a block that would leave the
 * chart is pulled back inside, and joined to the next if that makes them touch.
 *
 * Returned in the order given. More tags than the chart is tall is the one case this cannot solve,
 * and there the block starts at the top and runs off the bottom rather than overlapping.
 */
internal fun stackTags(centres: List<Float>, heights: List<Float>, total: Float): List<Float> {
    class Block(val first: Int, val last: Int, val height: Float) {
        val top: Float

        init {
            var offset = 0f
            var sum = 0f
            for (index in first..last) {
                sum += centres[index] - heights[index] / 2 - offset
                offset += heights[index]
            }
            top = (sum / (last - first + 1)).coerceIn(0f, maxOf(0f, total - height))
        }
    }

    val blocks = ArrayList<Block>()
    centres.indices.forEach { index ->
        var block = Block(index, index, heights[index])
        while (blocks.isNotEmpty() && blocks.last().top + blocks.last().height > block.top) {
            val above = blocks.removeAt(blocks.lastIndex)
            block = Block(above.first, block.last, above.height + block.height)
        }
        blocks.add(block)
    }
    val tops = FloatArray(centres.size)
    blocks.forEach { block ->
        var at = block.top
        for (index in block.first..block.last) {
            tops[index] = at
            at += heights[index]
        }
    }
    return tops.toList()
}

/**
 * What a chart says above itself: one quiet line and one large figure.
 *
 * At rest that is the range and what the line adds up to across it. With a finger on the line it
 * is the touched session and its close, in the same two places, so nothing under the chart moves
 * when the reading arrives - see [chartReading]. [trailing] is the stock sheet's two buttons.
 */
@Composable
internal fun ChartHeadline(
    title: String,
    figure: String,
    colors: ChartColors,
    modifier: Modifier = Modifier,
    figureColor: Color = colors.text,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = colors.soft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                figure,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TabularFigures, fontWeight = FontWeight.Bold),
                color = figureColor,
                maxLines = 1,
            )
        }
        trailing()
    }
}

/**
 * The headline for a touched session: its date and what the line had done by then, over its close.
 *
 * [note] takes the move's place where the session has something better to say - on the stock
 * sheet, who made a call that day. Null when nothing is touched.
 */
internal fun chartReading(
    visible: List<DailySession>,
    touched: DailySession?,
    note: String? = null,
    price: (Double) -> String = { formatPrice(it) },
): Pair<String, String>? {
    val close = touched?.close ?: return null
    val since = visible.firstOrNull()?.let { first ->
        visible.moveTo(touched)?.let { formatPercent(it) + " since " + AppDates.DayMonth.format(first.date) }
    }
    val title = listOfNotNull(AppDates.DayMonth.format(touched.date), note ?: since).joinToString(" · ")
    return title to price(close)
}

/** What the dot on the line means, and whose the levels are, in one line of words. */
@Composable
internal fun ChartLegend(text: String, colors: ChartColors, modifier: Modifier = Modifier, dot: Boolean = true) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot) Box(Modifier.size(8.dp).clip(CircleShape).background(colors.mark))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.soft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * How far back a chart reaches: every range in one track across the chart's width.
 *
 * It replaced a row of chips that scrolled (2026-11-11), which on the cover screen left the last
 * of them cut off at the tile's edge. A track divides the width it is given, so six ranges fit the
 * cover screen whole and nothing is ever off the end. A label longer than a range's two or three
 * characters - the position card's "Since buy" - takes a wider share.
 */
@Composable
internal fun <T> ChartRanges(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    colors: ChartColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Kit.Capsule)
            .background(colors.track)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        options.forEach { option ->
            val text = label(option)
            val isSelected = option == selected
            val wide = text.length > 4
            Box(
                Modifier
                    .weight(if (wide) WideRangeShare else 1f)
                    .height(44.dp)
                    .clip(Kit.Capsule)
                    .background(if (isSelected) colors.chosen else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    style = if (wide) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures)
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) colors.onChosen else colors.text,
                    maxLines = 1,
                )
            }
        }
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
    // The kit's day bar since 5.0: a raised track, the day's move from the open to the close as an
    // ink wash across it, and the close an ink marker standing taller than the track.
    val track = palette.raised
    val closeMark = palette.ink
    val openMark = palette.ink.copy(alpha = 0.18f)
    val at = ((close - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()
    val openAt = session.open?.let { ((it - low) / (high - low)).coerceIn(0.0, 1.0).toFloat() }
    // The close slides to its place from the open (or the low) the first time the bar is drawn
    // (2026-11 redesign): the day's own move, said once. Read only in the draw.
    val still = LocalReduceMotion.current
    val slid = remember(session) { Animatable(if (still) at else (openAt ?: 0f)) }
    LaunchedEffect(slid, at) { slid.animateTo(at, uiSpring()) }
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
                val from = minOf(x(openAt), x(slid.value.coerceIn(0f, 1f)))
                val to = maxOf(x(openAt), x(slid.value.coerceIn(0f, 1f))) + markWidth
                drawRoundRect(
                    color = openMark,
                    topLeft = Offset(from, middle),
                    size = Size(to - from, bar),
                    cornerRadius = CornerRadius(bar / 2),
                )
            }
            // Taller than the track it sits in, because it is the figure being read: the open is
            // context and the close is the price.
            drawRoundRect(
                color = closeMark,
                topLeft = Offset(x(slid.value.coerceIn(0f, 1f)), 0f),
                size = Size(markWidth, size.height),
                cornerRadius = CornerRadius(markWidth / 2),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RangeCaption("LOW " + formatPrice(low))
            RangeCaption(session.open?.let { "OPEN " + formatPrice(it) } ?: "TODAY")
            RangeCaption("HIGH " + formatPrice(high))
        }
    }
}

@Composable
private fun RangeCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
        color = palette.muted,
    )
}

/**
 * Tall enough that levels within a few percent of each other still leave the line room to be read.
 *
 * 150dp was tuned for a price line and one or two guides; 220dp is also what a full column of tags
 * needs - two targets, the latest close, an entry range on two rows, what was paid and the stop are
 * seven rows of [TagRow].
 */
private val ChartHeight = 220.dp

/**
 * How far the scale may grow beyond the price's own range to take the levels in.
 *
 * Two and a half times the span the stock actually covered. An ordinary call's stop and targets sit
 * within that of a month's trading, so they are drawn where they really are; a level far outside it
 * is pinned rather than allowed to flatten the line the chart exists to show.
 */
private const val MaxScaleGrowth = 2.5

private val LineStroke = 2.2.dp

private val GuideStroke = 1.dp

private val CrosshairStroke = 1.5.dp

/** A level's hairline: present, and quiet enough that the price line is the thing read. */
private const val LevelAlpha = 0.55f

private const val LeaderAlpha = 0.7f

/** Faint enough to read the price line through, strong enough to find the band's edges. */
private const val BandAlpha = 0.2f

private const val AreaAlpha = 0.08f

private val MinBand = 2.dp

private val CallDot = 3.5.dp

/** The ring of the surface's own colour round every dot, which lifts it off the line. */
private val RingStroke = 2.dp

private val LatestDot = 5.dp

private val TouchDot = 5.dp

/** One tag's row in the column: a 12sp line and the air that keeps two of them apart. */
private val TagRow = 20.dp

/** Between a tag's word and its price. */
private val TagGap = 8.dp

/** Inside the filled tag, either side of its words. */
private val TagPad = 8.dp

/** How far the filled tag's pill stops short of its row, above and below. */
private val TagFillInset = 1.dp

/** The run from the plot's edge to the tags, which the leaders cross. */
private val TagLeader = 14.dp

/** The plot's least share of the chart, however wide the tags want to be. */
private const val MinPlotShare = 0.5f

private val AxisGap = 6.dp

/** The date line under the plot: the gap above it and a 12sp line. */
private val AxisHeight = 24.dp

/** The share of the range track a worded choice takes, against one for "1M". */
private const val WideRangeShare = 1.7f

/** The track, plus the overhang the close marker is drawn with above and below it. */
private val RangeHeight = 14.dp

private val RangeBarHeight = 6.dp

private val RangeMarkWidth = 3.dp

/** How long the price line takes to draw itself in. */
private const val LineDrawMillis = 550
