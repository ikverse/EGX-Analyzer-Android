package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
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
