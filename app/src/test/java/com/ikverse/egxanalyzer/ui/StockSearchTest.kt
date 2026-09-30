package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.ScoredCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Someone looking for a stock has whichever of its three names they happened to read.
 *
 * The Arabic one is the reason this is not a plain `contains`: channels spell the same company with
 * and without the dotted ya and the tied ta, so a search that demanded the exact spelling would
 * find nothing most of the time.
 *
 * One rule over three records - a stock a run found, a call the app scored, a trade the user took -
 * because the three boxes drawing it are the same box. The last tests here are what stops the three
 * screens drifting apart again.
 */
class StockSearchTest {

    private val etel = ConsolidatedRecommendation(
        stockCode = "ETEL",
        stockNameEnglish = "Egyptian Co. for Communication Systems",
        stockNameArabic = "المصرية للاتصالات",
        mentionCount = 1,
        rank = 1,
        notesSummary = null,
    )

    private fun ConsolidatedRecommendation.found(query: String) =
        matches(StockSearch.query(query))

    @Test
    fun `an empty search hides nothing`() {
        assertTrue(etel.found(""))
        assertTrue(etel.found("   "))
    }

    @Test
    fun `the ticker is matched whole or in part, in any case`() {
        assertTrue(etel.found("ETEL"))
        assertTrue(etel.found("etel"))
        assertTrue(etel.found("ete"))
    }

    @Test
    fun `the English name is matched anywhere inside it`() {
        assertTrue(etel.found("communication"))
        assertTrue(etel.found("Egyptian Co."))
    }

    /** `المصرية` and `المصريه` are one word to everyone except a byte comparison. */
    @Test
    fun `the Arabic name is matched across the spellings channels use`() {
        assertTrue(etel.found("المصرية"))
        assertTrue(etel.found("المصريه"))
        assertTrue(etel.found("للاتصالات"))
    }

    @Test
    fun `a stock that answers to none of the three is hidden`() {
        assertFalse(etel.found("COMI"))
        assertFalse(etel.found("بنك"))
    }

    private val comi = ConsolidatedRecommendation(
        stockCode = "COMI",
        stockNameEnglish = "Commercial International Bank",
        stockNameArabic = "البنك التجاري الدولي",
        mentionCount = 1,
        rank = 2,
        notesSummary = null,
    )

    private fun List<ConsolidatedRecommendation>.kept(query: String) =
        hasStockMatching(StockSearch.query(query))

    @Test
    fun `a run is kept when any one of its stocks answers`() {
        assertTrue(listOf(etel, comi).kept("COMI"))
        assertTrue(listOf(etel, comi).kept("المصرية"))
    }

    @Test
    fun `a run holding none of them is hidden`() {
        assertFalse(listOf(etel, comi).kept("AMOC"))
        assertFalse(listOf(etel).kept("bank"))
    }

    /** A run that found no stocks is still a run someone saved; only a query may hide it. */
    @Test
    fun `an empty search keeps every run, including one with no stocks`() {
        assertTrue(emptyList<ConsolidatedRecommendation>().kept(""))
        assertTrue(emptyList<ConsolidatedRecommendation>().kept("   "))
        assertFalse(emptyList<ConsolidatedRecommendation>().kept("COMI"))
    }

    // ── The same rule, over the other two records ─────────────────────────────────────

    private val called = LocalDate.of(2026, 8, 5)

    private fun call(
        ticker: String,
        english: String? = null,
        arabic: String? = null,
    ) = ScoredCall(
        ticker = ticker,
        companyEnglish = english,
        companyArabic = arabic,
        channel = "Some channel",
        channelId = null,
        openedOn = called,
        entryLow = 10.0,
        entryHigh = 10.2,
        target1 = 11.0,
        target2 = 12.0,
        stopLoss = 9.5,
        outcome = Outcome.PARTIAL_HIT,
        settledOn = called,
        peakHigh = 11.5,
        troughLow = 9.9,
        returnPct = 5.0,
        sessionsElapsed = 1,
    )

    private fun trade(
        ticker: String,
        english: String? = null,
        arabic: String? = null,
    ) = PositionView(
        position = Position(
            ticker = ticker,
            recommendationDate = called,
            companyEnglish = english,
            companyArabic = arabic,
            entryPrice = 10.0,
            entryDate = called,
        ),
        status = PositionStatus.OPEN,
        marketStatus = PositionStatus.OPEN,
        open = true,
        currentPrice = 10.5,
        exitPrice = 10.5,
        realized = false,
        returnPct = 5.0,
        sessionsElapsed = 1,
        sessionsRemaining = 9,
        deadlineDate = called.plusDays(14),
        settledOn = null,
        ranOutOfTime = false,
        overdueDays = 0,
    )

    /**
     * Insights used to match tickers and nothing else.
     *
     * Typing a company name on the tab that ranks the sources found nothing at all, even though
     * every call has carried both names all along.
     */
    @Test
    fun `a scored call answers to its names as well as its ticker`() {
        val comi = call("COMI", "Commercial International Bank", "البنك التجاري الدولي")

        assertTrue(comi.matches(StockSearch.query("comi")))
        assertTrue(comi.matches(StockSearch.query("bank")))
        assertTrue(comi.matches(StockSearch.query("التجارى")))
        assertTrue(comi.matches(StockSearch.query("")))
        assertFalse(comi.matches(StockSearch.query("ETEL")))
    }

    @Test
    fun `a trade answers to its names as well as its ticker`() {
        val etelHeld = trade("ETEL", "Egyptian Co. for Communication Systems", "المصرية للاتصالات")

        assertTrue(etelHeld.matches(StockSearch.query("etel")))
        assertTrue(etelHeld.matches(StockSearch.query("communication")))
        assertTrue(etelHeld.matches(StockSearch.query("المصريه")))
        assertTrue(etelHeld.matches(StockSearch.query("")))
        assertFalse(etelHeld.matches(StockSearch.query("COMI")))
    }

    /** A record with no names stored still answers to its ticker rather than disappearing. */
    @Test
    fun `a nameless record is matched on its ticker alone`() {
        assertTrue(call("AMOC").matches(StockSearch.query("amoc")))
        assertTrue(trade("AMOC").matches(StockSearch.query("amoc")))
        assertTrue(trade("AMOC").matches(StockSearch.query("   ")))
        assertFalse(trade("AMOC").matches(StockSearch.query("bank")))
    }

    /**
     * The three screens ask one question, which is the whole point of the shared rule.
     *
     * A stock a run found, a call scored off it and a trade taken on it are three records of the
     * same company, and a search that found one but not the others is the bug this replaced.
     */
    @Test
    fun `one query answers the same way on all three records`() {
        val queries = listOf("comi", "COMI", "bank", "البنك", "التجارية", "etel", "")
        val english = "Commercial International Bank"
        val arabic = "البنك التجاري الدولي"

        queries.forEach { typed ->
            val wanted = StockSearch.query(typed)
            val onRun = comi.matches(wanted)

            assertEquals(
                "\"$typed\" is read differently on Insights than on Results",
                onRun,
                call("COMI", english, arabic).matches(wanted),
            )
            assertEquals(
                "\"$typed\" is read differently on Portfolio than on Results",
                onRun,
                trade("COMI", english, arabic).matches(wanted),
            )
        }
    }
}
