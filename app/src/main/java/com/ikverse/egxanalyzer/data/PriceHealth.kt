package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.FeedFault
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PriceHealthReport
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.StockHealth
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One row of the log [LocalDataStore.saveFeedHealth] keeps, read back by [LocalDataStore.feedHealthChecks].
 *
 * A [PriceHealthReport] that happened to be worth recording, with the moment it was checked
 * attached - the one thing the live report itself has no reason to carry, because it is always
 * "now". Never drawn by a screen; see [LocalDataStore.feedHealthChecks] for why it is stored at
 * all.
 */
data class FeedHealthCheck(
    val checkedAt: java.time.Instant,
    val stocksNamed: Int,
    val callsHeld: Int,
    val faults: List<StockHealth>,
)

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
