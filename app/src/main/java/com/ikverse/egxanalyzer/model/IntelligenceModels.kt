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
     * The horizon this call was judged against.
     *
     * [Scoring.JUDGING_HORIZON_SESSIONS] for everything but a T+1 card, which is judged over its
     * own two sessions. Kept on the call rather than only on the report because those two numbers
     * differ, and a screen reading one figure for the whole page would describe every T+1 call
     * wrongly - on exactly the calls whose deadline is the point.
     */
    val windowSessions: Int = Scoring.JUDGING_HORIZON_SESSIONS,
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
    /**
     * The session that stop broke on.
     *
     * Not [settledOn], which is the session the target was reached - that is what the call is
     * scored on, and the card used to name it as the day the stop broke because it was the only
     * date it had.
     */
    val stoppedOn: LocalDate? = null,
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
    /**
     * The analysis this call was read out of, by the identity that travels between devices.
     *
     * Carried for one reason: an Ask AI opinion is stored against it, and deleting the report has to
     * take the opinion with it. Everything else about a call is derived from prices and would
     * survive the report going; an opinion about a card nobody can see any more is an orphan.
     *
     * Null only for a call built without one, which is every test fixture and nothing on a device.
     */
    val requestId: String? = null,
    /**
     * What looks wrong about the levels this call was read out of a screenshot with.
     *
     * A **mark and never a subtraction**: every rate counts this call exactly as it did before, and
     * the card gains a chip saying what does not add up. See [CallSanity], which explains why a
     * heuristic is allowed to caption a card and not to move a published figure.
     *
     * Derived on every recompute like the outcome, never stored: it is a reading of the levels, and
     * the levels are already on disk.
     */
    val faults: Set<CallFault> = emptySet(),
    /**
     * How many **other** sources called this stock for this same session.
     *
     * Detected since the two cards learned to press through to each other - they share a
     * [positionId] - and counted by nothing until now. It is deliberately labelled crowding rather
     * than confirmation: several channels reading the same chart on the same morning is one idea
     * going round, not three independent readings of it, and whether it is worth anything is a
     * question the record answers rather than a claim this field makes.
     */
    val alsoCalledBy: Int = 0,
    /**
     * How many times the source re-posted this call on a later analysed session.
     *
     * The other side of [repeatOf], which is a mark on the later postings so no rate counts them
     * twice. Read from the first posting it is a figure in its own right: a channel that printed
     * the same call every morning for a week kept it standing, and one that posted once and moved
     * on did not. Zero on a call that was never repeated, and on the repeats themselves.
     */
    val repostings: Int = 0,
    /**
     * Which of the cheap local signals this call carries, and the whole basis of the shortlist.
     *
     * Derived from figures the app already holds, costs nothing, and predicts nothing - see
     * [CallSignal]. Kept on the call so an order and a card read the same set rather than each
     * working it out from a different set of inputs.
     */
    val signals: Set<CallSignal> = emptySet(),
) {
    /**
     * The channel printed this as a T+1 trade: buy on this session, out on the next.
     *
     * Read off the two windows rather than carried as a flag of its own, because that is what makes
     * one: a T+1 card is the only call whose entry may trade in fewer sessions than it is judged
     * over. It was already the test three places on the Insights screen made by hand, spelled out
     * as `entrySessions < windowSessions`, which is the same question asked in a way that has to be
     * decoded before it can be read.
     */
    val isTPlusOne: Boolean get() = entrySessions < windowSessions
}

/**
 * A cheap local reason a call might be worth a closer look.
 *
 * The point is **which of twenty cards to spend a paid request on**, not what the market will do.
 * Every one of these is a fact the app already holds and can state in words, which is the test each
 * had to pass: a signal that cannot be explained on the card is a score wearing a statistic's
 * clothes, and this app does not do that anywhere else.
 *
 * They are counted, never weighted. Weights would imply the four had been calibrated against
 * outcomes, and they have not been - a count says exactly what it is, which is how many separate
 * things happen to line up on one card.
 */
enum class CallSignal(val label: String) {
    /** The source has a real record and it is a positive one. */
    STRONG_SOURCE("strong source"),

    /** The levels the channel printed offer more than they risk, by a clear margin. */
    GOOD_RISK_REWARD("good risk to reward"),

