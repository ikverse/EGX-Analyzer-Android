package com.ikverse.egxanalyzer.model

import kotlin.math.abs

/**
 * A level a trade is closing in on, and how the phone says so.
 *
 * Two of them and deliberately not four. The stop, because it is the only level a reader can still
 * do something about — a stop about to be taken is the one moment where selling early is a decision
 * rather than a regret. And target 2, because it is the one level that ends a trade outright, so a
 * reader holding for it wants to know it is nearly there. Target 1 is left out: it closes nothing
 * the reader has to act on, and a third alert per trade is how a channel gets switched off.
 */
enum class ApproachLevel(val summary: String, val label: String) {
    STOP("is closing on its stop", "stop"),
    TARGET2("is closing on target 2", "target 2"),
}

/**
 * Where a trade stood against one of its levels the last time anything was said about it.
 *
 * The counterpart of [CallState], and it exists for the identical reason: how far a price is from a
 * level is derived on every recompute and rightly stored nowhere, so nothing on disk remembers what
 * the reader has already been told.
 */
data class ApproachState(
    /** The last close sat within the threshold of the level. */
    val near: Boolean,
)

/** One trade that has just come within reach of a level, ready to be said out loud. */
data class ApproachChange(
    val position: PositionView,
    val level: ApproachLevel,
    /** How far it still has to go, in percent of the current price, for the notification to name. */
    val distancePercent: Double,
    /** The level itself, so the sentence can print the price the reader is watching. */
    val price: Double,
)

/**
 * Trades that have come within reach of their stop or of target 2.
 *
 * [TradeAlerts] reports what the market has **already** done to a trade and [CallAlerts] reports a
 * call becoming takeable. Both arrive at the moment the thing is settled, which is the one moment
 * nothing can be done about it: a stop announced is a stop already taken. This is the only alert in
 * the app that arrives while there is still a decision to make.
 *
 * **It reports a distance and never an instruction.** *AMOC is 1.4% from its stop* — the same
 * register as every other notification here. What to do about a stop coming up is exactly the sort
 * of judgement this app refuses to make on the reader's behalf everywhere else, and it is not going
 * to start on the lock screen.
 *
 * **Only open trades.** A settled one has nowhere left to go, and a trade the deadline has closed
 * is not one the reader is still holding. A trade kept open deliberately is very much included —
 * `PositionView.open` is true of it, and it is the trade most worth warning.
 *
 * Pure, with no Android in it, like [TradeAlerts] and [CallAlerts]: what counts as "closing on" is
 * a rule about trading and has to be checkable without waiting for a market. The caller does the
 * two things this cannot — raise the notification, and write [record] and [forgotten] back to disk.
 */
data class ApproachAlerts(
    val changes: List<ApproachChange> = emptyList(),
    /** Keys whose stored state has moved, with what to store. Unchanged trades are absent. */
    val record: Map<String, ApproachState> = emptyMap(),
    /** Keys no longer in the record at all, whose rows go with them. */
    val forgotten: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = changes.isEmpty() && record.isEmpty() && forgotten.isEmpty()

    companion object {

        /** What "closing on" means where the reader has not said, in percent of the price. */
        const val DEFAULT_THRESHOLD_PERCENT = 2

        /** The narrowest and widest the reader may set it to. */
        const val MIN_THRESHOLD_PERCENT = 1
        const val MAX_THRESHOLD_PERCENT = 10

        /**
         * Compares every open trade against what was last stored about each of its two levels.
         *
         * **First sight is recorded and never announced**, the rule [CallAlerts] needed and for the
         * same reason: without it the first sweep after this shipped would announce every trade
         * that happens to be sitting near a level, a month of standing positions arriving at once
         * as though each had just moved. It is also what keeps a quiet feed honest — a price that
         * has not been fetched for a fortnight is compared against the reading it was last seen at,
         * not treated as new.
         *
         * **Leaving the zone is recorded and not announced, and coming back is.** A price that
         * pulls away from its stop and later closes on it again has done the thing this alert is
         * for, twice. That does mean a price oscillating around the threshold can speak more than
         * once — accepted for the same reason [CallAlerts] accepts it, because the alternative is a
         * trade that drifts off its stop in the morning and is silently taken by it in the
         * afternoon.
         */
        fun sweep(
            previous: Map<String, ApproachState>,
            positions: List<PositionView>,
            thresholdPercent: Int = DEFAULT_THRESHOLD_PERCENT,
        ): ApproachAlerts {
            val changes = mutableListOf<ApproachChange>()
            val record = mutableMapOf<String, ApproachState>()
            val seen = mutableSetOf<String>()
            for (view in positions) {
                if (!view.open) continue
                // A trade whose stock changed scale is not measurable against its own levels: they
                // were printed in the old money and the price is quoted in the new. The app refuses
                // to value across a break everywhere else, and a lock screen is the last place that
                // refusal should lapse.
                if (view.priceScaleChanged) continue
                for (level in ApproachLevel.entries) {
                    val target = when (level) {
                        ApproachLevel.STOP -> view.position.stopLoss
                        ApproachLevel.TARGET2 -> view.position.target2
                    } ?: continue
                    val key = alertKey(view.position.id, level)
                    // Marked as still watched **before** the price is looked for, exactly as
                    // CallAlerts does it: dropping the reading on a stock whose feed has gone quiet
                    // would forget which side of the threshold it was on.
                    seen += key
                    val price = view.currentPrice ?: continue
                    if (price <= 0.0) continue
                    val distance = abs(target - price) / price * 100.0
                    val now = ApproachState(near = distance <= thresholdPercent)
                    val before = previous[key]
                    if (before == now) continue
                    record[key] = now
                    // First sight: stored so the next sweep has something to compare against,
                    // silent because a level the price was already near has not just been reached.
                    if (before == null) continue
                    if (now.near && !before.near) {
                        changes += ApproachChange(view, level, distance, target)
                    }
                }
            }
            return ApproachAlerts(
                changes = changes,
                record = record,
                // A trade that has closed, been sold, or been deleted. Its rows go with it, or the
                // table grows forever and a position re-recorded months later is compared against a
                // reading nobody remembers.
                forgotten = previous.keys - seen,
            )
        }

        /**
         * The key one trade's approach to one level is remembered under.
         *
         * Per level rather than per trade: a price can be near its stop and nowhere near target 2,
         * and one key for both would let whichever was checked first swallow the other.
         */
        fun alertKey(positionId: String, level: ApproachLevel): String = "$positionId|${level.name}"
    }
}
