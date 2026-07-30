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
        assertEquals(100.0, report.hitRate!!, 0.001)
    }

    @Test
    fun `the copy stating the most price levels is the one kept`() {
        val sparse = analysis(id = 1, entryLow = null, entryHigh = null, stopLoss = null)
        val complete = analysis(id = 2)

        val report = PerformanceCalculator.report(
            analyses = listOf(sparse, complete),
            pricesFrom = called,
            windowSessions = 10,
            sessionsFor = { _, _ -> sessions(12.5) },
        )

        assertEquals(1, report.calls.size)
        assertEquals(9.8, report.calls.single().entryLow!!, 0.001)
        assertEquals(9.0, report.calls.single().stopLoss!!, 0.001)
    }

    @Test
    fun `the same stock from two channels stays two calls`() {
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1), analysis(id = 2, channel = "Second channel")),
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
        assertNull(report.hitRate)
        assertEquals(1, report.byOutcome[Outcome.UNPRICED])
        assertNull(report.channels.single().hitRate)
    }

    @Test
    fun `prices are reported the way they are quoted`() {
        val report = PerformanceCalculator.report(
            analyses = listOf(analysis(id = 1)),
            pricesFrom = called,
            windowSessions = 10,
            sessionsFor = { _, _ -> sessions(12.869998931884766) },
        )

        assertEquals(12.87, report.calls.single().peakHigh!!, 0.0001)
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
    ) = SavedAnalysis(
        id = id,
        provider = CloudProvider.QWEN,
        model = "test-model",
        result = AnalysisResult(
            requestId = "request-$id",
            recommendations = emptyList(),
            inquiryReplyCount = 0,
            sources = listOf(
                SourceTrace(
                    sourceId = "source-$id",
                    channelId = 1,
                    channelName = channel,
                    messageId = 42,
                    timestamp = Instant.parse("2026-07-20T10:00:00Z"),
                    contentType = AnalysisContentType.TEXT,
                    preview = "",
                ),
            ),
            consolidated = listOf(
                ConsolidatedRecommendation(
                    stockCode = "AMOC.CA",
                    stockNameEnglish = "Alexandria Mineral Oils",
                    stockNameArabic = "اموك",
                    mentionCount = 1,
                    rank = 1,
                    notesSummary = null,
                    dataPoints = listOf(
                        RecommendationDataPoint(
                            date = called,
                            effectiveDateBasis = "explicit",
                            visibleSourceDate = called.toString(),
                            dateEvidence = null,
                            timingEvidence = null,
                            sourceMessageId = "42",
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
                        ),
                    ),
                ),
            ),
        ),
    )
}
