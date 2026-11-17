package com.ikverse.egxanalyzer.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Cash dividends: what is coming, from Arab Finance's dividend news, and what has happened, from
 * the `events=div` table on the daily price feed.
 *
 * Ported from Signal Lab's `DividendBook` (signal-lab-egx), which worked the rules out on real
 * 2025-26 dividends. Two changes on the way in: days are [LocalDate] rather than epoch days, and
 * there are no hand entries or corrections, so nothing here has an owner's value to protect.
 *
 * **Nothing here touches scoring.** A stock falls by its dividend on the ex-day, which can touch a
 * stop; the pill that says so explains a verdict and never changes one.
 */

/** What a dividend news item announces. */
enum class DividendNewsKind {
    /** The exchange's notice: the coupon, its amount and the day it is paid from. */
    NOTICE,

    /** The general assembly approved it: from here the amount is final. */
    ASSEMBLY,

    /** Anything else about a dividend (a board proposal, a bonus issue). Places nothing. */
    OTHER,
}

/** One Arab Finance item, read as far as it could be. */
data class DividendNews(
    val code: String,
    val kind: DividendNewsKind,
    /** Pounds per share, before tax; null where the item gives no cash amount. */
    val amount: Double?,
    /** The day the coupon is paid from ("اعتبارا من"). */
    val paymentDay: LocalDate?,
    val assemblyDay: LocalDate?,
    /** The day Arab Finance last updated the item. */
    val published: LocalDate?,
    val headline: String,
    val url: String,
)

/** A cash dividend as the price feed records it: [exDay] is the first session without it. */
data class PaidDividend(val exDay: LocalDate, val amount: Double)

/** How a news dividend stands against the price feed's own record. */
enum class DividendCheck {
    /** Its ex-day has not come, or the feed has not caught up yet. */
    WAITING,

    /** The feed recorded it near the expected day, with the same amount (within 2%). */
    CONFIRMED,

    /** The feed recorded a dividend near the expected day with a different amount. */
    AMOUNT_DIFFERS,

    /** Well past its ex-day the feed shows nothing near it: the item or its date may be wrong. */
    NOT_ON_FEED,
}

/** One cash dividend out of the news, with what the price feed has said about it. */
data class Dividend(
    val code: String,
    val amount: Double?,
    val assemblyDay: LocalDate? = null,
    val paymentDay: LocalDate? = null,
    /** The last session that still carries it: the last day to buy. */
    val lastCumDay: LocalDate? = null,
    val announcedDay: LocalDate? = null,
    /** [lastCumDay] is counted back from the payment day, not yet read off the feed. */
    val lastCumEstimated: Boolean = false,
    val feedExDay: LocalDate? = null,
    val feedAmount: Double? = null,
    val check: DividendCheck = DividendCheck.WAITING,
    val headline: String = "",
    val url: String = "",
) {
    /** The day it is placed by: payment, last cum-day, assembly, or the first news of it. */
    val anchor: LocalDate? get() = paymentDay ?: lastCumDay ?: assemblyDay ?: announcedDay

    /** The first session without it: the feed's own day, or the session after the last cum-day. */
    val exDay: LocalDate? get() = feedExDay ?: lastCumDay?.let { DividendBook.shift(it, 1) }
}

/** An ex-day inside a call's or a trade's life, which is what the card's pill says. */
data class DividendMark(val exDay: LocalDate, val amount: Double?, val estimated: Boolean)

/** Everything known about dividends: the news list and each stock's paid table. */
data class DividendRecord(
    val dividends: List<Dividend> = emptyList(),
    val paid: Map<String, List<PaidDividend>> = emptyMap(),
    /** When the news page was last read, epoch milliseconds, or 0 for never. */
    val newsAt: Long = 0L,
)

/** The rules that turn news and the feed's table into one list. Pure, so it is tested alone. */
object DividendBook {
    /**
     * On nine real 2025-26 dividends (MHOT, ISPH, MFSC, CICH, WCDF, EDFM, FERC, CEFM, MICH) the
     * feed's ex-day was two sessions before Arab Finance's payment day, so the last day that
     * carries the dividend is three sessions before it. Measured in Signal Lab.
     */
    const val EX_BEFORE_PAYMENT = 2
    const val LAST_CUM_BEFORE_PAYMENT = 3

