package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AppVersion
import org.json.JSONObject
import java.util.Locale

/** A build that exists somewhere else, and everything needed to decide whether to fetch it. */
data class AvailableUpdate(
    val version: AppVersion,
    /** As the release names itself, so the screen and the release page say the same thing. */
    val versionName: String,
    /** What the release says changed. Empty when the release said nothing. */
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
) {
    /**
     * The size in the unit someone on mobile data decides with.
     *
     * Forced to US digits rather than the phone's: this figure sits beside a version number, and
     * one of the two rendering as Arabic-Indic digits while the other does not reads as a fault in
     * the app.
     */
    val sizeLabel: String get() = byteLabel(sizeBytes)
}

/** A count of bytes as someone on mobile data would say it. US digits, for the reason above. */
fun byteLabel(bytes: Long): String = when {
    bytes <= 0 -> "size unknown"
    bytes >= BYTES_PER_MB -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / BYTES_PER_MB)
    else -> "${bytes / 1024} KB"
}

/**
 * Where an interrupted download carries on from, given what the server answered.
 *
 * Only a 206 means the server honoured the range and is sending the rest. A 200 is the whole file
 * again - some servers ignore the header, and a CDN can answer a resumed request with a fresh copy
 * - and appending that to what is already on disk builds a file that is neither, downloads another
 * seventy megabytes to do it, and fails its signature check at the end with nothing to explain it.
 */
fun resumeOffset(responseCode: Int, bytesOnDisk: Long): Long =
    if (responseCode == PARTIAL_CONTENT && bytesOnDisk > 0) bytesOnDisk else 0L

/**
 * How big the whole file is, given a response that may only be describing what is left of it.
 *
 * A resumed request's Content-Length counts the remainder, not the file, so the bytes already on
 * disk have to be added back or the progress bar reports 40% as 100%.
 */
fun totalBytes(contentLength: Long, resumeFrom: Long, offeredSize: Long): Long =
    if (contentLength > 0) contentLength + resumeFrom else offeredSize

private const val BYTES_PER_MB = 1024 * 1024
private const val PARTIAL_CONTENT = 206

/**
 * Which of a release's APKs this device should download.
 *
 * A release is split by architecture, because most of the app's size is TDLib's native libraries
 * and a phone has no use for the ones built for other chips. They are one version, one build and
 * one signature - what differs is only which libraries are inside.
 *
 * [abis] is the device's own list, best first, so a phone that can run two architectures gets the
 * one it runs natively.
 */
fun preferredApkName(names: List<String>, abis: List<String>): String? {
    val apks = names.filter { it.endsWith(APK_SUFFIX, ignoreCase = true) }
    if (apks.isEmpty()) return null
    abis.forEach { abi ->
        apks.firstOrNull { it.endsWith("-$abi$APK_SUFFIX", ignoreCase = true) }?.let { return it }
    }
    apks.firstOrNull { it.endsWith(UNIVERSAL_SUFFIX, ignoreCase = true) }?.let { return it }
    // What is left is a release cut before the split, whose one APK is named for nothing but its
    // version. An APK named for an architecture this device did not ask for is deliberately not a
    // fallback: Android refuses to install one, so offering it would promise an update that cannot
    // happen and leave someone downloading 50MB to be told no.
    return apks.firstOrNull { name ->
        KNOWN_ABIS.none { name.endsWith("-$it$APK_SUFFIX", ignoreCase = true) }
    }
}

/**
 * Reads one GitHub release, or null for anything that is not an installable one.
 *
 * A release with no APK attached is not an update - a tag pushed while the build was still running
 * produces exactly that, and offering it would send someone to a Download button with nothing
 * behind it. Drafts and pre-releases are refused here as well as by the endpoint, because the
 * endpoint is a URL that could be pointed somewhere else and this is the rule, not a side effect
 * of where it was asked.
 */
