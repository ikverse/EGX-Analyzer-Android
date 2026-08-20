package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.ScoredCall
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Everything the app knows about one call, written out for the model.
 *
 * This is context, not the question. The prompt's first section tells the model that every figure
 * here is already printed on the card the reader is looking at, and that listing them back is not
 * an opinion - so the block exists to stop the model inventing figures, not to be recited.
 *
 * Plain lines rather than JSON. The answer comes back as JSON because a parser reads it; this is
 * read by a language model, and prose costs fewer tokens than braces to say the same thing.
 */
object OpinionPrompt {

    /** Sessions shown from the feed. Enough to see the shape of the move, short of a price dump. */
    private const val SESSIONS_SHOWN = 10

    fun build(
        call: ScoredCall,
        latest: LatestPrice?,
        channel: ChannelScore?,
        held: PositionView?,
        today: LocalDate,
    ): String = buildString {
        appendLine("DATA")
        appendLine("Today: $today")
        appendLine()
        appendLine("Stock: ${call.ticker}${call.names()}")
        appendLine()
        appendLine("The recommendation, published by \"${call.channel}\" on ${call.openedOn}:")
        appendLine("  Entry band     ${call.entryBand()}")
        appendLine("  Stop loss      ${price(call.stopLoss)}")
        appendLine("  Target 1       ${price(call.target1)}")
        appendLine("  Target 2       ${price(call.target2)}")
        call.riskReward()?.let {
            appendLine("  Risk to reward ${ratio(it)} to 1, to target 1 from the middle of the band")
        }
        // Said out loud because the model is asked whether the targets are reachable in the window,
        // and a re-post is the same bet as the call it repeats rather than a fresh one.
        call.repeatOf?.let { appendLine("  Re-posted from a call first made on $it. One bet, not two.") }
        appendLine()
        appendLine("Scored by this app over a ${call.windowSessions}-session window:")
        appendLine("  Outcome        ${call.outcomeLine()}")
        appendLine("  Peak since     ${price(call.peakHigh)}${call.peakOn.on()}")
        appendLine("  Trough since   ${price(call.troughLow)}${call.troughOn.on()}")
        appendLine("  Return so far  ${percent(call.returnPct)}")
        appendLine()
        appendLine(latest.line(call))
        val sessions = call.sessions.takeLast(SESSIONS_SHOWN)
        if (sessions.isNotEmpty()) {
            appendLine()
            appendLine("Sessions from the price feed:")
            appendLine("  date        open    high    low     close")
            sessions.forEach { session ->
                appendLine(
                    "  ${session.date}  ${price(session.open).pad()}${price(session.high).pad()}" +
                        "${price(session.low).pad()}${price(session.close)}",
                )
            }
        }
        channel?.let {
            appendLine()
            appendLine("The channel's own record, measured by this app:")
            appendLine("  ${it.calls} calls, ${it.judged} judged")
            appendLine("  Average return per judged call   ${percent(it.averageReturn)}")
            appendLine("  Reached at least one target      ${rate(it.anyTargetRate)}")
            it.averageRiskReward?.let { rr ->
                appendLine("  Risk to reward on levels printed ${ratio(rr)} to 1 on average")
            }
            if (it.judged < PerformanceCalculator.MINIMUM_JUDGED_TO_RANK) {
                appendLine(
                    "  Fewer than ${PerformanceCalculator.MINIMUM_JUDGED_TO_RANK} judged calls: " +
                        "this is not yet a record.",
                )
            }
        }
        held?.let {
            appendLine()
            appendLine(
                "The reader holds this: bought at ${price(it.position.entryPrice)} on " +
                    "${it.position.entryDate}, ${if (it.open) "still open" else "closed"}, " +
                    "${percent(it.returnPct)}.",
            )
        }
    }.trimEnd()

    private fun ScoredCall.names(): String =
        listOfNotNull(companyEnglish, companyArabic)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("") { " - $it" }

    private fun ScoredCall.entryBand(): String {
        val low = entryLow
        val high = entryHigh
        return if (low != null && high != null && abs(high - low) > 1e-9) {
            "${price(low)} - ${price(high)}"
        } else {
            price(low ?: high)
        }
    }

    /** The middle of the buy zone, which every ratio here is measured from. */
    private fun ScoredCall.entryMidpoint(): Double? {
        val low = entryLow ?: entryHigh
        val high = entryHigh ?: entryLow
        return if (low == null || high == null) null else (low + high) / 2
    }

    /**
     * What the call offered against what it risked, as printed.
     *
     * Written out rather than left for the model to work out: it is the one figure that decides
     * whether a call was worth taking and is not on the card, and asking a language model to divide
     * two prices is asking for an arithmetic mistake stated with confidence.
     */
    private fun ScoredCall.riskReward(): Double? {
        val entry = entryMidpoint() ?: return null
        val stop = stopLoss ?: return null
        val target = target1 ?: target2 ?: return null
        val risk = entry - stop
        val reward = target - entry
        return if (risk <= 0 || reward <= 0) null else reward / risk
    }

    private fun ScoredCall.outcomeLine(): String = buildString {
        append(outcome.label.lowercase())
        append(", $sessionsElapsed of $windowSessions sessions elapsed")
        if (!windowComplete) append(", window still running")
        settledOn?.let { append(", settled $it") }
    }

    /**
     * Where the stock actually is, which is the price the verdict is about.
     *
     * Spelled out as such because the model is being asked about a reader standing here, not at the
     * price the channel wrote about - and those are usually different by the time anyone asks.
     */
    private fun LatestPrice?.line(call: ScoredCall): String {
        if (this == null) return "Latest close: not priced. Nothing is known about where the stock is now."
        val move = call.moveFromEntry(session.close)
        return buildString {
            append("Latest close: ${price(session.close)} on ${session.date}")
            move?.let { append(", ${percent(it)} from the middle of the entry band") }
            if (provisional) append(". That session is still trading, so the close will move")
            append(".")
        }
    }

    private fun ScoredCall.moveFromEntry(close: Double?): Double? {
        val entry = entryMidpoint() ?: return null
        if (close == null || entry <= 0) return null
        return (close - entry) / entry * 100
    }

    private fun LocalDate?.on(): String = this?.let { " on $it" }.orEmpty()

    /**
     * Up to three decimals, trailing zeros dropped - the same reading `formatPrice` gives.
     *
     * Written out here rather than imported for the reason `ReportExport` writes its own: that one
     * lives in `ui` beside Compose values that initialize with the file, so reaching for it would
     * drag a graphics stack into a prompt that has to be testable on the JVM. The rule is what
     * matters and the rule is one line - and a figure the model names back has to match the card
     * that sent it.
     */
    private fun price(value: Double?): String {
        if (value == null || value.isNaN()) return "unknown"
        val rounded = (value * 1000).roundToInt() / 1000.0
        if (abs(rounded - rounded.toLong()) < 1e-9) return rounded.toLong().toString()
        return rounded.toString().trimEnd('0').trimEnd('.')
    }

    private fun percent(value: Double?, signed: Boolean = true): String {
        if (value == null || value.isNaN()) return "unknown"
        val rounded = (value * 10).roundToInt() / 10.0
        val sign = if (signed && rounded > 0) "+" else ""
        val body = if (abs(rounded - rounded.toLong()) < 1e-9) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
        return "$sign$body%"
    }

    private fun rate(value: Double?): String = percent(value, signed = false)

    private fun ratio(value: Double): String = ((value * 100).toLong() / 100.0).toString()

    /** Columns wide enough for a price under 1000, which is every stock on this exchange. */
    private fun String.pad(): String = padEnd(8)
}
