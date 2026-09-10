package com.ikverse.egxanalyzer.data

import org.json.JSONObject

/**
 * A report that was deleted, published so every device forgets it too.
 *
 * Without one, deleting is not a delete: the device that still holds the report sees it missing
 * from the channel, helpfully uploads it again, and it returns on the next sync. The marker is what
 * makes a delete on one device reach the others, so it is permanent by design - there is nothing
 * left to restore from once the report itself is gone.
 */
data class Tombstone(val requestId: String) {
    val fileName: String get() = "$DELETED_PREFIX$requestId.json"

    companion object {
        const val DELETED_PREFIX = "deleted-"

        /** The id a tombstone's file name names, or null when the file is not a tombstone. */
        fun requestIdOf(fileName: String): String? = fileName
            .takeIf { it.startsWith(DELETED_PREFIX) }
            ?.removePrefix(DELETED_PREFIX)
            ?.removeSuffix(".json")
            ?.takeIf(String::isNotBlank)
    }
}

/** What a sync moved, in the terms the user asked the question in. */
data class SyncOutcome(
    val uploaded: Int,
    val downloaded: Int,
    val alreadyHeld: Int,
) {
    val summary: String
        get() = when {
            uploaded == 0 && downloaded == 0 -> "Already in sync"
            downloaded == 0 -> "$uploaded ${runs(uploaded)} uploaded"
            uploaded == 0 -> "$downloaded ${runs(downloaded)} downloaded"
            // Both directions named rather than spelled out: the arrows a sentence would need are
            // longer than the two figures anyone reads this for.
            else -> "$uploaded up, $downloaded down"
        }

    private fun runs(count: Int) = if (count == 1) "run" else "runs"
}

/** One run as it travels: the file name carries its identity, so nothing needs opening to skip it. */
data class SyncedRun(
    val requestId: String,
    val provider: String,
    val model: String,
    val completedAt: String,
    val payload: String,
    /**
     * How many times the report has been corrected, which is the only thing about it that moves.
     *
     * A run's extraction never changes; what the reader has since corrected in it does. This is
     * what the sync compares, and it is in the file name as well as in the document so that
     * deciding which copy is newer costs no downloads.
     */
    val editRevision: Long = 0,
) {
    /**
     * `<requestId>.json`, or `<requestId>-r<n>.json` once the report has been corrected.
     *
     * An uncorrected report keeps the name it has always had, so nothing already in a channel has
     * to be moved or re-uploaded, and a device running an older build goes on recognising it.
     * Revisions accumulate as separate files, exactly as a rule's and a trade's do; which one wins
     * is the merge's decision rather than the order Telegram happens to return them in.
     */
    val fileName: String
        get() = if (editRevision <= 0) "$requestId.json" else "$requestId-r$editRevision.json"

    fun toDocument(): String = JSONObject()
        .put("requestId", requestId)
        .put("provider", provider)
        .put("model", model)
        .put("completedAt", completedAt)
        .put("payload", payload)
        .put("editRevision", editRevision)
        .toString()

    companion object {
        /** Null for anything that is not one of ours, so a stray file in the chat is skipped. */
        fun fromDocument(text: String): SyncedRun? = runCatching {
            val json = JSONObject(text)
            SyncedRun(
                requestId = json.getString("requestId"),
                provider = json.getString("provider"),
                model = json.getString("model"),
                completedAt = json.getString("completedAt"),
                payload = json.getString("payload"),
                editRevision = json.optLong("editRevision", 0),
            ).takeIf { it.requestId.isNotBlank() && it.payload.isNotBlank() }
        }.getOrNull()

        /**
         * The id a file name carries, or null when the name is not one this app wrote.
         *
         * The revision suffix is stripped here rather than treated as part of the id, so a
         * corrected report and its earlier copies are recognised as the same report - which is what
         * lets the newest of them be picked instead of all of them being downloaded as strangers.
         */
        fun requestIdOf(fileName: String): String? = fileName
            .removeSuffix(".json")
            .takeIf { it != fileName && it.isNotBlank() }
            ?.substringBeforeLast(REVISION_MARK)
            ?.takeIf(String::isNotBlank)

        /** The revision a file name carries. Zero for a report nobody has corrected. */
        fun revisionOf(fileName: String): Long {
            val stem = fileName.removeSuffix(".json").takeIf { it != fileName } ?: return 0
            if (REVISION_MARK !in stem) return 0
            return stem.substringAfterLast(REVISION_MARK).toLongOrNull() ?: 0
        }

        /**
         * What separates an id from its revision in a file name.
         *
         * A request id is a UUID, which carries plain hyphens but never `-r` followed by digits, so
         * the two cannot be confused. Split from the **last** mark for the same reason.
         */
        private const val REVISION_MARK = "-r"
    }
}

/**
 * What a sync should do once tombstones and corrections are taken into account.
 *
 * A deleted report is neither uploaded nor downloaded by anyone, whichever side still happens to
 * hold a copy, and any device still holding one removes it. That is the whole of the rule for a
 * delete, and it outranks everything below it.
 */
data class SyncActions(
    val upload: Set<String>,
    val download: Set<String>,
    val forget: Set<String>,
)

/**
 * Which reports each side owes the other.
 *
 * A run's extraction never changes, so this was a union with nothing to resolve. What the reader
 * has **corrected** in a run does change, so it is no longer only about which side holds a copy: a
 * report both sides hold at different revisions travels from whichever holds the newer one. Equal
 * revisions move nothing, which is the ordinary case and has to stay free.
 *
 * Newest revision wins outright rather than being merged field by field. An edit is a deliberate
 * act on one occurrence of one report, and the reader who made the later one was looking at the
 * earlier one's result; merging two of them would produce a report neither device ever showed
 * anybody.
 */
fun syncActions(
    local: Map<String, Long>,
    remote: Map<String, Long>,
    deleted: Set<String>,
): SyncActions = SyncActions(
    upload = local.keys
        .filter { it !in deleted && local.getValue(it) > (remote[it] ?: -1) }
        .toSet(),
    download = remote.keys
        .filter { it !in deleted && remote.getValue(it) > (local[it] ?: -1) }
        .toSet(),
    forget = local.keys intersect deleted,
)
