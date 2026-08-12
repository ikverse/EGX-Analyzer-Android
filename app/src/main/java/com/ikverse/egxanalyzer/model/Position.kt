package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.LocalDate

/**
 * A trade the user actually took, recorded against the recommendation that suggested it.
 *
 * The levels are copied in rather than looked up. A report can be deleted, re-run, or replaced by a
 * later analysis of the same session, and none of that may move a trade that has already happened -
 * what was on the card when the button was pressed is what the trade was taken on.
 */
data class Position(
    /** Normalized through [Scoring.normalizeTicker], so AMOC and AMOC.CA are one holding. */
    val ticker: String,
    /**
     * The session the recommendation was for.
     *
     * Both the grouping and the deadline hang off this rather than off [entryDate]: buying two days
     * late does not buy two extra days, so a call for the 3rd stays a call for the 3rd.
     */
    val recommendationDate: LocalDate,
    /**
     * Derived from the call rather than invented, so two devices agree without coordinating.
     *
     * A random id would have made the same trade recorded on a phone and on a tablet two positions
     * that could never be merged - the sync would hand back a duplicate holding for one call. One
     * call is one holding, and that is exactly what this says.
     */
    val id: String = positionId(ticker, recommendationDate),
    val companyEnglish: String? = null,
    val companyArabic: String? = null,
    /** Who called it, for the record: a portfolio is also evidence about the sources. */
    val channel: String? = null,
    val entryPrice: Double,
    val entryDate: LocalDate,
    /** The user's own selling price, set only when they record the sale. */
    val exitPrice: Double? = null,
    val exitDate: LocalDate? = null,
    /** Sold by hand, whether or not the recommendation's window had closed. */
    val closedManually: Boolean = false,
    /** What the call asked for, kept so a card can show the trade against the advice. */
    val entryLow: Double? = null,
    val entryHigh: Double? = null,
    val target1: Double? = null,
    val target2: Double? = null,
    val stopLoss: Double? = null,
    /** The scoring window in force when the trade was recorded, which is this call's deadline. */
    val windowSessions: Int = Scoring.DEFAULT_WINDOW_SESSIONS,
    /**
     * The user set [windowSessions] by hand rather than taking the value they were offered.
     *
     * Recorded rather than worked out by comparing against the global setting, which moves: a trade
     * deliberately given fifteen sessions is still a deliberate choice after the setting is changed
     * to fifteen, and a card that stopped saying so would be hiding what the user did.
     */
    val windowCustom: Boolean = false,
    /**
     * The user has said they are still holding this, whatever the market has since done.
     *
     * Nothing closes a position carrying this except a recorded sale - not the deadline, not a
     * target, not the stop. The app's judgement of the call is not suspended by it: that is what
     * [PositionView.marketStatus] goes on saying.
     */
    val keepOpen: Boolean = false,
    /** Why, in the user's own words, so a decision made in July still explains itself in November. */
    val keepOpenNote: String? = null,
    val openedAt: Instant = Instant.now(),
    /**
     * When this revision was written, and by which device.
     *
     * A position is a row, not a file: it is edited, sold, and deleted, and two devices can do
     * different things to one while both are offline. These two decide which version wins, exactly
     * as they do for a wording rule.
     */
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
)

/** The identity of a holding: one stock, one call. */
fun positionId(ticker: String, recommendationDate: LocalDate): String =
    "$ticker@$recommendationDate"

/**
 * Where a position stands.
 *
 * Whether it is still running is [PositionView.open] rather than a property of the status: a partial
 * hit is a partial hit both while the window is open and after it has closed on one.
 */
enum class PositionStatus(val label: String) {
    OPEN("Open"),
    PARTIAL_TARGET_HIT("Partial target hit"),
    FULL_TARGET_HIT("Full target hit"),
    STOPPED_OUT("Stopped out"),
    /**
     * The window ran out with the trade still in the market's hands.
     *
     * "Closed" was the wrong word for this and said the wrong thing: a trade that simply ran out of
     * time is the one case still wanting a decision, and it read identically to one the user had
     * finished with. Closed now means only what the user means by it - sold by hand, or the targets
     * reached - and running out of time has its own name.
     */
    EXPIRED("Expired"),
    CLOSED_MANUALLY("Closed manually"),
}

/**
 * One position with everything the market has since said about it.
 *
 * Every percentage here is measured from the user's own prices. The recommendation's entry band is
 * what the channel asked for; it is not what this trade cost, and reporting the two as one figure is
 * the mistake this whole feature exists to correct.
 */
