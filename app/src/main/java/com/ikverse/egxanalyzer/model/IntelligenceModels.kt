package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.LocalDate

data class AnalysisReport(
    val title: String,
    val markdown: String,
)

/** One recommendation scored against the sessions that followed it. */
data class ScoredCall(
    val ticker: String,
    val companyEnglish: String?,
    val companyArabic: String?,
    val channel: String,
    /** Two chats can share a name once emoji come off, so coverage is keyed by id. */
    val channelId: Long?,
    val openedOn: LocalDate,
    val entryLow: Double?,
    val entryHigh: Double?,
    val target1: Double?,
    val target2: Double?,
    val stopLoss: Double?,
    val outcome: Outcome,
    val settledOn: LocalDate?,
    /** Highest and lowest the stock traded since the call, and the session that set each. */
    val peakHigh: Double?,
    val peakOn: LocalDate? = null,
    val troughLow: Double?,
    val troughOn: LocalDate? = null,
    val returnPct: Double?,
    val sessionsElapsed: Int,
    /**
     * The window this call was actually judged over.
     *
     * On the call rather than only on the report, because they are no longer all the same: a T+1
     * card is judged over its own two sessions while everything beside it takes the scoring
     * setting. A screen reading the report's figure would name the wrong deadline on exactly the
     * calls whose deadline is the point.
     */
    val windowSessions: Int = Scoring.DEFAULT_WINDOW_SESSIONS,
    /**
     * Leading sessions of that window the entry could first trade in.
     *
     * Equal to [windowSessions] for everything but a T+1 call, so the two differing is what marks
     * one - and is what a card checks before saying the buy zone missed its one session rather
     * than its whole window.
     */
    val entrySessions: Int = windowSessions,
    /** Set only for [Outcome.AMBIGUOUS], saying which pair of events could not be ordered. */
    val ambiguity: Ambiguity? = null,
    /** The first target was banked and the stop was reached afterwards. */
    val stoppedAfterPartial: Boolean = false,
    /** False while the window is still running, so a partial hit may still become a full one. */
    val windowComplete: Boolean = false,
    /** The sessions this call was judged on, so the card can show them without another query. */
    val sessions: List<DailySession> = emptyList(),
    /**
     * The session this exact call was first posted on, where this is a re-posting of it.
     *
     * A channel that prints the same table every morning is making one bet, not five, and counted
     * five times a single good call carries its whole record. Set on the later postings only, so the
     * first one stays the call. The card still shows it - the channel did post it that day, and the
     * session's record is what was posted - and every rate leaves it out.
     */
    val repeatOf: LocalDate? = null,
)

/**
 * What the call offered against what it risked, measured from the middle of the buy zone.
 *
 * The first target rather than the second: it is the one a reader can realistically take, and the
 * second is the channel's best case. Null where a level is missing or the levels contradict each
 * other - a stop above the entry is not a call risking nothing, it is a call this cannot describe.
 */
val ScoredCall.riskReward: Double?
    get() {
        val entry = if (entryLow != null && entryHigh != null) {
            (entryLow + entryHigh) / 2
        } else {
            entryLow ?: entryHigh ?: return null
        }
        val target = target1 ?: target2 ?: return null
        val stop = stopLoss ?: return null
        val reward = target - entry
        val risk = entry - stop
        if (reward <= 0 || risk <= 0) return null
        return reward / risk
    }

/**
 * One trading session and every call made for it, scored.
 *
 * The session is the subject, not the analysis: running the same day twice produces one record of
 * that day, not two competing ones. Where runs disagree the newer wins, so this holds the surviving
 * call from each.
 */
data class ScoredSession(
    /** The session these calls were made for. */
    val targetDate: LocalDate?,
    /** When it was last analysed, and with what - bookkeeping, kept for the detail view. */
    val lastRunAt: Instant,
    val model: String,
    /** How many analyses contributed, so a card built from more than one says so. */
    val runCount: Int,
    /** Chats covered by the newest run, against the total covered - the rest come from earlier ones. */
    val channelsFromLatest: Int = 0,
    val channelsTotal: Int = 0,
    val calls: List<ScoredCall>,
) {
    val fullHits: Int get() = calls.count { it.outcome.isFullHit }
    val partialHits: Int get() = calls.count { it.outcome == Outcome.PARTIAL_HIT }
    val stopped: Int get() = calls.count { it.outcome == Outcome.STOPPED }
    val pending: Int get() = calls.count { !it.outcome.judged }
}

