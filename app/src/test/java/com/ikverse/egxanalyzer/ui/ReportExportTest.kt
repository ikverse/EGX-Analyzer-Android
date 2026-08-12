package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.data.Cell
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.SourceTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * What the exported sheet says, as distinct from whether Excel will open it.
 *
 * The three ways this sheet deliberately differs from the table on screen are all here, because
 * each of them is a decision someone reading the file will notice: no stock heading rows, banding
 * by stock rather than by row, and entry split into a low and a high.
 */
class ReportExportTest {

    private val called = LocalDate.of(2026, 7, 20)

    private fun heading(label: String): Int =
        reportSheet(analysis()).rows.first().indexOfFirst { (it as Cell.Text).value == label }

    private fun rowFor(sheetRows: List<List<Cell>>, stock: String): List<Cell> =
        sheetRows.drop(1).first { (it[0] as Cell.Text).value == stock }

    @Test
    fun `the header names every column and the rows are the occurrences`() {
        val sheet = reportSheet(analysis())

        assertEquals(
            listOf(
                "Stock", "Name (AR)", "Name (EN)", "Source", "Timing",
                "Entry low", "Entry high", "Target 1", "TP1 %", "Target 2", "TP2 %",
                "Stop loss", "Risk %", "Support", "Resistance",
                "Target date", "Source date", "Notes",
            ),
            sheet.rows.first().map { (it as Cell.Text).value },
        )
        // Three occurrences across two stocks, and no heading row between them.
        assertEquals(4, sheet.rows.size)
    }

    @Test
    fun `no row is a stock heading, so the filter has nothing to trip over`() {
        val sheet = reportSheet(analysis())

        // Every data row carries a source and a timing. A heading row would have neither, and an
        // autofilter would either hide it or strand the stocks under it.
        sheet.rows.drop(1).forEach { row ->
            assertTrue((row[heading("Source")] as Cell.Text).value.isNotBlank())
            assertTrue((row[heading("Timing")] as Cell.Text).value.isNotBlank())
        }
    }

    @Test
    fun `every row names its own stock`() {
        val sheet = reportSheet(analysis())

        assertEquals(
            listOf("AMOC.CA", "AMOC.CA", "COMI.CA"),
            sheet.rows.drop(1).map { (it[0] as Cell.Text).value },
        )
    }

    @Test
    fun `banding follows the stock, not the row`() {
        val sheet = reportSheet(analysis())
        val fills = sheet.rows.drop(1).map { it[0].style.fill }

        // AMOC's two rows share one band and COMI, the second stock, takes the other. Striping by
        // row would have given AMOC's own two rows different fills.
        assertEquals(fills[0], fills[1])
        assertTrue(fills[1] != fills[2])
    }

    @Test
    fun `a stated entry range becomes a low and a high`() {
        val row = rowFor(reportSheet(analysis()).rows, "AMOC.CA")

        assertEquals(9.8, (row[heading("Entry low")] as Cell.Number).value, 0.0001)
        assertEquals(10.0, (row[heading("Entry high")] as Cell.Number).value, 0.0001)
    }

    @Test
    fun `a single stated price fills both entry columns`() {
        val sheet = reportSheet(analysis(entryLow = null, entryHigh = null, buyPrice = 9.9))
        val row = rowFor(sheet.rows, "AMOC.CA")

        assertEquals(9.9, (row[heading("Entry low")] as Cell.Number).value, 0.0001)
        assertEquals(9.9, (row[heading("Entry high")] as Cell.Number).value, 0.0001)
    }

    @Test
    fun `prices travel as numbers, so the column can be sorted and added up`() {
        val row = rowFor(reportSheet(analysis()).rows, "AMOC.CA")

        assertEquals(12.0, (row[heading("Target 1")] as Cell.Number).value, 0.0001)
        assertEquals(9.0, (row[heading("Stop loss")] as Cell.Number).value, 0.0001)
    }

    @Test
    fun `an absent figure is an empty cell rather than a dash`() {
        val row = rowFor(reportSheet(analysis()).rows, "AMOC.CA")

        // The table draws an em dash. A dash in a numeric column turns the whole column to text and
        // files under its own heading in the filter dropdown.
        assertTrue(row[heading("Support")] is Cell.Blank)
        assertTrue(row[heading("Resistance")] is Cell.Blank)
    }

    @Test
    fun `a return the source never printed is worked out and marked as derived`() {
        val sheet = reportSheet(analysis())
        val row = rowFor(sheet.rows, "AMOC.CA")
        val stated = rowFor(reportSheet(analysis(returnTp1Pct = 21.0)).rows, "AMOC.CA")

        // Entry midpoint 9.9 to target 12.0 is 21.2%, the same basis the table and the scorer use.
        assertEquals(21.2, (row[heading("TP1 %")] as Cell.Number).value, 0.05)
        // Derived is drawn softer than a figure the channel published, which keeps the target's
        // own green outright.
        assertTrue(row[heading("TP1 %")].style.colour != stated[heading("TP1 %")].style.colour)
        // ...but not in the grey the notes and dates are drawn in. Most rows are derived, so a grey
        // for those put two unrelated hues in one column and made a return read as context.
        assertTrue(row[heading("TP1 %")].style.colour != row[heading("Notes")].style.colour)
        // The target's own green at 60%, mixed onto the white a spreadsheet is read on because an
        // xlsx font colour carries no alpha. Held outright: a blend reading the wrong bytes is
        // still a colour, and every other assertion here would pass on one.
        assertEquals("FF71A48B", row[heading("TP1 %")].style.colour)
    }

