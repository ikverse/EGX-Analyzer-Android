package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.IntradayBar
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import com.ikverse.egxanalyzer.model.callDate
import com.ikverse.egxanalyzer.model.riskReward
import com.ikverse.egxanalyzer.model.tradeWindow
import com.ikverse.egxanalyzer.model.round
import java.time.Instant
import java.time.LocalDate
import kotlin.math.sqrt

/**
 * Turns saved analyses plus stored sessions into a record for each source.
 *
 * Anything predating the first stored session is skipped rather than reported as unpriced, since a
 * call made before there was any price history says nothing about the source that made it.
 */
object PerformanceCalculator {

    /**
     * The first session this app judges anything on.
     *
     * Everything stored before it came from testing the extraction rather than from reading the
     * market, and a rate resting on it describes the test rather than the source. A constant and
     * not a setting: a floor that can be dragged is a floor that silently rewrites the record, and
     * every figure the app has ever shown would move with it.
     *
     * The rows themselves are left on disk. Filtering is reversible and costs nothing; deleting a
     * year of prices to answer a question about which calls count would not be.
     */
    val ANALYSIS_START: LocalDate = LocalDate.of(2026, 8, 3)

    fun report(
        analyses: List<SavedAnalysis>,
        pricesFrom: LocalDate?,
        windowSessions: Int,
        sessionsFor: (ticker: String, from: LocalDate) -> List<DailySession>,
        pricedTickers: Set<String> = emptySet(),
        /**
         * The sessions on which each stock's prices changed scale.
         *
         * A call whose window contains one is reported rather than judged: a split is not a channel
         * being wrong, and scoring one as a 50% collapse would take the rate away from whichever
         * source happened to call that stock.
         */
        priceBreaksFor: (ticker: String) -> Set<LocalDate> = { emptySet() },
        /**
         * Five-minute bars for a stock's session, where any were fetched.
         *
         * Only sessions a call could not order on daily figures ever have them, so this answers
         * empty for almost everything it is asked about.
         */
        intradayFor: (ticker: String, date: LocalDate) -> List<IntradayBar> = { _, _ -> emptyList() },
        /** Where each stock stands as of the last refresh, for the card to show alongside the call. */
        latestPrices: Map<String, LatestPrice> = emptyMap(),
    ): PerformanceReport {
        val window = Scoring.clampWindow(windowSessions)
        if (pricesFrom == null) return PerformanceReport(windowSessions = window)
        // Whichever starting line is later. Prices reaching further back than [ANALYSIS_START] are
        // still fetched and still stored - they are what a split check compares against - but no
        // call is judged on them.
        val since = maxOf(pricesFrom, ANALYSIS_START)

        val scoredRuns = markRepeats(runs(analyses, since, window)).map { run ->
            run.copy(
                calls = run.calls.map { call ->
                    // The call's own window rather than the report's. They are no longer all the
                    // same: a T+1 card described a trade held over one night and is judged over
                    // exactly that, while everything beside it takes the scoring setting.
                    val sessions = sessionsFor(call.ticker, call.openedOn).take(call.windowSessions)
                    val scored = Scoring.score(
                        sessions = sessions,
                        entryLow = call.entryLow,
                        entryHigh = call.entryHigh,
                        target1 = call.target1,
                        target2 = call.target2,
                        stopLoss = call.stopLoss,
                        windowSessions = call.windowSessions,
                        entrySessions = call.entrySessions,
                        priceBreaks = priceBreaksFor(call.ticker),
                        intradayFor = { date -> intradayFor(call.ticker, date) },
                    )
                    call.copy(
                        outcome = scored.outcome,
                        settledOn = scored.settledOn,
                        peakHigh = scored.peakHigh,
                        peakOn = scored.peakOn,
                        troughLow = scored.troughLow,
                        troughOn = scored.troughOn,
                        returnPct = scored.returnPct,
                        sessionsElapsed = scored.sessionsElapsed,
                        ambiguity = scored.ambiguity,
                        stoppedAfterPartial = scored.stoppedAfterPartial,
                        windowComplete = scored.windowComplete,
                        sessions = sessions,
                    )
                },
            )
        }
        val calls = scoredRuns.flatMap(RunCalls::calls)
        // Every rate below is measured on distinct calls. A re-posting is the same bet as the call
        // it repeats, and counting it again would weight a channel that prints the same table every
        // morning several times over one that posts when it has something new to say. The sessions
        // keep theirs: what a channel put out that day is what that day's record is for.
        val counted = calls.filter { it.repeatOf == null }

        val judged = counted.count { it.outcome.judged }
        val full = counted.count { it.outcome.isFullHit }
        val any = counted.count { it.outcome.reachedATarget }
        return PerformanceReport(
            windowSessions = window,
            // The starting line itself, not the earliest call behind it. Derived from the calls it
            // was unreachable: it was null exactly when there were no calls, which is the one case
            // the screen wanted it for - so the empty state could never say what it was waiting on.
            scoringSince = since,
            unpricedStocks = counted.filter { it.outcome == Outcome.UNPRICED }
                .map(ScoredCall::ticker)
                .distinct()
                .count { it !in pricedTickers },
            awaitingSessions = counted.count {
                it.outcome == Outcome.UNPRICED && it.ticker in pricedTickers
            },
            tracked = counted.size,
            judged = judged,
            fullHits = full,
            partialHits = any - full,
            // Only calls that could be judged count toward either rate, so a stock with no price
            // data or an entry that never traded neither helps nor hurts them.
            fullHitRate = judged.takeIf { it > 0 }?.let { (full.toDouble() / it * 100).round(1) },
            anyTargetRate = judged.takeIf { it > 0 }?.let { (any.toDouble() / it * 100).round(1) },
            byOutcome = counted.groupingBy(ScoredCall::outcome).eachCount(),
            channels = channelScores(calls),
            sessions = scoredRuns
                .map { run ->
                    ScoredSession(
                        targetDate = run.targetDate,
                        lastRunAt = run.completedAt,
                        model = run.model,
                        runCount = run.runCount,
                        channelsFromLatest = run.channelsFromLatest,
                        channelsTotal = run.channelsTotal,
                        calls = run.calls.sortedBy(ScoredCall::ticker),
                    )
                }
                .sortedByDescending { it.targetDate },
            // Every stock the record names, not only the ones with a call on screen: `refine` keeps
            // this untouched, so a filtered view still knows where its stocks stand.
            latestPrices = latestPrices,
        )
    }

