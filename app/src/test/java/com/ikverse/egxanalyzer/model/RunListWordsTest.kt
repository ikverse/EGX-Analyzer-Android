package com.ikverse.egxanalyzer.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words and the order the Results list and an open report are built from. */
class RunListWordsTest {

    private val cairo = ZoneId.of("Africa/Cairo")

    /** A Wednesday. */
    private val today = LocalDate.parse("2026-11-11")

    @Test
    fun `a week runs Sunday to Saturday, the exchange's way`() {
        assertEquals("This week", weekHeading(LocalDate.parse("2026-11-08"), today))
        assertEquals("This week", weekHeading(today, today))
        assertEquals("Last week", weekHeading(LocalDate.parse("2026-11-07"), today))
        assertEquals("Last week", weekHeading(LocalDate.parse("2026-11-01"), today))
        assertEquals("Week of 25 Oct", weekHeading(LocalDate.parse("2026-10-29"), today))
        assertEquals("Next week", weekHeading(LocalDate.parse("2026-11-15"), today))
    }

    @Test
    fun `how often a session was read is said the way it would be spoken`() {
        val at = Instant.parse("2026-11-09T07:12:00Z")
        assertEquals("read once at 09:12", readLine(1, at, cairo))
        assertEquals("read twice, last at 09:12", readLine(2, at, cairo))
        assertEquals("read 5 times, last at 09:12", readLine(5, at, cairo))
    }

    @Test
    fun `a call past its target sorts ahead of one approaching it, and a watch last`() {
        val reached = closenessKey(CallGroup.REACHED, target1 = 11.0, close = 11.5)
        val near = closenessKey(CallGroup.RUNNING, target1 = 11.0, close = 10.9)
        val far = closenessKey(CallGroup.RUNNING, target1 = 11.0, close = 10.0)
        val unpriced = closenessKey(CallGroup.RUNNING, target1 = 11.0, close = null)
        val stopped = closenessKey(CallGroup.STOPPED, target1 = 11.0, close = 9.0)
        val watching = closenessKey(CallGroup.WATCHING, target1 = null, close = 10.0)
        val order = listOf(reached, near, far, unpriced, stopped, watching)
        assertEquals(order, order.sorted())
        assertTrue(order.toSet().size == order.size)
    }
}
