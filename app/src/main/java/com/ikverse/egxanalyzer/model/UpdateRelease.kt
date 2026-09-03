package com.ikverse.egxanalyzer.model

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

private const val BYTES_PER_MB = 1024 * 1024
