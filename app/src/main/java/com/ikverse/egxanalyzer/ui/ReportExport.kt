package com.ikverse.egxanalyzer.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ikverse.egxanalyzer.data.Cell
import com.ikverse.egxanalyzer.data.CellAlign
import com.ikverse.egxanalyzer.data.CellStyle
import com.ikverse.egxanalyzer.data.Sheet
import com.ikverse.egxanalyzer.data.SheetColumn
import com.ikverse.egxanalyzer.data.XLSX_MIME_TYPE
import com.ikverse.egxanalyzer.data.writeXlsx
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.SavedAnalysis
import java.io.File
import java.time.ZoneId

/**
 * A saved report as a spreadsheet, in the colours the table draws it in.
 *
 * The sheet is the same table read the same way - what kind of call it is, what it asks you to pay,
 * what it is worth, what it risks, then the context behind it - with three deliberate differences,
 * each of them forced by the one thing a table on screen cannot do and a spreadsheet must:
 *
 * - **No stock heading rows.** An autofilter needs uniform rows under one header, and a heading is
 *   not a row of data - filtering would hide it and leave its stocks orphaned under whichever
 *   heading survived. The stock's code and both its names lead every row instead, so a row still
 *   says which stock it belongs to when it is the only one left on screen.
 * - **Banding by stock rather than by row.** With the headings gone, a tint per stock is what still
 *   shows where one stock ends and the next begins. The table's alternating stripe would say nothing
 *   at all once a filter has hidden half the rows it was counting.
 * - **Entry as low and high.** The table prints `1.2 – 1.35` in one cell, which Excel can neither
 *   sort nor add up, and rows with a single stated price would land beside it as numbers and turn
 *   the column to text. A single price fills both columns, which is what the scorer reads anyway.
 *
 * Every column is written, including the context and notes that the on-screen table drops below
 * 620dp and 900dp: a sheet has no width to run out of.
 */
internal fun reportSheet(saved: SavedAnalysis): Sheet {
    // The model cites sources by Telegram id and the channel name lives on the stored trace, which
    // is the same lookup the table's pinned column does.
    val channelNames = saved.result.sources
        .filter { it.messageId != null }
        .associate { it.messageId.toString() to it.channelName }

    val rows = mutableListOf<List<Cell>>()
    rows += Columns.map { Cell.Text(it.label, HeaderStyle.copy(align = it.align)) }
    saved.result.consolidated.forEachIndexed { index, stock ->
        // Alternate stocks, not alternate rows: the band has to survive a filter that hides some of
        // the rows under it, and a stock's own rows must stay one block.
        val band = if (index % 2 == 1) BandFill else null
        stock.dataPoints.forEach { point ->
            rows += Columns.map { column ->
                column.cell(stock, point, channelNames).tinted(band)
            }
        }
    }

    return Sheet(
        name = "EGX ${saved.result.recommendationTargetDate ?: "analysis"}",
        columns = Columns.map { SheetColumn(it.width) },
        rows = rows,
        // The header, and the stock the row belongs to: the same two things the table pins, for the
        // same reason. Eighteen columns is more than a screen holds either way.
        freezeRows = 1,
        freezeColumns = 1,
        autoFilter = true,
    )
}

/** What the export is called on disk and in whatever it is sent to. */
internal fun exportFileName(saved: SavedAnalysis): String {
    val date = saved.result.recommendationTargetDate
        ?: saved.result.completedAt.atZone(ZoneId.systemDefault()).toLocalDate()
    return "EGX-analysis-$date.xlsx"
}

/**
 * Writes the export and hands back the file.
 *
 * The directory is emptied first. Every export is a fresh reading of a report that is already on
 * disk, so keeping the last one buys nothing and lets a private directory grow without bound.
 */
