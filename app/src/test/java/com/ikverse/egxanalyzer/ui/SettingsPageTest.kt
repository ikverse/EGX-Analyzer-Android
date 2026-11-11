package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
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
        compose.onNodeWithText("Telegram is signed out").assertExists()
        compose.onNodeWithText("Notifications").performScrollTo().performClick()
        compose.onNodeWithText("Your trades").assertExists()
        compose.onNodeWithText("Telegram is signed out").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.onNodeWithText("Your trades").assertDoesNotExist()
        compose.onNodeWithText("Telegram is signed out").assertExists()
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
        compose.onNodeWithText("Prices and trades").performScrollTo().performClick()
        compose.onNodeWithText("Appearance").assertExists()
        generation++
        compose.waitForIdle()
        compose.onNodeWithText("Appearance").assertExists()
        compose.onNodeWithText("Telegram is signed out").assertDoesNotExist()
    }

    @Test
    fun `delete all asks first, and Cancel deletes nothing`() {
        val state = Recording(FakeAppState(savedResults = listOf(saved())))
        show(state)
        compose.onNodeWithText("Backup and sync").performScrollTo().performClick()
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
        compose.onNodeWithText("Prices and trades").performScrollTo().performClick()
        compose.onNodeWithText("Dark").performClick()
        assertEquals(listOf(ThemeMode.DARK), state.themes)
        val window = AppPreferences().defaultTradeWindowSessions
        compose.onNodeWithContentDescription("More").performScrollTo().performClick()
        assertEquals(listOf(window + 1), state.windows)
    }

    @Test
    fun `advanced starts shut, opens on a press, and stays open through a rebuild`() {
        val state = FakeAppState()
        state.pages.settingsSection.value = SettingsSection.ANALYSIS
        var generation by mutableIntStateOf(0)
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                key(generation) { SettingsScreen(state) }
            }
        }
        compose.onNodeWithText("HTTPS API endpoint").assertDoesNotExist()
        compose.onNodeWithText("Advanced").performScrollTo().performClick()
        compose.onNodeWithText("HTTPS API endpoint").assertExists()
        generation++
        compose.waitForIdle()
        compose.onNodeWithText("HTTPS API endpoint").assertExists()
    }

    @Test
    fun `the report-ready notification has a switch of its own`() {
        val state = FakeAppState()
        state.pages.settingsSection.value = SettingsSection.NOTIFICATIONS
        show(state)
        compose.onNodeWithText("A report is ready").performScrollTo().assertExists()
        compose.onNodeWithText("Tap ? on any line to see an example.").assertExists()
    }

    @Test
    fun `the hub says everything is working, in the dark theme too`() {
        val state = FakeAppState(
            telegramAuthState = com.ikverse.egxanalyzer.model.TelegramAuthState(step = com.ikverse.egxanalyzer.model.TelegramAuthStep.READY),
            cloudConfiguration = com.ikverse.egxanalyzer.model.CloudConfiguration(hasCredential = true),
            credentialVerified = true,
            lastPriceRefreshAt = System.currentTimeMillis(),
        )
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) { SettingsScreen(state) }
        }
        compose.onNodeWithText("Everything is working").assertExists()
        compose.onNodeWithText("Telegram is connected").assertExists()
    }

    private class Toggles(base: AppState) : AppState by base {
        val changed = mutableListOf<String>()
        override fun updateTradeAlerts(enabled: Boolean) { changed += "trade" }
        override fun updateSessionDigest(enabled: Boolean) { changed += "digest" }
    }

    @Test
    fun `on tabletop a key chooses what is explained and switches nothing`() {
        val state = Toggles(FakeAppState())
        state.pages.settingsSection.value = SettingsSection.NOTIFICATIONS
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture provides com.ikverse.egxanalyzer.model.FoldPosture.Tabletop(0.5f, 0.51f),
                ) { SettingsScreen(state) }
            }
        }
        // The first kind is explained above the crease and has its key below.
        assertEquals(2, compose.onAllNodesWithText(NotificationKind.TRADE.label).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText(NotificationKind.DIGEST.label).fetchSemanticsNodes().size)
        compose.onAllNodesWithText(NotificationKind.DIGEST.label)[0].performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(2, compose.onAllNodesWithText(NotificationKind.DIGEST.label).fetchSemanticsNodes().size)
        assertEquals(emptyList<String>(), state.changed)
    }
}