    /**
     * The same report over a subset of its calls.
     *
     * Every figure is recomputed rather than filtered: a rate, a ranking or a session's counts that
     * still described calls the screen is hiding would be worse than no filter at all. Sessions
     * left with nothing to show drop out entirely.
     *
     * The scoring itself is untouched - each call keeps the outcome it was already given - so this
     * is only ever a narrower view of the same judgement.
     */
    fun refine(report: PerformanceReport, keep: (ScoredCall) -> Boolean): PerformanceReport {
        val sessions = report.sessions
            .map { session -> session.copy(calls = session.calls.filter(keep)) }
            .filter { it.calls.isNotEmpty() }
        val calls = sessions.flatMap(ScoredSession::calls)
        // The same exclusion the full report makes, for the same reason: a filtered view is a
        // narrower reading of one record, not a different way of counting it.
        val counted = calls.filter { it.repeatOf == null }
        val judged = counted.count { it.outcome.judged }
        val full = counted.count { it.outcome.isFullHit }
        val any = counted.count { it.outcome.reachedATarget }
        return report.copy(
            // Deliberately not recomputed. Every other figure here describes the calls on screen,
            // but the starting line is a property of the record, and a filter does not move it.
            tracked = counted.size,
            judged = judged,
            fullHits = full,
            partialHits = any - full,
            fullHitRate = judged.takeIf { it > 0 }?.let { (full.toDouble() / it * 100).round(1) },
            anyTargetRate = judged.takeIf { it > 0 }?.let { (any.toDouble() / it * 100).round(1) },
            byOutcome = counted.groupingBy(ScoredCall::outcome).eachCount(),
            channels = channelScores(calls),
            sessions = sessions,
        )
    }

