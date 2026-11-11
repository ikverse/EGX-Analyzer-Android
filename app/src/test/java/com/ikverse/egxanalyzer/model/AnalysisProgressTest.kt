package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * That a run only ever claims to know what it actually knows.
 *
 * The screen counted elapsed seconds and nothing else for as long as it did because the alternative
 * was a figure nobody had measured, and the whole point of this type is to be the measured one. So
 * what is pinned here is not the arithmetic - which is a division - but the two places the old
 * objection still applies: the writing stage, where nothing can be known, and the bounds, where a
 * bar that overshot or walked backwards would be worse than the clock it replaced.
 */
class AnalysisProgressTest {

    private fun reading(done: Int, batches: Int, imagesDone: Int, images: Int) = AnalysisProgress(
        stage = AnalysisProgress.Stage.READING,
        batchesDone = done,
        batches = batches,
        imagesDone = imagesDone,
        images = images,
    )

    @Test
    fun `reading reports the share of images read`() {
        assertEquals(0.0, reading(0, 7, 0, 56).fraction!!.toDouble(), 1e-6)
        assertEquals(0.5, reading(4, 7, 28, 56).fraction!!.toDouble(), 1e-6)
    }

    @Test
    fun `the share is measured in images rather than in batches`() {
        // Eight per request, so fifty-seven sources end on a batch of one. As a batch count the
        // last step would move the bar as far for that one image as for the eight before it.
        val lastBatch = reading(done = 7, batches = 8, imagesDone = 56, images = 57)
        assertEquals(56.0 / 57.0, lastBatch.fraction!!.toDouble(), 1e-6)
    }

    @Test
    fun `writing reports no share at all`() {
        // The one request whose length nothing here can predict. A figure for it would be the
        // invented one this type exists to avoid - see AnalysisProgress.fraction.
        val writing = AnalysisProgress(
            stage = AnalysisProgress.Stage.WRITING,
            batchesDone = 7,
            batches = 7,
            imagesDone = 56,
            images = 56,
        )
        assertNull(writing.fraction)
    }

    @Test
    fun `a run with nothing to read reports no share rather than dividing by zero`() {
        // Every source was read by an earlier run, so the plan has no batches to send. Real: it is
        // what a second schedule in one day over the same messages produces.
        assertNull(reading(0, 0, 0, 0).fraction)
    }

    @Test
    fun `the share never leaves its bounds`() {
        assertEquals(1.0, reading(8, 8, 60, 56).fraction!!.toDouble(), 1e-6)
        assertEquals(0.0, reading(0, 8, -3, 56).fraction!!.toDouble(), 1e-6)
    }

    @Test
    fun `the batch in flight is named, and never past the last`() {
        assertEquals("Reading the posts · 0 of 56 pictures", reading(0, 7, 0, 56).line())
        assertEquals("Reading the posts · 16 of 56 pictures", reading(2, 7, 16, 56).line())
        // A text-only run has no pictures to count, so it counts batches.
        assertEquals("Reading the posts · batch 3 of 7", reading(2, 7, 0, 0).line())
        // Defensive rather than expected: a count that ran past the end would read as "batch 8 of
        // 7", which is the kind of line that makes a reader distrust every other figure on screen.
        assertEquals("Batch 7 of 7", reading(9, 7, 56, 56).badge())
    }

    @Test
    fun `a correction says which attempt it is`() {
        fun writing(correction: Int) = AnalysisProgress(
            stage = AnalysisProgress.Stage.WRITING,
            correction = correction,
        )
        assertEquals("Writing the report", writing(0).line())
        assertEquals("Correcting the report", writing(1).line())
        assertEquals("Correcting the report, attempt 2", writing(2).line())
        assertEquals("Writing", writing(0).badge())
        assertEquals("Correcting", writing(1).badge())
    }

    @Test
    fun `the estimate counts pictures, and batches where there are none`() {
        val pictures = reading(2, 7, 16, 56)
        assertEquals(16, pictures.unitsDone)
        assertEquals(56, pictures.units)
        val text = reading(2, 7, 0, 0)
        assertEquals(2, text.unitsDone)
        assertEquals(7, text.units)
    }

    @Test
    fun `writing gives the estimate nothing to go on`() {
        val writing = AnalysisProgress(stage = AnalysisProgress.Stage.WRITING, batchesDone = 7, batches = 7, imagesDone = 56, images = 56)
        // Every unit is done, so the estimate has nothing left to time.
        assertNull(ReadingEstimate.endsAt(java.time.Instant.EPOCH, java.time.Instant.EPOCH.plusSeconds(60), writing.unitsDone, writing.units))
    }
}
