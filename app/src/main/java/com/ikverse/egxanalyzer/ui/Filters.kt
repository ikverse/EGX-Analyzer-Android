package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.max

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
 * **One layout at every width, and the filters are always folded.** It began as two - everything on
 * one line on a wide screen, folded on a narrow one - and the wide form was the wrong answer even
 * where it fitted: a shelf carrying four controls and a button is a toolbar the reader has to read
 * before they can ignore it, on a page whose subject is underneath it. Two controls is a line that
 * is scanned rather than read, and it is the same line on both panels, so the tab does not
 * rearrange itself when the phone opens.
 *
 * **[search] never folds**, and that is not an aesthetic choice - Results and the Portfolio both
 * carry the same comment about it, that it is "the control someone arrives at the screen already
 * knowing they want". Burying it would contradict the reason it leads.
 *
 * The fold is the pattern Results' in-report toolbar already uses, chip label included, so this is
 * that rule reused rather than a second one invented - it simply applies at every width here rather
 * than only on a cover screen. The chip reads **"Filters on"** whenever one
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
     * Separate from [active] because the search box is always on show: a chip reading "Filters on"
     * because of a box the reader is looking at would be reporting something they can already see,
     * and would go on reporting it once they had cleared everything else.
     */
    folded: Boolean = active,
    /**
     * Drawn first and never folded away.
     *
     * Receives the modifier that makes it fill the line, the way `ResponsiveRows` hands one to its
     * items: the bar owns how wide this is, and a caller that quietly kept its own width would sit
     * at 150dp on a 606dp line with the rest spent on nothing.
     */
    search: (@Composable (Modifier) -> Unit)? = null,
    /**
     * What the bar fills with **while it is held at the top of the screen**.
     *
     * Transparent at rest, because the bar is a shelf rather than a card and takes whatever is
     * behind it (see the `color` argument below). Held, it cannot: the list it filters is passing
     * underneath, and a transparent shelf with rows sliding through it is unreadable.
     *
     * A step **above** what it sits on rather than the same colour - `surfaceContainer` over the
     * page's well, `surfaceContainerHigh` inside the Positions card. Filling with the background
     * made it a slab of page that happened to hold the controls, with the rows appearing out of
     * nothing at its edge. A step up, with a shadow under it and its own rounded corners, it reads
     * as what it now is: one card lifted off the page while the rest of it travels underneath.
     */
    floatingColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    /**
     * Receives a [RowScope], not a `FlowRowScope`: these sit on one scrolling line rather than
     * wrapping onto a second. `FilterRow` keeps the flow, because the in-report toolbar it draws
     * shares its row with a Hide button and is a different shape.
     */
    content: @Composable RowScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // How far the bar has been scrolled past the top of the page's viewport, and the room it has to
    // travel back down before it would leave the group it belongs to. This is the same trick the
    // report's own table toolbar uses (see RecommendationTable): the bar is pushed down by exactly
    // what the scroll took away, so it looks pinned without being lifted out of the page - and the
    // clamp means it leaves with the last thing it filters rather than hanging over whatever card
    // comes next.
    //
    // Measured on the outer Box, which is never translated. Reading the position of the thing being
    // moved is a feedback loop: the offset moves it, which changes its position, which changes the
    // offset.
    val viewportTop = LocalViewportTop.current
    // Held this far below the top of the viewport rather than hard against it. Flush, the bar butts
    // straight onto the app header above it and the two read as one slab of chrome - and a card
    // that is floating cannot be touching the thing it floats over. The same [Space.m] the page
    // puts between its cards. It also starts holding a fraction early, which is what stops it
    // touching at all.
    val pinGap = with(LocalDensity.current) { Space.m.toPx() }
    var pin by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            // Above the content it filters, so the rows pass behind it rather than over it.
            .zIndex(1f)
            .onGloballyPositioned { coordinates ->
                val top = coordinates.positionInWindow().y
                // The parent is the column holding the bar and everything it narrows - the page on
                // Results and Insights, the Positions card on the Portfolio - so its foot is where
                // the bar has nothing left to sit over.
                val room = coordinates.parentLayoutCoordinates?.let { parent ->
                    parent.positionInWindow().y + parent.size.height - top - coordinates.size.height
                } ?: 0f
                pin = (viewportTop + pinGap - top).coerceIn(0f, max(0f, room))
            },
    ) {
    val floating = pin > 0f
    // Animated, so the bar rises as it takes hold rather than a shadow appearing under a shelf that
    // has not visibly moved. Short: the lift happens while a finger is dragging, and anything slower
    // arrives after the scroll it belongs to.
    val lift by animateDpAsState(if (floating) FloatingFilterLift else 0.dp, label = "filter bar lift")
    Box(Modifier.fillMaxWidth().graphicsLayer { translationY = pin }) {
    Surface(
        // The shadow is the whole of what says this is off the page: the fill alone is a card, and a
        // card the same size as the ones underneath it reads as one of them that stopped scrolling.
        shadowElevation = lift,
        shape = MaterialTheme.shapes.large,
        // Transparent, and that is the second attempt. It was `surfaceContainerLow` on the argument
        // that a shelf should sit a step below the cards it filters - which is sound reasoning about
        // elevation and wrong about this palette: the well is #0B0F14, the shelf was #11161C and a
        // card is #151A21, six units apart, so a shelf immediately above a card read as one
        // continuous background with a hairline through it. No fill cannot make that mistake. It
        // takes whatever is behind it and the outline says where it ends, which is also what lets
        // it sit on the page on two tabs and inside the Positions card on the third.
        // Opaque only while it is off the page. At rest it goes back to taking whatever is behind
        // it, which is the shelf this has always been on a page nobody has scrolled yet.
        color = if (floating) floatingColor else Color.Transparent,
        border = cardOutline,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = Space.m, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Weighted, and [StockFilterField] now lets the weight through - it takes whatever
                // the chip beside it leaves, at every width. A box fixed at 150dp on a 606dp bar
                // spent the rest of the line on nothing.
                if (search != null) Box(Modifier.weight(1f)) { search(Modifier.fillMaxWidth()) }
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
                    // One line that scrolls, not a row that wraps. Three chips fit a cover screen
                    // only while their labels are short: the panel has 355dp inside it on Insights
                    // and Results and 323 on the Portfolio, where the Positions card costs it
                    // another 32 - and "Source record, best first" alone takes Insights past 385.
                    // Wrapping made the panel two lines tall for one long label; scrolling keeps it
                    // one at every width, and `fadingScrollbar` draws nothing at all when there is
                    // nothing to scroll, so the short case is indistinguishable from a plain row.
                    Row(
                        Modifier.scrollableRow(),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                        content = content,
                    )
                    // Inside the panel rather than on the line above, which is the cost of holding
                    // that line to two controls: with the panel shut and a filter on, clearing means
                    // opening it first. The chip says "Filters on" so it is never a surprise, and
                    // the search box keeps its own cross for the case that comes up most.
                    if (active) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onClearAll) { Text("Clear filters") }
                        }
                    }
                }
            }
        }
    }
    }
    }
}

/**
 * How far the filter bar stands off the page once it is holding at the top.
 *
 * Material's own resting elevation for a card that floats over content. Higher and the shadow
 * spreads far enough to darken the row under it into something that looks selected; lower and on
 * this palette there is no shadow at all - the well is nearly black, and a 2dp shadow on it is
 * indistinguishable from none.
 */
private val FloatingFilterLift = 6.dp

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
