package com.ikverse.egxanalyzer.next

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * The pieces every screen is built from.
 *
 * This app is one system rather than five screens, and this file is where that is enforced: one
 * card, one chip in three constructions, one figure treatment, one section mark, one button, one
 * family of empty states. A screen that needs a variation states it as an argument here rather than
 * drawing its own - the moment two screens solve the same problem differently, the record stops
 * reading as one record.
 */

// ---- Edges ---------------------------------------------------------------------------------

/** A hairline along the top edge. The commonest rule in the app. */
internal fun Modifier.ruleTop(color: Color, weight: Dp = NextMetrics.hairline) = drawBehind {
    drawRect(color = color, size = Size(size.width, weight.toPx()))
}

/** A hairline along the bottom edge. */
internal fun Modifier.ruleBottom(color: Color, weight: Dp = NextMetrics.hairline) = drawBehind {
    val thickness = weight.toPx()
    drawRect(
        color = color,
        topLeft = Offset(0f, size.height - thickness),
        size = Size(size.width, thickness),
    )
}

/**
 * A dotted hairline along the top edge.
 *
 * Dotted means *inside* a block, where a solid rule means between blocks. The difference is doing
 * real work on these screens: a card's summary strip is part of the card, and a solid rule there
 * would cut it into two.
 */
internal fun Modifier.dottedRuleTop(color: Color) = drawBehind {
    val thickness = NextMetrics.hairline.toPx()
    drawLine(
        color = color,
        start = Offset(0f, thickness / 2f),
        end = Offset(size.width, thickness / 2f),
        strokeWidth = thickness,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 3f)),
    )
}

/** The mark down the leading edge of something opened, selected, urgent or arriving. */
internal fun Modifier.spine(color: Color, weight: Dp = NextMetrics.spine) = drawBehind {
    drawRect(color = color, size = Size(weight.toPx(), size.height))
}

/** A dashed box, which in this app always means "outside the arithmetic". */
internal fun Modifier.dashedEdge(color: Color) = drawBehind {
    drawRect(
        color = color,
        style = Stroke(
            width = NextMetrics.hairline.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
        ),
    )
}

// ---- Press ---------------------------------------------------------------------------------

