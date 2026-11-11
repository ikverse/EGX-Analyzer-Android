package com.ikverse.egxanalyzer.ui.kit

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.LocalReduceMotion
import com.ikverse.egxanalyzer.ui.TabularFigures
import com.ikverse.egxanalyzer.ui.pressScale
import com.ikverse.egxanalyzer.ui.theme.palette
import com.ikverse.egxanalyzer.ui.uiSpring

/** A filter or choice chip: a hairline capsule, ink with the highlight on it when selected. */
@Composable
fun KitChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val press = remember { MutableInteractionSource() }
    val fill by animateColorAsState(if (selected) p.ink else Color.Transparent, label = "chipFill")
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .pressScale(press)
            .height(40.dp)
            .clip(Kit.Capsule)
            .background(fill)
            .border(BorderStroke(1.5.dp, p.ink), Kit.Capsule)
            .selectable(selected = selected, interactionSource = press, indication = null, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (selected) "✓ $label" else label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) p.onInk else p.ink,
            maxLines = 1,
        )
    }
}

/**
 * One choice of several, side by side in a track: Light / Dark / Auto, Watching / T+1 / Explicit
 * date. The selected segment is ink with the highlight on it.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val p = palette
    Row(
        modifier
            .clip(Kit.Capsule)
            .background(p.raised)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val fill by animateColorAsState(if (isSelected) p.ink else Color.Transparent, label = "segment")
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(Kit.Capsule)
                    .background(fill)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) p.onInk else p.ink,
                    maxLines = 1,
                )
            }
        }
    }
}

/** On or off. Cobalt when on. [label] names it for a screen reader when no text sits beside it. */
@Composable
fun KitSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val p = palette
    val still = LocalReduceMotion.current
    val track by animateColorAsState(if (checked) p.hero else MaterialTheme.colorScheme.outline, label = "switchTrack")
    val knob by animateDpAsState(
        if (checked) 24.dp else 4.dp,
        animationSpec = if (still) androidx.compose.animation.core.snap() else uiSpring(),
        label = "switchKnob",
    )
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(width = 52.dp, height = 32.dp)
            .clip(Kit.Capsule)
            .background(track)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .offset(x = knob, y = 4.dp)
                .size(24.dp)
                .clip(Kit.Capsule)
                .background(Color.White),
        )
    }
}

/**
 * The switch's drawing alone, with no press of its own: for a row that is itself the toggle, so a
 * screen reader hears one switch - the row, named by its words - rather than two.
 */
@Composable
fun KitSwitchFace(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Drawn on the cobalt tile. The ordinary track is cobalt when on and the outline when off, and
     * on cobalt both disappear and leave a knob floating on the tile. There the track is lime when
     * on, under a navy knob, and a pale wash when off.
     */
    onHero: Boolean = false,
) {
    val p = palette
    val still = LocalReduceMotion.current
    val track by animateColorAsState(
        when {
            onHero && checked -> p.highlight
            onHero -> Color.White.copy(alpha = 0.32f)
            checked -> p.hero
            else -> MaterialTheme.colorScheme.outline
        },
        label = "switchFaceTrack",
    )
    val knobColor by animateColorAsState(if (onHero && checked) p.onTile else Color.White, label = "switchFaceKnobColor")
    val knob by animateDpAsState(
        if (checked) 24.dp else 4.dp,
        animationSpec = if (still) androidx.compose.animation.core.snap() else uiSpring(),
        label = "switchFaceKnob",
    )
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(Kit.Capsule)
            .background(track),
    ) {
        Box(
            Modifier
                .offset(x = knob, y = 4.dp)
                .size(24.dp)
                .clip(Kit.Capsule)
                .background(knobColor),
        )
    }
}

/** A tick box. Ink when ticked, with the highlight's tick on it. */
@Composable
fun KitCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val fill by animateColorAsState(if (checked) p.ink else Color.Transparent, label = "checkFill")
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(fill)
            .border(BorderStroke(1.8.dp, p.ink), RoundedCornerShape(7.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = p.onInk, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A text field: a label over a white field with a cobalt ring while focused.
 *
 * [was] is what the field held before a correction ("was 85.60"), with [onPutBack] to restore it;
 * [error] replaces it in loss red and is announced as the field's error. [figure] sets the value in
 * JetBrains Mono, for prices and dates.
 */
@Composable
fun KitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    figure: Boolean = false,
    error: String? = null,
    was: String? = null,
    onPutBack: (() -> Unit)? = null,
    keyboardType: KeyboardType = if (figure) KeyboardType.Decimal else KeyboardType.Text,
    singleLine: Boolean = true,
    /** Dots in place of what is typed, for an API key or a Telegram password. */
    password: Boolean = false,
    enabled: Boolean = true,
    /** What the keyboard's action key reads: Next to move on, Done to submit the step. */
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Default,
    /** What that key does. Null leaves it to the keyboard. */
    onImeAction: (() -> Unit)? = null,
) {
    val p = palette
    var focused by remember { mutableStateOf(false) }
    val edge = when {
        error != null -> p.loss
        focused -> p.hero
        // The outline rather than the hairline: a resting field is a control, and the hairline
        // is 1.5:1 on a dark card.
        else -> MaterialTheme.colorScheme.outline
    }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = p.muted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            enabled = enabled,
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
            keyboardActions = if (onImeAction == null) androidx.compose.foundation.text.KeyboardActions.Default
            else androidx.compose.foundation.text.KeyboardActions(onDone = { onImeAction() }, onNext = { onImeAction() }, onGo = { onImeAction() }, onSend = { onImeAction() }),
            textStyle = (if (figure) TextStyle(fontFamily = TabularFigures, fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyLarge).copy(color = p.ink, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
            cursorBrush = SolidColor(p.hero),
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { if (error != null) error(error) },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(Kit.FieldShape)
                        .background(p.card)
                        .border(BorderStroke(if (focused || error != null) 2.dp else 1.5.dp, edge), Kit.FieldShape)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
        when {
            error != null -> Text(error, style = MaterialTheme.typography.labelMedium, color = p.loss, modifier = Modifier.padding(top = 4.dp))
            was != null -> Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("was $was", style = MaterialTheme.typography.labelMedium, color = p.muted)
                if (onPutBack != null) {
                    Text(
                        "Put back",
                        style = MaterialTheme.typography.labelMedium,
                        color = p.heroInk,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onPutBack),
                    )
                }
            }
        }
    }
}

/** A whole number between [range], with − and + either side. */
@Composable
fun KitStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
    /** How far one press moves it: 30 for a timeout counted in half-minutes. */
    step: Int = 1,
) {
    val p = palette
    Row(
        modifier.semantics { stateDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StepperButton("−", "Fewer", enabled = value - step >= range.first, filled = false) { onValueChange(value - step) }
        Text(
            value.toString(),
            style = TextStyle(fontFamily = TabularFigures, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.headlineSmall.fontSize),
            color = p.ink,
            modifier = Modifier.widthIn(min = 44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepperButton("+", "More", enabled = value + step <= range.last, filled = true) { onValueChange(value + step) }
    }
}

@Composable
private fun StepperButton(glyph: String, label: String, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    val p = palette
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .pressScale(press)
            .size(36.dp)
            .clip(Kit.Capsule)
            .background(if (filled) p.ink else Color.Transparent)
            .border(BorderStroke(1.5.dp, p.ink), Kit.Capsule)
            .clickable(interactionSource = press, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = if (filled) p.onInk else p.ink, style = MaterialTheme.typography.titleMedium)
    }
}
