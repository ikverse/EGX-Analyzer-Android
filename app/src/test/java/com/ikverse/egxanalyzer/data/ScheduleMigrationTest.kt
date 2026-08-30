package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysisContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

/**
 * That the phone which already had schedules keeps what it was asking for.
 *
 * This runs exactly once per device and then the table it reads is dropped, so there is no second
 * chance and no way to notice from the app that it got it wrong - a carried-over price refresh
 * that quietly did not carry looks identical to a checkbox nobody ticked. The two rules worth
 * holding are that an intent is preserved and that one is never invented.
 */
class ScheduleMigrationTest {

    private val armedAt: Instant = Instant.parse("2026-08-30T09:00:00Z")
    private val contentType = AnalysisContentType.entries.first()

    @Test
    fun `a price refresh that was on becomes the checkbox`() {
        val result = ScheduleMigration.from(listOf(priceRow(enabled = true)), armedAt)
        assertTrue(result.marketRefresh)
    }

    /**
     * Whatever shape its trigger had. After the close, hourly, through the session - every one of
     * them was a way of asking the same question, and the checkbox is the answer to all of them.
     */
    @Test
    fun `any trigger shape counts as asking for the refresh`() {
        val rows = listOf(priceRow(enabled = true, triggerKind = "INTERVAL", triggerAt = "10:00"))
        assertTrue(ScheduleMigration.from(rows, armedAt).marketRefresh)
    }

    @Test
    fun `a price refresh that was off is not switched on for the user`() {
        val result = ScheduleMigration.from(listOf(priceRow(enabled = false)), armedAt)
        assertFalse(result.marketRefresh)
    }

    @Test
    fun `a repeating analysis keeps its time, its chats and its switch`() {
        val schedule = ScheduleMigration.from(listOf(analysisRow(enabled = true)), armedAt).schedule
        assertEquals(LocalTime.of(8, 30), schedule?.at)
        assertEquals(listOf(7L), schedule?.channels?.map { it.id })
        assertEquals(setOf(contentType), schedule?.contentTypes)
        assertTrue(schedule?.enabled == true)
    }

    /**
     * Armed now rather than carried, so a schedule whose hour has already gone by today does not
     * owe a run the moment the app finishes migrating and pay for it through the grace window.
     */
    @Test
    fun `a carried schedule is armed at the migration, not at its old creation`() {
        val schedule = ScheduleMigration.from(listOf(analysisRow(enabled = true)), armedAt).schedule
        assertEquals(armedAt, schedule?.armedAt)
    }

    @Test
    fun `a one-shot or interval analysis is dropped rather than guessed at`() {
        assertNull(
            ScheduleMigration.from(
                listOf(analysisRow(enabled = true, triggerKind = "ONCE")),
                armedAt,
            ).schedule,
        )
        assertNull(
            ScheduleMigration.from(
                listOf(analysisRow(enabled = true, triggerKind = "INTERVAL")),
                armedAt,
            ).schedule,
        )
    }

    /**
     * An unreadable row must not become an analysis of no chats, which is a paid request for an
     * empty answer booked by a migration nobody watched.
     */
    @Test
    fun `an unreadable analysis is dropped`() {
        assertNull(
            ScheduleMigration.from(
                listOf(analysisRow(enabled = true, workConfig = "not json")),
                armedAt,
            ).schedule,
        )
        assertNull(
            ScheduleMigration.from(
                listOf(
                    analysisRow(
                        enabled = true,
                        workConfig = """{"channels":[],"contentTypes":[]}""",
                    ),
                ),
                armedAt,
            ).schedule,
        )
    }

    @Test
    fun `a phone with no schedules gets none`() {
        val result = ScheduleMigration.from(emptyList(), armedAt)
        assertFalse(result.marketRefresh)
        assertNull(result.schedule)
    }

    @Test
    fun `both sides carry from one table`() {
        val result = ScheduleMigration.from(
            listOf(priceRow(enabled = true), analysisRow(enabled = true)),
            armedAt,
        )
        assertTrue(result.marketRefresh)
        assertEquals(LocalTime.of(8, 30), result.schedule?.at)
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
        workConfig: String = """{"channels":[{"id":7,"name":"Signals"}],""" +
            """"contentTypes":["${AnalysisContentType.entries.first().name}"]}""",
    ) = LegacyScheduleRow(
        enabled = enabled,
        workKind = "ANALYSIS",
        triggerKind = triggerKind,
        triggerAt = "08:30",
        workConfig = workConfig,
    )
}