internal fun writeExport(context: Context, saved: SavedAnalysis): File {
    val directory = File(context.filesDir, EXPORT_DIRECTORY)
    directory.mkdirs()
    directory.listFiles()?.forEach { it.delete() }
    val file = File(directory, exportFileName(saved))
    file.writeBytes(writeXlsx(reportSheet(saved)))
    return file
}

/**
 * Offers the written file to whatever the user picks.
 *
 * Through a provider of its own rather than another path on the trace provider, for the reason the
 * manifest already gives twice: an authority whose name lies about what it carries is how the wrong
 * file gets granted to the wrong app.
 */
internal fun exportIntent(context: Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}$EXPORT_AUTHORITY_SUFFIX", file)
    return Intent(Intent.ACTION_SEND)
        .setType(XLSX_MIME_TYPE)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, file.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

private const val EXPORT_DIRECTORY = "exports"
private const val EXPORT_AUTHORITY_SUFFIX = ".exports"

/** One column: its heading, how wide, how aligned, and how to draw a cell for one occurrence. */
private class ExportColumn(
    val label: String,
    val width: Double,
    val align: CellAlign,
    val cell: (
        stock: ConsolidatedRecommendation,
        point: RecommendationDataPoint,
        channels: Map<String, String>,
    ) -> Cell,
)

/**
 * The columns, in the order a row is read.
 *
 * The stock leads because it is the row's anchor once the heading rows are gone; the rest follow
 * the table exactly.
 */
private val Columns: List<ExportColumn> = listOf(
    ExportColumn("Stock", 12.0, CellAlign.START) { stock, _, _ ->
        Cell.Text(stock.stockCode, CellStyle(colour = Primary, bold = true))
    },
    ExportColumn("Name (AR)", 26.0, CellAlign.START) { stock, _, _ ->
        Cell.Text(stock.stockNameArabic.orEmpty(), CellStyle(colour = OnSurface))
    },
    // Dimmer than the Arabic name, as the table draws it: the model supplies this one from its own
    // knowledge rather than reading it off the source, and it is regularly wrong.
    ExportColumn("Name (EN)", 26.0, CellAlign.START) { stock, _, _ ->
        Cell.Text(stock.stockNameEnglish.orEmpty(), CellStyle(colour = Muted))
    },
    ExportColumn("Source", 20.0, CellAlign.START) { _, point, channels ->
        Cell.Text(
            channels[point.sourceMessageId] ?: point.sourceMessageId.orEmpty(),
            CellStyle(colour = OnSurface),
        )
    },
    ExportColumn("Timing", 14.0, CellAlign.START) { _, point, _ ->
        Cell.Text(timing(point).orEmpty(), CellStyle(colour = Muted))
    },
    ExportColumn("Entry low", 12.0, CellAlign.END) { _, point, _ ->
        price(point.buyPriceLow ?: point.buyPrice, OnSurface, bold = true)
    },
    ExportColumn("Entry high", 12.0, CellAlign.END) { _, point, _ ->
        price(point.buyPriceHigh ?: point.buyPrice, OnSurface, bold = true)
    },
    ExportColumn("Target 1", 11.0, CellAlign.END) { _, point, _ -> price(point.target1, Target) },
    ExportColumn("TP1 %", 10.0, CellAlign.END) { _, point, _ ->
        returnCell(point, point.returnTp1Pct, point.target1)
    },
    ExportColumn("Target 2", 11.0, CellAlign.END) { _, point, _ -> price(point.target2, Target) },
    ExportColumn("TP2 %", 10.0, CellAlign.END) { _, point, _ ->
        returnCell(point, point.returnTp2Pct, point.target2)
    },
    ExportColumn("Stop loss", 11.0, CellAlign.END) { _, point, _ -> price(point.stopLoss, Stop) },
    ExportColumn("Risk %", 10.0, CellAlign.END) { _, point, _ ->
        percent(point.riskPct, Stop)
    },
    ExportColumn("Support", 11.0, CellAlign.END) { _, point, _ -> price(point.support, Market) },
    ExportColumn("Resistance", 12.0, CellAlign.END) { _, point, _ -> price(point.resistance, Market) },
    ExportColumn("Target date", 13.0, CellAlign.START) { _, point, _ ->
        point.date?.let { Cell.Date(it, CellStyle(colour = Muted, format = DateFormat)) }
            ?: Cell.Blank()
    },
    // Text, not a date: this is what was printed on the card, in whatever form the channel printed
    // it, and reading it as a date would invent a precision the source never had.
    ExportColumn("Source date", 14.0, CellAlign.START) { _, point, _ ->
        Cell.Text(point.visibleSourceDate.orEmpty(), CellStyle(colour = Muted))
    },
    ExportColumn("Notes", 60.0, CellAlign.START) { _, point, _ ->
        Cell.Text(point.notesArabic.orEmpty(), CellStyle(colour = Muted))
    },
)

