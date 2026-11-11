package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import com.ikverse.egxanalyzer.model.AnalysisProgress
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.model.TelegramAuthState
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The 5.0 Analyze page. Every state here is the preview state with a few fields set, and every
 * action is recorded rather than performed - none of these tests can start a real run.
 */
@RunWith(RobolectricTestRunner::class)
class AnalyzePageTest {

    @get:Rule
    val compose = createComposeRule()

    private val tadawul = ChannelSelection(id = 1, name = "Tadawul Calls", selected = true)
    private val nile = ChannelSelection(id = 2, name = "Nile Signals", selected = true)

    private fun trace(id: Long, name: String, n: Int) = SourceTrace(
        sourceId = "$name-$n", channelId = id, channelName = name, messageId = n.toLong(),
        timestamp = Instant.EPOCH, contentType = AnalysisContentType.TEXT, preview = "message $n",
    )

    private val loaded = listOf(trace(1, "Tadawul Calls", 1), trace(1, "Tadawul Calls", 2), trace(2, "Nile Signals", 3))

    private fun ready(
        status: AnalysisStatus = AnalysisStatus.IDLE,
        credential: Boolean = true,
    ) = FakeAppState(
        cloudConfiguration = CloudConfiguration(hasCredential = credential),
        telegramAuthState = TelegramAuthState(step = TelegramAuthStep.READY),
        channels = listOf(tadawul, nile),
        telegramSources = loaded,
        analysisStatus = status,
    )

    /** Records what the page asked for, and says notifications are allowed so no prompt is raised. */
    private class Recording(base: AppState) : AppState by base {
        var started = 0
        var cancelled = 0
        override fun notificationsPermitted(): Boolean = true
        override fun startAnalysis(confirmed: Boolean) { started++ }
        override suspend fun cancelAnalysis() { cancelled++ }
    }

