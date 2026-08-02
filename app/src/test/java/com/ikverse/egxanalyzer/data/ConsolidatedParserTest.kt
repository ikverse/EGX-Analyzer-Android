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

    /** One stock, with whatever occurrences the test needs. */
    private fun stock(code: String, vararg points: String) = """
        {
          "top_consolidated_recommendations": [
            {"stock_code": "$code", "mention_count": 1, "rank": 1,
             "data_points": [${points.joinToString(",")}]}
          ]
        }
    """.trimIndent()

    private fun priced(target1: String, target2: String, message: String = "60397") = """
        {
          "date": "2026-08-02", "visible_source_date": "02 AUG 2026",
          "source_message_id": "$message", "recommendation_type": "buy",
          "buy_price_low": 1.92, "buy_price_high": 1.93,
          "target_1": $target1, "target_2": $target2, "stop_loss": 1.86
        }
    """.trimIndent()

    @Test
    fun `an occurrence carrying no price is not a recommendation`() {
        val summary = """
            {
              "date": "2026-08-02", "visible_source_date": "02 AUG 2026",
              "source_message_id": "60397", "recommendation_type": "hold",
              "notes_ar": "المؤشر ضمن أكثر الأسهم ارتفاعا"
            }
        """.trimIndent()
        val notes = mutableListOf<String>()

        val rows = ConsolidatedParser.parse(stock("EGX30", summary), null, notes)

        assertEquals(emptyList<Any>(), rows)
        assertEquals(listOf("EGX30 dropped 1 occurrence(s) carrying no price."), notes)
    }

    @Test
    fun `a watching card with only a trigger price survives`() {
        val watching = """
            {
              "date": "2026-08-02", "visible_source_date": "02 AUG 2026",
              "source_message_id": "60397", "recommendation_type": "buy",
              "buy_price": 9.2, "timing_evidence": "سهم المراقبة"
            }
        """.trimIndent()

        val rows = ConsolidatedParser.parse(stock("AMOC", watching))

        assertEquals(1, rows.single().dataPoints.size)
    }

    @Test
    fun `a buy whose targets arrive reversed is put back in order`() {
        val notes = mutableListOf<String>()
        val stocks = ConsolidatedParser.parse(stock("CRST", priced("2.10", "2.01")))

        val row = ConsolidatedParser.flatten(
            stocks, listOf(trace("s1", 60397L, "إسأل فني")), LocalDate.of(2026, 8, 2), notes,
        ).single()

        assertEquals(2.01, row.takeProfit1!!, 0.001)
        assertEquals(2.10, row.takeProfit2!!, 0.001)
        assertEquals(listOf("CRST had its targets the wrong way round."), notes)
    }

    @Test
    fun `targets already in order are left alone`() {
        val stocks = ConsolidatedParser.parse(stock("CRST", priced("2.01", "2.10")))

        val row = ConsolidatedParser.flatten(
            stocks, listOf(trace("s1", 60397L, "إسأل فني")), LocalDate.of(2026, 8, 2),
        ).single()

        assertEquals(2.01, row.takeProfit1!!, 0.001)
        assertEquals(2.10, row.takeProfit2!!, 0.001)
    }

    @Test
    fun `a call a channel posted twice is counted once, keeping both sources`() {
        val notes = mutableListOf<String>()
        val stocks = ConsolidatedParser.parse(
            stock("ETEL", priced("2.01", "2.10", "111"), priced("2.01", "2.10", "222")),
        )

        val rows = ConsolidatedParser.flatten(
            stocks,
            listOf(trace("s1", 111L, "إسأل فني"), trace("s2", 222L, "إسأل فني")),
            LocalDate.of(2026, 8, 2),
            notes,
        )

        assertEquals(1, rows.size)
        assertEquals(listOf("s1", "s2"), rows.single().sourceIds)
        assertEquals(listOf("1 repeated posting(s) of a call counted once."), notes)
    }

    @Test
    fun `two channels making the same call are two calls`() {
        val stocks = ConsolidatedParser.parse(
            stock("ETEL", priced("2.01", "2.10", "111"), priced("2.01", "2.10", "222")),
        )

        val rows = ConsolidatedParser.flatten(
            stocks,
            listOf(trace("s1", 111L, "إسأل فني"), trace("s2", 222L, "CFI Egypt")),
            LocalDate.of(2026, 8, 2),
        )

        assertEquals(2, rows.size)
        assertEquals(listOf("إسأل فني", "CFI Egypt"), rows.map { it.sourceName })
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

    /**
     * A T+1 card is its own kind of call, and the sources label it as one.
     *
     * The channels post a table headed "أسهم مرشحة للمتاجرة T+1" - a trade between today's close
     * and tomorrow's open - and it used to arrive indistinguishable from an ordinary dated call.
     */
    @Test
    fun `a T+1 occurrence keeps its basis and its wording`() {
        val point = priced("2.01", "2.10").replace(
            "\"recommendation_type\": \"buy\"",
            "\"recommendation_type\": \"buy\", \"effective_date_basis\": \"t_plus_1\", " +
                "\"timing_evidence\": \"أسهم مرشحة للمتاجرة T+1\"",
        )

        val occurrence = ConsolidatedParser.parse(stock("CRST", point)).single().dataPoints.single()

        assertTrue(occurrence.isTPlusOne)
        assertTrue(!occurrence.isWatching)
        assertEquals("أسهم مرشحة للمتاجرة T+1", occurrence.timingEvidence)
    }
}
