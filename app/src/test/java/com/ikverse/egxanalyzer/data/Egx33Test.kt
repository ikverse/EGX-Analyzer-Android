package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.egx33
import com.ikverse.egxanalyzer.model.isEgx33
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
        assertEquals(34, egx33.size)
    }

    @Test
    fun `membership reads through the same cleaning a ticker gets anywhere else`() {
        assertTrue(isEgx33("ADIB"))
        assertTrue("lower case", isEgx33("adib"))
        assertTrue("price feed suffix", isEgx33("CLHO.CA"))
        assertTrue("padded", isEgx33("  tmgh  "))
    }

    @Test
    fun `both of Faisal Islamic Bank's listings count`() {
        assertTrue("pound line", isEgx33("FAIT"))
        assertTrue("dollar line", isEgx33("FAITA"))
    }

    @Test
    fun `a member the price catalog does not carry is still a member`() {
        assertTrue(isEgx33("ICFC"))
    }

    @Test
    fun `the September 2026 review is the version transcribed`() {
        assertTrue("GOUR joined", isEgx33("GOUR"))
        assertTrue("CLHO joined", isEgx33("CLHO"))
        assertFalse("CIRA left", isEgx33("CIRA"))
        assertFalse("OLFI left", isEgx33("OLFI"))
    }

    @Test
    fun `a stock outside the index is not claimed for it`() {
        assertFalse(isEgx33("COMI"))
        assertFalse(isEgx33("HRHO"))
        assertFalse("a blank ticker is nobody", isEgx33(""))
    }
}
