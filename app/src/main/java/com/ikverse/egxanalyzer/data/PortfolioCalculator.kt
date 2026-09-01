package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.Portfolio
import com.ikverse.egxanalyzer.model.PortfolioGroup
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.PortfolioStats
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.Quote
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.round
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Turns recorded trades plus stored sessions into the portfolio.
 *
 * Three rules run through all of it. The deadline belongs to the recommendation - a window of N
 * trading sessions counted from the session it was made for, whenever the user happened to buy -
 * every percentage is measured from the price the user actually paid, never from the entry band the
 * channel printed, and a position the user has marked Keep Open ends only when they record a sale or
 * when it reaches target 2.
 */
object PortfolioCalculator {

    fun build(
        positions: List<Position>,
        /** Sessions for one stock from a date onward, oldest first. */
        sessionsFor: (ticker: String, from: LocalDate) -> List<DailySession>,
        /**
         * The latest close the feed has for a stock, with the session it closed on.
         *
         * The date rides along rather than being fetched beside it: the price and the day it was
         * set are one fact, and a card that showed the figure without the day was reporting a
         * fortnight-old close as today's.
         */
        latestQuoteFor: (ticker: String) -> Quote?,
        /**
         * The exchange's calendar date, and only for how late a trade is running.
         *
         * Passed in rather than read here, so that can be tested at all. The exchange's zone in the
         * default, not the device's: how many days past its deadline a trade has run is counted in
         * Cairo's days, whatever day it happens to be where the phone is.
         */
        today: LocalDate = LocalDate.now(ScheduleClock.ZONE),
        /**
         * The newest session the exchange has finished with, and what decides a window is spent.
         *
         * Separate from [today] because they answer different questions, and the difference between
         * them is the afternoon: a window whose last session ended at 14:30 is spent from the close,
         * while the trade it retires only becomes a day overdue when the date turns over. Both are
         * read once for a whole recompute - see [PerformanceCalculator.report] - so nothing here
         * can judge two trades against two different afternoons.
         */
        finalThrough: LocalDate = ScheduleClock.lastFinalSession(),
        /** The sessions on which each stock's prices changed scale - a split, a bonus issue. */
        priceBreaksFor: (ticker: String) -> Set<LocalDate> = { emptySet() },
    ): Portfolio {
        val views = positions.map { position ->
            val quote = latestQuoteFor(position.ticker)
            evaluate(
                position = position,
                sessions = sessionsFor(position.ticker, position.recommendationDate),
                currentPrice = quote?.price,
                currentPriceOn = quote?.on,
                today = today,
                finalThrough = finalThrough,
                priceBreaks = priceBreaksFor(position.ticker),
            )
        }
        return Portfolio(groups = views.grouped(), stats = stats(views))
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
        /** Defaulted rather than required: a caller with no feed date still has a price to score. */
        currentPriceOn: LocalDate? = null,
        today: LocalDate = LocalDate.now(ScheduleClock.ZONE),
        finalThrough: LocalDate = ScheduleClock.lastFinalSession(),
        priceBreaks: Set<LocalDate> = emptySet(),
    ): PositionView {
        // The deadline set: the recommendation's own window, counted from its own session.
        val window = sessions.take(position.windowSessions)
        val elapsed = window.size
        // Sessions the window has actually spent, which is not the same as sessions it has rows
        // for. A session dated today is in the price table from the opening bell and is still
        // trading, so counting it spent retired the trade at the open of the session it was meant
        // to be sold in - and on a two-session T+1 window that is the whole of the sell side. The
        // same test [Scoring.score] applies to the call this trade was taken on, so the Portfolio
        // and Insights can never disagree about whether one session's window has run out - and
        // both now turn over at the close rather than at midnight, so a trade whose last session
        // ended this afternoon is expired this afternoon.
        val spent = window.count { it.date <= finalThrough }
        val remaining = (position.windowSessions - spent).coerceAtLeast(0)
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
            priceBreaks = priceBreaks,
            finalThrough = finalThrough,
        )

        // The prices moved scale under this trade. Every figure on the card would be measured
        // across that - the return from an entry paid in the old money to a close quoted in the
        // new - so the trade stays open and says why rather than being valued on it.
        val priceScaleChanged = scored.outcome == Outcome.PRICE_BREAK
        val marketStatus = scored.outcome.asPositionStatus()
        val settledByMarket = when (marketStatus) {
            PositionStatus.FULL_TARGET_HIT, PositionStatus.STOPPED_OUT -> true
            PositionStatus.PARTIAL_TARGET_HIT ->
                scored.stoppedAfterPartial || scored.windowComplete
            PositionStatus.EXPIRED -> true
            else -> false
        }
        val soldByHand = position.exitPrice != null
        // Keep Open defeats every automatic close but one: a stop the market broke is a fact about
        // the call, and a user who is still holding is still holding. Target 2 is the exception,
        // because it is not a close the user might disagree with - the trade did the thing it was
        // bought to do, and there is nothing left to keep it open for.
        val open = !soldByHand &&
            marketStatus != PositionStatus.FULL_TARGET_HIT &&
            (position.keepOpen || !settledByMarket)

        // The one state the overdue clock runs on. Keeping a trade past its deadline is a decision
        // the user made and can unmake, so the app has something to ask them about; a deadline that
        // merely ran out is the app's own answer, and chasing that would be chasing itself.
        val keptOpen = open && position.keepOpen

