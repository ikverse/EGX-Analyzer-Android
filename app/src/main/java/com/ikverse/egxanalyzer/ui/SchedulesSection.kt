package com.ikverse.egxanalyzer.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.ikverse.egxanalyzer.data.JobScheduler
import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisAim
import com.ikverse.egxanalyzer.model.AnalysisSchedule
import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.ScheduleClock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * The one analysis this phone runs on its own, on the screen where runs are set up.
 *
 * A checkbox and a time, and that is the whole of it. What stood here was a sheet over a table of
 * jobs, each with a name, a kind of work and a choice of firing once, on chosen weekdays, or
 * repeatedly inside a window - roughly nine hundred lines of form for a feature whose two real
 * answers are "keep prices fresh while the market is open", which is now a checkbox of its own in
 * Settings that needs no configuration at all, and "read the chats before the open", which is
 * this.
 *
 * Inline rather than behind a sheet, because there is no longer enough here to be worth a surface
 * of its own: everything this feature can be asked fits under the card that reports on it. The
 * line that matters most is still the next one - the failure mode of every scheduler on this
 * platform is silence, and a card that names the next fire and the last outcome is the only way to
 * notice from the outside.
 */
@Composable
internal fun SchedulesSection(appState: AppState) {
    SectionCard(title = "Scheduled analysis", icon = Icons.Outlined.Schedule) {
        AnalysisScheduleControls(appState)
        Text(
            "Runs on this phone only. Never synced - the other devices would pay for the same " +
                "answer over again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The same schedule in Settings, with the two system permissions that decide whether it works.
 *
 * Both belong here rather than on the Analyze card: they are granted on system pages, they are
 * granted once, and neither is about this schedule in particular - the price refresh depends on
 * exactly the same two. Which is also why they are worth showing at all: an app the phone has put
 * to sleep keeps no time and reports nothing, and these lines are the only place that state is
 * visible.
 */
@Composable
internal fun SchedulesSettingsSection(appState: AppState, contentMaxWidth: Dp) {
    val schedule = appState.analysisSchedule
    ExpandableSection(
        "Scheduled analysis",
        icon = Icons.Outlined.Schedule,
        summary = nextRunLine(
            schedule = schedule,
            now = Instant.now(),
            paidAllowed = appState.paidSchedulesEnabled,
            hasCredential = appState.cloudConfiguration.hasCredential,
            knownChannelIds = appState.channels.mapTo(mutableSetOf()) { it.id },
        ),
        contentMaxWidth = contentMaxWidth,
    ) {
        AnalysisScheduleControls(appState)
        SystemPermissions()
    }
}

/**
 * Everything this schedule can be asked, in the order the reader has to answer it.
 *
 * On or off, then when, then what it covers - and the money switch last, because it is the one
 * decision that is not about this schedule at all.
 */
@Composable
private fun AnalysisScheduleControls(appState: AppState) {
    val context = LocalContext.current
    val schedule = appState.analysisSchedule
    val now = Instant.now()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = schedule.enabled,
            onCheckedChange = { appState.saveAnalysisSchedule(schedule.copy(enabled = it)) },
        )
        Text("Analyse the next session every trading day")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text("At ${ScheduleClock.clock(schedule.at)} Cairo time")
        OutlinedButton(
            onClick = {
                pickTime(context, schedule.at) {
                    appState.saveAnalysisSchedule(schedule.copy(at = it))
                }
            },
        ) { Text("Change") }
    }
    Text(
        "Sunday to Thursday, always for the session that has not happened yet. A run before the " +
            "open reads the messages posted overnight while the levels can still be acted on.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ReaimControl(appState, schedule)
    // The next fire is the line that says the schedule is alive. One that is off, unaimed or
    // blocked says that instead, because a time it will never reach is worse than no time at all.
    Text(
        nextRunLine(
            schedule = schedule,
            now = now,
            paidAllowed = appState.paidSchedulesEnabled,
            hasCredential = appState.cloudConfiguration.hasCredential,
            knownChannelIds = appState.channels.mapTo(mutableSetOf()) { it.id },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
    schedule.lastMessage?.let {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            StatusPill(schedule.lastOutcome.displayName, schedule.lastOutcome.tone())
            Text(
                lastRunLine(schedule, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // A switch rather than a checkbox, and deliberately last: letting the phone keep a time says
    // nothing about letting it spend money at that time, and arming the clock to spend later is
    // the same act as spending.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = appState.paidSchedulesEnabled,
            onCheckedChange = appState::updatePaidSchedulesEnabled,
        )
        Column(Modifier.padding(start = Space.m)) {
            Text("Allow runs that spend cloud credits")
            Text(
                "An analysis is a paid request. Off, a run that came due is passed over and says " +
                    "so rather than being made quietly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The frozen selection against what Analyze has ticked now, and a button naming which it takes.
 *
 * Freezing is right - a schedule must not change what it covers because someone re-ticked a chat
 * on another screen weeks later - but a selection that can never be corrected is one the user has
 * to delete the schedule to fix. So: both sides shown, and a button that says plainly which one it
 * is taking. Never automatic, and never quiet.
 */
@Composable
private fun ReaimControl(appState: AppState, schedule: AnalysisSchedule) {
    val fromScreen = appState.scheduledAnalysisFromScreen()
    Text(
        "Covers: ${coverageLine(schedule) ?: "nothing yet"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when {
        fromScreen == null -> Text(
            "Tick chats on the Analyze screen to point this at them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        fromScreen == schedule.aim -> Text(
            "Matches what Analyze has ticked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> {
            Text(
                "Analyze now has: ${aimLine(fromScreen) ?: "nothing"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    appState.saveAnalysisSchedule(
                        schedule.copy(
                            channels = fromScreen.channels,
                            contentTypes = fromScreen.contentTypes,
                        ),
                    )
                },
            ) {
                Text(
                    if (schedule.configured) {
                        "Re-aim at the current selection"
                    } else {
                        "Use the current selection"
                    },
                )
            }
        }
    }
}

/**
 * The two system permissions, said the same way whichever it is.
 *
 * The granted line is drawn too, not only the missing one: a page that goes quiet once something
 * is right leaves the reader unable to tell a granted permission from an app that forgot to check.
 */
@Composable
internal fun SystemPermissions() {
    val context = LocalContext.current
    SystemPermissionRow(
        // Read on every recomposition rather than remembered: the answer changes on a system page,
        // and this screen is still underneath when the user comes back from it.
        granted = JobScheduler(context).canScheduleExact(),
        grantedText = "Exact alarms are allowed, so runs keep to the minute.",
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
        grantedText = "Battery optimization is off for this app, so it will not be put to sleep.",
        missing = "Battery optimization can put this app to sleep, and a sleeping app keeps no " +
            "time at all. Samsung does this after a few days of not opening it.",
        action = "Open battery settings",
    ) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

@Composable
private fun SystemPermissionRow(
    granted: Boolean,
    grantedText: String,
    missing: String,
    action: String,
    onAct: () -> Unit,
) {
    Text(
        if (granted) grantedText else missing,
        style = MaterialTheme.typography.bodySmall,
        color = if (granted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        },
    )
    if (!granted) TextButton(onClick = onAct) { Text(action) }
}

/** Green for a run that worked, red for one that failed, grey for the rest. */
internal fun JobOutcome.tone(): StatusTone = when (this) {
    JobOutcome.SUCCEEDED -> StatusTone.GOOD
    JobOutcome.FAILED, JobOutcome.MISSED -> StatusTone.BAD
    JobOutcome.SKIPPED, JobOutcome.NEVER -> StatusTone.NEUTRAL
}

/**
 * What this schedule is waiting on, and it is not always a time.
 *
 * Everything above the last branch is a reason the next fire will not happen, and each is checked
 * here rather than at the fire, because a card promising "Next Sun 07:00" over a run that is going
 * to be passed over is the app misleading the one person who could fix it. A schedule that quietly
 * does nothing is this whole feature's failure mode; one that says why is not.
 */
internal fun nextRunLine(
    schedule: AnalysisSchedule,
    now: Instant,
    paidAllowed: Boolean = true,
    hasCredential: Boolean = true,
    knownChannelIds: Set<Long> = emptySet(),
): String = blockedReason(schedule, paidAllowed, hasCredential, knownChannelIds)
    ?: "Next ${whenLabel(ScheduleClock.nextFire(schedule.at, now), now)}"

/**
 * Why this schedule will not fire, or null when nothing is stopping it.
 *
 * One list, read by the card and by the Settings summary above it, because the two disagreeing is
 * how a summary ends up promising a next run for a schedule the card underneath says is blocked.
 * The order is what the reader has to fix first: the switch, then what it is aimed at, then what
 * the run itself needs. A line listing four problems at once fixes none of them.
 *
 * [knownChannelIds] is empty when the chat list has not loaded, which on a cold start it has not.
 * That is deliberately treated as no opinion rather than as the chats having gone - claiming a
 * schedule is broken because Telegram is still connecting would be exactly the wrong alarm.
 */
internal fun blockedReason(
    schedule: AnalysisSchedule,
    paidAllowed: Boolean = true,
    hasCredential: Boolean = true,
    knownChannelIds: Set<Long> = emptySet(),
): String? = when {
    !schedule.enabled -> "Switched off"
    !schedule.configured -> "No chats chosen yet"
    !paidAllowed -> "Paid runs are switched off"
    !hasCredential -> "No provider credential saved"
    schedule.chatsAreGone(knownChannelIds) -> "Its chats are no longer in the app"
    else -> null
}

/**
 * Whether every chat this schedule froze has since gone from the app.
 *
 * All of them, not some: losing one chat of four leaves a run that still reads the other three,
 * and a warning about it would be noise on a schedule that works. Losing all four leaves a paid
 * request with nothing to send.
 */
private fun AnalysisSchedule.chatsAreGone(knownChannelIds: Set<Long>): Boolean {
    if (knownChannelIds.isEmpty()) return false
    if (channels.isEmpty()) return false
    return channels.none { it.id in knownChannelIds }
}

/**
 * The chats this covers, short enough for a line.
 *
 * A schedule that names only what it does - "analyse the next session" - cannot be checked without
 * opening it, and the whole point of freezing the selection is that it stops matching what is
 * ticked on screen. Two names and a count is enough to recognise the wrong one at a glance.
 */
internal fun coverageLine(schedule: AnalysisSchedule, limit: Int = 2): String? =
    aimLine(schedule.aim, limit)

internal fun aimLine(aim: AnalysisAim, limit: Int = 2): String? {
    val names = aim.channels.map(AnalysedChannel::name)
    return when {
        names.isEmpty() -> null
        names.size <= limit -> names.joinToString(", ")
        else -> names.take(limit).joinToString(", ") + " +${names.size - limit} more"
    }
}

/** What became of the last fire, in the fewest words that still say which one it was. */
internal fun lastRunLine(
    schedule: AnalysisSchedule,
    now: Instant,
    zone: ZoneId = ScheduleClock.ZONE,
): String {
    val fired = schedule.lastFiredAt ?: return schedule.lastMessage.orEmpty()
    return listOfNotNull(whenLabel(fired, now, zone), schedule.lastMessage).joinToString(" · ")
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

/** The system time picker, 24-hour because everything else here reads times that way. */
private fun pickTime(context: Context, current: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        current.hour,
        current.minute,
        true,
    ).show()
}
