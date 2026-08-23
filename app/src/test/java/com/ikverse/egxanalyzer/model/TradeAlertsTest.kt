package com.ikverse.egxanalyzer.model

import com.ikverse.egxanalyzer.data.PortfolioCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What the user is told, and - just as deliberately - what they are not.
 *
 * The views are built by running [PortfolioCalculator] over real sessions rather than by
 * constructing a [PositionView] with the status set by hand. A hand-built view would let this file
 * assert whatever it liked about a state the scorer might never produce, which is the one way a
 * test about "when the status changes" can pass while the app says nothing.
 */
class TradeAlertsTest {

    private val called = LocalDate.of(2026, 8, 3)

    @Test
    fun `a trade seen for the first time is recorded and never announced`() {
        // Otherwise the first run after this shipped would introduce itself by announcing a stop
        // that was hit in June, and every new purchase would announce whatever the market had
        // already done to the call before the user bought it.
        val stopped = view(sessions = listOf(session(called, high = 10.2, low = 8.5)))

        val alerts = TradeAlerts.sweep(emptyMap(), listOf(stopped))

        assertTrue(alerts.changes.isEmpty())
        assertEquals(
            mapOf("AMOC@2026-08-03" to TradeState(PositionStatus.STOPPED_OUT, open = false)),
            alerts.record,
        )
    }

    @Test
    fun `reaching target 2 is announced once and not again`() {
        val open = view(sessions = listOf(session(called, high = 10.4, low = 9.9)))
        val hit = view(
            sessions = listOf(
                session(called, high = 10.4, low = 9.9),
                session(called.plusDays(1), high = 12.4, low = 10.2),
            ),
        )
        val seen = TradeAlerts.sweep(emptyMap(), listOf(open)).record

        val first = TradeAlerts.sweep(seen, listOf(hit))
        val second = TradeAlerts.sweep(seen + first.record, listOf(hit))

        assertEquals(listOf(TradeEvent.TARGET2_HIT), first.changes.map(TradeChange::event))
        assertTrue(second.changes.isEmpty())
        // Nothing moved, so nothing is written: a portfolio of fifty trades must not cost fifty
        // writes on a day when none of them did anything.
        assertTrue(second.record.isEmpty())
    }

    @Test
    fun `the stop being taken is announced`() {
        val alerts = sweepFrom(
            before = listOf(session(called, high = 10.4, low = 9.9)),
            after = listOf(
                session(called, high = 10.4, low = 9.9),
                session(called.plusDays(1), high = 10.0, low = 8.5),
            ),
        )

        assertEquals(listOf(TradeEvent.STOPPED_OUT), alerts.changes.map(TradeChange::event))
    }

    @Test
    fun `target 1 on a trade still running is announced as target 1`() {
        val alerts = sweepFrom(
            before = listOf(session(called, high = 10.4, low = 9.9)),
            after = listOf(
                session(called, high = 10.4, low = 9.9),
                session(called.plusDays(1), high = 11.3, low = 10.2),
            ),
        )

        assertEquals(listOf(TradeEvent.TARGET1_HIT), alerts.changes.map(TradeChange::event))
        assertTrue(alerts.changes.single().position.open)
    }

    @Test
    fun `a stop after target 1 is its own event, not a plain stop-out`() {
        // The status label does not move here - it stays "Partial target hit", because the call did
        // reach target 1 - and the trade closes underneath it. Watching the label alone would miss
        // this ending entirely, which is why TradeState carries `open` beside the status.
        val alerts = sweepFrom(
            before = listOf(session(called, high = 11.3, low = 10.2)),
            after = listOf(
                session(called, high = 11.3, low = 10.2),
                session(called.plusDays(1), high = 10.6, low = 8.5),
            ),
        )

        assertEquals(
            listOf(TradeEvent.STOPPED_AFTER_TARGET1),
            alerts.changes.map(TradeChange::event),
        )
    }

    @Test
    fun `the window running out on a partial hit says so rather than saying expired`() {
        val alerts = sweepFrom(
            window = 2,
            before = listOf(session(called, high = 11.3, low = 10.2)),
            after = listOf(
                session(called, high = 11.3, low = 10.2),
                session(called.plusDays(1), high = 10.6, low = 10.1),
            ),
        )

        assertEquals(
            listOf(TradeEvent.EXPIRED_ON_TARGET1),
            alerts.changes.map(TradeChange::event),
        )
    }

