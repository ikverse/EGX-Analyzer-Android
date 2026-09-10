package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.data.EgxStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the header offers somebody hunting for a stock, and in what order.
 *
 * The ordering is the part with a decision in it: a reader on Results is hunting inside their own
 * runs, so the stocks that page holds lead - and the catalog still follows, because "no runs
 * mention this" is an answer and a listing silently missing from the list is not.
 *
 * Matching is [StockSearch]'s and is tested there; what is checked here is that the picker actually
 * asks it about every name a listing has, aliases included.
 */
class TickerPickerTest {

    private val catalog = listOf(
        EgxStock("COMI", "Commercial International Bank", "البنك التجاري الدولي", setOf("CIB")),
        EgxStock("ETEL", "Egyptian Co. for Communication Systems", "المصرية للاتصالات"),
        EgxStock("AMOC", "Alexandria Mineral Oils", "الإسكندرية للزيوت المعدنية"),
        EgxStock("COMD", "Commodore Trading", null),
    )

    private fun picked(typed: String, onPage: Set<String> = emptySet()) =
        TickerPicker.suggest(typed, onPage, catalog).map { it.stock.ticker }

    @Test
    fun `an empty query offers the whole catalog`() {
        assertEquals(catalog.size, picked("").size)
    }

    @Test
    fun `the page's own stocks lead, whatever the alphabet says`() {
        assertEquals(listOf("ETEL", "AMOC", "COMD", "COMI"), picked("", onPage = setOf("ETEL")))
    }

    @Test
    fun `a ticker that starts with the query leads the ones that merely contain it`() {
        // ETEL answers too - "Communication" holds those letters - and trails both tickers that
        // begin with them, which is the whole point of the second question in the order.
        assertEquals(listOf("COMD", "COMI", "ETEL"), picked("com"))
    }

    @Test
    fun `a company name finds its ticker`() {
        assertEquals(listOf("AMOC"), picked("mineral oils"))
    }

    @Test
    fun `an alias finds the listing it belongs to`() {
        assertEquals(listOf("COMI"), picked("cib"))
    }

    @Test
    fun `an Arabic name is found however the reader spells it`() {
        // The spelling the app normalizes away: a tied ta for the marbuta, no hamza on the alef.
        assertEquals(listOf("ETEL"), picked("المصريه"))
    }

    @Test
    fun `a query nothing answers to offers nothing`() {
        assertTrue(picked("zzzz").isEmpty())
    }

    @Test
    fun `every suggestion says whether the page holds it`() {
        val suggestions = TickerPicker.suggest("", setOf("AMOC"), catalog)
        assertEquals(setOf("AMOC"), suggestions.filter { it.onPage }.map { it.stock.ticker }.toSet())
    }

    @Test
    fun `a picked ticker is named by its company`() {
        assertEquals("Commercial International Bank Egypt", TickerPicker.name("COMI"))
    }

    @Test
    fun `a ticker the catalog has never heard of is named by itself`() {
        assertEquals("ZZZZ", TickerPicker.name("ZZZZ"))
    }
}
