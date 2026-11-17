package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** A source's last 30 and 60 days against its whole record, and when that is worth a tag. */
class ChannelFormTest {
    private val today = LocalDate.of(2026, 11, 17)

    private fun call(channel: String, daysAgo: Long, outcome: Outcome, repeat: Boolean = false) = ScoredCall(
        ticker = "COMI", companyEnglish = null, companyArabic = null, channel = channel, channelId = null,
        openedOn = today.minusDays(daysAgo + 5), entryLow = 10.0, entryHigh = 10.2, target1 = 11.0, target2 = 12.0,
        stopLoss = 9.0, outcome = outcome, settledOn = today.minusDays(daysAgo), peakHigh = null, troughLow = null,
        returnPct = null, sessionsElapsed = 5, repeatOf = if (repeat) today.minusDays(daysAgo + 6) else null,
    )

    private fun hits(channel: String, n: Int, daysAgo: Long) = List(n) { call(channel, daysAgo, Outcome.PARTIAL_HIT) }
    private fun stops(channel: String, n: Int, daysAgo: Long) = List(n) { call(channel, daysAgo, Outcome.STOPPED) }

    @Test
    fun windowsCountBySettledDay() {
        val calls = hits("A", 2, 10) + stops("A", 1, 45) + hits("A", 3, 200) + listOf(call("A", 5, Outcome.OPEN))
        val form = PerformanceCalculator.channelForm(calls, today).getValue("A")
        assertEquals(FormWindow(2, 2), form.last30)
        assertEquals(FormWindow(3, 2), form.last60)
        assertEquals(FormWindow(6, 5), form.lifetime)
    }

    @Test
    fun aSourceGoingColdSaysSo() {
        // Lifetime 20 of 30 hit; the last 60 days 1 of 6.
        val calls = hits("A", 19, 300) + stops("A", 5, 300) + hits("A", 1, 20) + stops("A", 5, 20)
        assertEquals(FormTrend.COOLING, PerformanceCalculator.channelForm(calls, today).getValue("A").trend)
    }

    @Test
    fun aSourceComingGoodSaysSo() {
        val calls = stops("B", 20, 300) + hits("B", 5, 300) + hits("B", 6, 20)
        assertEquals(FormTrend.WARMING, PerformanceCalculator.channelForm(calls, today).getValue("B").trend)
    }

    @Test
    fun tooFewRecentCallsSayNothing() {
        val calls = hits("C", 20, 300) + stops("C", 4, 20)
        assertNull(PerformanceCalculator.channelForm(calls, today).getValue("C").trend)
    }

    @Test
    fun repeatsAreLeftOut() {
        val calls = hits("D", 1, 10) + listOf(call("D", 10, Outcome.STOPPED, repeat = true))
        assertEquals(FormWindow(1, 1), PerformanceCalculator.channelForm(calls, today).getValue("D").last30)
    }
}
