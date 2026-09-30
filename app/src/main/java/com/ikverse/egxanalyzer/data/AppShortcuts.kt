package com.ikverse.egxanalyzer.data

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ikverse.egxanalyzer.MainActivity
import com.ikverse.egxanalyzer.R

/**
 * The one shortcut that cannot be written in XML, because it counts something.
 *
 * Portfolio and Insights are static (`res/xml/shortcuts.xml`) - they are the same two entrances
 * whatever the record holds. This one says how many trades are overdue, so it exists only while
 * some are, and its label changes as the number does.
 *
 * **Pushed and removed rather than enabled and disabled.** A disabled shortcut still occupies a
 * slot in the launcher's list and shows greyed out on some of them, which is a permanent reminder
 * of a state the app is normally not in - the same reason the Overdue card is absent rather than
 * empty when nothing is late.
 *
 * Every failure is swallowed. A launcher that refuses a shortcut, is mid-update, or has run out of
 * slots is not something the reader asked about, and an exception raised from a resume effect over
 * a convenience would take the record's own refresh down with it.
 */
object AppShortcuts {

    private const val OVERDUE_ID = "overdue"

    /** Named in `res/xml/shortcuts.xml` too, and read by `MainActivity`. */
    const val ACTION_PORTFOLIO = "com.ikverse.egxanalyzer.OPEN_PORTFOLIO"
    const val ACTION_INSIGHTS = "com.ikverse.egxanalyzer.OPEN_INSIGHTS"

    /**
     * Puts the overdue shortcut up, or takes it down.
     *
     * Called from wherever the overdue count is already known, so it costs no extra reading of the
     * record.
     */
    fun setOverdue(context: Context, count: Int) {
        runCatching {
            if (count <= 0) {
                ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(OVERDUE_ID))
                return@runCatching
            }
            val label = if (count == 1) {
                "1 overdue trade"
            } else {
                "$count overdue trades"
            }
            // The same action the static Portfolio shortcut names, so both are read one way. A
            // shortcut intent must carry an action at all, or the launcher refuses it.
            val intent = Intent(context, MainActivity::class.java).setAction(ACTION_PORTFOLIO)
            ShortcutManagerCompat.pushDynamicShortcut(
                context,
                ShortcutInfoCompat.Builder(context, OVERDUE_ID)
                    // The count is the whole point of this one, so it goes in the short label -
                    // the long one is what a launcher shows where it has room, and "Overdue" alone
                    // beside a number is the number said twice.
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_egx_notification))
                    .setIntent(intent)
                    .build(),
            )
        }
    }
}
