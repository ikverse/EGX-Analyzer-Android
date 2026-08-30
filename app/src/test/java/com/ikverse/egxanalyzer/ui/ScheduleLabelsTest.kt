package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisAim
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisSchedule
import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.ScheduleClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The sentences this feature is judged by.
 *
 * Nothing here computes anything the app depends on; all of it is what the reader is told, which
 * on a feature whose failure mode is silence is the part that decides whether a broken phone is
 * ever noticed. A line promising "Next Sun 07:00" over a run that is going to be passed over is
 * the app misleading the one person who could fix it.
 *
 * 2026-08-20 is a Thursday.
 */
class ScheduleLabelsTest {

    private val morning = LocalTime.of(7, 0)
    private val now = at("2026-08-20", "06:00")

    @Test
    fun `a schedule with nothing wrong says what it covers and when it next runs`() {
        assertEquals(
            "Signals · every trading day · next today 07:00",
            scheduleDetail(schedule(), now),
        )
        assertEquals(
            "Signals · every trading day · next Sun 07:00",
            scheduleDetail(schedule(), at("2026-08-20", "08:00")),
        )
    }

    @Test
    fun `what is blocking it is said instead of a time it will never reach`() {
        assertEquals("Switched off", blockedReason(schedule(enabled = false)))
        assertEquals("No days chosen", blockedReason(schedule(days = emptySet())))
        assertEquals("No chats chosen yet", blockedReason(schedule(channels = emptyList())))
        assertEquals("Paid runs are switched off", blockedReason(schedule(), paidAllowed = false))
        assertEquals(
            "No provider credential saved",
            blockedReason(schedule(), hasCredential = false),
        )
        assertEquals(
            "Its chats are no longer in the app",
            blockedReason(schedule(), knownChannelIds = setOf(99L)),
        )
    }

    /**
     * The order is what the reader has to fix first. A line listing four problems at once fixes
     * none of them, and telling someone their credential is missing on a schedule that is switched
     * off is answering a question they have not asked yet.
     */
    @Test
    fun `the switch is reported before anything the run needs`() {
        assertEquals(
            "Switched off",
            blockedReason(
                schedule(enabled = false),
                paidAllowed = false,
                hasCredential = false,
            ),
        )
    }

    /**
     * The money switch and the credential stop all four schedules at once, so a list of rows would
     * say the same sentence four times over the switch that answers it. Split so it can be said
     * once, above them.
     */
    @Test
    fun `the shared reasons are kept apart from the ones a row can fix`() {
        assertNull(scheduleBlocker(schedule()))
        assertEquals("Paid runs are switched off", sharedBlocker(false, hasCredential = true))
        assertEquals("No provider credential saved", sharedBlocker(true, hasCredential = false))
        assertNull(sharedBlocker(paidAllowed = true, hasCredential = true))
        assertEquals("No days chosen", scheduleBlocker(schedule(days = emptySet())))
    }

