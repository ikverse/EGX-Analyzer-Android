package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ClaudeInPlan
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The ClaudeIn pairing card and the plan tile, driven the way the owner would: Scan and Type it
 * instead on an unpaired card at every real container width, the typed route handing its address
 * and code on, the fingerprint dialog, and the two states that turn the card coral.
 */
@RunWith(RobolectricTestRunner::class)
class ClaudeInCardTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: AppState, width: () -> Int = { 614 }) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalFoldPosture provides FoldPosture.Cover) {
                    Box(Modifier.width(width().dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            ClaudeInPairingCard(state)
                            ClaudeInPlanTile(state)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `an unpaired card offers both ways in at every real width`() {
        var width by mutableIntStateOf(614)
        show(FakeAppState(), width = { width })
        for (w in listOf(614, 682, 715)) {
            width = w
            compose.waitForIdle()
            compose.onNodeWithText("Scan QR code").assertIsDisplayed()
            compose.onNodeWithText("Type it instead").assertIsDisplayed()
            compose.onNodeWithText("Not paired").assertIsDisplayed()
        }
    }

    @Test
    fun `typing an address and a code hands both on`() {
        var asked: Pair<String, String>? = null
        show(FakeAppState(onClaudeInTyped = { a, c -> asked = a to c }))
        compose.onNodeWithText("Type it instead").performClick()
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("100.101.102.103:8790")
        fields[1].performTextInput("abcde-fgh23")
        compose.onNodeWithText("Pair").performScrollTo().performClick()
        assertEquals("100.101.102.103:8790" to "ABCDE-FGH23", asked)
    }

    @Test
    fun `the fingerprint dialog shows the six characters and pairs only on a match`() {
        var confirmed = 0
        var refused = 0
        show(
            FakeAppState(
                claudeInConfirm = ClaudeInConfirm("100.101.102.103:8790", "C0FFEE"),
                onClaudeInConfirm = { confirmed++ },
                onClaudeInCancel = { refused++ },
            ),
        )
        compose.onNodeWithText("Does ClaudeIn show these?").assertIsDisplayed()
        compose.onNodeWithText("C0F FEE").assertIsDisplayed()
        compose.onNodeWithText("They match").performClick()
        assertEquals(1, confirmed)
        assertEquals(0, refused)
    }

    @Test
    fun `a pairing for another app says so and offers to pair again`() {
        show(
            FakeAppState(
                claudeInState = ClaudeInLinkState.WRONG_APP,
                claudeInPaired = ClaudeInPaired("my-pc", "100.101.102.103", "C0FFEE"),
                claudeInMessage = com.ikverse.egxanalyzer.model.ClaudeInOutcome.WRONG_APP,
            ),
        )
        compose.onNodeWithText("Wrong app").assertIsDisplayed()
        compose.onNodeWithText(com.ikverse.egxanalyzer.model.ClaudeInOutcome.WRONG_APP).assertIsDisplayed()
        compose.onNodeWithText("Pair again").assertIsDisplayed()
        compose.onNodeWithText("Certificate …C0FFEE").assertIsDisplayed()
    }

    @Test
    fun `a paired PC that does not answer says PC not available`() {
        var forgotten = 0
        show(
            FakeAppState(
                claudeInState = ClaudeInLinkState.NOT_AVAILABLE,
                claudeInPaired = ClaudeInPaired("my-pc", "100.101.102.103", "C0FFEE"),
                onClaudeInForget = { forgotten++ },
            ),
        )
        compose.onNodeWithText("PC not available").assertIsDisplayed()
        compose.onNodeWithText("Forget").performClick()
        assertEquals(1, forgotten)
    }

    @Test
    fun `scan opens the camera sheet, which offers typing instead`() {
        show(FakeAppState())
        compose.onNodeWithText("Scan QR code").performClick()
        compose.onNodeWithText("Scan ClaudeIn's code").assertExists()
        // The card's link and the sheet's own: the sheet offers the other way in too.
        assertTrue(compose.onAllNodesWithText("Type it instead").fetchSemanticsNodes().size >= 2)
    }

    @Test
    fun `the plan tile shows both windows in percent, and what the PC says during a run`() {
        show(
            FakeAppState(
                claudeInPlan = ClaudeInPlan(fiveHour = 0.34, sevenDay = 0.12, at = 0L),
                claudeInWorking = "Waiting for the PC (1 ahead)…",
            ),
        )
        compose.onNodeWithText("34%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12%").assertExists()
        compose.onNodeWithText("Waiting for the PC (1 ahead)…").assertExists()
    }
}
