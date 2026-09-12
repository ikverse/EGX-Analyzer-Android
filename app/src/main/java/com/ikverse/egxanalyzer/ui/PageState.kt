package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.time.LocalDate

/**
 * Where each tab was left: what it had open, what it was filtered to, how it was sorted.
 *
 * **Held outside the composition on purpose, and it is the second attempt at this.** The shell draws
 * a rail beside the page on a wide screen and a pill over it on a narrow one, and those are two
 * different call sites in `EgxAnalyzerApp` - as are the `AnimatedContent` and the `HorizontalPager`
 * inside them. Folding the phone flips between them, so Compose disposes one subtree whole and
 * composes the other from nothing, and everything a `remember` was holding goes with it. A report
 * opened on the cover screen was gone by the time the inner panel drew it, and the reader landed
 * back on the list of runs.
 *
 * `rememberSaveable` under a `SaveableStateHolder` above the branch was tried, in 2.1.1, and it
 * cannot work here: the two branches swap inside **one frame**, so the arriving page composes and
 * reads the holder before the leaving page's `onDispose` has written anything into it - and on the
 * way back it reads what the previous fold left there, which quietly replaces anything changed in
 * between. That holder is built for navigation, where the disposal and the next composition are
 * separate frames. `movableContentOf` is the tool for a same-frame move, but it cannot reach across
 * `HorizontalPager`'s lazy subcomposition, which is exactly the direction that matters.
 *
 * So none of this lives in the composition at all. It hangs off the application-scoped `AppState`,
 * where nothing about how the page is drawn can reach it - the same place `destination` lives,
 * which is why the tab already survived a fold when none of the rest of the page did.
 *
 * **Still session-only**, in the sense every screen here always meant: it dies with the process, so
 * a launch from cold opens on an unfiltered list with nothing expanded. Nothing here reaches the
 * disk. `AppPreferences.portfolioOrder` remains the one ordering that is deliberately persisted,
 * because an order hides nothing where a filter does.
 *
 * Fields are `MutableState` rather than delegated properties so a screen can keep reading and
 * writing a plain local name - `var openRun by appState.pages.openResultId` - which is Compose's own
 * `getValue`/`setValue` and not reflection.
 */
/**
 * The header's stock box on one page: what it narrows the page to, and what the reader is doing to
 * it right now.
 *
 * **All four fields live out here rather than in the header's own `remember`, and that is the whole
 * of the fix of 2026-09-11.** `opened` and `typed` were held in the composition. Anything that
 * rebuilt the header took them with it, so the box vanished back to the page's title between the
 * reader pressing a key and the letter landing - "I can't write anything in the box, the box
 * disappears". The picker survived the same treatment for the day before that only by accident: the
 * text was then kept in [picked], which has always lived out here, so a rebuilt header came back
 * open with the text still in it and nobody could see the fault. See [PageState] itself for why
 * nothing about a page may be held by the composition.
 */
class StockBox {

    /**
     * The ticker this page is narrowed to, or blank for the whole page.
     *
     * Written only by a **pick** from the catalog, never by what was typed - see `TickerPicker`.
     */
    val picked: MutableState<String> = mutableStateOf("")

    /** Whether the field is showing, as against the page's title or the pick it closed onto. */
    val opened: MutableState<Boolean> = mutableStateOf(false)

    /** What is in the field. It narrows the listings on offer and never the page. */
    val typed: MutableState<String> = mutableStateOf("")

    /**
     * Whether the catalog is dropped under the field.
     *
     * Separate from [opened] on purpose: a press past the list has to be able to put the **list**
     * away without taking the box with it, or a stray one throws away a half-typed query.
     */
    val listing: MutableState<Boolean> = mutableStateOf(false)

    /** Back to the page's title, filtering nothing. The X, back and Clear filters all mean this. */
    fun clear() {
        picked.value = ""
        opened.value = false
        typed.value = ""
        listing.value = false
    }
}

class PageState {

    // ── Shared by Portfolio and Insights ─────────────────────────────────────────────────────

    /**
     * Whether the "what happened" card is open, for both tabs at once.
     *
     * One entry rather than one per tab, because it is one card: it reports one session's events
     * and draws the same tiles on both screens, and folding it away on the Portfolio only to find
     * it open again on Insights would read as two cards that happen to agree. Starts **open** -
     * every other card in this app starts folded, and this is the one whose whole purpose is to
     * have been read before anyone went looking for it.
     *
     * Session-only like everything else here, which is what makes "expanded by default" true of
     * every launch rather than only of the first one.
     */
    val todayExpanded: MutableState<Boolean> = mutableStateOf(true)

