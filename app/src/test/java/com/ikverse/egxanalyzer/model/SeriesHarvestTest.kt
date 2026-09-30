package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The clock behind the archive, checked without waiting for an evening.
 *
 * These are the rules that decide whether a session is copied before the feed forgets it, and every
 * one of them fails silently: a fire that never comes due, a range that asks for more than the feed
 * will serve, a mark that never advances. None of that shows up as an error - it shows up months
 * later as a session nobody can get any more.
 */
class SeriesHarvestTest {

    private fun cairo(text: String) =
        LocalDateTime.parse(text).atZone(ScheduleClock.ZONE).toInstant()

    // --- when it fires -------------------------------------------------------------------------

    @Test
    fun `fires at the close on a trading day`() {
        // Sunday 6 September 2026 is a trading day; the fire is the close, not the bell.
        assertEquals(
            cairo("2026-09-06T14:45"),
            SeriesHarvest.nextFire(cairo("2026-09-06T09:00")),
        )
    }

    @Test
    fun `skips the weekend`() {
        // Thursday evening, past the close: the next session the exchange holds is Sunday.
        assertEquals(
            cairo("2026-09-13T14:45"),
            SeriesHarvest.nextFire(cairo("2026-09-10T18:00")),
        )
    }

    @Test
    fun `owes the close it slept through`() {
        // Deliberately without a grace window: this fire has no successor for a day, and what it
        // missed ages out of the feed rather than waiting.
        assertEquals(
            cairo("2026-09-06T14:45"),
            SeriesHarvest.dueFire(cairo("2026-09-06T21:00"), lastHarvestAt = null),
        )
    }

    @Test
    fun `owes nothing before the close`() {
        // Mid-session. Copying now would store half a day as though it were whole.
        assertNull(
            SeriesHarvest.dueFire(
                cairo("2026-09-06T11:00"),
                lastHarvestAt = cairo("2026-09-03T14:45"),
            ),
        )
    }

    @Test
    fun `owes nothing once served`() {
        assertNull(
            SeriesHarvest.dueFire(
                cairo("2026-09-06T16:00"),
                lastHarvestAt = cairo("2026-09-06T14:50"),
            ),
        )
    }

    @Test
    fun `a harvest from this morning does not serve this afternoon`() {
        // The trap the moment exists for: a run earlier the same day has not copied the session
        // that closed since. Answered against the fire, never against the date.
        assertEquals(
            cairo("2026-09-06T14:45"),
            SeriesHarvest.dueFire(
                cairo("2026-09-06T16:00"),
                lastHarvestAt = cairo("2026-09-06T08:00"),
            ),
        )
    }

    @Test
    fun `a week away comes back owing one close, not five`() {
        val due = SeriesHarvest.dueFire(
            cairo("2026-09-10T20:00"),
            lastHarvestAt = cairo("2026-09-03T14:45"),
        )
        assertEquals(cairo("2026-09-10T14:45"), due)
    }

    // --- what it asks for ----------------------------------------------------------------------

    private val today = LocalDate.of(2026, 9, 7)
    private val final = LocalDate.of(2026, 9, 7)

    @Test
    fun `a stock with nothing stored starts at the retention wall`() {
        // One request covering the whole window the feed still serves, not one per session.
        assertEquals(
            today.minusDays(59),
            SeriesHarvest.harvestFrom(null, final, retentionDays = 59, today = today),
        )
    }

    @Test
    fun `a stock already copied resumes the day after its mark`() {
        assertEquals(
            LocalDate.of(2026, 9, 4),
            SeriesHarvest.harvestFrom(
                LocalDate.of(2026, 9, 3),
                final,
                retentionDays = 59,
                today = today,
            ),
        )
    }

    @Test
    fun `a stale mark is clamped to the wall rather than asking past it`() {
        // A window wider than the feed keeps is refused outright with HTTP 422 rather than
        // trimmed, so asking from before the wall loses the whole request and not its oldest end.
        assertEquals(
            today.minusDays(59),
            SeriesHarvest.harvestFrom(
                LocalDate.of(2025, 1, 1),
                final,
                retentionDays = 59,
                today = today,
            ),
        )
    }

    @Test
    fun `a stock already current asks for nothing`() {
        // The common case on a second fire in one evening, and it has to cost no request at all.
        assertNull(
            SeriesHarvest.harvestFrom(final, final, retentionDays = 59, today = today),
        )
    }

    @Test
    fun `never asks past the session the exchange has finished with`() {
        // The guard against storing a half-traded session as though it were whole: today is final
        // only once the close has passed, so before it the newest final session is yesterday.
        assertNull(
            SeriesHarvest.harvestFrom(
                LocalDate.of(2026, 9, 6),
                finalThrough = LocalDate.of(2026, 9, 6),
                retentionDays = 59,
                today = today,
            ),
        )
    }
}
