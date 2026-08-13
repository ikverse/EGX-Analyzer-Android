package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.ScoredCall
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * A record needs enough behind it to be a record.
 *
 * Ranking on the rate alone put CFI Egypt top at 100% on two settled calls, above a channel at
 * 71.4% on seven. Both figures were measured correctly; the order was the lie.
 */
class ChannelRankingTest {

    private val called = LocalDate.of(2026, 8, 3)

    private fun call(channel: String, ticker: String, outcome: Outcome) = ScoredCall(
        ticker = ticker,
        companyEnglish = null,
        companyArabic = null,
        channel = channel,
        channelId = null,
        openedOn = called,
        entryLow = 10.0,
        entryHigh = 10.2,
        target1 = 11.0,
        target2 = 12.0,
        stopLoss = 9.5,
        outcome = outcome,
        settledOn = called,
        peakHigh = 11.5,
        troughLow = 9.9,
        returnPct = 5.0,
        sessionsElapsed = 1,
    )

    /** Two settled calls, both good: a real 100%, and no evidence at all. */
    private val lucky = List(2) { call("Lucky", "AAA$it", Outcome.PARTIAL_HIT) }

    /** Seven settled, five of them good: a worse rate carrying far more weight. */
    private val proven = List(5) { call("Proven", "BBB$it", Outcome.PARTIAL_HIT) } +
        List(2) { call("Proven", "CCC$it", Outcome.STOPPED) }

    @Test
    fun `a channel below the floor sorts under one above it, whatever the rate`() {
        val ranked = PerformanceCalculator.channelScores(lucky + proven)

        assertEquals(listOf("Proven", "Lucky"), ranked.map { it.channel })
    }

    /** Demoted, not corrected: the figure it earned is the figure it keeps. */
    @Test
    fun `the thin channel still reports the rate it actually achieved`() {
        val thin = PerformanceCalculator.channelScores(lucky + proven).single { it.channel == "Lucky" }

        assertEquals(100.0, thin.anyTargetRate!!, 0.001)
        assertEquals(2, thin.judged)
    }

    @Test
    fun `above the floor the better rate still leads`() {
        val strong = List(6) { call("Strong", "DDD$it", Outcome.PARTIAL_HIT) }
        val ranked = PerformanceCalculator.channelScores(strong + proven)

        assertEquals(listOf("Strong", "Proven"), ranked.map { it.channel })
    }

    @Test
    fun `a channel with nothing settled sorts last of all`() {
        val silent = List(9) { call("Silent", "EEE$it", Outcome.OPEN) }
        val ranked = PerformanceCalculator.channelScores(silent + lucky + proven)

        assertEquals("Silent", ranked.last().channel)
    }

    /**
     * The four verdict counts divide the judged calls exactly, with nothing left over.
     *
     * The outcome bar draws them as one band whose whole length *is* `judged`, so a call that fell
     * outside all four - or landed in two of them - would draw a bar that disagrees with the count
     * printed beside it. The four are the only outcomes `Outcome.judged` is true for, and this is
     * what holds that true as outcomes are added.
     */
    @Test
    fun `the four verdicts partition the judged calls`() {
        val mixed = List(3) { call("Mixed", "AAA$it", Outcome.FULL_HIT) } +
            List(2) { call("Mixed", "BBB$it", Outcome.PARTIAL_HIT) } +
            List(4) { call("Mixed", "CCC$it", Outcome.STOPPED) } +
            List(1) { call("Mixed", "DDD$it", Outcome.EXPIRED) } +
            // Say nothing about the source, so they are outside every rate and outside the bar.
            List(5) { call("Mixed", "EEE$it", Outcome.OPEN) } +
            List(2) { call("Mixed", "FFF$it", Outcome.UNPRICED) }

        val score = PerformanceCalculator.channelScores(mixed).single()

        assertEquals(10, score.judged)
        assertEquals(
            score.judged,
            score.fullHits + score.partialHits + score.stopped + score.expired,
        )
        // The unjudged are still counted as calls made; they are simply not weighed.
        assertEquals(17, score.calls)
    }

    /**
     * Whoever the hero names as the best record has a record behind it.
     *
     * The screen takes the first channel clearing [PerformanceCalculator.MINIMUM_JUDGED_TO_RANK] out
     * of this order. It used to re-sort on the rate alone first, which named Lucky as "Best" at 100%
     * directly above Lucky's own card calling it too thin to rank.
     */
    @Test
    fun `the source the hero leads with is one that clears the floor`() {
        val ranked = PerformanceCalculator.channelScores(lucky + proven)

        val leader = ranked.firstOrNull {
            it.judged >= PerformanceCalculator.MINIMUM_JUDGED_TO_RANK
        }

        assertEquals("Proven", leader?.channel)
        // The one with the better rate, and it is still not the leader.
        assertEquals(100.0, ranked.single { it.channel == "Lucky" }.anyTargetRate!!, 0.001)
    }
}
