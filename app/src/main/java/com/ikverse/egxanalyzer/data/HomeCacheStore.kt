package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DatedClose
import com.ikverse.egxanalyzer.model.Headline
import com.ikverse.egxanalyzer.model.IndexQuote
import com.ikverse.egxanalyzer.model.MarketBrief
import com.ikverse.egxanalyzer.model.OutsideQuote
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Everything Home keeps between launches, as one value. */
data class HomeCache(
    val indices: List<IndexQuote> = emptyList(),
    val outside: List<OutsideQuote> = emptyList(),
    val marketAt: Long = 0L,
    val headlines: List<Headline> = emptyList(),
    val newsAt: Long = 0L,
    val brief: MarketBrief? = null,
)

/**
 * The last thing Home showed, kept in one small file so the page the app opens on is not blank
 * until the network answers.
 *
 * A file of its own in `filesDir`, not a table: it is a copy of public figures and of a model's
 * paragraph about them, it is replaced whole every time, and losing it costs one refresh. So it is
 * device-local and stays out of the database, every backup, the sync channel and Save diagnostics,
 * and adding it needed no schema version.
 */
class HomeCacheStore(private val file: File) {

    fun load(): HomeCache =
        runCatching { if (file.exists()) decodeHomeCache(file.readText()) else HomeCache() }.getOrDefault(HomeCache())

    fun save(cache: HomeCache) {
        runCatching {
            // Written beside and renamed over, so a process killed mid-write leaves the old copy.
            val draft = File(file.parentFile, "${file.name}.tmp")
            draft.writeText(encodeHomeCache(cache))
            if (!draft.renameTo(file)) {
                file.writeText(draft.readText())
                draft.delete()
            }
        }
    }

    companion object {
        const val FILE_NAME = "home_cache.json"
    }
}

internal fun encodeHomeCache(cache: HomeCache): String = JSONObject().apply {
    put("marketAt", cache.marketAt)
    put("newsAt", cache.newsAt)
    put("indices", JSONArray().apply {
        cache.indices.forEach { quote ->
            put(JSONObject().apply {
                put("key", quote.key)
                put("name", quote.name)
                put("level", quote.level)
                quote.previous?.let { put("previous", it) }
                put("closes", JSONArray().apply {
                    quote.closes.forEach { put(JSONArray().put(it.date.toString()).put(it.close)) }
                })
            })
        }
    })
    put("outside", JSONArray().apply {
        cache.outside.forEach { quote ->
            put(JSONObject().apply {
                put("key", quote.key)
                put("name", quote.name)
                put("value", quote.value)
                quote.previous?.let { put("previous", it) }
            })
        }
    })
    put("headlines", JSONArray().apply {
        cache.headlines.forEach { headline ->
            put(JSONObject().apply {
                put("title", headline.title)
                put("source", headline.source)
                put("link", headline.link)
                put("at", headline.publishedAt)
                put("arabic", headline.arabic)
            })
        }
    })
    cache.brief?.let { brief ->
        put("brief", JSONObject().apply {
            put("summary", brief.summary)
            put("points", JSONArray(brief.points))
            put("watch", JSONArray(brief.watch))
            put("model", brief.model)
            put("askedAt", brief.askedAt)
        })
    }
}.toString()

internal fun decodeHomeCache(text: String): HomeCache {
    val json = JSONObject(text)
    return HomeCache(
        indices = json.optJSONArray("indices").objects().mapNotNull { quote ->
            val level = quote.number("level") ?: return@mapNotNull null
            IndexQuote(
                key = quote.optString("key"),
                name = quote.optString("name"),
                level = level,
                previous = quote.number("previous"),
                closes = quote.optJSONArray("closes").arrays().mapNotNull { pair ->
                    val date = runCatching { LocalDate.parse(pair.optString(0)) }.getOrNull()
                    val close = pair.optDouble(1).takeUnless(Double::isNaN)
                    if (date == null || close == null) null else DatedClose(date, close)
                },
            )
        },
        outside = json.optJSONArray("outside").objects().mapNotNull { quote ->
            val value = quote.number("value") ?: return@mapNotNull null
            OutsideQuote(quote.optString("key"), quote.optString("name"), value, quote.number("previous"))
        },
        marketAt = json.optLong("marketAt"),
        headlines = json.optJSONArray("headlines").objects().map { headline ->
            Headline(
                title = headline.optString("title"),
                source = headline.optString("source"),
                link = headline.optString("link"),
                publishedAt = headline.optLong("at"),
                arabic = headline.optBoolean("arabic"),
            )
        },
        newsAt = json.optLong("newsAt"),
        brief = json.optJSONObject("brief")?.let { brief ->
            MarketBrief(
                summary = brief.optString("summary"),
                points = brief.optJSONArray("points").strings(),
                watch = brief.optJSONArray("watch").strings(),
                model = brief.optString("model"),
                askedAt = brief.optLong("askedAt"),
            )
        },
    )
}

private fun JSONObject.number(name: String): Double? =
    if (has(name)) optDouble(name).takeUnless(Double::isNaN) else null

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)

private fun JSONArray?.arrays(): List<JSONArray> =
    if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONArray)

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter(String::isNotBlank)
