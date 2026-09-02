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
import com.ikverse.egxanalyzer.model.AnalysisSchedule

/**
 * The two ways this app can stop working without anything looking wrong.
 *
 * Both were already detected and both reached the reader only on a screen they had to think to go
 * and open. A frozen price feed looks exactly like a calm market; a schedule the system dropped
 * looks exactly like a schedule with nothing to do. Silence is the failure mode of everything this
 * phone does unattended, and these are the two silences the app can hear and could not speak.
 *
 * **Its own channel and not the overdue one**, although both belong to the same "you need to look
 * at this" register. That channel is named for trades past their deadline, and Android silences a
 * whole channel at a time - so folding a feed fault into it would mean a reader who muted one had
 * silently muted the other, which is the exact failure this file exists to prevent.
 *
 * **Nothing here offers to fix anything.** In particular a missed analysis carries no "run now":
 * that is a paid request, and a one-tap way to spend from the lock screen is the same act as
 * spending. Every one of these leads into the app, where the decision is made in full.
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
                    description =
                        "A price feed that has gone quiet, or a scheduled run that did not happen."
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
                .setContentIntent(openApp(FEED_REQUEST, schedules = false))
                .build(),
        )
    }

    /**
     * Says a scheduled analysis was due and did not happen, and why.
     *
     * The reported symptom this exists for was **silence**: schedules that never fired, with no
     * outcome line anywhere until the app was opened and the row read. The status line has always
     * named a missing exact-alarm grant or a battery exemption ahead of anything else - to anybody
     * who opened Settings.
     *
     * Deliberately not raised for a skip. "Scheduled runs are not allowed to spend cloud credits on
     * this phone" is the *normal* state of the money switch, and a daily notification restating it
     * would be the app nagging to be allowed to spend.
     */
    fun scheduleMissed(schedule: AnalysisSchedule) {
        if (!permitted()) return
        val detail = schedule.lastMessage.orEmpty().ifBlank { "It did not run." } +
            " Settings shows what is stopping it, and the next fire is the retry."
        manager.notify(
            SCHEDULE_ID_BASE + schedule.id.hashCode().mod(ID_RANGE),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_egx_notification)
                .setContentTitle("A scheduled analysis did not run")
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openApp(SCHEDULE_REQUEST + schedule.id.toInt(), schedules = true))
                .build(),
        )
    }

    /**
     * Takes the reader to Settings, which is where both of these are explained and answered.
     *
     * [schedules] opens the schedules section with it, through the same entrance the Analyze card's
     * own button uses - a notification that landed on Settings and left the reader to find the row
     * would have wasted most of what it was for.
     */
    private fun openApp(requestCode: Int, schedules: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SHOW_SETTINGS, true)
            .putExtra(EXTRA_SHOW_SCHEDULES, schedules)
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
        const val EXTRA_SHOW_SCHEDULES = "com.ikverse.egxanalyzer.SHOW_SCHEDULES"

        /** Clear of 1001-1005, which the notifiers before this one hold. */
        private const val FEED_ID = 1006
        private const val SCHEDULE_ID_BASE = 5000
        private const val ID_RANGE = 1000
        private const val FEED_REQUEST = 6
        private const val SCHEDULE_REQUEST = 50_000
    }
}