    /**
     * How far back the stock sheet's chart is drawn, and whether it carries the levels.
     *
     * Here rather than in the sheet for the reason everything else here is: the sheet is composed
     * inside whichever shell is drawing, so folding the phone would put a reader who had just
     * pressed 6M back on a month of line. It also outlives the sheet itself, so opening a second
     * stock keeps the range the reader chose rather than making the choice once per ticker.
     *
     * Session-only like the rest of this class, so a launch from cold opens on a month with the
     * levels showing - which is what those defaults are for.
     */
    internal val stockChartRange: MutableState<ChartRange> = mutableStateOf(ChartRange.Default)

    internal val stockChartLevels: MutableState<Boolean> = mutableStateOf(true)

    // ── Results ──────────────────────────────────────────────────────────────────────────────

    /** The run whose table is open, which needs the whole row and so cannot be held by a card. */
    val openResultId: MutableState<Long?> = mutableStateOf(null)

    /** Runs whose written report is showing, by run id: re-sorting must not move which card it is. */
    val openReportMarkdown: MutableState<Set<Long>> = mutableStateOf(emptySet())

    val resultsChannels: MutableState<Set<String>> = mutableStateOf(emptySet())
    val resultsDate: MutableState<String?> = mutableStateOf(null)
    /** The header's stock box for this page: the pick, and what the reader is doing to it. */
    val resultsStockBox: StockBox = StockBox()

    /** What Results is narrowed to. The box that writes it is [resultsStockBox]. */
    val resultsStock: MutableState<String> get() = resultsStockBox.picked
    val resultsOrder: MutableState<RunOrder> = mutableStateOf(RunOrder.RUN_NEWEST)
    val resultsFiltersOpen: MutableState<Boolean> = mutableStateOf(false)

    // ── Insights ─────────────────────────────────────────────────────────────────────────────

    /** The session card that is open, by `ScoredSession.key()`. */
    val openInsightsSession: MutableState<String?> = mutableStateOf(null)

    val insightsChannels: MutableState<Set<String>> = mutableStateOf(emptySet())
    val insightsOutcomes: MutableState<Set<String>> = mutableStateOf(emptySet())
    /** The header's stock box for this page: the pick, and what the reader is doing to it. */
    val insightsStockBox: StockBox = StockBox()

    /** What Insights is narrowed to. The box that writes it is [insightsStockBox]. */
    val insightsStock: MutableState<String> get() = insightsStockBox.picked
    val insightsFiltersOpen: MutableState<Boolean> = mutableStateOf(false)

    // ── Portfolio ────────────────────────────────────────────────────────────────────────────

    /** Which session cards are open, by session date rather than by position in the list. */
    val openPortfolioGroups: MutableState<Set<LocalDate>> = mutableStateOf(emptySet())

    val portfolioDate: MutableState<String?> = mutableStateOf(null)
    /** The header's stock box for this page: the pick, and what the reader is doing to it. */
    val portfolioStockBox: StockBox = StockBox()

    /** What the Portfolio is narrowed to. The box that writes it is [portfolioStockBox]. */
    val portfolioStock: MutableState<String> get() = portfolioStockBox.picked
    val portfolioFiltersOpen: MutableState<Boolean> = mutableStateOf(false)

    // ── Analyze ──────────────────────────────────────────────────────────────────────────────

    /**
     * Whether the reader has waved away the setup card on Analyze.
     *
     * Out here rather than in a `remember` inside the screen, like everything else in this class and
     * for the reason the stock box records against itself: a card that came back every time the
     * phone was folded would be a card that cannot be dismissed. Session-only all the same - it is
     * a hint, and a hint that stays hidden across launches is one nobody can find again.
     */
    val analyzeSetupDismissed: MutableState<Boolean> = mutableStateOf(false)

    // ── What is narrowing a tab ──────────────────────────────────────────────────────────────

