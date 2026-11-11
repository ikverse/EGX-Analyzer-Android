package com.ikverse.egxanalyzer.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class NotificationSamplesTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every notification has a label, a line and an example`() {
        assertEquals(7, NotificationKind.entries.size)
        NotificationKind.entries.forEach { kind ->
            assertTrue(kind.name, kind.label.isNotBlank() && kind.detail.isNotBlank())
            assertTrue(kind.name, kind.sample.title.isNotBlank() && kind.sample.text.isNotBlank())
        }
    }

    @Test
    fun `the question mark shows what the notification looks like`() {
        val state = FakeAppState()
        state.pages.settingsSection.value = SettingsSection.NOTIFICATIONS
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) { SettingsScreen(state) }
        }
        compose.onNodeWithContentDescription(NotificationKind.DIGEST.label).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("What it looks like").assertExists()
        compose.onNodeWithText(NotificationKind.DIGEST.sample.title).assertExists()
    }
}