    /** An assembly and a notice for the same stock this close together are one dividend. */
    const val SAME_DIVIDEND_DAYS = 120L

    /** Two notices whose payment days differ by at most this are one dividend. */
    const val SAME_PAYMENT_DAYS = 7L

    /** How far from the expected ex-day a feed dividend may sit and still be this one. */
    const val FEED_MATCH_DAYS = 7L

    /** Sessions after the ex-day before a missing feed record counts as a warning. */
    const val FEED_LAG_SESSIONS = 5

    const val AMOUNT_TOLERANCE = 0.02

    /**
     * [day] moved by [sessions] trading days (negative is back). Sunday to Thursday, with no
     * holiday calendar: no free source publishes the exchange's holidays ahead, which is why a
     * date worked out this way is shown as "about" until the feed confirms it.
     */
    fun shift(day: LocalDate, sessions: Int): LocalDate {
        var at = day
        var left = abs(sessions)
        val step = if (sessions >= 0) 1L else -1L
        while (left > 0) {
            at = at.plusDays(step)
            if (at.dayOfWeek in ScheduleClock.tradingDays) left--
        }
        return at
    }

    /**
     * [existing] updated by [news], then every dividend checked against [paid]. A news item joins
     * the dividend it is about (same stock, payment days within a week, or an assembly and its
     * notice within four months) and otherwise starts a new one.
     */
    fun merge(
        existing: List<Dividend>,
        news: List<DividendNews>,
        paid: Map<String, List<PaidDividend>>,
        today: LocalDate,
    ): List<Dividend> {
        val out = existing.toMutableList()
        val ordered = news
            .filter { it.amount != null || it.paymentDay != null || it.kind == DividendNewsKind.ASSEMBLY }
            .sortedWith(compareBy<DividendNews>({ it.published ?: it.paymentDay }, { it.paymentDay }))
        for (n in ordered) {
            val at = matchIndex(out, n)
            if (at < 0) {
                out += Dividend(
                    code = n.code, amount = n.amount, assemblyDay = n.assemblyDay, paymentDay = n.paymentDay,
                    announcedDay = n.published, headline = n.headline, url = n.url,
                )
                continue
            }
            val d = out[at]
            val notice = n.kind == DividendNewsKind.NOTICE
            out[at] = d.copy(
                amount = if (n.amount != null && (notice || d.amount == null)) n.amount else d.amount,
                paymentDay = n.paymentDay ?: d.paymentDay,
                assemblyDay = n.assemblyDay ?: d.assemblyDay,
                announcedDay = d.announcedDay ?: n.published,
                headline = if (notice || d.headline.isEmpty()) n.headline else d.headline,
                url = if (notice || d.url.isEmpty()) n.url else d.url,
            )
        }
        return out.map { check(it, paid[it.code].orEmpty(), today) }
    }

    private fun gapDays(a: LocalDate, b: LocalDate): Long = abs(ChronoUnit.DAYS.between(a, b))

    /** The dividend in [list] that [n] is about, or -1 for a new one. */
    private fun matchIndex(list: List<Dividend>, n: DividendNews): Int {
        val anchor = n.paymentDay ?: n.assemblyDay ?: n.published ?: return -1
        var best = -1
        var bestGap = Long.MAX_VALUE
        for ((i, d) in list.withIndex()) {
            if (d.code != n.code) continue
            if (d.paymentDay != null && n.paymentDay != null) {
                val gap = gapDays(d.paymentDay, n.paymentDay)
                if (gap <= SAME_PAYMENT_DAYS && gap < bestGap) { best = i; bestGap = gap }
                continue
            }
            val theirs = d.anchor ?: continue
            val gap = gapDays(theirs, anchor)
            // An assembly comes before its payment; a notice never joins an assembly held after it.
            val ordered = !(n.paymentDay != null && d.assemblyDay != null && d.assemblyDay > n.paymentDay) &&
                !(d.paymentDay != null && n.assemblyDay != null && n.assemblyDay > d.paymentDay)
            if (ordered && gap <= SAME_DIVIDEND_DAYS && gap < bestGap) { best = i; bestGap = gap }
        }
        return best
    }

