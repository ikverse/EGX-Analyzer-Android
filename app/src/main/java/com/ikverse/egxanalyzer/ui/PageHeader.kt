package com.ikverse.egxanalyzer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * The page's own name, at the top of the page, shrinking as the page is read.
 *
 * It replaces the band that used to say `EGX Analyzer` above every screen, removed on 2026-09-09.
 * That band said the same two words on all five tabs and cost a row of the window to do it, while
 * the thing a reader actually needs at the top - which page this is - was the first line *inside*
 * the scroll and went away as soon as they read anything.
 *
 * **One title, shrinking, rather than two cross-fading.** Material's own large app bar fades a big
 * title out and a small one in, which is two titles briefly drawn over each other. Here the size,
 * the letter spacing and the icon are interpolated on [collapse], so what a reader sees is the page
 * name getting smaller and staying put - and the header can never be caught showing two of them.
 * That costs a recomposition of this row per frame of the first [HeaderCollapseTravel] of a scroll,
 * which is one glyph and one word; the page below is untouched, because nothing here is read from
 * inside it.
 *
 * **The icon is the destination's own**, taken from the same property the navigation bar reads and
 * tinted `primary`, which inside a page is that page's hue. So the glyph at the top of Analyze and
 * the lit glyph in the bar are the same drawing in the same cyan, and neither can drift from the
 * other. It is the **selected** icon on purpose: the page you are looking at is the selected one.
 *
 * @param collapse 0 with the page at the top, 1 once the header has finished shrinking. Owned by
 *   [Screen], which is where the scroll it is read from lives. **A lambda rather than a value**:
 *   read at the call site it would be the whole page recomposing on every frame of a collapse, and
 *   read here it is this row and nothing else.
 * @param search the page's own stock filter, or null on a page with no list to narrow - see
 *   `PageState.stockFilter`. Null draws no search icon at all, because an icon opening a box that
 *   narrows nothing is a control the page cannot honour. It holds a **picked ticker** rather than
 *   whatever was typed: see [SearchField] and [TickerPicker].
 * @param stocksOnPage the tickers this page actually holds, which the picker offers first. Called
 *   once when the list opens rather than per keystroke - see `pageStocks` for what it walks.
 * @param current whether this page is the one the reader is actually on. See [SearchField], where
 *   it is what keeps a keyboard from opening on a page nobody has arrived at.
 * @param filters the page's own "is the filter sheet open" flag, or null on a page with no filters
 *   - `PageState.filtersOpen`. The icon sets it; the screen underneath draws what is inside, since
 *   this row has no idea what a page filters by. See `FilterSheet`.
 * @param filtered whether one of the filters **in that sheet** is narrowing the page, which puts a
 *   dot on the icon and holds both icons on screen from the first frame of the collapse. The stock
 *   box is not counted: it says so itself by staying open. See `PageState.filtersInSheet`.
 */