        // Ran out of time rather than ending anywhere: no target 2, no stop, and no sale the user
        // told us about. This is the whole of the Expired section, and half of the overdue clock -
        // the other half being [keptOpen], so a trade that reached its target is never chased and
        // one that went nowhere is chased only while the user is choosing to stay in it.
        val ranOutOfTime = !soldByHand &&
            deadlineDate != null &&
            marketStatus != PositionStatus.FULL_TARGET_HIT &&
            marketStatus != PositionStatus.STOPPED_OUT &&
            !scored.stoppedAfterPartial &&
            // Nagging someone daily about a trade the app has just admitted it cannot read would be
            // asking them to answer for the price feed.
            !priceScaleChanged

        val exit = when {
            // The user's own price, and the only figure here that is not an estimate. Both ends are
            // theirs, so a split between them is already reflected in what they actually got.
            soldByHand -> position.exitPrice
            // Nothing to measure to. The entry was paid in the old money and every price since is
            // quoted in the new, so any figure here would be a percentage of two different things.
            priceScaleChanged -> null
            open -> currentPrice
            else -> position.estimatedExit(scored.outcome, scored.stoppedAfterPartial, window)
        }

        return PositionView(
            position = position,
            status = when {
                position.closedManually -> PositionStatus.CLOSED_MANUALLY
                // A kept-open position whose window merely ran out is still open, and a chip
                // reading "Expired" above a trade sitting in the Open section would be a
                // contradiction. Every other verdict survives: a target the market reached is
                // worth seeing on a position still running, exactly as a partial hit already is.
                open && marketStatus == PositionStatus.EXPIRED -> PositionStatus.OPEN
                else -> marketStatus
            },
            marketStatus = marketStatus,
            open = open,
            currentPrice = currentPrice,
            currentPriceOn = currentPriceOn,
            exitPrice = exit,
            realized = soldByHand,
            returnPct = returnPct(position.entryPrice, exit),
            sessionsElapsed = elapsed,
            sessionsHeld = held.size,
            sessionsRemaining = remaining,
            deadlineDate = deadlineDate,
            settledOn = scored.settledOn,
            stoppedOn = scored.stoppedOn,
            stoppedAfterPartial = scored.stoppedAfterPartial,
            // Read off the held sessions rather than the whole window, because that is what the
            // scorer was handed: a high set before the user bought belongs to the call, not to
            // them. Dropped entirely across a change of scale, for the reason [exit] is - a high
            // quoted in the old money against an entry paid in it is a percentage of two different
            // things, and the card says so rather than printing one.
            peakSinceEntry = scored.peakHigh.takeUnless { priceScaleChanged },
            peakOn = scored.peakOn.takeUnless { priceScaleChanged },
            troughSinceEntry = scored.troughLow.takeUnless { priceScaleChanged },
            troughOn = scored.troughOn.takeUnless { priceScaleChanged },
            ranOutOfTime = ranOutOfTime,
            overdueDays = overdueDays(ranOutOfTime && keptOpen, deadlineDate, today),
            priceScaleChanged = priceScaleChanged,
        )
    }

    /**
     * How far past its deadline a trade the user is deliberately still holding has run.
     *
     * Two conditions, and both are the rule. The trade ended nowhere - a position the user reported
     * selling is over, a target reached is the trade doing what it was bought to do, and a stop
     * broken is the call's own instruction being followed - and the user marked it Keep Open. A
     * window that simply ran out closes the trade as expired, which is an answer in itself: it sits
     * in the Expired section saying so, and nothing counts it or asks after it again. What is left
     * is a trade running past its deadline only because the user said it should, which is the one
     * thing here worth a reminder.
     *
     * Zero on the day the deadline lands, so the pill starts the morning after.
     */
    private fun overdueDays(
        chased: Boolean,
        deadlineDate: LocalDate?,
        today: LocalDate,
    ): Long {
        if (!chased || deadlineDate == null) return 0
        return ChronoUnit.DAYS.between(deadlineDate, today).coerceAtLeast(0)
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
        Outcome.EXPIRED -> PositionStatus.EXPIRED
        // The rest cannot arise for a held position: the entry is given rather than waited for, so
        // nothing is unreachable and no two events need ordering against it. A stock with no stored
        // sessions yet is simply open and unpriced.
        else -> PositionStatus.OPEN
    }

    /**
     * One group per session, in the screen's default order.
     *
     * [PortfolioOrder.URGENT] rather than a comparator written out here: the Portfolio lets the user
     * pick an order, so the rule has to live somewhere both this and the screen can reach, or the
     * default and the option the user chooses drift apart. Anything reading the portfolio without a
     * screen - the overdue worker, a test - gets the same order the screen opens on.
     */
    private fun List<PositionView>.grouped(): List<PortfolioGroup> =
        groupBy(PositionView::recommendationDate)
            .map { (date, held) ->
                PortfolioGroup(date, held.sortedWith(PortfolioOrder.URGENT.positions))
            }
            .sortedWith(PortfolioOrder.URGENT.groups)

    /**
     * The record as figures.
     *
     * Settled positions carry the verdict - they are what the record delivered - while open ones are
     * reported apart, because a position still running can still change its mind. Settled counts
     * every trade no longer running, the expired ones included: a trade that went nowhere for ten
     * sessions is a result, and leaving it out would flatter the win rate.
     */
    internal fun stats(views: List<PositionView>): PortfolioStats {
        if (views.isEmpty()) return PortfolioStats()
        val open = views.filter(PositionView::open)
        val settled = views.filterNot(PositionView::open)
        val settledReturns = settled.mapNotNull(PositionView::returnPct)
        return PortfolioStats(
            total = views.size,
            openCount = open.size,
            settledCount = settled.size,
            overdueCount = views.count(PositionView::overdue),
            settledReturnPct = settledReturns.meanOrNull(),
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
