package com.ikverse.egxanalyzer.ui.kit

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Two pills side by side are one height, whatever is written in them. Reported from the phone on
 * 2026-11-11: an Arabic channel name stood taller than "Watching" beside it, because a tag was as
 * tall as its text and the Arabic fallback reaches further above and below the line.
 */
@RunWith(RobolectricTestRunner::class)
class KitTagTest {

    @get:Rule
    val compose = createComposeRule()

    private val words = listOf("Watching", "إسأل في", "T+1", "اشترِ الآن · Buy", "g")

    private fun heights(prefix: String) =
        words.indices.map { compose.onNodeWithTag("$prefix$it").fetchSemanticsNode().size.height }

    @Test
    fun `every tag of a size is one height, in any script`() {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                Row {
                    words.forEachIndexed { i, w -> KitTag(w, Color.Gray, Modifier.testTag("s$i")) }
                    words.forEachIndexed { i, w -> KitTag(w, Color.Gray, Modifier.testTag("l$i"), large = true) }
                }
            }
        }
        val small = heights("s")
        val large = heights("l")
        assertEquals("small tags differ: $small", 1, small.toSet().size)
        assertEquals("large tags differ: $large", 1, large.toSet().size)
        assertTrue("a large tag should be taller than a small one", large.first() > small.first())
    }

    /**
     * The test machine draws no Arabic fallback, so the measurement above cannot see the fault it
     * was written for on its own. This holds the mechanism instead: a fixed height, read off the
     * style, and a line that is not trimmed to whatever font happens to be drawing it.
     */
    @Test
    fun `the tag's height is fixed in its source, not taken from its text`() {
        val tag = java.io.File("src/main/java/com/ikverse/egxanalyzer/ui/kit/Extras.kt").readText()
            .replace("\r\n", "\n").substringAfter("fun KitTag(").substringBefore("\n}\n")
        assertTrue("KitTag no longer has a fixed height", ".height(height)" in tag && "style.lineHeight.toDp()" in tag)
        assertTrue("KitTag's line is trimmed to the font again", "LineHeightStyle.Trim.None" in tag)
        assertTrue("KitTag pads vertically again, which adds to the fixed height", "vertical =" !in tag)
    }

    @Test
    fun `the height still grows with the phone's font size`() {
        var plain = 0
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                val base = LocalDensity.current
                Row {
                    KitTag("Watching", Color.Gray, Modifier.testTag("plain"))
                    CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.5f)) {
                        words.forEachIndexed { i, w -> KitTag(w, Color.Gray, Modifier.testTag("b$i")) }
                    }
                }
            }
        }
        plain = compose.onNodeWithTag("plain").fetchSemanticsNode().size.height
        val big = heights("b")
        assertEquals("tags at a large font size differ: $big", 1, big.toSet().size)
        assertTrue("a larger font should make a taller tag ($plain vs ${big.first()})", big.first() > plain)
    }
}
