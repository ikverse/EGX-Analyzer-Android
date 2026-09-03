package com.ikverse.egxanalyzer.model

/** A prompt as it will be sent, and the identity that lets a run say which one it used. */
data class ComposedPrompt(
    val id: String,
    val text: String,
    val schemaVersion: Int?,
    /** Which rules were folded in, so a version can be explained without diffing 400 lines. */
    val ruleIds: List<String>,
)
