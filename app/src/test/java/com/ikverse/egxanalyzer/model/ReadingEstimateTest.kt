package com.ikverse.egxanalyzer.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingEstimateTest {

    private val start = Instant.parse("2026-11-11T09:00:00Z")

    @Test
    fun `nothing is estimated before the first unit is back`() {
        assertNull(ReadingEstimate.endsAt(start, start.plusSeconds(30), done = 0, total = 6))
    }

    @Test
    fun `two of six in a minute leaves two minutes`() {
        val now = start.plusSeconds(60)
        assertEquals(now.plusSeconds(120), ReadingEstimate.endsAt(start, now, done = 2, total = 6))
    }

    @Test
    fun `nothing is estimated once every unit is in, or with no time passed`() {
        assertNull(ReadingEstimate.endsAt(start, start.plusSeconds(60), done = 6, total = 6))
        assertNull(ReadingEstimate.endsAt(start, start, done = 2, total = 6))
    }

    @Test
    fun `the phrase rounds to the nearest minute and never says none`() {
        assertNull(ReadingEstimate.phrase(start, null))
        assertEquals("Less than a minute to go", ReadingEstimate.phrase(start, start.plusSeconds(44)))
        assertEquals("Less than a minute to go", ReadingEstimate.phrase(start, start.minusSeconds(10)))
        assertEquals("About a minute to go", ReadingEstimate.phrase(start, start.plusSeconds(45)))
        assertEquals("About a minute to go", ReadingEstimate.phrase(start, start.plusSeconds(89)))
        assertEquals("About 2 minutes to go", ReadingEstimate.phrase(start, start.plusSeconds(90)))
        assertEquals("About 3 minutes to go", ReadingEstimate.phrase(start, start.plusSeconds(200)))
    }
}
