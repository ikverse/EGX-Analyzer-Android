package com.ikverse.egxanalyzer.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A call's or a trade's state in the words a reader would use, for the screens.
 *
 * The enums' own `label` fields stay as they are: exports, call text and the diagnostics copy are
 * read against them, and "Full hit" is the scorer's term of art rather than something to say to a
 * person. These are the screen's words, and two different states can share one - Watching covers a
 * call whose entry has not traded, Not counted covers every way the scorer declined to judge one.
 */
fun Outcome.plainWord(): String = when (this) {
    Outcome.FULL_HIT -> "Target 2 reached"
    Outcome.PARTIAL_HIT -> "Target 1 reached"
    Outcome.STOPPED -> "Stopped out"
    Outcome.EXPIRED -> "Ran out of time"
    Outcome.OPEN -> "Running"
    Outcome.ENTRY_NOT_REACHED -> "Watching"
    Outcome.AMBIGUOUS, Outcome.UNPRICED, Outcome.PRICE_BREAK -> "Not counted"
}

/** A trade's state in the same words, with "You sold" for the one only the reader can cause. */
fun PositionStatus.plainWord(): String = when (this) {
    PositionStatus.OPEN -> "Running"
    PositionStatus.PARTIAL_TARGET_HIT -> "Target 1 reached"
    PositionStatus.FULL_TARGET_HIT -> "Target 2 reached"
    PositionStatus.STOPPED_OUT -> "Stopped out"
    PositionStatus.EXPIRED -> "Ran out of time"
    PositionStatus.CLOSED_MANUALLY -> "You sold"
}

/**
 * A date named the way it would be said aloud, where that is shorter than the date itself.
 *
 * Null past a fortnight back and past tomorrow forward, where the date says it better. "Last week"
 * covers 7 to 13 days ago, so a run from the Sunday before is not "9 days ago".
 */
fun relativeDay(date: LocalDate, today: LocalDate): String? =
    when (val days = ChronoUnit.DAYS.between(today, date)) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        in -6L..-2L -> "${-days} days ago"
        in -13L..-7L -> "Last week"
        else -> null
    }
