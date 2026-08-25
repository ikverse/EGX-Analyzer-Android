package com.ikverse.egxanalyzer.data

import android.content.Context
import android.net.Uri
import com.ikverse.egxanalyzer.model.PromptVersion
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Reading a backup back in, which is the half of this that was missing.
 *
 * `Save diagnostics` has written the record out since the app was first released and nothing has
 * ever read one. A user whose phone was lost, whose Telegram account went, or who simply cleared
 * the app's storage was holding a file that nothing on earth could do anything with. That is the
 * gap: not which cloud the record sits in, but whether there is a way back from a copy of it.
 *
 * **A restore only ever adds.** It adopts what the backup knows and this device does not, and it
 * never deletes anything that is here. Deletes travel between devices through the sync channel,
 * where a merge belongs and where both sides are live; a file is a snapshot of one moment, and
 * letting a moment from last week remove a trade recorded yesterday would make restoring dangerous
 * to reach for. Somebody opens this because something is missing, and the one outcome they must
 * never get is more missing.
 */

/**
 * A backup written by a build later than this one.
 *
 * Thrown from `LocalDataStore.onDowngrade`, which is the only thing that can notice: the app's own
 * database is never ahead of the app, so nothing but a restore can put an unfamiliar schema in
 * front of it. Carries both versions so the message can say which way round the problem is - the
 * file is fine and the app is behind it, which is a sentence that ends with someone updating rather
 * than someone deleting their only copy.
 */
class BackupTooNewException(
    val backupSchema: Int,
    val appSchema: Int,
) : RuntimeException(
    "This backup was made by a newer version of the app (record version $backupSchema, " +
        "this build reads $appSchema). Update the app, then restore again.",
)

/** A file that is neither a backup nor a database, said in the terms the user picked it in. */
class NotABackupException : RuntimeException(
    "That file is not an EGX Analyzer backup. Pick an egx-backup-….zip, or a diagnostics .db file.",
)

/**
 * Everything a backup holds, read out and closed again.
 *
 * Plain lists rather than an open database, and deliberately: the merge that follows is the part
 * worth testing, and handing it data instead of a cursor keeps it ordinary Kotlin that a unit test
 * can drive without Android. It also means the scratch copy of the backup is deleted before a
 * single record is adopted, rather than being held open across the whole merge.
 */
data class BackupRecord(
    val runs: List<SyncedRun>,
    val rules: List<SyncedRule>,
    val positions: List<SyncedPosition>,
    val promptVersions: List<PromptVersion>,
    /** Null for a bare `.db`, which carries no settings - see [readBackup]. */
    val settings: SettingsSnapshot?,
) {
    val isEmpty: Boolean
        get() = runs.isEmpty() && rules.isEmpty() && positions.isEmpty() &&
            promptVersions.isEmpty() && settings == null
}

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

/**
 * Which wording rules a restore should take out of a backup.
 *
 * The comparison is `rulesToUpload`'s, and deliberately the same one: `(updatedAt, device)`, newest
 * wins, ties broken by the device that wrote it. What is different is the **filter on deletes**. A
 * merge between two live devices carries a tombstone, because that is what makes a delete stick;
 * a backup is one moment preserved, and letting last week's moment remove a rule edited yesterday
 * would make restoring something nobody dares press. Deletes go on travelling through the channel.
 */
fun rulesToRestore(mine: List<SyncedRule>, backup: List<SyncedRule>): List<SyncedRule> {
    val here = mine.associateBy { it.rule.id }
    return backup.filterNot { it.deleted }.filter { incoming ->
        val ours = here[incoming.rule.id] ?: return@filter true
        incoming.rule.updatedAt > ours.rule.updatedAt ||
            (incoming.rule.updatedAt == ours.rule.updatedAt && incoming.rule.updatedBy > ours.rule.updatedBy)
    }
}

/** Which trades a restore should take, by exactly the rule [rulesToRestore] follows. */
fun positionsToRestore(mine: List<SyncedPosition>, backup: List<SyncedPosition>): List<SyncedPosition> {
    val here = mine.associateBy { it.position.id }
    return backup.filterNot { it.deleted }.filter { incoming ->
        val ours = here[incoming.position.id] ?: return@filter true
        incoming.position.updatedAt > ours.position.updatedAt ||
            (
                incoming.position.updatedAt == ours.position.updatedAt &&
                    incoming.position.updatedBy > ours.position.updatedBy
                )
    }
}

/**
 * Which runs a restore should take: whatever this device is missing, minus what it has buried.
 *
 * A union like the sync's, because a saved run never changes and there is nothing to merge. The
 * subtraction that matters is [buried] - a report deleted here but not yet published as a tombstone.
 * That delete is a decision already taken and still in flight, and restoring over it would undo it
 * silently, leaving a tombstone about to be published for a report that is on disk again.
 */
fun runsToRestore(held: Set<String>, buried: Set<String>, backup: List<SyncedRun>): List<SyncedRun> =
    backup.filter { it.requestId !in held && it.requestId !in buried }

/**
 * Which generated prompts a restore should take. A union, keyed the way the channel keys them.
 *
 * Without these a restored install holds every report and can no longer show the prompt any of them
 * was judged under - the same reason they travel between devices at all.
 */
