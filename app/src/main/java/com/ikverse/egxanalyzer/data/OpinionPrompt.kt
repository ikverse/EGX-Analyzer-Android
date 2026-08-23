package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.ScoredCall
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Everything the app knows about one call, written out for the model.
 *
 * This is context, not the question. The prompt's first section tells the model that every figure
 * here is already printed on the card the reader is looking at, and that reciting them back is not
 * an opinion - so the block exists to stop the model inventing figures, not to be read out.
 *
 * Not everything here is on the card, and the parts that are not are the point. How much of this
 * stock actually trades, where the price sits against its own averages, and who else is calling it
 * are things the app has always held and never sent. None of them cost an extra request, and no
 * amount of web search would find them: they are measurements of this user's own data.
 *
 * Plain lines rather than JSON. The answer comes back as JSON because a parser reads it; this is
 * read by a language model, and prose costs fewer tokens than braces to say the same thing.
 */
object OpinionPrompt {

    /**
     * Sessions shown from the feed. Enough to see the shape of the move, short of a price dump.
     *
     * Raised to thirty on the theory that the run-up before the call is worth seeing, then put
     * back: thirty rows is about 1,100 characters on every press, and the two figures that
     * actually place the price - the averages and the period high and low - are computed from the
     * whole feed regardless of how much of it is printed here. The table is for shape, and ten
     * sessions show a shape.
     */
    private const val SESSIONS_SHOWN = 10

    /** Sessions behind the liquidity figures - a month of trading, which is what a month tells. */
    private const val LIQUIDITY_SESSIONS = 20

    /** The window the high and the low are measured over, when the feed reaches that far. */
    private const val RANGE_SESSIONS = 60

    private const val SHORT_AVERAGE = 20
    private const val LONG_AVERAGE = 50

    /**
     * What a position in this stock would cost, for judging whether it can be traded at all.
     *
     * A round number rather than the user's real size, which the app does not know. It is here so
     * the model has something to measure a day's turnover against instead of guessing what "thin"
     * means on an exchange it may know little about.
     */
    private const val REFERENCE_POSITION_EGP = 100_000

    fun build(
        call: ScoredCall,
        latest: LatestPrice?,
        channel: ChannelScore?,
        held: PositionView?,
        today: LocalDate,
        /**
         * Price history reaching back further than the call's own judged window.
         *
         * The call carries only the sessions it was scored on - ten, usually - which is not enough
         * to average over or to place the price in a range. Empty falls back to those sessions, so
         * a caller that has no history still produces a valid block.
         */
        history: List<DailySession> = emptyList(),
        /** Every other call the app holds on this stock, from any channel. */
        otherCalls: List<ScoredCall> = emptyList(),
        /** The SEARCH block, when this request carries a live web search. */
        search: String? = null,
    ): String = buildString {
        val feed = history.ifEmpty { call.sessions }
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
        // Said out loud because the model is asked whether the targets are reachable from here,
        // and a re-post is the same bet as the call it repeats rather than a fresh one.
        call.repeatOf?.let { appendLine("  Re-posted from a call first made on $it. One bet, not two.") }
        appendLine()
        appendLine("Scored by this app against real prices:")
        appendLine("  Outcome        ${call.outcomeLine()}")
        appendLine("  Peak since     ${price(call.peakHigh)}${call.peakOn.on()}")
        appendLine("  Trough since   ${price(call.troughLow)}${call.troughOn.on()}")
        appendLine("  Return so far  ${percent(call.returnPct)}")
        appendLine()
        appendLine(latest.line(call))
        appendStanding(feed)
        appendLiquidity(feed)
        appendSessions(feed)
        channel?.let { appendChannel(it) }
        appendOtherCalls(call, otherCalls)
        held?.let {
            appendLine()
            appendLine(
                "The reader holds this: bought at ${price(it.position.entryPrice)} on " +
                    "${it.position.entryDate}, ${if (it.open) "still open" else "closed"}, " +
                    "${percent(it.returnPct)}.",
            )
        }
        search?.takeIf(String::isNotBlank)?.let {
            appendLine()
            appendLine()
            append(it)
        }
    }.trimEnd()

