package com.ikverse.egxanalyzer.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.ikverse.egxanalyzer.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The whole of this device's record in one file, so losing the phone is not losing the record.
 *
 * Everything already travels through a private Telegram channel, and for a user of this app that is
 * the one cloud account there is certain to be: signing into Telegram is what makes the app work at
 * all. What the channel cannot survive is losing the Telegram account itself, and what it cannot
 * offer is a copy the user holds. This is that copy.
 *
 * **A zip, not the database on its own.** `Save diagnostics` already writes the database out, and it
 * stays exactly as it is - it exists to be read by whoever is chasing a bug. It is not a backup,
 * because settings are not in it: the provider, the model, the wording, the trade window and the
 * prompt history all live in preferences, and an install restored without them comes back holding
 * everyone's reports and scoring them against a window nobody chose. The zip carries both.
 *
 * What is deliberately **not** in it:
 * - **The provider API key**, for the reason it is left out of settings sync: a live cloud
 *   credential does not belong in a file that is about to be copied to a cloud folder. It is
 *   encrypted by Android Keystore and has never been in the database either.
 * - **The Telegram database key**, same reasoning, and it would be useless elsewhere anyway - it is
 *   sealed to the Keystore of the device that made it.
 * - **Prices, sessions and intraday bars** are in the database and so do travel, but nothing depends
 *   on them: they are fetched again from the feed. They are not excluded, only unimportant.
 */

/** `record.db` inside the zip: the database, checkpointed, exactly as it sits on disk. */
internal const val RECORD_ENTRY = "record.db"

/** `settings.json`: the same document that travels to the sync channel, so one reader serves both. */
internal const val SETTINGS_ENTRY = "settings.json"

/** `backup.json`: what wrote the file, so a restore can say why it cannot read one. */
private const val METADATA_ENTRY = "backup.json"

internal const val BACKUP_MIME_TYPE = "application/zip"

/**
 * Half-written backups carry this until they are whole, and are renamed only once they are.
 *
 * A folder the user picked has no equivalent of MediaStore's `IS_PENDING`, and a write that stops
 * part way - the phone sleeping, the folder's cloud app losing its connection - would otherwise
 * leave a truncated zip sitting under the name of a finished one. That is the worst failure this
 * whole file exists to prevent: not an absent backup, which is obvious, but a present one that
 * turns out to be empty on the day it is needed.
 */
private const val PART_SUFFIX = ".part"

/** How many daily backups a folder keeps before the oldest is dropped. */
internal const val BACKUPS_KEPT = 7

/** `egx-backup-2026-08-25.zip`: sorts by date as text, which is what the pruning below relies on. */
internal fun backupFileName(date: LocalDate = LocalDate.now()): String = "$BACKUP_PREFIX$date$BACKUP_SUFFIX"

private const val BACKUP_PREFIX = "egx-backup-"
private const val BACKUP_SUFFIX = ".zip"

/** Whether a name in the chosen folder is one of ours, so pruning never touches anything else. */
internal fun isBackupFileName(name: String): Boolean =
    name.startsWith(BACKUP_PREFIX) && name.endsWith(BACKUP_SUFFIX)

/**
 * Everything a restore needs to know before it opens the record, written in plain JSON.
 *
 * The schema version is the one that matters. A backup taken on a later build carries tables this
 * one has never heard of, and reading it would fail somewhere deep in a query; naming the versions
 * here lets the failure be a sentence about updating the app instead.
 */
internal fun backupMetadata(now: Long = System.currentTimeMillis(), device: String): String =
    JSONObject()
        .put("app", BuildConfig.VERSION_NAME)
        .put("appCode", BuildConfig.VERSION_CODE)
        .put("schema", LocalDataStore.DATABASE_VERSION)
        .put("createdAt", now)
        .put("device", device)
        .toString()

/**
 * Writes the backup itself: the record, the settings, and what made them.
 *
 * [checkpoint] is not optional, for the same reason it is not optional in `saveDatabaseToDownloads`.
 * SQLite runs in write-ahead mode, so the newest commits sit in a `-wal` sidecar until something
 * folds them in - and a backup missing the most recent activity is the shape of backup that looks
 * like it worked. The whole point is the last thing that happened before the phone was lost.
 */
