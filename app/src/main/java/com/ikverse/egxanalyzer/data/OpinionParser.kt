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

    /** The key each span arrives under, and the order the three are read in. */
    private val SPAN_KEYS = listOf(
        StockOpinion.Span.SHORT to "short",
        StockOpinion.Span.MEDIUM to "medium",
        StockOpinion.Span.LONG to "long",
    )

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
        val forecast = json.optJSONObject("forecast").forecast()
        return StockOpinion(
            verdict = verdict,
            // Derived, because schema 3 stopped asking for it. The fallback reads a `horizon` token
            // where one arrived anyway: a model still answering in the old shape has told us
            // something, and discarding it to print NEITHER beside a BUY_NOW would be this build
            // ignoring the answer it was given.
            horizon = if (forecast != null) {
                StockOpinion.Horizon.from(verdict, forecast)
            } else {
                json.enum("horizon", StockOpinion.Horizon.entries)
                    ?: StockOpinion.Horizon.NEITHER
            },
            // Not defaulted to MEDIUM: an answer that never stated its confidence has not earned
            // the middle of the scale.
            confidence = json.enum("confidence", StockOpinion.Confidence.entries)
                ?: StockOpinion.Confidence.LOW,
            headline = json.text("headline").orEmpty(),
            // Tolerant where `outlook` is not, and the asymmetry is deliberate. An answer with no
            // reading of the stock at all is not an answer; one that skipped the price section
            // still carries everything else the request paid for, and the section is simply absent
            // on the sheet - which is itself the signal that the model did not do the work.
            standing = json.text("standing").orEmpty(),
            outlook = json.text("outlook")
                ?: error("The model gave no reading of the stock. Try again."),
            forecast = forecast,
            onTheCall = StockOpinion.CallView(
                stance = call?.enum("stance", StockOpinion.Stance.entries)
                    ?: StockOpinion.Stance.RISKY,
                detail = call?.text("detail").orEmpty(),
                checks = call?.optJSONArray("checks").checks(),
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
     * The three spans, or nothing.
     *
     * All-or-nothing on purpose, and it is the one place this parser is stricter than the rest of
     * it. A leg is a *comparison* - the whole point of asking three questions is that the answers
     * can differ - so two legs on screen would not be a smaller forecast, it would be a different
     * one, with the app rather than the model having chosen which span to leave out. A missing
     * direction is a missing leg for the same reason: prose with no direction attached says
     * nothing the outlook has not already said.
     */
    private fun JSONObject?.forecast(): StockOpinion.Forecast? {
        if (this == null) return null
        val legs = SPAN_KEYS.mapNotNull { (span, key) ->
            val leg = optJSONObject(key) ?: return@mapNotNull null
            val direction = leg.enum("direction", StockOpinion.Direction.entries)
                ?: return@mapNotNull null
            StockOpinion.Forecast.Leg(
                span = span,
                direction = direction,
                why = leg.text("why").orEmpty(),
                // Per leg and defaulted low, like the answer's own: a span that never said how sure
                // it was has not earned the middle of the scale either.
                confidence = leg.enum("confidence", StockOpinion.Confidence.entries)
                    ?: StockOpinion.Confidence.LOW,
            )
        }
        return if (legs.size == SPAN_KEYS.size) StockOpinion.Forecast(legs) else null
    }

    /**
     * The printed numbers, each rated once, in the app's own order.
     *
     * The prompt asks for the five in a fixed order and the order is enforced here rather than
     * trusted: the sheet draws them as a fixed list beside the levels on the card, and a model
     * that shuffled them would rearrange the reader's screen from one answer to the next. A second
     * entry for an item already rated is dropped rather than allowed to overwrite the first - two
     * ratings for one number is the model disagreeing with itself, and the earlier one is the one
     * it committed to.
     */
    private fun JSONArray?.checks(): List<StockOpinion.Check> {
        val given = objects().mapNotNull { item ->
            val what = item.enum("item", StockOpinion.CheckItem.entries) ?: return@mapNotNull null
            val rating = item.enum("rating", StockOpinion.Rating.entries) ?: return@mapNotNull null
            StockOpinion.Check(item = what, rating = rating, note = item.text("note").orEmpty())
        }
        return StockOpinion.CheckItem.entries.mapNotNull { wanted ->
            given.firstOrNull { it.item == wanted }
        }
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
