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
    /**
     * Stocks whose series changes scale partway through and did not come back onto one scale when
     * the whole history was refetched. Their calls are reported rather than judged.
     */
    val suspect: List<String> = emptyList(),
    /**
     * Stocks whose newest session is older than the exchange's own calendar explains.
     *
     * Reported separately from [unpriced], which means no history at all. A feed that answers every
     * request with a history frozen three weeks ago is the harder of the two to notice, and the one
     * that has actually happened here.
     */
    val stale: List<String> = emptyList(),
    /**
     * Stocks whose daily history was rebuilt from the intraday feed because no daily feed carries
     * them.
     *
     * Reported rather than done quietly. It is the app building sessions rather than reading them,
     * and every other place it does anything of the kind - a heal, a recorded break - says so.
     */
    val rebuilt: List<String> = emptyList(),
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
    /**
     * Builds a daily history for a stock the daily feeds do not carry, out of finer bars.
     *
     * A lambda rather than an `IntradayRepository` held here, for the reason `refresh` already
     * takes `neededFrom` as one: this needs an answer, not a collaborator, and the two repositories
     * are built side by side and know nothing of each other. Defaulting to none keeps every test
     * that constructs a `PriceRepository` fetching exactly what it always did.
     */
    private val derivedHistory: suspend (
        ticker: String,
        from: LocalDate?,
        today: LocalDate,
    ) -> List<DailySession> = { _, _, _ -> emptyList() },
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
        // The whole session rather than its date alone: a change of scale falls exactly between the
        // newest stored session and the first one fetched, so the check needs the price too.
        val newest = withContext(Dispatchers.IO) {
            normalized.associateWith(localDataStore::latestSession)
        }
        val today = LocalDate.now(ZoneId.of("UTC"))
        // A few at a time rather than all at once: this is an undocumented public endpoint and
        // hammering it from a phone would be both rude and likely to get throttled.
        val limit = Semaphore(CONCURRENCY)
        val fetched = normalized
            .map { ticker ->
                val previous = newest[ticker]
                val from = fetchFrom(previous?.date, neededFrom(ticker), today)
                async { limit.withPermit { fetchChecked(ticker, previous, from, today) } }
            }
            .map { it.await() }

        val stored = fetched.flatMap(Fetched::sessions)
        withContext(Dispatchers.IO) {
            // A healed series replaces what is on disk rather than merging with it: the rows it is
            // replacing are the ones on the old scale, and leaving them would keep exactly the
            // mixture this went to the trouble of refetching to get rid of.
            fetched.filter(Fetched::healed).forEach { result ->
                localDataStore.deleteSessions(result.ticker)
                localDataStore.clearPriceBreaks(result.ticker)
                localDataStore.clearIntraday(result.ticker)
                // The verdicts too. A settled call is frozen against the sessions it was judged on,
                // and this has just replaced every one of them - so the frozen answer describes a
                // history that is no longer on disk. Dropped rather than kept: re-scoring is what
                // the heal was for.
                localDataStore.clearSettledCalls(result.ticker)
            }
            // Written under the source they actually came from, and the derived rows first. The
            // table is keyed on (ticker, session_date) and replaces on conflict, so writing them
            // the other way round would let a rebuilt session overwrite one the exchange really
            // reported - the one direction this must never go.
            val (built, reported) = stored.partition(DailySession::derived)
            if (built.isNotEmpty()) localDataStore.saveSessions(built, DERIVED_SOURCE)
            if (reported.isNotEmpty()) localDataStore.saveSessions(reported, SOURCE)
            localDataStore.savePriceBreaks(fetched.flatMap(Fetched::breaks))
        }
        PriceRefresh(
            requested = normalized.size,
            priced = fetched.count { it.sessions.isNotEmpty() },
            sessionsStored = stored.size,
            // A ticker that fails is skipped rather than aborting the run: one delisted symbol
            // should not cost the rest of the list.
            unpriced = fetched.filter { it.sessions.isEmpty() }.map(Fetched::ticker),
            suspect = fetched.filter { it.breaks.isNotEmpty() }.map(Fetched::ticker),
            stale = fetched.filter(Fetched::stale).map(Fetched::ticker),
            rebuilt = fetched
                .filter { result -> result.sessions.any(DailySession::derived) }
                .map(Fetched::ticker),
        )
    }

    /** One stock's fetch, and what the sanity pass made of it. */
    private data class Fetched(
        val ticker: String,
        val sessions: List<DailySession>,
        val breaks: List<PriceBreak>,
        /**
         * The whole history was refetched to put it back on one scale, so what is on disk is the
         * old mixture and has to go before this is written over it.
         */
        val healed: Boolean,
        val stale: Boolean,
    )

    /**
     * Fetches one stock and checks that the result is all in the same money.
     *
     * The usual cause of a break is not a bad feed but this app's own incrementalism: a refresh
     * asks only for what it is missing, so after a split the stored half stays in the old prices
     * while the fetched half arrives in the new ones. Yahoo rewrites its own history when a stock
     * splits, which means the fix is simply to ask for all of it again - and then the break is gone,
     * because it was never in the feed.
     *
     * What survives that is a genuine discontinuity: a bad series, or a split the feed itself has
     * not applied. Recorded rather than corrected. Guessing a ratio and rescaling a year of prices
     * on it would be the app inventing history, and a call it cannot judge is a better answer than
     * a call it judges against numbers it made up.
     */
    private suspend fun fetchChecked(
        ticker: String,
        previous: DailySession?,
        from: LocalDate?,
        today: LocalDate,
    ): Fetched {
        val reported = fetchAllFeeds(ticker, from, today)
        // The whole stored series rather than its last session alone. The boundary between disk and
        // this fetch is where a break usually falls, but it is not the only place one can be: a
        // split that happened while an earlier version of this app was storing prices is already
        // buried inside the history, and checking only the boundary would never look at it. Reading
        // it back costs one indexed query against a table this same call is about to write to.
        val history = if (previous == null) emptyList() else withContext(Dispatchers.IO) {
            localDataStore.allSessions(ticker)
        }
        // A stock the daily feeds do not carry has almost nothing after that merge, however wide a
        // window was asked for - the ISIN endpoint serves one session and the legacy symbol is a
        // 404. Rebuilt from the intraday feed, which does hold it. Asked only when the stock really
        // is that thin, so a healthy series never pays for the request; once a history has been
        // built it is on disk, the stock is no longer thin, and it is never asked again.
        //
        // The whole hourly window rather than [from]. `from` is the incremental one - three days
        // behind the newest stored session - and a rebuild handed it returns three days of history
        // and stops. That is precisely how VLMRA ended up on five sessions: enough to clear
        // [THIN_HISTORY_SESSIONS] and never be asked again, and nowhere near the 30 a call is
        // judged over. This is the one fetch that must ignore where the stored history stops,
        // because where it stops is the hole it exists to fill.
        val days = if (isThin(history, reported)) {
            merge(derivedHistory(ticker, null, today), reported)
        } else {
            reported
        }
        val found = PriceSanity.breaks(ticker, history + days)
        // Nothing to heal, or nothing a refetch could heal: with no stored history this fetch was
        // already the whole of it, so asking again would return the same series and cost a request.
        if (found.isEmpty() || from == null) {
            return Fetched(ticker, days, found, healed = false, isStale(previous, days, today))
        }

        val full = fetchAllFeeds(ticker, null, today)
        // The refetch failed. Keep what was fetched and let the break stand: reporting a stock as
        // suspect is recoverable, and the next refresh tries again.
        if (full.isEmpty()) {
            return Fetched(ticker, days, found, healed = false, isStale(previous, days, today))
        }

        return Fetched(
            ticker = ticker,
            sessions = full,
            breaks = PriceSanity.breaks(ticker, full),
            healed = true,
            stale = isStale(null, full, today),
        )
    }

    private fun isStale(previous: DailySession?, days: List<DailySession>, today: LocalDate) =
        PriceSanity.isStale(listOfNotNull(previous) + days, today)

    /**
     * Whether the daily feeds have given this stock anything worth calling a history.
     *
     * Reported sessions only, and the exclusion is the point: a derived row is what the rebuild
     * produced, so counting those lets a short rebuild answer the question that decides whether to
     * rebuild. A stock then freezes at whatever its first attempt happened to return - five
     * sessions, in the case this was found in. What is being asked is only ever what the daily
     * feeds carry, and they never carry a derived row.
     */
    private fun isThin(history: List<DailySession>, fetched: List<DailySession>): Boolean =
        (
            history.filterNot(DailySession::derived).map(DailySession::date) +
                fetched.map(DailySession::date)
            )
            .distinct()
            .size < THIN_HISTORY_SESSIONS

    /**
     * Derived sessions with the reported ones laid over them.
     *
     * The order is the whole of it: what the exchange actually reported for a session always wins,
     * so a derived row can only ever fill a day the daily feeds had nothing for. That is also what
     * makes this safe to run again - the day a stock's real feed comes back, its rows replace the
     * derived ones for free.
     */
    private fun merge(
        derived: List<DailySession>,
        reported: List<DailySession>,
    ): List<DailySession> {
        if (derived.isEmpty()) return reported
        val byDate = LinkedHashMap<LocalDate, DailySession>()
        derived.forEach { byDate[it.date] = it }
        reported.forEach { byDate[it.date] = it }
        return byDate.values.sortedBy(DailySession::date)
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
        private const val CONCURRENCY = 4

        /**
         * Below how many known sessions a stock counts as one the daily feeds are not carrying.
         *
         * Measured behaviour on both sides of this. A stock with a working legacy symbol answers a
         * 40-day window with about 25 sessions; one without answers with the ISIN feed's single
         * session and nothing else, whatever window is asked for. Five sits in a gap so wide that
         * no threshold inside it behaves differently.
         *
         * A genuinely new listing is thin too, and rebuilding its history from the intraday feed
         * returns exactly the sessions that exist - which is right, not wrong.
         */
        internal const val THIN_HISTORY_SESSIONS = 5

        /** What the daily feeds reported, which is every row the app has ever stored until now. */
        internal const val SOURCE = "Yahoo Finance"

        /**
         * A session this app built by aggregating finer bars, rather than one a daily feed reported.
         *
         * The `source` column has carried provenance since the table was created and was written by
         * exactly one value, so a second value is the column being used for its purpose rather than
         * a flag smuggled through it - and it needed no migration, which on this table means no
         * chance of an upgrade taking the prices already on the phone with it.
         *
         * `LocalDataStore` compares against this constant to fill [DailySession.derived]. Changing
         * the string would silently reclassify every row already written under it, which is what
         * `PriceRepositoryDerivedTest` pins.
         */
        internal const val DERIVED_SOURCE = "Yahoo Finance (1h aggregated)"

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
internal fun JSONArray.price(index: Int): Double? =
    optDouble(index).takeUnless { it.isNaN() || it <= 0.0 }
