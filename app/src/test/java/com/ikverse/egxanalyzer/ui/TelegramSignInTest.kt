package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ikverse.egxanalyzer.model.TelegramAuthState
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 5.0 Telegram sign-in: four steps as a bar, the one in hand on its own tile, and QR sign-in
 * recorded rather than sent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class TelegramSignInTest {

    @get:Rule
    val compose = createComposeRule()

    private class Recording(base: AppState) : AppState by base {
        var qr = 0
        override suspend fun startTelegramQrSignIn() { qr++ }
    }

    @Test
    fun `the phone step is step two, behind the application and ahead of the code`() {
        val state = Recording(FakeAppState(telegramAuthState = TelegramAuthState(step = TelegramAuthStep.PHONE_NUMBER)))
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) { Column { TelegramSignIn(state) } }
        }
        compose.onNodeWithContentDescription("Step 2 of 4").assertExists()
        compose.onNodeWithText("Telegram application").assertExists()
        compose.onNodeWithText("Choose the chats to read").assertExists()
        compose.onNodeWithText("Sign in to Telegram").assertExists()
        compose.onNodeWithText("Sign in by QR code").performClick()
        compose.waitForIdle()
        assertEquals(1, state.qr)
    }

    @Test
    fun `step numbers follow the auth state`() {
        assertEquals(1, TelegramAuthStep.API_CONFIGURATION.signInStep())
        assertEquals(2, TelegramAuthStep.OTHER_DEVICE_CONFIRMATION.signInStep())
        assertEquals(3, TelegramAuthStep.TWO_FACTOR_PASSWORD.signInStep())
        assertEquals(SignInSteps + 1, TelegramAuthStep.READY.signInStep())
        assertEquals(null, TelegramAuthStep.ERROR.signInStep())
    }
}
