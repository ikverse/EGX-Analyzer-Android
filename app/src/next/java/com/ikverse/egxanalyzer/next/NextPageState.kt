package com.ikverse.egxanalyzer.next

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ikverse.egxanalyzer.model.WordingRule
import java.time.LocalDate

/**
 * What a screen would lose if it held its own state.
 *
 * Remembered above the destination switch rather than inside a screen, because leaving a tab
 * disposes it: a card the reader opened, a filter they set, and a sheet half filled in would all be
 * gone by the time they came back from checking something on another tab. The shipping app learned
 * this the hard way on the fold, where the two navigation arrangements are two call sites and every
 * `remember` under them died on every unfold.
 *
 * Only what a reader would notice losing goes here. A dropdown that is open, a press in flight, a
 * scroll position - those are transient chrome and stay in an ordinary `remember`.
 */
internal class NextPageState {

    // ---- Portfolio ---------------------------------------------------------------------------

    /** Which session cards are open. Every card starts folded, including one holding a live trade. */
    val openSessions = mutableStateListOf<LocalDate>()

    /**
     * The session being looked at alone, or null for the whole record.
     *
     * Deliberately not stored across restarts: an order hides nothing, but a date filter that came
     * back weeks later showing one session is how someone concludes their trades have gone missing.
     */
    var portfolioDate by mutableStateOf<LocalDate?>(null)

    /** The trade a sheet is currently asking about, and what it is asking. */
    var tradeSheet by mutableStateOf<TradeSheet?>(null)

    fun toggleSession(date: LocalDate) {
        if (!openSessions.remove(date)) openSessions.add(date)
    }

    fun openSession(date: LocalDate) {
        if (date !in openSessions) openSessions.add(date)
    }

    // ---- Insights ----------------------------------------------------------------------------

    /**
     * Which session cards are open on Insights.
     *
     * Its own list rather than sharing the Portfolio's: the two screens file the same dates for
     * different reasons, and opening a session's trades is not a request to open its calls.
     */
    val openCallSessions = mutableStateListOf<LocalDate>()

    fun toggleCallSession(date: LocalDate) {
        if (!openCallSessions.remove(date)) openCallSessions.add(date)
    }

    fun openCallSession(date: LocalDate) {
        if (date !in openCallSessions) openCallSessions.add(date)
    }

    /** The status tier the ranking is narrowed to, or null for everything. */
    var insightsFilter by mutableStateOf<String?>(null)

    /** The one channel being read, or null for every source. */
    var insightsChannel by mutableStateOf<String?>(null)

    /** The call a buy is being recorded against, named by the id one call and one holding share. */
    var buyingCall by mutableStateOf<String?>(null)

    // ---- Results -----------------------------------------------------------------------------

    /** Which saved runs are open. */
    val openRuns = mutableStateListOf<Long>()

    /** The runs whose body is showing where the rows came from rather than the rows. */
    val runsShowingProvenance = mutableStateListOf<Long>()

    /** A ticker or a name, matched against both scripts. Session-only, like every other filter. */
    var resultsStock by mutableStateOf("")

    /** Which of [NextResultsSort] the list is in. */
    var resultsSort by mutableStateOf(0)

    /** The run whose menu is open, if any. Transient, but it outlives a recomposition. */
    var openMenu by mutableStateOf<Long?>(null)

    /** The run being asked about before it is deleted. */
    var deletingRun by mutableStateOf<Long?>(null)

    fun toggleRun(id: Long) {
        if (!openRuns.remove(id)) openRuns.add(id)
    }

    fun toggleProvenance(id: Long) {
        if (!runsShowingProvenance.remove(id)) runsShowingProvenance.add(id)
    }

    // ---- Settings ----------------------------------------------------------------------------

    /** Which of the ordinary sections are open. The two services and the fence never fold. */
    val openSections = mutableStateListOf<String>()

    /** The wording rule being corrected, if any. */
    var ruleSheet by mutableStateOf<WordingRule?>(null)

    /** Whether the sheet is writing a new rule rather than correcting one. */
    var addingRule by mutableStateOf(false)

    // ---- Analyze -----------------------------------------------------------------------------

    /** Whether the message preview is showing, on a width too narrow to keep it permanently. */
    var previewOpen by mutableStateOf(false)

    /** Whether the session calendar is open. */
    var pickingSession by mutableStateOf(false)
}

/** What a sheet is asking about one trade. */
internal sealed interface TradeSheet {
    val positionId: String

    /** Record the sale: a price, and the day it happened. */
    data class Sell(override val positionId: String) : TradeSheet

    /** Change what was recorded, which recomputes this trade's figures and nothing else. */
    data class Edit(override val positionId: String) : TradeSheet

    /** Take the trade off the record entirely. */
    data class Remove(override val positionId: String) : TradeSheet
}
