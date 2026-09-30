package com.ikverse.egxanalyzer.data

import android.content.Context
import com.ikverse.egxanalyzer.model.DailySession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/**
 * When a stock the daily feeds do not carry gets its history rebuilt, and how much of one it asks
 * for.
 *
 * Both halves of this were wrong together, which is why one test file covers them. The rebuild was
 * handed the *incremental* window - three days behind the newest stored session - so it returned
 * three days of history and stopped. Those rows then counted towards the test that decides whether
 * a stock is thin enough to rebuild, so the stub disqualified the stock from ever being asked
 * again. VLMRA sat on five sessions against a thirty-session judging horizon and could not have
 * left it: every refresh looked at five rows, called the history healthy, and moved on.
 *
 * Robolectric because the store is real SQLite and the mapping is a real asset. The endpoint is
 * pointed at a closed port instead, so every daily feed comes back empty and what the rebuild was
 * asked for is the only thing left to observe.
 */
@RunWith(RobolectricTestRunner::class)
class PriceRepositoryRebuildTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** Refused immediately rather than left to time out: nothing here wants a daily answer. */
    private val deadEndpoint = "http://127.0.0.1:9/%s"

    private fun session(on: LocalDate, close: Double) = DailySession(
        ticker = TICKER,
        date = on,
        high = close + 0.1,
        low = close - 0.1,
        close = close,
        volume = 1_000.0,
        open = close,
        derived = false,
    )

    /** What the rebuild was asked for, or that it was never asked at all. */
    private class Rebuild {
        var calls = 0
        var from: LocalDate? = null
    }

    private fun refresh(store: LocalDataStore): Rebuild {
        val rebuild = Rebuild()
        val repository = PriceRepository(
            store,
            SymbolMap(context.assets),
            deadEndpoint,
        ) { _, from, _ ->
            rebuild.calls++
            rebuild.from = from
            emptyList()
        }
        runBlocking { repository.refresh(listOf(TICKER)) }
        return rebuild
    }

    @Test
    fun `a stub the app built does not count as history the feeds carry`() {
        val store = LocalDataStore(context)
        // VLMRA exactly as the phone actually held it: four sessions the app built out of hourly
        // bars, and one the ISIN feed reported. Five rows - the threshold to the row, which is the
        // whole of why it never moved again.
        store.saveSessions(
            (17..20).map { session(LocalDate.of(2026, 8, it), 30.0) },
            PriceRepository.DERIVED_SOURCE,
        )
        store.saveSessions(
            listOf(session(LocalDate.of(2026, 8, 23), 29.43)),
            PriceRepository.SOURCE,
        )

        assertEquals(1, refresh(store).calls)
    }

    @Test
    fun `the rebuild asks for the whole hourly window, not where the stored history stops`() {
        val store = LocalDataStore(context)
        store.saveSessions(
            listOf(session(LocalDate.of(2026, 8, 23), 29.43)),
            PriceRepository.SOURCE,
        )

        // Null is "everything the hourly feed still keeps". Handed the incremental window instead,
        // this rebuilds the three days it already has and calls the history built.
        assertNull(refresh(store).from)
    }

    @Test
    fun `a stock the daily feeds do carry is never rebuilt`() {
        val store = LocalDataStore(context)
        // Six reported sessions is a feed doing its job, and a rebuild here would be a request
        // spent on a stock that does not need one - the reason the threshold exists at all.
        store.saveSessions(
            (16..21).map { session(LocalDate.of(2026, 8, it), 30.0) },
            PriceRepository.SOURCE,
        )

        assertEquals(0, refresh(store).calls)
    }

    private companion object {
        /** Mapped to an ISIN symbol in the shipped asset, which is what makes it rebuildable. */
        const val TICKER = "VLMRA"
    }
}
