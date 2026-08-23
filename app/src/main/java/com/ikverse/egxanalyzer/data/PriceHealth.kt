package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.ScoredCall
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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

/**
 * Which stocks the price feed has gone quiet about, and what that is costing the record.
 *
 * Every input here is already on disk and already read on a recompute - the newest session per
 * stock, the recorded scale breaks, and the scored calls. Nothing is fetched and nothing is stored:
 * this is derived on every recompute exactly as the outcomes are, which is what makes it right
 * after a restart rather than a cache of what some refresh once reported.
 *
 * It replaces nothing. The refresh still says "3 unpriced · 1 stale" as it finishes; that reports a
 * refresh, and this reports a state. The toast was the only place either had ever been said, and it
 * is gone from the screen a second later.
 *
 * No Android in it, like [PriceSanity] and for the same reason: what counts as a broken feed is a
 * rule about prices, and a rule about prices should not need a device to check.
 */
object PriceHealth {

    fun assess(
        calls: List<ScoredCall>,
        latestPrices: Map<String, LatestPrice>,
        breaks: Map<String, Set<LocalDate>>,
        today: LocalDate,
    ): PriceHealthReport {
        // A re-posting is the same bet as the call it repeats and is outside every rate already, so
        // counting it here would overstate what a broken feed is costing - the same rule the rates
        // themselves follow.
        val counted = calls.filter { it.repeatOf == null }
        // Only the stocks the record actually names. The catalog holds every Cairo listing, and
        // reporting a frozen feed for a stock nobody has ever been recommended is a page of noise
        // hiding the four rows that matter.
        val byTicker = counted.groupBy(ScoredCall::ticker)

        val rows = byTicker.mapNotNull { (ticker, onStock) ->
            val latest = latestPrices[ticker]
            val newest = latest?.session?.date
            val age = newest?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) }
            val breakDates = breaks[ticker].orEmpty()

            val faults = buildSet {
                if (latest == null) {
                    add(FeedFault.UNPRICED)
                } else if (age != null && age > PriceSanity.MAX_SESSION_AGE_DAYS) {
                    // Only ever asked of a stock that has a series at all: "the newest session is
                    // old" says nothing about one that has no sessions, and reporting both faults
                    // on it would count the same broken stock twice.
                    add(FeedFault.STALE)
                }
                if (breakDates.isNotEmpty()) add(FeedFault.SCALE_CHANGED)
            }
            if (faults.isEmpty()) return@mapNotNull null

            StockHealth(
                ticker = ticker,
                faults = faults,
                newestSession = newest,
                ageDays = age,
                callsHeld = onStock.count { it.heldBy(faults) },
            )
        }

        return PriceHealthReport(
            // Most expensive first, then the ticker so the list cannot reshuffle between two reads
            // that measured the same thing. The same shape as the Overdue roster: what is costing
            // the most leads, and the tie-break is a name rather than an accident of iteration.
            faults = rows.sortedWith(
                compareByDescending(StockHealth::callsHeld).thenBy(StockHealth::ticker),
            ),
            stocksNamed = byTicker.size,
        )
    }

    /**
     * Whether this call is one the fault is actually holding.
     *
     * Deliberately narrow. A call whose entry never traded is unjudged because of what the market
     * did, not because of the feed, and sweeping every unjudged call into the count would let a
     * single stale symbol appear to be suppressing a source's whole record. The claim on screen is
     * "these stocks are holding N calls out of every rate", and it has to be true.
     */
    private fun ScoredCall.heldBy(faults: Set<FeedFault>): Boolean = when (outcome) {
        Outcome.UNPRICED -> FeedFault.UNPRICED in faults || FeedFault.STALE in faults
        Outcome.PRICE_BREAK -> FeedFault.SCALE_CHANGED in faults
        // Still waiting on sessions that a frozen feed is never going to deliver. Only under STALE:
        // an open call on a healthy feed is simply a call in progress, which is not a fault.
        Outcome.OPEN -> FeedFault.STALE in faults
        else -> false
    }
}
