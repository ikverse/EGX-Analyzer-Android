package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/**
 * A call the market has finished with, written down once so it is never scored again.
 *
 * Everything else on the Insights tab is derived on every recompute, and deliberately so: a figure
 * that is worked out from the prices cannot drift from them. A **settled** call is the one case
 * where deriving it again answers a question the market has already closed. It reached the second
 * target, or it broke the stop, or it banked the first target and fell back to the stop - and no
 * session after that can change any of the three. Replaying thirty sessions of prices to be told so
 * again is work with one possible answer.
 *
 * So the verdict is frozen, and the sessions it was judged on are frozen with it. The sessions are
 * kept here rather than re-read because they are the *evidence*: the call card draws them, and a
 * frozen verdict beside a table fetched from somewhere else could disagree with itself. Thirty rows
 * on a few hundred closed calls is a table measured in kilobytes, against the per-call query it
 * removes from every recompute.
 *
 * Device-local and **never synced**, the same rule `price_events` follows. Every device fetches the
 * same prices from the same public feed and settles a call the same way; shipping one phone's
 * conclusion into another phone's evidence would put an opinion where a measurement belongs.
 *
 * It is a cache with an honest key, not a second source of truth. [settledKey] carries the levels
 * and the window, so a re-extraction that reads a different stop re-opens the call rather than
 * inheriting a verdict that was reached about different numbers, and a heal or a newly recorded
 * change of scale drops every row for that stock - those are the two events that rewrite the prices
 * underneath a settled call.
 */
data class SettledCall(
    /** [settledKey] of the call this is the verdict for. */
    val key: String,
    /** Normalized, so a heal can drop every verdict resting on one stock's prices. */
    val ticker: String,
    val outcome: Outcome,
    val settledOn: LocalDate?,
    val stoppedOn: LocalDate?,
    val stoppedAfterPartial: Boolean,
    val windowComplete: Boolean,
    val peakHigh: Double?,
    val peakOn: LocalDate?,
    val troughLow: Double?,
    val troughOn: LocalDate?,
    val returnPct: Double?,
    val sessionsElapsed: Int,
    /** The sessions the call was replayed on, so its card still draws the price table. */
    val sessions: List<DailySession>,
)

/**
 * Whether this verdict is one the market can never take back.
 *
 * The three the record calls closed. A partial hit is final **only** once the stop has taken it
 * back: left standing it can still be promoted to a full hit, which is the whole reason a call runs
 * to its settlement rather than to a deadline. Everything else is still moving - an expiry and an
 * entry that never traded are settled in principle, but they are rare, they cost nothing to derive,
 * and freezing them would widen this for no gain.
 */
fun Outcome.isFinal(stoppedAfterPartial: Boolean): Boolean = when (this) {
    Outcome.FULL_HIT, Outcome.STOPPED -> true
    Outcome.PARTIAL_HIT -> stoppedAfterPartial
    else -> false
}

/**
 * What a frozen verdict is filed under: this call, judged against exactly these numbers.
 *
 * [opinionId] identifies the call - the stock, the session and the channel that named it. The rest
 * is a fingerprint of what was actually judged: the levels, and how long the call was given. A
 * report re-read by a newer prompt can come back with a different stop on the same card, and a
 * verdict reached about the old one says nothing about the new one. Changing any of them produces a
 * different key, the old row is simply never asked for again, and the call is scored from scratch.
 */
fun ScoredCall.settledKey(): String = listOf(
    opinionId(ticker, openedOn, channel),
    entryLow.orNone(),
    entryHigh.orNone(),
    target1.orNone(),
    target2.orNone(),
    stopLoss.orNone(),
    "$windowSessions/$entrySessions",
).joinToString("#")

/** A level the channel did not print, spelled out rather than left as an empty gap in the key. */
private fun Double?.orNone(): String = this?.toString() ?: "-"