    @Test
    fun `a window that runs out on a trade that reached nothing is announced as out of time`() {
        val alerts = sweepFrom(
            window = 2,
            before = listOf(session(called, high = 10.4, low = 9.9)),
            after = listOf(
                session(called, high = 10.4, low = 9.9),
                session(called.plusDays(1), high = 10.3, low = 9.95),
            ),
        )

        assertEquals(listOf(TradeEvent.EXPIRED), alerts.changes.map(TradeChange::event))
    }

    @Test
    fun `a sale the user recorded is not news to the user who recorded it`() {
        val running = view(sessions = listOf(session(called, high = 10.4, low = 9.9)))
        val sold = view(
            position = position().copy(exitPrice = 10.8, exitDate = called.plusDays(1)),
            sessions = listOf(session(called, high = 10.4, low = 9.9)),
        )
        val seen = TradeAlerts.sweep(emptyMap(), listOf(running)).record

        val alerts = TradeAlerts.sweep(seen, listOf(sold))

        assertTrue(alerts.changes.isEmpty())
        // Still written down. The state has genuinely moved, and leaving it stale would have a
        // later price refresh announce the user's own sale back to them.
        assertEquals(1, alerts.record.size)
    }

    @Test
    fun `closing a trade by hand says nothing back`() {
        val running = view(sessions = listOf(session(called, high = 10.4, low = 9.9)))
        val closed = view(
            position = position().copy(closedManually = true, exitPrice = 10.8),
            sessions = listOf(session(called, high = 10.4, low = 9.9)),
        )
        val seen = TradeAlerts.sweep(emptyMap(), listOf(running)).record

        assertTrue(TradeAlerts.sweep(seen, listOf(closed)).changes.isEmpty())
    }

    @Test
    fun `a trade thrown back open by a split is recorded and not announced`() {
        // A split heals the whole stored series, so the sessions behind a settled verdict are
        // refetched and the trade goes back to open. That is the app correcting itself, and
        // "AMOC is open again" would be reporting a repair as though the market had done it.
        val hit = view(
            sessions = listOf(
                session(called, high = 10.4, low = 9.9),
                session(called.plusDays(1), high = 12.4, low = 10.2),
            ),
        )
        val healed = view(
            sessions = listOf(
                session(called, high = 10.4, low = 9.9),
                session(called.plusDays(1), high = 12.4, low = 10.2),
            ),
            priceBreaks = setOf(called.plusDays(1)),
        )
        val seen = TradeAlerts.sweep(emptyMap(), listOf(hit)).record

        val alerts = TradeAlerts.sweep(seen, listOf(healed))

        assertTrue(alerts.changes.isEmpty())
        assertEquals(
            TradeState(PositionStatus.OPEN, open = true),
            alerts.record.getValue("AMOC@2026-08-03"),
        )
    }

    @Test
    fun `a trade that has gone is forgotten`() {
        val seen = mapOf("AMOC@2026-08-03" to TradeState(PositionStatus.OPEN, open = true))

        val alerts = TradeAlerts.sweep(seen, emptyList())

        assertEquals(setOf("AMOC@2026-08-03"), alerts.forgotten)
        assertTrue(alerts.changes.isEmpty())
    }

    /** Records where a trade stood over [before], then asks what [after] changed about it. */
    private fun sweepFrom(
        before: List<DailySession>,
        after: List<DailySession>,
        window: Int = 10,
    ): TradeAlerts {
        val seen = TradeAlerts.sweep(
            emptyMap(),
            listOf(view(sessions = before, window = window)),
        ).record
        return TradeAlerts.sweep(seen, listOf(view(sessions = after, window = window)))
    }

    private fun view(
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
        entryPrice = 10.0,
        entryDate = called,
        target1 = 11.0,
        target2 = 12.0,
        stopLoss = 9.0,
        windowSessions = 10,
    )

    private fun session(date: LocalDate, high: Double, low: Double) = DailySession(
        ticker = "AMOC",
        date = date,
        high = high,
        low = low,
        close = (high + low) / 2,
        volume = 1_000.0,
        open = low,
    )
}
