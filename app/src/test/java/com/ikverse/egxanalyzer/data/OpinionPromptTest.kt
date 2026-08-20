package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.ChannelScore
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.LatestPrice
import com.ikverse.egxanalyzer.model.Outcome
import com.ikverse.egxanalyzer.model.ScoredCall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What Ask AI is told about a call before it answers.
 *
 * The block is context, not the question: the prompt spends its first section telling the model
 * that everything here is already printed on the card the reader is looking at. What these tests
 * defend is that a figure the model *does* name comes back matching the card - a price rounded
 * differently here would have the answer contradicting the screen beside it - and that the two
 * figures the card does not carry are supplied rather than left to be worked out.
 */
class OpinionPromptTest {

    private val call = ScoredCall(
        ticker = "ABUK",
        companyEnglish = "Abu Qir Fertilizers",
        companyArabic = "ابو قير للاسمدة",
        channel = "EGX Signals",
        channelId = -100L,
        openedOn = LocalDate.parse("2026-08-11"),
        entryLow = 68.5,
        entryHigh = 69.8,
        target1 = 74.0,
        target2 = 78.5,
        stopLoss = 65.0,
        outcome = Outcome.OPEN,
        settledOn = null,
        peakHigh = 72.4,
        peakOn = LocalDate.parse("2026-08-14"),
        troughLow = 66.9,
        troughOn = LocalDate.parse("2026-08-12"),
        returnPct = 3.12,
        sessionsElapsed = 6,
        windowSessions = 10,
        requestId = "run-1",
    )

    private val today = LocalDate.parse("2026-08-20")

    private fun build(
        call: ScoredCall = this.call,
        latest: LatestPrice? = null,
        channel: ChannelScore? = null,
    ) = OpinionPrompt.build(call, latest, channel, held = null, today = today)

    @Test
    fun `the levels go in exactly as the card prints them`() {
        val prompt = build()

        assertTrue(prompt.contains("Entry band     68.5 - 69.8"))
        assertTrue(prompt.contains("Stop loss      65"))
        assertTrue(prompt.contains("Target 1       74"))
        assertTrue(prompt.contains("Target 2       78.5"))
    }

    /**
     * The one figure that decides whether a call was worth taking, and the one the card omits.
     *
     * Left for the model to work out it would be a division stated with confidence and got wrong
     * often enough to matter: (74 - 69.15) / (69.15 - 65) is 1.16, and a model that answers 1.6
     * has just endorsed a trade on arithmetic nobody checked.
     */
    @Test
    fun `risk and reward is measured rather than left to the model`() {
        assertTrue(build().contains("Risk to reward 1.16 to 1"))
    }

    @Test
    fun `a call whose levels contradict each other reports no ratio at all`() {
        // A target under the entry is not a trade offering nothing; it is a reading that failed.
        val backwards = call.copy(target1 = 60.0, target2 = null)

        assertFalse(build(backwards).contains("Risk to reward"))
    }

    @Test
    fun `the latest close is named as where the stock actually is`() {
        val latest = LatestPrice(
            session = DailySession(
                ticker = "ABUK",
                date = LocalDate.parse("2026-08-19"),
                high = 71.9,
                low = 70.8,
                close = 71.2,
                volume = null,
                open = 71.1,
            ),
            provisional = false,
        )

        val prompt = build(latest = latest)
        assertTrue(prompt.contains("Latest close: 71.2 on 2026-08-19"))
        // The move from the entry midpoint - 69.15 to 71.2 - which is what makes "the entry has
        // gone" checkable. One decimal, exactly as the card prints it.
        assertTrue(prompt.contains("+3% from the middle of the entry band"))
    }

    /**
     * A session that has not closed yet is marked, not hidden.
     *
     * Its close is going to move, and a verdict resting on it would be a verdict on a number that
     * no longer exists by the time anyone reads it.
     */
    @Test
    fun `a session still trading says so`() {
        val latest = LatestPrice(
            session = DailySession("ABUK", today, 71.9, 70.8, 71.2, null, 71.1),
            provisional = true,
        )

        assertTrue(build(latest = latest).contains("still trading"))
    }

    @Test
    fun `an unpriced stock says nothing is known rather than leaving the line out`() {
        val prompt = build(latest = null)

        assertTrue(prompt.contains("Latest close: not priced"))
    }

    /**
     * A record short of the ranking floor is handed over with that said out loud.
     *
     * Six judged calls and two are two data points. A model reading "61.9% hit rate" off three
     * calls will draw a conclusion from it, and the honest framing is the app's job rather than
     * something to hope the model works out.
     */
    @Test
    fun `a channel record too thin to rank is labelled as such`() {
        val thin = ChannelScore(
            channel = "EGX Signals",
            calls = 4,
            judged = 3,
            fullHits = 1,
            partialHits = 1,
            stopped = 1,
            expired = 0,
            notTradable = 1,
            fullHitRate = 33.3,
            anyTargetRate = 66.7,
            averageReturn = 2.41,
            medianSessionsToHit = 4.0,
        )

        assertTrue(build(channel = thin).contains("this is not yet a record"))
    }

    @Test
    fun `a re-posted call is named as one bet rather than two`() {
        val repeat = call.copy(repeatOf = LocalDate.parse("2026-08-10"))

        assertTrue(build(repeat).contains("One bet, not two."))
    }
}