/** The interaction source a press is read from. */
@Composable
internal fun rememberPress(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * The app's only press: a fill dropping to the well, in 90ms, released over 120.
 *
 * No ripple anywhere. The well is where a card goes when it opens, so a press is the same movement
 * as an opening, started and abandoned - which is exactly what a press is.
 */
@Composable
internal fun MutableInteractionSource.pressFill(
    pressedColor: Color,
    idle: Color = Color.Transparent,
): State<Color> {
    val pressed by collectIsPressedAsState()
    return animateColorAsState(
        targetValue = if (pressed) pressedColor else idle,
        animationSpec = tween(
            durationMillis = if (pressed) NextMotion.PRESS_MILLIS else NextMotion.RELEASE_MILLIS,
            easing = NextMotion.linear,
        ),
        label = "pressFill",
    )
}

// ---- Text fragments ------------------------------------------------------------------------

/** A column label: mono, tracked wide, capitals. */
@Composable
internal fun NextLabel(
    text: String,
    color: Color = LocalNextColors.current.ink3,
    modifier: Modifier = Modifier,
) = NextText(
    text = text.uppercase(Locale.ROOT),
    style = NextType.columnLabel,
    color = color,
    modifier = modifier,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

/**
 * A figure, with its provenance said by opacity.
 *
 * [derived] is the whole point: most percentages on these screens were worked out by the app rather
 * than printed by a source, and marking that with a colour of its own put the column in two hues.
 * The role keeps the hue; the alpha says who did the arithmetic.
 */
@Composable
internal fun NextFigure(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = NextType.figure,
    derived: Boolean = false,
) {
    val colors = LocalNextColors.current
    NextText(
        text = text,
        style = style,
        color = if (derived) color.copy(alpha = colors.derivedAlpha) else color,
        modifier = modifier,
        maxLines = 1,
    )
}

/** A label with its figure under it, which is how every stat on every screen is built. */
@Composable
internal fun NextFigureCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    derived: Boolean = false,
    style: TextStyle = NextType.figure,
) {
    Column(modifier) {
        NextLabel(label)
        Spacer(Modifier.height(NextMetrics.space1))
        NextFigure(value, color, style = style, derived = derived)
    }
}

/**
 * Figures laid out in a fixed number of columns.
 *
 * The column count is decided by the caller from the width it actually has, not from the window's:
 * a pane beside a rail is narrower than the window promises, and a grid that believed the window
 * would put four figures in the space for two.
 */
@Composable
internal fun NextFigureGrid(
    columns: Int,
    cells: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.animateContentSize(tween(NextMotion.OPEN_MILLIS, easing = NextMotion.hinge)),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        cells.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space5),
            ) {
                row.forEach { cell -> Box(Modifier.weight(1f)) { cell() } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---- Chips ---------------------------------------------------------------------------------

/**
 * The three constructions a status is allowed to take.
 *
 * The domain has three tiers, not nine labels, so the chip's *construction* says the tier and its
 * colour says which one inside that tier. [FILLED] is a verdict that counts, [HOLLOW] is still
 * running or still unresolved, [DASHED] is excluded from every rate on purpose and visibly.
 */
internal enum class ChipStyle { FILLED, HOLLOW, DASHED }

@Composable
internal fun NextChip(
    text: String,
    tone: Color,
    modifier: Modifier = Modifier,
    style: ChipStyle = ChipStyle.HOLLOW,
    fill: Color? = null,
    /** A shape as well as a colour, so the chip survives a reader who cannot separate the hues. */
    glyph: String? = null,
) {
    val colors = LocalNextColors.current
    val shape = RoundedCornerShape(NextMetrics.chipCorner)
    val edge = when (style) {
        ChipStyle.FILLED -> tone
        ChipStyle.HOLLOW -> tone
        ChipStyle.DASHED -> colors.ruleStrong
    }
    Row(
        modifier
            .clip(shape)
            .then(if (style == ChipStyle.FILLED && fill != null) Modifier.background(fill) else Modifier)
            .then(
                if (style == ChipStyle.DASHED) {
                    Modifier.dashedEdge(edge)
                } else {
                    Modifier.border(NextMetrics.hairline, edge, shape)
                },
            )
            .padding(horizontal = NextMetrics.space3, vertical = NextMetrics.space1 + 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space2),
    ) {
        if (glyph != null) {
            NextText(glyph, NextType.navLabel, tone)
        }
        NextText(
            text = text.uppercase(Locale.ROOT),
            style = NextType.chip,
            color = tone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Buttons -------------------------------------------------------------------------------

/**
 * How much of its tone a button wears.
 *
 * [NONE] is the default and most of them: a hairline and capitals. [WASH] marks the one action a
 * group is leaning towards. [SOLID] is reserved for the single act a modal exists to ask for, and
 * it is the only place in the app where the ground colour is used as ink.
 */
internal enum class NextFill { NONE, WASH, SOLID }

/**
 * Every button in the app.
 *
 * Two corners, a hairline in the tone it speaks in, capitals, and 44 high so the whole thing is the
 * tap target.
 */
@Composable
internal fun NextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = LocalNextColors.current.rule,
    labelColor: Color? = null,
    fill: NextFill = NextFill.NONE,
    minHeight: Dp = NextMetrics.tapMinimum,
    enabled: Boolean = true,
) {
    val colors = LocalNextColors.current
    val shape = RoundedCornerShape(NextMetrics.chipCorner)
    val press = rememberPress()
    val idle = when (fill) {
        NextFill.NONE -> Color.Transparent
        NextFill.WASH -> tone.copy(alpha = 0.16f)
        NextFill.SOLID -> tone
    }
    val down = when (fill) {
        NextFill.NONE -> colors.well
        NextFill.WASH -> tone.copy(alpha = 0.30f)
        NextFill.SOLID -> tone.copy(alpha = 0.82f)
    }
    val background by press.pressFill(pressedColor = down, idle = idle)
    val ink = when {
        !enabled -> colors.ink3
        labelColor != null -> labelColor
        fill == NextFill.SOLID -> colors.ground
        fill == NextFill.WASH -> tone
        else -> colors.ink2
    }
    Box(
        modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(background)
            .border(NextMetrics.hairline, if (enabled) tone else colors.ruleSoft, shape)
            .clickable(
                interactionSource = press,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = NextMetrics.space5),
        contentAlignment = Alignment.Center,
    ) {
        NextText(
            text = label.uppercase(Locale.ROOT),
            style = NextType.chip,
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * The one control in the app that costs money, and the only one that has to be held.
 *
 * A run is not undoable and not free, so this button refuses to fire on a brush past it: the bar
 * fills for [NextMotion.HOLD_MILLIS] under the finger and the run starts when it reaches the end.
 * Letting go early cancels it, silently and completely. This is the asymmetry between committing
 * and browsing made visible without resorting to a scary red button - the colour stays the app's
 * own accent, and the *effort* carries the warning.
 */
@Composable
internal fun NextHoldButton(
    label: String,
    holdingLabel: String,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = LocalNextColors.current.accent,
    enabled: Boolean = true,
) {
    val colors = LocalNextColors.current
    val shape = RoundedCornerShape(NextMetrics.chipCorner)
    var holding by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (holding && enabled) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (holding) NextMotion.HOLD_MILLIS else NextMotion.RELEASE_MILLIS,
            easing = NextMotion.linear,
        ),
        finishedListener = { value -> if (value == 1f) onCommit() },
        label = "holdProgress",
    )
    Box(
        modifier
            .heightIn(min = 46.dp)
            .clip(shape)
            .background(if (enabled) tone.copy(alpha = 0.16f) else Color.Transparent)
            .border(NextMetrics.hairline, if (enabled) tone else colors.ruleSoft, shape)
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                holding = true
                                tryAwaitRelease()
                                holding = false
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(tone.copy(alpha = 0.42f)),
        )
        NextText(
            text = (if (progress > 0f) holdingLabel else label).uppercase(Locale.ROOT),
            style = NextType.chip,
            color = if (enabled) tone else colors.ink3,
            maxLines = 1,
        )
    }
}

// ---- Screen furniture ----------------------------------------------------------------------

/**
 * The line every screen opens with: its name, what it is holding, and how it is ordered.
 *
 * Identical on all five, which is most of what makes them one app rather than five.
 */
@Composable
internal fun NextScreenHeader(
    title: String,
    holding: String,
    trailing: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNextColors.current
    Row(
        modifier
            .fillMaxWidth()
            .ruleBottom(colors.ruleStrong)
            .padding(top = NextMetrics.space7, bottom = NextMetrics.space5),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        NextText(title, NextType.screenTitle, colors.ink)
        NextText(
            text = holding,
            style = NextType.meta,
            color = colors.figMuted,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) NextLabel(trailing)
    }
}

/** The strip of filters and sorts under a screen's header. One row, one construction, every screen. */
@Composable
internal fun NextFilterRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalNextColors.current
    Row(
        modifier
            .fillMaxWidth()
            .ruleBottom(colors.ruleStrong)
            .padding(vertical = NextMetrics.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
        content = content,
    )
}

/** A filter or sort control: the same chip, in the accent when it is doing something. */
@Composable
internal fun NextFilterChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNextColors.current
    NextButton(
        label = "$label ▾",
        onClick = onClick,
        modifier = modifier,
        tone = if (active) colors.accent else colors.rule,
        labelColor = if (active) colors.accent else colors.ink2,
        fill = if (active) NextFill.WASH else NextFill.NONE,
        minHeight = 40.dp,
    )
}

/**
 * A section inside a card: a state mark, a tracked word, its count, and a rule to the edge.
 *
 * Not a nested card. Three cards inside a card inside a list is where a phone-width record stops
 * being readable, so a section is 22dp of chrome and nothing more.
 */
@Composable
internal fun NextSectionMark(
    label: String,
    count: Int,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNextColors.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = NextMetrics.space3, bottom = NextMetrics.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
    ) {
        Box(Modifier.size(5.dp).background(tone))
        NextText(label.uppercase(Locale.ROOT), NextType.navLabel, colors.ink2)
        NextText("$count", NextType.navLabel, colors.ink3)
        Box(
            Modifier
                .weight(1f)
                .height(NextMetrics.hairline)
                .background(colors.ruleSoft),
        )
    }
}

/**
 * The card everything expandable is made of.
 *
 * Folded it is on the ground; opened it drops to the well, so the list around it stays ground and
 * exactly one thing on screen is ever open-looking. The fold takes 220ms on the way out and 120 on
 * the way back, because closing is not information and does not get the reader's time.
 */
@Composable
internal fun NextRecordCard(
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Drawn down the leading edge: urgency, arrival, or nothing. */
    spineColor: Color? = null,
    header: @Composable ColumnScope.(chevron: Float) -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalNextColors.current
    val press = rememberPress()
    val pressed by press.pressFill(colors.well.copy(alpha = 0.6f), Color.Transparent)
    val background by animateColorAsState(
        targetValue = if (open) colors.well else colors.ground,
        animationSpec = tween(NextMotion.DESTINATION_MILLIS, easing = NextMotion.linear),
        label = "cardGround",
    )
    val chevron by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(NextMotion.OPEN_MILLIS, easing = NextMotion.hinge),
        label = "cardChevron",
    )
    Column(
        modifier
            .fillMaxWidth()
            .background(background)
            .then(if (spineColor != null) Modifier.spine(spineColor) else Modifier),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(pressed)
                .clickable(
                    interactionSource = press,
                    indication = null,
                    role = Role.Button,
                    onClick = onToggle,
                )
                .padding(horizontal = NextMetrics.space5, vertical = NextMetrics.space4),
        ) {
            header(chevron)
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(NextMotion.OPEN_MILLIS, easing = NextMotion.hinge)) +
                fadeIn(tween(NextMotion.OPEN_MILLIS, easing = NextMotion.linear)),
            exit = shrinkVertically(tween(NextMotion.EXIT_MILLIS, easing = NextMotion.exit)) +
                fadeOut(tween(NextMotion.EXIT_MILLIS, easing = NextMotion.linear)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = NextMetrics.space5,
                        end = NextMetrics.space5,
                        bottom = NextMetrics.space5,
                    ),
                content = body,
            )
        }
    }
}

