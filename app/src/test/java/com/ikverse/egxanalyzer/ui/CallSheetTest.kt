package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.ikverse.egxanalyzer.model.CallGroup
import com.ikverse.egxanalyzer.model.CallSanity
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * One call in full, as pressing it in a report opens it. Held to the shape it was rebuilt in on
 * 2026-11-11: the names in full, the price now, the verdict, the days left, and no row of dashes.
 * One `setContent` per test.
 */
@RunWith(RobolectricTestRunner::class)
class CallSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 11)

    private fun point(basis: String? = null, evidence: String? = null, support: Double? = null) = RecommendationDataPoint(
        date = day,
        effectiveDateBasis = basis,
        visibleSourceDate = "11-Nov-2026 10:54",
        dateEvidence = null,
        timingEvidence = null,
        sourceMessageId = "68924997632",
        sourceImageRef = 6,
        recommendationEvidence = evidence,
        recommendationType = "buy",
        buyPrice = 320.0,
        buyPriceLow = null,
        buyPriceHigh = null,
        target1 = 335.0,
        returnTp1Pct = null,
        target2 = 350.0,
        returnTp2Pct = null,
        stopLoss = 310.0,
        support = support,
        resistance = null,
        riskPct = null,
        notesArabic = null,
        parseIndex = 0,
    )

    private fun stock(point: RecommendationDataPoint) = ConsolidatedRecommendation(
        stockCode = "NIPH",
        stockNameEnglish = "El-Nile Co. for Pharmaceuticals",
        stockNameArabic = null,
        mentionCount = 1, rank = 1, notesSummary = null,
        dataPoints = listOf(point),
    )

    private val latest = LatestPrice(
        session = DailySession(ticker = "NIPH", date = day, high = 325.01, low = 318.0, close = 325.01, volume = 1_000.0),
        provisional = false,
    )

    private val running = ScoredCall(
        ticker = "NIPH", companyEnglish = null, companyArabic = null, channel = "Ask Fi", channelId = null,
        openedOn = day, entryLow = 320.0, entryHigh = 320.0, target1 = 335.0, target2 = 350.0, stopLoss = 310.0,
        outcome = Outcome.OPEN, settledOn = null, peakHigh = 325.01, troughLow = 318.0, returnPct = null,
        sessionsElapsed = 1, repeatOf = null,
        faults = CallSanity.faults(320.0, 320.0, 335.0, 350.0, 310.0, session = null),
        windowSessions = 10,
    )

    private fun show(
        point: RecommendationDataPoint,
        status: Pair<String, CallGroup>? = "Running" to CallGroup.RUNNING,
        scored: ScoredCall? = running,
    ) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OccurrenceContent(
                        stock = stock(point),
                        point = point,
                        imagePath = null,
                        peak = 325.01,
                        channel = "Ask Fi",
                        session = day,
                        latest = latest,
                        status = status,
                        scored = scored,
                        editor = null,
                        onEdit = {},
                        onOpenImage = {},
                    )
                }
            }
        }
    }

    @Test
    fun `the name is whole, with nothing sharing its line but the menu`() {
        show(point())
        compose.onNodeWithText("El-Nile Co. for Pharmaceuticals").assertExists()
        // The header used to carry this block, which is what cut the names off.
        compose.onAllNodesWithText("PEAK").assertCountEquals(0)
    }

    @Test
    fun `it leads with the price now and how far that is from the entry`() {
        show(point())
        compose.onNodeWithText("WHERE IT STANDS").assertExists()
        compose.onNodeWithText("325.01").assertExists()
        compose.onNodeWithText("+1.6% above the entry of 320.00").assertExists()
        compose.onNodeWithText("The highest it has been since the call.").assertExists()
    }

    @Test
    fun `it says the call's verdict and how long it has left`() {
        show(point())
        // On its tag, and again as the step the timeline has reached.
        compose.onAllNodesWithText("Running").assertCountEquals(2)
        compose.onNodeWithText("9 of 10 trading days left").assertExists()
        compose.onNodeWithText("from ⁨Ask Fi⁩").assertExists()
    }

    @Test
    fun `the levels carry their distances and there is no row of dashes`() {
        show(point())
        compose.onNodeWithText("Stop").assertExists()
        compose.onNodeWithText("-3.1%").assertExists()
        compose.onNodeWithText("+9.4%").assertExists()
        compose.onNodeWithText("Risks 1 to make 1.5").assertExists()
        // Support and resistance are drawn only where the call gave one.
        compose.onAllNodesWithText("Support", substring = true, ignoreCase = true).assertCountEquals(0)
        compose.onAllNodesWithText("Resistance", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun `a support the call gave is one line under the levels`() {
        show(point(support = 305.0))
        compose.onNodeWithText("Support 305.00").assertExists()
    }

    @Test
    fun `a watching call leads with its condition and what the app can say about it`() {
        show(
            point(basis = "watching", evidence = "Buy only on a break of the price shown"),
            status = "Watching" to CallGroup.WATCHING,
            scored = null,
        )
        compose.onNodeWithText("THE CALL'S OWN CONDITION").assertExists()
        // Once, as the condition, and not again as a quote under the screenshot.
        compose.onAllNodesWithText("Buy only on a break of the price shown", substring = true).assertCountEquals(1)
        compose.onNodeWithText("The last close is above the entry", substring = true).assertExists()
        // The timing pill already says Watching; a second tag saying it would be the same word twice.
        compose.onAllNodesWithText("Watching").assertCountEquals(1)
    }

    @Test
    fun `the source says when it was printed and which session it was for, in the app's own dates`() {
        show(point())
        compose.onNodeWithText("Printed 11 Nov, 10:54").assertExists()
        compose.onNodeWithText("For the session of 11 Nov").assertExists()
        // Behind the closed Source details row until it is opened.
        compose.onAllNodesWithText("message 68924997632", substring = true).assertCountEquals(0)
    }
}
