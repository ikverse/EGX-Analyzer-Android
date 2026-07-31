package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.SourceTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PerformanceCalculatorTest {
    private val called = LocalDate.of(2026, 7, 20)

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
        val second = analysis(id = 2, ranAt = Instant.parse("2026-07-20T12:00:00Z"))

        val report = PerformanceCalculator.report(
            analyses = listOf(first, second),
            pricesFrom = called,
            windowSessions = 10,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        assertEquals(1, report.sessions.size)
        assertEquals(listOf("AMOC"), report.sessions.single().calls.map { it.ticker })
        assertEquals(2, report.sessions.single().runCount)
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

    private fun sessions(high: Double) = listOf(
        DailySession("AMOC", called, high = high, low = 9.9, close = high, volume = 1000.0, open = 9.9),
    )

    private fun analysis(
        id: Long,
        channel: String = "First channel",
        entryLow: Double? = 9.8,
        entryHigh: Double? = 10.0,
        stopLoss: Double? = 9.0,
        ranAt: Instant = Instant.parse("2026-07-20T09:00:00Z"),
        /** A second stock this run named, to show a rerun dropping it. */
        extraTicker: String? = null,
        /** The same stock quoted again by another channel in the same run. */
        secondChannel: String? = null,
        /** The same stock and channel described twice, once with fewer levels. */
        withSparseDuplicate: Boolean = false,
    ) = SavedAnalysis(
        id = id,
        provider = CloudProvider.QWEN,
        model = "test-model",
        result = AnalysisResult(
            requestId = "request-$id",
            recommendations = emptyList(),
            // The session the run was aimed at, which is what a call is dated by and what the
            // Insights cards group on.
            recommendationTargetDate = called,
            completedAt = ranAt,
            inquiryReplyCount = 0,
            sources = listOfNotNull(
                SourceTrace(
                    sourceId = "source-$id",
                    channelId = 1,
                    channelName = channel,
                    messageId = 42,
                    timestamp = Instant.parse("2026-07-20T10:00:00Z"),
                    contentType = AnalysisContentType.TEXT,
                    preview = "",
                ),
                secondChannel?.let {
                    SourceTrace(
                        sourceId = "source-$id-b",
                        channelId = 2,
                        channelName = it,
                        messageId = 43,
                        timestamp = Instant.parse("2026-07-20T10:05:00Z"),
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
                        point("42", entryLow, entryHigh, stopLoss),
                        // The same stock and channel again, stated with fewer levels.
                        if (withSparseDuplicate) point("42", null, null, null) else null,
                        // The same stock quoted by a second channel in the same run.
                        secondChannel?.let { point("43", entryLow, entryHigh, stopLoss) },
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
                        dataPoints = listOf(point("42", entryLow, entryHigh, stopLoss)),
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
    ) = RecommendationDataPoint(
        date = called,
        effectiveDateBasis = "explicit",
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
