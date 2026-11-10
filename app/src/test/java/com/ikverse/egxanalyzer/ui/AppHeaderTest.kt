package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.AnalysisProgress
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.SessionState
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The 5.0 header: the tab row that replaced the floating bar, the session line, the run's progress
 * and the status line's Undo.
 */
@RunWith(RobolectricTestRunner::class)
class AppHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun cairo(text: String): Instant = LocalDateTime.parse(text).atZone(ScheduleClock.ZONE).toInstant()

    @Test
    fun `the tabs run Portfolio first`() {
        assertEquals(
            listOf("Portfolio", "Analyze", "Results", "Insights", "Settings"),
            AppDestination.entries.map(AppDestination::label),
        )
    }

    /**
     * Every tab is reachable at both real widths - the cover screen and the unfolded Fold - and a
     * press on another tab navigates while a press on this one goes back to the top. The width is
     * moved, not re-set, because a rule allows one `setContent` per test.
     */
    @Test
    fun `every tab answers a press at the cover and unfolded widths`() {
        var width by mutableIntStateOf(411)
        val navigated = mutableListOf<AppDestination>()
        var toTop = 0
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                HeaderTabs(
                    current = AppDestination.RESULTS,
                    collapse = 0f,
                    onSelect = { navigated += it },
                    onReselect = { toTop++ },
                    modifier = Modifier.width(width.dp),
                )
            }
        }
        listOf(411, 750).forEach { w ->
            width = w
            compose.waitForIdle()
            compose.onNodeWithText("Results").assertIsSelected()
            AppDestination.entries.filter { it != AppDestination.RESULTS }.forEach { other ->
                compose.onNodeWithText(other.label).performScrollTo().assertIsNotSelected().performClick()
            }
            compose.onNodeWithText("Results").performScrollTo().performClick()
        }
        assertEquals(8, navigated.size)
        assertEquals(2, toTop)
    }

    @Test
    fun `the session line names the close while trading and the next open otherwise`() {
        val monday = cairo("2026-11-09T11:00:00")
        assertEquals("Session open · closes 14:30", sessionText(ScheduleClock.sessionState(monday), monday))
        val evening = cairo("2026-11-09T18:00:00")
        assertEquals("Market closed · opens tomorrow 10:00", sessionText(ScheduleClock.sessionState(evening), evening))
        val early = cairo("2026-11-09T08:00:00")
        assertEquals("Market closed · opens 10:00", sessionText(ScheduleClock.sessionState(early), early))
        val thursdayNight = cairo("2026-11-12T20:00:00")
        assertEquals("Market closed · opens Sun 10:00", sessionText(ScheduleClock.sessionState(thursdayNight), thursdayNight))
        assertEquals("Market closed", sessionText(SessionState(open = false, nextChange = null)))
    }

    @Test
    fun `the run's clock reads minutes and seconds`() {
        val start = cairo("2026-11-09T11:00:00")
        assertEquals("1:42", elapsedSince(start, start.plusSeconds(102)))
        assertEquals("0:00", elapsedSince(start, start.minusSeconds(5)))
        assertNull(elapsedSince(null))
    }

    @Test
    fun `a running analysis says which batch it is on`() {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                RunProgress(
                    progress = AnalysisProgress(AnalysisProgress.Stage.READING, batchesDone = 2, batches = 5, imagesDone = 16, images = 40),
                    startedAt = null,
                )
            }
        }
        compose.onNodeWithContentDescription("Analysis running: Batch 3 of 5").assertExists()
    }

    @Test
    fun `undo on the status line is a button that runs the undo`() {
        var undone = false
        val state = FakeAppState(
            statusMessage = StatusMessage("TMGH removed", succeeded = true, undo = StatusUndo("Undo") { undone = true }),
        )
        var dismissed by mutableStateOf(false)
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                AppStatusLine(state, onDismiss = { dismissed = true })
            }
        }
        compose.onNodeWithText("TMGH removed").assertExists()
        compose.onNodeWithText("Undo").performClick()
        assertTrue(undone)
        assertTrue(dismissed)
    }
}
