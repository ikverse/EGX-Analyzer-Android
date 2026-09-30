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

/**
 * How long a call is given, and how much of that the entry is still takeable in.
 *
 * One pair rather than two loose numbers: whoever is deciding - the scorer or the buy dialog - has
 * to hand the same shape to the same scoring code, and a second place assembling it would drift
 * from this one the first time either was adjusted.
 */
data class TradeWindow(
    /** Sessions the call is judged over, counted inclusively from the session it was made for. */
    val sessions: Int,
    /** Leading sessions of that window in which the entry may first trade. */
    val entrySessions: Int,
)

/**
 * How long this occurrence is judged for on the Insights tab.
 *
 * Nothing the user chose. A call runs until it resolves, out to
 * [Scoring.JUDGING_HORIZON_SESSIONS] - the record is meant to say how long a source's calls take,
 * and a short deadline answers that by deleting the slow ones. The one exception is a T+1 card,
 * which named its own deadline: buy on the session it is for, sell on the next. Its band is on
 * offer across both of them, so the entry runs the length of the window like every other call's.
 */
fun RecommendationDataPoint.judgingWindow(): TradeWindow = if (isTPlusOne) {
    TradeWindow(Scoring.T_PLUS_ONE_WINDOW_SESSIONS, Scoring.T_PLUS_ONE_ENTRY_SESSIONS)
} else {
    TradeWindow(Scoring.JUDGING_HORIZON_SESSIONS, Scoring.JUDGING_HORIZON_SESSIONS)
}

/**
 * What the Bought dialog offers as this trade's deadline, given the user's setting.
 *
 * The user's own clock and the only window left that anybody sets. Deliberately not
 * [judgingWindow]: a channel's record is not a trade, and the reader who wants to be out in five
 * sessions is not asking for the source to be judged on five. A T+1 card still overrides it, since
 * a trade taken on one is over the next session by construction - the dialog lets that be typed
 * over like any other. What a T+1 no longer overrides is where the entry may trade: both sessions,
 * the same as the window, because that is how long the channel left the band standing.
 */
fun RecommendationDataPoint.offeredTradeWindow(setting: Int): TradeWindow = if (isTPlusOne) {
    TradeWindow(Scoring.T_PLUS_ONE_WINDOW_SESSIONS, Scoring.T_PLUS_ONE_ENTRY_SESSIONS)
} else {
    val window = Scoring.clampWindow(setting)
    TradeWindow(window, window)
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
