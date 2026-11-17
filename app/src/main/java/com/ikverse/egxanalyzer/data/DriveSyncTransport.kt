package com.ikverse.egxanalyzer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * The sync files in a folder of the owner's Google Drive: `EGX Analyzer/sync`.
 *
 * The same files under the same names as the Telegram channel holds, sorted by the same
 * [classifySyncFiles], so every rule about what moves is the one `LiveAppState` already applies.
 * What differs is only how a file is listed, fetched and added.
 *
 * **Files are never rewritten**, as messages in the channel never were: a revision is a new file,
 * and a file's contents never change under its id. That is what makes [cachedText] safe - a rule or
 * trade revision fetched once is read from the phone ever after, so a sync downloads only what is
 * new rather than every revision every time.
 *
 * **Every folder called `sync` is read, and new files go to the oldest**, for the reason the
 * channel reads every duplicate: two devices that each made one before either could see the other
 * would otherwise never meet. A lock keeps two publishes on this device from making two.
 */
internal class DriveSyncTransport(
    private val client: DriveClient,
    private val cacheDir: File,
) : SyncTransport {

    private val folderLock = Mutex()
    private var folderIds: List<String>? = null

    /** One listing reused across the six reads of a single sync, dropped by any write. */
    private var listed: Pair<Long, List<SyncListing<String>>>? = null

    private suspend fun folders(): List<String> = folderLock.withLock {
        folderIds ?: run {
            val parent = client.folder()
            client.folders(SYNC_FOLDER_NAME, parent).ifEmpty {
                listOf(client.createFolder(SYNC_FOLDER_NAME, parent))
            }
        }.also { folderIds = it }
    }

    private suspend fun target(): String = folders().first()

    private suspend fun listings(): List<SyncListing<String>> {
        listed?.let { (at, held) -> if (System.currentTimeMillis() - at < LISTING_FRESH_MILLIS) return held }
        val fresh = folders().map { folder ->
            classifySyncFiles(client.listAll(folder).map { it.name to it.id })
        }
        listed = System.currentTimeMillis() to fresh
        return fresh
    }

    /**
     * Runs one operation on IO, forgetting the folders if it failed so the next call looks again -
     * a folder deleted in Drive must not swallow every later sync.
     */
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (error: Throwable) {
            folderLock.withLock { folderIds = null }
            listed = null
            throw error
        }
    }

    private suspend fun add(name: String, text: String) {
        client.uploadText(text, name, target(), JSON_MIME_TYPE)
        listed = null
    }

    /** A file's text, from the phone if it has been fetched before. See the note on this class. */
    private suspend fun cachedText(id: String): String {
        val dir = File(cacheDir, CACHE_DIR).apply { mkdirs() }
        val held = File(dir, id)
        if (held.exists()) return held.readText()
        val text = client.downloadText(id)
        runCatching { held.writeText(text) }
        return text
    }

    override suspend fun listSyncedReports(): Map<String, RemoteReport> = io {
        newestReports(listings()) { it }
    }

    override suspend fun downloadReport(handle: String): SyncedRun? = io {
        SyncedRun.fromDocument(client.downloadText(handle))
    }

    override suspend fun uploadReport(run: SyncedRun) = io { add(run.fileName, run.toDocument()) }

    override suspend fun listTombstones(): Set<String> = io {
        listings().flatMap { it.tombstones.keys }.toSet()
    }

    override suspend fun buryReport(requestId: String) = io {
        // Every copy, not only the newest: a corrected report is several files, and leaving an
        // earlier one would let another device download it under the tombstone's nose.
        val all = listings()
        all.forEach { listing ->
            listing.reports[requestId].orEmpty().forEach { (id, _) -> runCatching { client.delete(id) } }
        }
        listed = null
        if (all.none { requestId in it.tombstones }) {
            val marker = Tombstone(requestId)
            add(marker.fileName, JSONObject().put("deleted", requestId).toString())
        }
    }

    override suspend fun syncedRules(): List<SyncedRule> = io {
        mergeRules(
            listings().flatMap { it.ruleRevisions }.mapNotNull { id ->
                runCatching { SyncedRule.fromDocument(cachedText(id)) }.getOrNull()
            },
        )
    }

    override suspend fun uploadRule(revision: SyncedRule) = io { add(revision.fileName, revision.toDocument()) }

    override suspend fun syncedPositions(): List<SyncedPosition> = io {
        mergePositions(
            listings().flatMap { it.positionRevisions }.mapNotNull { id ->
                runCatching { SyncedPosition.fromDocument(cachedText(id)) }.getOrNull()
            },
        )
    }

    override suspend fun uploadPosition(revision: SyncedPosition) =
        io { add(revision.fileName, revision.toDocument()) }

    override suspend fun syncedSettings(): SettingsSnapshot? = io {
        // Only the newest in each folder is fetched, by the stamp its name carries - settings are
        // written far more often than read.
        mergeSettings(
            listings().mapNotNull(::newestSettings).mapNotNull { id ->
                runCatching { SettingsSnapshot.fromDocument(cachedText(id)) }.getOrNull()
            },
        )
    }

    override suspend fun uploadSettings(snapshot: SettingsSnapshot) =
        io { add(snapshot.fileName, snapshot.toDocument()) }

    override suspend fun listSyncedPromptVersions(): Map<String, String> = io {
        listings().fold(linkedMapOf()) { all, listing ->
            listing.promptVersions.forEach { (id, handle) -> all.putIfAbsent(id, handle) }
            all
        }
    }

    override suspend fun downloadPromptVersion(handle: String): SyncedPromptVersion? = io {
        SyncedPromptVersion.fromDocument(client.downloadText(handle))
    }

    override suspend fun uploadPromptVersion(version: SyncedPromptVersion) =
        io { add(version.fileName, version.toDocument()) }

    private companion object {
        const val SYNC_FOLDER_NAME = "sync"
        const val JSON_MIME_TYPE = "application/json"
        const val CACHE_DIR = "drive-sync"
        const val LISTING_FRESH_MILLIS = 10_000L
    }
}
