package com.ikverse.egxanalyzer.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.RecommendationResult
import com.ikverse.egxanalyzer.model.ExcludedSource
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.SourceTrace
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

class LocalDataStore(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

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
        db.createPendingDeletions()
        db.createWordingRules()
        db.createPromptVersions()
        db.createPositions()
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
        db.createPendingDeletions()
        db.createWordingRules()
        db.createPromptVersions()
        db.createPositions()
        db.addPositionRevisionColumns()
        db.addPositionWindowColumns()
        db.addOpenColumn()
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
                put("closed_manually", if (position.closedManually) 1 else 0)
                put("entry_low", position.entryLow)
                put("entry_high", position.entryHigh)
                put("target1", position.target1)
                put("target2", position.target2)
                put("stop_loss", position.stopLoss)
                put("window_sessions", position.windowSessions)
                put("window_custom", if (position.windowCustom) 1 else 0)
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
        closedManually = getInt(getColumnIndexOrThrow("closed_manually")) == 1,
        entryLow = nullableDouble(getColumnIndexOrThrow("entry_low")),
        entryHigh = nullableDouble(getColumnIndexOrThrow("entry_high")),
        target1 = nullableDouble(getColumnIndexOrThrow("target1")),
        target2 = nullableDouble(getColumnIndexOrThrow("target2")),
        stopLoss = nullableDouble(getColumnIndexOrThrow("stop_loss")),
        windowSessions = getInt(getColumnIndexOrThrow("window_sessions")),
        windowCustom = getInt(getColumnIndexOrThrow("window_custom")) == 1,
        keepOpen = getInt(getColumnIndexOrThrow("keep_open")) == 1,
        keepOpenNote = nullableString("keep_open_note"),
        openedAt = Instant.ofEpochMilli(getLong(getColumnIndexOrThrow("opened_at"))),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        updatedBy = getString(getColumnIndexOrThrow("updated_by")),
    )

    private fun Cursor.nullableString(column: String): String? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }

    /**
     * The most recent close stored for a stock, which is what the app can call its current price.
     *
     * The daily feed is the only thing that writes prices here, so "current" means the last session
     * that has settled rather than a live quote. A position's return moves once a day, deliberately:
     * a figure that changed while nothing had traded would be invented.
     */
    fun latestClose(ticker: String): Double? = readableDatabase.query(
        "daily_prices",
        arrayOf("close"),
        "ticker = ? AND close IS NOT NULL AND close > 0",
        arrayOf(ticker),
        null,
        null,
        "session_date DESC",
        "1",
    ).use { cursor -> if (cursor.moveToFirst()) cursor.nullableDouble(0) else null }

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

    /** Sessions for one stock from the day a call was made onward, oldest first. */
    fun sessionsFrom(ticker: String, from: LocalDate): List<DailySession> = readableDatabase.query(
        "daily_prices",
        arrayOf("ticker", "session_date", "high", "low", "close", "volume", "open"),
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

    /** Removes a report by the identity that travels between devices. */
    fun deleteResultByRequestId(requestId: String) {
        writableDatabase.delete("analyses", "request_id = ?", arrayOf(requestId))
    }

    fun deleteResult(id: Long) {
        writableDatabase.delete("analyses", "id = ?", arrayOf(id.toString()))
    }

    fun deleteAllResults() {
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

    /** Internal rather than private so the migration test can open version 9 by the same name. */
    internal companion object {
        const val DATABASE_NAME = "egx_analyzer.db"
        /**
         * 11 rather than 10 because a phone already ran a 10.
         *
         * `onUpgrade` fires only when the stored number is lower than this one, so adding columns
         * to a version that has already shipped anywhere reaches no device that has it.
         */
        const val DATABASE_VERSION = 11
    }
}
