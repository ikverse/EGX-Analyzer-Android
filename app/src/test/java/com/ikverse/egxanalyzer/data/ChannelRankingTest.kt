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
}
