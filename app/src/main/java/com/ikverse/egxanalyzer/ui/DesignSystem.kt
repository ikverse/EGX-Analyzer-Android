package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The one spacing scale.
 *
 * Twelve different gaps were in use - 2, 3, 4, 6, 8, 10, 12, 14, 16, 20, 24 and 28 - which is what
 * made the screens read as almost-aligned rather than aligned. Four steps cover every case here.
 */
object Space {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
}

/**
 * Two icon sizes, and no others.
 *
 * [Inline] sits beside text - section headings, chips, list rows. [Action] is for anything with a
 * touch target of its own: navigation, buttons, menu affordances.
 */
object IconSize {
    val Inline: Dp = 20.dp
    val Action: Dp = 24.dp
}

/**
 * Colour carries meaning for a price, not decoration.
 *
 * A row of eight numbers in one colour has to be read left to right before any of it means
 * anything. Tying the hue to the role - what you pay, what you hope for, what you cannot afford -
 * makes a row scannable at a glance.
 */
object PriceRole {
    /** What the call asks you to pay. Neutral: it is the reference every other figure is read against. */
    val entry: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurface

    /** Where the call says to take profit. */
    val target: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.tertiary

    /** Where the call says to give up. */
    val stop: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.error

    /** A price the market reached, rather than one a channel chose. */
    val market: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary

    /** Supporting context: dates, notes, counts. */
    val muted: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

    /** A figure the app worked out rather than read from a source. */
    val derived: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun forReturn(value: Double?): Color = when {
        value == null -> muted
        value > 0 -> target
        value < 0 -> stop
        else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    }
}

/** Digits that line up in a column, so a table can be compared down as well as read across. */
val TabularFigures = FontFamily.Monospace

const val Dash = "—"

/**
 * A price exactly as it is worth reading.
 *
 * EGX trades plenty of stocks below one pound - 0.243, 0.408 - so two decimals is not enough, and
 * a raw Double prints float noise. Three is the most any source prints; trailing zeros go because
 * `0.250` and `0.25` are the same price and the extra digit only adds width.
 */
fun formatPrice(value: Double?): String {
    if (value == null || value.isNaN()) return Dash
    val rounded = (value * 1000).roundToInt() / 1000.0
    if (abs(rounded - rounded.toLong()) < 1e-9) return rounded.toLong().toString()
    return rounded.toString().trimEnd('0').trimEnd('.')
}

/** A signed percentage, at one decimal: past that the figure implies a precision it does not have. */
fun formatPercent(value: Double?, signed: Boolean = true): String {
    if (value == null || value.isNaN()) return Dash
    val rounded = (value * 10).roundToInt() / 10.0
    val sign = if (signed && rounded > 0) "+" else ""
    return "$sign${if (abs(rounded - rounded.toLong()) < 1e-9) rounded.toLong().toString() else rounded}%"
}

/**
 * How many columns of at least [minColumnWidth] fit in the space actually available.
 *
 * Derived rather than enumerated: a width nobody thought to test still gets a sensible answer,
 * which is the whole point of a responsive layout. Measured from the container, because a pane is
 * often narrower than the window and window width promises room a component does not have.
 */
fun BoxWithConstraintsScope.responsiveColumns(
    minColumnWidth: Dp = 320.dp,
    maxColumns: Int = 3,
): Int = max(1, min(maxColumns, floor(maxWidth / minColumnWidth).toInt()))

/**
 * Lays [items] out in [columns] rows of equal width.
 *
 * A grid rather than a list, so a wide screen stops running one card per row across a metre of
 * empty space. The last row is padded with blanks so its cards keep the same width as the rest.
 */
