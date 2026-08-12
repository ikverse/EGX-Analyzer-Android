package com.ikverse.egxanalyzer.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ikverse.egxanalyzer.BuildConfig
import com.ikverse.egxanalyzer.data.UpdateInstallReceiver.Companion.ACTION_INSTALL_STATUS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Where the update check has got to, so the screen can say the same thing the app is doing. */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** Nothing newer exists. Carries the version asked about, so the answer names what it answered. */
    data class UpToDate(val versionName: String) : UpdateState

    data class Available(val update: AvailableUpdate) : UpdateState

    data class Downloading(val update: AvailableUpdate, val progress: Float) : UpdateState

    /** Downloaded, checked, and waiting for the one tap that hands it to Android's installer. */
    data class Ready(val update: AvailableUpdate, val file: File) : UpdateState

    data class Failed(val reason: String) : UpdateState
}

/**
 * Fetches the app's own updates from its GitHub releases.
 *
 * The app is sideloaded, so nothing else is going to offer it an update: without this, a new
 * version reaches a phone only by plugging it into the machine that built it. Releases are public,
 * so no token travels in the app and the check is an ordinary HTTPS GET.
 *
 * Nothing here installs anything. It downloads a file and hands it to Android's package installer,
 * which shows its own dialog and asks the user - an app that could replace itself without being
 * asked is exactly what the "install unknown apps" permission exists to prevent.
 */
