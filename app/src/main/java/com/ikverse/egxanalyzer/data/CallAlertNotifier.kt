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
import com.ikverse.egxanalyzer.model.CallChange
import com.ikverse.egxanalyzer.model.positionId
import com.ikverse.egxanalyzer.model.alertId
import com.ikverse.egxanalyzer.ui.formatPrice

/**
 * Tells the phone a stock has traded into a buy zone somebody printed.
 *
 * `TradeStatusNotifier` speaks about trades the user is in; this speaks about the calls they are
 * not. Prices refresh through the session on their own now, so the app already knew at eleven in
 * the morning that a call had become takeable, and told nobody unless they happened to open it.
 *
 * **Its own channel, beside the trade one.** Android silences a whole channel at a time, and these
 * are different questions: one reports something that happened to money the user has committed, and
 * the other reports an opportunity they have committed nothing to. Somebody who wants the first and
 * not the second must be able to have exactly that.
 *
 * The wording is a fact and never an instruction: *AMOC has traded into the buy zone.* The app
 * measures what sources said and what the market did, and it does not tell anyone to buy anything.
 */
class CallAlertNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Buy zones reached",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description =
                        "A stock has traded into the entry band of a call you have not taken."
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
     * One notification per call, gathered under a summary.
     *
     * The same shape as the trade notifications and for the same reason: each leads to a different
     * card, so one digest could only carry one of them to where it belongs, and the group is what
     * stops a busy morning becoming six separate buzzes.
     */
    fun announce(changes: List<CallChange>) {
        if (changes.isEmpty() || !permitted()) return
        changes.forEach { change ->
            val detail = detail(change)
            manager.notify(
                notificationId(change.call.alertId),
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_egx_notification)
                    .setContentTitle("${change.call.ticker} is in its buy zone")
                    .setContentText(detail)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                    .setContentIntent(openCall(change.call.positionId))
                    .setAutoCancel(true)
                    .setGroup(GROUP_KEY)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build(),
            )
        }
        summarise(changes)
    }

    private fun summarise(changes: List<CallChange>) {
        val lines = changes.map { "${it.call.ticker} · ${detail(it)}" }
        manager.notify(
            SUMMARY_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_egx_notification)
                .setContentTitle(
                    if (changes.size == 1) {
                        "1 call is in its buy zone"
                    } else {
                        "${changes.size} calls are in their buy zones"
                    },
                )
                .setStyle(
                    NotificationCompat.InboxStyle().also { style ->
                        lines.forEach(style::addLine)
                    },
                )
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build(),
        )
    }

    /**
     * What the market did, with the source that printed the band.
     *
     * The channel is named because the band is **its** claim rather than the app's, and the price
     * because that is the fact behind the notification.
     */
    private fun detail(change: CallChange): String =
        "Last close ${formatPrice(change.price)}, inside the entry band ⁨${change.call.channel}⁩ " +
            "printed on ${change.call.openedOn}."

    /**
     * Opens the call's own card, through the entrance every other link to one already uses.
     *
     * Carries `positionId` and not `alertId`, because that is the key `AppState.openCall` and the
     * arrival effect in Insights both match on - it unfolds the session card, scrolls to the call
     * and flashes its edge. Two channels calling one stock share it, so both cards flash and the
     * first is scrolled to, which is exactly what a reader arriving on this notification wants to
     * see. A second path would be a second way for the app to disagree about where a call is.
     */
    private fun openCall(positionId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_CALL_ID, positionId)
        return PendingIntent.getActivity(
            context,
            positionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "call-alerts"
        const val EXTRA_CALL_ID = "com.ikverse.egxanalyzer.CALL_ID"

        /** Clear of the analysis notification, the overdue reminder and the trade summary. */
        private const val SUMMARY_ID = 1004
        private const val ID_BASE = 3000
        private const val ID_RANGE = 1000
        private const val GROUP_KEY = "com.ikverse.egxanalyzer.CALL_ALERTS"

        /**
         * A stable id per call, so one that crosses back and forth replaces its own notification.
         *
         * Derived rather than counted for the reason the trade ids are: a counted id depends on
         * what else moved in the same sweep, so one call would land somewhere different each time.
         */
        private fun notificationId(callId: String): Int =
            ID_BASE + callId.hashCode().mod(ID_RANGE)
    }
}
