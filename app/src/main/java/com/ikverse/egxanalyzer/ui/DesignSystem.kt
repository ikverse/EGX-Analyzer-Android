package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Shape
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
 * Anything that floats over a page rather than sitting in it.
 *
 * The navigation bar and the action button are both loose on top of the content, and both have to
 * say so the same way: slightly see-through, so the page carries on behind them, and outlined, so
 * their edge holds against whatever scrolls under it. Defined once here because two definitions of
 * "floating" drift apart the first time one of them is adjusted.
 *
 * The shape is still the caller's, though both callers currently pass the page's own card radius.
 *
 * @param painted draws the surface itself, for a caller whose fill is not one colour. It is applied
 *   to the content rather than to [modifier] so it lands where the flat tint would have - inside the
 *   shape's clip and on top of the elevation shadow - and it carries its own alpha, so a gradient
 *   here is as see-through as a tint here. [color] is ignored when it is passed; pass
 *   `Color.Transparent`.
 * @param outline overrides the neutral hairline for a caller that needs the edge to mean something.
 */
@Composable
internal fun FloatingSurface(
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    painted: Modifier? = null,
    outline: Color? = null,
    content: @Composable () -> Unit,
) {
    // See-through enough that the page carries on behind it, opaque enough that it cannot be read
    // there. At 0.88 a heading passing underneath came through as a second row of words tangled in
    // the labels; what is wanted is the movement, not the content.
    val tinted = if (painted == null) color.copy(alpha = 0.94f) else Color.Transparent
    // The tint alone leaves the edge indistinct against a card of a similar colour; the hairline is
    // what draws the shape whatever is behind it.
    val hairline = BorderStroke(
        1.dp,
        outline ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    )
    // Only wrapped when there is something to paint: an unconditional Box would put a layout node
    // under every floating surface in the app to serve the one that needs it.
    val body: @Composable () -> Unit = if (painted == null) {
        content
    } else {
        { Box(painted, propagateMinConstraints = true) { content() } }
    }
    if (onClick == null) {
        Surface(modifier, shape = shape, color = tinted, border = hairline, shadowElevation = FloatingElevation) {
            body()
        }
    } else {
        Surface(onClick, modifier, shape = shape, color = tinted, border = hairline, shadowElevation = FloatingElevation) {
            body()
        }
    }
}

/** Enough to lift the surface off the page without casting a shadow the tint then shows through. */
private val FloatingElevation = 6.dp

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
 * How tall a pill-shaped button stands.
 *
 * Shorter than a Material button, because these share a row with each other rather than anchoring
 * a screen. Defined here rather than beside either of them: two pills side by side at two heights
 * is the kind of almost-aligned this scale exists to stop.
 */
val PillHeight: Dp = 32.dp

/**
 * Three icon sizes, and no others.
 *
 * [Inline] sits beside text - section headings, chips, list rows. [Action] is for anything with a
 * touch target of its own: navigation, buttons, menu affordances.
 *
 * [Hint] is the exception the overdue tile earned: a glyph whose whole job is to say the surface
 * leads somewhere, on a card small enough that an [Inline] arrow stands nearly as tall as the logo
 * across from it and reads as a control rather than a hint. Not for anything that is pressed on its
 * own - it is under the touch target every such thing has to clear.
 */
object IconSize {
    val Hint: Dp = 16.dp
    val Inline: Dp = 20.dp
    val Action: Dp = 24.dp
}

/**
 * The hairline a card is drawn with.
 *
 * The same width and colour as the stroke around the page well, on purpose: the edge of a card and
 * the edge of the page it sits on are then one line rather than two that happen to agree. Defined
 * once so the three places that draw a card cannot drift apart.
 */
val cardOutline: BorderStroke
    @Composable get() = BorderStroke(
        1.dp,
        androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
    )

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

    /**
     * A price the market reached, rather than one a channel chose.
     *
     * Its own hue rather than `primary`, which is the app's own voice and was doing both jobs. See
     * `market` in `ExtraColors`.
     */
    val market: Color @Composable get() = com.ikverse.egxanalyzer.ui.theme.extraColors.market

    /** Supporting context: dates, notes, counts. */
    val muted: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

    /**
     * A figure the app worked out rather than read from a source: the role's own colour, softened.
     *
     * Not a colour of its own. Drawn in [muted] it came out the exact grey of the notes and dates
     * beside it, and a column holding both kinds of figure was left in two different hues - which
     * one a row got depending only on whether its channel happened to print the number. Hue stays
     * the role, opacity carries the provenance, and the column reads as one column again.
     */
    fun derived(of: Color): Color = of.copy(alpha = DerivedAlpha)

    /**
     * How far a derived figure is softened.
     *
     * Shared with the Excel export, which mixes it onto the page by hand because an xlsx font
     * colour carries no alpha. Two constants would drift apart the first time either was adjusted.
     */
    const val DerivedAlpha = com.ikverse.egxanalyzer.model.DERIVED_ALPHA

    @Composable
    fun forReturn(value: Double?): Color = when {
        value == null -> muted
        value > 0 -> target
        value < 0 -> stop
        else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    }
}

/**
 * Digits that line up in a column, so a table can be compared down as well as read across.
 *
 * IBM Plex Mono rather than `FontFamily.Monospace`, which resolved to whatever the device happened
 * to call monospace and matched nothing else on screen. See `Figures` in `theme/Type.kt`.
 */
val TabularFigures = com.ikverse.egxanalyzer.ui.theme.Figures

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

/** "session" or "sessions", so the three places that count them all read the same. */
fun Int.sessionWord(): String = if (this == 1) "session" else "sessions"

/** "day" or "days", for the overdue count. */
fun Long.dayWord(): String = if (this == 1L) "day" else "days"

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

