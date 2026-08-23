package com.ikverse.egxanalyzer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.SettledCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/**
 * That a call the market has finished with stays finished, and comes undone when it should.
 *
 * The freeze is the one derived thing on the Insights tab that is written down, so it is the one
 * that can be *wrong* in a way re-deriving would have fixed. Both halves are tested for that
 * reason: the round trip, because a verdict read back differently from how it went in is a rate
 * that quietly disagrees with the prices behind it; and the two events that must drop it, because a
 * verdict surviving a heal is exactly the phantom stop-out the split handling exists to prevent.
 */
@RunWith(RobolectricTestRunner::class)
class SettledCallStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun stopped(key: String, ticker: String = "AMOC") = SettledCall(
        key = key,
        ticker = ticker,
        outcome = Outcome.STOPPED,
        settledOn = LocalDate.of(2026, 8, 12),
        stoppedOn = LocalDate.of(2026, 8, 12),
        stoppedAfterPartial = false,
        windowComplete = false,
        peakHigh = 11.4,
        peakOn = LocalDate.of(2026, 8, 10),
        troughLow = 8.9,
        troughOn = LocalDate.of(2026, 8, 12),
        returnPct = -9.5,
        sessionsElapsed = 4,
        sessions = listOf(
            DailySession(ticker, LocalDate.of(2026, 8, 10), 11.4, 10.8, 11.0, 1_200.0, open = 10.9),
            // A rebuilt session, because the flag is a fact about where the row came from and a
            // round trip that quietly dropped it would pass a fabricated row off as reported.
            DailySession(ticker, LocalDate.of(2026, 8, 11), 11.0, 10.2, 10.3, null, derived = true),
            DailySession(ticker, LocalDate.of(2026, 8, 12), 10.3, 8.9, 9.0, 3_400.0, open = 10.1),
        ),
    )

    @Test
    fun `a verdict comes back exactly as it went in`() {
        val store = LocalDataStore(context)
        val settled = stopped("AMOC@2026-08-10@source-one#10.5#11.0#12.0#13.0#9.5#30/30")

        store.saveSettledCalls(listOf(settled))

        assertEquals(mapOf(settled.key to settled), store.settledCalls())
    }

    @Test
    fun `a heal re-opens that stock and leaves every other one alone`() {
        val store = LocalDataStore(context)
        val amoc = stopped("AMOC@2026-08-10@one#a", ticker = "AMOC")
        val swdy = stopped("SWDY@2026-08-10@one#a", ticker = "SWDY")
        store.saveSettledCalls(listOf(amoc, swdy))

        // What the price repository does when a refetch has replaced a stock's whole series.
        store.clearSettledCalls("AMOC")

        val left = store.settledCalls()
        assertNull(left[amoc.key])
        assertEquals(swdy, left[swdy.key])
    }

    @Test
    fun `a change of scale nobody had recorded re-opens the stock`() {
        val store = LocalDataStore(context)
        val settled = stopped("AMOC@2026-08-10@one#a")
        store.saveSettledCalls(listOf(settled))

        store.savePriceBreaks(
            listOf(PriceBreak("AMOC", LocalDate.of(2026, 8, 11), 10.0, 5.0)),
        )

        // The verdict was reached on prices now known to be in two different currencies. Keeping it
        // would keep a stop-out the channel never earned.
        assertTrue(store.settledCalls().isEmpty())
    }

    @Test
    fun `a change of scale already on disk changes nothing`() {
        val store = LocalDataStore(context)
        val known = PriceBreak("AMOC", LocalDate.of(2026, 8, 11), 10.0, 5.0)
        store.savePriceBreaks(listOf(known))
        val settled = stopped("AMOC@2026-08-10@one#a")
        store.saveSettledCalls(listOf(settled))

        // Every refresh re-reports the breaks it finds. Treating a re-report as news would throw
        // the record open on every refresh, for good, on every stock that has ever split.
        store.savePriceBreaks(listOf(known))

        assertEquals(settled, store.settledCalls()[settled.key])
    }

    @Test
    fun `upgrading from 19 gains the table and keeps the trades already on the phone`() {
        // Version 19 as it shipped, written by hand: a test that builds its "old" schema from
        // today's code tests nothing, because both sides move together.
        Version19(context).writableDatabase.use { old ->
            old.insertWithOnConflict(
                "positions",
                null,
                ContentValues().apply {
                    put("id", "AMOC@2026-08-03")
                    put("ticker", "AMOC")
                    put("recommendation_date", "2026-08-03")
                    put("entry_price", 1.2)
                    put("entry_date", "2026-08-03")
                    put("window_sessions", 10)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }

        // This build's own upgrade, run against that phone.
        val store = LocalDataStore(context)
        val settled = stopped("AMOC@2026-08-10@one#a")
        store.saveSettledCalls(listOf(settled))

        assertEquals(settled, store.settledCalls()[settled.key])
        // The risk on an upgrade is never that it fails outright - it is that it takes the answers
        // already on the phone with it.
        assertEquals("AMOC@2026-08-03", store.positions().single().id)
    }

    private class Version19(context: Context) :
        SQLiteOpenHelper(context, LocalDataStore.DATABASE_NAME, null, 19) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS positions (
                    id TEXT PRIMARY KEY,
                    ticker TEXT NOT NULL,
                    name_en TEXT,
                    name_ar TEXT,
                    channel TEXT,
                    recommendation_date TEXT NOT NULL,
                    entry_price REAL NOT NULL,
                    entry_date TEXT NOT NULL,
                    exit_price REAL,
                    exit_date TEXT,
                    closed_manually INTEGER NOT NULL DEFAULT 0,
                    entry_low REAL,
                    entry_high REAL,
                    target1 REAL,
                    target2 REAL,
                    stop_loss REAL,
                    window_sessions INTEGER NOT NULL,
                    opened_at INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL DEFAULT 0,
                    updated_by TEXT NOT NULL DEFAULT '',
                    deleted INTEGER NOT NULL DEFAULT 0,
                    window_custom INTEGER NOT NULL DEFAULT 0,
                    keep_open INTEGER NOT NULL DEFAULT 0,
                    keep_open_note TEXT,
                    unknown TEXT
                )""",
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS call_alert_seen (
                    call_id TEXT PRIMARY KEY,
                    in_band INTEGER NOT NULL,
                    at INTEGER NOT NULL
                )""",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    }
}
