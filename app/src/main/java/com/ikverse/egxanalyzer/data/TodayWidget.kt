package com.ikverse.egxanalyzer.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ikverse.egxanalyzer.MainActivity
import com.ikverse.egxanalyzer.model.SessionDigest
import com.ikverse.egxanalyzer.ui.AppDates
import java.time.LocalDate

/**
 * What the session did and what is overdue, on the home screen.
 *
 * The smallest useful thing this app can say without being opened, and the two facts it says are
 * the two the notifications already lead with - so a reader who has them switched off still has
 * somewhere to glance. Pressing it opens the Portfolio, which is where both are answered in full.
 *
 * **It reads and never computes.** The digest comes out of `session_events`, which is written on
 * every recompute, and the overdue count comes off the last one the app took. A widget that rebuilt
 * the portfolio would be doing the most expensive thing in the app on a surface the system may draw
 * a dozen times a day, in a process it is free to kill halfway through - and it would do it while
 * holding prices that could be a week old anyway. What it shows is what the app last knew, which is
 * the honest thing for a glance.
 *
 * **The one new UI toolkit in the codebase**, and it is here because a widget genuinely is a
 * different thing: RemoteViews underneath, drawn by the launcher rather than by this app. Glance is
 * what keeps it written in the same idiom as the rest instead of in an XML layout. Nothing else in
 * the app imports it, and nothing here imports anything from `ui` except `AppDates`.
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = LocalDataStore(context)
        // Thirty days back, which is what `session_events` keeps. The newest row is the answer; the
        // range only has to be wide enough to contain it on a phone that has been shut for a while.
        val today = LocalDate.now()
        val digest = runCatching {
            store.sessionEvents(today.minusDays(30), today).maxByOrNull(SessionDigest::date)
        }.getOrNull()
        val overdue = runCatching {
            SettingsRepository(context, AndroidKeystoreCredentialStore(context)).lastOverdueCount()
        }.getOrDefault(0)
        provideContent { Content(digest, overdue) }
    }

    @Composable
    private fun Content(digest: SessionDigest?, overdue: Int) {
        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    digest?.let { AppDates.DayMonth.format(it.date) } ?: "EGX Analyzer",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    headline(digest),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                // Absent when nothing is late, which is the rule the Overdue card follows: a
                // permanent line reading "0 overdue" is furniture, and this surface has room for
                // about three lines in total.
                if (overdue > 0) {
                    Text(
                        if (overdue == 1) "1 overdue" else "$overdue overdue",
                        // One colour in both themes: the red that means a deadline has passed is the same
                        // red on a light launcher and a dark one, exactly as it is in the app.
                        style = TextStyle(color = ColorProvider(Red)),
                    )
                }
            }
        }
    }

    /**
     * The session in one phrase, in the card's own order of importance.
     *
     * A stop outranks a target, exactly as it does in the heading on screen: a line leading with
     * the good news over a session that also took a stop is the widget burying the thing worth
     * knowing.
     */
    private fun headline(digest: SessionDigest?): String = when {
        digest == null -> "No prices yet"
        digest.isEmpty -> "Nothing moved"
        digest.stops > 0 -> "${digest.stops} stopped out"
        digest.targets > 0 -> "${digest.targets} reached a target"
        digest.expiries > 0 -> "${digest.expiries} out of time"
        digest.inRange > 0 -> "${digest.inRange} came into range"
        else -> "${digest.newCalls} new calls"
    }

    private companion object {
        /**
         * The error colour, stated here rather than read from the app's scheme.
         *
         * A widget is drawn by the launcher in its own process-less composition and has no access
         * to `MaterialTheme`, so the one colour this surface needs that Glance does not supply is
         * written out. It is the same red the Overdue pill wears, and if that ever moves this is
         * the one place that has to move with it.
         */
        val Red = Color(0xFFE5484D)
    }
}

/**
 * What the launcher talks to.
 *
 * Its only job is to name the widget above. Updates are pushed by the app on every recompute - see
 * [refreshTodayWidget] - rather than by a period declared in the widget's own info: the record
 * changes when prices arrive, not on a clock, and a widget polling for a change it cannot cause is
 * a wake-up spent on nothing.
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/**
 * Redraws the widget, if one is on a home screen.
 *
 * Swallows everything. A launcher that is mid-update, has been replaced, or never had a widget is
 * not something the reader asked about, and an exception raised from a recompute over a glance
 * surface would take the record's own rebuild down with it.
 */
suspend fun refreshTodayWidget(context: Context) {
    runCatching { TodayWidget().updateAll(context) }
}
