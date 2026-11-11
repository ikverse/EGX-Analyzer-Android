package com.ikverse.egxanalyzer.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a trade's one sentence should be read: as bad news, good news, or neither. */
enum class BriefTone { PLAIN, GOOD, BAD }

/** Whether the price has covered as much of the way to target 1 as the call has used of its time. */
enum class Pace { AHEAD, ON, BEHIND }

/** [pace], with the two shares it was read from, in whole percent. */
data class PaceReading(val pace: Pace, val timeUsedPct: Int, val wayPct: Int) {
    /** "On pace", or the two shares where the trade is behind and the reader will want to know by how much. */
    val words: String
        get() = when (pace) {
            Pace.AHEAD -> "Ahead of pace"
            Pace.ON -> "On pace"
            Pace.BEHIND -> {
                val way = if (wayPct <= 0) "none of the way" else "$wayPct% of the way"
                "Behind pace: $timeUsedPct% of the time used, $way"
            }
        }
}

/** The call's trading days as segments: how many of [total] are [used], and whether the deadline has passed. */
data class DaySegments(val used: Int, val total: Int, val late: Boolean)

/**
 * What an open trade's card says about it, worked out once so the card only draws.
 *
 * The card used to print facts - what was paid, what it is now, and a row of tags - and leave the
 * reader to work out which of them mattered. This is that working out: one [sentence], the most
 * urgent thing true of the trade; the time as [days]; and whether the price is keeping up with the
 * clock, as [pace].
 */
data class TradeBrief(
    val sentence: String,
    val tone: BriefTone,
    /** Null where there is no window to count, or the prices changed scale under the trade. */
    val days: DaySegments?,
    /** What the segments add up to, in words. Null with [days]. */
    val daysLine: String?,
    /** Only on a trade still inside its window, on the way to a first target it has not reached. */
    val pace: PaceReading?,
)

/**
 * Reads [view] into a [TradeBrief].
 *
 * The sentence is the first of these that is true, most urgent first: the prices changed scale;
 * the stop has been broken and the trade kept open; the deadline has passed; the price is close to
 * its stop; close to target 2; target 1 reached; and otherwise how far it is to the next target.
 *
 * [near] is the Portfolio's own reading of "close to a level" (see `needsYou`), so the sentence
 * and the approach notification agree about what close means.
 *
 * Every distance is in percent of the last close, which is how the trade's timeline and the
 * notifications measure it: "2.4% to target 1" is how far the price has to move from here.
 */
fun tradeBrief(view: PositionView, near: NeedReason? = null): TradeBrief {
    val p = view.position
    val price = view.currentPrice?.takeIf { it > 0.0 }
    val days = view.daySegments()
    val daysLine = days?.let { view.daysLine(it) }

    val stop = p.stopLoss
    val t1 = p.target1
    val t2 = p.target2
    val stopBroken = view.marketStatus == PositionStatus.STOPPED_OUT || view.stoppedAfterPartial ||
        (stop != null && price != null && price <= stop)
    val reachedT1 = view.marketStatus == PositionStatus.PARTIAL_TARGET_HIT ||
        (t1 != null && price != null && price >= t1)
    // The clock is only worth reading against a first target the trade is still on its way to.
    val pace = if (price != null && !stopBroken && !reachedT1) view.pace(price) else null

    fun brief(sentence: String, tone: BriefTone) = TradeBrief(sentence, tone, days, daysLine, pace)

    /** How far [level] is from the last close, in percent of that close. */
    fun gap(level: Double?): Double? = if (level == null || price == null) null else abs(level - price) / price * 100.0

    if (view.priceScaleChanged) {
        return TradeBrief("Its prices changed scale, so it cannot be valued", BriefTone.PLAIN, null, null, null)
    }

    if (stopBroken) return brief("Below its stop, and you are keeping it open", BriefTone.BAD)

    if (view.overdue) {
        val word = if (view.overdueDays == 1L) "day" else "days"
        return brief("Past its deadline by ${view.overdueDays} $word", BriefTone.BAD)
    }
    if (view.open && view.deadlineDate != null) return brief("Its deadline has passed", BriefTone.BAD)

    if (near == NeedReason.NEAR_STOP) {
        gap(stop)?.let { return brief("${percent(it)} above its stop", BriefTone.BAD) }
    }
    if (near == NeedReason.NEAR_TARGET2) {
        gap(p.target2)?.let { return brief("${percent(it)} from target 2", BriefTone.GOOD) }
    }

    if (reachedT1) {
        val left = t2?.takeIf { price != null && it > price }?.let(::gap)
        return brief(
            if (left != null) "Target 1 reached, ${percent(left)} to target 2" else "Target 1 reached",
            BriefTone.GOOD,
        )
    }

    if (price == null) return brief("No price for it yet", BriefTone.PLAIN)

    val next = t1 ?: t2
    if (next != null && next > price) {
        val name = if (t1 != null) "target 1" else "target 2"
        return brief("${percent(abs(next - price) / price * 100.0)} to $name", BriefTone.PLAIN)
    }

    // No target to measure toward: say where it stands against what was paid.
    val moved = (price - p.entryPrice) / p.entryPrice * 100.0
    return brief(
        when {
            moved > 0 -> "${percent(moved)} above what you paid"
            moved < 0 -> "${percent(-moved)} below what you paid"
            else -> "At what you paid"
        },
        BriefTone.PLAIN,
    )
}

private fun PositionView.daySegments(): DaySegments? {
    val total = position.windowSessions
    if (!open || total <= 0 || priceScaleChanged) return null
    val late = sessionsRemaining <= 0
    val used = if (late) total else (total - sessionsRemaining).coerceIn(0, total)
    return DaySegments(used, total, late)
}

private fun PositionView.daysLine(days: DaySegments): String {
    val word = if (days.total == 1) "day" else "days"
    return when {
        !days.late -> "$sessionsRemaining of ${days.total} trading $word left"
        keptOpen -> "All ${days.total} trading $word used · you are keeping it open"
        else -> "All ${days.total} trading $word used"
    }
}

/**
 * The share of the way from the entry to target 1 against the share of the window used.
 *
 * Read only where the comparison means something: a first target above what was paid, at least one
 * session traded, and days still left. Within [PaceBand] of each other the two are on pace - a
 * trade a tenth of the way after a fifth of its time is not yet worth a word.
 */
private fun PositionView.pace(price: Double): PaceReading? {
    val t1 = position.target1 ?: return null
    val total = position.windowSessions
    if (!open || sessionsRemaining <= 0 || total <= 0) return null
    val used = total - sessionsRemaining
    if (used < 1 || t1 <= position.entryPrice) return null
    val time = used.toDouble() / total
    val way = ((price - position.entryPrice) / (t1 - position.entryPrice)).coerceIn(0.0, 1.0)
    val pace = when {
        way >= time + PaceBand -> Pace.AHEAD
        way <= time - PaceBand -> Pace.BEHIND
        else -> Pace.ON
    }
    return PaceReading(pace, (time * 100).roundToInt(), (way * 100).roundToInt())
}

/** How far the two shares may differ and still be called on pace. */
private const val PaceBand = 0.15

private fun percent(value: Double) = String.format(Locale.US, "%.1f%%", value)
