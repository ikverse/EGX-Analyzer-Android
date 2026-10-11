package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * When a watched stock's latest price counts as having reached a level.
 *
 * Pure, like the other alert sweeps: the rule about crossing a level has to be testable without
 * a device, and the one-shot nature - a fired alert leaves the list - is the part that keeps a
 * price sitting past a level from being said every quarter of an hour.
 */
class WatchAlertsTest {

    private fun latest(ticker: String, close: Double, provisional: Boolean = false) = LatestPrice(
        session = DailySession(ticker, LocalDate.of(2026, 10, 11), close + 0.1, close - 0.1, close, 1_000.0),
        provisional = provisional,
    )

    @Test
    fun `rises to fires at or above, falls to at or below`() {
        val alerts = listOf(
            WatchAlert("COMI", 12.5, above = true),
            WatchAlert("AMOC", 9.8, above = false),
            WatchAlert("ETEL", 30.0, above = true),
        )
        val swept = WatchAlerts.sweep(
            alerts,
            mapOf("COMI" to latest("COMI", 12.5), "AMOC" to latest("AMOC", 9.7), "ETEL" to latest("ETEL", 29.0)),
        )

        assertEquals(listOf("COMI", "AMOC"), swept.fired.map { it.alert.ticker })
        assertEquals(listOf(WatchAlert("ETEL", 30.0, above = true)), swept.remaining)
    }

    @Test
    fun `a stock with no price keeps its alert`() {
        val swept = WatchAlerts.sweep(listOf(WatchAlert("COMI", 12.5, above = true)), emptyMap())
        assertTrue(swept.fired.isEmpty())
        assertEquals(1, swept.remaining.size)
    }

    @Test
    fun `a still-trading price counts`() {
        val swept = WatchAlerts.sweep(
            listOf(WatchAlert("COMI", 12.5, above = true)),
            mapOf("COMI" to latest("COMI", 12.6, provisional = true)),
        )
        assertEquals(1, swept.fired.size)
        assertTrue(swept.remaining.isEmpty())
    }

    @Test
    fun `the label prints the level the way a price is printed`() {
        assertEquals("Rises to 12.5", WatchAlert("COMI", 12.5, above = true).label)
        assertEquals("Falls to 30", WatchAlert("COMI", 30.0, above = false).label)
    }
}
