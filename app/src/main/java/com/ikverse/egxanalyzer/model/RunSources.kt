package com.ikverse.egxanalyzer.model

/**
 * How many of the loaded messages each chat contributes to the next run.
 *
 * Read off the message preview, which is exactly what a run starts from - so this costs no Telegram
 * request of its own and cannot disagree with what is sent. Keyed by the chat's id where the message
 * carries one, and by its name where it does not (a source loaded before ids were recorded).
 *
 * "In this run" rather than "new": cards an earlier run already read are taken off when the run is
 * built, after this count, so a figure here is what is loaded, not what is unread.
 */
fun messagesPerSource(sources: List<SourceTrace>): Map<SourceKey, Int> =
    sources.groupingBy { trace -> SourceKey(trace.channelId, trace.channelName.trim()) }.eachCount()

/** A chat as a message names it: its id where known, and its name. */
data class SourceKey(val id: Long?, val name: String) {
    /** Whether this key is the chat [id] / [name], matching by id first and by name only without one. */
    fun matches(id: Long, name: String): Boolean =
        if (this.id != null) this.id == id else this.name.equals(name.trim(), ignoreCase = true)
}

/** The count for one chat out of [counts], or zero. */
fun Map<SourceKey, Int>.forChat(id: Long, name: String): Int =
    entries.filter { it.key.matches(id, name) }.sumOf { it.value }
