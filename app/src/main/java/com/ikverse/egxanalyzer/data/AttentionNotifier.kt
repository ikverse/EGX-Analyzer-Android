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
 * The price feed going quiet without anything else looking wrong.
 *
 * It was already detected and reached the reader only on a screen they had to think to go and
 * open. A frozen price feed looks exactly like a calm market, and silence is the failure mode of
 * everything this phone does unattended.
 *
 * **Its own channel and not the overdue one**, although both belong to the same "you need to look
 * at this" register. That channel is named for trades past their deadline, and Android silences a
 * whole channel at a time - so folding a feed fault into it would mean a reader who muted one had
 * silently muted the other, which is the exact failure this file exists to prevent.
 */
class AttentionNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Needs attention",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "A price feed that has gone quiet."
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
     * Says the feed has stopped answering about some of the stocks the record names.
     *
     * The figure it leads with is [callsHeld] and not the count of stocks, for the reason the
     * Settings card leads with it: a tally of stale symbols is trivia, while "these 4 stocks are
     * holding 11 calls out of every rate you are reading" is the sentence that changes what the
     * reader believes about the page.
     *
     * Raised **once per spell** by the caller, which is what keeps it from becoming a daily line
     * about a symbol that retired in June.
     */
    fun feedQuiet(stocks: Int, callsHeld: Int) {
        if (stocks <= 0 || !permitted()) return
        val what = if (stocks == 1) {
            "1 stock has no usable prices"
        } else {
            "$stocks stocks have no usable prices"
        }
        val detail = "The feed has gone quiet about " +
            (if (stocks == 1) "it" else "them") + ", so " +
            (if (callsHeld == 1) "1 call is" else "$callsHeld calls are") +
            " sitting outside every rate the app shows. Settings explains what happened to each " +
            "and whether fetching again can help."
        manager.notify(
            FEED_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_egx_notification)
                .setContentTitle(what)
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(FEED_REQUEST))
                .build(),
        )
    }

    /** Takes the reader to Settings, which is where this is explained and answered. */
    private fun openApp(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SHOW_SETTINGS, true)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "attention"
        const val EXTRA_SHOW_SETTINGS = "com.ikverse.egxanalyzer.SHOW_SETTINGS"

        /** Clear of 1001-1005, which the notifiers before this one hold. */
        private const val FEED_ID = 1006
        private const val FEED_REQUEST = 6
    }
}
