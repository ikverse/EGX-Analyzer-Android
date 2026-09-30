package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.IntradayBar
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.SeriesHarvest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId

/** One session of one stock that a call could not order on daily figures alone. */
data class UnorderedSession(val ticker: String, val date: LocalDate)

/**
 * What one pass of the archive harvest managed, for the line on screen that reports it.
 *
 * [failed] is counted separately from a stock that had nothing to do, because the two look
 * identical from the outside and only one of them means the phone is quietly falling behind a feed
 * that will not keep what it is missing.
 */
data class SeriesHarvestOutcome(
    val copied: Int = 0,
    val bars: Int = 0,
    val failed: Int = 0,
)

/**
 * The intraday feed, which this app asks three quite different questions of.
 *
 * **Ordering a session** ([fetchMissing]): a daily bar gives a high and a low with no sequence, so
 * a session that both offered the entry and reached a target says nothing about which came first.
 * Five-minute bars answer that. It is not worth storing them for every stock and every session, so
 * they are fetched for the handful that actually need one, and stored.
 *
 * **Building a history at all** ([dailyHistory]): a stock whose legacy `SYMBOL.CA` symbol is a 404
 * has no daily history to order. Hourly bars from the same endpoint are aggregated into daily
 * sessions, which are stored as prices rather than as bars and marked [DailySession.derived].
 *
 * **Keeping the session itself** ([harvestSeries]): the two above read bars to answer a question
 * and store only what the answer needed. This copies the whole five-minute session and keeps it,
 * because the feed will not - it serves about two months of them ([RETENTION_DAYS]) and then they
 * are gone permanently. Nothing in the app reads what it writes; it goes into its own database
 * file, [PriceSeriesStore], and exists so that the record can be asked questions nobody has thought
 * of yet.
 *
 * The three share the endpoint and nothing else - different granularity, different retention,
 * different table, different question. Separate from [PriceRepository] for the same reason it
 * always was: this never merges two feeds and never heals a series.
 */
