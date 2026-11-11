package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Swiping between tabs: a swipe on the page changes tab the way a press does, a short one settles
 * back, nothing happens past either end, and a sideways scroller keeps its own gesture.
 *
 * Driven through the real modifier on a stand-in page, one `setContent` per test.
 */
@RunWith(RobolectricTestRunner::class)
class TabSwipeTest {

    @get:Rule
    val compose = createComposeRule()

    private var destination by mutableStateOf(AppDestination.ANALYZE)

    private fun show(start: AppDestination = AppDestination.ANALYZE, still: Boolean = false) {
        destination = start
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                val swipe = remember { TabSwipe() }
                val travel = remember { TabTravel(start) }
                val scope = rememberCoroutineScope()
                Column(
                    Modifier
                        .fillMaxSize()
                        .tabSwipe(swipe, { destination }, travel, { still }, scope) { destination = it },
                ) {
                    Row(Modifier.testTag("chips").horizontalScroll(rememberScrollState())) {
                        repeat(40) { Text("Chip $it   ") }
                    }
                    Box(Modifier.fillMaxWidth().height(300.dp).testTag("body"))
                }
            }
        }
    }

    @Test
    fun `a swipe left goes to the next tab`() {
        show()
        compose.onNodeWithTag("body").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(AppDestination.RESULTS, destination)
    }

    @Test
    fun `a swipe right goes to the previous tab`() {
        show()
        compose.onNodeWithTag("body").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(AppDestination.PORTFOLIO, destination)
    }

    @Test
    fun `a short slow drag settles back without changing tab`() {
        show()
        compose.onNodeWithTag("body").performTouchInput {
            down(center)
            repeat(4) { moveBy(Offset(-10f, 0f), delayMillis = 100) }
            up()
        }
        compose.waitForIdle()
        assertEquals(AppDestination.ANALYZE, destination)
    }

    @Test
    fun `nothing happens past the first tab`() {
        show(start = AppDestination.entries.first())
        compose.onNodeWithTag("body").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(AppDestination.entries.first(), destination)
    }

    @Test
    fun `nothing happens past the last tab`() {
        show(start = AppDestination.entries.last())
        compose.onNodeWithTag("body").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(AppDestination.entries.last(), destination)
    }

    @Test
    fun `a swipe on a chip row scrolls the chips and stays on the tab`() {
        show()
        compose.onNodeWithTag("chips").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(AppDestination.ANALYZE, destination)
    }

    @Test
    fun `with reduced motion a swipe still changes tab`() {
        show(still = true)
        compose.onNodeWithTag("body").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(AppDestination.RESULTS, destination)
    }
}
