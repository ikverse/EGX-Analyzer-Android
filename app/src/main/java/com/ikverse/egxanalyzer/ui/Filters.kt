package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.ui.theme.palette
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.KitChip
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.KitCheckbox
import com.ikverse.egxanalyzer.ui.kit.Kit
import androidx.compose.foundation.border
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A page's filters, in a sheet the header's filter icon opens.
 *
 * **It replaced a shelf on the page**, removed on 2026-09-09 along with the floating machinery that
 * held it at the top of a scroll. That shelf was a search box and a Filters chip; when the box moved
 * into the header it became a chip alone - a loose one-control row above every list, standing off
 * the page on a shadow, opening a panel of three more chips each of which opened a menu of its own.
 * Three levels deep for a question the reader asks in one press.
 *
 * **The trigger is in the header and the content is here, and neither could hold the other.** The
 * header does not know what a page filters by - channels come off `savedResults`, dates off the
 * trades - and this is composed underneath the icon that opens it, so it cannot own the flag. The
 * flag lives on `PageState.filtersOpen`, which is where the rest of a page's own state already is
 * and the one place a fold cannot take it from. See `PageHeader`.
 *
 * **A sheet rather than a panel hanging off the header**, for two reasons. A sheet already means
 * one thing in this app - the longer version of the thing that was pressed, which is what
 * `StockSheet` and `InfoSheet` are - and a panel over a scrolling page would have to re-solve, in a
 * second place, exactly the pinning the shelf existed to solve. The width is the rest of it: the
 * choices are **shown open** here, as chips, where the shelf had room only for a chip that opened a
 * menu. That is the level of nesting this removes, and it is also why [MultiSelectFilter] and its
 * siblings stay in this file - the in-report toolbar still uses them, inside a card where there is
 * no room for anything else.
 *
 * @param open the page's flag from `PageState.filtersOpen`, so the icon above can set it.
 * @param active anything at all is narrowing the page, the stock box included, which is when Clear
 *   filters is offered. Not what lights the header's dot - see `PageState.filtersInSheet`.
 * @param title what the sheet is narrowing. Defaults to the whole page; the Portfolio passes
 *   `Filter positions`, because its date and its order narrow the Positions card and leave the
 *   record above it alone. A sheet reached from the page header would otherwise be claiming the
 *   whole page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(
    open: MutableState<Boolean>,
    active: Boolean,
    onClearAll: () -> Unit,
    title: String = "Filters",
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!open.value) return
    // **A kit sheet since 5.0**: from the bottom on the cover screen, beside the page unfolded.
    KitSheet(onDismiss = { open.value = false }, posture = LocalFoldPosture.current, title = title) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = palette.ink)
            // On the title's line rather than at the foot of the sheet: the sections below are as
            // long as the page has channels, and a button under them is one the reader has to
            // scroll to in order to undo something they can see from the top.
            if (active) {
                KitButton("Clear filters", onClick = onClearAll, kind = ButtonKind.DANGER, size = ButtonSize.SMALL)
            }
        }
        Column(Modifier.padding(horizontal = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            content()
        }
    }
}

/**
 * One filter's name and its choices, shown open.
 *
 * The heading is what lets the chips lose the labels a chip-and-menu had to carry: a row reading
 * `All / MubasherTrade / ...` needs no chip to say what it is once the word `Channels` is above it.
 */
