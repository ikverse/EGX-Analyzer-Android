package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceCalculator
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.StockScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What happens when one stock gets recommended.
 *
 * A stock's record and a source's are one piece of arithmetic over two groupings - `tally` - so the
 * rules argued out for channels hold here without being restated. What is worth pinning is that the
 * grouping is right and that a stock's record uses the same floor the channels do.
 */
class StockRecordTest {

    private val called = LocalDate.of(2026, 8, 3)

    private fun call(
        ticker: String,
        channel: String,
        outcome: Outcome = Outcome.FULL_HIT,
        returnPct: Double = 5.0,
        openedOn: LocalDate = called,
        repeatOf: LocalDate? = null,
    ) = ScoredCall(
        ticker = ticker,
        companyEnglish = null,
        companyArabic = null,
        channel = channel,
        channelId = null,
        openedOn = openedOn,
        entryLow = 10.0,
        entryHigh = 10.2,
        target1 = 11.0,
        target2 = 12.0,
        stopLoss = 9.5,
        outcome = outcome,
        settledOn = openedOn,
        peakHigh = 11.5,
        troughLow = 9.9,
        returnPct = returnPct,
        sessionsElapsed = 3,
        repeatOf = repeatOf,
    )

    @Test
    fun `a stock's record gathers every source that named it`() {
        val scores = PerformanceCalculator.stockScores(
            listOf(
                call("AMOC", "one"),
                call("AMOC", "two", outcome = Outcome.STOPPED, returnPct = -4.0),
                call("SWDY", "one"),
            ),
        )

        val amoc = scores.single { it.ticker == "AMOC" }
        assertEquals(2, amoc.sources)
        assertEquals(2, amoc.tally.judged)
        assertEquals(0.5, amoc.tally.averageReturn!!, 1e-9)
    }

    @Test
    fun `two spellings of one stock are one record`() {
        // `COMI` and `COMI.CA` reaching the rollup as two stocks would split every figure about it
        // in half and rank both halves.
        val scores = PerformanceCalculator.stockScores(
            listOf(call("COMI", "one"), call("COMI.CA", "two")),
        )

        assertEquals(1, scores.size)
        assertEquals(2, scores.single().sources)
    }

    @Test
    fun `a re-posting is not a second call on the stock`() {
        val scores = PerformanceCalculator.stockScores(
            listOf(
                call("AMOC", "one"),
                call("AMOC", "one", openedOn = called.plusDays(1), repeatOf = called),
            ),
        )

        val amoc = scores.single()
        assertEquals(1, amoc.tally.judged)
        assertEquals(1, amoc.tally.repeats)
        // The source named it once, on one idea, however many mornings it went on saying so.
        assertEquals(1, amoc.sources)
    }

    @Test
    fun `a stock with too little behind it keeps its figures and stops leading`() {
        val thin = List(2) { call("SWDY", "one", returnPct = 40.0) }
        val long = List(8) { index -> call("AMOC", "one", returnPct = 3.0, openedOn = called.plusDays(index.toLong())) }

        val scores = PerformanceCalculator.stockScores(thin + long)

        // Measured exactly - the figure is not hidden or softened.
        assertEquals(40.0, scores.single { it.ticker == "SWDY" }.tally.averageReturn!!, 1e-9)
        // And it does not out-rank eight calls, which is the whole point of the floor.
        assertEquals("AMOC", scores.first().ticker)
    }

    @Test
    fun `the ranking floor is the same one the channels use`() {
        val scores: List<StockScore> = PerformanceCalculator.stockScores(
            List(PerformanceCalculator.MINIMUM_JUDGED_TO_RANK) { index ->
                call("AMOC", "one", openedOn = called.plusDays(index.toLong()))
            },
        )

        assertTrue(
            scores.single().tally.judged >= PerformanceCalculator.MINIMUM_JUDGED_TO_RANK,
        )
    }
}
