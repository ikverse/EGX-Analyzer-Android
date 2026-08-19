package com.ikverse.egxanalyzer.next

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * One filter, and everything it could be set to.
 *
 * The app has no dropdown menus - a floating list over a record is exactly the Material read this
 * redesign exists to get away from - so a filter that is opened pushes its options **into** the
 * page, under the row it belongs to, and the list closes when one is picked. It is the same
 * disclosure the cards use, applied to a control.
 */
internal data class NextPickerSpec(
    /** What the chip reads when the picker is shut. */
    val label: String,
    /** Whether this filter is currently hiding anything. */
    val active: Boolean,
    val options: List<String>,
    val selectedIndex: Int,
    val onPick: (Int) -> Unit,
)

/**
 * The row of filters every record screen carries, and the one panel they share.
 *
 * One open at a time, on purpose: two option lists stacked under a filter row is a menu system, and
 * this app has one control that is open at once, like it has one card.
 */
@Composable
internal fun NextFilterBar(
    pickers: List<NextPickerSpec>,
    modifier: Modifier = Modifier,
    leading: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalNextColors.current
    var openIndex by remember { mutableStateOf(-1) }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (openIndex < 0) Modifier.ruleBottom(colors.ruleStrong) else Modifier)
                .padding(vertical = NextMetrics.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
        ) {
            if (leading != null) NextLabel(leading)
            pickers.forEachIndexed { index, picker ->
                NextFilterChip(
                    label = picker.label,
                    active = picker.active || openIndex == index,
                    onClick = { openIndex = if (openIndex == index) -1 else index },
                )
            }
            Spacer(Modifier.weight(1f))
            trailing()
        }

        AnimatedVisibility(
            visible = openIndex >= 0,
            enter = expandVertically(tween(NextMotion.OPEN_MILLIS, easing = NextMotion.hinge)) +
                fadeIn(tween(NextMotion.OPEN_MILLIS, easing = NextMotion.linear)),
            exit = shrinkVertically(tween(NextMotion.EXIT_MILLIS, easing = NextMotion.exit)) +
                fadeOut(tween(NextMotion.EXIT_MILLIS, easing = NextMotion.linear)),
        ) {
            val picker = pickers.getOrNull(openIndex)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.well)
                    .ruleBottom(colors.ruleStrong),
            ) {
                picker?.options?.forEachIndexed { option, text ->
                    OptionRow(
                        text = text,
                        selected = option == picker.selectedIndex,
                        onClick = {
                            picker.onPick(option)
                            openIndex = -1
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalNextColors.current
    val press = rememberPress()
    val fill by press.pressFill(colors.ground)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = NextMetrics.tapMinimum)
            .background(fill)
            .then(if (selected) Modifier.spine(colors.accent) else Modifier)
            .ruleTop(colors.ruleSoft)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(
                start = NextMetrics.space5,
                end = NextMetrics.space5,
                top = NextMetrics.space4,
                bottom = NextMetrics.space4,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NextText(
            text = text,
            style = NextType.name,
            color = if (selected) colors.ink else colors.ink2,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) NextText("✓", NextType.meta, colors.accent)
    }
}

/**
 * A card's own menu, opened in place rather than over the record.
 *
 * The destructive entries sit under a rule and in the stop colour, because this is the menu that
 * holds both "save a copy of this" and "delete it everywhere" and those two must never look alike.
 */
@Composable
internal fun NextInlineMenu(
    actions: List<ShellAction>,
    destructive: List<ShellAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colors = LocalNextColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.well),
    ) {
        actions.forEach { action ->
            OptionRow(action.label, selected = false, onClick = action.onClick)
        }
        destructive.forEach { action ->
            val press = rememberPress()
            val fill by press.pressFill(colors.stopFill)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NextMetrics.tapMinimum)
                    .background(fill)
                    .ruleTop(colors.stop)
                    .clickable(
                        interactionSource = press,
                        indication = null,
                        onClick = action.onClick,
                    )
                    .padding(
                        start = NextMetrics.space5,
                        end = NextMetrics.space5,
                        top = NextMetrics.space4,
                        bottom = NextMetrics.space4,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NextText(action.label, NextType.name, colors.stop, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * A date, picked rather than typed.
 *
 * A month at a time, set in the figure face like every other number in this app, so the grid lines
 * up in columns the way a record does. Days beyond [latest] are drawn dead rather than hidden - a
 * calendar with holes in it is harder to read than one that simply refuses.
 */
@Composable
internal fun NextDateSheet(
    kicker: String,
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    earliest: LocalDate? = null,
    latest: LocalDate? = null,
) {
    val colors = LocalNextColors.current
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    var chosen by remember { mutableStateOf(initial) }

    NextModal(
        kicker = kicker,
        tone = colors.market,
        onDismiss = onDismiss,
        body = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
            ) {
                NextButton(
                    label = "‹",
                    onClick = { month = month.minusMonths(1) },
                    tone = colors.rule,
                    minHeight = 40.dp,
                )
                NextText(
                    text = "${month.month.getDisplayName(TextStyle.FULL, Locale.US)} ${month.year}",
                    style = NextType.name,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                NextButton(
                    label = "›",
                    onClick = { month = month.plusMonths(1) },
                    tone = colors.rule,
                    minHeight = 40.dp,
                )
            }

            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        NextText(day, NextType.navLabel, colors.ink3)
                    }
                }
            }

            val first = month.atDay(1)
            val blanks = first.dayOfWeek.value - 1
            val days = month.lengthOfMonth()
            val cells = List(blanks) { null } + (1..days).map(month::atDay)
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(
                            Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day != null) {
                                val allowed = (earliest == null || !day.isBefore(earliest)) &&
                                    (latest == null || !day.isAfter(latest))
                                DayCell(
                                    day = day,
                                    selected = day == chosen,
                                    allowed = allowed,
                                    onClick = { chosen = day },
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            NextText(
                "Cairo's calendar, like every other date in this app.",
                NextType.meta,
                colors.ink3,
            )
        },
        actions = {
            NextButton(
                label = "Use ${formatFullDay(chosen)}",
                onClick = {
                    onPick(chosen)
                    onDismiss()
                },
                tone = colors.accent,
                fill = NextFill.SOLID,
                modifier = Modifier.weight(1f),
            )
            NextButton("Cancel", onDismiss, tone = colors.rule, labelColor = colors.ink3)
        },
    )
}

@Composable
private fun DayCell(
    day: LocalDate,
    selected: Boolean,
    allowed: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalNextColors.current
    val press = rememberPress()
    val fill by press.pressFill(colors.well)
    val today = day == LocalDate.now()
    Box(
        Modifier
            .size(40.dp)
            .background(if (selected) colors.accentFill else fill)
            .then(if (selected) Modifier.ruleBottom(colors.accent, NextMetrics.spine) else Modifier)
            .then(
                if (today && !selected) {
                    Modifier.ruleBottom(colors.ruleStrong, NextMetrics.hairline)
                } else {
                    Modifier
                },
            )
            .then(
                if (allowed) {
                    Modifier.clickable(
                        interactionSource = press,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        NextFigure(
            text = "${day.dayOfMonth}",
            color = when {
                !allowed -> colors.ruleStrong
                selected -> colors.accent
                else -> colors.ink2
            },
            style = NextType.meta,
        )
    }
}

/** A horizontal strip of chips, for a choice too small to deserve a panel. */
@Composable
internal fun NextChipStrip(
    options: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tone: Color? = null,
) {
    val colors = LocalNextColors.current
    val accent = tone ?: colors.accent
    LazyRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
    ) {
        items(options.size) { index ->
            NextButton(
                label = options[index],
                onClick = { onPick(index) },
                tone = if (index == selectedIndex) accent else colors.rule,
                labelColor = if (index == selectedIndex) accent else colors.ink3,
                fill = if (index == selectedIndex) NextFill.WASH else NextFill.NONE,
                minHeight = 40.dp,
            )
        }
    }
}
