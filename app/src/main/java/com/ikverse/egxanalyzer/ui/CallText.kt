package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import java.time.LocalDate

/**
 * One call as plain text, for pasting into a broker, a note, or a message to somebody.
 *
 * The report exports as a spreadsheet and that is the right shape for a record; it is the wrong
 * shape entirely for the thing a reader actually does with a single call, which is to retype four
 * numbers into an order ticket and get one of them wrong. Nothing between the two existed.
 *
 * **Every level, one per line, and no prose.** A paragraph would be pleasant to read and useless to
 * copy from - what this is for is the numbers surviving the trip intact. The channel and the
 * session are on it because a call without either is one nobody can place later, and the ticker
 * leads because that is what gets typed first.
 *
 * Pure, with no Android and no Compose in it, so [CallTextTest] can pin the wording without a
 * device - and so the one place this app decides what a call looks like written down is a function
 * rather than a string built inside a menu item.
 */
internal object CallText {

    fun of(
        stock: ConsolidatedRecommendation,
        point: RecommendationDataPoint,
        channel: String?,
        session: LocalDate?,
    ): String = buildList {
        add(listOfNotNull(stock.stockCode, stock.stockNameArabic).joinToString(" · "))
        entry(point)?.let { add("Entry $it") }
        point.target1?.let { add("Target 1 ${formatPrice(it)}") }
        point.target2?.let { add("Target 2 ${formatPrice(it)}") }
        point.stopLoss?.let { add("Stop ${formatPrice(it)}") }
        // Last, and only where the report knows them. A call whose source or session is missing is
        // still worth copying; a line reading "Source: —" is not worth carrying.
        channel?.takeIf(String::isNotBlank)?.let { add("Source $it") }
        session?.let { add("For ${AppDates.DayMonth.format(it)}") }
    }.joinToString("\n")

    /**
     * The buy zone as one figure or as a band, matching what the card draws.
     *
     * A band is written with the same en dash the ladder uses rather than a hyphen, because the two
     * read differently at a glance and this text is meant to be recognisable as the card it came
     * from.
     */
    private fun entry(point: RecommendationDataPoint): String? {
        val low = point.buyPriceLow
        val high = point.buyPriceHigh
        return when {
            low != null && high != null && low != high ->
                "${formatPrice(low)} – ${formatPrice(high)}"
            else -> (low ?: high ?: point.buyPrice)?.let(::formatPrice)
        }
    }
}
