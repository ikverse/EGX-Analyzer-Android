package com.ikverse.egxanalyzer.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.ikverse.egxanalyzer.data.JobScheduler
import com.ikverse.egxanalyzer.model.JobTrigger
import com.ikverse.egxanalyzer.model.JobWork
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.ScheduledJob
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/**
 * Every schedule on this phone, and the form for one of them.
 *
 * A sheet rather than a screen because this app has no back stack - five destinations and modal
 * surfaces for everything else - and a sixth tab would give permanent navigation weight to
 * something set up once and then left alone. The editor takes the sheet over rather than opening a
 * second one on top: stacked sheets are awkward to dismiss and impossible to reason about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SchedulesSheet(appState: AppState, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<ScheduledJob?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            when (val job = editing) {
                null -> ScheduleList(appState, onEdit = { editing = it })
                else -> ScheduleEditor(appState, job) { editing = null }
            }
        }
    }
}

@Composable
private fun ScheduleList(appState: AppState, onEdit: (ScheduledJob) -> Unit) {
    val context = LocalContext.current
    Text("Scheduled runs", style = MaterialTheme.typography.headlineSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = appState.schedulesEnabled,
            onCheckedChange = appState::updateSchedulesEnabled,
        )
        Column(Modifier.padding(start = Space.m)) {
            Text("Run schedules on this phone")
            Text(
                "Off by default. Nothing here spends cloud credits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (appState.schedulesEnabled) ExactAlarmNotice(context)
    HorizontalDivider()
    val now = Instant.now()
    if (appState.scheduledJobs.isEmpty()) {
        Text(
            "Nothing scheduled yet. A schedule is a job this phone runs on its own, at a time " +
                "you choose, in Cairo time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    appState.scheduledJobs.forEach { job ->
        ScheduleRow(job, appState, now, onEdit)
        HorizontalDivider()
    }
    Button(
        onClick = { onEdit(newSchedule(appState.scheduledJobs)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(IconSize.Inline))
        Text("New schedule", Modifier.padding(start = Space.s))
    }
}

@Composable
private fun ScheduleRow(
    job: ScheduledJob,
    appState: AppState,
    now: Instant,
    onEdit: (ScheduledJob) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(job.name, fontWeight = FontWeight.SemiBold)
            Text(
                "${ScheduleClock.describe(job.trigger)} · ${job.work.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The next fire is the line that says the schedule is alive. A job that is off, spent,
            // or of a kind this build cannot run says that instead, because a time it will never
            // reach is worse than no time at all.
            Text(
                nextRunLine(job, appState.schedulesEnabled, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            job.lastMessage?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    modifier = Modifier.padding(top = Space.xs),
                ) {
                    StatusPill(job.lastOutcome.displayName, job.lastOutcome.tone())
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Switch(
            checked = job.enabled,
            // A job this build cannot run is left switchable so it can be paused, but it is never
            // going to fire either way.
            onCheckedChange = { appState.saveScheduledJob(job.copy(enabled = it)) },
        )
        TextButton(onClick = { onEdit(job) }) { Text("Edit") }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ScheduleEditor(appState: AppState, job: ScheduledJob, onDone: () -> Unit) {
    val context = LocalContext.current
    val existing = appState.scheduledJobs.any { it.id == job.id }
    var name by remember(job.id) { mutableStateOf(job.name) }
    var repeating by remember(job.id) { mutableStateOf(job.trigger !is JobTrigger.Once) }
    var days by remember(job.id) {
        mutableStateOf((job.trigger as? JobTrigger.Repeat)?.days ?: ScheduleClock.tradingDays)
    }
    var time by remember(job.id) {
        mutableStateOf(
            when (val trigger = job.trigger) {
                is JobTrigger.Repeat -> trigger.at
                is JobTrigger.Once -> trigger.at.toLocalTime()
            },
        )
    }
    var date by remember(job.id) {
        mutableStateOf(
            (job.trigger as? JobTrigger.Once)?.at?.toLocalDate()
                ?: LocalDate.now(ScheduleClock.ZONE).plusDays(1),
        )
    }
    var grace by remember(job.id) { mutableStateOf(job.graceMinutes) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDone) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to the list")
        }
        Text(
            if (existing) "Edit schedule" else "New schedule",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Text("Runs", fontWeight = FontWeight.SemiBold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = repeating, onClick = { repeating = true })
        Text("Repeating", Modifier.padding(end = Space.l))
        RadioButton(selected = !repeating, onClick = { repeating = false })
        Text("Once")
    }
    if (repeating) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            ScheduleClock.weekOrder.forEach { day ->
                FilterChip(
                    selected = day in days,
                    onClick = {
                        days = if (day in days) days - day else days + day
                    },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) },
                )
            }
        }
        TextButton(onClick = { days = ScheduleClock.tradingDays }) { Text("Trading days") }
    } else {
        OutlinedButton(onClick = { pickDate(context, date) { date = it } }) {
            Text("Date: $date")
        }
    }
    OutlinedButton(onClick = { pickTime(context, time) { time = it } }) {
        Text("Time: ${ScheduleClock.clock(time)} Cairo")
    }
    Text(
        "Cairo time, always. A schedule belongs to the exchange rather than to wherever the " +
            "phone is.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text("Does", fontWeight = FontWeight.SemiBold)
    Text(job.work.displayName)
    Text(
        when (job.work) {
            JobWork.PriceRefresh ->
                "Fetches the daily prices for every stock the record names or you hold, and " +
                    "re-scores what changed. Free: no cloud provider is involved."

            is JobWork.Unsupported ->
                "Written by a newer version of the app. It is kept and will not be run here."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text("Run late by up to", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        graceChoices.forEach { (minutes, label) ->
            FilterChip(
                selected = grace == minutes,
                onClick = { grace = minutes },
                label = { Text(label) },
            )
        }
    }
    Text(
        "A phone that was off, asleep or out of signal at the appointed minute is the normal " +
            "case. Past this the run is recorded as missed rather than started late.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val trigger = if (repeating) {
        JobTrigger.Repeat(days, time)
    } else {
        JobTrigger.Once(LocalDateTime.of(date, time))
    }
    val problem = triggerProblem(trigger, name)
    problem?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Button(
            onClick = {
                appState.saveScheduledJob(
                    job.copy(
                        name = name.trim(),
                        trigger = trigger,
                        graceMinutes = grace,
                        // A trigger that moved is a different slot, so whatever the old one served
                        // must not talk the new one out of its first run - and the new one is armed
                        // from now, so a time already past today is next week rather than instantly.
                        lastFiredAt = if (trigger == job.trigger) job.lastFiredAt else null,
                        armedAt = if (trigger == job.trigger) job.armedAt else Instant.now(),
                    ),
                )
                onDone()
            },
            enabled = problem == null,
        ) { Text("Save") }
        TextButton(onClick = onDone) { Text("Cancel") }
        if (existing) {
            TextButton(
                onClick = {
                    appState.deleteScheduledJob(job.id)
                    onDone()
                },
            ) { Text("Delete") }
        }
    }
}

/**
 * The exact-alarm permission, which from Android 14 is off until it is asked for.
 *
 * Worth a line on screen rather than a silent fallback: without it the alarm is inexact and can
 * arrive the better part of an hour late, which for an 18:00 job is the difference between working
 * and appearing not to.
 */
