package com.ikverse.egxanalyzer.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.data.JobScheduler
import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisAim
import com.ikverse.egxanalyzer.model.AnalysisSchedule
import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.ScheduleClock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * What this phone has booked, on the screen where runs are started by hand.
 *
 * One line and a button, and deliberately nothing else. Every control used to be drawn twice -
 * here in full and again in Settings - which is two places to edit one thing and, worse, two
 * places that could disagree about it. Analyze is where a run is aimed, so what it owes the reader
 * is the answer to "is anything going to happen without me": the count, the next fire, and the way
 * to the controls. Settings owns the rest.
 */
@Composable
internal fun SchedulesSection(appState: AppState) {
    SectionCard(title = "Scheduled analysis", icon = Icons.Outlined.Schedule) {
        val summary = schedulesSummary(
            schedules = appState.analysisSchedules,
            now = Instant.now(),
            paidAllowed = appState.paidSchedulesEnabled,
            hasCredential = appState.cloudConfiguration.hasCredential,
            knownChannelIds = appState.channels.mapTo(mutableSetOf()) { it.id },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                summary.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (summary.warning) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = appState::editSchedules) {
                Text(if (appState.analysisSchedules.isEmpty()) "Set one up" else "Manage")
            }
        }
    }
}

/**
 * The schedules themselves, and the two system permissions that decide whether any of them works.
 *
 * The permissions belong here rather than beside the rows: they are granted on system pages, they
 * are granted once, and neither is about a particular schedule - the price refresh depends on
 * exactly the same two. Which is also why they are worth drawing at all. An app the phone has put
 * to sleep keeps no time and reports nothing, and these lines are the only place that shows.
 */
