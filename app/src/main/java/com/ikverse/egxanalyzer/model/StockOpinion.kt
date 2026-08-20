package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/**
 * What the model says about one stock and the call made on it.
 *
 * Two judgements and three lists. The judgements are what it makes of the stock and what it makes
 * of the levels the channel printed; every figure a reader might otherwise want is already on the
 * card this opens from, which is why the prompt spends a whole section forbidding the model from
 * reciting them back.
 *
 * The lists are what the judgements rest on and were the thing missing: [news] is what a searched
 * request actually found, dated and attributed, rather than melted into a paragraph where a
 * headline from last year reads exactly like one from last week; [catalysts] is what is already
 * scheduled ahead of the reader; [risks] is what goes wrong for someone who buys.
 *
 * The token fields stay English in transit and are printed in Arabic by the screen. Letting the
 * model answer them in Arabic would put the parser at the mercy of its choice of synonym, and a
 * verdict that failed to parse is a verdict the card cannot colour.
 */
data class StockOpinion(
    val verdict: Verdict,
    val horizon: Horizon,
    val confidence: Confidence,
    /** The answer before the reasoning, in Arabic. */
    val headline: String,
    /** The stock itself, in Arabic: what moves it and what to want before paying today's price. */
    val outlook: String,
    val onTheCall: CallView,
    /**
     * What a live search turned up inside the window it was given, newest first.
     *
     * Empty where the search found nothing, and empty where no search was attached. Those are
     * different facts and [searched] is what tells them apart - the sheet says so, because "we
     * looked and the fortnight was quiet" and "we did not look" lead a reader to opposite places.
     */
    val news: List<NewsItem> = emptyList(),
    /** Dated events ahead of the reader rather than news behind them. Nearest first. */
    val catalysts: List<Catalyst> = emptyList(),
    /** What goes wrong for a reader who buys at today's close, most likely first, in Arabic. */
    val risks: List<String> = emptyList(),
    /** What the model could not see. Empty where it claimed to be missing nothing. */
    val unknowns: List<String> = emptyList(),
    /**
     * The lookback the news was asked for, in days.
     *
     * Kept on the answer rather than read from Settings when the sheet draws: the setting can be
     * changed afterwards, and an opinion has to keep saying what window it was actually given.
     * Zero on an opinion saved before the window existed.
     */
    val newsWindowDays: Int = 0,
    /** Named on the sheet, because an opinion is only readable beside who gave it. */
    val model: String,
    val askedOn: LocalDate,
    /** Whether live search was attached, which is the difference between a view and a guess. */
    val searched: Boolean,
) {
    data class CallView(val stance: Stance, val detail: String)

    /**
     * One thing the search found.
     *
     * [date] is kept as the model wrote it rather than parsed on the way in. The prompt asks for
     * ISO and the model usually obliges, but an item that arrives dated "last Thursday" is still
     * worth showing with that written under it - dropping it would be throwing away something the
     * user paid for, and silently.
     */
    data class NewsItem(
        val headline: String,
        val date: String,
        val source: String,
        val tone: Tone,
    ) {
        /** The date where it parses, for ordering and for checking it against the window. */
        val on: LocalDate? get() = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
    }

    /** What an item means for the price, as the item reads. */
    enum class Tone(val arabic: String) {
        BULLISH("إيجابي"),
        BEARISH("سلبي"),
        NEUTRAL("محايد"),
    }

    /**
     * Something already scheduled that has not happened yet.
     *
     * [on] is a string for the same reason a news date is: "2026-09-14" and "with the Q3 results"
     * are both honest answers, and forcing the second into a date would invent a precision the
     * model was told not to claim.
     */
    data class Catalyst(val what: String, val on: String, val source: String)

    /** Whether to buy at the latest close - not at the price the channel wrote about. */
    enum class Verdict(val arabic: String) {
        BUY_NOW("شراء الآن"),
        WAIT("انتظار"),
        AVOID("تجنّب"),
    }

    enum class Horizon(val arabic: String) {
        SHORT("أجل قصير"),
        LONG("أجل طويل"),
        BOTH("قصير وطويل"),

        /** The answer is not to buy at all, so there is no holding period to name. */
        NEITHER("لا شراء"),
    }

    enum class Confidence(val arabic: String) {
        LOW("ثقة منخفضة"),
        MEDIUM("ثقة متوسطة"),
        HIGH("ثقة مرتفعة"),
    }

    /** What the model makes of the levels as printed. */
    enum class Stance(val arabic: String) {
        SOUND("سليمة"),
        RISKY("محفوفة بالمخاطر"),
        UNSOUND("غير سليمة"),

        /** The price left the levels behind, whatever the call was worth when it was published. */
        OVERTAKEN("تجاوزها السعر"),
    }
}

/**
 * The call an opinion is about.
 *
 * Ticker, session and channel together, because two channels calling one stock on one session are
 * two cards on the screen and deserve two opinions - they are being asked about different levels.
 * This is deliberately not [positionId], which ignores the channel because one holding is one
 * holding however many sources called it.
 */
fun opinionId(ticker: String, openedOn: LocalDate, channel: String): String =
    "${Scoring.normalizeTicker(ticker)}@$openedOn@${channel.trim()}"
