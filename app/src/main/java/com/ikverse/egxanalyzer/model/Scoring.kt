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
     * How far below the stop a session must trade before the call counts as stopped out.
     *
     * The sources print the rule on the cards themselves - "ننصح بإيقاف الخسائر المحدد مع العلم أنة
     * يتاكد بالكسر بنسبة 2%", the stop is confirmed by a 2% break - and judging them on an exact
     * touch instead was stricter than the rule they publish. Across the saved calls it turned 26
     * stop-outs into 7, and six of the calls it had recorded as losses had reached a target.
     *
     * Only the stop is given room. A target loosened the same way would credit a source for a level
     * it never traded at.
     */
    const val STOP_BREAK_TOLERANCE = 0.02

    /**
     * One spelling per stock.
     *
     * Sources quote the same company as both AMOC and AMOC.CA, and treating those as two stocks
     * splits a channel's record in half and prices only one of them.
     */
    fun normalizeTicker(value: String?): String =
        value.orEmpty().trim().uppercase().removeSuffix(".CA")

    /**
     * Walks the whole window and reports how far the call got.
     *
     * Reaching the first target no longer ends the call: it is a partial hit that keeps running,
     * because the second target may still arrive before the window closes. A session whose high
     * reaches a target and whose low reaches the stop is reported as ambiguous - daily figures
     * cannot say which came first, and picking the favourable one would inflate every rate built
     * on this.
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
        // A session in progress can arrive with a high or low of zero. Stored once, it stopped
        // every call on that stock - nothing trades below nothing - and reported a peak of zero
        // besides. A price that is not positive is not a price, whatever the feed says.
        val considered = sessions.map(DailySession::traded).take(window)
        if (considered.isEmpty()) return Scored(Outcome.UNPRICED, null, null, 0, null, null, null, null, null)

        // A call quoting only one target has nothing further to reach, so that target is the full
        // one rather than a partial step toward a second that was never named.
        val fullTarget = target2 ?: target1
        val partialTarget = target1.takeIf { target2 != null }

        var entered = entryLow == null && entryHigh == null
        var peak: Double? = null
        var trough: Double? = null
        // Which session set each extreme, so the figure can be placed rather than just quoted.
        var peakOn: LocalDate? = null
        var troughOn: LocalDate? = null
        var partialOn: LocalDate? = null
        var partialElapsed = 0

        considered.forEachIndexed { zeroBased, day ->
            val elapsed = zeroBased + 1
            day.high?.let { high ->
                val best = peak
                if (best == null || high > best) {
                    peak = high
                    peakOn = day.date
                }
            }
            day.low?.let { low ->
                val worst = trough
                if (worst == null || low < worst) {
                    trough = low
                    troughOn = day.date
                }
            }
            val enteredHere = !entered && day.touchedEntry(entryLow, entryHigh)
            if (enteredHere) entered = true
            if (entered) {
                val hitFull = reached(day.high, fullTarget)
                val hitPartial = partialTarget != null && reached(day.high, partialTarget)
                val hitStop = day.low != null && stopLoss != null &&
                    day.low <= stopLoss * (1 - STOP_BREAK_TOLERANCE)
                val hitAnyTarget = hitFull || hitPartial

                // Nothing has settled yet, so an unorderable session cannot be resolved either way.
                if (partialOn == null && hitAnyTarget) {
                    if (hitStop) {
                        return Scored(Outcome.AMBIGUOUS, day.date, null, elapsed, peak, peakOn, trough, troughOn, null)
                    }
                    // The entry first became available on the same session a target was reached.
                    // Only the open can order those, since it precedes every other price of the day.
                    if (enteredHere && !day.buyableAtOpen(entryLow, entryHigh)) {
                        return Scored(Outcome.AMBIGUOUS, day.date, null, elapsed, peak, peakOn, trough, troughOn, null)
                    }
                }

                if (hitFull) {
                    // Already a partial hit and this session also reached the stop: the upgrade
                    // cannot be ordered against it, so the partial stands rather than being
                    // promoted on a guess.
                    if (partialOn != null && hitStop) {
                        return partial(partialTarget, partialOn, partialElapsed, peak, peakOn,
                            trough, troughOn, entryLow, entryHigh, stoppedAfter = true,
                            windowComplete = true)
                    }
                    return Scored(
                        Outcome.FULL_HIT, day.date, fullTarget, elapsed, peak, peakOn, trough,
                        troughOn, returnPct(entryLow, entryHigh, fullTarget),
                    )
                }
                if (hitPartial && partialOn == null) {
                    partialOn = day.date
                    partialElapsed = elapsed
                }
                if (hitStop) {
                    // The first target was already banked, so the call is not simply a loss.
                    if (partialOn != null) {
                        return partial(partialTarget, partialOn, partialElapsed, peak, peakOn,
                            trough, troughOn, entryLow, entryHigh, stoppedAfter = true,
                            windowComplete = true)
                    }
                    return Scored(
                        Outcome.STOPPED, day.date, stopLoss, elapsed, peak, peakOn, trough,
                        troughOn, returnPct(entryLow, entryHigh, stopLoss),
                    )
                }
            }
        }

        val complete = considered.size >= window
        return when {
            !entered ->
                Scored(Outcome.ENTRY_NOT_REACHED, null, null, considered.size, peak, peakOn, trough, troughOn, null)
            partialOn != null -> partial(partialTarget, partialOn, partialElapsed, peak, peakOn,
                trough, troughOn, entryLow, entryHigh, stoppedAfter = false,
                windowComplete = complete)
            complete -> Scored(Outcome.EXPIRED, null, null, considered.size, peak, peakOn, trough, troughOn, null)
            else -> Scored(Outcome.OPEN, null, null, considered.size, peak, peakOn, trough, troughOn, null)
        }
    }

    private fun partial(
        target: Double?,
        on: LocalDate?,
        elapsed: Int,
        peak: Double?,
        peakOn: LocalDate?,
        trough: Double?,
        troughOn: LocalDate?,
        entryLow: Double?,
        entryHigh: Double?,
        stoppedAfter: Boolean,
        windowComplete: Boolean,
    ) = Scored(
        outcome = Outcome.PARTIAL_HIT,
        settledOn = on,
        priceAtSettlement = target,
        sessionsElapsed = elapsed,
        peakHigh = peak,
        peakOn = peakOn,
        troughLow = trough,
        troughOn = troughOn,
        returnPct = returnPct(entryLow, entryHigh, target),
        stoppedAfterPartial = stoppedAfter,
        windowComplete = windowComplete,
    )

    /** The session traded through the entry band at some point. */
    private fun DailySession.touchedEntry(low: Double?, high: Double?): Boolean {
        val boundLow = low ?: high ?: return false
        val boundHigh = high ?: low ?: return false
        if (this.low == null || this.high == null) return false
        return this.low <= maxOf(boundLow, boundHigh) && this.high >= minOf(boundLow, boundHigh)
    }

    /**
     * The session opened at a price the entry band would already have bought.
     *
     * This is the one thing daily figures can say about ordering: the open precedes every other
     * price in the session, so an open at or below the top of the band means the entry was
     * available before the day's high. Sessions stored before the open was recorded report null,
     * which is treated as unknown rather than favourable.
     */
    private fun DailySession.buyableAtOpen(low: Double?, high: Double?): Boolean {
        val bound = listOfNotNull(low, high).maxOrNull() ?: return false
        return open != null && open <= bound
    }

    private fun reached(high: Double?, target: Double?): Boolean =
        high != null && target != null && high >= target

    /**
     * Return measured from the middle of the entry band.
     *
     * Measuring from the bottom assumed the best price in the band was filled every time, which
     * overstated every winning call.
     */
    private fun returnPct(low: Double?, high: Double?, exit: Double?): Double? {
        val entry = if (low != null && high != null) (low + high) / 2 else low ?: high ?: return null
        if (entry == 0.0 || exit == null) return null
        return ((exit - entry) / entry * 100).round(2)
    }
}

