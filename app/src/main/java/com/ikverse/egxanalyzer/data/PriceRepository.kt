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
    private val endpointTemplate: String = YAHOO_CHART_URL,
) {
    suspend fun refresh(
        tickers: Collection<String>,
        sessions: Int = Scoring.MAX_WINDOW_SESSIONS,
    ): PriceRefresh = coroutineScope {
        val wanted = Scoring.clampWindow(sessions)
        val normalized = tickers.map(Scoring::normalizeTicker).filter(String::isNotBlank).distinct()
        // A few at a time rather than all at once: this is an undocumented public endpoint and
        // hammering it from a phone would be both rude and likely to get throttled.
        val limit = Semaphore(CONCURRENCY)
        val fetched = normalized
            .map { ticker -> async { ticker to limit.withPermit { fetch(ticker, wanted) } } }
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

    private suspend fun fetch(ticker: String, wanted: Int): List<DailySession> =
        withContext(Dispatchers.IO) {
            runCatching {
                // A month of calendar days covers the longest window once weekends and market
                // holidays are removed.
                val url = URL("${endpointTemplate.format(ticker)}?interval=1d&range=1mo")
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("User-Agent", USER_AGENT)
                    if (connection.responseCode !in 200..299) return@runCatching emptyList()
                    val payload = connection.inputStream.bufferedReader().use { it.readText() }
                    parseChart(ticker, payload, wanted)
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(emptyList())
        }

    private fun parseChart(ticker: String, payload: String, wanted: Int): List<DailySession> {
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
        return days.takeLast(wanted)
    }

    fun earliestSession(): LocalDate? = localDataStore.earliestSessionDate()

    private companion object {
        const val YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s.CA"
        const val USER_AGENT = "Mozilla/5.0 (compatible; EGX-Analyzer)"
        const val SOURCE = "Yahoo Finance"
        const val CONCURRENCY = 4
    }
}
