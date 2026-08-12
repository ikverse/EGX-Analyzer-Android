package com.ikverse.egxanalyzer.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ikverse.egxanalyzer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
        val offered = updateOffered(
            currentVersionName,
            document,
            Build.SUPPORTED_ABIS?.toList().orEmpty(),
        )
        // An install that has caught up has no use for the APK it was updated from, and this is the
        // first moment anything knows that: the file was written by the version before this one.
        if (offered == null) clearDownloads()
        offered
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
        // One at a time. A half-finished download is worth nothing, and a phone should not carry
        // every version it was ever offered.
        clearDownloads()
        val target = File(directory, "egx-analyzer-${update.versionName}.apk")
        val partial = File(directory, "${target.name}.part")

        val connection = open(update.downloadUrl)
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException(failureFor(connection.responseCode, "The download"))
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
            var written = 0L
            var reported = -1
            connection.inputStream.use { source ->
                partial.outputStream().use { sink ->
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
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }

        if (!partial.renameTo(target)) {
            partial.delete()
            throw IOException("The download could not be saved.")
        }
        target
    }

    /**
     * Whether the downloaded APK was signed with the key this build was signed with.
     *
     * Android enforces this at install time anyway; doing it here changes what the user reads. A
     * mismatch is otherwise a dialog saying "App not installed" with no reason given, which looks
     * like a broken download and invites someone to try again forever.
     */
    fun signedLikeThisApp(file: File): Boolean = runCatching {
        val installed = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo
            ?.apkContentsSigners
            ?.digests()
            .orEmpty()
        val downloaded = context.packageManager
            .getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?.signingInfo
            ?.apkContentsSigners
            ?.digests()
            .orEmpty()
        installed.isNotEmpty() && installed == downloaded
    }.getOrDefault(false)

    /** True once the user has allowed this app to install apps. Android asks; nothing else can. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system page where that permission is granted, for the app that needs it. */
    fun permissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    /** Hands the APK to Android's installer, which does the asking and the installing. */
    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** The release page, for reading what changed somewhere with more room than a settings card. */
    fun releasesPageIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun clearDownloads() {
        File(context.filesDir, DOWNLOAD_DIRECTORY).listFiles()?.forEach { it.delete() }
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

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", GITHUB_MEDIA_TYPE)
            // GitHub refuses a request that does not name what is asking.
            setRequestProperty("User-Agent", "EGXAnalyzer/$currentVersionName")
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

        /** Matches the provider declared in the manifest. */
        private const val AUTHORITY_SUFFIX = ".updates"
        private const val DOWNLOAD_DIRECTORY = "updates"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val GITHUB_MEDIA_TYPE = "application/vnd.github+json"
        private const val TIMEOUT_MILLISECONDS = 20_000
        private const val BUFFER_BYTES = 64 * 1024
    }
}
