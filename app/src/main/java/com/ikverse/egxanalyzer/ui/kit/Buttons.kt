package com.ikverse.egxanalyzer.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.pressScale
import com.ikverse.egxanalyzer.ui.theme.palette

/**
 * The three kinds of button, and what each says.
 *
 * - [PRIMARY]: the one thing the surface is for. Ink with the highlight on it.
 * - [SECONDARY]: a hairline in ink. Everything else a surface offers.
 * - [DANGER]: something that cannot be undone. Loss red.
 * - [ON_TONE]: drawn on a coloured tile - a hairline in whatever the tile's content colour is.
 */
enum class ButtonKind { PRIMARY, SECONDARY, DANGER, ON_TONE }

/** Small (40dp), medium (52dp) or large (60dp). The touch target is 48dp at every size. */
enum class ButtonSize(val height: Dp, val padding: Dp) {
    SMALL(Kit.ButtonSmall, 16.dp),
    MEDIUM(Kit.ButtonMedium, 20.dp),
    LARGE(Kit.ButtonLarge, 24.dp),
}

/** A capsule button: one of three kinds, one of three heights, an optional leading icon. */
@Composable
fun KitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    size: ButtonSize = ButtonSize.MEDIUM,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val p = palette
    val onTone = LocalContentColor.current
    val (fill, ink, edge) = when (kind) {
        ButtonKind.PRIMARY -> Triple(p.ink, p.onInk, null)
        ButtonKind.SECONDARY -> Triple(Color.Transparent, p.ink, p.ink)
        // White on the dark theme's brighter red is 3.3:1; navy on it is 5.4:1. A light ink means
        // the dark theme, whichever of its grounds is in force.
        ButtonKind.DANGER -> Triple(
            p.loss,
            if (p.ink.luminance() > 0.5f) p.onTile else Color.White,
            null,
        )
        ButtonKind.ON_TONE -> Triple(Color.Transparent, onTone, onTone)
    }
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .pressScale(press)
            .height(size.height)
            .clip(Kit.Capsule)
            .background(fill)
            .then(if (edge != null) Modifier.border(BorderStroke(1.5.dp, edge), Kit.Capsule) else Modifier)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(
                interactionSource = press,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = size.padding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text,
                style = if (size == ButtonSize.SMALL) MaterialTheme.typography.labelLarge
                else MaterialTheme.typography.titleMedium,
                fontWeight = if (kind == ButtonKind.PRIMARY) FontWeight.ExtraBold else FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/**
 * A round icon button. [label] is required, because an icon alone says nothing to a screen reader.
 */
@Composable
fun KitIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Dp = 44.dp,
) {
    val p = palette
    val press = remember { MutableInteractionSource() }
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .pressScale(press)
            .size(size)
            .clip(Kit.Capsule)
            .background(if (filled) p.ink else p.raised)
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) p.ground else p.ink, modifier = Modifier.size(20.dp))
    }
}
