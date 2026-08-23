package com.ikverse.egxanalyzer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.JobTrigger
import com.ikverse.egxanalyzer.model.JobWork
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.ScheduledJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * That a schedule survives being written down, and that adding the table costs nobody their record.
 *
 * The migration half matters for the same reason [LocalDataStoreMigrationTest] does: a fresh
 * install builds the table from scratch and is always right, so a version bump that goes wrong
 * only ever breaks on a phone that has been running the app for months.
 */
@RunWith(RobolectricTestRunner::class)
class ScheduledJobStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val evenings = ScheduledJob(
        id = "prices",
        name = "Evening prices",
        enabled = true,
        trigger = JobTrigger.Repeat(ScheduleClock.tradingDays, LocalTime.of(18, 0)),
        work = JobWork.PriceRefresh,
        graceMinutes = 90,
        createdAt = Instant.ofEpochMilli(1_760_000_000_000),
    )

    @Test
    fun `a repeating schedule comes back exactly as it went in`() {
        val store = LocalDataStore(context)
        store.saveScheduledJob(evenings)

        assertEquals(evenings, store.scheduledJobs().single())
    }

    @Test
    fun `a periodic schedule keeps its step and both ends of its window`() {
        val store = LocalDataStore(context)
        val periodic = evenings.copy(
            id = "session-prices",
            name = "Session prices",
            trigger = JobTrigger.Interval(
                days = ScheduleClock.tradingDays,
                everyMinutes = 15,
                from = ScheduleClock.sessionStart,
                until = ScheduleClock.sessionEnd,
            ),
        )
        store.saveScheduledJob(periodic)

        assertEquals(periodic, store.scheduledJobs().single())
    }

    @Test
    fun `a schedule written before intervals existed still reads as the daily job it was`() {
        // The half a migration can get wrong. The two new columns default to a step of nothing and
        // an empty window, and a REPEAT row must go on ignoring both rather than coming back as an
        // interval that fires from midnight to midnight.
        Version16(context).writableDatabase.use { old ->
            old.insert("scheduled_jobs", null, version16Job())
        }

        val store = LocalDataStore(context)

        assertEquals(
            JobTrigger.Repeat(ScheduleClock.tradingDays, LocalTime.of(18, 0)),
            store.scheduledJobs().single().trigger,
        )
        // And the upgraded table can hold the new shape, which is the other half.
        val periodic = evenings.copy(
            id = "session-prices",
            trigger = JobTrigger.Interval(
                days = setOf(DayOfWeek.SUNDAY),
                everyMinutes = 30,
                from = LocalTime.of(10, 0),
                until = LocalTime.of(14, 45),
            ),
        )
        store.saveScheduledJob(periodic)
        assertEquals(periodic, store.scheduledJobs().first { it.id == "session-prices" })
    }

    private fun version16Job() = ContentValues().apply {
        put("id", "prices")
        put("name", "Evening prices")
        put("enabled", 1)
        put("trigger_kind", "REPEAT")
        put("trigger_at", "18:00")
        put("trigger_days", "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY")
        put("work_kind", "PRICE_REFRESH")
        put("work_config", "{}")
        put("grace_minutes", 90)
        put("last_outcome", "NEVER")
        put("created_at", 1_760_000_000_000)
        put("armed_at", 1_760_000_000_000)
    }

    @Test
    fun `a one-shot keeps its date as well as its time`() {
        val store = LocalDataStore(context)
        val once = evenings.copy(
            id = "one-off",
            trigger = JobTrigger.Once(LocalDateTime.parse("2026-08-21T06:00")),
        )
        store.saveScheduledJob(once)

        assertEquals(once, store.scheduledJobs().single())
    }

    @Test
    fun `what a run made of the job is stored with it`() {
        val store = LocalDataStore(context)
        val served = evenings.copy(
            lastFiredAt = Instant.ofEpochMilli(1_760_100_000_000),
            lastOutcome = JobOutcome.SUCCEEDED,
            lastMessage = "Priced 42/45",
        )
        store.saveScheduledJob(served)

        val restored = store.scheduledJobs().single()
        assertEquals(Instant.ofEpochMilli(1_760_100_000_000), restored.lastFiredAt)
        assertEquals(JobOutcome.SUCCEEDED, restored.lastOutcome)
        assertEquals("Priced 42/45", restored.lastMessage)
    }

    @Test
    fun `saving a job again replaces it rather than adding a second`() {
        val store = LocalDataStore(context)
        store.saveScheduledJob(evenings)
        store.saveScheduledJob(evenings.copy(enabled = false, name = "Paused"))

        val only = store.scheduledJobs().single()
        assertEquals("Paused", only.name)
        assertTrue(!only.enabled)
    }

    @Test
    fun `the days a schedule runs on survive the trip`() {
        val store = LocalDataStore(context)
        val scattered = evenings.copy(
            trigger = JobTrigger.Repeat(
                setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY),
                LocalTime.of(7, 5),
            ),
        )
        store.saveScheduledJob(scattered)

        val trigger = store.scheduledJobs().single().trigger as JobTrigger.Repeat
        assertEquals(setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY), trigger.days)
        assertEquals(LocalTime.of(7, 5), trigger.at)
    }

    @Test
    fun `an analysis job keeps the chats and content types it was made with`() {
        val store = LocalDataStore(context)
        val analysis = evenings.copy(
            id = "morning-analysis",
            name = "Morning analysis",
            work = JobWork.Analysis(
                channels = listOf(AnalysedChannel(-100L, "Signals"), AnalysedChannel(-200L, "Calls")),
                contentTypes = setOf(AnalysisContentType.IMAGES, AnalysisContentType.TEXT),
            ),
        )
        store.saveScheduledJob(analysis)

        // The whole point of freezing them: this is what the run will cover months from now,
        // whatever is ticked on the Analyze screen by then.
        assertEquals(analysis, store.scheduledJobs().single())
    }

    @Test
    fun `an analysis job with nothing to read is not treated as an analysis of nothing`() {
        val store = LocalDataStore(context)
        store.writableDatabase.insert(
            "scheduled_jobs",
            null,
            ContentValues().apply {
                put("id", "empty")
                put("name", "Broken analysis")
                put("enabled", 1)
                put("trigger_kind", "REPEAT")
                put("trigger_at", "07:00")
                put("trigger_days", "SUNDAY")
                put("work_kind", "ANALYSIS")
                put("work_config", """{"channels":[],"contentTypes":[]}""")
                put("grace_minutes", 60)
                put("last_outcome", "NEVER")
                put("created_at", 1_760_000_000_000)
            },
        )

        // A paid request over no chats buys an empty answer. Unsupported means it is kept, shown
        // and never run, which is the right end for a row that cannot say what it covers.
        val restored = store.scheduledJobs().single()
        assertTrue(restored.work is JobWork.Unsupported)
        assertTrue(!restored.runnable)
    }

    @Test
    fun `a job from a newer build is kept, shown, and never rewritten into something else`() {
        val store = LocalDataStore(context)
        store.writableDatabase.insert(
            "scheduled_jobs",
            null,
            ContentValues().apply {
                put("id", "from-the-future")
                put("name", "Morning analysis")
                put("enabled", 1)
                put("trigger_kind", "REPEAT")
                put("trigger_at", "07:00")
                put("trigger_days", "SUNDAY,MONDAY")
                put("work_kind", "ANALYSIS")
                put("work_config", """{"channels":[1,2]}""")
                put("grace_minutes", 60)
                put("last_outcome", "NEVER")
                put("created_at", 1_760_000_000_000)
            },
        )

        // A newer build's idea of what an analysis job carries. This one knows the kind and cannot
        // read the settings, which comes to the same thing: it must not run it.
        val unknown = store.scheduledJobs().single()
        assertEquals(JobWork.Unsupported("ANALYSIS", """{"channels":[1,2]}"""), unknown.work)
        assertTrue(!unknown.runnable)

        // Switching it off - the one thing this build can usefully do with it - must not file it
        // under a kind, or a shape, that the build which understands it would no longer recognise.
        // Rewriting the settings as an empty object here would gut the schedule on a downgrade and
        // a re-upgrade, and the row would come back looking intact.
        store.saveScheduledJob(unknown.copy(enabled = false))
        assertEquals(
            JobWork.Unsupported("ANALYSIS", """{"channels":[1,2]}"""),
            store.scheduledJobs().single().work,
        )
    }

    @Test
    fun `one unreadable row does not take the rest of the list with it`() {
        val store = LocalDataStore(context)
        store.saveScheduledJob(evenings)
        store.writableDatabase.insert(
            "scheduled_jobs",
            null,
            ContentValues().apply {
                put("id", "broken")
                put("name", "Nonsense")
                put("enabled", 1)
                put("trigger_kind", "WHENEVER")
                put("trigger_at", "not a time")
                put("work_kind", "PRICE_REFRESH")
                put("grace_minutes", 60)
                put("last_outcome", "NEVER")
                put("created_at", 1_760_000_100_000)
            },
        )

        // The screen that lists schedules has to survive one bad row, or a single unparseable
        // record takes away the only place the user could have deleted it from.
        assertEquals(listOf("prices"), store.scheduledJobs().map(ScheduledJob::id))
    }

    @Test
    fun `deleting a schedule leaves the others alone`() {
        val store = LocalDataStore(context)
        store.saveScheduledJob(evenings)
        store.saveScheduledJob(evenings.copy(id = "second", name = "Morning prices"))

        store.deleteScheduledJob("prices")

        assertEquals(listOf("second"), store.scheduledJobs().map(ScheduledJob::id))
    }

    @Test
    fun `a phone that had trades before schedules existed keeps them and gains the table`() {
        Version13(context).writableDatabase.use { old ->
            old.insert("positions", null, version13Position())
        }

        val store = LocalDataStore(context)

        // The upgrade added a table; it must not have cost the record that was already there.
        assertEquals("AMOC@2026-07-20", store.positions().single().id)
        store.saveScheduledJob(evenings)
        assertEquals(evenings, store.scheduledJobs().single())
    }

    private fun version13Position() = ContentValues().apply {
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
     * The database as version 13 left it, which is the version every phone is on before this one.
     *
     * Only the table this test needs to find intact afterwards. Written out here rather than taken
     * from today's code for the reason [LocalDataStoreMigrationTest] gives: a migration test whose
     * old shape is generated by the new code is testing nothing, because both sides move together.
     */
    private class Version13(context: Context) :
        SQLiteOpenHelper(context, LocalDataStore.DATABASE_NAME, null, 13) {

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
     * `scheduled_jobs` as version 16 left it: three trigger columns and no window.
     *
     * Spelled out rather than taken from today's `createScheduledJobs`, for the reason the class
     * above gives - an old shape generated by the new code moves whenever the new code does, and
     * then proves nothing about the phones actually holding the old one.
     */
    private class Version16(context: Context) :
        SQLiteOpenHelper(context, LocalDataStore.DATABASE_NAME, null, 16) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS scheduled_jobs (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    trigger_kind TEXT NOT NULL,
                    trigger_at TEXT NOT NULL,
                    trigger_days TEXT NOT NULL DEFAULT '',
                    work_kind TEXT NOT NULL,
                    work_config TEXT NOT NULL DEFAULT '{}',
                    grace_minutes INTEGER NOT NULL,
                    last_fired_at INTEGER,
                    last_outcome TEXT NOT NULL DEFAULT 'NEVER',
                    last_message TEXT,
                    created_at INTEGER NOT NULL DEFAULT 0,
                    armed_at INTEGER NOT NULL DEFAULT 0
                )""",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
