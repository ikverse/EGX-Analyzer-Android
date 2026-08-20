package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.JobTrigger
import com.ikverse.egxanalyzer.model.JobWork
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.ScheduledJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * What the app decides to do when it wakes up, which is mostly a set of decisions not to.
 *
 * Every one of these paths is invisible in use: a job that ran twice, one that ran on a fire it
 * should have passed over, one that quietly did nothing and said nothing - none of them shows up
 * as a crash or an error, and all of them are only ever noticed weeks later as a record that does
 * not add up.
 */
class JobRunnerTest {

    private val cairo = ScheduleClock.ZONE

    private fun at(date: String, time: String): Instant =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(cairo).toInstant()

    private val evenings = JobTrigger.Repeat(ScheduleClock.tradingDays, LocalTime.of(18, 0))

    private fun job(
        enabled: Boolean = true,
        lastFiredAt: Instant? = null,
        graceMinutes: Int = ScheduledJob.DEFAULT_GRACE_MINUTES,
    ) = ScheduledJob(
        id = "prices",
        name = "Evening prices",
        enabled = enabled,
        trigger = evenings,
        work = JobWork.PriceRefresh,
        graceMinutes = graceMinutes,
        lastFiredAt = lastFiredAt,
        createdAt = Instant.EPOCH,
    )

    /** A store that behaves like the table: what was written last is what is read back. */
    private class Recorder(initial: List<ScheduledJob>) {
        var jobs = initial
            private set
        val written = mutableListOf<ScheduledJob>()

        fun record(job: ScheduledJob) {
            written += job
            jobs = jobs.map { if (it.id == job.id) job else it }
        }
    }

    private fun run(
        jobs: List<ScheduledJob>,
        now: Instant,
        schedulesEnabled: Boolean = true,
        perform: suspend (JobWork, Instant) -> String = { _, _ -> "Done" },
    ): Recorder {
        val recorder = Recorder(jobs)
        runBlocking {
            JobRunner(
                jobs = { recorder.jobs },
                record = recorder::record,
                schedulesEnabled = { schedulesEnabled },
                now = { now },
            ).runDue(perform)
        }
        return recorder
    }

    @Test
    fun `a job whose time has come runs, and says what it did`() {
        val recorder = run(listOf(job()), at("2026-08-20", "18:01")) { _, _ -> "Priced 42/45" }

        val served = recorder.written.single()
        assertEquals(JobOutcome.SUCCEEDED, served.lastOutcome)
        assertEquals("Priced 42/45", served.lastMessage)
        // Stamped with the fire it served rather than the moment it ran, so a minute of lateness
        // cannot make the same slot look unserved on the next wake-up.
        assertEquals(at("2026-08-20", "18:00"), served.lastFiredAt)
    }

    @Test
    fun `the work is handed the moment it was due, not the moment it started`() {
        var seen: Instant? = null
        run(listOf(job()), at("2026-08-20", "19:30")) { _, due -> seen = due; "Done" }

        assertEquals(at("2026-08-20", "18:00"), seen)
    }

    @Test
    fun `one fire is served once, however often the app wakes up`() {
        var runs = 0
        val recorder = run(listOf(job()), at("2026-08-20", "18:01")) { _, _ -> runs++; "Done" }
        // Exactly what a boot, an alarm and a launch landing together would do.
        run(recorder.jobs, at("2026-08-20", "18:02")) { _, _ -> runs++; "Done" }
        run(recorder.jobs, at("2026-08-20", "18:40")) { _, _ -> runs++; "Done" }

        assertEquals(1, runs)
    }

    @Test
    fun `a fire missed by more than its grace is passed over rather than run late`() {
        var ran = false
        val recorder = run(
            listOf(job(graceMinutes = 60)),
            at("2026-08-20", "23:00"),
        ) { _, _ -> ran = true; "Done" }

        val served = recorder.written.single()
        assertTrue(!ran)
        assertEquals(JobOutcome.MISSED, served.lastOutcome)
        assertTrue(served.lastMessage!!.contains("5 h"))
        // Still stamped, or every wake-up from here to the next fire reports the same miss again.
        assertEquals(at("2026-08-20", "18:00"), served.lastFiredAt)
    }

    @Test
    fun `work that decides there is nothing to do is not a failure`() {
        val recorder = run(listOf(job()), at("2026-08-20", "18:01")) { _, _ ->
            throw JobSkipped("Prices had already been fetched since this run came due.")
        }

        val served = recorder.written.single()
        assertEquals(JobOutcome.SKIPPED, served.lastOutcome)
        assertEquals("Prices had already been fetched since this run came due.", served.lastMessage)
    }

