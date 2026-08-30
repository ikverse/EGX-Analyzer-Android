package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisSchedule
import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.ScheduleClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 */
class JobRunnerTest {

    private val morning = LocalTime.of(7, 0)

    @Test
    fun `a served fire records what the work said`() {
        val written = mutableListOf<AnalysisSchedule>()
        val served = runBlocking {
            runner(written, now = at("2026-08-20", "07:01")).runDue { _, _ -> "Saved 12 calls" }
        }
        assertEquals(JobOutcome.SUCCEEDED, served?.lastOutcome)
        assertEquals("Saved 12 calls", served?.lastMessage)
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
        }
        assertEquals(at("2026-08-20", "07:00"), served?.lastFiredAt)
    }

    @Test
    fun `a skip is not a failure`() {
        val written = mutableListOf<AnalysisSchedule>()
        val served = runBlocking {
            runner(written, now = at("2026-08-20", "07:01")).runDue { _, _ ->
                throw JobSkipped("The session is already analysed for these chats.")
            }
        }
        assertEquals(JobOutcome.SKIPPED, served?.lastOutcome)
        assertEquals("The session is already analysed for these chats.", served?.lastMessage)
    }

    @Test
    fun `a thrown run is recorded as failed`() {
        val written = mutableListOf<AnalysisSchedule>()
        val served = runBlocking {
            runner(written, now = at("2026-08-20", "07:01")).runDue { _, _ ->
                error("the provider refused")
            }
        }
        assertEquals(JobOutcome.FAILED, served?.lastOutcome)
        assertEquals("the provider refused", served?.lastMessage)
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
        }
        assertFalse(ran)
        assertEquals(JobOutcome.MISSED, served?.lastOutcome)
        assertTrue(served?.lastMessage.orEmpty().startsWith("Missed by 4 h"))
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
        }
        assertFalse(ran)
        assertEquals(JobOutcome.SKIPPED, served?.lastOutcome)
        assertEquals(
            "Scheduled runs are not allowed to spend cloud credits on this phone.",
            served?.lastMessage,
        )
    }

    @Test
    fun `refusing to spend is the default`() {
        val written = mutableListOf<AnalysisSchedule>()
        val runner = JobRunner(
            schedule = { schedule() },
            record = { written += it },
            now = { at("2026-08-20", "07:01") },
        )
        val served = runBlocking { runner.runDue { _, _ -> "ran" } }
        assertEquals(JobOutcome.SKIPPED, served?.lastOutcome)
    }

    @Test
    fun `nothing owed writes nothing`() {
        val written = mutableListOf<AnalysisSchedule>()
        val served = runBlocking {
            runner(written, now = at("2026-08-20", "06:00")).runDue { _, _ -> "ran" }
        }
        assertNull(served)
        assertTrue(written.isEmpty())
    }

    private fun runner(
        written: MutableList<AnalysisSchedule>,
        now: Instant,
        paidAllowed: Boolean = true,
        schedule: AnalysisSchedule = schedule(),
    ) = JobRunner(
        schedule = { schedule },
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
