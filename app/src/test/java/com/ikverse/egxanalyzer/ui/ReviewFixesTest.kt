package com.ikverse.egxanalyzer.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Faults found in the review before 5.0.0 shipped, each held in the shape it was found in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ReviewFixesTest {

    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 2)

    private fun held() = PositionView(
        position = Position(ticker = "FWRY", recommendationDate = day, entryPrice = 9.10, entryDate = day, target1 = 9.80, target2 = 10.50),
        status = PositionStatus.OPEN,
        marketStatus = PositionStatus.OPEN,
        open = true,
        currentPrice = 9.80,
        exitPrice = 9.80,
        realized = false,
        returnPct = 7.7,
        sessionsElapsed = 5,
        sessionsRemaining = 10,
        deadlineDate = day.plusDays(21),
        settledOn = null,
        ranOutOfTime = false,
        overdueDays = 0,
    )

    @Test
    fun `the sale's split field stays on screen while it is emptied and retyped`() {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                SellDialog(held = held(), suggestedExit = 9.80, onDismiss = {}, onConfirm = {})
            }
        }
        val field = compose.onNode(hasSetTextAction() and hasText("50"))
        field.performScrollTo().performTextClearance()
        // Before the fix the whole tile left the composition here, field and all.
        compose.onNodeWithText("Sold at the target 1 price (%)").assertExists()
        compose.onNodeWithText("Between 1 and 100.").assertExists()
        compose.onNode(hasSetTextAction() and hasText("")).assertExists()
        compose.onNodeWithText("In two parts").assertExists()
        compose.onNode(hasSetTextAction() and hasText("")).performTextInput("70")
        compose.onNodeWithText("70%").assertExists()
    }
}