internal fun writeBackup(
    out: OutputStream,
    database: File,
    settings: String,
    device: String,
    checkpoint: () -> Unit,
) {
    checkpoint()
    // Not closed with `use`: the caller owns the stream it handed over, and a ZipOutputStream that
    // closes it would take a SAF document's stream down before the resolver had finished with it.
    val zip = ZipOutputStream(out)
    zip.putNextEntry(ZipEntry(METADATA_ENTRY))
    zip.write(backupMetadata(device = device).toByteArray())
    zip.closeEntry()
    zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
    zip.write(settings.toByteArray())
    zip.closeEntry()
    zip.putNextEntry(ZipEntry(RECORD_ENTRY))
    database.inputStream().use { it.copyTo(zip) }
    zip.closeEntry()
    zip.finish()
    zip.flush()
}

/**
 * Puts a backup in Downloads, which every phone has and no permission is needed to reach.
 *
 * The fallback destination, and the only one on a device whose owner has picked no folder. It goes
 * through the same `writeToDownloads` the spreadsheet export and the diagnostics copy use, so a
 * failure part way through leaves nothing behind rather than a truncated file.
 */
internal fun saveBackupToDownloads(
    context: Context,
    database: File,
    settings: String,
    device: String,
    checkpoint: () -> Unit,
): String = writeToDownloads(context, backupFileName(), BACKUP_MIME_TYPE) { out ->
    writeBackup(out, database, settings, device, checkpoint)
}

/**
 * Puts a backup in the folder the user chose, wherever that folder actually lives.
 *
 * A folder rather than an account, and this is the whole reason the app asks for one: OneDrive,
 * Dropbox, Nextcloud, an SD card and a plain local folder all appear in the same picker, so a user
 * ends up backed up to whatever cloud they already have without this app learning a single one of
 * them, holding a single credential, or costing anything as more people use it.
 *
 * Written under [PART_SUFFIX] and renamed once whole. Returns what the folder ended up calling it.
 */
internal fun writeBackupToFolder(
    context: Context,
    folder: Uri,
    database: File,
    settings: String,
    device: String,
    checkpoint: () -> Unit,
): String {
    val resolver = context.contentResolver
    val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
    val finalName = backupFileName()
    // Today's backup replacing today's earlier one, rather than sitting beside it as "(1)". A day
    // is the unit here: keeping every run of an app that syncs on launch would fill the folder with
    // copies of an unchanged record and push the older days out of the seven that are kept.
    existingDocuments(context, folder).forEach { (name, id) ->
        if (name == finalName || name == finalName + PART_SUFFIX) {
            runCatching {
                DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, id))
            }
        }
    }
    val part = DocumentsContract.createDocument(resolver, parent, BACKUP_MIME_TYPE, finalName + PART_SUFFIX)
        ?: throw IOException("The backup folder would not accept a new file")
    try {
        resolver.openOutputStream(part)?.use { out ->
            writeBackup(out, database, settings, device, checkpoint)
        } ?: throw IOException("The backup folder would not open the file it had just made")
        // A null from renameDocument does not always mean it failed - several providers rename the
        // document and return nothing - so the folder is asked before this is called a failure.
        // Getting that wrong would delete the backup that had just been written successfully.
        val renamed = runCatching { DocumentsContract.renameDocument(resolver, part, finalName) }.getOrNull()
        if (renamed == null && existingDocuments(context, folder).none { (name, _) -> name == finalName }) {
            throw IOException("The backup folder would not name the finished file")
        }
        return renamed?.let { documentName(context, it) } ?: finalName
    } catch (error: Throwable) {
        // The part file exists from createDocument onwards, so an abandoned one has to go here or
        // the folder keeps a half-written zip that the next prune would count as a backup. Safe
        // after a rename that reported nothing: this only runs when the folder confirmed the final
        // name is absent, so whatever is still sitting under the part URI is the failed write.
        runCatching { DocumentsContract.deleteDocument(resolver, part) }
        throw error
    }
}

