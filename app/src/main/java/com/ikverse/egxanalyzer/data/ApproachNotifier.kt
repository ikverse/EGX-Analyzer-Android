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
import com.ikverse.egxanalyzer.model.ApproachChange
import com.ikverse.egxanalyzer.ui.formatPercent
import com.ikverse.egxanalyzer.ui.formatPrice

/**
 * Says a trade is closing on its stop or on target 2, while there is still something to decide.
 *
 * Every other notification in this app arrives after the fact - a stop announced is a stop already
 * taken, a buy zone announced is a price already there. This is the only one that arrives early,
 * which is also the only reason it is worth another channel: somebody who wants to be told what
 * happened and *not* to be told what might be about to may have exactly that.
 *
 * **Its own channel beside the trade one**, for the reason every channel here is its own: Android
 * silences a whole channel at a time, and a warning that is inherently noisier than a settlement
 * must be silenceable without taking the settlements with it.
 */
class ApproachNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Approaching a level",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "A trade coming within reach of its stop or of target 2."
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
     * One notification per trade and level, under a group summary.
     *
     * The same shape `TradeStatusNotifier` uses and for the same reason: each leads to a different
     * card, so a single digest could carry only one of them to the trade it is about, and the group
     * is what keeps a busy afternoon from becoming four buzzes.
     */
    fun announce(changes: List<ApproachChange>) {
        if (changes.isEmpty() || !permitted()) return
        changes.forEach { change ->
            val detail = detail(change)
            manager.notify(
                notificationId(change),
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_egx_notification)
                    .setContentTitle("${change.position.ticker} ${change.level.summary}")
                    .setContentText(detail)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setGroup(GROUP_KEY)
                    .setContentIntent(openTrade(change.position.position.id))
                    .build(),
            )
        }
        if (changes.size > 1) summarise(changes)
    }

    private fun summarise(changes: List<ApproachChange>) {
        val what = "${changes.size} trades near a level"
        val lines = changes.map { "${it.position.ticker} ${it.level.summary}" }
        manager.notify(
            SUMMARY_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_egx_notification)
                .setContentTitle(what)
                .setContentText(lines.joinToString(" · "))
                .setStyle(
                    NotificationCompat.InboxStyle()
                        .setSummaryText(what)
                        .also { style -> lines.forEach(style::addLine) },
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setContentIntent(openTrade(null))
                .build(),
        )
    }

    /**
     * The distance, the level, and where the trade stands - and no advice about any of it.
     *
     * The return is included because it is what decides whether a stop coming up is a loss to cut
     * or a profit to protect, and those are opposite situations wearing the same sentence.
     */
    private fun detail(change: ApproachChange): String {
        val distance = formatPercent(change.distancePercent, signed = false)
        val level = "${change.level.label} at ${formatPrice(change.price)}"
        val standing = change.position.returnPct
            ?.let { "You are ${formatPercent(it)} on what you paid." }
            ?: "No return to quote, because this stock's prices changed scale."
        return "$distance from its $level. $standing"
    }

    private fun openTrade(positionId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(OverdueNotifier.EXTRA_SHOW_PORTFOLIO, true)
        if (positionId != null) {
            intent.putExtra(TradeStatusNotifier.EXTRA_POSITION_ID, positionId)
        }
        return PendingIntent.getActivity(
            context,
            positionId?.let { REQUEST_BASE + it.hashCode().mod(ID_RANGE) } ?: SUMMARY_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "approach"

        /** Clear of the analysis notification's 1001, overdue's 1002 and trade status's 1003. */
        private const val SUMMARY_ID = 1004
        private const val ID_BASE = 4000
        private const val ID_RANGE = 1000
        private const val REQUEST_BASE = 40_000
        private const val GROUP_KEY = "com.ikverse.egxanalyzer.APPROACH"

        /**
         * Stable per trade **and** level, so a trade that closes on its stop twice replaces its own
         * notification rather than landing on a different one, while its target-2 warning keeps a
         * notification of its own.
         */
        private fun notificationId(change: ApproachChange): Int =
            ID_BASE + "${change.position.position.id}|${change.level.name}".hashCode().mod(ID_RANGE)
    }
}
