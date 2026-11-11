package com.ikverse.egxanalyzer.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one sentence an open trade's card says, its order of urgency, and the pace beside its days.
 *
 * Every trade here is bought at 100 with a stop at 95, target 1 at 110 and target 2 at 120, over a
 * ten-session window, unless a test says otherwise.
 */
class TradeBriefTest {

    private fun view(
        current: Double? = 104.0,
        remaining: Int = 6,
        window: Int = 10,
        stop: Double? = 95.0,
        target1: Double? = 110.0,
        target2: Double? = 120.0,
        marketStatus: PositionStatus = PositionStatus.OPEN,
        overdueDays: Long = 0,
        deadline: LocalDate? = null,
        keepOpen: Boolean = false,
        scaleChanged: Boolean = false,
        stoppedAfterPartial: Boolean = false,
    ) = PositionView(
        position = Position(
            ticker = "COMI", recommendationDate = LocalDate.of(2026, 11, 2), entryPrice = 100.0,
            entryDate = LocalDate.of(2026, 11, 2), stopLoss = stop, target1 = target1, target2 = target2,
            keepOpen = keepOpen, windowSessions = window,
        ),
        status = marketStatus, marketStatus = marketStatus, open = true, currentPrice = current,
        exitPrice = current, realized = false, returnPct = null, sessionsElapsed = window - remaining,
        sessionsRemaining = remaining, deadlineDate = deadline, settledOn = null, ranOutOfTime = false,
        overdueDays = overdueDays, priceScaleChanged = scaleChanged, stoppedAfterPartial = stoppedAfterPartial,
    )

    @Test
    fun `an ordinary running trade says how far it is to target 1`() {
        val brief = tradeBrief(view(current = 104.0))
        // 110 is 5.8% above a close of 104.
        assertEquals("5.8% to target 1", brief.sentence)
        assertEquals(BriefTone.PLAIN, brief.tone)
    }

    @Test
    fun `with no first target it measures to target 2`() {
        assertEquals("15.4% to target 2", tradeBrief(view(current = 104.0, target1 = null)).sentence)
    }

    @Test
    fun `with no targets at all it says where it stands against what was paid`() {
        assertEquals("4.0% above what you paid", tradeBrief(view(current = 104.0, target1 = null, target2 = null)).sentence)
        assertEquals("3.0% below what you paid", tradeBrief(view(current = 97.0, target1 = null, target2 = null)).sentence)
    }

    @Test
    fun `close to its stop is bad news and says the distance`() {
        val brief = tradeBrief(view(current = 96.0), NeedReason.NEAR_STOP)
        assertEquals("1.0% above its stop", brief.sentence)
        assertEquals(BriefTone.BAD, brief.tone)
    }

    @Test
    fun `close to target 2 is good news and says the distance`() {
        val brief = tradeBrief(view(current = 119.0, marketStatus = PositionStatus.PARTIAL_TARGET_HIT), NeedReason.NEAR_TARGET2)
        assertEquals("0.8% from target 2", brief.sentence)
        assertEquals(BriefTone.GOOD, brief.tone)
    }

    @Test
    fun `target 1 reached says what is left to target 2`() {
        val brief = tradeBrief(view(current = 112.0, marketStatus = PositionStatus.PARTIAL_TARGET_HIT))
        assertEquals("Target 1 reached, 7.1% to target 2", brief.sentence)
        assertEquals(BriefTone.GOOD, brief.tone)
        // Reached on an earlier session and fallen back since: still reached.
        assertTrue(tradeBrief(view(current = 106.0, marketStatus = PositionStatus.PARTIAL_TARGET_HIT)).sentence.startsWith("Target 1 reached"))
        assertEquals("Target 1 reached", tradeBrief(view(current = 112.0, target2 = null)).sentence)
    }

    @Test
    fun `a passed deadline outranks a level the price is close to`() {
        val brief = tradeBrief(view(current = 96.0, remaining = 0, overdueDays = 3, keepOpen = true), NeedReason.NEAR_STOP)
        assertEquals("Past its deadline by 3 days", brief.sentence)
        assertEquals(BriefTone.BAD, brief.tone)
        assertEquals("Past its deadline by 1 day", tradeBrief(view(remaining = 0, overdueDays = 1, keepOpen = true)).sentence)
    }

