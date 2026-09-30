package com.ikverse.egxanalyzer.model

import com.ikverse.egxanalyzer.data.PortfolioCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What happened on a session, and - just as deliberately - which session it is filed under.
 *
 * Every trade here is built by running [PortfolioCalculator] over real sessions and every verdict
 * by running [Scoring], rather than by setting an outcome on a hand-built object. The reason is the
 * one `TradeAlertsTest` gives: a hand-built view lets a test assert whatever it likes about a state
 * the scorer might never produce, which is exactly how a test about "what the market did" passes
 * while the card stays empty.
 *
 * The dating is the point of most of this file. `TradeAlerts` answers "what should I say now", so
 * it is allowed to notice a week-old stop today; a digest that did the same would pile a week of
 * news onto whichever day the phone was switched on.
 */
class SessionDigestTest {

    private val called = LocalDate.of(2026, 8, 3)
    private val second = called.plusDays(1)
    private val third = called.plusDays(2)
    private val week = listOf(third, second, called)

    // ── Trades ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a target is filed under the session the market reached it, not the day it was noticed`() {
        // The whole difference between this and a notification sweep. A phone that was off for the
        // week comes back and derives all three sessions; the target belongs to the one it happened
        // on, and the other two say nothing about it.
        val trade = trade(
            sessions = listOf(
                session(called, high = 10.4, low = 9.9),
                session(second, high = 12.4, low = 10.2),
                session(third, high = 12.6, low = 12.1),
            ),
        )

        val digests = SessionDigest.build(week, listOf(trade), emptyList()).associateBy { it.date }

