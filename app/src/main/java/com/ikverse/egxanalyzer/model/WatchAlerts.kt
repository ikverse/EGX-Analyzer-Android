package com.ikverse.egxanalyzer.model

/**
 * A level the reader asked to be told about, on a stock they follow.
 *
 * [above] is "tell me when it rises to [level]"; false is "when it falls to". One shot: an alert
 * that fires is removed, like every other announcement here, which says a thing once.
 */
data class WatchAlert(
    /** Normalized through [Scoring.normalizeTicker]. */
    val ticker: String,
    val level: Double,
    val above: Boolean,
) {
    /** How the sheet and the notification name it. */
    val label: String get() = (if (above) "Rises to " else "Falls to ") + trimmed(level)

    private fun trimmed(value: Double): String {
        val rounded = Math.round(value * 1000) / 1000.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }
}

/** One alert that has gone off, with the price that set it off. */
data class WatchAlertFired(val alert: WatchAlert, val price: LatestPrice)

/** What one sweep decided: the alerts that fired, and the ones still standing. */
data class WatchAlertSweep(
    val fired: List<WatchAlertFired>,
    val remaining: List<WatchAlert>,
)

object WatchAlerts {
    /**
     * Compares every alert against the latest price of its stock.
     *
     * Pure, like `CallAlerts` and `TradeAlerts`: what counts as crossing a level is a rule about
     * prices and has to be testable without a device. A stock with no latest price fires nothing
     * and keeps its alert. A session still trading counts - an alert is about the price, and the
     * live price is a price.
     */
    fun sweep(alerts: List<WatchAlert>, latest: Map<String, LatestPrice>): WatchAlertSweep {
        val fired = mutableListOf<WatchAlertFired>()
        val remaining = mutableListOf<WatchAlert>()
        for (alert in alerts) {
            val price = latest[alert.ticker]
            val close = price?.session?.close
            val crossed = close != null &&
                if (alert.above) close >= alert.level else close <= alert.level
            if (crossed) fired += WatchAlertFired(alert, price!!) else remaining += alert
        }
        return WatchAlertSweep(fired, remaining)
    }
}
