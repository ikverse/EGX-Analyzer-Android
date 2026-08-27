package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/**
 * What the model says about one stock and the call made on it.
 *
 * Four judgements and three lists. The judgements are how the stock has actually traded, what the
 * business is, where it goes over three spans of time, and what the levels the channel printed are
 * worth.
 *
 * [standing] and [forecast] were added because without them every answer read the same. The prompt
 * had one free-text field for the stock and forbade the model from naming any figure in it, so on a
 * quiet stock there was nothing specific left to say and what came back fitted any ticker on the
 * exchange. [standing] is measured rather than recalled and cannot come out the same twice;
 * [forecast] asks three questions where there was one, and a reason that carries all three spans is
 * one reading rather than three.
 *
 * The lists are what the judgements rest on: [news] is what a searched request actually found,
 * dated and attributed, rather than melted into a paragraph where a headline from last year reads
 * exactly like one from last week; [catalysts] is what is already scheduled ahead of the reader;
 * [risks] is what goes wrong for someone who buys.
 *
 * The token fields stay English in transit and are printed in Arabic by the screen. Letting the
 * model answer them in Arabic would put the parser at the mercy of its choice of synonym, and a
 * verdict that failed to parse is a verdict the card cannot colour.
 */
data class StockOpinion(
    val verdict: Verdict,
    /**
     * How long to hold, derived from [forecast] rather than asked for.
     *
     * Schema 3 stopped asking the model for this. Two fields answering "how long" is two fields
     * free to contradict each other - a `SHORT` horizon printed beside a short leg pointing down -
     * and the forecast is strictly the more informative of the two. Kept on the answer because
     * opinions saved under schema 2 carry one and the sheet still prints theirs.
     */
    val horizon: Horizon,
    val confidence: Confidence,
    /** The answer before the reasoning, in Arabic. */
    val headline: String,
    /**
     * How the stock has actually traded, in Arabic, against the figures the app measured.
     *
     * Blank on an opinion given under schema 2, which had no such field - not on one where the
     * model declined to answer, which the parser refuses outright.
     */
    val standing: String = "",
    /** The business itself, in Arabic: what moves it and what to want before paying today's price. */
    val outlook: String,
    /** Where it goes over three spans. Null on a schema 2 answer and on one that arrived short. */
    val forecast: Forecast? = null,
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
    /**
     * The levels as printed, judged one number at a time.
     *
     * [detail] used to be the whole of this: three or four sentences covering the ratio, the stop,
     * both targets and whether the price had left the band behind. Four questions in one paragraph
     * average into the same paragraph - "the ratio is acceptable but the stop is tight" fitted
     * almost every call - and the reader had asked what the *numbers* were worth. [checks] asks
     * each of the five separately and rates it, so two calls on one stock can disagree visibly.
     *
     * Empty on a schema 2 answer, which carried only the prose.
     */
    data class CallView(
        val stance: Stance,
        val detail: String,
        val checks: List<Check> = emptyList(),
    )

    /** One of the printed numbers, rated on its own. [note] is Arabic and names a figure. */
    data class Check(val item: CheckItem, val rating: Rating, val note: String)

    /**
     * Where the stock goes, over three spans that are asked separately.
     *
     * A list rather than three named fields because the sheet reads it as a row of three and the
     * store writes it as an array; [Leg.span] carries which is which. The parser builds one only
     * where all three arrived, so anything holding a [Forecast] is holding a complete one - a
     * two-legged forecast on screen would be the app deciding which span to drop.
     */
    data class Forecast(val legs: List<Leg>) {

        fun leg(span: Span): Leg? = legs.firstOrNull { it.span == span }

        /** One span's reading. [confidence] is this leg's own and usually falls as the span grows. */
        data class Leg(
            val span: Span,
            val direction: Direction,
            /** One or two sentences, in Arabic, and not the reason another leg gave. */
            val why: String,
            val confidence: Confidence,
        )
    }

    /**
     * The three spans, in the order they are read.
     *
     * [window] is printed under the Arabic label rather than left to the reader: "medium" is a word
     * every market column uses and none of them define, and a forecast whose span is guessed at is
     * a forecast about nothing.
     */
    enum class Span(val arabic: String, val window: String) {
        SHORT("أجل قصير", "days to 4 weeks"),
        MEDIUM("أجل متوسط", "1 to 3 months"),
        LONG("أجل طويل", "6 to 12 months"),
    }

    /**
     * Where the price goes over one span - not whether to buy.
     *
     * Deliberately separate from [Verdict]: a stock can be heading up and still be a poor buy at
     * today's close because the move is already paid for, and collapsing the two would lose exactly
     * that answer.
     */
    enum class Direction(val arabic: String) {
        UP("صاعد"),
        DOWN("هابط"),
        SIDEWAYS("عرضي"),
    }

    /** Which printed number a [Check] is about. The label is English, like every heading. */
    enum class CheckItem(val label: String) {
        RISK_REWARD("Risk to reward"),
        STOP("Stop"),
        TARGET_1("Target 1"),
        TARGET_2("Target 2"),
        ENTRY_STILL_VALID("Entry still valid"),
    }

    /** What one printed number is worth, on its own. */
    enum class Rating(val arabic: String) {
        GOOD("جيد"),
        FAIR("مقبول"),
        POOR("ضعيف"),
    }

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
        NEITHER("لا شراء");

        companion object {
            /**
             * The holding period the forecast implies, since the model is no longer asked for one.
             *
             * Three spans read into four values, so the mapping has to say what it is doing:
             * `SHORT` means the case is in the near spans, `LONG` that it is out at six to twelve
             * months, `BOTH` that it is at both ends. A medium leg counts toward `SHORT` because
             * one to three months is a holding period a reader plans in weeks, not the one they
             * plan in years.
             *
             * `NEITHER` on `AVOID`, whatever the directions say - there is no holding period on a
             * stock the answer says not to buy - and on an answer that carried no forecast at all,
             * which is a schema 2 row being re-read or an answer that arrived short.
             */
            fun from(verdict: Verdict, forecast: Forecast?): Horizon {
                if (verdict == Verdict.AVOID || forecast == null) return NEITHER
                fun rising(span: Span) = forecast.leg(span)?.direction == Direction.UP
                val near = rising(Span.SHORT) || rising(Span.MEDIUM)
                val far = rising(Span.LONG)
                return when {
                    near && far -> BOTH
                    far -> LONG
                    near -> SHORT
                    else -> NEITHER
                }
            }
        }
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
