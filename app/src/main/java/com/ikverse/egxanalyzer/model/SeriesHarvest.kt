package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * When this phone copies the five-minute record of a session out of the feed and keeps it.
 *
 * Everything else here reads prices to answer a question the app already has - is this call a hit,
 * has this window run out, which of a high and a low came first. This reads them to keep, because
 * the feed does not: five-minute bars are served for about two months
 * ([com.ikverse.egxanalyzer.data.IntradayRepository.RETENTION_DAYS]) and are then gone for good,
 * from everybody, permanently. A session not copied inside that window is a session no later
 * request can recover.
 *
 * That is the whole reason this is a fire on a clock rather than something the screen does when
 * asked. A record that only accumulates while somebody remembers to open the app is one with holes
 * exactly where the phone was busy, and the holes cannot be filled in afterwards.
 *
 * **One fire a trading day, at the close**, the same moment [CloseSweep] takes and for a related
 * reason: [ScheduleClock.sessionEnd] is where this app already draws the line between a session
 * still moving and one the exchange has finished with. Copying a session before that line stores
 * half a day as though it were a whole one.
 *
 * **Deliberately without a grace window**, exactly as [CloseSweep] is. A price-refresh slot that is
 * late has been superseded a quarter of an hour later; this fire has no successor for a day and
 * what it missed expires, so a phone that was asleep at 14:45 still owes it at nine that evening -
 * and at nine the following morning, which is the last hour it can still be paid.
 *
 * No Android in here, for the reason [ScheduleClock], [MarketRefresh] and [CloseSweep] have none:
 * a rule about what happens at 14:45 next Tuesday cannot be checked by waiting for next Tuesday.
 */
object SeriesHarvest {

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
     * The close this phone still owes a harvest for, or null where it owes none.
     *
     * Answered against when a harvest last actually ran rather than against a day, for the reason
     * `MarketRefresh.dueFire` is: a day cannot tell "since this fire" from "this morning", and a
     * harvest that ran at breakfast has not copied the session that closed this afternoon.
     *
     * Unlike [CloseSweep] this is **not** stood down by an ordinary price refresh. The two do
     * different work off the same endpoint - a refresh asks for daily rows and stores one line for
     * the whole session - so a refresh at four o'clock has done nothing whatever about the bars.
     */
    fun dueFire(
        now: Instant,
        lastHarvestAt: Instant?,
        zone: ZoneId = ScheduleClock.ZONE,
    ): Instant? {
        val due = checkNotNull(ScheduleClock.previousFire(at, ScheduleClock.tradingDays, now, zone))
        if (lastHarvestAt != null && !lastHarvestAt.isBefore(due)) return null
        return due
    }

    /**
     * The first session a stock still owes bars for, or null where it owes none.
     *
     * Two bounds and the later of them wins. [storedThrough] is the newest session already on disk,
     * so the day after it is where this stock resumes - which on an ordinary evening is a one-day
     * request and on a phone that has been shut for a fortnight is a fortnight. [retentionDays] is
     * the wall: past it the feed answers nothing, and a window wider than it is refused outright
     * with HTTP 422 rather than trimmed, so asking from before the wall loses the whole request
     * and not merely its oldest end.
     *
     * Null where the stock is already current, which is what makes a second fire on the same
     * evening cost no requests at all.
     */
    fun harvestFrom(
        storedThrough: LocalDate?,
        finalThrough: LocalDate,
        retentionDays: Long,
        today: LocalDate,
    ): LocalDate? {
        val wall = today.minusDays(retentionDays)
        val resume = storedThrough?.plusDays(1) ?: wall
        val from = maxOf(resume, wall)
        return from.takeIf { !it.isAfter(finalThrough) }
    }
}
