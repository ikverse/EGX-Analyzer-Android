package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ScoringTest {
    private val start = LocalDate.of(2026, 7, 20)

    private fun sessions(vararg highLow: Pair<Double, Double>): List<DailySession> =
        highLow.mapIndexed { index, (high, low) ->
            // Opens at the session low unless a test says otherwise, so the entry is buyable at the
            // open and cases that are not about entry ordering stay unaffected.
            DailySession("TEST", start.plusDays(index.toLong()), high, low, high, 1000.0, open = low)
        }

    @Test
    fun `reaching the first target settles the call`() {
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 11.0 to 10.0, 12.5 to 11.5),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )
        assertEquals(Outcome.TARGET_1, scored.outcome)
        assertEquals(start.plusDays(2), scored.settledOn)
        assertEquals(3, scored.sessionsElapsed)
        // Measured from the middle of the 9.8-10.0 band, not from the peak and not from the
        // bottom of the band.
        assertEquals(21.21, scored.returnPct!!, 0.01)
    }

    @Test
    fun `the further target wins when one session reaches both`() {
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 15.0 to 10.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )
        assertEquals(Outcome.TARGET_2, scored.outcome)
    }

    @Test
    fun `a session that hits both target and stop is reported as ambiguous`() {
        // Daily figures cannot say which came first, and picking the favourable one would inflate
        // every hit rate built on this.
        val scored = Scoring.score(
            sessions = sessions(13.0 to 8.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = null, stopLoss = 9.0,
            windowSessions = 10,
        )
        assertEquals(Outcome.AMBIGUOUS, scored.outcome)
        assertNull(scored.returnPct)
    }

    @Test
    fun `an entry that never traded is not counted against the source`() {
        val scored = Scoring.score(
            sessions = sessions(20.0 to 15.0, 21.0 to 16.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 25.0, target2 = null, stopLoss = 9.0,
            windowSessions = 10,
        )
        assertEquals(Outcome.ENTRY_NOT_REACHED, scored.outcome)
        assertEquals(false, scored.outcome.judged)
        assertEquals(21.0, scored.peakHigh!!, 0.001)
    }

    @Test
    fun `the window is counted in sessions, so a shorter one expires the same call`() {
        val walk = sessions(
            10.0 to 9.5, 10.2 to 9.6, 10.3 to 9.7, 10.4 to 9.8, 12.5 to 10.0,
        )
        val long = Scoring.score(walk, 9.8, 10.0, 12.0, null, 8.0, windowSessions = 10)
        val short = Scoring.score(walk, 9.8, 10.0, 12.0, null, 8.0, windowSessions = 3)
        assertEquals(Outcome.TARGET_1, long.outcome)
        assertEquals(Outcome.EXPIRED, short.outcome)
    }

    @Test
    fun `a call still inside its window is open rather than expired`() {
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 10.2 to 9.6),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = null, stopLoss = 8.0,
            windowSessions = 10,
        )
        assertEquals(Outcome.OPEN, scored.outcome)
    }

    @Test
    fun `a stock with no stored session cannot be judged either way`() {
        val scored = Scoring.score(emptyList(), 9.8, 10.0, 12.0, null, 8.0, windowSessions = 10)
        assertEquals(Outcome.UNPRICED, scored.outcome)
        assertEquals(false, scored.outcome.judged)
    }

    @Test
    fun `entry and target in one session count when the session opened inside the band`() {
        // The open precedes every other price of the day, so an open at or below the band means the
        // entry was available before the high that reached the target.
        val session = listOf(
            DailySession("TEST", start, high = 12.5, low = 9.5, close = 12.0, volume = 1.0, open = 9.9),
        )
        val scored = Scoring.score(session, 9.8, 10.0, 12.0, null, 9.0, windowSessions = 10)
        assertEquals(Outcome.TARGET_1, scored.outcome)
    }

    @Test
    fun `entry and target in one session are ambiguous when the session opened above the band`() {
        // The stock may have run to the target first and only fallen back into the band afterwards.
        val session = listOf(
            DailySession("TEST", start, high = 12.5, low = 9.5, close = 12.0, volume = 1.0, open = 11.5),
        )
        val scored = Scoring.score(session, 9.8, 10.0, 12.0, null, 9.0, windowSessions = 10)
        assertEquals(Outcome.AMBIGUOUS, scored.outcome)
        assertEquals(false, scored.outcome.judged)
    }

    @Test
    fun `a session with no recorded open is not assumed favourable`() {
        val session = listOf(
            DailySession("TEST", start, high = 12.5, low = 9.5, close = 12.0, volume = 1.0, open = null),
        )
        val scored = Scoring.score(session, 9.8, 10.0, 12.0, null, 9.0, windowSessions = 10)
        assertEquals(Outcome.AMBIGUOUS, scored.outcome)
    }

    @Test
    fun `entering on an earlier session leaves a later target unaffected`() {
        val walk = sessions(10.0 to 9.5, 12.5 to 11.0)
        val scored = Scoring.score(walk, 9.8, 10.0, 12.0, null, 9.0, windowSessions = 10)
        assertEquals(Outcome.TARGET_1, scored.outcome)
        assertEquals(2, scored.sessionsElapsed)
    }

    @Test
    fun `return is measured from the middle of the entry band`() {
        // Measuring from the bottom assumed the best price in the band was filled every time.
        val walk = sessions(10.0 to 9.5, 11.0 to 10.0, 12.5 to 11.5)
        val scored = Scoring.score(walk, 9.0, 11.0, 12.0, null, 8.0, windowSessions = 10)
        assertEquals(Outcome.TARGET_1, scored.outcome)
        assertEquals(20.0, scored.returnPct!!, 0.01)   // from 10.0, not from 9.0
    }

    @Test
    fun `one spelling per stock`() {
        // Sources quote the same company both ways; treating them as two splits a channel's record.
        assertEquals("AMOC", Scoring.normalizeTicker(" amoc.ca "))
        assertEquals("AMOC", Scoring.normalizeTicker("AMOC"))
    }

    @Test
    fun `the window cannot be set outside what the settings offer`() {
        assertEquals(1, Scoring.clampWindow(0))
        assertEquals(30, Scoring.clampWindow(90))
        assertEquals(10, Scoring.clampWindow(10))
    }
}
