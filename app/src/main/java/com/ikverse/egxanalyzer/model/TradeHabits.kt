package com.ikverse.egxanalyzer.model

/**
 * The reader's own habits across finished trades: how often they sold before target 1, held past
 * the stop, or closed by hand, and what those trades returned against what the call itself did.
 *
 * In percent, never money, like everything on the Portfolio. The call's figure is its scored
 * return, matched by the trade's id (one trade per call), so "you" and "the call" are measured
 * from the same entry band only where the reader bought inside it.
 */
data class HabitGroup(
    val trades: Int,
    /** What those trades returned, on average. */
    val yourReturn: Double?,
    /** What the calls they were taken on returned, on average, over the trades that have one. */
    val callReturn: Double?,
)

data class TradeHabits(
    val finished: Int,
    /** Sold below target 1 and above the stop, before the call reached target 1. */
    val soldEarly: HabitGroup,
    /** Got out below the stop: held on after it was crossed. */
    val heldPastStop: HabitGroup,
    /** Closed by hand rather than by a level or the deadline. */
    val closedByHand: HabitGroup,
) {
    val isEmpty: Boolean get() = finished == 0
}

/**
 * [positions] that are finished and sold, read for the three habits. [callReturn] gives the scored
 * return of the call a trade was taken on, or null where the record no longer holds it.
 */
fun tradeHabits(positions: List<PositionView>, callReturn: (Position) -> Double?): TradeHabits {
    val finished = positions.filter { !it.open && it.realized }
    fun group(of: List<PositionView>) = HabitGroup(
        trades = of.size,
        yourReturn = of.mapNotNull(PositionView::returnPct).takeIf(List<Double>::isNotEmpty)?.average(),
        callReturn = of.mapNotNull { callReturn(it.position) }.takeIf(List<Double>::isNotEmpty)?.average(),
    )
    val early = finished.filter { view ->
        val p = view.position
        val exit = p.exitPrice ?: p.exitPrice1 ?: return@filter false
        val t1 = p.target1 ?: return@filter false
        exit < t1 && (p.stopLoss == null || exit > p.stopLoss) &&
            view.marketStatus !in setOf(PositionStatus.PARTIAL_TARGET_HIT, PositionStatus.FULL_TARGET_HIT)
    }
    val pastStop = finished.filter { view ->
        val p = view.position
        val exit = p.exitPrice ?: p.exitPrice1 ?: return@filter false
        val stop = p.stopLoss ?: return@filter false
        exit < stop
    }
    return TradeHabits(
        finished = finished.size,
        soldEarly = group(early),
        heldPastStop = group(pastStop),
        closedByHand = group(finished.filter { it.position.closedManually }),
    )
}
