package com.ikverse.egxanalyzer.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.AnalysisDiagnostics
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.DailySession
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
        db.addOpenColumn()
    }

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
     * Which chats the user has picked.
     *
     * Only the choice is kept, never the chat list itself - Telegram is the authority on which
     * chats exist, so a stored list would go stale the moment one is left or renamed.
     */
    fun selectedChannelIds(): Set<Long> = readableDatabase.query(
        "channels",
        arrayOf("id"),
        "selected != 0",
        null,
        null,
        null,
        null,
    ).use { cursor ->
        buildSet {
            while (cursor.moveToNext()) add(cursor.getLong(0))
        }
    }

    fun setChannelSelected(channel: ChannelSelection) {
        if (channel.selected) {
            writableDatabase.insertWithOnConflict(
                "channels",
                null,
                ContentValues().apply {
                    put("id", channel.id)
                    put("name", channel.name.trim())
                    put("selected", 1)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } else {
            removeChannel(channel.id)
        }
    }

    fun removeChannel(id: Long) {
        writableDatabase.delete("channels", "id = ?", arrayOf(id.toString()))
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

    fun results(): List<SavedAnalysis> = readableDatabase.query(
        "analyses",
        arrayOf("id", "provider", "model", "payload"),
        null,
        null,
        null,
        null,
        "completed_at DESC",
    ).use { cursor ->
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
                }
            }
        }
    }

    fun deleteResult(id: Long) {
        writableDatabase.delete("analyses", "id = ?", arrayOf(id.toString()))
    }

    fun deleteAllResults() {
        writableDatabase.delete("analyses", null, null)
    }

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
            put("validationWarnings", JSONArray(diagnostics.validationWarnings))
            put("excludedSources", JSONArray().apply {
                diagnostics.excludedSources.forEach {
                    put(JSONObject().put("sourceId", it.sourceId).put("reason", it.reason))
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
                validationWarnings = value.optJSONArray("validationWarnings")?.strings().orEmpty(),
                excludedSources = value.optJSONArray("excludedSources")?.objects()?.map {
                    ExcludedSource(it.optString("sourceId"), it.optString("reason"))
                }.orEmpty(),
            )
        } ?: AnalysisDiagnostics(),
        imagePaths = optJSONArray("imagePaths")?.strings().orEmpty(),
        rawResponse = optString("rawResponse"),
        // Rebuilt from the stored response rather than persisted separately, so the nested
        // occurrences can never drift from the response they came from - and analyses saved before
        // this existed still gain them. Responses predating the consolidated contract yield none.
        consolidated = runCatching { ConsolidatedParser.parse(optString("rawResponse")) }
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
        sources = getJSONArray("sources").objects().map { item ->
            SourceTrace(
                sourceId = item.getString("sourceId"),
                channelId = item.nullableLong("channelId"),
                channelName = item.getString("channelName"),
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

    private companion object {
        const val DATABASE_NAME = "egx_analyzer.db"
        const val DATABASE_VERSION = 4
    }
}
