package com.ikverse.egxanalyzer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The three shapes a date filter takes, and the one rule ["accepts"] applies to all of them so a
 * screen's own predicate never has to know which one it is holding.
 */
class DateFilterTest {

    @Test
    fun `an exact filter matches only its own date`() {
        val filter = DateFilter.Exact("2026-08-14")
        assertTrue(filter.matches("2026-08-14"))
        assertFalse(filter.matches("2026-08-15"))
    }

    @Test
    fun `this week matches Monday through Sunday of the current week`() {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        assertTrue(DateFilter.ThisWeek.matches(monday.toString()))
        assertTrue(DateFilter.ThisWeek.matches(monday.plusDays(6).toString()))
        assertFalse(DateFilter.ThisWeek.matches(monday.minusDays(1).toString()))
        assertFalse(DateFilter.ThisWeek.matches(monday.plusDays(7).toString()))
    }

    @Test
    fun `this month matches only dates in the current calendar month`() {
        val today = LocalDate.now()
        assertTrue(DateFilter.ThisMonth.matches(today.withDayOfMonth(1).toString()))
        assertTrue(DateFilter.ThisMonth.matches(today.withDayOfMonth(today.lengthOfMonth()).toString()))
        assertFalse(DateFilter.ThisMonth.matches(today.minusMonths(1).toString()))
        assertFalse(DateFilter.ThisMonth.matches(today.plusMonths(1).toString()))
    }

    @Test
    fun `a null filter accepts everything, including a null date`() {
        val filter: DateFilter? = null
        assertTrue(filter.accepts("2026-08-14"))
        assertTrue(filter.accepts(null))
    }

    @Test
    fun `a real filter rejects a null date`() {
        val filter: DateFilter? = DateFilter.Exact("2026-08-14")
        assertFalse(filter.accepts(null))
    }
}