@Composable
internal fun SchedulesSettingsSection(appState: AppState, contentMaxWidth: Dp) {
    val schedules = appState.analysisSchedules
    val now = Instant.now()
    val knownChannelIds = appState.channels.mapTo(mutableSetOf()) { it.id }
    var expanded by remember { mutableStateOf(false) }
    // Opened when the summary on Analyze sent the reader here. Cleared as it is taken, so coming
    // back to Settings later finds this closed like every other group.
    LaunchedEffect(appState.openScheduleSettings) {
        if (appState.openScheduleSettings) {
            expanded = true
            appState.openScheduleSettings = false
        }
    }
    ExpandableSection(
        "Scheduled analysis",
        icon = Icons.Outlined.Schedule,
        summary = schedulesSummary(
            schedules = schedules,
            now = now,
            paidAllowed = appState.paidSchedulesEnabled,
            hasCredential = appState.cloudConfiguration.hasCredential,
            knownChannelIds = knownChannelIds,
        ).text,
        contentMaxWidth = contentMaxWidth,
        expandedState = expanded,
        onExpandedChange = { expanded = it },
    ) {
        // Said once, above every row, because it is one condition and not four. Repeating "paid
        // runs are switched off" on each schedule would be the same sentence four times, and the
        // switch that answers it is a few lines below.
        sharedBlocker(
            paidAllowed = appState.paidSchedulesEnabled,
            hasCredential = appState.cloudConfiguration.hasCredential,
        )?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (schedules.isEmpty()) {
            Text(
                "Nothing runs on its own yet. A schedule reads the chats ticked on Analyze at a " +
                    "time you choose and has the report saved before you open the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        schedules.forEach { schedule ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ScheduleRow(appState, schedule, now, knownChannelIds)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (schedules.size < AnalysisSchedule.MAX) {
            TextButton(onClick = appState::addAnalysisSchedule) {
                Icon(Icons.Outlined.Add, contentDescription = null, Modifier.padding(end = Space.s))
                Text("Add a schedule")
            }
        } else {
            Text(
                "Four is the most this phone keeps. Each one that fires is a paid request, so " +
                    "the cap is also what the most expensive day can cost.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A switch rather than a checkbox, and deliberately last: letting the phone keep a time
        // says nothing about letting it spend money at that time, and arming the clock to spend
        // later is the same act as spending.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = appState.paidSchedulesEnabled,
                onCheckedChange = appState::updatePaidSchedulesEnabled,
            )
            Column(Modifier.padding(start = Space.m)) {
                Text("Allow runs that spend cloud credits")
                Text(
                    "Off, a run that came due is passed over and says so rather than being made " +
                        "quietly. Kept on this phone only and never synced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SystemPermissions()
    }
}

/**
 * One schedule, in as few lines as can still be checked at a glance.
 *
 * The switch, the time and the days are one block because they are the whole of what can be set;
 * everything under them is the app reporting back. The order is deliberate - what it will do, then
 * what it covers and when it next runs, then what became of the last one - so a reader who takes
 * only the first line has still read the part that decides whether anything happens.
 */
@Composable
private fun ScheduleRow(
    appState: AppState,
    schedule: AnalysisSchedule,
    now: Instant,
    knownChannelIds: Set<Long>,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = schedule.enabled,
                onCheckedChange = { appState.saveAnalysisSchedule(schedule.copy(enabled = it)) },
            )
            Spacer(Modifier.width(Space.s))
            // The time is the heading of the row, so it is set by pressing the time itself. The
            // "Change" button that used to sit beside it was a second control for one number.
            TextButton(
                onClick = {
                    pickTime(context, schedule.at) {
                        appState.saveAnalysisSchedule(schedule.copy(at = it))
                    }
                },
                contentPadding = PaddingValues(horizontal = Space.s),
            ) {
                Text(ScheduleClock.clock(schedule.at), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { appState.deleteAnalysisSchedule(schedule.id) }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete the ${ScheduleClock.clock(schedule.at)} schedule",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // A line of their own rather than the end of the row above. Five chips beside a switch, a
        // time and a delete is about 340dp of row, which overflows the cover panel - and a week
        // with its last two days clipped off is worse than a week on its own line.
        DayChips(schedule) { appState.saveAnalysisSchedule(schedule.copy(days = it)) }
        // What is wrong with this schedule takes the place of what it would do, rather than
        // sitting under it: a row promising "next today 07:00" over a run that cannot happen is
        // the app misleading the one person who could fix it.
        val blocker = scheduleBlocker(schedule, knownChannelIds)
        Text(
            blocker ?: scheduleDetail(schedule, now),
            style = MaterialTheme.typography.bodySmall,
            color = if (blocker != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
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
        ReaimControl(appState, schedule)
    }
}

/**
 * The week, one letter pair each, starting on Sunday because the exchange's does.
 *
 * Chips rather than a list of named checkboxes, because seven names down a page is the shape of
 * form this feature was rebuilt to get away from - and because the pattern of what is filled is
 * readable at a glance in a way seven ticked boxes are not.
 *
 * The weekend is offered and never default. A run on a Friday is aimed at the Sunday session and
 * reads from the Thursday before it, so it is the schedule that picks up what the chats posted
 * over the weekend - worth having on some weeks, worth nobody's money by default.
 */
@Composable
private fun DayChips(schedule: AnalysisSchedule, onChange: (Set<DayOfWeek>) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        DayOfWeek.entries.sortedBy { it.value % 7 }.forEach { day ->
            val on = day in schedule.days
            DayChip(dayChipLabel(day), on) {
                onChange(if (on) schedule.days - day else schedule.days + day)
            }
        }
    }
}

@Composable
private fun DayChip(text: String, selected: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        // Filled where it is on and a hairline where it is not, so the week reads as a pattern
        // before a single letter of it is.
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = DayChipSize, minHeight = DayChipSize)
                .padding(vertical = Space.s),
        )
    }
}

/** Wide enough for two letters and tall enough to press without aiming. */
private val DayChipSize: Dp = 32.dp

/**
 * The frozen aim against what Analyze has ticked now, offered as one button.
 *
 * Freezing is right - a schedule must not change what it covers because someone re-ticked a chat
 * on another screen weeks later - but a selection that can never be corrected is one the user has
 * to delete the schedule to fix. What it covers is already on the line above, so this only has to
 * name the other side and take it. Never automatic and never quiet: nothing happens here without
 * the button being pressed.
 */
@Composable
private fun ReaimControl(appState: AppState, schedule: AnalysisSchedule) {
    val fromScreen = appState.scheduledAnalysisFromScreen() ?: return
    if (fromScreen == schedule.aim) return
    TextButton(
        onClick = {
            appState.saveAnalysisSchedule(
                schedule.copy(
                    channels = fromScreen.channels,
                    contentTypes = fromScreen.contentTypes,
                ),
            )
        },
        contentPadding = PaddingValues(horizontal = Space.s),
    ) {
        Text(
            if (schedule.configured) {
                "Re-aim at ${aimLine(fromScreen)}"
            } else {
                "Aim at ${aimLine(fromScreen)}"
            },
        )
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

/** A line about the whole list, and whether it is the kind that should be read in red. */
internal data class SchedulesSummary(val text: String, val warning: Boolean = false)

/**
 * What this phone has booked, in one line, for the card on Analyze and the closed one in Settings.
 *
 * Both read it from here rather than each building their own, because the two disagreeing is
 * exactly how a summary ends up promising a next run over schedules that are all blocked.
 *
 * The shared blockers - the money switch and the credential - are named before any count, since
 * neither is about a particular schedule and both stop all four. Past those it is how many are on,
 * the next fire any of them will actually reach, and how many are blocked, which is the whole of
 * what a glance is owed.
 */
internal fun schedulesSummary(
    schedules: List<AnalysisSchedule>,
    now: Instant,
    paidAllowed: Boolean = true,
    hasCredential: Boolean = true,
    knownChannelIds: Set<Long> = emptySet(),
): SchedulesSummary {
    if (schedules.isEmpty()) return SchedulesSummary("Nothing scheduled")
    val on = schedules.filter { it.enabled }
    if (on.isEmpty()) return SchedulesSummary("${schedules.size} scheduled, all switched off")
    sharedBlocker(paidAllowed, hasCredential)?.let { return SchedulesSummary(it, warning = true) }
    val runnable = on.filter { scheduleBlocker(it, knownChannelIds) == null }
    val next = ScheduleClock.nextFireOf(runnable, now)
        // Every one of them is blocked, so the reason the first hit is more use than a count of
        // schedules that are going to do nothing.
        ?: return SchedulesSummary(
            scheduleBlocker(on.first(), knownChannelIds) ?: "Nothing scheduled",
            warning = true,
        )
    val blocked = on.size - runnable.size
    return SchedulesSummary(
        listOfNotNull(
            "${runnable.size} on",
            "next ${whenLabel(next, now)}",
            if (blocked > 0) "$blocked blocked" else null,
        ).joinToString(" · "),
        warning = blocked > 0,
    )
}

/**
 * What this schedule will do, once nothing is stopping it.
 *
 * The chats, the days and the next fire, in that order: the coverage is what tells a reader which
 * of four rows they are looking at, and the whole point of freezing a selection is that it stops
 * matching what is ticked on screen.
 */
internal fun scheduleDetail(
    schedule: AnalysisSchedule,
    now: Instant,
    zone: ZoneId = ScheduleClock.ZONE,
): String = listOfNotNull(
    coverageLine(schedule),
    daysLabel(schedule.days),
    ScheduleClock.nextFire(schedule.at, schedule.days, now, zone)
        ?.let { "next ${whenLabel(it, now, zone)}" },
).joinToString(" · ")

/**
 * Why this schedule will not fire, or null when nothing is stopping it.
 *
 * The order is what the reader has to fix first: the switch, then what it is aimed at, then what
 * the run itself needs. A line listing four problems at once fixes none of them.
 */
internal fun blockedReason(
    schedule: AnalysisSchedule,
    paidAllowed: Boolean = true,
    hasCredential: Boolean = true,
    knownChannelIds: Set<Long> = emptySet(),
): String? = when {
    !schedule.enabled -> "Switched off"
    schedule.days.isEmpty() -> NO_DAYS
    !schedule.configured -> "No chats chosen yet"
    !paidAllowed -> PAID_OFF
    !hasCredential -> NO_CREDENTIAL
    else -> scheduleBlocker(schedule, knownChannelIds)
}

/**
 * The reasons that belong to one schedule, which are the ones its own row can fix.
 *
 * Split from the two shared ones so that a list of four does not say "paid runs are switched off"
 * four times over the switch that turns them on. Same order and the same words; only where they
 * are drawn differs.
 */
internal fun scheduleBlocker(
    schedule: AnalysisSchedule,
    knownChannelIds: Set<Long> = emptySet(),
): String? = when {
    !schedule.enabled -> "Switched off"
    schedule.days.isEmpty() -> NO_DAYS
    !schedule.configured -> "No chats chosen yet"
    schedule.chatsAreGone(knownChannelIds) -> "Its chats are no longer in the app"
    else -> null
}

/** The two reasons that stop every schedule at once, said once above all of them. */
internal fun sharedBlocker(paidAllowed: Boolean, hasCredential: Boolean): String? = when {
    !paidAllowed -> PAID_OFF
    !hasCredential -> NO_CREDENTIAL
    else -> null
}

private const val NO_DAYS = "No days chosen"
private const val PAID_OFF = "Paid runs are switched off"
private const val NO_CREDENTIAL = "No provider credential saved"

/**
 * Whether every chat this schedule froze has since gone from the app.
 *
 * All of them, not some: losing one chat of four leaves a run that still reads the other three,
 * and a warning about it would be noise on a schedule that works. Losing all four leaves a paid
 * request with nothing to send.
 *
 * [knownChannelIds] is empty when the chat list has not loaded, which on a cold start it has not.
 * That is deliberately treated as no opinion rather than as the chats having gone - claiming a
 * schedule is broken because Telegram is still connecting would be exactly the wrong alarm.
 */
private fun AnalysisSchedule.chatsAreGone(knownChannelIds: Set<Long>): Boolean {
    if (knownChannelIds.isEmpty()) return false
    if (channels.isEmpty()) return false
    return channels.none { it.id in knownChannelIds }
}

/**
 * The days a schedule keeps, in the fewest words that still say which.
 *
 * The two whole weeks are named rather than listed: five or seven short names is longer to read
 * and harder to recognise than the one phrase that means all of them. "Every trading day" is the
 * one a schedule starts on, so it is worth naming even though it is a subset - a reader who sees
 * it knows at once that the weekend is not being paid for.
 */
internal fun daysLabel(days: Set<DayOfWeek>): String {
    val on = days.sortedBy { it.value % 7 }
    return when {
        on.isEmpty() -> NO_DAYS
        on.size == DayOfWeek.entries.size -> "every day"
        days == ScheduleClock.tradingDays -> "every trading day"
        else -> on.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    }
}

/** Two letters, which is the width a day is worth on a row that holds five of them. */
private fun dayChipLabel(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2)

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
