package com.ikverse.egxanalyzer.model

import java.time.Instant
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
        /**
         * Sessions on which this stock's prices changed scale - a split, a bonus issue.
         *
         * Empty by default, so a caller with no price history to consult scores exactly as before.
         */
        priceBreaks: Set<LocalDate> = emptySet(),
        /**
         * Five-minute bars for a session, where any have been fetched.
         *
         * Consulted only for the one session a call cannot order on its own, so this is a lambda
         * rather than a map: for almost every call it is never asked at all. Empty by default, so a
         * caller with no intraday history scores exactly as before.
         */
        intradayFor: (LocalDate) -> List<IntradayBar> = { emptyList() },
    ): Scored {
        val fullTargetLevel = target2 ?: target1
        val partialTargetLevel = target1.takeIf { target2 != null }
        val plain = walk(
            sessions, entryLow, entryHigh, target1, target2, stopLoss, windowSessions, priceBreaks,
        ) { day ->
            resolveFromBars(
                intradayFor(day.date), entryLow, entryHigh, partialTargetLevel, fullTargetLevel,
            )
        }
        if (plain.outcome != Outcome.AMBIGUOUS) return plain

        // Neither the open nor the bars could order that session, but the rest of the window often
        // makes the question moot. The entry is a fact of the session either way - its low traded
        // through the band - so the reader holds from its close whichever way round it happened,
        // and only that day's own target is in doubt. Score it both ways: where the two agree there
        // is nothing left to be ambiguous about.
        val entryFirst = walk(
            sessions, entryLow, entryHigh, target1, target2, stopLoss, windowSessions, priceBreaks,
        ) { Ordering.ENTRY_FIRST }
        val targetFirst = walk(
            sessions, entryLow, entryHigh, target1, target2, stopLoss, windowSessions, priceBreaks,
        ) { Ordering.TARGET_FIRST }
        // The pessimistic run is the one reported. Where the two agree it is also the later of the
        // two, since it never credits the unproven target - so this is the conservative settlement
        // date as well as the conservative reading.
        return if (entryFirst.outcome == targetFirst.outcome) targetFirst else plain
    }

    /**
     * One pass over the window under a single answer to "which of the two came first".
     *
     * [ordering] is asked only about the session that first offered the entry and reached a target
     * on the same day, and only when the open cannot settle it for free.
     */
    private fun walk(
        sessions: List<DailySession>,
        entryLow: Double?,
        entryHigh: Double?,
        target1: Double?,
        target2: Double?,
        stopLoss: Double?,
        windowSessions: Int,
        priceBreaks: Set<LocalDate>,
        ordering: (DailySession) -> Ordering,
    ): Scored {
        val window = clampWindow(windowSessions)
        // A session in progress can arrive with a high or low of zero. Stored once, it stopped
        // every call on that stock - nothing trades below nothing - and reported a peak of zero
        // besides. A price that is not positive is not a price, whatever the feed says.
        val considered = sessions.map(DailySession::traded).take(window)
        if (considered.isEmpty()) return Scored(Outcome.UNPRICED, null, null, 0, null, null, null, null, null)

        // A split inside the window puts the levels and the prices in different money, and every
        // comparison below - the entry, the targets, the stop - is then between two numbers that
        // were never the same kind of thing. Judged on it, a 2-for-1 split reads as a 50% collapse
        // and the call is recorded as a stop-out the channel never earned.
        //
        // The test is `>=` rather than `>` on the first session deliberately. A break dated on the
        // very first session of the window still leaves the call's own levels on the old scale,
        // since they were printed before that session opened. It can cost a call that was genuinely
        // made after the split, and that is the direction to err in: a rate that is missing a call
        // is honest, a rate that counts a phantom loss is not.
        val breakInside = priceBreaks.any { it >= considered.first().date && it <= considered.last().date }
        if (breakInside) {
            // No peak, no trough, no return: every one of them would be measured across the break.
            return Scored(
                Outcome.PRICE_BREAK, null, null, considered.size, null, null, null, null, null,
            )
        }

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

                if (partialOn == null && hitAnyTarget) {
                    // A target and the stop in one session is read as the target first: the call
                    // got where it was sent and gave it back, which is not the same as a call that
                    // only ever went against you. Daily figures cannot prove that order, so this
                    // credits the favourable one deliberately.
                    if (hitStop) {
                        return partial(
                            partialTarget ?: fullTarget, day.date, elapsed, peak, peakOn, trough,
                            troughOn, entryLow, entryHigh, stoppedAfter = true, windowComplete = true,
                        )
                    }
                    // The entry first became available on the same session a target was reached.
                    // The open orders those for free where it already sits inside the band; failing
                    // that it takes the bars, and failing those the caller's assumption.
                    if (enteredHere && !day.buyableAtOpen(entryLow, entryHigh)) {
                        when (val order = ordering(day)) {
                            // The band traded before the high, so the target counts and this
                            // session is scored exactly as an unambiguous one would be.
                            Ordering.ENTRY_FIRST -> Unit
                            // The target was reached before the band ever traded, so the reader
                            // could not have been in for it. They are in from here on - which is
                            // what leaving `entered` set says - but this target is not theirs.
                            Ordering.TARGET_FIRST -> return@forEachIndexed
                            Ordering.SAME_BAR, Ordering.NO_DATA -> return Scored(
                                Outcome.AMBIGUOUS, day.date, null, elapsed, peak, peakOn, trough,
                                troughOn, null,
                                ambiguity = if (order == Ordering.SAME_BAR) {
                                    Ambiguity.SAME_INTRADAY_BAR
                                } else {
                                    Ambiguity.ENTRY_AND_TARGET
                                },
                            )
                        }
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
    private fun DailySession.touchedEntry(low: Double?, high: Double?): Boolean =
        traded(this.low, this.high, low, high)

    /** The same question of one intraday bar, so a bar and a session cannot answer it differently. */
    private fun IntradayBar.touchedEntry(low: Double?, high: Double?): Boolean =
        traded(this.low, this.high, low, high)

    /** Whether a low/high pair overlaps the entry band, at the precision the feed can be trusted to. */
    private fun traded(barLow: Double?, barHigh: Double?, low: Double?, high: Double?): Boolean {
        val boundLow = low ?: high ?: return false
        val boundHigh = high ?: low ?: return false
        if (barLow == null || barHigh == null) return false
        return barLow.atMost(maxOf(boundLow, boundHigh)) &&
            barHigh.atLeast(minOf(boundLow, boundHigh))
    }

    /**
     * Which of the entry and the target came first, as far as anything can say.
     *
     * [SAME_BAR] and [NO_DATA] are both "unknown" to the scorer and differ only in what the card is
     * then able to tell the reader: one means the feed was asked and could not separate them, the
     * other that it was never asked or had nothing left to give.
     */
    private enum class Ordering { ENTRY_FIRST, TARGET_FIRST, SAME_BAR, NO_DATA }

    /**
     * Reads the real order off a session's intraday bars.
     *
     * The first bar whose range covers the buy zone against the first bar whose high reaches a
     * target: at five minutes these are almost never the same bar, and when they are the session
     * genuinely cannot be ordered any finer than the feed offers.
     *
     * A bar that reaches a target without the band ever trading is not an answer either - the entry
     * has to have happened for the question to arise - so a missing side reads as no data rather
     * than as the other side winning.
     */
    private fun resolveFromBars(
        bars: List<IntradayBar>,
        entryLow: Double?,
        entryHigh: Double?,
        partialTarget: Double?,
        fullTarget: Double?,
    ): Ordering {
        if (bars.isEmpty()) return Ordering.NO_DATA
        val ordered = bars.sortedBy(IntradayBar::at)
        val entryAt = ordered.indexOfFirst { it.touchedEntry(entryLow, entryHigh) }
        val targetAt = ordered.indexOfFirst {
            reached(it.high, fullTarget) || (partialTarget != null && reached(it.high, partialTarget))
        }
        return when {
            entryAt < 0 || targetAt < 0 -> Ordering.NO_DATA
            entryAt < targetAt -> Ordering.ENTRY_FIRST
            targetAt < entryAt -> Ordering.TARGET_FIRST
            else -> Ordering.SAME_BAR
        }
    }

    /**
     * Whether two prices are the same price, at the precision anyone actually quotes one.
     *
     * The feed sends 32-bit floats, so a stock that opened at exactly 18.10 is stored as
     * 18.100000381469727. Against a buy zone ending at 18.1 that read as opening *above* the zone,
     * and the call was recorded as unorderable rather than the partial hit it was. UEGC lost a day
     * the same way, on a low of 2.430000066757202 against 2.43.
     *
     * Three decimals is more than any of these cards print, so this can only ever forgive the
     * feed's own noise. Deliberately not applied to targets: a target is either met or it is not.
     */
    private fun Double.atMost(bound: Double): Boolean = this <= bound + PRICE_EPSILON

    private fun Double.atLeast(bound: Double): Boolean = this >= bound - PRICE_EPSILON

    private const val PRICE_EPSILON = 0.0005

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
        return open != null && open.atMost(bound)
    }

    /**
     * Whether the session's high got to the target.
     *
     * The comparison allows for the feed's own precision and nothing else. Yahoo sends 32-bit
     * floats, so a high of 1.03 arrives as 1.0299999713897705 - GGCC reached its first target on
     * 4 August and was recorded as a plain stop-out because of it. This is not a tolerance: the
     * slack is a millionth, far below any price anyone quotes and far above the float's own error,
     * so a target genuinely missed is still missed.
     */
    private fun reached(high: Double?, target: Double?): Boolean =
        high != null && target != null && high >= target * (1 - FLOAT_PRECISION_SLACK)

    /** Comfortably above a 32-bit float's error, comfortably below a quoted price's last digit. */
    private const val FLOAT_PRECISION_SLACK = 1e-6

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

    /**
     * The stock's prices changed scale inside the window - a split, or a bonus issue.
     *
     * Unjudged for the same reason as the rest: it says nothing about whether the source was right.
     * A company handing out bonus shares halves the printed price without anyone being wrong about
     * anything, and a channel must never lose a call to it.
     */
    PRICE_BREAK("Prices changed scale", judged = false),
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

    /**
     * The row does not hang together: no close, or a close outside its own high and low.
     *
     * A session still trading reports exactly this. GBCO came back on 16 August with a close of
     * 30.16 beneath a low of 30.31, because the session had not finished and the two fields were
     * written at different moments. It is a second, feed-side sign of the same thing the session
     * date says, and it catches the case where the phone's calendar and the exchange's disagree.
     *
     * The slack is the feed's own 32-bit precision, not a tolerance: a close genuinely at the high
     * must not read as being above it.
     */
    val inconsistent: Boolean
        get() {
            val settled = close ?: return true
            return (high != null && settled > high * (1 + FLOAT_NOISE)) ||
                (low != null && settled < low * (1 - FLOAT_NOISE))
        }

    private companion object {
        const val FLOAT_NOISE = 1e-6
    }
}

/**
 * Why a session could not be ordered.
 *
 * Daily bars give a high and a low but no sequence, so two events inside one session sometimes
 * cannot be placed against each other. Which of the two it was is the only useful thing to say
 * about an ambiguous call, and it was being thrown away.
 */
enum class Ambiguity {
    /** The buy zone first opened on the day a target was reached, above the zone. */
    ENTRY_AND_TARGET,

    /**
     * The bars were read and both events fall inside the same five-minute bar.
     *
     * As far as this feed goes the two are simultaneous. Kept apart from [ENTRY_AND_TARGET] because
     * the two ask for different things: that one may be answerable by fetching, this one never is.
     */
    SAME_INTRADAY_BAR,
}

/**
 * One intraday bar, fine enough to order two events inside a session.
 *
 * Only the extremes are kept. The open and the close of a five-minute bar say nothing the high and
 * the low do not already say about whether a level was touched inside it.
 */
data class IntradayBar(
    val ticker: String,
    val at: Instant,
    val high: Double?,
    val low: Double?,
)

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
    /** Set only for [Outcome.AMBIGUOUS], saying which pair of events could not be ordered. */
    val ambiguity: Ambiguity? = null,
    /** False while the window is still running, so a partial hit may still become a full one. */
    val windowComplete: Boolean = false,
)

/** Prices come off the feed at full float precision, which reads as noise rather than a price. */
internal fun Double.round(decimals: Int): Double {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    return Math.round(this * factor) / factor
}
