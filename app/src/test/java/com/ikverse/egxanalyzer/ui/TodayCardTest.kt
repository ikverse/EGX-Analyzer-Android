package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ikverse.egxanalyzer.model.DayEvent
import com.ikverse.egxanalyzer.model.DayEventKind
import com.ikverse.egxanalyzer.model.SessionDigest
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The session card in its 5.1 shape (2026-11-11): one-line count labels, one row per event with its
 * outcome in plain words, and a press on a row opening that call or that trade.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class TodayCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val session = LocalDate.of(2026, 11, 11)
    private val called = LocalDate.of(2026, 11, 3)

    private val digest = SessionDigest(
        date = session,
        events = listOf(
            DayEvent(DayEventKind.CALL_TARGET2, "SUGR", "CFI Egypt", "SUGR@2026-11-03", called),
            DayEvent(DayEventKind.CALL_STOPPED, "EGAS", "Nile Signals", "EGAS@2026-11-03", called),
            DayEvent(DayEventKind.CALL_IN_RANGE, "ACGC", "Nile Signals", "ACGC@2026-11-03", called, price = 10.2),
            DayEvent(DayEventKind.TRADE_TARGET1, "COMI", "CFI Egypt", "COMI@2026-11-03", called, returnPct = 6.8),
        ),
        newCalls = 11,
        newCallSources = 2,
    )

    private class Opening(base: AppState) : AppState by base {
        val calls = mutableListOf<String>()
        val trades = mutableListOf<String>()
        override fun openCall(id: String, returnTo: NavStop?) { calls += id }
        override fun openPosition(id: String, returnTo: NavStop?) { trades += id }
    }

    private fun show(state: AppState) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) {
                Column { TodayCard(state) }
            }
        }
    }

    @Test
    fun `counts and events read in plain words, and a row opens what it names`() {
        val state = Opening(FakeAppState(sessionDigest = digest))
        show(state)

        listOf("Target 1", "Target 2", "Stopped", "In range").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("Target 2 reached").assertIsDisplayed()
        compose.onNodeWithText("Stopped out").assertIsDisplayed()
        compose.onNodeWithText("Came into range").assertIsDisplayed()
        compose.onNodeWithText("Target 1 reached").assertIsDisplayed()
        compose.onNodeWithText("Your trade", substring = true).assertIsDisplayed()
        compose.onNodeWithText("11 new calls from 2 sources").assertIsDisplayed()

        compose.onNodeWithText("SUGR").performClick()
        compose.onNodeWithText("COMI").performClick()
        assertEquals(listOf("SUGR@2026-11-03"), state.calls)
        assertEquals(listOf("COMI@2026-11-03"), state.trades)
    }

    @Test
    fun `a session where nothing moved says so`() {
        show(FakeAppState(sessionDigest = SessionDigest(date = session, events = emptyList())))
        compose.onNodeWithText("Nothing moved on this session.").assertIsDisplayed()
    }
}