/** How a recommendation turned out, or why it cannot be judged. */
enum class Outcome(val label: String, val judged: Boolean) {
    /** The second target was reached - or the only target, for a call that named just one. */
    FULL_HIT("Full hit", judged = true),

    /** The first target was reached; the second was not, at least not yet. */
    PARTIAL_HIT("Partial hit", judged = true),
    STOPPED("Stopped out", judged = true),
    EXPIRED("Expired", judged = true),

    // Outcomes that say nothing about whether the source was right, so they are reported separately
    // rather than counted as hits or misses.
    ENTRY_NOT_REACHED("Entry never traded", judged = false),
    OPEN("Still open", judged = false),
    AMBIGUOUS("Ambiguous", judged = false),
    UNPRICED("No price data", judged = false),
    ;

    /** Reached the second target. */
    val isFullHit: Boolean get() = this == FULL_HIT

    /** Reached at least the first target. */
    val reachedATarget: Boolean get() = this == FULL_HIT || this == PARTIAL_HIT
}

/** One trading session as the price feed reported it. */
data class DailySession(
    val ticker: String,
    val date: LocalDate,
    val high: Double?,
    val low: Double?,
    val close: Double?,
    val volume: Double?,
    /** Null for sessions stored before the open was recorded; a refresh fills it in. */
    val open: Double? = null,
) {
    /**
     * The same session with any price that is not positive read as unknown.
     *
     * A session still in progress can come back with zeros where its high and low belong. Twelve
     * such rows were stored on 2 August, and every call on those stocks was judged stopped by a
     * low of zero. Rows already on disk are cleaned on the way in rather than trusted.
     */
    val traded: DailySession
        get() = copy(
            high = high?.takeIf { it > 0.0 },
            low = low?.takeIf { it > 0.0 },
            close = close?.takeIf { it > 0.0 },
            open = open?.takeIf { it > 0.0 },
        )
}

data class Scored(
    val outcome: Outcome,
    val settledOn: LocalDate?,
    val priceAtSettlement: Double?,
    val sessionsElapsed: Int,
    /** Highest and lowest the stock traded across the window, with the session that set each. */
    val peakHigh: Double?,
    val peakOn: LocalDate?,
    val troughLow: Double?,
    val troughOn: LocalDate?,
    val returnPct: Double?,
    /** The first target was banked and the stop was reached afterwards. */
    val stoppedAfterPartial: Boolean = false,
    /** False while the window is still running, so a partial hit may still become a full one. */
    val windowComplete: Boolean = false,
)

/** Prices come off the feed at full float precision, which reads as noise rather than a price. */
internal fun Double.round(decimals: Int): Double {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    return Math.round(this * factor) / factor
}
