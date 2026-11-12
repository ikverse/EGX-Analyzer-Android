package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.ZoneId

/**
 * Following the market a minute at a time, while somebody is looking at the app.
 *
 * [MarketRefresh] is the background's answer and cannot be finer than a quarter of an hour: Android
 * stretches an alarm on a dozing phone to roughly one in ten minutes. None of that applies to an
 * app on screen, so this is a loop rather than an alarm, and it runs only between
 * `enterLivePrices` and `leaveLivePrices` - the activity resumed and paused.
 *
 * **A minute, because that is the finest bar the feed serves** (measured 2026-11-12: the ISIN
 * symbol answers `interval=1m` with true one-minute bars). It is not a minute behind the exchange:
 * the same measurement put the feed's newest bar about fifteen minutes old, on the most liquid
 * stock listed, throughout a live session. Polling faster would ask for the same bar twice.
 *
 * **Narrow on purpose.** One request per stock per minute, against a public feed the app is a guest
 * on, so it covers only the stocks where a minute matters - an open trade, a call still waiting on
 * the market, the watchlist - and stops at [MAX_STOCKS]. Everything else stays on the ordinary
 * refresh. The feed's batched `spark` endpoint was measured and left out: it answers with a close
 * per minute and no high or low, and a level touched between two closes is exactly what this is
 * for.
 *
 * No Android in here, for the reason [ScheduleClock] and [MarketRefresh] have none.
 */
object LivePrices {

    /** One bar of the feed. */
    const val EVERY_SECONDS = 60L

    /** The most stocks asked about in one minute. */
    const val MAX_STOCKS = 40

    /**
     * Whether the exchange is inside a session this loop should follow.
     *
     * The bell to [ScheduleClock.sessionEnd], which is a quarter of an hour past the close - the
     * same quarter of an hour the feed runs behind, so the last minute polled is the one that
     * brings the closing bars. At the end and after it the session is final and the ordinary
     * refresh owns its row: a live write past that line could replace a reported close with one
     * built from bars.
     */
    fun inSession(now: Instant, zone: ZoneId = ScheduleClock.ZONE): Boolean {
        val here = now.atZone(zone)
        if (here.dayOfWeek !in ScheduleClock.tradingDays) return false
        val time = here.toLocalTime()
        return time >= ScheduleClock.sessionStart && time < ScheduleClock.sessionEnd
    }

    /**
     * Which stocks one minute asks about, most pressing first.
     *
     * Open trades lead because they are the reader's own money and the cap must never be what
     * drops one; calls the market has not finished with come next, then the watchlist. A stock
     * named twice is asked about once, at the place it first appears.
     */
    fun watched(
        openTrades: Collection<String>,
        openCalls: Collection<String>,
        watchlist: Collection<String>,
        limit: Int = MAX_STOCKS,
    ): List<String> = (openTrades.asSequence() + openCalls + watchlist)
        .map(Scoring::normalizeTicker)
        .filter(String::isNotBlank)
        .distinct()
        .take(limit)
        .toList()

    /** Whether a call is one the market has not finished with, so a new minute can still move it. */
    fun stillMoving(outcome: Outcome): Boolean =
        outcome == Outcome.OPEN || outcome == Outcome.AMBIGUOUS
}