/**
 * Writes a backup wherever this device is set up to write one, and says where that was.
 *
 * The one entrance, used by the button in Settings and by the daily write on launch alike. Two call
 * sites choosing between a folder and Downloads separately is two places for the rule to be, and
 * the one that drifted would be the automatic one nobody watches.
 *
 * A folder write prunes afterwards rather than before: the old copies are what stands between a
 * failed write and having nothing at all, and deleting them first would open a window - however
 * short - in which this app had removed the user's backups and not yet replaced them.
 */
internal fun writeBackupTo(
    context: Context,
    folder: Uri?,
    database: File,
    settings: String,
    device: String,
    checkpoint: () -> Unit,
): String {
    if (folder == null || !holdsBackupFolder(context, folder)) {
        return "Downloads/" + saveBackupToDownloads(context, database, settings, device, checkpoint)
    }
    val written = writeBackupToFolder(context, folder, database, settings, device, checkpoint)
    pruneBackupFolder(context, folder)
    return backupFolderLabel(folder).trimEnd('/') + "/" + written
}

/**
 * Drops the oldest backups, keeping [keep] of them.
 *
 * Without this a daily backup grows without limit in a folder the user is unlikely to look at, which
 * on a metered cloud plan is a bill and on an SD card is a full card. Seven is a week: long enough
 * that a problem noticed on Monday can be undone from before it, short enough to stay small.
 *
 * Only files this app names are ever considered. The user picked a folder, not a folder this app
 * owns, and deleting anything else in it would be a betrayal of what that picker asked for.
 */
internal fun pruneBackupFolder(context: Context, folder: Uri, keep: Int = BACKUPS_KEPT) {
    val ours = existingDocuments(context, folder)
        .filter { (name, _) -> isBackupFileName(name) }
        // By name, which is by date: the file name carries an ISO date and nothing else varies.
        // Reading the folder's own modified times would be at the mercy of whichever cloud app
        // syncs it, and several rewrite them on upload.
        .sortedBy { (name, _) -> name }
    if (ours.size <= keep) return
    ours.take(ours.size - keep).forEach { (_, id) ->
        runCatching {
            DocumentsContract.deleteDocument(
                context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(folder, id),
            )
        }
    }
}

/** How many backups the chosen folder is holding, for the line in Settings that says so. */
internal fun backupsInFolder(context: Context, folder: Uri): List<String> =
    existingDocuments(context, folder)
        .map { (name, _) -> name }
        .filter(::isBackupFileName)
        .sortedDescending()

/** Every document directly in the folder, as name to id. Empty when the grant has gone. */
private fun existingDocuments(context: Context, folder: Uri): List<Pair<String, String>> = runCatching {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(
        folder,
        DocumentsContract.getTreeDocumentId(folder),
    )
    context.contentResolver.query(
        children,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
        null,
        null,
        null,
    )?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                val id = cursor.getString(1) ?: continue
                add(name to id)
            }
        }
    }.orEmpty()
}.getOrDefault(emptyList())

private fun documentName(context: Context, document: Uri): String? = runCatching {
    context.contentResolver.query(
        document,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull()

/**
 * The chosen folder as something worth showing a person.
 *
 * A tree URI reads as `content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FEGX`,
 * which tells the user nothing about whether they picked the right folder. The document id inside it
 * is `primary:Documents/EGX`, and the part after the colon is the path they actually chose.
 */
internal fun backupFolderLabel(folder: Uri): String = runCatching {
    val id = DocumentsContract.getTreeDocumentId(folder)
    id.substringAfter(':').trim('/').ifBlank { id }
}.getOrNull() ?: folder.lastPathSegment.orEmpty()

/**
 * Whether the app still holds a grant on the folder it was given.
 *
 * A persisted grant survives a reboot but not everything: the user can revoke it, and the folder's
 * provider can be uninstalled or its storage unmounted with it. Checked before every automatic
 * backup, because the alternative is a phone that has quietly not been backing up for a month while
 * Settings still names a folder.
 */
internal fun holdsBackupFolder(context: Context, folder: Uri): Boolean =
    context.contentResolver.persistedUriPermissions.any { it.uri == folder && it.isWritePermission }
