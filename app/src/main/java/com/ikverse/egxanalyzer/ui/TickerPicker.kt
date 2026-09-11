package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ikverse.egxanalyzer.data.EgxCatalog
import com.ikverse.egxanalyzer.data.EgxStock
import com.ikverse.egxanalyzer.model.WordingRule

/**
 * Which stocks to offer somebody typing in the header, and in what order.
 *
 * **The header's box picks a listing rather than filtering on whatever was typed.** Free text let a
 * reader narrow a page to `comi`, to `Commercia`, or to a misremembered spelling that quietly
 * matched nothing, and the page answered each of those with an empty list that looked the same as
 * having no runs at all. A pick can only ever be a ticker the exchange actually lists, so what
 * reaches `PageState.resultsStock` and its two siblings is a code the rest of the app already knows
 * how to name, price and score.
 *
 * **It is still the page's filter, not a lookup.** A lookup that opened a stock sheet in place of
 * filtering was tried in the first version of this header and pulled on 2026-09-09; the sheet is
 * reachable from a row here as a second, trailing target, and only where the app holds a record
 * worth opening. See `CLAUDE.md`, "The stock filter, moved into the header".
 *
 * No Compose in this half on purpose: the ordering is the part with a decision in it, and it is
 * unit-tested where a composable could not be.
 */
internal object TickerPicker {

    /** One offered listing, and whether the page being narrowed holds anything about it. */
    data class Suggestion(val stock: EgxStock, val onPage: Boolean)

    /**
     * The catalog, narrowed to what was typed and ordered for the reader in front of it.
     *
     * The order is three questions, in this order:
     *
     * 1. **Does this page hold it**, which is the whole reason the picker knows what page it is
     *    over. A reader on Results looking for a stock they analysed this morning should not have
     *    to scroll past two hundred listings they have never run to reach it - and picking one the
     *    page has nothing for is still allowed, because "no runs mention this" is a real answer and
     *    a listing missing from the list with nothing saying why is not.
     * 2. **Does its ticker start with what was typed**, so `COMI` leads on `com` rather than
     *    trailing whichever company happens to have those letters in the middle of its name.
     * 3. **Alphabetically by ticker**, which is stable and is the order the catalog already keeps.
     *
     * Matching itself is [StockSearch]'s, aliases included, so `CIB` finds COMI and `المصريه` finds
     * `المصرية` here exactly as it does on the three pages this narrows. An empty query is not a
     * question: it offers the whole catalog with the page's own stocks at the top, which is the
     * useful thing to show somebody who has just pressed the icon.
     */
    fun suggest(
        typed: String,
        onPage: Set<String>,
        catalog: List<EgxStock> = EgxCatalog.entries(),
    ): List<Suggestion> {
        val wanted = StockSearch.query(typed)
        return catalog.asSequence()
            .filter { stock ->
                val names = buildList {
                    add(stock.ticker)
                    add(stock.nameEnglish)
                    stock.nameArabic?.let(::add)
                    addAll(stock.aliases)
                }
                StockSearch.matches(wanted, *names.toTypedArray())
            }
            .map { stock -> Suggestion(stock, stock.ticker in onPage) }
            .sortedWith(
                compareByDescending<Suggestion> { it.onPage }
                    .thenByDescending {
                        wanted.isNotBlank() &&
                            WordingRule.normalize(it.stock.ticker).startsWith(wanted)
                    }
                    .thenBy { it.stock.ticker },
            )
            .toList()
    }

    /**
     * What to call a picked ticker in a sentence.
     *
     * The empty states used to echo the box - "No runs mention COMI" - which was the only wording
     * available while the filter held whatever had been typed. It holds a listing now, so the
     * screens can say what the reader actually picked. Falls back to the code for a seeded entry
     * whose English name is its own ticker, where there is nothing else to say.
     */
    fun name(ticker: String): String {
        val stock = EgxCatalog.find(ticker) ?: return ticker.trim()
        return stock.nameEnglish.takeUnless { it == stock.ticker } ?: stock.ticker
    }
}

