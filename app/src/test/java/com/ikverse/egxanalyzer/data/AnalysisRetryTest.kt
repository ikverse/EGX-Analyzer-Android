package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

/**
 * Which provider answers earn a batch one more try, and how long the pause before it is.
 *
 * The run's own loop cannot be driven without a network; what can be pinned is the rule it
 * follows, which is the part that decides whether money already spent is kept or thrown away.
 */
class AnalysisRetryTest {

    @Test
    fun `a rate limit and a server fault are moments`() {
        assertTrue(isTransientStatus(429))
        assertTrue(isTransientStatus(500))
        assertTrue(isTransientStatus(502))
        assertTrue(isTransientStatus(503))
        assertTrue(isTransientStatus(408))
    }

    @Test
    fun `a refusal about this request is not retried`() {
        assertFalse(isTransientStatus(400))
        assertFalse(isTransientStatus(401))
        assertFalse(isTransientStatus(403))
        assertFalse(isTransientStatus(404))
        assertFalse(isTransientStatus(200))
    }

    @Test
    fun `a rate limit waits longer than a dropped connection`() {
        assertEquals(10_000L, retryDelayMillis(TransientProviderFailure(429, "slow down")))
        assertEquals(3_000L, retryDelayMillis(TransientProviderFailure(503, "unavailable")))
        assertEquals(3_000L, retryDelayMillis(SocketTimeoutException("read timed out")))
    }
}
