package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.RuleKind
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.RuleScope
import com.ikverse.egxanalyzer.model.RuleSlot
import com.ikverse.egxanalyzer.model.WordingRule
import org.json.JSONArray
import org.json.JSONObject

/**
 * One revision of one rule, as it travels between devices.
 *
 * Rules are a table, and a table is not a set of files the way saved reports are: a row can be
 * edited, switched off, and deleted, and two devices can do different things to it while offline.
 * So what travels is the revision, not the row - an append-only log the channel is already shaped
 * to carry, with the merge deciding which revision wins rather than the order they arrived in.
 */
data class SyncedRule(
    val rule: WordingRule,
    val deleted: Boolean,
    /**
     * Fields a newer app version wrote that this one does not understand.
     *
     * Kept and written back untouched. Without this, an older device reading and re-uploading a
     * rule would quietly strip whatever the newer one had added to it.
     */
    val unknown: String = "{}",
) {
    /**
     * Names the rule and the revision, so two edits made offline are two documents rather than one
     * overwriting the other before anyone has compared them.
     */
    val fileName: String get() = "rule-${rule.id.sanitized()}-${rule.updatedAt}.json"

    fun toDocument(): String {
        // Held as text rather than a JSONObject: that class has no equals, so a rule would never
        // compare equal to itself and every sync would look like a change.
        val json = runCatching { JSONObject(unknown) }.getOrDefault(JSONObject())
        return json
            .put("id", rule.id)
            .put("slot", rule.slot.name)
            .put("kind", rule.kind.name)
            .put("phrase", rule.phrase)
            .put("scope", rule.scope.name)
            .put("enabled", rule.enabled)
            .put("origin", rule.origin.name)
            .put("channels", JSONArray(rule.channels.toList()))
            .put("note", rule.note)
            .put("updatedAt", rule.updatedAt)
            .put("updatedBy", rule.updatedBy)
            .put("deleted", deleted)
            .toString()
    }

    companion object {
        private val KNOWN = setOf(
            "id", "slot", "kind", "phrase", "scope", "enabled", "origin",
            "channels", "note", "updatedAt", "updatedBy", "deleted",
        )

        fun fromDocument(text: String): SyncedRule? = runCatching {
            val json = JSONObject(text)
            val id = json.getString("id").ifBlank { return null }
            val unknown = JSONObject()
            json.keys().forEach { key -> if (key !in KNOWN) unknown.put(key, json.get(key)) }
            SyncedRule(
                rule = WordingRule(
                    id = id,
                    slot = RuleSlot.valueOf(json.getString("slot")),
                    kind = RuleKind.valueOf(json.getString("kind")),
                    phrase = json.getString("phrase"),
                    scope = RuleScope.valueOf(json.getString("scope")),
                    enabled = json.optBoolean("enabled", true),
                    origin = RuleOrigin.valueOf(json.optString("origin", RuleOrigin.USER.name)),
                    channels = json.optJSONArray("channels")
                        ?.let { array -> (0 until array.length()).map(array::getLong).toSet() }
                        .orEmpty(),
                    note = json.optString("note").takeIf { it.isNotBlank() && it != "null" },
                    updatedAt = json.optLong("updatedAt"),
                    updatedBy = json.optString("updatedBy"),
                ),
                deleted = json.optBoolean("deleted", false),
                unknown = unknown.toString(),
            )
        }.getOrNull()

        /** A rule this app version cannot make sense of is skipped, not guessed at. */
        fun ruleIdOf(fileName: String): String? = fileName
            .takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }
            ?.removePrefix(PREFIX)
            ?.removeSuffix(SUFFIX)
            ?.substringBeforeLast('-')
            ?.takeIf(String::isNotBlank)

        private const val PREFIX = "rule-"
        private const val SUFFIX = ".json"

        /** File names cannot carry every character an id might; the id inside the file is the truth. */
        private fun String.sanitized(): String = replace(Regex("[^A-Za-z0-9._:-]"), "_")
    }
}

/**
 * Decides which revision of each rule wins.
 *
 * Last writer wins, by the moment it was written, with the device name breaking a tie so two
 * devices that edited within the same millisecond still agree on the answer. A delete is a
 * revision like any other, so it can be overtaken by a later edit rather than being permanent -
 * and an earlier edit can never resurrect it.
 */
fun mergeRules(revisions: List<SyncedRule>): List<SyncedRule> = revisions
    .groupBy { it.rule.id }
    .mapNotNull { (_, forId) ->
        forId.maxWithOrNull(
            compareBy<SyncedRule> { it.rule.updatedAt }.thenBy { it.rule.updatedBy },
        )
    }
    .sortedBy { it.rule.id }

/** What a device has to send, having seen what the channel holds. */
fun rulesToUpload(local: List<SyncedRule>, remote: List<SyncedRule>): List<SyncedRule> {
    val newest = remote.associateBy { it.rule.id }
    return local.filter { mine ->
        val theirs = newest[mine.rule.id] ?: return@filter true
        mine.rule.updatedAt > theirs.rule.updatedAt ||
            (mine.rule.updatedAt == theirs.rule.updatedAt && mine.rule.updatedBy > theirs.rule.updatedBy)
    }
}