    /** Calls on this stock have paid, across every source that has made one. */
    STOCK_DELIVERS("stock delivers"),

    /** The price is inside the buy zone now, so the call is actually takeable today. */
    PRICE_IN_BAND("price in the band"),
    ;

    companion object {
        /** Above this, a source's or a stock's average is treated as a positive record. */
        const val POSITIVE_RETURN = 0.0

        /**
         * The risk-to-reward a call has to offer before it counts for anything.
         *
         * Two to one. Below it the figure is not a signal - a source reaching a target 1.2 times
         * its risk needs to be right most of the time, which is the thing the other signals are
         * about and not this one.
         */
        const val GOOD_RISK_REWARD_RATIO = 2.0
    }
}

/**
 * The figures any group of scored calls yields, whatever the group is about.
 *
 * Extracted so a source's record and a stock's are computed by **one** piece of arithmetic rather
 * than two that agree today. The rules folded in here - repeats excluded from every rate, risk to
 * reward measured over every call rather than only the judged ones, the median rather than the mean
 * for how long a call took - were argued out once for channels and are just as true of stocks.
 */
data class CallTally(
    val calls: Int,
    val judged: Int,
    val fullHits: Int,
    val partialHits: Int,
    val stopped: Int,
    val expired: Int,
    val notTradable: Int,
    val fullHitRate: Double?,
    val anyTargetRate: Double?,
    val averageReturn: Double?,
    val medianSessionsToHit: Double?,
    val medianSessionsToStop: Double?,
    val discountedReturn: Double?,
    val anyTargetRateFloor: Double?,
    val averageRiskReward: Double?,
    val repeats: Int,
)

/**
 * What happens when one stock gets recommended, across every source that has recommended it.
 *
 * The only record of its kind anywhere: the app holds every call made on every stock and, until
 * now, used that fact in exactly one place - a list inside the Ask AI prompt. A channel's record
 * says whether to read the channel; this says whether the market has ever done what anybody
 * printed about this particular stock, which is a different question and one no rate on the
 * Insights page could answer.
 *
 * Same discipline as [ChannelScore] and by construction, since both are built from one [CallTally]:
 * repeats excluded, the `MINIMUM_JUDGED_TO_RANK` floor deciding what may lead, figures below it
 * reported exactly as measured and simply not ranked.
 */
data class StockScore(
    val ticker: String,
    val companyEnglish: String?,
    val companyArabic: String?,
    /**
     * How many distinct sources have called it.
     *
     * The figure that separates a stock the whole channel list is pushing from one a single source
     * likes. Counted over every call, repeats included: a source that named it is a source that
     * named it, however many mornings it said so.
     */
    val sources: Int,
    val tally: CallTally,
)

/**
 * One subset of the record set beside the rest of it.
 *
 * Built for the two questions the app could always have answered and never asked - whether calls
 * several sources agree on do better, and whether calls a source keeps re-posting do - and shaped
 * so a third costs a line rather than a type.
 *
 * **It states two figures and never a verdict.** At the ten to thirty judged calls each side of one
 * of these actually has, the spread of stock returns swamps the gap between two means; saying
 * "consensus calls do better" would be reading noise out loud. The counts are printed beside the
 * figures for exactly that reason, and [stateable] is what keeps a split off the screen entirely
 * until both sides have enough behind them to be worth a reader's time.
 */
data class RecordSplit(
    /** What the matching calls have in common, as a heading. */
    val subject: String,
    /** What the split is asking, in a sentence, for the reader who wants to know why it is here. */
    val detail: String,
    val matching: CallTally,
    val rest: CallTally,
) {
    /**
     * Whether both sides carry enough judged calls to be worth printing at all.
     *
     * Higher than the ranking floor on purpose. Ranking picks one source out of several and is
     * wrong in a recoverable way; a split makes a claim about a *difference*, and a difference
     * needs more behind it than an ordering does.
     */
    val stateable: Boolean
        get() = matching.judged >= MINIMUM_JUDGED_TO_COMPARE &&
            rest.judged >= MINIMUM_JUDGED_TO_COMPARE

    companion object {
        /** Judged calls needed on **each** side before a split is shown. */
        const val MINIMUM_JUDGED_TO_COMPARE = 10
    }
}

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
 * How the calls inside one session card are laid out.
 *
 * A **view**, never the record: every option orders the same calls and none of them hides one, so
 * this can be changed freely without any figure on the page moving. `PerformanceCalculator` keeps
 * ordering by ticker, which stays the default here - that is the canonical order of the record, and
 * anything reading a report without a screen gets it.
 *
 * It exists because alphabetical is the one order that carries no information. A fresh report is a
 * grid of twenty cards, and the two worth reading were placed by the first letter of the stock.
 *
 * The comparator takes the channel record rather than living on [ScoredCall], because the figure
 * one option sorts on belongs to the source and not to the call. Nulls sort last in every option: a
 * call whose source has no record yet, or whose levels contradict each other, has not earned the
 * top of the list by being unmeasurable.
 */
