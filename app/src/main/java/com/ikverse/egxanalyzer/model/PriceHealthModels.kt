package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/**
 * What is wrong with the feed behind one stock.
 *
 * Three different problems that all end the same way - the app goes quiet about a stock rather than
 * wrong about it - which is exactly why they need saying out loud. A rate that silently rests on
 * fewer calls than the reader thinks is the one failure this whole file exists to make visible.
 */
enum class FeedFault(val label: String, val detail: String) {
    /** No stored history at all. A refresh is the missing step. */
    UNPRICED(
        "No prices",
        "Nothing has ever been stored for this stock, so no call on it can be judged.",
    ),

    /**
     * The series answers every request while its newest session stays put.
     *
     * Harder to see than an unpriced stock and worse, because everything looks like it is working.
     * This has happened here - the ISIN symbol migration - and nothing noticed.
     */
    STALE(
        "Feed has stopped",
        "The series still answers, and its newest session has not moved. That is what a symbol " +
            "that has been retired looks like from here.",
    ),

    /**
     * A split or a bonus issue inside the stored history.
     *
     * Recorded rather than corrected: guessing a ratio and rescaling a year of prices would be the
     * app inventing history. The calls that straddle it are unjudgeable and stay that way.
     */
    SCALE_CHANGED(
        "Prices changed scale",
        "A split or bonus issue sits inside the stored history. Levels printed before it are in " +
            "the old money, so calls across it cannot be judged.",
    ),
}

/**
 * One stock the record names, and what the feed is doing about it.
 *
 * [callsHeld] is the figure this type exists for. A count of stale symbols is trivia; how many
 * calls that staleness is keeping out of every rate on the page is the thing a reader would want to
 * know before believing a hit rate.
 */
data class StockHealth(
    val ticker: String,
    /**
     * Every fault this stock carries, not just the worst.
     *
     * A stock can be both frozen and split, and a list showing one row per stock that named only
     * the more severe would hide the other - on the stocks with most wrong with them.
     */
    val faults: Set<FeedFault>,
    /** The newest session stored, absent for a stock with no history at all. */
    val newestSession: LocalDate?,
    /** Calendar days since that session, absent for the same reason. */
    val ageDays: Long?,
    /** Calls on this stock that no rate on the page is counting because of the above. */
    val callsHeld: Int,
)

/**
 * The feed's state across every stock the record names.
 *
 * Deliberately **not** narrowed by anything chosen on screen, for the same reason the Overdue card
 * is not: a channel filter is a view of the record, never a claim about which prices are broken.
 */
data class PriceHealthReport(
    /** Worst first, measured by how many calls each is holding. Empty when nothing is wrong. */
    val faults: List<StockHealth> = emptyList(),
    /** Distinct stocks the record names, so the screen can say four *of what*. */
    val stocksNamed: Int = 0,
) {
    /** Calls held out of every rate on the page, across every stock below. */
    val callsHeld: Int get() = faults.sumOf(StockHealth::callsHeld)

    val clean: Boolean get() = faults.isEmpty()
}