    /** The run's button, as against the Analyze tab in the header, which has the same word. */
    private val analyzeButton = hasText("Analyze") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun show(state: AppState, posture: FoldPosture = FoldPosture.Cover) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalFoldPosture provides posture) {
                    AnalyzeScreen(state)
                }
            }
        }
    }

    @Test
    fun `a ready page says what it will read and Analyze starts the run`() {
        val state = Recording(ready())
        show(state)
        compose.onNodeWithText("Ready when you are").assertExists()
        compose.onNodeWithText("3").assertExists()
        compose.onNodeWithText("new posts to read").assertExists()
        compose.onNode(analyzeButton).performClick()
        assertEquals(1, state.started)
    }

    @Test
    fun `a blocked run says why and Analyze does nothing`() {
        val state = Recording(ready(credential = false))
        show(state)
        compose.onNodeWithText("Save the provider API key in Settings first.").assertExists()
        compose.onNode(analyzeButton).performClick()
        assertEquals(0, state.started)
    }

    @Test
    fun `a running analysis asks before it stops`() {
        val state = Recording(ready(status = AnalysisStatus.RUNNING))
        show(state)
        compose.onNodeWithContentDescription("Analysis running").assertExists()
        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithText("Stop this analysis?").assertExists()
        // The tile's Stop and the dialog's: the dialog's is the one that stops it.
        compose.onAllNodesWithText("Stop").onLast().performClick()
        compose.waitForIdle()
        assertEquals(1, state.cancelled)
    }

    @Test
    fun `a phone with nothing set up shows the checklist`() {
        show(FakeAppState())
        compose.onNodeWithText("Getting started").assertExists()
        compose.onNodeWithText("Sign in to Telegram").assertExists()
        compose.onNodeWithText("Not now").assertExists()
    }

    @Test
    fun `each source says how many of the loaded messages are its`() {
        show(ready())
        compose.onNodeWithText("Chats").performScrollTo()
        compose.onNodeWithText("2 chosen").assertExists()
        compose.onNodeWithText("2").assertExists()
        compose.onNodeWithText("1").assertExists()
    }

    @Test
    fun `unfolded, the run and what it reads sit side by side`() {
        show(ready(), FoldPosture.Unfolded)
        compose.onNodeWithText("Ready when you are").assertExists()
        compose.onNodeWithText("What it will read").assertExists()
    }

    @Test
    fun `in book posture the page splits at the hinge`() {
        show(ready(), FoldPosture.Book(0.49f, 0.51f))
        compose.onNodeWithText("Ready when you are").assertExists()
        compose.onNodeWithText("What it will read").assertExists()
    }

    @Test
    fun `in tabletop the button sits apart from the summary and still starts the run`() {
        val state = Recording(ready())
        show(state, FoldPosture.Tabletop(0.5f, 0.51f))
        compose.onNodeWithText("Ready when you are").assertExists()
        compose.onNode(analyzeButton).performClick()
        assertTrue(state.started == 1)
    }

    private fun running(progress: AnalysisProgress, endsAt: Instant? = null) = FakeAppState(
        cloudConfiguration = CloudConfiguration(hasCredential = true),
        telegramAuthState = TelegramAuthState(step = TelegramAuthStep.READY),
        channels = listOf(tadawul, nile),
        telegramSources = loaded,
        analysisStatus = AnalysisStatus.RUNNING,
        analysisProgress = progress,
        analysisReadingEndsAt = endsAt,
    )

    @Test
    fun `while it reads, the run says how long it has left`() {
        val reading = AnalysisProgress(AnalysisProgress.Stage.READING, batchesDone = 1, batches = 3, imagesDone = 8, images = 24)
        show(running(reading, endsAt = Instant.now().plusSeconds(200)))
        compose.onNodeWithText("8/24").assertExists()
        compose.onNodeWithText("minutes to go", substring = true).assertExists()
    }

    @Test
    fun `while it writes, there is no estimate`() {
        val writing = AnalysisProgress(AnalysisProgress.Stage.WRITING, batchesDone = 3, batches = 3, imagesDone = 24, images = 24)
        show(running(writing, endsAt = Instant.now().plusSeconds(200)))
        // The title and the timeline step it has reached both say it.
        assertTrue(compose.onAllNodesWithText("Writing the report").fetchSemanticsNodes().size == 2)
        compose.onNodeWithText("to go", substring = true).assertDoesNotExist()
    }

    @Test
    fun `while a run is going, what it reads is locked`() {
        show(running(AnalysisProgress(AnalysisProgress.Stage.READING, batches = 3, images = 24)))
        assertTrue(compose.onAllNodesWithText("locked while it runs").fetchSemanticsNodes().size >= 3)
        compose.onNodeWithText("Pick a day").assertDoesNotExist()
    }

    @Test
    fun `on tabletop a run is stopped from under the crease`() {
        show(running(AnalysisProgress(AnalysisProgress.Stage.READING, batches = 3, images = 24)), FoldPosture.Tabletop(0.5f, 0.51f))
        compose.onNodeWithText("Stop this run").performScrollTo().assertExists()
        compose.onNodeWithText("Stop").assertDoesNotExist()
    }

    @Test
    fun `a post an earlier run has read says it will be skipped`() {
        val state = FakeAppState(
            cloudConfiguration = CloudConfiguration(hasCredential = true),
            telegramAuthState = TelegramAuthState(step = TelegramAuthStep.READY),
            channels = listOf(tadawul, nile),
            telegramSources = loaded,
            sourcesReadEarlier = setOf(loaded.first().sourceId),
        )
        show(state)
        compose.onNodeWithText("Already read earlier · skipped").performScrollTo().assertExists()
        compose.onNodeWithText("1 of them was read earlier and will not be read again.").assertExists()
        // Two new posts in the hero, and Tadawul's own count of two beside its name.
        assertTrue(compose.onAllNodesWithText("2").fetchSemanticsNodes().size == 2)
    }
}
