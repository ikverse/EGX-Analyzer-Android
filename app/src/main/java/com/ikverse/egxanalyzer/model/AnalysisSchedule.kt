package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.LocalTime

/**
 * The one analysis this phone runs on its own, and the time it runs at.
 *
 * One rather than a list, and a time rather than a trigger. What stood here before was a general
 * scheduler - a table of jobs, each with a name, a kind of work, and a choice of firing once, on
 * chosen weekdays, or repeatedly inside a window - and every part of that generality was paid for
 * in a form nobody could get through. The two things it was ever going to be asked for are a price
 * refresh through the session, which is now a checkbox of its own that needs no configuration at
 * all, and one analysis before the open. This is the second of them.
 *
 * Deliberately device-local. Everything else the app records - reports, trades, wording rules -
 * travels between phones through the sync channel, and this must not: three devices holding one
 * schedule is three runs of the same work, and for an analysis that is three times the cloud bill
 * for one answer. Nothing here is ever published.
 */
data class AnalysisSchedule(
    /**
     * Whether the clock runs this at all.
     *
     * This is the switch the user sees, and it is not the one that permits spending: an enabled
     * schedule still cannot send a paid request unless `paidSchedulesEnabled` is separately on.
     * Two decisions, because letting the phone keep a time says nothing about letting it spend
     * money at that time.
     */
    val enabled: Boolean = false,
    /**
     * Cairo time, always.
     *
     * A schedule belongs to the exchange rather than to wherever the phone is: a user who books a
     * run for before the open means before the open in Cairo, and a time that shifted an hour when
     * they landed somewhere would read a session that had not happened.
     */
    val at: LocalTime = DEFAULT_TIME,
    /**
     * The chats this covers, frozen when the schedule was aimed.
     *
     * Frozen for the reason a position snapshots the levels it was taken on: re-ticking chats on
     * the Analyze screen months later must not silently re-aim a run that happens while nobody is
     * watching. Correcting it is possible and deliberate - see the re-aim control on the card.
     */
    val channels: List<AnalysedChannel> = emptyList(),
    val contentTypes: Set<AnalysisContentType> = emptySet(),
    /**
     * The fire this schedule has already served, not the wall-clock moment it ran.
     *
     * Stored as the scheduled time so that a run which started 20 minutes late still counts as
     * having served its 07:00 slot. Comparing against the real start time would let one slot fire
     * twice on a phone whose clock moved.
     */
    val lastFiredAt: Instant? = null,
    val lastOutcome: JobOutcome = JobOutcome.NEVER,
    val lastMessage: String? = null,
    /**
     * The moment from which fires count.
     *
     * Without it, a schedule switched on at 07:30 for 07:00 owes today's fire the instant it is
     * saved, and the grace window - which exists to forgive a sleeping phone - runs it there and
     * then. Turning a schedule on is not a way of asking for it to happen now. Re-armed whenever
     * the time is changed, for the same reason.
     */
    val armedAt: Instant = Instant.now(),
) {

    /** The chats and content types this is pointed at, for the re-aim control to compare. */
    val aim: AnalysisAim get() = AnalysisAim(channels, contentTypes)

    /** Whether there is anything to run: an analysis of no chats is a paid request for nothing. */
    val configured: Boolean get() = channels.isNotEmpty() && contentTypes.isNotEmpty()

    /**
     * What this sends, always for the next session and never for a historical date.
     *
     * A repeating schedule re-reading one fixed day would pay for the same answer every week.
     */
    fun plan(): AnalysisPlan = AnalysisPlan(
        channels = channels,
        contentTypes = contentTypes,
        mode = AnalysisMode.NEXT_DAY,
    )

    companion object {
        /**
         * Before the open, which is the only time an analysis is worth having.
         *
         * The exchange opens at 10:00 Cairo and the channels post their calls in the morning ahead
         * of it. A run at seven reads the night's messages and has the report ready while the
         * levels can still be acted on; the same run in the evening is a post-mortem.
         */
        val DEFAULT_TIME: LocalTime = LocalTime.of(7, 0)

        /**
         * Two hours, which covers a phone left face-down through a night in Doze.
         *
         * Long enough to be the difference between a schedule that works and one that silently
         * does not; short enough that a run booked before the open cannot arrive so late that the
         * session it was meant to prepare for has already started. A constant rather than a
         * setting: nobody picking a number here is picking it from evidence.
         */
        const val GRACE_MINUTES = 120
    }
}

/** How a schedule's last fire went. */
enum class JobOutcome(val displayName: String) {
    /** Booked, never yet fired. */
    NEVER("Not run yet"),
    SUCCEEDED("Ran"),

    /** Fired, and decided there was nothing to do. Not a failure and not worth alarming anyone. */
    SKIPPED("Skipped"),

    /** The fire was missed by more than the grace, so it was passed over rather than run late. */
    MISSED("Missed"),
    FAILED("Failed"),
}

/**
 * What an analysis is pointed at: the chats and the kinds of message inside them.
 *
 * Named separately from the schedule that holds it because it is the half that gets compared. A
 * schedule freezes its aim when it is made, the Analyze screen has an aim of its own at any
 * moment, and the re-aim control on the card exists to show the reader both and let them take the
 * newer one. Comparing whole schedules would have made a change of time look like a change of aim.
 */
data class AnalysisAim(
    val channels: List<AnalysedChannel>,
    val contentTypes: Set<AnalysisContentType>,
)
