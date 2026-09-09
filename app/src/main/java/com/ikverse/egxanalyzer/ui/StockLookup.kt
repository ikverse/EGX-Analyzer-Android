package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.DirectoryStock

/**
 * What the header's search box offers for what has been typed.
 *
 * A lookup rather than a filter, and the distinction is the whole point of it. The boxes on
 * Results, Insights and Portfolio narrow a list that is already on the screen — a record of what
 * the sources said, or of what was traded. This one asks a question that has nothing to do with
 * the page it was typed on: *which stock*. It is answered from the catalog, so it finds a stock
 * the app has never had a recommendation for, and what it opens is [StockSheet].
 *
 * That is also why it is on every tab including Analyze, which has no list to narrow at all.
 *
 * Matching is [StockSearch]'s, so `المصريه` finds `المصرية للاتصالات` here exactly as it does in
 * the three filters — one normalizer, one set of foldings, and a search that behaves the same
 * wherever the box is drawn.
 */
internal object StockLookup {

    /**
     * How many rows the panel under the header will offer.
     *
     * A three-letter query matches a lot of a 200-odd stock catalog, and a panel that runs the
     * height of the screen is a page rather than a suggestion. Somebody who cannot see what they
     * meant in thirty rows types another letter.
     */
    const val LIMIT = 30

    /**
     * The matches, best first, or nothing at all for a query nobody has typed yet.
     *
     * **An empty query returns an empty list rather than the whole catalog**, which is the one
     * place this deliberately disagrees with [StockSearch.matches] — there a blank query is "not a
     * question" and hides nothing, because it is narrowing something the reader can already see.
     * Here there is nothing on screen until this answers, and answering an empty box with 200 rows
     * is a wall of stocks in front of the page.
     *
     * The order is the order somebody hunting a ticker expects: what they typed as the start of a
     * ticker, then anywhere in a ticker, then a name. Ties keep the catalog's own order, which is
     * roughly by size, so `COMI` comes before a small cap that also contains those letters.
     */
    fun matches(typed: String, directory: List<DirectoryStock>): List<DirectoryStock> {
        val wanted = StockSearch.query(typed)
        if (wanted.isBlank()) return emptyList()
        return directory
            .mapNotNull { stock ->
                val rank = rank(wanted, stock) ?: return@mapNotNull null
                rank to stock
            }
            // Stable, so equally ranked stocks stay in the order the catalog holds them.
            .sortedBy { (rank, _) -> rank }
            .take(LIMIT)
            .map { (_, stock) -> stock }
    }

    /** Lower is better; null is no match at all. */
    private fun rank(wanted: String, stock: DirectoryStock): Int? {
        val ticker = StockSearch.query(stock.ticker)
        return when {
            ticker.startsWith(wanted) -> 0
            ticker.contains(wanted) -> 1
            StockSearch.matches(wanted, stock.nameEnglish, stock.nameArabic) -> 2
            else -> null
        }
    }
}
