package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * How the calls inside one session card are laid out.
 *
 * A view and never the record, so the thing worth testing is not which figure each option reads but
 * that **no option loses a call**. Alphabetical was the order this replaced; an order that quietly
 * dropped the one call with no risk-to-reward would be strictly worse than the order it replaced,
 * and it would do it on exactly the cards nobody checks.
 */
class CallOrderTest {

    private val session = LocalDate.of(2026, 8, 10)

    private fun call(
        ticker: String,
        channel: String,
        entry: Double? = 10.0,
        target1: Double? = 12.0,
        stop: Double? = 9.0,
    ) = ScoredCall(
        ticker = ticker,
        companyEnglish = null,
        companyArabic = null,
        channel = channel,
        channelId = 1L,
        openedOn = session,
        entryLow = entry,
        entryHigh = entry,
        target1 = target1,
        target2 = null,
        stopLoss = stop,
        outcome = Outcome.OPEN,
        settledOn = null,
        peakHigh = null,
        troughLow = null,
        returnPct = null,
        sessionsElapsed = 0,
    )

    private fun score(channel: String, discounted: Double?) = ChannelScore(
        channel = channel,
        calls = 10,
        judged = 10,
        fullHits = 0,
        partialHits = 0,
        stopped = 0,
        expired = 0,
        notTradable = 0,
        fullHitRate = null,
        anyTargetRate = null,
        averageReturn = discounted,
        medianSessionsToHit = null,
        discountedReturn = discounted,
    )

    /** Three sources: a good one, a poor one, and one the app has never scored. */
    private val scores = mapOf(
        "strong" to score("strong", 4.5),
        "weak" to score("weak", -3.0),
    )

    private val lookup: (String) -> ChannelScore? = { scores[it] }

    @Test
    fun `ticker order is the record's own`() {
        val calls = listOf(call("SWDY", "weak"), call("AMOC", "strong"), call("COMI", "unrated"))

        assertEquals(
            listOf("AMOC", "COMI", "SWDY"),
            CallOrder.TICKER.sort(calls, lookup).map(ScoredCall::ticker),
        )
    }

    @Test
    fun `source order leads with the best record and puts an unrated source last`() {
        val calls = listOf(call("SWDY", "weak"), call("COMI", "unrated"), call("AMOC", "strong"))

        // A source with no record has not earned the top of the list by being unmeasurable, which
        // is precisely what sorting nulls high would do on a fresh install - where *every* source
        // is unrated and the order would silently become arbitrary.
        assertEquals(
            listOf("AMOC", "SWDY", "COMI"),
            CallOrder.SOURCE.sort(calls, lookup).map(ScoredCall::ticker),
        )
    }

    @Test
    fun `two calls from one source stay in ticker order`() {
        val calls = listOf(call("SWDY", "strong"), call("AMOC", "strong"))

        // The tie-break is what stops a card reshuffling itself between two recompositions that
        // both read the same figures.
        assertEquals(
            listOf("AMOC", "SWDY"),
            CallOrder.SOURCE.sort(calls, lookup).map(ScoredCall::ticker),
        )
    }

    @Test
    fun `risk to reward leads with the widest and keeps a call whose levels contradict each other`() {
        val calls = listOf(
            // 2 up against 1 down.
            call("SWDY", "weak", entry = 10.0, target1 = 12.0, stop = 9.0),
            // 5 up against 1 down.
            call("AMOC", "weak", entry = 10.0, target1 = 15.0, stop = 9.0),
            // A stop above the entry: not a call risking nothing, a call this cannot describe.
            call("COMI", "weak", entry = 10.0, target1 = 12.0, stop = 11.0),
        )

        val ordered = CallOrder.RISK_REWARD.sort(calls, lookup)

        assertEquals(listOf("AMOC", "SWDY", "COMI"), ordered.map(ScoredCall::ticker))
        // The point of the whole enum: the card the app cannot measure is still on the screen.
        assertEquals(calls.size, ordered.size)
    }

    @Test
    fun `every option lays out every call`() {
        val calls = listOf(
            call("SWDY", "weak"),
            call("COMI", "unrated", entry = null, target1 = null, stop = null),
            call("AMOC", "strong"),
        )

        CallOrder.entries.forEach { order ->
            val ordered = order.sort(calls, lookup)
            assertEquals(order.name, calls.size, ordered.size)
            assertEquals(order.name, calls.toSet(), ordered.toSet())
        }
    }
}
