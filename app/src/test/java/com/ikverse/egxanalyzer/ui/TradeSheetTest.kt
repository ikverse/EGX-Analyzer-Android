package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * One trade in full, as the sheet, the book pane and tabletop draw it. Held to the shape it was
 * rebuilt in on 2026-11-11: who, where it stands, the levels, what happened. One `setContent` per
 * test.
 */
@RunWith(RobolectricTestRunner::class)
class TradeSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val bought = LocalDate.of(2026, 9, 6)

    private val elka = Position(
        ticker = "ELKA", recommendationDate = bought, entryPrice = 1.9, entryDate = bought,
        entryLow = 1.9, entryHigh = 1.9, stopLoss = 1.85, target1 = 2.1, target2 = 2.2,
        channel = "CFI Egypt", companyEnglish = "El Kahera Housing", keepOpen = true,
    )

    /** Stopped out two days after it was bought, and kept open for two months since. */
    private val keptPastStop = PositionView(
        position = elka,
        status = PositionStatus.STOPPED_OUT, marketStatus = PositionStatus.STOPPED_OUT, open = true,
        currentPrice = 1.59, currentPriceOn = LocalDate.of(2026, 11, 11), exitPrice = 1.59, realized = false,
        returnPct = -16.32, sessionsElapsed = 10, sessionsHeld = 46, sessionsRemaining = 0,
        deadlineDate = LocalDate.of(2026, 9, 17), settledOn = LocalDate.of(2026, 9, 8),
        stoppedOn = LocalDate.of(2026, 9, 8), ranOutOfTime = false, overdueDays = 0,
        peakSinceEntry = 1.93, troughSinceEntry = 1.42, callEndedReturnPct = -2.63,
    )

    /** The same trade four sessions in, inside its window and above what was paid. */
    private val running = PositionView(
        position = elka.copy(keepOpen = false),
        status = PositionStatus.OPEN, marketStatus = PositionStatus.OPEN, open = true,
        currentPrice = 2.0, exitPrice = 2.0, realized = false,
        returnPct = 5.26, sessionsElapsed = 4, sessionsHeld = 4, sessionsRemaining = 6,
        deadlineDate = null, settledOn = null, ranOutOfTime = false, overdueDays = 0,
    )

    private val nothing = TradeActions(
        sell = {}, edit = {}, openCall = {}, chart = {}, keepOpen = {}, stopKeepingOpen = {},
        undoSale = {}, showOnly = {}, remove = {},
    )

    private fun show(view: PositionView, brief: Boolean = false) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TradeDetail(view, FakeAppState(), nothing, brief = brief)
                }
            }
        }
    }

    @Test
    fun `the header names the stock in full and says where the call came from`() {
        show(keptPastStop)
        compose.onNodeWithText("El Kahera Housing").assertExists()
        // "from", so the channel does not read as a second status beside Stopped out.
        compose.onNodeWithText("from CFI Egypt").assertExists()
        compose.onNodeWithText("WHERE IT STANDS").assertExists()
    }

    @Test
    fun `every level has its name, its price and its distance, at one precision`() {
        show(keptPastStop)
        compose.onNodeWithText("You paid").assertExists()
        compose.onNodeWithText("Target 1").assertExists()
        compose.onNodeWithText("2.10").assertExists()
        compose.onNodeWithText("+10.5%").assertExists()
        compose.onNodeWithText("1.59 now, below the stop").assertExists()
    }

    @Test
    fun `a trade kept past its stop says what that has cost, and has no time left to show`() {
        show(keptPastStop)
        compose.onNodeWithText("You kept it open past the stop").assertExists()
        compose.onNodeWithText("Holding since has cost 13.7 points more.", substring = true).assertExists()
        compose.onAllNodesWithText("Time left", substring = true).assertCountEquals(0)
        // "Stop keeping it open" beside "Stopped out" was two different stops in one glance.
        compose.onNodeWithText("Let it close").assertExists()
        compose.onAllNodesWithText("Stop keeping it open").assertCountEquals(0)
    }

    @Test
    fun `a trade inside its window shows its days and no note about the stop`() {
        show(running)
        compose.onNodeWithText("Time left · 6 of 10 trading days").assertExists()
        compose.onAllNodesWithText("You kept it open", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the three figures and the planned ratio are where the plan put them`() {
        show(keptPastStop)
        compose.onNodeWithText("Trading days held").assertExists()
        compose.onNodeWithText("Best").assertExists()
        // The call's plan, not the trade's result, so it waits in More numbers and is not among
        // the three figures a reader checks against the return.
        compose.onAllNodesWithText("Gain vs risk").assertCountEquals(0)
    }

    @Test
    fun `tabletop's half says the deadline has passed in words`() {
        show(keptPastStop, brief = true)
        compose.onNodeWithText("You are keeping it open.", substring = true).assertExists()
        compose.onAllNodesWithText("What happened").assertCountEquals(0)
    }

    @Test
    fun `past the call's end the timeline measures from what was paid`() {
        val steps = keptPastStop.timeline(decimals = 2).map { it.text }
        // The range was one price and it was the price paid: nothing to add to "You bought".
        assertEquals("You bought at 1.90", steps.first())
        assertTrue("Hit the stop at 1.85" in steps)
        assertTrue("The call's 10 trading days ran out" in steps)
        assertEquals("Now 1.59, 16.3% below what you paid", steps.last())
    }

    @Test
    fun `inside the window the timeline still measures to target 1`() {
        assertEquals("Now 2.00, 5.0% below target 1", running.timeline(decimals = 2).last().text)
    }

    @Test
    fun `a call that asked for another price than the one paid says so, once`() {
        val late = running.copy(position = elka.copy(entryPrice = 1.95))
        assertEquals("You bought at 1.95 · the call said 1.90", late.timeline(decimals = 2).first().text)
    }
}