@Composable
private fun ExactAlarmNotice(context: Context) {
    val scheduler = remember { JobScheduler(context) }
    // Read on every recomposition rather than remembered: the answer changes in system settings,
    // and the sheet is still on screen underneath when the user comes back.
    if (scheduler.canScheduleExact()) return
    Text(
        "Exact alarms are off, so a run can arrive up to an hour late. Turning them on is what " +
            "makes a schedule keep to the minute.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    TextButton(
        onClick = {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        },
    ) { Text("Allow exact alarms") }
}

private val graceChoices = listOf(15 to "15 min", 120 to "2 hours", 720 to "12 hours")

/** What stops this schedule being saved, or null when nothing does. */
internal fun triggerProblem(
    trigger: JobTrigger,
    name: String,
    now: LocalDateTime = LocalDateTime.now(ScheduleClock.ZONE),
): String? = when {
    name.isBlank() -> "Give the schedule a name."
    trigger is JobTrigger.Repeat && trigger.days.isEmpty() -> "Choose at least one day."
    trigger is JobTrigger.Once && !trigger.at.isAfter(now) ->
        "That moment has passed. Choose a date and time still ahead."

    else -> null
}

/** The line under a schedule saying when it next runs, or why it never will. */
internal fun nextRunLine(job: ScheduledJob, schedulesEnabled: Boolean, now: Instant): String = when {
    !job.runnable -> "This version cannot run it"
    !schedulesEnabled -> "Schedules are off on this phone"
    !job.enabled -> "Switched off"
    job.spent -> "Done - it only ran once"
    else -> ScheduleClock.nextFire(job, now)
        ?.let { "Next ${whenLabel(it, now)}" }
        ?: "No run left"
}

/**
 * A new schedule, already filled in with the one most people want.
 *
 * 18:00 on trading days: the exchange closes at 14:30 Cairo, so an evening fetch is the first one
 * that reads a settled session, and it leaves the record right before the phone is next opened.
 */
private fun newSchedule(existing: List<ScheduledJob>): ScheduledJob = ScheduledJob(
    id = UUID.randomUUID().toString(),
    name = if (existing.isEmpty()) "Evening prices" else "New schedule",
    enabled = true,
    trigger = JobTrigger.Repeat(ScheduleClock.tradingDays, LocalTime.of(18, 0)),
    work = JobWork.PriceRefresh,
)

private fun pickTime(context: Context, current: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        current.hour,
        current.minute,
        // 24-hour, matching how every time in this feature is written down.
        true,
    ).show()
}

private fun pickDate(context: Context, current: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        current.year,
        current.monthValue - 1,
        current.dayOfMonth,
    ).apply {
        // Nothing before today: a one-shot in the past is a schedule that can never fire, and the
        // form would have to refuse it a moment later anyway.
        datePicker.minDate = LocalDate.now(ScheduleClock.ZONE)
            .atStartOfDay(ScheduleClock.ZONE)
            .toInstant()
            .toEpochMilli()
    }.show()
}

