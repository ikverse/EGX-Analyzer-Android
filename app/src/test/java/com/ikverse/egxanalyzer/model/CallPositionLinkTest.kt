package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * The key a scored call and the trade taken on it share.
 *
 * It is what carries a press from the Insights card to the Portfolio one and back, so it has to be
 * the same key the held outline is already drawn from. Anything else and a card could lead somewhere
 * its own outline disagreed with - or, more quietly, lead nowhere at all.
 */
class CallPositionLinkTest {

    private val session = LocalDate.of(2026, 7, 20)

    private fun call(
        ticker: String,
        openedOn: LocalDate = session,
        channel: String = "أخبار البورصة",
    ) = ScoredCall(
        ticker = ticker,
        companyEnglish = null,
        companyArabic = null,
        channel = channel,
        channelId = 1L,
        openedOn = openedOn,
        entryLow = null,
        entryHigh = null,
        target1 = null,
        target2 = null,
        stopLoss = null,
        outcome = Outcome.OPEN,
        settledOn = null,
        peakHigh = null,
        troughLow = null,
        returnPct = null,
        sessionsElapsed = 0,
    )

    private fun scoredSession(targetDate: LocalDate?, vararg calls: ScoredCall) = ScoredSession(
        targetDate = targetDate,
        lastRunAt = Instant.parse("2026-07-20T09:00:00Z"),
        model = "test",
        runCount = 1,
        calls = calls.toList(),
    )

    @Test
    fun `a call's key is the id of the position recorded from it`() {
        val position = Position(
            ticker = Scoring.normalizeTicker("AMOC"),
            recommendationDate = session,
            entryPrice = 1.2,
            entryDate = session,
        )
        assertEquals(position.id, call("AMOC").positionId)
    }

    @Test
    fun `the Yahoo suffix comes off, exactly as it does when a trade is recorded`() {
        assertEquals("AMOC@2026-07-20", call("AMOC.CA").positionId)
    }

    @Test
    fun `a stock called for two sessions is two trades`() {
        val july = call("AMOC").positionId
        val august = call("AMOC", openedOn = LocalDate.of(2026, 8, 3)).positionId
        assertEquals("AMOC@2026-07-20", july)
        assertEquals("AMOC@2026-08-03", august)
    }

    @Test
    fun `two channels calling one stock on one session share the trade`() {
        val first = call("AMOC", channel = "one")
        val second = call("AMOC", channel = "two")
        assertEquals(first.positionId, second.positionId)
    }

    @Test
    fun `sessionFor finds the card holding the call`() {
        val wanted = scoredSession(session, call("AMOC"), call("COMI"))
        val other = scoredSession(LocalDate.of(2026, 8, 3), call("SWDY"))
        val report = PerformanceReport(sessions = listOf(other, wanted))

        assertSame(wanted, report.sessionFor("AMOC@2026-07-20"))
    }

    @Test
    fun `sessionFor says nothing about a trade whose analysis has been deleted`() {
        val report = PerformanceReport(
            sessions = listOf(scoredSession(session, call("COMI"))),
        )
        assertNull(report.sessionFor("AMOC@2026-07-20"))
    }

    @Test
    fun `a session with no target date is still found by the date its call was made for`() {
        // The scorer dates such a call from the message itself, and a trade recorded off that card
        // is filed under the same date - so the link has to hold without a target date to lean on.
        val undated = scoredSession(null, call("AMOC", openedOn = session))
        val report = PerformanceReport(sessions = listOf(undated))

        assertSame(undated, report.sessionFor("AMOC@2026-07-20"))
    }

    @Test
    fun `callIds holds every call in the report, once each`() {
        val report = PerformanceReport(
            sessions = listOf(
                scoredSession(session, call("AMOC", channel = "one"), call("AMOC", channel = "two")),
                scoredSession(LocalDate.of(2026, 8, 3), call("COMI", openedOn = LocalDate.of(2026, 8, 3))),
            ),
        )

        assertEquals(setOf("AMOC@2026-07-20", "COMI@2026-08-03"), report.callIds)
    }
}
