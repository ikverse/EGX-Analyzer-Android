package com.ikverse.egxanalyzer.ui.kit

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.model.hingeColumns

/**
 * How the phone is being held, provided once by the shell from the platform's hinge report.
 *
 * The cover screen by default, so anything drawn outside the shell - a preview, a test of one
 * component - lays out as the narrowest case rather than assuming room it may not have.
 */
val LocalFoldPosture = staticCompositionLocalOf<FoldPosture> { FoldPosture.Cover }

/**
 * Two columns side by side, as every page lays itself out unfolded and in book posture.
 *
 * Unfolded the row is split by [unfoldedStart] with the kit's gap. In book posture the row measures
 * where it sits on the screen and sizes the columns so the gap between them is the crease - see
 * [hingeColumns]. Until it has been measured, and wherever the hinge does not cross it, it falls back
 * to the hinge's fractions, clamped so neither side can be weighted to nothing.
 */
@androidx.compose.runtime.Composable
fun SplitColumns(
    posture: FoldPosture,
    unfoldedStart: Float,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    start: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    end: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val windowWidth = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toFloat()
    var row by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<Float, Float>?>(null) }
    val book = posture as? FoldPosture.Book
    val measured = book?.let { b ->
        row?.let { (x, width) ->
            with(density) {
                hingeColumns(
                    rowStart = x,
                    rowWidth = width,
                    hingeStart = b.hingeStartFraction * windowWidth,
                    hingeEnd = b.hingeEndFraction * windowWidth,
                    minGap = Kit.TileGap.toPx(),
                    minColumn = 160.dp.toPx(),
                )
            }
        }
    }
    val column = androidx.compose.foundation.layout.Arrangement.spacedBy(Kit.TileGap)
    androidx.compose.foundation.layout.Row(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val at = coordinates.positionInWindow().x to coordinates.size.width.toFloat()
                if (at != row) row = at
            },
        horizontalArrangement = if (measured == null) column else androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        if (measured != null) {
            val (left, right) = measured
            androidx.compose.foundation.layout.Column(Modifier.width(with(density) { left.toDp() }), verticalArrangement = column, content = start)
            androidx.compose.foundation.layout.Column(Modifier.width(with(density) { right.toDp() }), verticalArrangement = column, content = end)
        } else {
            val first = (book?.hingeStartFraction ?: unfoldedStart).coerceIn(0.05f, 0.95f)
            val second = (book?.let { 1f - it.hingeEndFraction } ?: (1f - unfoldedStart)).coerceIn(0.05f, 0.95f)
            androidx.compose.foundation.layout.Column(Modifier.weight(first), verticalArrangement = column, content = start)
            androidx.compose.foundation.layout.Column(Modifier.weight(second), verticalArrangement = column, content = end)
        }
    }
}
