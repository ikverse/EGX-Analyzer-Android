package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
 * The shelf a page's filters sit on.
 *
 * They used to sit on nothing. A bare [FilterRow] between two cards is the one loose element on
 * pages otherwise built of them, and on a 411dp cover screen four controls plus a text button wrap
 * into three ragged lines above the content - with **Clear filters inside the flow**, so it landed
 * wherever the wrap put it and moved every time a chip changed width (`All channels` to
 * `2 channels` is enough to shift it).
 *
 * The fill is `surfaceContainerLow`, deliberately a step **below** the `surfaceContainer` cards it
 * filters and above the `background` well they sit on. It should read as a shelf the controls stand
 * on rather than as another card of content competing with the record - the same reason it carries
 * no title: the chips name themselves.
 *
 * **Two layouts, switched on [LocalWindowWidth].** The shell's published answer to "is this window
 * wide", already used by `Screen` and `TodayCard`, rather than a fourth threshold of this file's
 * own. Wide: everything on one line, the controls in a weighted [FlowRow] so an overflow wraps
 * *inside* the shelf, and Clear pinned hard right where it cannot move. Compact: [search] stays out
 * and the rest fold behind a chip.
 *
 * **[search] never folds**, and that is not an aesthetic choice - Results and the Portfolio both
 * carry the same comment about it, that it is "the control someone arrives at the screen already
 * knowing they want". Burying it would contradict the reason it leads.
 *
 * The fold is the pattern Results' in-report toolbar already uses, chip label included, so this is
 * that rule reused rather than a second one invented. The chip reads **"Filters on"** whenever one
 * of the folded controls is narrowing the list, so a filtered list never looks unfiltered on the
 * screen where most of the controls are out of sight.
 */
@Composable
internal fun FilterBar(
    /** Anything at all is narrowing the list, which is when Clear filters is offered. */
    active: Boolean,
    onClearAll: () -> Unit,
    /**
     * One of the **folded** controls is narrowing it, which is what lights the chip.
     *
     * Separate from [active] because on a compact screen the search box is still on show: a chip
     * reading "Filters on" because of a box the reader is looking at would be reporting something
     * they can already see, and would go on reporting it once they had cleared everything else.
     */
    folded: Boolean = active,
    /** Drawn first and never folded away. */
    search: (@Composable () -> Unit)? = null,
    content: @Composable FlowRowScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val compact = LocalWindowWidth.current == WindowWidth.COMPACT
    Surface(
        shape = MaterialTheme.shapes.large,
        // Transparent, and that is the second attempt. It was `surfaceContainerLow` on the argument
        // that a shelf should sit a step below the cards it filters - which is sound reasoning about
        // elevation and wrong about this palette: the well is #0B0F14, the shelf was #11161C and a
        // card is #151A21, six units apart, so a shelf immediately above a card read as one
        // continuous background with a hairline through it. No fill cannot make that mistake. It
        // takes whatever is behind it and the outline says where it ends, which is also what lets
        // it sit on the page on two tabs and inside the Positions card on the third.
        color = Color.Transparent,
        border = cardOutline,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = Space.m, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (compact) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Weighted, so the search box takes whatever the chip beside it leaves rather
                    // than its fixed width - on 411dp those two are the whole line.
                    if (search != null) Box(Modifier.weight(1f)) { search() }
                    FilterChip(
                        selected = folded,
                        onClick = { open = !open },
                        label = { Text(if (folded) "Filters on" else "Filters", maxLines = 1) },
                        trailingIcon = {
                            Icon(
                                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.Inline),
                            )
                        },
                    )
                }
                AnimatedVisibility(open) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.s),
                            verticalArrangement = Arrangement.spacedBy(Space.s),
                            itemVerticalAlignment = Alignment.CenterVertically,
                            content = content,
                        )
                        // On its own line and hard right, for the reason it is pinned right on a
                        // wide screen: in the flow it moves whenever a chip's label changes.
                        if (active) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = onClearAll) { Text("Clear filters") }
                            }
                        }
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Says what the shelf is without spending a heading on it. Only here: on the
                    // cover screen the width it would cost is width the controls need.
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Inline),
                    )
                    // Weighted, so an overflow wraps inside the shelf instead of pushing Clear off
                    // the end of it. Results needs this: its sort chip reads "Run date, newest",
                    // nearly twice the width of the others, and four controls do not fit 638dp.
                    FlowRow(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                        verticalArrangement = Arrangement.spacedBy(Space.s),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        search?.invoke()
                        content()
                    }
                    if (active) {
                        TextButton(onClick = onClearAll) { Text("Clear filters") }
                    }
                }
            }
        }
    }
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
        modifier = modifier.height(FilterControlHeight).width(StockFieldWidth),
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
