package com.ikverse.egxanalyzer.data

import android.app.Notification
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
 * Tells the user how a run is going while they are elsewhere.
 *
 * An analysis takes long enough to leave the app during, so the only honest place to report it is
 * a notification. The running one cannot be dismissed - it is the handle on live work - while the
 * finished one can, and carries a way straight to what it produced.
 */
class AnalysisNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Analysis",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Progress and results of EGX analyses." },
            )
        }
    }

    /** True once the user has allowed notifications, which Android 13 and later ask for. */
    fun permitted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            "android.permission.POST_NOTIFICATIONS",
        ) == PackageManager.PERMISSION_GRANTED

    fun running(sources: Int, model: String): Notification = base()
        .setContentTitle("Analysis running")
        .setContentText("$sources ${if (sources == 1) "source" else "sources"} · $model")
        .setProgress(0, 0, true)
        .setOngoing(true)
        .setContentIntent(openApp(null))
        .build()

    fun finished(recommendations: Int, resultId: Long) {
        show(
            base()
                .setContentTitle("Analysis finished")
                .setContentText(
                    "$recommendations ${if (recommendations == 1) "recommendation" else "recommendations"} saved",
                )
                .setAutoCancel(true)
                .setContentIntent(openApp(resultId))
                .addAction(0, "Open results", openApp(resultId))
                .build(),
        )
    }

    fun failed(reason: String) {
        show(
            base()
                .setContentTitle("Analysis failed")
                .setContentText(reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                .setAutoCancel(true)
                .setContentIntent(openApp(null))
                .build(),
        )
    }

    fun cancelled() = manager.cancel(NOTIFICATION_ID)

    private fun base() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_egx_notification)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOnlyAlertOnce(true)

    private fun show(notification: Notification) {
        if (!permitted()) return
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Reopens the app, optionally on a particular saved analysis.
     *
     * `SINGLE_TOP` with the activity's `singleTask` launch mode means tapping this returns to the
     * running app rather than stacking a second copy of it.
     */
    private fun openApp(resultId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (resultId != null) intent.putExtra(EXTRA_RESULT_ID, resultId)
        return PendingIntent.getActivity(
            context,
            resultId?.toInt() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "analysis"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_RESULT_ID = "com.ikverse.egxanalyzer.RESULT_ID"
    }
}
