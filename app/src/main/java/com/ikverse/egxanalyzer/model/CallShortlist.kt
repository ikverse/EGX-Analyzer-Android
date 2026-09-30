package com.ikverse.egxanalyzer.model

/**
 * Which of twenty cards is worth spending a paid request on.
 *
 * Ask AI is one paid request per press, fired at whichever card the reader happened to scroll to.
 * Everything needed to aim it better was already computed and sitting on the same screen: what the
 * source has delivered, what the levels offer against what they risk, what has happened to everyone
 * who ever called this stock, and whether the price is even in the buy zone today.
 *
 * **It ranks attention, and predicts nothing.** No signal here is evidence about where the stock is
 * going; each is a reason this card is a better question than the one beside it. That distinction
 * is why the signals are **counted rather than weighted**: weights would imply the four had been
 * calibrated against outcomes, and they have not been. A count says exactly what it is - how many
 * separate things happen to line up on one card - and can be checked by eye against the words on
 * the card, which is the test every signal here had to pass.
 *
 * Pure, no Android, derived on every recompute and stored nowhere, like [CallSanity] beside it.
 */
object CallShortlist {

    /**
     * Every signal this call carries.
     *
     * Each input is optional because each can genuinely be missing - a source with no record yet, a
     * stock nobody else has called, a call with no levels, a stock with no price. A missing input
     * raises **no** signal rather than a negative one: the shortlist is a reason to look, and
     * absence of evidence is not a reason to look away.
     */
    fun signals(
        call: ScoredCall,
        source: ChannelScore?,
        stock: StockScore?,
        latest: LatestPrice?,
    ): Set<CallSignal> = buildSet {
        // A record has to clear the same floor it takes to lead the ranking. Below it the average
        // is measured exactly and is worth nothing as a verdict, which is precisely the state this
        // must not read as a signal.
        if (source != null &&
            source.judged >= MINIMUM_JUDGED_FOR_A_RECORD &&
            (source.averageReturn ?: 0.0) > CallSignal.POSITIVE_RETURN
        ) {
            add(CallSignal.STRONG_SOURCE)
        }
        if ((call.riskReward ?: 0.0) >= CallSignal.GOOD_RISK_REWARD_RATIO) {
            add(CallSignal.GOOD_RISK_REWARD)
        }
        if (stock != null &&
            stock.tally.judged >= MINIMUM_JUDGED_FOR_A_RECORD &&
            (stock.tally.averageReturn ?: 0.0) > CallSignal.POSITIVE_RETURN
        ) {
            add(CallSignal.STOCK_DELIVERS)
        }
        if (latest != null && inBand(call, latest)) add(CallSignal.PRICE_IN_BAND)
    }

    /**
     * Whether the last close sits inside the buy zone the channel printed.
     *
     * The one signal that is about **today** rather than about a record, and the only one that can
     * change without any new call being made. A provisional session is deliberately allowed: the
     * question is whether the price is in the band now, and a session still trading is exactly when
     * that matters most.
     *
     * A call that has already settled raises nothing. The price wandering back through the buy zone
     * of a call that hit its target three weeks ago is not an opportunity, it is a coincidence.
     */
    private fun inBand(call: ScoredCall, latest: LatestPrice): Boolean {
        if (call.outcome.judged) return false
        val close = latest.session.traded.close ?: return false
        val low = call.entryLow ?: call.entryHigh ?: return false
        val high = call.entryHigh ?: call.entryLow ?: return false
        return close >= minOf(low, high) && close <= maxOf(low, high)
    }

    /**
     * How many judged calls a source or a stock needs before its average counts as a record.
     *
     * The ranking floor, stated here rather than imported: `PerformanceCalculator` lives in `data`
     * and this lives in `model`, which depends on nothing above it - the same reason `Scoring`
     * holds its own constants. It must equal `PerformanceCalculator.MINIMUM_JUDGED_TO_RANK`, or a
     * card would raise a signal off a record the ranking itself refuses to rank; `CallShortlistTest`
     * pins the two equal so they cannot part company quietly.
     */
    internal const val MINIMUM_JUDGED_FOR_A_RECORD = 5
}
