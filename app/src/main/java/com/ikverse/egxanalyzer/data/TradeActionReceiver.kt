package com.ikverse.egxanalyzer.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.ikverse.egxanalyzer.EgxApplication

/**
 * Answers the one notification action that needs no screen: **Keep open**.
 *
 * Every other decision a trade notification offers needs something only the user has. A sale needs
 * the price they got and the day they got it, so its action opens the app on those two fields
 * instead - see `AppState.openPositionToSell`. Keeping a trade open needs nothing at all: it is a
 * boolean on the row, and the whole point of offering it here is that the reader can answer a
 * deadline from the lock screen without the app coming up.
 *
 * **It is a user edit, so it announces nothing.** `setKeepOpen` recomputes with `announceChanges`
 * false, which is the rule every path that is the user editing their own trade follows - an app
 * that buzzes about the button somebody just pressed is one whose notifications get switched off.
 * The record is still updated, which is what stops the next price refresh announcing it.
 *
 * It reaches the application-scoped `AppState` rather than the database directly, which is the
 * opposite of what `OverdueWorker` does and is deliberate for the same reason `ScheduledJobWorker`
 * goes through it: recording a decision about a trade is what a button on screen already does, and
 * a second implementation would be a second set of rules about what is written, published and
 * re-scored. The cost - waking the state - is one this process has already paid, because a
 * notification action can only be pressed on a notification this app posted.
 */
class TradeActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KEEP_OPEN) return
        val id = intent.getStringExtra(EXTRA_POSITION_ID)?.takeIf(String::isNotBlank) ?: return
        val application = context.applicationContext as? EgxApplication ?: return
        val appState = application.appState
        // Silently ignored where the trade has gone - deleted on this device, or buried by another
        // and pulled down since. A notification outlives the thing it is about, and an action that
        // failed loudly about a trade the reader has already dealt with would be worse than one
        // that simply takes the notification away.
        appState.positionFor(id)?.let { position -> appState.setKeepOpen(position, keepOpen = true) }
        // Whether or not the trade was found: the notification has been acted on, and one that
        // stayed in the shade would invite the same press again.
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
    }

    companion object {
        const val ACTION_KEEP_OPEN = "com.ikverse.egxanalyzer.KEEP_OPEN"
        const val EXTRA_POSITION_ID = "com.ikverse.egxanalyzer.ACTION_POSITION_ID"
        const val EXTRA_NOTIFICATION_ID = "com.ikverse.egxanalyzer.ACTION_NOTIFICATION_ID"
    }
}
