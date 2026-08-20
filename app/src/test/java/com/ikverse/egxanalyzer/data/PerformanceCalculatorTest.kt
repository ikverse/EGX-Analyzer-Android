package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.SourceTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            windowSessions = 10,
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
            windowSessions = 10,
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
            windowSessions = 10,
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
            windowSessions = 10,
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
            windowSessions = 10,
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
            windowSessions = 10,
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
            windowSessions = 10,
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
            windowSessions = 10,
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
            windowSessions = 10,
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
            windowSessions = 10,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        assertEquals(0, report.tracked)
    }

    @Test
    fun `nothing is scored until prices exist`() {
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = null,
            windowSessions = 10,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        assertEquals(0, report.tracked)
        assertNull(report.scoringSince)
        assertEquals(10, report.windowSessions)
    }

    @Test
    fun `a stock with no stored price does not move the hit rate`() {
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            windowSessions = 10,
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
            windowSessions = 10,
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
            windowSessions = 10,
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
            windowSessions = 10,
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
            windowSessions = 10,
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
            windowSessions = 10,
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
            windowSessions = 10,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        val narrowed = PerformanceCalculator.refine(report) { false }

        assertEquals(0, narrowed.tracked)
        assertEquals(report.scoringSince, narrowed.scoringSince)
    }

    @Test
    fun `a T plus one call is judged over its own two sessions, not the setting`() {
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
            windowSessions = 10,
            sessionsFor = { _, _ -> prices },
        )
        // The same prices, the same setting, the same everything but the basis printed on the card.
        val ordinary = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            windowSessions = 10,
            sessionsFor = { _, _ -> prices },
        )

        val judged = tPlusOne.sessions.single().calls.single()
        assertEquals(Outcome.EXPIRED, judged.outcome)
        assertEquals(Scoring.T_PLUS_ONE_WINDOW_SESSIONS, judged.windowSessions)
        assertEquals(Scoring.T_PLUS_ONE_ENTRY_SESSIONS, judged.entrySessions)
        // Two of the three sessions were ever looked at.
        assertEquals(2, judged.sessions.size)
        assertEquals(Outcome.PARTIAL_HIT, ordinary.sessions.single().calls.single().outcome)
        // The report still reports the setting: it describes what the user chose, not one call.
        assertEquals(10, tPlusOne.windowSessions)
    }

    private fun sessions(high: Double) = listOf(session(called, high))

    private fun session(date: LocalDate, high: Double) =
        DailySession("AMOC", date, high = high, low = 9.9, close = high, volume = 1000.0, open = 9.9)

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
        target1 = 12.0,
        returnTp1Pct = null,
        target2 = 14.0,
        returnTp2Pct = null,
        stopLoss = stopLoss,
        support = null,
        resistance = null,
        riskPct = null,
        notesArabic = null,
    )
}
