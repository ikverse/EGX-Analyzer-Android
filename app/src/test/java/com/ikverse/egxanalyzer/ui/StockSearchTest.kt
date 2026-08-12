package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.WordingRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Someone looking for a stock has whichever of its three names they happened to read.
 *
 * The Arabic one is the reason this is not a plain `contains`: channels spell the same company with
 * and without the dotted ya and the tied ta, so a search that demanded the exact spelling would
 * find nothing most of the time.
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
        matches(WordingRule.normalize(query))

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
        hasStockMatching(WordingRule.normalize(query))

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
}
