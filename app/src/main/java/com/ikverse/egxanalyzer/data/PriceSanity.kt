package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * A session whose prices are on a different scale from the session before it.
 *
 * The cause is a corporate action - a share split, a bonus issue - rather than anything the market
 * did. Two hundred shares worth half as much each is the same company at the same value, but the
 * printed price halves overnight, and every level a channel published the day before is quoted in
 * the old money.
 */
data class PriceBreak(
    val ticker: String,
    /**
     * The first session on the new scale.
     *
     * Prices before this date and prices from it onward cannot be compared with each other, and
     * neither can be compared with a level printed on the other side of it.
     */
    val date: LocalDate,
    /** The last price on the old scale. */
    val previousClose: Double,
    /** The first price on the new one. */
    val openingPrice: Double,
) {
    /** How far the price moved across the break, as a multiple: a 2-for-1 split arrives near 0.5. */
    val ratio: Double get() = if (previousClose == 0.0) 0.0 else openingPrice / previousClose
}

/**
 * Whether a fetched price series can be believed.
 *
 * Two questions, both about the feed rather than about the market. The first is whether the series
 * changes scale partway through, which is what a split does to it and what would otherwise be scored
 * as a collapse - the app would record every open call on the stock as a stop-out and charge the
 * channel that made them. The second is whether the feed has simply stopped: a series that answers
 * every request while its newest session stays three weeks old looks exactly like a market that is
 * closed, and the app went on treating it as current.
 *
 * Deliberately arithmetic and deliberately pure. Nothing here asks Yahoo what happened - a feed that
 * has gone wrong is not the thing to ask about its own reliability - and nothing here corrects a
 * price. It reports what cannot be trusted, and the scorer's answer to that is to say so rather than
 * to judge a call on it.
 */
object PriceSanity {

    /**
     * How far a stock may move between two consecutive sessions before the move is read as a change
     * of scale rather than as trading.
     *
     * The exchange itself is the argument for a threshold existing at all: EGX caps how far a stock
     * may move in one session, so a move far beyond that cap is not something the market was
     * permitted to do. The number is set well above the cap rather than at it, because the cap has
     * exceptions - newly listed stocks, stocks returning from suspension - and this must fire on a
     * data event, never on a violent but legal session. A split is at least 2-for-1, which lands at
     * -50%, so the gap between the two is wide.
     */
    const val MAX_SESSION_MOVE = 0.30

    /**
     * How far apart two sessions may be and still be read as consecutive.
     *
     * A 30% move overnight is not something the market did. A 30% move across a three-month hole in
     * the history is ordinary, and reading it as a split would flag every stock the app had not
     * priced in a while. Where the gap is wider than this the pair is skipped: the app cannot say
     * what happened in between, and saying nothing is the honest answer.
     */
    const val MAX_SESSION_GAP_DAYS = 7L

    /**
     * How old a series' newest session may be before the feed is reported as stale.
     *
     * EGX trades Sunday to Thursday, so a Friday and a Saturday sit behind every Sunday, and a
     * public holiday can put four or five days between two real sessions. Seven clears all of that
     * and still catches a feed that has quietly stopped - which has happened here before, when
     * Yahoo moved EGX onto ISIN-form symbols and the old ones went on answering with a frozen
     * history rather than with an error.
     */
    const val MAX_SESSION_AGE_DAYS = 7L

    /**
     * Every change of scale in a series, oldest first.
     *
     * The series is expected in date order and to include the last session already stored, where
     * there is one: the break almost always falls between what is on disk and what has just been
     * fetched, because that is where a refresh stops and starts. Comparing only within the fetched
     * window would miss precisely the case this exists for.
     *
     * The comparison runs from one session's close to the next session's open, which are adjacent
     * prices with nothing between them, falling back to close-to-close where the open was not
     * recorded. Sessions carrying no usable price are stepped over rather than breaking the chain,
     * so a single in-progress row does not hide a split behind it.
     */
    fun breaks(ticker: String, sessions: List<DailySession>): List<PriceBreak> {
        val ordered = sessions.map(DailySession::traded).sortedBy(DailySession::date)
        val found = mutableListOf<PriceBreak>()
        var previous: DailySession? = null
        for (session in ordered) {
            val from = previous?.close
            val to = session.open ?: session.close
            if (from != null && to != null) {
                val gap = ChronoUnit.DAYS.between(previous!!.date, session.date)
                if (gap in 1..MAX_SESSION_GAP_DAYS && abs(to - from) / from > MAX_SESSION_MOVE) {
                    found += PriceBreak(
                        ticker = ticker,
                        date = session.date,
                        previousClose = from,
                        openingPrice = to,
                    )
                }
            }
            // Only a session with a close can anchor the next comparison; one without leaves the
            // previous anchor standing, so the chain reaches across it rather than stopping there.
            if (session.close != null) previous = session
        }
        return found
    }

    /**
     * How many days old the newest session in a series is, or null for a series with nothing in it.
     *
     * Calendar days rather than sessions, because the question is how long the feed has been silent
     * and a feed that has stopped reports no sessions at all to count.
     */
    fun ageInDays(sessions: List<DailySession>, today: LocalDate): Long? {
        val newest = sessions.maxOfOrNull(DailySession::date) ?: return null
        return ChronoUnit.DAYS.between(newest, today).coerceAtLeast(0)
    }

    /** Whether a series is old enough that the feed behind it should be doubted. */
    fun isStale(sessions: List<DailySession>, today: LocalDate): Boolean =
        (ageInDays(sessions, today) ?: 0) > MAX_SESSION_AGE_DAYS

    /**
     * The dates a call's window cannot straddle, for one stock.
     *
     * A window that lies entirely on one side of a break is fine: the levels and the prices are in
     * the same money, whichever money that is. It is a window containing one that cannot be judged.
     */
    fun datesOf(breaks: List<PriceBreak>): Set<LocalDate> = breaks.map(PriceBreak::date).toSet()
}
