package com.ikverse.egxanalyzer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ikverse.egxanalyzer.model.FeedFault
import com.ikverse.egxanalyzer.model.PriceHealthReport
import com.ikverse.egxanalyzer.model.StockHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/**
 * The log [PriceHealth] left with no memory of its own, now that the Settings card explaining a
 * fault in words is gone. Nothing on screen reads this table; it exists for a diagnostics copy
 * pulled off a device, so most of this file is about the log staying honest and staying small.
 */
@RunWith(RobolectricTestRunner::class)
class FeedHealthStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun stock(
        ticker: String = "AMOC",
        faults: Set<FeedFault> = setOf(FeedFault.STALE),
        callsHeld: Int = 3,
        newestSession: LocalDate? = LocalDate.of(2026, 8, 1),
        ageDays: Long? = 9L,
    ) = StockHealth(
        ticker = ticker,
        faults = faults,
        newestSession = newestSession,
        ageDays = ageDays,
        callsHeld = callsHeld,
    )

    @Test
    fun `a check comes back exactly as it went in`() {
        val store = LocalDataStore(context)
        val report = PriceHealthReport(faults = listOf(stock()), stocksNamed = 12)

        store.saveFeedHealth(report, now = 1_000L)

        val read = store.feedHealthChecks().single()
        assertEquals(12, read.stocksNamed)
        assertEquals(3, read.callsHeld)
        assertEquals(listOf(stock()), read.faults)
    }

    @Test
    fun `a clean check is recorded too, not only a faulty one`() {
        // The distinction the removed card existed to make visible: "checked and found nothing
        // wrong" has to be told apart from "never checked at all", and only a row for both readings
        // can do that.
        val store = LocalDataStore(context)

        store.saveFeedHealth(PriceHealthReport(faults = emptyList(), stocksNamed = 5), now = 1_000L)

        val read = store.feedHealthChecks().single()
        assertEquals(5, read.stocksNamed)
        assertTrue(read.faults.isEmpty())
    }

    @Test
    fun `an unchanged reading is not written again`() {
        val store = LocalDataStore(context)
        val report = PriceHealthReport(faults = listOf(stock()), stocksNamed = 12)
        store.saveFeedHealth(report, now = 1_000L)

        store.saveFeedHealth(report, now = 2_000L)

        // Two identical checks are one entry: writing the second would fill the log with repeats
        // of a state that has not moved and push out the check that actually changed something.
        assertEquals(1, store.feedHealthChecks().size)
    }

    @Test
    fun `a faulty stock recovering is a change worth a new entry`() {
        val store = LocalDataStore(context)
        store.saveFeedHealth(PriceHealthReport(faults = listOf(stock()), stocksNamed = 12), now = 1_000L)

        store.saveFeedHealth(PriceHealthReport(faults = emptyList(), stocksNamed = 12), now = 2_000L)

        val checks = store.feedHealthChecks()
        assertEquals(2, checks.size)
        assertTrue(checks.first().faults.isEmpty())
    }

    @Test
    fun `more calls held on the same stock is a change worth a new entry`() {
        // The figure the whole log exists for is calls held, not the stock count - a source
        // quietly losing more of its record to one frozen symbol must not be mistaken for no
        // change at all.
        val store = LocalDataStore(context)
        store.saveFeedHealth(
            PriceHealthReport(faults = listOf(stock(callsHeld = 3)), stocksNamed = 12),
            now = 1_000L,
        )

        store.saveFeedHealth(
            PriceHealthReport(faults = listOf(stock(callsHeld = 4)), stocksNamed = 12),
            now = 2_000L,
        )

        assertEquals(2, store.feedHealthChecks().size)
    }

    @Test
    fun `fault order does not by itself count as a change`() {
        val store = LocalDataStore(context)
        val first = PriceHealthReport(
            faults = listOf(stock(ticker = "AMOC"), stock(ticker = "COMI")),
            stocksNamed = 12,
        )
        store.saveFeedHealth(first, now = 1_000L)

        val reordered = first.copy(faults = first.faults.reversed())
        store.saveFeedHealth(reordered, now = 2_000L)

        assertEquals(1, store.feedHealthChecks().size)
    }

    @Test
    fun `checks come back newest first`() {
        val store = LocalDataStore(context)
        store.saveFeedHealth(PriceHealthReport(faults = emptyList(), stocksNamed = 1), now = 1_000L)
        store.saveFeedHealth(PriceHealthReport(faults = listOf(stock()), stocksNamed = 1), now = 2_000L)

        val checks = store.feedHealthChecks()
        assertEquals(2, checks.size)
        assertEquals(2_000L, checks.first().checkedAt.toEpochMilli())
    }

    @Test
    fun `the log is pruned to the newest 200 checks`() {
        val store = LocalDataStore(context)
        // Alternates clean and faulty so every write actually changes the signature and is kept -
        // 201 distinct states, one more than the ceiling.
        for (i in 0 until 201) {
            val faults = if (i % 2 == 0) emptyList() else listOf(stock(callsHeld = i))
            store.saveFeedHealth(PriceHealthReport(faults = faults, stocksNamed = 1), now = i.toLong())
        }

        val checks = store.feedHealthChecks(limit = 500)
        assertEquals(200, checks.size)
        // The oldest one - now = 0 - is the one that had to go for the newest 200 to fit.
        assertTrue(checks.none { it.checkedAt.toEpochMilli() == 0L })
        // Newest first: the survivor at the far end is the next-oldest, checked_at = 1.
        assertEquals(1L, checks.last().checkedAt.toEpochMilli())
    }

    @Test
    fun `a row this build cannot name a fault for is dropped rather than read as a healthy stock`() {
        val store = LocalDataStore(context)
        store.writableDatabase.insertWithOnConflict(
            "feed_checks",
            null,
            ContentValues().apply {
                put("checked_at", 1_000L)
                put("stocks_named", 5)
                put("faulty_stocks", 1)
                put("calls_held", 2)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        store.writableDatabase.insertWithOnConflict(
            "feed_faults",
            null,
            ContentValues().apply {
                put("checked_at", 1_000L)
                put("ticker", "AMOC")
                put("faults", "SOME_FUTURE_FAULT")
                put("calls_held", 2)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )

        // Silently filed as an unaffected stock would be the worse failure - it would report the
        // record as cleaner than the phone that wrote the row actually found it to be.
        assertTrue(store.feedHealthChecks().single().faults.isEmpty())
    }

    @Test
    fun `upgrading from 26 gains the log and keeps the trades already on the phone`() {
        // Version 26 as it shipped, written by hand: a test built from today's migration code
        // moves whenever that code does, and would stop covering the one upgrade every phone
        // holding trades actually has to run.
        Version26(context).writableDatabase.use { old ->
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

        val store = LocalDataStore(context)
        store.saveFeedHealth(PriceHealthReport(faults = listOf(stock()), stocksNamed = 1), now = 1L)

        assertEquals(1, store.feedHealthChecks().size)
        // The risk on an upgrade is never that it fails outright - it is that it takes the answers
        // already on the phone with it.
        assertEquals("AMOC@2026-08-03", store.positions().single().id)
    }

    private class Version26(context: Context) :
        SQLiteOpenHelper(context, LocalDataStore.DATABASE_NAME, null, 26) {

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
                    window_custom INTEGER NOT NULL DEFAULT 0,
                    is_t_plus_one INTEGER NOT NULL DEFAULT 0,
                    keep_open INTEGER NOT NULL DEFAULT 0,
                    keep_open_note TEXT,
                    unknown TEXT NOT NULL DEFAULT '{}',
                    opened_at INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL DEFAULT 0,
                    updated_by TEXT NOT NULL DEFAULT '',
                    deleted INTEGER NOT NULL DEFAULT 0,
                    exit_price_1 REAL,
                    exit_date_1 TEXT,
                    exit_price_2 REAL,
                    exit_split_pct REAL
                )""",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
