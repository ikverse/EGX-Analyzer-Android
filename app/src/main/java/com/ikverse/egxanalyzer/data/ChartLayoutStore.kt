package com.ikverse.egxanalyzer.data

import android.content.Context

/**
 * What the reader has put on the full-screen chart: which indicators are switched on, and the lines
 * they have drawn on each stock.
 *
 * Its own preference file, outside the settings document, for the reason [ModelUsageStore] gives:
 * none of it is meant to travel. A trend line is one reader's opinion drawn on one phone, it changes
 * every time a point is dragged, and merging two phones' drawings of one stock would put lines on the
 * chart nobody drew there. So it is in no backup, no sync and no diagnostics copy.
 *
 * The drawings are kept as the page wrote them - a JSON list the chart page alone reads - so this
 * store never has to learn what a Fibonacci retracement is.
 */
class ChartLayoutStore(context: Context) {

    private val preferences = context.getSharedPreferences("egx_chart_layout", Context.MODE_PRIVATE)

    /** The indicators the chart opens with; volume alone until the reader has chosen. */
    fun indicators(): Set<String> =
        preferences.getStringSet(KEY_INDICATORS, null)?.toSet() ?: DefaultIndicators

    fun saveIndicators(names: Set<String>) {
        preferences.edit().putStringSet(KEY_INDICATORS, names.toSet()).apply()
    }

    /** What has been drawn on [ticker], or null when nothing has. */
    fun drawings(ticker: String): String? = preferences.getString(drawingsKey(ticker), null)

    /** Replaces what is drawn on [ticker]; an empty list or null forgets the stock altogether. */
    fun saveDrawings(ticker: String, json: String?) {
        val edit = preferences.edit()
        if (json.isNullOrBlank() || json.trim() == "[]") edit.remove(drawingsKey(ticker)) else edit.putString(drawingsKey(ticker), json)
        edit.apply()
    }

    private fun drawingsKey(ticker: String) = "drawings_" + ticker.uppercase()

    companion object {
        private const val KEY_INDICATORS = "indicators"
        val DefaultIndicators = setOf("VOL")
    }
}
