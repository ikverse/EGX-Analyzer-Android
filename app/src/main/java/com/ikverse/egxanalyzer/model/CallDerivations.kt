package com.ikverse.egxanalyzer.model

/**
 * What a row of an extracted recommendation is worth, and what dated it.
 *
 * In `model/` rather than beside the table that first drew them: both are derivations from the
 * data, both are read by more than one user interface and by the spreadsheet export, and a second
 * definition of what a call is worth would drift from this one the first time either was adjusted -
 * after which the same row would report two different returns depending on where it was read.
 */

/** Entry midpoint to target, the same basis the scorer uses, so the two never disagree. */
fun returnFrom(point: RecommendationDataPoint, target: Double?): Double? {
    if (target == null) return null
    val low = point.buyPriceLow ?: point.buyPrice
    val high = point.buyPriceHigh ?: point.buyPrice
    val entry = when {
        low != null && high != null -> (low + high) / 2
        else -> low ?: high ?: return null
    }
    if (entry == 0.0) return null
    return (target - entry) / entry * 100
}

/** What dated this occurrence: the first thing read on a row, and the label on a card. */
fun timing(point: RecommendationDataPoint): String? = when {
    point.isWatching -> "Watching"
    point.isTPlusOne -> "T+1"
    point.effectiveDateBasis == "explicit_date" -> "Explicit date"
    else -> point.effectiveDateBasis
}

/**
 * How far a figure the app worked out is softened from one a source printed.
 *
 * Opacity rather than a colour of its own: drawn in the muted grey it came out the exact hue of the
 * notes beside it, and a column holding both kinds of figure was left in two hues depending only on
 * whether a channel happened to print the number. Hue stays the role, opacity carries the
 * provenance.
 *
 * Here rather than in either user interface because the spreadsheet export mixes it onto white by
 * hand - an xlsx font colour carries no alpha - and three constants would drift apart.
 */
const val DERIVED_ALPHA = 0.6f
