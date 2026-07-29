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

    fun size(): Int = stocks.size

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
            root.optJSONArray("stocks")
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
