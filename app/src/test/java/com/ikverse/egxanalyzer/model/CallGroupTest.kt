package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** How an open report sorts its calls into chips, and how it finds the scored call behind each. */
class CallGroupTest {

    private val day = LocalDate.parse("2026-11-09")

    private fun point(index: Int = 0, basis: String? = null) = RecommendationDataPoint(
        date = day,
        effectiveDateBasis = basis,
        visibleSourceDate = null,
        dateEvidence = null,
        timingEvidence = null,
        sourceMessageId = null,
        sourceImageRef = null,
        recommendationEvidence = null,
        recommendationType = "buy",
        buyPrice = null,
        buyPriceLow = 10.0,
        buyPriceHigh = 10.2,
        target1 = 11.0,
        returnTp1Pct = null,
        target2 = 12.0,
        returnTp2Pct = null,
        stopLoss = 9.5,
        support = null,
        resistance = null,
        riskPct = null,
        notesArabic = null,
        parseIndex = index,
    )

    private fun stock(point: RecommendationDataPoint) = ConsolidatedRecommendation(
        stockCode = "COMI", stockNameEnglish = null, stockNameArabic = null,
        mentionCount = 1, rank = 1, notesSummary = null, dataPoints = listOf(point),
    )

    private fun saved(id: Long, point: RecommendationDataPoint) = SavedAnalysis(
        id = id,
        provider = CloudProvider.QWEN,
        model = "m",
        result = AnalysisResult(
            requestId = "run-$id",
            recommendations = emptyList(),
            inquiryReplyCount = 0,
            recommendationTargetDate = day,
            completedAt = Instant.EPOCH,
            consolidated = listOf(stock(point)),
        ),
    )

    private fun scored(
        outcome: Outcome = Outcome.OPEN,
        channel: String = "Tadawul",
        reportId: Long? = null,
        code: String? = null,
        index: Int? = null,
    ) = ScoredCall(
        ticker = "COMI", companyEnglish = null, companyArabic = null, channel = channel, channelId = null,
        openedOn = day, entryLow = 10.0, entryHigh = 10.2, target1 = 11.0, target2 = 12.0, stopLoss = 9.5,
        outcome = outcome, settledOn = null, peakHigh = null, troughLow = null, returnPct = null,
        sessionsElapsed = 0, reportId = reportId, originalStockCode = code, pointIndex = index,
    )

    private fun report(vararg calls: ScoredCall) = PerformanceReport(
        sessions = listOf(
            ScoredSession(targetDate = day, lastRunAt = Instant.EPOCH, model = "m", runCount = 1, calls = calls.toList()),
        ),
    )

    @Test
    fun `each outcome lands in its group`() {
        val expected = mapOf(
            Outcome.FULL_HIT to CallGroup.REACHED,
            Outcome.PARTIAL_HIT to CallGroup.REACHED,
            Outcome.STOPPED to CallGroup.STOPPED,
            Outcome.EXPIRED to CallGroup.ENDED,
            Outcome.OPEN to CallGroup.RUNNING,
            Outcome.ENTRY_NOT_REACHED to CallGroup.RUNNING,
            Outcome.AMBIGUOUS to CallGroup.RUNNING,
            Outcome.UNPRICED to CallGroup.RUNNING,
            Outcome.PRICE_BREAK to CallGroup.RUNNING,
        )
        assertEquals(Outcome.entries.toSet(), expected.keys)
        expected.forEach { (outcome, group) ->
            assertEquals(outcome.name, group, callGroup(point(), scored(outcome)))
        }
    }

    @Test
    fun `an unscored call is running`() {
        assertEquals(CallGroup.RUNNING, callGroup(point(), null))
    }

    @Test
    fun `watching wins over whatever the scorer made of it`() {
        assertEquals(CallGroup.WATCHING, callGroup(point(basis = "watching"), scored(Outcome.FULL_HIT)))
    }

    @Test
    fun `a match by identity beats a match by what the call is about`() {
        val p = point(index = 3)
        val byIdentity = scored(Outcome.STOPPED, channel = "Other", reportId = 7, code = "COMI", index = 3)
        val byFallback = scored(Outcome.FULL_HIT)
        val found = scoredCallFor(saved(7, p), stock(p), p, report(byFallback, byIdentity), channel = "Tadawul")
        assertSame(byIdentity, found)
    }

    @Test
    fun `an older re-run with no scored call of its own resolves by ticker, session and channel`() {
        val p = point()
        // The session's scored call belongs to the newer report, 8; this is report 7.
        val newer = scored(Outcome.FULL_HIT, reportId = 8, code = "COMI", index = 0)
        val otherChannel = scored(Outcome.STOPPED, channel = "Elsewhere")
        val found = scoredCallFor(saved(7, p), stock(p), p, report(otherChannel, newer), channel = "Tadawul")
        assertSame(newer, found)
    }

    @Test
    fun `nothing for a stock the session never scored`() {
        val p = point()
        assertNull(scoredCallFor(saved(7, p), stock(p), p, PerformanceReport(sessions = emptyList())))
    }
}
