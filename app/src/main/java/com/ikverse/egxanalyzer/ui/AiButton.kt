package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How an [AskAiButton] is drawn, which is decided by what pressing it costs.
 *
 * [Filled] spends money; [Outlined] reopens something already paid for. A solid capsule and a
 * hairline ring are told apart before the label is read, and the label is the only other thing
 * separating a billed request from a free one.
 */
internal enum class AiLook { Filled, Outlined }

/**
 * Ask AI, in the 4.7.0 design: ink rather than a colour of its own.
 *
 * It wore a violet-to-accent gradient, a breathing halo and a gold mark until 4.7.0 - the glass
 * design's way of saying "a model is speaking", left behind when everything around it went flat.
 * In paper and ink the one accent is taken by what can be pressed and red and green by money, so
 * the model gets the one thing left that no figure uses: the page's own ink, solid when pressing it
 * is billed and a hairline when it only reopens an answer. It sits beside the tonal [ActionPill]s
 * on a call card as their equal in shape and height, apart from them in weight.
 *
 * @param working sweeps a band of light across the button for as long as the request is out. It is
 *   the progress indicator: the button said "Asking…" with nothing moving before. Held still under
 *   reduce motion, and built only while working, so a screen of idle buttons runs no clock.
 */
@Composable
internal fun AskAiButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    look: AiLook = AiLook.Filled,
    enabled: Boolean = true,
    working: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val filled = look == AiLook.Filled
    val content = if (filled) colors.surface else colors.onSurface
    val press = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(pressCorner(press, rest = PillHeight / 2))
    val sweep = rememberSweep(active = working)
    Row(
        modifier
            .pressScale(press)
            // The pill is shorter than a fingertip. This keeps the target the full 48dp without
            // making the button look like one.
            .minimumInteractiveComponentSize()
            .height(PillHeight)
            // A refused press reads as refused, but not while working: then the sweep is the point.
            .alpha(if (enabled || working) 1f else DisabledAlpha)
            .clip(shape)
            .then(
                if (filled) {
                    Modifier.background(colors.onSurface)
                } else {
                    Modifier.border(OutlineWidth, colors.onSurface.copy(alpha = OutlineAlpha), shape)
                },
            )
            .drawWithContent {
                drawContent()
                // Read in the draw lambda, so the sweep costs a repaint a frame and no recomposition.
                val at = sweep.value
                if (at >= 0f) {
                    val band = size.width * SweepBand
                    val x = at * (size.width + band * 2f) - band
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, content.copy(alpha = SweepAlpha), Color.Transparent),
                            startX = x - band,
                            endX = x + band,
                        ),
                        topLeft = Offset.Zero,
                    )
                }
            }
            .clickable(
                interactionSource = press,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(IconSize.Hint),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Where the working sweep is, 0 to 1 across the button, or -1 when there is none to draw.
 *
 * The transition is only created while [active], so an idle button costs no frames, and reduce
 * motion leaves a working button still rather than moving it.
 */
@Composable
private fun rememberSweep(active: Boolean): State<Float> {
    if (!active || LocalReduceMotion.current) return remember { mutableFloatStateOf(-1f) }
    return rememberInfiniteTransition(label = "ai").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SweepMs, easing = LinearEasing)),
        label = "sweep",
    )
}

private val OutlineWidth: Dp = 1.dp

/** The ring in ink at a little under half strength: a hairline, not a second solid button. */
private const val OutlineAlpha = 0.55f

private const val DisabledAlpha = 0.38f

/** How wide the light is, as a share of the button, and how bright at its centre. */
private const val SweepBand = 0.35f
private const val SweepAlpha = 0.28f
private const val SweepMs = 1_600
