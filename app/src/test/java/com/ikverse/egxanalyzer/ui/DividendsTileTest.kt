package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ikverse.egxanalyzer.model.Dividend
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Home's dividends tile: the reader's own stocks lead, a row opens the stock, and five show at first. */
@RunWith(RobolectricTestRunner::class)
class DividendsTileTest {

    @get:Rule
    val compose = createComposeRule()

    private fun dividend(code: String, day: Int) = Dividend(
        code = code, amount = 0.5, lastCumDay = LocalDate.of(2026, 11, day),
        paymentDay = LocalDate.of(2026, 11, day + 3), lastCumEstimated = true,
    )

    @Test
    fun yoursLeadAndARowOpensTheStock() {
        val upcoming = listOf(
            dividend("AAAA", 16), dividend("BBBB", 17), dividend("CCCC", 18),
            dividend("DDDD", 19), dividend("EEEE", 20), dividend("FFFF", 22),
        )
        var opened: String? = null
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalOpenStock provides { opened = it }) {
                    DividendsTile(upcoming, mine = setOf("FFFF"))
                }
            }
        }
        // The held stock is shown though it is the latest, and one of the others waits behind Show all.
        compose.onNodeWithText("FFFF").assertExists()
        compose.onNodeWithText("yours").assertExists()
        compose.onAllNodesWithText("EEEE").assertCountEquals(0)
        compose.onNodeWithText("Show all 6").performClick()
        compose.onNodeWithText("EEEE").assertExists()
        compose.onNodeWithText("buy by ~16 Nov · paid 19 Nov").assertExists()
        compose.onNodeWithText("AAAA").performClick()
        assertEquals("AAAA", opened)
    }
}
