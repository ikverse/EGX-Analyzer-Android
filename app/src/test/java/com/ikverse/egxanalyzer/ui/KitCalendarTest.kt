package com.ikverse.egxanalyzer.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ikverse.egxanalyzer.model.CallSanity
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.kit.KitCalendarSheet
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The kit's calendar, which replaced all three date pickers, and the Ask AI confirmation, which
 * must still stand between a press and a paid request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class KitCalendarTest {

    @get:Rule
    val compose = createComposeRule()

    private val nov = { day: Int -> LocalDate.of(2026, 11, day) }

    @Test
    fun `a day outside the allowed set cannot be picked and an allowed one can`() {
        val picked = mutableListOf<LocalDate>()
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                KitCalendarSheet(
                    title = "Pick a date",
                    selected = nov(5),
                    onPick = { picked += it },
                    onDismiss = {},
                    posture = FoldPosture.Cover,
                    allowed = { it == nov(3) || it == nov(5) },
                    earliest = nov(3),
                    latest = nov(5),
                )
            }
        }
        compose.onNodeWithText("November 2026").assertExists()
        compose.onNodeWithText("4").performClick()
        assertEquals(emptyList<LocalDate>(), picked)
        compose.onNodeWithText("3").performClick()
        assertEquals(listOf(nov(3)), picked)
    }

    @Test
    fun `the arrows stop at the last month there is anything to pick in`() {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                KitCalendarSheet(
                    title = "Calls for which session",
                    selected = nov(10),
                    onPick = {},
                    onDismiss = {},
                    posture = FoldPosture.Cover,
                    allowed = { it <= nov(10) },
                    latest = nov(10),
                )
            }
        }
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithText("November 2026").assertExists()
        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithText("October 2026").assertExists()
    }

    private val call = ScoredCall(
        ticker = "COMI", companyEnglish = null, companyArabic = null, channel = "Tadawul Calls", channelId = null,
        openedOn = nov(9), entryLow = 80.0, entryHigh = 82.0, target1 = 84.0, target2 = 86.5, stopLoss = 72.0,
        outcome = Outcome.OPEN, settledOn = null, peakHigh = 83.0, troughLow = 79.0, returnPct = 1.0,
        sessionsElapsed = 2, repeatOf = null,
        faults = CallSanity.faults(80.0, 82.0, 84.0, 86.5, 72.0, session = null),
    )

    @Test
    fun `Ask AI still asks first, and Cancel spends nothing`() {
        var asked = 0
        var dismissed = 0
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                AskAiDialog(
                    call = call,
                    model = "gemini-2.5-pro",
                    searching = true,
                    newsWindowDays = 14,
                    onConfirm = { asked++ },
                    onDismiss = { dismissed++ },
                )
            }
        }
        compose.onNodeWithText("Ask about COMI?").assertExists()
        compose.onNodeWithText("gemini-2.5-pro · checks news from the last 14 days").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, asked)
        assertEquals(1, dismissed)
        compose.onNodeWithText("Ask").performClick()
        assertEquals(1, asked)
    }
}