    /** What one target session ends up holding, and how many runs it came from. */
    private data class RunCalls(
        val targetDate: LocalDate?,
        val completedAt: Instant,
        val model: String,
        val runCount: Int,
        val channelsFromLatest: Int,
        val channelsTotal: Int,
        val calls: List<ScoredCall>,
    )

    /**
     * For each session and each chat, the newest run that actually covered that chat.
     *
     * A run can only speak about what it read. Taking the newest run wholesale let an unrelated
     * re-run over one chat erase four other chats' calls for that day, quietly shrinking their
     * records; merging runs indiscriminately did the opposite and resurrected calls a re-run had
     * already dropped. Keyed per chat, a re-run replaces the chats it covered - a dropped stock
     * stays dropped - while chats it never looked at keep their last real reading.
     *
     * Coverage comes from the recorded selection. Analyses saved before that was stored fall back
     * to the chats that produced sources, which understates them, so an older run can only lose a
     * chat to a newer one that demonstrably read it.
     */
    private fun runs(
        analyses: List<SavedAnalysis>,
        since: LocalDate,
        /** The scoring setting, which every call that does not name its own deadline takes. */
        window: Int,
    ): List<RunCalls> = analyses
        .groupBy { it.result.recommendationTargetDate }
        .mapNotNull { (targetDate, forSession) ->
            val reads = forSession
                .sortedByDescending { it.result.completedAt }
                .map { saved -> saved to callsByChannel(saved, targetDate, since, window) }

            val claimed = mutableSetOf<Long?>()
            val chosen = linkedMapOf<SavedAnalysis, List<ScoredCall>>()
            for ((saved, byChannel) in reads) {
                val covered = coverage(saved)
                val mine = byChannel.filterKeys { it !in claimed && (covered.isEmpty() || it in covered) }
                if (mine.isEmpty()) continue
                claimed += mine.keys
                chosen[saved] = mine.values.flatten()
            }
            if (chosen.isEmpty()) return@mapNotNull null

            val newest = chosen.keys.first()
            RunCalls(
                targetDate = targetDate,
                completedAt = newest.result.completedAt,
                model = newest.model,
                runCount = chosen.size,
                channelsFromLatest = chosen.getValue(newest).map(ScoredCall::channelId).distinct().size,
                channelsTotal = claimed.size,
                calls = chosen.values.flatten(),
            )
        }

