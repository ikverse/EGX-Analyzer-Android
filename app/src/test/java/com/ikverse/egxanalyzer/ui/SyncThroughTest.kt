package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ikverse.egxanalyzer.model.SyncMode
import com.ikverse.egxanalyzer.model.TelegramAuthState
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The choice of what carries this device's sync, which replaced the Telegram switch in 5.8.
 *
 * What it must hold on screen is that the choice and Sync now agree: a Sync now that stayed
 * pressable with sync off, or with Drive chosen and not connected, is a button that answers with a
 * refusal to the one person who has just been told it is a way to sync.
 */
@RunWith(RobolectricTestRunner::class)
class SyncThroughTest {

    @get:Rule
    val compose = createComposeRule()

    private class Choosable(
        drive: String?,
        base: AppState = FakeAppState(
            telegramAuthState = TelegramAuthState(step = TelegramAuthStep.READY),
            driveAccount = drive,
        ),
    ) : AppState by base {
        override var syncMode by mutableStateOf(SyncMode.OFF)
        override fun updateSyncMode(mode: SyncMode) {
            syncMode = mode
        }
    }

    private fun open(state: AppState) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) { SettingsScreen(state) }
        }
        // Since 5.0 the part is its own page, opened from its tile on the hub.
        compose.onNodeWithText("Backup and sync").performScrollTo().performClick()
    }

    @Test
    fun `sync now is held back while off and offered once a carrier is chosen`() {
        open(Choosable(drive = "owner@example.com"))
        compose.onNodeWithText("Sync now").performScrollTo().assertIsNotEnabled()

        compose.onNodeWithText("Telegram").performScrollTo().performClick()
        compose.onNodeWithText("Sync now").assertIsEnabled()

        compose.onNodeWithText("Google Drive").performClick()
        compose.onNodeWithText("Sync now").assertIsEnabled()
    }

    @Test
    fun `drive chosen without a connected account says what is missing`() {
        open(Choosable(drive = null))
        compose.onNodeWithText("Google Drive").performScrollTo().performClick()
        compose.onNodeWithText("Sync now").assertIsNotEnabled()
        compose.onNodeWithText("Connect Google Drive under Backup to sync.").assertExists()
    }
}
