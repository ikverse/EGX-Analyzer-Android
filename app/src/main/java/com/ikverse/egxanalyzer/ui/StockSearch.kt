package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.WordingRule

/**
 * The one question every stock search in the app puts, wherever the box is drawn.
 *
 * Results, Insights and Portfolio each hold a different record of the same market - saved runs,
 * scored calls, trades taken - and every one of them stores a ticker with an Arabic and an English
 * name beside it. Someone hunting for a stock has whichever of the three they happened to read, so
 * all three are asked at once.
 *
 * The rule lived on the Results screen alone and Insights matched tickers only, which meant typing a
 * company name on the tab that ranks the sources found nothing at all. Three screens searching three
 * ways is a rule that drifts; there is one of it now.
 */
internal object StockSearch {

    /**
     * What a typed query becomes before it is put to anything.
     *
     * Run once per keystroke for a whole list rather than once per row: the same question goes to
     * every stock on the screen.
     */
    fun query(typed: String): String = WordingRule.normalize(typed)

    /**
     * Whether any of a stock's names answers to what was typed.
     *
     * Both sides go through the app's own normalizer, which is the point: it folds the spellings
     * channels actually use, so `المصرية` finds `المصريه` and a search no longer has to repeat the
     * exact hamza and tied ta someone else typed. `contains` rather than a prefix, because a name
     * is remembered from the middle as often as from the start.
     *
     * An empty query is not a question and hides nothing.
     */
    fun matches(normalizedQuery: String, vararg names: String?): Boolean {
        if (normalizedQuery.isBlank()) return true
        return names.any { name ->
            name != null && WordingRule.normalize(name).contains(normalizedQuery)
        }
    }
}

/** Whether a stock a run found answers to what was typed. */
internal fun ConsolidatedRecommendation.matches(normalizedQuery: String): Boolean =
    StockSearch.matches(normalizedQuery, stockCode, stockNameArabic, stockNameEnglish)

/**
 * Whether a saved run holds anything answering to what was typed.
 *
 * A run that holds nothing is hidden rather than listed empty: the screen is answering "which of my
 * analyses read this stock", and a card that opens onto no rows is a worse answer than no card. An
 * empty query is not a question, so it hides nothing - including a run that found no stocks at all,
 * which `any` alone would drop.
 */
internal fun List<ConsolidatedRecommendation>.hasStockMatching(normalizedQuery: String): Boolean =
    normalizedQuery.isBlank() || any { it.matches(normalizedQuery) }

/** Whether a scored call answers to what was typed. */
internal fun ScoredCall.matches(normalizedQuery: String): Boolean =
    StockSearch.matches(normalizedQuery, ticker, companyArabic, companyEnglish)

/** Whether a trade the user took answers to what was typed. */
internal fun PositionView.matches(normalizedQuery: String): Boolean =
    StockSearch.matches(normalizedQuery, ticker, position.companyArabic, position.companyEnglish)
