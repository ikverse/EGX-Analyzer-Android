package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * The wake that lets the phone say what the session just did to a trade.
 *
 * One fire a trading day, at the close - [ScheduleClock.sessionEnd], the quarter of an hour past
 * 14:30 in which the day's figures settle. It fetches the day's prices once and re-scores the
 * record off them, which is what turns a window that has just run out into a notification on the
 * afternoon it ran out.
 *
 * **Why it is not the daily worker.** `OverdueWorker` books a period and not a time: WorkManager
 * may run it anywhere inside each rolling twenty-four hours, and the interval starts wherever it
 * was first enqueued - so the daily look at the record lands at an hour nobody chose, drifts under
 * Doze, and fetches nothing when it gets there. That is survivable for "this trade is three days
 * overdue" and useless for "this trade ended today". This is an alarm, so it lands at the close;
 * the worker stays behind it as the backstop for a phone whose alarm the system dropped.
 *
 * **Why it is not the price refresh.** That checkbox is a promise to keep up with a market while it
 * moves, fifteen minutes at a time, and it is off until somebody asks for it. This is one fetch
 * after the exchange has shut, for a phone whose owner asked to be told about their trades - a
 * different question, a different switch, and a hundredth of the traffic.
 *
 * No Android in here, for the reason [ScheduleClock] and [MarketRefresh] have none: a rule about
 * what happens at 14:45 next Tuesday cannot be tested by waiting for next Tuesday.
 */
object CloseSweep {

    /** The close, as the rest of the app already defines it. */
    val at: LocalTime = ScheduleClock.sessionEnd

    /**
     * The first fire strictly after [after].
     *
     * Never null: [ScheduleClock.nextFire] answers null only for a schedule with no days left in
     * it, and these days are a constant.
     */
    fun nextFire(after: Instant, zone: ZoneId = ScheduleClock.ZONE): Instant =
        checkNotNull(ScheduleClock.nextFire(at, ScheduleClock.tradingDays, after, zone))

    /**
     * The close this phone still owes a sweep for, or null where it owes none.
     *
     * **Deliberately without a grace window**, unlike everything else here. A refresh slot that is
     * fifteen minutes late has been superseded by the next one, and an analysis that is late costs
     * money to run against the wrong session - but this fire has no successor for a day, and its
     * whole promise is that the session's endings are announced on the day they happened. A phone
     * that was asleep at 14:45 and wakes at 19:00 still owes it. One that comes back on Wednesday
     * having missed Monday and Tuesday owes exactly one, because [ScheduleClock.previousFire]
     * answers with the most recent close and not with all of them: catching up on a sweep is not
     * the same thing as honouring it.
     *
     * [lastRefreshAt] is when prices were last actually fetched, and it is what stops this fetching
     * twice. A refresh that already happened after this close - the 14:45 slot on a phone that
     * keeps prices fresh, or the user pressing the button at four o'clock - has done the work this
     * fire exists to do. A refresh from breakfast has not, which is why this is the moment and not
     * the day.
     */
    fun dueFire(
        now: Instant,
        lastRefreshAt: Instant?,
        zone: ZoneId = ScheduleClock.ZONE,
    ): Instant? {
        val due = checkNotNull(ScheduleClock.previousFire(at, ScheduleClock.tradingDays, now, zone))
        if (lastRefreshAt != null && !lastRefreshAt.isBefore(due)) return null
        return due
    }
}
