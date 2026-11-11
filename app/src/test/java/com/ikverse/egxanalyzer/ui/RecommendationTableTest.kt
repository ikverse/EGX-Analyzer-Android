package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.ikverse.egxanalyzer.ui.kit.MenuItem
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import java.time.LocalDate
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * That a wider window never shows less of a row than a narrower one.
 *
 * **This is the property the 2026-09-09 rebuild was for, and it had already been got wrong twice.**
 * The table's width used to be a function of how many columns had been added, and crossing a
 * breakpoint *appended* columns - so the tablet's 682dp showed 49% of a row where the Fold's 614dp
 * showed 64%, the bigger screen truncating harder than the small one. The same mistake is on record
 * against the `TodayCard` tile grid before it.
 *
 * Only `StockWidth` and `StatusWidth` are fixed now and every figure column is weighted, which
 * makes "no width shows fewer columns than a narrower one" a property rather than a number - and a
 * property is a thing a test can hold. The three widths below are the real containers on the three
 * devices this app runs on, taken from the table at the top of `CLAUDE.md`: what is left of the
 * window once the rail, the page padding and the report card's padding are spent.
 */
@RunWith(RobolectricTestRunner::class)
class RecommendationTableTest {

    @get:Rule
    val compose = createComposeRule()

    /** The Fold unfolded, the Huawei tablet, and the emulator, as the table actually meets them. */
    private val containers = listOf(614, 682, 715)

    private val columns =
        listOf("Stock", "Now", "Entry", "Stop", "Target 1", "Target 2", "Stop to target")

    @Composable
    private fun Table(width: Int, point: RecommendationDataPoint = point()) {
        EgxAnalyzerTheme(themeMode = ThemeMode.DARK) {
            Box(Modifier.size(width.dp, 2000.dp)) {
                RecommendationTable(
                    stocks = listOf(stock(point)),
                    channelFor = { it },
                    latestFor = { null },
                    onSelectPoint = { _, _ -> },
                    holdItems = { _, _ ->
                        listOf(MenuItem("I bought this") {}, MenuItem("Fix a wrong number") {})
                    },
                )
            }
        }
    }

    @Test
    fun `every column is on the row at every width this app is drawn at`() {
        // One `setContent` and a width that moves, rather than a test per device: the rule allows
        // the content to be set once, and resizing the window is what the property is about anyway.
        var width by mutableIntStateOf(containers.min())
        compose.setContent { Table(width) }

        containers.forEach { container ->
            width = container
            compose.waitForIdle()
            columns.forEach { column ->
                compose.onNodeWithText(column)
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun `holding a row opens what can be done about the call`() {
        compose.setContent { Table(containers.min()) }

        compose.onNodeWithText("AMOC").performTouchInput { longClick() }
        compose.onNodeWithText("I bought this").assertIsDisplayed()
        compose.onNodeWithText("Fix a wrong number").assertIsDisplayed()
    }

    @Test
    fun `a watched stock says what it waits for instead of drawing a ladder`() {
        compose.setContent { Table(containers.min(), point(watching = true)) }

        compose.onNodeWithText("only above", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(LadderCellTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `an ordinary call draws its ladder`() {
        compose.setContent { Table(containers.min()) }

        compose.onNodeWithTag(LadderCellTag, useUnmergedTree = true).assertExists()
    }

    private fun stock(point: RecommendationDataPoint) = ConsolidatedRecommendation(
        stockCode = "AMOC",
        stockNameEnglish = "Alexandria Mineral Oils",
        stockNameArabic = "أموك",
        mentionCount = 1,
        rank = 1,
        notesSummary = null,
        dataPoints = listOf(point),
    )

    private fun point(watching: Boolean = false) = RecommendationDataPoint(
        date = LocalDate.of(2026, 9, 1),
        effectiveDateBasis = if (watching) "watching" else "explicit",
        visibleSourceDate = null,
        dateEvidence = null,
        timingEvidence = null,
        sourceMessageId = null,
        sourceImageRef = null,
        recommendationEvidence = null,
        recommendationType = "buy",
        buyPrice = null,
        buyPriceLow = 10.2,
        buyPriceHigh = 10.5,
        target1 = 11.0,
        returnTp1Pct = null,
        target2 = 12.0,
        returnTp2Pct = null,
        stopLoss = 9.8,
        support = 9.5,
        resistance = 12.5,
        riskPct = null,
        notesArabic = null,
    )
}
