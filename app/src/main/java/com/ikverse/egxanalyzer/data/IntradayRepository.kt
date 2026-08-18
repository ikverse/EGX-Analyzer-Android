package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.IntradayBar
import com.ikverse.egxanalyzer.model.Scoring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One session of one stock that a call could not order on daily figures alone. */
data class UnorderedSession(val ticker: String, val date: LocalDate)

/**
 * Five-minute bars for the sessions daily figures cannot order.
 *
 * A daily bar gives a high and a low with no sequence, so a session that both offered the entry and
 * reached a target says nothing about which came first. The same feed will answer that at five
 * minutes - it simply is not worth storing five-minute bars for every stock and every session to
 * have the answer ready, so they are fetched for the handful of sessions that actually need one.
 *
 * Separate from [PriceRepository] because almost nothing is shared but the host: this writes a
 * different table, never merges two feeds, never heals a series, and has a retention wall the daily
 * feed does not.
 */
class IntradayRepository(
    private val localDataStore: LocalDataStore,
    private val symbolMap: SymbolMap,
    private val endpointTemplate: String = YAHOO_CHART_URL,
) {
    /**
     * Fetches bars for whichever of [wanted] have not been asked about yet, and stores them.
     *
     * Returns how many sessions gained bars, so the caller can tell whether re-scoring would say
     * anything new. Sessions already asked about are skipped whatever the answer was: a closed
     * session's bars never change, and one the feed has nothing for will have nothing next time
     * either.
     */
    suspend fun fetchMissing(
        wanted: Collection<UnorderedSession>,
        today: LocalDate = LocalDate.now(ZoneId.of(UTC)),
    ): Int = coroutineScope {
        if (wanted.isEmpty()) return@coroutineScope 0
        val asked = withContext(Dispatchers.IO) { localDataStore.intradayFetched() }
        val outstanding = wanted
            .distinct()
            .filter { (it.ticker to it.date) !in asked }
            // Past the wall the feed simply has nothing, and asking costs a request per session per
            // refresh forever. The session stays unordered, which the card says in as many words.
            .filter { it.date >= today.minusDays(RETENTION_DAYS) }
            // Today is still trading. Its bars are not the whole session yet, and a session stored
            // half-finished would be recorded as asked about and never completed.
            .filter { it.date < today }
        if (outstanding.isEmpty()) return@coroutineScope 0

        val limit = Semaphore(CONCURRENCY)
        outstanding
            .map { session -> async { limit.withPermit { fetchOne(session) } } }
            .map { it.await() }
            .count { it }
    }

    /** True when the session gained bars; false when it was refused, empty, or unreachable. */
    private suspend fun fetchOne(session: UnorderedSession): Boolean {
        // The ISIN feed only. The legacy `SYMBOL.CA` symbol answers an intraday request with daily
        // bars rather than an error - see [parseBars] - and a stock still on the legacy feed has no
        // intraday history to offer at all.
        val symbol = symbolMap[session.ticker]?.yahooSymbol ?: return false
        if (symbol == legacyFormOf(session.ticker)) return false
        val bars = fetch(symbol, session) ?: return false
        withContext(Dispatchers.IO) {
            localDataStore.saveIntradayBars(session.ticker, session.date, bars)
        }
        return bars.isNotEmpty()
    }

    /**
     * One session's bars, or null when the request never got a usable answer.
     *
     * Null and empty are deliberately different: empty is the feed saying it has nothing for that
     * session, which is worth recording so it is never asked again, while null is a connection that
     * failed and must be retried.
     */
    private suspend fun fetch(symbol: String, session: UnorderedSession): List<IntradayBar>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val zone = ZoneId.of(UTC)
                val from = session.date.atStartOfDay(zone).toEpochSecond()
                val to = session.date.plusDays(1).atStartOfDay(zone).toEpochSecond()
                val url = URL(
                    "${endpointTemplate.format(symbol)}?interval=$INTERVAL" +
                        "&period1=$from&period2=$to",
                )
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("User-Agent", USER_AGENT)
                    // A window wider than the feed's intraday retention is refused outright with
                    // 422 rather than trimmed, so the caller's clamp is what keeps this a 200.
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val payload = connection.inputStream.bufferedReader().use { it.readText() }
                    parseIntradayBars(session.ticker, payload, INTERVAL)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }

    private fun legacyFormOf(ticker: String) = "${Scoring.normalizeTicker(ticker)}.CA"

    companion object {
        private const val YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s"

        /** Fine enough to separate the two events on all but the most crowded session. */
        internal const val INTERVAL = "5m"

        /**
         * How far back the feed still serves five-minute bars.
         *
         * Measured, not documented: a 59-day window answers, a 90-day one is refused with HTTP 422.
         * One day short of the 60 the feed appears to keep, so a request made as the wall arrives is
         * inside it rather than on it.
         */
        internal const val RETENTION_DAYS = 59L

        private const val USER_AGENT = "Mozilla/5.0 (compatible; EGX-Analyzer)"
        private const val CONCURRENCY = 4
        private const val UTC = "UTC"
    }
}

/**
 * The bars out of a chart response, but only if they really are bars.
 *
 * The granularity is checked rather than assumed, which is the whole reason this is a function of
 * its own. Yahoo's legacy `SYMBOL.CA` symbols **ignore** `interval` and answer an intraday request
 * with daily rows, and nothing in the shape of the response says so. Taken at face value, one daily
 * bar would be read as the whole session and would "prove" that the entry and the target happened
 * at the same instant - a confident answer to a question the feed was never asked.
 *
 * Null means the answer cannot be used. Empty means the feed genuinely has no bars for it, which is
 * worth recording so the session is never asked about again.
 */
internal fun parseIntradayBars(
    ticker: String,
    payload: String,
    interval: String,
): List<IntradayBar>? {
    val result = JSONObject(payload)
        .optJSONObject("chart")
        ?.optJSONArray("result")
        ?.optJSONObject(0)
        ?: return null
    if (result.optJSONObject("meta")?.optString("dataGranularity") != interval) return null
    val stamps = result.optJSONArray("timestamp") ?: return emptyList()
    val quote = result.optJSONObject("indicators")
        ?.optJSONArray("quote")
        ?.optJSONObject(0)
        ?: return emptyList()
    val highs = quote.optJSONArray("high")
    val lows = quote.optJSONArray("low")
    return buildList {
        for (index in 0 until stamps.length()) {
            val high = highs?.price(index)
            val low = lows?.price(index)
            // A five-minute bar in which nothing traded reports nulls. It is not a price and it
            // cannot have touched a level, so it is dropped rather than stored as a gap.
            if (high == null || low == null) continue
            add(
                IntradayBar(
                    ticker = ticker,
                    at = Instant.ofEpochSecond(stamps.optLong(index)),
                    high = high,
                    low = low,
                ),
            )
        }
    }
}
