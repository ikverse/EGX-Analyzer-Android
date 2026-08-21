package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

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
        // Two different sessions, and the card names both. It is scored on the day it reached the
        // target; the stop that undid it came a session later, and saying so used to mean printing
        // the target's date beside the word "stop".
        assertEquals(start.plusDays(1), scored.settledOn)
        assertEquals(start.plusDays(2), scored.stoppedOn)
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
        assertNull(scored.ambiguity)
    }

    /**
     * A call that got where it was sent and gave it back is not a call that only went against you.
     *
     * Daily figures cannot prove the target came first, so this credits the favourable order on
     * purpose rather than by accident.
     */
    @Test
    fun `a session that hits both target and stop is a partial hit that fell back`() {
        val scored = Scoring.score(
            sessions = sessions(13.0 to 8.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = null, stopLoss = 9.0,
            windowSessions = 10,
        )
        assertEquals(Outcome.PARTIAL_HIT, scored.outcome)
        assertEquals(true, scored.stoppedAfterPartial)
        // One session, so the two dates are the same one - which is the case the card words as
        // "reached target 1 and fell back to the stop on <date>" rather than as two events.
        assertEquals(start, scored.settledOn)
        assertEquals(start, scored.stoppedOn)
        assertNull(scored.ambiguity)
    }

    /**
     * The feed's precision is not a tolerance.
     *
     * Yahoo sends 32-bit floats, so GGCC's high of 1.03 arrived as 1.0299999713897705 and its
     * first target read as missed - the call was recorded as a plain stop-out on 4 August.
     */
    @Test
    fun `a high stored as a float still reaches the target it printed`() {
        val session = DailySession(
            "TEST", start, high = 1.0299999713897705, low = 0.88, close = 1.0,
            volume = null, open = 0.98,
        )

        val scored = Scoring.score(
            sessions = listOf(session),
            entryLow = 0.98, entryHigh = 0.989,
            target1 = 1.03, target2 = 1.08, stopLoss = 0.955,
            windowSessions = 5,
        )

        assertEquals(Outcome.PARTIAL_HIT, scored.outcome)
        assertEquals(true, scored.stoppedAfterPartial)
    }

    /** A millionth of slack must never turn a real miss into a hit. */
    @Test
    fun `a high a whole cent short of the target has still missed it`() {
        val scored = Scoring.score(
            sessions = sessions(11.99 to 9.9),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = null, stopLoss = 9.0,
            windowSessions = 10,
        )
        assertEquals(Outcome.OPEN, scored.outcome)
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
    fun `a call that ran out of time is worth what the window left it at`() {
        // Expired reported no return at all, which kept every expired call out of the average
        // return while leaving it inside the rate that average is read beside: a channel whose
        // calls fizzle out flat read exactly like one whose calls all resolved.
        val flat = sessions(10.0 to 9.5, 10.2 to 9.6, 10.3 to 9.7)

        val scored = Scoring.score(flat, 9.8, 10.0, 12.0, null, 8.0, windowSessions = 3)

        assertEquals(Outcome.EXPIRED, scored.outcome)
        // From the middle of the 9.8-10.0 band to 10.3, the last close of the window.
        assertEquals(4.04, scored.returnPct!!, 0.01)
        // Nothing settled: the market reached no level the call named, and the date says so.
        assertNull(scored.settledOn)
    }

    @Test
    fun `a call that ran out of time below the entry says so`() {
        val down = sessions(10.0 to 9.5, 9.4 to 9.2, 9.1 to 8.9)

        val scored = Scoring.score(down, 9.8, 10.0, 12.0, null, 8.0, windowSessions = 3)

        assertEquals(Outcome.EXPIRED, scored.outcome)
        assertEquals(-8.08, scored.returnPct!!, 0.01)
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
        // Which of the two cases it was is the whole of what the card can say about it.
        assertEquals(Ambiguity.ENTRY_AND_TARGET, scored.ambiguity)
    }

    @Test
    fun `an unorderable session stops mattering when the window settles it either way`() {
        // The entry is a fact of that session under both readings - its low traded through the band
        // - so the reader holds from its close whichever way round it happened. A later session
        // reaching the target settles the call without anyone having to know the order.
        val walk = listOf(
            DailySession("TEST", start, high = 12.5, low = 9.5, close = 12.0, volume = 1.0, open = 11.5),
            DailySession("TEST", start.plusDays(1), high = 12.5, low = 11.0, close = 12.4, volume = 1.0, open = 11.2),
        )

        val scored = Scoring.score(walk, 9.8, 10.0, 12.0, null, 9.0, windowSessions = 10)

        assertEquals(Outcome.FULL_HIT, scored.outcome)
        // The later of the two readings, because the earlier one rests on an order nothing proved.
        assertEquals(start.plusDays(1), scored.settledOn)
    }

    @Test
    fun `an unorderable session stays ambiguous when the two readings disagree`() {
        // Read one way the call reached its target on the first session; read the other it sat out
        // the window and expired. Nothing in the record can choose between them.
        val later = (1..9).map { day ->
            DailySession("TEST", start.plusDays(day.toLong()), 11.0, 10.5, 10.8, 1.0, open = 10.6)
        }
        val walk = listOf(
            DailySession("TEST", start, high = 12.5, low = 9.5, close = 12.0, volume = 1.0, open = 11.5),
        ) + later

        val scored = Scoring.score(walk, 9.8, 10.0, 12.0, null, 9.0, windowSessions = 10)

        assertEquals(Outcome.AMBIGUOUS, scored.outcome)
        assertEquals(Ambiguity.ENTRY_AND_TARGET, scored.ambiguity)
    }

    @Test
    fun `five-minute bars order an entry that came before the target`() {
        val session = listOf(
            DailySession("TEST", start, high = 12.5, low = 9.5, close = 12.0, volume = 1.0, open = 11.5),
        )

        val scored = Scoring.score(
            session, 9.8, 10.0, 12.0, null, 9.0, windowSessions = 10,
            intradayFor = { bars(10.0 to 9.5, 12.5 to 12.1) },
        )

        assertEquals(Outcome.FULL_HIT, scored.outcome)
        assertEquals(start, scored.settledOn)
    }

    @Test
    fun `five-minute bars refuse a target the buy zone never preceded`() {
        // The target was reached before the band ever traded, so the reader could not have been in
        // for it. They are in from that session on, and the target is not theirs.
        val session = listOf(
            DailySession("TEST", start, high = 12.5, low = 9.5, close = 12.0, volume = 1.0, open = 11.5),
        )

        val scored = Scoring.score(
            session, 9.8, 10.0, 12.0, null, 9.0, windowSessions = 1,
            intradayFor = { bars(12.5 to 12.1, 10.0 to 9.5) },
        )

        assertEquals(Outcome.EXPIRED, scored.outcome)
    }

    @Test
    fun `two events inside one five-minute bar cannot be ordered any finer`() {
        val session = listOf(
            DailySession("TEST", start, high = 12.5, low = 9.5, close = 12.0, volume = 1.0, open = 11.5),
        )

        val scored = Scoring.score(
            session, 9.8, 10.0, 12.0, null, 9.0, windowSessions = 10,
            intradayFor = { bars(12.5 to 9.5) },
        )

        assertEquals(Outcome.AMBIGUOUS, scored.outcome)
        // Worth telling apart from the unfetched case: this one will never be answerable.
        assertEquals(Ambiguity.SAME_INTRADAY_BAR, scored.ambiguity)
    }

    /** Bars five minutes apart on the session under test, in the order given. */
    private fun bars(vararg highLow: Pair<Double, Double>): List<IntradayBar> =
        highLow.mapIndexed { index, (high, low) ->
            IntradayBar(
                ticker = "TEST",
                at = start.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(index * 300L),
                high = high,
                low = low,
            )
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

    /**
     * A price the feed stores as 18.100000381469727 is a price of 18.10.
     *
     * Yahoo sends 32-bit floats, so a stock that opened exactly at the top of its buy zone reads as
     * opening above it, and the call is filed as unorderable rather than the partial hit it was.
     * Three real calls went that way on 3 August 2026 - CLHO on its open, UEGC on a low of
     * 2.430000066757202 against a zone ending at 2.43.
     */
    @Test
    fun `an open a fraction above the band still counts as buyable`() {
        val session = DailySession(
            "TEST", start, high = 19.72, low = 18.0, close = 18.32, volume = null,
            open = 18.100000381469727,
        )

        val scored = Scoring.score(
            sessions = listOf(session),
            entryLow = 18.0, entryHigh = 18.1,
            target1 = 19.2, target2 = 20.7, stopLoss = 17.3,
            windowSessions = 5,
        )

        assertEquals(Outcome.PARTIAL_HIT, scored.outcome)
    }

    @Test
    fun `a low a fraction above the band still touches it`() {
        val sessions = listOf(
            DailySession("TEST", start, high = 2.55, low = 2.430000066757202, close = 2.45, volume = null, open = 2.46),
            DailySession("TEST", start.plusDays(1), high = 2.70, low = 2.41, close = 2.70, volume = null, open = 2.45),
        )

        val scored = Scoring.score(
            sessions = sessions,
            entryLow = 2.4, entryHigh = 2.43,
            target1 = 2.62, target2 = 2.75, stopLoss = 2.35,
            windowSessions = 5,
        )

        assertEquals(Outcome.PARTIAL_HIT, scored.outcome)
    }

    /** A real gap is still a real gap: this is not licence to buy above the zone. */
    @Test
    fun `an open genuinely above the band is still unorderable`() {
        val session = DailySession(
            "TEST", start, high = 0.907, low = 0.85, close = 0.901, volume = null, open = 0.86,
        )

        val scored = Scoring.score(
            sessions = listOf(session),
            entryLow = 0.848, entryHigh = 0.856,
            target1 = 0.89, target2 = 0.92, stopLoss = 0.825,
            windowSessions = 5,
        )

        assertEquals(Outcome.AMBIGUOUS, scored.outcome)
    }

    /** The tolerance is for the entry only. A target is either met or it is not. */
    @Test
    fun `a high a fraction short of the target has not reached it`() {
        val session = DailySession(
            "TEST", start, high = 11.999, low = 9.8, close = 11.9, volume = null, open = 9.9,
        )

        val scored = Scoring.score(
            sessions = listOf(session),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 5,
        )

        assertEquals(Outcome.OPEN, scored.outcome)
    }

    /**
     * A split inside the window, which is the whole point of the outcome.
     *
     * Without it this is the worst kind of wrong the app can be: the prices halve, every level the
     * channel printed is suddenly far above the market, and the call is filed as a stop-out. The
     * channel loses the call, the ranking moves, and nothing anywhere looks broken.
     */
    @Test
    fun `a split inside the window is not a stop-out`() {
        val split = sessions(10.0 to 9.5, 11.0 to 10.0, 5.5 to 5.0)

        val judged = Scoring.score(
            sessions = split,
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )
        // What it does without being told, and what it must stop doing.
        assertEquals(Outcome.STOPPED, judged.outcome)

        val scored = Scoring.score(
            sessions = split,
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
            priceBreaks = setOf(start.plusDays(2)),
        )

        assertEquals(Outcome.PRICE_BREAK, scored.outcome)
        assertEquals(false, scored.outcome.judged)
        // Nothing is reported that would have been measured across the break.
        assertNull(scored.returnPct)
        assertNull(scored.peakHigh)
        assertNull(scored.troughLow)
        assertNull(scored.settledOn)
    }

    @Test
    fun `a split after the window closes leaves the call judged`() {
        // The window is three sessions long here and the split lands later, so the levels and every
        // price they were compared with are in the same money. Un-judging it would throw away a
        // verdict the app is entitled to.
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 12.5 to 11.0, 14.5 to 12.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 3,
            priceBreaks = setOf(start.plusDays(9)),
        )

        assertEquals(Outcome.FULL_HIT, scored.outcome)
    }

    @Test
    fun `a split before the call was made does not touch it`() {
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 12.5 to 11.0, 14.5 to 12.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 3,
            priceBreaks = setOf(start.minusDays(1)),
        )

        assertEquals(Outcome.FULL_HIT, scored.outcome)
    }

    @Test
    fun `a split on the first session of the window still costs the call`() {
        // Deliberate, and the one case where this can take a call it did not have to. The levels
        // were printed before that session opened, so they are in the old money whatever the
        // sessions say. A rate missing a call is honest; a rate counting a phantom loss is not.
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 12.5 to 11.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 3,
            priceBreaks = setOf(start),
        )

        assertEquals(Outcome.PRICE_BREAK, scored.outcome)
    }

    @Test
    fun `a stock with no break scores exactly as it did before`() {
        // The default is empty, so every existing caller is unaffected.
        val withoutArgument = Scoring.score(
            sessions = sessions(10.0 to 9.5, 12.5 to 11.0, 14.5 to 12.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )
        val withEmptySet = Scoring.score(
            sessions = sessions(10.0 to 9.5, 12.5 to 11.0, 14.5 to 12.0),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
            priceBreaks = emptySet(),
        )

        assertEquals(withoutArgument, withEmptySet)
        assertEquals(Outcome.FULL_HIT, withoutArgument.outcome)
    }

    @Test
    fun `a T plus one call reaching its target on the sell session is a full hit`() {
        // Bought on the session it was made for, sold on the next: the whole of the trade the card
        // described, and a window that stopped before it would judge a call nobody made.
        val scored = Scoring.score(
            sessions = sessions(10.0 to 9.5, 14.5 to 10.2),
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = Scoring.T_PLUS_ONE_WINDOW_SESSIONS,
            entrySessions = Scoring.T_PLUS_ONE_ENTRY_SESSIONS,
        )

        assertEquals(Outcome.FULL_HIT, scored.outcome)
        assertEquals(start.plusDays(1), scored.settledOn)
    }

    @Test
    fun `a T plus one call whose target arrives after the sell session expires`() {
        val prices = sessions(10.0 to 9.5, 11.0 to 10.2, 14.5 to 11.0)
        val tPlusOne = Scoring.score(
            sessions = prices,
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = Scoring.T_PLUS_ONE_WINDOW_SESSIONS,
            entrySessions = Scoring.T_PLUS_ONE_ENTRY_SESSIONS,
        )
        // The same prices under the scoring setting are a full hit. The window is the only
        // difference between the two, which is the whole of what a T+1 card changes.
        val ordinary = Scoring.score(
            sessions = prices,
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )

        assertEquals(Outcome.EXPIRED, tPlusOne.outcome)
        assertEquals(Outcome.FULL_HIT, ordinary.outcome)
    }

    @Test
    fun `a T plus one buy zone that only trades on the sell session is not judged`() {
        // The instruction was to buy on the first of these and sell on the second. The band never
        // traded on the buy session, so there was no trade to take - and expiring it would count a
        // loss against a channel for a trade nobody could have been in.
        val prices = sessions(11.0 to 10.5, 14.5 to 9.5)
        val tPlusOne = Scoring.score(
            sessions = prices,
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = Scoring.T_PLUS_ONE_WINDOW_SESSIONS,
            entrySessions = Scoring.T_PLUS_ONE_ENTRY_SESSIONS,
        )
        // An ordinary call is judged on the same prices: its band was still being offered on the
        // second session, so it was taken there and the target counts.
        val ordinary = Scoring.score(
            sessions = prices,
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )

        assertEquals(Outcome.ENTRY_NOT_REACHED, tPlusOne.outcome)
        // Unjudged is the point of it: it says nothing about the channel either way.
        assertEquals(false, Outcome.ENTRY_NOT_REACHED.judged)
        assertEquals(Outcome.FULL_HIT, ordinary.outcome)
    }

    @Test
    fun `an unshortened entry window scores exactly as it did before`() {
        // The default is the whole window, so every existing caller is unaffected.
        val prices = sessions(11.0 to 10.5, 14.5 to 9.5)
        val withoutArgument = Scoring.score(
            sessions = prices,
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
        )
        val spelledOut = Scoring.score(
            sessions = prices,
            entryLow = 9.8, entryHigh = 10.0,
            target1 = 12.0, target2 = 14.0, stopLoss = 9.0,
            windowSessions = 10,
            entrySessions = 10,
        )

        assertEquals(withoutArgument, spelledOut)
    }
}
