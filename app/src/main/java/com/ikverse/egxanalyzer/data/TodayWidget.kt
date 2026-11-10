package com.ikverse.egxanalyzer.data

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
// The base `androidx.glance.action.actionStartActivity` only launches by class or component; the
// overload that takes a plain `Intent` - the one this needs, to carry the same action string the
// Portfolio shortcut and the overdue notification already launch with - lives in this package
// instead.
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ikverse.egxanalyzer.MainActivity
import com.ikverse.egxanalyzer.model.ScheduleClock
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

    /**
     * Two layouts, chosen by the space the launcher gives: the one-tile glance at the size it
     * always had, and a taller one with the session's three counts side by side.
     */
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SmallSize, MidSize, TallSize))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = LocalDataStore(context)
        // Thirty days back, which is what `session_events` keeps. The newest row is the answer; the
        // range only has to be wide enough to contain it on a phone that has been shut for a while.
        val today = LocalDate.now(ScheduleClock.ZONE)
        val digest = runCatching {
            store.sessionEvents(today.minusDays(30), today).maxByOrNull(SessionDigest::date)
        }.getOrNull()
        val overdue = runCatching {
            SettingsRepository(context, AndroidKeystoreCredentialStore(context)).lastOverdueCount()
        }.getOrDefault(0)
        // The same action the Portfolio shortcut and the overdue notification both name, so all
        // three entrances are read one way by `MainActivity.openRequestedResult` - which is what
        // actually lands on Portfolio and refreshes the overdue count. A plain launch with no
        // action opened whatever tab the app was last left on instead, which is what this fixes.
        val openPortfolio = Intent(context, MainActivity::class.java)
            .setAction(AppShortcuts.ACTION_PORTFOLIO)
        provideContent {
            val height = LocalSize.current.height.value
            when (widgetLayoutFor(height)) {
                WidgetLayout.SMALL -> Small(digest, overdue, openPortfolio, withDate = showsDate(height))
                WidgetLayout.TALL -> Tall(digest, overdue, openPortfolio)
            }
        }
    }

    /**
     * The glance: the date, the session in one phrase, and the overdue pill when something is late.
     * Cobalt on a light home screen and graphite with the phrase in lime on a dark one, as the
     * Portfolio's hero tile is.
     */
    @Composable
    private fun Small(digest: SessionDigest?, overdue: Int, openPortfolio: Intent, withDate: Boolean) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(30.dp)
                .background(ColorProvider(day = Cobalt, night = Graphite))
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .clickable(actionStartActivity(openPortfolio)),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            // Left out at the one-cell height, where the date, the phrase and the pill do not all
            // fit and the last of them was cut off.
            if (withDate) {
                Text(
                    digest?.let { AppDates.DayMonth.format(it.date) } ?: "EGX Analyzer",
                    style = TextStyle(
                        color = ColorProvider(day = White.copy(alpha = 0.85f), night = Pale.copy(alpha = 0.7f)),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
            Text(
                headline(digest),
                style = TextStyle(
                    color = ColorProvider(day = White, night = Lime),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
                maxLines = 2,
            )
            // Absent when nothing is late, which is the rule the Overdue tile follows: a permanent
            // pill reading "0 overdue" is furniture, and this surface has room for about three lines.
            if (overdue > 0) {
                Spacer(GlanceModifier.height(6.dp))
                Box(
                    GlanceModifier
                        .cornerRadius(13.dp)
                        .background(ColorProvider(day = Coral, night = Coral))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (overdue == 1) "1 overdue" else "$overdue overdue",
                        style = TextStyle(color = ColorProvider(day = Ink, night = Ink), fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    )
                }
            }
        }
    }

    /** The taller size: "Today", the session's date, and three counts on the tile colours. */
    @Composable
    private fun Tall(digest: SessionDigest?, overdue: Int, openPortfolio: Intent) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(30.dp)
                .background(ColorProvider(day = Paper, night = Graphite))
                .padding(14.dp)
                .clickable(actionStartActivity(openPortfolio)),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(
                    "Today",
                    style = TextStyle(color = ColorProvider(day = Ink, night = Pale), fontWeight = FontWeight.Bold, fontSize = 17.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    digest?.let { AppDates.DayMonth.format(it.date) } ?: "No prices yet",
                    style = TextStyle(color = ColorProvider(day = Muted, night = Pale.copy(alpha = 0.7f)), fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                )
            }
            Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Count(digest?.targets ?: 0, "hit a target", Lime, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                Count(digest?.stops ?: 0, "stopped", Coral, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                Count(overdue, "overdue", Sunflower, GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun Count(value: Int, label: String, fill: Color, modifier: GlanceModifier) {
        Column(
            modifier
                .cornerRadius(16.dp)
                .background(ColorProvider(day = fill, night = fill))
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Text(
                value.toString(),
                style = TextStyle(color = ColorProvider(day = Ink, night = Ink), fontWeight = FontWeight.Bold, fontSize = 22.sp, fontFamily = FontFamily.Monospace),
            )
            Text(label, style = TextStyle(color = ColorProvider(day = Ink, night = Ink), fontSize = 11.sp), maxLines = 1)
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
        val SmallSize = DpSize(110.dp, 40.dp)
        val MidSize = DpSize(110.dp, DateFromHeight.dp)
        val TallSize = DpSize(110.dp, TallFromHeight.dp)

        /**
         * The palette, written out. A widget is drawn by the launcher in its own process-less
         * composition and has no access to the app's theme, so these are LightPalette's and
         * DarkPalette's values from `ui/theme/Palette.kt`; if those move, these move with them.
         */
        val Cobalt = Color(0xFF1E2BFF)
        val Lime = Color(0xFFC8FF4D)
        val Coral = Color(0xFFFF6B4A)
        val Sunflower = Color(0xFFFFD12E)
        val Ink = Color(0xFF101828)
        val Paper = Color(0xFFEFEDE6)
        val Graphite = Color(0xFF1F2228)
        val Pale = Color(0xFFE6EAF2)
        val Muted = Color(0xFF6A675E)
        val White = Color(0xFFFFFFFF)
    }
}

/** The two layouts the widget has, chosen by how tall the launcher has made it. */
internal enum class WidgetLayout { SMALL, TALL }

/** Tall enough for the row of three counts under its heading. */
internal const val TallFromHeight = 100f

/** Tall enough for the small layout's date line above its phrase and pill. */
internal const val DateFromHeight = 72f

internal fun showsDate(heightDp: Float): Boolean = heightDp >= DateFromHeight

internal fun widgetLayoutFor(heightDp: Float): WidgetLayout =
    if (heightDp >= TallFromHeight) WidgetLayout.TALL else WidgetLayout.SMALL

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
