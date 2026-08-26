package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.CallSanity
import com.ikverse.egxanalyzer.model.CallShortlist
import com.ikverse.egxanalyzer.model.CallTally
import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.IntradayBar
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.RecordSplit
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import com.ikverse.egxanalyzer.model.SettledCall
import com.ikverse.egxanalyzer.model.isFinal
import com.ikverse.egxanalyzer.model.settledKey
import com.ikverse.egxanalyzer.model.StockScore
import com.ikverse.egxanalyzer.model.callDate
import com.ikverse.egxanalyzer.model.judgingWindow
import com.ikverse.egxanalyzer.model.riskReward
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
        /**
         * The calls the market has already finished with, by [settledKey].
         *
         * A map read once for the whole recompute rather than a lookup per call, like the breaks
         * and the bars: a settled call is looked up on every recompute, and a query each would be
         * the cost this exists to remove.
         */
        settled: Map<String, SettledCall> = emptyMap(),
        /**
         * Handed the calls that have just settled for the first time, so they can be written down.
         *
         * A callback rather than a return value on the report: settling is a side effect of scoring
         * and nothing on screen reads it, and threading it through `PerformanceReport` would put a
         * write instruction inside the thing every screen draws from.
         */
        onSettled: (List<SettledCall>) -> Unit = {},
    ): PerformanceReport {
        if (pricesFrom == null) return PerformanceReport()
        // Whichever starting line is later. Prices reaching further back than [ANALYSIS_START] are
        // still fetched and still stored - they are what a split check compares against - but no
        // call is judged on them.
        val since = maxOf(pricesFrom, ANALYSIS_START)

        // Filled while the runs are scored and handed over once. A call reaching a final verdict is
        // a rare event on any one recompute - most closed calls were already closed last time.
        val justSettled = mutableListOf<SettledCall>()

        val scoredRuns = markRepeats(runs(analyses, since)).map { run ->
            run.copy(
                calls = run.calls.map { call ->
                    // Already finished with. The market cannot take back a second target, a broken
                    // stop, or a first target given back to the stop, so replaying thirty sessions
                    // of prices here is work with one possible answer - and the query for those
                    // sessions is the expensive half of it. The sessions come back with the verdict
                    // rather than from the price table, so the card draws exactly what was judged.
                    settled[call.settledKey()]?.let { return@map call.restoredFrom(it) }
                    // The call's own horizon: a T+1 card described a trade held over one night and
                    // is judged over exactly that, while everything beside it runs to the general
                    // horizon and settles whenever the market settles it.
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
                        stoppedOn = scored.stoppedOn,
                        windowComplete = scored.windowComplete,
                        lastCloseAfterPartial = scored.lastCloseAfterPartial,
                        sessions = sessions,
                        // Checked against the call's own session, which is the first one it was
                        // replayed on. Nothing here changes what was scored above - a fault
                        // captions the card and moves no figure. See CallSanity.
                        faults = CallSanity.faults(
                            entryLow = call.entryLow,
                            entryHigh = call.entryHigh,
                            target1 = call.target1,
                            target2 = call.target2,
                            stopLoss = call.stopLoss,
                            session = sessions.firstOrNull(),
                        ),
                    ).also { judged ->
                        // Written down the first time and only the first time. The faults, the
                        // crowding and the shortlist signals are deliberately not part of it: they
                        // describe this call against the calls around it, and those change as the
                        // record grows. What is frozen is what the market did.
                        if (judged.outcome.isFinal(judged.stoppedAfterPartial)) {
                            justSettled += judged.asSettled()
                        }
                    }
                },
            )
        }
        onSettled(justSettled)
        // Built once from the scored calls, then handed to the second pass that needs them - the
        // signals on a card read the same records the ranking below is built from, or a card would
        // call a source strong that the list beneath it declines to rank.
        val scored = scoredRuns.flatMap(RunCalls::calls)
        val enriched = enrich(
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
            channels = channelScores(scored),
            stocks = stockScores(scored),
            latestPrices = latestPrices,
        )
        val calls = enriched.flatMap(ScoredSession::calls)
        // Every rate below is measured on distinct calls. A re-posting is the same bet as the call
        // it repeats, and counting it again would weight a channel that prints the same table every
        // morning several times over one that posts when it has something new to say. The sessions
        // keep theirs: what a channel put out that day is what that day's record is for.
        val counted = calls.filter { it.repeatOf == null }

        val judged = counted.count { it.outcome.judged }
        val full = counted.count { it.outcome.isFullHit }
        val any = counted.count { it.outcome.reachedATarget }
        return PerformanceReport(
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
            stocks = stockScores(calls),
            splits = splits(calls),
            sessions = enriched,
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
        val filtered = report.sessions
            .map { session -> session.copy(calls = session.calls.filter(keep)) }
            .filter { it.calls.isNotEmpty() }
        val kept = filtered.flatMap(ScoredSession::calls)
        // Re-enriched, not carried over. Crowding, re-postings and the signals all describe a call
        // against the calls around it, and a filter changes which calls those are - a card left
        // reading "2 other sources" beside a page that is showing one of them would be the screen
        // disagreeing with itself. The narrower reading is the honest one here: the figures on
        // screen describe the calls on screen.
        val sessions = enrich(
            sessions = filtered,
            channels = channelScores(kept),
            stocks = stockScores(kept),
            latestPrices = report.latestPrices,
        )
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
            stocks = stockScores(calls),
            splits = splits(calls),
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
    ): List<RunCalls> = analyses
        .groupBy { it.result.recommendationTargetDate }
        .mapNotNull { (targetDate, forSession) ->
            val reads = forSession
                .sortedByDescending { it.result.completedAt }
                .map { saved -> saved to callsByChannel(saved, targetDate, since) }

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
    ): Map<Long?, List<ScoredCall>> {
        val traces = saved.result.sources.filter { it.messageId != null }
        val channelNames = traces.associate { it.messageId.toString() to it.channelName }
        val channelIds = traces.associate { it.messageId.toString() to it.channelId }

        val best = linkedMapOf<Pair<String, Long?>, Pair<Int, ScoredCall>>()
        saved.result.consolidated.forEach { stock ->
            stock.dataPoints.forEach { point ->
                val call = point.toCall(
                    stock, channelNames, channelIds, targetDate, saved.result.requestId,
                )
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
        /** The report this was read out of, so an opinion stored against it can be deleted with it. */
        requestId: String,
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
        val deadline = judgingWindow()
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
            requestId = requestId,
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

    /**
     * Every figure a group of calls yields, computed once for whatever the group is about.
     *
     * A source's record and a stock's ask the same arithmetic of different groupings, and two
     * copies of it would be two copies that agree until one of them is touched. Every rule folded
     * in here was argued out for channels and is just as true of stocks: repeats out of every rate,
     * risk to reward over every call rather than only the judged ones, medians rather than means
     * for how long a call took.
     */
    internal fun tally(posted: List<ScoredCall>): CallTally {
        // A re-posting is the same bet as the call it repeats, so it is outside every figure here.
        // Counted rather than dropped silently: the card says how many were set aside, because
        // otherwise it reports fewer calls than the session list plainly shows.
        val rows = posted.filter { it.repeatOf == null }
        val judged = rows.filter { it.outcome.judged }
        val full = judged.filter { it.outcome.isFullHit }
        val any = judged.filter { it.outcome.reachedATarget }
        val returns = judged.mapNotNull(ScoredCall::returnPct)
        // Every call made, not only the judged ones: this describes the levels that were printed,
        // which were printed whatever the market then did about them.
        val riskRewards = rows.mapNotNull(ScoredCall::riskReward)
        // Bank it all at target 1, or sell half and let the rest run - the same calls priced under
        // both rules. Narrower than `judged` by construction; see `policyPairs`.
        val policy = policyPairs(judged)
        return CallTally(
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
            // How long being right takes, and how long being wrong takes. Worth having only now
            // that a call runs to a settlement rather than to a deadline: under a ten-session
            // window every one of these figures was capped at ten, and a source that reliably
            // comes good in a fortnight was indistinguishable from one whose calls never do.
            medianSessionsToHit = median(any.map(ScoredCall::sessionsElapsed)),
            // Only the calls the stop took out on their own. A partial hit that fell back to the
            // stop settled on its target and is counted in the figure above; putting it in both
            // would let one call describe how fast a source is right and how fast it is wrong at
            // once, and its `sessionsElapsed` counts to the target either way.
            medianSessionsToStop = median(
                judged.filter { it.outcome == Outcome.STOPPED }
                    .map(ScoredCall::sessionsElapsed),
            ),
            discountedReturn = discountedReturn(returns),
            anyTargetRateFloor = wilsonLowerBound(any.size, judged.size),
            averageRiskReward = riskRewards.takeIf(List<Double>::isNotEmpty)
                ?.average()
                ?.round(2),
            repeats = posted.size - rows.size,
            // Conditioned on having reached target 1, which is the whole point of it: over every
            // judged call this is `fullHitRate`, and that answers a different question.
            continuationRate = any.size
                .takeIf { it > 0 }
                ?.let { (full.size.toDouble() / it * 100).round(1) },
            continuationRateFloor = wilsonLowerBound(full.size, any.size),
            sellAtTarget1Return = policy.map(PolicyPair::sellAll).averageOrNull(),
            splitReturn = policy.map(PolicyPair::split).averageOrNull(),
            policyCalls = policy.size,
        )
    }

    /** One call priced twice: once under each rule, so the pair can be differenced. */
    private data class PolicyPair(val sellAll: Double, val split: Double)

    /**
     * Both policies priced over the calls where the decision is real and finished.
     *
     * Three exclusions, and each of them would otherwise bend the comparison toward "it makes no
     * difference":
     *
     * - **Only one target printed.** `Scoring` gives such a call `partialTarget = null`, so it can
     *   only ever be a full hit. There is no second target to hold for, so there is no decision, and
     *   both policies would price it identically.
     * - **A partial hit still running.** Its un-sold half has not finished, so holding has no
     *   result yet. Marking it at today's close would put a price that is not an outcome into an
     *   average of outcomes.
     * - **No priceable ending.** A call whose levels or last close leave either policy unpriceable
     *   is dropped from *both*, never from one.
     *
     * A call that stopped or expired without ever reaching target 1 stays in, priced the same under
     * both: nothing was sold at target 1 because target 1 never came. Dropping those would turn each
     * figure into a return conditional on winning, which is not what following a source pays.
     */
    private fun policyPairs(judged: List<ScoredCall>): List<PolicyPair> = judged.mapNotNull { call ->
        val t1 = call.target1
        val t2 = call.target2
        if (t1 == null || t2 == null) return@mapNotNull null
        val atTarget1 = call.returnTo(t1) ?: return@mapNotNull null
        when (call.outcome) {
            Outcome.FULL_HIT -> call.returnTo(t2)?.let { PolicyPair(atTarget1, (atTarget1 + it) / 2) }
            Outcome.PARTIAL_HIT -> {
                // Where the half that was left to run actually ended: the stop took it back, or the
                // window closed with it still short of target 2. A partial that is neither is still
                // running and has no ending to price.
                val rest = if (call.stoppedAfterPartial) call.stopLoss else call.lastCloseAfterPartial
                rest?.let { call.returnTo(it) }
                    ?.let { PolicyPair(atTarget1, (atTarget1 + it) / 2) }
            }
            // Target 1 never came, so neither rule ever sold anything and both end where the call
            // did. `returnPct` is already measured at the stop and at the last close respectively.
            Outcome.STOPPED, Outcome.EXPIRED -> call.returnPct?.let { PolicyPair(it, it) }
            else -> null
        }
    }

    /**
     * A price as a return from the middle of the entry band.
     *
     * The same base `Scoring` measures every return from, so a policy figure and the scored return
     * beside it differ in where they end and in nothing else.
     */
    private fun ScoredCall.returnTo(exit: Double): Double? {
        val low = entryLow
        val high = entryHigh
        val entry = if (low != null && high != null) (low + high) / 2 else low ?: high ?: return null
        if (entry == 0.0) return null
        return ((exit - entry) / entry * 100).round(2)
    }

    private fun List<Double>.averageOrNull(): Double? =
        takeIf(List<Double>::isNotEmpty)?.average()?.round(2)

    internal fun channelScores(calls: List<ScoredCall>): List<ChannelScore> = calls
        .groupBy(ScoredCall::channel)
        .map { (channel, posted) ->
            val figures = tally(posted)
            ChannelScore(
                channel = channel,
                calls = figures.calls,
                judged = figures.judged,
                fullHits = figures.fullHits,
                partialHits = figures.partialHits,
                stopped = figures.stopped,
                expired = figures.expired,
                notTradable = figures.notTradable,
                fullHitRate = figures.fullHitRate,
                anyTargetRate = figures.anyTargetRate,
                averageReturn = figures.averageReturn,
                medianSessionsToHit = figures.medianSessionsToHit,
                medianSessionsToStop = figures.medianSessionsToStop,
                discountedReturn = figures.discountedReturn,
                anyTargetRateFloor = figures.anyTargetRateFloor,
                averageRiskReward = figures.averageRiskReward,
                repeats = figures.repeats,
                continuationRate = figures.continuationRate,
                continuationRateFloor = figures.continuationRateFloor,
                sellAtTarget1Return = figures.sellAtTarget1Return,
                splitReturn = figures.splitReturn,
                policyCalls = figures.policyCalls,
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
     * What happens when each stock gets recommended, across every source that has recommended it.
     *
     * Grouped on the **normalized** ticker, or `COMI` and `COMI.CA` become two records of one
     * stock. Ordered exactly as the channels are, and for the same reasons: the floor decides what
     * may lead, and inside it what a call was worth decides the order rather than how often it
     * worked.
     */
    internal fun stockScores(calls: List<ScoredCall>): List<StockScore> = calls
        .groupBy { Scoring.normalizeTicker(it.ticker) }
        .map { (ticker, onStock) ->
            StockScore(
                ticker = ticker,
                // The first name anything actually carries. A call read off a card that named no
                // company leaves these null, and one that did fills them for the whole group.
                companyEnglish = onStock.firstNotNullOfOrNull { it.companyEnglish },
                companyArabic = onStock.firstNotNullOfOrNull { it.companyArabic },
                // Over every posting, repeats included: a source that named this stock named it,
                // however many mornings it went on saying so.
                sources = onStock.map(ScoredCall::channel).distinct().size,
                tally = tally(onStock),
            )
        }
        .sortedWith(
            compareByDescending<StockScore> { it.tally.judged >= MINIMUM_JUDGED_TO_RANK }
                .thenByDescending { it.tally.judged > 0 }
                .thenByDescending { it.tally.discountedReturn ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { it.tally.anyTargetRateFloor ?: 0.0 }
                .thenBy(StockScore::ticker),
        )

    /**
     * The two questions the record could always have answered and nobody ever asked it.
     *
     * Both rest on something the app already detects and then throws away: that several sources
     * named one stock for one session, and that a source kept re-posting a call rather than saying
     * it once. Neither is stated as a verdict - see [RecordSplit], which prints two figures with
     * their counts and lets the reader see how little is behind them.
     */
    internal fun splits(calls: List<ScoredCall>): List<RecordSplit> {
        val (crowded, alone) = calls.partition { it.alsoCalledBy > 0 }
        val (standing, once) = calls.partition { it.repostings > 0 }
        return listOf(
            RecordSplit(
                subject = "Called by more than one source",
                detail = "Whether a stock several channels named for the same session did better " +
                    "than one only a single channel named. Crowding, not confirmation: several " +
                    "sources reading one chart on one morning is one idea going round.",
                matching = tally(crowded),
                rest = tally(alone),
            ),
            RecordSplit(
                subject = "Kept standing by its source",
                detail = "Whether a call its channel re-posted on later sessions did better than " +
                    "one posted once and dropped. The re-postings themselves are counted in " +
                    "neither figure - they are the same bet as the call they repeat.",
                matching = tally(standing),
                rest = tally(once),
            ),
        )
    }

    /**
     * A second pass over the scored calls, for the figures that need every other call to exist.
     *
     * Crowding is a fact about a stock and a session rather than about one card, the re-postings of
     * a call are only knowable once every posting has been read, and the signals need the source's
     * and the stock's whole record. None of it can be worked out inside the loop that scores a
     * call, and all of it has to run again on a filtered view - so it is one function, called by
     * both [report] and [refine], rather than two that would drift.
     */
    private fun enrich(
        sessions: List<ScoredSession>,
        channels: List<ChannelScore>,
        stocks: List<StockScore>,
        latestPrices: Map<String, LatestPrice>,
    ): List<ScoredSession> {
        val calls = sessions.flatMap(ScoredSession::calls)
        // How many distinct sources named each stock for each session. Keyed on the normalized
        // ticker so two spellings of one stock are one crowd.
        val crowd = calls
            .groupBy { Scoring.normalizeTicker(it.ticker) to it.openedOn }
            .mapValues { (_, forSession) -> forSession.map(ScoredCall::channel).distinct().size }
        // Re-postings, counted against the first posting they point back at.
        val repostings = calls
            .filter { it.repeatOf != null }
            .groupingBy { Triple(Scoring.normalizeTicker(it.ticker), it.channel, it.repeatOf) }
            .eachCount()
        val byChannel = channels.associateBy(ChannelScore::channel)
        val byStock = stocks.associateBy(StockScore::ticker)

        return sessions.map { session ->
            session.copy(
                calls = session.calls.map { call ->
                    val ticker = Scoring.normalizeTicker(call.ticker)
                    call.copy(
                        // Other sources, not all of them: a card saying "1 source" about itself is
                        // a card counting itself as company.
                        alsoCalledBy = (crowd[ticker to call.openedOn] ?: 1) - 1,
                        // Zero on a repeat itself. The figure belongs to the call that was kept
                        // standing, and a repeat is not a call that was kept standing, it is the
                        // standing.
                        repostings = if (call.repeatOf != null) {
                            0
                        } else {
                            repostings[Triple(ticker, call.channel, call.openedOn)] ?: 0
                        },
                        signals = CallShortlist.signals(
                            call = call,
                            source = byChannel[call.channel],
                            stock = byStock[ticker],
                            latest = latestPrices[call.ticker],
                        ),
                    )
                },
            )
        }
    }

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

    /**
     * A call wearing a verdict that was reached once and will not be reached again.
     *
     * Everything about *what the market did* comes from the frozen row; everything about how this
     * call sits among the calls around it is left to [enrich], which runs over settled and running
     * calls alike. `ambiguity` is null by construction - an unordered session is not a settlement,
     * so it is never one of the verdicts frozen here.
     */
    private fun ScoredCall.restoredFrom(settled: SettledCall): ScoredCall = copy(
        outcome = settled.outcome,
        settledOn = settled.settledOn,
        peakHigh = settled.peakHigh,
        peakOn = settled.peakOn,
        troughLow = settled.troughLow,
        troughOn = settled.troughOn,
        returnPct = settled.returnPct,
        sessionsElapsed = settled.sessionsElapsed,
        ambiguity = null,
        stoppedAfterPartial = settled.stoppedAfterPartial,
        stoppedOn = settled.stoppedOn,
        windowComplete = settled.windowComplete,
        // Null by construction rather than by omission, and so not stored: `isFinal` freezes a
        // partial hit **only** once the stop has taken it back, and there the stop is where the
        // un-sold half ended. The case this field exists for - a window that closed short of the
        // second target with the call still alive - is never frozen and is re-derived every run.
        lastCloseAfterPartial = null,
        sessions = settled.sessions,
        // Re-derived rather than frozen: a fault is a reading of the levels against the call's own
        // first session, both of which are in hand here, and it captions the card without moving a
        // figure. Storing it would freeze a heuristic beside a measurement.
        faults = CallSanity.faults(
            entryLow = entryLow,
            entryHigh = entryHigh,
            target1 = target1,
            target2 = target2,
            stopLoss = stopLoss,
            session = settled.sessions.firstOrNull(),
        ),
    )

    /** The same call as the row that will be written for it. */
    private fun ScoredCall.asSettled(): SettledCall = SettledCall(
        key = settledKey(),
        ticker = ticker,
        outcome = outcome,
        settledOn = settledOn,
        stoppedOn = stoppedOn,
        stoppedAfterPartial = stoppedAfterPartial,
        windowComplete = windowComplete,
        peakHigh = peakHigh,
        peakOn = peakOn,
        troughLow = troughLow,
        troughOn = troughOn,
        returnPct = returnPct,
        sessionsElapsed = sessionsElapsed,
        sessions = sessions,
    )

    private const val UNKNOWN = "Unknown"
}

/** Every ticker named by any saved analysis; the rest cannot be scored, so pricing them is waste. */
fun List<SavedAnalysis>.recommendedTickers(): Set<String> = flatMap { saved ->
    saved.result.consolidated.map { Scoring.normalizeTicker(it.stockCode) } +
        saved.result.recommendations.map { Scoring.normalizeTicker(it.ticker) }
}.filter(String::isNotBlank).toSet()