enum class CallOrder(val label: String) {
    /** The record's own order, and the one the calculator produces. */
    TICKER("Ticker"),

    /**
     * Best source first, on the figure the ranking itself is ordered by.
     *
     * [ChannelScore.discountedReturn] and not the raw average, so a card and the channel ranking can
     * never disagree about which of two sources is ahead - and so three lucky calls do not lead a
     * session the way they are already stopped from leading the ranking.
     */
    SOURCE("Source record, best first"),

    /** What the call offers against what it risks, from the levels the channel printed. */
    RISK_REWARD("Risk to reward, best first"),

    /**
     * Most local signals first, which is what a paid question is best aimed at.
     *
     * The signals are counted and not weighted - see [CallSignal] - so this puts the cards where
     * the most separate things happen to line up at the top. It ranks attention and predicts
     * nothing, and the card names its own signals so the order can be checked by eye.
     */
    WORTH_ASKING("Worth a closer look"),
    ;

    fun sort(calls: List<ScoredCall>, scoreFor: (String) -> ChannelScore?): List<ScoredCall> =
        when (this) {
            TICKER -> calls.sortedBy(ScoredCall::ticker)
            SOURCE -> calls.sortedWith(
                compareByDescending<ScoredCall> {
                    scoreFor(it.channel)?.discountedReturn ?: Double.NEGATIVE_INFINITY
                }.thenBy(ScoredCall::ticker),
            )
            RISK_REWARD -> calls.sortedWith(
                compareByDescending<ScoredCall> { it.riskReward ?: Double.NEGATIVE_INFINITY }
                    .thenBy(ScoredCall::ticker),
            )
            // Ties broken by risk to reward before the ticker: at four signals a great many cards
            // will carry the same count, and the alphabet is the order this whole enum exists to
            // stop deciding which of them a reader sees first.
            WORTH_ASKING -> calls.sortedWith(
                compareByDescending<ScoredCall> { it.signals.size }
                    .thenByDescending { it.riskReward ?: Double.NEGATIVE_INFINITY }
                    .thenBy(ScoredCall::ticker),
            )
        }
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
    /**
     * How long the calls that worked took about it, in trading sessions.
     *
     * The median rather than the mean: one call that took twenty-eight sessions to come good would
     * drag an average away from what the source typically does, and the typical is the question.
     * Counted to the first target reached, since that is the point at which the reader was in
     * profit and could act.
     */
    val medianSessionsToHit: Double?,
    /**
     * How long the calls that failed took to fail, in trading sessions.
     *
     * The other half of the same question, and not a figure that reads well alone: a source whose
     * stops come in two sessions while its targets take fifteen is asking the reader to carry a
     * loss quickly and a gain slowly, which no hit rate on the card would show. Null where nothing
     * has been stopped out.
     */
    val medianSessionsToStop: Double? = null,
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
    /**
     * What happens when each stock gets recommended, whoever recommended it.
     *
     * Recomputed by `refine` beside the channels, so a filtered view never quotes a stock's whole
     * record beside rates that have been narrowed.
     */
    val stocks: List<StockScore> = emptyList(),
    /**
     * Subsets of the record set beside the rest of it, for the questions no single rate answers.
     *
     * Always built, and each one shown only where [RecordSplit.stateable] - so the screen has them
     * ready the day there is enough behind them, and says nothing until then.
     */
    val splits: List<RecordSplit> = emptyList(),
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
