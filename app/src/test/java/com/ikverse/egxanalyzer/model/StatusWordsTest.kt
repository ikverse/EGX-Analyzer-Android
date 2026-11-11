package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatusWordsTest {

    @Test
    fun `every outcome and every trade status has a word`() {
        Outcome.entries.forEach { assertTrue(it.name, it.plainWord().isNotBlank()) }
        PositionStatus.entries.forEach { assertTrue(it.name, it.plainWord().isNotBlank()) }
    }

    @Test
    fun `a call and a trade in the same state say the same thing`() {
        assertEquals(Outcome.FULL_HIT.plainWord(), PositionStatus.FULL_TARGET_HIT.plainWord())
        assertEquals(Outcome.PARTIAL_HIT.plainWord(), PositionStatus.PARTIAL_TARGET_HIT.plainWord())
        assertEquals(Outcome.STOPPED.plainWord(), PositionStatus.STOPPED_OUT.plainWord())
        assertEquals(Outcome.EXPIRED.plainWord(), PositionStatus.EXPIRED.plainWord())
        assertEquals(Outcome.OPEN.plainWord(), PositionStatus.OPEN.plainWord())
    }

    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun `the near days have names`() {
        assertEquals("Today", relativeDay(today, today))
        assertEquals("Yesterday", relativeDay(today.minusDays(1), today))
        assertEquals("Tomorrow", relativeDay(today.plusDays(1), today))
    }

    @Test
    fun `days ago run from two to six, then it is last week`() {
        assertEquals("2 days ago", relativeDay(today.minusDays(2), today))
        assertEquals("6 days ago", relativeDay(today.minusDays(6), today))
        assertEquals("Last week", relativeDay(today.minusDays(7), today))
        assertEquals("Last week", relativeDay(today.minusDays(13), today))
    }

    @Test
    fun `beyond those the date says it better`() {
        assertNull(relativeDay(today.minusDays(14), today))
        assertNull(relativeDay(today.plusDays(2), today))
    }
}
