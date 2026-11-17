package com.ikverse.egxanalyzer.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The statistics page, read from rows laid out the way stockanalysis.com lays them; ported from Signal Lab. */
class CompanyFactsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun statsPage(rows: List<Triple<String, String?, String>>) = buildString {
        append("<html><body><p>The company has a Debt / Equity ratio of 9.99.</p><table><tbody>")
        for ((label, title, text) in rows) {
            append("<!--[0--><tr class=\"border-y\"><td class=\"px-[5px]\"><!--[1--><span><!---->$label<!----></span><!--]--></td>")
            append("<!--[-1--><td class=\"px-[5px] text-right\"${title?.let { " title=\"$it\"" } ?: ""}>$text</td><!--]--></tr><!--]-->")
        }
        append("</tbody></table></body></html>")
    }

    private val elka = statsPage(
        listOf(
            Triple("PE Ratio", "2.871", "2.87"),
            Triple("Net Income", "1,028,174,764", "1.03B"),
            Triple("Earnings Per Share (EPS)", null, "0.55"),
            Triple("Total Debt", "855,336,105", "855.34M"),
            Triple("Debt / Equity", null, "0.14"),
        ),
    )

    @Test
    fun factsAreReadFromTheTableRowsOnly() {
        val f = StockAnalysisParser.parse("ELKA", elka, 5L)
        assertEquals(2.871, f.peRatio!!, 1e-12)
        assertEquals(1_028_174_764.0, f.netIncome!!, 1e-6)
        assertEquals(0.55, f.eps!!, 1e-12)
        assertEquals(855_336_105.0, f.totalDebt!!, 1e-6)
        // The prose's 9.99 is not a table row.
        assertEquals(0.14, f.debtToEquity!!, 1e-12)
        assertEquals(false, f.lossMaking)
    }

    @Test
    fun aLossAndMissingValuesAreKept() {
        val f = StockAnalysisParser.parse(
            "NEDA",
            statsPage(listOf(Triple("PE Ratio", null, "n/a"), Triple("Net Income", null, "-4.59M"))),
            0L,
        )
        assertNull(f.peRatio)
        assertEquals(-4_590_000.0, f.netIncome!!, 1e-6)
        assertEquals(true, f.lossMaking)
        assertEquals(2.5e12, StockAnalysisParser.number("2.5T")!!, 1.0)
        assertNull(StockAnalysisParser.number(""))
    }

    @Test
    fun keptForAWeekAndKeptWhenThePageFails() = runBlocking {
        var now = 1_000L
        var asked = 0
        var page: String? = elka
        val file = folder.newFile("company_facts.json").also { it.delete() }
        val repo = CompanyFactsRepository(file, clock = { now }, fetchPage = { asked++; page })
        assertEquals(2.871, repo.facts("ELKA")!!.peRatio!!, 1e-12)
        now += 60_000
        repo.facts("ELKA")
        assertEquals(1, asked)
        // A week on it asks again; the page not answering keeps what it had.
        now += CompanyFactsRepository.KEEP_MILLIS
        page = null
        assertEquals(2.871, repo.facts("ELKA")!!.peRatio!!, 1e-12)
        assertEquals(2, asked)
        // And a fresh repository reads the file back.
        val again = CompanyFactsRepository(file, clock = { 1_000L }, fetchPage = { error("not asked") })
        assertEquals(0.55, again.facts("ELKA")!!.eps!!, 1e-12)
    }
}