    /**
     * The header's stock box for this page, or null for a page that has no list to narrow.
     *
     * Read by [Screen], which draws that box in the page header rather than on the filter shelf -
     * one control per page, in one place, wherever the page keeps its state. Analyze and Settings
     * answer null and so get no search icon at all: an icon that opened a box narrowing nothing
     * would be a control the page cannot honour.
     *
     * The same shape [filtersActive] has and for the same reason - the question is asked from
     * outside the screen that owns the answer, so the answer lives here. What the box itself keeps,
     * and why every part of it is out here rather than in the header, is [StockBox].
     */
    fun stockBox(destination: AppDestination): StockBox? = when (destination) {
        AppDestination.RESULTS -> resultsStockBox
        AppDestination.INSIGHTS -> insightsStockBox
        AppDestination.PORTFOLIO -> portfolioStockBox
        AppDestination.ANALYZE, AppDestination.SETTINGS -> null
    }

    /**
     * Whether [destination]'s filter sheet is open, or null for a page that has no filters.
     *
     * The same shape [stockBox] has, and it is read from the same place: the header draws the
     * icon that opens the sheet, and the screen underneath draws what is inside it. Neither can
     * hold the flag - the header does not know what a page filters by, and the screen is composed
     * below the icon that opens it - so it lives out here with the rest of the page's own state.
     *
     * Held here rather than remembered in the sheet for this class's founding reason as well: a
     * fold disposes the screen whole, and a sheet that was open would close itself on the way.
     */
    fun filtersOpen(destination: AppDestination): MutableState<Boolean>? = when (destination) {
        AppDestination.RESULTS -> resultsFiltersOpen
        AppDestination.INSIGHTS -> insightsFiltersOpen
        AppDestination.PORTFOLIO -> portfolioFiltersOpen
        AppDestination.ANALYZE, AppDestination.SETTINGS -> null
    }

    /**
     * Whether one of the filters that lives **in the sheet** is narrowing [destination].
     *
     * What lights the dot on the header's filter icon, and deliberately not [filtersActive]: the
     * stock box is its own indicator - it stays on screen while it holds text - so a dot lit by it
     * would report something the reader is already looking at, and would go on reporting it after
     * they had cleared everything else. It is the distinction the old filter shelf drew between
     * its `active` and its `folded`, asked here because the icon is drawn above the screen that
     * knows the answer.
     */
    fun filtersInSheet(destination: AppDestination): Boolean = when (destination) {
        AppDestination.RESULTS ->
            resultsChannels.value.isNotEmpty() || resultsDate.value != null
        AppDestination.INSIGHTS ->
            insightsChannels.value.isNotEmpty() || insightsOutcomes.value.isNotEmpty()
        AppDestination.PORTFOLIO -> portfolioDate.value != null
        AppDestination.ANALYZE, AppDestination.SETTINGS -> false
    }

    /**
     * Whether anything on [destination] is hiding rows from the reader.
     *
     * Here rather than in each screen because two things now ask it: the sheet, to offer Clear
     * filters, and the shell, to decide what a back press means. Three screens each stating their
     * own version of the predicate is three that agree until one gains a filter and the other
     * reader of it silently stops seeing it.
     *
     * **Sorts are not filters and are left out**, which is the rule the screens already followed:
     * [resultsOrder] changes what is read first and hides nothing, so a back press has no business
     * resetting it and the sheet's dot has no business reporting it.
     */
    fun filtersActive(destination: AppDestination): Boolean = when (destination) {
        AppDestination.RESULTS ->
            resultsChannels.value.isNotEmpty() ||
                resultsDate.value != null ||
                resultsStock.value.isNotBlank()
        AppDestination.INSIGHTS ->
            insightsChannels.value.isNotEmpty() ||
                insightsOutcomes.value.isNotEmpty() ||
                insightsStock.value.isNotBlank()
        AppDestination.PORTFOLIO ->
            portfolioDate.value != null || portfolioStock.value.isNotBlank()
        AppDestination.ANALYZE, AppDestination.SETTINGS -> false
    }

    /** Opens [destination] back up to everything it holds. */
    fun clearFilters(destination: AppDestination) {
        when (destination) {
            AppDestination.RESULTS -> {
                resultsChannels.value = emptySet()
                resultsDate.value = null
                resultsStockBox.clear()
            }
            AppDestination.INSIGHTS -> {
                insightsChannels.value = emptySet()
                insightsOutcomes.value = emptySet()
                insightsStockBox.clear()
            }
            AppDestination.PORTFOLIO -> {
                portfolioDate.value = null
                portfolioStockBox.clear()
            }
            AppDestination.ANALYZE, AppDestination.SETTINGS -> Unit
        }
    }
}
