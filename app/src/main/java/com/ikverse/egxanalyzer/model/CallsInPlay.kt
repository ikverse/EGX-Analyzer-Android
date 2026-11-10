package com.ikverse.egxanalyzer.model

import kotlin.math.abs

/**
 * The calls the market has not finished with, for the Insights tiles and the price strip.
 *
 * A call is **in play** while its window is still running and nothing has settled it: still waiting
 * for its entry, entered and open, or past target 1 with target 2 still to come. A re-post of an
 * earlier call is the same call and is counted once, as the ranking counts it.
 *
 * Nothing here is fetched or stored: it is the scoring Insights already does, read against the
 * newest close each stock has.
 */
data class CallsInPlay(
    /** Every call still in play. */
    val calls: List<ScoredCall>,
    /** How many of them closed inside their own entry zone on the newest session. */
    val inEntryZone: Int,
    /** How many have reached target 1 and are still running for target 2. */
    val pastTarget1: Int,
    /**
     * Calls in play whose stock closed inside the entry zone and that the reader does not hold,
     * nearest to the middle of the zone first: a buy the record says is on offer right now.
     */
    val untaken: List<ScoredCall>,
) {
    val count: Int get() = calls.size
}

private val LiveOutcomes = setOf(Outcome.ENTRY_NOT_REACHED, Outcome.OPEN, Outcome.PARTIAL_HIT)

/** Whether [call] is still in play: see [CallsInPlay]. */
fun ScoredCall.inPlay(): Boolean = !windowComplete && repeatOf == null && outcome in LiveOutcomes

/** Whether [price] sits inside this call's entry zone, a single printed price counting as a zone of one. */
fun ScoredCall.inEntryZone(price: Double): Boolean {
    val low = entryLow ?: entryHigh ?: return false
    val high = entryHigh ?: low
    return price >= minOf(low, high) && price <= maxOf(low, high)
}

/**
 * @param latestClose the newest stored close of a ticker, by its normalized code.
 * @param held whether the reader already holds a trade on that call.
 */
fun callsInPlay(
    report: PerformanceReport,
    latestClose: (String) -> Double? = { ticker -> report.latestPrices[ticker]?.session?.close },
    held: (ScoredCall) -> Boolean = { false },
): CallsInPlay {
    val live = report.sessions.flatMap(ScoredSession::calls).filter { it.inPlay() }
    val inZone = live.filter { call ->
        latestClose(Scoring.normalizeTicker(call.ticker))?.let(call::inEntryZone) == true
    }
    val untaken = inZone
        .filter { it.outcome != Outcome.PARTIAL_HIT && !held(it) }
        .sortedBy { call ->
            val close = latestClose(Scoring.normalizeTicker(call.ticker)) ?: return@sortedBy Double.MAX_VALUE
            val mid = listOfNotNull(call.entryLow, call.entryHigh).average()
            if (mid > 0.0) abs(close - mid) / mid else Double.MAX_VALUE
        }
    return CallsInPlay(
        calls = live,
        inEntryZone = inZone.size,
        pastTarget1 = live.count { it.outcome == Outcome.PARTIAL_HIT },
        untaken = untaken,
    )
}