@Composable
internal fun PageHeader(
    destination: AppDestination,
    collapse: () -> Float,
    search: MutableState<String>?,
    current: Boolean,
    filters: MutableState<Boolean>?,
    filtered: Boolean,
    /** The tickers this page holds, for the picker's **On this page** group. See `pageStocks`. */
    stocksOnPage: () -> Set<String>,
    modifier: Modifier = Modifier,
) {
    var opened by remember { mutableStateOf(false) }
    // What is being typed into the box, which since the picker arrived is **not** what the page is
    // filtered by: typing narrows the list of listings on offer, and only a pick reaches the page.
    // Header-local, so a fold loses a half-typed query and keeps the pick - which is the right way
    // round, since the pick is the thing the reader can see the page answering.
    var typed by remember { mutableStateOf("") }
    val picked = search?.value.orEmpty()
    // Open because it was pressed, or because it is still narrowing the page. The second half is
    // what makes the box the indicator as well as the control: a page filtered to one stock with no
    // box on screen saying so is a page that looks as though it has lost its other rows.
    val searching = search != null && (opened || picked.isNotBlank())

    fun close() {
        opened = false
        typed = ""
        search?.value = ""
    }
    // Pressing past the list is "never mind", not "stop filtering": whatever was already picked
    // stays and the box goes back to showing it. Only the X and back clear the page.
    fun dismiss() {
        if (picked.isNotBlank()) {
            opened = false
            typed = ""
        } else {
            close()
        }
    }

    fun pick(ticker: String) {
        search?.value = ticker
        typed = ""
        opened = false
    }
    // The same entrance every ticker in the app opens the stock sheet through. Read here rather
    // than inside the list, because the list has to be put away before the sheet is raised: one
    // left standing over a modal sheet is a scrim across the thing it was asked to open.
    val openStock = LocalOpenStock.current
    // Closing the box is not navigation and never reaches `AppState.goBack`: back closes what is
    // open on the screen, which is what the press means while a keyboard is up. Enabled only while
    // the box is open **and this is the page the reader is on** - the pager keeps the neighbouring
    // pages composed, so an unguarded handler here would let a filtered page one tab away swallow a
    // back press meant for the tab in front of the reader.
    BackHandler(enabled = searching && current, onBack = ::close)

    // Held collapsed while the box is open, whatever the scroll says. The box lives in the bar the
    // title collapses into, so a page too short to scroll - which is most pages once a filter is
    // narrowing them - would otherwise expand the title back over the control being typed into.
    val fraction = if (searching) 1f else collapse()

    Box(
        modifier
            .fillMaxWidth()
            // The top inset, held by the header rather than by the shell. The page runs to the top
            // of the window now so that its wash carries up behind the clock - see `AppContent` -
            // which leaves this row responsible for not being under it. `safeDrawing` rather than
            // `statusBars`, so a cutout taller than the bar is cleared too; `.only(Top)` keeps the
            // keyboard's inset out of it.
            .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues())
            .height(lerp(ExpandedHeight, CollapsedHeight, fraction))
            .padding(horizontal = Space.l),
        contentAlignment = Alignment.CenterStart,
    ) {
        AnimatedContent(
            targetState = searching,
            // **A plain cross-fade, and the box does not grow out of the icon's corner.** It did,
            // through `expandHorizontally(expandFrom = End)`, and that was the whole of the bug
            // where pressing search on any tab but the first walked the reader back towards it.
            // An expand-from-end starts the content at zero width with its **end** pinned, so on
            // the first frame the field is laid out with its left edge most of a screen to the
            // left of the window - and the focus request below fired on exactly that frame. A
            // `BasicTextField` taking focus asks every scrollable ancestor to bring its rect into
            // view; the outermost of those is `DestinationPager`, which duly scrolled left to
            // reveal a rect sitting off the start edge, landing on the previous tab. Nothing was
            // wrong with the focus or with the pager: the rect was honestly reported and honestly
            // answered. Both ends of that are fixed here - the field is full width from its first
            // frame, and the focus waits for the transition to finish - because either alone would
            // leave the trap armed for the next person who animates this row.
            transitionSpec = { fadeIn(tween(SearchSwapMillis)) togetherWith fadeOut(tween(SearchSwapMillis)) },
            label = "header search",
        ) { open ->
            // Both halves fill the row, so this only ever reports "has the fade finished".
            val settledIn = transition.currentState == transition.targetState
            if (open && search != null) {
                // Two states inside one open box, swapped without a fade: the field while a stock
                // is being chosen, and the stock itself once one has been. They are the same
                // control at two moments, so a cross-fade between them would announce a change of
                // control where the reader has just made a choice.
                if (opened) {
                    SearchField(
                        typed = typed,
                        onTyped = { typed = it },
                        onClose = ::close,
                        onPick = ::pick,
                        onDismiss = ::dismiss,
                        onOpenStock = { ticker ->
                            dismiss()
                            openStock(ticker)
                        },
                        stocksOnPage = stocksOnPage,
                        focusable = current && settledIn,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PickedStock(
                        ticker = picked,
                        onReopen = { opened = true },
                        onClear = ::close,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                TitleRow(
                    destination = destination,
                    collapse = fraction,
                    onSearch = if (search == null) null else ({ opened = true }),
                    onFilters = if (filters == null) null else ({ filters.value = true }),
                    filtered = filtered,
                )
            }
        }
    }
}

@Composable
private fun TitleRow(
    destination: AppDestination,
    collapse: Float,
    /** Null on a page with nothing to narrow, which draws no icon rather than a dead one. */
    onSearch: (() -> Unit)?,
    /** Null on a page with no filters, for the same reason. */
    onFilters: (() -> Unit)?,
    filtered: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            destination.selectedIcon,
            // The word is right beside it, and a reader announcing both says it twice.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(lerp(ExpandedIcon, CollapsedIcon, collapse)),
        )
        Text(
            destination.label,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = lerp(ExpandedTitle, CollapsedTitle, collapse),
                letterSpacing = lerp(ExpandedTracking, CollapsedTracking, collapse),
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // **The icons arrive with the collapsed bar**, over the last of the shrink, rather than
        // sitting there from the start: at rest the page name is the only thing at the top of the
        // screen, which is the point of the change, and controls that had to be there always would
        // be a title bar with things in it again. Composed at every collapse but only pressable
        // once they have finished arriving, so a tap aimed at the page cannot land on a glyph that
        // is still fading in.
        //
        // **Except on a page that is filtered, where they are there from the first frame.** The
        // filter sheet is modal, so with the icons faded out a page narrowed to two channels sits
        // at the top of its scroll with nothing on screen saying so - and the reader's next thought
        // is that rows have gone missing, which is exactly what the shelf's lit chip existed to
        // prevent. The stock box solves its own half by staying open; this is the other half. It
        // costs the empty header only on pages the reader has actually filtered.
        val arrived = ((collapse - SearchFadeStart) / (1f - SearchFadeStart)).coerceIn(0f, 1f)
        val shown = if (filtered) 1f else arrived
        if (shown > 0f) {
            if (onSearch != null) {
                HeaderAction(
                    icon = Icons.Outlined.Search,
                    description = "Filter by stock",
                    shown = shown,
                    onClick = onSearch,
                )
            }
            if (onFilters != null) {
                HeaderAction(
                    icon = Icons.Outlined.FilterList,
                    description = if (filtered) "Filters, on" else "Filters",
                    shown = shown,
                    dot = filtered,
                    onClick = onFilters,
                )
            }
        }
    }
}

/**
 * One of the header's two glyphs, fading in as the title collapses.
 *
 * @param shown 0 before it has begun arriving, 1 once it is fully there. **Pressable only at 1**,
 *   so a tap meant for the page cannot land on something half drawn.
 * @param dot a mark in the corner saying this control is doing something right now. Only the
 *   filters carry one: the search box reports itself by being open.
 */
@Composable
private fun HeaderAction(
    icon: ImageVector,
    description: String,
    shown: Float,
    onClick: () -> Unit,
    dot: Boolean = false,
) {
    Box(
        Modifier
            .size(SearchTarget)
            .clip(CircleShape)
            .then(if (shown == 1f) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = shown),
            modifier = Modifier.size(lerp(SearchIconSize * 0.7f, SearchIconSize, shown)),
        )
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(DotInset)
                    .size(DotSize)
                    .clip(CircleShape)
                    // The page's own hue, which is what the title's icon beside it is tinted with:
                    // a dot in any other colour would be the one mark on this row that belongs to
                    // no page in particular.
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = shown)),
            )
        }
    }
}

