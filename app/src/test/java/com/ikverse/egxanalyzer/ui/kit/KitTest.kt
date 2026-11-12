package com.ikverse.egxanalyzer.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The kit's pieces do what they say, under the real theme, before any screen uses them.
 *
 * One `setContent` per test, as everywhere in this suite.
 */
@RunWith(RobolectricTestRunner::class)
class KitTest {

    @get:Rule
    val compose = createComposeRule()

    private fun content(body: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) { body() } }

    @Test
    fun `a switch flips and says so`() {
        var on by mutableStateOf(false)
        content { KitSwitch(checked = on, onCheckedChange = { on = it }, label = "Sync with Telegram") }
        val node = compose.onNodeWithContentDescription("Sync with Telegram")
        node.assertIsOff()
        node.performClick()
        node.assertIsOn()
        assertTrue(on)
    }

    @Test
    fun `a checkbox ticks`() {
        var ticked by mutableStateOf(false)
        content { KitCheckbox(checked = ticked, onCheckedChange = { ticked = it }, label = "Tadawul Calls") }
        compose.onNodeWithContentDescription("Tadawul Calls").performClick().assertIsOn()
    }

    @Test
    fun `a segmented control selects the segment pressed`() {
        var mode by mutableStateOf("Auto")
        content {
            SegmentedControl(listOf("Light", "Dark", "Auto"), mode, { mode = it }, label = { it })
        }
        compose.onNodeWithText("Dark").performClick()
        assertEquals("Dark", mode)
        compose.onNodeWithText("Dark").assertIsSelected()
    }

    @Test
    fun `a chip toggles and shows its tick when selected`() {
        var chosen by mutableStateOf(false)
        content { KitChip("Nile Signals", selected = chosen, onClick = { chosen = !chosen }) }
        compose.onNodeWithText("Nile Signals").performClick()
        compose.onNodeWithText("✓ Nile Signals").assertIsDisplayed()
    }

    @Test
    fun `a stepper stops at the ends of its range`() {
        var sessions by mutableIntStateOf(59)
        content { KitStepper(sessions, { sessions = it }, range = 1..60, label = "Trade window") }
        compose.onNodeWithContentDescription("More").performClick()
        assertEquals(60, sessions)
        compose.onNodeWithContentDescription("More").assertIsNotEnabled()
    }

    @Test
    fun `a field with an error announces it`() {
        content { KitTextField("72.0O", {}, label = "Stop loss", figure = true, error = "Not a number") }
        compose.onNodeWithText("Not a number").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)).assertExists()
    }

    @Test
    fun `a field shows what it was and puts it back`() {
        var value by mutableStateOf("86.50")
        content { KitTextField(value, { value = it }, label = "Target 2", figure = true, was = "85.60", onPutBack = { value = "85.60" }) }
        compose.onNodeWithText("was 85.60").assertIsDisplayed()
        compose.onNodeWithText("Put back").performClick()
        assertEquals("85.60", value)
    }

    @Test
    fun `a disabled button does not fire`() {
        var presses = 0
        content {
            Column {
                KitButton("Save", onClick = { presses++ })
                KitButton("Delete everywhere", onClick = { presses += 10 }, kind = ButtonKind.DANGER, enabled = false)
            }
        }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Delete everywhere").performClick()
        assertEquals(1, presses)
    }

    @Test
    fun `an icon button is named for a screen reader`() {
        var pressed = false
        content {
            KitIconButton(Icons.Filled.Search, label = "Search stocks", onClick = { pressed = true })
        }
        compose.onNodeWithContentDescription("Search stocks").performClick()
        assertTrue(pressed)
    }

    @Test
    fun `a pressable tile is one button`() {
        var opened = false
        content {
            Tile(tone = TileTone.ALERT, onClick = { opened = true }) { Text("Needs you") }
        }
        compose.onNodeWithText("Needs you").performClick()
        assertTrue(opened)
    }

    @Test
    fun `a dialog offers both choices and each one answers`() {
        var answer by mutableStateOf("")
        content {
            KitDialog(
                title = "Delete this analysis?",
                text = "This cannot be undone.",
                confirmLabel = "Delete everywhere",
                onConfirm = { answer = "delete" },
                dismissLabel = "Keep",
                onDismiss = { answer = "keep" },
                destructive = true,
            )
        }
        compose.onNodeWithText("Delete this analysis?").assertIsDisplayed()
        compose.onNodeWithText("Keep").performClick()
        assertEquals("keep", answer)
    }

    @Test
    fun `a menu runs the item pressed and closes`() {
        var open by mutableStateOf(true)
        var removed = false
        content {
            KitMenu(
                expanded = open,
                onDismiss = { open = false },
                items = listOf(MenuItem("Edit trade") {}, MenuItem("Remove", danger = true) { removed = true }),
            )
        }
        compose.onNodeWithText("Remove").performClick()
        assertTrue(removed)
        assertTrue(!open)
    }

    @Test
    fun `the market bars describe themselves`() {
        content {
            Column {
                TargetProgress(progress = 0.82f, tickAt = 0.55f, label = "82% of the way to target 2")
                BreadthBar(up = 121, flat = 34, down = 68)
                OutcomeStrip(Outcomes(hit = 27, stopped = 7, expired = 2, open = 2))
                LiveDot(live = true)
                Skeleton(Modifier.size(100.dp))
            }
        }
        compose.onNodeWithContentDescription("82% of the way to target 2").assertExists()
        compose.onNodeWithContentDescription("121 up, 34 flat, 68 down").assertExists()
        compose.onNodeWithContentDescription("27 hit, 7 stopped, 2 expired, 2 open").assertExists()
        compose.onNodeWithContentDescription("Loading").assertExists()
    }

    @Test
    fun `the ticker tape names every stock it scrolls`() {
        content {
            TickerTape(listOf(TapeQuote("COMI", "82.40", 2.1), TapeQuote("ABUK", "66.10", -1.9)))
        }
        compose.onNodeWithContentDescription("Prices: COMI 82.40, ABUK 66.10").assertExists()
    }

    @Test
    fun `a sheet on the cover screen shows its content`() {
        content {
            KitSheet(onDismiss = {}, posture = com.ikverse.egxanalyzer.model.FoldPosture.Cover, title = "Record the purchase") {
                Text("Entry price")
            }
        }
        compose.onNodeWithText("Entry price").assertExists()
    }

    @Test
    fun `a sheet unfolded opens as a panel and shows its content`() {
        content {
            KitSheet(onDismiss = {}, posture = com.ikverse.egxanalyzer.model.FoldPosture.Unfolded, title = "COMI call") {
                Text("I bought it")
            }
        }
        compose.onNodeWithText("I bought it").assertExists()
    }

    /** A tile popping in is still in the tree from its first frame, so a screen reader finds it. */
    @Test
    fun `a tile that pops in is there from the first frame`() {
        compose.mainClock.autoAdvance = false
        content {
            Tile(tone = TileTone.HIGHLIGHT, modifier = Modifier.popIn(3)) { Text("Hit rate") }
        }
        compose.onNodeWithText("Hit rate").assertExists()
    }

    @Test
    fun `a hold opens the actions`() {
        content {
            HoldMenu(items = listOf(MenuItem("Copy the call") {}), onTap = {}) { Text("A held row") }
        }
        compose.onNodeWithText("A held row").performTouchInput { longClick() }
        compose.onNodeWithText("Copy the call").assertIsDisplayed()
    }

    @Test
    fun `a tap opens the thing and no menu`() {
        var tapped = false
        content {
            HoldMenu(items = listOf(MenuItem("Copy the call") {}), onTap = { tapped = true }) { Text("A tapped row") }
        }
        compose.onNodeWithText("A tapped row").performClick()
        assertTrue(tapped)
        compose.onNodeWithText("Copy the call").assertDoesNotExist()
    }

    /** 5.1.1's dark mode drew every bare name black on graphite: nothing set a default text colour. */
    @Test
    fun `text outside a tile takes the palette's ink in the dark theme`() {
        var seen = androidx.compose.ui.graphics.Color.Unspecified
        var ink = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) {
                seen = androidx.compose.material3.LocalContentColor.current
                ink = com.ikverse.egxanalyzer.ui.theme.palette.ink
            }
        }
        compose.waitForIdle()
        assertEquals(ink, seen)
        assertTrue("ink should be light at night", ink.luminance() > 0.5f)
    }

    /**
     * 5.1.4 drew every tag's word in the page ink, which is light at night, so tags on the lilac
     * tile - which stays bright in the dark - lost their words. A tag takes the ink of what it sits on.
     */
    @Test
    fun `a tag on a bright tile in the dark takes the tile's ink`() {
        var onTile = androidx.compose.ui.graphics.Color.Unspecified
        var tileInk = androidx.compose.ui.graphics.Color.Unspecified
        var tileFill = androidx.compose.ui.graphics.Color.Unspecified
        var pageInk = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.DARK) {
                pageInk = com.ikverse.egxanalyzer.ui.theme.palette.ink
                tileInk = tileColors(TileTone.TILE2).second
                tileFill = tileColors(TileTone.TILE2).first
                Tile(tone = TileTone.TILE2) {
                    // What KitTag's `ink` defaults to, read where the tag would be drawn.
                    onTile = androidx.compose.material3.LocalContentColor.current
                    KitTag("Target 1 reached", tone = com.ikverse.egxanalyzer.ui.theme.palette.gain)
                }
            }
        }
        compose.waitForIdle()
        assertEquals(tileInk, onTile)
        assertTrue("the tile's ink should differ from the page's at night", tileInk != pageInk)
        // Since 2026-11-12 a dark tile is a deep fill with a pale text of its own hue.
        assertTrue("pale text on the deep lilac", tileInk.luminance() > tileFill.luminance())
        compose.onNodeWithText("Target 1 reached").assertIsDisplayed()
    }
}
