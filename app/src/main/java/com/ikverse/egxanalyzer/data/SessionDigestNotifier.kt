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
import com.ikverse.egxanalyzer.model.SessionDigest
import com.ikverse.egxanalyzer.ui.AppDates

/**
 * One line at the close saying what the whole session did.
 *
 * The card on Portfolio and Insights has said this since it was built, and said it only to somebody
 * who opened a tab. `TradeStatusNotifier` covers the reader's own trades one at a time, so the gap
 * this fills is precisely the session where *nothing of theirs moved* and three of their sources'
 * calls reached targets - a busy session the phone was completely silent about, because every
 * notification the app had was about a trade.
 *
 * **The whole session, never the held half.** [SessionDigest.heldOnly] exists for the Portfolio's
 * card, where narrowing is right because that tab is the reader's money. Here narrowing would leave
 * this saying what the per-trade notifications already said, one buzz later.
 *
 * **Default off, and its own channel.** A daily line is the most easily resented notification an
 * app can have: it arrives whether or not anything happened to the reader, on a rhythm rather than
 * on an event. That is the `callAlertsEnabled` case exactly, and it gets the same answer - switched
 * on by somebody who wants it, silenceable without touching the alerts about their own trades.
 */
class SessionDigestNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "What the session did",
                    // Low: it is a summary at a fixed hour rather than something that happened, and
                    // the events worth interrupting for have already interrupted on their own
                    // channels. A daily line that buzzes is a daily line that gets switched off.
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "One line after the close, saying what the session did."
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
     * Says what the session did, or says nothing at all.
     *
     * A quiet session is **silent here and not on the card**, which is the one place the two part
     * company. On screen "nothing moved on this session" is a real answer to a question the reader
     * asked by looking; in the shade it is an interruption to report an absence, every evening the
     * market does nothing.
     */
    fun announce(digest: SessionDigest) {
        if (digest.isEmpty || !permitted()) return
        val detail = detail(digest)
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_egx_notification)
                .setContentTitle(AppDates.DayMonth.format(digest.date) + " · what the session did")
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                // One id, so a recompute later the same evening replaces the line rather than
                // stacking a second copy of one session under it.
                .setOnlyAlertOnce(true)
                .setContentIntent(openPortfolio())
                .build(),
        )
    }

    /**
     * The counts, in the card's own order and words.
     *
     * Zeros are omitted, exactly as the heading omits them - a line reading "0 stops" is a line
     * spent saying nothing happened. The reader's own trades are named separately at the end
     * because that is the part they will look for first, and a count of everything hides it.
     */
    private fun detail(digest: SessionDigest): String {
        val parts = buildList {
            if (digest.targets > 0) add("${digest.targets} reached a target")
            if (digest.stops > 0) add("${digest.stops} stopped out")
            if (digest.expiries > 0) add("${digest.expiries} ran out of time")
            if (digest.inRange > 0) add("${digest.inRange} came into range")
            if (digest.newCalls > 0) {
                add(
                    "${digest.newCalls} new " + (if (digest.newCalls == 1) "call" else "calls") +
                        " from ${digest.newCallSources} " +
                        (if (digest.newCallSources == 1) "source" else "sources"),
                )
            }
        }
        val held = digest.heldEvents
        val yours = when (held) {
            0 -> "None of them were yours."
            1 -> "1 of them was one of your trades."
            else -> "$held of them were your trades."
        }
        return parts.joinToString(" · ") + ". " + yours
    }

    /**
     * Opens the Portfolio, where the card this is a summary of sits second on the page.
     *
     * The Portfolio rather than Insights although the count is the whole market's: the reader
     * following a digest into the app is asking what it means for them, and the narrower card is
     * the one that answers that. Insights is one swipe away and carries the same card in full.
     */
    private fun openPortfolio(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(OverdueNotifier.EXTRA_SHOW_PORTFOLIO, true)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "session-digest"

        /** Clear of 1001-1004, which the four notifiers before this one hold. */
        private const val NOTIFICATION_ID = 1005
        private const val REQUEST_CODE = 5
    }
}
