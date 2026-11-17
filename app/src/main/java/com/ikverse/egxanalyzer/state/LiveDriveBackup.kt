package com.ikverse.egxanalyzer.state

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
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
import com.ikverse.egxanalyzer.BuildConfig
import com.ikverse.egxanalyzer.data.AndroidKeystoreCredentialStore
import com.ikverse.egxanalyzer.data.DRIVE_FOLDER_NAME
import com.ikverse.egxanalyzer.data.DRIVE_SCOPE
import com.ikverse.egxanalyzer.data.DriveBackupWorker
import com.ikverse.egxanalyzer.data.DriveClient
import com.ikverse.egxanalyzer.data.DriveSyncTransport
import com.ikverse.egxanalyzer.data.DeviceCode
import com.ikverse.egxanalyzer.data.GoogleDeviceAuth
import com.ikverse.egxanalyzer.model.DriveSignInCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.ikverse.egxanalyzer.data.SettingsRepository
import com.ikverse.egxanalyzer.data.SyncTransport
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
 * The Google Drive connection - backup, restore, and the sync transport - the third region to live
 * outside [LiveAppState]; see [LiveUpdates] for the pattern and `data/DriveBackup.kt` for why Drive
 * needs its own path beside the folder backup.
 *
 * What it needs back from the host - writing the zip, and adopting a backup that has been read -
 * arrives through [bind], as [LiveHome]'s does, because a constructor default cannot read `this`.
 *
 * **Two ways to sign in, one Drive.** With Google Play services the phones ask
 * `play-services-auth`, which holds the grant and hands out a short-lived token each time; this
 * class keeps only the account's address. Without it - the Huawei tablet - [GoogleDeviceAuth]
 * signs in with a code typed at google.com/device and keeps a refresh token in the Keystore.
 * Everything after the token is the same [DriveClient].
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

    /** Whether Play services can sign in here. False on the tablet, which uses a code. */
    private val playServices: Boolean by lazy {
        runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }

    private val deviceAuth: GoogleDeviceAuth by lazy {
        GoogleDeviceAuth(
            AndroidKeystoreCredentialStore(context),
            BuildConfig.GOOGLE_DEVICE_CLIENT_ID,
            BuildConfig.GOOGLE_DEVICE_CLIENT_SECRET,
        )
    }

    override val driveAvailable: Boolean by lazy { playServices || deviceAuth.configured }

    override var driveSignInCode by mutableStateOf<DriveSignInCode?>(null)
        private set

    private var signInJob: Job? = null

    override var driveAccount by mutableStateOf(settingsRepository.driveAccount())
        private set

    private val client = DriveClient { fresh -> token(fresh) }

    /** The sync files in `EGX Analyzer/sync`, for a device whose sync goes through Drive. */
    val syncTransport: SyncTransport by lazy { DriveSyncTransport(client, context.cacheDir) }

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
     * [fresh] after Drive refused the last one: the cached token is replaced rather than handed
     * back expired.
     */
    private suspend fun token(fresh: Boolean): String {
        val account = driveAccount ?: throw IOException("Google Drive is not connected")
        if (!playServices) return withContext(Dispatchers.IO) { deviceAuth.token(fresh) }
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

    /**
     * Starts connecting. With Play services this is Google's consent screen, handed back to the
     * page to launch; without it a code is put in [driveSignInCode], its page is opened, and null
     * comes back - [waitForApproval] finishes the connection once the code has been typed.
     */
    override suspend fun requestDriveAccess(): IntentSender? {
        if (!playServices) {
            val code = withContext(Dispatchers.IO) { deviceAuth.start() }
            driveSignInCode = DriveSignInCode(code.userCode, code.verificationUrl)
            signInJob?.cancel()
            signInJob = scope.launch { waitForApproval(code) }
            openDriveSignInPage()
            return null
        }
        val result = authorize(null)
        if (result.hasResolution()) {
            return result.pendingIntent?.intentSender
                ?: throw IOException("Google would not show its sign-in")
        }
        connect(result.accessToken ?: throw IOException("Google Drive gave no access"))
        return null
    }

    override suspend fun finishDriveAccess(data: Intent?) {
        val result = runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data) }
            .getOrElse { throw IOException("Google Drive was not connected") }
        connect(result.accessToken ?: throw IOException("Google Drive gave no access"))
    }

    /**
     * Asks Google every few seconds whether the code has been approved, until it has, it is
     * refused, or it expires. Its own job, so Cancel and a second Connect can stop it.
     */
    private suspend fun waitForApproval(code: DeviceCode) {
        val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000
        try {
            while (System.currentTimeMillis() < deadline) {
                delay(code.intervalSeconds * 1000)
                val granted = withContext(Dispatchers.IO) { deviceAuth.poll(code) } ?: continue
                driveSignInCode = null
                connect(granted)
                return
            }
            throw IOException("The code expired - press Connect again")
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            driveSignInCode = null
            status.message = StatusMessage(
                error.message?.takeIf(String::isNotBlank) ?: "Google Drive was not connected",
                succeeded = false,
            )
        }
    }

    override fun cancelDriveSignIn() {
        signInJob?.cancel()
        signInJob = null
        driveSignInCode = null
    }

    override fun openDriveSignInPage() {
        val address = driveSignInCode?.verificationUrl ?: return
        val uri = Uri.parse(address)
        runCatching {
            val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
            // Launched from the application's context, so it needs a task of its own.
            tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            tab.launchUrl(context, uri)
        }.recoverCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            status.message = StatusMessage("Open ${address} in a browser and type the code", succeeded = false)
        }
    }

    /**
     * Learns which account was granted, and makes the folder, before calling Drive connected.
     *
     * The account is read from Drive itself, which works the same for both sign-ins and proves the
     * token works.
     */
    private suspend fun connect(granted: String) {
        val first = DriveClient { granted }
        val email = withContext(Dispatchers.IO) {
            val email = first.accountEmail() ?: throw IOException("Google Drive did not say which account it is")
            first.folder()
            email
        }
        settingsRepository.saveDriveAccount(email)
        driveAccount = email
        status.message = StatusMessage("Google Drive connected as $email", succeeded = true)
    }

    override fun disconnectDrive() {
        val account = driveAccount ?: return
        settingsRepository.saveDriveAccount(null)
        driveAccount = null
        scope.launch {
            // Revoked so the grant does not outlive the owner's decision; the backups and sync
            // files themselves are the owner's and stay in their Drive.
            runCatching {
                if (!playServices) {
                    withContext(Dispatchers.IO) { deviceAuth.signOut() }
                } else {
                    Identity.getAuthorizationClient(context).revokeAccess(
                        RevokeAccessRequest.builder()
                            .setAccount(Account(account, "com.google"))
                            .setScopes(scopes)
                            .build(),
                    ).await()
                }
            }
        }
        status.message = StatusMessage("Google Drive disconnected. Files already there stay", succeeded = true)
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