    /**
     * Where the price sits against its own history.
     *
     * None of this is on the card, and all of it is arithmetic the model would otherwise be asked
     * to do in its head over a table of thirty rows. A moving average worked out wrong is worse
     * than one left out, so it is worked out here or not stated.
     */
    private fun StringBuilder.appendStanding(feed: List<DailySession>) {
        val closes = feed.mapNotNull(DailySession::close)
        val short = closes.averageOfLast(SHORT_AVERAGE)
        val long = closes.averageOfLast(LONG_AVERAGE)
        val ranged = feed.takeLast(RANGE_SESSIONS)
        val high = ranged.mapNotNull { session -> session.high?.let { it to session.date } }.maxByOrNull { it.first }
        val low = ranged.mapNotNull { session -> session.low?.let { it to session.date } }.minByOrNull { it.first }
        if (short == null && long == null && high == null) return
        appendLine()
        appendLine("Where the price stands, measured from the feed:")
        short?.let { appendLine("  $SHORT_AVERAGE-session average close   ${price(it)}") }
        long?.let { appendLine("  $LONG_AVERAGE-session average close   ${price(it)}") }
        if (high != null && low != null) {
            appendLine(
                "  Over the last ${ranged.size} sessions: high ${price(high.first)} on " +
                    "${high.second}, low ${price(low.first)} on ${low.second}",
            )
        }
    }

    /**
     * Whether the stock can actually be traded, which no chart says and no search finds.
     *
     * A call on a stock turning over a few thousand pounds a session is not a trade the reader can
     * take at any size, however good the levels look - the spread and the exit are the whole story
     * and the levels are decoration. The app has held the volume since the feed was built and has
     * never once sent it.
     */
    private fun StringBuilder.appendLiquidity(feed: List<DailySession>) {
        val recent = feed.takeLast(LIQUIDITY_SESSIONS)
        val traded = recent.mapNotNull { session ->
            val volume = session.volume ?: return@mapNotNull null
            val close = session.close ?: return@mapNotNull null
            if (volume <= 0 || close <= 0) null else volume to volume * close
        }
        appendLine()
        if (traded.isEmpty()) {
            appendLine(
                "How much trades: no volume recorded in the feed, so nothing is known about " +
                    "whether this stock can be traded at size.",
            )
            return
        }
        val sessions = traded.size
        val averageVolume = traded.sumOf { it.first } / sessions
        val averageValue = traded.sumOf { it.second } / sessions
        appendLine("How much trades, over the last $sessions sessions:")
        appendLine("  Average volume         ${count(averageVolume)} shares a session")
        appendLine("  Average value traded   ${money(averageValue)} a session")
        // Stated as a share of a day rather than left as two large numbers to compare. The point is
        // whether getting out takes a morning or a fortnight, and that is the ratio, not the total.
        val share = REFERENCE_POSITION_EGP / averageValue * 100
        appendLine(
            "  A ${money(REFERENCE_POSITION_EGP.toDouble())} position is " +
                "${percent(share, signed = false)} of one session's turnover.",
        )
    }

    private fun StringBuilder.appendSessions(feed: List<DailySession>) {
        val sessions = feed.takeLast(SESSIONS_SHOWN)
        if (sessions.isEmpty()) return
        appendLine()
        appendLine("Sessions from the price feed:")
        appendLine("  date        open    high    low     close   volume")
        sessions.forEach { session ->
            appendLine(
                "  ${session.date}  ${price(session.open).pad()}${price(session.high).pad()}" +
                    "${price(session.low).pad()}${price(session.close).pad()}" +
                    count(session.volume),
            )
        }
    }

