package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisSchedule
import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.ScheduleClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * That every fire is written down, including the ones that did nothing.
 *
 * Silence is how a scheduler fails on this platform: the phone sleeps, nothing runs, and nothing
 * says so. Each case below ends in a line a reader could find the next morning, and the two that
 * must never reach the work at all - a fire past its grace, and a paid run nobody allowed - are
 * checked by watching whether the work was called rather than by reading the outcome afterwards.
 *
 * Now that a phone can keep four of these, the cases that matter most are the ones about a list:
 * that two schedules owing the same morning both get served, in the order they came due, and that
 * one of them failing does not quietly take the others with it.
 */
class JobRunnerTest {

    private val morning = LocalTime.of(7, 0)

    @Test
    fun `a served fire records what the work said`() {
        val written = mutableListOf<AnalysisSchedule>()
        val served = runBlocking {
            runner(written, now = at("2026-08-20", "07:01")).runDue { _, _ -> "Saved 12 calls" }
        }.single()
        assertEquals(JobOutcome.SUCCEEDED, served.lastOutcome)
        assertEquals("Saved 12 calls", served.lastMessage)
        assertEquals(listOf(served), written)
    }

    /**
     * The scheduled moment and not the moment it ran, so a run that started twenty minutes late
     * still counts as having filled its 07:00 slot. Comparing real start times would let one slot
     * fire twice on a phone whose clock moved.
     */
    @Test
    fun `the fire recorded is the one it was due for`() {
        val written = mutableListOf<AnalysisSchedule>()
        val served = runBlocking {
            runner(written, now = at("2026-08-20", "07:20")).runDue { _, _ -> "ran" }
        }.single()
        assertEquals(at("2026-08-20", "07:00"), served.lastFiredAt)
    }

    @Test
    fun `a skip is not a failure`() {
        val written = mutableListOf<AnalysisSchedule>()
        val served = runBlocking {
            runner(written, now = at("2026-08-20", "07:01")).runDue { _, _ ->
                throw JobSkipped("Nothing new since the 07:00 report of the 2026-08-20 session.")
            }
        }.single()
        assertEquals(JobOutcome.SKIPPED, served.lastOutcome)
        assertEquals(
            "Nothing new since the 07:00 report of the 2026-08-20 session.",
            served.lastMessage,
        )
    }

    @Test
    fun `a thrown run is recorded as failed`() {
        val written = mutableListOf<AnalysisSchedule>()
        val served = runBlocking {
            runner(written, now = at("2026-08-20", "07:01")).runDue { _, _ ->
                error("the provider refused")
            }
        }.single()
        assertEquals(JobOutcome.FAILED, served.lastOutcome)
        assertEquals("the provider refused", served.lastMessage)
    }

    @Test
    fun `a fire past its grace is recorded as missed and never run`() {
        val written = mutableListOf<AnalysisSchedule>()
        var ran = false
        val served = runBlocking {
            runner(written, now = at("2026-08-20", "11:00")).runDue { _, _ ->
                ran = true
                "ran"
            }
        }.single()
        assertFalse(ran)
        assertEquals(JobOutcome.MISSED, served.lastOutcome)
        assertTrue(served.lastMessage.orEmpty().startsWith("Missed by 4 h"))
    }

    /**
     * The whole of the money guard. Every schedule left in the app sends a paid request, so a
     * second switch nobody turned on is the one thing between the clock and the owner's account -
     * and it has to be checked before the work, not inside it.
     */
    @Test
    fun `a paid run nobody allowed is passed over and says so`() {
        val written = mutableListOf<AnalysisSchedule>()
        var ran = false
        val served = runBlocking {
            runner(written, now = at("2026-08-20", "07:01"), paidAllowed = false).runDue { _, _ ->
                ran = true
                "ran"
            }
        }.single()
        assertFalse(ran)
        assertEquals(JobOutcome.SKIPPED, served.lastOutcome)
        assertEquals(
            "Scheduled runs are not allowed to spend cloud credits on this phone.",
            served.lastMessage,
        )
    }

