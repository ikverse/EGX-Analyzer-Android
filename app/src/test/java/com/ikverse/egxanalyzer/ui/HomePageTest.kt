package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ikverse.egxanalyzer.model.DatedClose
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.model.Headline
import com.ikverse.egxanalyzer.model.IndexQuote
import com.ikverse.egxanalyzer.model.MarketBreadth
import com.ikverse.egxanalyzer.model.MarketBrief
import com.ikverse.egxanalyzer.model.MarketMovers
import com.ikverse.egxanalyzer.model.NeedReason
import com.ikverse.egxanalyzer.model.NeedsYouItem
import com.ikverse.egxanalyzer.model.Mover
import com.ikverse.egxanalyzer.model.OutsideQuote
import com.ikverse.egxanalyzer.model.Portfolio
import com.ikverse.egxanalyzer.model.PortfolioGroup
import com.ikverse.egxanalyzer.model.PortfolioStats
import com.ikverse.egxanalyzer.model.Position
import com.ikverse.egxanalyzer.model.PositionStatus
import com.ikverse.egxanalyzer.model.PositionView
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Home page at the four ways the Fold is held: the indices, the book, the market, the news and
 * the paid button that must not spend by itself. One `setContent` per test.
 */
@RunWith(RobolectricTestRunner::class)
class HomePageTest {

    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 11)

    private fun index(key: String, name: String, level: Double) = IndexQuote(
        key, name, level, level * 0.99,
        (10 downTo 0).map { DatedClose(day.minusDays(it.toLong()), level - it) },
    )

    private val indices = listOf(
        index("EGX30", "EGX30", 52_500.5),
        index("EGX70", "EGX70 EWI", 18_447.2),
        index("EGX100", "EGX100 EWI", 24_459.9),
        index("SHARIAH", "Shariah 33", 6_310.9),
    )

    private fun open(ticker: String, entry: Double, now: Double, stop: Double, t2: Double) = PositionView(
        position = Position(
            ticker = ticker, recommendationDate = day, entryPrice = entry, entryDate = day,
            stopLoss = stop, target1 = t2 - 1, target2 = t2, channel = "Tadawul Calls",
        ),
        status = PositionStatus.OPEN, marketStatus = PositionStatus.OPEN, open = true,
        currentPrice = now, exitPrice = now, realized = false,
        returnPct = (now - entry) / entry * 100, sessionsElapsed = 4, sessionsHeld = 4, sessionsRemaining = 6,
        deadlineDate = null, settledOn = null, ranOutOfTime = false, overdueDays = 0,
    )

    // 1.2% above its stop, inside the default warning distance.
    private val abuk = open("ABUK", 68.0, 65.5, 64.7, 78.0)

    private fun portfolio(openReturn: Double?) = Portfolio(
        groups = listOf(PortfolioGroup(day, listOf(abuk))),
        stats = PortfolioStats(total = 1, openCount = 1, openReturnPct = openReturn),
    )

    private val arabic = Headline("البورصة تغلق على ارتفاع", "اليوم السابع", "https://example.com/ar", 0L, true)
    private val english = Headline("EGX30 closes higher", "Enterprise", "https://example.com/en", 0L, false)

    private fun state(openReturn: Double? = 4.2, brief: MarketBrief? = null) = FakeAppState(
        portfolio = portfolio(openReturn),
        homeIndices = indices,
        homeOutside = listOf(
            OutsideQuote("USDEGP", "USD / EGP", 53.5, 53.4),
            OutsideQuote("GOLD", "Gold · oz", 3_993.8, 3_980.0),
            OutsideQuote("BRENT", "Brent", 92.6, 93.7),
        ),
        homeMovers = MarketMovers(
            day,
            gainers = listOf(Mover("ORHD", "Orascom Development", 20.0, 9.8)),
            losers = listOf(Mover("ISPH", "Ibnsina Pharma", 5.0, -6.2)),
        ),
        marketBreadth = MarketBreadth(day, 128, 31, 64),
        homeHeadlines = listOf(arabic, english),
        homeBrief = brief,
    )

    private fun show(posture: FoldPosture, state: AppState = state(), onStock: (String) -> Unit = {}) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalFoldPosture provides posture, LocalOpenStock provides onStock) {
                    HomeScreen(state)
                }
            }
        }
    }

    /** Every block of the page, by one thing each of them prints. */
    private fun assertEveryBlock() {
        listOf(
            "52,500.5", "EGX70 EWI", "EGX100 EWI", "Shariah 33", "Your book", "Needs a look",
            "128↑  31·  64↓", "Top gainers", "Top losers", "USD / EGP", "Brent", "News",
            "EGX30 closes higher", "Ask AI for a market brief",
        ).forEach { compose.onNodeWithText(it, substring = true).assertExists() }
    }

    /**
     * The 2026-11-12 report: on the cover screen the tile is half the width, "1.2% above stop" took
     * nearly all of the row and NIPH was drawn one letter per line. Each trade is two lines now;
     * this holds the ticker to one, at a width narrower than the tile ever is.
     */
    @Test
    fun `a ticker on the needs a look tile stays on one line at half the cover screen`() {
        val needs = listOf(
            NeedsYouItem(open("PRCL", 10.0, 9.0, 8.0, 14.0), NeedReason.OVERDUE),
            NeedsYouItem(open("NIPH", 30.0, 28.0, 27.67, 40.0), NeedReason.NEAR_STOP, 1.2),
        )
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.width(160.dp)) { NeedsLookTile(needs, onOpen = {}) }
            }
        }
        compose.onNodeWithText("1.2% above stop", substring = true, useUnmergedTree = true).assertExists()
        // Measured against the tile's own title, the same style on one line, so the bar is whatever
        // one line is under this font rather than a number guessed for it.
        val oneLine = compose.onNodeWithText("Needs a look", useUnmergedTree = true).getUnclippedBoundsInRoot().height
        listOf("PRCL", "NIPH").forEach { ticker ->
            val height = compose.onNodeWithText(ticker, useUnmergedTree = true).getUnclippedBoundsInRoot().height
            assertTrue("$ticker is $height tall against $oneLine for one line", height < oneLine * 1.5f)
        }
    }

    @Test
    fun `the cover screen draws every block in one column`() {
        show(FoldPosture.Cover)
        assertEveryBlock()
    }

    @Test
    @Config(qualifiers = "w750dp-h891dp")
    fun `unfolded draws every block across two columns`() {
        show(FoldPosture.Unfolded)
        assertEveryBlock()
    }

    @Test
    @Config(qualifiers = "w750dp-h891dp")
    fun `book posture draws every block either side of the hinge`() {
        show(FoldPosture.Book(0.49f, 0.51f))
        assertEveryBlock()
    }

    @Test
    @Config(qualifiers = "w750dp-h891dp")
    fun `tabletop draws every block over the two halves`() {
        show(FoldPosture.Tabletop(0.5f, 0.51f))
        assertEveryBlock()
    }

    @Test
    fun `the book tile's tone follows the sign of its figure`() {
        // The rule the tile is built on, held where it is chosen rather than by reading pixels.
        fun tone(openReturn: Double?) = when (com.ikverse.egxanalyzer.model.bookMood(openReturn)) {
            com.ikverse.egxanalyzer.model.BookMood.UP -> TileTone.BOOK_UP
            com.ikverse.egxanalyzer.model.BookMood.FLAT -> TileTone.BOOK_FLAT
            com.ikverse.egxanalyzer.model.BookMood.DOWN -> TileTone.BOOK_DOWN
        }
        assertEquals(TileTone.BOOK_UP, tone(4.2))
        assertEquals(TileTone.BOOK_FLAT, tone(0.0))
        assertEquals(TileTone.BOOK_DOWN, tone(-3.1))
        show(FoldPosture.Cover, state(openReturn = -3.1))
        compose.onNodeWithText("-3.1%", substring = true).assertExists()
        compose.onNodeWithText("average per open trade · 1 open").assertExists()
    }

    @Test
    fun `the book tile goes to Portfolio and a trade that needs a look opens that trade`() {
        val went = mutableListOf<AppDestination>()
        val opened = mutableListOf<String>()
        val base = state()
        val state = object : AppState by base {
            override fun navigate(destination: AppDestination) { went += destination }
            override fun openPosition(id: String, returnTo: NavStop?) { opened += id }
        }
        show(FoldPosture.Cover, state)
        compose.onNodeWithText("Your book").performScrollTo().performClick()
        compose.onNodeWithText("ABUK").performScrollTo().performClick()
        assertEquals(listOf(AppDestination.PORTFOLIO), went)
        assertEquals(listOf(abuk.position.id), opened)
    }

    @Test
    fun `a mover opens its stock and a headline opens its article`() {
        val stocks = mutableListOf<String>()
        val articles = mutableListOf<Headline>()
        val base = state()
        val state = object : AppState by base {
            override fun openHeadline(headline: Headline) { articles += headline }
        }
        show(FoldPosture.Cover, state, onStock = { stocks += it })
        compose.onNodeWithText("ORHD").performScrollTo().performClick()
        compose.onNodeWithText("البورصة تغلق على ارتفاع").performScrollTo().performClick()
        assertEquals(listOf("ORHD"), stocks)
        assertEquals(listOf(arabic), articles)
    }

    @Test
    fun `the brief is asked for only through the confirmation`() {
        var asked = 0
        val base = state()
        val state = object : AppState by base {
            override fun askMarketBrief() { asked++ }
        }
        show(FoldPosture.Cover, state)
        compose.onNodeWithText("Ask AI for a market brief").performScrollTo().performClick()
        compose.waitForIdle()
        // The button has only opened the question. Nothing has been sent and nothing spent.
        assertEquals(0, asked)
        compose.onNodeWithText("uses credits", substring = true).assertExists()
        compose.onNodeWithText("Ask").performClick()
        compose.waitForIdle()
        assertEquals(1, asked)
    }

    @Test
    fun `a brief already kept is drawn above the button that asks for a new one`() {
        show(FoldPosture.Cover, state(brief = MarketBrief("The market rose.", listOf("Banks led."), listOf("The dollar."), "model-x", 0L)))
        compose.onNodeWithText("The market rose.").assertExists()
        compose.onNodeWithText("Ask AI for a new brief").assertExists()
    }

    @Test
    fun `the brief opens and closes on a press, shut to its summary`() {
        show(FoldPosture.Cover, state(brief = MarketBrief("The market rose.", listOf("Banks led."), listOf("The dollar."), "model-x", 0L)))
        compose.onNodeWithText("The market rose.").assertExists()
        compose.onNodeWithText("• Banks led.").assertDoesNotExist()
        compose.onNodeWithText("AI market brief").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("• Banks led.").assertExists()
        compose.onNodeWithText("• The dollar.").assertExists()
        compose.onNodeWithText("AI market brief").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("• Banks led.").assertDoesNotExist()
    }

    @Test
    fun `the news refresh fetches the headlines and nothing else`() {
        val news = mutableListOf<Boolean>()
        var market = 0
        var briefs = 0
        val base = state()
        val state = object : AppState by base {
            override fun refreshHomeNews(force: Boolean) { news += force }
            override fun refreshHomeMarket(force: Boolean) { market++ }
            override fun askMarketBrief() { briefs++ }
        }
        show(FoldPosture.Cover, state)
        compose.waitForIdle()
        // Arriving on the page asks once, unforced; only the button's press forces.
        news.clear()
        market = 0
        compose.onNodeWithContentDescription("Refresh news").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(true), news)
        assertEquals(0, market)
        assertEquals(0, briefs)
    }

    @Test
    fun `an empty page says how to fill it and still stands up`() {
        show(FoldPosture.Cover, FakeAppState())
        compose.onNodeWithText("Pull down to fetch the market.").assertExists()
        compose.onNodeWithText("No trades recorded yet").assertExists()
        compose.onNodeWithText("No headlines yet. Pull down to fetch them.").assertExists()
    }

    @Test
    fun `the three index tiles are on the row at every width a column of this page is drawn at`() {
        // One `setContent` and a width that moves. The narrowest is half of the unfolded Fold's
        // page, which is where three tiles side by side have the least room.
        val widths = listOf(304, 379, 614, 682, 715)
        var width by mutableIntStateOf(widths.first())
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.width(width.dp)) { IndexRow(indices.drop(1)) }
            }
        }
        widths.forEach { at ->
            width = at
            compose.waitForIdle()
            listOf("18,447.2", "24,459.9", "6,310.9").forEach { level ->
                val node = compose.onNodeWithText(level).fetchSemanticsNode()
                assertTrue("$level is cut off at ${at}dp", node.boundsInRoot.width > 0f && node.boundsInRoot.right <= with(compose.density) { at.dp.toPx() } + 1f)
            }
        }
    }
}
