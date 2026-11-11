package com.ikverse.egxanalyzer.ui

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Settings status tile's three lines and its headline, worked out without composing. */
class SettingsStatusTest {

    private val zone = ZoneId.of("Africa/Cairo")

    // 12:00 in Cairo on 11 Nov.
    private val now = Instant.parse("2026-11-11T10:00:00Z")

    private fun status(
        telegram: Boolean = true,
        verified: Boolean? = true,
        key: Boolean = true,
        model: String = "qwen3.7-plus",
        lastAt: Long = now.minusSeconds(3_600).toEpochMilli(),
        refreshing: Boolean = false,
        clean: Boolean = true,
        at: Instant = now,
    ) = settingsStatus(
        telegramReady = telegram,
        credentialVerified = verified,
        hasCredential = key,
        model = model,
        lastPriceRefreshAt = lastAt,
        pricesRefreshing = refreshing,
        feedClean = clean,
        feedFaults = if (clean) 0 else 3,
        stocksNamed = 40,
        now = at,
        zone = zone,
    )

    @Test
    fun `all good says so and opens nothing`() {
        val all = status()
        assertEquals("Everything is working", all.headline)
        assertNull(all.firstFault)
        assertEquals(listOf("Telegram is connected", "The reader is ready"), all.lines.take(2).map { it.text })
    }

    @Test
    fun `signed out is a fault that opens Telegram`() {
        val out = status(telegram = false)
        assertEquals("Telegram is signed out", out.lines[0].text)
        assertEquals(SettingsSection.TELEGRAM, out.firstFault)
        assertEquals("1 thing needs a look", out.headline)
    }

    @Test
    fun `a rejected key and a missing one are faults, an unchecked one is not`() {
        assertEquals("The key was rejected", status(verified = false).lines[1].text)
        assertEquals("No key saved yet", status(verified = null, key = false).lines[1].text)
        val unchecked = status(verified = null)
        assertEquals("The key is saved but not checked", unchecked.lines[1].text)
        assertNull(unchecked.lines[1].ok)
        assertEquals("Everything is working", unchecked.headline)
    }

    @Test
    fun `prices never fetched are said but not a fault`() {
        val never = status(lastAt = 0L)
        assertEquals("Prices not fetched yet", never.lines[2].text)
        assertNull(never.lines[2].ok)
    }

    @Test
    fun `prices from yesterday are a fault`() {
        val old = status(lastAt = now.minusSeconds(86_400).toEpochMilli())
        assertEquals(false, old.lines[2].ok)
        assertEquals(true, old.lines[2].text.startsWith("Prices last arrived yesterday"))
    }

    @Test
    fun `a quiet feed outranks a fresh time`() {
        val quiet = status(clean = false)
        assertEquals("3 of 40 stocks not coming through", quiet.lines[2].text)
        assertEquals(SettingsSection.GENERAL, quiet.firstFault)
    }

    @Test
    fun `the headline counts what is wrong`() {
        assertEquals("3 things need a look", status(telegram = false, verified = false, clean = false).headline)
    }

    private fun cairo(text: String): Instant = java.time.LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test
    fun `on a Saturday Thursday's prices are not late`() {
        val sat = status(lastAt = cairo("2026-11-12T15:00").toEpochMilli(), at = cairo("2026-11-14T12:00"))
        assertEquals(true, sat.lines[2].ok)
        assertEquals("Everything is working", sat.headline)
    }

    @Test
    fun `before Sunday's bell Thursday's prices are not late`() {
        val sun = status(lastAt = cairo("2026-11-12T15:00").toEpochMilli(), at = cairo("2026-11-15T08:00"))
        assertEquals(true, sun.lines[2].ok)
    }

    @Test
    fun `after Thursday's close a morning refresh is late`() {
        val thu = status(lastAt = cairo("2026-11-12T10:00").toEpochMilli(), at = cairo("2026-11-12T16:00"))
        assertEquals(false, thu.lines[2].ok)
        assertEquals(SettingsSection.GENERAL, thu.firstFault)
    }
}
