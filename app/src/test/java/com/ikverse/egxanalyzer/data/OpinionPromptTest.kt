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
        history: List<DailySession> = emptyList(),
    ) = OpinionPrompt.build(call, latest, channel, held = null, today = today, history = history)

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

    /**
     * A run of sessions at one price, long enough for both averages to be real.
     *
     * Flat on purpose. What these tests are checking is that the figures are produced and labelled
     * correctly, and a mean of a constant is the one mean whose right answer cannot be argued with.
     */
    private fun history(
        count: Int,
        close: Double = 70.0,
        volume: Double? = 100_000.0,
    ): List<DailySession> = (0 until count).map { index ->
        DailySession(
            ticker = "ABUK",
            date = LocalDate.parse("2026-06-01").plusDays(index.toLong()),
            high = close + 1,
            low = close - 1,
            close = close,
            volume = volume,
            open = close,
        )
    }

    /**
     * The volume the app has always held and never sent.
     *
     * It is the figure that decides whether a call can be taken at all: levels on a stock turning
     * over a few thousand pounds a session are decoration, because the exit is the whole trade. It
     * sat in the feed unused while the model was asked to judge the trade without it.
     */
    @Test
    fun `how much the stock trades is measured rather than left out`() {
        val prompt = build(history = history(20))

        assertTrue(prompt.contains("Average volume         100k shares a session"))
        assertTrue(prompt.contains("Average value traded   7.0M EGP a session"))
        // The ratio rather than the two totals: the question is whether getting out takes a
        // morning or a fortnight, and that is not something to leave to a model's arithmetic.
        assertTrue(prompt.contains("A 100k EGP position is 1.4% of one session's turnover."))
    }

    @Test
    fun `a feed with no volume says so rather than implying the stock is liquid`() {
        val prompt = build(history = history(20, volume = null))

        assertTrue(prompt.contains("no volume recorded in the feed"))
        assertFalse(prompt.contains("Average value traded"))
    }

    @Test
    fun `the session table carries the volume beside the prices`() {
        val prompt = build(history = history(20))

        assertTrue(prompt.contains("date        open    high    low     close   volume"))
        assertTrue(prompt.contains("100k"))
    }

    /**
     * The averages are worked out here or not stated.
     *
     * Asking a model to mean fifty closes off a table it is reading is asking for a number that
     * looks right and is not, stated with the same confidence as the ones that are.
     */
    @Test
    fun `the moving averages are computed where there are enough sessions`() {
        val prompt = build(history = history(50))

        assertTrue(prompt.contains("20-session average close   70"))
        assertTrue(prompt.contains("50-session average close   70"))
        assertTrue(prompt.contains("Over the last 50 sessions: high 71"))
    }

    /**
     * Thirty sessions at one price and twenty at a much higher one.
     *
     * A flat run makes every figure below zero, which is the one shape that cannot tell a gap
     * computed correctly from a gap not computed at all.
     */
    private fun stepped(): List<DailySession> = (0 until 50).map { index ->
        val close = if (index < 30) 60.0 else 80.0
        DailySession(
            ticker = "ABUK",
            date = LocalDate.parse("2026-06-01").plusDays(index.toLong()),
            high = close + 1,
            low = close - 1,
            close = close,
            volume = 100_000.0,
            open = close,
        )
    }

    /**
     * Every level is stated twice: as the price, and as the distance from today's close.
     *
     * The prompt now *requires* the model to read this section back - an answer naming no figure
     * fitted every stock on the exchange, which is why every one of them came back the same - so
     * the gaps it is required to name have to be handed to it. (80 - 68) / 68 is 17.6%, and a model
     * that answers 15% has just placed the stock somewhere it is not, inside a forecast.
     */
    @Test
    fun `the distance from each average is computed rather than left to be worked out`() {
        val prompt = build(history = stepped())

        assertTrue(prompt.contains("20-session average close   80, latest close 0% against it"))
        assertTrue(prompt.contains("50-session average close   68, latest close +17.6% against it"))
    }

    /**
     * Which average is on top is the trend, and by how much is whether it is worth saying.
     */
    @Test
    fun `the gap between the two averages is stated as a figure`() {
        val prompt = build(history = stepped())

        assertTrue(prompt.contains("The 20-session average is +17.6% against the 50-session one."))
    }

    /**
     * "Near the top of its range" is the reading, and it needs a close placed between two prices.
     */
    @Test
    fun `where the close sits inside its own range is one number`() {
        val prompt = build(history = stepped())

        assertTrue(
            prompt.contains("The latest close sits at 95.5% of that range, measured from the low."),
        )
    }

    /**
     * Measured from where the stock actually is, not from wherever the stored feed happens to end.
     *
     * The two differ on every stock whose history was fetched before today's session, which is most
     * of them for most of the day - and a gap measured from a stale row would be a figure the sheet
     * printed beside a different price on the card behind it.
     */
    @Test
    fun `the gaps are measured from the latest close rather than the end of the feed`() {
        val latest = LatestPrice(
            session = DailySession(
                ticker = "ABUK",
                date = LocalDate.parse("2026-08-19"),
                high = 89.0,
                low = 87.0,
                close = 88.0,
                volume = null,
                open = 87.5,
            ),
            provisional = false,
        )

        val prompt = build(latest = latest, history = stepped())

        assertTrue(prompt.contains("20-session average close   80, latest close +10% against it"))
    }

    /**
     * Ten sessions called a fifty-session average is a wrong figure, not a rounded one.
     */
    @Test
    fun `an average with too little behind it is left out entirely`() {
        val prompt = build(history = history(20))

        assertTrue(prompt.contains("20-session average close"))
        assertFalse(prompt.contains("50-session average close"))
    }

    /**
     * Who else is in the same trade, and how it went the last time.
     *
     * Split into two lists because they are read for opposite reasons: calls open now are other
     * channels crowding in, which the prompt is careful to say is not confirmation, and settled
     * ones are the only record the app holds of what happens when this stock is recommended.
     */
    @Test
    fun `other calls on the same stock are separated into crowding and record`() {
        val open = call.copy(channel = "Cairo Calls", openedOn = LocalDate.parse("2026-08-18"))
        val settled = call.copy(
            channel = "Nile Trades",
            openedOn = LocalDate.parse("2026-06-02"),
            outcome = Outcome.FULL_HIT,
            returnPct = 8.1,
        )

        val prompt = OpinionPrompt.build(
            call, null, null, held = null, today = today, otherCalls = listOf(open, settled),
        )

        assertTrue(prompt.contains("Other channels calling this stock now:"))
        assertTrue(prompt.contains("\"Cairo Calls\" on 2026-08-18"))
        assertTrue(prompt.contains("Earlier calls on this stock, already judged:"))
        assertTrue(prompt.contains("\"Nile Trades\" on 2026-06-02: full hit, +8.1%"))
    }

    /** The card that opened the sheet is not listed back as another channel agreeing with itself. */
    @Test
    fun `the call being asked about is not listed among the others`() {
        val prompt = OpinionPrompt.build(
            call, null, null, held = null, today = today, otherCalls = listOf(call),
        )

        assertFalse(prompt.contains("Other channels calling this stock now:"))
    }

    /**
     * The search brief goes in last, where the model reads it after the figures it has to respect.
     */
    @Test
    fun `the search brief is appended when one is given`() {
        val prompt = OpinionPrompt.build(
            call, null, null, held = null, today = today, search = "SEARCH\nlook here",
        )

        assertTrue(prompt.endsWith("SEARCH\nlook here"))
    }

    @Test
    fun `an unsearched request carries no search block at all`() {
        assertFalse(build().contains("SEARCH"))
    }
}
