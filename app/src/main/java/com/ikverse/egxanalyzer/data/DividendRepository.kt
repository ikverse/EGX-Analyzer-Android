package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DividendNews
import com.ikverse.egxanalyzer.model.DividendNewsKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Arab Finance's public dividend news page (the "توزيعات" tag): server-rendered Arabic HTML, no
 * account and no key. One GET, and nothing the user holds is in it.
 *
 * Checked on 2026-11-17: twenty items, about two months of them, each with the ticker in brackets
 * and the payment day in the wording [ArabFinanceParser] reads.
 */
class DividendRepository(private val address: String = NEWS_URL) {

    /** The page's items, or null where it never answered, so the caller keeps what it had. */
    suspend fun news(): List<DividendNews>? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(address).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "text/html")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                if (connection.responseCode !in 200..299) return@runCatching null
                val html = connection.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(8192)
                    val out = StringBuilder()
                    while (out.length < MAX_PAGE_CHARS) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        out.append(buffer, 0, read)
                    }
                    out.toString()
                }
                ArabFinanceParser.parse(html)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    companion object {
        const val SITE = "https://www.arabfinance.com"
        const val NEWS_URL = "$SITE/ar/home/tagresult?slug=724"
        private const val USER_AGENT = "Mozilla/5.0 (compatible; EGX-Analyzer)"

        /** The page measured about 400 KB. A cap, so an address answering with anything else is not read whole. */
        private const val MAX_PAGE_CHARS = 3_000_000
    }
}

/**
 * Reads the dividend news page. Ported from Signal Lab's parser, and tolerant on purpose: it does
 * not rely on the page's classes or layout beyond "a link to a news item, then its summary", and
 * reads the facts from the wording, which is fixed - the ticker in brackets, "بواقع ... جنيه" for
 * the amount, "اعتبارا من" for the payment day and "الجمعية العامة ... المنعقدة" for the assembly.
 *
 * Patterns, not an HTML parser, like `parseRss`: the page is somebody else's document, nothing in
 * it is followed or rendered, and only an `https` link to Arab Finance itself is kept.
 */
