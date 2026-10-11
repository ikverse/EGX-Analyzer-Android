package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A setting row is one switch with a name, not a name beside a switch.
 *
 * Found by its words and asserted as a toggle, which is only possible when the row carries the
 * toggleable semantics - the shape a screen reader reads as "<name>, switch, off".
 */
@RunWith(RobolectricTestRunner::class)
class SettingToggleTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the words are the switch`() {
        var checked by mutableStateOf(false)
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) {
                SettingToggle("Pure black background", checked, { checked = it })
            }
        }

        compose.onNodeWithText("Pure black background").assertIsOff()
        compose.onNodeWithText("Pure black background").performClick()
        compose.onNodeWithText("Pure black background").assertIsOn()
    }
}
