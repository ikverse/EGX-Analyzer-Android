package com.ikverse.egxanalyzer.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ikverse.egxanalyzer.MainActivity
import com.ikverse.egxanalyzer.R

/**
 * The one notification at the end of a run: the report is ready, or the run did not finish.
 *
 * **Its own channel, and an audible one.** The run's progress lives on `analysis`, which is low and
 * silent because it is the handle on live work and updates batch by batch. The end of a run is the
 * moment the reader left the app to wait for, so it is the one worth a sound - and it used to post
 * on that silent channel under the foreground service's own id, right after the service was told
 * to stop, where the service's teardown could take it down with it.
 *
 * Fired even with the app on screen, where a run already lands on Results: the reader may be on
 * another tab, and one notification more than needed costs less than one missed.
 *
 * Gated by `AppPreferences.reportReadyEnabled`, checked by the caller.
 */
class ReportReadyNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "A report is ready",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "When a run has finished, or could not finish." },
            )
        }
    }

    /** True once the user has allowed notifications, which Android 13 and later ask for. */
    fun permitted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            "android.permission.POST_NOTIFICATIONS",
        ) == PackageManager.PERMISSION_GRANTED

    /** The report is saved: how many calls it found, and a tap that opens it. */
    fun ready(calls: Int, resultId: Long) {
        if (!permitted()) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(AnalysisNotifier.EXTRA_RESULT_ID, resultId)
        manager.notify(
            NOTIFICATION_ID,
            base()
                .setContentTitle("Your report is ready")
                .setContentText("$calls ${if (calls == 1) "call" else "calls"} found")
                .setContentIntent(pending(intent, READY_REQUEST))
                .build(),
        )
    }

    /** The run stopped on an error: why, and a tap back to the Analyze page to try again. */
    fun failed(reason: String) {
        if (!permitted()) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SHOW_ANALYZE, true)
        manager.notify(
            NOTIFICATION_ID,
            base()
                .setContentTitle("The run did not finish")
                .setContentText(reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                .setContentIntent(pending(intent, FAILED_REQUEST))
                .build(),
        )
    }

    private fun base() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_egx_notification)
        .setColor(NotificationTone.TRADES.argb)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)

    private fun pending(intent: Intent, request: Int): PendingIntent = PendingIntent.getActivity(
        context,
        request,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ID = "report-ready"
        const val NOTIFICATION_ID = 1008
        const val EXTRA_SHOW_ANALYZE = "com.ikverse.egxanalyzer.SHOW_ANALYZE"
        private const val READY_REQUEST = 8
        private const val FAILED_REQUEST = 9
    }
}
