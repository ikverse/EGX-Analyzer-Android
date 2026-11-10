package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsInPlayTest {

    private val day = LocalDate.parse("2026-11-09")

    private fun call(
        ticker: String,
        outcome: Outcome,
        windowComplete: Boolean = false,
        repeatOf: LocalDate? = null,
        entryLow: Double? = 10.0,
        entryHigh: Double? = 10.4,
    ) = ScoredCall(
        ticker = ticker, companyEnglish = null, companyArabic = null, channel = "Tadawul", channelId = null,
        openedOn = day, entryLow = entryLow, entryHigh = entryHigh, target1 = 11.0, target2 = 12.0, stopLoss = 9.5,
        outcome = outcome, settledOn = null, peakHigh = 10.5, troughLow = 9.9, returnPct = 1.0,
        sessionsElapsed = 1, repeatOf = repeatOf, windowComplete = windowComplete,
        faults = CallSanity.faults(entryLow, entryHigh, 11.0, 12.0, 9.5, session = null),
    )

    private fun report(vararg calls: ScoredCall) = PerformanceReport(
        sessions = listOf(ScoredSession(day, Instant.EPOCH, "m", 1, calls = calls.toList())),
    )

    @Test
    fun `only calls the market has not finished with are in play`() {
        val r = report(
            call("COMI", Outcome.ENTRY_NOT_REACHED),
            call("ABUK", Outcome.OPEN),
            call("ORAS", Outcome.PARTIAL_HIT),
            call("SWDY", Outcome.FULL_HIT),
            call("FWRY", Outcome.STOPPED),
            call("HRHO", Outcome.OPEN, windowComplete = true),
            call("COMI", Outcome.ENTRY_NOT_REACHED, repeatOf = day.minusDays(3)),
        )
        val play = callsInPlay(r, latestClose = { null })
        assertEquals(listOf("COMI", "ABUK", "ORAS"), play.calls.map { it.ticker })
        assertEquals(1, play.pastTarget1)
        assertEquals(0, play.inEntryZone)
    }

    @Test
    fun `a close inside the zone counts, and an untaken one is offered nearest first`() {
        val closes = mapOf("COMI" to 10.39, "ABUK" to 10.21, "ORAS" to 10.1, "EFIH" to 11.0, "TMGH" to 10.2)
        val r = report(
            call("COMI", Outcome.ENTRY_NOT_REACHED),
            call("ABUK", Outcome.OPEN),
            call("ORAS", Outcome.PARTIAL_HIT), // in the zone, but already past target 1
            call("EFIH", Outcome.OPEN), // above the zone
            call("TMGH", Outcome.OPEN), // held
        )
        val play = callsInPlay(r, latestClose = { closes[it] }, held = { it.ticker == "TMGH" })
        assertEquals(4, play.inEntryZone)
        assertEquals(listOf("ABUK", "COMI"), play.untaken.map { it.ticker })
    }

    @Test
    fun `a single printed entry is a zone of one price`() {
        val single = call("COMI", Outcome.OPEN, entryLow = 10.0, entryHigh = null)
        assertTrue(single.inEntryZone(10.0))
        assertEquals(false, single.inEntryZone(10.01))
    }

    private fun session(date: String, close: Double) =
        DailySession("^CASE30", LocalDate.parse(date), high = null, low = null, close = close, volume = null)

    @Test
    fun `the index line starts at zero on the chart's first day`() {
        val index = listOf(session("2026-11-02", 100.0), session("2026-11-03", 102.0), session("2026-11-05", 99.0))
        val days = listOf("2026-11-03", "2026-11-04", "2026-11-05").map(LocalDate::parse)
        val line = indexByDay(index, days)
        assertEquals(listOf(0.0, 0.0, -2.94), line.map { Math.round(it.changePct * 100) / 100.0 })
    }

    @Test
    fun `days before the index's first close are left out`() {
        val index = listOf(session("2026-11-04", 50.0), session("2026-11-05", 55.0))
        val days = listOf("2026-11-03", "2026-11-04", "2026-11-05").map(LocalDate::parse)
        val line = indexByDay(index, days)
        assertEquals(listOf("2026-11-04", "2026-11-05").map(LocalDate::parse), line.map { it.date })
        assertEquals(10.0, line.last().changePct, 1e-9)
    }

    @Test
    fun `no index, no line`() {
        assertEquals(emptyList<IndexPoint>(), indexByDay(emptyList(), listOf(day)))
    }
}