    @Test
    fun `the day the deadline passes is said before a day has gone by`() {
        val brief = tradeBrief(view(remaining = 0, deadline = LocalDate.of(2026, 11, 16), keepOpen = true))
        assertEquals("Its deadline has passed", brief.sentence)
    }

    @Test
    fun `a broken stop outranks everything but a change of scale`() {
        val kept = view(current = 93.0, remaining = 0, overdueDays = 4, keepOpen = true, marketStatus = PositionStatus.STOPPED_OUT)
        assertEquals("Below its stop, and you are keeping it open", tradeBrief(kept).sentence)
        // At the stop on today's close, before the scorer has caught up.
        assertEquals(BriefTone.BAD, tradeBrief(view(current = 95.0)).tone)
        assertTrue(tradeBrief(view(current = 104.0, stoppedAfterPartial = true)).sentence.startsWith("Below its stop"))

        val scaled = tradeBrief(view(current = 9.3, scaleChanged = true, marketStatus = PositionStatus.STOPPED_OUT))
        assertEquals("Its prices changed scale, so it cannot be valued", scaled.sentence)
        assertNull(scaled.days)
        assertNull(scaled.pace)
    }

    @Test
    fun `a trade with no price says so`() {
        val brief = tradeBrief(view(current = null))
        assertEquals("No price for it yet", brief.sentence)
        assertNull(brief.pace)
    }

    @Test
    fun `the days are counted from what is left of the window`() {
        val brief = tradeBrief(view(remaining = 6))
        assertEquals(DaySegments(used = 4, total = 10, late = false), brief.days)
        assertEquals("6 of 10 trading days left", brief.daysLine)
        assertEquals("1 of 2 trading days left", tradeBrief(view(remaining = 1, window = 2)).daysLine)
    }

    @Test
    fun `a passed deadline fills every segment and says who is keeping it open`() {
        val brief = tradeBrief(view(remaining = 0, overdueDays = 2, keepOpen = true))
        assertEquals(DaySegments(used = 10, total = 10, late = true), brief.days)
        assertEquals("All 10 trading days used · you are keeping it open", brief.daysLine)
    }

    @Test
    fun `pace compares the way to target 1 with the time used`() {
        // 4 of 10 days used. 40% of the way is on pace; within 15 points either side still is.
        assertEquals(Pace.ON, tradeBrief(view(current = 104.0)).pace?.pace)
        assertEquals(Pace.ON, tradeBrief(view(current = 102.6)).pace?.pace)
        assertEquals(Pace.AHEAD, tradeBrief(view(current = 105.6)).pace?.pace)
        assertEquals(Pace.BEHIND, tradeBrief(view(current = 102.4)).pace?.pace)
    }

    @Test
    fun `behind pace says both shares, and none of the way below the entry`() {
        val behind = tradeBrief(view(current = 102.0, remaining = 3)).pace
        assertEquals("Behind pace: 70% of the time used, 20% of the way", behind?.words)
        val under = tradeBrief(view(current = 98.0, remaining = 3)).pace
        assertEquals("Behind pace: 70% of the time used, none of the way", under?.words)
        assertEquals("On pace", tradeBrief(view(current = 104.0)).pace?.words)
    }

    @Test
    fun `pace is left out where it would mean nothing`() {
        // No session traded yet.
        assertNull(tradeBrief(view(remaining = 10)).pace)
        // Deadline passed.
        assertNull(tradeBrief(view(remaining = 0, overdueDays = 1, keepOpen = true)).pace)
        // Target 1 already reached.
        assertNull(tradeBrief(view(current = 112.0)).pace)
        // No first target, or one at or under what was paid.
        assertNull(tradeBrief(view(target1 = null)).pace)
        assertNull(tradeBrief(view(target1 = 100.0)).pace)
        // The stop already broken.
        assertNull(tradeBrief(view(current = 94.0)).pace)
        // Close to its stop is still inside the window, so the clock is still read.
        assertEquals(Pace.BEHIND, tradeBrief(view(current = 96.0), NeedReason.NEAR_STOP).pace?.pace)
    }
}
