package com.ikverse.egxanalyzer.data

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
    fun `free work runs even though nothing is allowed to spend credits yet`() {
        // The guard that refuses paid work is on by default and has no way to be turned off in this
        // version. What has to be true today is that it does not stand in the way of work that
        // costs nothing - the paid branch itself has nothing to test against until a paid job type
        // exists to point it at.
        val recorder = run(listOf(job()), at("2026-08-20", "18:01"))

        assertEquals(JobOutcome.SUCCEEDED, recorder.written.single().lastOutcome)
    }
}
