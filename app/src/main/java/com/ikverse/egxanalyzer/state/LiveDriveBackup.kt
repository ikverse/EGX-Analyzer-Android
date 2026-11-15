package com.ikverse.egxanalyzer.state

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.ikverse.egxanalyzer.data.DRIVE_FOLDER_NAME
import com.ikverse.egxanalyzer.data.DRIVE_SCOPE
import com.ikverse.egxanalyzer.data.DriveBackupWorker
import com.ikverse.egxanalyzer.data.DriveClient
import com.ikverse.egxanalyzer.data.SettingsRepository
import com.ikverse.egxanalyzer.data.backupFileName
import com.ikverse.egxanalyzer.data.driveBackupsNewestFirst
import com.ikverse.egxanalyzer.data.driveFilesToRemove
import com.ikverse.egxanalyzer.model.DriveBackup
import com.ikverse.egxanalyzer.model.RestoreOutcome
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.ui.DriveBackups
import com.ikverse.egxanalyzer.ui.StatusMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The Google Drive backup, the third region to live outside [LiveAppState] - see [LiveUpdates] for
 * the pattern and `data/DriveBackup.kt` for why Drive needs its own path beside the folder backup.
 *
 * What it needs back from the host - writing the zip, and adopting a backup that has been read -
 * arrives through [bind], as [LiveHome]'s does, because a constructor default cannot read `this`.
 *
 * **The token is never stored.** Google Play services holds the grant and hands out a short-lived
 * access token on every [authorize]; this class keeps only the account's address, so it can ask for
 * that same account silently and name it in Settings.
 */
class LiveDriveBackup(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val status: StatusChannel,
) : DriveBackups {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var writeZip: (OutputStream) -> Unit = { throw IOException("Not ready") }
    private var restore: suspend (Uri) -> RestoreOutcome = { throw IOException("Not ready") }

    fun bind(write: (OutputStream) -> Unit, restore: suspend (Uri) -> RestoreOutcome) {
        writeZip = write
        this.restore = restore
    }

    override val driveAvailable: Boolean by lazy {
        runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }

    override var driveAccount by mutableStateOf(settingsRepository.driveAccount())
        private set

    private val client = DriveClient { fresh -> token(fresh) }

    private val scopes = listOf(Scope(DRIVE_SCOPE))

    private fun request(account: String?): AuthorizationRequest =
        AuthorizationRequest.Builder()
            .setRequestedScopes(scopes)
            .apply { account?.let { setAccount(Account(it, "com.google")) } }
            .build()

    private suspend fun authorize(account: String?): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request(account)).await()

    /**
     * A token for the connected account, without showing anything.
     *
     * [fresh] after Drive refused the last one: the cached token is cleared first so Play services
     * mints a new one rather than handing the expired one back.
     */
    private suspend fun token(fresh: Boolean): String {
        val account = driveAccount ?: throw IOException("Google Drive is not connected")
        var result = authorize(account)
        if (fresh) {
            result.accessToken?.let { stale ->
                runCatching {
                    Identity.getAuthorizationClient(context)
                        .clearToken(ClearTokenRequest.builder().setToken(stale).build()).await()
                }
            }
            result = authorize(account)
        }
        if (result.hasResolution()) {
            throw IOException("Google Drive needs signing in again - connect it in Settings")
        }
        return result.accessToken ?: throw IOException("Google Drive gave no access")
    }

    override suspend fun requestDriveAccess(): IntentSender? {
        val result = authorize(null)
        if (result.hasResolution()) {
            return result.pendingIntent?.intentSender
                ?: throw IOException("Google would not show its sign-in")
        }
        connect(result)
        return null
    }

    override suspend fun finishDriveAccess(data: Intent?) {
        val result = runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data) }
            .getOrElse { throw IOException("Google Drive was not connected") }
        connect(result)
    }

    /**
     * Learns which account was granted, and makes the folder, before calling Drive connected.
     *
     * The account is read from Drive itself rather than from the grant: `AuthorizationResult` names
     * it only through a deprecated sign-in type, and asking Drive also proves the token works.
     */
    private suspend fun connect(result: AuthorizationResult) {
        val granted = result.accessToken ?: throw IOException("Google Drive gave no access")
        val first = DriveClient { granted }
        val email = withContext(Dispatchers.IO) {
            val email = first.accountEmail() ?: throw IOException("Google Drive did not say which account it is")
            first.folder()
            email
        }
        settingsRepository.saveDriveAccount(email)
        driveAccount = email
        status.message = StatusMessage("Backing up to Google Drive as $email", succeeded = true)
    }

    override fun disconnectDrive() {
        val account = driveAccount ?: return
        settingsRepository.saveDriveAccount(null)
        driveAccount = null
        scope.launch {
            // Revoked so the grant does not outlive the owner's decision; the backups themselves are
            // the owner's files and stay in their Drive.
            runCatching {
                Identity.getAuthorizationClient(context).revokeAccess(
                    RevokeAccessRequest.builder()
                        .setAccount(Account(account, "com.google"))
                        .setScopes(scopes)
                        .build(),
                ).await()
            }
        }
        status.message = StatusMessage("Google Drive disconnected. Backups already there stay", succeeded = true)
    }

    override suspend fun backUpToDrive(): String = withContext(Dispatchers.IO) {
        val name = backupFileName()
        val scratch = File(context.cacheDir, "drive-upload.zip")
        try {
            scratch.outputStream().use(writeZip)
            val folder = client.folder()
            val written = client.upload(scratch, name, folder)
            // Only once the new copy is whole: the old ones are what stands between a failed upload
            // and having nothing at all.
            driveFilesToRemove(client.list(folder), writtenId = written, writtenName = name)
                .forEach { runCatching { client.delete(it.id) } }
            settingsRepository.recordDriveBackupDay(today())
            "Google Drive/$DRIVE_FOLDER_NAME/$name"
        } finally {
            scratch.delete()
        }
    }

    override suspend fun driveBackups(): List<DriveBackup> = withContext(Dispatchers.IO) {
        driveBackupsNewestFirst(client.list(client.folder()))
    }

    override suspend fun restoreFromDrive(id: String): RestoreOutcome {
        val scratch = File(context.cacheDir, "drive-restore.zip")
        try {
            withContext(Dispatchers.IO) { client.download(id, scratch) }
            return restore(Uri.fromFile(scratch))
        } finally {
            scratch.delete()
        }
    }

    /**
     * Queues today's Drive backup if it is due, to run once the phone has a connection.
     *
     * Its own day, apart from the folder backup's, so a phone that uses both writes both. The day
     * is recorded by [backUpToDrive] only when the upload is whole.
     */
    fun backUpIfDue() {
        if (!driveAvailable || driveAccount == null) return
        if (settingsRepository.lastDriveBackupDay() == today()) return
        DriveBackupWorker.enqueue(context)
    }

    private fun today(): String = LocalDate.now(ScheduleClock.ZONE).toString()
}

/** A Play services task as a suspend call. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
