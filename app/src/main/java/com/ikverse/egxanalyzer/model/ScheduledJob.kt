package com.ikverse.egxanalyzer.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A job the app runs on its own, at a time the user chose.
 *
 * Deliberately device-local. Everything else the app records - reports, trades, wording rules -
 * travels between phones through the sync channel, and a schedule must not: three devices holding
 * one schedule is three runs of the same work, and once analyses can be scheduled that is three
 * times the cloud bill for one answer. Nothing here is ever published, and [ScheduledJob] is
 * absent from every sync document on purpose.
 */
data class ScheduledJob(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val trigger: JobTrigger,
    val work: JobWork,
    /**
     * How late a fire may be and still run.
     *
     * A phone that was off, in Doze, or out of signal at the appointed minute is the normal case
     * rather than the exception, and a schedule that only ever fires punctually or not at all is a
     * schedule that mostly does not fire. Past this, the run is recorded as missed instead - which
     * is the honest outcome for work whose whole point was to happen at a particular time.
     */
    val graceMinutes: Int = DEFAULT_GRACE_MINUTES,
    /**
     * The fire this job has already served, not the wall-clock moment it ran.
     *
     * Stored as the scheduled time rather than the actual one so that a run which started 20
     * minutes late still counts as having served its 18:00 slot. Comparing against the real start
     * time would let the same slot fire twice on a phone whose clock moved.
     */
    val lastFiredAt: Instant? = null,
    val lastOutcome: JobOutcome = JobOutcome.NEVER,
    val lastMessage: String? = null,
    val createdAt: Instant = Instant.now(),
    /**
     * The moment from which this schedule's fires count.
     *
     * Without it, a job created at 19:00 for 18:00 on trading days owes today's 18:00 fire the
     * instant it is saved, and the grace window - which exists to forgive a sleeping phone - runs
     * it there and then. Creating a schedule is not a way of asking for it to happen now. Re-armed
     * when the trigger is edited, for the same reason and in the same breath as [lastFiredAt] being
     * cleared: the old slot was served or missed under a rule that no longer applies.
     */
    val armedAt: Instant = createdAt,
) {
    /** A one-shot that has already served its only fire has nothing left to do. */
    val spent: Boolean get() = trigger is JobTrigger.Once && lastFiredAt != null

    /** Whether this build knows how to do what the job asks for. See [JobWork.Unsupported]. */
    val runnable: Boolean get() = work !is JobWork.Unsupported

    companion object {
        /**
         * Two hours, which covers a phone left face-down through an evening and a night in Doze.
         *
         * Long enough to be the difference between a schedule that works and one that silently
         * does not; short enough that a price refresh booked for after the close cannot run so
         * late that the next day's session has opened underneath it.
         */
        const val DEFAULT_GRACE_MINUTES = 120
    }
}

/** When a job fires. */
sealed interface JobTrigger {
    /**
     * One fire, at a wall-clock moment.
     *
     * Held as a [LocalDateTime] rather than an [Instant] because the user picked a time on a
     * clock in Cairo, and an instant computed at the moment they picked it would be wrong the
     * moment daylight saving moved underneath it.
     */
    data class Once(val at: LocalDateTime) : JobTrigger

    /** The same clock time on each of the chosen days, week after week. */
    data class Repeat(val days: Set<DayOfWeek>, val at: LocalTime) : JobTrigger
}

/** What a job actually does. */
sealed interface JobWork {
    /** What this is called on screen. */
    val displayName: String

    /**
     * Whether running it sends anything to a paid provider.
     *
     * Read by the UI, which says so on every job, and by the runner, which will refuse to start
     * paid work the user has not separately allowed. Free work is what this feature was proved on
     * before anything could spend money unattended.
     */
    val spendsCredits: Boolean

    /**
     * Fetch the daily prices for every stock the record names or the user holds.
     *
     * Free: it reads the same public price feed the pull-to-refresh gesture does, sends nothing
     * anywhere, and touches no cloud provider.
     */
    data object PriceRefresh : JobWork {
        override val displayName = "Refresh prices"
        override val spendsCredits = false
    }

    /**
     * A job written by a build newer than this one.
     *
     * Kept and shown rather than dropped: the row is the user's, and an app that silently deletes
     * what it cannot read is one that loses a schedule on every downgrade. Never run, for the
     * obvious reason that this build does not know what it would be starting.
     */
    data class Unsupported(val kind: String) : JobWork {
        override val displayName = "Not supported by this version"
        override val spendsCredits = false
    }
}

/** How a job's last fire went. */
enum class JobOutcome(val displayName: String) {
    /** Booked, never yet fired. */
    NEVER("Not run yet"),
    SUCCEEDED("Ran"),

    /** Fired, and decided there was nothing to do. Not a failure and not worth alarming anyone. */
    SKIPPED("Skipped"),

    /** The fire was missed by more than the job's grace, so it was passed over rather than run late. */
    MISSED("Missed"),
    FAILED("Failed"),
}
