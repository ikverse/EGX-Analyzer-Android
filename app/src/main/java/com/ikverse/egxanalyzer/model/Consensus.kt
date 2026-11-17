package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/** One stock that several channels have called lately, still open. */
data class SharedCall(
    val ticker: String,
    /** The channels that called it, newest call first. */
    val channels: List<String>,
    /** The newest of those calls. */
    val latest: LocalDate,
)

/**
 * Stocks two or more channels called within the last [WINDOW_SESSIONS] sessions, whose calls are
 * still open: a fact about the record, ordered by how many channels and then by how recent.
 *
 * **Crowding, not confirmation**, as `CallContext` on the call card says of the same-day count:
 * several channels reading one chart in one week is one idea going round, not independent readings
 * of it. So the tile is "Called by several channels", the order is the count and never a channel's
 * rank, and nothing here scores or recommends. Whether it is worth anything is for the record.
 *
 * A channel that called the same stock twice counts once. Sessions are counted Sunday to Thursday,
 * as `DividendBook.shift` does.
 */
object Consensus {
    const val WINDOW_SESSIONS = 10

    fun shared(calls: List<ScoredCall>, today: LocalDate): List<SharedCall> {
        val since = DividendBook.shift(today, -WINDOW_SESSIONS)
        return calls
            .filter { it.outcome == Outcome.OPEN && it.openedOn >= since }
            .groupBy { Scoring.normalizeTicker(it.ticker) }
            .mapNotNull { (ticker, made) ->
                val channels = made.sortedByDescending(ScoredCall::openedOn).map(ScoredCall::channel).distinct()
                if (channels.size < 2) null else SharedCall(ticker, channels, made.maxOf(ScoredCall::openedOn))
            }
            .sortedWith(compareByDescending<SharedCall> { it.channels.size }.thenByDescending { it.latest }.thenBy { it.ticker })
    }

    /** The one stock's entry, for its sheet. */
    fun forStock(calls: List<ScoredCall>, ticker: String, today: LocalDate): SharedCall? =
        shared(calls, today).firstOrNull { it.ticker == Scoring.normalizeTicker(ticker) }
}