/** A source's record over everything it has been scored on. */
data class ChannelScore(
    val channel: String,
    val calls: Int,
    val judged: Int,
    val fullHits: Int,
    val partialHits: Int,
    val stopped: Int,
    val expired: Int,
    val notTradable: Int,
    /** Reached the second target. */
    val fullHitRate: Double?,
    /** Reached at least the first target. */
    val anyTargetRate: Double?,
    /**
     * What one call was worth on average, across every judged call.
     *
     * The figure the ranking is built on. A hit rate can be bought by printing the target closer to
     * the entry: 90% at +2% against a -10% stop loses money, and how often a channel is right says
     * nothing on its own about whether following it pays.
     */
    val averageReturn: Double?,
    val medianSessionsToHit: Double?,
    /**
     * [averageReturn] pulled toward zero by how little is behind it, which is what the list is
     * ordered on.
     *
     * Six calls averaging +5% and fifty averaging +4.5% are not the same claim, and ordering on the
     * mean alone put the six on top - the mistake the minimum-judged floor exists to stop, made
     * again one call above it.
     *
     * A lower bound on the mean was the obvious way to do this and is the wrong one here. At the
     * ten-to-thirty calls a channel actually has, the spread of stock returns swamps the difference
     * between two channels' averages: a source printing +2% targets against a -10% stop scores a
     * *better* bound than one making more per call, purely for being less varied, which is the
     * ordering this whole figure exists to overturn.
     */
    val discountedReturn: Double? = null,
    /**
     * The Wilson 95% lower bound on [anyTargetRate]: the rate the evidence will bear.
     *
     * Printed under the rate rather than in place of it - the rate a channel achieved is the rate it
     * keeps. 6 of 6 is a true 100% with a floor of 61%; 40 of 50 is 80% with a floor of 67%, and the
     * second is the better record.
     */
    val anyTargetRateFloor: Double? = null,
    /**
     * How far the target sits above the entry against how far the stop sits below it, on average.
     *
     * The context a hit rate cannot be read without. A channel reaching a target on nine calls in
     * ten at 0.3 to 1 gives it all back on the tenth, and no other figure on the card would say so.
     */
    val averageRiskReward: Double? = null,
    /** Re-postings of a call already counted, left out of every figure above. */
    val repeats: Int = 0,
)

data class PerformanceReport(
    val windowSessions: Int,
    /**
     * The session scoring starts from: the later of the first stored price and the analysis floor.
     *
     * The starting line rather than the earliest call behind it. Derived from the calls, this was
     * null exactly when no call had been scored - which is the one case the screen needs it for, so
     * the empty state could never name the date it was waiting on. Null now means only that no
     * price has ever been stored.
     */
    val scoringSince: LocalDate? = null,
    /** Stocks with no stored price at all, so a refresh is the missing step. */
    val unpricedStocks: Int = 0,
    /**
     * Calls whose stock is priced but whose own sessions have not been published yet.
     *
     * A refresh cannot help these - the exchange data simply is not out - so telling the user to
     * refresh would be wrong.
     */
    val awaitingSessions: Int = 0,
    val tracked: Int = 0,
    val judged: Int = 0,
    val fullHits: Int = 0,
    val partialHits: Int = 0,
    /** Reached the second target. */
    val fullHitRate: Double? = null,
    /** Reached at least the first target. */
    val anyTargetRate: Double? = null,
    val byOutcome: Map<Outcome, Int> = emptyMap(),
    val channels: List<ChannelScore> = emptyList(),
    val sessions: List<ScoredSession> = emptyList(),
    /**
     * Where each stock stands now, as of the last refresh.
     *
     * A property of the stock rather than of any one call, so it is held once here and read by
     * ticker. Taking it from a call's own sessions would give the end of that call's window, which
     * for anything already settled is not the current price at all.
     */
    val latestPrices: Map<String, LatestPrice> = emptyMap(),
) {
    /**
     * The newest session any stock has a price for.
     *
     * Derived from the prices themselves rather than from the day a refresh last ran: a refresh
     * records that it went out, not that it came back with anything, and on a day the exchange did
     * not trade the two are a day or more apart.
     */
    val pricesTo: LocalDate? get() = latestPrices.values.maxOfOrNull { it.session.date }
}

/**
 * The newest stored session for one stock, and whether it can be trusted as final.
 *
 * [provisional] is what stops the card stating a price that is still moving as though the market
 * had closed on it. A session in progress is also where the feed contradicts itself - a close below
 * the day's own low - so the flag covers a row that is wrong as well as one that is merely early.
 */
data class LatestPrice(
    val session: DailySession,
    val provisional: Boolean,
)

/**
 * The trade this call would be recorded as, whether or not one was.
 *
 * The same key [Portfolio.heldFor] matches on, deliberately: it is what puts the held outline on a
 * call's card, so a link built from anything else could send a card somewhere its own outline
 * disagreed with. Two channels calling one stock for one session share it, because that is one
 * holding rather than two - both their cards point at the same trade, and it points back at both.
 */
val ScoredCall.positionId: String get() = positionId(Scoring.normalizeTicker(ticker), openedOn)

/** The session card holding the call a trade was taken on, if the record still has one. */
fun PerformanceReport.sessionFor(positionId: String): ScoredSession? =
    sessions.firstOrNull { session -> session.calls.any { it.positionId == positionId } }

/**
 * Every call in the report, by trade key.
 *
 * Asked once per report rather than once per position card: a report deleted since a trade was
 * recorded leaves that trade with nowhere to jump to, and the Portfolio has to know that about every
 * card it draws.
 */
val PerformanceReport.callIds: Set<String>
    get() = sessions.flatMapTo(mutableSetOf()) { session -> session.calls.map(ScoredCall::positionId) }
