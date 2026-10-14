package com.ikverse.egxanalyzer.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What counts as a usable consolidation answer, and the ranking made on the phone when none is.
 *
 * Written from the 14 October run: ten calls were extracted, the model thought for 17,130 tokens
 * and answered `[]`, and the report came out empty with the cause in a footnote.
 */
class LocalRankingTest {

    private fun call(
        code: String,
        message: String,
        date: String = "2026-10-14",
        notesAr: String? = "ملاحظة",
    ) = JSONObject()
        .put("stock_code", code)
        .put("stock_name_en", JSONObject.NULL)
        .put("stock_name_ar", "اسم $code")
        .put("date", date)
        .put("effective_date_basis", "explicit_date")
        .put("visible_source_date", "14/10/2026")
        .put("timing_evidence", JSONObject.NULL)
        .put("source_message_id", message)
        .put("source_image_ref", 1)
        .put("recommendation_type", "buy")
        .put("buy_price", 10.0)
        .put("target_1", 11.0)
        .put("stop_loss", 9.0)
        .put("notes_ar", notesAr ?: JSONObject.NULL)
        .put("notes_en", "English note")

    private fun extracted(vararg calls: JSONObject) = JSONArray().apply { calls.forEach(::put) }

    @Test
    fun `a bare empty array is not a ranking`() {
        assertNull(usableRanking("[]"))
    }

    @Test
    fun `an array of stocks is not a ranking either`() {
        assertNull(usableRanking("""[{"stock_code":"ETEL"}]"""))
    }

    @Test
    fun `garbage and an empty answer are not rankings`() {
        assertNull(usableRanking("I will now produce the JSON"))
        assertNull(usableRanking(""))
        assertNull(usableRanking("null"))
    }

    @Test
    fun `an object that ranks nothing is not a ranking when calls were supplied`() {
        assertNull(usableRanking("""{"top_consolidated_recommendations": []}"""))
        assertNull(usableRanking("""{"excluded": []}"""))
    }

    @Test
    fun `a ranked object passes through, with or without a code fence`() {
        val plain = """{"top_consolidated_recommendations":[{"stock_code":"ETEL"}]}"""
        assertNotNull(usableRanking(plain))
        assertNotNull(usableRanking("```json\n$plain\n```"))
    }

    @Test
    fun `an answer is described by its start, cut and flattened`() {
        assertEquals("(empty)", describeAnswer("  "))
        assertEquals("\"[]\"", describeAnswer("[]"))
        assertEquals("\"a b\"", describeAnswer("a\n  b"))
        assertEquals("\"abc...\"", describeAnswer("abcdef", limit = 3))
    }

    @Test
    fun `stocks are ranked by mentions, ties keeping the order first seen`() {
        val ranked = rankLocally(
            extracted(
                call("ORAS", "1"), call("HELI", "2"), call("ETEL", "3"),
                call("ORAS", "4"), call("ETEL", "5"), call("ORAS", "6"),
            ),
        ).getJSONArray("top_consolidated_recommendations")

        assertEquals(listOf("ORAS", "ETEL", "HELI"), (0 until 3).map { ranked.getJSONObject(it).getString("stock_code") })
        assertEquals(listOf(1, 2, 3), (0 until 3).map { ranked.getJSONObject(it).getInt("rank") })
        assertEquals(listOf(3, 2, 1), (0 until 3).map { ranked.getJSONObject(it).getInt("mention_count") })
    }

    @Test
    fun `every call is kept once, as extracted, without the stock fields`() {
        val ranked = rankLocally(
            extracted(call("ORAS", "1"), call("ORAS.CA", "2"), call("AMOC", "3")),
        ).getJSONArray("top_consolidated_recommendations")

        val oras = ranked.getJSONObject(0)
        assertEquals("ORAS", oras.getString("stock_code"))
        val points = oras.getJSONArray("data_points")
        assertEquals(2, points.length())
        assertEquals("1", points.getJSONObject(0).getString("source_message_id"))
        assertFalse(points.getJSONObject(0).has("stock_code"))
        assertEquals(10.0, points.getJSONObject(1).getDouble("buy_price"), 0.0)
        assertEquals(3, (0 until ranked.length()).sumOf { ranked.getJSONObject(it).getJSONArray("data_points").length() })
    }

    @Test
    fun `the summary is the first Arabic note, falling back to the English one`() {
        val ranked = rankLocally(
            extracted(call("ORAS", "1"), call("AMOC", "2", notesAr = null)),
        ).getJSONArray("top_consolidated_recommendations")

        assertEquals("ملاحظة", ranked.getJSONObject(0).getString("notes_summary"))
        assertEquals("English note", ranked.getJSONObject(1).getString("notes_summary"))
    }

    @Test
    fun `a call with no stock code is not invented into a stock`() {
        val nameless = call("X", "9").put("stock_code", JSONObject.NULL)
        val ranked = rankLocally(extracted(nameless, call("ORAS", "1")))
            .getJSONArray("top_consolidated_recommendations")

        assertEquals(1, ranked.length())
    }

    @Test
    fun `the parser reads a local ranking as it would the model's`() {
        val document = rankLocally(
            extracted(call("ORAS", "1"), call("ORAS", "2"), call("AMOC", "3")),
        ).toString()

        val stocks = ConsolidatedParser.parse(document, LocalDate.of(2026, 10, 14))

        assertEquals(listOf("ORAS", "AMOC"), stocks.map { it.stockCode })
        assertEquals(listOf(2, 1), stocks.map { it.dataPoints.size })
        assertEquals(listOf(2, 1), stocks.map { it.mentionCount })
        assertTrue(stocks.all { stock -> stock.dataPoints.all { it.buyPrice == 10.0 } })
    }
}
