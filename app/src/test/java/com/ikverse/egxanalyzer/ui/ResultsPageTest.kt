package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
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
        compose.onNodeWithText("Mon 9 Nov").assertExists()
        compose.onNodeWithText("1 of 2").assertExists()
        compose.onNodeWithText("reached target").assertExists()
        compose.onNodeWithText("1 reached").assertExists()
        compose.onNodeWithText("1 stopped").assertExists()
        compose.onNodeWithText("2 stocks · read once at 09:12").assertExists()
        compose.onNodeWithContentDescription("1 hit, 1 stopped, 0 expired, 0 open").assertExists()
    }

    @Test
    fun `pressing a run opens its report with its figures`() {
        show(FakeAppState(savedResults = listOf(run), performance = report))
        compose.onNodeWithText("Mon 9 Nov").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("reached target · 2 stocks").assertExists()
        // Every call says its levels as numbers, under their names.
        assertEquals(2, compose.onAllNodesWithText("Stop").fetchSemanticsNodes().size)
        assertEquals(2, compose.onAllNodesWithText("Target 2").fetchSemanticsNodes().size)
        compose.onNodeWithText("About this run").assertExists()
    }

    @Test
    fun `delete asks first and then removes the run`() {
        val state = Recording(FakeAppState(savedResults = listOf(run), performance = report))
        show(state)
        compose.onNodeWithText("Mon 9 Nov").performClick()
        compose.onNodeWithText("About this run").performScrollTo().performClick()
        compose.onNodeWithText("Delete this run").performScrollTo().performClick()
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
    @Config(qualifiers = "w750dp-h891dp")
    fun `unfolded, a pressed run fills the page as one table`() {
        show(FakeAppState(savedResults = listOf(run), performance = report), FoldPosture.Unfolded)
        compose.onNodeWithText("Mon 9 Nov").performClick()
        compose.waitForIdle()
        // The list of sessions is gone: only the report's own bar names the day.
        assertEquals(1, compose.onAllNodesWithText("Mon 9 Nov").fetchSemanticsNodes().size)
        compose.onNodeWithText("2 stocks · read once at 09:12").assertDoesNotExist()
        // One table, its column names said once for both stocks, and no ladder labels.
        assertEquals(1, compose.onAllNodesWithText("TARGET 2").fetchSemanticsNodes().size)
        compose.onNodeWithText("Target 2").assertDoesNotExist()
        compose.onNodeWithText("COMI").assertExists()
        compose.onNodeWithText("ABUK").assertExists()
    }

    /** The same session read again two hours later, when a third stock had been posted. */
    private val reread = run.copy(
        id = 32,
        result = run.result.copy(
            requestId = "run-32",
            completedAt = Instant.parse("2026-11-09T09:12:00Z"),
            consolidated = listOf(stock("COMI", 0), stock("ABUK", 1), stock("ORAS", 2)),
        ),
    )

    @Test
    fun `a session read twice is one card that says so, and its readings are a press away`() {
        val state = FakeAppState(savedResults = listOf(run, reread), performance = mixed)
        show(state)
        // One card, drawn from the newest reading.
        assertEquals(1, compose.onAllNodesWithText("Mon 9 Nov").fetchSemanticsNodes().size)
        compose.onNodeWithText("3 stocks · read twice, last at 11:12").assertExists()
        compose.onNodeWithText("Mon 9 Nov").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Read at 11:12 · 2 of 2").performClick()
        compose.runOnIdle { assertEquals(true, state.pages.resultsReadingsOpen.value) }
        compose.onNodeWithText("Mon 9 Nov was read 2 times").assertExists()
        compose.onNodeWithText("New since 09:12: ORAS").assertExists()
        compose.onNodeWithText("09:12").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Read at 09:12 · 1 of 2").assertExists()
    }

    @Test
    fun `the stocks of a report can be put back in the report's own order`() {
        openRun()
        compose.onNodeWithText("2 stocks, closest to target first").assertExists()
        compose.onNodeWithText("Sort").performClick()
        compose.onNodeWithText("Report order").performClick()
        compose.onNodeWithText("2 stocks, in the report's order").assertExists()
    }

    /** COMI still running and ABUK stopped, so the chips have two groups to tell apart. */
    private val mixed = PerformanceReport(
        sessions = listOf(
            ScoredSession(
                targetDate = day, lastRunAt = Instant.EPOCH, model = "m", runCount = 1,
                calls = listOf(scored("COMI", Outcome.OPEN), scored("ABUK", Outcome.STOPPED)),
            ),
        ),
    )

    private fun openRun(posture: FoldPosture = FoldPosture.Cover, state: AppState? = null) {
        val shown = state ?: FakeAppState(savedResults = listOf(run), performance = mixed)
        show(shown, posture)
        compose.onNodeWithText("Mon 9 Nov").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `a status chip narrows the report and All puts it back`() {
        openRun()
        compose.onNodeWithText("Stopped 1").performClick()
        compose.onNodeWithText("ABUK").assertExists()
        compose.onNodeWithText("COMI").assertDoesNotExist()
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("COMI").assertExists()
        compose.onNodeWithText("ABUK").assertExists()
    }

    @Test
    fun `holding a call card opens its menu`() {
        openRun()
        compose.onNodeWithText("Running").performTouchInput { longClick() }
        compose.onNodeWithText("Fix a wrong number").assertIsDisplayed()
        compose.onNodeWithText("Show only COMI").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w750dp-h891dp")
    fun `in book posture the report splits on the crease, with no list beside it`() {
        openRun(FoldPosture.Book(0.5f, 0.5f))
        assertEquals(1, compose.onAllNodesWithText("Mon 9 Nov").fetchSemanticsNodes().size)
        compose.onNodeWithText("About this run").assertExists()
        compose.onNodeWithText("Stopped 1").assertExists()
        // The calls are cards on the far side of the crease, not a table across it.
        compose.onNodeWithText("TARGET 2").assertDoesNotExist()
        assertEquals(2, compose.onAllNodesWithText("Target 2").fetchSemanticsNodes().size)
    }

    @Test
    fun `on tabletop a call key chooses the call shown above the crease`() {
        openRun(FoldPosture.Tabletop(0.5f, 0.52f))
        assertEquals(0, compose.onAllNodesWithText("ABUK").fetchSemanticsNodes().size)
        compose.onNodeWithText("● ABUK").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithText("ABUK").fetchSemanticsNodes().size)
    }

    @Test
    fun `the technical lines stay shut until asked for`() {
        openRun()
        compose.onNodeWithText("About this run").performScrollTo().performClick()
        compose.onNodeWithText("qwen3.7-plus", substring = true).assertDoesNotExist()
        compose.onNodeWithText("For the curious").performScrollTo().performClick()
        compose.onNodeWithText("qwen3.7-plus", substring = true).assertExists()
    }

    @Test
    fun `back closes an open report`() {
        val state = FakeAppState(
            savedResults = listOf(run),
            performance = mixed,
            destination = com.ikverse.egxanalyzer.ui.AppDestination.RESULTS,
        )
        openRun(state = state)
        compose.onNodeWithText("Stopped 1").assertExists()
        compose.runOnIdle { assertEquals(true, state.goBack()) }
        compose.onNodeWithText("Stopped 1").assertDoesNotExist()
        compose.onNodeWithText("0 of 2").assertExists()
    }

    @Test
    fun `a report deleted while open is closed, so back is not swallowed`() {
        var results by androidx.compose.runtime.mutableStateOf(listOf(run))
        val base = FakeAppState(performance = mixed, destination = com.ikverse.egxanalyzer.ui.AppDestination.RESULTS)
        val state = object : AppState by base {
            override val savedResults get() = results
        }
        show(state)
        compose.runOnIdle { state.pages.openReport(run.id) }
        compose.onNodeWithText("Stopped 1").assertExists()
        // The record without it, and one more run so the list is still drawn.
        results = listOf(run.copy(id = run.id + 1))
        compose.waitForIdle()
        assertEquals(null, state.pages.openResultId.value)
    }
}
