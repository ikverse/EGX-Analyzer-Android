package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
     * `collapse = 1f` because the icons deliberately arrive with the last of the shrink: at
     * `0f` there is nothing to press, which is the header working as designed and not a test
     * fixture worth fighting.
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
     * A filtered page carries its icons from the first frame of the scroll rather than fading them
     * in with the collapse. Without it a page narrowed to two channels sits at the top of its
     * scroll with nothing on screen saying so.
     */
    @Test
    fun `a filtered page shows its controls before the header has collapsed`() {
        compose.setContent { Header(collapse = 0f, filtered = true) }

        compose.onNodeWithContentDescription("Filters, on").assertIsDisplayed()
    }

    @Test
    fun `an unfiltered page at the top of its scroll shows none`() {
        compose.setContent { Header(collapse = 0f) }

        compose.onNodeWithContentDescription("Filter by stock").assertDoesNotExist()
        compose.onNodeWithContentDescription("Filters").assertDoesNotExist()
    }
}
