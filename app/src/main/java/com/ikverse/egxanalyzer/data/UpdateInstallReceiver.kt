package com.ikverse.egxanalyzer.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.ikverse.egxanalyzer.EgxApplication

/**
 * What became of an install the app asked for.
 *
 * Nothing ever told the app whether an install happened. The old path handed an APK to another
 * process and heard nothing back, which is why an installer that refused it silently looked exactly
 * like a button that did nothing. A session reports, and this is where it reports to.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val state = (context.applicationContext as? EgxApplication)?.appState
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android asking the user, which is the one thing an app must never do for them.
                // NEW_TASK because a broadcast has no activity of its own to launch from.
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }.onFailure {
                    state?.reportUpdateProblem(
                        it.message?.takeIf(String::isNotBlank)
                            ?: "Android would not show the install confirmation.",
                    )
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                // Usually never arrives: installing this app replaces this process. Kept for when
                // it does, so the APK is not left behind taking seventy megabytes for nothing.
                UpdateRepository(context.applicationContext).forgetDownloads()
            }

            else -> state?.reportUpdateProblem(
                installFailureMessage(
                    status,
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                ),
            )
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.ikverse.egxanalyzer.INSTALL_STATUS"
    }
}

/**
 * Why an install did not happen, in words that say what to do about it.
 *
 * The system's own message is a diagnostic - "INSTALL_FAILED_UPDATE_INCOMPATIBLE" tells the user
 * nothing - so it is carried only where it adds something this cannot say better.
 */
fun installFailureMessage(status: Int, systemMessage: String?): String = when (status) {
    PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled."
    PackageInstaller.STATUS_FAILURE_BLOCKED ->
        "Android blocked the install. Check that this app is allowed to install apps."
    PackageInstaller.STATUS_FAILURE_CONFLICT ->
        "That build is signed with a different key, so it cannot replace this install."
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
        "That build is not compatible with this device."
    PackageInstaller.STATUS_FAILURE_INVALID ->
        "The downloaded file is not a valid APK. Download it again."
    PackageInstaller.STATUS_FAILURE_STORAGE ->
        "There is not enough free space to install the update."
    else -> systemMessage?.takeIf(String::isNotBlank)?.let { "The install failed: $it" }
        ?: "The install failed."
}
