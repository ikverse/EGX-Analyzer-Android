package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.SourceTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ConsolidatedParserTest {

    private fun trace(sourceId: String, messageId: Long, channel: String) = SourceTrace(
        sourceId = sourceId,
        channelId = 1L,
        channelName = channel,
        messageId = messageId,
        timestamp = Instant.parse("2026-07-29T06:00:00Z"),
        contentType = AnalysisContentType.IMAGES,
        preview = "",
    )

    private val response = """
        {
          "top_consolidated_recommendations": [
            {
              "stock_code": "SCEM.CA",
              "stock_name_en": null,
              "stock_name_ar": "اسمنت سيناء",
              "mention_count": 2,
              "rank": 1,
              "notes_summary": "ملخص",
              "data_points": [
                {
                  "date": "2026-07-29",
                  "effective_date_basis": "watching",
                  "visible_source_date": "29 JULY 2026",
                  "timing_evidence": "سهم المراقبة",
                  "source_message_id": "60397",
                  "source_image_ref": 16,
                  "recommendation_type": "buy",
                  "buy_price_low": 86.5,
                  "buy_price_high": 86.61,
                  "target_1": 91.0,
                  "return_tp1_pct": 5.2,
                  "target_2": 99.0,
                  "stop_loss": 82.5,
                  "support": 85.5,
                  "resistance": 87.0,
                  "risk_pct": -4.62,
                  "notes_ar": "ملاحظة"
                },
                {
                  "date": "2026-07-29",
                  "effective_date_basis": "explicit_date",
                  "source_message_id": "60397",
                  "source_image_ref": null,
                  "recommendation_type": "buy",
                  "buy_price": 86.0,
                  "target_1": 91.0
                }
              ]
            }
          ],
          "client_inquiry_responses": [{"stock_code": "COMI"}]
        }
    """.trimIndent()

    @Test
    fun `parses nested data points and strips the exchange suffix`() {
        val stocks = ConsolidatedParser.parse(response)

        assertEquals(1, stocks.size)
        val stock = stocks.single()
        assertEquals("SCEM", stock.stockCode)
        assertNull(stock.stockNameEnglish)
        assertEquals("اسمنت سيناء", stock.stockNameArabic)
        assertEquals(2, stock.dataPoints.size)

        val watching = stock.dataPoints.first()
        assertTrue(watching.isWatching)
        assertEquals("سهم المراقبة", watching.timingEvidence)
        assertEquals(16, watching.sourceImageRef)
        assertEquals(86.5, watching.buyPriceLow!!, 0.001)
        assertEquals(-4.62, watching.riskPct!!, 0.001)
        assertEquals(LocalDate.of(2026, 7, 29), watching.date)

        // A null image reference must stay null rather than collapsing to 0.
        assertNull(stock.dataPoints[1].sourceImageRef)
    }

    @Test
    fun `keeps every occurrence as its own row when flattened`() {
        val stocks = ConsolidatedParser.parse(response)

        val rows = ConsolidatedParser.flatten(
            stocks, listOf(trace("s1", 60397L, "إسأل فني")), LocalDate.of(2026, 7, 29),
        )

        assertEquals(2, rows.size)
        assertEquals("إسأل فني", rows.first().sourceName)
        assertEquals(listOf("s1"), rows.first().sourceIds)
        // Falls back to the ticker when the model supplies no English name.
        assertEquals("SCEM", rows.first().companyName)
        assertEquals("BUY", rows.first().signal)
        assertEquals(86.5, rows.first().entryLow!!, 0.001)
        // A single buy_price fills both bounds of the entry.
        assertEquals(86.0, rows[1].entryLow!!, 0.001)
        assertEquals(86.0, rows[1].entryHigh!!, 0.001)
    }

    @Test
    fun `leaves citations empty when the telegram id matches no supplied source`() {
        val stocks = ConsolidatedParser.parse(response)

        val rows = ConsolidatedParser.flatten(
            stocks, listOf(trace("s1", 999L, "Other")), LocalDate.of(2026, 7, 29),
        )

        assertTrue(rows.all { it.sourceIds.isEmpty() })
        assertTrue(rows.all { it.sourceName.isEmpty() })
    }

    @Test
    fun `reads a response wrapped in a code fence`() {
        val fenced = "```json\n$response\n```"

        assertEquals(1, ConsolidatedParser.parse(fenced).size)
    }

    @Test
    fun `returns nothing when the model reports no recommendations`() {
        val empty = """{"top_consolidated_recommendations": [], "client_inquiry_responses": []}"""

        assertEquals(emptyList<Any>(), ConsolidatedParser.parse(empty))
    }

    /**
     * A response whose occurrences repeat, the way a model does when it gets stuck. The run on
     * 2 August returned this same point 106 times for one image and ranked the stock first on it.
     */
    private fun repeated(copies: Int, vararg extra: String) = """
        {
          "top_consolidated_recommendations": [
            {
              "stock_code": "ADPS",
              "stock_name_ar": "أدنس",
              "mention_count": $copies,
              "rank": 1,
              "data_points": [
                ${(List(copies) { POINT } + extra).joinToString(",")}
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `identical occurrences describe one occurrence`() {
        val rows = ConsolidatedParser.parse(repeated(106))

        assertEquals(1, rows.size)
        assertEquals(1, rows.single().dataPoints.size)
    }

    @Test
    fun `an occurrence differing in any field is kept`() {
        val other = POINT.replace("\"target_1\": 2.5", "\"target_1\": 2.9")

        val points = ConsolidatedParser.parse(repeated(4, other)).single().dataPoints

        assertEquals(2, points.size)
        assertEquals(setOf(2.5, 2.9), points.mapNotNull { it.target1 }.toSet())
    }

    @Test
    fun `a stuck answer is recorded for the diagnostics`() {
        val notes = mutableListOf<String>()

        ConsolidatedParser.parse(repeated(106), null, notes)

        assertEquals(listOf("ADPS returned 106 occurrences, 1 distinct."), notes)
    }

    @Test
    fun `an ordinary answer says nothing`() {
        val notes = mutableListOf<String>()

        ConsolidatedParser.parse(repeated(2), notes = notes)

        assertTrue(notes.isEmpty())
    }

    private companion object {
        val POINT = """
            {
              "date": "2026-08-02",
              "effective_date_basis": "explicit_date",
              "visible_source_date": "02 AUG 2026",
              "source_message_id": "63564677120",
              "source_image_ref": 19,
              "recommendation_type": "hold",
              "buy_price": 2.1,
              "target_1": 2.5,
              "notes_ar": "الصفحة تعرض إحصائيات"
            }
        """.trimIndent()
    }
}
