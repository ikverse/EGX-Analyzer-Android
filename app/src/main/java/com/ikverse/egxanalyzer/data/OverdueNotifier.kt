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
 * Tells the user a trade has run past its deadline while they were not looking.
 *
 * The Portfolio tab already says so, which is exactly the problem: it only says so to someone who
 * opened it. A deadline passes on a day like any other, and the trade that most needs a decision is
 * the one on a call old enough to have been forgotten.
 *
 * Its own channel rather than the analysis one, so it can be silenced without silencing the thing
 * that reports a running analysis - they are unrelated, and Android only lets a user turn off a
 * whole channel at a time.
 */
class OverdueNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Overdue trades",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Trades past their deadline with no sale recorded."
                },
            )
        }
    }

    /** True once the user has allowed notifications, which Android 13 and later ask for. */
    fun permitted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            "android.permission.POST_NOTIFICATIONS",
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Says how many trades are waiting, and how late the latest of them is.
     *
     * One notification for all of them rather than one each: a fortnight away could otherwise mean
     * eight separate buzzes about the same thing, which is how a user learns to turn a channel off.
     */
    fun overdue(count: Int, longestDays: Long) {
        if (count <= 0 || !permitted()) return
        val what = if (count == 1) "1 trade is overdue" else "$count trades are overdue"
        val detail = "Past the deadline with no sale recorded - the longest by " +
            "$longestDays ${if (longestDays == 1L) "day" else "days"}. " +
            "Record the sale, or press Keep Open to hold it deliberately."
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_egx_notification)
                .setContentTitle(what)
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPortfolio())
                .build(),
        )
    }

    private fun openPortfolio(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SHOW_PORTFOLIO, true)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "overdue"
        const val NOTIFICATION_ID = 1002
        const val EXTRA_SHOW_PORTFOLIO = "com.ikverse.egxanalyzer.SHOW_PORTFOLIO"

        /** Distinct from the analysis notifier's, or one would replace the other's intent. */
        private const val REQUEST_CODE = 2
    }
}
