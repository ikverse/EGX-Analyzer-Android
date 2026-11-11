package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DatedClose
import com.ikverse.egxanalyzer.model.Headline
import com.ikverse.egxanalyzer.model.IndexQuote
import com.ikverse.egxanalyzer.model.MarketBreadth
import com.ikverse.egxanalyzer.model.MarketBrief
import com.ikverse.egxanalyzer.model.OutsideQuote
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Home reads off the network and keeps between launches: the feeds, the chart answers, the cache. */
class HomeFeedsTest {

    private val searchFeed = """
        <?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>feed</title>
        <item><title>البورصة المصرية تربح 12 مليار جنيه - اليوم السابع</title>
        <link>https://news.google.com/rss/articles/abc?oc=5</link>
        <pubDate>Wed, 11 Nov 2026 13:05:00 GMT</pubDate>
        <source url="https://www.youm7.com">اليوم السابع</source></item>
        <item><title><![CDATA[EGX30 closes higher &amp; breadth improves]]></title>
        <link>https://example.com/a?x=1&amp;y=2</link><pubDate>not a date</pubDate></item>
        <item><title>No link here</title></item>
        <item><title>Bad scheme</title><link>javascript:alert(1)</link></item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun `a search feed's publisher becomes the source and leaves the title`() {
        val first = parseRss(searchFeed, "Google News", arabic = true).first()
        assertEquals("البورصة المصرية تربح 12 مليار جنيه", first.title)
        assertEquals("اليوم السابع", first.source)
        assertTrue(first.arabic)
        assertEquals(java.time.Instant.parse("2026-11-11T13:05:00Z").toEpochMilli(), first.publishedAt)
    }

    @Test
    fun `wrappers and references are taken out, and an item with no web link is dropped`() {
        val items = parseRss(searchFeed, "Enterprise", arabic = false)
        assertEquals(2, items.size)
        val second = items[1]
        assertEquals("EGX30 closes higher & breadth improves", second.title)
        assertEquals("Enterprise", second.source)
        assertEquals("https://example.com/a?x=1&y=2", second.link)
        assertEquals(0L, second.publishedAt)
    }

    @Test
    fun `markup inside a field is removed, not kept`() {
        assertEquals("Bold move", plainText("<![CDATA[<b>Bold</b>  move]]>"))
        assertEquals("It’s up", plainText("It&#8217;s up"))
    }

    @Test
    fun `something that is not a feed gives no headlines`() {
        assertTrue(parseRss("<html><body>404</body></html>", "X", arabic = false).isEmpty())
    }

    /** A chart answer the way the feed shapes one. */
    private fun chart(granularity: String, stamps: List<Long>, closes: List<Double?>, price: Double = 99.0) =
        """{"chart":{"result":[{"meta":{"dataGranularity":"$granularity","regularMarketPrice":$price},
        "timestamp":$stamps,"indicators":{"quote":[{"open":$closes,"high":$closes,"low":$closes,
        "close":$closes,"volume":${closes.map { 0 }}}]}}]}}"""

    private val egx30 = HomeMarketRepository.Indices.first()
    private val day1 = 1_794_300_000L - (1_794_300_000L % 86_400L)

    @Test
    fun `an index is its daily closes rebuilt from hourly bars`() {
        val payload = chart(
            "1h",
            listOf(day1 + 8 * 3600, day1 + 9 * 3600, day1 + 86_400 + 8 * 3600, day1 + 86_400 + 9 * 3600),
            listOf(100.0, 101.0, 102.0, 103.5),
        )
        val quote = parseIndexQuote(egx30, payload)!!
        assertEquals("EGX30", quote.key)
        assertEquals(2, quote.closes.size)
        assertEquals(103.5, quote.level, 1e-9)
        assertEquals(101.0, quote.previous!!, 1e-9)
    }

    @Test
    fun `daily rows where hourly was asked are refused, and the header's level stands alone`() {
        val quote = parseIndexQuote(egx30, chart("1d", listOf(day1), listOf(100.0), price = 52_500.5))!!
        assertEquals(52_500.5, quote.level, 1e-9)
        assertNull(quote.previous)
        assertTrue(quote.closes.isEmpty())
    }

    @Test
    fun `an answer with nothing usable is no quote at all`() {
        assertNull(runCatching { parseIndexQuote(egx30, """{"chart":{"result":null}}""") }.getOrNull())
    }

    @Test
    fun `an outside number is its newest close against the one before`() {
        val spec = HomeMarketRepository.Outside.first()
        val quote = parseOutsideQuote(spec, chart("1d", listOf(day1, day1 + 86_400), listOf(52.0, 53.5)))!!
        assertEquals(53.5, quote.value, 1e-9)
        assertEquals(52.0, quote.previous!!, 1e-9)
    }

    @Test
    fun `the cache gives back what it was given`() {
        val cache = HomeCache(
            indices = listOf(
                IndexQuote("EGX30", "EGX30", 52_500.5, 52_483.5, listOf(DatedClose(LocalDate.of(2026, 11, 11), 52_500.5))),
                IndexQuote("EGX70", "EGX70 EWI", 18_447.2, null),
            ),
            outside = listOf(OutsideQuote("USDEGP", "USD / EGP", 53.5, 52.4), OutsideQuote("GOLD", "Gold · oz", 3993.8, null)),
            marketAt = 11L,
            headlines = listOf(Headline("عنوان", "مصدر", "https://example.com/1", 5L, true)),
            newsAt = 12L,
            brief = MarketBrief("Summary.", listOf("One"), listOf("Watch"), "model-x", 13L),
        )
        assertEquals(cache, decodeHomeCache(encodeHomeCache(cache)))
    }

    @Test
    fun `an empty cache has no brief`() {
        assertEquals(HomeCache(), decodeHomeCache(encodeHomeCache(HomeCache())))
    }

    @Test
    fun `the brief's question carries the market and the headlines, capped`() {
        val question = MarketBriefPrompt.question(
            today = LocalDate.of(2026, 11, 12),
            indices = listOf(IndexQuote("EGX30", "EGX30", 52_500.5, 52_000.0)),
            breadth = MarketBreadth(LocalDate.of(2026, 11, 11), 128, 31, 64),
            movers = null,
            outside = listOf(OutsideQuote("USDEGP", "USD / EGP", 53.5, 53.0)),
            headlines = (1..30).map { Headline("Title $it", "Source", "https://example.com/$it", 0L, false) },
        )
        assertTrue("EGX30: 52,500.50 (+0.96%)" in question)
        assertTrue("128 stocks rose, 31 were flat, 64 fell" in question)
        assertTrue("Title 20 (Source)" in question)
        // Capped, so a search feed's hundred results are not all paid for.
        assertFalse("Title 21 (Source)" in question)
    }

    @Test
    fun `a brief is read out of the answer, fenced or not`() {
        val brief = MarketBriefPrompt.parse(
            "```json\n{\"summary\": \"Up day.\", \"points\": [\"a\", \"\", \"b\"], \"watch\": [\"c\"]}\n```",
            model = "m", askedAt = 7L,
        )
        assertEquals("Up day.", brief.summary)
        assertEquals(listOf("a", "b"), brief.points)
        assertEquals(listOf("c"), brief.watch)
        assertEquals("m", brief.model)
    }

    @Test
    fun `an answer with no summary is a failure the status line can say`() {
        val failure = assertThrows(IllegalStateException::class.java) {
            MarketBriefPrompt.parse("I cannot help with that.", "m", 0L)
        }
        assertTrue(failure.message!!.contains("Try again"))
    }
}
