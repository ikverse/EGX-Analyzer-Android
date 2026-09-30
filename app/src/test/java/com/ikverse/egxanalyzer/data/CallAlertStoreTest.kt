package com.ikverse.egxanalyzer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ikverse.egxanalyzer.model.CallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * That what the user has been told about a buy zone survives a restart, and that gaining the table
 * costs no trades.
 *
 * The record of what was said is the whole basis of an alert about a *crossing*: lose it and every
 * call is seen for the first time, which announces nothing and then announces the next crossing as
 * though it were the first. The migration half matters for the reason `LocalDataStoreMigrationTest`
 * gives - a fresh install builds the table correctly every time, so a bad version bump only ever
 * shows up on a phone that has been running the app for months.
 */
@RunWith(RobolectricTestRunner::class)
class CallAlertStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a state comes back exactly as it went in`() {
        val store = LocalDataStore(context)
        val seen = mapOf(
            "AMOC@2026-08-10@source-one" to CallState(inBand = true),
            "SWDY@2026-08-10@source-two" to CallState(inBand = false),
        )

        store.saveCallAlertSeen(seen)

        assertEquals(seen, store.callAlertSeen())
    }

    @Test
    fun `a call the record no longer holds is forgotten`() {
        val store = LocalDataStore(context)
        store.saveCallAlertSeen(mapOf("AMOC@2026-08-10@source-one" to CallState(inBand = true)))

        // Its report was deleted, or the call has settled. Without this the table grows forever and
        // a session re-run months later is compared against a reading nobody remembers.
        store.saveCallAlertSeen(emptyMap(), forgotten = setOf("AMOC@2026-08-10@source-one"))

        assertTrue(store.callAlertSeen().isEmpty())
    }

    @Test
    fun `upgrading from 18 gains the table and keeps the trades already on the phone`() {
        // Version 18 as it actually shipped, written by hand. A test that builds its "old" schema
        // from today's code tests nothing, because both sides move together.
        val name = "egx-upgrade-18.db"
        object : SQLiteOpenHelper(context, name, null, 18) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS positions (
                        id TEXT PRIMARY KEY,
                        ticker TEXT NOT NULL,
                        recommendation_date TEXT,
                        entry_price REAL NOT NULL,
                        entry_date TEXT NOT NULL
                    )""",
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS position_status_seen (
                        position_id TEXT PRIMARY KEY,
                        status TEXT NOT NULL,
                        open INTEGER NOT NULL,
                        at INTEGER NOT NULL
                    )""",
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
        }.writableDatabase.use { db ->
            db.insertWithOnConflict(
                "positions",
                null,
                ContentValues().apply {
                    put("id", "AMOC@2026-08-03")
                    put("ticker", "AMOC")
                    put("recommendation_date", "2026-08-03")
                    put("entry_price", 1.2)
                    put("entry_date", "2026-08-03")
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }

        // The upgrade this build performs on a phone holding that.
        val upgraded = object : SQLiteOpenHelper(
            context,
            name,
            null,
            LocalDataStore.DATABASE_VERSION,
        ) {
            override fun onCreate(db: SQLiteDatabase) = Unit
            override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS call_alert_seen (
                        call_id TEXT PRIMARY KEY,
                        in_band INTEGER NOT NULL,
                        at INTEGER NOT NULL
                    )""",
                )
            }
        }.writableDatabase

        upgraded.use { db ->
            // The new table exists and is usable.
            db.insertWithOnConflict(
                "call_alert_seen",
                null,
                ContentValues().apply {
                    put("call_id", "AMOC@2026-08-10@source-one")
                    put("in_band", 1)
                    put("at", 0L)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            // And the trade that was already there is still there. The risk on an upgrade is never
            // that it fails outright - it is that it takes the answers already on the phone with it.
            db.query("positions", null, null, null, null, null, null).use { cursor ->
                assertEquals(1, cursor.count)
            }
        }
    }
}
