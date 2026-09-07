package com.ikverse.egxanalyzer.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ikverse.egxanalyzer.model.PriceSeriesSummary
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One five-minute bar as it is kept, which is a bar and not a touch record. */
data class PriceBar(
    val ticker: String,
    val at: Instant,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val close: Double?,
    val volume: Double?,
)

/**
 * The five-minute record of every session this phone has managed to copy, kept for its own sake.
 *
 * **Its own database file, and that is the design rather than a detail of it.** `egx_analyzer.db`
 * is the record: it is zipped whole into every backup, seven of which are kept, and copied whole by
 * Save diagnostics. This table is an order of magnitude larger than everything in that file put
 * together - measured at about 86 MB a year against the 6 MB the whole record occupies after two -
 * so a table inside it would have turned a daily backup into a daily 86 MB write to somebody
 * else's cloud folder. A separate file is excluded from both by construction: no flag to set,
 * nothing to remember, and no way for a later change to either of them to quietly start carrying
 * it.
 *
 * The trade is stated plainly on the screen that offers this: what is here is **not** in a backup,
 * so a lost phone loses it. That is survivable in a way the alternative is not, because none of it
 * is evidence - nothing in the app reads this table, no figure rests on it, and losing it costs a
 * research archive rather than the record of what anybody recommended.
 *
 * **Why it cannot simply be refetched, unlike every other price here.** `Backup.kt` can say prices
 * "are not excluded, only unimportant" because the daily feed serves years. The five-minute feed
 * serves about two months and then the bars are gone, from everyone, permanently. A session not
 * copied inside that window cannot be recovered by any later request, which is the whole reason
 * this runs on a clock instead of when somebody asks.
 *
 * Deliberately **not** `intraday_bars` in the main store. That table holds a high and a low for the
 * handful of sessions a call could not be ordered on, is read by the scorer on every recompute, and
 * is small because it is answering a question. Widening it into an archive would put a million rows
 * nobody scores in front of the query that scores everybody.
 */