/**
 * The box the search icon opens, across the bar the title was in, with the catalog under it.
 *
 * **It is the page's own stock filter, and what it narrows by is now a listing rather than a
 * phrase.** Results, Insights and the Portfolio each drew a box on their filter shelf; this is the
 * same state - `PageState.resultsStock` and its two siblings - drawn once, at the top, reachable
 * from anywhere down a long page. What changed on 2026-09-11 is what reaches that state: typing
 * narrows [TickerPickerList] and a pick writes a ticker. Nothing here is a lookup in the sense the
 * first version of this header was - the page is still filtered rather than replaced by a sheet -
 * and the sheet is a trailing target on a row, not what a press does.
 *
 * Not a `TextField`, for [StockFilterField]'s reason, which this deliberately resembles: Material's
 * own is built for a form and brings a label, a container and 56dp of height with it, where this is
 * one line inside a bar that is 56dp in total.
 *
 * @param focusable whether it is safe to take focus yet: this page is the one the reader is on,
 *   the pager has stopped moving, and the row's own cross-fade has finished. **All three are
 *   load-bearing**, because a `BasicTextField` taking focus asks every scrollable ancestor to bring
 *   its rect into view and the outermost of those is `DestinationPager`. Focus taken on a page the
 *   reader has not arrived at, or on a frame where the field has not reached its final position,
 *   is a rect the pager answers by scrolling somewhere nobody asked to go. See the transition at
 *   the call site for the frame that actually shipped this bug, and `revealIfOnScreen` for the
 *   same trap arriving through a reveal instead.
 */