class IntradayRepository(
    private val localDataStore: LocalDataStore,
    private val symbolMap: SymbolMap,
    private val endpointTemplate: String = YAHOO_CHART_URL,
    /**
     * Where a kept five-minute series is written, or null on a build that keeps none.
     *
     * Nullable rather than always present because [harvestSeries] is the one thing here that is
     * off by default and switched on per device, and because every test of the two questions above
     * predates it and has no business constructing an archive to ask them.
     */
    private val priceSeries: PriceSeriesStore? = null,
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

    /**
     * A daily history for a stock the daily feeds do not carry, built out of hourly bars.
     *
     * The one answer to a stock like VLMRA, whose `VLMRA.CA` symbol is a 404: the merge is left
     * with the ISIN feed's single session, and a call on it keeps a permanent hole in its window,
     * so it never completes and never expires. The same symbol answers an intraday request with
     * real history.
     *
     * **Hourly, not five-minute**, and the difference decides whether this is worth having. Both
     * were measured on 19 August 2026: the 5m feed reaches back about four weeks, which is under
     * the 30 sessions a call is judged over, while `interval=1h` reaches back about two years.
     * Aggregating to a daily bar needs the extremes of a session and its ends - five-minute
     * resolution buys nothing here that an hour does not, and buys it over a sixth of the history.
     *
     * Returns empty on anything that is not a usable answer, which leaves the stock exactly where
     * it already stands. This can only add history, never remove or contradict any.
     */
    suspend fun dailyHistory(
        ticker: String,
        from: LocalDate?,
        today: LocalDate = LocalDate.now(ZoneId.of(UTC)),
    ): List<DailySession> {
        // The ISIN feed only, for the same reason `fetchOne` insists on it: a legacy symbol ignores
        // `interval` and answers with daily rows, which would be aggregated into "derived" sessions
        // built from one daily bar each - identical to what they came from and marked as though
        // something finer stood behind them.
        val symbol = symbolMap[ticker]?.yahooSymbol ?: return emptyList()
        if (symbol == legacyFormOf(ticker)) return emptyList()
        val start = (from ?: today.minusDays(HOURLY_RANGE_DAYS))
            .coerceAtLeast(today.minusDays(HOURLY_RANGE_DAYS))
        val bars = fetchBars(symbol, start, today, HOURLY) ?: return emptyList()
        return DailyFromIntraday.aggregate(ticker, bars)
    }

    /**
     * Copies the five-minute session of every stock in [tickers] that is behind, and keeps it.
     *
     * **One request per stock, not one per session.** The endpoint answers a whole date range at
     * five-minute granularity, so a phone switching this on for the first time backfills the
     * feed's entire two-month window in one pass of about a hundred requests, and every evening
     * after that asks each stock for the one session it is missing. Fetching session by session
     * would have made the first run sixty times that against a public feed the app is a guest on.
     *
     * [finalThrough] is the newest session the exchange has finished with -
     * `ScheduleClock.lastFinalSession` - and it is the only guard against storing half a day as
     * though it were a whole one. A harvest that ran at noon would otherwise copy the morning and
     * mark the session done, and the afternoon would never be fetched because the feed will not
     * serve it twice and the mark says the day is finished.
     *
     * A stock whose request fails keeps its old mark and is simply behind, which the next fire
     * picks up. The alternative - advancing the mark anyway - would lose those sessions for good,
     * and this is the one table in the app where "fetch it again tomorrow" is not a remedy.
     */
    suspend fun harvestSeries(
        tickers: Collection<String>,
        finalThrough: LocalDate,
        today: LocalDate = LocalDate.now(ZoneId.of(UTC)),
    ): SeriesHarvestOutcome = coroutineScope {
        val series = priceSeries ?: return@coroutineScope SeriesHarvestOutcome()
        if (tickers.isEmpty()) return@coroutineScope SeriesHarvestOutcome()
        val marks = withContext(Dispatchers.IO) { series.harvestedThrough() }
        val limit = Semaphore(CONCURRENCY)
        val outcomes = tickers
            .distinct()
            .map { ticker ->
                async {
                    limit.withPermit {
                        harvestOne(series, ticker, marks[ticker], finalThrough, today)
                    }
                }
            }
            .map { it.await() }
        SeriesHarvestOutcome(
            copied = outcomes.count { it != null && it > 0 },
            bars = outcomes.filterNotNull().sum(),
            failed = outcomes.count { it == null },
        )
    }

    /**
     * One stock's outstanding sessions: how many bars were stored, or null where the fetch failed.
     *
     * Zero and null are deliberately different, exactly as they are in [fetch]. Zero is the feed
     * answering that it has nothing for those days, which is a real answer and advances the mark so
     * the days are never asked about again. Null is a request that did not arrive, which must not.
     */
    private suspend fun harvestOne(
        series: PriceSeriesStore,
        ticker: String,
        storedThrough: LocalDate?,
        finalThrough: LocalDate,
        today: LocalDate,
    ): Int? {
        // The ISIN feed only, the same insistence [fetchOne] makes and for the same reason: a
        // legacy `SYMBOL.CA` symbol ignores `interval` and answers an intraday request with daily
        // rows, which would be stored here as five-minute bars that are nothing of the kind.
        val symbol = symbolMap[ticker]?.yahooSymbol ?: return 0
        if (symbol == legacyFormOf(ticker)) return 0
        val from = SeriesHarvest.harvestFrom(storedThrough, finalThrough, RETENTION_DAYS, today)
        // Already current. The common case on a second fire in one evening, and it costs nothing.
            ?: return 0
        val bars = fetchBars(symbol, from, finalThrough, INTERVAL) ?: return null
        val kept = bars.map { bar ->
            PriceBar(
                ticker = ticker,
                at = bar.at,
                open = bar.open,
                high = bar.high,
                low = bar.low,
                close = bar.close,
                volume = bar.volume,
            )
        }
        withContext(Dispatchers.IO) { series.saveBars(ticker, kept, finalThrough) }
        return kept.size
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
        request(symbol, session.date, session.date, INTERVAL)
            ?.let { payload -> parseIntradayBars(session.ticker, payload, INTERVAL) }

    /** The same request, kept whole rather than reduced to the touch record the ordering needs. */
    private suspend fun fetchBars(
        symbol: String,
        from: LocalDate,
        to: LocalDate,
        interval: String,
    ): List<SessionBar>? =
        request(symbol, from, to, interval)?.let { parseSessionBars(it, interval) }

    /** The response body, or null where the request never got one. */
    private suspend fun request(
        symbol: String,
        from: LocalDate,
        to: LocalDate,
        interval: String,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val zone = ZoneId.of(UTC)
            val start = from.atStartOfDay(zone).toEpochSecond()
            val end = to.plusDays(1).atStartOfDay(zone).toEpochSecond()
            val url = URL(
                "${endpointTemplate.format(symbol)}?interval=$interval" +
                    "&period1=$start&period2=$end",
            )
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                // A window wider than the feed keeps for that granularity is refused outright with
                // 422 rather than trimmed, so the caller's clamp is what keeps this a 200.
                if (connection.responseCode !in 200..299) return@runCatching null
                connection.inputStream.bufferedReader().use { it.readText() }
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

        /**
         * The granularity a daily history is rebuilt from, for a stock the daily feeds do not carry.
         *
         * Not [INTERVAL]. Both were measured on 19 August 2026: five-minute bars reach back about
         * four weeks, hourly bars about two years. A call is judged over as many as 30 sessions, so
         * a four-week history would leave the exact hole this exists to fill - and a daily bar
         * needs a session's extremes and its two ends, which an hour gives as exactly as five
         * minutes does.
         */
        internal const val HOURLY = "1h"

        /**
         * How far back a daily history is rebuilt.
         *
         * Inside the two years the hourly feed was measured to hold, with room to spare: a window
         * wider than the feed keeps is refused outright rather than trimmed, and a refusal here
         * costs the whole history rather than its oldest end.
         */
        internal const val HOURLY_RANGE_DAYS = 700L

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
): List<IntradayBar>? = parseSessionBars(payload, interval)?.mapNotNull { bar ->
    // A bar in which nothing traded reports nulls. It is not a price and it cannot have touched a
    // level, so it is dropped rather than stored as a gap.
    val high = bar.high ?: return@mapNotNull null
    val low = bar.low ?: return@mapNotNull null
    IntradayBar(ticker = ticker, at = bar.at, high = high, low = low)
}
