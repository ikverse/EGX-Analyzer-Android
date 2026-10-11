package com.ikverse.egxanalyzer.data

import android.content.Context
import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.PerformanceCalculator
import com.ikverse.egxanalyzer.model.ScheduleClock
import java.time.LocalDate

/**
 * The source ranking as a spreadsheet: one row per source, every figure the card prints.
 *
 * Built on the same writer the report export uses and, like it, ignorant of Android until the
 * last step. The order is the one `PerformanceCalculator.channelScores` produced - the floor first,
 * then what a call was worth - and the rank column is blank below the floor for the reason the
 * cards carry no number there: a position printed on a source the page says it cannot rank would
 * be the sheet ranking what it has just said it cannot.
 */
internal fun rankingSheet(
    channels: List<ChannelScore>,
    today: LocalDate = LocalDate.now(ScheduleClock.ZONE),
): Sheet {
    val header = CellStyle(bold = true)
    val rows = mutableListOf<List<Cell>>()
    rows += RankingColumns.map { Cell.Text(it.label, header) }
    var place = 0
    channels.forEach { channel ->
        val rank = if (channel.judged >= PerformanceCalculator.MINIMUM_JUDGED_TO_RANK) {
            place += 1
            place
        } else {
            null
        }
        rows += RankingColumns.map { column -> column.cell(channel, rank) }
    }
    return Sheet(
        name = "EGX sources $today",
        columns = RankingColumns.map { SheetColumn(it.width) },
        rows = rows,
        freezeRows = 1,
        freezeColumns = 2,
        autoFilter = true,
    )
}

/** What the file is called in Downloads. */
internal fun rankingFileName(today: LocalDate = LocalDate.now(ScheduleClock.ZONE)): String =
    "EGX-sources-$today.xlsx"

/** Writes the ranking into Downloads and returns what Downloads called it. */
internal fun saveRankingToDownloads(context: Context, channels: List<ChannelScore>): String =
    writeToDownloads(context, rankingFileName(), XLSX_MIME_TYPE) {
        it.write(writeXlsx(rankingSheet(channels)))
    }

private class RankingColumn(
    val label: String,
    val width: Double,
    val cell: (ChannelScore, Int?) -> Cell,
)

private fun number(value: Double?): Cell = value?.let { Cell.Number(it) } ?: Cell.Blank()
private fun count(value: Int): Cell = Cell.Number(value.toDouble())

private val RankingColumns: List<RankingColumn> = listOf(
    RankingColumn("Rank", 7.0) { _, rank -> rank?.let { Cell.Number(it.toDouble()) } ?: Cell.Blank() },
    RankingColumn("Source", 28.0) { c, _ -> Cell.Text(c.channel) },
    RankingColumn("Calls", 7.0) { c, _ -> count(c.calls) },
    RankingColumn("Judged", 8.0) { c, _ -> count(c.judged) },
    RankingColumn("Full hits", 9.0) { c, _ -> count(c.fullHits) },
    RankingColumn("Partial hits", 11.0) { c, _ -> count(c.partialHits) },
    RankingColumn("Stopped", 8.0) { c, _ -> count(c.stopped) },
    RankingColumn("Expired", 8.0) { c, _ -> count(c.expired) },
    RankingColumn("Not tradable", 11.0) { c, _ -> count(c.notTradable) },
    RankingColumn("Full-hit rate %", 13.0) { c, _ -> number(c.fullHitRate) },
    RankingColumn("Any-target rate %", 15.0) { c, _ -> number(c.anyTargetRate) },
    RankingColumn("Rate floor %", 12.0) { c, _ -> number(c.anyTargetRateFloor) },
    RankingColumn("Average return %", 15.0) { c, _ -> number(c.averageReturn) },
    RankingColumn("Discounted return", 16.0) { c, _ -> number(c.discountedReturn) },
    RankingColumn("Median sessions to hit", 20.0) { c, _ -> number(c.medianSessionsToHit) },
    RankingColumn("Median sessions to stop", 21.0) { c, _ -> number(c.medianSessionsToStop) },
    RankingColumn("Risk : reward", 13.0) { c, _ -> number(c.averageRiskReward) },
    RankingColumn("Repeats", 8.0) { c, _ -> count(c.repeats) },
    RankingColumn("Continuation %", 14.0) { c, _ -> number(c.continuationRate) },
    RankingColumn("Sell at T1 %", 12.0) { c, _ -> number(c.sellAtTarget1Return) },
    RankingColumn("Split %", 9.0) { c, _ -> number(c.splitReturn) },
)
