package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Scoring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
     * A stock with no history yet is fetched in full. One that has history is fetched from where
     * that history stops, which is not the same as "the last few days": a phone left shut for a
     * fortnight has a fortnight-shaped hole, and a fixed short range can never reach back over it.
     * The hole used to be permanent, because every later refresh asked for the same few days again -
     * and a call whose window has a hole in it never reaches its deadline, so it never expires and
     * never shows as overdue.
     *
     * Re-asking for sessions already on disk costs nothing: they are stored by (ticker, date) and
     * overwrite themselves. It buys something, too - a session first saved while it was still
     * trading is corrected the next time it is fetched.
     */
    suspend fun refresh(
        tickers: Collection<String>,
        /**
         * The oldest session that still has to be judged for a stock, where there is one.
         *
         * An open trade is the case that matters: its window has to be complete for the deadline to
         * arrive, so a hole inside it is fetched again every refresh until it fills. Anything older
         * than that is settled and is left where it is.
         */
        neededFrom: (ticker: String) -> LocalDate? = { null },
    ): PriceRefresh = coroutineScope {
        val normalized = tickers.map(Scoring::normalizeTicker).filter(String::isNotBlank).distinct()
        val newest = withContext(Dispatchers.IO) {
            normalized.associateWith(localDataStore::latestSessionDate)
        }
        val today = LocalDate.now(ZoneId.of("UTC"))
        // A few at a time rather than all at once: this is an undocumented public endpoint and
        // hammering it from a phone would be both rude and likely to get throttled.
        val limit = Semaphore(CONCURRENCY)
        val fetched = normalized
            .map { ticker ->
                val from = fetchFrom(newest[ticker], neededFrom(ticker), today)
                async { ticker to limit.withPermit { fetchAllFeeds(ticker, from, today) } }
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
    private suspend fun fetchAllFeeds(
        ticker: String,
        from: LocalDate?,
        today: LocalDate,
    ): List<DailySession> {
        val merged = LinkedHashMap<java.time.LocalDate, DailySession>()
        // Reversed so the live feed, listed first, overwrites the legacy rows rather than the
        // other way round.
        for (symbol in symbolMap.feedsFor(ticker).asReversed()) {
            for (session in fetch(symbol, ticker, window(from, today))) {
                merged[session.date] = session
            }
        }
        // A dated window that comes back with nothing, on a stock that has history, is the one way
        // this can be worse than the fixed range it replaced - so it falls back to that range and
        // is no worse. Unproven endpoint behaviour is the reason: `range=max` quietly returns
        // monthly buckets, and an explicit period is not obliged to behave any better.
        if (merged.isEmpty() && from != null) {
            for (symbol in symbolMap.feedsFor(ticker).asReversed()) {
                for (session in fetch(symbol, ticker, "range=$FULL_RANGE")) {
                    merged[session.date] = session
                }
            }
        }
        return merged.values.sortedBy(DailySession::date)
    }

    private suspend fun fetch(symbol: String, ticker: String, query: String): List<DailySession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("${endpointTemplate.format(symbol)}?interval=1d&$query")
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
                val high = highs?.price(index)
                val low = lows?.price(index)
                // A session still open reports nulls, and sometimes zeros; it is not history yet.
                if (high == null || low == null) continue
                add(
                    DailySession(
                        ticker = ticker,
                        date = Instant.ofEpochSecond(stamps.optLong(index))
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate(),
                        high = high,
                        low = low,
                        close = closes?.price(index),
                        volume = volumes?.optDouble(index)?.takeUnless(Double::isNaN),
                        open = opens?.price(index),
                    ),
                )
            }
        }
        return days
    }

    fun earliestSession(): LocalDate? = localDataStore.earliestSessionDate()

    companion object {
        private const val YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s"

        /** A stock with no stored history: a year of daily sessions, which is what the feed offers
         *  at daily granularity. Asking for `max` returns monthly buckets instead. */
        private const val FULL_RANGE = "1y"

        /**
         * Re-asked for on every refresh, so the newest stored session is never trusted on its own.
         *
         * It may have been saved while it was still trading, and a weekend sits behind it either
         * way. Three days costs a couple of rows that overwrite themselves.
         */
        private const val OVERLAP_DAYS = 3L

        private const val USER_AGENT = "Mozilla/5.0 (compatible; EGX-Analyzer)"
        private const val SOURCE = "Yahoo Finance"
        private const val CONCURRENCY = 4

        /**
         * Where a stock's fetch has to start, or null to ask for the full history.
         *
         * The earlier of two dates, because both are holes worth filling: where the stored history
         * stops, and where the oldest thing still being judged begins. The second is what heals a
         * gap that already exists - starting at the newest stored session would step straight over
         * it and leave the trade that needs it stuck short of its deadline forever.
         */
        internal fun fetchFrom(
            latestStored: LocalDate?,
            neededFrom: LocalDate?,
            today: LocalDate,
        ): LocalDate? {
            // Nothing on disk is the one case that genuinely wants everything.
            val stored = latestStored ?: return null
            val since = stored.minusDays(OVERLAP_DAYS)
            val from = if (neededFrom != null && neededFrom < since) neededFrom else since
            // A clock that has gone backwards, or history stored ahead of today, would otherwise
            // ask for a window that ends before it starts.
            return if (from > today) today else from
        }

        /**
         * The query that names the window, dated where there is one to date.
         *
         * `period1`/`period2` rather than `range`, because a fixed range can only ever reach back a
         * fixed distance, and the distance that matters is however long the app went unopened.
         */
        internal fun window(from: LocalDate?, today: LocalDate): String {
            if (from == null) return "range=$FULL_RANGE"
            val zone = ZoneId.of("UTC")
            val start = from.atStartOfDay(zone).toEpochSecond()
            // Tomorrow, so a session that settled today is inside the window rather than on its
            // edge: period2 is exclusive of anything later in the same day.
            val end = today.plusDays(1).atStartOfDay(zone).toEpochSecond()
            return "period1=$start&period2=$end"
        }
    }
}

/**
 * One price from a Yahoo series, or null when the session has not really traded yet.
 *
 * A session still in progress comes back as null on some fields and as **zero** on others. Zero was
 * being stored as a real price, and a low of zero is under every stop loss ever printed, so every
 * call on that stock was judged stopped by a session that had not happened. No EGX stock trades at
 * or below nothing, which makes this safe to read as "not known".
 */
private fun JSONArray.price(index: Int): Double? =
    optDouble(index).takeUnless { it.isNaN() || it <= 0.0 }
