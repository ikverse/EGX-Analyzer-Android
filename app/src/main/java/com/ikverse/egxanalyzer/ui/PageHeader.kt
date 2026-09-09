package com.ikverse.egxanalyzer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.model.DirectoryStock

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
 * **The search icon arrives with the collapsed bar** rather than sitting there from the start, on
 * [SearchFadeStart]. At rest the page name is the only thing at the top of the screen, which is the
 * point of the change; a control that had to be there always would be a title bar with things in it
 * again.
 *
 * @param collapse 0 with the page at the top, 1 once the header has finished shrinking. Owned by
 *   [Screen], which is where the scroll it is read from lives. **A lambda rather than a value**:
 *   read at the call site it would be the whole page recomposing on every frame of a collapse, and
 *   read here it is this row and nothing else.
 * @param onOpenStock what a hit in the search panel opens. `LocalOpenStock` reaches the same place,
 *   but this is called from a panel this file owns, so it is passed rather than looked up.
 */
@Composable
internal fun PageHeader(
    destination: AppDestination,
    collapse: () -> Float,
    directory: List<DirectoryStock>,
    onOpenStock: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searching by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val fraction = collapse()

    fun close() {
        searching = false
        typed = ""
    }
    // Closing the box is not navigation and never reaches `AppState.goBack`: back closes what is
    // open on the screen, which is what the press means while a keyboard is up. Enabled only while
    // the box is open, so a reader with nothing open gets the shell's handler and then the system's.
    BackHandler(enabled = searching, onBack = ::close)

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                // The top inset, held by the header rather than by the shell. The page runs to
                // the top of the window now so that its wash carries up behind the clock - see
                // `AppContent` - which leaves this row responsible for not being under it.
                // `safeDrawing` rather than `statusBars`, so a cutout taller than the bar is cleared
                // too; `.only(Top)` keeps the keyboard's inset out of it.
                .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues())
                .height(lerp(ExpandedHeight, CollapsedHeight, fraction))
                .padding(horizontal = Space.l),
            contentAlignment = Alignment.CenterStart,
        ) {
            AnimatedContent(
                targetState = searching,
                transitionSpec = {
                    // The box grows out of the corner the icon was pressed in and shrinks back into
                    // it, so the control and what it opens are one movement rather than a swap.
                    if (targetState) {
                        (fadeIn() + expandHorizontally(expandFrom = Alignment.End))
                            .togetherWith(fadeOut())
                    } else {
                        fadeIn().togetherWith(
                            fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                        )
                    }.using(SizeTransform(clip = false))
                },
                label = "header search",
            ) { open ->
                if (open) {
                    SearchField(
                        typed = typed,
                        onTyped = { typed = it },
                        onClose = ::close,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    TitleRow(destination, fraction, onSearch = { searching = true })
                }
            }
        }
        if (searching) {
            StockLookupPanel(
                typed = typed,
                directory = directory,
                onPick = { ticker ->
                    close()
                    onOpenStock(ticker)
                },
            )
        }
    }
}

@Composable
private fun TitleRow(
    destination: AppDestination,
    collapse: Float,
    onSearch: () -> Unit,
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
        // Composed at every collapse but only pressable once it has finished arriving, so a tap
        // aimed at the page cannot land on a glyph that is still fading in.
        val shown = ((collapse - SearchFadeStart) / (1f - SearchFadeStart)).coerceIn(0f, 1f)
        if (shown > 0f) {
            Box(
                Modifier
                    .size(SearchTarget)
                    .clip(CircleShape)
                    .then(if (shown == 1f) Modifier.clickable(onClick = onSearch) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "Search a stock",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = shown),
                    modifier = Modifier.size(lerp(SearchIconSize * 0.7f, SearchIconSize, shown)),
                )
            }
        }
    }
}

/**
 * The box the search icon opens, across the bar the title was in.
 *
 * Not a `TextField`, for [StockFilterField]'s reason, which this deliberately resembles: Material's
 * own is built for a form and brings a label, a container and 56dp of height with it, where this is
 * one line inside a bar that is 56dp in total.
 */
@Composable
private fun SearchField(
    typed: String,
    onTyped: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    // The box was opened by somebody who wants to type in it. Requested once, on the composition
    // that put it on screen.
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(
        modifier.height(SearchFieldHeight),
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
                        "Search a stock",
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
                    contentDescription = "Close search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Inline),
                )
            }
        }
    }
}

/**
 * What the typed query found, under the header and over the page.
 *
 * Drawn on the page's own background rather than as a menu or a sheet: it belongs to the box above
 * it, and a floating surface here would be a second window over a page whose top the reader can
 * still see. It stops at [PanelMaxHeight] and scrolls from there, so a two-letter query cannot fill
 * the screen with stocks.
 */
@Composable
private fun StockLookupPanel(
    typed: String,
    directory: List<DirectoryStock>,
    onPick: (String) -> Unit,
) {
    // Once per query rather than once per row: the whole catalog is asked the same question, which
    // is the argument `StockSearch.query` already makes for normalizing outside the loop.
    val hits = remember(typed, directory) { StockLookup.matches(typed, directory) }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            when {
                typed.isBlank() -> PanelNote("Type a ticker or a company name.")
                hits.isEmpty() -> PanelNote("No stock matches “$typed”.")
                else -> LazyColumn(Modifier.heightIn(max = PanelMaxHeight)) {
                    items(hits, key = DirectoryStock::ticker) { stock -> StockHit(stock, onPick) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun StockHit(stock: DirectoryStock, onPick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPick(stock.ticker) }
            .padding(horizontal = Space.l, vertical = Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stock.ticker,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            // A column of its own, so the names beside them line up rather than starting at as many
            // places down the panel as there are lengths of ticker.
            modifier = Modifier.width(TickerColumn),
        )
        Column(Modifier.weight(1f)) {
            stock.nameEnglish?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            stock.nameArabic?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PanelNote(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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

private val SearchFieldHeight = 40.dp

/** Enough for the widest ticker the catalog holds, which is four characters and a digit. */
private val TickerColumn = 54.dp

/**
 * Where the panel stops and starts scrolling instead.
 *
 * Short of the screen on purpose: what is behind it is the page the reader was already on, and a
 * panel covering all of it reads as having navigated somewhere.
 */
private val PanelMaxHeight = 320.dp
