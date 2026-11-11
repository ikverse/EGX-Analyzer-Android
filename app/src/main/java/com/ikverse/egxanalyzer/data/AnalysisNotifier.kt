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
 * a notification. The running one cannot be dismissed - it is the handle on live work. How the run
 * ended is [ReportReadyNotifier]'s, on a channel of its own that is allowed to make a sound.
 */
class AnalysisNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "A run in progress",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Shown while a run is going." },
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
        .setContentTitle("Reading the posts")
        .setContentText("$sources ${if (sources == 1) "post" else "posts"} · $model")
        .setProgress(0, 0, true)
        .setOngoing(true)
        .setContentIntent(openApp(null))
        .build()

    fun cancelled() = manager.cancel(NOTIFICATION_ID)

    private fun base() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_egx_notification)
        .setColor(NotificationTone.TRADES.argb)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOnlyAlertOnce(true)

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