/**
 * The list the header's box drops under itself, over the page it is narrowing.
 *
 * **A `Popup`, and it covers the page on purpose.** Drawn as a sibling of the header it would be
 * painted under the page - the header is the first child of `Screen`'s column - and drawn inside
 * the header it would be clipped to a 56dp bar. The popup's content is the window below the
 * field: the list at the top and a scrim under it that takes every press aimed past the list and
 * answers it with [onDismiss]. Without that scrim a tap meant to put the list away would land on
 * whichever card happened to be beneath it.
 *
 * **Its height is measured rather than filled**, which is not tidiness. A popup window is clamped
 * to the display unless it asks not to be, so content taller than the space under the anchor is
 * answered by the *window* being shifted up - and this one would slide the list back over the
 * header it hangs from. [spaceBelow] is what the caller measured, and the content is exactly that
 * tall.
 *
 * **`focusable = false` is what keeps the keyboard up.** A focusable popup takes window focus, the
 * IME closes with it, and the reader is left typing into a field that no longer has the keyboard.
 * It also means the popup never sees the back press - which is correct here, because
 * [PageHeader]'s own `BackHandler` is what answers back while the box is open.
 */
@Composable
internal fun TickerPickerList(
    typed: String,
    /** The tickers the page underneath actually holds. See `pageStocks`. */
    stocksOnPage: () -> Set<String>,
    onPick: (String) -> Unit,
    /** Pressing past the list: puts it away and keeps whatever was already picked. */
    onDismiss: () -> Unit,
    /** Opens the stock sheet, having put the list away first. */
    onOpenStock: (String) -> Unit,
    /** How far under the field's top edge the list hangs, in pixels. */
    anchorOffset: Int,
    /** How much window is left under that point **above the keyboard**, in pixels. */
    spaceBelow: Int,
) {
    if (spaceBelow <= 0) return
    // Read once per opening rather than per keystroke: it is a pass over every saved run, every
    // scored call or every position, and none of those change while somebody is typing.
    val onPage = remember { stocksOnPage() }
    // **The rest of the exchange is held back until something is typed.** Two hundred-odd listings
    // dropped over the page the moment the icon is pressed is a menu to be scrolled rather than
    // read, and the half worth reading - what this page actually holds - was the first few rows of
    // it. An empty box offers those and says where the others are; the exchange's own listing is a
    // search, and a search wants a query.
    val browsing = typed.isBlank()
    val suggestions = remember(typed, onPage) { TickerPicker.suggest(typed, onPage) }
    val held = remember(suggestions) { suggestions.filter(TickerPicker.Suggestion::onPage) }
    val rest = remember(suggestions, browsing) {
        if (browsing) emptyList() else suggestions.filterNot(TickerPicker.Suggestion::onPage)
    }
    val position = remember(anchorOffset) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = IntOffset(0, anchorBounds.top + anchorOffset)
        }
    }
    val height = with(LocalDensity.current) { spaceBelow.toDp() }
    // The list never runs past the window it hangs in, whatever [PickerMaxHeight] says: the popup
    // is only as tall as the space measured for it, so a card taller than that would be cut off at
    // the window's edge rather than scrolled. Space.l is left under it so it ends on a margin.
    val listHeight = minOf(PickerMaxHeight, height - Space.l)
    if (listHeight <= 0.dp) return
    Popup(popupPositionProvider = position, properties = PopupProperties(focusable = false)) {
        Box(Modifier.fillMaxWidth().height(height)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha))
                    // No ripple and no indication: it is the page behind the list, not a control.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Surface(
                Modifier
                    // The header's own margin, so the list stands under the field rather than
                    // under the window - the popup is placed at the window's start edge.
                    .padding(horizontal = Space.l)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = PickerElevation,
            ) {
                if (held.isEmpty() && rest.isEmpty()) {
                    Text(
                        // A page with nothing on it is not the same answer as a query nothing
                        // answers to, and on an empty box the reader has not asked anything yet.
                        if (browsing) "Type to search the exchange" else "No listing answers to that",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Space.l),
                    )
                    return@Surface
                }
                LazyColumn(Modifier.heightIn(max = listHeight).padding(vertical = Space.s)) {
                    if (held.isNotEmpty()) {
                        item { PickerGroup("On this page") }
                        items(held, key = { it.stock.ticker }) { suggestion ->
                            SuggestionRow(suggestion, onPick, onOpenStock)
                        }
                    }
                    if (rest.isNotEmpty()) {
                        item { PickerGroup(if (held.isEmpty()) "All stocks" else "All other stocks") }
                        items(rest, key = { it.stock.ticker }) { suggestion ->
                            SuggestionRow(suggestion, onPick, onOpenStock)
                        }
                    }
                    // Where the other listings went, said at the foot of the short list rather than
                    // left to be guessed - a picker that offers six stocks with no word about the
                    // exchange's other two hundred reads as a picker that has never heard of them.
                    if (browsing) {
                        item { PickerNote("Type to search all ${EgxCatalog.size()} listings") }
                    }
                }
            }
        }
    }
}