    /** [d] with its last cum-day and its check against the feed worked out. */
    fun check(d: Dividend, paid: List<PaidDividend>, today: LocalDate): Dividend {
        var lastCum = d.lastCumDay
        var estimated = d.lastCumEstimated
        if (d.paymentDay != null) {
            lastCum = shift(d.paymentDay, -LAST_CUM_BEFORE_PAYMENT)
            estimated = true
        }
        val expectedEx = lastCum?.let { shift(it, 1) }
            ?: return d.copy(lastCumDay = null, lastCumEstimated = false, check = DividendCheck.WAITING)

        val seen = paid
            .filter { gapDays(it.exDay, expectedEx) <= FEED_MATCH_DAYS }
            .minByOrNull { gapDays(it.exDay, expectedEx) }
        if (seen == null) {
            val late = shift(expectedEx, FEED_LAG_SESSIONS) < today
            return d.copy(
                lastCumDay = lastCum, lastCumEstimated = estimated, feedExDay = null, feedAmount = null,
                check = if (late) DividendCheck.NOT_ON_FEED else DividendCheck.WAITING,
            )
        }
        val amount = d.amount ?: seen.amount
        val same = abs(seen.amount / amount - 1) <= AMOUNT_TOLERANCE
        return d.copy(
            amount = amount, lastCumDay = shift(seen.exDay, -1), lastCumEstimated = false,
            feedExDay = seen.exDay, feedAmount = seen.amount,
            check = if (same) DividendCheck.CONFIRMED else DividendCheck.AMOUNT_DIFFERS,
        )
    }

    /** Dividends whose last day to buy is [today] or later, soonest first. */
    fun upcoming(list: List<Dividend>, today: LocalDate): List<Dividend> =
        list.filter { it.lastCumDay != null && it.lastCumDay >= today }
            .sortedWith(compareBy<Dividend>({ it.lastCumDay }, { it.code }))

    /** The dividend a stock sheet shows: the next one, or one whose ex-day was in the last 30 days. */
    fun forStock(list: List<Dividend>, ticker: String, today: LocalDate): Dividend? {
        val mine = list.filter { it.code == ticker }
        upcoming(mine, today).firstOrNull()?.let { return it }
        return mine
            .filter { d -> d.exDay?.let { it <= today && ChronoUnit.DAYS.between(it, today) <= 30 } == true }
            .maxByOrNull { it.exDay!! }
    }

    /**
     * The first ex-day after [from] and on or before [to] (null for "still open", which reaches
     * the next ex-day announced). Read from the feed's own table and from the news, which is the
     * only one of the two that knows a date before it happens.
     */
    fun inside(record: DividendRecord, ticker: String, from: LocalDate, to: LocalDate?): DividendMark? {
        val marks = buildList {
            record.paid[ticker].orEmpty().forEach { add(DividendMark(it.exDay, it.amount, estimated = false)) }
            record.dividends.filter { it.code == ticker }.forEach { d ->
                val ex = d.exDay ?: return@forEach
                // The feed's own day for the same dividend is already in the list above.
                if (d.feedExDay == null) add(DividendMark(ex, d.amount, d.lastCumEstimated))
            }
        }
        return marks
            .filter { it.exDay > from && (to == null || it.exDay <= to) }
            .minByOrNull { it.exDay }
    }

    /** [paid] with a fresh answer for one stock folded in, newest kept for a day seen twice. */
    fun withPaid(paid: Map<String, List<PaidDividend>>, ticker: String, fresh: List<PaidDividend>): Map<String, List<PaidDividend>> {
        if (fresh.isEmpty()) return paid
        val byDay = LinkedHashMap<LocalDate, PaidDividend>()
        paid[ticker].orEmpty().forEach { byDay[it.exDay] = it }
        fresh.forEach { byDay[it.exDay] = it }
        return paid + (ticker to byDay.values.sortedBy { it.exDay })
    }
}
