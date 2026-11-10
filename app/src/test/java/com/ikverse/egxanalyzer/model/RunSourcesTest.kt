package com.ikverse.egxanalyzer.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RunSourcesTest {

    private fun trace(id: Long?, name: String) = SourceTrace(
        sourceId = "$name-${Math.random()}", channelId = id, channelName = name, messageId = null,
        timestamp = Instant.EPOCH, contentType = AnalysisContentType.TEXT, preview = "",
    )

    @Test
    fun `messages are counted per chat by id`() {
        val counts = messagesPerSource(listOf(trace(1, "Tadawul Calls"), trace(1, "Tadawul Calls"), trace(2, "Nile Signals")))
        assertEquals(2, counts.forChat(1, "Tadawul Calls"))
        assertEquals(1, counts.forChat(2, "Nile Signals"))
        assertEquals(0, counts.forChat(3, "Cairo Desk"))
    }

    /** Two chats can share a name; the id keeps them apart. */
    @Test
    fun `two chats with one name are kept apart by id`() {
        val counts = messagesPerSource(listOf(trace(1, "Market Brief"), trace(9, "Market Brief")))
        assertEquals(1, counts.forChat(1, "Market Brief"))
        assertEquals(1, counts.forChat(9, "Market Brief"))
    }

    @Test
    fun `a message with no chat id is matched by name`() {
        val counts = messagesPerSource(listOf(trace(null, " Tadawul Calls ")))
        assertEquals(1, counts.forChat(1, "tadawul calls"))
    }
}
