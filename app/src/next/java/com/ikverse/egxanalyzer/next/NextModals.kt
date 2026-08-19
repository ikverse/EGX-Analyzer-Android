package com.ikverse.egxanalyzer.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * A modal, and the blast radius it is asking about.
 *
 * The set this comes from has one rule behind it: **when the blast radius changes, the screen
 * changes.** A checkbox is for a reversible option inside one act - swap a run, not erase a
 * channel - so widening the radius means a new frame, a new colour, an arming step, and a way back
 * stated in words. [tone] is that colour: the accent adds a record, steel changes one, red removes
 * one, amber interrupts something already running.
 */
@Composable
internal fun NextModal(
    kicker: String,
    tone: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    meta: String? = null,
    body: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = LocalNextColors.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.scrim)
            // The scrim is what makes this modal. An empty click stops a press reaching the screen
            // underneath; the way out is on the panel, in words.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(NextMetrics.space6),
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth >= NextMetrics.railBreakpoint
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = if (wide) NextMetrics.modalWidth else maxWidth)
                .background(colors.chrome)
                .border(NextMetrics.hairline, colors.ruleStrong)
                .spine(tone, 3.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .ruleBottom(colors.ruleSoft)
                    .padding(
                        start = NextMetrics.space6,
                        end = NextMetrics.space5,
                        top = NextMetrics.space5,
                        bottom = NextMetrics.space4,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
            ) {
                NextText(kicker.uppercase(Locale.ROOT), NextType.columnLabel, tone)
                Spacer(Modifier.weight(1f))
                if (meta != null) NextText(meta, NextType.meta, colors.figMuted)
                NextButton(
                    label = "Close",
                    onClick = onDismiss,
                    tone = colors.rule,
                    labelColor = colors.ink3,
                    minHeight = 32.dp,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(NextMetrics.space6),
                verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
            ) {
                if (title != null) {
                    NextText(title, NextType.ticker.copy(fontFamily = Sans), colors.ink)
                }
                body()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .ruleTop(colors.ruleSoft)
                    .padding(NextMetrics.space6),
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
                content = actions,
            )
        }
    }
}

/**
 * A field, which in this app only ever holds a figure or a date.
 *
 * Set in the figure face for the same reason every other number is: the value being typed is going
 * to be read back in a column beside prices, and a proportional face would put it at a different
 * width there than it has here.
 */
@Composable
internal fun NextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = true,
    /** Said under the field when what was typed cannot be read as a number or a date. */
    problem: String? = null,
) {
    val colors = LocalNextColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NextMetrics.space2)) {
        NextLabel(label, color = colors.figMuted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .background(colors.well, RoundedCornerShape(NextMetrics.chipCorner))
                .border(
                    NextMetrics.hairline,
                    if (problem == null) colors.rule else colors.stop,
                    RoundedCornerShape(NextMetrics.chipCorner),
                )
                .padding(horizontal = NextMetrics.space5, vertical = NextMetrics.space4),
            textStyle = NextType.figure.copy(color = colors.ink),
            singleLine = true,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            ),
        )
        if (problem != null) NextText(problem, NextType.meta, colors.stop)
    }
}

/** A row of the modal's own summary lines: what changes, and what survives. */
@Composable
internal fun NextConsequence(
    mark: String,
    markTone: Color,
    what: String,
    where: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNextColors.current
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
        verticalAlignment = Alignment.Top,
    ) {
        NextText(mark, NextType.meta, markTone)
        NextText(what, NextType.meta, colors.ink2, modifier = Modifier.weight(1f))
        if (where != null) NextText(where, NextType.meta, colors.ink3)
    }
}

/** A hairline spacer used where two blocks inside a modal need separating without a heading. */
@Composable
internal fun NextModalDivider() {
    val colors = LocalNextColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(NextMetrics.hairline)
            .background(colors.ruleSoft),
    )
}
