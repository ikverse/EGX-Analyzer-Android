package com.ikverse.egxanalyzer.data

/**
 * Where the sync files live: the Telegram channel, or a folder in Google Drive.
 *
 * Everything that decides what moves - who wins, what a tombstone buries, which settings are newest
 * - is in `LiveAppState` and the `*Sync.kt` files, and none of it ever cared what carried the files.
 * Each record already travels as one small JSON file whose name says what it is, so a transport only
 * has to list names, fetch one, and add one. This is that surface, lifted off `TelegramRepository`
 * when Google Drive became the second place it could go.
 *
 * A [RemoteReport.handle] and a prompt's handle are whatever the transport needs to fetch the file
 * again - a Telegram file id as text, or a Drive file id - and are meaningless outside it.
 */
interface SyncTransport {
    suspend fun listSyncedReports(): Map<String, RemoteReport>
    suspend fun downloadReport(handle: String): SyncedRun?
    suspend fun uploadReport(run: SyncedRun)
    suspend fun listTombstones(): Set<String>

    /** Deletes every copy of a report and leaves a tombstone, so no device brings it back. */
    suspend fun buryReport(requestId: String)
    suspend fun syncedRules(): List<SyncedRule>
    suspend fun uploadRule(revision: SyncedRule)
    suspend fun syncedPositions(): List<SyncedPosition>
    suspend fun uploadPosition(revision: SyncedPosition)
    suspend fun syncedSettings(): SettingsSnapshot?
    suspend fun uploadSettings(snapshot: SettingsSnapshot)
    suspend fun listSyncedPromptVersions(): Map<String, String>
    suspend fun downloadPromptVersion(handle: String): SyncedPromptVersion?
    suspend fun uploadPromptVersion(version: SyncedPromptVersion)
}

/** Which copy of a report the store holds, and how to fetch it. */
data class RemoteReport(val handle: String, val editRevision: Long)

/**
 * Every sync file a store holds, sorted by what its name says it is.
 *
 * [H] is the transport's own way of reaching a file again. Newest-first order is the caller's to
 * give: where two files claim the same slot (a tombstone, a settings name, a prompt), the first
 * one seen is kept, which is what Telegram's newest-first history always did.
 */
internal data class SyncListing<H>(
    /** Every uploaded copy of every report, with its revision, because a corrected one has several. */
    val reports: Map<String, List<Pair<H, Long>>>,
    val tombstones: Map<String, H>,
    val ruleRevisions: List<H>,
    val positionRevisions: List<H>,
    /** By file name: the name carries the stamp and the device the settings merge decides on. */
    val settingsRevisions: Map<String, H>,
    val promptVersions: Map<String, H>,
)

/**
 * Sorts file names into [SyncListing], in the one order that reads them right.
 *
 * The order is the whole of this function. Every name ends in `.json`, so anything not claimed
 * before the report branch is read as a report: a tombstone `deleted-<id>.json` would be a report
 * called "deleted-<id>", a position a report called "position-AMOC_...". It lived inside
 * `TelegramRepository` until Drive needed the same answer; two copies of it would be two places for
 * that order to drift.
 */
internal fun <H> classifySyncFiles(files: Iterable<Pair<String, H>>): SyncListing<H> {
    val reports = linkedMapOf<String, MutableList<Pair<H, Long>>>()
    val tombstones = linkedMapOf<String, H>()
    val ruleRevisions = mutableListOf<H>()
    val positionRevisions = mutableListOf<H>()
    val settingsRevisions = linkedMapOf<String, H>()
    val promptVersions = linkedMapOf<String, H>()
    files.forEach { (name, handle) ->
        val buried = Tombstone.requestIdOf(name)
        if (buried != null) {
            tombstones.putIfAbsent(buried, handle)
            return@forEach
        }
        if (SyncedRule.ruleIdOf(name) != null) {
            ruleRevisions += handle
            return@forEach
        }
        if (SyncedPosition.positionIdOf(name) != null) {
            positionRevisions += handle
            return@forEach
        }
        if (SettingsSnapshot.stampOf(name) != null) {
            settingsRevisions.putIfAbsent(name, handle)
            return@forEach
        }
        SyncedPromptVersion.promptIdOf(name)?.let {
            promptVersions.putIfAbsent(it, handle)
            return@forEach
        }
        SyncedRun.requestIdOf(name)?.let {
            reports.getOrPut(it, ::mutableListOf) += handle to SyncedRun.revisionOf(name)
        }
    }
    return SyncListing(reports, tombstones, ruleRevisions, positionRevisions, settingsRevisions, promptVersions)
}

/** The newest revision of each report across [listings], which is the copy a download should fetch. */
internal fun <H> newestReports(listings: List<SyncListing<H>>, handleText: (H) -> String): Map<String, RemoteReport> =
    listings.fold(linkedMapOf()) { all, listing ->
        listing.reports.forEach { (id, copies) ->
            val (handle, revision) = copies.maxBy { it.second }
            val held = all[id]
            if (held == null || revision > held.editRevision) all[id] = RemoteReport(handleText(handle), revision)
        }
        all
    }

/** The handle of the newest settings revision in [listing], by the stamp its name carries. */
internal fun <H> newestSettings(listing: SyncListing<H>): H? =
    listing.settingsRevisions.entries
        .mapNotNull { (name, handle) -> SettingsSnapshot.stampOf(name)?.let { it to handle } }
        .maxByOrNull { (stamp, _) -> stamp }
        ?.second
