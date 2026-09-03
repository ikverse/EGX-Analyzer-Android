package com.ikverse.egxanalyzer.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ikverse.egxanalyzer.model.FeedFault
import com.ikverse.egxanalyzer.model.PriceHealthReport
import com.ikverse.egxanalyzer.model.StockHealth
import com.ikverse.egxanalyzer.model.WordingRule
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.PromptVersion
import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.AnalysisDiagnostics
import com.ikverse.egxanalyzer.model.UnaccountedImage
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.cleanChannelName
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.IntradayBar
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.Quote
import com.ikverse.egxanalyzer.model.CallState
import com.ikverse.egxanalyzer.model.TradeState
import com.ikverse.egxanalyzer.model.RecommendationResult
import com.ikverse.egxanalyzer.model.ExcludedSource
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.SettledCall
import com.ikverse.egxanalyzer.model.DayEvent
import com.ikverse.egxanalyzer.model.DayEventKind
import com.ikverse.egxanalyzer.model.ApproachState
import com.ikverse.egxanalyzer.model.SessionDigest
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.SourceTrace
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.ikverse.egxanalyzer.model.StockOpinion

/**
 * The record on this device, and - under another [name] - the record inside a backup.
 *
 * [name] exists so a backup is read by this class rather than by a second reader written beside it.
 * A backup holds the schema of whatever version wrote it, and opening it here runs [onUpgrade] over
 * it exactly as an app update would, so a restore from a months-old file reads through today's
 * columns instead of failing on the ones it predates. A hand-written reader would have to carry its
 * own copy of every migration, and would be wrong the first time one was added and not copied.
 */
