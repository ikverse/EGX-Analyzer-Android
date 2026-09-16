package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

/**
 * Every reading of one session, as a deck.
 *
 * Two properties, and both of them fail silently on a device rather than in a build.
 *
 * **The cards behind have to be composed at all.** A pager composes what is in its viewport, and
 * every card in this deck is laid out a whole page away and dragged back under the front one by its
 * own layer - so left to its defaults the pager composes exactly one page and the deck is a single
 * card with nothing behind it. Nothing about that reads as a fault: the row draws, the dots draw,
 * and the reader is simply never shown that the session was read twice. That is the shape the
 * original peek shipped in.
 *
 * **A card behind is brought forward and never opened.** The press that used to land on the strip
 * below the front card opened whichever reading was under the thumb, which is never the one the
 * reader can see.
 */
@RunWith(RobolectricTestRunner::class)
class SavedRunStackTest {

    @get:Rule
    val compose = createComposeRule()

    private fun run(id: Long) = SavedAnalysis(
        id = id,
        provider = CloudProvider.QWEN,
        model = "qwen3.7-plus",
        result = AnalysisResult(
            requestId = "run-$id",
            recommendations = emptyList(),
            inquiryReplyCount = 0,
            recommendationTargetDate = LocalDate.parse("2026-08-31"),
            completedAt = Instant.parse("2026-08-31T09:00:00Z"),
        ),
    )

    /** What each reading was handed this composition: null means "a press opens this one". */
    private val presses = mutableMapOf<Long, (() -> Unit)?>()

    private fun stack(runs: List<SavedAnalysis>, openRunId: Long? = null) {
        compose.setContent {
            EgxAnalyzerTheme {
                SavedRunStack(runs = runs, openRunId = openRunId) { saved, _, _, bringForward, mod ->
                    presses[saved.id] = bringForward
                    // Stands in for the report card: the deck's geometry is about heights and
                    // presses, and a real card would drag a whole AppState in to prove neither.
                    Text("run ${saved.id}", mod.fillMaxWidth().height(120.dp))
                }
            }
        }
        compose.waitForIdle()
    }

    /** The one that has to be true before any of the rest of it can be seen. */
    @Test
    fun `every card of the deck is composed, not only the one in front`() {
        stack(listOf(run(1), run(2), run(3)))

        assertEquals(setOf(1L, 2L, 3L), presses.keys)
    }

    @Test
    fun `the card in front is opened and the ones behind come forward`() {
        stack(listOf(run(1), run(2), run(3)))

        assertNull("the reading in front opens its report", presses[1])
        assertNotNull("a reading behind comes forward instead", presses[2])
        assertNotNull(presses[3])
    }

    @Test
    fun `pressing a card behind makes it the one in front`() {
        stack(listOf(run(1), run(2), run(3)))

        compose.runOnUiThread { presses[2]?.invoke() }
        compose.waitForIdle()

        assertNotNull("the reading it left is behind it now", presses[1])
        assertNull("the one that was pressed is the one a press now opens", presses[2])
    }

    /**
     * A day read once is the day most days are, and it keeps the card it always had: no deck, no
     * room held below it, and a press that opens the report rather than fetching a card that is
     * not there.
     */
    @Test
    fun `a single reading is drawn as one card and opens on a press`() {
        stack(listOf(run(1)))

        assertEquals(setOf(1L), presses.keys)
        assertNull(presses[1])
    }

    /** Nothing in a deck is pressable behind an open report; closing it is what puts it back. */
    @Test
    fun `an open report leaves nothing behind it to press`() {
        stack(listOf(run(1), run(2)), openRunId = 1L)

        assertNull(presses[1])
        assertNull(presses[2])
    }
}
