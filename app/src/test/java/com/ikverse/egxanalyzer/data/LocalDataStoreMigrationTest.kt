package com.ikverse.egxanalyzer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ikverse.egxanalyzer.model.IntradayBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import java.time.LocalDate

/**
 * That an update does not cost the user the trades they already recorded.
 *
 * This is the one failure the rest of the suite cannot see. A fresh install builds the table from
 * scratch and is always correct, so a migration that scrambles or drops an existing row looks
 * perfect in development and only breaks on a phone that has been running the app for months -
 * which is every phone that matters. Plain JVM tests cannot open a database at all, so before this
 * existed the only check was installing over an old build by hand and hoping to notice.
 *
 * The shape below is version 9 exactly as it shipped. It is written out in full rather than
 * generated, because a migration test that builds its "old" table from today's code is testing
 * nothing: both sides would move together and the test would pass through the very change that
 * breaks a real upgrade.
 */
@RunWith(RobolectricTestRunner::class)
class LocalDataStoreMigrationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val called = LocalDate.of(2026, 7, 20)

    @Test
    fun `a trade recorded before the update survives it`() {
        Version9(context).writableDatabase.use { old ->
            old.insert("positions", null, version9Position())
        }

        val positions = LocalDataStore(context).positions()

        val restored = positions.single()
        assertEquals("AMOC@2026-07-20", restored.id)
        assertEquals("AMOC", restored.ticker)
        assertEquals(called, restored.recommendationDate)
        assertEquals(10.5, restored.entryPrice, 0.0001)
        assertEquals(11.0, restored.target1!!, 0.0001)
        assertEquals(9.0, restored.stopLoss!!, 0.0001)
        assertEquals(10, restored.windowSessions)
        assertEquals("phone", restored.updatedBy)
    }

    @Test
    fun `the new columns arrive switched off, which is what those trades were`() {
        Version9(context).writableDatabase.use { old ->
            old.insert("positions", null, version9Position())
        }

        val restored = LocalDataStore(context).positions().single()

        // A trade recorded before any of this existed took the window it was offered and closed
        // when that ran out. False on both is not a default chosen for convenience - it is the
        // truth about those trades.
        assertFalse(restored.keepOpen)
        assertFalse(restored.windowCustom)
    }

    @Test
    fun `the upgraded table takes a trade the new columns actually use`() {
        Version9(context).writableDatabase.use { old ->
            old.insert("positions", null, version9Position())
        }

        val store = LocalDataStore(context)
        val kept = store.positions().single().copy(
            keepOpen = true,
            windowCustom = true,
            windowSessions = 21,
        )
        store.savePosition(kept)

        val restored = LocalDataStore(context).positions().single()
        assertTrue(restored.keepOpen)
        assertTrue(restored.windowCustom)
        assertEquals(21, restored.windowSessions)
    }

    @Test
    fun `an upgraded row carries fields a newer app version wrote`() {
        // The column exists to stop this device erasing what a later one knows simply by editing a
        // trade. An upgraded table has to have it too, or the phone that upgrades is the one that
        // does the erasing.
        Version9(context).writableDatabase.use { old ->
            old.insert("positions", null, version9Position())
        }

        val store = LocalDataStore(context)
        // Nothing was stored against a version-9 row, so it starts empty and stays valid JSON.
        assertEquals("{}", store.unknownFor("AMOC@2026-07-20"))

        store.savePosition(store.positions().single(), """{"trailingStopPct":5.5}""")
        assertEquals(
            """{"trailingStopPct":5.5}""",
            LocalDataStore(context).unknownFor("AMOC@2026-07-20"),
        )
    }

    @Test
    fun `a reason for keeping a trade open survives the upgrade`() {
        Version9(context).writableDatabase.use { old ->
            old.insert("positions", null, version9Position())
        }

        val store = LocalDataStore(context)
        store.savePosition(
            store.positions().single().copy(keepOpen = true, keepOpenNote = "Holding for T2"),
        )

        val restored = LocalDataStore(context).positions().single()
        assertTrue(restored.keepOpen)
        assertEquals("Holding for T2", restored.keepOpenNote)
    }

    @Test
    fun `a phone that ran the half-finished schema 10 still gets the rest of the columns`() {
        // The bug this exists for, found on a real phone rather than here. A build shipped schema
        // 10 carrying only two of the four new columns; the next build added the other two to the
        // same version number, so onUpgrade never fired again and they could never arrive. Version
        // 11 makes it fire, and the per-column guards make it add only what is actually missing.
        Version10Partial(context).writableDatabase.use { old ->
            old.insert("positions", null, version9Position().apply { put("keep_open", 1) })
        }

        val store = LocalDataStore(context)
        val restored = store.positions().single()

        // The two the half-finished version did have, with their values intact.
        assertTrue(restored.keepOpen)
        assertFalse(restored.windowCustom)
        // The two it did not, which is what was broken.
        assertEquals(null, restored.keepOpenNote)
        assertEquals("{}", store.unknownFor("AMOC@2026-07-20"))

        store.savePosition(restored.copy(keepOpenNote = "Holding for T2"), """{"future":1}""")
        val again = LocalDataStore(context)
        assertEquals("Holding for T2", again.positions().single().keepOpenNote)
        assertEquals("""{"future":1}""", again.unknownFor("AMOC@2026-07-20"))
    }

    @Test
    fun `a database created from scratch matches one that was upgraded`() {
        // The two paths are written separately - onCreate builds the table, onUpgrade alters it -
        // and nothing else in the suite would notice them drifting apart.
        val fresh = LocalDataStore(context)
        fresh.savePosition(
            com.ikverse.egxanalyzer.model.Position(
                ticker = "COMI",
                recommendationDate = called,
                entryPrice = 50.0,
                entryDate = called,
                windowSessions = 10,
                keepOpen = true,
                windowCustom = true,
            ),
        )

        val restored = LocalDataStore(context).positions().single()
        assertTrue(restored.keepOpen)
        assertTrue(restored.windowCustom)
    }

    @Test
    fun `a phone on schema 11 gains the price-events table and keeps its prices`() {
        Version11(context).writableDatabase.use { old ->
            old.insert("positions", null, version9Position())
            old.insert("daily_prices", null, storedSession())
        }

        val store = LocalDataStore(context)

        // The prices it already had, untouched by the upgrade. A migration that quietly emptied
        // this table would leave every call unscored and look like a feed outage.
        val session = store.sessionsFrom("AMOC", called).single()
        assertEquals(called, session.date)
        assertEquals(10.4, session.high!!, 0.0001)

        // And the new table is there to be written to, which is the point of the version bump.
        store.savePriceBreaks(
            listOf(PriceBreak("AMOC", called.plusDays(1), previousClose = 10.2, openingPrice = 5.1)),
        )
        assertEquals(
            setOf(called.plusDays(1)),
            LocalDataStore(context).priceBreakDates()["AMOC"],
        )
    }

    @Test
    fun `a phone on schema 12 gains the intraday tables and keeps its prices`() {
        Version12(context).writableDatabase.use { old ->
            old.insert("positions", null, version9Position())
            old.insert("daily_prices", null, storedSession())
        }

        val store = LocalDataStore(context)

        // What it already had, untouched. A migration that quietly emptied this would leave every
        // call unscored and read as a feed outage rather than as a bad upgrade.
        assertEquals(10.4, store.sessionsFrom("AMOC", called).single().high!!, 0.0001)

        // And the new tables are there to be written to, which is the point of the version bump.
        store.saveIntradayBars(
            "AMOC",
            called,
            listOf(IntradayBar("AMOC", Instant.ofEpochSecond(1_000), high = 10.4, low = 9.8)),
        )

        val reopened = LocalDataStore(context)
        assertEquals(1, reopened.intradayBars()[("AMOC" to called)]?.size)
        assertEquals(setOf("AMOC" to called), reopened.intradayFetched())
    }

    @Test
    fun `a session the feed had no bars for is still recorded as asked about`() {
        // Otherwise it is asked again on every refresh for the rest of the record's life - and the
        // sessions with nothing to give are exactly the ones past the feed's intraday retention,
        // which only accumulate.
        val store = LocalDataStore(context)

        store.saveIntradayBars("AMOC", called, emptyList())

        assertTrue(store.intradayBars().isEmpty())
        assertEquals(setOf("AMOC" to called), store.intradayFetched())
    }

    @Test
    fun `a healed series drops its bars along with its prices`() {
        // A split rewrites the intraday history exactly as it rewrites the daily one. Bars kept
        // across one would be in the old money while the levels beside them are in the new, and
        // ordering an entry against a target on them would give a confident, wrong answer.
        val store = LocalDataStore(context)
        store.saveIntradayBars(
            "AMOC",
            called,
            listOf(IntradayBar("AMOC", Instant.ofEpochSecond(1_000), high = 10.4, low = 9.8)),
        )

        store.clearIntraday("AMOC")

        val reopened = LocalDataStore(context)
        assertTrue(reopened.intradayBars().isEmpty())
        // The fetch record goes too, so the session is asked about again against the new scale.
        assertTrue(reopened.intradayFetched().isEmpty())
    }

    @Test
    fun `healing a series clears all of it, including what a refetch cannot reach`() {
        // The loop this prevents: a refetch reaches back a year, so a split older than that would
        // leave the pre-split rows in place with a seam between them and the new ones. That seam is
        // the same break in a different position - found again on the next refresh, healed again,
        // and never settling. Everything goes, because everything older is in the old money.
        val store = LocalDataStore(context)
        val old = called.minusYears(2)
        store.saveSessions(
            listOf(
                com.ikverse.egxanalyzer.model.DailySession("AMOC", old, 20.0, 19.0, 19.5, 1.0, 19.2),
                com.ikverse.egxanalyzer.model.DailySession("AMOC", called, 10.4, 9.8, 10.2, 1.0, 9.9),
            ),
            "Yahoo Finance",
        )
        assertEquals(2, store.allSessions("AMOC").size)

        store.deleteSessions("AMOC")

        assertTrue(LocalDataStore(context).allSessions("AMOC").isEmpty())
    }

    @Test
    fun `a healed series takes its recorded break with it`() {
        // Refetching the whole history puts it back on one scale, so the break that was recorded
        // against the old mixture is no longer true of what is on disk. Left behind, it would go on
        // un-judging every call on the stock forever.
        val store = LocalDataStore(context)
        store.savePriceBreaks(
            listOf(PriceBreak("AMOC", called, previousClose = 10.0, openingPrice = 5.0)),
        )
        assertTrue(store.priceBreakDates().containsKey("AMOC"))

        store.clearPriceBreaks("AMOC")

        assertFalse(LocalDataStore(context).priceBreakDates().containsKey("AMOC"))
    }

    private fun storedSession() = ContentValues().apply {
        put("ticker", "AMOC")
        put("session_date", called.toString())
        put("open", 9.9)
        put("high", 10.4)
        put("low", 9.8)
        put("close", 10.2)
        put("volume", 1_000.0)
        put("source", "Yahoo Finance")
    }

    private fun version9Position() = ContentValues().apply {
        put("id", "AMOC@2026-07-20")
        put("ticker", "AMOC")
        put("name_en", "Egyptian Company")
        put("name_ar", "المصرية")
        put("channel", "First channel")
        put("recommendation_date", called.toString())
        put("entry_price", 10.5)
        put("entry_date", called.toString())
        put("closed_manually", 0)
        put("entry_low", 10.0)
        put("entry_high", 11.0)
        put("target1", 11.0)
        put("target2", 12.0)
        put("stop_loss", 9.0)
        put("window_sessions", 10)
        put("opened_at", 1_700_000_000_000L)
        put("updated_at", 1_700_000_000_000L)
        put("updated_by", "phone")
        put("deleted", 0)
    }

    /**
     * The positions table as version 9 shipped it, and nothing else.
     *
     * Only this table is recreated: the upgrade path for every other one already runs `CREATE TABLE
     * IF NOT EXISTS`, so an empty file exercises them the same way a full one would.
     */
    private class Version9(context: Context) :
        SQLiteOpenHelper(context, LocalDataStore.DATABASE_NAME, null, 9) {

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
                    deleted INTEGER NOT NULL DEFAULT 0
                )""",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    /**
     * Schema 10 as one build actually left it: two of the four new columns, not four.
     *
     * Written out rather than derived, for the same reason [Version9] is - and this one doubly so,
     * since the whole failure was a version number that stopped describing what a phone held.
     */
    private class Version10Partial(context: Context) :
        SQLiteOpenHelper(context, LocalDataStore.DATABASE_NAME, null, 10) {

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
                    keep_open INTEGER NOT NULL DEFAULT 0,
                    opened_at INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL DEFAULT 0,
                    updated_by TEXT NOT NULL DEFAULT '',
                    deleted INTEGER NOT NULL DEFAULT 0
                )""",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    /**
     * Schema 11 as it shipped: every position column, prices, and no `price_events`.
     *
     * Written out for the same reason the two above are. This one carries `daily_prices` as well,
     * because the question the version-12 upgrade has to answer is not only whether the new table
     * arrives but whether the prices already on the phone are still there afterwards.
     */
    private class Version11(context: Context) :
        SQLiteOpenHelper(context, LocalDataStore.DATABASE_NAME, null, 11) {

        override fun onCreate(db: SQLiteDatabase) = version11Tables(db)

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    /**
     * Schema 12 as it shipped: schema 11 plus `price_events`, and no intraday tables.
     *
     * Written by hand like the rest. Building the "old" schema from today's code would test
     * nothing, because both sides would move together.
     */
    private class Version12(context: Context) :
        SQLiteOpenHelper(context, LocalDataStore.DATABASE_NAME, null, 12) {

        override fun onCreate(db: SQLiteDatabase) {
            version11Tables(db)
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS price_events (
                    ticker TEXT NOT NULL,
                    session_date TEXT NOT NULL,
                    previous_close REAL NOT NULL,
                    opening_price REAL NOT NULL,
                    detected_at INTEGER NOT NULL,
                    PRIMARY KEY (ticker, session_date)
                )""",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    /**
     * The tables schema 11 shipped, hand-written, shared by the two old shapes that both had them.
     *
     * A function rather than a base class so it stays a literal transcription of what a phone
     * holds: 12 is 11 plus one table, and writing 11 out twice would let the two drift.
     */
    private companion object {
        fun version11Tables(db: SQLiteDatabase) {
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
                    keep_open INTEGER NOT NULL DEFAULT 0,
                    keep_open_note TEXT,
                    unknown TEXT NOT NULL DEFAULT '{}',
                    opened_at INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL DEFAULT 0,
                    updated_by TEXT NOT NULL DEFAULT '',
                    deleted INTEGER NOT NULL DEFAULT 0
                )""",
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS daily_prices (
                    ticker TEXT NOT NULL,
                    session_date TEXT NOT NULL,
                    open REAL,
                    high REAL,
                    low REAL,
                    close REAL,
                    volume REAL,
                    source TEXT,
                    PRIMARY KEY (ticker, session_date)
                )""",
            )
        }
    }

    @Test
    fun `days the exchange was shut are cleared out of a database that stored them`() {
        // Yahoo answers an EGX holiday with the previous close repeated across the high, the low
        // and the close, and no volume. Those went in as ordinary sessions, and a session that
        // never happened still counts against a call's window - which is half of a T+1's.
        //
        // The parse refuses them now, but only guards what arrives next: the refresh re-asks for
        // three days and the heal that rewrites a whole history fires only on a change of scale,
        // so rows already on disk would have stayed for good.
        Version21(context).writableDatabase.use { old ->
            old.insert("daily_prices", null, session("AMOC", "2026-08-06", 6.18, 5.81, 5.90, 13_361_341.0))
            // The holiday: one price, no volume.
            old.insert("daily_prices", null, session("AMOC", "2026-08-09", 5.90, 5.90, 5.90, 0.0))
            old.insert("daily_prices", null, session("AMOC", "2026-08-11", 7.19, 6.48, 7.15, 15_348_885.0))
            // A real session that printed once, which looks the same but for the volume against it.
            old.insert("daily_prices", null, session("THIN", "2026-08-11", 4.20, 4.20, 4.20, 900.0))
            old.insert("price_events", null, ContentValues().apply {
                put("ticker", "AMOC")
                put("session_date", "2026-08-11")
                put("previous_close", 5.90)
                put("opening_price", 7.15)
                put("detected_at", 1L)
            })
            old.insert("settled_calls", null, ContentValues().apply {
                put("call_key", "AMOC@2026-08-06@a#1#1#2#3#0.5#30/30")
                put("ticker", "AMOC")
                put("outcome", "STOPPED")
                put("stopped_after_partial", 0)
                put("window_complete", 1)
                put("sessions_elapsed", 3)
                put("sessions", "[]")
                put("settled_at", 1L)
            })
        }

        val store = LocalDataStore(context)
        val amoc = store.sessionsFrom("AMOC", LocalDate.of(2026, 8, 1)).map { it.date.toString() }

        assertEquals(listOf("2026-08-06", "2026-08-11"), amoc)
        // The volume is what separates a closed exchange from a stock nobody wanted that day.
        assertEquals(1, store.sessionsFrom("THIN", LocalDate.of(2026, 8, 1)).size)
        // Both are derived from the prices this just changed, and both re-derive on the next
        // refresh. Cleared only because something was actually removed.
        assertTrue(store.priceBreakDates()["AMOC"].isNullOrEmpty())
        assertTrue(store.settledCalls().isEmpty())
    }

    private fun session(
        ticker: String,
        date: String,
        high: Double,
        low: Double,
        close: Double,
        volume: Double,
    ) = ContentValues().apply {
        put("ticker", ticker)
        put("session_date", date)
        put("open", close)
        put("high", high)
        put("low", low)
        put("close", close)
        put("volume", volume)
        put("source", "Yahoo Finance")
    }

    /** The three tables the non-trading-day migration touches, as they shipped under schema 21. */
    private class Version21(context: Context) :
        SQLiteOpenHelper(context, LocalDataStore.DATABASE_NAME, null, 21) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS daily_prices (
                    ticker TEXT NOT NULL,
                    session_date TEXT NOT NULL,
                    open REAL,
                    high REAL,
                    low REAL,
                    close REAL,
                    volume REAL,
                    source TEXT,
                    PRIMARY KEY (ticker, session_date)
                )""",
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS price_events (
                    ticker TEXT NOT NULL,
                    session_date TEXT NOT NULL,
                    previous_close REAL NOT NULL,
                    opening_price REAL NOT NULL,
                    detected_at INTEGER NOT NULL,
                    PRIMARY KEY (ticker, session_date)
                )""",
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS settled_calls (
                    call_key TEXT PRIMARY KEY,
                    ticker TEXT NOT NULL,
                    outcome TEXT NOT NULL,
                    settled_on TEXT,
                    stopped_on TEXT,
                    stopped_after_partial INTEGER NOT NULL,
                    window_complete INTEGER NOT NULL,
                    peak_high REAL,
                    peak_on TEXT,
                    trough_low REAL,
                    trough_on TEXT,
                    return_pct REAL,
                    sessions_elapsed INTEGER NOT NULL,
                    sessions TEXT NOT NULL,
                    settled_at INTEGER NOT NULL
                )""",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
