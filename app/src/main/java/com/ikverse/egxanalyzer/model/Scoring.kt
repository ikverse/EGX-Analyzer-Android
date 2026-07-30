package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/**
 * Scores saved recommendations against what the market actually did.
 *
 * Outcomes are decided by comparing target and stop levels to the session high and low, which is
 * arithmetic rather than judgement, so no model is involved. Deliberately a straight mirror of the
 * desktop engine: the two apps score the same analyses, and a figure that differs between them is
 * worse than no figure at all.
 */
object Scoring {
    /**
     * The window is expressed in trading sessions rather than calendar days: a stock does not move
     * at the weekend, so counting those would shorten every window by two days in five.
     */
    const val MIN_WINDOW_SESSIONS = 1
    const val MAX_WINDOW_SESSIONS = 30
    const val DEFAULT_WINDOW_SESSIONS = 10

    fun clampWindow(sessions: Int): Int =
        sessions.coerceIn(MIN_WINDOW_SESSIONS, MAX_WINDOW_SESSIONS)

    /**
     * One spelling per stock.
     *
     * Sources quote the same company as both AMOC and AMOC.CA, and treating those as two stocks
     * splits a channel's record in half and prices only one of them.
     */
    fun normalizeTicker(value: String?): String =
        value.orEmpty().trim().uppercase().removeSuffix(".CA")

    /**
     * Walks forward one session at a time and returns the first outcome that settles.
     *
     * A session whose high reaches the target and whose low reaches the stop is reported as
     * ambiguous: daily figures cannot say which came first, and picking the favourable one would
     * quietly inflate every hit rate built on this.
     */
    fun score(
        sessions: List<DailySession>,
        entryLow: Double?,
        entryHigh: Double?,
        target1: Double?,
        target2: Double?,
        stopLoss: Double?,
        windowSessions: Int,
    ): Scored {
        val window = clampWindow(windowSessions)
        val considered = sessions.take(window)
        if (considered.isEmpty()) return Scored(Outcome.UNPRICED, null, null, 0, null, null)

        var entered = entryLow == null && entryHigh == null
        var peak: Double? = null
        considered.forEachIndexed { zeroBased, day ->
            val elapsed = zeroBased + 1
            day.high?.let { high -> peak = peak?.coerceAtLeast(high) ?: high }
            if (!entered && day.touchedEntry(entryLow, entryHigh)) entered = true
            if (entered) {
                val hitTarget = reached(day.high, target2) || reached(day.high, target1)
                val hitStop = day.low != null && stopLoss != null && day.low <= stopLoss
                when {
                    hitTarget && hitStop ->
                        return Scored(Outcome.AMBIGUOUS, day.date, null, elapsed, peak, null)
                    reached(day.high, target2) -> return Scored(
                        Outcome.TARGET_2, day.date, target2, elapsed, peak,
                        returnPct(entryLow, entryHigh, target2),
                    )
                    reached(day.high, target1) -> return Scored(
                        Outcome.TARGET_1, day.date, target1, elapsed, peak,
                        returnPct(entryLow, entryHigh, target1),
                    )
                    hitStop -> return Scored(
                        Outcome.STOPPED, day.date, stopLoss, elapsed, peak,
                        returnPct(entryLow, entryHigh, stopLoss),
                    )
                }
            }
        }

        return when {
            !entered -> Scored(Outcome.ENTRY_NOT_REACHED, null, null, considered.size, peak, null)
            considered.size >= window ->
                Scored(Outcome.EXPIRED, null, null, considered.size, peak, null)
            else -> Scored(Outcome.OPEN, null, null, considered.size, peak, null)
        }
    }

    /** The session traded through the entry band at some point. */
    private fun DailySession.touchedEntry(low: Double?, high: Double?): Boolean {
        val boundLow = low ?: high ?: return false
        val boundHigh = high ?: low ?: return false
        if (this.low == null || this.high == null) return false
        return this.low <= maxOf(boundLow, boundHigh) && this.high >= minOf(boundLow, boundHigh)
    }

    private fun reached(high: Double?, target: Double?): Boolean =
        high != null && target != null && high >= target

    private fun returnPct(low: Double?, high: Double?, exit: Double?): Double? {
        val entry = low ?: high ?: return null
        if (entry == 0.0 || exit == null) return null
        return ((exit - entry) / entry * 100).round(2)
    }
}

/** How a recommendation turned out, or why it cannot be judged. */
enum class Outcome(val label: String, val judged: Boolean) {
    TARGET_1("Target 1 hit", judged = true),
    TARGET_2("Target 2 hit", judged = true),
    STOPPED("Stopped out", judged = true),
    EXPIRED("Expired", judged = true),

    // Outcomes that say nothing about whether the source was right, so they are reported separately
    // rather than counted as hits or misses.
    ENTRY_NOT_REACHED("Entry never traded", judged = false),
    OPEN("Still open", judged = false),
    AMBIGUOUS("Ambiguous", judged = false),
    UNPRICED("No price data", judged = false),
    ;

    val isHit: Boolean get() = this == TARGET_1 || this == TARGET_2
}

/** One trading session as the price feed reported it. */
data class DailySession(
    val ticker: String,
    val date: LocalDate,
    val high: Double?,
    val low: Double?,
    val close: Double?,
    val volume: Double?,
)

data class Scored(
    val outcome: Outcome,
    val settledOn: LocalDate?,
    val priceAtSettlement: Double?,
    val sessionsElapsed: Int,
    val peakHigh: Double?,
    val returnPct: Double?,
)

/** Prices come off the feed at full float precision, which reads as noise rather than a price. */
internal fun Double.round(decimals: Int): Double {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    return Math.round(this * factor) / factor
}