class PriceSeriesStore(context: Context, name: String = DATABASE_NAME) :
    SQLiteOpenHelper(context, name, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        // WITHOUT ROWID, and it halves the file. The primary key is the whole of a row's identity
        // and every read is by it, so the hidden rowid and the second b-tree keyed on it are pure
        // overhead - measured at 61 bytes a row against 122 for the same columns with a rowid and a
        // session_date index beside them. On a table sized in millions that is the difference
        // between 86 MB a year and 172.
        //
        // No session_date column for the same reason: it is `bar_at` read in UTC, derivable in one
        // expression, and storing it would spend a text field on every one of a million rows to
        // save an arithmetic the export does per row anyway.
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS price_bars (
                ticker TEXT NOT NULL,
                bar_at INTEGER NOT NULL,
                open REAL,
                high REAL,
                low REAL,
                close REAL,
                volume REAL,
                PRIMARY KEY (ticker, bar_at)
            ) WITHOUT ROWID""",
        )
        // How far each stock has been copied, which max(bar_at) cannot answer. A session the feed
        // genuinely has nothing for - a holiday it omits, a stock suspended for a week - comes back
        // with no bars, so a mark derived from the newest stored bar would never pass it and every
        // harvest from then on would ask about the same empty days forever. This advances on a
        // request that was answered, whatever the answer turned out to contain.
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS harvest_marks (
                ticker TEXT NOT NULL PRIMARY KEY,
                through TEXT NOT NULL
            )""",
        )
    }

    /**
     * Nothing to migrate yet, and a drop would be the wrong reflex if there ever is.
     *
     * Bars here cannot be refetched once they age past the feed's window, so the usual "rebuild it
     * from the source" escape every other table in this app has does not exist for this one. Any
     * future version has to carry the rows across rather than start again.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** How far each stock has been copied, for the harvest to resume from. */
    fun harvestedThrough(): Map<String, LocalDate> = readableDatabase
        .query("harvest_marks", arrayOf("ticker", "through"), null, null, null, null, null)
        .use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val date = runCatching { LocalDate.parse(cursor.getString(1)) }.getOrNull()
                    if (date != null) put(cursor.getString(0), date)
                }
            }
        }

    /**
     * Stores one stock's bars and moves its mark, as one transaction.
     *
     * Both or neither: a mark written without the bars under it would skip those sessions for good
     * on the next harvest, and the feed will not serve them a second time.
     *
     * `CONFLICT_REPLACE` because a harvest may legitimately re-cover a session it already holds -
     * the range starts the day after the mark, but the feed answers in whole days and a phone whose
     * clock moved can ask for one twice. A bar is the same bar whenever it was fetched.
     */
    fun saveBars(ticker: String, bars: List<PriceBar>, through: LocalDate) {
        val database = writableDatabase
        database.beginTransaction()
        try {
            bars.forEach { bar ->
                database.insertWithOnConflict(
                    "price_bars",
                    null,
                    ContentValues().apply {
                        put("ticker", ticker)
                        put("bar_at", bar.at.epochSecond)
                        put("open", bar.open)
                        put("high", bar.high)
                        put("low", bar.low)
                        put("close", bar.close)
                        put("volume", bar.volume)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            database.insertWithOnConflict(
                "harvest_marks",
                null,
                ContentValues().apply {
                    put("ticker", ticker)
                    put("through", through.toString())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    /** How much is here, for the one line on screen that says whether this is working. */
    fun summary(): PriceSeriesSummary = readableDatabase
        .rawQuery(
            "SELECT COUNT(*), COUNT(DISTINCT ticker), MIN(bar_at), MAX(bar_at) FROM price_bars",
            null,
        )
        .use { cursor ->
            if (!cursor.moveToFirst() || cursor.getLong(0) == 0L) {
                PriceSeriesSummary.EMPTY
            } else {
                PriceSeriesSummary(
                    bars = cursor.getLong(0),
                    stocks = cursor.getInt(1),
                    from = sessionDateOf(cursor.getLong(2)),
                    through = sessionDateOf(cursor.getLong(3)),
                )
            }
        }

    /**
     * Every bar in order, handed over one at a time.
     *
     * A callback rather than a list, because the caller is writing a CSV of something that passes a
     * million rows inside a year, and materialising that as objects first would be an
     * out-of-memory on the one kind of device this ever runs on.
     */
    fun forEachBar(action: (PriceBar) -> Unit) {
        readableDatabase
            .query(
                "price_bars",
                arrayOf("ticker", "bar_at", "open", "high", "low", "close", "volume"),
                null,
                null,
                null,
                null,
                "ticker, bar_at",
            )
            .use { cursor ->
                while (cursor.moveToNext()) {
                    action(
                        PriceBar(
                            ticker = cursor.getString(0),
                            at = Instant.ofEpochSecond(cursor.getLong(1)),
                            open = cursor.value(2),
                            high = cursor.value(3),
                            low = cursor.value(4),
                            close = cursor.value(5),
                            volume = cursor.value(6),
                        ),
                    )
                }
            }
    }

    private fun Cursor.value(index: Int): Double? =
        if (isNull(index)) null else getDouble(index)

    /** Folds the write-ahead log back in, for the reason the main store exposes one. */
    fun checkpoint() {
        runCatching {
            writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)
                .use { it.moveToFirst() }
        }
    }

    /** Where the file sits, so the screen can say how large it has grown. */
    fun databaseFile(): File = File(writableDatabase.path)

    internal companion object {
        const val DATABASE_NAME = "egx_price_series.db"
        const val DATABASE_VERSION = 1

        /**
         * The zone a bar's date is read in.
         *
         * UTC, matching `DailyFromIntraday.aggregate` and `PriceRepository.parseChart`, so a bar
         * and the daily session it belongs to can never be filed under different dates. EGX trades
         * 10:00-14:30 Cairo, which is the middle of a UTC day whatever the offset is doing, so no
         * session is split across two dates by the choice.
         */
        val ZONE: ZoneId = ZoneId.of("UTC")

        fun sessionDateOf(epochSecond: Long): LocalDate =
            Instant.ofEpochSecond(epochSecond).atZone(ZONE).toLocalDate()
    }
}
