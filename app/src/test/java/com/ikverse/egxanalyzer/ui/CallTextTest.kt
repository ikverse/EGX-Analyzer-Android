package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What a copied call actually says.
 *
 * Worth pinning because the whole point of it is that the figures survive the trip: a band written
 * as one price, or a missing stop written as a dash, is a wrong number pasted into an order ticket
 * - which is exactly the mistake this was added to prevent.
 */
class CallTextTest {

    private val session = LocalDate.of(2026, 8, 14)

    @Test
    fun `every level the call printed is on its own line`() {
        val text = CallText.of(stock(), point(), "قناة التحليل", session)

        assertEquals(
            listOf(
                "AMOC · أموك",
                "Entry 10.2 – 10.5",
                "Target 1 11",
                "Target 2 12",
                "Stop 9.8",
                "Source قناة التحليل",
                "For 14 Aug",
            ),
            text.lines(),
        )
    }

    @Test
    fun `a single entry price is written as one figure and not as a band`() {
        val text = CallText.of(
            stock(),
            point().copy(buyPriceLow = 10.2, buyPriceHigh = 10.2),
            null,
            null,
        )

        assertTrue(text.lines().contains("Entry 10.2"))
    }

    @Test
    fun `a level the call never printed is absent rather than a dash`() {
        // A dash pasted into an order ticket is a wrong number. Absent is the honest shape, and it
        // is also what the card does.
        val text = CallText.of(stock(), point().copy(target2 = null, stopLoss = null), null, null)

        assertFalse(text.contains("Target 2"))
        assertFalse(text.contains("Stop"))
        assertTrue(text.contains("Target 1"))
    }

    @Test
    fun `a call with no source and no session still copies its numbers`() {
        val text = CallText.of(stock(), point(), channel = "  ", session = null)

        assertFalse(text.contains("Source"))
        assertFalse(text.contains("For "))
        assertTrue(text.lines().first().startsWith("AMOC"))
    }

    private fun stock() = ConsolidatedRecommendation(
        stockCode = "AMOC",
        stockNameEnglish = "Alexandria Mineral Oils",
        stockNameArabic = "أموك",
        mentionCount = 1,
        rank = 1,
        notesSummary = null,
        dataPoints = emptyList(),
    )

    private fun point() = RecommendationDataPoint(
        date = session,
        effectiveDateBasis = "explicit",
        visibleSourceDate = null,
        dateEvidence = null,
        timingEvidence = null,
        sourceMessageId = null,
        sourceImageRef = null,
        recommendationEvidence = null,
        recommendationType = "buy",
        buyPrice = null,
        buyPriceLow = 10.2,
        buyPriceHigh = 10.5,
        target1 = 11.0,
        returnTp1Pct = null,
        target2 = 12.0,
        returnTp2Pct = null,
        stopLoss = 9.8,
        support = null,
        resistance = null,
        riskPct = null,
        notesArabic = null,
    )
}
