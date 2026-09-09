package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.DirectoryStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockLookupTest {

    private val catalog = listOf(
        DirectoryStock("COMI", "Commercial International Bank", "البنك التجاري الدولي"),
        DirectoryStock("HRHO", "EFG Holding", "المجموعة المالية هيرميس"),
        DirectoryStock("ETEL", "Telecom Egypt", "المصرية للاتصالات"),
        DirectoryStock("MICH", "Misr Chemical Industries", "مصر للصناعات الكيماوية"),
        DirectoryStock("ORWE", "Oriental Weavers", "النساجون الشرقيون"),
        DirectoryStock("OCDI", "SODIC", "السادس من أكتوبر للتنمية"),
        DirectoryStock("EFIH", "e-finance", null),
    )

    private fun tickers(typed: String) = StockLookup.matches(typed, catalog).map { it.ticker }

    @Test
    fun `an empty query offers nothing`() {
        // Deliberately not the whole catalog, which is where this parts company with the three
        // in-page filters: there is nothing on screen until this answers.
        assertEquals(emptyList<String>(), tickers(""))
        assertEquals(emptyList<String>(), tickers("   "))
    }

    @Test
    fun `a ticker is found however it is cased`() {
        assertEquals(listOf("COMI"), tickers("comi"))
        assertEquals(listOf("COMI"), tickers("CoMi"))
    }

    @Test
    fun `a ticker the query starts outranks a ticker that merely contains it`() {
        // `OC` starts OCDI and sits inside... nothing here, so add the case that matters: `E`
        // starts ETEL and EFIH, and appears inside ORWE and MICH via their names.
        val hits = tickers("et")
        assertEquals("ETEL", hits.first())
    }

    @Test
    fun `a ticker outranks a name`() {
        // MICH's name is "Misr Chemical Industries"; `mi` starts no other ticker here.
        assertEquals("MICH", tickers("mi").first())
    }

    @Test
    fun `an english name is found from the middle of it`() {
        assertTrue("weavers" + tickers("weavers"), tickers("weavers").contains("ORWE"))
    }

    @Test
    fun `an arabic name is found through the app's own spelling foldings`() {
        // The tied ta and the alef maksura are what channels actually disagree about, and folding
        // them is the whole reason this goes through StockSearch rather than String.contains.
        assertTrue(tickers("المصريه").contains("ETEL"))
        assertTrue(tickers("المصرية").contains("ETEL"))
    }

    @Test
    fun `a stock with no arabic name is still searchable and never crashes`() {
        assertEquals(listOf("EFIH"), tickers("e-finance"))
        assertEquals(listOf("EFIH"), tickers("efih"))
    }

    @Test
    fun `nothing matching gives nothing back`() {
        assertEquals(emptyList<String>(), tickers("zzzz"))
    }

    @Test
    fun `the panel is capped`() {
        val many = (1..80).map { DirectoryStock("AA%02d".format(it), "Alpha $it", null) }
        assertEquals(StockLookup.LIMIT, StockLookup.matches("aa", many).size)
    }

    @Test
    fun `equally ranked stocks keep the catalog's own order`() {
        // Both are name matches, so neither outranks the other and the catalog decides - which is
        // roughly by size, so the large cap a reader is likelier to mean comes first.
        val hits = StockLookup.matches("misr", catalog).map { it.ticker }
        assertEquals(listOf("MICH"), hits)
    }
}
