package com.ikverse.egxanalyzer.data

import android.content.Context
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.EditField
import com.ikverse.egxanalyzer.model.RecommendationEdit
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.model.editFingerprint
import com.ikverse.egxanalyzer.model.recommendedTickers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import java.time.LocalDate

/**
 * That a correction survives being written down, and reaches everything read off the report.
 *
 * The interesting half is that a report's stocks are **not stored** - they are re-parsed from the
 * model's response on every read - so the only way a correction can exist at all is as an overlay
 * applied after that parse. This is the test that the overlay is actually stored, actually applied,
 * and actually undoable, none of which a test over `RecommendationEdits` alone can show.
 */
@RunWith(RobolectricTestRunner::class)
class RecommendationEditStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val rawResponse = """
        {
          "top_consolidated_recommendations": [
            {
              "stock_code": "ORAS",
              "stock_name_en": "Orascom Construction PLC",
              "stock_name_ar": "اوراسكوم كونستراكشون",
              "mention_count": 1,
              "rank": 1,
              "data_points": [
                {
                  "effective_date_basis": "t_plus_1",
                  "visible_source_date": "03/09/2026",
                  "source_message_id": "5501",
                  "source_image_ref": 2,
                  "recommendation_type": "buy",
                  "buy_price_low": 12.10,
                  "buy_price_high": 12.40,
                  "target_1": 13.20,
                  "target_2": 14.00,
                  "stop_loss": 11.80
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private val result = AnalysisResult(
        requestId = "run-1",
        recommendations = emptyList(),
        imagePaths = emptyList(),
        inquiryReplyCount = 0,
        recommendationTargetDate = LocalDate.parse("2026-09-03"),
        sources = listOf(
            SourceTrace(
                sourceId = "chat-1:5501",
                channelId = 1,
                channelName = "Ashum Masr",
                messageId = 5501,
                timestamp = Instant.parse("2026-09-02T12:00:00Z"),
                contentType = AnalysisContentType.IMAGES,
                preview = "اهم الاسهم غدا",
            ),
        ),
        rawResponse = rawResponse,
        completedAt = Instant.parse("2026-09-02T13:00:00Z"),
        sourceReads = mapOf("chat-1:5501" to """{"read":"ORAS"}"""),
    )

    private fun store(): Pair<LocalDataStore, Long> {
        val store = LocalDataStore(context, "edits-${System.nanoTime()}.db")
        val id = store.saveResult(result, CloudProvider.QWEN, "qwen-vl-max", readingKey = "v4/ar")
        return store to id
    }

    private fun LocalDataStore.reportedStock(id: Long) =
        results().first { it.id == id }.result.consolidated.single()

    private fun correction(store: LocalDataStore, id: Long) = RecommendationEdit(
        originalStockCode = "ORAS",
        pointIndex = 0,
        fingerprint = store.reportedStock(id).dataPoints.single().editFingerprint(),
    )

    @Test
    fun `a corrected ticker survives the report being read again`() {
        val (store, id) = store()

        store.saveResultEdit(id, correction(store, id).copy(stockCode = "ORHD"))

        assertEquals("ORHD", store.reportedStock(id).stockCode)
        // Filed under what the model read, which is what a second correction has to find.
        assertEquals("ORAS", store.reportedStock(id).originalStockCode)
    }

    /**
     * The parse runs again on every read, so this is the failure the overlay exists to prevent.
     *
     * An edit written into the parsed object rather than stored beside the response would pass any
     * test that never reads the report back, and would be gone by the time anybody looked at it.
     */
    @Test
    fun `a corrected level is still corrected after a fresh read`() {
        val (store, id) = store()

        store.saveResultEdit(id, correction(store, id).copy(target1 = 13.85))

        val point = store.reportedStock(id).dataPoints.single()
        assertEquals(13.85, point.target1)
        assertEquals(13.06, point.returnTp1Pct!!, 0.01)
    }

    /** What the model actually said is evidence and is never overwritten by a correction. */
    @Test
    fun `the model's own response is left exactly as it answered`() {
        val (store, id) = store()

        store.saveResultEdit(id, correction(store, id).copy(stockCode = "ORHD", target1 = 13.85))

        val stored = store.results().first { it.id == id }.result.rawResponse
        assertTrue(stored.contains("\"stock_code\": \"ORAS\""))
        assertTrue(stored.contains("13.2"))
    }

    @Test
    fun `undoing puts the report back to what the model read`() {
        val (store, id) = store()
        store.saveResultEdit(id, correction(store, id).copy(stockCode = "ORHD", target1 = 13.85))

        store.clearResultEdits(id)

        val stock = store.reportedStock(id)
        assertEquals("ORAS", stock.stockCode)
        assertEquals(13.20, stock.dataPoints.single().target1)
    }

    /**
     * The revision is what makes a corrected report syncable at all.
     *
     * Reports otherwise travel as a union, which has no way to say that one copy is newer - so
     * without this a correction would live on the phone it was made on for ever.
     */
    @Test
    fun `every correction raises the revision, and so does undoing one`() {
        val (store, id) = store()
        assertEquals(0L, store.savedReportRevisions().getValue("run-1"))

        store.saveResultEdit(id, correction(store, id).copy(stockCode = "ORHD"))
        assertEquals(1L, store.savedReportRevisions().getValue("run-1"))

        store.clearResultEdits(id)
        assertEquals(2L, store.savedReportRevisions().getValue("run-1"))
    }

    /** A copy of the same report at or below the revision held here must never overwrite it. */
    @Test
    fun `a report arriving with an older correction is refused`() {
        val (store, id) = store()
        store.saveResultEdit(id, correction(store, id).copy(stockCode = "ORHD"))
        val payload = JSONObject(store.storedJsonOf(store.results().first { it.id == id }.result))
            .put("edits", org.json.JSONArray())
            .put("editRevision", 0)
            .toString()

        val took = store.adoptResult("run-1", "QWEN", "qwen-vl-max", "x", payload, editRevision = 0)

        assertEquals(false, took)
        assertEquals("ORHD", store.reportedStock(id).stockCode)
    }

    @Test
    fun `a report arriving with a newer correction replaces the copy held here`() {
        val (store, id) = store()
        val corrected = LocalDataStore(context, "other-${System.nanoTime()}.db").let { other ->
            val otherId = other.saveResult(result, CloudProvider.QWEN, "qwen-vl-max")
            other.saveResultEdit(otherId, correction(other, otherId).copy(stockCode = "ORHD"))
            other.storedJsonOf(other.results().first { it.id == otherId }.result)
        }

        val took = store.adoptResult("run-1", "QWEN", "qwen-vl-max", "x", corrected, editRevision = 1)

        assertTrue(took)
        assertEquals("ORHD", store.reportedStock(id).stockCode)
    }

    /**
     * The correction that reaches further than this report.
     *
     * A run writes down what it read of each message so the next one need not pay to read it again,
     * and that reading still carries the wrong ticker. Left alone, tomorrow's run adopts the same
     * misread for free and puts it back into a fresh report with nothing on screen to say why.
     */
    @Test
    fun `forgetting a source reading makes the next run read that message afresh`() {
        val (store, _) = store()
        assertEquals(
            mapOf("chat-1:5501" to """{"read":"ORAS"}"""),
            store.sourceReads("qwen-vl-max", "v4/ar", Instant.parse("2026-09-01T00:00:00Z")),
        )

        store.forgetSourceReads("run-1", setOf("chat-1:5501"))

        assertEquals(
            emptyMap<String, String>(),
            store.sourceReads("qwen-vl-max", "v4/ar", Instant.parse("2026-09-01T00:00:00Z")),
        )
    }

    /** The flat list is what the price feed is aimed from, so a stale code there is money. */
    @Test
    fun `a corrected ticker reaches the list the price feed is built from`() {
        val flat = result.copy(
            recommendations = listOf(
                com.ikverse.egxanalyzer.model.RecommendationResult(
                    ticker = "ORAS",
                    companyName = "Orascom Construction PLC",
                    sourceName = "Ashum Masr",
                    targetDate = LocalDate.parse("2026-09-03"),
                    timing = "t_plus_1",
                    entryLow = 12.10,
                    entryHigh = 12.40,
                    takeProfit1 = 13.20,
                    takeProfit2 = 14.00,
                    stopLoss = 11.80,
                    notesArabic = null,
                ),
            ),
        )
        val store = LocalDataStore(context, "flat-${System.nanoTime()}.db")
        val id = store.saveResult(flat, CloudProvider.QWEN, "qwen-vl-max")

        store.saveResultEdit(id, correction(store, id).copy(stockCode = "ORHD"))

        val saved = store.results().first { it.id == id }
        assertEquals("ORHD", saved.result.recommendations.single().ticker)
        assertEquals(setOf("ORHD"), listOf(saved).recommendedTickers())
    }

    /** An edit filed against a report that is no longer there is a no-op rather than a crash. */
    @Test
    fun `correcting a report that has gone does nothing`() {
        val (store, id) = store()
        val edit = correction(store, id)
        store.deleteResult(id)

        assertNull(store.saveResultEdit(id, edit.copy(stockCode = "ORHD")))
        assertNull(store.clearResultEdits(id))
    }

    /** A correction that says nothing is a removal, so putting one field back leaves no trace. */
    @Test
    fun `an empty correction removes the one it replaces`() {
        val (store, id) = store()
        store.saveResultEdit(id, correction(store, id).copy(target1 = 13.85))

        store.saveResultEdit(id, correction(store, id).copy(cleared = emptySet()))

        val saved = store.results().first { it.id == id }
        assertEquals(emptyList<RecommendationEdit>(), saved.result.edits)
        assertEquals(13.20, saved.result.consolidated.single().dataPoints.single().target1)
    }

    /** Clearing has to survive the round trip, or it would read back as "say nothing". */
    @Test
    fun `a cleared field is still cleared after a fresh read`() {
        val (store, id) = store()

        store.saveResultEdit(id, correction(store, id).copy(cleared = setOf(EditField.TARGET_2)))

        assertNull(store.reportedStock(id).dataPoints.single().target2)
    }
}