object ArabFinanceParser {
    private val ITEM_LINK = Regex(
        """<a\b[^>]*href\s*=\s*["']([^"']*/news/newdetails/[^"']*)["'][^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val TAG = Regex("<[^>]*>")
    private val SPACE = Regex("\\s+")
    private val TICKER = Regex("""\(\s*-?\s*([A-Z][A-Z0-9]{2,5})(?:\.CA)?\s*\)""")
    private val AMOUNT = Regex("""(?:بواقع|بقيمة|قدره|قيمته)\s*(\d+(?:[.,]\d+)?)\s*(جنيه|جنيها|جنيهات|قرش|قرشا|قروش)""")
    private const val DATE =
        """(\d{1,2})\s*/\s*(\d{1,2})\s*/\s*(\d{4})(?!\d)|(\d{4})\s*/\s*(\d{1,2})\s*/\s*(\d{1,2})(?!\d)|(\d{1,2})\s+(\S+)\s+(\d{4})(?!\d)"""
    private val PAYMENT = Regex("""اعتبارا\s*من\s*(?:يوم\s*)?(?:$DATE)""")
    private val ASSEMBLY_DAY = Regex("""(?:انعقدت|المنعقدة|المنعقده|انعقاد)\s*(?:\S+\s*){0,2}?(?:$DATE)""")
    private val UPDATED = Regex("""تحديث[^0-9]{0,20}(\d{4})/(\d{1,2})/(\d{1,2})""")

    private val MONTHS = mapOf(
        "يناير" to 1, "فبراير" to 2, "مارس" to 3, "ابريل" to 4, "مايو" to 5, "يونيو" to 6, "يونيه" to 6,
        "يوليو" to 7, "يوليه" to 7, "اغسطس" to 8, "سبتمبر" to 9, "اكتوبر" to 10, "نوفمبر" to 11, "ديسمبر" to 12,
    )

    fun parse(html: String, baseUrl: String = DividendRepository.SITE): List<DividendNews> {
        val links = ITEM_LINK.findAll(html).toList()
        val out = ArrayList<DividendNews>()
        for ((i, m) in links.withIndex()) {
            val end = if (i + 1 < links.size) links[i + 1].range.first else minOf(html.length, m.range.last + 4000)
            val headline = text(m.groupValues[2])
            if (headline.isEmpty()) continue
            val tail = text(html.substring(m.range.last + 1, maxOf(m.range.last + 1, end)))
            item(baseUrl, m.groupValues[1], headline, tail)?.let { out += it }
        }
        return out.distinctBy { it.url }
    }

    /** One item from its headline and the text after it (summary, then the "last updated" line). */
    fun item(baseUrl: String, href: String, headline: String, tail: String): DividendNews? {
        val cut = tail.indexOf("تحديث").let { if (it < 0) tail.length else it }
        val summary = normalize(tail.substring(0, cut))
        val head = normalize(headline)
        val code = TICKER.find(summary)?.groupValues?.get(1) ?: TICKER.find(head)?.groupValues?.get(1) ?: return null
        val amount = amountIn(summary) ?: amountIn(head)
        val payment = PAYMENT.find(summary)?.let { dateOf(it.groups, 1) }
        val assembly = if ("الجمعية العامة" in summary) ASSEMBLY_DAY.find(summary)?.let { dateOf(it.groups, 1) } else null
        val published = UPDATED.find(normalize(tail))?.let { g ->
            safeDay(g.groupValues[1].toInt(), g.groupValues[2].toInt(), g.groupValues[3].toInt())
        }
        val kind = when {
            "الجمعية العامة" in summary || "الجمعية العامة" in head -> DividendNewsKind.ASSEMBLY
            payment != null && amount != null -> DividendNewsKind.NOTICE
            else -> DividendNewsKind.OTHER
        }
        val url = if (href.startsWith("http")) href else baseUrl.trimEnd('/') + "/" + href.trimStart('/')
        // Only Arab Finance's own pages are ever handed to the browser.
        val safe = url.takeIf { it.startsWith(baseUrl) }.orEmpty()
        return DividendNews(code, kind, amount, payment, assembly, published, headline.trim(), safe)
    }

    private fun amountIn(s: String): Double? {
        val m = AMOUNT.find(s) ?: return null
        val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val pounds = if (m.groupValues[2].startsWith("قر")) value / 100 else value
        return pounds.takeIf { it > 0 }
    }

    /** The date in a match of [DATE] whose first group is number [g]: d/m/yyyy, yyyy/m/d or "19 نوفمبر 2026". */
    private fun dateOf(groups: MatchGroupCollection, g: Int): LocalDate? {
        fun v(k: Int) = groups[g + k]?.value
        v(0)?.let { return safeDay(v(2)!!.toInt(), v(1)!!.toInt(), it.toInt()) }
        v(3)?.let { return safeDay(it.toInt(), v(4)!!.toInt(), v(5)!!.toInt()) }
        v(6)?.let { d -> val month = MONTHS[v(7)!!] ?: return null; return safeDay(v(8)!!.toInt(), month, d.toInt()) }
        return null
    }

    private fun safeDay(year: Int, month: Int, day: Int): LocalDate? =
        if (year !in 2000..2100) null else try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }

    private fun text(html: String): String = SPACE.replace(decode(TAG.replace(html, " ")), " ").trim()

    private fun decode(s: String): String = s
        .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&amp;", "&")

    /**
     * Arabic spelled one way: Arabic-Indic digits to ASCII, the hamza forms of alef to a bare
     * alef, no tatweel or short vowels, and the Arabic comma to a space.
     */
    fun normalize(s: String): String {
        val b = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                in '٠'..'٩' -> b.append('0' + (ch - '٠'))
                in '۰'..'۹' -> b.append('0' + (ch - '۰'))
                'آ', 'أ', 'إ', 'ٱ' -> b.append('ا')
                'ـ', in 'ً'..'ْ' -> {}
                '،', '؛' -> b.append(' ')
                else -> b.append(ch)
            }
        }
        return SPACE.replace(b, " ")
    }
}
