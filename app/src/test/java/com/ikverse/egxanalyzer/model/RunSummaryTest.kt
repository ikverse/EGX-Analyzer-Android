package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RunSummaryTest {

    private fun cairo(text: String) = LocalDateTime.parse(text).atZone(ScheduleClock.ZONE).toInstant()

    @Test
    fun `a run is named for its weekday and the part of the day`() {
        // 2026-11-09 is a Monday.
        assertEquals("Monday morning", runTitle(cairo("2026-11-09T09:12:00")))
        assertEquals("Monday midday", runTitle(cairo("2026-11-09T12:40:00")))
        assertEquals("Monday close", runTitle(cairo("2026-11-09T15:04:00")))
        assertEquals("Monday evening", runTitle(cairo("2026-11-09T21:30:00")))
        assertEquals("Tuesday night", runTitle(cairo("2026-11-10T02:00:00")))
    }

    @Test
    fun `the bell is where midday becomes close`() {
        assertEquals("Monday midday", runTitle(cairo("2026-11-09T14:29:00")))
        assertEquals("Monday close", runTitle(cairo("2026-11-09T14:30:00")))
    }

    private val day = LocalDate.parse("2026-11-09")

    private fun call(ticker: String, outcome: Outcome) = ScoredCall(
        ticker = ticker, companyEnglish = null, companyArabic = null, channel = "Tadawul", channelId = null,
        openedOn = day, entryLow = 10.0, entryHigh = 10.2, target1 = 11.0, target2 = 12.0, stopLoss = 9.5,
        outcome = outcome, settledOn = day, peakHigh = 11.5, troughLow = 9.9, returnPct = 5.0,
        sessionsElapsed = 1, repeatOf = null,
        faults = CallSanity.faults(10.0, 10.2, 11.0, 12.0, 9.5, session = null),
    )

    private fun stock(code: String) = ConsolidatedRecommendation(
        stockCode = code, stockNameEnglish = null, stockNameArabic = null,
        mentionCount = 1, rank = 1, notesSummary = null,
    )

    private fun saved(vararg codes: String) = SavedAnalysis(
        id = 31,
        provider = CloudProvider.QWEN,
        model = "qwen3.7-plus",
        result = AnalysisResult(
            requestId = "run-31",
            recommendations = emptyList(),
            inquiryReplyCount = 0,
            recommendationTargetDate = day,
            completedAt = Instant.parse("2026-11-09T07:12:00Z"),
            consolidated = codes.map(::stock),
        ),
    )

    private fun session(date: LocalDate, vararg calls: ScoredCall) = ScoredSession(
        targetDate = date, lastRunAt = Instant.EPOCH, model = "m", runCount = 1, calls = calls.toList(),
    )

    @Test
    fun `a report counts the scored calls of its own session and stocks`() {
        val report = PerformanceReport(
            sessions = listOf(
                session(
                    day,
                    call("COMI", Outcome.FULL_HIT),
                    call("ABUK", Outcome.PARTIAL_HIT),
                    call("ORAS", Outcome.STOPPED),
                    call("SWDY", Outcome.EXPIRED),
                    call("FWRY", Outcome.OPEN),
                    call("HRHO", Outcome.FULL_HIT), // not in this report
                ),
                session(day.minusDays(1), call("COMI", Outcome.STOPPED)), // another session
            ),
        )
        val outcomes = runOutcomes(saved("COMI", "ABUK", "ORAS", "SWDY", "FWRY"), report)
        assertEquals(RunOutcomes(hit = 2, stopped = 1, expired = 1, open = 1), outcomes)
        assertEquals(5, outcomes.total)
    }

    @Test
    fun `calls not yet scored count as open`() {
        val report = PerformanceReport(sessions = listOf(session(day, call("COMI", Outcome.ENTRY_NOT_REACHED))))
        assertEquals(RunOutcomes(0, 0, 0, 1), runOutcomes(saved("COMI"), report))
    }
}
