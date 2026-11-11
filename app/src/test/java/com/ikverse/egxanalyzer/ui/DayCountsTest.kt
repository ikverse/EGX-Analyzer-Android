package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.CallSanity
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceCalculator
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The opened day's head and its chips count the same calls the same way. 5.1.1 said "11 running"
 * over a Running chip of 6, because every call the record leaves out was counted as running.
 */
class DayCountsTest {

    private val day = LocalDate.parse("2026-11-09")

    private fun call(ticker: String, outcome: Outcome) = ScoredCall(
        ticker = ticker, companyEnglish = null, companyArabic = null, channel = "Tadawul", channelId = null,
        openedOn = day, entryLow = 10.0, entryHigh = 10.4, target1 = 11.0, target2 = 12.0, stopLoss = 9.5,
        outcome = outcome, settledOn = null, peakHigh = 10.5, troughLow = 9.9, returnPct = 1.0,
        sessionsElapsed = 1, repeatOf = null, windowComplete = false,
        faults = CallSanity.faults(10.0, 10.4, 11.0, 12.0, 9.5, session = null),
    )

    @Test
    fun `running counts only the calls still running, and the rest are not counted`() {
        val calls = listOf(
            call("COMI", Outcome.OPEN),
            call("ABUK", Outcome.OPEN),
            call("ORAS", Outcome.ENTRY_NOT_REACHED),
            call("SWDY", Outcome.UNPRICED),
            call("EGAL", Outcome.STOPPED),
        )
        val run = ScoredSession(day, Instant.EPOCH, "m", 1, calls = calls)
        val split = run.outcomeSplit(PerformanceCalculator.tally(calls))
        assertEquals(2, split.open)
        assertEquals(1, split.stopped)
        assertEquals(2, run.notCountedCalls())
    }
}
