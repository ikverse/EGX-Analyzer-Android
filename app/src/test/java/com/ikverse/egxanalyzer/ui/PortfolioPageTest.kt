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
    fun `the cover screen shows every tile and the record`() {
        show(FoldPosture.Cover)
        compose.onNodeWithText("Open trades · 2 · average return").assertExists()
        compose.onNodeWithText("Hit rate · 5 weeks").assertExists()
        compose.onNodeWithText("Needs you").assertExists()
        compose.onNodeWithText("Open positions").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("COMI: 60% of the way to target 2").assertExists()
        compose.onNodeWithText("All positions").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `needs you opens the decision panel and Record sale goes to the sale`() {
        val sold = mutableListOf<String>()
        val base = FakeAppState(portfolio = portfolio)
        val state = object : AppState by base {
            override fun openPositionToSell(id: String) { sold += id }
        }
        show(FoldPosture.Cover, state)
        // Scrolled to first: the test window is short, and since the session line grew to hold the
        // header's buttons (2026-11-11) this tile starts below its foot, where a press lands on nothing.
        compose.onNodeWithText("Needs you").performScrollTo().performClick()
        compose.onNodeWithText("Needs you · 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Record sale").performScrollTo().performClick()
        assertEquals(listOf(abuk.position.id), sold)
    }

    @Test
    fun `unfolded, pressing a position opens its card beside the tiles`() {
        show(FoldPosture.Unfolded)
        compose.onNodeWithContentDescription("COMI: 60% of the way to target 2").performScrollTo().performClick()
        compose.onNodeWithText("Selected trade").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `book posture lays the tiles and the record side by side`() {
        show(FoldPosture.Book(0.49f, 0.51f))
        compose.onNodeWithText("Hit rate · 5 weeks").assertExists()
        compose.onNodeWithText("All positions").assertExists()
    }

    @Test
    fun `tabletop draws the hero and everything below it`() {
        show(FoldPosture.Tabletop(0.5f, 0.51f))
        compose.onNodeWithText("Open trades · 2 · average return").assertExists()
        compose.onNodeWithText("Needs you").assertExists()
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
    }

    @Test
    fun `with no index fetched the hero has no legend`() {
        show(FoldPosture.Cover, FakeAppState(portfolio = portfolio, returnByDay = recent))
        compose.onNodeWithText("EGX30").assertDoesNotExist()
    }

    @Test
    fun `an empty record says how to start one`() {
        show(FoldPosture.Cover, FakeAppState(portfolio = Portfolio()))
        compose.onNodeWithText("No trades recorded yet").assertIsDisplayed()
    }
}
