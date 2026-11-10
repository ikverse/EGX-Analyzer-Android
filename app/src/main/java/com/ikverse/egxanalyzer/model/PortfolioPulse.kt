package com.ikverse.egxanalyzer.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * What the 5.0 Portfolio page draws beyond the record itself: how the open trades have gone day by
 * day, how the whole market moved in the last session, how often trades hit a target week by week,
 * and which trades want a decision now.
 *
 * Percentages throughout, never money. No trade size is recorded, so a sum in pounds would be a
 * figure the app has no way of knowing; an average return weights every trade the same, which is
 * the convention "Your record" already uses.
 */

/** One day on the Portfolio chart: the average return of the trades held that day. */
data class ReturnPoint(val date: LocalDate, val averageReturnPct: Double, val held: Int)

/**
 * The average return of the trades held on each trading day, from entry to that day's close.
 *
 * A trade counts from its entry date. A sold trade counts up to its exit date and is valued at its
 * own selling price on that day, so the line ends where the record says the trade ended. A day
 * where a held stock has no stored close uses its last close before that day, so a quiet feed for
 * one stock does not drop the trade from the line. Trades whose prices changed scale are left out,
 * as they are everywhere a return is measured.
 *
 * @param sessions every stored session of each held ticker, in any order.
 */
fun returnByDay(positions: List<PositionView>, sessions: Map<String, List<DailySession>>): List<ReturnPoint> {
    val counted = positions.filter { !it.priceScaleChanged && it.position.entryPrice > 0.0 }
    if (counted.isEmpty()) return emptyList()
    val closes: Map<String, List<Pair<LocalDate, Double>>> = sessions.mapValues { (_, list) ->
        list.mapNotNull { s -> s.close?.let { s.date to it } }.sortedBy { it.first }
    }
    val days = counted
        .flatMap { closes[it.ticker].orEmpty().map(Pair<LocalDate, Double>::first) }
        .toSortedSet()
    val firstEntry = counted.minOf { it.position.entryDate }
    return days.filter { it >= firstEntry }.mapNotNull { day ->
        val returns = counted.mapNotNull { view ->
            val p = view.position
            if (day < p.entryDate) return@mapNotNull null
            val exitOn = p.exitDate
            if (exitOn != null && day > exitOn) return@mapNotNull null
            val price = if (exitOn != null && day == exitOn && p.exitPrice != null) {
                p.exitPrice
            } else {
                closes[view.ticker]?.lastOrNull { it.first <= day }?.second
            } ?: return@mapNotNull null
            (price - p.entryPrice) / p.entryPrice * 100.0
        }
        if (returns.isEmpty()) null else ReturnPoint(day, returns.average(), returns.size)
    }
}

/** The ranges the chart offers. */
enum class ReturnRange(val label: String, val days: Long?) {
    WEEK("1W", 7), MONTH("1M", 31), QUARTER("3M", 92), ALL("All", null),
}

/** [points] from [range] back from [today]. */
fun List<ReturnPoint>.within(range: ReturnRange, today: LocalDate): List<ReturnPoint> =
    range.days?.let { span -> filter { it.date > today.minusDays(span) } } ?: this

/** How the market split in one session: listings that rose, held flat, and fell. */
data class MarketBreadth(val date: LocalDate, val up: Int, val flat: Int, val down: Int) {
    val total: Int get() = up + flat + down
}

/**
 * The split for the newest session any listing has, against each listing's previous close.
 *
 * Only listings that traded on that newest session are counted - a stock whose last session is a
 * week old says nothing about today - and only those with a close before it to compare against.
 *
 * @param lastTwo each listing's newest two stored sessions, in any order.
 */
fun marketBreadth(lastTwo: Map<String, List<DailySession>>): MarketBreadth? {
    val priced = lastTwo.values.mapNotNull { list ->
        val sorted = list.filter { it.close != null }.sortedBy(DailySession::date)
        if (sorted.size < 2) null else sorted[sorted.size - 2] to sorted.last()
    }
    val latest = priced.maxOfOrNull { it.second.date } ?: return null
    var up = 0; var flat = 0; var down = 0
    priced.filter { it.second.date == latest }.forEach { (before, last) ->
        val change = last.close!! - before.close!!
        when {
            abs(change) < FlatTolerance -> flat++
            change > 0 -> up++
            else -> down++
        }
    }
    if (up + flat + down == 0) return null
    return MarketBreadth(latest, up, flat, down)
}