private fun price(value: Double?, colour: String, bold: Boolean = false): Cell =
    value?.let { Cell.Number(it, CellStyle(colour = colour, bold = bold, format = PriceFormat, align = CellAlign.END)) }
        ?: Cell.Blank()

private fun percent(value: Double?, colour: String): Cell =
    value?.let { Cell.Number(it, CellStyle(colour = colour, format = PercentFormat, align = CellAlign.END)) }
        ?: Cell.Blank()

/**
 * A return percentage, worked out where the source did not print one.
 *
 * The same rule the table's own cell follows: a derived figure is drawn muted, because a figure the
 * app calculated and one a channel published are not the same claim.
 */
private fun returnCell(point: RecommendationDataPoint, stated: Double?, target: Double?): Cell {
    val value = stated ?: returnFrom(point, target)
    val colour = when {
        stated == null -> Muted
        value == null -> Muted
        value > 0 -> Target
        value < 0 -> Stop
        else -> OnSurface
    }
    return percent(value, colour)
}

/** Puts a stock's band behind a cell without disturbing anything else about how it is drawn. */
private fun Cell.tinted(fill: String?): Cell {
    if (fill == null) return this
    val banded = style.copy(fill = fill)
    return when (this) {
        is Cell.Text -> copy(style = banded)
        is Cell.Number -> copy(style = banded)
        is Cell.Date -> copy(style = banded)
        is Cell.Blank -> copy(style = banded)
    }
}

/**
 * The palette, as ARGB.
 *
 * The light scheme from `theme/Theme.kt`, because a spreadsheet is read on a white page whatever
 * the phone's theme is set to. The roles are the app's own and mean here exactly what they mean
 * there: green is a target, red is a stop, cyan is a price the market reached, grey is context.
 * `ReportExportTest` holds these against `LightColors` so the two cannot drift apart in silence.
 */
private const val Primary = "FF00697A"
private const val OnSurface = "FF181C20"
private const val Muted = "FF41484F"
private const val Target = "FF13683D"
private const val Stop = "FFB3261E"
private const val HeaderFill = "FFE5EAF0"
private const val BandFill = "FFF1F4F8"

/** A price the market reached rather than one a channel chose. */
private const val Market = Primary

private val HeaderStyle = CellStyle(colour = Muted, bold = true, fill = HeaderFill)

/**
 * Up to three decimals, trailing zeros dropped - the same reading `formatPrice` gives.
 *
 * EGX trades plenty of stocks below one pound, so two decimals is not enough, and no source prints
 * more than three.
 */
private const val PriceFormat = "0.###"

/** `+12.5%`, `-3%`, `0%`: one optional decimal, signed, which is what `formatPercent` prints. */
private const val PercentFormat = "\"+\"0.#\"%\";\"-\"0.#\"%\";0\"%\""

/** ISO, as the table prints it. The hyphens are escaped or Excel reads them as its own separator. */
private const val DateFormat = "yyyy\\-mm\\-dd"
