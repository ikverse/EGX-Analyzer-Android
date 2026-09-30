package com.ikverse.egxanalyzer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.ikverse.egxanalyzer.model.DailySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/**
 * That a session the app built is never read back as one the exchange reported.
 *
 * This is the guarantee a `derived` column would have given, and it rests instead on the `source`
 * column the table has carried since it was created - which is why it is worth a test of its own.
 * A migration cannot go wrong here because there is no migration; what can go wrong is the string,
 * so the round trip is pinned in both directions and on every read path.
 *
 * Robolectric because these are real SQLite reads. A fake would round-trip whatever it was handed
 * and would never exercise the one thing being checked, which is what the cursor makes of a column
 * holding one of three things: the daily feed's name, the aggregator's, or nothing at all.
 */
@RunWith(RobolectricTestRunner::class)
class PriceRepositoryDerivedTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val day = LocalDate.of(2026, 8, 10)

    private fun session(ticker: String, on: LocalDate, close: Double, derived: Boolean) =
        DailySession(
            ticker = ticker,
            date = on,
            high = close + 0.5,
            low = close - 0.5,
            close = close,
            volume = 1000.0,
            open = close - 0.2,
            derived = derived,
        )

    @Test
    fun `a rebuilt session reads back as rebuilt and a reported one does not`() {
        val store = LocalDataStore(context)
        store.saveSessions(listOf(session("VLMRA", day, 3.0, derived = true)), PriceRepository.DERIVED_SOURCE)
        store.saveSessions(listOf(session("AMOC", day, 10.0, derived = false)), PriceRepository.SOURCE)

        assertTrue(store.sessionsFrom("VLMRA", day).single().derived)
        assertFalse(store.sessionsFrom("AMOC", day).single().derived)
        // Every read path, because each builds its own cursor and one left behind would report a
        // rebuilt price as the exchange's on whichever screen happened to use it.
        assertTrue(store.latestSession("VLMRA")!!.derived)
        assertFalse(store.latestSession("AMOC")!!.derived)
        assertTrue(store.latestSessions().getValue("VLMRA").derived)
        assertFalse(store.latestSessions().getValue("AMOC").derived)
    }

    @Test
    fun `a reported session replaces a rebuilt one for the same day`() {
        val store = LocalDataStore(context)
        store.saveSessions(listOf(session("VLMRA", day, 3.0, derived = true)), PriceRepository.DERIVED_SOURCE)

        // The day the stock's real daily feed comes back. The table is keyed on (ticker, date) and
        // replaces on conflict, so this needs no cleanup path of its own - which is also why the
        // refresh writes the rebuilt rows first and the reported ones second.
        store.saveSessions(listOf(session("VLMRA", day, 3.4, derived = false)), PriceRepository.SOURCE)

        val stored = store.sessionsFrom("VLMRA", day).single()
        assertEquals(3.4, stored.close!!, 1e-9)
        assertFalse(stored.derived)
    }

    @Test
    fun `a row written before any of this existed reads as reported`() {
        val store = LocalDataStore(context)
        // Every price already on a phone was written by the daily feed under its own name, and
        // rows older still may carry no source at all. Both are exactly what they claim: sessions
        // the exchange reported. Reading either as rebuilt would put a caveat on the whole record.
        store.writableDatabase.insertWithOnConflict(
            "daily_prices",
            null,
            ContentValues().apply {
                put("ticker", "COMI")
                put("session_date", day.toString())
                put("high", 62.0)
                put("low", 60.0)
                put("close", 61.0)
                putNull("source")
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )

        assertFalse(store.sessionsFrom("COMI", day).single().derived)
        assertFalse(store.latestSession("COMI")!!.derived)
    }

    @Test
    fun `the two sources are different strings`() {
        // The whole scheme rests on this. Equal, and every rebuilt row on every phone would read as
        // one the exchange reported, silently and with nothing failing.
        assertTrue(PriceRepository.SOURCE != PriceRepository.DERIVED_SOURCE)
    }
}
