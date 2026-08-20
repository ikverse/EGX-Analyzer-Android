package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.StockOpinion
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * The model's answer into a [StockOpinion].
 *
 * Tolerant in exactly the places [ConsolidatedParser] is, and for the same reason: a model told to
 * return bare JSON returns fenced JSON often enough that refusing it would fail a paid request over
 * three backticks. Tolerant nowhere else - a missing verdict is a missing answer, not a default.
 *
 * The lists are the tolerant end of that. A news item without a headline is nothing and is dropped;
 * one without a source is shown with that gap named, because the alternative is throwing away a
 * paid-for finding over a field the model forgot.
 */
object OpinionParser {

    /** More than the prompt asks for, so a model that overshoots is trimmed rather than refused. */
    private const val MAX_NEWS = 6
    private const val MAX_CATALYSTS = 5
    private const val MAX_RISKS = 6

    /**
     * @throws IllegalStateException where the answer is not usable, carrying a message the user can
     *   act on. Failures surface through `runAction`, which prints the message on the toast.
     */
    fun parse(
        response: String,
        model: String,
        askedOn: LocalDate,
        searched: Boolean,
        /** The lookback the request was given, recorded on the answer as the window it covers. */
        newsWindowDays: Int = 0,
    ): StockOpinion {
        val json = runCatching { JSONObject(stripFence(response)) }.getOrNull()
            ?: error("The model did not answer in the form the app asked for. Try again.")
        val verdict = json.enum("verdict", StockOpinion.Verdict.entries)
            ?: error("The model gave no verdict. Try again.")
        val call = json.optJSONObject("on_the_call")
        return StockOpinion(
            verdict = verdict,
            // A horizon is meaningless where the answer is not to buy, and a model that has just
            // said AVOID leaving it out is answering correctly rather than incompletely.
            horizon = json.enum("horizon", StockOpinion.Horizon.entries)
                ?: StockOpinion.Horizon.NEITHER,
            // Not defaulted to MEDIUM: an answer that never stated its confidence has not earned
            // the middle of the scale.
            confidence = json.enum("confidence", StockOpinion.Confidence.entries)
                ?: StockOpinion.Confidence.LOW,
            headline = json.text("headline").orEmpty(),
            outlook = json.text("outlook")
                ?: error("The model gave no reading of the stock. Try again."),
            onTheCall = StockOpinion.CallView(
                stance = call?.enum("stance", StockOpinion.Stance.entries)
                    ?: StockOpinion.Stance.RISKY,
                detail = call?.text("detail").orEmpty(),
            ),
            news = json.optJSONArray("news").news(),
            catalysts = json.optJSONArray("catalysts").catalysts(),
            risks = json.optJSONArray("risks").strings().take(MAX_RISKS),
            unknowns = json.optJSONArray("unknowns").strings(),
            newsWindowDays = newsWindowDays,
            model = model,
            askedOn = askedOn,
            searched = searched,
        )
    }

    /**
     * What the search found, newest first.
     *
     * Sorted here rather than trusted from the model: it is asked for the items that matter, not
     * for them in order, and a reader scanning three headlines reads the newest one first whatever
     * order they arrived in. Undated items fall to the bottom rather than out.
     */
    private fun JSONArray?.news(): List<StockOpinion.NewsItem> = objects()
        .mapNotNull { item ->
            val headline = item.text("headline") ?: return@mapNotNull null
            StockOpinion.NewsItem(
                headline = headline,
                date = item.text("date").orEmpty(),
                source = item.text("source").orEmpty(),
                tone = item.enum("tone", StockOpinion.Tone.entries) ?: StockOpinion.Tone.NEUTRAL,
            )
        }
        .sortedByDescending { it.on ?: LocalDate.MIN }
        .take(MAX_NEWS)

    private fun JSONArray?.catalysts(): List<StockOpinion.Catalyst> = objects()
        .mapNotNull { item ->
            val what = item.text("what") ?: return@mapNotNull null
            StockOpinion.Catalyst(
                what = what,
                on = item.text("when").orEmpty(),
                source = item.text("source").orEmpty(),
            )
        }
        .take(MAX_CATALYSTS)

    /** ```json fences and stray prose around the object, which arrive despite the instruction. */
    private fun stripFence(value: String): String {
        val trimmed = value.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed.trim()
    }

    /**
     * A token read back to its entry, case and spacing forgiven.
     *
     * `buy now` and `BUY_NOW` are the same answer, and losing a paid opinion to an underscore would
     * be an expensive kind of strictness. An unrecognized token is null rather than a guess.
     */
    private fun <T : Enum<T>> JSONObject.enum(key: String, entries: List<T>): T? {
        val raw = text(key)?.uppercase()?.replace(' ', '_')?.replace('-', '_') ?: return null
        return entries.firstOrNull { it.name == raw }
    }

    private fun JSONObject.text(key: String): String? =
        optString(key).trim().takeIf { it.isNotBlank() && it != "null" }

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).trim().takeIf { it.isNotBlank() && it != "null" }
        }
    }

    /** The object entries of a list, skipping anything that arrived as a bare string. */
    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
