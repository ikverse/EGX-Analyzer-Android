package com.ikverse.egxanalyzer.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.ikverse.egxanalyzer.data.JobScheduler
import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.ScheduledJob
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * What this phone is going to do on its own, on the screen where runs are set up.
 *
 * Here rather than buried in Settings because a schedule is a run with a time on it, and this is
 * the screen where a run is configured. The line that matters most is the next one: the failure
 * mode of every scheduler on this platform is silence - the phone puts the app to sleep, nothing
 * fires, and nothing says so - and a card that names the next fire and the last outcome is the
 * only way to notice from the outside.
 */
@Composable
internal fun SchedulesSection(appState: AppState) {
    var sheetOpen by remember { mutableStateOf(false) }
    SectionCard(title = "Scheduled runs", icon = Icons.Outlined.Schedule) {
        val now = Instant.now()
        Text(
            scheduleSummary(appState.scheduledJobs, appState.schedulesEnabled, now),
            style = MaterialTheme.typography.bodyMedium,
        )
        val last = appState.scheduledJobs
            .filter { it.lastFiredAt != null }
            .maxByOrNull { it.lastFiredAt!! }
        if (last != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                StatusPill(last.lastOutcome.displayName, last.lastOutcome.tone())
                Text(
                    lastRunLine(last, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Runs on this phone only. Schedules are never synced - the other devices would do " +
                "the same work over again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { sheetOpen = true }) {
                Text(if (appState.scheduledJobs.isEmpty()) "Add a schedule" else "Manage schedules")
            }
        }
    }
    if (sheetOpen) SchedulesSheet(appState) { sheetOpen = false }
}

/** Green for a run that worked, red for one that failed, grey for the rest. */
internal fun JobOutcome.tone(): StatusTone = when (this) {
    JobOutcome.SUCCEEDED -> StatusTone.GOOD
    JobOutcome.FAILED, JobOutcome.MISSED -> StatusTone.BAD
    JobOutcome.SKIPPED, JobOutcome.NEVER -> StatusTone.NEUTRAL
}

/**
 * The one line a folded card has to inform with.
 *
 * Leads with whatever is actually stopping the schedules, because a card reading "next Sunday
 * 18:00" over a switch that is off is a card that lies.
 */
internal fun scheduleSummary(
    jobs: List<ScheduledJob>,
    enabled: Boolean,
    now: Instant,
    zone: ZoneId = ScheduleClock.ZONE,
): String {
    val live = jobs.filter { it.enabled && it.runnable }
    return when {
        jobs.isEmpty() -> "Nothing scheduled."
        !enabled -> "Schedules are switched off on this phone."
        live.isEmpty() -> "Every schedule is switched off."
        else -> {
            val next = ScheduleClock.earliestFire(live, now, zone)
            if (next == null) {
                "Nothing left to run."
            } else {
                val owner = live.firstOrNull { ScheduleClock.nextFire(it, now, zone) == next }
                "Next: ${owner?.name ?: "a schedule"} ${whenLabel(next, now, zone)}"
            }
        }
    }
}

/** What became of the last fire, in the fewest words that still say which one it was. */
internal fun lastRunLine(job: ScheduledJob, now: Instant, zone: ZoneId = ScheduleClock.ZONE): String {
    val fired = job.lastFiredAt ?: return job.name
    val when_ = whenLabel(fired, now, zone)
    return listOfNotNull(job.name, when_, job.lastMessage).joinToString(" · ")
}

/**
 * A moment as a reader would say it: today, tomorrow, a weekday, or a date.
 *
 * Relative near the present and absolute past it, because "Sunday" is unambiguous three days out
 * and useless three weeks out - by then the reader needs the date, which is the point at which the
 * weekday stops being the shorter way of saying it.
 */
internal fun whenLabel(at: Instant, now: Instant, zone: ZoneId = ScheduleClock.ZONE): String {
    val moment = at.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val clock = ScheduleClock.clock(moment.toLocalTime())
    val days = Duration.between(today.atStartOfDay(zone), moment.toLocalDate().atStartOfDay(zone))
        .toDays()
    return when {
        days == 0L -> "today $clock"
        days == 1L -> "tomorrow $clock"
        days == -1L -> "yesterday $clock"
        days in 2..6 -> "${moment.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} $clock"
        days in -6..-2 ->
            "last ${moment.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} $clock"

        else -> "${moment.toLocalDate()} $clock"
    }
}


/**
 * The same schedules, in Settings, with the two system permissions that decide whether they work.
 *
 * Both belong here rather than on the Analyze card: they are granted on system pages, they are
 * granted once, and neither is about any one schedule. Which is also why they are worth showing at
 * all - an app that has been put to sleep by the phone keeps no schedules and reports nothing, and
 * these two lines are the only place that state is visible.
 */
@Composable
internal fun SchedulesSettingsSection(appState: AppState, contentMaxWidth: Dp) {
    val context = LocalContext.current
    var sheetOpen by remember { mutableStateOf(false) }
    ExpandableSection(
        "Schedules",
        icon = Icons.Outlined.Schedule,
        summary = scheduleSummary(appState.scheduledJobs, appState.schedulesEnabled, Instant.now()),
        contentMaxWidth = contentMaxWidth,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = appState.schedulesEnabled,
                onCheckedChange = appState::updateSchedulesEnabled,
            )
            Text("Run schedules on this phone")
        }
        Text(
            "Off by default, and this phone's own answer - schedules are the one thing that never " +
                "syncs, because three devices keeping one schedule is the same work done three " +
                "times. Nothing that can be scheduled here spends cloud credits.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SystemPermissionRow(
            granted = remember { JobScheduler(context) }.canScheduleExact(),
            granted_ = "Exact alarms are allowed, so runs keep to the minute.",
            missing = "Exact alarms are off. Runs can arrive up to an hour late.",
            action = "Allow exact alarms",
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
        SystemPermissionRow(
            granted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
            granted_ = "Battery optimization is off for this app, so it will not be put to sleep.",
            missing = "Battery optimization can put this app to sleep, and a sleeping app keeps " +
                "no schedules. Samsung does this after a few days of not opening it.",
            action = "Open battery settings",
        ) {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        TextButton(onClick = { sheetOpen = true }) {
            Text(if (appState.scheduledJobs.isEmpty()) "Add a schedule" else "Manage schedules")
        }
    }
    if (sheetOpen) SchedulesSheet(appState) { sheetOpen = false }
}

/**
 * One system permission, said the same way whichever it is.
 *
 * The granted line is drawn too, not only the missing one: a page that goes quiet once something is
 * right leaves the reader unable to tell "granted" from "this app forgot to check".
 */
@Composable
private fun SystemPermissionRow(
    granted: Boolean,
    granted_: String,
    missing: String,
    action: String,
    onAct: () -> Unit,
) {
    Text(
        if (granted) granted_ else missing,
        style = MaterialTheme.typography.bodySmall,
        color = if (granted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        },
    )
    if (!granted) TextButton(onClick = onAct) { Text(action) }
}
