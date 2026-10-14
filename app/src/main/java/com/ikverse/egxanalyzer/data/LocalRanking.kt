package com.ikverse.egxanalyzer.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The model's consolidation answer as a ranking, or null if it is not one.
 *
 * Usable means a JSON object whose `top_consolidated_recommendations` is not empty. The caller only
 * asks when extraction supplied calls, and the consolidation prompt requires every one of them to
 * appear under its stock - so an object that ranks nothing is a model that failed, not a day with
 * nothing to report. A bare array is the same failure in another shape (a model answered `[]` after
 * seventeen thousand reasoning tokens, with ten calls supplied).
 */
internal fun usableRanking(content: String): JSONObject? {
    val parsed = runCatching { JSONObject(stripFence(content)) }.getOrNull() ?: return null
    val stocks = parsed.optJSONArray("top_consolidated_recommendations") ?: return null
    return parsed.takeIf { stocks.length() > 0 }
}

/** The start of an answer for a warning, whitespace collapsed and cut at [limit] characters. */
internal fun describeAnswer(content: String, limit: Int = 300): String {
    val flat = content.trim().replace(Regex("\\s+"), " ")
    if (flat.isEmpty()) return "(empty)"
    return if (flat.length <= limit) "\"$flat\"" else "\"${flat.take(limit)}...\""
}

/**
 * Ranks extracted calls without the model: one entry per stock, most mentioned first.
 *
 * The shape is the one the consolidation prompt asks for, so the parser reads it unchanged. Every
 * occurrence is kept exactly as extraction returned it - nothing is merged or invented - and ties
 * keep the order the stocks were first seen in.
 */
internal fun rankLocally(extracted: JSONArray): JSONObject {
    val byStock = LinkedHashMap<String, MutableList<JSONObject>>()
    for (index in 0 until extracted.length()) {
        val occurrence = extracted.optJSONObject(index) ?: continue
        val code = occurrence.optString("stock_code").trim().uppercase().removeSuffix(".CA")
        if (code.isEmpty() || occurrence.isNull("stock_code")) continue
        byStock.getOrPut(code) { mutableListOf() }.add(occurrence)
    }
    val ordered = byStock.entries.sortedByDescending { it.value.size }
    val stocks = JSONArray()
    ordered.forEachIndexed { position, (code, occurrences) ->
        val first = occurrences.first()
        stocks.put(
            JSONObject()
                .put("stock_code", code)
                .put("stock_name_en", first.opt("stock_name_en") ?: JSONObject.NULL)
                .put("stock_name_ar", first.opt("stock_name_ar") ?: JSONObject.NULL)
                .put("mention_count", occurrences.size)
                .put("rank", position + 1)
                .put(
                    "notes_summary",
                    first.optString("notes_ar").takeIf { it.isNotBlank() && !first.isNull("notes_ar") }
                        ?: first.optString("notes_en").takeIf { it.isNotBlank() && !first.isNull("notes_en") }
                        ?: JSONObject.NULL,
                )
                .put(
                    "data_points",
                    JSONArray().apply {
                        occurrences.forEach { put(withoutStockFields(it)) }
                    },
                ),
        )
    }
    return JSONObject().put("top_consolidated_recommendations", stocks)
}

private fun withoutStockFields(occurrence: JSONObject): JSONObject {
    val copy = JSONObject(occurrence.toString())
    listOf("stock_code", "stock_name_en", "stock_name_ar").forEach(copy::remove)
    return copy
}

private fun stripFence(value: String): String = value.trim()
    .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
