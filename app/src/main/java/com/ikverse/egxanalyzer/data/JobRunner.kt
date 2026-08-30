package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysisSchedule
import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.ScheduleClock
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId

/**
 * Thrown by a run to say it looked and there was nothing to do.
 *
 * Separate from a failure because the two want different reactions from a reader: a run that found
 * the session already analysed worked perfectly, and filing it as an error would train whoever
 * glances at the card to ignore the one word that matters.
 */
class JobSkipped(message: String) : Exception(message)

/**
 * Runs the analysis schedule if it owes a run, and writes down what happened.
 *
 * Knows nothing about alarms, workers or screens - it is handed the schedule, a clock and a way to
 * do the work, so the decisions that matter (has this fire already been served, is it too late to
 * run it, is this allowed to spend money) can be tested without a device or a wait.
 *
 * Every path records an outcome, including the ones that do nothing. A schedule whose last line
 * says why it stayed quiet is diagnosable; one that is simply blank on a morning it should have
 * run is not, and silence is the failure mode of every scheduler on this platform.
 */
class JobRunner(
    private val schedule: () -> AnalysisSchedule,
    private val record: (AnalysisSchedule) -> Unit,
    /**
     * Whether work that spends cloud credits may start unattended.
     *
     * Defaults to refusing, and the default is the point: the clock cannot arm itself to spend
     * money by a schedule merely existing, and every caller that wants it has to say so out loud.
     * In the app it reads a switch of its own, separate from the schedule's.
     */
    private val paidWorkAllowed: () -> Boolean = { false },
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = ScheduleClock.ZONE,
) {

    /**
     * Serves the fire that has come and gone unanswered, if there is one.
     *
     * [perform] is given the schedule and the moment it was due - the scheduled moment, not this
     * one, because a run that starts late still has to reason about the slot it is filling. It
     * returns the line to record, throws [JobSkipped] to say it deliberately did nothing, and
     * throws anything else to fail.
     *
     * Returns what it wrote, or null where nothing was owed - which is what the tests read and
     * what the caller re-books from.
     */
    suspend fun runDue(
        perform: suspend (AnalysisSchedule, Instant) -> String,
    ): AnalysisSchedule? {
        val current = schedule()
        val moment = now()
        // Only the most recent unserved fire is ever considered, so a phone that was off for a
        // week comes back owing one run rather than seven. Catching up on a schedule is not the
        // same thing as honouring it.
        val due = ScheduleClock.unservedFire(current, moment, zone) ?: return null
        val served = when {
            !ScheduleClock.withinGrace(due, moment) -> current.record(
                due,
                JobOutcome.MISSED,
                "Missed by ${lateness(due, moment)}. The next run is the retry.",
            )

            // Every schedule left here sends a paid request, so this is the whole of the money
            // guard: no second switch, no run.
            !paidWorkAllowed() -> current.record(
                due,
                JobOutcome.SKIPPED,
                "Scheduled runs are not allowed to spend cloud credits on this phone.",
            )

            else -> runWork(current, due, perform)
        }
        record(served)
        return served
    }

    private suspend fun runWork(
        schedule: AnalysisSchedule,
        due: Instant,
        perform: suspend (AnalysisSchedule, Instant) -> String,
    ): AnalysisSchedule = try {
        schedule.record(due, JobOutcome.SUCCEEDED, perform(schedule, due))
    } catch (cancelled: CancellationException) {
        // The process is going away underneath us. Leaving the fire unserved is right: nothing was
        // finished, and recording it as done would mean the next wake-up skips it.
        throw cancelled
    } catch (skipped: JobSkipped) {
        schedule.record(due, JobOutcome.SKIPPED, skipped.message ?: "Nothing to do.")
    } catch (error: Exception) {
        schedule.record(due, JobOutcome.FAILED, error.message ?: "The run failed.")
    }

    private fun AnalysisSchedule.record(
        due: Instant,
        outcome: JobOutcome,
        message: String,
    ) = copy(lastFiredAt = due, lastOutcome = outcome, lastMessage = message)

    private fun lateness(due: Instant, moment: Instant): String {
        val minutes = (moment.epochSecond - due.epochSecond) / 60
        return when {
            minutes < 60 -> "$minutes min"
            minutes < 60 * 48 -> "${minutes / 60} h"
            else -> "${minutes / (60 * 24)} days"
        }
    }
}
