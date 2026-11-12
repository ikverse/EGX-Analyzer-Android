package com.ikverse.egxanalyzer.model

import java.time.LocalDate
import kotlin.math.abs

/**
 * What the Home page draws that is not the user's own record: the exchange's indices, the numbers
 * from outside it that move it, the stocks that moved most, and the headlines.
 *
 * Pure, with no Android and no network in it. Fetching is `data/HomeMarketRepository` and
 * `data/NewsRepository`; holding it is `state/LiveHome`.
 */

/** One day's close of an index, for the small chart on its tile. */
data class DatedClose(val date: LocalDate, val close: Double)

/**
 * One index as Home shows it.
 *
 * @param closes daily closes, oldest first, rebuilt from hourly bars because the feed's daily
 *   endpoint answers an EGX index with its newest session alone. Empty where only the level arrived.
 * @param previous the close before [level], or null where the feed gave no earlier session - the
 *   tile then shows the level without a move rather than a move against nothing.
 */
data class IndexQuote(
    val key: String,
    val name: String,
    val level: Double,
    val previous: Double?,
    val closes: List<DatedClose> = emptyList(),
    /**
     * The newest session's five-minute bars, for the tile's 1D range. Asked for the index that
     * leads the page and no other; empty where the feed did not answer, and 1D is then not offered.
     */
    val day: List<DayBar> = emptyList(),
) {
    val change: Double? get() = previous?.let { level - it }
    val changePercent: Double? get() = previous?.takeIf { it > 0.0 }?.let { (level - it) / it * 100.0 }
}

/** A number from outside the exchange: the dollar, gold, oil. */
data class OutsideQuote(val key: String, val name: String, val value: Double, val previous: Double?) {
    val changePercent: Double? get() = previous?.takeIf { it > 0.0 }?.let { (value - it) / it * 100.0 }
}

/** One stock on the gainers or the losers list. */
data class Mover(val ticker: String, val name: String?, val close: Double, val changePercent: Double)

/** The session's biggest moves either way, each list strongest first. */
data class MarketMovers(val date: LocalDate, val gainers: List<Mover>, val losers: List<Mover>)

/**
 * The stocks that moved most in the newest session any listing has, against each one's previous
 * close - the same two closes and the same newest-session rule [marketBreadth] counts with.
 *
 * A move beyond [MoverSanityPercent] is left out: the exchange does not permit one, so it is a
 * split or a bonus issue showing through the feed, and the top of a gainers list is the worst
 * place for it. See `PriceSanity`, which draws the same line.
 *
 * @param lastTwo each listing's newest two stored sessions, in any order.
 * @param nameOf the company's name for a ticker, or null where the catalog has none.
 */
fun marketMovers(
    lastTwo: Map<String, List<DailySession>>,
    limit: Int = 5,
    nameOf: (String) -> String? = { null },
): MarketMovers? {
    val priced = lastTwo.mapNotNull { (ticker, list) ->
        val sorted = list.filter { it.close != null }.sortedBy(DailySession::date)
        if (sorted.size < 2) null else Triple(ticker, sorted[sorted.size - 2], sorted.last())
    }
    val latest = priced.maxOfOrNull { it.third.date } ?: return null
    val moves = priced.filter { it.third.date == latest }.mapNotNull { (ticker, before, last) ->
        val was = before.close!!
        val now = last.close!!
        if (was <= 0.0) return@mapNotNull null
        val change = (now - was) / was * 100.0
        if (abs(change) > MoverSanityPercent) null else Mover(ticker, nameOf(ticker), now, change)
    }
    if (moves.isEmpty()) return null
    return MarketMovers(
        date = latest,
        gainers = moves.filter { it.changePercent > 0.0 }
            .sortedWith(compareByDescending<Mover> { it.changePercent }.thenBy { it.ticker }).take(limit),
        losers = moves.filter { it.changePercent < 0.0 }
            .sortedWith(compareBy<Mover> { it.changePercent }.thenBy { it.ticker }).take(limit),
    )
}

/** Past this a one-session move is a change of scale, not a move. */
const val MoverSanityPercent = 30.0

/**
 * One headline. A title, where it came from and where it leads; never the article.
 *
 * @param publishedAt epoch milliseconds, or 0 where the feed gave no date the app could read.
 */
data class Headline(
    val title: String,
    val source: String,
    val link: String,
    val publishedAt: Long,
    val arabic: Boolean,
)

/**
 * Every feed's headlines as one list: Arabic first, then English, each newest first, with a story
 * two feeds both carry said once.
 *
 * A headline older than [maxAgeMillis] is dropped where its date is known - a search feed answers
 * with weeks of results - and one with no date is kept, at the end of its language.
 */
fun mergeHeadlines(
    all: List<Headline>,
    now: Long,
    maxAgeMillis: Long = 3L * 24 * 60 * 60 * 1000,
    perLanguage: Int = 20,
): List<Headline> {
    val seen = mutableSetOf<String>()
    val fresh = all
        .filter { it.title.isNotBlank() && it.link.startsWith("http") }
        .filter { it.publishedAt == 0L || now - it.publishedAt <= maxAgeMillis }
        .sortedByDescending { it.publishedAt }
        .filter { seen.add(headlineKey(it.title)) }
    return fresh.filter { it.arabic }.take(perLanguage) + fresh.filterNot { it.arabic }.take(perLanguage)
}

/** A title reduced to its letters and digits, so punctuation and spacing cannot make two of one. */
internal fun headlineKey(title: String): String =
    title.lowercase().filter { it.isLetterOrDigit() }

/**
 * What the news tile shows before "Show all" is pressed: the newest [arabic] Arabic headlines, then
 * the newest [english] English ones, so neither language can push the other off the tile.
 */
fun leadHeadlines(all: List<Headline>, arabic: Int = 5, english: Int = 3): List<Headline> =
    all.filter { it.arabic }.take(arabic) + all.filterNot { it.arabic }.take(english)

/** How long ago [publishedAt] was, as the tile says it: `now`, `20m`, `3h`, `2d`. Empty for no date. */
fun headlineAge(publishedAt: Long, now: Long): String {
    if (publishedAt <= 0L) return ""
    val minutes = ((now - publishedAt) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}

/**
 * The model's short read of the day, asked for by a press and kept until the next one.
 *
 * @param points what happened, a sentence each.
 * @param watch what to look at next session.
 */
data class MarketBrief(
    val summary: String,
    val points: List<String> = emptyList(),
    val watch: List<String> = emptyList(),
    val model: String = "",
    val askedAt: Long = 0L,
)

/** Which way the open trades stand, which is what colours the "Your book" tile. */
enum class BookMood { UP, FLAT, DOWN }

/**
 * [BookMood] for an average return, judged on the figure **as it is printed**: one decimal place.
 * A tile reading `0.0%` in the colour of a gain would be the colour contradicting the number.
 * Null - no open trade with a price - is flat.
 */
fun bookMood(returnPercent: Double?): BookMood {
    val shown = returnPercent ?: return BookMood.FLAT
    return when {
        abs(shown) < 0.05 -> BookMood.FLAT
        shown > 0.0 -> BookMood.UP
        else -> BookMood.DOWN
    }
}
