package com.ikverse.egxanalyzer.model

import kotlin.math.max
import kotlin.math.min

/**
 * Something about a call's levels that a channel is unlikely to have meant.
 *
 * Every one of these is a shape a **misread** takes, not a shape a bad call takes. A source can be
 * wrong about a stock all day and still print four numbers that hang together; a vision model
 * reading a screenshot can put the decimal point in the wrong place, swap two rows of a table, or
 * pick up the previous card's stop. Those are the cases here.
 */
enum class CallFault(val label: String, val detail: String) {
    ENTRY_BAND_INVERTED(
        "Entry band is backwards",
        "The low of the buy zone is above its high, which is a pair of numbers read the wrong " +
            "way round rather than a zone anyone printed.",
    ),
    STOP_ABOVE_ENTRY(
        "Stop sits above the buy zone",
        "A stop at or above the entry would be broken the moment the call was taken. Usually the " +
            "stop of the card above or below this one.",
    ),
    TARGET_BELOW_ENTRY(
        "First target sits below the buy zone",
        "A target at or under the entry is not a target. Usually a stop read into the target row.",
    ),
    TARGETS_OUT_OF_ORDER(
        "Second target is below the first",
        "The two targets have been read in the wrong order, so the call's own best case is " +
            "beneath the level before it.",
    ),
    LEVELS_OFF_THE_CHART(
        "Levels are nowhere near the price",
        "The buy zone is a long way from where the stock actually traded that session, which is " +
            "what a misplaced decimal point looks like.",
    ),
}

/**
 * Whether a call's levels can be believed, checked against the session it was made for.
 *
 * `PriceSanity` guards the price feed and nothing has ever guarded the **extraction**, which is the
 * other half of every judgement the app makes. A misread that puts the buy zone somewhere the stock
 * has never traded mostly neutralises itself - the entry never trades, and the call is excluded
 * from every rate - but a stop read into the target row, or two targets swapped, scores perfectly
 * plausibly and counts against whichever channel happened to be misread.
 *
 * **It marks and never excludes.** Every figure on the page stays exactly what it was; the card
 * gains a chip saying what looks wrong. Excluding a suspect call would move published rates on the
 * strength of a heuristic, and a heuristic that is 95% right would then be silently rewriting a
 * channel's record on the other 5%. Reporting is recoverable and reversible; a rate that quietly
 * dropped calls is neither.
 *
 * Pure and with no Android in it, like [Scoring] and `PriceSanity`, and for the same reason: what
 * counts as an impossible set of levels is a rule about trading, and it should be checkable without
 * a device. Derived on every recompute and never stored - a fault is a reading of the levels, and
 * the levels are already on disk.
 */
object CallSanity {

    /**
     * How far the buy zone may sit from where the stock actually traded before it reads as a misread.
     *
     * Deliberately loose. A patient call naming a dip well under today's price is ordinary, and a
     * band a third below the session's low is a call worth keeping; a decimal point in the wrong
     * place is out by a factor of ten. A factor of two catches every misplaced decimal and touches
     * nothing a channel would actually print, which is the only place a threshold can safely sit
     * when the cost of a false positive is a caveat on an honest call.
     */
    const val MAX_DISTANCE_FACTOR = 2.0

    /**
     * What looks wrong about these levels, if anything.
     *
     * [session] is the stock's own session for the day the call was made, where the app has one.
     * Without it the structural checks still run - they need no price at all - and only the
     * distance check is skipped, which is the right way round: an unpriced stock must not collect a
     * fault for being unpriced.
     */
    fun faults(
        entryLow: Double?,
        entryHigh: Double?,
        target1: Double?,
        target2: Double?,
        stopLoss: Double?,
        session: DailySession?,
    ): Set<CallFault> = buildSet {
        if (entryLow != null && entryHigh != null && entryLow > entryHigh) {
            add(CallFault.ENTRY_BAND_INVERTED)
        }
        // The middle of the band, which is the base every other figure on a call card is measured
        // from, so a fault is never raised against a number the reader is not looking at.
        val entry = midpoint(entryLow, entryHigh)
        if (entry != null) {
            if (stopLoss != null && stopLoss >= entry) add(CallFault.STOP_ABOVE_ENTRY)
            if (target1 != null && target1 <= entry) add(CallFault.TARGET_BELOW_ENTRY)
        }
        if (target1 != null && target2 != null && target2 < target1) {
            add(CallFault.TARGETS_OUT_OF_ORDER)
        }
        if (entry != null && session != null && farFromTraded(entry, session)) {
            add(CallFault.LEVELS_OFF_THE_CHART)
        }
    }

    private fun midpoint(low: Double?, high: Double?): Double? {
        // The band the way round it was printed. An inverted band already has its own fault, and
        // reading its middle is still the best guess at what the card meant.
        val a = low ?: high ?: return null
        val b = high ?: low ?: return null
        val mid = (a + b) / 2
        return mid.takeIf { it > 0.0 }
    }

    /**
     * Whether the buy zone is further from that session's own prices than a misread could be.
     *
     * Measured against the session's traded range rather than its close: a call printed before the
     * open names a level the day has yet to reach, and a stock that moved 5% that day would collect
     * a fault for a band sitting perfectly inside the range it actually traded.
     */
    private fun farFromTraded(entry: Double, session: DailySession): Boolean {
        val traded = session.traded
        val high = traded.high ?: traded.close ?: return false
        val low = traded.low ?: traded.close ?: return false
        if (high <= 0.0 || low <= 0.0) return false
        return entry > max(high, low) * MAX_DISTANCE_FACTOR ||
            entry < min(high, low) / MAX_DISTANCE_FACTOR
    }
}
