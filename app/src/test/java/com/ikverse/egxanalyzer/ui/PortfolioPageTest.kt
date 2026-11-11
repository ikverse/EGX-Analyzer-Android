package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.model.Portfolio
import com.ikverse.egxanalyzer.model.PortfolioGroup
import com.ikverse.egxanalyzer.model.PortfolioStats
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.robolectric.annotation.Config
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The 5.0 Portfolio page at the four ways the Fold is held: its tiles, the decision panel, the
 * target bars, and the unfolded trade pane. One `setContent` per test.
 */
@RunWith(RobolectricTestRunner::class)
class PortfolioPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val called = LocalDate.of(2026, 11, 3)

    private fun open(ticker: String, entry: Double, now: Double, stop: Double, t1: Double, t2: Double) = PositionView(
        position = Position(
            ticker = ticker, recommendationDate = called, entryPrice = entry, entryDate = called,
            stopLoss = stop, target1 = t1, target2 = t2, channel = "Tadawul Calls",
        ),
        status = PositionStatus.OPEN, marketStatus = PositionStatus.OPEN, open = true,
        currentPrice = now, exitPrice = now, realized = false,
        returnPct = (now - entry) / entry * 100, sessionsElapsed = 4, sessionsHeld = 4, sessionsRemaining = 6,
        deadlineDate = null, settledOn = null, ranOutOfTime = false, overdueDays = 0,
    )

    // COMI is well clear of its levels; ABUK is 1.2% above its stop, inside the default 2% warning.
    private val comi = open("COMI", 76.2, 82.4, 72.0, 80.0, 86.5)
    private val abuk = open("ABUK", 68.0, 65.5, 64.7, 74.0, 78.0)

    private val portfolio = Portfolio(
        groups = listOf(PortfolioGroup(called, listOf(comi, abuk))),
        stats = PortfolioStats(total = 2, openCount = 2, openReturnPct = 2.6, winRate = 64.0),
    )

    private fun show(posture: FoldPosture, state: AppState = FakeAppState(portfolio = portfolio)) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalFoldPosture provides posture) {
                    PortfolioScreen(state)
                }
            }
        }
    }

    @Test
    fun `the cover screen shows the record and the open trades`() {
        show(FoldPosture.Cover)
        compose.onNodeWithText("Your trades so far").assertExists()
        compose.onNodeWithText("Open now").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("You paid", substring = true).onFirst().assertExists()
        assertEquals(2, compose.onAllNodesWithText("Running").fetchSemanticsNodes().size)
    }

    /** COMI three days past a deadline that ran out, and ABUK still open. */
    private val late = comi.copy(
        status = PositionStatus.EXPIRED, marketStatus = PositionStatus.EXPIRED, open = false,
        deadlineDate = called.plusDays(10), ranOutOfTime = true, overdueDays = 3,
    )

    private fun lateState(): FakeAppState = FakeAppState(
        portfolio = Portfolio(groups = listOf(PortfolioGroup(called, listOf(late, abuk))), stats = portfolio.stats),
    )

    @Test
    fun `needs you names a late trade and I sold it goes to the sale`() {
        val sold = mutableListOf<String>()
        val base = lateState()
        val state = object : AppState by base {
            override fun openPositionToSell(id: String) { sold += id }
        }
        show(FoldPosture.Cover, state)
        compose.onNodeWithText("Needs you · 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("COMI has run out of time").assertExists()
        compose.onNodeWithText("I sold it").performScrollTo().performClick()
        assertEquals(listOf(late.position.id), sold)
    }

    @Test
    @Config(qualifiers = "w750dp-h891dp")
    fun `unfolded, pressing a trade opens it beside the table`() {
        val state = FakeAppState(portfolio = portfolio)
        show(FoldPosture.Unfolded, state)
        compose.onNodeWithText("COMI").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(comi.position.id, state.pages.portfolioSelectedTrade.value)
        compose.onNodeWithText("since you bought", substring = true).assertExists()
    }

    @Test
    fun `book posture lays the list beside the trade in full`() {
        show(FoldPosture.Book(0.49f, 0.51f))
        compose.onNodeWithText("Open now").assertExists()
        compose.onNodeWithText("Time left", substring = true).assertExists()
    }

    @Test
    fun `tabletop draws the record and the trade's actions`() {
        show(FoldPosture.Tabletop(0.5f, 0.51f))
        compose.onNodeWithText("Your trades so far").assertExists()
        compose.onNodeWithText("I sold it").assertExists()
    }
    @Test
    fun `the filter sheet opens on the page's own title and still offers the order`() {
        val state = FakeAppState(portfolio = portfolio)
        state.pages.portfolioFiltersOpen.value = true
        show(FoldPosture.Cover, state)
        compose.onNodeWithText("Filter positions").assertExists()
        compose.onNodeWithText("Sort").assertExists()
    }

    private val recent = (3 downTo 1).map { back ->
        com.ikverse.egxanalyzer.model.ReturnPoint(
            LocalDate.now(com.ikverse.egxanalyzer.model.ScheduleClock.ZONE).minusDays(back.toLong()), back.toDouble(), 2,
        )
    }

    @Test
    fun `the hero carries the EGX30 line and its legend when there is one`() {
        val index = recent.map { com.ikverse.egxanalyzer.model.IndexPoint(it.date, 1.0) }
        show(FoldPosture.Cover, FakeAppState(portfolio = portfolio, returnByDay = recent, indexByDay = index))
        compose.onNodeWithText("EGX30").assertExists()
        compose.onNodeWithText("Your trades").assertExists()
        compose.onNodeWithText("EGX30 did", substring = true).assertExists()
    }

    @Test
    fun `with no index fetched the hero has no legend`() {
        show(FoldPosture.Cover, FakeAppState(portfolio = portfolio, returnByDay = recent))
        compose.onNodeWithText("EGX30").assertDoesNotExist()
        compose.onNodeWithText("EGX30 did", substring = true).assertDoesNotExist()
    }

    @Test
    fun `an empty record says how to start one`() {
        show(FoldPosture.Cover, FakeAppState(portfolio = Portfolio()))
        compose.onNodeWithText("No trades recorded yet").assertIsDisplayed()
    }

    @Test
    fun `holding a trade opens its menu`() {
        show(FoldPosture.Cover)
        compose.onAllNodesWithText("You paid", substring = true).onFirst().performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("Fix the trade").assertIsDisplayed()
    }

    @Test
    fun `a trade close to its stop says so and one clear of it does not`() {
        show(FoldPosture.Cover)
        // ABUK is 1.2% above its stop; COMI is well clear.
        assertEquals(1, compose.onAllNodesWithText("Close to its stop").fetchSemanticsNodes().size)
    }

    private fun finished(ticker: String, ret: Double) = comi.copy(
        // A copy keeps the id it was built with, so each finished trade is given its own.
        position = comi.position.copy(
            ticker = ticker,
            id = com.ikverse.egxanalyzer.model.positionId(ticker, called),
            exitPrice = 80.0,
            exitDate = called.plusDays(5),
        ),
        status = PositionStatus.CLOSED_MANUALLY, marketStatus = PositionStatus.CLOSED_MANUALLY, open = false,
        realized = true, returnPct = ret,
    )

    private val fourDone = listOf(finished("SWDY", 4.0), finished("ETEL", 2.0), finished("HRHO", -1.0), finished("FWRY", 3.0))

    private fun doneState(pending: String? = null) = FakeAppState(
        portfolio = Portfolio(groups = listOf(PortfolioGroup(called, listOf(comi) + fourDone)), stats = portfolio.stats),
        pendingPositionId = pending,
    )

    @Test
    fun `see all reveals the fourth finished trade`() {
        show(FoldPosture.Cover, doneState())
        val fourth = fourDone.sortedWith(com.ikverse.egxanalyzer.model.PortfolioOrder.URGENT.positions).last().ticker
        compose.onNodeWithText(fourth).assertDoesNotExist()
        compose.onNodeWithText("See all 4").performScrollTo().performClick()
        compose.onNodeWithText(fourth).assertExists()
    }

    @Test
    @Config(qualifiers = "w750dp-h891dp")
    fun `the scope chips filter the table`() {
        show(FoldPosture.Unfolded, doneState())
        compose.onNodeWithText("COMI").assertExists()
        compose.onNodeWithText("Finished 4").performClick()
        compose.onNodeWithText("COMI").assertDoesNotExist()
        compose.onNodeWithText("SWDY").assertExists()
    }

    @Test
    fun `more numbers shows the targets`() {
        val state = FakeAppState(portfolio = portfolio)
        state.pages.portfolioSelectedTrade.value = comi.position.id
        show(FoldPosture.Cover, state)
        compose.onNodeWithText("TARGET 2").assertDoesNotExist()
        compose.onNodeWithText("More numbers").performScrollTo().performClick()
        compose.onNodeWithText("TARGET 2").assertExists()
    }

    @Test
    fun `a trade chosen before a fold into book posture is the one drawn in full`() {
        val state = FakeAppState(portfolio = portfolio)
        state.pages.portfolioSelectedTrade.value = abuk.position.id
        show(FoldPosture.Book(0.49f, 0.51f), state)
        // Its row in the list and its own header beside it.
        assertEquals(2, compose.onAllNodesWithText("ABUK").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("COMI").fetchSemanticsNodes().size)
    }

    @Test
    fun `needs you's return takes the ink tile's own loss colour in the dark theme`() {
        val signals = com.ikverse.egxanalyzer.ui.kit.tileSignalsFor(
            com.ikverse.egxanalyzer.ui.theme.DarkPalette,
            com.ikverse.egxanalyzer.ui.kit.TileTone.INK,
        )
        assertEquals(signals.loss, returnTone(-3.0, signals))
        // The ink tile is light in the dark theme, so its loss is not the dark page's bright red.
        assertNotEquals(com.ikverse.egxanalyzer.ui.theme.DarkPalette.loss, returnTone(-3.0, signals))
    }

    @Test
    fun `arriving at a finished trade past the first three opens See all`() {
        val fourth = fourDone.sortedWith(com.ikverse.egxanalyzer.model.PortfolioOrder.URGENT.positions).last()
        val state = doneState(pending = fourth.position.id)
        show(FoldPosture.Cover, state)
        compose.waitForIdle()
        assertTrue(state.pages.portfolioFinishedAll.value)
    }

    /** Counts the arrivals a screen marks as done. */
    private class Arrivals(base: AppState) : AppState by base {
        var positions = 0
        var sells = 0
        override fun consumePendingPosition() { positions++ }
        override fun consumePendingSell() { sells++ }
    }

    @Test
    @Config(qualifiers = "w750dp-h891dp")
    fun `unfolded, Record sale from the shade opens the sale`() {
        val state = Arrivals(FakeAppState(portfolio = portfolio, pendingSellPositionId = comi.position.id))
        show(FoldPosture.Unfolded, state)
        compose.waitForIdle()
        assertEquals(TradeDialogRequest(comi.position.id, TradeDialogKind.SELL), state.pages.portfolioDialog.value)
        assertEquals(1, state.sells)
    }

    @Test
    @Config(qualifiers = "w750dp-h891dp")
    fun `unfolded, arriving at a trade opens it and ends the arrival`() {
        val state = Arrivals(FakeAppState(portfolio = portfolio, pendingPositionId = abuk.position.id))
        show(FoldPosture.Unfolded, state)
        compose.waitForIdle()
        assertEquals(abuk.position.id, state.pages.portfolioSelectedTrade.value)
        assertEquals(1, state.positions)
    }

    @Test
    fun `arriving at a finished trade ends the arrival`() {
        val base = FakeAppState(
            portfolio = Portfolio(groups = listOf(PortfolioGroup(called, listOf(late, abuk))), stats = portfolio.stats),
            pendingPositionId = late.position.id,
        )
        val state = Arrivals(base)
        show(FoldPosture.Cover, state)
        compose.waitForIdle()
        assertEquals(1, state.positions)
    }
}
