package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the transcribed EGX 33 membership.
 *
 * The list is typed out from the exchange's own review announcement and cannot be checked against
 * anything the app fetches, so these are the only things standing between a mistyped edit and a
 * card claiming the wrong stock is Shariah-compliant.
 */
class Egx33Test {

    @Test
    fun `the index is transcribed as thirty-four listed symbols`() {
        // 33 companies; Faisal Islamic Bank is listed twice. The count is what catches a symbol
        // dropped or repeated in an edit, since the set itself would swallow the repeat.
        assertEquals(34, EgxCatalog.egx33.size)
    }

    @Test
    fun `membership reads through the same cleaning a ticker gets anywhere else`() {
        assertTrue(EgxCatalog.isEgx33("ADIB"))
        assertTrue("lower case", EgxCatalog.isEgx33("adib"))
        assertTrue("price feed suffix", EgxCatalog.isEgx33("CLHO.CA"))
        assertTrue("padded", EgxCatalog.isEgx33("  tmgh  "))
    }

    @Test
    fun `both of Faisal Islamic Bank's listings count`() {
        assertTrue("pound line", EgxCatalog.isEgx33("FAIT"))
        assertTrue("dollar line", EgxCatalog.isEgx33("FAITA"))
    }

    @Test
    fun `a member the price catalog does not carry is still a member`() {
        assertTrue(EgxCatalog.isEgx33("ICFC"))
    }

    @Test
    fun `the September 2026 review is the version transcribed`() {
        assertTrue("GOUR joined", EgxCatalog.isEgx33("GOUR"))
        assertTrue("CLHO joined", EgxCatalog.isEgx33("CLHO"))
        assertFalse("CIRA left", EgxCatalog.isEgx33("CIRA"))
        assertFalse("OLFI left", EgxCatalog.isEgx33("OLFI"))
    }

    @Test
    fun `a stock outside the index is not claimed for it`() {
        assertFalse(EgxCatalog.isEgx33("COMI"))
        assertFalse(EgxCatalog.isEgx33("HRHO"))
        assertFalse("a blank ticker is nobody", EgxCatalog.isEgx33(""))
    }
}
