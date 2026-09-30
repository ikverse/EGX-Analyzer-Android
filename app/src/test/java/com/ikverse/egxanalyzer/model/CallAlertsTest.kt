package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Calls whose buy zone the price has just traded into.
 *
 * An unprompted notification about a stock nobody has committed anything to is the easiest feature
 * in the app to make unbearable, so almost everything here is about the times it must stay **quiet**
 * - the first sweep, a price that was already in the band, a call the user already holds, a
 * re-posting of a call it has already spoken about.
 */
class CallAlertsTest {

    private val day = LocalDate.of(2026, 8, 10)

    private fun call(
        ticker: String = "AMOC",
        channel: String = "source-one",
        outcome: Outcome = Outcome.OPEN,
        entryLow: Double? = 10.0,
        entryHigh: Double? = 10.5,
        repeatOf: LocalDate? = null,
    ) = ScoredCall(
        ticker = ticker,
        companyEnglish = null,
        companyArabic = null,
        channel = channel,
        channelId = 1L,
        openedOn = day,
        entryLow = entryLow,
        entryHigh = entryHigh,
        target1 = 12.0,
        target2 = 13.0,
        stopLoss = 9.0,
        outcome = outcome,
        settledOn = null,
        peakHigh = null,
        troughLow = null,
        returnPct = null,
        sessionsElapsed = 0,
        repeatOf = repeatOf,
    )

    private fun priced(ticker: String, close: Double) = LatestPrice(
        session = DailySession(
            ticker,
            day,
            high = close + 0.3,
            low = close - 0.3,
            close = close,
            volume = 1000.0,
        ),
        provisional = false,
    )

    private fun sweep(
        previous: Map<String, CallState>,
        calls: List<ScoredCall>,
        prices: Map<String, LatestPrice> = mapOf("AMOC" to priced("AMOC", 10.2)),
        held: Set<String> = emptySet(),
    ) = CallAlerts.sweep(previous, calls, { prices[it] }, held)

    @Test
    fun `the first sweep records everything and announces nothing`() {
        val alerts = sweep(emptyMap(), listOf(call()))

        // Without this the first refresh after shipping would announce every call whose band the
        // price happens to be sitting in - a month of history arriving at once as though it had
        // all just happened.
        assertTrue(alerts.changes.isEmpty())
        assertEquals(mapOf(call().alertId to CallState(inBand = true)), alerts.record)
    }

    @Test
    fun `crossing into the band is announced once`() {
        val subject = call()
        val alerts = sweep(mapOf(subject.alertId to CallState(inBand = false)), listOf(subject))

        assertEquals(1, alerts.changes.size)
        assertEquals(10.2, alerts.changes.single().price, 1e-9)

        // Told once. The next sweep sees the stored reading and says nothing, or a stock sitting in
        // its band would be announced on every price refresh for the rest of the call's life.
        val again = sweep(alerts.record, listOf(subject))
        assertTrue(again.changes.isEmpty())
        assertTrue(again.record.isEmpty())
    }

    @Test
    fun `leaving the band is recorded and never announced`() {
        val subject = call()
        val alerts = sweep(
            previous = mapOf(subject.alertId to CallState(inBand = true)),
            calls = listOf(subject),
            prices = mapOf("AMOC" to priced("AMOC", 11.4)),
        )

        // A price drifting back out is not news, and announcing both directions would double every
        // notification for a stock moving around inside its own zone.
        assertTrue(alerts.changes.isEmpty())
        assertEquals(mapOf(subject.alertId to CallState(inBand = false)), alerts.record)
    }

    @Test
    fun `a call the user already holds is left to the portfolio`() {
        val subject = call()
        val alerts = sweep(
            previous = mapOf(subject.alertId to CallState(inBand = false)),
            calls = listOf(subject),
            held = setOf(subject.positionId),
        )

        // Two notifications about one stock from two features is how a channel gets switched off.
        assertTrue(alerts.changes.isEmpty())
    }

    @Test
    fun `a settled call says nothing however the price wanders`() {
        val subject = call(outcome = Outcome.FULL_HIT)

        val alerts = sweep(mapOf(subject.alertId to CallState(inBand = false)), listOf(subject))

        // The price coming back through the buy zone of a call that hit its target three weeks ago
        // is a coincidence, not an opportunity.
        assertTrue(alerts.changes.isEmpty())
    }

    @Test
    fun `a re-posting does not speak for the call it repeats`() {
        val first = call()
        val repost = call(repeatOf = day)

        val alerts = sweep(
            previous = mapOf(first.alertId to CallState(inBand = false)),
            calls = listOf(first, repost),
        )

        // One bet, one notification. Both would say the same thing twice on the morning a standing
        // recommendation comes into range.
        assertEquals(1, alerts.changes.size)
    }

    @Test
    fun `two channels calling one stock are two alerts because they printed two bands`() {
        // 10.2 is inside the first band and below the second. Keyed on the holding they share, the
        // second call could never be announced at all.
        val wide = call(channel = "source-one", entryLow = 10.0, entryHigh = 10.5)
        val higher = call(channel = "source-two", entryLow = 11.0, entryHigh = 11.5)

        val alerts = sweep(
            previous = mapOf(
                wide.alertId to CallState(inBand = false),
                higher.alertId to CallState(inBand = false),
            ),
            calls = listOf(wide, higher),
        )

        assertEquals(listOf("source-one"), alerts.changes.map { it.call.channel })
        assertEquals(CallState(inBand = true), alerts.record[wide.alertId])
        // The second is unchanged and therefore absent from the record, not rewritten.
        assertTrue(higher.alertId !in alerts.record)
    }

    @Test
    fun `a call whose stock has no price is skipped rather than guessed at`() {
        val subject = call()

        val alerts = sweep(
            previous = mapOf(subject.alertId to CallState(inBand = false)),
            calls = listOf(subject),
            prices = emptyMap(),
        )

        assertTrue(alerts.changes.isEmpty())
        // And it is not forgotten either: a stock the feed has gone quiet about will be priced
        // again, and dropping the reading would announce the band it was already in as new.
        assertTrue(alerts.forgotten.isEmpty())
    }

    @Test
    fun `a call the record no longer holds is forgotten`() {
        val gone = call(ticker = "SWDY")

        val alerts = sweep(mapOf(gone.alertId to CallState(inBand = true)), listOf(call()))

        assertEquals(setOf(gone.alertId), alerts.forgotten)
    }
}