class UpdateRepository(
    private val context: Context,
    /** What this build calls itself, and so what every answer here is measured against. */
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    private val releaseUrl: String = LATEST_RELEASE_URL,
) {
    /**
     * The newer release, or null when this build is the newest there is.
     *
     * Throws what went wrong rather than swallowing it, because the button that calls this asked a
     * question and "could not reach GitHub" is the answer to it.
     */
    suspend fun check(): AvailableUpdate? = withContext(Dispatchers.IO) {
        val document = fetch(releaseUrl)
        // The device's own list, best first, so a release split by architecture hands this phone
        // the build for its chip rather than the libraries for four of them.
        // Nothing is deleted here. [downloaded] decides what is worth keeping, on every launch and
        // against the version actually running - where this only ever knew what GitHub had, and a
        // release page briefly missing its newest entry would have thrown away a good download.
        updateOffered(
            currentVersionName,
            document,
            Build.SUPPORTED_ABIS?.toList().orEmpty(),
        )
    }

    /**
     * An update already fetched and still waiting to be installed, or null when there is none.
     *
     * This is what makes the permission trip survivable. Granting "install unknown apps" restarts
     * the app on some phones, and everything it knew about the download used to die with the
     * process - leaving 70MB on disk that nothing would ever look at again, and a card back at
     * "Check for updates" as if the download had never happened. The file is the record now.
     *
     * Anything that cannot be read, has been caught up with, or was not signed by this build's key
     * is deleted rather than offered: all three would end at an installer refusing it.
     */
    suspend fun downloaded(): Pair<AvailableUpdate, File>? = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, DOWNLOAD_DIRECTORY)
            .listFiles()
            ?.firstOrNull { it.isFile && it.name.endsWith(APK_SUFFIX) }
            ?: return@withContext null
        val version = downloadedUpdateVersion(file.name, currentVersionName)
        // The signature last, because it digests the whole file - no point paying for 70MB of it to
        // learn the name was wrong.
        if (version == null || inspect(file) != DownloadedApk.MATCHES) {
            file.delete()
            return@withContext null
        }
        AvailableUpdate(
            version = version,
            versionName = version.toString(),
            // Empty on purpose: the notes were read when it was offered, and there is nothing left
            // to download. Only the file matters from here.
            notes = "",
            downloadUrl = "",
            sizeBytes = file.length(),
        ) to file
    }

    /**
     * Fetches the APK, reporting how far it has got.
     *
     * Into the app's own storage rather than Downloads: this is not a file anyone asked to keep,
     * and one the user could swap for another between the download and the install is not the file
     * that was checked. [FileProvider] is what lets the installer read it from there.
     */
    suspend fun download(
        update: AvailableUpdate,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, DOWNLOAD_DIRECTORY).apply { mkdirs() }
        val target = File(directory, downloadFileName(update.versionName))
        val partial = File(directory, "${target.name}$PARTIAL_SUFFIX")
        // Everything except this download's own part file, which is the whole point: a connection
        // that dies at sixty megabytes should cost the last ten, not all seventy.
        clearDownloads(keep = partial.name)

        var attempt = 0
        while (true) {
            attempt++
            try {
                fetchInto(partial, update, onProgress)
                // A stream that ends is not a download that finished. A connection closed early
                // reads as end-of-file with no exception at all, so this used to rename a half APK
                // to a finished one - and the signature check then failed on it and reported a
                // wrong signing key, which was true of nothing and sent the search somewhere else
                // entirely. The release says how many bytes there should be; this is that check.
                if (downloadIsShort(partial.length(), update.sizeBytes)) {
                    throw IOException(
                        "The download ended early at ${byteLabel(partial.length())} of " +
                            "${byteLabel(update.sizeBytes)}.",
                    )
                }
                break
            } catch (error: HttpFailure) {
                // The server said no, and it will say no again. Rate limits and missing files are
                // not made better by asking three times in ten seconds.
                throw error
            } catch (error: IOException) {
                if (attempt >= DOWNLOAD_ATTEMPTS) {
                    throw IOException(
                        "The download stopped at ${byteLabel(partial.length())} of " +
                            "${byteLabel(update.sizeBytes)}. Press Download to carry on from there.",
                        error,
                    )
                }
                // A moment, rather than straight back into a network that has just dropped.
                delay(RETRY_DELAY_MILLISECONDS * attempt)
            }
        }

        if (!partial.renameTo(target)) {
            partial.delete()
            throw IOException("The download could not be saved.")
        }
        target
    }

    /**
     * One attempt, carrying on from whatever is already on disk.
     *
     * Throws on a dropped connection and leaves the part file where it is - that file is the
     * progress, and deleting it is what used to turn one bad moment on a train into another
     * seventy megabytes.
     */
    private fun fetchInto(partial: File, update: AvailableUpdate, onProgress: (Float) -> Unit) {
        val onDisk = partial.length()
        val connection = open(
            url = update.downloadUrl,
            accept = ANY_MEDIA_TYPE,
            rangeFrom = onDisk.takeIf { it > 0 },
        )
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw HttpFailure(failureFor(code, "The download"))
            val resumeFrom = resumeOffset(code, onDisk)
            val total = totalBytes(connection.contentLengthLong, resumeFrom, update.sizeBytes)
            var written = resumeFrom
            var reported = -1
            connection.inputStream.use { source ->
                // Appending only when the server actually agreed to resume. Otherwise this
                // truncates, because what is arriving is the file from the beginning again.
                FileOutputStream(partial, resumeFrom > 0).use { sink ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        written += read
                        if (total <= 0) continue
                        // Only when the figure someone can see has actually moved: this lands on a
                        // Compose state, and a recomposition per 8KB chunk is a recomposition for
                        // a number that has not changed.
                        val percent = (written * 100 / total).toInt()
                        if (percent != reported) {
                            reported = percent
                            onProgress(percent / 100f)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** A refusal from the server, as opposed to a connection that fell over. Never retried. */
    private class HttpFailure(message: String) : IOException(message)

    /**
     * Whether the downloaded APK was signed with the key this build was signed with.
     *
     * Android enforces this at install time anyway; doing it here changes what the user reads. A
     * mismatch is otherwise a dialog saying "App not installed" with no reason given, which looks
     * like a broken download and invites someone to try again forever.
     */
    fun inspect(file: File): DownloadedApk {
        val downloaded = runCatching {
            context.packageManager
                .getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
                ?.signingInfo
                ?.apkContentsSigners
                ?.digests()
        }.getOrNull().orEmpty()
        // Nothing to read means nothing arrived intact, which is a different problem with a
        // different answer: fetch it again, rather than go and uninstall the app by hand.
        if (downloaded.isEmpty()) return DownloadedApk.DAMAGED
        val installed = runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
                ?.digests()
        }.getOrNull().orEmpty()
        return if (installed.isNotEmpty() && installed == downloaded) {
            DownloadedApk.MATCHES
        } else {
            DownloadedApk.WRONG_KEY
        }
    }

    /** True once the user has allowed this app to install apps. Android asks; nothing else can. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system page where that permission is granted, for the app that needs it. */
    fun permissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    /**
     * Installs the APK by writing it into a session the system owns.
     *
     * The file never crosses a process boundary as a URI, which is the whole point. Handing the
     * installer a `content://` URI failed three releases running - "Permission Denial: opening
     * provider androidx.core.content.FileProvider ... that is not exported" - because the grant an
     * intent carries belongs to the activity that receives it, and Samsung's installer reads the
     * file later, from its staging layer, by which time that grant is gone. It closed without a
     * word, so the phone looked like it had ignored the button. Nothing here depends on provider
     * export rules, on a grant outliving a handoff, on package visibility, or on which of several
     * apps the resolver picks.
     *
     * Android still asks the user before it installs anything - that confirmation arrives as
     * [PackageInstaller.STATUS_PENDING_USER_ACTION] and is shown by [UpdateInstallReceiver].
     */
    suspend fun install(file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(APK_ENTRY, 0, file.length()).use { sink ->
                file.inputStream().use { source -> source.copyTo(sink) }
                // Without this the bytes can still be in flight when the session is committed, and
                // the install fails on a file the app has already finished writing.
                session.fsync(sink)
            }
            session.commit(statusSender(sessionId))
        }
    }

    /**
     * Where the system reports what became of the install.
     *
     * Mutable because the system fills the result in; a broadcast to this package only, so nothing
     * else can answer for it.
     */
    private fun statusSender(sessionId: Int): IntentSender = PendingIntent.getBroadcast(
        context,
        sessionId,
        Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    ).intentSender

    /** Removes a downloaded update once it has been installed and is no longer worth keeping. */
    fun forgetDownloads() = clearDownloads()

    /** The release page, for reading what changed somewhere with more room than a settings card. */
    fun releasesPageIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun clearDownloads(keep: String? = null) {
        File(context.filesDir, DOWNLOAD_DIRECTORY)
            .listFiles()
            ?.forEach { if (it.name != keep) it.delete() }
    }

    private fun fetch(url: String): String {
        val connection = open(url)
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException(failureFor(connection.responseCode, "The update check"))
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(
        url: String,
        accept: String = GITHUB_MEDIA_TYPE,
        rangeFrom: Long? = null,
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", accept)
            // GitHub refuses a request that does not name what is asking.
            setRequestProperty("User-Agent", "EGXAnalyzer/$currentVersionName")
            // Carry on from what is already on disk. A server that ignores this answers 200 rather
            // than 206, which is why the answer is read rather than assumed.
            if (rangeFrom != null) setRequestProperty("Range", "bytes=$rangeFrom-")
            connectTimeout = TIMEOUT_MILLISECONDS
            readTimeout = TIMEOUT_MILLISECONDS
        }

    /** In the terms the person reading it can act on, which "HTTP 403" is not. */
    private fun failureFor(code: Int, subject: String): String = when (code) {
        403, 429 -> "GitHub is rate-limiting this connection. Try again in an hour."
        404 -> "$subject found no release to install."
        in 500..599 -> "GitHub is having trouble. Try again later."
        else -> "$subject failed ($code)."
    }

    private fun Array<Signature>.digests(): Set<String> = mapTo(mutableSetOf()) { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val REPOSITORY = "ikverse/EGX-Analyzer-Android"

        /** Excludes drafts and pre-releases by definition, which is why it is this URL and not a list. */
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$REPOSITORY/releases/latest"
        const val RELEASES_PAGE_URL = "https://github.com/$REPOSITORY/releases"

        private const val DOWNLOAD_DIRECTORY = "updates"
        private const val APK_SUFFIX = ".apk"

        /** The name the APK is written under inside the install session; nothing reads it back. */
        private const val APK_ENTRY = "update.apk"
        private const val GITHUB_MEDIA_TYPE = "application/vnd.github+json"

        /** An asset is a file, not the API. Asking it for JSON was harmless and still a lie. */
        private const val ANY_MEDIA_TYPE = "*/*"
        private const val PARTIAL_SUFFIX = ".part"
        private const val TIMEOUT_MILLISECONDS = 20_000
        private const val BUFFER_BYTES = 64 * 1024

        /**
         * How many times a dropped connection is picked back up before the user is told.
         *
         * Three, because the failure this exists for is a moment - a handover between Wi-Fi and
         * mobile, a lift, a screen locking - and not a network that is down. Each attempt resumes,
         * so the cost of one more try is seconds rather than another seventy megabytes.
         */
        private const val DOWNLOAD_ATTEMPTS = 3
        private const val RETRY_DELAY_MILLISECONDS = 2_000L
    }
}
