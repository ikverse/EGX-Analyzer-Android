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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.ikverse.egxanalyzer.ui.kit.MenuItem
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.LatestPrice
import java.time.LocalDate
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Assert.assertEquals
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
 * Only `WhoWidth` is fixed now and every figure column is weighted, which
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

    /** Said above the figures of a tile's first call, since 2026-11-11, and not in a shared header. */
    private val columns =
        listOf("Entry", "Stop", "Target 1", "Target 2", "Since entry", "To target 1")

    @Composable
    private fun Table(
        width: Int,
        vararg points: RecommendationDataPoint = arrayOf(point()),
        close: Double? = null,
    ) {
        EgxAnalyzerTheme(themeMode = ThemeMode.DARK) {
            Box(Modifier.size(width.dp, 2000.dp)) {
                RecommendationTable(
                    stocks = listOf(stock(points.toList())),
                    channelFor = { it },
                    latestFor = { code ->
                        close?.let {
                            LatestPrice(DailySession(code, LocalDate.of(2026, 9, 2), it, it, it, null), provisional = false)
                        }
                    },
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

        compose.onNodeWithText("Nile Signals").performTouchInput { longClick() }
        compose.onNodeWithText("I bought this").assertIsDisplayed()
        compose.onNodeWithText("Fix a wrong number").assertIsDisplayed()
    }

    @Test
    fun `a watched stock says what it waits for instead of a move`() {
        compose.setContent { Table(containers.min(), point(basis = "watching"), close = 10.6) }

        compose.onNodeWithText("buy only above 10.5").assertIsDisplayed()
        compose.onNodeWithText("+2.4%").assertDoesNotExist()
    }

    @Test
    fun `a watched stock shows the levels its post gave`() {
        compose.setContent { Table(containers.min(), point(basis = "watching")) }

        listOf("9.8", "11", "12").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `a watched stock with no entry falls back to its support and resistance`() {
        assertEquals(
            "support 9.5 · resistance 12.5",
            watchingNote(point(basis = "watching").copy(buyPriceLow = null, buyPriceHigh = null)),
        )
    }

    @Test
    fun `two calls on one stock share one heading, and a T+1 call says so`() {
        compose.setContent {
            Table(containers.min(), point(), point(basis = "t_plus_1", channel = "Cairo Desk"))
        }

        assertEquals(1, compose.onAllNodesWithText("AMOC").fetchSemanticsNodes().size)
        compose.onNodeWithText("2 calls · 2 channels", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Nile Signals").assertIsDisplayed()
        compose.onNodeWithText("Cairo Desk").assertIsDisplayed()
        assertEquals(1, compose.onAllNodesWithText("T+1", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `a second call on a stock lines up under the names without repeating them`() {
        compose.setContent { Table(containers.min(), point(), point(channel = "Cairo Desk")) }

        columns.forEach { assertEquals(it, 1, compose.onAllNodesWithText(it).fetchSemanticsNodes().size) }
    }

    @Test
    fun `each level says how far it is from the entry`() {
        compose.setContent { Table(containers.min()) }

        // Against the middle of the 10.2 - 10.5 band: stop 9.8, targets 11 and 12.
        listOf("-5.3%", "+6.3%", "+15.9%").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `a live call says its move since entry and what is left to target 1`() {
        compose.setContent { Table(containers.min(), close = 10.6) }

        compose.onNodeWithText("+2.4%").assertIsDisplayed()
        compose.onNodeWithText("3.8%").assertIsDisplayed()
        compose.onNodeWithText("now").assertIsDisplayed()
    }

    @Test
    fun `a stock the feed has no price for says so`() {
        compose.setContent { Table(containers.min()) }

        compose.onNodeWithText("no price").assertIsDisplayed()
        compose.onNodeWithText("now").assertDoesNotExist()
    }

    @Test
    fun `live calls come before watched ones, each in the report's order`() {
        val watched = point(basis = "watching", channel = "A")
        val first = point(channel = "B")
        val second = point(channel = "C")

        assertEquals(
            listOf("B", "C", "A"),
            liveFirst(listOf(watched, first, second)).map { it.sourceMessageId },
        )
    }

    @Test
    fun `a price at or past target 1 has nothing left to go`() {
        compose.setContent { Table(containers.min(), close = 11.2) }

        compose.onNodeWithText("reached").assertIsDisplayed()
    }

    private fun stock(points: List<RecommendationDataPoint>) = ConsolidatedRecommendation(
        stockCode = "AMOC",
        stockNameEnglish = "Alexandria Mineral Oils",
        stockNameArabic = "أموك",
        mentionCount = 1,
        rank = 1,
        notesSummary = null,
        dataPoints = points,
    )

    private fun point(basis: String = "explicit", channel: String = "Nile Signals") = RecommendationDataPoint(
        date = LocalDate.of(2026, 9, 1),
        effectiveDateBasis = basis,
        visibleSourceDate = null,
        dateEvidence = null,
        timingEvidence = null,
        sourceMessageId = channel,
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
