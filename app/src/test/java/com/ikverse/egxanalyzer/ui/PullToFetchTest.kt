package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The pull that fetches prices: the band the page uncovers, the point where a release acts, and
 * the refusal while a fetch is already running. One `setContent` per test.
 */
@RunWith(RobolectricTestRunner::class)
class PullToFetchTest {

    @get:Rule
    val compose = createComposeRule()

    private var fetched = 0
    private var refreshing by mutableStateOf(false)

    private fun show(words: PullWords = PullWords.Prices) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                Screen(
                    appState = FakeAppState(),
                    destination = AppDestination.PORTFOLIO,
                    onRefresh = { fetched++ },
                    refreshing = refreshing,
                    pullWords = words,
                ) {
                    Box(Modifier.fillMaxWidth().height(120.dp).testTag("page"))
                }
            }
        }
    }

    /** Finger down on the page and dragged [dp] down, in steps, and left there. */
    private fun pull(dp: Int) {
        compose.onNodeWithTag("page").performTouchInput {
            down(Offset(centerX, 20f))
            // Past the touch slop first, then the distance itself.
            moveBy(Offset(0f, viewConfiguration.touchSlop + 1f))
            repeat(dp / 10) { moveBy(Offset(0f, 10.dp.toPx())) }
        }
    }

    private fun lift() {
        compose.onNodeWithTag("page").performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun `a page at rest shows no band`() {
        show()
        compose.onNodeWithText("Pull to fetch prices").assertDoesNotExist()
    }

    @Test
    fun `a short pull names the gesture and a release fetches nothing`() {
        show()
        pull(60)
        compose.onNodeWithText("Pull to fetch prices").assertExists()
        compose.onNodeWithText("Prices not fetched yet").assertExists()
        lift()
        assertEquals(0, fetched)
        compose.onNodeWithText("Pull to fetch prices").assertDoesNotExist()
    }

    @Test
    fun `a full pull says release and a release fetches once`() {
        show()
        // The page moves at half the finger's speed, so the band's 64dp takes 128dp of finger.
        pull(160)
        compose.onNodeWithText("Release to fetch prices").assertExists()
        lift()
        assertEquals(1, fetched)
        compose.onNodeWithText("Release to fetch prices").assertDoesNotExist()
    }

    @Test
    fun `a pull while a fetch is running says so and fetches nothing`() {
        refreshing = true
        show()
        pull(160)
        compose.onNodeWithText("Already fetching prices").assertExists()
        lift()
        assertEquals(0, fetched)
    }

    @Test
    fun `the chat list has its own words and no price age`() {
        show(PullWords.Chats)
        pull(160)
        compose.onNodeWithText("Release to refresh chats").assertExists()
        compose.onNodeWithText("Prices not fetched yet").assertDoesNotExist()
    }

    @Test
    fun `the band's words follow the gesture, and running work outranks it`() {
        val words = PullWords.Prices
        assertEquals("Pull to fetch prices", pullBandLabel(words, armed = false, busy = false))
        assertEquals("Release to fetch prices", pullBandLabel(words, armed = true, busy = false))
        assertEquals("Already fetching prices", pullBandLabel(words, armed = true, busy = true))
    }

    @Test
    fun `the price age reads off the last fetch`() {
        val now = Instant.parse("2026-11-11T12:00:00Z")
        assertEquals("Prices not fetched yet", pricesAsOfLabel(0L, now))
        assertEquals(
            "Prices from ${whenLabel(now.minusSeconds(600), now)}",
            pricesAsOfLabel(now.minusSeconds(600).toEpochMilli(), now),
        )
    }
}
