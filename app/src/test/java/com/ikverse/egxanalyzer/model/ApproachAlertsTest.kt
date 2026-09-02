package com.ikverse.egxanalyzer.model

import com.ikverse.egxanalyzer.data.PortfolioCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * When the phone warns that a trade is closing on a level, and — just as deliberately — when it
 * stays quiet.
 *
 * The views are built by running [PortfolioCalculator] over real sessions rather than by
 * constructing a [PositionView] by hand, exactly as `TradeAlertsTest` does and for the same reason:
 * a hand-built view would let this file assert whatever it liked about a state the scorer might
 * never produce, which is the one way a test about approaching a level passes while the app is
 * silent.
 */
class ApproachAlertsTest {

    private val called = LocalDate.of(2026, 8, 3)

    @Test
    fun `a trade seen for the first time is recorded and never announced`() {
        // Without this the first sweep after this shipped would announce every trade that happens
        // to be sitting near a level - a whole portfolio arriving at once as though each had just
        // moved. The rule CallAlerts needed, for the reason it needed it.
        val near = view(sessions = listOf(session(called, high = 9.2, low = 9.1)))

        val alerts = ApproachAlerts.sweep(emptyMap(), listOf(near))

        assertTrue(alerts.changes.isEmpty())
        assertTrue(
            alerts.record.getValue(
                ApproachAlerts.alertKey("AMOC@2026-08-03", ApproachLevel.STOP),
            ).near,
        )
    }

    @Test
    fun `closing on the stop is announced once and not again`() {
        val away = listOf(session(called, high = 10.4, low = 10.2))
        val closing = away + session(called.plusDays(1), high = 9.2, low = 9.15)
        val seen = ApproachAlerts.sweep(emptyMap(), listOf(view(sessions = away))).record

        val first = ApproachAlerts.sweep(seen, listOf(view(sessions = closing)))
        val second = ApproachAlerts.sweep(seen + first.record, listOf(view(sessions = closing)))

        assertEquals(listOf(ApproachLevel.STOP), first.changes.map(ApproachChange::level))
        assertTrue(second.changes.isEmpty())
        // Nothing moved, so nothing is written: a portfolio of fifty trades must not cost fifty
        // writes on a day none of them went anywhere.
        assertTrue(second.record.isEmpty())
    }

    @Test
    fun `closing on target 2 is its own warning`() {
        val away = listOf(session(called, high = 10.4, low = 10.2))
        val closing = away + session(called.plusDays(1), high = 11.85, low = 11.8)
        val seen = ApproachAlerts.sweep(emptyMap(), listOf(view(sessions = away))).record

        val alerts = ApproachAlerts.sweep(seen, listOf(view(sessions = closing)))

        assertEquals(listOf(ApproachLevel.TARGET2), alerts.changes.map(ApproachChange::level))
    }

    @Test
    fun `drifting away is recorded quietly and coming back speaks again`() {
        // The one place this parts company with a plain once-only rule, and it is deliberate: a
        // price that pulls off its stop in the morning and closes on it again in the afternoon has
        // done the thing this warning exists for, twice.
        val away = listOf(session(called, high = 10.4, low = 10.2))
        val closing = away + session(called.plusDays(1), high = 9.2, low = 9.15)
        val back = closing + session(called.plusDays(2), high = 10.3, low = 10.2)
        val again = back + session(called.plusDays(3), high = 9.2, low = 9.15)

        var seen = ApproachAlerts.sweep(emptyMap(), listOf(view(sessions = away))).record
        val first = ApproachAlerts.sweep(seen, listOf(view(sessions = closing)))
        seen = seen + first.record
        val left = ApproachAlerts.sweep(seen, listOf(view(sessions = back)))
        seen = seen + left.record
        val second = ApproachAlerts.sweep(seen, listOf(view(sessions = again)))

        assertEquals(1, first.changes.size)
        assertTrue(left.changes.isEmpty())
        assertEquals(1, second.changes.size)
    }

    @Test
    fun `a settled trade is not approaching anything`() {
        // The stop was actually broken here, so the trade is closed. Warning that it is "closing
        // on" a level it has already been taken by would be the app a session behind itself.
        val away = listOf(session(called, high = 10.4, low = 10.2))
        val taken = away + session(called.plusDays(1), high = 10.0, low = 8.5)
        val seen = ApproachAlerts.sweep(emptyMap(), listOf(view(sessions = away))).record

        val alerts = ApproachAlerts.sweep(seen, listOf(view(sessions = taken)))

        assertTrue(alerts.changes.isEmpty())
        // Its rows go with it, or the table grows forever.
        assertEquals(2, alerts.forgotten.size)
    }

    @Test
    fun `a wider threshold catches a price a narrow one does not`() {
        val away = listOf(session(called, high = 10.4, low = 10.2))
        // The close lands at 9.5, which is 5.3% above the 9.0 stop.
        val approaching = away + session(called.plusDays(1), high = 9.6, low = 9.4)
        val seen = ApproachAlerts.sweep(
            emptyMap(),
            listOf(view(sessions = away)),
            thresholdPercent = 8,
        ).record

        val narrow = ApproachAlerts.sweep(seen, listOf(view(sessions = approaching)), 2)
        val wide = ApproachAlerts.sweep(seen, listOf(view(sessions = approaching)), 8)

        assertTrue(narrow.changes.isEmpty())
        assertEquals(listOf(ApproachLevel.STOP), wide.changes.map(ApproachChange::level))
    }

    @Test
    fun `a stock whose prices changed scale is left alone`() {
        // The levels are in the old money and the price is in the new, so a distance between them
        // is a number about nothing. The app refuses to value across a break everywhere else.
        val away = listOf(session(called, high = 10.4, low = 10.2))
        val closing = away + session(called.plusDays(1), high = 9.2, low = 9.15)
        val seen = ApproachAlerts.sweep(emptyMap(), listOf(view(sessions = away))).record

        val alerts = ApproachAlerts.sweep(
            seen,
            listOf(view(sessions = closing, priceBreaks = setOf(called.plusDays(1)))),
        )

        assertTrue(alerts.changes.isEmpty())
    }

    @Test
    fun `a trade that has gone is forgotten`() {
        val seen = mapOf(
            ApproachAlerts.alertKey("AMOC@2026-08-03", ApproachLevel.STOP) to ApproachState(true),
        )

        val alerts = ApproachAlerts.sweep(seen, emptyList())

        assertEquals(seen.keys, alerts.forgotten)
        assertTrue(alerts.changes.isEmpty())
    }

    private fun view(
        sessions: List<DailySession>,
        priceBreaks: Set<LocalDate> = emptySet(),
    ) = PortfolioCalculator.evaluate(
        position = position(),
        sessions = sessions,
        currentPrice = sessions.last().close,
        currentPriceOn = sessions.last().date,
        today = called.plusDays(1),
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
        windowSessions = 30,
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
