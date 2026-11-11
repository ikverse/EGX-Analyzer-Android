package com.ikverse.egxanalyzer.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Home page's pure rules: who moved most, which headlines show, and what colours the book. */
class HomeMarketTest {

    private val day = LocalDate.of(2026, 11, 11)

    private fun closes(ticker: String, before: Double, last: Double, on: LocalDate = day) = ticker to listOf(
        DailySession(ticker, on.minusDays(1), null, null, before, null),
        DailySession(ticker, on, null, null, last, null),
    )

    @Test
    fun `movers are the strongest moves of the newest session, each way`() {
        val movers = marketMovers(
            mapOf(
                closes("AAA", 10.0, 11.0), closes("BBB", 10.0, 10.5), closes("CCC", 10.0, 9.0),
                closes("DDD", 10.0, 9.8), closes("EEE", 10.0, 10.0),
                // A week old: says nothing about the newest session, so it is on neither list.
                closes("OLD", 10.0, 12.0, on = day.minusDays(7)),
            ),
            limit = 2,
        )!!
        assertEquals(day, movers.date)
        assertEquals(listOf("AAA", "BBB"), movers.gainers.map { it.ticker })
        assertEquals(listOf("CCC", "DDD"), movers.losers.map { it.ticker })
        assertEquals(10.0, movers.gainers.first().changePercent, 1e-9)
    }

    @Test
    fun `a move no exchange permits is a change of scale and is left off the list`() {
        val movers = marketMovers(mapOf(closes("SPLIT", 100.0, 20.0), closes("AAA", 10.0, 10.2)))!!
        assertTrue(movers.losers.isEmpty())
        assertEquals(listOf("AAA"), movers.gainers.map { it.ticker })
    }

    @Test
    fun `no movers without two sessions of anything`() {
        assertNull(marketMovers(mapOf("AAA" to listOf(DailySession("AAA", day, null, null, 10.0, null)))))
    }

    private fun headline(title: String, at: Long, arabic: Boolean, source: String = "S") =
        Headline(title, source, "https://example.com/${title.hashCode()}", at, arabic)

    @Test
    fun `headlines are Arabic first, newest first, with a repeated story said once`() {
        val now = 1_000_000_000_000L
        val hour = 3_600_000L
        val merged = mergeHeadlines(
            listOf(
                headline("English one", now - hour, false),
                headline("خبر قديم", now - 5 * hour, true),
                headline("خبر جديد", now - hour, true),
                headline("English  one!", now - 2 * hour, false),
                headline("Too old", now - 100 * hour, false),
                headline("No date", 0L, false),
            ),
            now,
        )
        assertEquals(listOf("خبر جديد", "خبر قديم", "English one", "No date"), merged.map { it.title })
    }

    @Test
    fun `a headline that leads nowhere on the web is dropped`() {
        val bad = Headline("Title", "S", "javascript:alert(1)", 1L, false)
        assertTrue(mergeHeadlines(listOf(bad), now = 2L).isEmpty())
    }

    @Test
    fun `the lead headlines keep both languages on the tile`() {
        val all = (1..9).map { headline("ع$it", 0L, true) } + (1..9).map { headline("E$it", 0L, false) }
        val lead = leadHeadlines(all, arabic = 5, english = 3)
        assertEquals(5, lead.count { it.arabic })
        assertEquals(3, lead.count { !it.arabic })
    }

    @Test
    fun `an age is said in the largest unit that fits`() {
        val now = 10L * 24 * 3_600_000
        assertEquals("", headlineAge(0L, now))
        assertEquals("now", headlineAge(now - 20_000, now))
        assertEquals("20m", headlineAge(now - 20 * 60_000, now))
        assertEquals("3h", headlineAge(now - 3 * 3_600_000, now))
        assertEquals("2d", headlineAge(now - 50 * 3_600_000L, now))
    }

    @Test
    fun `the book's colour follows the figure as it is printed`() {
        assertEquals(BookMood.UP, bookMood(4.2))
        assertEquals(BookMood.DOWN, bookMood(-3.1))
        assertEquals(BookMood.FLAT, bookMood(0.0))
        // Prints as 0.0%, so a gain's colour over it would contradict the number.
        assertEquals(BookMood.FLAT, bookMood(0.04))
        assertEquals(BookMood.FLAT, bookMood(-0.04))
        assertEquals(BookMood.UP, bookMood(0.05))
        assertEquals(BookMood.FLAT, bookMood(null))
    }

    @Test
    fun `an index with no earlier close shows no move`() {
        val quote = IndexQuote("EGX30", "EGX30", 100.0, previous = null)
        assertNull(quote.changePercent)
        assertEquals(2.0, IndexQuote("EGX30", "EGX30", 102.0, 100.0).changePercent!!, 1e-9)
    }
}
