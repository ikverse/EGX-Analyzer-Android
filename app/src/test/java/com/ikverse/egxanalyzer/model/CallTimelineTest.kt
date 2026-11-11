package com.ikverse.egxanalyzer.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallTimelineTest {

    private val day = LocalDate.parse("2026-11-02")

    private fun call(
        outcome: Outcome,
        settledOn: LocalDate? = day.plusDays(5),
        stoppedAfterPartial: Boolean = false,
        stoppedOn: LocalDate? = null,
        tPlusOne: Boolean = false,
        windowComplete: Boolean = true,
    ) = ScoredCall(
        ticker = "COMI", companyEnglish = null, companyArabic = null, channel = "Tadawul", channelId = null,
        openedOn = day, entryLow = 10.0, entryHigh = 10.2, target1 = 11.0, target2 = 12.0, stopLoss = 9.5,
        outcome = outcome, settledOn = settledOn, peakHigh = null, troughLow = null, returnPct = null,
        sessionsElapsed = 4, windowSessions = if (tPlusOne) 2 else Scoring.JUDGING_HORIZON_SESSIONS,
        isTPlusOne = tPlusOne, stoppedAfterPartial = stoppedAfterPartial, stoppedOn = stoppedOn,
        windowComplete = windowComplete,
    )

    @Test
    fun `a full hit runs from the call to target 2`() {
        val steps = call(Outcome.FULL_HIT).timeline()
        assertEquals(
            listOf("Called by Tadawul", "Price came into the buying range", "Reached target 1", "Reached target 2"),
            steps.map { it.text },
        )
        assertEquals(CallStepKind.GOOD, steps.last().kind)
        assertEquals(day.plusDays(5), steps.last().date)
        // The record keeps no date for coming into the buying range.
        assertNull(steps[1].date)
    }

    @Test
    fun `a stop after target 1 says so, with the stop's own date`() {
        val stopped = day.plusDays(8)
        val steps = call(Outcome.PARTIAL_HIT, stoppedAfterPartial = true, stoppedOn = stopped).timeline()
        assertEquals("Fell back to the stop", steps.last().text)
        assertEquals(stopped, steps.last().date)
        assertEquals(CallStepKind.BAD, steps.last().kind)
    }

    @Test
    fun `a stop after target 1 scored before its date was kept is undated`() {
        val steps = call(Outcome.PARTIAL_HIT, stoppedAfterPartial = true, stoppedOn = null).timeline()
        assertEquals("Fell back to the stop", steps.last().text)
        assertNull(steps.last().date)
    }

    @Test
    fun `a next-day call that ran out of time names its own window`() {
        val steps = call(Outcome.EXPIRED, tPlusOne = true).timeline()
        assertEquals("Ran out of time", steps.last().text)
        assertEquals("A next-day call, over after its 2 trading days", steps.last().detail)
    }

    @Test
    fun `an open call is running with target 1 still to come`() {
        val steps = call(Outcome.OPEN, settledOn = null).timeline()
        assertEquals(CallStepKind.ACTIVE, steps[2].kind)
        assertEquals("4 trading days so far", steps[2].detail)
        assertEquals(CallStepKind.WAITING, steps.last().kind)
    }
}
