package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.RecommendationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** What one stock is called, once the catalog has had its say. */
data class StockNames(
    val english: String?,
    val arabic: String?,
)

data class EgxStock(
    val ticker: String,
    val nameEnglish: String,
    val nameArabic: String? = null,
    val aliases: Set<String> = emptySet(),
)

object EgxCatalog {
    @Volatile
    private var stocks = EgxSeedStocks.ALL

    /**
     * Mirrors `AppPreferences.catalogEnrichmentEnabled`.
     *
     * Held here rather than passed in because the naming now happens in two places that have no
     * preferences to hand - [ConsolidatedParser], which rebuilds every saved report on load, and
     * the position reader. One switch both of them can see beats threading a boolean through both.
     */
    @Volatile
    var enrichmentEnabled: Boolean = true

    @Volatile
    var lastRefresh: Instant? = null
        private set

    /**
     * The catalog's names for a recommendation, in place of whatever the model called it.
     *
     * **No name here ever comes from the model, in any field, under any condition.** They were
     * invented per run and moved between runs - QNBA was "QNB Alahli" one run and "National Bank of
     * Kuwait - Egypt" the next, AMOC was "Alexandria Mineral Oils" and then "Amouk" - so a ticker
     * the catalog does not hold shows as a bare ticker, which is the honest reading. That is a
     * property of this function rather than of how complete [EgxSeedStocks] happens to be: a stock
     * added to the exchange tomorrow cannot drift while waiting for its row. The run records an
     * unknown ticker in its diagnostics, so a gap in the table is visible rather than silent.
     */
    fun enrich(value: RecommendationResult): RecommendationResult {
        val ticker = value.ticker.trim().uppercase().removeSuffix(".CA")
        if (!enrichmentEnabled) return value.copy(ticker = ticker)
        val stock = lookup(ticker, value.companyName)
            ?: return value.copy(ticker = ticker, companyName = ticker, companyNameArabic = null)
        return value.copy(
            ticker = stock.ticker,
            companyName = stock.nameEnglish,
            // Never the model's, even where the catalog holds no Arabic for the stock. That
            // fallback was the last route by which a name nobody had checked reached a screen, and
            // it drifted exactly like the rest: the same stock read out of two screenshots comes
            // back spelled two ways. A blank line says nothing; a name that changes says something
            // false. What fills these in is [EgxSeedStocks], not the next run.
            companyNameArabic = stock.nameArabic,
        )
    }

    /**
     * The catalog entry for a ticker, falling back to matching on the name the model gave.
     *
     * The name match is what catches a ticker written in a form the exchange does not use - "CIB"
     * for COMI. It stays a [singleOrNull] deliberately: two entries answering to one name is not a
     * match, it is an ambiguity, and naming the wrong company is worse than naming none.
     */
    private fun lookup(ticker: String, companyName: String?): EgxStock? {
        stocks.firstOrNull { it.ticker == ticker }?.let { return it }
        if (companyName.isNullOrBlank()) return null
        return stocks.singleOrNull { stock ->
            val names = buildSet {
                add(stock.nameEnglish)
                stock.nameArabic?.let(::add)
                addAll(stock.aliases)
            }
            names.any { normalize(it) == normalize(companyName) }
        }
    }

    /**
     * What to call a stock, given what the model called it.
     *
     * The catalog wins where it has an entry. Where it does not, both names come back null rather
     * than as the model's: those moved from run to run for the same ticker, which makes them a
     * reading of an image and not an identity. Only with enrichment switched off does the model's
     * own text come back through.
     */
    fun namesFor(ticker: String, english: String?, arabic: String?): StockNames {
        if (!enrichmentEnabled) return StockNames(english, arabic)
        val stock = find(ticker) ?: return StockNames(null, null)
        return StockNames(stock.nameEnglish, stock.nameArabic)
    }

