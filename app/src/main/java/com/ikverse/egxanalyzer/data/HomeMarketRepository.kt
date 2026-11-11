package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DatedClose
import com.ikverse.egxanalyzer.model.IndexQuote
import com.ikverse.egxanalyzer.model.OutsideQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The Home page's market figures: four EGX indices and three numbers from outside the exchange.
 *
 * The same public chart feed the prices come from, asked seven small questions. Nothing here is
 * stored in the database and nothing is scored: an index is not a listing, so it stays out of the
 * stored prices, the catalog and the feed's health checks, exactly as the EGX30 line on the
 * Portfolio chart does.
 *
 * **Indices are asked for hourly bars.** The feed's daily endpoint answers an EGX index with its
 * newest session alone; `interval=1h` answers with about three months (measured 2026-11-12 on all
 * four: 320 bars over 64 sessions each). The outside numbers are ordinary symbols and answer daily.
 *
 * A symbol that does not answer is simply absent from the result, and an empty result leaves Home
 * showing what it last had.
 */
class HomeMarketRepository(
    private val endpointTemplate: String = YAHOO_CHART_URL,
) {

    suspend fun indices(): List<IndexQuote> = coroutineScope {
        Indices.map { spec ->
            async {
                request(spec.symbol, HOURLY, INDEX_RANGE)?.let { runCatching { parseIndexQuote(spec, it) }.getOrNull() }
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun outside(): List<OutsideQuote> = coroutineScope {
        Outside.map { spec ->
            async {
                request(spec.symbol, DAILY, OUTSIDE_RANGE)?.let { runCatching { parseOutsideQuote(spec, it) }.getOrNull() }
            }
        }.awaitAll().filterNotNull()
    }

    /** The response body, or null where the request never got one. */
    private suspend fun request(symbol: String, interval: String, range: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("${endpointTemplate.format(symbol)}?interval=$interval&range=$range")
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("User-Agent", USER_AGENT)
                    if (connection.responseCode !in 200..299) return@runCatching null
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }

    /** A symbol on the feed, already escaped for the path, and what Home calls it. */
    data class Spec(val key: String, val name: String, val symbol: String)

    companion object {
        private const val YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s"
        private const val USER_AGENT = "Mozilla/5.0 (compatible; EGX-Analyzer)"
        internal const val HOURLY = "1h"
        internal const val DAILY = "1d"
        private const val INDEX_RANGE = "3mo"
        private const val OUTSIDE_RANGE = "1mo"

        /** The key of the index that leads the page. */
        const val EGX30 = "EGX30"

        /** In the order Home draws them: the EGX30 leads, the other three share a row. */
        val Indices = listOf(
            Spec(EGX30, "EGX30", "%5ECASE30"),
            Spec("EGX70", "EGX70 EWI", "%5EEGX70EWI.CA"),
            Spec("EGX100", "EGX100 EWI", "%5EEGX100EWI.CA"),
            Spec("SHARIAH", "Shariah 33", "%5ESHARIAH.CA"),
        )

        val Outside = listOf(
            Spec("USDEGP", "USD / EGP", "USDEGP%3DX"),
            Spec("GOLD", "Gold · oz", "GC%3DF"),
            Spec("BRENT", "Brent", "BZ%3DF"),
        )
    }
}

/**
 * An index out of an hourly chart response: its daily closes, the newest as its level and the one
 * before as what it moved from.
 *
 * Where the bars cannot be used - the feed answered at another granularity, or with none - the
 * level printed in the response's own header is taken instead and the tile shows it without a move
 * or a chart. Null where there is not even that.
 */
internal fun parseIndexQuote(spec: HomeMarketRepository.Spec, payload: String): IndexQuote? {
    val closes = parseSessionBars(payload, HomeMarketRepository.HOURLY)
        ?.let { DailyFromIntraday.aggregate(spec.key, it) }
        .orEmpty()
        .mapNotNull { session -> session.close?.let { DatedClose(session.date, it) } }
    if (closes.isNotEmpty()) {
        return IndexQuote(
            key = spec.key,
            name = spec.name,
            level = closes.last().close,
            previous = closes.getOrNull(closes.size - 2)?.close,
            closes = closes,
        )
    }
    val level = headerPrice(payload) ?: return null
    return IndexQuote(spec.key, spec.name, level, previous = null)
}

/** An outside number out of a daily chart response: its newest close and the one before it. */
internal fun parseOutsideQuote(spec: HomeMarketRepository.Spec, payload: String): OutsideQuote? {
    val closes = parseSessionBars(payload, HomeMarketRepository.DAILY).orEmpty().mapNotNull { it.close }
    val value = closes.lastOrNull() ?: headerPrice(payload) ?: return null
    return OutsideQuote(spec.key, spec.name, value, closes.getOrNull(closes.size - 2))
}

/** `meta.regularMarketPrice`, where the response carries one. */
private fun headerPrice(payload: String): Double? =
    runCatching {
        JSONObject(payload).optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0)
            ?.optJSONObject("meta")?.optDouble("regularMarketPrice")
    }.getOrNull()?.takeUnless { it.isNaN() || it <= 0.0 }
