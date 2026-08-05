package com.ikverse.egxanalyzer.model

/**
 * A prompt that was generated, kept so a run can say which one it used.
 *
 * Stored whole rather than regenerated on demand: the rules that produced it can change, and a
 * report read a year from now has to show the text that actually judged it, not the text the same
 * button would produce today.
 */
data class PromptVersion(
    /** A hash of the shipped prompt and the rules folded into it. The same config, the same id. */
    val id: String,
    /** Only for reading aloud - "v4". The id is the identity; two devices can disagree about this. */
    val sequence: Int,
    val text: String,
    val schemaVersion: Int?,
    val ruleIds: List<String>,
    /** What changed to bring this about, in the words the person who changed it would use. */
    val reason: String,
    val device: String,
    val createdAt: Long,
)
