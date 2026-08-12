package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.PromptVersion
import org.json.JSONArray
import org.json.JSONObject

/**
 * One generated prompt, as it travels between devices.
 *
 * A union like reports rather than revisions like rules: the id is a hash of the shipped prompt and
 * the rules folded into it, so a version never changes once written and two devices cannot disagree
 * about one. Without this a restored install still has every report but can no longer show the
 * prompt that produced any of them - the reports name a version id that only the wiped device held.
 */
data class SyncedPromptVersion(val version: PromptVersion) {
    /** `prompt-<id>.json` - the id is the identity, so nothing needs opening to skip it. */
    val fileName: String get() = "$PREFIX${keyFor(version.id)}$SUFFIX"

    fun toDocument(): String = JSONObject()
        .put("id", version.id)
        .put("sequence", version.sequence)
        .put("text", version.text)
        .put("schemaVersion", version.schemaVersion ?: JSONObject.NULL)
        .put("ruleIds", JSONArray(version.ruleIds))
        .put("reason", version.reason)
        .put("device", version.device)
        .put("createdAt", version.createdAt)
        .toString()

    companion object {
        fun fromDocument(text: String): SyncedPromptVersion? = runCatching {
            val json = JSONObject(text)
            val id = json.getString("id").ifBlank { return null }
            SyncedPromptVersion(
                PromptVersion(
                    id = id,
                    sequence = json.optInt("sequence", 1),
                    text = json.getString("text"),
                    schemaVersion = if (json.isNull("schemaVersion")) {
                        null
                    } else {
                        json.optInt("schemaVersion")
                    },
                    ruleIds = json.optJSONArray("ruleIds")
                        ?.let { array -> (0 until array.length()).map(array::getString) }
                        .orEmpty(),
                    reason = json.optString("reason"),
                    device = json.optString("device"),
                    createdAt = json.optLong("createdAt"),
                ),
            )
        }.getOrNull()

        /** The id a file name carries, or null when the file is not one of ours. */
        fun promptIdOf(fileName: String): String? = fileName
            .takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }
            ?.removePrefix(PREFIX)
            ?.removeSuffix(SUFFIX)
            ?.takeIf(String::isNotBlank)

        /**
         * The id in the form a file name can carry it.
         *
         * Both sides of the comparison go through this. A name cannot hold every character an id
         * might, so asking "does the channel already have this one" against the raw id would answer
         * no forever and upload the same version on every sync.
         */
        fun keyFor(id: String): String = id.replace(Regex("[^A-Za-z0-9._:-]"), "_")

        private const val PREFIX = "prompt-"
        private const val SUFFIX = ".json"
    }
}