    /**
     * Marks a call that is the same call posted again, on the next session anyone looked at.
     *
     * Channels re-post a standing recommendation morning after morning until it resolves. Scored as
     * it stood, that is one bet counted once per posting: a channel running a daily table collects
     * four or five judged calls out of a single idea, and whichever way that idea went it moves the
     * channel's rate four or five times. A channel posting once when it has something to say gets
     * one. Comparing the two was comparing how often they post as much as how often they are right.
     *
     * Same source, same stock, and every level identical - a channel that moves its stop or lifts a
     * target has made a new call and is scored on it. Adjacency is measured against the sessions
     * that were actually analysed rather than the calendar: a gap means a session went by without
     * the call being made, which makes the next posting a fresh one rather than the same one still
     * standing. Repeats chain, so a call posted on four straight sessions points all three
     * re-postings back at the first.
     *
     * [ScoredCall.repeatOf] is a mark, not a deletion. The call stays in its session, which is the
     * record of what that channel published that day.
     */
    private fun markRepeats(runs: List<RunCalls>): List<RunCalls> {
        val everyCall = runs.flatMap(RunCalls::calls)
        val order = everyCall
            .map(ScoredCall::openedOn)
            .distinct()
            .sorted()
            .withIndex()
            .associate { (index, date) -> date to index }
        if (order.size < 2) return runs

        val firstPosting = HashMap<Pair<CallIdentity, LocalDate>, LocalDate>()
        // A lambda rather than a reference: an extension declared inside an object is a member
        // and an extension at once, and Kotlin will not take a callable reference to one.
        everyCall.groupBy { it.callIdentity }.forEach { (identity, postings) ->
            val dates = postings.map(ScoredCall::openedOn).distinct().sorted()
            var origin = dates.first()
            var previous = dates.first()
            dates.drop(1).forEach { date ->
                if (order.getValue(date) == order.getValue(previous) + 1) {
                    firstPosting[identity to date] = origin
                } else {
                    origin = date
                }
                previous = date
            }
        }
        if (firstPosting.isEmpty()) return runs

        return runs.map { run ->
            run.copy(
                calls = run.calls.map { call ->
                    call.copy(repeatOf = firstPosting[call.callIdentity to call.openedOn])
                },
            )
        }
    }

    /**
     * What makes two postings the same call.
     *
     * The channel is part of it for the same reason it is part of the parser's own collapse: two
     * sources making the same call are two calls, and one of them may be copying the other.
     */
    private data class CallIdentity(
        val channelId: Long?,
        val channel: String,
        val ticker: String,
        val entryLow: Double?,
        val entryHigh: Double?,
        val target1: Double?,
        val target2: Double?,
        val stopLoss: Double?,
    )

    private val ScoredCall.callIdentity: CallIdentity
        get() = CallIdentity(
            channelId, channel, ticker, entryLow, entryHigh, target1, target2, stopLoss,
        )

    /** The chats a run was pointed at, or the ones it heard from when that was not recorded. */
    private fun coverage(saved: SavedAnalysis): Set<Long?> =
        saved.result.selectedChannels.map { it.id as Long? }.toSet().ifEmpty {
            saved.result.sources.map { it.channelId }.toSet()
        }

    /**
     * One run's calls, grouped by the chat that made them.
     *
     * A stock described several times in one run collapses to the reading stating the most price
     * levels, since runs differ mainly in how much of a message the model managed to read.
     */
    private fun callsByChannel(
        saved: SavedAnalysis,
        targetDate: LocalDate?,
        since: LocalDate,
        window: Int,
    ): Map<Long?, List<ScoredCall>> {
        val traces = saved.result.sources.filter { it.messageId != null }
        val channelNames = traces.associate { it.messageId.toString() to it.channelName }
        val channelIds = traces.associate { it.messageId.toString() to it.channelId }

        val best = linkedMapOf<Pair<String, Long?>, Pair<Int, ScoredCall>>()
        saved.result.consolidated.forEach { stock ->
            stock.dataPoints.forEach { point ->
                val call = point.toCall(stock, channelNames, channelIds, targetDate, window)
                    ?: return@forEach
                if (call.openedOn < since) return@forEach
                val levels = listOf(
                    call.entryLow, call.entryHigh, call.target1, call.target2, call.stopLoss,
                ).count { it != null }
                val key = call.ticker to call.channelId
                val existing = best[key]
                if (existing == null || levels > existing.first) best[key] = levels to call
            }
        }
        return best.values.map { (_, call) -> call }.groupBy(ScoredCall::channelId)
    }

