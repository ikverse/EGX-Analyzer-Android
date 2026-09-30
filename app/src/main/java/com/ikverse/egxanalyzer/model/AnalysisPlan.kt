package com.ikverse.egxanalyzer.model

import java.time.LocalDate

/**
 * Everything a run needs to know about what it is analysing, said explicitly.
 *
 * It exists because a run can now start from two places, and the older of the two read the Analyze
 * screen's own fields directly - which chats are ticked, which content types, which target date.
 * A scheduled run has its own answers to all three and no screen to keep them in, and the moment
 * there were two ways to assemble a run there were two sets of rules about what gets sent. One of
 * them would have drifted, and it would have been the unattended one, because nobody is watching
 * it. Both paths build one of these and hand it to one function.
 *
 * Deliberately **not** carrying the provider, the model or the API key. Those follow whatever
 * Settings holds at the moment the run starts: a schedule that pinned a model would go on sending
 * to one the user had moved off, and one that pinned a credential would keep a key they had
 * removed.
 */
data class AnalysisPlan(
    /**
     * The chats to read, with the names they had when the plan was made.
     *
     * Names travel with the ids rather than being looked up at run time. A scheduled run can wake a
     * process whose chat list has not loaded yet, and a report that could not name its own coverage
     * would be one the duplicate check can never match again.
     */
    val channels: List<AnalysedChannel>,
    val contentTypes: Set<AnalysisContentType>,
    val mode: AnalysisMode = AnalysisMode.NEXT_DAY,
    /** Only meaningful for [AnalysisMode.SPECIFIC_DATE]; the next-session mode resolves its own. */
    val targetDate: LocalDate? = null,
) {
    val channelIds: List<Long> get() = channels.map(AnalysedChannel::id)

    val isEmpty: Boolean get() = channels.isEmpty() || contentTypes.isEmpty()
}
