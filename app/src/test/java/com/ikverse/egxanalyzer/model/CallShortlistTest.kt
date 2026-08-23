package com.ikverse.egxanalyzer.model

import com.ikverse.egxanalyzer.data.PerformanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Which of twenty cards is worth spending a paid request on.
 *
 * It ranks attention and predicts nothing, so what matters is that every signal is a fact the card
 * can state in words and that a **missing** input never becomes a negative one - the shortlist is a
 * reason to look, and absence of evidence is not a reason to look away.
 */
class CallShortlistTest {

    private val day = LocalDate.of(2026, 8, 10)

    private fun call(
        entryLow: Double? = 10.0,
        entryHigh: Double? = 10.5,
        target1: Double? = 13.0,
        stopLoss: Double? = 9.0,
        outcome: Outcome = Outcome.OPEN,
    ) = ScoredCall(
        ticker = "AMOC",
        companyEnglish = null,
        companyArabic = null,
        channel = "source-one",
        channelId = 1L,
        openedOn = day,
        entryLow = entryLow,
        entryHigh = entryHigh,
        target1 = target1,
        target2 = null,
        stopLoss = stopLoss,
        outcome = outcome,
        settledOn = null,
        peakHigh = null,
        troughLow = null,
        returnPct = null,
        sessionsElapsed = 0,
    )

    private fun source(judged: Int, average: Double?) = ChannelScore(
        channel = "source-one",
        calls = judged,
        judged = judged,
        fullHits = 0,
        partialHits = 0,
        stopped = 0,
        expired = 0,
        notTradable = 0,
        fullHitRate = null,
        anyTargetRate = null,
        averageReturn = average,
        medianSessionsToHit = null,
    )

    private fun stock(judged: Int, average: Double?) = StockScore(
        ticker = "AMOC",
        companyEnglish = null,
        companyArabic = null,
        sources = 2,
        tally = CallTally(
            calls = judged,
            judged = judged,
            fullHits = 0,
            partialHits = 0,
            stopped = 0,
            expired = 0,
            notTradable = 0,
            fullHitRate = null,
            anyTargetRate = null,
            averageReturn = average,
            medianSessionsToHit = null,
            medianSessionsToStop = null,
            discountedReturn = average,
            anyTargetRateFloor = null,
            averageRiskReward = null,
            repeats = 0,
        ),
    )

    private fun priced(close: Double) = LatestPrice(
        session = DailySession("AMOC", day, high = close, low = close, close = close, volume = 1.0),
        provisional = false,
    )

    @Test
    fun `the floor here is the floor the ranking uses`() {
        // A card raising "strong source" off a record the ranking itself declines to rank would be
        // the screen contradicting itself, on the one figure a reader is about to spend money on.
        assertEquals(
            PerformanceCalculator.MINIMUM_JUDGED_TO_RANK,
            CallShortlist.MINIMUM_JUDGED_FOR_A_RECORD,
        )
    }

    @Test
    fun `a call with everything going for it carries every signal`() {
        val signals = CallShortlist.signals(
            call = call(),
            source = source(judged = 20, average = 4.0),
            stock = stock(judged = 12, average = 3.0),
            latest = priced(10.2),
        )

        assertEquals(CallSignal.entries.toSet(), signals)
    }

    @Test
    fun `nothing known raises nothing rather than everything`() {
        // A card that named a stock and no numbers, from a source with no record, on a stock
        // nobody else has called, with no price. Absence of evidence must not read as evidence in
        // either direction - and note that risk to reward is the one signal needing no record at
        // all, so it has to be the levels that are missing here rather than the rollups.
        val bare = call(entryLow = null, entryHigh = null, target1 = null, stopLoss = null)

        val signals = CallShortlist.signals(bare, source = null, stock = null, latest = null)

        assertTrue(signals.isEmpty())
    }

    @Test
    fun `a thin record raises no signal even where its average is high`() {
        val signals = CallShortlist.signals(
            call = call(target1 = 10.6, stopLoss = 9.9),
            // Three calls at +9% is measured exactly and is worth nothing as a verdict.
            source = source(judged = 3, average = 9.0),
            stock = stock(judged = 2, average = 8.0),
            latest = null,
        )

        assertTrue(signals.isEmpty())
    }

    @Test
    fun `a losing record raises no signal`() {
        val signals = CallShortlist.signals(
            call = call(target1 = 10.6, stopLoss = 9.9),
            source = source(judged = 30, average = -2.0),
            stock = stock(judged = 30, average = -1.0),
            latest = null,
        )

        assertTrue(signals.isEmpty())
    }

    @Test
    fun `risk to reward has to clear two to one`() {
        // 3.0 up against 1.25 down, from the middle of the band: 2.4 to 1.
        assertTrue(
            CallSignal.GOOD_RISK_REWARD in
                CallShortlist.signals(call(), null, null, null),
        )
        // 0.75 up against 1.25 down. A source printing this needs to be right most of the time,
        // which is what the other signals are about and not this one.
        assertTrue(
            CallSignal.GOOD_RISK_REWARD !in
                CallShortlist.signals(call(target1 = 11.0), null, null, null),
        )
    }

    @Test
    fun `the price signal is about today and only for a live call`() {
        assertTrue(
            CallSignal.PRICE_IN_BAND in
                CallShortlist.signals(call(), null, null, priced(10.2)),
        )
        assertTrue(
            CallSignal.PRICE_IN_BAND !in
                CallShortlist.signals(call(), null, null, priced(11.9)),
        )
        // The price wandering back through the buy zone of a call that hit its target three weeks
        // ago is a coincidence, not an opportunity.
        assertTrue(
            CallSignal.PRICE_IN_BAND !in
                CallShortlist.signals(call(outcome = Outcome.FULL_HIT), null, null, priced(10.2)),
        )
    }
}
