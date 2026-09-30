package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.AnalysisResult
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.SavedAnalysis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Every reading of one session, gathered into one row of the list.
 *
 * A session read again the next morning is a second reading of the same day's calls. Three cards
 * carrying the same date, told apart only by the time in their small print, is what this replaces.
 * What it must never do is move anything: the list is sorted by date, and a stack that reorders the
 * runs inside it is how the newest reading gets mistaken for the second newest.
 */
class GroupRunsByDayTest {

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

    private fun ids(runs: List<SavedAnalysis>) =
        groupRunsByDay(runs).map { day -> day.map(SavedAnalysis::id) }

    @Test
    fun `runs of one session become one row`() {
        val morning = run(1, target = "2026-08-31", ranAt = "2026-08-31T09:12:00Z")
        val night = run(2, target = "2026-08-31", ranAt = "2026-08-30T21:40:00Z")

        assertEquals(listOf(listOf(1L, 2L)), ids(listOf(morning, night)))
    }

    @Test
    fun `different sessions stay in different rows`() {
        val sunday = run(1, target = "2026-08-31", ranAt = "2026-08-31T09:12:00Z")
        val monday = run(2, target = "2026-09-01", ranAt = "2026-08-31T21:00:00Z")

        assertEquals(listOf(listOf(1L), listOf(2L)), ids(listOf(sunday, monday)))
    }

    /** The reason grouping is by target date and not by the day the run happened. */
    @Test
    fun `two sessions read in one evening are two rows`() {
        val sunday = run(1, target = "2026-08-31", ranAt = "2026-08-30T20:00:00Z")
        val monday = run(2, target = "2026-09-01", ranAt = "2026-08-30T20:05:00Z")

        assertEquals(listOf(listOf(1L), listOf(2L)), ids(listOf(sunday, monday)))
    }

    /** A day sits where its first run sat, however far down the list the rest of it was. */
    @Test
    fun `a day holds the place of its first run`() {
        val runs = listOf(
            run(1, target = "2026-08-31", ranAt = "2026-08-31T09:00:00Z"),
            run(2, target = "2026-08-28", ranAt = "2026-08-28T09:00:00Z"),
            run(3, target = "2026-08-31", ranAt = "2026-08-30T21:00:00Z"),
        )

        assertEquals(listOf(listOf(1L, 3L), listOf(2L)), ids(runs))
    }

    /** Nothing inside a stack is re-sorted: the order the chosen sort produced is what it keeps. */
    @Test
    fun `the reading order survives the grouping`() {
        val runs = listOf(
            run(1, target = "2026-08-31", ranAt = "2026-08-30T21:00:00Z"),
            run(2, target = "2026-08-31", ranAt = "2026-08-31T09:00:00Z"),
        )

        assertEquals(listOf(listOf(1L, 2L)), ids(runs))
    }

    /** No session to gather them into, and heaping them together would invent one. */
    @Test
    fun `undated runs each stand alone`() {
        val runs = listOf(
            run(1, target = null, ranAt = "2026-08-31T09:00:00Z"),
            run(2, target = null, ranAt = "2026-08-30T09:00:00Z"),
        )

        assertEquals(listOf(listOf(1L), listOf(2L)), ids(runs))
    }

    @Test
    fun `an empty list groups into nothing`() {
        assertEquals(emptyList<List<Long>>(), ids(emptyList()))
    }
}
