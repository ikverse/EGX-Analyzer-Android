package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.RecommendationResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That a stock is called the same thing every run.
 *
 * It was not. The catalog was applied to one derived list that no screen reads, so the cards, the
 * portfolio and the scorer printed whatever the model had called the stock in that run - AMOC was
 * "Alexandria Mineral Oils" in one report and "Amouk" in the next, QNBA once came back as "National
 * Bank of Kuwait - Egypt". These hold the two halves of the fix: the catalog decides, and where it
 * has nothing to say the answer is a bare ticker rather than the model's invention.
 */
class EgxCatalogTest {

    @After
    fun restoreDefaults() {
        // A singleton with a switch on it: left off, every later test in the run would see the
        // model's names instead of the catalog's.
        EgxCatalog.enrichmentEnabled = true
        EgxCatalog.restore(EgxSeedStocks.ALL)
    }

    @Test
    fun `the model's name for a known stock is replaced by the catalog's`() {
        val named = EgxCatalog.enrich(recommendation("AMOC", "Amouk", "أموك المصرية"))

        assertEquals("Alexandria Mineral Oils Company", named.companyName)
        assertEquals("الاسكندرية للزيوت المعدنية", named.companyNameArabic)
    }

    @Test
    fun `a ticker the catalog does not hold is left as a bare ticker`() {
        val named = EgxCatalog.enrich(recommendation("ZZZZ", "Bank of Nowhere", "بنك لا مكان"))

        assertEquals("ZZZZ", named.companyName)
        assertNull(named.companyNameArabic)
    }

    @Test
    fun `the model's arabic is refused even where the catalog carries none`() {
        // The root of the whole fault, and the case that has to hold however complete the table
        // is - which is why it builds its own gap rather than looking for one. Every shipped entry
        // carries Arabic today; a listing downloaded tomorrow will not, and the run that first
        // reads it must not name it from a screenshot.
        EgxCatalog.restore(listOf(EgxStock("ZZNEW", "Newly Listed Company")))

        val named = EgxCatalog.enrich(recommendation("ZZNEW", "whatever", "اسم من الصورة"))

        assertEquals("Newly Listed Company", named.companyName)
        assertNull(named.companyNameArabic)
    }

    @Test
    fun `every shipped listing carries an english and an arabic name`() {
        // The table is what stands between a card and a name nobody checked, so a row missing one
        // is a stock that silently shows less than the one beside it. Both names, every row.
        val bare = EgxSeedStocks.ALL
            .filter { it.nameArabic.isNullOrBlank() || it.nameEnglish.isBlank() }
            .map(EgxStock::ticker)

        assertEquals("Listings with a name missing", emptyList<String>(), bare)
    }

    @Test
    fun `no shipped entry names a stock after the ticker it already carries`() {
        // An English name equal to its own ticker is a row that knows nothing, and it would read on
        // a card as a company called "MBEG". The card hides it, so this is the only place it shows.
        val hollow = EgxSeedStocks.ALL.filter { it.nameEnglish == it.ticker }.map(EgxStock::ticker)

        assertEquals(emptyList<String>(), hollow)
    }

    @Test
    fun `the exchange suffix and casing do not hide a stock from the catalog`() {
        val named = EgxCatalog.enrich(recommendation("comi.ca", "CIB", null))

        assertEquals("COMI", named.ticker)
        assertEquals("Commercial International Bank Egypt", named.companyName)
    }

    @Test
    fun `switching enrichment off hands back what the model wrote`() {
        EgxCatalog.enrichmentEnabled = false

        val names = EgxCatalog.namesFor("AMOC", "Amouk", "أموك المصرية")

        assertEquals("Amouk", names.english)
        assertEquals("أموك المصرية", names.arabic)
    }

    @Test
    fun `a download cannot empty a name the app already ships`() {
        // A downloaded entry is exactly this: a symbol and an English name, no Arabic and no
        // aliases. Folded in whole it wiped the Arabic name of every seeded stock and COMI's "CIB"
        // with it, which is how a device ends up holding 223 stocks and not one Arabic name.
        EgxCatalog.restore(listOf(EgxStock("COMI", "Commercial International Bank")))
        val after = EgxCatalog.find("COMI")!!

        assertEquals("البنك التجاري الدولي", after.nameArabic)
        assertTrue("CIB alias must survive a download", after.aliases.contains("CIB"))
    }

    @Test
    fun `a download still adds a listing the app does not ship`() {
        EgxCatalog.restore(EgxSeedStocks.ALL + EgxStock("NEWL", "Newly Listed Company"))

        assertEquals("Newly Listed Company", EgxCatalog.find("NEWL")?.nameEnglish)
    }

    @Test
    fun `the listings the catalog endpoint omits are shipped anyway`() {
        // Each confirmed a real listing by its own price history, not by the model saying so.
        listOf("QNBA", "ICFC", "AIFI", "AMII", "ADRI", "IEEC", "MBEG", "NAKH", "EFHI").forEach {
            assertNotNull("$it must be in the catalog", EgxCatalog.find(it))
        }
        assertEquals("QNB Alahli", EgxCatalog.find("QNBA")?.nameEnglish)
    }

    @Test
    fun `an alternate code is named after the company it is a code for`() {
        // EFHI and NAKH are the same listings as EFIH and KRDI - identical closes on every session
        // each pair shares - so they are second codes rather than second companies. Named after
        // their twin rather than left blank: they turn up in reports, and a bare ticker tells the
        // reader less than the company's own name does. The codes stay separate, because merging
        // them changes what the scorer counts and not what a card is called.
        assertEquals(EgxCatalog.find("EFIH")?.nameArabic, EgxCatalog.find("EFHI")?.nameArabic)
        assertNotNull(EgxCatalog.find("NAKH")?.nameArabic)
    }

    private fun recommendation(ticker: String, english: String, arabic: String?) =
        RecommendationResult(
            ticker = ticker,
            companyName = english,
            companyNameArabic = arabic,
            sourceName = "channel",
            targetDate = null,
            timing = null,
            entryLow = null,
            entryHigh = null,
            takeProfit1 = null,
            takeProfit2 = null,
            stopLoss = null,
            notesArabic = null,
            signal = "BUY",
        )
}
