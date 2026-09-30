package com.ikverse.egxanalyzer.model

/**
 * What the app has already told the user about one trade.
 *
 * The status of a trade is derived on every recompute and never stored, which is right - it is a
 * reading of the prices and the calendar, and a stored copy would be another thing that can be
 * wrong. But it also means nothing on disk remembers what the user has been told, and "tell me when
 * this changes" is a question about the difference between two readings. This is the smallest thing
 * that answers it.
 *
 * [open] is carried beside [status] because the status alone cannot see one of the endings.
 * A trade stopped out after taking target 1 keeps the label "Partial target hit" - the label is
 * about what the market did with the call, and it did reach target 1 - while the trade itself
 * closes. Watching the label alone would miss the close entirely.
 */
data class TradeState(val status: PositionStatus, val open: Boolean)

/**
 * What happened to a trade, in the words the notification uses.
 *
 * Only endings the market or the calendar brought about. Selling by hand and closing a trade are
 * the user's own acts, and an app that notifies someone about the button they just pressed is one
 * whose notifications get turned off.
 */
enum class TradeEvent(val summary: String) {
    TARGET1_HIT("took target 1"),
    TARGET2_HIT("reached target 2"),
    STOPPED_OUT("stopped out"),

    /**
     * Reached target 1 and was then taken by the stop, which is one event and not two.
     *
     * Its own wording because "stopped out" alone would report a trade that got somewhere as one
     * that got nothing.
     */
    STOPPED_AFTER_TARGET1("stopped out after target 1"),

    /** The window ran out with target 1 taken and target 2 never reached. */
    EXPIRED_ON_TARGET1("ran out of time on target 1"),
    EXPIRED("ran out of time"),
}

/** One trade and what became of it, ready to be said out loud. */
data class TradeChange(val position: PositionView, val event: TradeEvent)

/**
 * The difference between what the user has been told and where their trades now stand.
 *
 * Pure, and deliberately knows nothing about notifications, databases or Android: what counts as a
 * change is a rule about trading, and a rule about trading has to be testable without a device.
 * The caller does the two things this cannot - raise the notifications, and write [record] and
 * [forgotten] back to disk - and the writing is what makes a change announced once rather than at
 * every recompute for the rest of the trade's life.
 */
data class TradeAlerts(
    /** Worth a notification, in the order the portfolio handed them over. */
    val changes: List<TradeChange> = emptyList(),
    /** Ids whose stored state has moved, with what to store. Unchanged trades are absent. */
    val record: Map<String, TradeState> = emptyMap(),
    /** Ids no longer in the record at all, whose rows go with them. */
    val forgotten: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = changes.isEmpty() && record.isEmpty() && forgotten.isEmpty()

    companion object {

        /**
         * Compares every trade against what was last stored about it.
         *
         * A trade seen for the first time is **recorded and not announced**. Without that, the
         * first run after this feature ships would announce every trade ever taken - the app would
         * introduce itself by telling the user about a stop that was hit in June - and every new
         * purchase would immediately announce whatever the market had already done to the call
         * before they bought.
         */
        fun sweep(previous: Map<String, TradeState>, positions: List<PositionView>): TradeAlerts {
            val changes = mutableListOf<TradeChange>()
            val record = mutableMapOf<String, TradeState>()
            for (view in positions) {
                val now = TradeState(view.status, view.open)
                val before = previous[view.position.id]
                if (before == now) continue
                record[view.position.id] = now
                // First sight: stored so the next sweep has something to compare against, and
                // silent because nothing about it is news to the person who just recorded it.
                if (before == null) continue
                eventFor(before, view)?.let { changes += TradeChange(view, it) }
            }
            return TradeAlerts(
                changes = changes,
                record = record,
                forgotten = previous.keys - positions.map { it.position.id }.toSet(),
            )
        }

        /**
         * What to call the move from [before] to where [view] now stands, or nothing worth saying.
         *
         * Nothing worth saying covers two cases, and they are different. A trade the user sold or
         * closed by hand moved because they moved it. And a trade that has gone *back* to open -
         * which happens when a split heals a stock's whole history and the sessions behind the
         * verdict are refetched - is the app correcting itself, not the market doing something.
         * Announcing "AMOC is open again" would be reporting a repair as an event.
         */
        private fun eventFor(before: TradeState, view: PositionView): TradeEvent? {
            if (view.position.closedManually || view.realized) return null
            val closing = before.open && !view.open
            return when (view.status) {
                PositionStatus.FULL_TARGET_HIT -> TradeEvent.TARGET2_HIT
                PositionStatus.STOPPED_OUT -> TradeEvent.STOPPED_OUT
                PositionStatus.EXPIRED -> TradeEvent.EXPIRED
                PositionStatus.PARTIAL_TARGET_HIT -> when {
                    // Reached target 1 and ended in the same sweep - a refresh can cover several
                    // sessions at once, and a phone that was shut for a week comes back to both
                    // halves of the story. The ending is the half worth the notification.
                    closing && view.ranOutOfTime -> TradeEvent.EXPIRED_ON_TARGET1
                    closing -> TradeEvent.STOPPED_AFTER_TARGET1
                    before.status != PositionStatus.PARTIAL_TARGET_HIT -> TradeEvent.TARGET1_HIT
                    else -> null
                }

                PositionStatus.OPEN, PositionStatus.CLOSED_MANUALLY -> null
            }
        }
    }
}
