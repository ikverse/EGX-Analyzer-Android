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
import com.ikverse.egxanalyzer.model.TradeChange
import com.ikverse.egxanalyzer.model.TradeEvent
import com.ikverse.egxanalyzer.ui.AppDates
import com.ikverse.egxanalyzer.ui.formatPercent
import com.ikverse.egxanalyzer.ui.formatPrice

/**
 * Says what the market has just done to a trade the user is in.
 *
 * The Portfolio tab has always known - it re-derives every status from the prices on disk - and
 * that was exactly the gap. Prices now refresh through the session on their own, so a target
 * reached at eleven in the morning was being answered correctly by a screen nobody was looking at.
 *
 * Its own channel rather than the overdue one. They are different questions - one is the market
 * moving, the other is the app asking for a decision it cannot make on the user's behalf - and
 * Android silences a whole channel at a time, so sharing one would mean losing both together.
 */
class TradeStatusNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Trade status",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description =
                        "Targets reached, stops taken and deadlines passed on your trades."
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
     * One notification per trade, gathered under a summary.
     *
     * Per trade rather than one digest, because each of these leads somewhere different: a stop
     * taken on one holding and a target reached on another are two decisions, and a single
     * notification could only carry one of them to the card it belongs to. The group is what keeps
     * that from becoming four separate buzzes - a bad morning arrives as one collapsed block, and
     * every line still opens its own trade.
     */
    fun announce(changes: List<TradeChange>) {
        if (changes.isEmpty() || !permitted()) return
        changes.forEach { change ->
            val detail = detail(change)
            val id = change.position.position.id
            val notificationId = notificationId(id)
            manager.notify(
                notificationId,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_egx_notification)
                    .setContentTitle("${change.position.ticker} ${change.event.summary}")
                    .setContentText(detail)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setGroup(GROUP_KEY)
                    .setContentIntent(openTrade(id))
                    // The two answers to what this notification is reporting, in the order the
                    // reader is most likely to want them. Everything the app knows about the trade
                    // is now on the notification, and the decision it leaves open is on it too -
                    // which is the difference between being told a stop was taken and being able to
                    // do anything about it without unlocking the phone.
                    .also { builder ->
                        // Keep Open defeats every automatic close but target 2, so it is offered on
                        // every ending except that one - where there is nothing left to hold the
                        // trade open for, because it did what it was bought to do.
                        if (change.event != TradeEvent.TARGET2_HIT) {
                            builder.addAction(0, "Keep open", keepOpen(id, notificationId))
                        }
                        builder.addAction(0, "Record sale", recordSale(id))
                    }
                    .build(),
            )
        }
        if (changes.size > 1) summarise(changes)
    }

    /**
     * The line the group collapses to.
     *
     * Posted only for more than one change: a summary above a single notification is the same
     * sentence twice, and a lone child already reads as a whole.
     */
    private fun summarise(changes: List<TradeChange>) {
        val what = "${changes.size} trades changed"
        val lines = changes.map { "${it.position.ticker} ${it.event.summary}" }
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
     * What the trade is worth now, measured the way the card measures it.
     *
     * The return is always an estimate here and is labelled as one: every trade that reaches this
     * point is one the user has not told the app they sold, so the figure is marked at a level the
     * market reached rather than at a price they got. The entry is their own, which is the basis
     * every percentage in the portfolio is measured from.
     */
    private fun detail(change: TradeChange): String {
        val view = change.position
        val entry = "bought ${view.position.entryDate.format(AppDates.DayMonth)} at " +
            formatPrice(view.position.entryPrice)
        // Null across a change of scale, where the entry was paid in the old money and every price
        // since is quoted in the new. The card declines to print a percentage of two different
        // things, and so does this.
        val figure = view.returnPct?.let { "${formatPercent(it)} on what you paid" }
            ?: "no return to quote, because this stock's prices changed scale"
        return "$figure, $entry. An estimate until you record the sale."
    }

    /**
     * Opens the app on the trade itself, or on the Portfolio where no one trade is meant.
     *
     * The request code is the notification's own, or every one of these would hand back the first
     * intent that was built and each notification would open the same trade.
     */
    private fun openTrade(positionId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(OverdueNotifier.EXTRA_SHOW_PORTFOLIO, true)
        if (positionId != null) intent.putExtra(EXTRA_POSITION_ID, positionId)
        return PendingIntent.getActivity(
            context,
            positionId?.let(::notificationId) ?: SUMMARY_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Marks the trade as kept open without opening the app.
     *
     * A broadcast rather than an activity, which is what makes it a true one-tap: the shade answers
     * it in place and the phone stays where it is. It is the only action here that can be, because
     * it is the only one whose answer the app already holds - see [TradeActionReceiver].
     *
     * `FLAG_MUTABLE` is deliberately not used: nothing outside this app may add to the extras, and
     * an immutable intent is the whole of that guarantee.
     */
    private fun keepOpen(positionId: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, TradeActionReceiver::class.java)
            .setAction(TradeActionReceiver.ACTION_KEEP_OPEN)
            .putExtra(TradeActionReceiver.EXTRA_POSITION_ID, positionId)
            .putExtra(TradeActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        return PendingIntent.getBroadcast(
            context,
            // Clear of the content intent's request code, which is the notification id itself: two
            // pending intents sharing a request code and a component hand back the first one built,
            // so the action would open the trade instead of keeping it open.
            KEEP_OPEN_REQUEST_BASE + notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Opens the app on the trade with its sale dialog already showing.
     *
     * Not a one-tap, and it cannot be: a sale is a price and a date the user got and the app has
     * neither. What it saves is the part the app *can* do - finding the card. See
     * `AppState.openPositionToSell`.
     */
    private fun recordSale(positionId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(OverdueNotifier.EXTRA_SHOW_PORTFOLIO, true)
            .putExtra(EXTRA_SELL_POSITION_ID, positionId)
        return PendingIntent.getActivity(
            context,
            SELL_REQUEST_BASE + notificationId(positionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "trade-status"
        const val EXTRA_POSITION_ID = "com.ikverse.egxanalyzer.POSITION_ID"

        /** Clear of the analysis notification's 1001 and the overdue reminder's 1002. */
        private const val SUMMARY_ID = 1003
        /** The trade a Record sale action named, read by `MainActivity`. */
        const val EXTRA_SELL_POSITION_ID = "com.ikverse.egxanalyzer.SELL_POSITION_ID"

        private const val ID_BASE = 2000
        private const val ID_RANGE = 1000

        // Request codes for the two actions, held well clear of the notification ids the content
        // intent uses as its own. A pending intent is identified by request code and component, so
        // a collision hands back an intent built for something else.
        private const val KEEP_OPEN_REQUEST_BASE = 20_000
        private const val SELL_REQUEST_BASE = 30_000
        private const val GROUP_KEY = "com.ikverse.egxanalyzer.TRADE_STATUS"

        /**
         * A stable id per trade, so one that changes twice replaces its own notification.
         *
         * Derived from the position id rather than counted off, because a counted one depends on
         * what else moved in the same sweep and a trade would land on a different notification
         * every time. Two ids can collide inside the range; the cost is one notification replacing
         * another, which is why the summary carries every line.
         */
        private fun notificationId(positionId: String): Int =
            ID_BASE + positionId.hashCode().mod(ID_RANGE)
    }
}
