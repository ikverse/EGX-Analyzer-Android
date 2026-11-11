package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/**
 * Which part of the unfolded Portfolio's table a trade sits in, in the order the table shows them.
 *
 * The open list used to be one run of rows in the page's sort order, so a trade that was simply
 * running sat between two whose stop had broken. The groups put what needs nothing first and say,
 * in their headings, what the rest are waiting for.
 */
enum class TradeRowGroup(val heading: String) {
    RUNNING("Running"),
    TO_SELL("Waiting for you to sell"),
    PAST_DEADLINE("Past deadline"),
    FINISHED("Finished"),
}

/**
 * The group [this] belongs to.
 *
 * An open trade whose stop has broken is one the user is keeping open past it, and so is one past
 * its deadline: see `PositionView.open`. The stop wins where both are true, being the worse news.
 */
fun PositionView.tableGroup(): TradeRowGroup = when {
    !open -> TradeRowGroup.FINISHED
    status == PositionStatus.STOPPED_OUT || marketStatus == PositionStatus.STOPPED_OUT -> TradeRowGroup.TO_SELL
    overdue || ranOutOfTime -> TradeRowGroup.PAST_DEADLINE
    else -> TradeRowGroup.RUNNING
}

/** Open trades in the table's order: by [tableGroup], and inside a group as they arrived. */
fun List<PositionView>.inTableOrder(): List<PositionView> = sortedBy { it.tableGroup().ordinal }

/** The words under the ticker: what the trade is doing, or what it is waiting for. */
fun PositionView.tableStatusWord(): String = when (tableGroup()) {
    TradeRowGroup.TO_SELL -> "Stop hit, sell"
    TradeRowGroup.PAST_DEADLINE -> "Past deadline"
    else -> status.plainWord()
}

/**
 * The line under the return: how long is left, when the stop broke, how late, or how long it took.
 *
 * It used to read "0 trading days left" on every trade kept open past its stop or its deadline,
 * which is true of the window and says nothing about the trade.
 *
 * @param date how a date is written, the screen's own short form.
 */
fun PositionView.tableTimeWord(date: (LocalDate) -> String): String = when (tableGroup()) {
    TradeRowGroup.RUNNING -> "$sessionsRemaining ${if (sessionsRemaining == 1) "day" else "days"} left"
    TradeRowGroup.TO_SELL -> (stoppedOn ?: settledOn)?.let { "hit ${date(it)}" } ?: "stop hit"
    TradeRowGroup.PAST_DEADLINE -> if (overdueDays > 0) "${overdueDays}d late" else "time is up"
    TradeRowGroup.FINISHED -> when {
        ranOutOfTime && awaitingSale -> "time is up"
        else -> "in $sessionsHeld ${if (sessionsHeld == 1) "day" else "days"}"
    }
}

/**
 * Where a trade's price stands between its stop and its far target, measured from what was paid.
 *
 * @param fill -1 at the stop or below, 0 at the price paid, 1 at the far target or above.
 * @param target1At how far along the gain side the first target sits, 0..1, where there are two.
 * @param stop the price under the loss end, and [far] the one under the gain end.
 * @param pastStop the price is below the stop, so the loss side is full and says so.
 * @param pastFar the same beyond the far target.
 */
data class LevelBar(
    val fill: Float,
    val target1At: Float?,
    val stop: Double?,
    val far: Double?,
    val pastStop: Boolean,
    val pastFar: Boolean,
)

/**
 * Reads a trade's levels into a [LevelBar].
 *
 * Each side is its own scale: the loss side runs from [paid] down to [stop], the gain side from
 * [paid] up to the far target. So the bar's centre is always what was paid, and a trade that risks
 * little for a lot still reads as half way to each end when it is. A side with no usable level
 * (none printed, or one on the wrong side of the entry) stays empty.
 */
fun levelBar(paid: Double, now: Double?, stop: Double?, target1: Double?, target2: Double?): LevelBar {
    val usableStop = stop?.takeIf { it < paid }
    val far = (target2 ?: target1)?.takeIf { it > paid }
    val fill = when {
        now == null -> 0f
        now >= paid -> far?.let { ((now - paid) / (it - paid)).coerceIn(0.0, 1.0).toFloat() } ?: 0f
        else -> usableStop?.let { -((paid - now) / (paid - it)).coerceIn(0.0, 1.0).toFloat() } ?: 0f
    }
    val notch = target1
        ?.takeIf { target2 != null && far != null && it > paid && it < far }
        ?.let { ((it - paid) / (far!! - paid)).toFloat() }
    return LevelBar(
        fill = fill,
        target1At = notch,
        stop = usableStop,
        far = far,
        pastStop = now != null && usableStop != null && now < usableStop,
        pastFar = now != null && far != null && now > far,
    )
}
