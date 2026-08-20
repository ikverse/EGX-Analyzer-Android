package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.JobWork
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.ScheduledJob
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId

/**
 * Thrown by a job's work to say it looked and there was nothing to do.
 *
 * Separate from a failure because the two want different reactions from a reader: a refresh that
 * found today's prices already fetched worked perfectly, and filing it as an error would train
 * whoever glances at the schedule to ignore the one word that matters.
 */
class JobSkipped(message: String) : Exception(message)

/**
 * Runs whatever the schedules owe, and writes down what happened.
 *
 * Knows nothing about alarms, workers or screens - it is handed the jobs, a clock and a way to do
 * the work, so the decisions that matter (has this fire already been served, is it too late to run
 * it, is this job allowed to spend money) can be tested without a device or a wait.
 *
 * Every path records an outcome, including the ones that do nothing. A schedule whose last line
 * says why it stayed quiet is diagnosable; one that is simply blank on a morning it should have
 * run is not, and silence is the failure mode of every scheduler on this platform.
 */
class JobRunner(
    private val jobs: () -> List<ScheduledJob>,
    private val record: (ScheduledJob) -> Unit,
    /** The one switch that stops everything, without the user having to undo each job. */
    private val schedulesEnabled: () -> Boolean,
    /**
     * Whether work that spends cloud credits may start unattended.
     *
     * False, and there is no way to make it true yet: the scheduler is being proved on work that
     * costs nothing before it is trusted with work that bills. The guard lives here rather than in
     * the absence of a paid job type, so that adding one cannot accidentally arm it.
     */
    private val paidWorkAllowed: () -> Boolean = { false },
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = ScheduleClock.ZONE,
) {

    /**
     * Serves every job whose fire has come and gone unanswered.
     *
     * [perform] is given the work and the moment it was due - the scheduled moment, not this one,
     * because a job that runs late still has to reason about the slot it is filling. It returns
     * the line to record, throws [JobSkipped] to say it deliberately did nothing, and throws
     * anything else to fail.
     *
     * Returns what it wrote, which is what the tests read and what the caller re-books from.
     */
    suspend fun runDue(perform: suspend (JobWork, Instant) -> String): List<ScheduledJob> {
        if (!schedulesEnabled()) return emptyList()
        val moment = now()
        val written = mutableListOf<ScheduledJob>()
        for (job in jobs()) {
            val due = ScheduleClock.unservedFire(job, moment, zone) ?: continue
            // Only the most recent unserved fire is ever considered, so a phone that was off for a
            // week comes back owing one run rather than seven. Catching up on a schedule is not
            // the same thing as honouring it.
            val served = when {
                !ScheduleClock.withinGrace(job, due, moment) -> job.record(
                    due,
                    JobOutcome.MISSED,
                    "Missed by ${lateness(due, moment)}. The next run is the retry.",
                )

                job.work.spendsCredits && !paidWorkAllowed() -> job.record(
                    due,
                    JobOutcome.SKIPPED,
                    "Scheduled runs may not spend cloud credits in this version.",
                )

                else -> runWork(job, due, perform)
            }
            record(served)
            written += served
        }
        return written
    }

    private suspend fun runWork(
        job: ScheduledJob,
        due: Instant,
        perform: suspend (JobWork, Instant) -> String,
    ): ScheduledJob = try {
        job.record(due, JobOutcome.SUCCEEDED, perform(job.work, due))
    } catch (cancelled: CancellationException) {
        // The process is going away underneath us. Leaving the fire unserved is right: nothing was
        // finished, and recording it as done would mean the next wake-up skips it.
        throw cancelled
    } catch (skipped: JobSkipped) {
        job.record(due, JobOutcome.SKIPPED, skipped.message ?: "Nothing to do.")
    } catch (error: Exception) {
        job.record(due, JobOutcome.FAILED, error.message ?: "The job failed.")
    }

    private fun ScheduledJob.record(due: Instant, outcome: JobOutcome, message: String) = copy(
        lastFiredAt = due,
        lastOutcome = outcome,
        lastMessage = message,
    )

    private fun lateness(due: Instant, moment: Instant): String {
        val minutes = (moment.epochSecond - due.epochSecond) / 60
        return when {
            minutes < 60 -> "$minutes min"
            minutes < 60 * 48 -> "${minutes / 60} h"
            else -> "${minutes / (60 * 24)} days"
        }
    }
}
