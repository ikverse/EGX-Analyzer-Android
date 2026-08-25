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
internal class PageState {

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

    // ── Results ──────────────────────────────────────────────────────────────────────────────

    /** The run whose table is open, which needs the whole row and so cannot be held by a card. */
    val openResultId: MutableState<Long?> = mutableStateOf(null)

    /** Runs whose written report is showing, by run id: re-sorting must not move which card it is. */
    val openReportMarkdown: MutableState<Set<Long>> = mutableStateOf(emptySet())

    val resultsChannels: MutableState<Set<String>> = mutableStateOf(emptySet())
    val resultsDate: MutableState<String?> = mutableStateOf(null)
    val resultsStock: MutableState<String> = mutableStateOf("")
    val resultsOrder: MutableState<RunOrder> = mutableStateOf(RunOrder.RUN_NEWEST)

    // ── Insights ─────────────────────────────────────────────────────────────────────────────

    /** The session card that is open, by `ScoredSession.key()`. */
    val openInsightsSession: MutableState<String?> = mutableStateOf(null)

    val insightsChannels: MutableState<Set<String>> = mutableStateOf(emptySet())
    val insightsOutcomes: MutableState<Set<String>> = mutableStateOf(emptySet())
    val insightsStock: MutableState<String> = mutableStateOf("")

    // ── Portfolio ────────────────────────────────────────────────────────────────────────────

    /** Which session cards are open, by session date rather than by position in the list. */
    val openPortfolioGroups: MutableState<Set<LocalDate>> = mutableStateOf(emptySet())

    val portfolioDate: MutableState<String?> = mutableStateOf(null)
    val portfolioStock: MutableState<String> = mutableStateOf("")
}
