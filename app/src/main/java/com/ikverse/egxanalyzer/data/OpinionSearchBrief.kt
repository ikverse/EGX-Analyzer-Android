package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.ScoredCall
import java.time.LocalDate

/**
 * What a searching Ask AI request is sent to look for.
 *
 * Before this existed the app turned a search on and said nothing about what to search. The
 * provider was handed a question about levels and left to invent a query from a four-letter
 * Latin ticker - which on this exchange is as likely to find an airport code as a company. The
 * block written here is the difference between "search the web" and "search for these names, over
 * these dates, in these places".
 *
 * Two texts come out of it and they are not the same text. [query] goes into the question, where
 * it aims what the provider actually searches for. [resultPreamble] goes to OpenRouter as the
 * `search_prompt` that introduces the results it found - it cannot change the query, only what the
 * model is told about the results, which is where the date rule has to be repeated.
 */
object OpinionSearchBrief {

    /**
     * How far ahead a catalyst is worth naming.
     *
     * Deliberately not the news window and deliberately not a setting. The news window is a
     * lookback the user tightens or loosens to taste; this is a forward horizon, and a dividend
     * three weeks out does not stop mattering because the reader asked for a fortnight of news.
     */
    private const val CATALYST_HORIZON_DAYS = 120L

    /** Where Egyptian company news is actually published, best-sourced first. */
    private val SOURCES = listOf(
        "the EGX's own disclosures (egx.com.eg) and the company's investor relations page",
        "Mubasher (mubasher.info), Argaam, Hapi Journal, Al Borsa (alborsaanews.com)",
        "Enterprise, Reuters, Asharq Business, Zawya",
    )

    /** What moves a price on this exchange, which is what the search is for. */
    private val LOOK_FOR = listOf(
        "quarterly or annual results, and the date the next ones are due",
        "dividends: the amount, the coupon and ex-dividend dates, and any change to the policy",
        "board meetings, general assemblies, and what they were called to decide",
        "capital increases, rights issues, share buybacks, splits",
        "major contracts won or lost, plant or capacity changes, asset sales",
        "changes in a major shareholder's stake, insider dealing, a new strategic investor",
        "EGX disclosures, trading suspensions, halts, or a move between listing tiers",
        "index reviews that would add or drop the stock",
        "regulatory or state decisions bearing on this company's sector",
        "anything about the Egyptian pound, CBE rates or fuel and energy pricing that names this " +
            "company or its sector directly",
    )

    /**
     * The SEARCH block appended to the question.
     *
     * Every name the company is known by goes in, in both scripts, because the Arabic name is what
     * the Egyptian press prints and the English one is what the wires print - a search on either
     * alone misses half of what is out there.
     */
    fun query(call: ScoredCall, today: LocalDate, windowDays: Int): String = buildString {
        val from = today.minusDays(windowDays.toLong())
        appendLine("SEARCH")
        appendLine(
            "A live web search is attached to this request. Use it, then fill `news` and " +
                "`catalysts` from what you find.",
        )
        appendLine()
        appendLine("Search for this company under every name it goes by:")
        names(call).forEach { appendLine("  $it") }
        appendLine()
        appendLine("News window: $from to $today, inclusive - the last $windowDays days.")
        appendLine(
            "  Anything published before $from is outside the window. Do not put it in `news`.",
        )
        appendLine(
            "  Every item you report carries its publication date and the source that published it.",
        )
        appendLine("  Found nothing inside the window? Return `news` empty and say so honestly.")
        appendLine()
        appendLine("Look for:")
        LOOK_FOR.forEach { appendLine("  - $it") }
        appendLine()
        appendLine(
            "Then look forward, to ${today.plusDays(CATALYST_HORIZON_DAYS)}, for what is already " +
                "scheduled - results dates, coupon and ex-dividend dates, assemblies, index " +
                "reviews - and put those in `catalysts` rather than in `news`.",
        )
        appendLine()
        appendLine("Where this news is published:")
        SOURCES.forEach { appendLine("  - $it") }
        append(
            "Prefer a primary disclosure over a report of one. Where two sources disagree, say " +
                "which you are following.",
        )
    }

    /**
     * What OpenRouter prepends to the results it retrieved.
     *
     * The window rule is stated twice on purpose. This text arrives attached to the results
     * themselves, after the question has been read, and it is the last thing the model sees before
     * it decides which of them to report - a stale headline is easiest to reject right there.
     */
    fun resultPreamble(today: LocalDate, windowDays: Int): String {
        val from = today.minusDays(windowDays.toLong())
        return "A web search was run on $today for news about this Egyptian Exchange company. " +
            "Results follow. Only items published between $from and $today belong in `news`, and " +
            "each one must carry its own publication date and source. Discard results that do not " +
            "name this company or its ticker, results you cannot date, and results published " +
            "before $from. Reporting nothing is correct where nothing inside the window was found."
    }

    /**
     * Every name worth searching on, Arabic and English together.
     *
     * The catalog is consulted rather than only the call: a call carries whatever name the channel
     * happened to print, and the aliases - "CIB" for COMI, "MOPCO" for MFPC - are exactly the
     * strings the local press uses and the ticker never matches.
     */
    private fun names(call: ScoredCall): List<String> {
        val ticker = call.ticker.trim().uppercase().removeSuffix(".CA")
        val catalog = EgxCatalog.find(ticker)
        val names = buildList {
            add("$ticker (ticker, also written $ticker.CA)")
            listOfNotNull(
                catalog?.nameEnglish ?: call.companyEnglish,
                catalog?.nameArabic ?: call.companyArabic,
            ).forEach { add(it) }
            catalog?.aliases?.forEach { add(it) }
        }
        return names.map(String::trim).filter(String::isNotBlank).distinct()
    }
}
