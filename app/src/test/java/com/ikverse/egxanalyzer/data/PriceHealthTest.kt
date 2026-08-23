package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.ScoredCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Which stocks the price feed has gone quiet about, and what that is costing the record.
 *
 * The claim this puts on screen is "these stocks are holding N calls out of every rate", and the
 * only way that claim goes wrong is by being too generous - sweeping in a call that is unjudged for
 * a reason the feed had nothing to do with. Most of what follows is about the calls it must *not*
 * count.
 */
class PriceHealthTest {

    private val today = LocalDate.of(2026, 8, 23)
    private val session = LocalDate.of(2026, 8, 10)

    private fun call(
        ticker: String,
        outcome: Outcome,
        repeatOf: LocalDate? = null,
    ) = ScoredCall(
        ticker = ticker,
        companyEnglish = null,
        companyArabic = null,
        channel = "أخبار البورصة",
        channelId = 1L,
        openedOn = session,
        entryLow = 10.0,
        entryHigh = 10.0,
        target1 = 12.0,
        target2 = null,
        stopLoss = 9.0,
        outcome = outcome,
        settledOn = null,
        peakHigh = null,
        troughLow = null,
        returnPct = null,
        sessionsElapsed = 0,
        repeatOf = repeatOf,
    )

    /** A stock whose feed answered `on`, however long ago that was. */
    private fun priced(ticker: String, on: LocalDate) = ticker to LatestPrice(
        session = DailySession(ticker, on, high = 11.0, low = 9.5, close = 10.5, volume = 1000.0),
        provisional = false,
    )

    private fun assess(
        calls: List<ScoredCall>,
        latest: Map<String, LatestPrice> = emptyMap(),
        breaks: Map<String, Set<LocalDate>> = emptyMap(),
    ) = PriceHealth.assess(calls, latest, breaks, today)

    @Test
    fun `a feed keeping up reports nothing at all`() {
        val report = assess(
            calls = listOf(call("AMOC", Outcome.OPEN), call("COMI", Outcome.FULL_HIT)),
            // Two days old: a weekend, not a fault.
            latest = mapOf(priced("AMOC", today.minusDays(2)), priced("COMI", today.minusDays(2))),
        )

        assertTrue(report.clean)
        assertEquals(0, report.callsHeld)
        // Still counted, so a section that does appear can say four *of what*.
        assertEquals(2, report.stocksNamed)
    }

    @Test
    fun `a stock with no history at all is unpriced and is not also called stale`() {
        val report = assess(calls = listOf(call("VLMRA", Outcome.UNPRICED)))

        val row = report.faults.single()
        assertEquals("VLMRA", row.ticker)
        // Both would be true in a loose reading, and reporting both would count one broken stock
        // twice in a list whose whole point is a count.
        assertEquals(setOf(FeedFault.UNPRICED), row.faults)
        assertEquals(1, row.callsHeld)
        assertEquals(null, row.newestSession)
        assertEquals(null, row.ageDays)
    }

    @Test
    fun `a series that stopped moving is stale and names the session it stopped on`() {
        val stopped = today.minusDays(20)
        val report = assess(
            calls = listOf(call("AMOC", Outcome.OPEN), call("AMOC", Outcome.OPEN)),
            latest = mapOf(priced("AMOC", stopped)),
        )

        val row = report.faults.single()
        assertEquals(setOf(FeedFault.STALE), row.faults)
        assertEquals(stopped, row.newestSession)
        assertEquals(20L, row.ageDays)
        // Both calls are waiting on sessions this feed is never going to deliver.
        assertEquals(2, row.callsHeld)
    }

    @Test
    fun `a recorded scale break is reported and holds only the calls it made unjudgeable`() {
        val report = assess(
            calls = listOf(
                call("SWDY", Outcome.PRICE_BREAK),
                // Made after the split and judged normally. The break is real and this call is not
                // its victim, so counting it would overstate what the break cost.
                call("SWDY", Outcome.FULL_HIT),
            ),
            latest = mapOf(priced("SWDY", today.minusDays(1))),
            breaks = mapOf("SWDY" to setOf(LocalDate.of(2026, 8, 5))),
        )

        val row = report.faults.single()
        assertEquals(setOf(FeedFault.SCALE_CHANGED), row.faults)
        assertEquals(1, row.callsHeld)
    }

    @Test
    fun `a stock can be frozen and split at once and says both`() {
        val report = assess(
            calls = listOf(call("ETEL", Outcome.OPEN), call("ETEL", Outcome.PRICE_BREAK)),
            latest = mapOf(priced("ETEL", today.minusDays(30))),
            breaks = mapOf("ETEL" to setOf(LocalDate.of(2026, 8, 1))),
        )

        val row = report.faults.single()
        // Naming only the more severe would hide the other on the stocks with most wrong with them.
        assertEquals(setOf(FeedFault.STALE, FeedFault.SCALE_CHANGED), row.faults)
        assertEquals(2, row.callsHeld)
    }

    @Test
    fun `a call the market answered is not held by a broken feed`() {
        val report = assess(
            calls = listOf(
                // The feed is stale, but these two were settled before it stopped and an entry that
                // never traded is a fact about the market, not about the prices being missing.
                call("AMOC", Outcome.ENTRY_NOT_REACHED),
                call("AMOC", Outcome.STOPPED),
                call("AMOC", Outcome.OPEN),
            ),
            latest = mapOf(priced("AMOC", today.minusDays(14))),
        )

        // The stock is still reported - its feed really has stopped - but the claim about what that
        // is costing names one call, not three.
        assertEquals(1, report.faults.single().callsHeld)
    }

    @Test
    fun `a re-posting is not counted twice`() {
        val report = assess(
            calls = listOf(
                call("AMOC", Outcome.OPEN),
                call("AMOC", Outcome.OPEN, repeatOf = session),
            ),
            latest = mapOf(priced("AMOC", today.minusDays(14))),
        )

        // The repeat is outside every rate already, so counting it would overstate what the stalled
        // feed is keeping out of them - the same rule the rates themselves follow.
        assertEquals(1, report.faults.single().callsHeld)
    }

    @Test
    fun `the worst offender leads and ties break on the ticker`() {
        val report = assess(
            calls = listOf(
                call("SWDY", Outcome.UNPRICED),
                call("AMOC", Outcome.UNPRICED),
                call("COMI", Outcome.UNPRICED),
                call("COMI", Outcome.UNPRICED),
                call("COMI", Outcome.UNPRICED),
            ),
        )

        assertEquals(
            listOf("COMI", "AMOC", "SWDY"),
            report.faults.map(StockHealth::ticker),
        )
        assertEquals(5, report.callsHeld)
        assertEquals(3, report.stocksNamed)
    }

    @Test
    fun `a stock the record never named is not reported however broken it is`() {
        val report = assess(
            calls = listOf(call("AMOC", Outcome.FULL_HIT)),
            latest = mapOf(
                priced("AMOC", today.minusDays(1)),
                // Frozen for a year, and nobody has ever been recommended it. The catalog holds
                // every Cairo listing; a page reporting on all of them hides the rows that matter.
                priced("VLMRA", today.minusDays(365)),
            ),
            breaks = mapOf("VLMRA" to setOf(LocalDate.of(2026, 3, 1))),
        )

        assertTrue(report.clean)
        assertEquals(1, report.stocksNamed)
    }
}