@Composable
internal fun FilterSection(label: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = palette.ink,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
            itemVerticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Every choice for a filter that accepts any combination, as chips.
 *
 * The `All` chip leads and is selected while nothing else is, so "untouched means everything" is on
 * the screen rather than implied by an empty row - the same convention [MultiSelectFilter] has to
 * state in words inside a menu, on a surface with room to just show it.
 */
@Composable
internal fun MultiSelectSection(
    label: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (options.isEmpty()) return
    FilterSection(label) {
        ChoiceChip("All", selected.isEmpty(), onClear)
        options.forEach { option -> ChoiceChip(option, option in selected) { onToggle(option) } }
    }
}

/**
 * What a page's date filter is narrowed to - nothing, a rolling range from today, or one exact
 * date. One slot rather than two flags, because a range and an exact date are two answers to the
 * same question and only one can stand at a time: choosing one is what clears the other.
 */
sealed interface DateFilter {
    data class Exact(val date: String) : DateFilter
    data object ThisWeek : DateFilter
    data object ThisMonth : DateFilter

    /** Whether an ISO `yyyy-MM-dd` date falls inside what this filter asks for. */
    fun matches(date: String): Boolean {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
        return when (this) {
            is Exact -> date == this.date
            ThisWeek -> {
                val start = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
                !parsed.isBefore(start) && !parsed.isAfter(start.plusDays(6))
            }
            ThisMonth -> {
                val today = LocalDate.now()
                parsed.year == today.year && parsed.month == today.month
            }
        }
    }

    /** What an empty list narrowed by this filter is empty of, for the sentence that says so. */
    fun describe(): String = when (this) {
        is Exact -> "on " + (
            runCatching { LocalDate.parse(date) }.getOrNull()?.format(AppDates.DayMonthYear) ?: date
            )
        ThisWeek -> "this week"
        ThisMonth -> "this month"
    }
}

/** True when there is no filter at all, or [date] (nullable) falls inside the one there is. */
internal fun DateFilter?.accepts(date: String?): Boolean =
    this == null || (date != null && matches(date))

/**
 * A choice restricted to the dates that actually have something behind them, picked from a
 * calendar rather than named as chips - the list of chips this replaced grew with the record and
 * pushed the rest of a page's filters further down the sheet on every report saved. "This week"
 * and "this month" stay named rather than folding into the calendar: a rolling range is not a date
 * the picker could ever land on by tapping a day.
 *
 * [SelectableDates] is what keeps a picked date honest: every date outside [dates] is greyed out
 * and cannot be tapped, so the picker can never land on a date the page would show nothing for.
 */
@Composable
internal fun DateFilterSection(
    label: String,
    dates: List<String>,
    selected: DateFilter?,
    onSelect: (DateFilter?) -> Unit,
) {
    if (dates.isEmpty()) return
    val allowed = remember(dates) {
        dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
    }
    var open by remember { mutableStateOf(false) }
    val exact = selected as? DateFilter.Exact
    FilterSection(label) {
        ChoiceChip("All", selected == null) { onSelect(null) }
        ChoiceChip("This week", selected == DateFilter.ThisWeek) { onSelect(DateFilter.ThisWeek) }
        ChoiceChip("This month", selected == DateFilter.ThisMonth) { onSelect(DateFilter.ThisMonth) }
        ChoiceChip(
            exact?.date
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.format(AppDates.DayMonthYear)
                ?: "Pick a date",
            exact != null,
        ) { open = true }
    }
    if (open) {
        // The kit's calendar since 5.0. Every day outside [dates] is faint and refuses a press, so
        // the filter can never land on a session the page would show nothing for, and the arrows
        // stop at the first and last month that has one.
        com.ikverse.egxanalyzer.ui.kit.KitCalendarSheet(
            title = "Pick a date",
            selected = exact?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: allowed.maxOrNull(),
            onPick = { picked ->
                onSelect(DateFilter.Exact(picked.toString()))
                open = false
            },
            onDismiss = { open = false },
            posture = LocalFoldPosture.current,
            allowed = { it in allowed },
            earliest = allowed.minOrNull(),
            latest = allowed.maxOrNull(),
        )
    }
}

/**
 * The orders a list can be read in.
 *
 * **Below a rule and under its own heading**, because an order is not a filter: it hides nothing,
 * it is left out of `filtersActive`, and Clear filters does not touch it. Mixed in with the filters
 * it reads as one of them - which is what the shelf lived with, the sort chip sitting on the same
 * line as the rest and kept out of the clear-all by a comment no reader could see. There is no
 * `All` here for the same reason: a list is always in some order.
 */
@Composable
internal fun <T> SortSection(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        com.ikverse.egxanalyzer.ui.kit.KitRule(thickness = 1.5.dp, color = palette.ink)
        FilterSection("Sort") {
            options.forEach { option ->
                ChoiceChip(label(option), option == selected) { onSelect(option) }
            }
        }
    }
}

/** One choice in a sheet's section: a chip that says whether it is the one in force. */
@Composable
internal fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // The kit's chip since 5.0: a hairline capsule, ink with the highlight's tick when chosen.
    KitChip(label = label, selected = selected, onClick = onClick)
}

internal typealias FlowRowScope = androidx.compose.foundation.layout.FlowRowScope
