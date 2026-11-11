package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.Headline
import com.ikverse.egxanalyzer.model.mergeHeadlines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Home page's headlines, read from four public RSS feeds.
 *
 * Free, and headlines only: a title, a source and a link the reader's own browser opens. No
 * article is fetched, nothing is sent anywhere but the four GET requests below, and nothing the
 * user holds - no ticker, no trade - is in any of them. The Arabic feed is a news search for the
 * exchange by name; the three English ones are business desks that cover it. All four were checked
 * on 2026-11-12. Mubasher, Arab Finance and Ahram Online publish no feed that answers.
 */
class NewsRepository(
    private val feeds: List<Feed> = Feeds,
) {

    /** One feed, what to call it where an item does not name its own source, and its language. */
    data class Feed(val url: String, val source: String, val arabic: Boolean)

    /**
     * Every feed that answers, merged. Null where none did, which is "could not reach the news"
     * and not "there is no news", so the caller keeps what it had.
     */
    suspend fun headlines(now: Long = System.currentTimeMillis()): List<Headline>? = coroutineScope {
        val answers = feeds.map { feed ->
            async { request(feed.url)?.let { runCatching { parseRss(it, feed.source, feed.arabic) }.getOrNull() } }
        }.awaitAll()
        if (answers.all { it == null }) null else mergeHeadlines(answers.filterNotNull().flatten(), now)
    }

    private suspend fun request(address: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(address).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                if (connection.responseCode !in 200..299) return@runCatching null
                // A feed is a few hundred kilobytes at most. A cap, so an address that starts
                // answering with something else entirely cannot be read into memory whole.
                connection.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(8192)
                    val out = StringBuilder()
                    while (out.length < MAX_FEED_CHARS) {
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

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (compatible; EGX-Analyzer)"
        private const val MAX_FEED_CHARS = 2_000_000

        val Feeds = listOf(
            Feed(
                // "البورصة المصرية", Egypt edition, Arabic.
                "https://news.google.com/rss/search?q=%D8%A7%D9%84%D8%A8%D9%88%D8%B1%D8%B5%D8%A9+" +
                    "%D8%A7%D9%84%D9%85%D8%B5%D8%B1%D9%8A%D8%A9&hl=ar&gl=EG&ceid=EG:ar",
                "Google News",
                arabic = true,
            ),
            Feed("https://enterpriseam.com/egypt/feed/", "Enterprise", arabic = false),
            Feed("https://www.dailynewsegypt.com/category/business/feed/", "Daily News Egypt", arabic = false),
            Feed("https://en.amwalalghad.com/feed/", "Amwal Al Ghad", arabic = false),
        )
    }
}

/**
 * The items of an RSS document as headlines.
 *
 * Read with patterns rather than an XML parser, on purpose: all that is wanted is four short text
 * fields per item, a feed is somebody else's document and is not always well-formed, and a parser
 * that resolves entities is a way for a document to make the phone fetch things. Nothing here
 * follows a reference or evaluates anything; markup inside a field is removed, not rendered.
 *
 * A search feed prints its titles as `Headline - Publisher` and names the publisher again in a
 * `<source>` element, so the suffix is taken off and the publisher becomes the source.
 */
internal fun parseRss(xml: String, fallbackSource: String, arabic: Boolean): List<Headline> =
    ItemPattern.findAll(xml).mapNotNull { match ->
        val item = match.groupValues[1]
        val link = field(item, "link")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return@mapNotNull null
        val publisher = field(item, "source")?.takeIf(String::isNotBlank)
        var title = field(item, "title")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        if (publisher != null && title.endsWith(" - $publisher")) {
            title = title.removeSuffix(" - $publisher").trim()
        }
        Headline(
            title = title,
            source = publisher ?: fallbackSource,
            link = link,
            publishedAt = field(item, "pubDate")?.let(::rssInstant) ?: 0L,
            arabic = arabic,
        )
    }.toList()

private val ItemPattern = Regex("<item\\b[^>]*>(.*?)</item>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

/** The text of the first `<name>` element in [item], cleaned, or null where there is none. */
private fun field(item: String, name: String): String? =
    Regex("<$name\\b[^>]*>(.*?)</$name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        .find(item)?.groupValues?.get(1)?.let(::plainText)

/** A field's text with its CDATA wrapper, any markup and its character references taken out. */
internal fun plainText(raw: String): String {
    val unwrapped = raw.trim().removePrefix("<![CDATA[").removeSuffix("]]>")
    val unescaped = unescape(unwrapped)
    return unescape(unescaped.replace(Regex("<[^>]*>"), " ")).replace(Regex("\\s+"), " ").trim()
}

private val NumericReference = Regex("&#(x?)([0-9a-fA-F]+);")

private fun unescape(text: String): String =
    NumericReference.replace(text) { match ->
        val code = match.groupValues[2].toIntOrNull(if (match.groupValues[1].isEmpty()) 10 else 16)
        if (code != null && Character.isValidCodePoint(code) && code >= 0x20) String(Character.toChars(code)) else " "
    }
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&nbsp;", " ").replace("&amp;", "&")

/** An RSS `pubDate` (RFC 1123) as epoch milliseconds, or null where it is written some other way. */
internal fun rssInstant(text: String): Long? =
    runCatching {
        ZonedDateTime.parse(text.trim(), DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH))
            .toInstant().toEpochMilli()
    }.getOrNull()
