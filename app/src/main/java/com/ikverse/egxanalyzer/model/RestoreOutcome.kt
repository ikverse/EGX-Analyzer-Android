package com.ikverse.egxanalyzer.model

/**
 * What a restore actually brought back, in the terms the person who pressed it would ask.
 *
 * Counts and not a boolean, because the commonest outcome of a restore is that it moved nothing -
 * the device already held everything in the file - and "restored" over that is a lie the user only
 * finds out about later. Naming zero explicitly is the whole value of this type.
 */
data class RestoreOutcome(
    val reports: Int,
    val rules: Int,
    val trades: Int,
    val promptVersions: Int,
    val settingsAdopted: Boolean,
) {
    val movedNothing: Boolean
        get() = reports == 0 && rules == 0 && trades == 0 && promptVersions == 0 && !settingsAdopted

    val summary: String
        get() {
            if (movedNothing) return "Nothing to restore - this device already had it all"
            // Prompt versions are named last and only when nothing else moved. They are plumbing
            // behind a report rather than something anyone recorded, so a count of them beside a
            // count of trades invites the reader to wonder what they lost that they had never heard
            // of - but a restore that moved only these has still moved, and must not report itself
            // as having done nothing.
            val parts = buildList {
                if (reports > 0) add("$reports ${plural(reports, "report")}")
                if (trades > 0) add("$trades ${plural(trades, "trade")}")
                if (rules > 0) add("$rules ${plural(rules, "rule")}")
                if (isEmpty() && !settingsAdopted && promptVersions > 0) {
                    add("$promptVersions ${plural(promptVersions, "prompt version")}")
                }
            }
            return when {
                parts.isEmpty() -> "Settings restored"
                settingsAdopted -> "${parts.joinToString(", ")} and settings restored"
                else -> "${parts.joinToString(", ")} restored"
            }
        }

    private fun plural(count: Int, noun: String) = if (count == 1) noun else noun + "s"
}