fun readRelease(document: String, abis: List<String> = emptyList()): AvailableUpdate? = runCatching {
    val json = JSONObject(document)
    if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
    val tag = json.optString("tag_name")
    val version = AppVersion.parse(tag) ?: return null
    val assets = json.optJSONArray("assets") ?: return null
    val downloadable = (0 until assets.length())
        .mapNotNull(assets::optJSONObject)
        .filter { it.optString("browser_download_url").isNotBlank() }
    val chosen = preferredApkName(downloadable.map { it.optString("name") }, abis)
        ?.let { name -> downloadable.first { it.optString("name") == name } }
        ?: return null
    AvailableUpdate(
        version = version,
        versionName = tag.trim().trimStart('v', 'V').ifBlank { version.toString() },
        notes = json.optString("body").trim(),
        downloadUrl = chosen.optString("browser_download_url"),
        sizeBytes = chosen.optLong("size"),
    )
}.getOrNull()

/**
 * The update worth telling someone about, or null when there is none.
 *
 * Strictly newer, never merely different: reinstalling the version already on the phone is not an
 * update, and a release republished to fix its notes must not send every device back to the store
 * page. A version this build cannot make sense of offers nothing rather than everything - the
 * comparison is the whole point, and there is nothing to compare against.
 */
fun updateOffered(
    currentVersionName: String,
    document: String,
    abis: List<String> = emptyList(),
): AvailableUpdate? {
    val current = AppVersion.parse(currentVersionName) ?: return null
    return readRelease(document, abis)?.takeIf { it.version > current }
}

/**
 * The name a fetched APK is kept under while it waits to be installed.
 *
 * The version is in the name because the name is all that survives. Granting "install unknown apps"
 * restarts the app on some phones - Samsung force-stops it - and everything the app knew about the
 * download dies with the process while the file itself sits there. Read back out of the name, a
 * download outlives the permission grant that was needed to install it.
 */
fun downloadFileName(versionName: String): String = "$DOWNLOAD_PREFIX$versionName$APK_SUFFIX"

/** The version a kept APK names, or null when the file is not one this app wrote. */
fun downloadedVersionName(fileName: String): String? = fileName
    .takeIf { it.startsWith(DOWNLOAD_PREFIX) && it.endsWith(APK_SUFFIX) }
    ?.removePrefix(DOWNLOAD_PREFIX)
    ?.removeSuffix(APK_SUFFIX)
    ?.takeIf { AppVersion.parse(it) != null }

/**
 * The version a kept APK would install, or null when there is no reason to keep it.
 *
 * Anything this build has already caught up with is rubbish: the file was written by the version
 * before this one, and offering to install it would walk someone backwards. A half-finished
 * download - still named `.part` - is not one of ours either.
 */
fun downloadedUpdateVersion(fileName: String, currentVersionName: String): AppVersion? {
    val downloaded = downloadedVersionName(fileName)?.let(AppVersion::parse) ?: return null
    val current = AppVersion.parse(currentVersionName) ?: return null
    return downloaded.takeIf { it > current }
}

/**
 * Whether a download that appears to have ended is actually incomplete.
 *
 * A connection closed early reads as end-of-file with no exception, so "the stream ended" and "the
 * file arrived" are the same event to a reader. The release states the byte count, which is the
 * only thing that tells them apart. A release that stated no size at all cannot be checked, and an
 * unverifiable download is not a failed one.
 */
fun downloadIsShort(bytesOnDisk: Long, expectedBytes: Long): Boolean =
    expectedBytes > 0 && bytesOnDisk < expectedBytes

/**
 * What a downloaded APK turned out to be.
 *
 * Damaged and wrong-key were one answer before, and it was the alarming one: a truncated file
 * cannot be read, an unreadable file has no certificates, and no certificates compares unequal to
 * this build's. So an ordinary interrupted download reported that the release was signed by someone
 * else - which was true of nothing, and sent the search a long way from the network fault that
 * caused it.
 */
enum class DownloadedApk {
    /** Signed by the key this build was signed with. The only one worth installing. */
    MATCHES,

    /** Readable, and signed by something else. Nothing to do but uninstall by hand. */
    WRONG_KEY,

    /** Not a readable APK at all. Fetch it again. */
    DAMAGED,
}

private const val APK_SUFFIX = ".apk"
private const val UNIVERSAL_SUFFIX = "-universal$APK_SUFFIX"
private const val DOWNLOAD_PREFIX = "egx-analyzer-"

/**
 * The architectures a file name might be named for.
 *
 * Only used to recognise a name as architecture-specific, never to decide what a device can run -
 * that is [preferredApkName]'s [abis], which the device itself supplies.
 */
private val KNOWN_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