    /**
     * Two whole weeks are worth a phrase; anything else is worth naming. "Every trading day" is
     * the week a schedule starts on, so a reader who sees it knows at once that no weekend day is
     * being paid for.
     */
    @Test
    fun `a whole week is named and any other is listed, starting on Sunday`() {
        assertEquals("every trading day", daysLabel(ScheduleClock.tradingDays))
        assertEquals("every day", daysLabel(DayOfWeek.entries.toSet()))
        assertEquals("Sun, Tue", daysLabel(setOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY)))
        assertEquals("Sun, Sat", daysLabel(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))
        assertEquals("Fri", daysLabel(setOf(DayOfWeek.FRIDAY)))
        assertEquals("No days chosen", daysLabel(emptySet()))
    }

    /**
     * A weekend schedule is a real one, so nothing about it is reported as broken. The clock aims
     * it at the Sunday session - see `ScheduleClockTest` - and the row says which day it keeps.
     */
    @Test
    fun `a schedule on a weekend day is not treated as blocked`() {
        val friday = schedule(days = setOf(DayOfWeek.FRIDAY))
        assertNull(scheduleBlocker(friday))
        // Thursday morning, so the Friday fire is tomorrow - and a moment that close is said
        // relatively, which is `whenLabel` doing its job rather than losing the day.
        assertEquals("Signals · Fri · next tomorrow 07:00", scheduleDetail(friday, now))
        // An hour after its own fire the next one is a week out, which is past the range a
        // weekday name is any use over - so a schedule that keeps one day always names the
        // date, and "next Fri" never appears on one.
        assertEquals(
            "Signals · Fri · next 2026-08-28 07:00",
            scheduleDetail(friday, at("2026-08-21", "08:00")),
        )
    }

    // ------------------------------------------------------------------ the list, in one line

    @Test
    fun `an empty list and a list that is all off say so`() {
        assertEquals("Nothing scheduled", schedulesSummary(emptyList(), now).text)
        assertEquals(
            "2 scheduled, all switched off",
            schedulesSummary(
                listOf(schedule(enabled = false), schedule(enabled = false)),
                now,
            ).text,
        )
    }

    @Test
    fun `the summary counts what is on and names the earliest fire any of it reaches`() {
        val summary = schedulesSummary(
            listOf(schedule(), schedule().copy(id = 2, at = LocalTime.of(12, 0))),
            now,
        )
        assertEquals("2 on · next today 07:00", summary.text)
        assertFalse(summary.warning)
    }

    /**
     * A count that included a schedule which cannot run would be the summary promising a next run
     * for something the row underneath reports as blocked - the exact disagreement this one line
     * exists to prevent.
     */
    @Test
    fun `a blocked schedule is counted separately and read in red`() {
        val summary = schedulesSummary(
            listOf(schedule(), schedule().copy(id = 2, at = LocalTime.of(6, 0), channels = emptyList())),
            now,
        )
        assertEquals("1 on · next today 07:00 · 1 blocked", summary.text)
        assertTrue(summary.warning)
    }

    @Test
    fun `a reason that stops all of them is said before any count`() {
        val summary = schedulesSummary(listOf(schedule(), schedule()), now, paidAllowed = false)
        assertEquals("Paid runs are switched off", summary.text)
        assertTrue(summary.warning)
    }

    @Test
    fun `with every schedule blocked the summary says why rather than counting`() {
        val summary = schedulesSummary(listOf(schedule(channels = emptyList())), now)
        assertEquals("No chats chosen yet", summary.text)
        assertTrue(summary.warning)
    }

    /**
     * An empty chat list is a cold start, not evidence the chats have gone - Telegram has not
     * loaded yet, and the wrong alarm at the worst moment is worse than none.
     */
    @Test
    fun `a chat list that has not loaded is no opinion`() {
        assertNull(blockedReason(schedule(), knownChannelIds = emptySet()))
    }

    @Test
    fun `losing one chat of several is not losing the schedule`() {
        val many = schedule(
            channels = listOf(AnalysedChannel(1, "Signals"), AnalysedChannel(2, "Calls")),
        )
        assertNull(blockedReason(many, knownChannelIds = setOf(2L)))
    }

    @Test
    fun `the chats are named, and counted past two`() {
        assertEquals("Signals", coverageLine(schedule()))
        assertEquals(
            "Signals, Calls",
            coverageLine(schedule(channels = channels("Signals", "Calls"))),
        )
        assertEquals(
            "Signals, Calls +2 more",
            coverageLine(schedule(channels = channels("Signals", "Calls", "Picks", "Alerts"))),
        )
        assertNull(coverageLine(schedule(channels = emptyList())))
    }

    @Test
    fun `a moment reads relatively near the present and absolutely past it`() {
        assertEquals("today 07:00", whenLabel(at("2026-08-20", "07:00"), now))
        assertEquals("tomorrow 07:00", whenLabel(at("2026-08-21", "07:00"), now))
        assertEquals("yesterday 07:00", whenLabel(at("2026-08-19", "07:00"), now))
        assertEquals("Sun 07:00", whenLabel(at("2026-08-23", "07:00"), now))
        assertEquals("2026-09-30 07:00", whenLabel(at("2026-09-30", "07:00"), now))
    }

    @Test
    fun `the last run says when and what`() {
        val ran = schedule().copy(
            lastFiredAt = at("2026-08-19", "07:00"),
            lastOutcome = JobOutcome.SUCCEEDED,
            lastMessage = "Saved 12 calls",
        )
        assertEquals("yesterday 07:00 · Saved 12 calls", lastRunLine(ran, now))
    }

    // ------------------------------------------------------------------ the price checkbox

    @Test
    fun `switched off, the refresh line says what happens instead`() {
        val line = marketRefreshLine(enabled = false, note = null, noteAt = 0L, now = now)
        assertEquals(
            "Off. Prices are fetched once a day, the first time you open the app.",
            line.text,
        )
        assertFalse(line.warning)
    }

    /**
     * The two ways the system can stop this working are said first and in the error colour. A line
     * reporting a cheerful last fetch over a phone that is going to sleep between them is a line
     * that lies quietly.
     */
    @Test
    fun `a phone that cannot keep the promise says so before anything else`() {
        val alarms = marketRefreshLine(
            enabled = true,
            note = "Priced 92/92",
            noteAt = at("2026-08-20", "05:45").toEpochMilli(),
            now = now,
            exactAlarms = false,
        )
        assertTrue(alarms.warning)
        assertTrue(alarms.text.startsWith("On, but exact alarms are off"))

        val battery = marketRefreshLine(
            enabled = true,
            note = "Priced 92/92",
            noteAt = at("2026-08-20", "05:45").toEpochMilli(),
            now = now,
            batteryExempt = false,
        )
        assertTrue(battery.warning)
        assertTrue(battery.text.contains("put this app to sleep"))
    }

    /**
     * Never blank. On the day this is switched on there is nothing to report yet, and an empty
     * line reads exactly like one that has stopped working.
     */
    @Test
    fun `with nothing fetched yet it still names the next fetch`() {
        val line = marketRefreshLine(enabled = true, note = null, noteAt = 0L, now = now)
        assertEquals("On. Nothing fetched yet - next today 10:00.", line.text)
        assertFalse(line.warning)
    }

    @Test
    fun `once it has run it reports the last fetch and the next`() {
        val line = marketRefreshLine(
            enabled = true,
            note = "Priced 92/92",
            noteAt = at("2026-08-20", "10:15").toEpochMilli(),
            now = at("2026-08-20", "10:20"),
        )
        assertEquals("Last today 10:15 · Priced 92/92 · next today 10:30", line.text)
        assertFalse(line.warning)
    }

    /**
     * A run that did nothing still writes a line, which is the whole point of keeping one: a
     * schedule whose last word is "Skipped" is diagnosable, and a blank one is not.
     */
    @Test
    fun `a run that did nothing is still reported`() {
        val line = marketRefreshLine(
            enabled = true,
            note = "Skipped - a refresh was already running.",
            noteAt = at("2026-08-20", "10:15").toEpochMilli(),
            now = at("2026-08-20", "10:20"),
        )
        assertTrue(line.text.contains("Skipped - a refresh was already running."))
        assertFalse(line.warning)
    }

    private fun channels(vararg names: String) =
        names.mapIndexed { index, name -> AnalysedChannel(index + 1L, name) }

    private fun schedule(
        enabled: Boolean = true,
        channels: List<AnalysedChannel> = listOf(AnalysedChannel(1, "Signals")),
        days: Set<DayOfWeek> = ScheduleClock.tradingDays,
    ) = AnalysisSchedule(
        enabled = enabled,
        at = morning,
        days = days,
        channels = channels,
        contentTypes = setOf(AnalysisContentType.entries.first()),
        armedAt = at("2026-08-19", "12:00"),
    )

    @Suppress("unused")
    private fun aim(vararg names: String) =
        AnalysisAim(channels(*names), setOf(AnalysisContentType.entries.first()))

    private fun at(date: String, time: String): Instant =
        LocalDateTime.parse("${date}T$time").atZone(ScheduleClock.ZONE).toInstant()
}
