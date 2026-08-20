package com.ikverse.egxanalyzer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ikverse.egxanalyzer.model.StockOpinion
import com.ikverse.egxanalyzer.model.opinionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/**
 * That an opinion survives being written down, and that it goes when its report does.
 *
 * The deletion half is the point. An opinion is about one card on one report; delete the report and
 * the card is gone, so an opinion left behind is a row nothing will ever show and nothing will ever
 * clean up - and it would come back the day that session was re-run, attached to a card whose
 * levels nobody has checked it against.
 */
@RunWith(RobolectricTestRunner::class)
class StockOpinionStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val call = opinionId("ABUK", LocalDate.parse("2026-08-11"), "EGX Signals")

    private val opinion = StockOpinion(
        verdict = StockOpinion.Verdict.WAIT,
        horizon = StockOpinion.Horizon.SHORT,
        confidence = StockOpinion.Confidence.MEDIUM,
        headline = "الفرصة فاتت عند هذه المستويات",
        outlook = "سهم توزيعات محكوم بسعر اليوريا وسعر الغاز.",
        onTheCall = StockOpinion.CallView(
            stance = StockOpinion.Stance.OVERTAKEN,
            detail = "المستويات كانت معقولة وقت النشر.",
        ),
        unknowns = listOf("آخر نتائج أعمال معلنة", "أحجام التداول"),
        model = "qwen-plus",
        askedOn = LocalDate.parse("2026-08-20"),
        searched = true,
    )

    private fun LocalDataStore.save(id: String = call, requestId: String = "run-1") =
        saveStockOpinion(
            id = id,
            requestId = requestId,
            ticker = "ABUK",
            openedOn = LocalDate.parse("2026-08-11"),
            channel = "EGX Signals",
            opinion = opinion,
        )

    @Test
    fun `an opinion comes back exactly as it went in`() {
        val store = LocalDataStore(context)
        store.save()

        assertEquals(opinion, store.stockOpinions()[call])
    }

    /**
     * Two channels calling one stock on one session are two cards printing different levels, so an
     * opinion on one is not an opinion on the other.
     */
    @Test
    fun `two channels on one stock and one session keep separate opinions`() {
        val store = LocalDataStore(context)
        val other = opinionId("ABUK", LocalDate.parse("2026-08-11"), "Another Channel")
        store.save()
        store.save(id = other)

        assertEquals(2, store.stockOpinions().size)
    }

    @Test
    fun `asking again replaces the answer rather than adding a second`() {
        val store = LocalDataStore(context)
        store.save()
        store.saveStockOpinion(
            id = call,
            requestId = "run-1",
            ticker = "ABUK",
            openedOn = LocalDate.parse("2026-08-11"),
            channel = "EGX Signals",
            opinion = opinion.copy(verdict = StockOpinion.Verdict.AVOID),
        )

        assertEquals(StockOpinion.Verdict.AVOID, store.stockOpinions().getValue(call).verdict)
    }

    @Test
    fun `deleting the report by request id takes its opinions with it`() {
        val store = LocalDataStore(context)
        store.save()

        store.deleteResultByRequestId("run-1")

        assertNull(store.stockOpinions()[call])
    }

    /**
     * The path the ⋮ menu actually takes, which knows the row id and not the request id.
     *
     * It has to read the request id back *before* the row goes, and getting that order wrong is a
     * cascade that silently deletes nothing while every test on the other path still passes.
     */
    @Test
    fun `deleting the report by row id takes its opinions with it`() {
        val store = LocalDataStore(context)
        val id = store.writableDatabase.insert(
            "analyses",
            null,
            ContentValues().apply {
                put("request_id", "run-1")
                put("provider", "QWEN")
                put("model", "qwen3.5-omni-plus")
                put("completed_at", "2026-08-11T09:00:00Z")
                put("payload", "{}")
            },
        )
        store.save()

        store.deleteResult(id)

        assertNull(store.stockOpinions()[call])
    }

    @Test
    fun `an opinion belonging to another report is left alone`() {
        val store = LocalDataStore(context)
        val other = opinionId("AMOC", LocalDate.parse("2026-08-11"), "EGX Signals")
        store.save()
        store.save(id = other, requestId = "run-2")

        store.deleteResultByRequestId("run-1")

        assertEquals(setOf(other), store.stockOpinions().keys)
    }

    @Test
    fun `deleting every report leaves no opinion behind`() {
        val store = LocalDataStore(context)
        store.save()

        store.deleteAllResults()

        assertTrue(store.stockOpinions().isEmpty())
    }

    /**
     * A verdict this build does not know is dropped rather than defaulted.
     *
     * The alternative is a card colouring an answer it cannot read, which is the one outcome worse
     * than the button appearing unpressed.
     */
    @Test
    fun `a row this build cannot read does not take the rest with it`() {
        val store = LocalDataStore(context)
        store.save()
        store.writableDatabase.insert(
            "stock_opinions",
            null,
            ContentValues().apply {
                put("id", "FROM@2026-08-11@The Future")
                put("request_id", "run-1")
                put("ticker", "FROM")
                put("opened_on", "2026-08-11")
                put("channel", "The Future")
                put("verdict", "BUY_LATER")
                put("horizon", "SHORT")
                put("confidence", "HIGH")
                put("headline", "")
                put("outlook", "…")
                put("stance", "SOUND")
                put("stance_detail", "")
                put("unknowns", "[]")
                put("model", "qwen-plus")
                put("asked_on", "2026-08-20")
                put("searched", 1)
            },
        )

        assertEquals(setOf(call), store.stockOpinions().keys)
    }

    @Test
    fun `a phone that had trades before Ask AI existed keeps them and gains the table`() {
        Version14(context).writableDatabase.use { old ->
            old.insert("positions", null, version14Position())
        }

        val store = LocalDataStore(context)

        // The upgrade added a table; it must not have cost the record already on the phone.
        assertEquals("AMOC@2026-07-20", store.positions().single().id)
        store.save()
        assertEquals(opinion, store.stockOpinions()[call])
    }

    private fun version14Position() = ContentValues().apply {
        put("id", "AMOC@2026-07-20")
        put("ticker", "AMOC")
        put("recommendation_date", "2026-07-20")
        put("entry_price", 10.5)
        put("entry_date", "2026-07-21")
        put("window_sessions", 10)
        put("opened_at", 1_753_000_000_000)
        put("updated_at", 1_753_000_000_000)
        put("updated_by", "phone")
        put("deleted", 0)
    }

    /**
     * The database as version 14 left it, which is the version every phone is on before this one.
     *
     * Written out by hand rather than taken from today's code, for the reason
     * [LocalDataStoreMigrationTest] gives: a migration test whose "old" shape is generated by the
     * new code tests nothing, because both sides move together.
     */
    private class Version14(context: Context) :
        SQLiteOpenHelper(context, LocalDataStore.DATABASE_NAME, null, 14) {

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
                    keep_open_note TEXT,
                    unknown TEXT NOT NULL DEFAULT '{}',
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
     * The findings survive the round trip, tone and dates included.
     *
     * They are stored as JSON in a column each rather than as columns, so nothing but this test
     * stands between a reordered enum or a renamed key and a sheet that quietly prints every item
     * as neutral.
     */
    @Test
    fun `the news catalysts and risks come back exactly as they went in`() {
        val store = LocalDataStore(context)
        val rich = opinion.copy(
            news = listOf(
                StockOpinion.NewsItem(
                    headline = "أرباح الربع الثاني تقفز 22%",
                    date = "2026-08-12",
                    source = "مباشر",
                    tone = StockOpinion.Tone.BULLISH,
                ),
            ),
            catalysts = listOf(
                StockOpinion.Catalyst(
                    what = "قسيمة التوزيع",
                    on = "2026-09-14",
                    source = "إفصاح البورصة",
                ),
            ),
            risks = listOf("سيولة ضعيفة"),
            newsWindowDays = 15,
        )
        store.saveStockOpinion(
            id = call,
            requestId = "run-1",
            ticker = "ABUK",
            openedOn = LocalDate.parse("2026-08-11"),
            channel = "EGX Signals",
            opinion = rich,
        )

        assertEquals(rich, store.stockOpinions()[call])
    }

    /**
     * A phone holding opinions from before they carried findings keeps every one of them.
     *
     * The columns arrive by ALTER, so the risk is not that the upgrade fails - it is that it takes
     * the answers already on the phone with it, and an opinion cannot be re-asked for free.
     */
    @Test
    fun `an opinion saved before findings existed survives the upgrade`() {
        Version15(context).writableDatabase.use { old ->
            old.insert("stock_opinions", null, version15Opinion())
        }

        val store = LocalDataStore(context)
        val restored = store.stockOpinions().getValue(call)

        assertEquals(StockOpinion.Verdict.WAIT, restored.verdict)
        assertEquals("الفرصة فاتت عند هذه المستويات", restored.headline)
        // Nothing was ever searched for, so nothing is claimed to have been found - and the window
        // is zero, which is what stops the sheet saying "nothing in the last 0 days".
        assertTrue(restored.news.isEmpty())
        assertTrue(restored.catalysts.isEmpty())
        assertTrue(restored.risks.isEmpty())
        assertEquals(0, restored.newsWindowDays)
        // The list it did carry is untouched by the upgrade.
        assertEquals(listOf("آخر نتائج أعمال معلنة"), restored.unknowns)
    }

    private fun version15Opinion() = ContentValues().apply {
        put("id", call)
        put("request_id", "run-1")
        put("ticker", "ABUK")
        put("opened_on", "2026-08-11")
        put("channel", "EGX Signals")
        put("verdict", "WAIT")
        put("horizon", "SHORT")
        put("confidence", "MEDIUM")
        put("headline", "الفرصة فاتت عند هذه المستويات")
        put("outlook", "سهم توزيعات محكوم بسعر اليوريا وسعر الغاز.")
        put("stance", "OVERTAKEN")
        put("stance_detail", "المستويات كانت معقولة وقت النشر.")
        put("unknowns", """["آخر نتائج أعمال معلنة"]""")
        put("model", "qwen-plus")
        put("asked_on", "2026-08-20")
        put("searched", 0)
    }

    /**
     * The opinions table as version 15 wrote it - no news, no catalysts, no risks, no window.
     *
     * Written by hand for the reason [Version14] is: an "old" schema generated from today's code
     * moves whenever today's code moves, so it can never catch the migration going wrong.
     */
    private class Version15(context: Context) :
        SQLiteOpenHelper(context, LocalDataStore.DATABASE_NAME, null, 15) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS stock_opinions (
                    id TEXT PRIMARY KEY,
                    request_id TEXT NOT NULL,
                    ticker TEXT NOT NULL,
                    opened_on TEXT NOT NULL,
                    channel TEXT NOT NULL,
                    verdict TEXT NOT NULL,
                    horizon TEXT NOT NULL,
                    confidence TEXT NOT NULL,
                    headline TEXT NOT NULL,
                    outlook TEXT NOT NULL,
                    stance TEXT NOT NULL,
                    stance_detail TEXT NOT NULL,
                    unknowns TEXT NOT NULL DEFAULT '[]',
                    model TEXT NOT NULL,
                    asked_on TEXT NOT NULL,
                    searched INTEGER NOT NULL DEFAULT 0
                )""",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
