package com.ikverse.egxanalyzer.model

/**
 * A stock's newest stored close and how far it moved from the close before it, in percent.
 *
 * What the header's price strip prints for each held stock. Sessions with no close are skipped
 * rather than read as zero, and a stock with only one priced session has a close and no move - the
 * strip shows it flat rather than inventing a change.
 */
data class LastMove(val close: Double, val changePercent: Double)

fun lastMove(sessions: List<DailySession>): LastMove? {
    val closes = sessions.sortedBy(DailySession::date).mapNotNull(DailySession::close)
    val last = closes.lastOrNull() ?: return null
    val before = closes.dropLast(1).lastOrNull()
    val change = if (before == null || before == 0.0) 0.0 else (last - before) / before * 100.0
    return LastMove(last, change)
}
