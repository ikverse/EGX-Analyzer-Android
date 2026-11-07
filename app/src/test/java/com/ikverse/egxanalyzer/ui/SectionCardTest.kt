package com.ikverse.egxanalyzer.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The section card every page draws, after the 2026-11 redesign took its stripe and coloured tile
 * away.
 *
 * The redesign changed only how the card looks, so this pins what it must still do: show its
 * title, show what is inside it, and keep the question mark that explains it - which is the one
 * control on the card, and the one a restyle could most easily lose by drawing over it.
 */
@RunWith(RobolectricTestRunner::class)
class SectionCardTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a section card shows its title, its content, and opens its explanation`() {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                SectionCard(
                    title = "Your record",
                    about = infoNote("How the record is counted", "Every settled trade, once."),
                ) {
                    Text("Card body")
                }
            }
        }

        compose.onNodeWithText("Your record").assertIsDisplayed()
        compose.onNodeWithText("Card body").assertIsDisplayed()

        compose.onNodeWithContentDescription("How the record is counted").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Every settled trade, once.").assertExists()
    }
}