/** Below a thousandth of a pound a close has not moved; prices are quoted to three decimals. */
private const val FlatTolerance = 0.0005

/** One week of settled trades: how many settled, and how many of those reached a target. */
data class WeekRate(val weekStart: LocalDate, val settled: Int, val hit: Int) {
    val rate: Double? get() = if (settled == 0) null else hit.toDouble() / settled
}

/**
 * The last [weeks] trading weeks, oldest first, each counting the trades that settled in it.
 *
 * The exchange's week starts on Sunday. A trade settles on the day a target or the stop was reached
 * or the day it was sold, whichever the record has; a trade still running is not counted.
 */
fun weeklyHitRates(positions: List<PositionView>, weeks: Int, today: LocalDate): List<WeekRate> {
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val starts = (weeks - 1 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }
    val settled = positions.filter { !it.open }.mapNotNull { view ->
        val on = view.settledOn ?: view.position.exitDate ?: return@mapNotNull null
        on to (view.status == PositionStatus.PARTIAL_TARGET_HIT || view.status == PositionStatus.FULL_TARGET_HIT)
    }
    return starts.map { start ->
        val inWeek = settled.filter { (on, _) -> on >= start && on < start.plusWeeks(1) }
        WeekRate(start, inWeek.size, inWeek.count { it.second })
    }
}

/** Why a trade is on the "Needs you" list. */
enum class NeedReason { NEAR_STOP, NEAR_TARGET2, OVERDUE }

/** One entry on the list, and how far the price is from the level it is near, where it is near one. */
data class NeedsYouItem(val view: PositionView, val reason: NeedReason, val distancePercent: Double? = null)

/**
 * The trades that want a decision now: overdue ones, then open ones within [thresholdPercent] of
 * their stop, then within it of target 2.
 *
 * The same distance rule as the "approaching a level" notification - how far the level is from the
 * last close, in percent of that close - read against the same threshold the reader set, so the
 * page and the lock screen never disagree about what "close" means. A trade near both levels at
 * once (a narrow call) is listed once, for its stop.
 */
fun needsYou(positions: List<PositionView>, thresholdPercent: Int): List<NeedsYouItem> {
    val overdue = positions
        .filter(PositionView::overdue)
        .sortedWith(compareByDescending<PositionView> { it.overdueDays }.thenBy { it.ticker })
        .map { NeedsYouItem(it, NeedReason.OVERDUE) }
    val listed = overdue.map { it.view.position.id }.toMutableSet()
    val near = mutableListOf<NeedsYouItem>()
    for (reason in listOf(NeedReason.NEAR_STOP, NeedReason.NEAR_TARGET2)) {
        positions.filter { it.open && !it.priceScaleChanged && it.position.id !in listed }.forEach { view ->
            val level = if (reason == NeedReason.NEAR_STOP) view.position.stopLoss else view.position.target2
            val price = view.currentPrice
            if (level == null || price == null || price <= 0.0) return@forEach
            val distance = abs(level - price) / price * 100.0
            if (distance <= thresholdPercent) {
                near += NeedsYouItem(view, reason, distance)
                listed += view.position.id
            }
        }
    }
    return overdue + near.sortedWith(compareBy<NeedsYouItem> { it.reason }.thenBy { it.distancePercent })
}

/**
 * How far an open trade has come from its entry toward target 2, and where target 1 sits on that
 * path - both as fractions, for the progress bar on a position row. Null where the trade has no
 * target above its entry to measure toward.
 */
data class TargetPath(val progress: Float, val target1At: Float?, val losing: Boolean)

fun targetPath(view: PositionView): TargetPath? {
    val p = view.position
    val goal = p.target2 ?: p.target1 ?: return null
    val price = view.currentPrice ?: return null
    val span = goal - p.entryPrice
    if (span <= 0.0) return null
    val progress = ((price - p.entryPrice) / span).toFloat().coerceIn(0f, 1f)
    val t1 = p.target1?.takeIf { p.target2 != null }?.let { ((it - p.entryPrice) / span).toFloat().coerceIn(0f, 1f) }
    return TargetPath(progress, t1, losing = price < p.entryPrice)
}