    private fun StringBuilder.appendChannel(score: ChannelScore) {
        appendLine()
        appendLine("The channel's own record, measured by this app:")
        appendLine("  ${score.calls} calls, ${score.judged} judged")
        appendLine("  Average return per judged call   ${percent(score.averageReturn)}")
        appendLine("  Reached at least one target      ${rate(score.anyTargetRate)}")
        score.averageRiskReward?.let { rr ->
            appendLine("  Risk to reward on levels printed ${ratio(rr)} to 1 on average")
        }
        if (score.judged < PerformanceCalculator.MINIMUM_JUDGED_TO_RANK) {
            appendLine(
                "  Fewer than ${PerformanceCalculator.MINIMUM_JUDGED_TO_RANK} judged calls: " +
                    "this is not yet a record.",
            )
        }
    }

    /**
     * Who else has called this stock, and how those ended.
     *
     * Two different things sharing one section because they are read the same way. Calls open now
     * are crowding - the prompt says as much, so the model does not read four channels agreeing as
     * four reasons - and calls already settled are the only record the app holds of what happens
     * when this particular stock is recommended.
     */
    private fun StringBuilder.appendOtherCalls(call: ScoredCall, others: List<ScoredCall>) {
        val relevant = others.filter { it.openedOn != call.openedOn || it.channel != call.channel }
        if (relevant.isEmpty()) return
        val (open, settled) = relevant.partition { !it.outcome.judged }
        if (open.isNotEmpty()) {
            appendLine()
            appendLine("Other channels calling this stock now:")
            open.sortedByDescending(ScoredCall::openedOn).take(5).forEach {
                appendLine(
                    "  \"${it.channel}\" on ${it.openedOn}: entry ${it.entryBand()}, " +
                        "stop ${price(it.stopLoss)}, target ${price(it.target1 ?: it.target2)}",
                )
            }
        }
        if (settled.isNotEmpty()) {
            appendLine()
            appendLine("Earlier calls on this stock, already judged:")
            settled.sortedByDescending(ScoredCall::openedOn).take(5).forEach {
                appendLine(
                    "  \"${it.channel}\" on ${it.openedOn}: ${it.outcome.label.lowercase()}, " +
                        "${percent(it.returnPct)}",
                )
            }
        }
    }

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

    /**
     * What became of the call, and how long it took about it.
     *
     * The elapsed count is bare rather than a fraction of a window. A call is followed until it
     * settles, so "6 of 10" was describing a deadline the app no longer keeps - and read to a model
     * being asked whether a target is still reachable, a denominator is an invitation to answer
     * about the time left rather than about the stock.
     */
    private fun ScoredCall.outcomeLine(): String = buildString {
        append(outcome.label.lowercase())
        append(", $sessionsElapsed ${if (sessionsElapsed == 1) "session" else "sessions"} elapsed")
        // Only a partial hit can still improve, and `windowComplete` is only ever set on one - so
        // testing it alone put "still running" onto full hits and stop-outs, which had settled.
        if (outcome == Outcome.PARTIAL_HIT && !windowComplete) append(", target 2 still in reach")
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

    /** Null rather than a short average: five sessions called a 20-session mean is a wrong figure. */
    private fun List<Double>.averageOfLast(count: Int): Double? =
        if (size < count) null else takeLast(count).average()

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

    /**
     * A share count at the scale a reader would say it.
     *
     * Grouped rather than written out in full: 1,240,000 and 1240000 are the same number, and only
     * one of them can be read at a glance in a table of thirty rows.
     */
    private fun count(value: Double?): String {
        if (value == null || value.isNaN() || value <= 0) return "unknown"
        return when {
            value >= 1_000_000 -> "${((value / 100_000).roundToLong() / 10.0)}M"
            value >= 1_000 -> "${(value / 1_000).roundToLong()}k"
            else -> value.roundToLong().toString()
        }
    }

    /** Turnover in pounds, at the scale the exchange reports it. */
    private fun money(value: Double): String = when {
        value >= 1_000_000 -> "${((value / 100_000).roundToLong() / 10.0)}M EGP"
        value >= 1_000 -> "${(value / 1_000).roundToLong()}k EGP"
        else -> "${value.roundToLong()} EGP"
    }

    /** Columns wide enough for a price under 1000, which is every stock on this exchange. */
    private fun String.pad(): String = padEnd(8)
}