@Composable
private fun SearchField(
    typed: String,
    onTyped: (String) -> Unit,
    onClose: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenStock: (String) -> Unit,
    stocksOnPage: () -> Set<String>,
    focusable: Boolean,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    // Where the list hangs from, in pixels, measured off this row's own top edge - the popup is
    // positioned against the box's bounds and knows nothing about how tall the box is drawn.
    val anchorOffset = with(LocalDensity.current) { (SearchFieldHeight + Space.s).roundToPx() }
    // And how much window is left under that point, which the list is drawn exactly as tall as.
    // A popup taller than the space it hangs in is shifted **up** by the window manager to fit,
    // which would put the list back over the header it belongs to. See `TickerPickerList`.
    val windowHeight = LocalWindowInfo.current.containerSize.height
    var spaceBelow by remember { mutableIntStateOf(0) }
    // The box was opened by somebody who wants to type in it - but only once the pages have stopped
    // moving and this is the one in front of them. `LocalTabsSettled` is the shell's own answer to
    // "has the reader arrived", written by the pager and true beside a rail.
    val settled = LocalTabsSettled.current
    LaunchedEffect(focusable, settled.value) {
        if (focusable && settled.value) focus.requestFocus()
    }
    // The list is anchored to this box rather than to the row, so it stands under the field
    // wherever the field is laid out.
    Box(
        modifier.onGloballyPositioned { placed ->
            spaceBelow = windowHeight - (placed.positionInWindow().y.roundToInt() + anchorOffset)
        },
    ) {
        Surface(
            Modifier.fillMaxWidth().height(SearchFieldHeight),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier.padding(start = Space.m, end = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Inline),
                )
                Box(Modifier.weight(1f).padding(horizontal = Space.s)) {
                    if (typed.isEmpty()) {
                        Text(
                            // What it asks for, not what it does: the reader is choosing a listing
                            // here, and the page being narrowed is the consequence of the choice.
                            "Search stocks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = typed,
                        onValueChange = onTyped,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                Box(
                    Modifier.size(SearchTarget).clip(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        // It clears as well as closes, which is what keeps the box the one visible
                        // sign that the page underneath is narrowed.
                        contentDescription = "Clear stock filter",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Inline),
                    )
                }
            }
        }
        TickerPickerList(
            typed = typed,
            stocksOnPage = stocksOnPage,
            onPick = onPick,
            onDismiss = onDismiss,
            onOpenStock = onOpenStock,
            anchorOffset = anchorOffset,
            spaceBelow = spaceBelow,
        )
    }
}

/**
 * The listing the page is narrowed to, in the bar the box was in.
 *
 * The box closes onto this rather than back to the title, for the reason the box used to stay open
 * while it held text: a page showing one stock's rows with nothing at the top saying which stock is
 * a page that looks as though it has lost the rest. It says more than the typed query ever could -
 * the mark, the code and the company's own name - because the pick is a listing rather than a
 * phrase somebody remembered.
 *
 * Pressing it reopens the picker; the X clears the page, which is the same X the field carries and
 * means the same thing.
 */
@Composable
private fun PickedStock(
    ticker: String,
    onReopen: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.height(SearchFieldHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier
                .clickable(onClick = onReopen)
                .padding(start = Space.s, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StockLogo(ticker, LogoSize.Row)
            Text(
                ticker,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(start = Space.s),
            )
            Text(
                TickerPicker.name(ticker),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = Space.s),
            )
            Box(
                Modifier.size(SearchTarget).clip(CircleShape).clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Clear stock filter",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Inline),
                )
            }
        }
    }
}

/**
 * How tall the header stands with the page at the top, and how far it falls.
 *
 * The difference between the two is what the header eats out of the scroll before the page moves,
 * so it is deliberately small: 40dp is a flick, and a reader who scrolls a whole screen should be
 * looking at content rather than watching chrome finish an animation.
 */
private val ExpandedHeight = 96.dp
private val CollapsedHeight = 56.dp

/** How far the page scrolls into the header before it has finished shrinking. */
internal val HeaderCollapseTravel: Dp = ExpandedHeight - CollapsedHeight

/** Sized to the title beside it at either end, the way the app's mark used to be. */
private val ExpandedIcon = 30.dp
private val CollapsedIcon = 22.dp

/**
 * `headlineLarge`'s own size, and what it falls to.
 *
 * The collapsed end is a shade above `titleLarge`'s 19sp, which is where the app's name used to
 * sit, because this one shares its row with an icon and a control rather than heading a page alone.
 * The tracking travels with it: -0.5sp is set for 30sp text and reads as cramped at 20.
 */
private val ExpandedTitle = 30.sp
private val CollapsedTitle = 20.sp
private val ExpandedTracking = (-0.5).sp
private val CollapsedTracking = (-0.2).sp

/** Where in the collapse the search icon starts arriving. */
private const val SearchFadeStart = 0.6f

/** A press target for the glyph, not the size of it. */
private val SearchTarget = 40.dp
private val SearchIconSize = 22.dp

/**
 * The mark on the filter icon, and how far in from the corner of its target it sits.
 *
 * Placed against the glyph rather than the 40dp press target - the glyph is 22 of that 40, so a dot
 * hung in the target's own corner floats in the gap beside the icon instead of touching it.
 */
private val DotSize = 7.dp
private val DotInset = 8.dp

private val SearchFieldHeight = 40.dp

/**
 * How long the title and the search box take to trade places.
 *
 * Brief, because the keyboard is waiting on the other side of it - see [SearchField], where the
 * focus is held back until this has finished.
 */
private const val SearchSwapMillis = 120
