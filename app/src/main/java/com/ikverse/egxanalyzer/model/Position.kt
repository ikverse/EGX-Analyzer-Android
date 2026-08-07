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
    CLOSED("Closed"),
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
) {
    val ticker: String get() = position.ticker
    val recommendationDate: LocalDate get() = position.recommendationDate

    /** A sale can still be recorded against any position the user has not reported selling. */
    val awaitingSale: Boolean get() = position.exitPrice == null
}

/** Every position taken for one recommendation session. */
data class PortfolioGroup(
    val recommendationDate: LocalDate,
    val positions: List<PositionView>,
) {
    /** Average return across the group, which is the only total available without trade sizes. */
    val averageReturnPct: Double?
        get() = positions.mapNotNull(PositionView::returnPct)
            .takeIf(List<Double>::isNotEmpty)
            ?.average()
            ?.round(2)
}

/**
 * The whole record, open and closed.
 *
 * Statistics live in [stats] rather than being worked out by the screen, so a figure the Portfolio
 * does not show yet - a win rate, a best trade - is already computed and only needs somewhere to be
 * drawn.
 */
data class Portfolio(
    val open: List<PortfolioGroup> = emptyList(),
    val closed: List<PortfolioGroup> = emptyList(),
    val stats: PortfolioStats = PortfolioStats(),
) {
    val isEmpty: Boolean get() = open.isEmpty() && closed.isEmpty()

    /** Every position, in no particular order, for anything that needs the flat list. */
    val positions: List<PositionView>
        get() = (open + closed).flatMap(PortfolioGroup::positions)

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
    val closedCount: Int = 0,
    /** Average return over closed positions - what the record actually delivered. */
    val realizedReturnPct: Double? = null,
    /** Average return over open positions, which can still change. */
    val openReturnPct: Double? = null,
    /** Share of closed positions that ended in profit. */
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
