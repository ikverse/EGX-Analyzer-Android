package com.ikverse.egxanalyzer.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class AnalysisWindow(
    val start: Instant,
    val endExclusive: Instant,
    val targetDate: LocalDate,
)

private val cairoZone: ZoneId = ScheduleClock.ZONE

fun nextEgxOpenDay(from: LocalDate): LocalDate {
    var candidate = from.plusDays(1)
    while (candidate.dayOfWeek == DayOfWeek.FRIDAY ||
        candidate.dayOfWeek == DayOfWeek.SATURDAY
    ) {
        candidate = candidate.plusDays(1)
    }
    return candidate
}

fun egxTargetSession(now: ZonedDateTime = ZonedDateTime.now(cairoZone)): LocalDate {
    val cairoNow = now.withZoneSameInstant(cairoZone)
    return when (cairoNow.dayOfWeek) {
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY -> nextEgxOpenDay(cairoNow.toLocalDate())
        else -> if (!cairoNow.toLocalTime().isAfter(LocalTime.of(14, 30))) {
            cairoNow.toLocalDate()
        } else {
            nextEgxOpenDay(cairoNow.toLocalDate())
        }
    }
}

/**
 * Which days the "Calls for which day" calendar offers: today and earlier, plus the next trading
 * day - which runs ahead of today after the close and on a weekend. Picking that one is how the
 * reader goes back to "Next trading day"; with the calendar stopped at today it could never be
 * picked once the session had closed.
 */
fun targetDatePickable(date: LocalDate, now: ZonedDateTime = ZonedDateTime.now(cairoZone)): Boolean {
    val cairoNow = now.withZoneSameInstant(cairoZone)
    return !date.isAfter(cairoNow.toLocalDate()) || date == egxTargetSession(cairoNow)
}

/** The last day [targetDatePickable] offers. */
fun latestPickableTarget(now: ZonedDateTime = ZonedDateTime.now(cairoZone)): LocalDate {
    val cairoNow = now.withZoneSameInstant(cairoZone)
    return maxOf(cairoNow.toLocalDate(), egxTargetSession(cairoNow))
}

fun resolveAnalysisWindow(
    mode: AnalysisMode,
    selectedDate: LocalDate?,
    now: ZonedDateTime = ZonedDateTime.now(cairoZone),
): AnalysisWindow {
    val cairoNow = now.withZoneSameInstant(cairoZone)
    if (mode == AnalysisMode.SPECIFIC_DATE) {
        val target = requireNotNull(selectedDate) { "Choose a historical target date." }
        require(!target.isAfter(cairoNow.toLocalDate())) {
            "Historical analysis can only use today or an earlier Cairo date."
        }
        return AnalysisWindow(
            start = target.minusDays(1).atStartOfDay(cairoZone).toInstant(),
            endExclusive = target.plusDays(1).atStartOfDay(cairoZone).toInstant(),
            targetDate = target,
        )
    }

    val target = egxTargetSession(cairoNow)
    val sourceStartDate = if (
        target.dayOfWeek == DayOfWeek.SUNDAY &&
        cairoNow.dayOfWeek in setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
    ) {
        generateSequence(cairoNow.toLocalDate()) { it.minusDays(1) }
            .first { it.dayOfWeek == DayOfWeek.THURSDAY }
    } else {
        cairoNow.toLocalDate().minusDays(1)
    }
    return AnalysisWindow(
        start = sourceStartDate.atStartOfDay(cairoZone).toInstant(),
        endExclusive = cairoNow.toInstant(),
        targetDate = target,
    )
}
