package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.Headline
import com.ikverse.egxanalyzer.model.IndexQuote
import com.ikverse.egxanalyzer.model.MarketBrief
import com.ikverse.egxanalyzer.model.MarketMovers
import com.ikverse.egxanalyzer.model.OutsideQuote

/**
 * What the Home page reads and does that no other page does: the indices, the outside numbers, the
 * movers, the headlines and the model's brief.
 *
 * A sub-interface of [AppState], the way [AppUpdates] is and for the same reason: the code behind
 * it lives in `state/LiveHome` rather than in `LiveAppState`, which says `HomeFeed by home` and
 * gains every member with no forwarding written by hand. The book tile on the same page needs none
 * of this - it reads `portfolio` and `sessionDigest`, which were already here.
 */
interface HomeFeed {

    /** The EGX indices, the EGX30 first. Empty until the first fetch or a cached copy arrives. */
    val homeIndices: List<IndexQuote>

    /** The dollar, gold and oil. */
    val homeOutside: List<OutsideQuote>

    /** The newest session's biggest moves, from the stored prices. Null before two sessions exist. */
    val homeMovers: MarketMovers?

    /** Arabic first, then English, each newest first. */
    val homeHeadlines: List<Headline>

    val homeNewsLoading: Boolean

    /** When the headlines were last fetched, epoch milliseconds, or 0 for never. */
    val homeNewsAt: Long

    val homeBrief: MarketBrief?

    val homeBriefPending: Boolean

    /** Fetches the indices and the outside numbers, unless a fetch is recent. [force] skips that. */
    fun refreshHomeMarket(force: Boolean = false)

    /** Fetches the headlines, unless they are under half an hour old. [force] skips that. */
    fun refreshHomeNews(force: Boolean = false)

    /**
     * Asks the model for a brief of the session. **A paid request**: the page calls it only from
     * the confirmation its button opens.
     */
    fun askMarketBrief()

    /** Opens the article in the reader's own browser. */
    fun openHeadline(headline: Headline)
}
