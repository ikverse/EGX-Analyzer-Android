package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.egxTargetSession
import com.ikverse.egxanalyzer.model.nextEgxOpenDay
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.resolveAnalysisWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RecommendationDateTest {
    @Test
    fun `next EGX trading day advances normally Sunday through Wednesday`() {
        assertEquals(
            LocalDate.of(2026, 7, 30),
            nextEgxOpenDay(LocalDate.of(2026, 7, 29)),
        )
    }

    @Test
    fun `next EGX trading day skips Friday and Saturday weekend`() {
        assertEquals(
            LocalDate.of(2026, 8, 2),
            nextEgxOpenDay(LocalDate.of(2026, 7, 30)),
        )
        assertEquals(
            LocalDate.of(2026, 8, 2),
            nextEgxOpenDay(LocalDate.of(2026, 7, 31)),
        )
    }

    @Test
    fun `current session is used through the Cairo market close`() {
        val cairo = ZoneId.of("Africa/Cairo")
        assertEquals(
            LocalDate.of(2026, 7, 29),
            egxTargetSession(ZonedDateTime.of(2026, 7, 29, 14, 30, 0, 0, cairo)),
        )
    }

    @Test
    fun `after market close advances to the next open session`() {
        val cairo = ZoneId.of("Africa/Cairo")
        assertEquals(
            LocalDate.of(2026, 8, 2),
            egxTargetSession(ZonedDateTime.of(2026, 7, 30, 14, 31, 0, 0, cairo)),
        )
    }

    @Test
    fun `weekend targets Sunday`() {
        val cairo = ZoneId.of("Africa/Cairo")
        assertEquals(
            LocalDate.of(2026, 8, 2),
            egxTargetSession(ZonedDateTime.of(2026, 7, 31, 10, 0, 0, 0, cairo)),
        )
    }

    @Test
    fun `Thursday after close uses Thursday through current time for Sunday target`() {
        val cairo = ZoneId.of("Africa/Cairo")
        val now = ZonedDateTime.of(2026, 7, 30, 15, 0, 0, 0, cairo)
        val window = resolveAnalysisWindow(AnalysisMode.NEXT_DAY, null, now)
        assertEquals(LocalDate.of(2026, 8, 2), window.targetDate)
        assertEquals(
            ZonedDateTime.of(2026, 7, 30, 0, 0, 0, 0, cairo).toInstant(),
            window.start,
        )
        assertEquals(now.toInstant(), window.endExclusive)
    }

    @Test
    fun `historical window includes preceding and selected Cairo calendar days`() {
        val cairo = ZoneId.of("Africa/Cairo")
        val target = LocalDate.of(2026, 7, 20)
        val window = resolveAnalysisWindow(
            AnalysisMode.SPECIFIC_DATE,
            target,
            ZonedDateTime.of(2026, 7, 29, 10, 0, 0, 0, cairo),
        )
        assertEquals(
            target.minusDays(1).atStartOfDay(cairo).toInstant(),
            window.start,
        )
        assertEquals(
            target.plusDays(1).atStartOfDay(cairo).toInstant(),
            window.endExclusive,
        )
    }
}
