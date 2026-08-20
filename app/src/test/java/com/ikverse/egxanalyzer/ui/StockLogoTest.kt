package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.data.EgxCatalog
import com.ikverse.egxanalyzer.data.EgxStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * That the logo map still covers the catalog, and still normalizes what it is handed.
 *
 * `StockLogos.kt` is generated. Nothing regenerates it on a build, so the way it breaks is by
 * standing still: a ticker is added to the seed list, or renamed, and its rows quietly start
 * drawing a monogram - which looks deliberate, because it is what the fallback is supposed to look
 * like. That is the failure this catches.
 */
class StockLogoTest {
    @Test
    fun `every seeded catalog stock has a bundled logo`() {
        val missing = EgxCatalog.entries()
            .map(EgxStock::ticker)
            .filter { StockLogos.forTicker(it) == null }
        assertEquals("Seeded tickers with no bundled logo", emptyList<String>(), missing)
    }

    @Test
    fun `the bundled set has not shrunk`() {
        // 222 of the 223 companies the catalog endpoint listed when these were generated. ARVA
        // (Arab Valves) is the one with no logo published anywhere, and is meant to fall back.
        assertEquals(222, StockLogos.COUNT)
        assertNull("ARVA has no logo and must fall back", StockLogos.forTicker("ARVA"))
    }

    @Test
    fun `lookup normalizes the ticker as the catalog does`() {
        val expected = StockLogos.forTicker("COMI")
        assertNotNull(expected)
        // The three forms a ticker actually reaches the UI in: the Yahoo-style suffix the price
        // feed uses, the model's lowercase, and whitespace off a parsed message.
        assertEquals(expected, StockLogos.forTicker("COMI.CA"))
        assertEquals(expected, StockLogos.forTicker("comi"))
        assertEquals(expected, StockLogos.forTicker("  COMI  "))
    }

    @Test
    fun `an unknown ticker resolves to no logo rather than a wrong one`() {
        // The catalog is refreshed from a remote endpoint, so this is a real state, not a bad input.
        assertNull(StockLogos.forTicker("ZZZZ"))
        assertNull(StockLogos.forTicker(""))
    }
}