    private fun RecommendationDataPoint.toCall(
        stock: ConsolidatedRecommendation,
        channelNames: Map<String, String>,
        channelIds: Map<String, Long?>,
        targetDate: LocalDate?,
        window: Int,
    ): ScoredCall? {
        val ticker = Scoring.normalizeTicker(stock.stockCode)
        // The session the run was aimed at, which is the one the recommendation is for. Reading the
        // date off the extraction instead put a back-dated analysis under whatever day its sources
        // happened to be printed with, so scoring started from the wrong session. Shared with the
        // portfolio so a trade and the call it was taken on can never land on different dates.
        val openedOn = callDate(targetDate) ?: return null
        if (ticker.isBlank()) return null
        // Decided here, where the extraction is still in hand: the basis that makes a call T+1 is a
        // property of the card it was read off, and nothing downstream of this point ever sees it.
        val deadline = tradeWindow(window)
        return ScoredCall(
            ticker = ticker,
            companyEnglish = stock.stockNameEnglish,
            companyArabic = stock.stockNameArabic,
            channel = channelNames[sourceMessageId]?.trim()?.takeIf(String::isNotBlank) ?: UNKNOWN,
            channelId = channelIds[sourceMessageId],
            openedOn = openedOn,
            // Kept exactly as the source printed them. Rounding here reached the scorer, not just
            // the screen: on a stock trading at 0.243 a target of 0.275 became 0.28, and the run
            // then waited for a price nobody had called.
            entryLow = buyPriceLow ?: buyPrice,
            entryHigh = buyPriceHigh ?: buyPrice,
            target1 = target1,
            target2 = target2,
            stopLoss = stopLoss,
            outcome = Outcome.UNPRICED,
            settledOn = null,
            peakHigh = null,
            troughLow = null,
            returnPct = null,
            sessionsElapsed = 0,
            windowSessions = deadline.sessions,
            entrySessions = deadline.entrySessions,
        )
    }

    /**
     * Judged calls a channel needs before its rate is allowed to lead.
     *
     * Low enough that a few weeks of history ranks something, high enough that a single session
     * cannot. Below it the rate is still reported, exactly as measured - it simply stops sorting
     * above channels with a record behind them.
     */
    const val MINIMUM_JUDGED_TO_RANK = 5

