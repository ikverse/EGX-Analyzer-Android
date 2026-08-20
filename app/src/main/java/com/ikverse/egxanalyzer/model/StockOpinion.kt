package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/**
 * What the model says about one stock and the call made on it.
 *
 * Two answers and nothing else: what it makes of the stock, and what it makes of the levels the
 * channel printed. Every other figure a reader might want is already on the card this opens from,
 * which is why the prompt spends a whole section forbidding the model from listing them back.
 *
 * The four token fields stay English in transit and are printed in Arabic by the screen. Letting
 * the model answer them in Arabic would put the parser at the mercy of its choice of synonym, and
 * a verdict that failed to parse is a verdict the card cannot colour.
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
    /** What the model could not see. Empty where it claimed to be missing nothing. */
    val unknowns: List<String> = emptyList(),
    /** Named on the sheet, because an opinion is only readable beside who gave it. */
    val model: String,
    val askedOn: LocalDate,
    /** Whether live search was attached, which is the difference between a view and a guess. */
    val searched: Boolean,
) {
    data class CallView(val stance: Stance, val detail: String)

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