/** Where the rest of the exchange is, under a list that is only showing this page's own stocks. */
@Composable
private fun PickerNote(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s),
    )
}

@Composable
private fun PickerGroup(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s),
    )
}

/**
 * One listing on offer: press the row to narrow the page to it.
 *
 * **The arrow is a second target and it sits at the end of the row**, which is the rule the app
 * arrived at on 2026-09-08 after the opposite was tried: a target at the leading edge is one a
 * thumb reaching for the row hits on its way, and a second affordance that steals the first press
 * is not a second affordance. The logo is at that leading edge, so the logo presses the row.
 *
 * **It is absent rather than disabled on a stock the app holds no record of.** `StockSheet` reads
 * the scored calls, the trades and the price history; on a listing nobody has ever analysed all
 * three are empty and the sheet opens on a shell. Absent is the same answer the chart's Levels chip
 * gives on a stock nobody has called.
 */
@Composable
private fun SuggestionRow(
    suggestion: TickerPicker.Suggestion,
    onPick: (String) -> Unit,
    onOpenStock: (String) -> Unit,
) {
    val stock = suggestion.stock
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPick(stock.ticker) }
            .padding(start = Space.l, end = Space.s, top = Space.s, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StockLogo(stock.ticker, LogoSize.Row, Modifier.padding(end = Space.m))
        Column(Modifier.weight(1f)) {
            // Arabic first where the catalog holds it, as the cards print it: it is the line the
            // channels themselves write, and the one a reader is most likely to be hunting by.
            Text(
                stock.nameArabic ?: stock.nameEnglish,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (stock.nameArabic == null || stock.nameEnglish == stock.ticker) {
                    stock.ticker
                } else {
                    "${stock.ticker} · ${stock.nameEnglish}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (suggestion.onPage) {
            Box(
                Modifier
                    .size(SheetTarget)
                    .clip(CircleShape)
                    .clickable { onOpenStock(stock.ticker) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Open ${stock.ticker}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Inline),
                )
            }
        }
    }
}

/**
 * How far the list may run before it scrolls.
 *
 * Short enough that the page it is narrowing is still visible underneath on a cover screen - the
 * reader is choosing what to do to that page, and a list that filled the window would read as
 * having left it. It is now a ceiling rather than the usual height: what the list is actually drawn
 * as tall as is the window above the keyboard, measured in [SearchField].
 */
private val PickerMaxHeight = 300.dp

/** It floats over the page rather than sitting on it, so it carries a shadow the cards do not. */
private val PickerElevation: Dp = 6.dp

/** A press target for the arrow, not the size of it - the same 40dp the header's own glyphs use. */
private val SheetTarget = 40.dp

/** Enough that the page reads as out of reach, which while the list is open it is. */
private const val ScrimAlpha = 0.32f
