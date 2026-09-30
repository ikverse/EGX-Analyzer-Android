package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.SavedAnalysis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Two dates, and they disagree.
 *
 * A report is about one session but was run at another moment, sometimes days later. Sorting by
 * one order and reading the other is how a stale report gets mistaken for the newest one, which is
 * the whole reason the order is a visible choice rather than a fixed rule.
 */
class RunOrderTest {

    private fun run(id: Long, target: String?, ranAt: String) = SavedAnalysis(
        id = id,
        provider = CloudProvider.QWEN,
        model = "qwen3.7-plus",
        result = AnalysisResult(
            requestId = "run-$id",
            recommendations = emptyList(),
            inquiryReplyCount = 0,
            recommendationTargetDate = target?.let(LocalDate::parse),
            completedAt = Instant.parse(ranAt),
        ),
    )

    /** A late rerun of an old session: newest by run, oldest by target. */
    private val late = run(1, target = "2026-07-28", ranAt = "2026-08-03T10:00:00Z")
    private val recent = run(2, target = "2026-08-02", ranAt = "2026-08-02T14:35:00Z")
    private val undated = run(3, target = null, ranAt = "2026-08-01T09:00:00Z")

    private fun order(order: RunOrder) =
        listOf(recent, undated, late).sortedWith(order.comparator).map(SavedAnalysis::id)

    @Test
    fun `run date orders by when the analysis actually ran`() {
        assertEquals(listOf(1L, 2L, 3L), order(RunOrder.RUN_NEWEST))
        assertEquals(listOf(3L, 2L, 1L), order(RunOrder.RUN_OLDEST))
    }

    @Test
    fun `target date orders by the session the report is about`() {
        assertEquals(listOf(2L, 1L), order(RunOrder.TARGET_NEWEST).take(2))
        assertEquals(listOf(1L, 2L), order(RunOrder.TARGET_OLDEST).take(2))
    }

    /**
     * A run with no target date has nothing to sort on, so it goes last - and stays in the same
     * place whichever way the arrow points, rather than appearing to move for no stated reason.
     */
    @Test
    fun `undated runs sit at the end of both target orders`() {
        assertEquals(3L, order(RunOrder.TARGET_NEWEST).last())
        assertEquals(3L, order(RunOrder.TARGET_OLDEST).last())
    }

    @Test
    fun `undated runs keep the newest run first among themselves`() {
        val older = run(4, target = null, ranAt = "2026-07-20T09:00:00Z")
        val ordering = listOf(older, undated).sortedWith(RunOrder.TARGET_OLDEST.comparator)

        assertEquals(listOf(3L, 4L), ordering.map(SavedAnalysis::id))
    }
}