/** The chevron on a foldable header, rotated by [NextRecordCard]. */
@Composable
internal fun NextChevron(angle: Float, color: Color = LocalNextColors.current.ink3) {
    NextText("▾", NextType.navLabel, color, modifier = Modifier.rotate(angle))
}

// ---- Empty states --------------------------------------------------------------------------

/**
 * Three kinds, and they behave differently.
 *
 * [INVITE] is "nothing yet" and asks for the action that would fill it. [NO_MATCH] is "nothing
 * matches" and has to hand back the filter that hid everything, or the reader is stuck looking at a
 * record they believe they have lost. [INLINE] is "nothing here specifically" - local to one row or
 * one card, and never allowed to look like the screen is empty.
 */
internal enum class EmptyKind { INVITE, NO_MATCH, INLINE }

@Composable
internal fun NextEmpty(
    kind: EmptyKind,
    title: String,
    body: String? = null,
    action: ShellAction? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNextColors.current
    val tone = when (kind) {
        EmptyKind.INVITE -> colors.accent
        EmptyKind.NO_MATCH -> colors.expired
        EmptyKind.INLINE -> colors.ink3
    }
    if (kind == EmptyKind.INLINE) {
        Column(
            modifier
                .fillMaxWidth()
                .ruleTop(colors.ruleSoft)
                .padding(vertical = NextMetrics.space4),
            verticalArrangement = Arrangement.spacedBy(NextMetrics.space2),
        ) {
            NextText(title, NextType.meta, colors.ink3)
            if (body != null) NextText(body, NextType.meta, colors.figMuted)
        }
        return
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(tone.copy(alpha = 0.06f))
            .ruleTop(tone, NextMetrics.spine)
            .padding(NextMetrics.space6),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space3),
    ) {
        NextLabel(
            text = if (kind == EmptyKind.INVITE) "Nothing yet" else "Nothing matches",
            color = tone,
        )
        NextText(title, NextType.ticker.copy(fontFamily = Sans), colors.ink)
        if (body != null) NextText(body, NextType.name, colors.ink2)
        if (action != null) {
            NextButton(
                label = action.label,
                onClick = action.onClick,
                tone = tone,
                labelColor = tone,
                fill = if (kind == EmptyKind.INVITE) NextFill.WASH else NextFill.NONE,
                modifier = Modifier.padding(top = NextMetrics.space2),
            )
        }
    }
}