data class PositionView(
    val position: Position,
    val status: PositionStatus,
    /**
     * What the market did with the call, ignoring any manual sale.
     *
     * Kept beside [status] so selling early does not erase the answer to "was the call right" - the
     * two are different questions and a portfolio has to be able to show both.
     */
    val marketStatus: PositionStatus,
    /** Still running: it belongs under Open positions and can still be sold. */
    val open: Boolean,
    /** Latest stored close for the stock, which is as current as the daily feed gets. */
    val currentPrice: Double?,
    /** The price the return is measured to, whether the user's own or the app's estimate. */
    val exitPrice: Double?,
    /** True when [exitPrice] is the user's own selling price rather than an estimate. */
    val realized: Boolean,
    /** Profit or loss from [Position.entryPrice] to [exitPrice], in percent. */
    val returnPct: Double?,
    /** Sessions of the recommendation's window that have traded so far. */
    val sessionsElapsed: Int,
    /** Sessions left before the deadline, or zero once it has passed. */
    val sessionsRemaining: Int,
    /** The last session of the window, known only once it has actually traded. */
    val deadlineDate: LocalDate?,
    /** The session a target or the stop was reached on. */
    val settledOn: LocalDate?,
    /**
     * The deadline passed and the trade ended at no level of its own - no target 2, no stop.
     *
     * The one shape of unfinished business the app can recognise, and the whole basis of both the
     * Expired section and the overdue count. A trade that reached target 2 did what it was bought to
     * do; a trade the stop took out ended where the call said to get out; a trade the user reported
     * selling ended where they say it did. What is left is a trade that ran out of time while they
     * were still in it, which is the only case worth chasing them about.
     */
    val ranOutOfTime: Boolean,
    /**
     * Days since the deadline on a trade that ran out of time, or zero.
     *
     * Calendar days rather than sessions: a deadline that passed a fortnight ago has passed by a
     * fortnight whether or not the feed has caught up with what traded since. Zero on the day it
     * expires, so the pill starts the day after - expiring today is not being late.
     */
    val overdueDays: Long,
    /**
     * The stock's prices changed scale inside this trade's window - a split, or a bonus issue.
     *
     * The trade is neither closed nor valued while this is true, and it is not chased for being
     * overdue: the entry was paid in the old money and every price since is quoted in the new, so
     * a return measured across it would be a percentage of two different things. The card says so
     * instead, which is the one honest thing available.
     */
    val priceScaleChanged: Boolean = false,
) {
    val ticker: String get() = position.ticker
    val recommendationDate: LocalDate get() = position.recommendationDate

    /** A sale can still be recorded against any position the user has not reported selling. */
    val awaitingSale: Boolean get() = position.exitPrice == null

    /** Out of time, and late with it. */
    val overdue: Boolean get() = overdueDays > 0

    /**
     * Reached target 2, which is the one ending nothing reopens.
     *
     * A trade that did what it was bought to do is finished with. Keep Open does not survive it and
     * is not offered on it - there is nothing left to hold for.
     */
    val finished: Boolean get() = marketStatus == PositionStatus.FULL_TARGET_HIT

    /**
     * Running past its deadline because the user said to keep it running.
     *
     * Gated on [open] rather than read straight off the position: the flag stays stored through a
     * full target hit and through a sale, and a pill reading "sell to close" on a trade that is
     * already closed would be instructing the user to do something they have done.
     */
    val keptOpen: Boolean get() = position.keepOpen && open
}

/**
 * Every position taken for one recommendation session, whatever became of it.
 *
 * One card per session rather than the same session appearing under two headings: a day's trades
 * were one decision, and reading how it went meant scrolling to find its other half. The three
 * states are sections inside the card instead.
 */
data class PortfolioGroup(
    val recommendationDate: LocalDate,
    val positions: List<PositionView>,
) {
    /** Still running, the user's to sell. Includes anything they have marked Keep Open. */
    val open: List<PositionView> get() = positions.filter(PositionView::open)

    /** Out of time with nothing recorded about how it ended, which is the section wanting a reply. */
    val expired: List<PositionView>
        get() = positions.filter { !it.open && it.ranOutOfTime }

    /** Finished with: sold by hand, taken by the stop, or reached target 2. */
    val closed: List<PositionView>
        get() = positions.filter { !it.open && !it.ranOutOfTime }

    /** Holds something past its deadline with no sale recorded, and so wants reading first. */
    val hasOverdue: Boolean get() = positions.any(PositionView::overdue)

    /** Average return across the group, which is the only total available without trade sizes. */
    val averageReturnPct: Double?
        get() = positions.mapNotNull(PositionView::returnPct)
            .takeIf(List<Double>::isNotEmpty)
            ?.average()
            ?.round(2)
}

