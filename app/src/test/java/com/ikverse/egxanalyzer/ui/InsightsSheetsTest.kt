package com.ikverse.egxanalyzer.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ikverse.egxanalyzer.model.CallSanity
import com.ikverse.egxanalyzer.model.IndexPoint
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.ReturnPoint
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.StockOpinion
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Ask AI answer sheet, and the EGX30 line's placement on the Portfolio chart. The Ask again
 * press is recorded by the test, never sent anywhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class InsightsSheetsTest {

    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.parse("2026-11-09")

    private val call = ScoredCall(
        ticker = "COMI", companyEnglish = "Commercial International Bank", companyArabic = null,
        channel = "Tadawul Calls", channelId = null, openedOn = day, entryLow = 80.0, entryHigh = 82.0,
        target1 = 84.0, target2 = 86.5, stopLoss = 72.0, outcome = Outcome.OPEN, settledOn = null,
        peakHigh = 83.0, troughLow = 79.0, returnPct = 1.0, sessionsElapsed = 2, repeatOf = null,
        faults = CallSanity.faults(80.0, 82.0, 84.0, 86.5, 72.0, session = null),
    )

    private val opinion = StockOpinion(
        verdict = StockOpinion.Verdict.WAIT,
        horizon = StockOpinion.Horizon.SHORT,
        confidence = StockOpinion.Confidence.MEDIUM,
        headline = "الزخم قائم فوق ٨٠",
        outlook = "تذبذب بين ٨٠ و ٨٦",
        onTheCall = StockOpinion.CallView(stance = StockOpinion.Stance.SOUND, detail = "المستويات سليمة"),
        risks = listOf("رفع الفائدة يضغط على البنوك"),
        catalysts = listOf(StockOpinion.Catalyst(what = "اجتماع المركزي", on = "20 Nov", source = "")),
        unknowns = listOf("بيانات التداول اللحظية"),
        newsWindowDays = 14,
        model = "gemini-2.5-pro",
        askedOn = day,
        searched = true,
    )

    @Test
    fun `the answer sheet lays out every section and Ask again is only recorded`() {
        var asked = 0
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                StockOpinionSheet(call, opinion, onAskAgain = { asked++ }, onDismiss = {})
            }
        }
        compose.onNodeWithText("COMI").assertExists()
        compose.onNodeWithText("THE RECOMMENDATION").assertExists()
        compose.onNodeWithText("WHAT IT FOUND · LAST 14 DAYS").performScrollTo().assertExists()
        compose.onNodeWithText("WHAT IS COMING").performScrollTo().assertExists()
        compose.onNodeWithText("WHAT GOES WRONG").assertExists()
        compose.onNodeWithText("WHAT IT COULD NOT SEE").performScrollTo().assertExists()
        compose.onNodeWithText("Ask again").performScrollTo().performClick()
        assertEquals(1, asked)
    }

    @Test
    fun `the market line starts on the trades' first point and moves by the index`() {
        val shown = listOf(
            ReturnPoint(day, 2.0, 1),
            ReturnPoint(day.plusDays(1), 3.0, 1),
            ReturnPoint(day.plusDays(2), 4.0, 1),
        )
        val index = listOf(IndexPoint(day, 5.0), IndexPoint(day.plusDays(2), 15.5))
        val line = marketAlong(shown, index)
        assertEquals(listOf(0, 2), line.map { it.first })
        assertEquals(2.0, line[0].second, 1e-9)
        // 1.155 / 1.05 = 1.10, so ten points over the trades' starting 2%.
        assertEquals(12.0, line[1].second, 1e-9)
    }

    @Test
    fun `no index, no market line`() {
        assertEquals(emptyList<Pair<Int, Double>>(), marketAlong(listOf(ReturnPoint(day, 1.0, 1), ReturnPoint(day.plusDays(1), 2.0, 1)), emptyList()))
    }
}