    @Test
    fun `the source column carries the channel name the filter is read by`() {
        val row = rowFor(reportSheet(analysis()).rows, "COMI.CA")

        assertEquals("Second channel", (row[heading("Source")] as Cell.Text).value)
    }

    @Test
    fun `the target date is a date and the printed source date stays text`() {
        val row = rowFor(reportSheet(analysis()).rows, "AMOC.CA")

        assertEquals(called, (row[heading("Target date")] as Cell.Date).value)
        // Whatever the channel printed, in the form it printed it. Reading it as a date would
        // invent a precision the source never had.
        assertEquals("٢٠ يوليو", (row[heading("Source date")] as Cell.Text).value)
    }

    @Test
    fun `the sheet is filterable and pinned where the table pins it`() {
        val sheet = reportSheet(analysis())

        assertTrue(sheet.autoFilter)
        assertEquals(1, sheet.freezeRows)
        assertEquals(1, sheet.freezeColumns)
        assertEquals(18, sheet.columns.size)
    }

    @Test
    fun `the file is named for the session the report is about`() {
        assertEquals("EGX-analysis-2026-07-20.xlsx", exportFileName(analysis()))
    }

    @Test
    fun `a run with no recorded target falls back to the day it ran`() {
        val name = exportFileName(analysis(targetDate = null))

        // The run completed on 2026-07-20T09:00:00Z; the fallback is that day in the device's zone.
        assertTrue(name.startsWith("EGX-analysis-2026-07-"))
        assertTrue(name.endsWith(".xlsx"))
    }

    @Test
    fun `a report holding no stocks produces a header and nothing else`() {
        // The screen refuses this one before it gets here, but a sheet that quietly grew a row out
        // of nothing would be the harder fault to find.
        val sheet = reportSheet(analysis(stocks = false))

        assertEquals(1, sheet.rows.size)
        assertEquals("Stock", (sheet.rows.single().first() as Cell.Text).value)
    }

    private fun analysis(
        entryLow: Double? = 9.8,
        entryHigh: Double? = 10.0,
        buyPrice: Double? = null,
        returnTp1Pct: Double? = null,
        targetDate: LocalDate? = called,
        stocks: Boolean = true,
    ) = SavedAnalysis(
        id = 1,
        provider = CloudProvider.QWEN,
        model = "test-model",
        result = AnalysisResult(
            requestId = "request-1",
            recommendations = emptyList(),
            recommendationTargetDate = targetDate,
            completedAt = Instant.parse("2026-07-20T09:00:00Z"),
            inquiryReplyCount = 0,
            sources = listOf(
                trace("source-a", 1, "First channel", 42),
                trace("source-b", 2, "Second channel", 43),
            ),
            consolidated = if (!stocks) {
                emptyList()
            } else {
                listOf(
                    ConsolidatedRecommendation(
                        stockCode = "AMOC.CA",
                        stockNameEnglish = "Alexandria Mineral Oils",
                        stockNameArabic = "اموك",
                        mentionCount = 2,
                        rank = 1,
                        notesSummary = null,
                        dataPoints = listOf(
                            point("42", entryLow, entryHigh, buyPrice, returnTp1Pct),
                            point("43", entryLow, entryHigh, buyPrice, returnTp1Pct),
                        ),
                    ),
                    ConsolidatedRecommendation(
                        stockCode = "COMI.CA",
                        stockNameEnglish = "Commercial International Bank",
                        stockNameArabic = "البنك التجاري الدولي",
                        mentionCount = 1,
                        rank = 2,
                        notesSummary = null,
                        dataPoints = listOf(point("43", entryLow, entryHigh, buyPrice, returnTp1Pct)),
                    ),
                )
            },
        ),
    )

    private fun trace(sourceId: String, channelId: Long, name: String, messageId: Long) = SourceTrace(
        sourceId = sourceId,
        channelId = channelId,
        channelName = name,
        messageId = messageId,
        timestamp = Instant.parse("2026-07-20T10:00:00Z"),
        contentType = AnalysisContentType.TEXT,
        preview = "",
    )

    private fun point(
        messageId: String,
        entryLow: Double?,
        entryHigh: Double?,
        buyPrice: Double?,
        returnTp1Pct: Double?,
    ) = RecommendationDataPoint(
        date = called,
        effectiveDateBasis = "explicit_date",
        visibleSourceDate = "٢٠ يوليو",
        dateEvidence = null,
        timingEvidence = null,
        sourceMessageId = messageId,
        sourceImageRef = 1,
        recommendationEvidence = null,
        recommendationType = "buy",
        buyPrice = buyPrice,
        buyPriceLow = entryLow,
        buyPriceHigh = entryHigh,
        target1 = 12.0,
        returnTp1Pct = returnTp1Pct,
        target2 = 14.0,
        returnTp2Pct = null,
        stopLoss = 9.0,
        support = null,
        resistance = null,
        riskPct = -8.2,
        notesArabic = "مستهدف أول ١٢",
    )
}
