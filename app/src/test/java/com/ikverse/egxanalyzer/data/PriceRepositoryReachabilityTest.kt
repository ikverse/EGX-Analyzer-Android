package com.ikverse.egxanalyzer.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A feed that never answers is not a feed that answered with nothing.
 *
 * Both used to come back as an empty list, so a phone with no signal filed every stock as
 * "unpriced" and recorded its fetch as the day's. Robolectric because the store is real SQLite and
 * the symbol mapping is a real asset; the endpoint is a closed port, so every request fails before
 * any HTTP status arrives - which is exactly the case under test.
 */
@RunWith(RobolectricTestRunner::class)
class PriceRepositoryReachabilityTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** Refused immediately rather than left to time out. */
    private val deadEndpoint = "http://127.0.0.1:9/%s"

    private fun repository(store: LocalDataStore) =
        PriceRepository(store, SymbolMap(context.assets), deadEndpoint)

    @Test
    fun `a feed that never answers is unreachable, not unpriced`() {
        val store = LocalDataStore(context)

        val refresh = runBlocking { repository(store).refresh(listOf(TICKER)) }

        assertEquals(listOf(TICKER), refresh.unreachable)
        assertTrue(refresh.unpriced.isEmpty())
        assertTrue(refresh.stale.isEmpty())
        assertFalse(refresh.reachedFeed)
        assertEquals(0, refresh.priced)
    }

    @Test
    fun `asking for nothing is not a failure to reach the feed`() {
        val store = LocalDataStore(context)

        val refresh = runBlocking { repository(store).refresh(emptyList()) }

        assertTrue(refresh.reachedFeed)
        assertTrue(refresh.unreachable.isEmpty())
    }

    private companion object {
        /** Mapped in the shipped symbol asset, as `PriceRepositoryRebuildTest` already relies on. */
        const val TICKER = "VLMRA"
    }
}
