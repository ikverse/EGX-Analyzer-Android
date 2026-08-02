package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.ModelExclusion
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.RecommendationResult
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.model.SourceDateGate
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Parses the desktop's schema-3 consolidated contract.
 *
 * The model returns stocks with nested occurrences (`data_points`) rather than one flat row per
 * recommendation, because the same stock can appear in several independent sections of one image
 * and those occurrences must not be merged during extraction.
 */
object ConsolidatedParser {

    /**
     * How many collapsed copies of one occurrence are worth reporting.
     *
     * A stock legitimately appearing twice in an image is ordinary; a stock whose occurrences are
     * byte-identical several times over is a model that got stuck.
     */
    private const val REPEAT_NOTE_THRESHOLD = 3

    /**
     * @param targetDate the session being analysed. Occurrences printed with a date older than the
     * session before it are dropped: they are re-posts of earlier cards, which the model has been
     * told to exclude and has twice included anyway. Null skips the check.
     * @param notes collects anything worth recording about the answer itself, for the diagnostics.
     * Deliberately separate from the validation warnings, which cost a correction request.
     */
    fun parse(
        content: String,
        targetDate: LocalDate? = null,
        notes: MutableList<String> = mutableListOf(),
    ): List<ConsolidatedRecommendation> {
        val payload = JSONObject(stripCodeFence(content))
        val stocks = payload.optJSONArray("top_consolidated_recommendations") ?: JSONArray()
        return buildList {
            for (index in 0 until stocks.length()) {
                val stock = stocks.optJSONObject(index) ?: continue
                val code = stock.string("stock_code")?.trim()?.uppercase()?.removeSuffix(".CA")
                if (code.isNullOrBlank()) continue
                // Occurrences identical in every field - date, evidence, prices, notes - describe
                // one occurrence, however many times they were returned. A run on 2 August came
                // back with the same point 106 times for one image and ranked that stock first on
                // the strength of it.
                val returned = stock.optJSONArray("data_points").dataPoints()
                val distinct = returned.distinct()
                if (returned.size - distinct.size >= REPEAT_NOTE_THRESHOLD) {
                    notes += "$code returned ${returned.size} occurrences, ${distinct.size} distinct."
                }
                val all = distinct.filter { it.hasPrice() }
                if (distinct.size > all.size) {
                    notes += "$code dropped ${distinct.size - all.size} occurrence(s) carrying no price."
                }
                val occurrences = all.filter {
                    SourceDateGate.accepts(it.visibleSourceDate, targetDate)
                }
                // Every occurrence rejected means nothing is left to recommend - whether they were
                // rejected for their date or for carrying no price.
                if (occurrences.isEmpty() && distinct.isNotEmpty()) continue
                add(
                    ConsolidatedRecommendation(
                        stockCode = code,
                        stockNameEnglish = stock.string("stock_name_en"),
                        stockNameArabic = stock.string("stock_name_ar"),
                        mentionCount = stock.optInt("mention_count", 0),
                        rank = stock.optInt("rank", index + 1),
                        notesSummary = stock.string("notes_summary"),
                        dataPoints = occurrences,
                    ),
                )
            }
        }
    }

    /**
     * An occurrence with no entry, no target and no stop has nothing to act on.
     *
     * The market summary an analyst posts at the close - index levels, sector bars, biggest movers -
     * is not a recommendation, but the model reads it as one. On 2 August it produced 37 occurrences
     * of a stock from one such page, and the next run turned the same page into an EGX30 row.
     */
    private fun RecommendationDataPoint.hasPrice(): Boolean =
        buyPrice != null || buyPriceLow != null || buyPriceHigh != null ||
            target1 != null || target2 != null || stopLoss != null

    /** Flattens one row per occurrence, mirroring how the desktop derives its recommendation rows. */
    fun flatten(
        stocks: List<ConsolidatedRecommendation>,
        traces: List<SourceTrace>,
        targetDate: LocalDate?,
        notes: MutableList<String> = mutableListOf(),
    ): List<RecommendationResult> {
        val channelByMessageId = traces
            .filter { it.messageId != null }
            .associateBy({ it.messageId.toString() }, { it.channelName })
        val sourceIdByMessageId = traces
            .filter { it.messageId != null }
            .groupBy({ it.messageId.toString() }, { it.sourceId })
        val rows = stocks.flatMap { stock ->
            val signal = signalOf(stock.dataPoints)
            val confidence = minOf(1.0, 0.5 + stock.mentionCount / 10.0)
            stock.dataPoints.map { point ->
                // A buy's second target is the farther one. Cards print the two side by side and
                // right to left, and the model has read that pair backwards while reading the buy
                // zone above it correctly - which would score the second target as reached first.
                val ordered = point.target1 != null && point.target2 != null &&
                    point.target2 < point.target1 && signal != "SELL"
                if (ordered) {
                    notes += "${stock.stockCode} had its targets the wrong way round."
                }
                RecommendationResult(
                    ticker = stock.stockCode,
                    companyName = stock.stockNameEnglish ?: stock.stockCode,
                    companyNameArabic = stock.stockNameArabic,
                    sourceName = channelByMessageId[point.sourceMessageId].orEmpty(),
                    targetDate = point.date ?: targetDate,
                    timing = point.timingEvidence ?: point.effectiveDateBasis,
                    entryLow = point.buyPriceLow ?: point.buyPrice,
                    entryHigh = point.buyPriceHigh ?: point.buyPrice,
                    takeProfit1 = if (ordered) point.target2 else point.target1,
                    takeProfit2 = if (ordered) point.target1 else point.target2,
                    stopLoss = point.stopLoss,
                    notesArabic = point.notesArabic ?: stock.notesSummary,
                    sourceIds = sourceIdByMessageId[point.sourceMessageId].orEmpty(),
                    signal = signal,
                    confidence = confidence,
                    riskLevel = point.riskPct?.let { "$it%" },
                )
            }
        }
        return rows.collapseRepeatedCalls(notes)
    }

