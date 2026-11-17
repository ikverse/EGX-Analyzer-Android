package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Stocks several channels called lately: counted, ordered by the count, and never ranked. */
class ConsensusTest {
    // Tuesday 17 November 2026; ten sessions back is Tuesday 3 November.
    private val today = LocalDate.of(2026, 11, 17)

    private fun call(ticker: String, channel: String, opened: LocalDate, outcome: Outcome = Outcome.OPEN) = ScoredCall(
        ticker = ticker, companyEnglish = null, companyArabic = null, channel = channel, channelId = null,
        openedOn = opened, entryLow = 10.0, entryHigh = 10.2, target1 = 11.0, target2 = 12.0, stopLoss = 9.0,
        outcome = outcome, settledOn = null, peakHigh = null, troughLow = null, returnPct = null, sessionsElapsed = 1,
    )

    @Test
    fun mostChannelsFirstThenNewest() {
        val calls = listOf(
            call("AAAA", "One", today.minusDays(1)), call("AAAA", "Two", today.minusDays(2)),
            call("BBBB", "One", today.minusDays(5)), call("BBBB", "Two", today.minusDays(6)), call("BBBB", "Three", today.minusDays(7)),
            call("CCCC", "One", today), call("CCCC", "Three", today),
        )
        val shared = Consensus.shared(calls, today)
        assertEquals(listOf("BBBB", "CCCC", "AAAA"), shared.map { it.ticker })
        assertEquals(listOf("One", "Two", "Three"), shared.first().channels)
    }

    @Test
    fun oneChannelTwiceIsOneChannel() {
        val calls = listOf(call("AAAA", "One", today.minusDays(1)), call("AAAA", "One", today.minusDays(3)))
        assertEquals(emptyList<SharedCall>(), Consensus.shared(calls, today))
    }

    @Test
    fun onlyOpenCallsInsideTheWindowCount() {
        val calls = listOf(
            call("AAAA", "One", today.minusDays(1)),
            call("AAAA", "Two", today.minusDays(1), Outcome.STOPPED),
            call("BBBB", "One", LocalDate.of(2026, 11, 3)),
            call("BBBB", "Two", LocalDate.of(2026, 11, 2)),
        )
        assertEquals(emptyList<SharedCall>(), Consensus.shared(calls, today))
        assertNull(Consensus.forStock(calls, "AAAA", today))
    }
}
