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
    /** Set only for [Outcome.AMBIGUOUS], saying which pair of events could not be ordered. */
    val ambiguity: Ambiguity? = null,
    /** The first target was banked and the stop was reached afterwards. */
    val stoppedAfterPartial: Boolean = false,
    /** False while the window is still running, so a partial hit may still become a full one. */
    val windowComplete: Boolean = false,
    /** The sessions this call was judged on, so the card can show them without another query. */
    val sessions: List<DailySession> = emptyList(),
)

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
    val averageReturn: Double?,
    val medianSessionsToHit: Double?,
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