    internal fun channelScores(calls: List<ScoredCall>): List<ChannelScore> = calls
        .groupBy(ScoredCall::channel)
        .map { (channel, posted) ->
            // A re-posting is the same bet as the call it repeats, so it is outside every figure
            // here. Counted rather than dropped silently: the card says how many were set aside,
            // because otherwise it reports fewer calls than the session list plainly shows.
            val rows = posted.filter { it.repeatOf == null }
            val judged = rows.filter { it.outcome.judged }
            val full = judged.filter { it.outcome.isFullHit }
            val any = judged.filter { it.outcome.reachedATarget }
            val returns = judged.mapNotNull(ScoredCall::returnPct)
            // Every call the channel made, not only the judged ones: this describes the levels it
            // prints, which it printed whatever the market then did about them.
            val riskRewards = rows.mapNotNull(ScoredCall::riskReward)
            ChannelScore(
                channel = channel,
                calls = rows.size,
                judged = judged.size,
                fullHits = full.size,
                partialHits = any.size - full.size,
                stopped = judged.count { it.outcome == Outcome.STOPPED },
                expired = judged.count { it.outcome == Outcome.EXPIRED },
                notTradable = rows.count {
                    it.outcome == Outcome.ENTRY_NOT_REACHED || it.outcome == Outcome.UNPRICED
                },
                fullHitRate = judged.size
                    .takeIf { it > 0 }
                    ?.let { (full.size.toDouble() / it * 100).round(1) },
                anyTargetRate = judged.size
                    .takeIf { it > 0 }
                    ?.let { (any.size.toDouble() / it * 100).round(1) },
                averageReturn = returns.takeIf(List<Double>::isNotEmpty)?.average()?.round(2),
                medianSessionsToHit = median(any.map(ScoredCall::sessionsElapsed)),
                discountedReturn = discountedReturn(returns),
                anyTargetRateFloor = wilsonLowerBound(any.size, judged.size),
                averageRiskReward = riskRewards.takeIf(List<Double>::isNotEmpty)
                    ?.average()
                    ?.round(2),
                repeats = posted.size - rows.size,
            )
        }
        // A record needs enough behind it to be a record, and being right often is not the same as
        // being worth following.
        //
        // The floor still decides who may lead: ranking on a rate alone put a channel with two
        // settled calls above one with seven, because one good session is 100%. Within it the order
        // is what a call was worth rather than how often it worked, because a hit rate is bought by
        // moving the target closer to the entry - reach for +2% with a -10% stop and nine calls in
        // ten will get there, and the tenth takes back more than the nine made. The rate is still
        // measured, still shown, and no longer what the list is sorted on.
        //
        // [ChannelScore.discountedReturn] rather than the average itself, so a short record cannot
        // out-rank a long one on a mean it has no evidence for. Where two records earn the same the
        // hit-rate floor separates them, and failing that the weight of evidence does.
        .sortedWith(
            compareByDescending<ChannelScore> { it.judged >= MINIMUM_JUDGED_TO_RANK }
                .thenByDescending { it.judged > 0 }
                .thenByDescending { it.discountedReturn ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { it.anyTargetRateFloor ?: 0.0 }
                .thenByDescending(ChannelScore::judged),
        )

    /**
     * The average return, pulled toward zero by how little is behind it.
     *
     * Half weight at [MINIMUM_JUDGED_TO_RANK] calls and closing on the full figure from there, so
     * six calls at +5% (2.73) sit below fifty at +4.5% (4.09) without either figure being
     * misreported. What the cards print is the average itself; this only decides their order.
     *
     * The textbook answer - the mean less a couple of standard errors - was tried first and is
     * wrong for this data. At ten calls the spread of the returns is worth several points and the
     * gap between two channels' averages is worth a fraction of one, so the bound ranks on variance
     * and almost nothing else: a source printing +2% targets against a -10% stop scored -1.55
     * against -3.80 for a source making more per call, which is precisely the ordering this figure
     * exists to overturn.
     */
    private fun discountedReturn(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val n = values.size
        return (values.average() * n / (n + MINIMUM_JUDGED_TO_RANK)).round(2)
    }

    /**
     * The Wilson 95% lower bound on a hit rate, as a percentage.
     *
     * The normal approximation everyone reaches for first breaks down exactly where this is needed:
     * at 6 of 6 it puts the floor at 100%, which is the claim being questioned. Wilson does not -
     * 6 of 6 floors at 61%, and 40 of 50 floors at 67%, which is the honest ordering of the two.
     */
    private fun wilsonLowerBound(hits: Int, of: Int): Double? {
        if (of <= 0) return null
        val p = hits.toDouble() / of
        val z2 = Z_95 * Z_95
        val centre = p + z2 / (2 * of)
        val spread = Z_95 * sqrt(p * (1 - p) / of + z2 / (4.0 * of * of))
        return (((centre - spread) / (1 + z2 / of)) * 100).coerceAtLeast(0.0).round(1)
    }

    /** The 95% two-sided normal quantile, used for both bounds so the two read on one scale. */
    private const val Z_95 = 1.96

    private fun median(values: List<Int>): Double? {
        if (values.isEmpty()) return null
        val ordered = values.sorted()
        val middle = ordered.size / 2
        return if (ordered.size % 2 == 1) {
            ordered[middle].toDouble()
        } else {
            (ordered[middle - 1] + ordered[middle]) / 2.0
        }
    }

    private const val UNKNOWN = "Unknown"
}

/** Every ticker named by any saved analysis; the rest cannot be scored, so pricing them is waste. */
fun List<SavedAnalysis>.recommendedTickers(): Set<String> = flatMap { saved ->
    saved.result.consolidated.map { Scoring.normalizeTicker(it.stockCode) } +
        saved.result.recommendations.map { Scoring.normalizeTicker(it.ticker) }
}.filter(String::isNotBlank).toSet()
