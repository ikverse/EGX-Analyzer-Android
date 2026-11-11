package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.model.Portfolio
import com.ikverse.egxanalyzer.model.PortfolioGroup
import com.ikverse.egxanalyzer.model.PortfolioStats
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.model.TradeRowGroup
import com.ikverse.egxanalyzer.model.inTableOrder
import com.ikverse.egxanalyzer.model.levelBar
import com.ikverse.egxanalyzer.model.tableGroup
import com.ikverse.egxanalyzer.model.tableTimeWord
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The unfolded Portfolio's table, as rebuilt on 2026-11-12: every row prints what was paid, where
 * the price is, the stop and the far target; a trade kept open past its stop says when the stop
 * broke rather than "0 trading days left"; and nothing in a row is cut off at the Fold's width or
 * the tablet's.
 *
 * Native graphics, so the text is measured with the app's own fonts and "cut off" means something.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TradesTableTest {

    @get:Rule
    val compose = createComposeRule()

    private val called = LocalDate.of(2026, 10, 20)

    private fun open(ticker: String, entry: Double, now: Double, stop: Double, t1: Double, t2: Double) = PositionView(
        position = Position(
            ticker = ticker, recommendationDate = called, entryPrice = entry, entryDate = called,
            stopLoss = stop, target1 = t1, target2 = t2, channel = "Tadawul Calls",
        ),
        status = PositionStatus.OPEN, marketStatus = PositionStatus.OPEN, open = true,
        currentPrice = now, exitPrice = now, realized = false,
        returnPct = (now - entry) / entry * 100, sessionsElapsed = 4, sessionsHeld = 4, sessionsRemaining = 6,
        deadlineDate = null, settledOn = null, ranOutOfTime = false, overdueDays = 0,
    )

    private val running = open("TMGH", 82.2, 85.7, 78.0, 88.0, 93.0)

    /** Kept open after its stop broke on 5 November: the window is over, the trade is not. */
    private val stopped = open("SWDY", 135.11, 121.46, 128.0, 143.0, 150.0).copy(
        status = PositionStatus.STOPPED_OUT, marketStatus = PositionStatus.STOPPED_OUT,
        sessionsRemaining = 0, settledOn = LocalDate.of(2026, 11, 5), stoppedOn = LocalDate.of(2026, 11, 5),
        position = open("SWDY", 135.11, 121.46, 128.0, 143.0, 150.0).position.copy(keepOpen = true),
    )

    /** Kept open 52 days past a deadline that ran out with no level reached. */
    private val late = open("PRCL", 33.25, 32.1, 31.0, 36.5, 39.0).copy(
        sessionsRemaining = 0, ranOutOfTime = true, overdueDays = 52,
        position = open("PRCL", 33.25, 32.1, 31.0, 36.5, 39.0).position.copy(keepOpen = true),
    )

    // Deliberately out of the table's order, which the table has to put right.
    private val trades = listOf(late, stopped, running)

    private fun show() {
        val state = FakeAppState(
            portfolio = Portfolio(
                groups = listOf(PortfolioGroup(called, trades)),
                stats = PortfolioStats(total = 3, openCount = 3, openReturnPct = -3.0, winRate = 50.0),
            ),
        )
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalFoldPosture provides FoldPosture.Unfolded) {
                    PortfolioScreen(state)
                }
            }
        }
    }

    /** True where any text matching [matcher] was laid out with part of it clipped or ellipsised. */
    private fun overflows(matcher: SemanticsMatcher): Boolean =
        compose.onAllNodes(matcher).fetchSemanticsNodes().any { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            // Not `hasVisualOverflow`: that is also true of a short text laid out in a wider slot.
            results.any { layout ->
                layout.multiParagraph.didExceedMaxLines ||
                    (0 until layout.lineCount).any { layout.isLineEllipsized(it) || layout.getLineRight(it) > layout.layoutInput.constraints.maxWidth }
            }
        }

    private fun assertTheTableReads() {
        // Paid and now, in their own column, for every trade.
        listOf("82.2", "85.7", "135.11", "121.46", "33.25", "32.1").forEach {
            assertTrue("$it is missing", compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty())
        }
        // The stop and the far target, under the ends of each bar.
        listOf("78", "93", "150", "31", "39").forEach {
            assertTrue("$it is missing", compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty())
        }
        // Below the stop, the stop's price points out of the bar.
        compose.onNodeWithText("◀ 128").assertExists()
        assertEquals(3, compose.onAllNodesWithContentDescription("Stop ", substring = true).fetchSemanticsNodes().size)

        // What each trade is waiting for, and for how long.
        compose.onNodeWithText("Stop hit, sell").assertExists()
        compose.onNodeWithText("hit 5 Nov").assertExists()
        compose.onNodeWithText("52d late").assertExists()
        compose.onNodeWithText("6 days left").assertExists()
        assertTrue(compose.onAllNodesWithText("trading days", substring = true).fetchSemanticsNodes().isEmpty())

        // The three groups are named.
        compose.onNodeWithText("Waiting for you to sell").assertExists()
        assertEquals(2, compose.onAllNodesWithText("Past deadline").fetchSemanticsNodes().size)

        // Nothing in a row is cut off.
        listOf(
            "TMGH", "SWDY", "PRCL", "Stop hit, sell", "Past deadline", "Running", "hit 5 Nov", "52d late",
            "6 days left", "135.11", "121.46", "◀ 128", "150", "Stop → target", "Made",
        ).forEach { assertFalse("$it is cut off", overflows(hasText(it))) }
    }

    @Test
    @Config(qualifiers = "w750dp-h891dp")
    fun `the unfolded Fold shows every figure whole`() {
        show()
        assertTheTableReads()
    }

    @Test
    @Config(qualifiers = "w818dp-h1245dp")
    fun `the tablet shows every figure whole`() {
        show()
        assertTheTableReads()
    }

    @Test
    fun `open trades run from running to waiting for a sale to past deadline`() {
        assertEquals(listOf("TMGH", "SWDY", "PRCL"), trades.inTableOrder().map { it.ticker })
        assertEquals(TradeRowGroup.TO_SELL, stopped.tableGroup())
        assertEquals(TradeRowGroup.PAST_DEADLINE, late.tableGroup())
    }

    @Test
    fun `a finished trade says how long it took`() {
        val done = running.copy(status = PositionStatus.FULL_TARGET_HIT, marketStatus = PositionStatus.FULL_TARGET_HIT, open = false)
        assertEquals(TradeRowGroup.FINISHED, done.tableGroup())
        assertEquals("in 4 days", done.tableTimeWord { it.toString() })
    }

    @Test
    fun `the bar is centred on what was paid and each side is its own scale`() {
        // Half way to the far target, with target 1 a third of the way there.
        val up = levelBar(paid = 100.0, now = 115.0, stop = 95.0, target1 = 110.0, target2 = 130.0)
        assertEquals(0.5f, up.fill, 0.001f)
        assertEquals(1f / 3f, up.target1At!!, 0.001f)
        assertFalse(up.pastFar)

        // Three fifths of the way down to the stop.
        assertEquals(-0.6f, levelBar(100.0, 97.0, 95.0, 110.0, 130.0).fill, 0.001f)

        // Past either end the side is full and says so.
        val below = levelBar(100.0, 80.0, 95.0, 110.0, 130.0)
        assertEquals(-1f, below.fill, 0.001f)
        assertTrue(below.pastStop)
        assertTrue(levelBar(100.0, 140.0, 95.0, 110.0, 130.0).pastFar)

        // One target: it is the far end and there is no notch. No price: an empty bar.
        val single = levelBar(100.0, null, 95.0, 110.0, null)
        assertEquals(110.0, single.far!!, 0.0)
        assertNull(single.target1At)
        assertEquals(0f, single.fill, 0f)

        // A stop printed above the entry is not a stop to measure a loss against.
        assertNull(levelBar(100.0, 90.0, 105.0, 110.0, null).stop)
    }
}
