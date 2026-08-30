package com.ikverse.egxanalyzer.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
/**
 * One analysis this phone runs on its own: a time, the days it keeps, and what it reads.
 *
 * A few of these rather than a table of jobs. What stood here before was a general scheduler -
 * every job carrying a name, a kind of work, and a choice of firing once, on chosen weekdays, or
 * repeatedly inside a window - and the shape of that form, not the number of schedules, was what
 * made it unusable. So the kind of work is gone (a price refresh is a checkbox of its own that
 * needs no configuration at all), the trigger is gone, the name is gone, and what is left is a
 * time and a set of weekdays. At most [MAX] of them, which is the cap that keeps a list of
 * schedules a list rather than a table with a form behind it.
 *
 * Deliberately device-local. Everything else the app records - reports, trades, wording rules -
 * travels between phones through the sync channel, and this must not: three devices holding one
 * schedule is three runs of the same work, and for an analysis that is three times the cloud bill
 * for one answer. Nothing here is ever published.
 */
data class AnalysisSchedule(
    /**
     * Which of the few this is, for the whole life of the schedule.
     *
     * A list needs identity that survives being reordered, edited and written back: the run that
     * fires at 07:00 records its outcome from a background process, while the screen may have
     * been used to change the time of the one beside it. Matching on position would file that
     * outcome against the wrong schedule; matching on the time would lose it the moment the time
     * was changed. Handed out by [nextId] and never reused.
     */
    val id: Long = 1L,
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
     * The weekdays it keeps, any of the seven.
     *
     * Chosen rather than fixed, because the calls worth reading do not arrive evenly: a schedule
     * for the Sunday open answers a different question from one on a Wednesday lunchtime, and
     * before this there was no way to ask either without also paying for the other three days.
     *
     * **Friday and Saturday are offered but never default.** The exchange is shut, so it looks at
     * first like a fire with no session to read - but a run then is aimed at the next session that
     * exists, which is Sunday's, and the window it reads starts at the previous Thursday. So a
     * weekend schedule is the one that picks up what the chats posted over the weekend and has the
     * report ready before Sunday opens. Off by default because most weeks it is the same answer as
     * the Sunday morning run for a second charge; on where the owner decides otherwise, which is
     * a judgement about their own chats and not one this app can make for them.
     *
     * An empty set is a schedule that never fires. It is reported as a blocked schedule rather
     * than silently corrected: correcting it would mean choosing days on the user's behalf.
     */
    val days: Set<DayOfWeek> = DEFAULT_DAYS,
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
         * Four, which is what keeps this a list rather than a table.
         *
         * A number small enough that every schedule fits on the screen at once, and that the whole
         * of what this phone does unattended can be read without scrolling or opening anything.
         * It is also a bound on the bill: each schedule that fires sends a paid request, so the
         * most this can cost in a day is four of them, and that is a number the owner can hold in
         * their head. The old table had no cap at all, which is part of why nobody could say what
         * it was going to do.
         */
        const val MAX = 4

        /**
         * Every trading day, which is what a new schedule starts as.
         *
         * The whole week the exchange is open, and neither weekend day. Narrowing it is a
         * deliberate act - a schedule that quietly started on Sundays only would be a run the
         * owner never asked to lose - and so is widening it, since a weekend run is a charge on a
         * day most weeks have nothing new to say.
         */
        val DEFAULT_DAYS: Set<DayOfWeek> = ScheduleClock.tradingDays

        /**
         * The next unused id, which is one past the highest ever handed out in this list.
         *
         * Never reused, and not a count: deleting the second of three and adding another must not
         * hand the new one an id the outcome of a run is still on its way back to.
         */
        fun nextId(existing: List<AnalysisSchedule>): Long =
            (existing.maxOfOrNull(AnalysisSchedule::id) ?: 0L) + 1L

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
