package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.ui.theme.palette

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
    // The kit's Ask AI pill since 5.0: an ink capsule with lilac words where the press is paid for,
    // a hairline ring in ink where it only reopens an answer already kept. A turning arc replaces
    // the sparkle while the request is out.
    val p = palette
    val filled = look == AiLook.Filled
    val content = if (filled) p.tile2 else p.ink
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(press)
            .minimumInteractiveComponentSize()
            .height(com.ikverse.egxanalyzer.ui.kit.Kit.ButtonSmall)
            .alpha(if (enabled || working) 1f else DisabledAlpha)
            .clip(com.ikverse.egxanalyzer.ui.kit.Kit.Capsule)
            .then(
                if (filled) {
                    Modifier.background(p.ink)
                } else {
                    Modifier.border(1.5.dp, p.ink, com.ikverse.egxanalyzer.ui.kit.Kit.Capsule)
                },
            )
            .clickable(
                interactionSource = press,
                indication = null,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        if (working) {
            com.ikverse.egxanalyzer.ui.kit.KitSpinner(color = content, size = 16.dp)
        } else {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = content, modifier = Modifier.size(IconSize.Hint))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val OutlineWidth: Dp = 1.dp

/** The ring in ink at a little under half strength: a hairline, not a second solid button. */
private const val OutlineAlpha = 0.55f

private const val DisabledAlpha = 0.38f

/** How wide the light is, as a share of the button, and how bright at its centre. */
private const val SweepBand = 0.35f
private const val SweepAlpha = 0.28f
private const val SweepMs = 1_600
