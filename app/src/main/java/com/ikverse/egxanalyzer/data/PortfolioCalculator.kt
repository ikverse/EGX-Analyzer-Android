package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.Portfolio
import com.ikverse.egxanalyzer.model.PortfolioGroup
import com.ikverse.egxanalyzer.model.PortfolioStats
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.round
import java.time.LocalDate

/**
 * Turns recorded trades plus stored sessions into the portfolio.
 *
 * Two rules run through all of it. The deadline belongs to the recommendation - a window of N
 * trading sessions counted from the session it was made for, whenever the user happened to buy - and
 * every percentage is measured from the price the user actually paid, never from the entry band the
 * channel printed.
 */
object PortfolioCalculator {

    fun build(
        positions: List<Position>,
        /** Sessions for one stock from a date onward, oldest first. */
        sessionsFor: (ticker: String, from: LocalDate) -> List<DailySession>,
        /** The latest close the feed has for a stock, which is as current as a daily feed gets. */
        latestCloseFor: (ticker: String) -> Double?,
    ): Portfolio {
        val views = positions.map { position ->
            evaluate(
                position = position,
                sessions = sessionsFor(position.ticker, position.recommendationDate),
                currentPrice = latestCloseFor(position.ticker),
            )
        }
        return Portfolio(
            open = views.filter(PositionView::open).grouped(),
            closed = views.filterNot(PositionView::open).grouped(),
            stats = stats(views),
        )
    }

    /**
     * Where one position stands, given every session since the call was made.
     *
     * The scoring is the app's own, called with no entry band: the user has bought, so the entry is
     * a fact rather than something the market still has to offer. That keeps the 2% stop rule and
     * the feed's float slack identical to every other judgement the app makes.
     */
    internal fun evaluate(
        position: Position,
        sessions: List<DailySession>,
        currentPrice: Double?,
    ): PositionView {
        // The deadline set: the recommendation's own window, counted from its own session.
        val window = sessions.take(position.windowSessions)
        val elapsed = window.size
        val remaining = (position.windowSessions - elapsed).coerceAtLeast(0)
        val deadlineDate = window.lastOrNull()?.date?.takeIf { remaining == 0 }

        // Only the part of the window the user actually held. A target reached before they bought
        // is not their gain, and a stop broken before they bought never touched them.
        val skipped = window.count { it.date < position.entryDate }
        val held = window.drop(skipped)
        val scored = Scoring.score(
            sessions = held,
            entryLow = null,
            entryHigh = null,
            target1 = position.target1,
            target2 = position.target2,
            stopLoss = position.stopLoss,
            // What the window still owes once the sessions before the entry are taken off, so the
            // scorer calls the window complete exactly when the recommendation's deadline passes.
            windowSessions = (position.windowSessions - skipped).coerceAtLeast(1),
        )

        val marketStatus = scored.outcome.asPositionStatus()
        val settledByMarket = when (marketStatus) {
            PositionStatus.FULL_TARGET_HIT, PositionStatus.STOPPED_OUT -> true
            PositionStatus.PARTIAL_TARGET_HIT ->
                scored.stoppedAfterPartial || scored.windowComplete
            PositionStatus.CLOSED -> true
            else -> false
        }
        val soldByHand = position.exitPrice != null
        val open = !soldByHand && !settledByMarket

        val exit = when {
            // The user's own price, and the only figure here that is not an estimate.
            soldByHand -> position.exitPrice
            open -> currentPrice
            else -> position.estimatedExit(scored.outcome, scored.stoppedAfterPartial, window)
        }

        return PositionView(
            position = position,
            status = if (position.closedManually) PositionStatus.CLOSED_MANUALLY else marketStatus,
            marketStatus = marketStatus,
            open = open,
            currentPrice = currentPrice,
            exitPrice = exit,
            realized = soldByHand,
            returnPct = returnPct(position.entryPrice, exit),
            sessionsElapsed = elapsed,
            sessionsRemaining = remaining,
            deadlineDate = deadlineDate,
            settledOn = scored.settledOn,
        )
    }

