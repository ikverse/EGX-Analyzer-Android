package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Whether a second run of the same session has anything to read.
 *
 * The rule that makes more than one schedule a day worth having. It used to be that a report
 * already covering this session and these chats meant skip, full stop - correct while a repeat
 * could only ever be the same request paid for twice, and wrong the moment a midday schedule
 * exists to pick up what was posted after the morning one.
 *
 * The cost of getting this wrong is money in one direction and a missed call in the other, which
 * is why it is arithmetic over message ids rather than a rule about times.
 */
class SourceFreshnessTest {

    @Test
    fun `a session re-read with nothing posted since has nothing new`() {
        val read = listOf(trace(1), trace(2))
        assertTrue(SourceFreshness.newSources(read, read).isEmpty())
    }

    @Test
    fun `a message posted since the report is new`() {
        val fresh = SourceFreshness.newSources(listOf(trace(1), trace(2)), listOf(trace(1), trace(2), trace(3)))
        assertEquals(listOf(3L), fresh.map { it.messageId })
    }

    /** The first run of a session has read nothing, so everything in the window is new. */
    @Test
    fun `with no report behind it every source is new`() {
        assertEquals(2, SourceFreshness.newSources(emptyList(), listOf(trace(1), trace(2))).size)
    }

    /**
     * A source with no message id cannot be shown to have been read before. Erring towards running
     * is right: the run is the thing the user asked for, and this guard is only meant to stop
     * paying for a repeat of nothing.
     */
    @Test
    fun `a source with no message id counts as new`() {
        val fresh = SourceFreshness.newSources(listOf(trace(1)), listOf(trace(1), trace(null)))
        assertEquals(1, fresh.size)
    }

    /** Order and count in the window do not matter; identity does. */
    @Test
    fun `the same messages read back in another order are not new`() {
        val fresh = SourceFreshness.newSources(
            listOf(trace(1), trace(2), trace(3)),
            listOf(trace(3), trace(1)),
        )
        assertTrue(fresh.isEmpty())
    }

    private fun trace(messageId: Long?) = SourceTrace(
        sourceId = "s$messageId",
        channelId = 7L,
        channelName = "Signals",
        messageId = messageId,
        timestamp = Instant.EPOCH,
        contentType = AnalysisContentType.entries.first(),
        preview = "",
    )
}
