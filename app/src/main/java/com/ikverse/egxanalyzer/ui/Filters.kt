package com.ikverse.egxanalyzer.ui

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * A dropdown of checkboxes, for a filter that accepts any combination.
 *
 * Chips would be simpler, but a row of them grows with the number of channels and pushes the
 * results themselves off the screen. A menu keeps the filter one line wide however many sources
 * there are, and says in that line how many are picked.
 *
 * An empty selection means everything, not nothing: a filter nobody has touched must not hide the
 * data it was opened to look at.
 */
@Composable
internal fun MultiSelectFilter(
    label: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Row(modifier) {
        FilterChip(
            selected = selected.isNotEmpty(),
            onClick = { open = true },
            label = {
                Text(
                    when {
                        selected.isEmpty() -> "All $label"
                        selected.size == 1 -> selected.first()
                        else -> "${selected.size} $label"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Inline),
                )
            },
        )
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = option in selected, onCheckedChange = null)
                            Text(
                                option,
                                Modifier.padding(start = Space.s),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    onClick = { onToggle(option) },
                )
            }
            if (selected.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Show all") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.Inline),
                        )
                    },
                    onClick = {
                        onClear()
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * A dropdown of checkboxes that start ticked, where the ticks are what is shown.
 *
 * The opposite convention to [MultiSelectFilter], and deliberately: that one filters a list nobody
 * has to look at in full, so untouched means everything. This one narrows a table whose rows are
 * all on screen already, and a box that has to be ticked to reveal a row that was already there
 * reads backwards.
 */
@Composable
internal fun CheckedSetFilter(
    label: String,
    options: List<String>,
    shown: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.size < 2) return
    var open by remember { mutableStateOf(false) }
    Row(modifier) {
        FilterChip(
            selected = shown.size < options.size,
            onClick = { open = true },
            label = {
                Text(
                    when {
                        shown.size == options.size -> "All $label"
                        shown.size == 1 -> shown.first()
                        else -> "${shown.size} of ${options.size} $label"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Inline),
                )
            },
        )
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = option in shown, onCheckedChange = null)
                            Text(
                                option,
                                Modifier.padding(start = Space.s),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    onClick = { onToggle(option) },
                )
            }
            if (shown.size < options.size) {
                DropdownMenuItem(
                    text = { Text("Select all") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.Inline),
                        )
                    },
                    onClick = {
                        onSelectAll()
                        open = false
                    },
                )
            }
        }
    }
}

/** A dropdown that picks one value, or none for everything. */
@Composable
internal fun SingleSelectFilter(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Row(modifier) {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected ?: "All $label", maxLines = 1) },
            trailingIcon = {
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Inline),
                )
            },
        )
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("All $label") },
                onClick = {
                    onSelect(null)
                    open = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * A dropdown that picks an order.
 *
 * Unlike the filters beside it there is no "none": a list is always in some order, so the chip
 * always names the one in force rather than sitting quietly at a default nobody chose.
 */
@Composable
internal fun <T> SortFilter(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Row(modifier) {
        FilterChip(
            selected = false,
            onClick = { open = true },
            label = { Text(label(selected), maxLines = 1) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.SwapVert,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Inline),
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Inline),
                )
            },
        )
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    trailingIcon = {
                        if (option == selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.Inline),
                            )
                        }
                    },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                )
            }
        }
    }
}

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
    ModalBottomSheet(
        onDismissRequest = { open.value = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .sheetDragSlop()
                .scrollableColumn()
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                // On the title's line rather than at the foot of the sheet, which is where the
                // shelf's panel kept it: the sections below are as long as the page has channels,
                // and a button under them is one the reader has to scroll to in order to undo
                // something they can see from the top.
                if (active) {
                    TextButton(onClick = onClearAll) { Text("Clear filters") }
                }
            }
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
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** Every choice for a filter that takes one value, or none for everything. */
@Composable
internal fun SingleSelectSection(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    if (options.isEmpty()) return
    FilterSection(label) {
        ChoiceChip("All", selected == null) { onSelect(null) }
        options.forEach { option -> ChoiceChip(option, option == selected) { onSelect(option) } }
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FilterSection("Sort") {
            options.forEach { option ->
                ChoiceChip(label(option), option == selected) { onSelect(option) }
            }
        }
    }
}

/** One choice in a sheet's section: a chip that says whether it is the one in force. */
@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (!selected) null else ({
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(IconSize.Inline),
            )
        }),
    )
}

/** The row a screen's filters sit in, wrapping rather than scrolling on a narrow screen. */
@Composable
internal fun FilterRow(
    active: Boolean,
    onClearAll: () -> Unit,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        if (active) {
            TextButton(onClick = onClearAll) { Text("Clear filters") }
        }
    }
}

/**
 * Narrows to one stock.
 *
 * Built to the height of a filter chip rather than as a text field: Material's outlined field is
 * 56dp against a chip's 32dp, so beside them it hung below the row and its floating label sat at a
 * different height from their text. Here the whole filter row reads as one line of controls.
 */
@Composable
internal fun StockFilterField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        // Its own height and width **first**, then the caller's. The other way round the 150dp
        // won whatever was passed in, which is why a `weight(1f)` around this box used to do
        // nothing at all. [StockFieldWidth] is still the right default for a box sharing a row
        // with other controls; on a bar where it is one of two, `fillMaxWidth` overrides it.
        modifier = Modifier
            .height(FilterControlHeight)
            .width(StockFieldWidth)
            .then(modifier),
    ) {
        Row(
            Modifier.padding(horizontal = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Inline),
            )
            Box(Modifier.weight(1f).padding(start = Space.s)) {
                if (value.isEmpty()) {
                    Text(
                        "Stock",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
            if (value.isNotEmpty()) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Clear stock filter",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(IconSize.Inline)
                        .clickable { onValueChange("") },
                )
            }
        }
    }
}

/** What a filter chip stands at, and therefore what everything beside one has to stand at. */
/** Every control in a filter row stands to this height, so the row reads as one line. */
internal val FilterControlHeight = 32.dp
/** A ticker is four letters; the box was sized for a sentence and ate half the toolbar. */
private val StockFieldWidth = 150.dp

internal typealias FlowRowScope = androidx.compose.foundation.layout.FlowRowScope