        assertEquals(listOf(DayEventKind.TRADE_TARGET2), digests.getValue(second).kinds())
        assertTrue(digests.getValue(third).events.isEmpty())
        assertTrue(digests.getValue(called).events.isEmpty())
    }

    @Test
    fun `a stop after target 1 is two events on the two sessions that caused them`() {
        val trade = trade(
            sessions = listOf(
                session(called, high = 10.4, low = 9.9),
                session(second, high = 11.3, low = 10.2),
                session(third, high = 10.6, low = 8.5),
            ),
        )

        val digests = SessionDigest.build(week, listOf(trade), emptyList()).associateBy { it.date }

        assertEquals(listOf(DayEventKind.TRADE_TARGET1), digests.getValue(second).kinds())
        assertEquals(
            listOf(DayEventKind.TRADE_STOPPED_AFTER_TARGET1),
            digests.getValue(third).kinds(),
        )
    }

    @Test
    fun `target 1 and the stop inside one session are one event, not two`() {
        // Both happened on that session and reporting both would put two pieces of news about one
        // stock on one day. The stop is the half worth keeping - it is where the trade ended.
        val trade = trade(
            sessions = listOf(
                session(called, high = 10.4, low = 9.9),
                session(second, high = 11.3, low = 8.5),
            ),
        )

        val digests = SessionDigest.build(week, listOf(trade), emptyList()).associateBy { it.date }

        assertEquals(
            listOf(DayEventKind.TRADE_STOPPED_AFTER_TARGET1),
            digests.getValue(second).kinds(),
        )
    }

    @Test
    fun `a window running out lands on the deadline session`() {
        val trade = trade(
            window = 2,
            sessions = listOf(
                session(called, high = 10.4, low = 9.9),
                session(second, high = 10.3, low = 9.95),
            ),
        )

        val digests = SessionDigest.build(week, listOf(trade), emptyList()).associateBy { it.date }

        assertEquals(listOf(DayEventKind.TRADE_RAN_OUT), digests.getValue(second).kinds())
        assertTrue(digests.getValue(called).events.isEmpty())
    }

    @Test
    fun `a partial hit whose window then runs out says both, on their own sessions`() {
        val trade = trade(
            window = 3,
            sessions = listOf(
                session(called, high = 10.4, low = 9.9),
                session(second, high = 11.3, low = 10.2),
                session(third, high = 11.4, low = 10.9),
            ),
        )

        val digests = SessionDigest.build(week, listOf(trade), emptyList()).associateBy { it.date }

        assertEquals(listOf(DayEventKind.TRADE_TARGET1), digests.getValue(second).kinds())
        assertEquals(listOf(DayEventKind.TRADE_RAN_OUT), digests.getValue(third).kinds())
    }

    @Test
    fun `a trade whose prices changed scale says nothing at all`() {
        // The app refuses to value one - the entry was paid in the old money and every price since
        // is quoted in the new - so a card announcing a stop it has just admitted it cannot read
        // would be the one place that refusal did not hold.
        val trade = trade(
            sessions = listOf(
                session(called, high = 10.4, low = 9.9),
                session(second, high = 12.4, low = 10.2),
            ),
            priceBreaks = setOf(second),
        )

        assertTrue(SessionDigest.build(week, listOf(trade), emptyList()).all { it.events.isEmpty() })
    }

    @Test
    fun `a sale the user recorded is not something the market did`() {
        val trade = trade(
            position = position().copy(exitPrice = 10.8, exitDate = second, closedManually = true),
            sessions = listOf(
                session(called, high = 10.4, low = 9.9),
                session(second, high = 10.6, low = 10.1),
            ),
        )

        assertTrue(SessionDigest.build(week, listOf(trade), emptyList()).all { it.events.isEmpty() })
    }

    // ── Calls ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a call the user holds is reported once, as their trade`() {
        // Two entries for one stock - the channel's call reaching a target and the trade reaching
        // it - would be the card reporting one event twice, and the trade is the one carrying the
        // money.
        val sessions = listOf(
            session(called, high = 10.4, low = 9.9),
            session(second, high = 12.4, low = 10.2),
        )
        val digests = SessionDigest.build(
            week,
            listOf(trade(sessions = sessions)),
            listOf(scoredCall(sessions = sessions)),
        ).associateBy { it.date }

        assertEquals(listOf(DayEventKind.TRADE_TARGET2), digests.getValue(second).kinds())
    }

    @Test
    fun `an untaken call reaching a target is reported as a call`() {
        val sessions = listOf(
            session(called, high = 10.4, low = 9.9),
            session(second, high = 12.4, low = 10.2),
        )

        val digests = SessionDigest.build(week, emptyList(), listOf(scoredCall(sessions = sessions)))
            .associateBy { it.date }

        assertEquals(listOf(DayEventKind.CALL_TARGET2), digests.getValue(second).kinds())
    }

    @Test
    fun `a re-posted call says nothing, because it is the call it repeats`() {
        val sessions = listOf(
            session(called, high = 10.4, low = 9.9),
            session(second, high = 12.4, low = 10.2),
        )
        val repeat = scoredCall(sessions = sessions).copy(repeatOf = called.minusDays(1))

        val digests = SessionDigest.build(week, emptyList(), listOf(repeat))

        assertTrue(digests.all { it.events.isEmpty() })
        // Nor does it count as a new call: a source running a daily table would otherwise look like
        // a busier session than one that posts when it has something to say.
        assertEquals(0, digests.first { it.date == called }.newCalls)
    }

    @Test
    fun `coming into range is dated on the session the close crossed in`() {
        val call = scoredCall(
            sessions = listOf(
                session(called, high = 12.0, low = 11.4),
                session(second, high = 10.6, low = 10.0),
                session(third, high = 10.5, low = 10.1),
            ),
        )

        val digests = SessionDigest.build(week, emptyList(), listOf(call)).associateBy { it.date }

        // The crossing in, on its own session. Staying inside the band on the next one is not a
        // second crossing, and the session before it was outside is not one either.
        assertEquals(listOf(DayEventKind.CALL_IN_RANGE), digests.getValue(second).kinds())
        assertTrue(digests.getValue(third).events.isEmpty())
        assertTrue(digests.getValue(called).events.isEmpty())
        assertEquals(10.3, digests.getValue(second).events.single().price!!, 1e-9)
    }

    @Test
    fun `the first session of a window is never a crossing`() {
        // There is nothing before it to have crossed from, and a call printed with the price
        // already inside its own zone is the new call rather than something that happened to it.
        val call = scoredCall(
            sessions = listOf(
                session(called, high = 10.5, low = 10.1),
                session(second, high = 10.4, low = 10.2),
            ),
        )

        assertTrue(SessionDigest.build(week, emptyList(), listOf(call)).all { it.events.isEmpty() })
    }

    @Test
    fun `a session the feed went quiet on does not turn an old band into news`() {
        // The rule CallAlerts needed for the same reason: dropping the reading would forget which
        // side the price was last on, so a band it had been sitting in for a fortnight would be
        // reported as newly reached the day prices came back.
        val call = scoredCall(
            sessions = listOf(
                session(called, high = 10.5, low = 10.1),
                DailySession("AMOC", second, high = null, low = null, close = null, volume = null),
                session(third, high = 10.4, low = 10.2),
            ),
        )

        assertTrue(SessionDigest.build(week, emptyList(), listOf(call)).all { it.events.isEmpty() })
    }

    // ── The session as a whole ───────────────────────────────────────────────────────────────

    @Test
    fun `new calls are counted per session, with the sources behind them`() {
        val calls = listOf(
            scoredCall(ticker = "AMOC", channel = "one"),
            scoredCall(ticker = "COMI", channel = "one"),
            scoredCall(ticker = "SWDY", channel = "two"),
        )

        val digest = SessionDigest.build(week, emptyList(), calls).first { it.date == called }

        assertEquals(3, digest.newCalls)
        assertEquals(2, digest.newCallSources)
        // Counted, never stored: the calls themselves are already the record of what was published.
        assertTrue(digest.events.isEmpty())
    }

    @Test
    fun `an event outside the sessions asked for is dropped rather than filed under the nearest`() {
        val trade = trade(
            sessions = listOf(
                session(called, high = 10.4, low = 9.9),
                session(second, high = 12.4, low = 10.2),
            ),
        )

        val digests = SessionDigest.build(listOf(third), listOf(trade), emptyList())

        assertEquals(1, digests.size)
        assertTrue(digests.single().events.isEmpty())
    }

    @Test
    fun `the user's own trades are listed before the calls they only watched`() {
        val hit = listOf(
            session(called, high = 10.4, low = 9.9),
            session(second, high = 12.4, low = 10.2),
        )
        val stopped = listOf(
            session(called, ticker = "COMI", high = 10.4, low = 9.9),
            session(second, ticker = "COMI", high = 10.0, low = 8.5),
        )

        val digest = SessionDigest.build(
            week,
            listOf(trade(sessions = hit)),
            listOf(scoredCall(ticker = "COMI", channel = "two", sessions = stopped)),
        ).first { it.date == second }

        assertEquals(
            listOf(DayEventKind.TRADE_TARGET2, DayEventKind.CALL_STOPPED),
            digest.kinds(),
        )
    }

    @Test
    fun `a stop outranks a target in the one colour the heading gets`() {
        // Deliberately asymmetric. A green heading over a session that also took a stop is the card
        // burying the news the reader most needs before they read anything else.
        val hit = listOf(
            session(called, high = 10.4, low = 9.9),
            session(second, high = 12.4, low = 10.2),
        )
        val stopped = listOf(
            session(called, ticker = "COMI", high = 10.4, low = 9.9),
            session(second, ticker = "COMI", high = 10.0, low = 8.5),
        )

        val digest = SessionDigest.build(
            week,
            listOf(trade(sessions = hit)),
            listOf(scoredCall(ticker = "COMI", channel = "two", sessions = stopped)),
        ).first { it.date == second }

        assertEquals(EventTone.LOSS, digest.headline)
        assertEquals(1, digest.targets)
        assertEquals(1, digest.stops)
        assertEquals(1, digest.heldEvents)
    }

    @Test
    fun `a session where nothing happened is reported, not omitted`() {
        val digests = SessionDigest.build(week, emptyList(), emptyList())

        assertEquals(week, digests.map(SessionDigest::date))
        assertTrue(digests.all(SessionDigest::isEmpty))
        assertNull(digests.first().headline)
    }

    @Test
    fun `two channels calling one stock on one session are two events, not one`() {
        // The key carries the channel for exactly this: a key they shared would silently swallow
        // the second, and the two printed different buy zones.
        val sessions = listOf(
            session(called, high = 10.4, low = 9.9),
            session(second, high = 12.4, low = 10.2),
        )
        val calls = listOf(
            scoredCall(channel = "one", sessions = sessions),
            scoredCall(channel = "two", sessions = sessions),
        )

        val digest = SessionDigest.build(week, emptyList(), calls).first { it.date == second }

        assertEquals(2, digest.events.size)
        assertEquals(2, digest.events.map(DayEvent::id).toSet().size)
    }

    // ── Held scope, which is the Portfolio's card ────────────────────────────────────────────

    /**
     * The Portfolio reports the reader's own trades and the calls they only watched are Insights'.
     *
     * Both cards are still one pass over one digest - `heldOnly` filters what was built rather than
     * building a second one - so the tests below are about scope and never about arithmetic.
     */
    @Test
    fun `held scope keeps the trade and drops the call on a session that saw both`() {
        val sessions = listOf(
            session(called, high = 10.4, low = 9.9),
            session(second, high = 12.4, low = 10.2),
        )
        val trade = trade(sessions = sessions)
        val call = scoredCall(ticker = "COMI", sessions = sessions)

        val digest = SessionDigest.build(week, listOf(trade), listOf(call)).first { it.date == second }

        assertEquals(listOf(DayEventKind.TRADE_TARGET2, DayEventKind.CALL_TARGET2), digest.kinds())
        assertEquals(listOf(DayEventKind.TRADE_TARGET2), digest.heldOnly().kinds())
    }

    /**
     * The reading this split exists to prevent.
     *
     * A busy session that the reader had no money in was reported on the tab holding their money as
     * though something of theirs had happened - they had to open the card to find nothing of theirs
     * in it.
     */
    @Test
    fun `a session of nothing but calls is empty to the Portfolio and not to Insights`() {
        val sessions = listOf(
            session(called, high = 10.4, low = 9.9),
            session(second, high = 12.4, low = 10.2),
        )
        val call = scoredCall(sessions = sessions)

        val digest = SessionDigest.build(week, emptyList(), listOf(call)).first { it.date == second }

        assertTrue(digest.events.isNotEmpty())
        assertTrue(digest.heldOnly().events.isEmpty())
        assertTrue(digest.heldOnly().isEmpty)
    }

    /** What channels published is a fact about the sources, not about anything the reader owns. */
    @Test
    fun `new calls do not travel into held scope`() {
        val calls = listOf(
            scoredCall(ticker = "AMOC", channel = "one"),
            scoredCall(ticker = "SWDY", channel = "two"),
        )

        val digest = SessionDigest.build(week, emptyList(), calls).first { it.date == called }

        assertEquals(2, digest.newCalls)
        assertEquals(2, digest.newCallSources)
        assertEquals(0, digest.heldOnly().newCalls)
        assertEquals(0, digest.heldOnly().newCallSources)
    }

    /**
     * Every figure the card draws follows the scope, because every one is derived from the events.
     *
     * The headline is the one worth pinning: it decides the colour of the card while it is folded,
     * and a red heading over a session whose only stop was somebody else's call would be the
     * Portfolio reporting a loss the reader never took.
     */
    @Test
    fun `the headline follows the scope rather than the session`() {
        val won = listOf(
            session(called, high = 10.4, low = 9.9),
            session(second, high = 12.4, low = 10.2),
        )
        val lost = listOf(
            session(called, high = 10.4, low = 9.9),
            session(second, high = 10.4, low = 8.5),
        )
        val trade = trade(sessions = won)
        val call = scoredCall(ticker = "COMI", sessions = lost)

        val digest = SessionDigest.build(week, listOf(trade), listOf(call)).first { it.date == second }

        // A stop outranks a target, so the whole session reads as a loss...
        assertEquals(EventTone.LOSS, digest.headline)
        assertEquals(1, digest.stops)
        // ...while the reader's own half of it was a win outright.
        assertEquals(EventTone.GAIN, digest.heldOnly().headline)
        assertEquals(0, digest.heldOnly().stops)
        assertEquals(1, digest.heldOnly().targets)
    }

    /** A call coming into range is news on Insights and nothing at all to a reader not in it. */
    @Test
    fun `a stock coming into range never reaches held scope`() {
        val sessions = listOf(
            session(called, high = 9.5, low = 9.2),
            session(second, high = 10.3, low = 10.1),
        )
        val call = scoredCall(sessions = sessions)

        val digest = SessionDigest.build(week, emptyList(), listOf(call)).first { it.date == second }

        assertEquals(0, digest.heldOnly().inRange)
        assertTrue(digest.heldOnly().events.isEmpty())
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────────────────

    private fun SessionDigest.kinds() = events.map(DayEvent::kind)

    private fun trade(
        position: Position = position(),
        sessions: List<DailySession>,
        window: Int = 10,
        priceBreaks: Set<LocalDate> = emptySet(),
    ) = PortfolioCalculator.evaluate(
        position = position.copy(windowSessions = window),
        sessions = sessions,
        currentPrice = sessions.last().close,
        currentPriceOn = sessions.last().date,
        today = called.plusDays(30),
        priceBreaks = priceBreaks,
    )

    private fun position() = Position(
        ticker = "AMOC",
        recommendationDate = called,
        channel = "one",
        entryPrice = 10.0,
        entryDate = called,
        target1 = 11.0,
        target2 = 12.0,
        stopLoss = 9.0,
        windowSessions = 10,
    )

    /**
     * A call put through the real scorer, the way `PerformanceCalculator` does it.
     *
     * The dates this file is about - where a call settled, where its stop broke - are the scorer's
     * own, so setting them by hand would be testing the fixture rather than the rule.
     */
    private fun scoredCall(
        ticker: String = "AMOC",
        channel: String = "one",
        sessions: List<DailySession> = emptyList(),
        entryLow: Double? = 10.0,
        entryHigh: Double? = 10.5,
    ): ScoredCall {
        val call = ScoredCall(
            ticker = ticker,
            companyEnglish = null,
            companyArabic = null,
            channel = channel,
            channelId = 1L,
            openedOn = called,
            entryLow = entryLow,
            entryHigh = entryHigh,
            target1 = 11.0,
            target2 = 12.0,
            stopLoss = 9.0,
            outcome = Outcome.UNPRICED,
            settledOn = null,
            peakHigh = null,
            troughLow = null,
            returnPct = null,
            sessionsElapsed = 0,
        )
        if (sessions.isEmpty()) return call
        val scored = Scoring.score(
            sessions = sessions,
            entryLow = call.entryLow,
            entryHigh = call.entryHigh,
            target1 = call.target1,
            target2 = call.target2,
            stopLoss = call.stopLoss,
            windowSessions = call.windowSessions,
            entrySessions = call.entrySessions,
        )
        return call.copy(
            outcome = scored.outcome,
            settledOn = scored.settledOn,
            peakHigh = scored.peakHigh,
            peakOn = scored.peakOn,
            troughLow = scored.troughLow,
            troughOn = scored.troughOn,
            returnPct = scored.returnPct,
            sessionsElapsed = scored.sessionsElapsed,
            stoppedAfterPartial = scored.stoppedAfterPartial,
            stoppedOn = scored.stoppedOn,
            windowComplete = scored.windowComplete,
            sessions = sessions,
        )
    }

    private fun session(
        date: LocalDate,
        high: Double,
        low: Double,
        ticker: String = "AMOC",
    ) = DailySession(
        ticker = ticker,
        date = date,
        high = high,
        low = low,
        close = (high + low) / 2,
        volume = 1_000.0,
        open = low,
    )
}
