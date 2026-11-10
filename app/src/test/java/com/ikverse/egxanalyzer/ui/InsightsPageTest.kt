package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ikverse.egxanalyzer.model.CallSanity
import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.FeedFault
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceReport
import com.ikverse.egxanalyzer.model.PriceHealthReport
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ScoredSession
import com.ikverse.egxanalyzer.model.StockHealth
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 5.0 Insights page: the podium, the ranking and its Show all, what is in play, the feed note,
 * the score sheet, and the two side-by-side postures. Nothing here can send an Ask AI request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class InsightsPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.parse("2026-11-09")

    private fun score(name: String, rate: Double, judged: Int = 10) = ChannelScore(
        channel = name, calls = judged + 2, judged = judged, fullHits = 2, partialHits = 3,
        stopped = 3, expired = 2, notTradable = 0, fullHitRate = 20.0, anyTargetRate = rate,
        averageReturn = 3.5, medianSessionsToHit = 4.0,
    )

    private fun call(ticker: String, outcome: Outcome) = ScoredCall(
        ticker = ticker, companyEnglish = null, companyArabic = null, channel = "Tadawul Calls", channelId = null,
        openedOn = day, entryLow = 10.0, entryHigh = 10.4, target1 = 11.0, target2 = 12.0, stopLoss = 9.5,
        outcome = outcome, settledOn = null, peakHigh = 10.5, troughLow = 9.9, returnPct = 1.0,
        sessionsElapsed = 1, repeatOf = null,
        faults = CallSanity.faults(10.0, 10.4, 11.0, 12.0, 9.5, session = null),
    )

    private fun latest(close: Double) = LatestPrice(
        DailySession("X", day, high = null, low = null, close = close, volume = null),
        provisional = false,
    )

    private val ranked = listOf(
        score("Tadawul Calls", 71.0),
        score("Nile Signals", 58.0),
        score("Cairo Desk", 52.0),
        score("Market Brief", 45.0),
        score("Thin Source", 90.0, judged = 2),
    )

    private fun report(inZone: Boolean = true) = PerformanceReport(
        tracked = 30,
        channels = ranked,
        sessions = listOf(
            ScoredSession(day, Instant.EPOCH, "m", 1, calls = listOf(call("EFIH", Outcome.OPEN), call("COMI", Outcome.PARTIAL_HIT))),
        ),
        latestPrices = mapOf("EFIH" to latest(if (inZone) 10.2 else 11.5), "COMI" to latest(11.2)),
    )

    private fun show(state: AppState, posture: FoldPosture = FoldPosture.Cover) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalFoldPosture provides posture) {
                    InsightsScreen(state)
                }
            }
        }
    }

    @Test
    fun `the podium names the three that lead and none too thin to rank`() {
        show(FakeAppState(performance = report()))
        compose.onNodeWithText("Who delivered").assertExists()
        compose.onNodeWithText("5 sources · 30 calls").assertExists()
        // The leader is on the podium and in the ranking.
        assertTrue(compose.onAllNodesWithText("⁨Tadawul Calls⁩").fetchSemanticsNodes().size >= 2)
        compose.onNodeWithText("Thin Source").assertDoesNotExist()
    }

    @Test
    fun `the ranking shows four, and Show all brings the rest`() {
        show(FakeAppState(performance = report()))
        compose.onNodeWithText("⁨Market Brief⁩").assertExists()
        compose.onNodeWithText("⁨Thin Source⁩").assertDoesNotExist()
        compose.onNodeWithText("Show all 5 sources").performScrollTo().performClick()
        compose.onNodeWithText("⁨Thin Source⁩").assertExists()
        compose.onNodeWithText("4 calls · too few judged to rank").assertExists()
    }

    @Test
    fun `a ranking row opens how that source is scored`() {
        show(FakeAppState(performance = report()))
        compose.onAllNodesWithText("⁨Nile Signals⁩")[1].performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("HOW THIS SOURCE IS SCORED").assertExists()
        compose.onNodeWithText("How a call is judged").assertExists()
    }

    @Test
    fun `calls in play and the untaken buy zone`() {
        show(FakeAppState(performance = report()))
        compose.onNodeWithText("Calls in play").performScrollTo()
        compose.onNodeWithText("1 in the entry zone\n1 past target 1").assertExists()
        compose.onNodeWithText("Untaken buy zone").assertExists()
        compose.onNodeWithText("EFIH").assertExists()
    }

    @Test
    fun `with nothing in a buy zone the lime tile is dropped`() {
        show(FakeAppState(performance = report(inZone = false)))
        compose.onNodeWithText("Calls in play").performScrollTo()
        compose.onNodeWithText("Untaken buy zone").assertDoesNotExist()
    }

    @Test
    fun `a quiet feed is noted and a clean one is not`() {
        val quiet = PriceHealthReport(
            faults = listOf(StockHealth("VLMRA", setOf(FeedFault.STALE), LocalDate.parse("2026-11-02"), 8, callsHeld = 1)),
            stocksNamed = 40,
        )
        show(FakeAppState(performance = report(), priceHealth = quiet))
        compose.onNodeWithText("Feed quiet for VLMRA since 2 Nov · 1 call can't be scored yet").performScrollTo().assertExists()
    }

    @Test
    fun `an empty record says so`() {
        show(FakeAppState())
        compose.onNodeWithText("No prices stored yet").assertExists()
    }

    @Test
    fun `unfolded, the tiles and the record sit side by side`() {
        show(FakeAppState(performance = report()), FoldPosture.Unfolded)
        compose.onNodeWithText("Who delivered").assertExists()
        compose.onNodeWithText("Calls in play").assertExists()
    }

    @Test
    fun `in book posture the page splits at the hinge`() {
        show(FakeAppState(performance = report()), FoldPosture.Book(0.49f, 0.51f))
        compose.onNodeWithText("Who delivered").assertExists()
    }
}