    /**
     * One row per call, however many times a channel posted it.
     *
     * A channel posts the same table twice a minute apart with a different row highlighted each
     * time; both images carry every row, so the same call is extracted once per post. Two channels
     * making the same call are still two calls, so the channel is part of what makes a call itself.
     * The extra posting is kept as a source: it happened, and the source list is where that shows.
     */
    private fun List<RecommendationResult>.collapseRepeatedCalls(
        notes: MutableList<String>,
    ): List<RecommendationResult> {
        val byCall = LinkedHashMap<List<Any?>, RecommendationResult>()
        var collapsed = 0
        for (row in this) {
            val call = listOf(
                row.sourceName, row.ticker,
                row.entryLow, row.entryHigh, row.takeProfit1, row.takeProfit2, row.stopLoss,
            )
            val kept = byCall[call]
            if (kept == null) {
                byCall[call] = row
            } else {
                collapsed++
                byCall[call] = kept.copy(
                    sourceIds = (kept.sourceIds + row.sourceIds).distinct(),
                )
            }
        }
        if (collapsed > 0) notes += "$collapsed repeated posting(s) of a call counted once."
        return byCall.values.toList()
    }

    /**
     * The desktop removed the model-owned status field and derives the signal from the accepted
     * rows instead, so a mixed set is only a Buy when no occurrence says otherwise.
     */
    private fun signalOf(points: List<RecommendationDataPoint>): String {
        val types = points.mapNotNull { it.recommendationType?.trim()?.lowercase() }.toSet()
        return when {
            types.contains("sell") && !types.contains("buy") -> "SELL"
            types.contains("buy") -> "BUY"
            else -> "HOLD"
        }
    }

    private fun JSONArray?.dataPoints(): List<RecommendationDataPoint> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val point = optJSONObject(index) ?: continue
                add(
                    RecommendationDataPoint(
                        date = point.string("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                        effectiveDateBasis = point.string("effective_date_basis"),
                        visibleSourceDate = point.string("visible_source_date"),
                        dateEvidence = point.string("date_evidence"),
                        timingEvidence = point.string("timing_evidence"),
                        sourceMessageId = point.string("source_message_id"),
                        sourceImageRef = if (point.isNull("source_image_ref")) null
                        else point.optInt("source_image_ref").takeIf { point.has("source_image_ref") },
                        recommendationEvidence = point.string("recommendation_evidence"),
                        recommendationType = point.string("recommendation_type"),
                        buyPrice = point.number("buy_price"),
                        buyPriceLow = point.number("buy_price_low"),
                        buyPriceHigh = point.number("buy_price_high"),
                        target1 = point.number("target_1"),
                        returnTp1Pct = point.number("return_tp1_pct"),
                        target2 = point.number("target_2"),
                        returnTp2Pct = point.number("return_tp2_pct"),
                        stopLoss = point.number("stop_loss"),
                        support = point.number("support"),
                        resistance = point.number("resistance"),
                        riskPct = point.number("risk_pct"),
                        notesArabic = point.string("notes_ar"),
                    ),
                )
            }
        }
    }

    private fun JSONObject.string(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONObject.number(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private fun stripCodeFence(value: String): String = value.trim()
        .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    /** Reads the model's own account of what it dropped. Absent in responses predating schema 4. */
    fun exclusions(content: String): List<ModelExclusion> = runCatching {
        val rows = JSONObject(stripCodeFence(content)).optJSONArray("excluded") ?: JSONArray()
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val reason = row.string("reason") ?: continue
                add(
                    ModelExclusion(
                        stockCode = row.string("stock_code")?.trim()?.uppercase()?.removeSuffix(".CA"),
                        sourceMessageId = row.string("source_message_id"),
                        visibleSourceDate = row.string("visible_source_date"),
                        reason = reason,
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}