    @Test
    fun `refusing to spend is the default`() {
        val written = mutableListOf<AnalysisSchedule>()
        val runner = JobRunner(
            schedules = { listOf(schedule()) },
            record = { written += it },
            now = { at("2026-08-20", "07:01") },
        )
        val served = runBlocking { runner.runDue { _, _ -> "ran" } }.single()
        assertEquals(JobOutcome.SKIPPED, served.lastOutcome)
    }

    @Test
    fun `nothing owed writes nothing`() {
        val written = mutableListOf<AnalysisSchedule>()
        val served = runBlocking {
            runner(written, now = at("2026-08-20", "06:00")).runDue { _, _ -> "ran" }
        }
        assertTrue(served.isEmpty())
        assertTrue(written.isEmpty())
    }

    /**
     * Two schedules owing the same morning are two promises, and both are kept. In the order the
     * fires came due, because the later one is usually the one that finds the earlier has already
     * read those chats - and it can only find that if the earlier one has finished.
     */
    @Test
    fun `every schedule that owes a fire is served, oldest first`() {
        val written = mutableListOf<AnalysisSchedule>()
        val order = mutableListOf<Instant>()
        val served = runBlocking {
            runner(
                written,
                now = at("2026-08-20", "12:30"),
                schedules = listOf(
                    schedule().copy(id = 1, at = LocalTime.of(12, 0)),
                    schedule().copy(id = 2, at = LocalTime.of(11, 0)),
                ),
            ).runDue { _, due ->
                order += due
                "ran"
            }
        }
        assertEquals(listOf(at("2026-08-20", "11:00"), at("2026-08-20", "12:00")), order)
        assertEquals(listOf(2L, 1L), served.map { it.id })
        assertEquals(served, written)
    }

    /**
     * They are separate promises. The one thing worse than a run that failed is a run that never
     * happened because another one did.
     */
    @Test
    fun `one schedule failing does not stop the next`() {
        val written = mutableListOf<AnalysisSchedule>()
        val served = runBlocking {
            runner(
                written,
                now = at("2026-08-20", "12:30"),
                schedules = listOf(
                    schedule().copy(id = 1, at = LocalTime.of(11, 0)),
                    schedule().copy(id = 2, at = LocalTime.of(12, 0)),
                ),
            ).runDue { schedule, _ ->
                if (schedule.id == 1L) error("the provider refused") else "Saved 3 calls"
            }
        }
        assertEquals(listOf(JobOutcome.FAILED, JobOutcome.SUCCEEDED), served.map { it.lastOutcome })
    }

    /** A schedule owing nothing is left alone by a sweep that served the one beside it. */
    @Test
    fun `only the schedules that owe a fire are written`() {
        val written = mutableListOf<AnalysisSchedule>()
        runBlocking {
            runner(
                written,
                now = at("2026-08-20", "07:01"),
                schedules = listOf(
                    schedule().copy(id = 1, at = morning),
                    schedule().copy(id = 2, at = LocalTime.of(12, 0)),
                ),
            ).runDue { _, _ -> "ran" }
        }
        assertEquals(listOf(1L), written.map { it.id })
    }

    private fun runner(
        written: MutableList<AnalysisSchedule>,
        now: Instant,
        paidAllowed: Boolean = true,
        schedules: List<AnalysisSchedule> = listOf(schedule()),
    ) = JobRunner(
        schedules = { schedules },
        record = { written += it },
        paidWorkAllowed = { paidAllowed },
        now = { now },
    )

    private fun schedule() = AnalysisSchedule(
        enabled = true,
        at = morning,
        channels = listOf(AnalysedChannel(1, "Signals")),
        contentTypes = setOf(AnalysisContentType.entries.first()),
        armedAt = at("2026-08-19", "12:00"),
    )

    private fun at(date: String, time: String): Instant =
        LocalDateTime.parse("${date}T$time").atZone(ScheduleClock.ZONE).toInstant()
}
