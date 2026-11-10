package com.ikverse.egxanalyzer.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.LocalReduceMotion
import com.ikverse.egxanalyzer.ui.UiEase
import kotlinx.coroutines.delay

/**
 * The 5.0 kit: the building blocks every redesigned screen is made of.
 *
 * Nothing in `ui/kit` knows about a screen, a repository or `AppState`. Each piece takes plain values
 * and callbacks, reads the palette from the theme, and does its own motion - so a screen is
 * assembled from these rather than restating a shape, a height or a spring of its own. That is how
 * the old UI grew six pill families in four corners and five heights, and it is the drift this
 * folder exists to prevent.
 */
object Kit {
    /** A tile: the coloured block every page is built from. */
    val TileCorner: Dp = 30.dp
    val TileShape: Shape = RoundedCornerShape(TileCorner)

    /** A row group or inner panel inside a tile. */
    val PanelShape: Shape = RoundedCornerShape(20.dp)

    /** Sheets, bottom or side. */
    val SheetCorner: Dp = 32.dp

    /** A dialog card. */
    val DialogShape: Shape = RoundedCornerShape(32.dp)

    /** Text fields. */
    val FieldShape: Shape = RoundedCornerShape(16.dp)

    /** Everything pressable that is not a tile is a capsule. */
    val Capsule: Shape = RoundedCornerShape(percent = 50)

    /** Button heights. The touch target is 48dp whatever the drawn height. */
    val ButtonSmall: Dp = 40.dp
    val ButtonMedium: Dp = 52.dp
    val ButtonLarge: Dp = 60.dp

    /** The gap between tiles, and the page's own side inset. */
    val TileGap: Dp = 10.dp
    val PageInset: Dp = 14.dp
}

/**
 * The spring things arrive on: a little past their mark and back. For arrivals and presses only;
 * market events use [drawInSpec] and the sweep instead, so the two kinds of motion stay apart.
 */
internal fun <T> popSpring() = spring<T>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

/** How a line or bar draws in when the market moves it: a decelerating tween, no overshoot. */
internal fun drawInSpec(durationMs: Int = 1_400) = tween<Float>(durationMs, easing = UiEase)

private const val PopStepMs = 60L
private const val PopCap = 8
private const val PopFromScale = 0.86f

/**
 * A tile arriving: it pops in from slightly small and slightly low, [index] steps after the first
 * of its group. Drawn in the layer, so it recomposes nothing. With reduce motion on it is simply
 * there.
 */
@Composable
fun Modifier.popIn(index: Int = 0): Modifier {
    val still = LocalReduceMotion.current
    val shown = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (shown.value >= 1f) return@LaunchedEffect
        delay(PopStepMs * index.coerceIn(0, PopCap))
        shown.animateTo(1f, popSpring())
    }
    val lift = with(LocalDensity.current) { 18.dp.toPx() }
    return graphicsLayer {
        val v = shown.value
        alpha = v.coerceIn(0f, 1f)
        val scale = PopFromScale + (1f - PopFromScale) * v
        scaleX = scale
        scaleY = scale
        translationY = (1f - v) * lift
    }
}

/**
 * 0 to 1 once, for something that draws itself in: a chart line, a progress bar growing to its
 * value. Starts at 1 with reduce motion on.
 */
@Composable
fun rememberDrawIn(key: Any? = Unit, delayMs: Long = 0, durationMs: Int = 1_400): Animatable<Float, *> {
    val still = LocalReduceMotion.current
    val progress = remember(key) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(key) {
        if (progress.value >= 1f) return@LaunchedEffect
        delay(delayMs)
        progress.animateTo(1f, drawInSpec(durationMs))
    }
    return progress
}
