package com.ikverse.egxanalyzer.ui

import android.content.Intent
import android.content.IntentSender
import com.ikverse.egxanalyzer.model.DriveBackup
import com.ikverse.egxanalyzer.model.RestoreOutcome

/**
 * Backing up to, and restoring from, a folder in the owner's Google Drive.
 *
 * A sub-interface of [AppState] in the shape of [AppUpdates] and [HomeFeed], so `LiveAppState`
 * gains every member by delegating to `LiveDriveBackup` with no forwarding written by hand.
 *
 * **Signing in is two halves because Google's consent screen is an activity.** [requestDriveAccess]
 * answers null when access is already granted (and finishes connecting), or the screen to show; the
 * page launches it and hands the result back to [finishDriveAccess]. Nothing here holds an activity.
 */
interface DriveBackups {

    /** False where Google Play services is missing - the Huawei tablet - and Drive cannot be used. */
    val driveAvailable: Boolean

    /** The connected Google account, or null when Drive is not set up on this phone. */
    val driveAccount: String?

    /** Asks for Drive access. Null once granted; otherwise Google's consent screen to launch. */
    suspend fun requestDriveAccess(): IntentSender?

    /** Takes the consent screen's answer, and connects if it granted access. */
    suspend fun finishDriveAccess(data: Intent?)

    /** Forgets the account here and revokes this app's access to it. Backups stay in Drive. */
    fun disconnectDrive()

    /** Writes a backup to the Drive folder now, and returns where it went. */
    suspend fun backUpToDrive(): String

    /** The backups in the Drive folder, newest first. */
    suspend fun driveBackups(): List<DriveBackup>

    /** Downloads one backup and adopts whatever this device is missing from it. */
    suspend fun restoreFromDrive(id: String): RestoreOutcome
}