    @Test
    fun `work that throws is written down rather than lost`() {
        val recorder = run(listOf(job()), at("2026-08-20", "18:01")) { _, _ ->
            error("Yahoo returned 429")
        }

        val served = recorder.written.single()
        assertEquals(JobOutcome.FAILED, served.lastOutcome)
        assertEquals("Yahoo returned 429", served.lastMessage)
        // Marked served even though it failed: the next run is the retry, and re-running a job
        // every time the phone happens to wake is how an unattended failure becomes an unattended
        // loop.
        assertEquals(at("2026-08-20", "18:00"), served.lastFiredAt)
    }

    @Test
    fun `the master switch stops everything without touching a single job`() {
        var ran = false
        val recorder = run(
            listOf(job()),
            at("2026-08-20", "18:01"),
            schedulesEnabled = false,
        ) { _, _ -> ran = true; "Done" }

        assertTrue(!ran)
        // Nothing recorded either: switching schedules off is not the same as them having run, and
        // a job stamped while the switch was off would be skipped when it was switched back on.
        assertTrue(recorder.written.isEmpty())
    }

    @Test
    fun `a switched-off job is left alone while the others run`() {
        val off = job(enabled = false).copy(id = "off")
        val on = job().copy(id = "on")
        val recorder = run(listOf(off, on), at("2026-08-20", "18:01"))

        assertEquals(listOf("on"), recorder.written.map(ScheduledJob::id))
    }

    @Test
    fun `free work runs without anything being allowed to spend credits`() {
        val recorder = run(listOf(job()), at("2026-08-20", "18:01"))

        assertEquals(JobOutcome.SUCCEEDED, recorder.written.single().lastOutcome)
    }

    /** The same schedule, but pointed at work that sends a paid request. */
    private fun paidJob() = job().copy(
        id = "analysis",
        name = "Morning analysis",
        work = JobWork.Analysis(
            channels = listOf(AnalysedChannel(1L, "A channel")),
            contentTypes = setOf(AnalysisContentType.IMAGES),
        ),
    )

    @Test
    fun `paid work does not run until it has been allowed, and says so`() {
        var ran = false
        val recorder = Recorder(listOf(paidJob()))
        runBlocking {
            JobRunner(
                jobs = { recorder.jobs },
                record = recorder::record,
                schedulesEnabled = { true },
                now = { at("2026-08-20", "18:01") },
            ).runDue { _, _ -> ran = true; "Sent" }
        }

        // Refusing by default is the whole point: a paid job type must not arm itself by existing.
        assertTrue(!ran)
        val served = recorder.written.single()
        assertEquals(JobOutcome.SKIPPED, served.lastOutcome)
        assertTrue(served.lastMessage!!.contains("cloud credits"))
        // Stamped, so the refusal is reported once rather than on every wake-up until the fire ages
        // out - and so that turning the switch on does not immediately run a fire from hours ago.
        assertEquals(at("2026-08-20", "18:00"), served.lastFiredAt)
    }

    @Test
    fun `paid work runs once it has been allowed`() {
        var sent = false
        val recorder = Recorder(listOf(paidJob()))
        runBlocking {
            JobRunner(
                jobs = { recorder.jobs },
                record = recorder::record,
                schedulesEnabled = { true },
                paidWorkAllowed = { true },
                now = { at("2026-08-20", "18:01") },
            ).runDue { _, _ -> sent = true; "Saved 6 recommendations." }
        }

        assertTrue(sent)
        assertEquals(JobOutcome.SUCCEEDED, recorder.written.single().lastOutcome)
    }

    @Test
    fun `allowing paid work does not switch schedules on by itself`() {
        var ran = false
        val recorder = Recorder(listOf(paidJob()))
        runBlocking {
            JobRunner(
                jobs = { recorder.jobs },
                record = recorder::record,
                // The two switches are separate and both have to be on. This is the combination
                // someone lands in by allowing paid runs and then pausing everything.
                schedulesEnabled = { false },
                paidWorkAllowed = { true },
                now = { at("2026-08-20", "18:01") },
            ).runDue { _, _ -> ran = true; "Sent" }
        }

        assertTrue(!ran)
        assertTrue(recorder.written.isEmpty())
    }
}
