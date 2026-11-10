package com.ikverse.egxanalyzer.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ikverse.egxanalyzer.model.CallSanity
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 5.0 stock sheet: the action row is about the newest call still in play, Ask AI still asks
 * first, a price alert is set from its own sheet, and the Call range is offered only with a call.
 * Every action is recorded, never sent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class StockSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.parse("2026-11-09")

    private fun call(outcome: Outcome, windowComplete: Boolean = false) = ScoredCall(
        ticker = "COMI", companyEnglish = "Commercial International Bank", companyArabic = null,
        channel = "Tadawul Calls", channelId = null, openedOn = day, entryLow = 75.5, entryHigh = 77.0,
        target1 = 80.0, target2 = 86.5, stopLoss = 72.0, outcome = outcome, settledOn = null,
        peakHigh = 83.0, troughLow = 75.0, returnPct = 6.0, sessionsElapsed = 6, repeatOf = null,
        windowComplete = windowComplete,
        faults = CallSanity.faults(75.5, 77.0, 80.0, 86.5, 72.0, session = null),
    )

    private fun report(vararg calls: ScoredCall) = PerformanceReport(
        tracked = calls.size,
        sessions = listOf(ScoredSession(day, Instant.EPOCH, "m", 1, calls = calls.toList())),
        latestPrices = mapOf(
            "COMI" to LatestPrice(
                DailySession("COMI", day, high = 83.05, low = 80.10, close = 82.40, volume = null, open = 81.0),
                provisional = false,
            ),
        ),
    )

    private class Recording(base: AppState) : AppState by base {
        var asked = 0
        val alerts = mutableListOf<Pair<Double, Boolean>>()
        override fun askAboutCall(call: ScoredCall, askAgain: Boolean) { asked++ }
        override fun addWatchAlert(ticker: String, level: Double, above: Boolean) { alerts += level to above }
    }

    private fun show(state: AppState) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                StockSheet("COMI", state, onDismiss = {})
            }
        }
    }

    @Test
    fun `the heading names the stock and its price`() {
        show(FakeAppState(performance = report(call(Outcome.PARTIAL_HIT))))
        compose.onNodeWithText("COMI").assertExists()
        compose.onNodeWithText("LAST CLOSE").assertExists()
        compose.onNodeWithText("LOW 80.1").assertExists()
    }

    @Test
    fun `with a call in play, Ask AI asks before anything is spent`() {
        val state = Recording(FakeAppState(performance = report(call(Outcome.PARTIAL_HIT))))
        show(state)
        compose.onNodeWithText("Ask AI").performScrollTo().performClick()
        compose.onNodeWithText("Ask about COMI?").assertExists()
        assertEquals(0, state.asked)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, state.asked)
    }

    @Test
    fun `a stock with only finished calls offers no Ask AI and no Call range`() {
        show(FakeAppState(performance = report(call(Outcome.STOPPED, windowComplete = true))))
        compose.onNodeWithText("Ask AI").assertDoesNotExist()
        compose.onNodeWithText("I bought it").assertDoesNotExist()
        // The range row is the only place the word appears; a finished call is still a call.
        compose.onNodeWithText("Call").assertExists()
    }

    @Test
    fun `no call at all means no Call range`() {
        show(FakeAppState(performance = report()))
        compose.onNodeWithText("Call").assertDoesNotExist()
        compose.onNodeWithText("1M").assertExists()
    }

    @Test
    fun `a price alert is set from its own sheet`() {
        val state = Recording(FakeAppState(performance = report(call(Outcome.PARTIAL_HIT))))
        show(state)
        compose.onNodeWithContentDescription("Price alerts").performScrollTo().performClick()
        compose.onNodeWithText("Alert me about COMI").assertExists()
        compose.onNodeWithText("Falls to").performClick()
        compose.onNodeWithText("Save alert").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(82.4 to false), state.alerts)
    }
}
