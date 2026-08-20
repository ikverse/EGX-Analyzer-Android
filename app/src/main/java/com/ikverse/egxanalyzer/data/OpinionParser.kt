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
 */
object OpinionParser {

    /**
     * @throws IllegalStateException where the answer is not usable, carrying a message the user can
     *   act on. Failures surface through `runAction`, which prints the message on the toast.
     */
    fun parse(
        response: String,
        model: String,
        askedOn: LocalDate,
        searched: Boolean,
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
            unknowns = json.optJSONArray("unknowns").strings(),
            model = model,
            askedOn = askedOn,
            searched = searched,
        )
    }

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
}
