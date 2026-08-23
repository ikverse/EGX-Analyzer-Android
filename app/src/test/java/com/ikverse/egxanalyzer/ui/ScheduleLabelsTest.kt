package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.JobTrigger
import com.ikverse.egxanalyzer.model.JobWork
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.ScheduledJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The lines that say whether a schedule is alive.
 *
 * Worth testing for the same reason the schedule exists at all: the failure mode here is silence.
 * Nothing fires, nothing says so, and the only way to notice from the outside is a card claiming a
 * next run. A card that says "next Sunday 18:00" over a switch that is off, or over a job this
 * build cannot run, is worse than one that says nothing - it is the app reporting a promise it has
 * no intention of keeping.
 */
class ScheduleLabelsTest {

    private val cairo = ScheduleClock.ZONE

    private fun at(date: String, time: String): Instant =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(cairo).toInstant()

    /** Thursday 20 August 2026, mid-morning. */
    private val now = at("2026-08-20", "09:00")

    private val evenings = JobTrigger.Repeat(ScheduleClock.tradingDays, LocalTime.of(18, 0))

    private fun job(
        name: String = "Evening prices",
        enabled: Boolean = true,
        trigger: JobTrigger = evenings,
        work: JobWork = JobWork.PriceRefresh,
        lastFiredAt: Instant? = null,
        lastOutcome: JobOutcome = JobOutcome.NEVER,
        lastMessage: String? = null,
    ) = ScheduledJob(
        id = name,
        name = name,
        enabled = enabled,
        trigger = trigger,
        work = work,
        lastFiredAt = lastFiredAt,
        lastOutcome = lastOutcome,
        lastMessage = lastMessage,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun `a moment near today is said the way a reader would say it`() {
        assertEquals("today 18:00", whenLabel(at("2026-08-20", "18:00"), now))
        assertEquals("tomorrow 07:30", whenLabel(at("2026-08-21", "07:30"), now))
        assertEquals("yesterday 18:00", whenLabel(at("2026-08-19", "18:00"), now))
    }

    @Test
    fun `a moment inside the week is named by its day`() {
        assertEquals("Sun 18:00", whenLabel(at("2026-08-23", "18:00"), now))
        assertEquals("last Sun 18:00", whenLabel(at("2026-08-16", "18:00"), now))
    }

    @Test
    fun `past a week the reader needs the date, not the weekday`() {
        // Seven days out, "Thu" would be true of today as well and says nothing about which one.
        assertEquals("2026-08-27 18:00", whenLabel(at("2026-08-27", "18:00"), now))
        assertEquals("2026-08-06 18:00", whenLabel(at("2026-08-06", "18:00"), now))
    }

    @Test
    fun `the time is read off the exchange's clock, not the phone's`() {
        // The same instant, asked for in Cairo. A phone in another zone must not shift the hour a
        // schedule reports, or the card disagrees with the alarm that is actually booked.
        val eighteenCairo = at("2026-08-20", "18:00")
        assertEquals("today 18:00", whenLabel(eighteenCairo, now, cairo))
    }

    @Test
    fun `the summary leads with whatever is actually stopping the schedules`() {
        val one = listOf(job())
        assertEquals("Nothing scheduled.", scheduleSummary(emptyList(), true, now))
        assertEquals("Schedules are switched off on this phone.", scheduleSummary(one, false, now))
        // Not just "nothing will run" - the reason, because it is the thing the reader has to
        // undo, and the card is the only place they would see it.
        assertEquals(
            "Nothing will run - Switched off.",
            scheduleSummary(listOf(job(enabled = false)), true, now),
        )
    }

    @Test
    fun `the card and the row never disagree about one job`() {
        val paid = job("Morning analysis", work = analysisWork("Signals"))
        // The row says the paid switch is off. The card used to promise a next run for the same
        // job, because it only looked at whether the job was enabled - so the app said two
        // different things about one schedule on one screen.
        assertEquals(
            "Paid runs are switched off",
            nextRunLine(paid, true, now, paidAllowed = false),
        )
        assertEquals(
            "Nothing will run - Paid runs are switched off.",
            scheduleSummary(listOf(paid), true, now, paidAllowed = false),
        )
    }

    @Test
    fun `the summary names the job that runs next, not just the time`() {
        val evening = job("Evening prices")
        val morning = job("Morning prices", trigger = JobTrigger.Repeat(ScheduleClock.tradingDays, LocalTime.of(7, 0)))
        // 07:00 is already past at 09:00, so the evening one leads even though it is listed second.
        assertEquals(
            "Next: Evening prices today 18:00",
            scheduleSummary(listOf(morning, evening), true, now),
        )
    }

    @Test
    fun `a job this build cannot run is never counted as the next one`() {
        val foreign = job("Morning analysis", work = JobWork.Unsupported("ANALYSIS"))
        assertEquals(
            "Nothing will run - This version cannot run it.",
            scheduleSummary(listOf(foreign), true, now),
        )
    }

    @Test
    fun `a spent one-shot leaves nothing to promise`() {
        val once = job(
            trigger = JobTrigger.Once(LocalDateTime.parse("2026-08-19T06:00")),
            lastFiredAt = at("2026-08-19", "06:00"),
        )
        assertEquals("Nothing left to run.", scheduleSummary(listOf(once), true, now))
        assertEquals("Done - it only ran once", nextRunLine(once, true, now))
    }

    @Test
    fun `a job says why it will not run rather than when it would have`() {
        assertEquals(
            "This version cannot run it",
            nextRunLine(job(work = JobWork.Unsupported("ANALYSIS")), true, now),
        )
        assertEquals("Schedules are off on this phone", nextRunLine(job(), false, now))
        assertEquals("Switched off", nextRunLine(job(enabled = false), true, now))
        assertEquals("Next today 18:00", nextRunLine(job(), true, now))
    }

    @Test
    fun `the last run line says which schedule, when, and what came of it`() {
        val ran = job(
            lastFiredAt = at("2026-08-19", "18:00"),
            lastOutcome = JobOutcome.SUCCEEDED,
            lastMessage = "Priced 42/45",
        )
        assertEquals("Evening prices · yesterday 18:00 · Priced 42/45", lastRunLine(ran, now))
    }

    @Test
    fun `a schedule that cannot work is refused before it is saved`() {
        val cairoNow = LocalDateTime.parse("2026-08-20T09:00")
        assertEquals("Give the schedule a name.", triggerProblem(evenings, "  ", cairoNow))
        assertEquals(
            "Choose at least one day.",
            triggerProblem(JobTrigger.Repeat(emptySet(), LocalTime.of(18, 0)), "Prices", cairoNow),
        )
        assertEquals(
            "That moment has passed. Choose a date and time still ahead.",
            triggerProblem(
                JobTrigger.Once(LocalDateTime.parse("2026-08-20T08:00")),
                "One off",
                cairoNow,
            ),
        )
        assertNull(triggerProblem(evenings, "Evening prices", cairoNow))
        assertNull(
            triggerProblem(
                JobTrigger.Once(LocalDateTime.parse("2026-08-20T18:00")),
                "One off",
                cairoNow,
            ),
        )
    }

    @Test
    fun `an outcome is coloured by whether anyone needs to do something about it`() {
        assertEquals(StatusTone.GOOD, JobOutcome.SUCCEEDED.tone())
        assertEquals(StatusTone.BAD, JobOutcome.FAILED.tone())
        // Missed is red too: it means the phone was asleep when it mattered, which is the one
        // thing about a schedule the owner would want to know.
        assertEquals(StatusTone.BAD, JobOutcome.MISSED.tone())
        // Skipped is not. The job looked, there was nothing to do, and that is it working.
        assertEquals(StatusTone.NEUTRAL, JobOutcome.SKIPPED.tone())
        assertEquals(StatusTone.NEUTRAL, JobOutcome.NEVER.tone())
    }

    @Test
    fun `a job named for one weekday still reads as that weekday`() {
        val mondays = job(trigger = JobTrigger.Repeat(setOf(DayOfWeek.MONDAY), LocalTime.of(7, 30)))
        assertEquals("Next Mon 07:30", nextRunLine(mondays, true, now))
    }

    private fun analysisWork(vararg names: String) = JobWork.Analysis(
        channels = names.mapIndexed { index, name -> AnalysedChannel(index.toLong(), name) },
        contentTypes = setOf(AnalysisContentType.IMAGES),
    )

    @Test
    fun `a schedule names the chats it covers, so it can be checked without opening it`() {
        assertEquals("Signals", coverageLine(analysisWork("Signals")))
        assertEquals("Signals, Calls", coverageLine(analysisWork("Signals", "Calls")))
        assertEquals(
            "Signals, Calls +2 more",
            coverageLine(analysisWork("Signals", "Calls", "Picks", "Alerts")),
        )
    }

    @Test
    fun `a price refresh has no chats to name`() {
        // It reads every stock the record already knows about, which is not a list worth printing
        // on a row - and a line saying nothing is worse than no line.
        assertNull(coverageLine(JobWork.PriceRefresh))
        assertNull(coverageLine(JobWork.Unsupported("ANALYSIS")))
    }

    @Test
    fun `a paid job says the switch is off rather than promising a run`() {
        val paid = job(work = analysisWork("Signals"))
        assertEquals(
            "Paid runs are switched off",
            nextRunLine(paid, true, now, paidAllowed = false),
        )
    }

    @Test
    fun `a paid job with no credential says that instead`() {
        val paid = job(work = analysisWork("Signals"))
        assertEquals(
            "No provider credential saved",
            nextRunLine(paid, true, now, paidAllowed = true, hasCredential = false),
        )
    }

    @Test
    fun `a job whose chats have all gone says so`() {
        val paid = job(work = analysisWork("Signals"))
        // The frozen chat is id 0; the app now knows about 7 and 8 only.
        assertEquals(
            "Its chats are no longer in the app",
            nextRunLine(paid, true, now, knownChannelIds = setOf(7L, 8L)),
        )
    }

    @Test
    fun `losing one chat of several is not worth a warning`() {
        val paid = job(work = analysisWork("Signals", "Calls"))
        // Ids 0 and 1 were frozen; only 1 survives. The run still reads that one, so the card goes
        // on saying when it happens rather than crying about a schedule that works.
        assertEquals(
            "Next today 18:00",
            nextRunLine(paid, true, now, knownChannelIds = setOf(1L)),
        )
    }

    @Test
    fun `an empty chat list is not evidence that the chats have gone`() {
        val paid = job(work = analysisWork("Signals"))
        // Exactly the state of a cold start: the alarm woke the app and Telegram has not loaded
        // yet. Claiming the schedule is broken here would be the wrong alarm at the worst moment.
        assertEquals("Next today 18:00", nextRunLine(paid, true, now, knownChannelIds = emptySet()))
    }

    @Test
    fun `the switches are reported before anything a job could be waiting on`() {
        val paid = job(work = analysisWork("Signals"))
        // Everything is wrong at once. The master switch is what the reader has to fix first, and
        // a card listing four problems fixes none of them.
        assertEquals(
            "Schedules are off on this phone",
            nextRunLine(paid, false, now, paidAllowed = false, hasCredential = false),
        )
    }

    @Test
    fun `a periodic job names its next slot rather than its window`() {
        // Mid-morning on a Thursday, so the session is running and the next fire is minutes away.
        // A card that read "10:00-14:45" would be describing the schedule; the reader wants to
        // know whether it is about to do something.
        val periodic = job(
            name = "Session prices",
            trigger = JobTrigger.Interval(
                days = ScheduleClock.tradingDays,
                everyMinutes = 15,
                from = ScheduleClock.sessionStart,
                until = ScheduleClock.sessionEnd,
            ),
        )
        assertEquals(
            "Next today 11:15",
            nextRunLine(periodic, schedulesEnabled = true, now = at("2026-08-20", "11:07")),
        )
        // After the close it names the next session's open, three days out over the weekend, which
        // is the line that shows the market being shut is a thing the schedule knows about.
        assertEquals(
            "Next Sun 10:00",
            nextRunLine(periodic, schedulesEnabled = true, now = at("2026-08-20", "15:00")),
        )
    }
}
