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
    fun `reaching only the first of two targets is a partial hit`() {
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 11.0 to 10.0, 12.5 to 11.5),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )
        assertEquals(Outcome.PARTIAL_HIT, scored.outcome)
        assertEquals(start.plusDays(2), scored.settledOn)
        // Measured to target 1, from the middle of the 9.8-10.0 band.
        assertEquals(21.21, scored.returnPct!!, 0.01)
    }

    @Test
    fun `reaching the second target is a full hit`() {
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 11.0 to 10.0, 14.5 to 11.5),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )
        assertEquals(Outcome.FULL_HIT, scored.outcome)
        assertEquals(41.41, scored.returnPct!!, 0.01)
    }

    @Test
    fun `a call quoting one target is a full hit when it reaches it`() {
        // There is nothing further to reach, so calling it partial forever would be wrong.
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 12.5 to 11.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = null, stopLoss = 9.0,
            windowSessions = 10,
        )
        assertEquals(Outcome.FULL_HIT, scored.outcome)
    }

    @Test
    fun `a partial hit that later reaches the stop stays a partial hit`() {
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 12.5 to 11.0, 11.0 to 8.5),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )
        assertEquals(Outcome.PARTIAL_HIT, scored.outcome)
        assertEquals(true, scored.stoppedAfterPartial)
    }

    @Test
    fun `a partial hit inside a live window is not final`() {
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 12.5 to 11.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )
        assertEquals(Outcome.PARTIAL_HIT, scored.outcome)
        assertEquals(false, scored.windowComplete)
    }

    @Test
    fun `the swing across the window is reported both ways`() {
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 10.6 to 8.9, 10.2 to 9.4),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 15.0, target2 = null, stopLoss = 8.0,
            windowSessions = 10,
        )
        assertEquals(10.6, scored.peakHigh!!, 0.001)
        assertEquals(8.9, scored.troughLow!!, 0.001)
    }

    @Test
    fun `one session clearing both targets is a full hit`() {
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 15.0 to 10.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )
        assertEquals(Outcome.FULL_HIT, scored.outcome)
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
        assertEquals(Outcome.FULL_HIT, long.outcome)
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
        assertEquals(Outcome.FULL_HIT, scored.outcome)
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
        assertEquals(Outcome.FULL_HIT, scored.outcome)
        assertEquals(2, scored.sessionsElapsed)
    }

    @Test
    fun `return is measured from the middle of the entry band`() {
        // Measuring from the bottom assumed the best price in the band was filled every time.
        val walk = sessions(10.0 to 9.5, 11.0 to 10.0, 12.5 to 11.5)
        val scored = Scoring.score(walk, 9.0, 11.0, 12.0, null, 8.0, windowSessions = 10)
        assertEquals(Outcome.FULL_HIT, scored.outcome)
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

    /**
     * A session still in progress can arrive with zeros where its high and low belong.
     *
     * Twelve such rows were stored on 2 August 2026 and every call on those stocks was judged
     * stopped, because nothing trades below nothing. The window they poisoned was the current one,
     * so every source's record was being marked down by a session that had not happened yet.
     */
    @Test
    fun `a session priced at zero neither stops a call nor reaches its target`() {
        val live = DailySession("TEST", start.plusDays(1), high = 0.0, low = 0.0, close = 11.0, volume = 0.0, open = 0.0)

        val scored = Scoring.score(
            sessions = listOf(sessions(10.0 to 9.5).single(), live),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )

        assertEquals(Outcome.OPEN, scored.outcome)
        assertNull(scored.settledOn)
    }

    @Test
    fun `a zero session is not counted as the peak or the trough`() {
        val live = DailySession("TEST", start.plusDays(1), high = 0.0, low = 0.0, close = 11.0, volume = 0.0, open = 0.0)

        val scored = Scoring.score(
            sessions = listOf(sessions(10.0 to 9.5).single(), live),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )

        assertEquals(10.0, scored.peakHigh!!, 0.001)
        assertEquals(9.5, scored.troughLow!!, 0.001)
    }

    @Test
    fun `only the bad half of a session is discarded`() {
        // The feed reports a real high and a zero low on the same row often enough to matter.
        val half = DailySession("TEST", start.plusDays(1), high = 12.5, low = 0.0, close = 12.0, volume = 1.0, open = 11.0)

        val scored = Scoring.score(
            sessions = listOf(sessions(10.0 to 9.5).single(), half),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )

        assertEquals(Outcome.PARTIAL_HIT, scored.outcome)
        assertEquals(12.5, scored.peakHigh!!, 0.001)
    }

    /**
     * A stop is confirmed by a break, not by a touch.
     *
     * The sources print the rule on the cards - the stop "يتاكد بالكسر بنسبة 2%" - and judging an
     * exact touch as a loss was stricter than what they publish. Across the saved calls it read 26
     * stop-outs where there were 7, and called six reached targets losses.
     */
    @Test
    fun `touching the stop exactly is not a stop out`() {
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 10.2 to 9.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )

        assertEquals(Outcome.OPEN, scored.outcome)
    }

    @Test
    fun `a break shallower than the tolerance is not a stop out`() {
        // 8.83 is 1.9% under a stop of 9.00.
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 10.2 to 8.83),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )

        assertEquals(Outcome.OPEN, scored.outcome)
    }

    @Test
    fun `a break past the tolerance stops the call`() {
        // 8.81 is 2.1% under a stop of 9.00.
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 10.2 to 8.81),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )

        assertEquals(Outcome.STOPPED, scored.outcome)
        assertEquals(start.plusDays(1), scored.settledOn)
    }

    @Test
    fun `a stopped call is still measured to the printed stop`() {
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 10.2 to 8.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )

        assertEquals(Outcome.STOPPED, scored.outcome)
        // From the middle of the 9.8-10.0 band down to 9.00, not to the level that triggered it.
        assertEquals(-9.09, scored.returnPct!!, 0.01)
    }

    @Test
    fun `a target is still only reached at the printed level`() {
        // 11.8 is within 2% of the 12.00 target, which buys it nothing.
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.8, 11.8 to 10.5),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )

        assertEquals(Outcome.OPEN, scored.outcome)
    }
}
