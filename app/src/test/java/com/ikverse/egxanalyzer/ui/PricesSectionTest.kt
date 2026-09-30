package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.ScheduleClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/**
 * The sentences the Prices section is judged by.
 *
 * Nothing here computes anything the app depends on; all of it is what the reader is told, which
 * on a feature whose failure mode is silence is the part that decides whether a broken phone is
 * ever noticed.
 *
 * 2026-08-20 is a Thursday.
 */
class PricesSectionTest {

    private val now = at("2026-08-20", "06:00")

    @Test
    fun `a moment reads relatively near the present and absolutely past it`() {
        assertEquals("today 07:00", whenLabel(at("2026-08-20", "07:00"), now))
        assertEquals("tomorrow 07:00", whenLabel(at("2026-08-21", "07:00"), now))
        assertEquals("yesterday 07:00", whenLabel(at("2026-08-19", "07:00"), now))
        assertEquals("Sun 07:00", whenLabel(at("2026-08-23", "07:00"), now))
        assertEquals("2026-09-30 07:00", whenLabel(at("2026-09-30", "07:00"), now))
    }

    @Test
    fun `switched off, the refresh line says what happens instead`() {
        val line = marketRefreshLine(enabled = false, note = null, noteAt = 0L, now = now)
        assertEquals(
            "Off. Prices are fetched once a day, the first time you open the app.",
            line.text,
        )
        assertFalse(line.warning)
    }

    /**
     * The two ways the system can stop this working are said first and in the error colour. A line
     * reporting a cheerful last fetch over a phone that is going to sleep between them is a line
     * that lies quietly.
     */
    @Test
    fun `a phone that cannot keep the promise says so before anything else`() {
        val alarms = marketRefreshLine(
            enabled = true,
            note = "Priced 92/92",
            noteAt = at("2026-08-20", "05:45").toEpochMilli(),
            now = now,
            exactAlarms = false,
        )
        assertTrue(alarms.warning)
        assertTrue(alarms.text.startsWith("On, but exact alarms are off"))

        val battery = marketRefreshLine(
            enabled = true,
            note = "Priced 92/92",
            noteAt = at("2026-08-20", "05:45").toEpochMilli(),
            now = now,
            batteryExempt = false,
        )
        assertTrue(battery.warning)
        assertTrue(battery.text.contains("put this app to sleep"))
    }

    /**
     * Never blank. On the day this is switched on there is nothing to report yet, and an empty
     * line reads exactly like one that has stopped working.
     */
    @Test
    fun `with nothing fetched yet it still names the next fetch`() {
        val line = marketRefreshLine(enabled = true, note = null, noteAt = 0L, now = now)
        assertEquals("On. Nothing fetched yet - next today 10:00.", line.text)
        assertFalse(line.warning)
    }

    @Test
    fun `once it has run it reports the last fetch and the next`() {
        val line = marketRefreshLine(
            enabled = true,
            note = "Priced 92/92",
            noteAt = at("2026-08-20", "10:15").toEpochMilli(),
            now = at("2026-08-20", "10:20"),
        )
        assertEquals("Last today 10:15 · Priced 92/92 · next today 10:30", line.text)
        assertFalse(line.warning)
    }

    /**
     * A run that did nothing still writes a line, which is the whole point of keeping one: a
     * refresh whose last word is "Skipped" is diagnosable, and a blank one is not.
     */
    @Test
    fun `a run that did nothing is still reported`() {
        val line = marketRefreshLine(
            enabled = true,
            note = "Skipped - a refresh was already running.",
            noteAt = at("2026-08-20", "10:15").toEpochMilli(),
            now = at("2026-08-20", "10:20"),
        )
        assertTrue(line.text.contains("Skipped - a refresh was already running."))
        assertFalse(line.warning)
    }

    private fun at(date: String, time: String): Instant =
        LocalDateTime.parse("${date}T$time").atZone(ScheduleClock.ZONE).toInstant()
}
