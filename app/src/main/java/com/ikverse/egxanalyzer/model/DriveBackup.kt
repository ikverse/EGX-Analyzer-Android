package com.ikverse.egxanalyzer.model

/**
 * One backup sitting in the app's Google Drive folder, as the restore list shows it.
 *
 * [id] is Drive's own file id, which is what a download asks for; [name] is the same
 * `egx-backup-YYYY-MM-DD.zip` a folder backup is written under, so the list reads by date and
 * sorts by name. [bytes] is null where Drive did not say.
 */
data class DriveBackup(
    val id: String,
    val name: String,
    val bytes: Long? = null,
)
