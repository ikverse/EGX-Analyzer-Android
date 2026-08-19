package com.ikverse.egxanalyzer.next

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How a figure is written down.
 *
 * Stated here rather than borrowed from the shipping app, for the same reason nothing else is
 * borrowed - but the rules themselves are not up for redesign, because they come from the data
 * rather than from taste. The two that matter: EGX trades plenty of stocks below a pound, so two
 * decimals is not enough; and a percentage past one decimal implies a precision the arithmetic
 * does not have.
 */

/** Absent. Never a blank, and never a zero - both of those are claims. */
internal const val DASH = "—"

/** The typographic minus, which is one digit wide in a monospaced face where a hyphen is not. */
private const val MINUS = "−"

/** A price exactly as it is worth reading: up to three decimals, trailing zeros dropped. */
internal fun formatPrice(value: Double?): String {
    if (value == null || value.isNaN()) return DASH
    val rounded = (value * 1000).roundToInt() / 1000.0
    val text = if (abs(rounded - rounded.toLong()) < 1e-9) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
    return text.replace("-", MINUS)
}

/** A signed percentage at one decimal. */
internal fun formatPercent(value: Double?, signed: Boolean = true): String {
    if (value == null || value.isNaN()) return DASH
    val rounded = (value * 10).roundToInt() / 10.0
    val sign = if (signed && rounded > 0) "+" else ""
    val body = if (abs(rounded - rounded.toLong()) < 1e-9) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
    return "$sign$body%".replace("-", MINUS)
}

/** A whole percentage, for a rate rather than a return. */
internal fun formatRate(value: Double?): String {
    if (value == null || value.isNaN()) return DASH
    return "${value.roundToInt()}%"
}

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

/**
 * A date inside a row: day and month, no year.
 *
 * US rather than the phone's locale, deliberately and only for figures: these sit in columns beside
 * prices set in a Latin monospace, and a locale that renders them in Arabic-Indic digits would put
 * two numbering systems in one column.
 */
internal fun formatDay(date: LocalDate?): String = date?.format(DAY_MONTH) ?: DASH

/** A date that heads a card, where the year is part of what is being identified. */
internal fun formatFullDay(date: LocalDate?): String = date?.format(DAY_MONTH_YEAR) ?: DASH

/** "session" or "sessions". */
internal fun Int.sessionWord(): String = if (this == 1) "session" else "sessions"

/** "trade" or "trades". */
internal fun Int.tradeWord(): String = if (this == 1) "trade" else "trades"

/** How the overdue count is written on a chip: short, because it sits beside a ticker. */
internal fun Long.overdueShort(): String = "${this}d"
