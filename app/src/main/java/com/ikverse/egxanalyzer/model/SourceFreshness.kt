package com.ikverse.egxanalyzer.model

/**
 * Whether a fresh read of the chats holds anything a saved report did not already cover.
 *
 * The question a second scheduled run of the same session has to answer. Before there could be
 * more than one schedule a day the answer was assumed: a report already covering this session and
 * these chats meant skip, full stop, because a repeat could only ever be the same request paid for
 * twice. With a morning and a midday schedule that is no longer true - the whole point of the
 * second is the calls posted after the first - so the test moves from "has this been analysed" to
 * "has anything been said since".
 *
 * Asked of the messages rather than of the clock. Two runs an hour apart with nothing posted
 * between them are the same request whatever the times say, and a chat that posted once in that
 * hour is a different one. Collecting the sources to find out is free: it reads Telegram's own
 * store and reaches no provider.
 */
object SourceFreshness {

    /**
     * The messages in [collected] that [saved] did not already contain.
     *
     * Matched on message id, which is Telegram's own identity for a post and the only thing here
     * that survives being re-read. A source with no message id cannot be shown to have been read
     * before, so it counts as new: erring towards running is right, since the run is the thing the
     * user asked for and the guard is only meant to stop paying for a repeat of nothing.
     */
    fun newSources(saved: List<SourceTrace>, collected: List<SourceTrace>): List<SourceTrace> {
        val read = saved.mapNotNullTo(mutableSetOf(), SourceTrace::messageId)
        return collected.filter { it.messageId == null || it.messageId !in read }
    }
}
