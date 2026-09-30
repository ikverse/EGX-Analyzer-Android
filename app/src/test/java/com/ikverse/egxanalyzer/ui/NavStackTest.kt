package com.ikverse.egxanalyzer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the back button undoes, driven without a device.
 *
 * The rule is small and every part of it is a decision that could have gone the other way, which is
 * exactly the kind of thing that gets quietly reversed by a later edit. The two that matter most are
 * that a jump replaces rather than stacks, and that a return is taken once.
 */
class NavStackTest {

    @Test
    fun `nothing to return to on a fresh stack`() {
        val stack = NavStack()
        assertFalse(stack.canReturn)
        assertNull(stack.pop())
    }

    @Test
    fun `a pushed stop comes back whole`() {
        val stack = NavStack()
        stack.push(NavStop(AppDestination.INSIGHTS, callId = "AMOC@2026-08-14"))
        assertTrue(stack.canReturn)
        val stop = stack.pop()
        assertEquals(AppDestination.INSIGHTS, stop?.destination)
        assertEquals("AMOC@2026-08-14", stop?.callId)
        assertNull(stop?.positionId)
    }

    @Test
    fun `a return is taken once`() {
        val stack = NavStack()
        stack.push(NavStop(AppDestination.PORTFOLIO))
        assertEquals(AppDestination.PORTFOLIO, stack.pop()?.destination)
        // The second press has to fall through to the system, or back never leaves the app.
        assertFalse(stack.canReturn)
        assertNull(stack.pop())
    }

    /**
     * One deep, which is the whole design: a pair of cards pointing at each other would otherwise
     * build a history of bounces the reader has to press their way back out of.
     */
    @Test
    fun `a second jump replaces the first`() {
        val stack = NavStack()
        stack.push(NavStop(AppDestination.RESULTS))
        stack.push(NavStop(AppDestination.PORTFOLIO, positionId = "AMOC@2026-07-20"))
        assertEquals(AppDestination.PORTFOLIO, stack.pop()?.destination)
        assertNull(stack.pop())
    }

    @Test
    fun `navigating by hand forgets the jump`() {
        val stack = NavStack()
        stack.push(NavStop(AppDestination.INSIGHTS))
        stack.clear()
        assertFalse(stack.canReturn)
    }
}
