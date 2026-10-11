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
import com.ikverse.egxanalyzer.model.WatchAlertFired
import com.ikverse.egxanalyzer.ui.AppDates
import com.ikverse.egxanalyzer.ui.formatPrice

/**
 * A watched stock reaching a level the reader named.
 *
 * Its own channel, like every notifier here, so muting it mutes nothing else. One notification per
 * alert, keyed on the stock so two alerts on one stock replace each other rather than stacking.
 */
class WatchlistNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Watch list", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A watched stock reaching a price you asked about."
            },
        )
    }

    /** True once the user has allowed notifications, which Android 13 and later ask for. */
    private fun permitted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            "android.permission.POST_NOTIFICATIONS",
        ) == PackageManager.PERMISSION_GRANTED

    fun announce(fired: List<WatchAlertFired>) {
        if (fired.isEmpty() || !permitted()) return
        fired.forEach { hit ->
            val ticker = hit.alert.ticker
            val title = if (hit.alert.above) {
                "$ticker reached ${formatPrice(hit.alert.level)}"
            } else {
                "$ticker fell to ${formatPrice(hit.alert.level)}"
            }
            val detail = "Last price ${formatPrice(hit.price.session.close)}, on " +
                AppDates.DayMonth.format(hit.price.session.date) + ". Tap to open the stock."
            manager.notify(
                NOTIFICATION_BASE + (ticker.hashCode() and 0xFFFF),
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_egx_notification)
                    .setContentTitle(title)
                    .setContentText(detail)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(openStock(ticker))
                    .build(),
            )
        }
    }

    private fun openStock(ticker: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_STOCK, ticker)
        return PendingIntent.getActivity(
            context,
            REQUEST_BASE + (ticker.hashCode() and 0xFFFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "watchlist"
        const val EXTRA_OPEN_STOCK = "com.ikverse.egxanalyzer.OPEN_STOCK"

        /** Clear of 1001-1007, which the notifiers before this one hold. */
        private const val NOTIFICATION_BASE = 20_000
        private const val REQUEST_BASE = 20_000
    }
}
