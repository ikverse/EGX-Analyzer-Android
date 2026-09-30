package com.ikverse.egxanalyzer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ikverse.egxanalyzer.model.DayEvent
import com.ikverse.egxanalyzer.model.DayEventKind
import com.ikverse.egxanalyzer.model.SessionDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/**
 * The archive of what the market did, and the one behaviour that makes it trustworthy.
 *
 * Everything on the "what happened" card is derived on every recompute, so the stored copy can only
 * ever be *wrong*: it is the one part of the feature that carries state. The rule that keeps it
 * honest is that a session is written **whole** - deleted and rebuilt - so an event the derivation
 * no longer produces cannot linger beside its replacement. Most of this file is that rule, from
 * both directions.
 */
@RunWith(RobolectricTestRunner::class)
class SessionEventStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val session = LocalDate.of(2026, 8, 12)
    private val earlier = LocalDate.of(2026, 8, 11)

    private fun tradeEvent(ticker: String = "AMOC") = DayEvent(
        kind = DayEventKind.TRADE_TARGET2,
        ticker = ticker,
        channel = "source-one",
        positionId = "$ticker@2026-08-03",
        openedOn = LocalDate.of(2026, 8, 3),
        returnPct = 12.4,
    )

    private fun callEvent(ticker: String = "COMI", channel: String = "source-two") = DayEvent(
        kind = DayEventKind.CALL_IN_RANGE,
        ticker = ticker,
        channel = channel,
        positionId = "$ticker@2026-08-03",
        openedOn = LocalDate.of(2026, 8, 3),
        price = 10.25,
    )

    @Test
    fun `an event comes back exactly as it went in`() {
        val store = LocalDataStore(context)
        val digest = SessionDigest(session, listOf(tradeEvent(), callEvent()))

        store.saveSessionDigests(listOf(digest))

        val read = store.sessionEvents(session, session).single()
        assertEquals(session, read.date)
        assertEquals(digest.events.toSet(), read.events.toSet())
    }

    @Test
    fun `re-deriving a session replaces it rather than accumulating into it`() {
        // The heal case, which is the whole reason a session is written whole. A stock's prices are
        // refetched, the verdict behind an event comes undone, and the row it produced has to go
        // with it - not sit alongside whatever replaced it.
        val store = LocalDataStore(context)
        store.saveSessionDigests(listOf(SessionDigest(session, listOf(tradeEvent(), callEvent()))))

        store.saveSessionDigests(listOf(SessionDigest(session, listOf(callEvent()))))

        assertEquals(listOf(callEvent()), store.sessionEvents(session, session).single().events)
    }

    @Test
    fun `a session that yields nothing is cleared, not left as it was`() {
        val store = LocalDataStore(context)
        store.saveSessionDigests(listOf(SessionDigest(session, listOf(tradeEvent()))))

        store.saveSessionDigests(listOf(SessionDigest(session, emptyList())))

        // Absence is the record of a quiet session: nothing is written back, and the rows that no
        // longer describe anything have gone.
        assertTrue(store.sessionEvents(session, session).isEmpty())
    }

    @Test
    fun `writing one session leaves the sessions around it alone`() {
        val store = LocalDataStore(context)
        store.saveSessionDigests(
            listOf(
                SessionDigest(session, listOf(tradeEvent())),
                SessionDigest(earlier, listOf(callEvent())),
            ),
        )

        store.saveSessionDigests(listOf(SessionDigest(session, emptyList())))

        assertEquals(listOf(callEvent()), store.sessionEvents(earlier, earlier).single().events)
    }

    @Test
    fun `sessions read back newest first`() {
        val store = LocalDataStore(context)
        store.saveSessionDigests(
            listOf(
                SessionDigest(earlier, listOf(callEvent())),
                SessionDigest(session, listOf(tradeEvent())),
            ),
        )

        assertEquals(
            listOf(session, earlier),
            store.sessionEvents(earlier, session).map(SessionDigest::date),
        )
    }

    @Test
    fun `two channels calling one stock on one session keep both rows`() {
        // The key carries the channel for exactly this. Keyed on the holding the two would share
        // one primary key and the second would silently overwrite the first.
        val store = LocalDataStore(context)
        val one = callEvent(channel = "source-two")
        val two = callEvent(channel = "source-three")

        store.saveSessionDigests(listOf(SessionDigest(session, listOf(one, two))))

        assertEquals(setOf(one, two), store.sessionEvents(session, session).single().events.toSet())
    }

    @Test
    fun `a row this build cannot name is dropped rather than read as some other event`() {
        val store = LocalDataStore(context)
        store.saveSessionDigests(listOf(SessionDigest(session, listOf(tradeEvent()))))
        store.writableDatabase.insertWithOnConflict(
            "session_events",
            null,
            ContentValues().apply {
                put("session_date", session.toString())
                put("event_id", "TRADE_SOMETHING_NEW@AMOC@2026-08-03")
                put("kind", "TRADE_SOMETHING_NEW")
                put("ticker", "AMOC")
                put("position_id", "AMOC@2026-08-03")
                put("opened_on", "2026-08-03")
                put("recorded_at", 0L)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )

        // A downgrade that cannot name an event must not quietly file it as one it does know.
        assertEquals(listOf(tradeEvent()), store.sessionEvents(session, session).single().events)
    }

    @Test
    fun `upgrading from 20 gains the table and keeps the trades already on the phone`() {
        // Version 20 as it shipped, written by hand: a test that builds its "old" schema from
        // today's code tests nothing, because both sides move together.
        Version20(context).writableDatabase.use { old ->
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
        store.saveSessionDigests(listOf(SessionDigest(session, listOf(tradeEvent()))))

        assertEquals(listOf(tradeEvent()), store.sessionEvents(session, session).single().events)
        // The risk on an upgrade is never that it fails outright - it is that it takes the answers
        // already on the phone with it.
        assertEquals("AMOC@2026-08-03", store.positions().single().id)
    }

    private class Version20(context: Context) :
        SQLiteOpenHelper(context, LocalDataStore.DATABASE_NAME, null, 20) {

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

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    }
}
