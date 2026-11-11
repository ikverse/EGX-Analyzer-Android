package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import com.ikverse.egxanalyzer.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The page header, driven rather than looked at.
 *
 * **The first Compose test in this app, and it is here because this is where the bug was.** The box
 * that disappeared on the first letter (2026-09-11) reached a phone and was reported by the owner,
 * and nothing in ninety-odd test files could have caught it: every one of them is a function of its
 * arguments, and that fault was a composition losing state it should never have been holding. This
 * is the shape of thing only a test that composes can see.
 *
 * Robolectric, so it runs in `testDebugUnitTest` beside everything else. `src/androidTest` would
 * have put this behind a device or the emulator, and the emulator is the slowest thing here - a
 * suite that has to be asked for is a suite that stops being run.
 *
 * The header is driven through its own arguments and one [StockBox]; it needs no `AppState`, no
 * repository and no database, which is what the interface at `ui/AppState.kt` is for.
 */
@RunWith(RobolectricTestRunner::class)
class PageHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    private val box = StockBox()

    /**
     * The header as a reader meets it, with the title finished shrinking.
     *
     * `collapse = 1f` is just the default rest state; the icons are on screen and pressable at
     * every point of the collapse now, not only here - see the tests below that drive `0f`.
     *
     * @param generation bumping it disposes this composition and builds a new one. That is the
     *   whole mechanism of the bug below - anything that rebuilt the header used to take the box
     *   with it - so a test of the fix has to be able to do it on purpose.
     */
    @Composable
    private fun Header(
        generation: Int = 0,
        collapse: Float = 1f,
        filtered: Boolean = false,
        filters: Boolean = false,
    ) {
        EgxAnalyzerTheme(themeMode = ThemeMode.DARK) {
            key(generation) {
                PageHeader(
                    destination = AppDestination.INSIGHTS,
                    collapse = { collapse },
                    search = box,
                    current = true,
                    filters = mutableStateOf(filters),
                    filtered = filtered,
                    stocksOnPage = { setOf("COMI", "PHDC") },
                )
            }
        }
    }

    @Test
    fun `the header says which page this is`() {
        compose.setContent { Header() }

        compose.onNodeWithText("Insights").assertIsDisplayed()
    }

    @Test
    fun `the search icon opens the box`() {
        compose.setContent { Header() }

        compose.onNodeWithContentDescription("Filter by stock").performClick()

        compose.onNodeWithText("Search stocks").assertIsDisplayed()
        assertTrue(box.opened.value)
        assertTrue(box.listing.value)
    }

    /**
     * The regression, in the shape it was reported: *"I can't write anything in the box, the box
     * disappears."*
     *
     * Typing a letter and then rebuilding the header is exactly what happened on the phone - the
     * key press recomposed something above, the header was built again, and `opened` and `typed`
     * came back at their defaults. With all four fields on [StockBox] the rebuild costs nothing.
     */
    @Test
    fun `a rebuilt header keeps the box open with what was typed in it`() {
        var generation by mutableIntStateOf(0)
        compose.setContent { Header(generation = generation) }

        compose.onNodeWithContentDescription("Filter by stock").performClick()
        // The field itself and not the placeholder beside it: "Search stocks" is a Text drawn
        // under an empty field, and it is the field that takes the keys.
        compose.onNode(hasSetTextAction()).performTextInput("COM")
        generation++
        compose.waitForIdle()

        assertTrue("the box closed itself", box.opened.value)
        assertEquals("COM", box.typed.value)
        compose.onNodeWithText("COM").assertIsDisplayed()
    }

    /**
     * The X means "never mind the filter", not "never mind the list" - so it takes the pick with
     * it and the page underneath opens back up. See `StockBox.clear`.
     */
    @Test
    fun `clearing puts the page's title back and unfilters it`() {
        box.picked.value = "COMI"
        box.opened.value = true
        compose.setContent { Header() }

        compose.onNodeWithContentDescription("Clear stock filter").performClick()

        assertEquals("", box.picked.value)
        assertFalse(box.opened.value)
        compose.onNodeWithText("Insights").assertIsDisplayed()
    }

    /**
     * A change of tab, as the shell does it since 2026-11-11: the page - header included - is
     * built afresh under a [TabTravel] that outlives it. The name pressed becomes the selected
     * one, the buttons are still there on the new page, and pressing the name already showing asks
     * for the top of that page rather than navigating again.
     */
    @Test
    fun `pressing a tab's name lands on that tab, and pressing it again asks for the top`() {
        var destination by mutableStateOf(AppDestination.INSIGHTS)
        var toTop by mutableStateOf<AppDestination?>(null)
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) {
                val travel = remember { TabTravel(destination) }
                LaunchedEffect(destination) { travel.goTo(destination, still = false) }
                CompositionLocalProvider(LocalTabTravel provides travel) {
                    key(destination) {
                        PageHeader(
                            destination = destination,
                            collapse = { 0f },
                            search = box,
                            current = true,
                            filters = mutableStateOf(false),
                            filtered = false,
                            stocksOnPage = { emptySet() },
                            onNavigate = { destination = it },
                            onScrollToTop = { toTop = it },
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("Results").performClick()
        compose.waitForIdle()

        assertEquals(AppDestination.RESULTS, destination)
        compose.onNode(hasText("Results") and isSelected()).assertIsDisplayed()
        compose.onNode(hasText("Insights") and isSelected()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Filters").assertIsDisplayed()

        compose.onNodeWithText("Results").performClick()

        assertEquals(AppDestination.RESULTS, toTop)
        assertEquals(AppDestination.RESULTS, destination)
    }

    /** With reduce motion on a travel lands at once: no frame draws a name part-way between sizes. */
    @Test
    fun `a travel told to hold still is already there`() = kotlinx.coroutines.test.runTest {
        val travel = TabTravel(AppDestination.PORTFOLIO)

        // A page composes a frame before the travel hears of it, and is drawn as the old one was.
        assertEquals(1f, travel.emphasis(AppDestination.PORTFOLIO, current = AppDestination.RESULTS))
        assertEquals(0f, travel.emphasis(AppDestination.RESULTS, current = AppDestination.RESULTS))

        travel.goTo(AppDestination.RESULTS, still = true)

        assertEquals(1f, travel.emphasis(AppDestination.RESULTS, current = AppDestination.RESULTS))
        assertEquals(0f, travel.emphasis(AppDestination.PORTFOLIO, current = AppDestination.RESULTS))
        assertEquals(AppDestination.PORTFOLIO, travel.from)
    }

    /** Unfolded the buttons close the tab row rather than sitting on the session line. */
    @Test
    fun `the buttons are there and pressable unfolded too`() {
        val filters = mutableStateOf(false)
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(LocalWindowWidth provides WindowWidth.EXPANDED) {
                    PageHeader(
                        destination = AppDestination.INSIGHTS,
                        collapse = { 0f },
                        search = box,
                        current = true,
                        filters = filters,
                        filtered = false,
                        stocksOnPage = { emptySet() },
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Filter by stock").assertIsDisplayed()
        compose.onNodeWithContentDescription("Filters").performClick()

        assertTrue(filters.value)
    }

    /**
     * The buttons are on screen and pressable at the top of the scroll, not only once the header
     * has collapsed: on the cover screen they sit on the session line there, and stay pinned at the
     * right as that line folds away. Without this, a page at the top of a long list gave no sign
     * that it could be searched or filtered at all until the reader had scrolled.
     */
    @Test
    fun `an unfiltered page at the top of its scroll still shows and presses its icons`() {
        compose.setContent { Header(collapse = 0f) }

        compose.onNodeWithContentDescription("Filter by stock").assertIsDisplayed().performClick()

        compose.onNodeWithText("Search stocks").assertIsDisplayed()
    }

    /** The dot on the filter icon is unaffected by the collapse - it says the sheet is narrowing. */
    @Test
    fun `a filtered page's dot shows at the top of its scroll too`() {
        compose.setContent { Header(collapse = 0f, filtered = true) }

        compose.onNodeWithContentDescription("Filters, on").assertIsDisplayed()
    }

    /**
     * Analyze has no list to narrow, so its search opens the sheet of the stock picked and the box
     * goes away behind it, leaving nothing picked (2026-11-11).
     */
    @Test
    fun `on Analyze a pick opens the stock's sheet and the box closes`() {
        var opened by mutableStateOf<String?>(null)
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(LocalOpenStock provides { opened = it }) {
                    PageHeader(
                        destination = AppDestination.ANALYZE,
                        collapse = { 0f },
                        search = box,
                        pickOpensStock = true,
                        current = true,
                        filters = null,
                        filtered = false,
                        stocksOnPage = { emptySet() },
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Filter by stock").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("COMI")
        compose.onAllNodes(hasClickAction() and hasText("COMI ·", substring = true)).onFirst().performClick()
        compose.waitForIdle()

        assertEquals("COMI", opened)
        assertEquals("", box.picked.value)
        assertFalse(box.opened.value)
        assertFalse(box.listing.value)
    }

    /**
     * The header keeps room for both buttons on every page, so a page with fewer of them does not
     * hand the tab row more width - which moved every name sideways on the way to or from Settings
     * and Analyze. The same page drawn with and without its buttons puts its names in one place.
     */
    @Test
    fun `the tab names stay put whether or not a page has buttons`() {
        var buttons by mutableStateOf(true)
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) {
                PageHeader(
                    destination = AppDestination.SETTINGS,
                    collapse = { 1f },
                    search = if (buttons) box else null,
                    current = true,
                    filters = if (buttons) mutableStateOf(false) else null,
                    filtered = false,
                    stocksOnPage = { emptySet() },
                )
            }
        }
        fun left() = compose.onNodeWithText("Settings").fetchSemanticsNode().boundsInRoot.left

        val withButtons = left()
        buttons = false
        compose.waitForIdle()

        assertEquals(withButtons, left(), 0.5f)
    }
}
