package com.ikverse.egxanalyzer.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the shipped mapping itself.
 *
 * A wrong row is worse than a missing one: it would score one company's recommendations against
 * another company's prices, silently and plausibly.
 */
class SymbolMapTest {

    private val rows: JSONArray = JSONArray(
        File("src/main/assets/yahoo_symbols.json").readText(Charsets.UTF_8),
    )

    private fun each(): Sequence<org.json.JSONObject> =
        (0 until rows.length()).asSequence().map(rows::getJSONObject)

    @Test
    fun `every row names an exchange code and a Yahoo symbol`() {
        assertTrue("mapping should not be empty", rows.length() > 100)
        each().forEach { row ->
            assertTrue("blank egx symbol in $row", row.optString("egx").isNotBlank())
            assertTrue("blank yahoo symbol in $row", row.optString("yahoo").isNotBlank())
        }
    }

    @Test
    fun `no two stocks claim the same Yahoo symbol`() {
        // Name matching alone once produced three different stocks pointing at one symbol.
        val symbols = each().map { it.getString("yahoo") }.toList()
        assertEquals(symbols.size, symbols.toSet().size)
    }

    @Test
    fun `no two rows describe the same exchange code`() {
        val codes = each().map { it.getString("egx") }.toList()
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `the ISIN matches the symbol it was taken from`() {
        each().forEach { row ->
            val isin = row.optString("isin")
            if (isin.isNotBlank()) {
                assertEquals(row.getString("yahoo"), "$isin.CA")
                assertTrue("odd ISIN $isin", isin.matches(Regex("EG[A-Z0-9]{10}")))
            }
        }
    }

    @Test
    fun `the pairs confirmed against the price feed are present`() {
        // Each was checked by hand: the legacy symbol's 29 July close equals the ISIN symbol's
        // 30 July open, which only holds for the same stock.
        val expected = mapOf(
            "COMI" to "EGS60121C018.CA",
            "ABUK" to "EGS38191C010.CA",
            "AMOC" to "EGS380P1C010.CA",
            "ETEL" to "EGS48031C016.CA",
            "EFIH" to "EGS743O1C013.CA",
            "TAQA" to "EGS490S1C014.CA",
            "HRHO" to "EGS69101C011.CA",
            "FWRY" to "EGS745L1C014.CA",
            "AMIA" to "EGS67221C019.CA",
            // Joined on 17/18 August rather than 29/30 July, because both were added long after
            // the migration: MBEG.CA closed 17 August at 6.800 and EGS221V1C015.CA opened
            // 18 August at exactly that.
            "MBEG" to "EGS221V1C015.CA",
        )
        val actual = each().associate { it.getString("egx") to it.getString("yahoo") }
        expected.forEach { (egx, yahoo) -> assertEquals(egx, yahoo, actual[egx]) }
    }

    @Test
    fun `the two Valmore listings stay apart`() {
        // One issuer, two instruments, one name - and the catalog gives both the same one, which is
        // precisely the shape that put three stocks on a single symbol the last time names were
        // trusted. They are told apart by price: VLMRA trades near 30 and VLMR near 0.70, and the
        // call that named VLMRA printed a support of 30.50 - the 12 August low of EGS69081C023.CA
        // exactly, and of nothing else listed in Cairo.
        val actual = each().associate { it.getString("egx") to it.getString("yahoo") }
        assertEquals("EGS69081C023.CA", actual["VLMRA"])
        assertEquals("EGS69082C013.CA", actual["VLMR"])
    }
}
