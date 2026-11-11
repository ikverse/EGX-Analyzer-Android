package com.ikverse.egxanalyzer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.ikverse.egxanalyzer.model.CallGroup
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.theme.AppPalette
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import com.ikverse.egxanalyzer.ui.theme.palette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * One colour per status, whichever screen says it. Until 5.1.5 "Running" was one blue on Portfolio
 * and another on Insights, and "ran out of time" was amber on a trade and grey on a call.
 */
@RunWith(RobolectricTestRunner::class)
class StatusTonesTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the same status is the same colour on a call, a trade and a report`() {
        val seen = mutableMapOf<String, List<Color>>()
        var p: AppPalette? = null
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) {
                p = palette
                seen["running"] = listOf(Outcome.OPEN.tone(), PositionStatus.OPEN.tone(), CallGroup.RUNNING.tone())
                seen["reached"] = listOf(
                    Outcome.PARTIAL_HIT.tone(), Outcome.FULL_HIT.tone(),
                    PositionStatus.PARTIAL_TARGET_HIT.tone(), PositionStatus.FULL_TARGET_HIT.tone(), CallGroup.REACHED.tone(),
                )
                seen["stopped"] = listOf(Outcome.STOPPED.tone(), PositionStatus.STOPPED_OUT.tone(), CallGroup.STOPPED.tone())
                seen["neither"] = listOf(
                    Outcome.EXPIRED.tone(), Outcome.ENTRY_NOT_REACHED.tone(),
                    PositionStatus.EXPIRED.tone(), PositionStatus.CLOSED_MANUALLY.tone(),
                    CallGroup.WATCHING.tone(), CallGroup.ENDED.tone(),
                )
            }
        }
        compose.waitForIdle()
        val palette = p!!
        assertEquals(setOf(palette.heroInk), seen.getValue("running").toSet())
        assertEquals(setOf(palette.gain), seen.getValue("reached").toSet())
        assertEquals(setOf(palette.loss), seen.getValue("stopped").toSet())
        assertEquals(setOf(palette.muted), seen.getValue("neither").toSet())
    }
}
