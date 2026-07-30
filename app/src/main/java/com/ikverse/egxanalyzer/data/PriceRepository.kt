package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
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

/** What a refresh managed to fetch, so the screen can say which stocks have no history. */
data class PriceRefresh(
    val requested: Int,
    val priced: Int,
    val sessionsStored: Int,
    val unpriced: List<String>,
)

/**
 * Daily highs and lows for EGX stocks.
 *
 * The quote feed the rest of the app uses reports only the current price with no session date, so
 * it cannot build history: storing it against today's calendar date invents a session on any day
 * the market did not trade. Yahoo's chart endpoint dates every session itself, so it is the only
 * thing that writes here.
 */
class PriceRepository(
    private val localDataStore: LocalDataStore,
    private val symbolMap: SymbolMap,
    private val endpointTemplate: String = YAHOO_CHART_URL,
) {
    /**
     * Brings stored prices up to date.
     *
     * A closed session never changes, so anything already stored is left alone. A stock with no
     * history yet is fetched in full; one that already has history only needs the days since, and
     * a short range covers those without asking for a year of prices that are already on disk.
     */
    suspend fun refresh(tickers: Collection<String>): PriceRefresh = coroutineScope {
        val normalized = tickers.map(Scoring::normalizeTicker).filter(String::isNotBlank).distinct()
        val known = withContext(Dispatchers.IO) { localDataStore.pricedTickers() }
        // A few at a time rather than all at once: this is an undocumented public endpoint and
        // hammering it from a phone would be both rude and likely to get throttled.
        val limit = Semaphore(CONCURRENCY)
        val fetched = normalized
            .map { ticker ->
                val range = if (ticker in known) RECENT_RANGE else FULL_RANGE
                async { ticker to limit.withPermit { fetchAllFeeds(ticker, range) } }
            }
            .map { it.await() }

        val stored = fetched.flatMap { (_, days) -> days }
        if (stored.isNotEmpty()) {
            withContext(Dispatchers.IO) { localDataStore.saveSessions(stored, SOURCE) }
        }
        PriceRefresh(
            requested = normalized.size,
            priced = fetched.count { (_, days) -> days.isNotEmpty() },
            sessionsStored = stored.size,
            // A ticker that fails is skipped rather than aborting the run: one delisted symbol
            // should not cost the rest of the list.
            unpriced = fetched.filter { (_, days) -> days.isEmpty() }.map { (ticker, _) -> ticker },
        )
    }

    /**
     * Reads every feed that carries this stock and merges them under the exchange's own code.
     *
     * Yahoo split each EGX listing across two symbols on 2026-07-30, so one alone is always
     * missing either the history or everything recent. Where both report the same session the
     * newer feed wins, since the legacy one froze mid-migration.
     */
    private suspend fun fetchAllFeeds(ticker: String, range: String): List<DailySession> {
        val merged = LinkedHashMap<java.time.LocalDate, DailySession>()
        // Reversed so the live feed, listed first, overwrites the legacy rows rather than the
        // other way round.
        for (symbol in symbolMap.feedsFor(ticker).asReversed()) {
            for (session in fetch(symbol, ticker, range)) {
                merged[session.date] = session
            }
        }
        return merged.values.sortedBy(DailySession::date)
    }

    private suspend fun fetch(symbol: String, ticker: String, range: String): List<DailySession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("${endpointTemplate.format(symbol)}?interval=1d&range=$range")
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("User-Agent", USER_AGENT)
                    if (connection.responseCode !in 200..299) return@runCatching emptyList()
                    val payload = connection.inputStream.bufferedReader().use { it.readText() }
                    parseChart(ticker, payload)
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(emptyList())
        }

    private fun parseChart(ticker: String, payload: String): List<DailySession> {
        val result = JSONObject(payload)
            .optJSONObject("chart")
            ?.optJSONArray("result")
            ?.optJSONObject(0)
            ?: return emptyList()
        val stamps = result.optJSONArray("timestamp") ?: return emptyList()
        val quote = result.optJSONObject("indicators")
            ?.optJSONArray("quote")
            ?.optJSONObject(0)
            ?: return emptyList()
        val opens = quote.optJSONArray("open")
        val highs = quote.optJSONArray("high")
        val lows = quote.optJSONArray("low")
        val closes = quote.optJSONArray("close")
        val volumes = quote.optJSONArray("volume")

        val days = buildList {
            for (index in 0 until stamps.length()) {
                val high = highs?.optDouble(index)?.takeUnless(Double::isNaN)
                val low = lows?.optDouble(index)?.takeUnless(Double::isNaN)
                // A session still open reports nulls; it is not history yet.
                if (high == null || low == null) continue
                add(
                    DailySession(
                        ticker = ticker,
                        date = Instant.ofEpochSecond(stamps.optLong(index))
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate(),
                        high = high,
                        low = low,
                        close = closes?.optDouble(index)?.takeUnless(Double::isNaN),
                        volume = volumes?.optDouble(index)?.takeUnless(Double::isNaN),
                        open = opens?.optDouble(index)?.takeUnless(Double::isNaN),
                    ),
                )
            }
        }
        return days
    }

    fun earliestSession(): LocalDate? = localDataStore.earliestSessionDate()

    private companion object {
        const val YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s"

        /** A stock with no stored history: a year of daily sessions, which is what the feed offers
         *  at daily granularity. Asking for `max` returns monthly buckets instead. */
        const val FULL_RANGE = "1y"

        /** A stock already on disk: only the sessions since, with slack for weekends and holidays. */
        const val RECENT_RANGE = "5d"
        const val USER_AGENT = "Mozilla/5.0 (compatible; EGX-Analyzer)"
        const val SOURCE = "Yahoo Finance"
        const val CONCURRENCY = 4
    }
}
