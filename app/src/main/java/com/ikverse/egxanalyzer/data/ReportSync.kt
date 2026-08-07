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
) {
    /** `<requestId>.json` - readable in a Telegram chat, and enough to tell copies apart. */
    val fileName: String get() = "$requestId.json"

    fun toDocument(): String = JSONObject()
        .put("requestId", requestId)
        .put("provider", provider)
        .put("model", model)
        .put("completedAt", completedAt)
        .put("payload", payload)
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
            ).takeIf { it.requestId.isNotBlank() && it.payload.isNotBlank() }
        }.getOrNull()

        /** The id a file name carries, or null when the name is not one this app wrote. */
        fun requestIdOf(fileName: String): String? =
            fileName.removeSuffix(".json").takeIf { it != fileName && it.isNotBlank() }
    }
}

/**
 * What a sync should do once tombstones are taken into account.
 *
 * A deleted report is neither uploaded nor downloaded by anyone, whichever side still happens to
 * hold a copy, and any device still holding one removes it. That is the whole of the rule.
 */
data class SyncActions(
    val upload: Set<String>,
    val download: Set<String>,
    val forget: Set<String>,
)

fun syncActions(local: Set<String>, remote: Set<String>, deleted: Set<String>): SyncActions =
    SyncActions(
        upload = local - remote - deleted,
        download = remote - local - deleted,
        forget = local intersect deleted,
    )

/**
 * Which runs each side is missing.
 *
 * A saved run never changes once written, so syncing is a union rather than a merge: whatever
 * either side has, both should have. There is no conflict to resolve, no clock to trust, and no
 * way for two devices to disagree about the same run - which is what makes this safe to do
 * automatically.
 */
fun syncPlan(local: Set<String>, remote: Set<String>): Pair<Set<String>, Set<String>> =
    (local - remote) to (remote - local)
