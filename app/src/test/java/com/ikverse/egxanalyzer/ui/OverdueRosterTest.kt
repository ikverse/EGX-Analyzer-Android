package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * What the Overdue card names, and in what order.
 *
 * The card is the one place in the app that interrupts, so what it leaves out matters as much as
 * what it holds: a trade that ended somewhere - sold, stopped, target reached - is not asking for
 * anything, and putting it here would teach the reader to ignore the card.
 */
class OverdueRosterTest {
    private val called = LocalDate.of(2026, 8, 5)

    private fun view(
        ticker: String,
        overdueDays: Long,
        open: Boolean = false,
        keepOpen: Boolean = false,
        status: PositionStatus = PositionStatus.EXPIRED,
    ) = PositionView(
        position = Position(
            ticker = ticker,
            recommendationDate = called,
            entryPrice = 10.0,
            entryDate = called,
            keepOpen = keepOpen,
        ),
        status = status,
        marketStatus = status,
        open = open,
        currentPrice = 10.5,
        exitPrice = 10.5,
        realized = false,
        returnPct = 5.0,
        sessionsElapsed = 10,
        sessionsRemaining = 0,
        deadlineDate = called.plusDays(14),
        settledOn = null,
        ranOutOfTime = overdueDays > 0,
        overdueDays = overdueDays,
    )

    @Test
    fun `only the trades that are actually late`() {
        val roster = overdueRoster(
            listOf(
                view("AMOC", overdueDays = 3),
                // Running, and inside its window: nothing to chase.
                view("SWDY", overdueDays = 0, open = true, status = PositionStatus.OPEN),
                // Expiring today is not being late - the day count is what decides, not the status.
                view("ORWE", overdueDays = 0),
                view("HRHO", overdueDays = 1),
            ),
        )

        assertEquals(listOf("AMOC", "HRHO"), roster.map(PositionView::ticker))
    }

    @Test
    fun `the latest trade leads, and ties are settled by ticker`() {
        val roster = overdueRoster(
            listOf(
                view("SWDY", overdueDays = 2),
                view("ORWE", overdueDays = 9),
                view("ABUK", overdueDays = 2),
            ),
        )

        assertEquals(listOf("ORWE", "ABUK", "SWDY"), roster.map(PositionView::ticker))
    }

    /**
     * Kept open is still overdue.
     *
     * The trade sits under Open rather than Expired, deliberately - the user is holding it on
     * purpose - but the deadline has still passed with no sale recorded, and the card says so with
     * the reason beside it. Dropping these would make the card disagree with the notification,
     * which counts them.
     */
    @Test
    fun `a trade kept open past its deadline is on the roster`() {
        val roster = overdueRoster(
            listOf(view("AMOC", overdueDays = 4, open = true, keepOpen = true)),
        )

        assertEquals(listOf("AMOC"), roster.map(PositionView::ticker))
        assertEquals(true, roster.single().keptOpen)
    }
}