/**
 * The order the Portfolio reads in, at both levels at once.
 *
 * Two levels rather than one because a session card and the trades inside it are dated by different
 * things: the card by the session its call was made for, the trades by the day the user bought. A
 * control that ordered only the cards would leave the positions inside them in an order the user did
 * not choose and could not explain.
 *
 * [URGENT] is what the screen has always done and stays the default. The two date orders deliberately
 * carry no override - picking a date order and then watching overdue trades jump the queue anyway is
 * a control that looks broken.
 */
enum class PortfolioOrder(
    val label: String,
    /** Orders the session cards. */
    val groups: Comparator<PortfolioGroup>,
    /** Orders the positions inside one section of a card. */
    val positions: Comparator<PositionView>,
) {
    URGENT(
        "Urgent first",
        compareByDescending<PortfolioGroup> { it.hasOverdue }
            .thenByDescending { it.recommendationDate },
        compareByDescending<PositionView> { it.overdue }.thenBy { it.ticker },
    ),
    NEWEST(
        "Session date, newest",
        compareByDescending { it.recommendationDate },
        compareByDescending<PositionView> { it.position.entryDate }.thenBy { it.ticker },
    ),
    OLDEST(
        "Session date, oldest",
        compareBy { it.recommendationDate },
        compareBy<PositionView> { it.position.entryDate }.thenBy { it.ticker },
    ),
}

/**
 * The whole record, open and closed.
 *
 * Statistics live in [stats] rather than being worked out by the screen, so a figure the Portfolio
 * does not show yet - a win rate, a best trade - is already computed and only needs somewhere to be
 * drawn.
 */
data class Portfolio(
    /** One per recommendation session, holding that session's trades in every state. */
    val groups: List<PortfolioGroup> = emptyList(),
    val stats: PortfolioStats = PortfolioStats(),
) {
    val isEmpty: Boolean get() = groups.isEmpty()

    /** Every position, in no particular order, for anything that needs the flat list. */
    val positions: List<PositionView>
        get() = groups.flatMap(PortfolioGroup::positions)

    /** Looks a card's stock up by the call it belongs to. */
    fun heldFor(ticker: String, recommendationDate: LocalDate?): PositionView? {
        if (recommendationDate == null) return null
        val wanted = Scoring.normalizeTicker(ticker)
        return positions.firstOrNull {
            it.ticker == wanted && it.recommendationDate == recommendationDate
        }
    }
}

/**
 * What the record adds up to.
 *
 * Returns are percentages rather than money because no trade size is recorded: averaging percentages
 * weights a hundred-pound trade the same as a thousand-pound one, which is honest only as long as
 * nothing here claims to be a total in pounds. Adding sizes later fills these in without moving them.
 */
data class PortfolioStats(
    val total: Int = 0,
    val openCount: Int = 0,
    /**
     * No longer running, however they ended - sold, stopped, target reached, or out of time.
     *
     * Settled rather than closed, because a card below this one now reserves "closed" for the trades
     * that ended somewhere in particular. The win rate and the averages have to keep counting the
     * expired ones or they stop describing the record: a trade that went nowhere for ten sessions is
     * a result.
     */
    val settledCount: Int = 0,
    /**
     * Past their deadline with no sale recorded, which is the only figure here asking to be acted on.
     *
     * Counted over everything rather than over open positions alone: a trade the deadline closed
     * while the user was still holding it is exactly the one that needs chasing, and it is not open.
     */
    val overdueCount: Int = 0,
    /** Average return over settled positions - what the record actually delivered. */
    val settledReturnPct: Double? = null,
    /** Average return over open positions, which can still change. */
    val openReturnPct: Double? = null,
    /** Share of settled positions that ended in profit. */
    val winRate: Double? = null,
    /** Average return over every position, open and closed. */
    val averageReturnPct: Double? = null,
    val best: PositionView? = null,
    val worst: PositionView? = null,
)

/**
 * The session a call belongs to.
 *
 * Shared by the scorer and the portfolio deliberately: a trade filed under one date and its call
 * scored under another would show a position whose recommendation the Insights tab cannot find.
 * The run's target date wins, because a back-dated analysis is still an analysis of that session.
 */
fun RecommendationDataPoint.callDate(targetDate: LocalDate?): LocalDate? =
    targetDate ?: date ?: visibleSourceDate?.let { value ->
        runCatching { LocalDate.parse(value.trim().take(10)) }.getOrNull()
    }
