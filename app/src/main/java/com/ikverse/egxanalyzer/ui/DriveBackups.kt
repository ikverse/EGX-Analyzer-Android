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

    /**
     * Whether this build can reach Drive at all: through Play services, or with a code where Play
     * services is missing. False only for a build without that second sign-in on such a device.
     */
    val driveAvailable: Boolean

    /** The connected Google account, or null when Drive is not set up on this phone. */
    val driveAccount: String?

    /**
     * Asks for Drive access. Google's consent screen to launch, or null: already granted, or (with
     * no Play services) a code is now in [driveSignInCode] and connecting finishes once it is typed.
     */
    suspend fun requestDriveAccess(): IntentSender?

    /** Takes the consent screen's answer, and connects if it granted access. */
    suspend fun finishDriveAccess(data: Intent?)

    /**
     * A sign-in waiting for the owner to type a code at Google, on a device with no Play services.
     * Null otherwise. Connecting finishes on its own once Google says yes.
     */
    val driveSignInCode: com.ikverse.egxanalyzer.model.DriveSignInCode?

    /** Opens the page the code is typed at. */
    fun openDriveSignInPage()

    /** Stops waiting for the code. */
    fun cancelDriveSignIn()

    /** Forgets the account here and revokes this app's access to it. Backups stay in Drive. */
    fun disconnectDrive()

    /** Writes a backup to the Drive folder now, and returns where it went. */
    suspend fun backUpToDrive(): String

    /** The backups in the Drive folder, newest first. */
    suspend fun driveBackups(): List<DriveBackup>

    /** Downloads one backup and adopts whatever this device is missing from it. */
    suspend fun restoreFromDrive(id: String): RestoreOutcome
}
