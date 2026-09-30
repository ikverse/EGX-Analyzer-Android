package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.ScoredCall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What a searching request is actually sent to look for.
 *
 * The block these tests defend is the difference between a search and a wasted one. Before it, the
 * provider was handed a question about entry bands and left to guess a query from four Latin
 * letters; what these check is that the query names the company in both scripts, that the window
 * is a real pair of dates rather than a word like "recent", and that the forward horizon is not
 * quietly clipped to the lookback.
 */
class OpinionSearchBriefTest {

    private val today = LocalDate.parse("2026-08-20")

    private val call = ScoredCall(
        ticker = "COMI",
        companyEnglish = "Commercial International Bank",
        companyArabic = "البنك التجاري الدولي",
        channel = "EGX Signals",
        channelId = -100L,
        openedOn = LocalDate.parse("2026-08-11"),
        entryLow = 68.5,
        entryHigh = 69.8,
        target1 = 74.0,
        target2 = 78.5,
        stopLoss = 65.0,
        outcome = Outcome.OPEN,
        settledOn = null,
        peakHigh = 72.4,
        troughLow = 66.9,
        returnPct = 3.12,
        sessionsElapsed = 6,
        windowSessions = 10,
        requestId = "run-1",
    )

    private fun query(days: Int = 15) = OpinionSearchBrief.query(call, today, days)

    /**
     * The window is two dates, and they are the ones the setting asked for.
     *
     * "Recent news" is not a window - every model reads it differently and none of them can be
     * checked afterwards. A date the reader can hold the answer against is the whole point.
     */
    @Test
    fun `the window is stated as the dates it actually covers`() {
        val brief = query(days = 15)

        assertTrue(brief.contains("News window: 2026-08-05 to 2026-08-20"))
        assertTrue(brief.contains("the last 15 days"))
        assertTrue(brief.contains("Anything published before 2026-08-05 is outside the window"))
    }

    @Test
    fun `a wider setting moves the opening date and nothing else`() {
        assertTrue(query(days = 90).contains("News window: 2026-05-22 to 2026-08-20"))
    }

    /**
     * Both scripts and every alias, because the press and the wires do not use the same name.
     *
     * A search on COMI alone finds a four-letter string. The Arabic name is what Mubasher prints
     * and "CIB" is what everyone actually calls it, and an alias list the app already holds was
     * sitting unused while the search returned nothing usable.
     */
    @Test
    fun `every name the company goes by is named`() {
        val brief = query()

        assertTrue(brief.contains("COMI"))
        assertTrue(brief.contains("Commercial International Bank"))
        assertTrue(brief.contains("البنك التجاري الدولي"))
        // From the catalog rather than from the call, which carries only what the channel printed.
        assertTrue(brief.contains("CIB"))
    }

    /**
     * The forward horizon is not the lookback.
     *
     * A fortnight of news is a choice about how stale a headline may be. A dividend three weeks out
     * does not stop mattering because of it, and clipping the two together would have hidden every
     * catalyst the setting was never about.
     */
    @Test
    fun `what is scheduled ahead reaches past the news window`() {
        val brief = query(days = 15)

        assertTrue(brief.contains("Then look forward, to 2026-12-18"))
        assertTrue(brief.contains("catalysts"))
    }

    @Test
    fun `the sources it should prefer are named`() {
        val brief = query()

        assertTrue(brief.contains("egx.com.eg"))
        assertTrue(brief.contains("mubasher.info"))
        assertTrue(brief.contains("Prefer a primary disclosure over a report of one"))
    }

    /**
     * The preamble is a second statement of the rule, not a copy of the query.
     *
     * It arrives attached to the results, after the question has been read, and it is the last
     * thing between a stale headline and the answer. Repeating the search terms there would waste
     * the one place the date rule is cheapest to enforce.
     */
    @Test
    fun `the result preamble restates the window and not the query`() {
        val preamble = OpinionSearchBrief.resultPreamble(today, windowDays = 15)

        assertTrue(preamble.contains("between 2026-08-05 and 2026-08-20"))
        assertTrue(preamble.contains("published before 2026-08-05"))
        assertTrue(preamble.contains("Reporting nothing is correct"))
        assertFalse(preamble.contains("Look for:"))
    }
}
