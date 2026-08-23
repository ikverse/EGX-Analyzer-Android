package com.ikverse.egxanalyzer.model

/**
 * Where a call stood relative to its buy zone the last time the user was told anything about it.
 *
 * The counterpart of [TradeState], and it exists for the same reason: whether a price is inside a
 * band is derived on every recompute and rightly stored nowhere, so nothing on disk remembers what
 * the user has already been told. "Tell me when this becomes takeable" is a question about the
 * difference between two readings, and this is the smallest thing that answers it.
 */
data class CallState(
    /** The last close sat inside the entry band the channel printed. */
    val inBand: Boolean,
)

/** One call that has just become takeable, ready to be said out loud. */
data class CallChange(
    val call: ScoredCall,
    /** The close that put it in the band, for the notification to name. */
    val price: Double,
)

/**
 * Calls whose buy zone the price has just traded into.
 *
 * `TradeAlerts` watches trades the user is already in. This watches the ones they are **not** in,
 * which was the gap: prices refresh through the session on their own now, so the app knows at
 * eleven in the morning that a stock has traded into a buy zone a source printed, and told nobody
 * unless they happened to open it.
 *
 * It reports a **fact about the market** - "AMOC has traded into the buy zone this source printed"
 * - in the same register as "your trade hit its target". It is not advice and it names no action.
 *
 * Pure, with no Android in it, like [TradeAlerts]: what counts as newly takeable is a rule about
 * trading and has to be checkable without a device. The caller does the two things this cannot -
 * raise the notification, and write [record] and [forgotten] back to disk - and the writing is what
 * makes an entry announced once rather than at every refresh for the rest of the call's life.
 */
data class CallAlerts(
    val changes: List<CallChange> = emptyList(),
    /** Ids whose stored state has moved, with what to store. Unchanged calls are absent. */
    val record: Map<String, CallState> = emptyMap(),
    /** Ids no longer in the record at all, whose rows go with them. */
    val forgotten: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = changes.isEmpty() && record.isEmpty() && forgotten.isEmpty()

    companion object {

        /**
         * Compares every open call against what was last stored about it.
         *
         * A call seen for the first time is **recorded and never announced**. Without that, the
         * first refresh after this shipped would announce every call whose band the price happens
         * to be sitting in - a month of history arriving at once as though it had all just
         * happened - and the rule is the same one `TradeAlerts` needed for the same reason.
         *
         * Only the crossing **into** the band is an event. Leaving it is not: a price drifting back
         * out is not news, and announcing both would double every notification for a stock moving
         * around inside its own zone.
         */
        fun sweep(
            previous: Map<String, CallState>,
            calls: List<ScoredCall>,
            latestFor: (ticker: String) -> LatestPrice?,
            /** Calls the user is already in, which have `TradeAlerts` watching them instead. */
            held: Set<String> = emptySet(),
        ): CallAlerts {
            val changes = mutableListOf<CallChange>()
            val record = mutableMapOf<String, CallState>()
            val seen = mutableSetOf<String>()
            for (call in calls) {
                // A call that has settled is history, and one already held is the Portfolio's to
                // talk about - two notifications about one stock from two features is how a
                // channel gets switched off.
                if (call.outcome.judged || call.positionId in held) continue
                // A re-posting is the same call as the one it repeats. Announcing both would say
                // the same thing twice on the morning a standing recommendation comes into range.
                if (call.repeatOf != null) continue
                // Marked as still watched **before** the price is looked for. A stock whose feed
                // has gone quiet is still a call being watched, and dropping its reading would
                // forget which side of the band it was on - so the day prices come back, a band it
                // had been sitting in for a fortnight would be announced as though it had just
                // been reached.
                val id = call.alertId
                seen += id
                val price = latestFor(call.ticker)?.session?.traded?.close ?: continue
                val now = CallState(inBand = inBand(call, price))
                val before = previous[id]
                if (before == now) continue
                record[id] = now
                // First sight: stored so the next sweep has something to compare against, silent
                // because a band the price was already in is not something that just happened.
                if (before == null) continue
                if (now.inBand && !before.inBand) changes += CallChange(call, price)
            }
            return CallAlerts(
                changes = changes,
                record = record,
                // Everything the record no longer holds: a deleted report, a call that has since
                // settled, a stock that lost its prices. Its row goes with it, or the table grows
                // forever and a call that settles and is re-run months later is compared against a
                // reading nobody remembers.
                forgotten = previous.keys - seen,
            )
        }

        private fun inBand(call: ScoredCall, price: Double): Boolean {
            val low = call.entryLow ?: call.entryHigh ?: return false
            val high = call.entryHigh ?: call.entryLow ?: return false
            return price >= minOf(low, high) && price <= maxOf(low, high)
        }
    }
}

/**
 * The key an entry alert is remembered under.
 *
 * Deliberately **not** [positionId], which two channels calling one stock for one session share
 * because it names one holding. These are two calls printing two different buy zones, and the price
 * can be inside one and outside the other - so an alert keyed on the holding would announce the
 * first and silently swallow the second. [opinionId]'s key for the same reason, and reused rather
 * than re-derived so the two cannot disagree about what counts as one call.
 */
val ScoredCall.alertId: String get() = opinionId(ticker, openedOn, channel)
