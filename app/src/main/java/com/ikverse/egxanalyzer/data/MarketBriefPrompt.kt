package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.Headline
import com.ikverse.egxanalyzer.model.IndexQuote
import com.ikverse.egxanalyzer.model.MarketBreadth
import com.ikverse.egxanalyzer.model.MarketBrief
import com.ikverse.egxanalyzer.model.MarketMovers
import com.ikverse.egxanalyzer.model.OutsideQuote
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/**
 * The question behind Home's "Ask AI for a market brief", and the reading of its answer.
 *
 * **What is sent is public and nothing else**: the index levels, the breadth and movers of the last
 * session, the dollar, gold and oil, and the headlines' titles. No Telegram content, no call, no
 * trade and no ticker the user holds is in it - the book tile sits on the same page and is
 * deliberately not an argument here. It is a paid request, made only by a press through a
 * confirmation, and never by a refresh or by the page being drawn.
 */
object MarketBriefPrompt {

    fun system(language: String): String =
        "You are a market desk assistant for a private investor in Egyptian Exchange (EGX) stocks. " +
            "From the figures and headlines in the message, write a short brief of the session. " +
            "Use only what is given: do not invent a figure, a company or a piece of news, and do " +
            "not give buy or sell advice. If the headlines say nothing that bears on the market, " +
            "say so rather than stretching one. Write in $language. Reply with one JSON object and " +
            "nothing else, in exactly this shape: {\"summary\": \"two sentences on what the market " +
            "did and why, as far as the headlines say\", \"points\": [\"up to four short sentences, " +
            "each one thing that happened\"], \"watch\": [\"up to three short things to watch next " +
            "session\"]}"

    fun question(
        today: LocalDate,
        indices: List<IndexQuote>,
        breadth: MarketBreadth?,
        movers: MarketMovers?,
        outside: List<OutsideQuote>,
        headlines: List<Headline>,
    ): String = buildString {
        appendLine("Date: $today")
        appendLine()
        appendLine("Indices (level, change from the previous close):")
        indices.forEach { appendLine("- ${it.name}: ${figure(it.level)}${move(it.changePercent)}") }
        breadth?.let {
            appendLine()
            appendLine("Breadth on ${it.date}: ${it.up} stocks rose, ${it.flat} were flat, ${it.down} fell.")
        }
        movers?.let { m ->
            if (m.gainers.isNotEmpty()) {
                appendLine("Top gainers: " + m.gainers.joinToString { "${it.ticker}${move(it.changePercent)}" })
            }
            if (m.losers.isNotEmpty()) {
                appendLine("Top losers: " + m.losers.joinToString { "${it.ticker}${move(it.changePercent)}" })
            }
        }
        if (outside.isNotEmpty()) {
            appendLine()
            appendLine("Outside the exchange:")
            outside.forEach { appendLine("- ${it.name}: ${figure(it.value)}${move(it.changePercent)}") }
        }
        appendLine()
        appendLine("Headlines (title, then source):")
        headlines.take(MAX_HEADLINES).forEach { appendLine("- ${it.title} (${it.source})") }
    }

    /** The answer as a brief. Throws with a sentence the status line can show where it is unusable. */
    fun parse(response: String, model: String, askedAt: Long): MarketBrief {
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        val json = if (start < 0 || end <= start) null else
            runCatching { JSONObject(response.substring(start, end + 1)) }.getOrNull()
        val summary = json?.optString("summary")?.trim().orEmpty()
        if (summary.isEmpty()) error("The model did not answer in the form the app asked for. Try again.")
        return MarketBrief(
            summary = summary,
            points = json?.optJSONArray("points").strings().take(4),
            watch = json?.optJSONArray("watch").strings().take(3),
            model = model,
            askedAt = askedAt,
        )
    }

    private fun figure(value: Double) = String.format(Locale.US, "%,.2f", value)

    private fun move(percent: Double?) = percent?.let { String.format(Locale.US, " (%+.2f%%)", it) }.orEmpty()

    const val MAX_HEADLINES = 20
}