    /**
     * Where a position that closed without a recorded sale is valued.
     *
     * A level the market itself reached where there is one - the stop it broke, the target it met -
     * because that is where a holder following the call would have got out. Otherwise the last close
     * of the window, which is simply where the position stood when the deadline arrived. Always an
     * estimate, and labelled as one: the user never said they sold.
     */
    private fun Position.estimatedExit(
        outcome: Outcome,
        stoppedAfterPartial: Boolean,
        window: List<DailySession>,
    ): Double? {
        val lastClose = window.lastOrNull { it.traded.close != null }?.traded?.close
        return when {
            outcome == Outcome.FULL_HIT -> (target2 ?: target1) ?: lastClose
            outcome == Outcome.STOPPED -> stopLoss ?: lastClose
            outcome == Outcome.PARTIAL_HIT && stoppedAfterPartial -> stopLoss ?: lastClose
            else -> lastClose
        }
    }

    /** Profit or loss from what was paid to what it is worth, which is the only basis used here. */
    internal fun returnPct(entry: Double, exit: Double?): Double? {
        if (exit == null || entry == 0.0) return null
        return ((exit - entry) / entry * 100).round(2)
    }

    private fun Outcome.asPositionStatus(): PositionStatus = when (this) {
        Outcome.FULL_HIT -> PositionStatus.FULL_TARGET_HIT
        Outcome.PARTIAL_HIT -> PositionStatus.PARTIAL_TARGET_HIT
        Outcome.STOPPED -> PositionStatus.STOPPED_OUT
        Outcome.EXPIRED -> PositionStatus.CLOSED
        // The rest cannot arise for a held position: the entry is given rather than waited for, so
        // nothing is unreachable and no two events need ordering against it. A stock with no stored
        // sessions yet is simply open and unpriced.
        else -> PositionStatus.OPEN
    }

    /** Newest session first, matching how every other list of target dates in the app reads. */
    private fun List<PositionView>.grouped(): List<PortfolioGroup> = groupBy(PositionView::recommendationDate)
        .map { (date, held) ->
            PortfolioGroup(date, held.sortedBy { it.ticker })
        }
        .sortedByDescending(PortfolioGroup::recommendationDate)

    /**
     * The record as figures.
     *
     * Closed positions carry the verdict - they are what the record delivered - while open ones are
     * reported apart, because a position still running can still change its mind.
     */
    internal fun stats(views: List<PositionView>): PortfolioStats {
        if (views.isEmpty()) return PortfolioStats()
        val open = views.filter(PositionView::open)
        val closed = views.filterNot(PositionView::open)
        val settledReturns = closed.mapNotNull(PositionView::returnPct)
        return PortfolioStats(
            total = views.size,
            openCount = open.size,
            closedCount = closed.size,
            realizedReturnPct = settledReturns.meanOrNull(),
            openReturnPct = open.mapNotNull(PositionView::returnPct).meanOrNull(),
            winRate = settledReturns
                .takeIf(List<Double>::isNotEmpty)
                ?.let { returns -> (returns.count { it > 0 }.toDouble() / returns.size * 100).round(1) },
            averageReturnPct = views.mapNotNull(PositionView::returnPct).meanOrNull(),
            // Best and worst over everything: a position running at -20% is worth seeing before it
            // closes, not after.
            best = views.filter { it.returnPct != null }.maxByOrNull { it.returnPct!! },
            worst = views.filter { it.returnPct != null }.minByOrNull { it.returnPct!! },
        )
    }

    /** The mean, or nothing at all where there is nothing to average. */
    private fun List<Double>.meanOrNull(): Double? =
        takeIf(List<Double>::isNotEmpty)?.let { it.sum() / it.size }?.round(2)
}
