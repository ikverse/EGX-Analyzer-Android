package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.RecommendationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class EgxStock(
    val ticker: String,
    val nameEnglish: String,
    val nameArabic: String? = null,
    val aliases: Set<String> = emptySet(),
)

object EgxCatalog {
    private val seedStocks = listOf(
        EgxStock("ABUK", "Abu Qir Fertilizers and Chemicals", "أبو قير للأسمدة والصناعات الكيماوية"),
        EgxStock("BTFH", "Beltone Holding", "بلتون القابضة"),
        EgxStock("CCAP", "Qalaa Holdings", "القلعة للاستثمارات المالية"),
        EgxStock("CICH", "CI Capital Holding", "سي آي كابيتال القابضة"),
        EgxStock(
            "COMI",
            "Commercial International Bank Egypt",
            "البنك التجاري الدولي",
            setOf("CIB", "Commercial International Bank", "التجاري الدولي"),
        ),
        EgxStock("EAST", "Eastern Company", "الشرقية للدخان"),
        EgxStock("EGAL", "Egypt Aluminum", "مصر للألومنيوم"),
        EgxStock("FWRY", "Fawry for Banking and Payment Technology Services", "فوري"),
        EgxStock("HDBK", "Housing and Development Bank", "بنك التعمير والإسكان"),
        EgxStock("HRHO", "EFG Holding", "إي إف جي القابضة"),
        EgxStock("MASR", "Madinet Masr for Housing and Development", "مدينة مصر"),
        EgxStock("MFPC", "Misr Fertilizers Production Company", "موبكو", setOf("MOPCO")),
        EgxStock("OCDI", "Six of October Development and Investment", "سوديك"),
        EgxStock("ORWE", "Oriental Weavers", "النساجون الشرقيون"),
        EgxStock("PHDC", "Palm Hills Development", "بالم هيلز"),
        EgxStock("SWDY", "Elsewedy Electric", "السويدي إليكتريك"),
        EgxStock("TMGH", "Talaat Moustafa Group Holding", "مجموعة طلعت مصطفى"),
    )
    @Volatile
    private var stocks = seedStocks
    @Volatile
    var lastRefresh: Instant? = null
        private set

    fun enrich(value: RecommendationResult): RecommendationResult {
        val ticker = value.ticker.trim().uppercase().removeSuffix(".CA")
        val byTicker = stocks.associateBy(EgxStock::ticker)
        val stock = byTicker[ticker] ?: stocks.singleOrNull { stock ->
            val names = buildSet {
                add(stock.nameEnglish)
                stock.nameArabic?.let(::add)
                addAll(stock.aliases)
            }
            names.any { normalize(it) == normalize(value.companyName) }
        } ?: return value.copy(ticker = ticker)
        return value.copy(
            ticker = stock.ticker,
            companyName = stock.nameEnglish,
            companyNameArabic = stock.nameArabic ?: value.companyNameArabic,
        )
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

    /**
     * The EGX 33 Shariah index, as the exchange's September 2026 review left it.
     *
     * Held here rather than fetched, because nothing the app talks to carries index membership. The
     * catalog endpoint answers with `symbol`, `name` and `sector` and nothing else; the exchange
     * publishes the constituents on a page behind a bot challenge no plain HTTP client can answer;
     * and the one machine-readable third-party list was still missing this review's addition two
     * days after it took effect. So the exchange's own announcement is the source, transcribed.
     *
     * 34 symbols, 33 companies: Faisal Islamic Bank is listed twice, in pounds as `FAIT` and in
     * dollars as `FAITA`, and a channel can name either.
     *
     * The index is reviewed every March and September, so this list goes stale twice a year and is
     * corrected by editing it. `ICFC` is deliberately here although the catalog's 223 symbols do
     * not carry it - membership is a fact about the company, not about whether a price feed
     * reaches it.
     *
     * Internal rather than private so the test can count it. A transcribed list has no source to
     * be checked against, which leaves the count as the only thing that catches a symbol dropped
     * or repeated while editing - and a repeat is invisible from outside, because a set eats it.
     */
    internal val egx33 = setOf(
        "ACGC", "ADIB", "AMOC", "ARCC", "ATQA", "CLHO", "EFID", "EFIH", "EGAL", "EGAS",
        "ETEL", "ETRS", "FAIT", "FAITA", "GOUR", "ICFC", "IFAP", "ISPH", "JUFO", "LCSW",
        "MASR", "MCQE", "MPCO", "MTIE", "OCDI", "ORAS", "ORHD", "ORWE", "PHDC", "RACC",
        "RMDA", "SAUD", "SKPC", "TMGH",
    )

    /**
     * Whether the exchange counts this stock among the Shariah-compliant thirty-three.
     *
     * Cleaned the way [find] cleans, so it answers for a ticker in any form a card carries it -
     * `comi`, `COMI`, or the `COMI.CA` the price feed uses.
     */
    fun isEgx33(ticker: String): Boolean =
        ticker.trim().uppercase().removeSuffix(".CA") in egx33

    fun size(): Int = stocks.size

    fun entries(): List<EgxStock> = stocks

    /** Restores a previously downloaded catalog so a refresh is not needed on every launch. */
    fun restore(saved: List<EgxStock>) {
        if (saved.isEmpty()) return
        stocks = (seedStocks + saved).associateBy(EgxStock::ticker).values.sortedBy(EgxStock::ticker)
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
            val merged = (seedStocks + parsed).associateBy(EgxStock::ticker).values
                .sortedBy(EgxStock::ticker)
            stocks = merged
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