class LocalDataStore(context: Context, name: String = DATABASE_NAME) :
    SQLiteOpenHelper(context, name, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE channels (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                selected INTEGER NOT NULL DEFAULT 1
            )""",
        )
        db.execSQL(
            """CREATE TABLE stocks (
                ticker TEXT PRIMARY KEY,
                name_en TEXT NOT NULL,
                name_ar TEXT
            )""",
        )
        db.execSQL(
            """CREATE TABLE analyses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                request_id TEXT NOT NULL UNIQUE,
                provider TEXT NOT NULL,
                model TEXT NOT NULL,
                completed_at TEXT NOT NULL,
                payload TEXT NOT NULL
            )""",
        )
        db.createDailyPrices()
        db.createPriceEvents()
        db.createIntradayBars()
        db.createIntradayFetches()
        db.createPendingDeletions()
        db.createWordingRules()
        db.createPromptVersions()
        db.createPositions()
        db.createPositionStatusSeen()
        db.createStockOpinions()
        db.createCallAlertSeen()
        db.createApproachSeen()
        db.createSettledCalls()
        db.createSessionEvents()
        db.createSessionDigestAnnounced()
        db.createFeedChecks()
        db.createFeedFaults()
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS stocks (
                ticker TEXT PRIMARY KEY,
                name_en TEXT NOT NULL,
                name_ar TEXT
            )""",
        )
        db.createDailyPrices()
        db.createPriceEvents()
        db.createIntradayBars()
        db.createIntradayFetches()
        db.createPendingDeletions()
        db.createWordingRules()
        db.createPromptVersions()
        db.createPositions()
        db.createPositionStatusSeen()
        db.addPositionRevisionColumns()
        db.addPositionWindowColumns()
        db.addPositionTimingColumn()
        db.addPositionSplitExitColumns()
        db.addOpenColumn()
        db.createStockOpinions()
        db.addOpinionDetailColumns()
        db.createCallAlertSeen()
        db.createApproachSeen()
        db.createSettledCalls()
        db.createSessionEvents()
        db.createSessionDigestAnnounced()
        db.createFeedChecks()
        db.createFeedFaults()
        db.dropNonTradingSessions()
    }

    /**
     * Clears out the days the exchange was shut that were stored as sessions.
     *
     * Yahoo does not omit an EGX holiday: it answers with a flat row at the previous close and no
     * volume, and until the parse learned to drop those, every one of them went in as an ordinary
     * session. A session that never happened is still a session to everything that counts them - so
     * a call's window was short by however many fell inside it, a T+1 posted the day before one
     * spent its entire sell side on a day nothing could trade, and every "sessions to a target"
     * figure on a channel card was inflated by the holidays it ran through.
     *
     * Here as well as in the parse because the parse only guards what arrives next. The refresh
     * re-asks for three days and the heal that would rewrite a whole history fires only on a
     * detected change of scale, so rows already on disk would have sat there for good.
     *
     * **Gated on having actually removed something, not on a version number.** The delete is a
     * no-op once it has run, which makes it safe on every upgrade; the two clears below are not, so
     * they are reached only when this really did change the prices underneath them.
     */
    private fun SQLiteDatabase.dropNonTradingSessions() {
        val removed = delete(
            "daily_prices",
            "close IS NOT NULL AND high = low AND low = close AND (volume IS NULL OR volume <= 0)",
            null,
        )
        if (removed == 0) return
        // Both are derived from the prices this just changed, and both re-derive on the next
        // refresh. A break is a comparison between two sessions, and some of the pairs it was
        // measured across were a real session and a closed day - which is how a stock quoting a
        // frozen price all year read as a change of scale on the morning its feed came back.
        delete("price_events", null, null)
        // A frozen verdict carries the sessions it was judged on, and those sessions included days
        // the market was shut. Dropped rather than kept: re-scoring is the point of this.
        delete("settled_calls", null, null)
    }

    /**
     * Refuses a database written by a newer build, rather than crashing inside a query.
     *
     * Only a restore can reach this: the app's own database is never older than the app. A backup
     * taken on a phone running a later version can be, and the default behaviour - an exception
     * naming version numbers - reaches the user as a failure that reads like a corrupt file. The
     * file is fine; this build is simply behind it, and saying so is the difference between someone
     * updating the app and someone deleting the only copy of their record.
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw BackupTooNewException(oldVersion, newVersion)
    }

    /**
     * Brings an opinions table written before an answer carried its findings up to date.
     *
     * Asked for one column at a time, exactly as the positions table learned to be: a build that
     * ships two of four leaves phones holding two, and a single guard over the first would decide
     * the other three had arrived. The defaults are what an opinion given before any of this
     * existed actually had - nothing found, because nothing was ever asked for.
     */
    private fun SQLiteDatabase.addOpinionDetailColumns() {
        val columns = rawQuery("PRAGMA table_info(stock_opinions)", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }.toSet()
        }
        if ("news" !in columns) {
            execSQL("ALTER TABLE stock_opinions ADD COLUMN news TEXT NOT NULL DEFAULT '[]'")
        }
        if ("catalysts" !in columns) {
            execSQL("ALTER TABLE stock_opinions ADD COLUMN catalysts TEXT NOT NULL DEFAULT '[]'")
        }
        if ("risks" !in columns) {
            execSQL("ALTER TABLE stock_opinions ADD COLUMN risks TEXT NOT NULL DEFAULT '[]'")
        }
        if ("news_window" !in columns) {
            execSQL("ALTER TABLE stock_opinions ADD COLUMN news_window INTEGER NOT NULL DEFAULT 0")
        }
        // Schema 3 of the prompt: how the stock has traded, where it goes over three spans, and the
        // printed numbers rated one at a time. The defaults are what an answer given before any of
        // them existed actually holds - a blank reading and two empty lists - rather than anything
        // this build could invent on its behalf.
        if ("standing" !in columns) {
            execSQL("ALTER TABLE stock_opinions ADD COLUMN standing TEXT NOT NULL DEFAULT ''")
        }
        if ("forecast" !in columns) {
            execSQL("ALTER TABLE stock_opinions ADD COLUMN forecast TEXT NOT NULL DEFAULT '[]'")
        }
        if ("checks" !in columns) {
            execSQL("ALTER TABLE stock_opinions ADD COLUMN checks TEXT NOT NULL DEFAULT '[]'")
        }
    }

    /**
     * The trades the user has actually taken.
     *
     * Keyed by its own id rather than by the recommendation: the trade is the thing being recorded,
     * and it has to survive the report it came from being deleted or re-run. Every level the trade
     * was taken on is copied in for the same reason.
     */
    private fun SQLiteDatabase.createPositions() = execSQL(
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
            exit_price_1 REAL,
            exit_date_1 TEXT,
            exit_price_2 REAL,
            exit_split_pct REAL,
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
            deleted INTEGER NOT NULL DEFAULT 0
        )""",
    )

    /**
     * Brings a positions table written before they could travel up to date.
     *
     * Positions arrived one version before they synced, so a device that ran that build has the
     * table without the three columns a revision needs. The ids are rewritten at the same time: they
     * were random then, and a random id is one no other device could ever have agreed with.
     */
    private fun SQLiteDatabase.addPositionRevisionColumns() {
        val columns = rawQuery("PRAGMA table_info(positions)", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }.toSet()
        }
        if ("updated_at" in columns) return
        execSQL("ALTER TABLE positions ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
        execSQL("ALTER TABLE positions ADD COLUMN updated_by TEXT NOT NULL DEFAULT ''")
        execSQL("ALTER TABLE positions ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
        // One holding per call, which is what the id says now. Nothing can collide: a second trade
        // on the same call always replaced the first.
        runCatching { execSQL("UPDATE positions SET id = ticker || '@' || recommendation_date") }
    }

    /**
     * Brings a positions table written before a trade could outlive its deadline up to date.
     *
     * Every column is checked for on its own, and that is the whole point. The first version of
     * this guarded all four behind "does `keep_open` exist", which is only correct if a device can
     * never hold some of them and not others - and a device can. A build that shipped two of these
     * under schema 10 left phones on schema 10 lacking the other two, so `onUpgrade` never ran
     * again and the columns could never arrive. Asking per column costs one query and cannot care
     * which build a phone happens to have come from.
     *
     * The defaults are not chosen for convenience: a trade recorded before any of this existed took
     * the window it was offered, closed when that ran out, and had nothing written against it by a
     * newer app.
     */
    private fun SQLiteDatabase.addPositionWindowColumns() {
        val columns = rawQuery("PRAGMA table_info(positions)", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }.toSet()
        }
        if ("window_custom" !in columns) {
            execSQL("ALTER TABLE positions ADD COLUMN window_custom INTEGER NOT NULL DEFAULT 0")
        }
        if ("keep_open" !in columns) {
            execSQL("ALTER TABLE positions ADD COLUMN keep_open INTEGER NOT NULL DEFAULT 0")
        }
        if ("keep_open_note" !in columns) {
            execSQL("ALTER TABLE positions ADD COLUMN keep_open_note TEXT")
        }
        if ("unknown" !in columns) {
            execSQL("ALTER TABLE positions ADD COLUMN unknown TEXT NOT NULL DEFAULT '{}'")
        }
    }

    /**
     * Brings a positions table written before a trade remembered it was a T+1 up to date.
     *
     * False for every trade already on the phone, and deliberately not guessed at here: a two
     * session window is what a T+1 is offered, but it is also what a reader who set their default
     * to two takes on every call, and a migration cannot tell those apart. What can tell them apart
     * is the record itself, which still holds the card each trade was taken on - so the backfill
     * runs where that record is built rather than here. See `AppState.recomputePerformance`.
     *
     * Its own function rather than a fifth line in [addPositionWindowColumns], whose columns all
     * arrived together in one build. This one did not, and a phone can hold those four without it.
     */
    private fun SQLiteDatabase.addPositionTimingColumn() {
        val hasColumn = rawQuery("PRAGMA table_info(positions)", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }
                .any { it == "is_t_plus_one" }
        }
        if (!hasColumn) {
            execSQL("ALTER TABLE positions ADD COLUMN is_t_plus_one INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * Brings a positions table written before a sale could be made in two parts up to date.
     *
     * Null on every trade already on the phone, which is what those sales were: one price, one day,
     * the whole holding. Nothing is backfilled and nothing needs to be - `exit_price` has always
     * held what the position closed at, and these four only say how that figure was made up on the
     * sales recorded from here on.
     *
     * Asked for one column at a time, for the reason the window columns are: a build that ships
     * some of them leaves phones holding some of them, and a single guard over the first would
     * decide the rest had arrived.
     */
    private fun SQLiteDatabase.addPositionSplitExitColumns() {
        val columns = rawQuery("PRAGMA table_info(positions)", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }.toSet()
        }
        if ("exit_price_1" !in columns) {
            execSQL("ALTER TABLE positions ADD COLUMN exit_price_1 REAL")
        }
        if ("exit_date_1" !in columns) {
            execSQL("ALTER TABLE positions ADD COLUMN exit_date_1 TEXT")
        }
        if ("exit_price_2" !in columns) {
            execSQL("ALTER TABLE positions ADD COLUMN exit_price_2 REAL")
        }
        if ("exit_split_pct" !in columns) {
            execSQL("ALTER TABLE positions ADD COLUMN exit_split_pct REAL")
        }
    }

    /** Every position this device holds, tombstoned ones excluded. */
    fun positions(): List<Position> = readableDatabase
        .query(
            "positions", null, "deleted = 0", null, null, null,
            "recommendation_date DESC, ticker ASC",
        )
        .use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    runCatching { cursor.toPosition() }.getOrNull()?.let(::add)
                }
            }
        }

    /**
     * Every position including the buried ones, with whatever a newer app added to each.
     *
     * The sync needs the tombstones: a delete that is not published is a delete the next device
     * undoes by uploading the position back. It needs the unknown fields for the same sort of
     * reason - re-uploading a revision without them is how a newer version's data gets erased by
     * an older one that merely looked at it.
     */
    fun positionRevisions(): List<PositionRevision> = readableDatabase
        .query("positions", null, null, null, null, null, "updated_at ASC")
        .use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    runCatching {
                        PositionRevision(
                            position = cursor.toPosition(),
                            deleted = cursor.getInt(cursor.getColumnIndexOrThrow("deleted")) == 1,
                            unknown = cursor.nullableString("unknown") ?: "{}",
                        )
                    }.getOrNull()?.let(::add)
                }
            }
        }

    /** Fields a newer app version wrote against one position, or an empty object for none. */
    fun unknownFor(id: String): String = readableDatabase.query(
        "positions",
        arrayOf("unknown"),
        "id = ?",
        arrayOf(id),
        null,
        null,
        null,
        "1",
    ).use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else "{}"
    }

    /** Writes a position exactly as the merge decided it, tombstone and unknown fields included. */
    fun adoptPosition(position: Position, deleted: Boolean, unknown: String = "{}") {
        savePosition(position, unknown)
        if (deleted) {
            writableDatabase.execSQL(
                "UPDATE positions SET deleted = 1 WHERE id = ?",
                arrayOf<Any>(position.id),
            )
        }
    }

    /**
     * Buries a position rather than removing the row.
     *
     * The same reason a deleted report leaves a marker: without one, the next device to sync sees
     * the position missing here and puts it back.
     */
    fun buryPosition(id: String, at: Long, by: String) {
        writableDatabase.execSQL(
            "UPDATE positions SET deleted = 1, updated_at = ?, updated_by = ? WHERE id = ?",
            arrayOf<Any>(at, by, id),
        )
    }

    /**
     * Writes a position, keeping whatever a newer app version had added to it.
     *
     * [unknown] is fields this build does not understand, carried through untouched. Without
     * storing them the passthrough only ever survived inside a single sync: the moment this device
     * edited a trade it published `{}` and silently stripped whatever a later version had written.
     */
    fun savePosition(position: Position, unknown: String = unknownFor(position.id)) {
        writableDatabase.insertWithOnConflict(
            "positions",
            null,
            ContentValues().apply {
                put("id", position.id)
                put("ticker", position.ticker)
                put("name_en", position.companyEnglish)
                put("name_ar", position.companyArabic)
                put("channel", position.channel)
                put("recommendation_date", position.recommendationDate.toString())
                put("entry_price", position.entryPrice)
                put("entry_date", position.entryDate.toString())
                put("exit_price", position.exitPrice)
                put("exit_date", position.exitDate?.toString())
                put("exit_price_1", position.exitPrice1)
                put("exit_date_1", position.exitDate1?.toString())
                put("exit_price_2", position.exitPrice2)
                put("exit_split_pct", position.exitSplitPct)
                put("closed_manually", if (position.closedManually) 1 else 0)
                put("entry_low", position.entryLow)
                put("entry_high", position.entryHigh)
                put("target1", position.target1)
                put("target2", position.target2)
                put("stop_loss", position.stopLoss)
                put("window_sessions", position.windowSessions)
                put("window_custom", if (position.windowCustom) 1 else 0)
                put("is_t_plus_one", if (position.isTPlusOne) 1 else 0)
                put("keep_open", if (position.keepOpen) 1 else 0)
                put("keep_open_note", position.keepOpenNote)
                put("unknown", unknown)
                put("opened_at", position.openedAt.toEpochMilli())
                put("updated_at", position.updatedAt)
                put("updated_by", position.updatedBy)
                put("deleted", 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun Cursor.toPosition() = Position(
        id = getString(getColumnIndexOrThrow("id")),
        ticker = getString(getColumnIndexOrThrow("ticker")),
        companyEnglish = nullableString("name_en"),
        companyArabic = nullableString("name_ar"),
        channel = nullableString("channel"),
        recommendationDate = LocalDate.parse(getString(getColumnIndexOrThrow("recommendation_date"))),
        entryPrice = getDouble(getColumnIndexOrThrow("entry_price")),
        entryDate = LocalDate.parse(getString(getColumnIndexOrThrow("entry_date"))),
        exitPrice = nullableDouble(getColumnIndexOrThrow("exit_price")),
        exitDate = nullableString("exit_date")?.let(LocalDate::parse),
        exitPrice1 = nullableDouble(getColumnIndexOrThrow("exit_price_1")),
        exitDate1 = nullableString("exit_date_1")?.let(LocalDate::parse),
        exitPrice2 = nullableDouble(getColumnIndexOrThrow("exit_price_2")),
        exitSplitPct = nullableDouble(getColumnIndexOrThrow("exit_split_pct")),
        closedManually = getInt(getColumnIndexOrThrow("closed_manually")) == 1,
        entryLow = nullableDouble(getColumnIndexOrThrow("entry_low")),
        entryHigh = nullableDouble(getColumnIndexOrThrow("entry_high")),
        target1 = nullableDouble(getColumnIndexOrThrow("target1")),
        target2 = nullableDouble(getColumnIndexOrThrow("target2")),
        stopLoss = nullableDouble(getColumnIndexOrThrow("stop_loss")),
        windowSessions = getInt(getColumnIndexOrThrow("window_sessions")),
        windowCustom = getInt(getColumnIndexOrThrow("window_custom")) == 1,
        isTPlusOne = getInt(getColumnIndexOrThrow("is_t_plus_one")) == 1,
        keepOpen = getInt(getColumnIndexOrThrow("keep_open")) == 1,
        keepOpenNote = nullableString("keep_open_note"),
        openedAt = Instant.ofEpochMilli(getLong(getColumnIndexOrThrow("opened_at"))),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        updatedBy = getString(getColumnIndexOrThrow("updated_by")),
    )

    private fun Cursor.nullableString(column: String): String? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }

    /**
     * The most recent close stored for a stock, with the session it closed on.
     *
     * The daily feed is the only thing that writes prices here, so "current" means the last session
     * that has settled rather than a live quote. A position's return moves once a day, deliberately:
     * a figure that changed while nothing had traded would be invented.
     *
     * The date comes back with the price rather than from [latestSessionDate], which answers a
     * different question: that one is the newest row stored, this one is the newest row that
     * actually carries a close. A session the feed knows about but could not price would put the
     * wrong date under the figure.
     */
    fun latestQuote(ticker: String): Quote? = readableDatabase.query(
        "daily_prices",
        arrayOf("close", "session_date"),
        "ticker = ? AND close IS NOT NULL AND close > 0",
        arrayOf(ticker),
        null,
        null,
        "session_date DESC",
        "1",
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val close = cursor.nullableDouble(0) ?: return@use null
        val on = runCatching { LocalDate.parse(cursor.getString(1)) }.getOrNull() ?: return@use null
        Quote(close, on)
    }

    /**
     * The newest session stored for one stock, which is where a fetch has to start from.
     *
     * The whole point of asking is that the answer is often not yesterday: a phone that was not
     * opened for a fortnight has a fortnight-shaped hole, and only this says how wide it is.
     */
    fun latestSessionDate(ticker: String): LocalDate? = readableDatabase.query(
        "daily_prices",
        arrayOf("session_date"),
        "ticker = ?",
        arrayOf(ticker),
        null,
        null,
        "session_date DESC",
        "1",
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        runCatching { LocalDate.parse(cursor.getString(0)) }.getOrNull()
    }

    /**
     * The wording rules, including the shipped ones once they have been changed.
     *
     * Only rows that differ from what the app ships are stored: a built-in rule nobody has touched
     * has nothing worth saving, and writing all of them would mean an app update could not retire
     * one. `deleted` is a tombstone rather than a real delete, so a removal survives a sync instead
     * of being undone by the next device that still holds the row.
     */
    private fun SQLiteDatabase.createWordingRules() = execSQL(
        """CREATE TABLE IF NOT EXISTS wording_rules (
            id TEXT PRIMARY KEY,
            slot TEXT NOT NULL,
            kind TEXT NOT NULL,
            phrase TEXT NOT NULL,
            scope TEXT NOT NULL,
            enabled INTEGER NOT NULL DEFAULT 1,
            origin TEXT NOT NULL,
            channels TEXT NOT NULL DEFAULT '',
            note TEXT,
            updated_at INTEGER NOT NULL DEFAULT 0,
            updated_by TEXT NOT NULL DEFAULT '',
            deleted INTEGER NOT NULL DEFAULT 0
        )""",
    )

    /**
     * Every prompt this device has generated, keyed by what generated it.
     *
     * The id is a hash of the shipped prompt and the rules folded into it, so the same
     * configuration is the same version everywhere and two devices never race for a number.
     */
    private fun SQLiteDatabase.createPromptVersions() = execSQL(
        """CREATE TABLE IF NOT EXISTS prompt_versions (
            id TEXT PRIMARY KEY,
            sequence INTEGER NOT NULL,
            text TEXT NOT NULL,
            schema_version INTEGER,
            rule_ids TEXT NOT NULL DEFAULT '',
            reason TEXT NOT NULL DEFAULT '',
            device TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL DEFAULT 0
        )""",
    )

    private fun SQLiteDatabase.createPendingDeletions() = execSQL(
        """CREATE TABLE IF NOT EXISTS pending_deletions (
            request_id TEXT PRIMARY KEY
        )""",
    )

    private fun SQLiteDatabase.createDailyPrices() = execSQL(
        // One row per stock per session. The unique key lets a refresh re-fetch the same days
        // without ever creating a second copy of a session that already exists.
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

    /**
     * The open is what settles a session that both offered the entry and reached the target: it
     * precedes every other price of the day. Sessions stored before this keep a null open and are
     * treated as unknown until the next price refresh rewrites them.
     */
    private fun SQLiteDatabase.addOpenColumn() {
        val hasOpen = rawQuery("PRAGMA table_info(daily_prices)", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }
                .any { it == "open" }
        }
        if (!hasOpen) execSQL("ALTER TABLE daily_prices ADD COLUMN open REAL")
    }

    /**
     * The sessions on which a stock's prices changed scale.
     *
     * Kept rather than recomputed on the way past, because scoring has to consult it on every
     * recompute and re-deriving it would mean reading a year of prices per stock to answer a
     * question whose answer changes about once a decade per company.
     *
     * Local, and deliberately not synced. Prices are fetched per device from the same public feed,
     * so each device reaches the same conclusion on its own; sending this through the sync channel
     * would put a device's opinion about a feed into another device's evidence.
     */
    private fun SQLiteDatabase.createPriceEvents() = execSQL(
        """CREATE TABLE IF NOT EXISTS price_events (
            ticker TEXT NOT NULL,
            session_date TEXT NOT NULL,
            previous_close REAL NOT NULL,
            opening_price REAL NOT NULL,
            detected_at INTEGER NOT NULL,
            PRIMARY KEY (ticker, session_date)
        )""",
    )

    /**
     * Records a change of scale, replacing any earlier reading of the same session.
     *
     * A break the table did not already hold re-opens every call settled on that stock. Scoring
     * consults the breaks, so a call frozen before one was found was judged on prices now known to
     * be in two different currencies - and the verdict it reached is exactly the phantom stop-out
     * the break exists to prevent. Only a **new** break does it: this is called with whatever the
     * last fetch found, so re-recording one already on disk says nothing has changed and must not
     * throw the record open every refresh.
     */
    fun savePriceBreaks(breaks: List<PriceBreak>) {
        if (breaks.isEmpty()) return
        val known = priceBreakDates()
        breaks.map(PriceBreak::ticker)
            .distinct()
            .filter { ticker ->
                breaks.any { it.ticker == ticker && it.date !in known[ticker].orEmpty() }
            }
            .forEach(::clearSettledCalls)
        writableDatabase.beginTransaction()
        try {
            breaks.forEach { event ->
                writableDatabase.insertWithOnConflict(
                    "price_events",
                    null,
                    ContentValues().apply {
                        put("ticker", event.ticker)
                        put("session_date", event.date.toString())
                        put("previous_close", event.previousClose)
                        put("opening_price", event.openingPrice)
                        put("detected_at", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /**
     * Forgets every change of scale recorded for one stock.
     *
     * Called before a healed series is stored: the whole history has just been refetched onto one
     * scale, so a break found against the old mixture is no longer a fact about what is on disk.
     */
    fun clearPriceBreaks(ticker: String) {
        writableDatabase.delete("price_events", "ticker = ?", arrayOf(ticker))
    }

    /**
     * Forgets every bar stored for one stock, and that they were ever fetched.
     *
     * Called on the same heal as [clearPriceBreaks]. A split rewrites the intraday history exactly
     * as it rewrites the daily one, so bars kept across one are in the old money while the levels
     * beside them are in the new - and ordering an entry against a target using the two would give
     * a confident answer built on prices that never belonged together.
     */
    fun clearIntraday(ticker: String) {
        writableDatabase.delete("intraday_bars", "ticker = ?", arrayOf(ticker))
        writableDatabase.delete("intraday_fetches", "ticker = ?", arrayOf(ticker))
    }

    /** Where the database actually sits, for the diagnostics copy that reads it off a device. */
    fun databaseFile(): File = File(writableDatabase.path)

    /**
     * Folds the write-ahead log back into the database file.
     *
     * Android opens this in write-ahead mode, so the newest commits live in a `-wal` sidecar until
     * something checkpoints them. Copying the database alone would hand over a record missing the
     * recent activity - which is precisely the part worth asking about. Best effort: a checkpoint
     * refused because something else holds the database still leaves a readable, if older, copy.
     */
    fun checkpoint() {
        runCatching {
            writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)
                .use { it.moveToFirst() }
        }
    }

    /** Every stock that has one, so a recompute reads them once rather than per call. */
    fun priceBreakDates(): Map<String, Set<LocalDate>> = readableDatabase
        .query("price_events", arrayOf("ticker", "session_date"), null, null, null, null, null)
        .use { cursor ->
            buildMap<String, MutableSet<LocalDate>> {
                while (cursor.moveToNext()) {
                    val date = runCatching { LocalDate.parse(cursor.getString(1)) }.getOrNull()
                        ?: continue
                    getOrPut(cursor.getString(0)) { mutableSetOf() }.add(date)
                }
            }
        }

    /**
     * Five-minute bars, kept only for the sessions a call could not order on its own.
     *
     * Deliberately not a series per stock: a year of five-minute bars for two hundred stocks is
     * millions of rows to answer a question that arises a handful of times. Only the extremes are
     * stored, because only "was this level touched inside this bar" is ever asked of them.
     *
     * A closed session's bars never change, so a row here is written once and never refreshed.
     */
    private fun SQLiteDatabase.createIntradayBars() {
        execSQL(
            """CREATE TABLE IF NOT EXISTS intraday_bars (
                ticker TEXT NOT NULL,
                session_date TEXT NOT NULL,
                bar_at INTEGER NOT NULL,
                high REAL,
                low REAL,
                PRIMARY KEY (ticker, bar_at)
            )""",
        )
        execSQL(
            "CREATE INDEX IF NOT EXISTS intraday_bars_session " +
                "ON intraday_bars (ticker, session_date)",
        )
    }

    /**
     * That a session's bars were asked for, and what came back.
     *
     * Without this a session the feed has nothing for is asked about again on every refresh,
     * forever - and the sessions with nothing to give are exactly the ones past the feed's 60-day
     * intraday retention, which accumulate. A row with no bars is an answer and is kept as one.
     */
    private fun SQLiteDatabase.createIntradayFetches() = execSQL(
        """CREATE TABLE IF NOT EXISTS intraday_fetches (
            ticker TEXT NOT NULL,
            session_date TEXT NOT NULL,
            fetched_at INTEGER NOT NULL,
            bars INTEGER NOT NULL,
            PRIMARY KEY (ticker, session_date)
        )""",
    )

    /**
     * Every stored bar, grouped by the stock and session it belongs to.
     *
     * Read whole rather than per call: only ambiguous sessions have bars at all, so this is a small
     * table, and a recompute would otherwise query it once for every call it scores.
     */
    fun intradayBars(): Map<Pair<String, LocalDate>, List<IntradayBar>> = readableDatabase
        .query(
            "intraday_bars",
            arrayOf("ticker", "session_date", "bar_at", "high", "low"),
            null, null, null, null, "ticker, bar_at",
        )
        .use { cursor ->
            buildMap<Pair<String, LocalDate>, MutableList<IntradayBar>> {
                while (cursor.moveToNext()) {
                    val date = runCatching { LocalDate.parse(cursor.getString(1)) }.getOrNull()
                        ?: continue
                    val ticker = cursor.getString(0)
                    getOrPut(ticker to date) { mutableListOf() }.add(
                        IntradayBar(
                            ticker = ticker,
                            at = Instant.ofEpochSecond(cursor.getLong(2)),
                            high = cursor.nullableDouble(3),
                            low = cursor.nullableDouble(4),
                        ),
                    )
                }
            }
        }

    /** The sessions already asked about, whether or not the feed had anything to say. */
    fun intradayFetched(): Set<Pair<String, LocalDate>> = readableDatabase
        .query("intraday_fetches", arrayOf("ticker", "session_date"), null, null, null, null, null)
        .use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    val date = runCatching { LocalDate.parse(cursor.getString(1)) }.getOrNull()
                        ?: continue
                    add(cursor.getString(0) to date)
                }
            }
        }

    /**
     * Stores one session's bars and the fact that it was asked for, in one transaction.
     *
     * The two have to land together: bars with no fetch row would be re-fetched forever, and a
     * fetch row with no bars where bars did arrive would read as a session the feed had nothing for.
     */
    fun saveIntradayBars(ticker: String, date: LocalDate, bars: List<IntradayBar>) {
        writableDatabase.beginTransaction()
        try {
            bars.forEach { bar ->
                writableDatabase.insertWithOnConflict(
                    "intraday_bars",
                    null,
                    ContentValues().apply {
                        put("ticker", ticker)
                        put("session_date", date.toString())
                        put("bar_at", bar.at.epochSecond)
                        put("high", bar.high)
                        put("low", bar.low)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            writableDatabase.insertWithOnConflict(
                "intraday_fetches",
                null,
                ContentValues().apply {
                    put("ticker", ticker)
                    put("session_date", date.toString())
                    put("fetched_at", System.currentTimeMillis())
                    put("bars", bars.size)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /**
     * The newest session stored for a stock, prices and all.
     *
     * The date alone is what a fetch window needs; a scale check needs the price beside it, because
     * the break it is looking for falls exactly between this session and the next one fetched.
     */
    fun latestSession(ticker: String): DailySession? = readableDatabase.query(
        "daily_prices",
        arrayOf("ticker", "session_date", "high", "low", "close", "volume", "open", "source"),
        "ticker = ?",
        arrayOf(ticker),
        null,
        null,
        "session_date DESC",
        "1",
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        DailySession(
            ticker = cursor.getString(0),
            date = runCatching { LocalDate.parse(cursor.getString(1)) }.getOrNull()
                ?: return@use null,
            high = cursor.nullableDouble(2),
            low = cursor.nullableDouble(3),
            close = cursor.nullableDouble(4),
            volume = cursor.nullableDouble(5),
            open = cursor.nullableDouble(6),
            derived = cursor.isDerived(7),
        )
    }

    /**
     * The newest session for every stock at once, for the screen that shows where each stands now.
     *
     * One query rather than [latestSession] per ticker, which a recompute would otherwise run
     * hundreds of times. The bare columns beside `MAX(session_date)` are taken from the row that
     * holds the maximum - SQLite guarantees this specifically for a single min or max aggregate,
     * which is why the query is shaped around one rather than joining back onto the table.
     */
    fun latestSessions(): Map<String, DailySession> = readableDatabase
        .rawQuery(
            "SELECT ticker, MAX(session_date), high, low, close, volume, open, source " +
                "FROM daily_prices GROUP BY ticker",
            null,
        )
        .use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val date = runCatching { LocalDate.parse(cursor.getString(1)) }.getOrNull()
                        ?: continue
                    val ticker = cursor.getString(0)
                    put(
                        ticker,
                        DailySession(
                            ticker = ticker,
                            date = date,
                            high = cursor.nullableDouble(2),
                            low = cursor.nullableDouble(3),
                            close = cursor.nullableDouble(4),
                            volume = cursor.nullableDouble(5),
                            open = cursor.nullableDouble(6),
                            derived = cursor.isDerived(7),
                        ),
                    )
                }
            }
        }

    /**
     * Drops everything stored for one stock.
     *
     * Only ever used to replace a series that has changed scale partway through, and deliberately
     * the whole series rather than the part the refetch covers. The refetch reaches back a year;
     * leaving anything older than that in place would leave the old money sitting behind the new
     * with a seam between them, which is the same break in a different position - found again on the
     * next refresh, healed again, and never settling. Prices older than a year cannot be refetched
     * and cannot be compared with a level printed after the split, so there is nothing to keep.
     */
    fun deleteSessions(ticker: String) {
        writableDatabase.delete("daily_prices", "ticker = ?", arrayOf(ticker))
    }

    /**
     * Everything stored for one stock, oldest first.
     *
     * Dates are ISO strings, so the earliest possible one compares below every real session and the
     * query needs no special case for "from the beginning".
     */
    fun allSessions(ticker: String): List<DailySession> =
        sessionsFrom(ticker, LocalDate.of(1900, 1, 1))

    /** Sessions for one stock from the day a call was made onward, oldest first. */
    fun sessionsFrom(ticker: String, from: LocalDate): List<DailySession> = readableDatabase.query(
        "daily_prices",
        arrayOf("ticker", "session_date", "high", "low", "close", "volume", "open", "source"),
        "ticker = ? AND session_date >= ?",
        arrayOf(ticker, from.toString()),
        null,
        null,
        "session_date",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    DailySession(
                        ticker = cursor.getString(0),
                        date = LocalDate.parse(cursor.getString(1)),
                        high = cursor.nullableDouble(2),
                        low = cursor.nullableDouble(3),
                        close = cursor.nullableDouble(4),
                        volume = cursor.nullableDouble(5),
                        open = cursor.nullableDouble(6),
                        derived = cursor.isDerived(7),
                    ),
                )
            }
        }
    }

    /**
     * The first session ever stored.
     *
     * Using the price history as the starting line means scoring covers exactly what it can
     * actually judge, with no date to configure and nothing counted from before it existed.
     */
    fun earliestSessionDate(): LocalDate? = readableDatabase
        .rawQuery("SELECT MIN(session_date) FROM daily_prices", null)
        .use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                runCatching { LocalDate.parse(cursor.getString(0)) }.getOrNull()
            } else null
        }

    fun pricedTickers(): Set<String> = readableDatabase
        .rawQuery("SELECT DISTINCT ticker FROM daily_prices", null)
        .use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun saveSessions(sessions: List<DailySession>, source: String) {
        writableDatabase.beginTransaction()
        try {
            sessions.forEach { session ->
                writableDatabase.insertWithOnConflict(
                    "daily_prices",
                    null,
                    ContentValues().apply {
                        put("ticker", session.ticker)
                        put("session_date", session.date.toString())
                        put("open", session.open)
                        put("high", session.high)
                        put("low", session.low)
                        put("close", session.close)
                        put("volume", session.volume)
                        put("source", source)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun Cursor.nullableDouble(index: Int): Double? =
        if (isNull(index)) null else getDouble(index)

    /**
     * The same by column name, for a row read a field at a time rather than by position.
     *
     * The price columns are read by index because those rows are read in their thousands and the
     * lookup is worth avoiding; a settled verdict is read once per closed call, where naming the
     * column is worth more than the lookup costs.
     */
    private fun Cursor.nullableDouble(column: String): Double? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getDouble(it) }

    /** A stored date, absent where the column is null or holds something that is not one. */
    private fun Cursor.nullableDate(column: String): LocalDate? =
        getColumnIndexOrThrow(column)
            .let { if (isNull(it)) null else getString(it) }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /**
     * Whether a stored session was built here rather than reported by a daily feed.
     *
     * Read off `source`, which has carried provenance since the table was created. Every row the
     * app has ever written names the feed it came from, so a row whose source is the aggregating
     * one is the only kind this can be true of - and an old row, or one with no source at all,
     * reads as reported, which is exactly what it is.
     */
    private fun Cursor.isDerived(index: Int): Boolean =
        !isNull(index) && getString(index) == PriceRepository.DERIVED_SOURCE

    /** The downloaded EGX catalog, so correct company names survive a restart. */
    fun stocks(): List<EgxStock> = readableDatabase
        .query("stocks", arrayOf("ticker", "name_en", "name_ar"), null, null, null, null, "ticker")
        .use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        EgxStock(
                            ticker = cursor.getString(0),
                            nameEnglish = cursor.getString(1),
                            nameArabic = cursor.getString(2),
                        ),
                    )
                }
            }
        }

    fun saveStocks(stocks: List<EgxStock>) {
        writableDatabase.beginTransaction()
        try {
            stocks.forEach { stock ->
                writableDatabase.insertWithOnConflict(
                    "stocks",
                    null,
                    ContentValues().apply {
                        put("ticker", stock.ticker)
                        put("name_en", stock.nameEnglish)
                        put("name_ar", stock.nameArabic)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /**
     * Drops every remembered chat selection.
     *
     * Selections used to survive a restart, so a chat picked days ago could still be feeding an
     * analysis without appearing to. They now last only as long as the app is open, and this
     * clears anything a previous version stored.
     */
    fun forgetChannelSelections() {
        writableDatabase.delete("channels", null, null)
    }

    fun saveResult(result: AnalysisResult, provider: CloudProvider, model: String): Long =
        writableDatabase.insertWithOnConflict(
            "analyses",
            null,
            ContentValues().apply {
                put("request_id", result.requestId)
                put("provider", provider.name)
                put("model", model)
                put("completed_at", result.completedAt.toString())
                put("payload", result.toJson().toString())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )

    /**
     * Reports deleted here but not yet buried in the sync channel.
     *
     * Deleting has to work with no signal - tidying up is exactly what you do on a plane - so the
     * intent is recorded and carried out at the next sync. Kept in the same database as the reports
     * so a delete and its record cannot be separated by a crash.
     */
    /** Every stored rule that has not been tombstoned. */
    fun wordingRules(): List<WordingRule> = readableDatabase
        .query("wording_rules", null, "deleted = 0", null, null, null, "updated_at ASC")
        .use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    runCatching { cursor.toWordingRule() }.getOrNull()?.let(::add)
                }
            }
        }

    /**
     * Every stored rule including the buried ones.
     *
     * The sync needs the tombstones: a delete that is not published is a delete the next device
     * undoes by uploading the rule back.
     */
    fun wordingRuleRevisions(): List<Pair<WordingRule, Boolean>> = readableDatabase
        .query("wording_rules", null, null, null, null, null, "updated_at ASC")
        .use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    runCatching {
                        cursor.toWordingRule() to
                            (cursor.getInt(cursor.getColumnIndexOrThrow("deleted")) == 1)
                    }.getOrNull()?.let(::add)
                }
            }
        }

    /** Writes a rule exactly as the merge decided it, tombstone included. */
    fun adoptWordingRule(rule: WordingRule, deleted: Boolean) {
        saveWordingRule(rule)
        if (deleted) {
            writableDatabase.execSQL(
                "UPDATE wording_rules SET deleted = 1 WHERE id = ?",
                arrayOf<Any>(rule.id),
            )
        }
    }

    fun saveWordingRule(rule: WordingRule) {
        writableDatabase.insertWithOnConflict(
            "wording_rules",
            null,
            ContentValues().apply {
                put("id", rule.id)
                put("slot", rule.slot.name)
                put("kind", rule.kind.name)
                put("phrase", rule.phrase)
                put("scope", rule.scope.name)
                put("enabled", if (rule.enabled) 1 else 0)
                put("origin", rule.origin.name)
                put("channels", rule.channels.joinToString(","))
                put("note", rule.note)
                put("updated_at", rule.updatedAt)
                put("updated_by", rule.updatedBy)
                put("deleted", 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /**
     * Buries a rule rather than removing the row.
     *
     * The same reason a deleted report leaves a marker: without one, the next device to sync sees
     * the rule missing here and puts it back.
     */
    fun buryWordingRule(id: String, at: Long, by: String) {
        writableDatabase.execSQL(
            "UPDATE wording_rules SET deleted = 1, updated_at = ?, updated_by = ? WHERE id = ?",
            arrayOf<Any>(at, by, id),
        )
    }

    private fun Cursor.toWordingRule() = WordingRule(
        id = getString(getColumnIndexOrThrow("id")),
        slot = RuleSlot.valueOf(getString(getColumnIndexOrThrow("slot"))),
        kind = RuleKind.valueOf(getString(getColumnIndexOrThrow("kind"))),
        phrase = getString(getColumnIndexOrThrow("phrase")),
        scope = RuleScope.valueOf(getString(getColumnIndexOrThrow("scope"))),
        enabled = getInt(getColumnIndexOrThrow("enabled")) == 1,
        origin = RuleOrigin.valueOf(getString(getColumnIndexOrThrow("origin"))),
        channels = getString(getColumnIndexOrThrow("channels"))
            .split(",")
            .mapNotNull(String::toLongOrNull)
            .toSet(),
        note = if (isNull(getColumnIndexOrThrow("note"))) null else getString(getColumnIndexOrThrow("note")),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        updatedBy = getString(getColumnIndexOrThrow("updated_by")),
    )

    fun promptVersions(): List<PromptVersion> = readableDatabase
        .query("prompt_versions", null, null, null, null, null, "sequence DESC")
        .use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        PromptVersion(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            sequence = cursor.getInt(cursor.getColumnIndexOrThrow("sequence")),
                            text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
                            schemaVersion = cursor.getColumnIndexOrThrow("schema_version").let {
                                if (cursor.isNull(it)) null else cursor.getInt(it)
                            },
                            ruleIds = cursor.getString(cursor.getColumnIndexOrThrow("rule_ids"))
                                .split(",").filter(String::isNotBlank),
                            reason = cursor.getString(cursor.getColumnIndexOrThrow("reason")),
                            device = cursor.getString(cursor.getColumnIndexOrThrow("device")),
                            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        ),
                    )
                }
            }
        }

    /**
     * Records a generated prompt, or leaves the existing one alone.
     *
     * Regenerating the same configuration produces the same id, and the version that already
     * carries it is the one runs were made against - overwriting its date would rewrite history
     * to say a later change had happened.
     */
    fun rememberPromptVersion(version: PromptVersion) {
        writableDatabase.insertWithOnConflict(
            "prompt_versions",
            null,
            ContentValues().apply {
                put("id", version.id)
                put("sequence", version.sequence)
                put("text", version.text)
                put("schema_version", version.schemaVersion)
                put("rule_ids", version.ruleIds.joinToString(","))
                put("reason", version.reason)
                put("device", version.device)
                put("created_at", version.createdAt)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun nextPromptSequence(): Int = readableDatabase
        .rawQuery("SELECT COALESCE(MAX(sequence), 0) + 1 FROM prompt_versions", null)
        .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 1 }

    fun pendingDeletions(): Set<String> = readableDatabase
        .rawQuery("SELECT request_id FROM pending_deletions", null)
        .use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun recordDeletion(requestId: String) {
        writableDatabase.insertWithOnConflict(
            "pending_deletions",
            null,
            ContentValues().apply { put("request_id", requestId) },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun clearDeletion(requestId: String) {
        writableDatabase.delete("pending_deletions", "request_id = ?", arrayOf(requestId))
    }

    fun requestIdOf(id: Long): String? = readableDatabase
        .query("analyses", arrayOf("request_id"), "id = ?", arrayOf(id.toString()), null, null, null)
        .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    /** Every run this device already holds, so a sync only fetches what it has not seen. */
    fun savedRequestIds(): Set<String> = readableDatabase
        .rawQuery("SELECT request_id FROM analyses", null)
        .use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    /**
     * Every run as it was stored, without reading a single payload.
     *
     * For the restore, which copies runs out of a backup and into here. Deliberately not [results]:
     * that parses each payload into a `SavedAnalysis` and counts the ones it cannot read, and a
     * report a later build wrote in a shape this one does not understand would be dropped on the
     * way through - lost by the very operation the user reached for to stop losing things. The
     * bytes are what a run is; understanding them is this build's problem and not the record's.
     */
    fun storedRuns(): List<SyncedRun> = readableDatabase
        .query(
            "analyses",
            arrayOf("request_id", "provider", "model", "completed_at", "payload"),
            null, null, null, null, "completed_at ASC",
        )
        .use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        SyncedRun(
                            requestId = cursor.getString(0),
                            provider = cursor.getString(1),
                            model = cursor.getString(2),
                            completedAt = cursor.getString(3),
                            payload = cursor.getString(4),
                        ),
                    )
                }
            }
        }

    /**
     * Stores a run that arrived from another device.
     *
     * Ignores one already held rather than replacing it: a saved run never changes, so the copy on
     * disk and the copy in the cloud are the same thing, and overwriting could only lose a repair
     * made locally. Returns whether anything was actually added.
     */
    fun adoptResult(
        requestId: String,
        provider: String,
        model: String,
        completedAt: String,
        payload: String,
    ): Boolean = writableDatabase.insertWithOnConflict(
        "analyses",
        null,
        ContentValues().apply {
            put("request_id", requestId)
            put("provider", provider)
            put("model", model)
            put("completed_at", completedAt)
            put("payload", payload)
        },
        SQLiteDatabase.CONFLICT_IGNORE,
    ) != -1L

    /**
     * How many saved runs the last [results] call could not read back.
     *
     * A payload that failed to parse used to be dropped without a word. Two runs saved on 2 August
     * were unreadable, so neither appeared in the list and the newest report on screen was quietly
     * an older one - a thinner report that simply looked like the latest.
     */
    var unreadableResults: Int = 0
        private set

    fun results(): List<SavedAnalysis> = readableDatabase.query(
        "analyses",
        arrayOf("id", "provider", "model", "payload"),
        null,
        null,
        null,
        null,
        "completed_at DESC",
    ).use { cursor ->
        var unreadable = 0
        buildList {
            while (cursor.moveToNext()) {
                runCatching {
                    add(
                        SavedAnalysis(
                            id = cursor.getLong(0),
                            provider = CloudProvider.valueOf(cursor.getString(1)),
                            model = cursor.getString(2),
                            result = JSONObject(cursor.getString(3)).toAnalysisResult(),
                        ),
                    )
                }.onFailure { unreadable++ }
            }
        }.also { unreadableResults = unreadable }
    }

    /**
     * What Ask AI said about one call, kept so re-opening a card costs nothing.
     *
     * Keyed by the call rather than by the stock: two channels calling one stock on one session are
     * two cards printing different levels, and an opinion on one of them is not an opinion on the
     * other. `request_id` is the report the call was read out of, and it is here for one purpose -
     * deleting the report deletes the opinions with it, because an opinion about a card that no
     * longer exists is an orphan nothing will ever show or clean up.
     *
     * Deliberately **not** synced. Everything else that travels is a record of what happened - a
     * report, a trade, a rule. This is one model's answer to one question at one moment, and the
     * cheapest way for another device to have it is to ask the question there.
     */
    private fun SQLiteDatabase.createStockOpinions() = execSQL(
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
            standing TEXT NOT NULL DEFAULT '',
            outlook TEXT NOT NULL,
            forecast TEXT NOT NULL DEFAULT '[]',
            stance TEXT NOT NULL,
            stance_detail TEXT NOT NULL,
            checks TEXT NOT NULL DEFAULT '[]',
            news TEXT NOT NULL DEFAULT '[]',
            catalysts TEXT NOT NULL DEFAULT '[]',
            risks TEXT NOT NULL DEFAULT '[]',
            unknowns TEXT NOT NULL DEFAULT '[]',
            news_window INTEGER NOT NULL DEFAULT 0,
            model TEXT NOT NULL,
            asked_on TEXT NOT NULL,
            searched INTEGER NOT NULL DEFAULT 0
        )"""
    )

    /** Every opinion on this device, by the call it is about. */
    fun stockOpinions(): Map<String, StockOpinion> = readableDatabase
        .query("stock_opinions", null, null, null, null, null, null)
        .use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    // A row whose verdict this build no longer knows is dropped rather than
                    // defaulted: a card colouring an unknown answer green would be inventing one.
                    runCatching { put(id, cursor.toStockOpinion()) }
                }
            }
        }

    fun saveStockOpinion(id: String, requestId: String, ticker: String, openedOn: LocalDate, channel: String, opinion: StockOpinion) {
        val values = ContentValues().apply {
            put("id", id)
            put("request_id", requestId)
            put("ticker", ticker)
            put("opened_on", openedOn.toString())
            put("channel", channel)
            put("verdict", opinion.verdict.name)
            put("horizon", opinion.horizon.name)
            put("confidence", opinion.confidence.name)
            put("headline", opinion.headline)
            put("standing", opinion.standing)
            put("outlook", opinion.outlook)
            put("forecast", opinion.forecast.forecastJson().toString())
            put("stance", opinion.onTheCall.stance.name)
            put("stance_detail", opinion.onTheCall.detail)
            put("checks", opinion.onTheCall.checks.checksJson().toString())
            put("news", opinion.news.newsJson().toString())
            put("catalysts", opinion.catalysts.catalystJson().toString())
            put("risks", JSONArray(opinion.risks).toString())
            put("unknowns", JSONArray(opinion.unknowns).toString())
            put("news_window", opinion.newsWindowDays)
            put("model", opinion.model)
            put("asked_on", opinion.askedOn.toString())
            put("searched", if (opinion.searched) 1 else 0)
        }
        writableDatabase.insertWithOnConflict(
            "stock_opinions", null, values, SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun deleteStockOpinion(id: String) {
        writableDatabase.delete("stock_opinions", "id = ?", arrayOf(id))
    }

    /**
     * Drops every opinion belonging to a report.
     *
     * Called from each of the three delete paths rather than left to a foreign key: `analyses` is
     * keyed on an autoincrementing id while everything that travels is keyed on the request id, and
     * a cascade wired to the wrong one of those deletes nothing while looking correct.
     */
    private fun deleteOpinionsFor(requestId: String) {
        writableDatabase.delete("stock_opinions", "request_id = ?", arrayOf(requestId))
    }

    private fun Cursor.toStockOpinion(): StockOpinion = StockOpinion(
        verdict = StockOpinion.Verdict.valueOf(getString(getColumnIndexOrThrow("verdict"))),
        horizon = StockOpinion.Horizon.valueOf(getString(getColumnIndexOrThrow("horizon"))),
        confidence = StockOpinion.Confidence.valueOf(getString(getColumnIndexOrThrow("confidence"))),
        headline = getString(getColumnIndexOrThrow("headline")),
        standing = getString(getColumnIndexOrThrow("standing")),
        outlook = getString(getColumnIndexOrThrow("outlook")),
        forecast = getString(getColumnIndexOrThrow("forecast")).toForecast(),
        onTheCall = StockOpinion.CallView(
            stance = StockOpinion.Stance.valueOf(getString(getColumnIndexOrThrow("stance"))),
            detail = getString(getColumnIndexOrThrow("stance_detail")),
            checks = getString(getColumnIndexOrThrow("checks")).toChecks(),
        ),
        news = getString(getColumnIndexOrThrow("news")).toNewsItems(),
        catalysts = getString(getColumnIndexOrThrow("catalysts")).toCatalysts(),
        risks = getString(getColumnIndexOrThrow("risks")).toStringList(),
        unknowns = getString(getColumnIndexOrThrow("unknowns")).toStringList(),
        newsWindowDays = getInt(getColumnIndexOrThrow("news_window")),
        model = getString(getColumnIndexOrThrow("model")),
        askedOn = LocalDate.parse(getString(getColumnIndexOrThrow("asked_on"))),
        searched = getInt(getColumnIndexOrThrow("searched")) == 1,
    )

    /**
     * The lists an opinion carries, stored as JSON in one column each.
     *
     * A column per field would mean a migration every time the prompt learns to report one more
     * thing, and these are read back only to be printed - nothing queries inside them. The tone is
     * written by name for the same reason the verdict is: a number would survive a reordering of
     * the enum and come back meaning something else.
     */
    private fun List<StockOpinion.NewsItem>.newsJson(): JSONArray = JSONArray().also { array ->
        forEach { item ->
            array.put(
                JSONObject()
                    .put("headline", item.headline)
                    .put("date", item.date)
                    .put("source", item.source)
                    .put("tone", item.tone.name),
            )
        }
    }

    private fun List<StockOpinion.Catalyst>.catalystJson(): JSONArray = JSONArray().also { array ->
        forEach { item ->
            array.put(
                JSONObject()
                    .put("what", item.what)
                    .put("when", item.on)
                    .put("source", item.source),
            )
        }
    }

    /**
     * The three spans as an array, and an absent forecast as an empty one.
     *
     * An array rather than an object keyed by span because the span travels on the leg either way
     * and the order is the reading order - which the parser has already fixed, so the row does not
     * have to be re-sorted on the way back out.
     */
    private fun StockOpinion.Forecast?.forecastJson(): JSONArray = JSONArray().also { array ->
        this?.legs?.forEach { leg ->
            array.put(
                JSONObject()
                    .put("span", leg.span.name)
                    .put("direction", leg.direction.name)
                    .put("why", leg.why)
                    .put("confidence", leg.confidence.name),
            )
        }
    }

    private fun List<StockOpinion.Check>.checksJson(): JSONArray = JSONArray().also { array ->
        forEach { check ->
            array.put(
                JSONObject()
                    .put("item", check.item.name)
                    .put("rating", check.rating.name)
                    .put("note", check.note),
            )
        }
    }

    /**
     * A stored forecast back, or nothing.
     *
     * All-or-nothing on the way out as it is on the way in: a leg written by a later build under a
     * span or a direction this one does not know costs the whole forecast rather than leaving two
     * of three spans on screen, which would be this build choosing which reading to hide. The other
     * judgements survive it, which is the difference from a verdict it cannot read.
     */
    private fun String.toForecast(): StockOpinion.Forecast? {
        val legs = runCatching { JSONArray(this) }.getOrNull().jsonObjects().mapNotNull { leg ->
            StockOpinion.Forecast.Leg(
                span = leg.optString("span").asEnum(StockOpinion.Span.entries) ?: return@mapNotNull null,
                direction = leg.optString("direction").asEnum(StockOpinion.Direction.entries)
                    ?: return@mapNotNull null,
                why = leg.optString("why"),
                confidence = leg.optString("confidence").asEnum(StockOpinion.Confidence.entries)
                    ?: StockOpinion.Confidence.LOW,
            )
        }
        return if (legs.size == StockOpinion.Span.entries.size) StockOpinion.Forecast(legs) else null
    }

    /** One unreadable check costs its own row and nothing else, the way one unreadable tone does. */
    private fun String.toChecks(): List<StockOpinion.Check> =
        runCatching { JSONArray(this) }.getOrNull().jsonObjects().mapNotNull { check ->
            StockOpinion.Check(
                item = check.optString("item").asEnum(StockOpinion.CheckItem.entries)
                    ?: return@mapNotNull null,
                rating = check.optString("rating").asEnum(StockOpinion.Rating.entries)
                    ?: return@mapNotNull null,
                note = check.optString("note"),
            )
        }

    /** A stored token back to its entry, or null where this build no longer knows it. */
    private fun <T : Enum<T>> String.asEnum(entries: List<T>): T? =
        entries.firstOrNull { it.name == this }

    /**
     * A stored list back into items, forgiving a row this build cannot fully read.
     *
     * An opinion cannot be re-asked for free, so a tone this build no longer knows costs the item
     * its colour rather than costing the reader the whole answer.
     */
    private fun String.toNewsItems(): List<StockOpinion.NewsItem> =
        runCatching { JSONArray(this) }.getOrNull().jsonObjects().map { item ->
            StockOpinion.NewsItem(
                headline = item.optString("headline"),
                date = item.optString("date"),
                source = item.optString("source"),
                tone = runCatching { StockOpinion.Tone.valueOf(item.optString("tone")) }
                    .getOrDefault(StockOpinion.Tone.NEUTRAL),
            )
        }

    private fun String.toCatalysts(): List<StockOpinion.Catalyst> =
        runCatching { JSONArray(this) }.getOrNull().jsonObjects().map { item ->
            StockOpinion.Catalyst(
                what = item.optString("what"),
                on = item.optString("when"),
                source = item.optString("source"),
            )
        }

    private fun String.toStringList(): List<String> =
        runCatching { JSONArray(this) }.getOrNull()?.let { array ->
            (0 until array.length()).map { array.getString(it) }
        }.orEmpty()

    private fun JSONArray?.jsonObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    /** Removes a report by the identity that travels between devices. */
    fun deleteResultByRequestId(requestId: String) {
        deleteOpinionsFor(requestId)
        writableDatabase.delete("analyses", "request_id = ?", arrayOf(requestId))
    }

    fun deleteResult(id: Long) {
        // Read back before the row goes: the opinions are keyed on the request id, and after the
        // delete there is nothing left to look it up from.
        requestIdOf(id)?.let(::deleteOpinionsFor)
        writableDatabase.delete("analyses", "id = ?", arrayOf(id.toString()))
    }

    fun deleteAllResults() {
        writableDatabase.delete("stock_opinions", null, null)
        // Every frozen verdict belongs to a call in one of those reports. A row left behind would
        // be harmless - its key names a call nothing asks about any more - but "delete everything"
        // has to mean it.
        writableDatabase.delete("settled_calls", null, null)
        writableDatabase.delete("analyses", null, null)
    }

    /** The stored form of a run, for anything that has to move one off this device. */
    fun storedJsonOf(result: AnalysisResult): String = result.toJson().toString()

    private fun AnalysisResult.toJson() = JSONObject().apply {
        put("requestId", requestId)
        put("inquiryReplyCount", inquiryReplyCount)
        put("analysisMode", analysisMode.name)
        putNullable("recommendationTargetDate", recommendationTargetDate?.toString())
        put("diagnostics", JSONObject().apply {
            putNullable("sourceWindowStart", diagnostics.sourceWindowStart?.toString())
            putNullable("sourceWindowEnd", diagnostics.sourceWindowEnd?.toString())
            put("inputCount", diagnostics.inputCount)
            put("acceptedInputCount", diagnostics.acceptedInputCount)
            put("correctionAttempted", diagnostics.correctionAttempted)
            put("durationMilliseconds", diagnostics.durationMilliseconds)
            put("promptId", diagnostics.promptId)
            put("promptSchemaVersion", diagnostics.promptSchemaVersion)
            put("promptRuleIds", JSONArray(diagnostics.promptRuleIds))
            put("validationWarnings", JSONArray(diagnostics.validationWarnings))
            put("excludedSources", JSONArray().apply {
                diagnostics.excludedSources.forEach {
                    put(JSONObject().put("sourceId", it.sourceId).put("reason", it.reason))
                }
            })
            put("requestCount", diagnostics.requestCount)
            put("imagesSent", diagnostics.imagesSent)
            put("promptTokens", diagnostics.promptTokens)
            put("completionTokens", diagnostics.completionTokens)
            put("totalTokens", diagnostics.totalTokens)
            put("unreportedTokenRequests", diagnostics.unreportedTokenRequests)
            put("unaccountedImages", JSONArray().apply {
                diagnostics.unaccountedImages.forEach {
                    put(
                        JSONObject().apply {
                            put("reference", it.reference)
                            putNullable("sourceId", it.sourceId)
                            putNullable("caption", it.caption)
                        },
                    )
                }
            })
        })
        put("imagePaths", JSONArray(imagePaths))
        put("rawResponse", rawResponse)
        put("completedAt", completedAt.toString())
        put("recommendations", JSONArray().apply {
            recommendations.forEach { recommendation ->
                put(JSONObject().apply {
                    put("ticker", recommendation.ticker)
                    put("companyName", recommendation.companyName)
                    putNullable("companyNameArabic", recommendation.companyNameArabic)
                    put("sourceName", recommendation.sourceName)
                    putNullable("targetDate", recommendation.targetDate?.toString())
                    putNullable("timing", recommendation.timing)
                    putNullable("entryLow", recommendation.entryLow)
                    putNullable("entryHigh", recommendation.entryHigh)
                    putNullable("takeProfit1", recommendation.takeProfit1)
                    putNullable("takeProfit2", recommendation.takeProfit2)
                    putNullable("stopLoss", recommendation.stopLoss)
                    putNullable("notesArabic", recommendation.notesArabic)
                    put("sourceIds", JSONArray(recommendation.sourceIds))
                    put("signal", recommendation.signal)
                    putNullable("confidence", recommendation.confidence)
                    putNullable("riskLevel", recommendation.riskLevel)
                    putNullable("timeHorizon", recommendation.timeHorizon)
                    put("indicators", JSONArray(recommendation.indicators))
                })
            }
        })
        put("selectedChannels", JSONArray().apply {
            selectedChannels.forEach {
                put(JSONObject().put("id", it.id).put("name", it.name))
            }
        })
        put("sources", JSONArray().apply {
            sources.forEach { source ->
                put(JSONObject().apply {
                    put("sourceId", source.sourceId)
                    putNullable("channelId", source.channelId)
                    put("channelName", source.channelName)
                    putNullable("messageId", source.messageId)
                    put("timestamp", source.timestamp.toString())
                    put("contentType", source.contentType.name)
                    put("preview", source.preview)
                })
            }
        })
    }

    private fun JSONObject.toAnalysisResult() = AnalysisResult(
        requestId = getString("requestId"),
        inquiryReplyCount = optInt("inquiryReplyCount"),
        analysisMode = optString("analysisMode")
            .takeIf(String::isNotBlank)
            ?.let { runCatching { AnalysisMode.valueOf(it) }.getOrDefault(AnalysisMode.NEXT_DAY) }
            ?: AnalysisMode.NEXT_DAY,
        recommendationTargetDate = nullableString("recommendationTargetDate")
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        diagnostics = optJSONObject("diagnostics")?.let { value ->
            AnalysisDiagnostics(
                sourceWindowStart = value.nullableString("sourceWindowStart")
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() },
                sourceWindowEnd = value.nullableString("sourceWindowEnd")
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() },
                inputCount = value.optInt("inputCount"),
                acceptedInputCount = value.optInt("acceptedInputCount"),
                correctionAttempted = value.optBoolean("correctionAttempted"),
                durationMilliseconds = value.optLong("durationMilliseconds"),
                promptId = value.optString("promptId").takeIf(String::isNotBlank),
                promptSchemaVersion = value.optInt("promptSchemaVersion").takeIf { it > 0 },
                promptRuleIds = value.optJSONArray("promptRuleIds")
                    ?.let { array -> (0 until array.length()).map(array::getString) }
                    .orEmpty(),
                validationWarnings = value.optJSONArray("validationWarnings")?.strings().orEmpty(),
                excludedSources = value.optJSONArray("excludedSources")?.objects()?.map {
                    ExcludedSource(it.optString("sourceId"), it.optString("reason"))
                }.orEmpty(),
                requestCount = value.optInt("requestCount"),
                imagesSent = value.optInt("imagesSent"),
                promptTokens = value.optLong("promptTokens"),
                completionTokens = value.optLong("completionTokens"),
                totalTokens = value.optLong("totalTokens"),
                unreportedTokenRequests = value.optInt("unreportedTokenRequests"),
                unaccountedImages = value.optJSONArray("unaccountedImages")?.objects()?.map {
                    UnaccountedImage(
                        reference = it.optInt("reference"),
                        sourceId = it.nullableString("sourceId"),
                        caption = it.nullableString("caption"),
                    )
                }.orEmpty(),
            )
        } ?: AnalysisDiagnostics(),
        imagePaths = optJSONArray("imagePaths")?.strings().orEmpty(),
        rawResponse = optString("rawResponse"),
        // Rebuilt from the stored response rather than persisted separately, so the nested
        // occurrences can never drift from the response they came from - and analyses saved before
        // this existed still gain them. Responses predating the consolidated contract yield none.
        consolidated = runCatching {
            ConsolidatedParser.parse(
                optString("rawResponse"),
                nullableString("recommendationTargetDate")
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            )
        }
            .getOrDefault(emptyList()),
        completedAt = Instant.parse(getString("completedAt")),
        recommendations = getJSONArray("recommendations").objects().map { item ->
            RecommendationResult(
                ticker = item.optString("ticker"),
                companyName = item.optString("companyName"),
                companyNameArabic = item.nullableString("companyNameArabic"),
                sourceName = item.optString("sourceName"),
                targetDate = item.nullableString("targetDate")?.let(LocalDate::parse),
                timing = item.nullableString("timing"),
                entryLow = item.nullableDouble("entryLow"),
                entryHigh = item.nullableDouble("entryHigh"),
                takeProfit1 = item.nullableDouble("takeProfit1"),
                takeProfit2 = item.nullableDouble("takeProfit2"),
                stopLoss = item.nullableDouble("stopLoss"),
                notesArabic = item.nullableString("notesArabic"),
                sourceIds = item.optJSONArray("sourceIds")?.strings().orEmpty(),
                signal = item.optString("signal", "HOLD"),
                confidence = item.nullableDouble("confidence"),
                riskLevel = item.nullableString("riskLevel"),
                timeHorizon = item.nullableString("timeHorizon"),
                indicators = item.optJSONArray("indicators")?.strings().orEmpty(),
            )
        },
        modelExclusions = runCatching {
            ConsolidatedParser.exclusions(optString("rawResponse"))
        }.getOrDefault(emptyList()),
        selectedChannels = optJSONArray("selectedChannels")?.objects()?.map { item ->
            AnalysedChannel(item.optLong("id"), cleanChannelName(item.optString("name")))
        }.orEmpty(),
        sources = getJSONArray("sources").objects().map { item ->
            SourceTrace(
                sourceId = item.getString("sourceId"),
                channelId = item.nullableLong("channelId"),
                // Folded on read as well as on write: analyses saved before this carry the raw title, and
                // would otherwise keep counting as a separate source forever.
                channelName = cleanChannelName(item.getString("channelName")),
                messageId = item.nullableLong("messageId"),
                timestamp = Instant.parse(item.getString("timestamp")),
                contentType = AnalysisContentType.valueOf(item.getString("contentType")),
                preview = item.optString("preview"),
            )
        },
    )

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key)

    private fun JSONObject.nullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    private fun JSONArray.objects() = buildList {
        for (index in 0 until length()) add(getJSONObject(index))
    }

    private fun JSONArray.strings() = buildList {
        for (index in 0 until length()) add(getString(index))
    }

    /**
     * The rows of the job table this app used to keep, for the one migration that reads them.
     *
     * Raw columns rather than a parsed job: the types they parsed into are gone, and reviving them
     * so that a one-time read can throw them away again would keep the whole vocabulary alive for
     * a single function. Tolerant of the table being absent, which on a fresh install it is.
     */
    fun legacyScheduleRows(): List<LegacyScheduleRow> = runCatching {
        readableDatabase
            .query("scheduled_jobs", null, null, null, null, null, "created_at ASC")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        runCatching {
                            LegacyScheduleRow(
                                enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) == 1,
                                workKind = cursor.getString(cursor.getColumnIndexOrThrow("work_kind")),
                                triggerKind = cursor.getString(cursor.getColumnIndexOrThrow("trigger_kind")),
                                triggerAt = cursor.getString(cursor.getColumnIndexOrThrow("trigger_at")),
                                workConfig = cursor.getString(cursor.getColumnIndexOrThrow("work_config")),
                            )
                        }.getOrNull()?.let(::add)
                    }
                }
            }
    }.getOrDefault(emptyList())

    /**
     * Takes the old job table away for good, once what was in it has been carried across.
     *
     * Dropped rather than left sitting unused: a table nothing reads is a table the next reader of
     * this file has to work out the status of, and the migration that empties it is the only thing
     * that ever knew the answer.
     */
    fun dropScheduledJobs() {
        runCatching { writableDatabase.execSQL("DROP TABLE IF EXISTS scheduled_jobs") }
    }

    /**
     * What the user has already been told about each trade.
     *
     * Device-local and never published, for the reason `scheduled_jobs` is: a phone and a tablet
     * holding one record would each announce the same stop, and being told twice about one trade
     * is how a notification channel gets switched off. Being told once per device is the honest
     * behaviour - each phone speaks for itself about what it has said.
     *
     * Not a cache of the status, which is derived on every recompute and must go on being derived:
     * this is a record of what was *said*, which is a different fact and the only one that cannot
     * be worked out from the prices.
     */
    private fun SQLiteDatabase.createPositionStatusSeen() = execSQL(
        """CREATE TABLE IF NOT EXISTS position_status_seen (
            position_id TEXT PRIMARY KEY,
            status TEXT NOT NULL,
            open INTEGER NOT NULL,
            at INTEGER NOT NULL
        )""",
    )

    /**
     * Where each call stood relative to its buy zone the last time anything was said about it.
     *
     * The sibling of `position_status_seen` and device-local for the identical reason: a phone and
     * a tablet holding one record would each announce the same stock coming into range, and being
     * told twice about one call is how a notification channel gets switched off. Nothing in
     * `*Sync.kt` reads it.
     *
     * Keyed on the call's own id - ticker, session **and** channel - rather than on the holding
     * key two channels share. Two channels calling one stock print two different buy zones, and the
     * price can be inside one and outside the other.
     */
    private fun SQLiteDatabase.createCallAlertSeen() = execSQL(
        """CREATE TABLE IF NOT EXISTS call_alert_seen (
            call_id TEXT PRIMARY KEY,
            in_band INTEGER NOT NULL,
            at INTEGER NOT NULL
        )""",
    )

    /** The whole table, one row per watched call, read in full on every sweep. */
    fun callAlertSeen(): Map<String, CallState> = readableDatabase
        .query("call_alert_seen", null, null, null, null, null, null)
        .use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(
                        cursor.getString(cursor.getColumnIndexOrThrow("call_id")),
                        CallState(
                            inBand = cursor.getInt(cursor.getColumnIndexOrThrow("in_band")) == 1,
                        ),
                    )
                }
            }
        }

    /**
     * Writes down what has just been said, and forgets the calls that have gone.
     *
     * One transaction and only what moved, exactly as [savePositionStatusSeen] does: a record of a
     * hundred calls has a handful cross their band on a busy day.
     */
    fun saveCallAlertSeen(seen: Map<String, CallState>, forgotten: Set<String> = emptySet()) {
        if (seen.isEmpty() && forgotten.isEmpty()) return
        val now = System.currentTimeMillis()
        writableDatabase.beginTransaction()
        try {
            seen.forEach { (id, state) ->
                writableDatabase.insertWithOnConflict(
                    "call_alert_seen",
                    null,
                    ContentValues().apply {
                        put("call_id", id)
                        put("in_band", if (state.inBand) 1 else 0)
                        put("at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            forgotten.forEach {
                writableDatabase.delete("call_alert_seen", "call_id = ?", arrayOf(it))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /**
     * Where each open trade stood against its stop and its target 2 when the reader was last told.
     *
     * The third sibling of `position_status_seen` and `call_alert_seen`, device-local for the same
     * reason both of those are: two phones holding one record would each announce the same trade
     * closing on the same stop.
     *
     * Keyed per trade **and per level**, because a price can be a whisker from its stop and nowhere
     * near target 2. One key for both would let whichever was checked first swallow the other.
     */
    private fun SQLiteDatabase.createApproachSeen() = execSQL(
        """CREATE TABLE IF NOT EXISTS position_approach_seen (
            alert_key TEXT PRIMARY KEY,
            near INTEGER NOT NULL,
            at INTEGER NOT NULL
        )""",
    )

    /** The whole table, one row per watched level, read in full on every sweep. */
    fun approachSeen(): Map<String, ApproachState> = readableDatabase
        .query("position_approach_seen", null, null, null, null, null, null)
        .use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(
                        cursor.getString(cursor.getColumnIndexOrThrow("alert_key")),
                        ApproachState(
                            near = cursor.getInt(cursor.getColumnIndexOrThrow("near")) == 1,
                        ),
                    )
                }
            }
        }

    /** Writes what has just been said, and forgets the trades that have gone. */
    fun saveApproachSeen(seen: Map<String, ApproachState>, forgotten: Set<String> = emptySet()) {
        if (seen.isEmpty() && forgotten.isEmpty()) return
        val now = System.currentTimeMillis()
        writableDatabase.beginTransaction()
        try {
            seen.forEach { (key, state) ->
                writableDatabase.insertWithOnConflict(
                    "position_approach_seen",
                    null,
                    ContentValues().apply {
                        put("alert_key", key)
                        put("near", if (state.near) 1 else 0)
                        put("at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            forgotten.forEach {
                writableDatabase.delete("position_approach_seen", "alert_key = ?", arrayOf(it))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /**
     * The sessions whose digest has already been announced.
     *
     * `session_events` holds what happened; this holds what was *said about it*, which is the same
     * distinction `position_status_seen` draws against the statuses it does not cache. The digest is
     * rebuilt on every recompute and rightly so - it has one right answer forever - and that is
     * exactly why it cannot decide on its own whether it has been spoken.
     *
     * A row per session rather than one "newest announced" value: the two differ on the phone that
     * was switched off across a session, where a single high-water mark would count a session it
     * never saw as already said. Device-local, like every other row that records what was spoken.
     */
    private fun SQLiteDatabase.createSessionDigestAnnounced() = execSQL(
        """CREATE TABLE IF NOT EXISTS session_digest_announced (
            session_date TEXT PRIMARY KEY,
            at INTEGER NOT NULL
        )""",
    )

    /** Whether this session's digest has already been spoken on this device. */
    fun sessionDigestAnnounced(date: LocalDate): Boolean = readableDatabase
        .query(
            "session_digest_announced",
            arrayOf("session_date"),
            "session_date = ?",
            arrayOf(date.toString()),
            null,
            null,
            null,
        )
        .use { it.moveToFirst() }

    /** Records that it has been, so a later recompute of the same session says nothing again. */
    fun recordSessionDigestAnnounced(date: LocalDate) {
        writableDatabase.insertWithOnConflict(
            "session_digest_announced",
            null,
            ContentValues().apply {
                put("session_date", date.toString())
                put("at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /**
     * The verdicts the market can no longer change, so they are never scored again.
     *
     * Keyed on `settledKey`, which carries the levels and the window as well as the call - a
     * re-extraction that reads a different stop asks under a different key and is scored from
     * scratch, rather than inheriting a verdict reached about other numbers.
     *
     * The sessions the call was judged on travel with it, as JSON in one column. They are evidence
     * rather than a second copy of the price table: the card draws them, and a frozen verdict beside
     * a table read from somewhere else could disagree with itself. JSON rather than a second table
     * for the reason `scheduled_jobs` keeps its settings that way - the shape belongs to the row,
     * and nothing else ever queries into it.
     *
     * Local and never synced, exactly like `price_events`: every device fetches the same public
     * feed and settles a call the same way, so shipping one phone's conclusion into another's
     * evidence would put an opinion where a measurement belongs.
     */
    private fun SQLiteDatabase.createSettledCalls() = execSQL(
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

    /**
     * Every frozen verdict, read once for a whole recompute.
     *
     * A row this build cannot interpret - an outcome name it does not know, sessions that will not
     * parse - is **dropped rather than defaulted**, and the call it belongs to is simply scored
     * again. Deriving the answer is always available; guessing at a stored one is not.
     */
    fun settledCalls(): Map<String, SettledCall> = readableDatabase
        .query("settled_calls", null, null, null, null, null, null)
        .use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val row = runCatching { cursor.toSettledCall() }.getOrNull() ?: continue
                    put(row.key, row)
                }
            }
        }

    private fun Cursor.toSettledCall(): SettledCall {
        val ticker = getString(getColumnIndexOrThrow("ticker"))
        return SettledCall(
            key = getString(getColumnIndexOrThrow("call_key")),
            ticker = ticker,
            outcome = Outcome.valueOf(getString(getColumnIndexOrThrow("outcome"))),
            settledOn = nullableDate("settled_on"),
            stoppedOn = nullableDate("stopped_on"),
            stoppedAfterPartial = getInt(getColumnIndexOrThrow("stopped_after_partial")) == 1,
            windowComplete = getInt(getColumnIndexOrThrow("window_complete")) == 1,
            peakHigh = nullableDouble("peak_high"),
            peakOn = nullableDate("peak_on"),
            troughLow = nullableDouble("trough_low"),
            troughOn = nullableDate("trough_on"),
            returnPct = nullableDouble("return_pct"),
            sessionsElapsed = getInt(getColumnIndexOrThrow("sessions_elapsed")),
            sessions = judgedSessions(ticker, getString(getColumnIndexOrThrow("sessions"))),
        )
    }

    /** Writes down the calls that have just settled. Only the newly settled ones ever reach here. */
    fun saveSettledCalls(settled: List<SettledCall>) {
        if (settled.isEmpty()) return
        val now = System.currentTimeMillis()
        writableDatabase.beginTransaction()
        try {
            settled.forEach { call ->
                writableDatabase.insertWithOnConflict(
                    "settled_calls",
                    null,
                    ContentValues().apply {
                        put("call_key", call.key)
                        put("ticker", call.ticker)
                        put("outcome", call.outcome.name)
                        put("settled_on", call.settledOn?.toString())
                        put("stopped_on", call.stoppedOn?.toString())
                        put("stopped_after_partial", if (call.stoppedAfterPartial) 1 else 0)
                        put("window_complete", if (call.windowComplete) 1 else 0)
                        put("peak_high", call.peakHigh)
                        put("peak_on", call.peakOn?.toString())
                        put("trough_low", call.troughLow)
                        put("trough_on", call.troughOn?.toString())
                        put("return_pct", call.returnPct)
                        put("sessions_elapsed", call.sessionsElapsed)
                        put("sessions", call.sessions.toStoredJson())
                        put("settled_at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /**
     * Re-opens every call settled on one stock's prices.
     *
     * The two events that rewrite those prices underneath a verdict: a heal, which replaces the
     * whole stored series, and a newly recorded change of scale, which says the levels and the
     * prices were never in the same money. Either leaves a frozen verdict describing a history that
     * no longer exists, so it goes and the call is scored again from what is on disk now.
     */
    fun clearSettledCalls(ticker: String) {
        writableDatabase.delete("settled_calls", "ticker = ?", arrayOf(ticker))
    }

    /** The sessions a frozen verdict was reached on, as they went in. */
    private fun List<DailySession>.toStoredJson(): String = JSONArray().apply {
        this@toStoredJson.forEach { session ->
            put(
                JSONObject().apply {
                    put("d", session.date.toString())
                    putNullable("o", session.open)
                    putNullable("h", session.high)
                    putNullable("l", session.low)
                    putNullable("c", session.close)
                    putNullable("v", session.volume)
                    // Absent rather than false on the ordinary row, which is nearly every row.
                    if (session.derived) put("built", true)
                },
            )
        }
    }.toString()

    private fun judgedSessions(ticker: String, stored: String): List<DailySession> {
        val array = JSONArray(stored)
        return (0 until array.length()).mapNotNull { index ->
            val row = array.optJSONObject(index) ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(row.getString("d")) }.getOrNull()
                ?: return@mapNotNull null
            DailySession(
                ticker = ticker,
                date = date,
                high = row.nullableDouble("h"),
                low = row.nullableDouble("l"),
                close = row.nullableDouble("c"),
                volume = row.nullableDouble("v"),
                open = row.nullableDouble("o"),
                derived = row.optBoolean("built", false),
            )
        }
    }

    /**
     * What the market did to this record, one row per event, dated by the session that caused it.
     *
     * The one thing on the "what happened" card that is written down, and the reason it is written
     * down is that nothing else records it. A verdict is on disk - `settled_calls` holds the frozen
     * ones and the rest are re-derived from prices - but *what changed on the session of the 24th*
     * is a difference between two readings, and a difference is exactly what a table of readings
     * does not hold. Kept so the question can be asked later of a record that no longer has the
     * prices to answer it: how often a source's calls move on the session after they are printed,
     * how a month of stops was distributed, what a bad week actually looked like.
     *
     * **Derived data, stored anyway, and never read back for the screen.** The card is built from
     * the same recompute that builds the portfolio, so it cannot drift from the tabs around it;
     * this is an archive beside that, and if the two ever disagreed the derivation is right. That
     * is why the writer replaces a session wholesale rather than accumulating into it - a price
     * heal rewrites a stock's whole history, and rows written against the old prices have to go
     * with them rather than sit alongside their replacements.
     *
     * Device-local and never synced, for the reason `position_status_seen` and `price_events` are:
     * every phone fetches the same public feed and derives the same events from it, so shipping one
     * device's copy into another's would be duplicating a measurement rather than sharing a fact.
     */
    private fun SQLiteDatabase.createSessionEvents() = execSQL(
        """CREATE TABLE IF NOT EXISTS session_events (
            session_date TEXT NOT NULL,
            event_id TEXT NOT NULL,
            kind TEXT NOT NULL,
            ticker TEXT NOT NULL,
            channel TEXT,
            position_id TEXT NOT NULL,
            opened_on TEXT NOT NULL,
            return_pct REAL,
            price REAL,
            recorded_at INTEGER NOT NULL,
            PRIMARY KEY (session_date, event_id)
        )""",
    )

    /**
     * Replaces the stored digest of every session handed over, in one transaction.
     *
     * Whole sessions rather than individual rows, and that is the point of it. An event can stop
     * being true - a heal replaces a stock's series, a re-run reads a different stop off the same
     * card, a trade is deleted - and a writer that only ever inserted would leave the retraction
     * unrecorded. Deleting the session and writing what is true now cannot: whatever the derivation
     * no longer produces is gone.
     *
     * A session that yielded nothing is still cleared, which is how an event that has been undone
     * actually leaves. It is not written back as an empty row - absence is the record of a quiet
     * session, and the digest is derived rather than read from here.
     */
    fun saveSessionDigests(digests: List<SessionDigest>) {
        if (digests.isEmpty()) return
        val now = System.currentTimeMillis()
        writableDatabase.beginTransaction()
        try {
            digests.forEach { digest ->
                writableDatabase.delete(
                    "session_events",
                    "session_date = ?",
                    arrayOf(digest.date.toString()),
                )
                digest.events.forEach { event ->
                    writableDatabase.insertWithOnConflict(
                        "session_events",
                        null,
                        ContentValues().apply {
                            put("session_date", digest.date.toString())
                            put("event_id", event.id)
                            put("kind", event.kind.name)
                            put("ticker", event.ticker)
                            put("channel", event.channel)
                            put("position_id", event.positionId)
                            put("opened_on", event.openedOn.toString())
                            put("return_pct", event.returnPct)
                            put("price", event.price)
                            put("recorded_at", now)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /**
     * The stored events over a range of sessions, newest session first.
     *
     * Nothing on screen reads this - the card derives its own - so it exists for the question the
     * table was kept for. A row whose kind this build does not know is **dropped rather than
     * defaulted**, exactly as an unreadable `settled_calls` row is: a downgrade that cannot name an
     * event must not quietly file it as some other one.
     *
     * [newCalls] is absent from everything read back, because it was never written: the calls
     * themselves are the record of what was published, and a count beside them would be a second
     * copy free to disagree.
     */
    fun sessionEvents(from: LocalDate, to: LocalDate): List<SessionDigest> = readableDatabase
        .query(
            "session_events",
            null,
            "session_date >= ? AND session_date <= ?",
            arrayOf(from.toString(), to.toString()),
            null,
            null,
            "session_date DESC",
        )
        .use { cursor ->
            val byDate = linkedMapOf<LocalDate, MutableList<DayEvent>>()
            while (cursor.moveToNext()) {
                val date = cursor.nullableDate("session_date") ?: continue
                val kind = DayEventKind.from(cursor.getString(cursor.getColumnIndexOrThrow("kind")))
                    ?: continue
                val openedOn = cursor.nullableDate("opened_on") ?: continue
                byDate.getOrPut(date) { mutableListOf() } += DayEvent(
                    kind = kind,
                    ticker = cursor.getString(cursor.getColumnIndexOrThrow("ticker")),
                    channel = cursor.nullableString("channel"),
                    positionId = cursor.getString(cursor.getColumnIndexOrThrow("position_id")),
                    openedOn = openedOn,
                    returnPct = cursor.nullableDouble("return_pct"),
                    price = cursor.nullableDouble("price"),
                )
            }
            byDate.map { (date, events) -> SessionDigest(date = date, events = events) }
        }

    /**
     * A log of what [PriceHealth] has found, so a diagnostics copy carries more than the state the
     * phone happened to be in the moment it was saved.
     *
     * `PriceHealth.assess` is computed fresh on every recompute and was never stored - the screen
     * that drew it read the state and threw the reading away, and the one thing a diagnostic needs
     * that a live screen does not is history: the check where a stock went quiet, and the check
     * where it came back. This is that log, kept only in the database that `Save diagnostics`
     * already copies off a phone - no screen in the app reads it back.
     *
     * `feed_checks` is one row per **recorded** assessment, including the clean ones: a diagnostic
     * that only ever wrote rows when something was wrong could not tell "the app checked and found
     * nothing wrong" from "the app never checked", which is exactly the distinction the removed
     * Settings card existed to make visible. `feed_faults` is the detail behind a check that found
     * something, one row per faulty stock, keyed on `(checked_at, ticker)` so the two can be
     * joined without guessing.
     *
     * Local and never synced, for the reason `price_events` and `session_events` are: every phone
     * fetches the same public feed and reaches the same conclusion, so shipping one device's log
     * into another's would duplicate a measurement rather than share a fact.
     */
    private fun SQLiteDatabase.createFeedChecks() = execSQL(
        """CREATE TABLE IF NOT EXISTS feed_checks (
            checked_at INTEGER PRIMARY KEY,
            stocks_named INTEGER NOT NULL,
            faulty_stocks INTEGER NOT NULL,
            calls_held INTEGER NOT NULL
        )""",
    )

    private fun SQLiteDatabase.createFeedFaults() = execSQL(
        """CREATE TABLE IF NOT EXISTS feed_faults (
            checked_at INTEGER NOT NULL,
            ticker TEXT NOT NULL,
            faults TEXT NOT NULL,
            newest_session TEXT,
            age_days INTEGER,
            calls_held INTEGER NOT NULL,
            PRIMARY KEY (checked_at, ticker)
        )""",
    )

    /**
     * Records one assessment, but only if it says something the newest recorded one did not.
     *
     * A recompute happens on every price refresh - roughly every fifteen minutes while the market
     * is open - and writing a row every time would fill the log with two hundred identical
     * "everything is fine" entries and push out the one check that actually changed. Comparing
     * against the newest row rather than a remembered flag is what lets this share nothing with
     * `AppState.reviewFeedHealth`, which decides whether to *speak*: a spell that goes on for a
     * week is one notification and can still be many checks, each confirming the calls held has
     * moved as more of a source's calls fall inside the fault.
     */
    fun saveFeedHealth(health: PriceHealthReport, now: Long = System.currentTimeMillis()) {
        if (health.signature() == newestFeedCheckSignature()) return
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertWithOnConflict(
                "feed_checks",
                null,
                ContentValues().apply {
                    put("checked_at", now)
                    put("stocks_named", health.stocksNamed)
                    put("faulty_stocks", health.faults.size)
                    put("calls_held", health.callsHeld)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            health.faults.forEach { stock ->
                writableDatabase.insertWithOnConflict(
                    "feed_faults",
                    null,
                    ContentValues().apply {
                        put("checked_at", now)
                        put("ticker", stock.ticker)
                        put("faults", stock.faults.joinToString(",") { it.name })
                        put("newest_session", stock.newestSession?.toString())
                        put("age_days", stock.ageDays)
                        put("calls_held", stock.callsHeld)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            // Pruned inside the same transaction as the write it makes room for, so a log that
            // never changes is never opened for writing at all.
            val cutoff = writableDatabase.query(
                "feed_checks",
                arrayOf("checked_at"),
                null,
                null,
                null,
                null,
                "checked_at DESC",
                "$FEED_HEALTH_CHECKS_KEPT,1",
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
            if (cutoff != null) {
                writableDatabase.delete("feed_checks", "checked_at <= ?", arrayOf(cutoff.toString()))
                writableDatabase.delete("feed_faults", "checked_at <= ?", arrayOf(cutoff.toString()))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /** What the newest recorded check actually said, or null where nothing has been recorded yet. */
    private fun newestFeedCheckSignature(): String? {
        val newest = writableDatabase.query(
            "feed_checks",
            arrayOf("checked_at", "stocks_named"),
            null,
            null,
            null,
            null,
            "checked_at DESC",
            "1",
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getInt(1) else null }
            ?: return null
        val (checkedAt, stocksNamed) = newest
        val faults = writableDatabase.query(
            "feed_faults",
            arrayOf("ticker", "faults", "calls_held"),
            "checked_at = ?",
            arrayOf(checkedAt.toString()),
            null,
            null,
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(Triple(cursor.getString(0), cursor.getString(1), cursor.getInt(2)))
                }
            }
        }
        return faultSignature(stocksNamed, faults)
    }

    /**
     * One report and the newest recorded check, reduced to the same comparable string.
     *
     * Ticker order does not matter to what the reader is told, so both sides sort before joining -
     * without it a report whose faults merely came back in a different order from the same rows
     * would read as a change worth a new entry.
     */
    private fun PriceHealthReport.signature(): String = faultSignature(
        stocksNamed,
        faults.map { Triple(it.ticker, it.faults.joinToString(",") { f -> f.name }, it.callsHeld) },
    )

    private fun faultSignature(stocksNamed: Int, faults: List<Triple<String, String, Int>>): String =
        "$stocksNamed|" + faults.sortedBy { it.first }
            .joinToString(";") { (ticker, faultNames, held) -> "$ticker:$faultNames:$held" }

    /**
     * The log, newest first, for a diagnostics copy read off a device.
     *
     * Never called from a screen - the one line Settings prints is derived from `priceHealth` on
     * every recompute, exactly as it always was, so this table can never disagree with it. This
     * exists for the file `Save diagnostics` writes, where a reader wants the history the screen
     * has never kept.
     */
    fun feedHealthChecks(limit: Int = FEED_HEALTH_CHECKS_KEPT): List<FeedHealthCheck> {
        val checks = readableDatabase.query(
            "feed_checks",
            null,
            null,
            null,
            null,
            null,
            "checked_at DESC",
            limit.toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Triple(
                            cursor.getLong(cursor.getColumnIndexOrThrow("checked_at")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("stocks_named")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("calls_held")),
                        ),
                    )
                }
            }
        }
        return checks.map { (checkedAt, stocksNamed, callsHeld) ->
            val faults = readableDatabase.query(
                "feed_faults",
                null,
                "checked_at = ?",
                arrayOf(checkedAt.toString()),
                null,
                null,
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val faultNames = cursor.getString(cursor.getColumnIndexOrThrow("faults"))
                            .split(",")
                            .filter(String::isNotBlank)
                            .mapNotNull { name -> runCatching { FeedFault.valueOf(name) }.getOrNull() }
                            .toSet()
                        if (faultNames.isEmpty()) continue
                        add(
                            StockHealth(
                                ticker = cursor.getString(cursor.getColumnIndexOrThrow("ticker")),
                                faults = faultNames,
                                newestSession = cursor.nullableDate("newest_session"),
                                ageDays = cursor.nullableLong("age_days"),
                                callsHeld = cursor.getInt(cursor.getColumnIndexOrThrow("calls_held")),
                            ),
                        )
                    }
                }
            }
            FeedHealthCheck(
                checkedAt = Instant.ofEpochMilli(checkedAt),
                stocksNamed = stocksNamed,
                callsHeld = callsHeld,
                faults = faults,
            )
        }
    }

    /**
     * The whole table, which is one row per trade and read in full on every sweep.
     *
     * A status this build no longer knows the name of is dropped rather than crashing the read, and
     * the trade it belongs to is then seen as new - which announces nothing and records where it
     * now stands. Silence is the right answer to a row this version cannot interpret.
     */
    fun positionStatusSeen(): Map<String, TradeState> = readableDatabase
        .query("position_status_seen", null, null, null, null, null, null)
        .use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val stored = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                    val status = runCatching { PositionStatus.valueOf(stored) }.getOrNull()
                        ?: continue
                    put(
                        cursor.getString(cursor.getColumnIndexOrThrow("position_id")),
                        TradeState(
                            status = status,
                            open = cursor.getInt(cursor.getColumnIndexOrThrow("open")) == 1,
                        ),
                    )
                }
            }
        }

    /**
     * Writes down what has just been said, and forgets the trades that have gone.
     *
     * One transaction, because the two halves are one fact about one sweep. Only what moved is
     * written: a portfolio of fifty trades has a handful change on a busy day, and rewriting the
     * other forty-five would be forty-five writes to say nothing happened.
     */
    fun savePositionStatusSeen(seen: Map<String, TradeState>, forgotten: Set<String> = emptySet()) {
        if (seen.isEmpty() && forgotten.isEmpty()) return
        val now = System.currentTimeMillis()
        writableDatabase.beginTransaction()
        try {
            seen.forEach { (id, state) ->
                writableDatabase.insertWithOnConflict(
                    "position_status_seen",
                    null,
                    ContentValues().apply {
                        put("position_id", id)
                        put("status", state.status.name)
                        put("open", if (state.open) 1 else 0)
                        put("at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            forgotten.forEach {
                writableDatabase.delete("position_status_seen", "position_id = ?", arrayOf(it))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }


    private fun Cursor.nullableLong(column: String): Long? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getLong(it) }

    /** Internal rather than private so the migration test can open version 9 by the same name. */
    internal companion object {
        const val DATABASE_NAME = "egx_analyzer.db"
        /** How many [FeedHealthCheck]s are kept. A log with no ceiling is a file that keeps growing. */
        const val FEED_HEALTH_CHECKS_KEPT = 200
        /**
         * 27 is `feed_checks` and `feed_faults`, a log of what `PriceHealth` has found - kept
         * because the Settings card that used to explain a fault in words was removed and nothing
         * else on disk remembered what the app had noticed. Never read by a screen; see
         * `LocalDataStore.feedHealthChecks`.
         * 26 is `exit_price_1`, `exit_date_1`, `exit_price_2` and `exit_split_pct` on `positions`,
         * which say how a sale made in two parts was made up: half the holding at target 1 on one
         * day and the rest at target 2 on another. `exit_price` still holds the one price the
         * position closed at - the two weighted by the split - so nothing that reads a return had
         * to learn anything, and every trade recorded before this keeps all four null, which is
         * what a sale made at a single price is.
         * 25 is `position_approach_seen` and `session_digest_announced`, the two remaining things
         * the phone can say that nothing on disk remembered having said: where a trade stood
         * against its stop and its target 2 when the reader was last told, and which sessions'
         * digests have already been spoken. Both are the same shape of fact as
         * `position_status_seen` and for the same reason - a notification about a change is a
         * question about two readings, and only one of the two is derivable.
         * 24 is `is_t_plus_one` on `positions`, the one thing a trade knew when it was recorded and
         * then forgot: that the channel had printed it as a call to be out of by the next session.
         * The window it was given carried the consequence all along and never the reason.
         * 21 is `session_events`, what the market did to this record on each trading session. The
         * only thing behind the "what happened" card that is kept, and kept because it is the only
         * part of it nothing else on disk holds: the prices say where a stock stands, and a
         * *change* is a difference between two of those readings. Derived like everything else on
         * that card, written down for the questions that can only be asked of a history.
         * 20 is `settled_calls`, the verdict of a call the market has finished with - the second
         * target reached, the stop broken, or the first target banked and then given back. No
         * session after any of those can change it, so it is written down once and the call is
         * never replayed again. The only stored thing on this page that is otherwise derived, and
         * it is dropped rather than trusted whenever the prices underneath it are rewritten.
         * 19 is `call_alert_seen`, where each *untaken* call stood against its buy zone when the
         * user was last told - the same fact `position_status_seen` holds for trades, and needed
         * for the same reason: an alert about a crossing is a question about two readings, and
         * only one of them is derivable.
         * 18 is `position_status_seen`, what the user has already been told about each trade -
         * the one thing a notification about a *change* needs and the prices cannot supply.
         * 17 for a schedule that repeats inside a window - `trigger_every_minutes` and
         * `trigger_until` on `scheduled_jobs`. Both are gone: the table was dropped when the
         * job list collapsed into one analysis schedule and a checkbox, and fetching prices
         * through a session became a rule rather than something assembled out of a form. 16 was the findings an opinion carries - the news it found, what is scheduled ahead, and
         * what it thinks goes wrong - which are columns on `stock_opinions` rather than a table of
         * their own. 15 was `stock_opinions` itself, what Ask AI said about a call. 14 was
         * `scheduled_jobs`, the work
         * this phone runs on its own. 13 was `intraday_bars`
         * and `intraday_fetches`, which order the two events inside a session that daily figures
         * cannot separate. 12 was `price_events`, which records where a stock's prices changed
         * scale.
         *
         * `onUpgrade` fires only when the stored number is lower than this one, so adding a table
         * to a version that has already shipped anywhere reaches no device that has it.
         */
        const val DATABASE_VERSION = 27
    }
}

/**
 * One row of the retired job table, exactly as it was stored.
 *
 * Only the fields the migration needs to decide what a row meant: whether it was on, what it did,
 * and when. The name, the grace window and the outcome of its last fire are deliberately not here
 * - none of them survives into what replaced it.
 */
data class LegacyScheduleRow(
    val enabled: Boolean,
    val workKind: String,
    val triggerKind: String,
    val triggerAt: String,
    val workConfig: String,
)