fun promptVersionsToRestore(mine: Set<String>, backup: List<PromptVersion>): List<PromptVersion> =
    backup.filter { SyncedPromptVersion.keyFor(it.id) !in mine }

/**
 * Opens whatever the user picked and reads the record out of it.
 *
 * Two files are accepted, told apart by what is actually in them rather than by what they are
 * called - a file that has been round a cloud folder and back may arrive named anything:
 *
 * - **`egx-backup-….zip`**, which carries the record and the settings both. This is the one to make.
 * - **A bare `.db`**, which is what `Save diagnostics` has always written. It has no settings in it,
 *   so a restore from one brings back every report, rule and trade and leaves the provider, the
 *   model and the wording as they are. Accepted because those files are already out there on
 *   people's phones and computers, and a backup format that cannot read them would strand the only
 *   copies some users have.
 *
 * The backup is opened through [LocalDataStore] rather than by a reader written for it, so
 * `onUpgrade` runs over an older file exactly as an app update would and it is read through today's
 * columns. A file from a *newer* build cannot be migrated backwards and raises
 * [BackupTooNewException] instead of failing somewhere inside a query.
 */
fun readBackup(context: Context, source: Uri): BackupRecord {
    val scratch = File(context.cacheDir, SCRATCH_FILE)
    scratch.parentFile?.mkdirs()
    context.contentResolver.openInputStream(source)?.use { input ->
        scratch.outputStream().use { input.copyTo(it) }
    } ?: throw IOException("Android would not open the file that was picked")
    try {
        return readScratch(context, scratch)
    } finally {
        scratch.delete()
    }
}

private fun readScratch(context: Context, scratch: File): BackupRecord {
    // The database has to sit in the app's own databases directory: SQLiteOpenHelper takes a name,
    // not a path, and that is the price of reading a backup through the same class that reads the
    // live record - which is worth paying, because the alternative is a second reader carrying its
    // own copy of every migration.
    val restored = context.getDatabasePath(RESTORE_DATABASE)
    restored.parentFile?.mkdirs()
    clearRestoreDatabase(context)
    var settings: SettingsSnapshot? = null
    when (magicOf(scratch)) {
        Magic.ZIP -> {
            var foundRecord = false
            ZipInputStream(scratch.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        RECORD_ENTRY -> {
                            restored.outputStream().use { zip.copyTo(it) }
                            foundRecord = true
                        }
                        SETTINGS_ENTRY -> settings = SettingsSnapshot.fromDocument(zip.readBytes().decodeToString())
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            if (!foundRecord) throw NotABackupException()
        }
        // A diagnostics copy, or a backup someone unzipped by hand. No settings travel in one.
        Magic.SQLITE -> scratch.inputStream().use { input ->
            restored.outputStream().use { input.copyTo(it) }
        }
        Magic.UNKNOWN -> throw NotABackupException()
    }
    // Opening it is what runs the migrations, so the first read below is where a file older than
    // the app becomes readable, and where one newer than it raises BackupTooNewException.
    val store = LocalDataStore(context, RESTORE_DATABASE)
    try {
        val record = BackupRecord(
            runs = store.storedRuns(),
            rules = store.wordingRuleRevisions().map { (rule, deleted) -> SyncedRule(rule, deleted) },
            positions = store.positionRevisions().map { revision ->
                SyncedPosition(revision.position, revision.deleted, revision.unknown)
            },
            promptVersions = store.promptVersions(),
            settings = settings,
        )
        if (record.isEmpty) throw NotABackupException()
        return record
    } finally {
        store.close()
        clearRestoreDatabase(context)
    }
}

/**
 * Removes the scratch database and the two sidecars SQLite keeps beside it.
 *
 * Both ends of the read, not only the far end: a restore that failed part way through leaves a
 * half-migrated file behind, and the next attempt would open *that* rather than the backup it was
 * given, and quietly restore whatever the failed attempt had got as far as.
 */
private fun clearRestoreDatabase(context: Context) {
    val base = context.getDatabasePath(RESTORE_DATABASE)
    listOf(base, File(base.path + "-wal"), File(base.path + "-shm")).forEach { it.delete() }
}

private enum class Magic { ZIP, SQLITE, UNKNOWN }

/**
 * What the file actually is, from its first bytes.
 *
 * By content rather than by extension. A file that has been through a cloud folder, a chat and a
 * downloads directory can arrive called anything at all, and refusing a perfectly good backup over
 * its name is the sort of thing that happens on the one day it matters.
 */
private fun magicOf(file: File): Magic {
    val head = ByteArray(16)
    val read = file.inputStream().use { it.read(head) }
    if (read < 4) return Magic.UNKNOWN
    val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    if (head.copyOfRange(0, 4).contentEquals(zip)) return Magic.ZIP
    if (read >= 15 && head.decodeToString(0, 15) == "SQLite format 3") return Magic.SQLITE
    return Magic.UNKNOWN
}

private const val SCRATCH_FILE = "restore-source"
private const val RESTORE_DATABASE = "restore-scratch.db"
