package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DailySession
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One bar of a chart response, at whatever granularity was asked for.
 *
 * Deliberately **not** [com.ikverse.egxanalyzer.model.IntradayBar], which carries a high and a low
 * and nothing else because that is all the ordering question needs and all that is ever stored. A
 * bar read back from `intraday_bars` would have no open and no close, so one type used for both
 * jobs would be a type whose fields are populated or null depending on where it came from - and the
 * aggregation below would silently build sessions with no open on every bar that had been through
 * the database. This one is only ever built fresh from a response and never stored.
 */
data class SessionBar(
    val at: Instant,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val close: Double?,
    val volume: Double?,
)

/**
 * A daily history for a stock the daily feeds do not carry, built out of finer bars.
 *
 * VLMRA is the case this exists for. Yahoo split each EGX listing across two symbols; the legacy
 * `SYMBOL.CA` feed is the one with depth, and where it 404s the merge is left with the ISIN feed's
 * single session. A call on such a stock keeps a permanent hole in its window, so it never
 * completes - which means it never expires, never resolves, and sits pending for good while
 * quietly sitting outside every rate on the page.
 *
 * The intraday endpoint answers the same symbols with real history. Nothing is invented here: a
 * session's open is its first bar's open, its close is its last bar's close, its high and low are
 * the extremes across the day, and its volume is the sum. That is the definition of a daily bar,
 * not an approximation of one - the only thing lost is any trade that happened outside the bars the
 * feed returned.
 *
 * Every session it produces is marked [DailySession.derived], and that mark is the point. This app
 * records rather than corrects everywhere else - it refuses to guess a split ratio and rescale a
 * year of prices - so a fabricated daily row presented as what the exchange reported would be the
 * sharpest break with its own rules anywhere in the codebase.
 */
object DailyFromIntraday {

    /**
     * Bars into sessions, in date order.
     *
     * Dated in **UTC**, the same zone `PriceRepository.parseChart` dates the daily feed in, so a
     * derived session and a real one for the same day carry the same key and one replaces the
     * other. EGX trades 10:00-14:30 Cairo, which is the middle of a UTC day whatever the offset is
     * doing, so no session is ever split across two dates by this choice.
     */
    fun aggregate(ticker: String, bars: List<SessionBar>): List<DailySession> = bars
        .groupBy { it.at.atZone(ZoneId.of(UTC)).toLocalDate() }
        .mapNotNull { (date, ofDay) -> session(ticker, date, ofDay) }
        .sortedBy(DailySession::date)

    private fun session(ticker: String, date: LocalDate, ofDay: List<SessionBar>): DailySession? {
        val ordered = ofDay.sortedBy(SessionBar::at)
        // Only positive prices, the same rule the daily feed's own parse applies: a bar in which
        // nothing traded reports nulls, and sometimes zeros, and a low of zero sits under every
        // stop loss ever printed.
        val highs = ordered.mapNotNull { it.high?.takeIf { price -> price > 0.0 } }
        val lows = ordered.mapNotNull { it.low?.takeIf { price -> price > 0.0 } }
        // A day whose bars carry no usable price is not a session that traded at nothing, it is a
        // day the feed has nothing for. Dropped rather than stored as a hole of its own.
        if (highs.isEmpty() || lows.isEmpty()) return null

        return DailySession(
            ticker = ticker,
            date = date,
            high = highs.max(),
            low = lows.min(),
            // The last bar that has one, not the last bar: a final bar reporting nulls would
            // otherwise leave the session with no close, which reads as still trading.
            close = ordered.lastNotNullOf { it.close?.takeIf { price -> price > 0.0 } },
            // Summed, and null rather than zero where no bar reported any - a session recorded as
            // having traded nothing is a claim, and an absent figure is the honest one.
            volume = ordered.mapNotNull(SessionBar::volume)
                .takeIf(List<Double>::isNotEmpty)
                ?.sum(),
            open = ordered.firstNotNullOfOrNull { it.open?.takeIf { price -> price > 0.0 } },
            derived = true,
        )
    }

    private fun <T> List<SessionBar>.lastNotNullOf(select: (SessionBar) -> T?): T? =
        asReversed().firstNotNullOfOrNull(select)

    private const val UTC = "UTC"
}

/**
 * Every bar of a chart response, at the granularity that was actually served.
 *
 * The granularity is **checked and not assumed**, which is the whole reason this is a function of
 * its own rather than a loop at a call site. Yahoo's legacy `SYMBOL.CA` symbols ignore `interval`
 * and answer with daily rows, and nothing in the shape of the response says so. Aggregating those
 * would produce a "derived" session built from one daily bar - identical to the row it came from,
 * marked as though finer evidence stood behind it, and offering the ordering code a bar that spans
 * the whole day while claiming to be an hour of it.
 *
 * Null means the answer cannot be used at all. Empty means the feed genuinely has nothing, which is
 * a different thing and worth telling apart.
 */
internal fun parseSessionBars(payload: String, interval: String): List<SessionBar>? {
    val result = JSONObject(payload)
        .optJSONObject("chart")
        ?.optJSONArray("result")
        ?.optJSONObject(0)
        ?: return null
    if (result.optJSONObject("meta")?.optString("dataGranularity") != interval) return null
    val stamps = result.optJSONArray("timestamp") ?: return emptyList()
    val quote = result.optJSONObject("indicators")
        ?.optJSONArray("quote")
        ?.optJSONObject(0)
        ?: return emptyList()
    val opens = quote.optJSONArray("open")
    val highs = quote.optJSONArray("high")
    val lows = quote.optJSONArray("low")
    val closes = quote.optJSONArray("close")
    val volumes = quote.optJSONArray("volume")
    return buildList {
        for (index in 0 until stamps.length()) {
            add(
                SessionBar(
                    at = Instant.ofEpochSecond(stamps.optLong(index)),
                    open = opens?.price(index),
                    high = highs?.price(index),
                    low = lows?.price(index),
                    close = closes?.price(index),
                    volume = volumes?.optDouble(index)?.takeUnless(Double::isNaN),
                ),
            )
        }
    }
}