@Composable
fun <T> ColumnScope.ResponsiveRows(
    items: List<T>,
    columns: Int,
    spacing: Dp = Space.m,
    /**
     * Receives a modifier that must be applied to whatever the item draws.
     *
     * Cards in a row are sized to the tallest of them, so a stock whose name wraps to two lines no
     * longer leaves its neighbour floating with a ragged edge beneath it. Handing the modifier down
     * is what lets the card itself stretch; a box around a short card only pads the gap.
     */
    item: @Composable (T, Modifier) -> Unit,
) {
    if (columns <= 1) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.forEach { value -> item(value, Modifier.fillMaxWidth()) }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns).forEach { row ->
            // Deliberately not IntrinsicSize.Min: every cell here contains a BoxWithConstraints
            // somewhere, and intrinsic measurement of a SubcomposeLayout throws. Cards are kept
            // level by giving their headers a common height instead, which is where the raggedness
            // came from.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                row.forEach { value ->
                    Box(Modifier.weight(1f)) { item(value, Modifier.fillMaxWidth()) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Splits a list into grid rows and full-width open items, in the order they are drawn.
 *
 * An open item needs the whole width - half a row squeezes a table of prices onto two lines - so it
 * gets a band of its own. Nothing else moves. Opening the second card of a row therefore leaves the
 * first alone on a half-empty row above it, and that gap is deliberate: the list is sorted by date,
 * so its order is the only thing telling you which run is the newest one.
 *
 * An earlier version closed that gap by moving the neighbour below the open item, which put the
 * second card of a row ahead of the first for as long as it was open - the newest report read as
 * the second newest, which is exactly what the sort exists to prevent.
 *
 * @return each band with a flag saying whether it is the open one.
 */
internal fun <T> expandableBands(
    items: List<T>,
    isOpen: (T) -> Boolean,
): List<Pair<List<T>, Boolean>> {
    val bands = mutableListOf<Pair<List<T>, Boolean>>()
    val closed = mutableListOf<T>()
    items.forEach { item ->
        if (!isOpen(item)) {
            closed += item
            return@forEach
        }
        if (closed.isNotEmpty()) bands += closed.toList() to false
        closed.clear()
        bands += listOf(item) to true
    }
    if (closed.isNotEmpty()) bands += closed.toList() to false
    return bands
}

/**
 * Draws a scrollbar that appears while scrolling and fades out afterwards.
 *
 * Compose draws none at all, so a long card gave no sign that anything sat below the fold. It
 * fades rather than staying put because a permanent bar on every container is its own kind of
 * clutter.
 */
fun Modifier.fadingScrollbar(
    state: ScrollState,
    horizontal: Boolean = false,
    thickness: Dp = 3.dp,
    color: Color? = null,
): Modifier = composed {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val bar = color ?: scheme.onSurfaceVariant
    var scrolling by remember { mutableStateOf(false) }
    // The effect also runs when the screen first composes, which showed a bar on every scrollable
    // the moment it appeared, before anything had been scrolled.
    var composed by remember { mutableStateOf(false) }
    LaunchedEffect(state.value) {
        if (!composed) {
            composed = true
            return@LaunchedEffect
        }
        scrolling = true
        delay(SCROLLBAR_LINGER_MS)
        scrolling = false
    }
    val alpha by animateFloatAsState(
        targetValue = if (scrolling) 0.5f else 0f,
        animationSpec = tween(if (scrolling) 120 else 400),
        label = "scrollbar",
    )
    drawWithContent {
        drawContent()
        if (alpha <= 0.01f || state.maxValue == 0 || state.maxValue == Int.MAX_VALUE) return@drawWithContent
        val track = if (horizontal) size.width else size.height
        val visible = track / (track + state.maxValue)
        val thumb = max(track * visible, MIN_THUMB_PX)
        val travel = (track - thumb) * (state.value.toFloat() / state.maxValue)
        val weight = thickness.toPx()
        val radius = CornerRadius(weight / 2, weight / 2)
        if (horizontal) {
            drawRoundRect(
                color = bar,
                topLeft = Offset(travel, size.height - weight),
                size = Size(thumb, weight),
                cornerRadius = radius,
                alpha = alpha,
            )
        } else {
            drawRoundRect(
                color = bar,
                topLeft = Offset(size.width - weight, travel),
                size = Size(weight, thumb),
                cornerRadius = radius,
                alpha = alpha,
            )
        }
    }
}

private const val SCROLLBAR_LINGER_MS = 900L
private const val MIN_THUMB_PX = 48f

/** A vertical scroll that shows how much is left. Use instead of `verticalScroll` directly. */
@Composable
fun Modifier.scrollableColumn(): Modifier {
    val state = rememberScrollState()
    // The bar is drawn outside the scroll, not inside it: a draw modifier placed after
    // verticalScroll is a child of it, so it would be measured against the content and slide away
    // with it instead of staying pinned to the edge of the viewport.
    return this.fadingScrollbar(state).verticalScroll(state)
}

/** A horizontal scroll that shows how much is left. */
@Composable
fun Modifier.scrollableRow(): Modifier {
    val state = rememberScrollState()
    return this.fadingScrollbar(state, horizontal = true).horizontalScroll(state)
}

/**
 * A tall thing beside the short things that configure it, once there is room for both.
 *
 * A screen of full-width cards wastes most of a wide display: a card holding three checkboxes was
 * spanning 1600 pixels. Below [minWidth] the two panes stack, which is the right answer on a cover
 * screen where height is the plentiful dimension.
 */
@Composable
fun AdaptivePanes(
    minWidth: Dp = 720.dp,
    mainWeight: Float = 1.3f,
    main: @Composable ColumnScope.() -> Unit,
    side: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints {
        if (maxWidth >= minWidth) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Column(
                    Modifier.weight(mainWeight),
                    verticalArrangement = Arrangement.spacedBy(Space.m),
                    content = main,
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.m),
                    content = side,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                main()
                side()
            }
        }
    }
}

/**
 * Lays a handful of small controls across the width instead of stacking them.
 *
 * Three checkboxes in a column is a phone layout; on anything wider it is three rows of mostly
 * nothing.
 */
@Composable
fun AdaptiveInline(
    minWidth: Dp = 420.dp,
    content: @Composable (horizontal: Boolean) -> Unit,
) {
    BoxWithConstraints {
        content(maxWidth >= minWidth)
    }
}

