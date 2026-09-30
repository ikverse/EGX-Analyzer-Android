package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.CallSanity
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.PerformanceCalculator
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

    private fun call(
        channel: String,
        ticker: String,
        outcome: Outcome,
        // A stopped call that returned +5% is not a stopped call, and every ordering below is now
        // decided by what the calls were worth rather than by how many of them worked.
        returnPct: Double = if (outcome == Outcome.STOPPED) -5.0 else 5.0,
        target1: Double = 11.0,
        target2: Double = 12.0,
        stopLoss: Double = 9.5,
        repeatOf: LocalDate? = null,
    ) = ScoredCall(
        ticker = ticker,
        companyEnglish = null,
        companyArabic = null,
        channel = channel,
        channelId = null,
        openedOn = called,
        entryLow = 10.0,
        entryHigh = 10.2,
        target1 = target1,
        target2 = target2,
        stopLoss = stopLoss,
        outcome = outcome,
        settledOn = called,
        peakHigh = 11.5,
        troughLow = 9.9,
        returnPct = returnPct,
        sessionsElapsed = 1,
        repeatOf = repeatOf,
        // Read off the levels exactly as the scorer reads them, rather than passed in: a fixture
        // that could name its own faults would be free to describe a call the app would judge
        // differently. No session here, so the structural checks run and the distance one does not.
        faults = CallSanity.faults(10.0, 10.2, target1, target2, stopLoss, session = null),
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

    /**
     * Above the floor it is the record that decides, and the record is what the calls were worth.
     *
     * This asserted the better *rate* led, which is the thing the ordering no longer does: a rate is
     * bought by moving the target closer to the entry, and two channels can hold the same rate on
     * calls worth quite different amounts. Strong still leads Proven here - six clean calls against
     * five and two stops - but it leads on +5.0 per call against +2.14, not on 100% against 71.4%.
     */
    @Test
    fun `above the floor the better record still leads`() {
        val strong = List(6) { call("Strong", "DDD$it", Outcome.PARTIAL_HIT) }
        val ranked = PerformanceCalculator.channelScores(strong + proven)

        assertEquals(listOf("Strong", "Proven"), ranked.map { it.channel })
        assertEquals(5.0, ranked.first().averageReturn!!, 0.001)
        assertEquals(2.14, ranked.last().averageReturn!!, 0.01)
    }

    /** Same earnings, more evidence: the record with more behind it leads. */
    @Test
    fun `where two records earn the same the longer one leads`() {
        val brief = List(6) { call("Brief", "EEE$it", Outcome.PARTIAL_HIT) }
        val lengthy = List(20) { call("Lengthy", "FFF$it", Outcome.PARTIAL_HIT) }

        val ranked = PerformanceCalculator.channelScores(brief + lengthy)

        assertEquals(listOf("Lengthy", "Brief"), ranked.map { it.channel })
        // Neither average moved. Both made exactly +5% a call; one has shown it more often.
        assertEquals(5.0, ranked.first().averageReturn!!, 0.001)
        assertEquals(5.0, ranked.last().averageReturn!!, 0.001)
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

    /**
     * Being right often is not the same as being worth following.
     *
     * Tight prints its target 2% above the entry against a stop 10% below and reaches it nine times
     * in ten; the tenth call takes back more than the nine made. Wide reaches its target less than
     * half the time and makes more per call. Ranked on the hit rate the wrong one led by 50 points.
     */
    @Test
    fun `the channel that makes more per call leads the one that is right more often`() {
        val tight = List(9) { call("Tight", "AAA$it", Outcome.PARTIAL_HIT, returnPct = 2.0) } +
            call("Tight", "AAZ", Outcome.STOPPED, returnPct = -10.0)
        val wide = List(4) { call("Wide", "BBB$it", Outcome.PARTIAL_HIT, returnPct = 10.0) } +
            List(6) { call("Wide", "BBZ$it", Outcome.STOPPED, returnPct = -5.0) }

        val ranked = PerformanceCalculator.channelScores(tight + wide)

        assertEquals(listOf("Wide", "Tight"), ranked.map { it.channel })
        // Both figures are still reported exactly as measured. The one that led on the old ordering
        // is still the higher of the two.
        assertEquals(90.0, ranked.single { it.channel == "Tight" }.anyTargetRate!!, 0.001)
        assertEquals(40.0, ranked.single { it.channel == "Wide" }.anyTargetRate!!, 0.001)
        assertEquals(0.8, ranked.single { it.channel == "Tight" }.averageReturn!!, 0.001)
        assertEquals(1.0, ranked.single { it.channel == "Wide" }.averageReturn!!, 0.001)
    }

    /** A mean from six calls is not the claim the same mean from fifty is. */
    @Test
    fun `a long record leads a short one that averages slightly more`() {
        val short = List(6) { call("Short", "AAA$it", Outcome.PARTIAL_HIT, returnPct = 5.0) }
        val long = List(50) { call("Long", "BBB$it", Outcome.PARTIAL_HIT, returnPct = 4.5) }

        val ranked = PerformanceCalculator.channelScores(short + long)

        assertEquals(listOf("Long", "Short"), ranked.map { it.channel })
        // Neither average moves: the discount decides the order and is not what the card prints.
        assertEquals(5.0, ranked.single { it.channel == "Short" }.averageReturn!!, 0.001)
        assertEquals(2.73, ranked.single { it.channel == "Short" }.discountedReturn!!, 0.01)
        assertEquals(4.09, ranked.single { it.channel == "Long" }.discountedReturn!!, 0.01)
    }

    /**
     * The floor under a hit rate, which is the figure printed beneath it.
     *
     * Six of six is a true 100% resting on six calls. Wilson says the record supports 61%, where the
     * normal approximation everyone reaches for first says 100% - the claim being questioned.
     */
    @Test
    fun `the hit rate carries the floor its own evidence supports`() {
        val perfect = List(6) { call("Perfect", "AAA$it", Outcome.PARTIAL_HIT) }
        val long = List(40) { call("Long", "BBB$it", Outcome.PARTIAL_HIT) } +
            List(10) { call("Long", "BBZ$it", Outcome.STOPPED) }

        val scores = PerformanceCalculator.channelScores(perfect + long)
        val six = scores.single { it.channel == "Perfect" }
        val fifty = scores.single { it.channel == "Long" }

        assertEquals(100.0, six.anyTargetRate!!, 0.001)
        assertEquals(61.0, six.anyTargetRateFloor!!, 0.1)
        assertEquals(80.0, fifty.anyTargetRate!!, 0.001)
        assertEquals(67.0, fifty.anyTargetRateFloor!!, 0.1)
    }

    /** What the channel printed, which is the context its hit rate cannot be read without. */
    @Test
    fun `risk and reward are measured from the levels the channel printed`() {
        // Entry midpoint 10.1, target 1 at 11.0, stop at 9.5: 0.9 up against 0.6 down.
        val scores = PerformanceCalculator.channelScores(
            List(5) { call("Source", "AAA$it", Outcome.PARTIAL_HIT) },
        )

        assertEquals(1.5, scores.single().averageRiskReward!!, 0.01)
    }

    /** A stop above the entry describes nothing, so it is left out rather than counted as free. */
    @Test
    fun `a call whose levels contradict each other is outside the ratio`() {
        val scores = PerformanceCalculator.channelScores(
            List(4) { call("Source", "AAA$it", Outcome.PARTIAL_HIT) } +
                call("Source", "BAD", Outcome.PARTIAL_HIT, target1 = 9.0, stopLoss = 10.5),
        )

        assertEquals(1.5, scores.single().averageRiskReward!!, 0.01)
    }

    /**
     * A misread level cannot be allowed to carry a channel's average return.
     *
     * Found on a device: a stop read as `30` off a screenshot of a stock trading near `1` scored a
     * stopped-out call at **+2900%**, and CFI Egypt's published figure read +61.65% a call against
     * the +2.99% its other forty-eight were worth. The channel beside it, on twice as many calls,
     * was at +2.92% - so the one number a reader would have chosen between them on was decided
     * entirely by a number nobody printed.
     *
     * The call is **not** dropped. It is still judged, still a stop-out, still counted for whoever
     * posted it; only the figure measured at the impossible level goes.
     */
    @Test
    fun `a return measured at a misread stop is left out of the average`() {
        val misread = call(
            "Source",
            "GGCC",
            Outcome.STOPPED,
            returnPct = 2900.0,
            stopLoss = 300.0,
        )
        val score = PerformanceCalculator.channelScores(
            List(4) { call("Source", "AAA$it", Outcome.PARTIAL_HIT) } + misread,
        ).single()

        // +5.0 from the four clean calls, and not the +584.0 that averaging all five would give.
        assertEquals(5.0, score.averageReturn!!, 0.001)
        assertEquals(5, score.judged)
        assertEquals(1, score.stopped)
    }

    /** The same, for a second target read in beneath the first. */
    @Test
    fun `a return measured at a misordered target is left out of the average`() {
        // TAQA, 9 August: target 2 beneath target 1 and beneath the entry, so a call that scored a
        // full hit was worth -20.26%.
        val misread = call(
            "Source",
            "TAQA",
            Outcome.FULL_HIT,
            returnPct = -20.26,
            target1 = 11.0,
            target2 = 8.0,
        )
        val score = PerformanceCalculator.channelScores(
            List(4) { call("Source", "AAA$it", Outcome.PARTIAL_HIT) } + misread,
        ).single()

        assertEquals(5.0, score.averageReturn!!, 0.001)
        assertEquals(5, score.judged)
        assertEquals(1, score.fullHits)
    }

    /** One bet, however many mornings it was printed on. */
    @Test
    fun `a re-posted call is counted once and said to have been`() {
        val once = List(5) { call("Source", "AAA$it", Outcome.PARTIAL_HIT) }
        val again = List(3) {
            call("Source", "AAA0", Outcome.PARTIAL_HIT, repeatOf = called)
        }

        val score = PerformanceCalculator.channelScores(once + again).single()

        assertEquals(5, score.calls)
        assertEquals(5, score.judged)
        assertEquals(3, score.repeats)
    }
}