    /**
     * One stock by ticker, with whatever names and aliases the catalog holds for it.
     *
     * [enrich] already does this lookup, but folded into a rewrite of a recommendation - it takes a
     * result and hands back a result. Ask AI needs the names on their own, to search on, and had no
     * way to reach them.
     */
    fun find(ticker: String): EgxStock? {
        val cleaned = ticker.trim().uppercase().removeSuffix(".CA")
        return stocks.firstOrNull { it.ticker == cleaned }
    }

    fun size(): Int = stocks.size

    fun entries(): List<EgxStock> = stocks

    /** Restores a previously downloaded catalog so a refresh is not needed on every launch. */
    fun restore(saved: List<EgxStock>) {
        if (saved.isEmpty()) return
        stocks = merge(EgxSeedStocks.ALL, saved)
    }

    /**
     * A download folded into what is already held, field by field.
     *
     * Whole-entry replacement is what this used to do, and the downloaded catalog carries no Arabic
     * name and no aliases at all - so the first refresh after install silently wiped the Arabic
     * name of every seeded stock and COMI's "CIB" alias with it, which is how a device ends up with
     * 223 stocks and not one Arabic name. A download can now add a listing and fill a blank; it
     * cannot empty a field that already has something in it.
     */
    private fun merge(base: List<EgxStock>, incoming: List<EgxStock>): List<EgxStock> {
        val merged = base.associateByTo(LinkedHashMap(), EgxStock::ticker)
        incoming.forEach { entry ->
            val held = merged[entry.ticker]
            merged[entry.ticker] = when {
                held == null -> entry
                else -> held.copy(
                    // A seed whose name is just its own ticker knows nothing worth keeping.
                    nameEnglish = held.nameEnglish.takeUnless { it == held.ticker }
                        ?: entry.nameEnglish,
                    nameArabic = held.nameArabic ?: entry.nameArabic,
                    aliases = held.aliases + entry.aliases,
                )
            }
        }
        return merged.values.sortedBy(EgxStock::ticker)
    }

    suspend fun refresh(
        endpoint: String = "https://demo.borsa.ashh.me/v1/stocks",
    ): Int = withContext(Dispatchers.IO) {
        require(endpoint.startsWith("https://")) { "Catalog endpoint must use HTTPS." }
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            require(status in 200..299) { "Catalog refresh failed (HTTP $status)." }
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = parseEntries(payload)
            stocks = merge(stocks, parsed)
            lastRefresh = Instant.now()
            parsed.size
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEntries(payload: String): List<EgxStock> {
        val trimmed = payload.trim()
        val values = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            // "symbols" is what the configured catalog actually returns; without it every
            // refresh parsed nothing and the app silently kept its seed list.
            root.optJSONArray("symbols")
                ?: root.optJSONArray("stocks")
                ?: root.optJSONArray("data")
                ?: root.optJSONArray("results")
                ?: JSONArray()
        }
        return buildList {
            for (index in 0 until values.length()) {
                val item = values.opt(index)
                if (item is String) {
                    cleanTicker(item)?.let { add(EgxStock(it, it)) }
                    continue
                }
                if (item !is JSONObject) continue
                val ticker = cleanTicker(
                    item.optString("ticker").ifBlank {
                        item.optString("symbol").ifBlank { item.optString("code") }
                    },
                ) ?: continue
                add(
                    EgxStock(
                        ticker = ticker,
                        nameEnglish = item.optString("name_en").ifBlank {
                            item.optString("company").ifBlank {
                                item.optString("name").ifBlank { ticker }
                            }
                        },
                        nameArabic = item.optString("name_ar").ifBlank {
                            item.optString("arabic_name")
                        }.takeIf(String::isNotBlank),
                    ),
                )
            }
        }
    }

    private fun cleanTicker(value: String): String? {
        val ticker = value.trim().uppercase().removeSuffix(".CA")
        return ticker.takeIf { it.matches(Regex("[A-Z][A-Z0-9]{1,9}")) }
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^0-9a-z\\u0621-\\u064a]"), "")
}
