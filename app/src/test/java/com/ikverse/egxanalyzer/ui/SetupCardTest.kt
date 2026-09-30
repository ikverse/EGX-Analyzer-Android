package com.ikverse.egxanalyzer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.CloudConfiguration
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.TelegramAuthState
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The card that tells a stranger what this app still needs.
 *
 * Two things are tested and they are not the same kind of thing. [setupSteps] is a plain function
 * and is checked as one - no composition, no rule. What needs a composition is the rule about when
 * the card appears at all, because the failure there is silent and permanent: a card that never
 * goes away nags every user of the app forever, and a card that never arrives is a feature that
 * shipped switched off. Neither shows up in a build.
 */
@RunWith(RobolectricTestRunner::class)
class SetupCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val signedIn = TelegramAuthState(step = TelegramAuthStep.READY)

    /** A key saved and a model named, which is what the two Settings steps are asking for. */
    private val configured = CloudConfiguration(
        model = "qwen/qwen3-vl-235b-a22b-instruct",
        hasCredential = true,
    )

    private fun ready() = FakeAppState(
        telegramAuthState = signedIn,
        channels = listOf(ChannelSelection(id = 1, name = "EGX calls", selected = true)),
        cloudConfiguration = configured,
    )

    /**
     * Three, not four - the model is already chosen on a phone that has done nothing at all.
     *
     * `CloudConfiguration` ships with its provider default, so that row arrives ticked, and this
     * test says so on purpose: a fresh install that reported four things outstanding would be
     * counting one the app had already answered for the reader. The row stays in the list because
     * the blank it guards against is reachable - clearing the field is what `NO_MODEL` is for.
     */
    @Test
    fun `a phone that has done nothing has three things left to do`() {
        val steps = setupSteps(FakeAppState())

        assertEquals(4, steps.size)
        assertEquals(3, steps.count { !it.done })
        assertTrue("the model ships chosen", steps.last().done)
        // The order is the order a run needs them, and Telegram is first because it is the one
        // account this app requires of anybody.
        assertEquals("Sign in to Telegram", steps.first().title)
    }

    @Test
    fun `each step reads the state that actually gates a run`() {
        val steps = setupSteps(ready())

        assertTrue(steps.joinToString { it.title + "=" + it.done }, steps.all(SetupStep::done))
    }

    @Test
    fun `signing in alone does not tick choosing the chats`() {
        val steps = setupSteps(
            FakeAppState(
                telegramAuthState = signedIn,
                channels = listOf(ChannelSelection(id = 1, name = "EGX calls", selected = false)),
            ),
        )

        assertTrue(steps.first().done)
        assertFalse(steps[1].done)
    }

    /** The two that are carried out somewhere else are the two the card offers to go to. */
    @Test
    fun `only the provider steps send the reader to Settings`() {
        val settings = setupSteps(FakeAppState()).filter(SetupStep::settings).map(SetupStep::title)

        assertEquals(listOf("Add a provider API key", "Choose the model"), settings)
    }

    @Test
    fun `a phone with nothing set up is told what to do`() {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) { SetupCard(FakeAppState()) }
        }

        compose.onNodeWithText("Getting started").assertIsDisplayed()
        compose.onNodeWithText("Sign in to Telegram").assertIsDisplayed()
        compose.onNodeWithText("Open Settings").assertIsDisplayed()
    }

    /**
     * The card is for a phone that has never got anywhere. One with a report on it has, whatever
     * its settings say now - a key removed on a working install is not somebody starting out.
     */
    @Test
    fun `a phone that already has a report is never told how to start`() {
        val withReport = FakeAppState(
            savedResults = listOf(
                SavedAnalysis(
                    id = 1,
                    result = AnalysisResult(
                        requestId = "r1",
                        recommendations = emptyList(),
                        inquiryReplyCount = 0,
                    ),
                    provider = CloudProvider.entries.first(),
                    model = "qwen/qwen3-vl-235b-a22b-instruct",
                ),
            ),
        )
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) { SetupCard(withReport) }
        }

        compose.onNodeWithText("Getting started").assertDoesNotExist()
    }

    /** Ready and simply not asked yet is not a phone that needs instructions. */
    @Test
    fun `a phone with every step done is not told to get started`() {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) { SetupCard(ready()) }
        }

        compose.onNodeWithText("Getting started").assertDoesNotExist()
    }

    @Test
    fun `Not now takes it off the page`() {
        val state = FakeAppState()
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) { SetupCard(state) }
        }

        compose.onNodeWithText("Not now").performClick()

        compose.onNodeWithText("Getting started").assertDoesNotExist()
        assertTrue(state.pages.analyzeSetupDismissed.value)
    }
}
