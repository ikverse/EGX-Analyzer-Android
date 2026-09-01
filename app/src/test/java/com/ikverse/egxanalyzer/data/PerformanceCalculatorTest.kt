package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.SettledCall
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.model.reachedTarget1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PerformanceCalculatorTest {
    private val called = LocalDate.of(2026, 8, 10)

    @Test
    fun `the same call saved by several runs is counted once`() {
        // Re-running the analysis on the same day saves another result listing the same
        // recommendations; counting each copy inflates every total.
        val analyses = List(4) { run -> analysis(id = run.toLong()) }

        val report = PerformanceCalculator.report(
            analyses = analyses,
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        assertEquals(1, report.tracked)
        assertEquals(1, report.judged)
        // The fixture reaches target 1 of two, so it counts toward the any-target rate only.
        assertEquals(100.0, report.anyTargetRate!!, 0.001)
        assertEquals(0.0, report.fullHitRate!!, 0.001)
    }

    @Test
    fun `a rerun of a session replaces the earlier one rather than adding to it`() {
        // The first run named two stocks, the second only one. The later run is the considered
        // view, so the stock it dropped does not survive from the earlier attempt.
        val first = analysis(id = 1, extraTicker = "COMI")
        val second = analysis(id = 2, ranAt = Instant.parse("2026-08-10T12:00:00Z"))

        val report = PerformanceCalculator.report(
            analyses = listOf(first, second),
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        assertEquals(1, report.sessions.size)
        assertEquals(listOf("AMOC"), report.sessions.single().calls.map { it.ticker })
        // Both runs covered the one chat, so the older contributes nothing and the card is the
        // work of a single run.
        assertEquals(1, report.sessions.single().runCount)
    }

    @Test
    fun `a chat the newer run never covered keeps its earlier reading`() {
        // The whole point of keying coverage per chat: a rerun over one chat must not erase what
        // another chat said about the same session.
        val broad = analysis(id = 1, secondChannel = "Second channel", extraTicker = "COMI")
        val narrow = analysis(id = 2, ranAt = Instant.parse("2026-08-10T12:00:00Z"))

        val report = PerformanceCalculator.report(
            analyses = listOf(broad, narrow),
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        val session = report.sessions.single()
        assertEquals(2, session.runCount)
        assertEquals(setOf("First channel", "Second channel"), session.calls.map { it.channel }.toSet())
        // The rerun dropped COMI for the chat it covered, and that drop stands.
        assertEquals(false, session.calls.any { it.ticker == "COMI" && it.channel == "First channel" })
    }

    @Test
    fun `within one run the copy stating the most price levels is kept`() {
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1, withSparseDuplicate = true)),
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        val call = report.sessions.flatMap { it.calls }.single()
        assertEquals(9.8, call.entryLow!!, 0.001)
        assertEquals(9.0, call.stopLoss!!, 0.001)
    }

    @Test
    fun `a call re-posted on the next analysed session is counted once`() {
        // Channels re-post a standing recommendation every morning until it resolves. Scored as it
        // stood, one idea collected a judged call per posting and moved the channel's record three
        // times - and a channel that posts a daily table outweighed one that posts when it has
        // something to say, on nothing but how often it posts.
        val postings = (0..2).map { day ->
            analysis(id = day.toLong(), targetDate = called.plusDays(day.toLong()))
        }

        val report = PerformanceCalculator.report(
            analyses = postings,
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        assertEquals(1, report.tracked)
        assertEquals(1, report.judged)
        // Still on the record. Each session shows what that channel actually published that day.
        assertEquals(3, report.sessions.sumOf { it.calls.size })
        assertEquals(2, report.sessions.flatMap { it.calls }.count { it.repeatOf != null })
        assertEquals(called, report.sessions.flatMap { it.calls }.first { it.repeatOf != null }.repeatOf)
        assertEquals(1, report.channels.single().calls)
        assertEquals(2, report.channels.single().repeats)
    }

    @Test
    fun `a call whose levels moved is a new call rather than a repeat`() {
        // A channel that lifts a target or moves a stop has made a different call, and is scored on
        // it. The identity is every level printed, exactly as the parser's own collapse keys on.
        val report = PerformanceCalculator.report(
            analyses = listOf(
                analysis(id = 1, targetDate = called),
                analysis(id = 2, targetDate = called.plusDays(1), stopLoss = 8.5),
            ),
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        assertEquals(2, report.tracked)
        assertEquals(0, report.sessions.flatMap { it.calls }.count { it.repeatOf != null })
    }

    @Test
    fun `a session going by without the call makes the next posting a new one`() {
        // Adjacency is measured against the sessions actually analysed, not the calendar. A gap
        // means the channel stopped saying it and started again, which is a second call.
        val report = PerformanceCalculator.report(
            analyses = listOf(
                analysis(id = 1, targetDate = called),
                analysis(id = 2, targetDate = called.plusDays(1), stopLoss = 8.5),
                analysis(id = 3, targetDate = called.plusDays(2)),
            ),
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        assertEquals(3, report.tracked)
        assertEquals(0, report.sessions.flatMap { it.calls }.count { it.repeatOf != null })
    }

    @Test
    fun `a filtered view leaves out the re-postings too`() {
        val postings = (0..2).map { day ->
            analysis(id = day.toLong(), targetDate = called.plusDays(day.toLong()))
        }
        val report = PerformanceCalculator.report(
            analyses = postings,
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        val narrowed = PerformanceCalculator.refine(report) { true }

        assertEquals(report.tracked, narrowed.tracked)
        assertEquals(report.judged, narrowed.judged)
    }

    @Test
    fun `the same stock from two channels stays two calls`() {
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1, secondChannel = "Second channel")),
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        assertEquals(2, report.tracked)
        assertEquals(2, report.channels.size)
    }

    @Test
    fun `calls made before there was any price history are skipped`() {
        // A call predating the stored sessions says nothing about the source, so reporting it as
        // unpriced would count it against a channel for something it could not have known.
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called.plusDays(5),
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        assertEquals(0, report.tracked)
    }

    @Test
    fun `nothing is scored until prices exist`() {
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = null,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        assertEquals(0, report.tracked)
        assertNull(report.scoringSince)
    }

    @Test
    fun `a stock with no stored price does not move the hit rate`() {
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            sessionsFor = { _, _ -> emptyList() },
        )

        assertEquals(1, report.tracked)
        assertEquals(0, report.judged)
        assertNull(report.fullHitRate)
        assertEquals(1, report.byOutcome[Outcome.UNPRICED])
        assertNull(report.channels.single().fullHitRate)
    }

    @Test
    fun `a scored call carries the sessions it was judged on`() {
        // The run card expands to show these, so they travel with the call rather than costing
        // another query per row.
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        val call = report.sessions.flatMap { it.calls }.single()
        assertEquals(1, call.sessions.size)
        assertEquals(called, call.sessions.single().date)
        assertEquals(9.9, call.sessions.single().open!!, 0.001)
    }

    @Test
    fun `a session keeps the model and time of the run that produced it`() {
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 7)),
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        val session = report.sessions.single()
        assertEquals(called, session.targetDate)
        assertEquals("test-model", session.model)
        assertEquals(1, session.runCount)
        assertEquals(1, session.calls.size)
    }

    @Test
    fun `prices are reported the way they are quoted`() {
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.869998931884766) },
        )

        assertEquals(12.87, report.sessions.flatMap { it.calls }.single().peakHigh!!, 0.0001)
    }

    @Test
    fun `calls made before the analysis floor are left out however far the prices reach`() {
        // The record starts on a date, not wherever the oldest stored price happens to sit. Prices
        // from before it are still on disk - a split check compares against them - and no call is
        // judged on them.
        val old = PerformanceCalculator.ANALYSIS_START.minusDays(1)
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1, targetDate = old)),
            pricesFrom = old.minusMonths(6),
            sessionsFor = { _, _ -> listOf(sessions(12.5).single().copy(date = old)) },
        )

        assertEquals(0, report.tracked)
        assertEquals(emptyList<Any>(), report.sessions)
    }

    @Test
    fun `the starting line is the floor rather than the earliest call behind it`() {
        // Derived from the calls, this was null exactly when nothing had been scored - which is the
        // one case the empty state needs it for, so it could never name the date it was waiting on.
        val report = PerformanceCalculator.report(
            analyses = emptyList(),
            pricesFrom = PerformanceCalculator.ANALYSIS_START.minusMonths(6),
            sessionsFor = { _, _ -> emptyList() },
        )

        assertEquals(PerformanceCalculator.ANALYSIS_START, report.scoringSince)
    }

    @Test
    fun `a filter does not move the starting line`() {
        // Every other figure on the report describes the calls on screen. This one is a property of
        // the record, and recomputing it would have a filter claim the record began later.
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        val narrowed = PerformanceCalculator.refine(report) { false }

        assertEquals(0, narrowed.tracked)
        assertEquals(report.scoringSince, narrowed.scoringSince)
    }

    @Test
    fun `a T plus one call is judged over its own two sessions, not the horizon`() {
        // Three sessions: the one the call was made for, the one it said to sell on, and a third on
        // which the target finally arrives. The trade the card described was over before that third
        // session opened, so crediting it would credit some other trade than the one it called.
        val prices = listOf(
            session(called, high = 10.0),
            session(called.plusDays(1), high = 11.0),
            session(called.plusDays(2), high = 12.5),
        )

        val tPlusOne = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1, basis = "t_plus_1")),
            pricesFrom = called,
            sessionsFor = { _, _ -> prices },
        )
        // The same prices, the same everything but the basis printed on the card.
        val ordinary = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            sessionsFor = { _, _ -> prices },
        )

        val judged = tPlusOne.sessions.single().calls.single()
        assertEquals(Outcome.EXPIRED, judged.outcome)
        assertEquals(Scoring.T_PLUS_ONE_WINDOW_SESSIONS, judged.windowSessions)
        assertEquals(Scoring.T_PLUS_ONE_ENTRY_SESSIONS, judged.entrySessions)
        // Two of the three sessions were ever looked at.
        assertEquals(2, judged.sessions.size)
        // The ordinary call is still running on the same prices: it has reached target 1 and has
        // the rest of the horizon to reach target 2, where the T+1 card was finished on session
        // two. That difference is the whole of what the T+1 rule buys.
        val open = ordinary.sessions.single().calls.single()
        assertEquals(Outcome.PARTIAL_HIT, open.outcome)
        assertEquals(Scoring.JUDGING_HORIZON_SESSIONS, open.windowSessions)
    }

    @Test
    fun `a call that takes longer than a trade window is a hit rather than an expiry`() {
        // Twelve sessions with the target on the twelfth. Judged over the old ten-session setting
        // this was an expiry, and the record could not then say the source takes a fortnight to be
        // right - the one question the timings on a channel card exist to answer.
        val prices = (0..11).map { day ->
            session(called.plusDays(day.toLong()), high = if (day == 11) 12.5 else 10.2)
        }

        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            sessionsFor = { _, _ -> prices },
        )

        val call = report.sessions.single().calls.single()
        assertEquals(Outcome.PARTIAL_HIT, call.outcome)
        assertEquals(12, call.sessionsElapsed)
        assertEquals(12.0, report.channels.single().medianSessionsToHit!!, 0.001)
    }

    @Test
    fun `sessions to a stop counts only the calls the stop took out`() {
        // A partial hit that fell back settled on its target and is counted in the timing above;
        // counting it here as well would let one call say how fast the source is right and how
        // fast it is wrong at once.
        val stopped = (0..3).map { day ->
            session(called.plusDays(day.toLong()), high = 10.0, low = if (day == 3) 8.0 else 9.9)
        }

        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            sessionsFor = { _, _ -> stopped },
        )

        val channel = report.channels.single()
        assertEquals(Outcome.STOPPED, report.sessions.single().calls.single().outcome)
        assertEquals(4.0, channel.medianSessionsToStop!!, 0.001)
        assertNull(channel.medianSessionsToHit)
    }

    @Test
    fun `a call the stop took out is written down once`() {
        val settled = mutableListOf<SettledCall>()

        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            sessionsFor = { _, _ -> listOf(session(called, high = 10.0, low = 8.0)) },
            onSettled = { settled += it },
        )

        assertEquals(Outcome.STOPPED, report.sessions.single().calls.single().outcome)
        // The market cannot take this back, so it never needs scoring again.
        assertEquals(1, settled.size)
        assertEquals(Outcome.STOPPED, settled.single().outcome)
        // The evidence travels with the verdict, or the card that draws it would have to go back
        // to the price table and could disagree with the verdict beside it.
        assertEquals(listOf(called), settled.single().sessions.map(DailySession::date))
    }

    @Test
    fun `a partial hit with target 2 still in reach is not frozen`() {
        val settled = mutableListOf<SettledCall>()

        PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            sessionsFor = { _, _ -> sessions(12.5) },
            onSettled = { settled += it },
        )

        // Freezing this would be the app deciding a call cannot improve when it plainly can - the
        // whole reason a call runs to its settlement rather than to a deadline.
        assertTrue(settled.isEmpty())
    }

    @Test
    fun `a frozen verdict is read back rather than scored again`() {
        val frozen = mutableListOf<SettledCall>()
        PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            sessionsFor = { _, _ -> listOf(session(called, high = 10.0, low = 8.0)) },
            onSettled = { frozen += it },
        )
        var asked = 0

        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            // Prices that would score a full hit, so reusing the verdict is visible rather than
            // merely plausible.
            sessionsFor = { _, _ -> asked++; sessions(14.5) },
            settled = frozen.associateBy(SettledCall::key),
        )

        val call = report.sessions.single().calls.single()
        assertEquals(Outcome.STOPPED, call.outcome)
        // Not asked for at all: the query is the expensive half of scoring a settled call.
        assertEquals(0, asked)
        assertEquals(listOf(called), call.sessions.map(DailySession::date))
    }

    @Test
    fun `a call re-read with different levels is scored from scratch`() {
        val frozen = mutableListOf<SettledCall>()
        PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            sessionsFor = { _, _ -> listOf(session(called, high = 10.0, low = 8.0)) },
            onSettled = { frozen += it },
        )

        // The same stock, the same session, the same channel - and a buy zone nowhere near the one
        // that verdict was reached about. A newer prompt reading a card differently must not
        // inherit an answer about other numbers.
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1, entryLow = 20.0, entryHigh = 21.0)),
            pricesFrom = called,
            sessionsFor = { _, _ -> listOf(session(called, high = 10.0, low = 8.0)) },
            settled = frozen.associateBy(SettledCall::key),
        )

        assertEquals(
            Outcome.ENTRY_NOT_REACHED,
            report.sessions.single().calls.single().outcome,
        )
    }

    @Test
    fun `a call that ran to target 2 still counts as having reached target 1`() {
        // The question the figures exist to answer, and the one the outcome bar cannot: its first
        // segment is target 1 *only*, so a reader adding the segments up is the only reader who
        // ever sees how many calls actually got there.
        val channel = policyChannel(
            PolicyStock("AAA", reaching(14.5)),
            PolicyStock("BBB", target1ThenStop()),
        )

        assertEquals(2, channel.judged)
        // One in each segment of the bar, and both reached target 1.
        assertEquals(1, channel.fullHits)
        assertEquals(1, channel.partialHits)
        assertEquals(2, channel.reachedTarget1)
        assertEquals(100.0, channel.anyTargetRate!!, 0.01)
        // Of the two that got to target 1, one carried on - which is not the 50% that fullHitRate
        // happens to read here, and the next test is what separates them.
        assertEquals(50.0, channel.continuationRate!!, 0.01)
    }

    @Test
    fun `the continuation rate ignores calls that never reached target 1`() {
        // fullHitRate divides by every judged call, so a stop-out drags it down; the sell-or-hold
        // decision is only ever taken on a call that got to target 1, so this must not move.
        val channel = policyChannel(
            PolicyStock("AAA", reaching(14.5)),
            PolicyStock("BBB", target1ThenStop()),
            PolicyStock("CCC", straightToStop()),
        )

        assertEquals(3, channel.judged)
        assertEquals(2, channel.reachedTarget1)
        // A third call, and the two rates part company: one third against one half.
        assertEquals(33.3, channel.fullHitRate!!, 0.05)
        assertEquals(50.0, channel.continuationRate!!, 0.01)
    }

    @Test
    fun `both rules are priced over the same calls`() {
        // Entry midpoint 9.9, so target 1 is +21.21% and target 2 is +41.41%. The full hit splits
        // to (21.21 + 41.41) / 2 and the one the stop took back to (21.21 + -9.09) / 2.
        val channel = policyChannel(
            PolicyStock("AAA", reaching(14.5)),
            PolicyStock("BBB", target1ThenStop()),
        )

        assertEquals(2, channel.policyCalls)
        assertEquals(21.21, channel.sellAtTarget1Return!!, 0.01)
        assertEquals(18.69, channel.splitReturn!!, 0.02)
    }

    @Test
    fun `a partial hit that ran out of time is priced at its last close`() {
        // The case the stored verdict cannot answer: no stop took this back, so the level it ended
        // at is on no card. Without the last close the un-sold half has no ending and the call
        // would drop out of the comparison entirely.
        val channel = policyChannel(PolicyStock("AAA", target1ThenDrift(sessions = 30, close = 11.0)))

        assertEquals(1, channel.policyCalls)
        assertEquals(21.21, channel.sellAtTarget1Return!!, 0.01)
        // Half banked at target 1, half left to close at 11.0, which is +11.11% from the midpoint.
        assertEquals(16.16, channel.splitReturn!!, 0.02)
    }

    @Test
    fun `a partial hit still running is left out of both figures`() {
        // Its un-sold half has not finished, so holding has no result yet. Marking it at today's
        // close would put a price that is not an outcome into an average of outcomes - and it is
        // still a judged call, so it must stay in every rate above.
        val channel = policyChannel(
            PolicyStock("AAA", reaching(14.5)),
            PolicyStock("BBB", target1ThenDrift(sessions = 5, close = 11.0)),
        )

        assertEquals(2, channel.judged)
        assertEquals(2, channel.reachedTarget1)
        // Only the settled one is priced, and on it the two rules are just its own two returns.
        assertEquals(1, channel.policyCalls)
        assertEquals(21.21, channel.sellAtTarget1Return!!, 0.01)
        assertEquals(31.31, channel.splitReturn!!, 0.02)
    }

    @Test
    fun `a call printing one target is left out of the pair`() {
        // With no second target there is nothing to hold for, so both rules would price it
        // identically and it would drag the two figures toward each other for no reason.
        val channel = policyChannel(
            PolicyStock("AAA", reaching(14.5)),
            PolicyStock("BBB", reaching(12.5), target2 = null),
        )

        assertEquals(2, channel.judged)
        // Scoring makes the only target the full one, so it lands as a full hit either way.
        assertEquals(2, channel.fullHits)
        assertEquals(1, channel.policyCalls)
    }

    /** The one channel's score out of a run naming each stock once, with its own price path. */
    private fun policyChannel(vararg stocks: PolicyStock): ChannelScore {
        val paths = stocks.associate { it.ticker to it.path }
        val report = PerformanceCalculator.report(
            analyses = listOf(policyRun(stocks.toList())),
            pricesFrom = called,
            sessionsFor = { ticker, _ -> paths[ticker].orEmpty() },
            // Behind every session these paths lay down, so what the wall clock happens to read
            // reaches none of them. A window is only spent once its last session has closed, and
            // the longest path here runs a month out from a date fixed in the test.
            finalThrough = called.plusYears(1),
        )
        return report.channels.single()
    }

    /** One stock's call: the levels it printed, and what the market then did about them. */
    private data class PolicyStock(
        val ticker: String,
        val path: List<DailySession>,
        val target1: Double? = 12.0,
        val target2: Double? = 14.0,
    )

    /** Enters on the first session and reaches [high] on it. */
    private fun reaching(high: Double) = listOf(session(called, high = high, low = 9.9))

    /** Reaches target 1, then the stop breaks by more than the 2% the rule allows. */
    private fun target1ThenStop() = listOf(
        session(called, high = 12.5, low = 9.9),
        session(called.plusDays(1), high = 10.0, low = 8.5),
    )

    /** Reaches target 1, then sits at [close] for the rest of [sessions] without resolving. */
    private fun target1ThenDrift(sessions: Int, close: Double) =
        listOf(session(called, high = 12.5, low = 9.9)) +
            (1 until sessions).map {
                session(called.plusDays(it.toLong()), high = close, low = close)
            }

    /** Enters, never reaches a target, and the stop takes it out. */
    private fun straightToStop() = listOf(
        session(called, high = 10.5, low = 9.9),
        session(called.plusDays(1), high = 10.0, low = 8.5),
    )

    /** One run naming each stock once for a single session, on one channel. */
    private fun policyRun(stocks: List<PolicyStock>) = SavedAnalysis(
        id = 1,
        provider = CloudProvider.QWEN,
        model = "test-model",
        result = AnalysisResult(
            requestId = "request-policy",
            recommendations = emptyList(),
            recommendationTargetDate = called,
            completedAt = Instant.parse("2026-08-10T09:00:00Z"),
            inquiryReplyCount = 0,
            sources = listOf(
                SourceTrace(
                    sourceId = "source-policy",
                    channelId = 1,
                    channelName = "First channel",
                    messageId = 42,
                    timestamp = Instant.parse("2026-08-10T10:00:00Z"),
                    contentType = AnalysisContentType.TEXT,
                    preview = "",
                ),
            ),
            // Separate stocks rather than separate sessions, deliberately: one channel printing the
            // same levels on adjacent analysed sessions is one call re-posted, and every rate here
            // would drop all but the first of them.
            consolidated = stocks.mapIndexed { index, stock ->
                ConsolidatedRecommendation(
                    stockCode = stock.ticker,
                    stockNameEnglish = stock.ticker,
                    stockNameArabic = null,
                    mentionCount = 1,
                    rank = index + 1,
                    notesSummary = null,
                    dataPoints = listOf(
                        point(
                            messageId = "42",
                            entryLow = 9.8,
                            entryHigh = 10.0,
                            stopLoss = 9.0,
                            target1 = stock.target1,
                            target2 = stock.target2,
                        ),
                    ),
                )
            },
        ),
    )

    private fun sessions(high: Double) = listOf(session(called, high))

    private fun session(date: LocalDate, high: Double, low: Double = 9.9) =
        DailySession("AMOC", date, high = high, low = low, close = high, volume = 1000.0, open = low)

    private fun analysis(
        id: Long,
        channel: String = "First channel",
        entryLow: Double? = 9.8,
        entryHigh: Double? = 10.0,
        stopLoss: Double? = 9.0,
        ranAt: Instant = Instant.parse("2026-08-10T09:00:00Z"),
        /** The session the run was aimed at, so a fixture can sit below the analysis floor. */
        targetDate: LocalDate = called,
        /** A second stock this run named, to show a rerun dropping it. */
        extraTicker: String? = null,
        /** The same stock quoted again by another channel in the same run. */
        secondChannel: String? = null,
        /** The same stock and channel described twice, once with fewer levels. */
        withSparseDuplicate: Boolean = false,
        /** What dated the card, which is what marks a T+1 one. */
        basis: String = "explicit",
    ) = SavedAnalysis(
        id = id,
        provider = CloudProvider.QWEN,
        model = "test-model",
        result = AnalysisResult(
            requestId = "request-$id",
            recommendations = emptyList(),
            // The session the run was aimed at, which is what a call is dated by and what the
            // Insights cards group on.
            recommendationTargetDate = targetDate,
            completedAt = ranAt,
            inquiryReplyCount = 0,
            sources = listOfNotNull(
                SourceTrace(
                    sourceId = "source-$id",
                    channelId = 1,
                    channelName = channel,
                    messageId = 42,
                    timestamp = Instant.parse("2026-08-10T10:00:00Z"),
                    contentType = AnalysisContentType.TEXT,
                    preview = "",
                ),
                secondChannel?.let {
                    SourceTrace(
                        sourceId = "source-$id-b",
                        channelId = 2,
                        channelName = it,
                        messageId = 43,
                        timestamp = Instant.parse("2026-08-10T10:05:00Z"),
                        contentType = AnalysisContentType.TEXT,
                        preview = "",
                    )
                },
            ),
            consolidated = listOfNotNull(
                ConsolidatedRecommendation(
                    stockCode = "AMOC.CA",
                    stockNameEnglish = "Alexandria Mineral Oils",
                    stockNameArabic = "اموك",
                    mentionCount = 1,
                    rank = 1,
                    notesSummary = null,
                    dataPoints = listOfNotNull(
                        point("42", entryLow, entryHigh, stopLoss, basis),
                        // The same stock and channel again, stated with fewer levels.
                        if (withSparseDuplicate) point("42", null, null, null, basis) else null,
                        // The same stock quoted by a second channel in the same run.
                        secondChannel?.let { point("43", entryLow, entryHigh, stopLoss, basis) },
                    ),
                ),
                // A stock only one run named, so a rerun dropping it is visible.
                extraTicker?.let {
                    ConsolidatedRecommendation(
                        stockCode = it,
                        stockNameEnglish = it,
                        stockNameArabic = null,
                        mentionCount = 1,
                        rank = 2,
                        notesSummary = null,
                        dataPoints = listOf(point("42", entryLow, entryHigh, stopLoss, basis)),
                    )
                },
            ),
        ),
    )

    private fun point(
        messageId: String,
        entryLow: Double?,
        entryHigh: Double?,
        stopLoss: Double?,
        basis: String = "explicit",
        target1: Double? = 12.0,
        target2: Double? = 14.0,
    ) = RecommendationDataPoint(
        date = called,
        effectiveDateBasis = basis,
        visibleSourceDate = called.toString(),
        dateEvidence = null,
        timingEvidence = null,
        sourceMessageId = messageId,
        sourceImageRef = null,
        recommendationEvidence = null,
        recommendationType = "buy",
        buyPrice = null,
        buyPriceLow = entryLow,
        buyPriceHigh = entryHigh,
        target1 = target1,
        returnTp1Pct = null,
        target2 = target2,
        returnTp2Pct = null,
        stopLoss = stopLoss,
        support = null,
        resistance = null,
        riskPct = null,
        notesArabic = null,
    )
}
