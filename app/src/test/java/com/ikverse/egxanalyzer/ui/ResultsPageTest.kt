package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.CallSanity
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 5.0 Results page: a run as a tile, the report it opens, and the dialog that deletes it.
 * Every action is recorded rather than performed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ResultsPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.parse("2026-11-09")

    private fun point(index: Int) = RecommendationDataPoint(
        date = day,
        effectiveDateBasis = null,
        visibleSourceDate = null,
        dateEvidence = null,
        timingEvidence = null,
        sourceMessageId = "55$index",
        sourceImageRef = null,
        recommendationEvidence = null,
        recommendationType = "buy",
        buyPrice = null,
        buyPriceLow = 10.0,
        buyPriceHigh = 10.2,
        target1 = 11.0,
        returnTp1Pct = null,
        target2 = 12.0,
        returnTp2Pct = null,
        stopLoss = 9.5,
        support = null,
        resistance = null,
        riskPct = null,
        notesArabic = null,
        parseIndex = 0,
    )

    private fun stock(code: String, index: Int) = ConsolidatedRecommendation(
        stockCode = code, stockNameEnglish = null, stockNameArabic = null,
        mentionCount = 1, rank = index + 1, notesSummary = null,
        dataPoints = listOf(point(index)),
    )

    private val run = SavedAnalysis(
        id = 31,
        provider = CloudProvider.QWEN,
        model = "qwen3.7-plus",
        result = AnalysisResult(
            requestId = "run-31",
            recommendations = emptyList(),
            inquiryReplyCount = 0,
            recommendationTargetDate = day,
            // 09:12 in Cairo, which is two hours ahead of UTC in November.
            completedAt = Instant.parse("2026-11-09T07:12:00Z"),
            consolidated = listOf(stock("COMI", 0), stock("ABUK", 1)),
        ),
    )

    private fun scored(ticker: String, outcome: Outcome) = ScoredCall(
        ticker = ticker, companyEnglish = null, companyArabic = null, channel = "Tadawul", channelId = null,
        openedOn = day, entryLow = 10.0, entryHigh = 10.2, target1 = 11.0, target2 = 12.0, stopLoss = 9.5,
        outcome = outcome, settledOn = day, peakHigh = 11.5, troughLow = 9.4, returnPct = 5.0,
        sessionsElapsed = 1, repeatOf = null,
        faults = CallSanity.faults(10.0, 10.2, 11.0, 12.0, 9.5, session = null),
    )

    private val report = PerformanceReport(
        sessions = listOf(
            ScoredSession(
                targetDate = day, lastRunAt = Instant.EPOCH, model = "m", runCount = 1,
                calls = listOf(scored("COMI", Outcome.FULL_HIT), scored("ABUK", Outcome.STOPPED)),
            ),
        ),
    )

    private class Recording(base: AppState) : AppState by base {
        val deleted = mutableListOf<Long>()
        override fun deleteResult(result: SavedAnalysis) { deleted += result.id }
    }

    private fun show(state: AppState, posture: FoldPosture = FoldPosture.Cover) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalFoldPosture provides posture) {
                    ResultsScreen(state)
                }
            }
        }
    }

    @Test
    fun `a shut run is named for its day and says how its calls went`() {
        show(FakeAppState(savedResults = listOf(run), performance = report))
        compose.onNodeWithText("Monday morning").assertExists()
        compose.onNodeWithText("#31 · 9 Nov 09:12").assertExists()
        compose.onNodeWithText("2 calls · 1 hit · 1 stopped").assertExists()
        compose.onNodeWithContentDescription("1 hit, 1 stopped, 0 expired, 0 open").assertExists()
    }

    @Test
    fun `pressing a run opens its report with its figures`() {
        show(FakeAppState(savedResults = listOf(run), performance = report))
        compose.onNodeWithText("Monday morning").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("STOCKS").assertExists()
        compose.onNodeWithText("Source trace and diagnostics").assertExists()
    }

    @Test
    fun `delete asks first and then removes the run`() {
        val state = Recording(FakeAppState(savedResults = listOf(run), performance = report))
        show(state)
        compose.onNodeWithContentDescription("Report options").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete this analysis?").assertExists()
        assertEquals(emptyList<Long>(), state.deleted)
        compose.onNodeWithText("Delete everywhere").performClick()
        compose.waitForIdle()
        assertEquals(listOf(31L), state.deleted)
    }

    @Test
    fun `an empty record says how to fill it`() {
        show(FakeAppState())
        compose.onNodeWithText("No saved results yet").assertExists()
        compose.onNodeWithText("Go to Analyze").assertExists()
    }

    @Test
    fun `while the record loads nothing claims it is empty`() {
        show(FakeAppState(initialDataLoaded = false))
        compose.onNodeWithText("No saved results yet").assertDoesNotExist()
    }

    @Test
    fun `unfolded, a pressed run opens beside the list`() {
        show(FakeAppState(savedResults = listOf(run), performance = report), FoldPosture.Unfolded)
        compose.onNodeWithText("Pick a run").assertExists()
        compose.onNodeWithText("Monday morning").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pick a run").assertDoesNotExist()
        compose.onNodeWithText("STOCKS").assertExists()
    }
}
