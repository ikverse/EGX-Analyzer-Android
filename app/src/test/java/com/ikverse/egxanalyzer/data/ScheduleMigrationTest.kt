package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That the phone which already had a price refresh keeps what it was asking for.
 *
 * This runs exactly once per device and then the table it reads is dropped, so there is no second
 * chance and no way to notice from the app that it got it wrong - a carried-over price refresh
 * that quietly did not carry looks identical to a checkbox nobody ticked.
 */
class ScheduleMigrationTest {

    @Test
    fun `a price refresh that was on becomes the checkbox`() {
        assertTrue(ScheduleMigration.marketRefreshWasOn(listOf(priceRow(enabled = true))))
    }

    /**
     * Whatever shape its trigger had. After the close, hourly, through the session - every one of
     * them was a way of asking the same question, and the checkbox is the answer to all of them.
     */
    @Test
    fun `any trigger shape counts as asking for the refresh`() {
        val rows = listOf(priceRow(enabled = true, triggerKind = "INTERVAL", triggerAt = "10:00"))
        assertTrue(ScheduleMigration.marketRefreshWasOn(rows))
    }

    @Test
    fun `a price refresh that was off is not switched on for the user`() {
        assertFalse(ScheduleMigration.marketRefreshWasOn(listOf(priceRow(enabled = false))))
    }

    @Test
    fun `a phone with no rows gets no refresh`() {
        assertFalse(ScheduleMigration.marketRefreshWasOn(emptyList()))
    }

    @Test
    fun `an old analysis row is not mistaken for a price refresh`() {
        assertFalse(ScheduleMigration.marketRefreshWasOn(listOf(analysisRow(enabled = true))))
    }

    private fun priceRow(
        enabled: Boolean,
        triggerKind: String = "REPEAT",
        triggerAt: String = "18:00",
    ) = LegacyScheduleRow(
        enabled = enabled,
        workKind = "PRICE_REFRESH",
        triggerKind = triggerKind,
        triggerAt = triggerAt,
        workConfig = "{}",
    )

    private fun analysisRow(
        enabled: Boolean,
        triggerKind: String = "REPEAT",
    ) = LegacyScheduleRow(
        enabled = enabled,
        workKind = "ANALYSIS",
        triggerKind = triggerKind,
        triggerAt = "08:30",
        workConfig = """{"channels":[{"id":7,"name":"Signals"}],"contentTypes":[]}""",
    )
}
