package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * The switch that stops this phone talking to the sync channel.
 *
 * What it must hold on screen is that the switch and Sync now agree: a Sync now that stayed
 * pressable with sync off would be a button that answers "Telegram sync is off" to the one person
 * who has just been told it is a way to sync.
 */
@RunWith(RobolectricTestRunner::class)
class TelegramSyncToggleTest {

    @get:Rule
    val compose = createComposeRule()

    private class Switchable(
        base: AppState = FakeAppState(
            telegramAuthState = TelegramAuthState(step = TelegramAuthStep.READY),
        ),
    ) : AppState by base {
        override var telegramSyncEnabled by mutableStateOf(false)
        override fun updateTelegramSync(enabled: Boolean) {
            telegramSyncEnabled = enabled
        }
    }

    @Test
    fun `sync now is held back while the switch is off and offered once it is on`() {
        val state = Switchable()
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) { SettingsScreen(state) }
        }

        // Since 5.0 the part is its own page, opened from its tile on the hub.
        compose.onNodeWithText("Backup and sync").performScrollTo().performClick()

        compose.onNodeWithText("Sync with Telegram").performScrollTo().assertIsOff()
        compose.onNodeWithText("Sync now").performScrollTo().assertIsNotEnabled()

        compose.onNodeWithText("Sync with Telegram").performClick()

        compose.onNodeWithText("Sync with Telegram").assertIsOn()
        compose.onNodeWithText("Sync now").assertIsEnabled()
    }
}
