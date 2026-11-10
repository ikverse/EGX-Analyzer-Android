package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.SavedAnalysis
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
 * The 5.0 Settings page, on the cover screen: a hub of tiles, each opening its part as a page of
 * its own, with a way back. Every action is recorded, never carried out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsPageTest {

    @get:Rule
    val compose = createComposeRule()

    private class Recording(base: AppState) : AppState by base {
        val themes = mutableListOf<ThemeMode>()
        val windows = mutableListOf<Int>()
        var deletedAll = 0
        override fun updateThemeMode(value: ThemeMode) { themes += value }
        override fun updateDefaultTradeWindow(sessions: Int) { windows += sessions }
        override fun deleteAllResults() { deletedAll++ }
    }

    private fun saved() = SavedAnalysis(
        id = 1,
        provider = CloudProvider.QWEN,
        model = "qwen3.7-plus",
        result = AnalysisResult(
            requestId = "run-1",
            recommendations = emptyList(),
            inquiryReplyCount = 0,
            recommendationTargetDate = LocalDate.parse("2026-11-09"),
            completedAt = Instant.parse("2026-11-09T07:12:00Z"),
            consolidated = emptyList(),
        ),
    )

    private fun show(state: AppState) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) { SettingsScreen(state) }
        }
    }

    @Test
    fun `a tile opens its part and back returns to the hub`() {
        show(FakeAppState())
        compose.onNodeWithText("Telegram not connected").assertExists()
        compose.onNodeWithText("Notifications").performScrollTo().performClick()
        compose.onNodeWithText("Your trades").assertExists()
        compose.onNodeWithText("Telegram not connected").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.onNodeWithText("Your trades").assertDoesNotExist()
        compose.onNodeWithText("Telegram not connected").assertExists()
    }

    @Test
    fun `the open part survives the page being rebuilt, as a fold rebuilds it`() {
        val state = FakeAppState()
        var generation by mutableIntStateOf(0)
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                key(generation) { SettingsScreen(state) }
            }
        }
        compose.onNodeWithText("General").performScrollTo().performClick()
        compose.onNodeWithText("Appearance").assertExists()
        generation++
        compose.waitForIdle()
        compose.onNodeWithText("Appearance").assertExists()
        compose.onNodeWithText("Telegram not connected").assertDoesNotExist()
    }

    @Test
    fun `delete all asks first, and Cancel deletes nothing`() {
        val state = Recording(FakeAppState(savedResults = listOf(saved())))
        show(state)
        compose.onNodeWithText("Data & backup").performScrollTo().performClick()
        compose.onNodeWithText("Delete all analyses").performScrollTo().performClick()
        compose.onNodeWithText("Delete all saved analyses?").assertExists()
        assertEquals(0, state.deletedAll)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete all saved analyses?").assertDoesNotExist()
        assertEquals(0, state.deletedAll)
    }

    @Test
    fun `the theme control and the stepper change their settings`() {
        val state = Recording(FakeAppState(appPreferences = AppPreferences()))
        show(state)
        compose.onNodeWithText("General").performScrollTo().performClick()
        compose.onNodeWithText("Dark").performClick()
        assertEquals(listOf(ThemeMode.DARK), state.themes)
        val window = AppPreferences().defaultTradeWindowSessions
        compose.onNodeWithContentDescription("More").performScrollTo().performClick()
        assertEquals(listOf(window + 1), state.windows)
    }
}
