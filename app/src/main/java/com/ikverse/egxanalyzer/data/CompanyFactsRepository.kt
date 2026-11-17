package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.CompanyFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * A company's facts off stockanalysis.com's public statistics page, kept for a week in
 * `company_facts.json`.
 *
 * One GET per stock, made only when its sheet is opened and the stored copy is over
 * [KEEP_MILLIS] old. No account and no key; the request names the stock and nothing else. The
 * file is device-local and stays out of the database, every backup, the sync channel and Save
 * diagnostics, like `home_cache.json`. Checked on 2026-11-17 against COMI.
 */
class CompanyFactsRepository(
    private val file: File,
    private val site: String = SITE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val fetchPage: suspend (String) -> String? = { request(it) },
) {
    private val lock = Mutex()
    private var kept: MutableMap<String, CompanyFacts>? = null

    /** The stored facts when fresh, else a fetch; the stored copy again if the page does not answer. */
    suspend fun facts(ticker: String): CompanyFacts? = lock.withLock {
        val all = kept ?: withContext(Dispatchers.IO) { load() }.toMutableMap().also { kept = it }
        val stored = all[ticker]
        if (stored != null && clock() - stored.fetchedAt < KEEP_MILLIS) return@withLock stored
        val html = fetchPage("$site/quote/egx/${ticker.lowercase()}/statistics/") ?: return@withLock stored
        val fresh = runCatching { StockAnalysisParser.parse(ticker, html, clock()) }.getOrNull()
            ?.takeUnless(CompanyFacts::isEmpty) ?: return@withLock stored
        all[ticker] = fresh
        withContext(Dispatchers.IO) { save(all) }
        fresh
    }

    private fun load(): Map<String, CompanyFacts> =
        runCatching { if (file.exists()) decodeCompanyFacts(file.readText()) else emptyMap() }.getOrDefault(emptyMap())

    private fun save(all: Map<String, CompanyFacts>) {
        runCatching {
            val draft = File(file.parentFile, "${file.name}.tmp")
            draft.writeText(encodeCompanyFacts(all))
            if (!draft.renameTo(file)) {
                file.writeText(draft.readText())
                draft.delete()
            }
        }
    }

    companion object {
        const val FILE_NAME = "company_facts.json"
        const val SITE = "https://stockanalysis.com"
        const val KEEP_MILLIS = 7L * 24 * 60 * 60 * 1000
        private const val USER_AGENT = "Mozilla/5.0 (compatible; EGX-Analyzer)"
        private const val MAX_PAGE_CHARS = 2_000_000

        private suspend fun request(address: String): String? = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(address).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 15_000
                    connection.setRequestProperty("Accept", "text/html")
                    connection.setRequestProperty("User-Agent", USER_AGENT)
                    if (connection.responseCode !in 200..299) return@runCatching null
                    connection.inputStream.bufferedReader().use { reader ->
                        val buffer = CharArray(8192)
                        val out = StringBuilder()
                        while (out.length < MAX_PAGE_CHARS) {
                            val read = reader.read(buffer)
                            if (read < 0) break
                            out.append(buffer, 0, read)
                        }
                        out.toString()
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }
}

/**
 * Reads the statistics page, ported from Signal Lab. Each figure is a table row: the label in the
 * first cell, the value in the second, whose `title` holds the exact number ("1,028,174,764") and
 * whose text the short one ("1.03B"). "n/a" is no value. The page's prose repeats some labels, so
 * only table rows are read. Patterns, not an HTML parser: nothing in the page is followed.
 */
object StockAnalysisParser {
    private val ROW = Regex("""<tr\b[^>]*>(.*?)</tr>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val CELL = Regex("""<td\b([^>]*)>(.*?)</td>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TITLE = Regex("""title\s*=\s*"([^"]*)"""")
    private val TAG = Regex("<[^>]*>|<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val SPACE = Regex("\\s+")

    const val PE = "PE Ratio"
    const val NET_INCOME = "Net Income"
    const val EPS = "Earnings Per Share (EPS)"
    const val DEBT = "Total Debt"
    const val DEBT_EQUITY = "Debt / Equity"

    /** Label to value of every table row whose value reads as a number. */
    fun rows(html: String): Map<String, Double?> {
        val out = LinkedHashMap<String, Double?>()
        for (r in ROW.findAll(html)) {
            val cells = CELL.findAll(r.groupValues[1]).toList()
            if (cells.size < 2) continue
            val label = text(cells[0].groupValues[2])
            if (label.isEmpty() || label in out) continue
            val title = TITLE.find(cells[1].groupValues[1])?.groupValues?.get(1)
            out[label] = number(title) ?: number(text(cells[1].groupValues[2]))
        }
        return out
    }

    fun parse(ticker: String, html: String, now: Long): CompanyFacts {
        val r = rows(html)
        return CompanyFacts(ticker, now, r[PE], r[NET_INCOME], r[EPS], r[DEBT], r[DEBT_EQUITY])
    }

    /** "1,028,174,764", "-4.59M", "2.87", "17.86%": a number with its B, M, K or T; null otherwise. */
    fun number(s: String?): Double? {
        if (s == null) return null
        val t = s.trim().replace(",", "").removeSuffix("%")
        if (t.isEmpty() || t.equals("n/a", true) || t == "-") return null
        val scale = when (t.last()) { 'B' -> 1e9; 'M' -> 1e6; 'K' -> 1e3; 'T' -> 1e12; else -> 1.0 }
        val digits = if (scale == 1.0) t else t.dropLast(1)
        return digits.toDoubleOrNull()?.times(scale)
    }

    private fun text(html: String): String =
        SPACE.replace(TAG.replace(html, " ").replace("&amp;", "&").replace("&nbsp;", " "), " ").trim()
}

internal fun encodeCompanyFacts(all: Map<String, CompanyFacts>): String = JSONObject().apply {
    all.forEach { (ticker, f) ->
        put(ticker, JSONObject().apply {
            put("at", f.fetchedAt)
            f.peRatio?.let { put("pe", it) }
            f.netIncome?.let { put("netIncome", it) }
            f.eps?.let { put("eps", it) }
            f.totalDebt?.let { put("debt", it) }
            f.debtToEquity?.let { put("debtToEquity", it) }
        })
    }
}.toString()

internal fun decodeCompanyFacts(text: String): Map<String, CompanyFacts> {
    val json = JSONObject(text)
    fun JSONObject.num(key: String): Double? = if (has(key)) optDouble(key).takeUnless(Double::isNaN) else null
    return json.keys().asSequence().mapNotNull { ticker ->
        val f = json.optJSONObject(ticker) ?: return@mapNotNull null
        ticker to CompanyFacts(
            ticker, f.optLong("at"), f.num("pe"), f.num("netIncome"), f.num("eps"), f.num("debt"), f.num("debtToEquity"),
        )
    }.toMap()
}
