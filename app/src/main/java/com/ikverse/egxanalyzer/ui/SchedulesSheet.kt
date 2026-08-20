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
import com.ikverse.egxanalyzer.model.AnalysedChannel
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
internal fun SchedulesSheet(
    appState: AppState,
    /** A job to open straight into, for the entrances that already know what they are scheduling. */
    initialEdit: ScheduledJob? = null,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf(initialEdit) }
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
                "Off by default, and never synced to your other devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // A second switch rather than a property of the job, and deliberately sitting under the first:
    // letting the phone fetch prices while it sleeps says nothing about letting it spend money.
    if (appState.schedulesEnabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = appState.paidSchedulesEnabled,
                onCheckedChange = appState::updatePaidSchedulesEnabled,
            )
            Column(Modifier.padding(start = Space.m)) {
                Text("Allow runs that spend cloud credits")
                Text(
                    "An analysis is a paid request. Off, a schedule that would make one is passed " +
                        "over and says so.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            coverageLine(job.work)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The next fire is the line that says the schedule is alive. A job that is off, spent,
            // blocked, or of a kind this build cannot run says that instead, because a time it will
            // never reach is worse than no time at all.
            Text(
                nextRunLine(
                    job = job,
                    schedulesEnabled = appState.schedulesEnabled,
                    now = now,
                    paidAllowed = appState.paidSchedulesEnabled,
                    hasCredential = appState.cloudConfiguration.hasCredential,
                    knownChannelIds = appState.channels.mapTo(mutableSetOf()) { it.id },
                ),
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
    var work by remember(job.id) { mutableStateOf(job.work) }

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
    // Fixed once a job exists. Changing what a schedule does is a different schedule, and quietly
    // turning a free job into a paid one from an edit screen is the last way anyone should be able
    // to arm a cloud request.
    if (existing) {
        Text(work.displayName)
        (work as? JobWork.Analysis)?.let { frozen ->
            ReaimControl(appState, frozen) { updated -> work = updated }
        }
    } else {
        val fromScreen = remember { appState.scheduledAnalysisFromScreen() }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            FilterChip(
                selected = work is JobWork.PriceRefresh,
                onClick = {
                    work = JobWork.PriceRefresh
                    if (name in defaultNames) name = defaultPriceName
                    if (time == analysisHour) time = priceHour
                },
                label = { Text(JobWork.PriceRefresh.displayName) },
            )
            FilterChip(
                // Nothing to schedule where the Analyze screen has no chats ticked: the selection
                // is what the job would be made of.
                enabled = fromScreen != null,
                selected = work is JobWork.Analysis,
                onClick = {
                    fromScreen?.let { work = it }
                    // Only while the name and time are still the ones this form put there. What
                    // the user typed or picked is theirs; a card reading "Evening prices" over an
                    // analysis, or an analysis booked for hours after the session it was meant to
                    // precede, is worse than either.
                    if (name in defaultNames) name = defaultAnalysisName
                    if (time == priceHour) time = analysisHour
                },
                label = { Text("Analyse the next session") },
            )
        }
        if (fromScreen == null) {
            Text(
                "Tick the chats you want on the Analyze screen first, and they become what the " +
                    "schedule covers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Text(
        when (val chosen = work) {
            JobWork.PriceRefresh ->
                "Fetches the daily prices for every stock the record names or you hold, and " +
                    "re-scores what changed. Free: no cloud provider is involved."

            is JobWork.Analysis ->
                "Reads ${chosen.channels.size} " +
                    "${if (chosen.channels.size == 1) "chat" else "chats"} " +
                    "(${chosen.contentTypes.joinToString { it.name.lowercase() }}) for the next " +
                    "EGX session and sends them to the model. This costs cloud credits, and the " +
                    "chats are fixed as they are now - changing what is ticked on Analyze later " +
                    "will not re-aim it."

            is JobWork.Unsupported ->
                "Written by a newer version of the app. It is kept and will not be run here."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (work.spendsCredits && !appState.paidSchedulesEnabled) {
        Text(
            "Paid schedules are switched off, so this one will be passed over and say so rather " +
                "than run. The switch is at the top of this sheet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

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
                        work = work,
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
 * The one deliberate way past a frozen selection.
 *
 * Freezing is right - a schedule must not change what it covers because someone re-ticked a chat on
 * another screen weeks later - but a selection that can never be corrected is one the user has to
 * delete the job to fix. So: both sides shown, and a button that says plainly which one it is
 * taking. Never automatic, and never quiet.
 */
@Composable
private fun ReaimControl(
    appState: AppState,
    frozen: JobWork.Analysis,
    onReaim: (JobWork.Analysis) -> Unit,
) {
    val fromScreen = appState.scheduledAnalysisFromScreen()
    Text(
        "Covers: ${coverageLine(frozen) ?: "nothing"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when {
        fromScreen == null -> Text(
            "Tick chats on the Analyze screen to be able to re-aim this at them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        fromScreen == frozen -> Text(
            "Matches what Analyze has ticked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> {
            Text(
                "Analyze now has: ${coverageLine(fromScreen) ?: "nothing"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { onReaim(fromScreen) }) {
                Text("Re-aim at the current selection")
            }
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

/**
 * What a schedule is waiting on, and it is not always a time.
 *
 * Everything above the last branch is a reason the next fire will not happen, and each one is
 * checked here rather than at the fire, because a card promising "Next Sun 07:00" over a job that
 * is going to be passed over is the app misleading the one person who could fix it. A schedule that
 * quietly does nothing is this feature's whole failure mode; a schedule that says why is not.
 *
 * [knownChannelIds] is empty when the chat list has not loaded, which on a cold start it has not.
 * That is deliberately treated as "no opinion" rather than as the chats having gone - claiming a
 * job is broken because Telegram is still connecting would be exactly the wrong alarm.
 */
internal fun nextRunLine(
    job: ScheduledJob,
    schedulesEnabled: Boolean,
    now: Instant,
    paidAllowed: Boolean = true,
    hasCredential: Boolean = true,
    knownChannelIds: Set<Long> = emptySet(),
): String = blockedReason(job, schedulesEnabled, paidAllowed, hasCredential, knownChannelIds)
    ?: ScheduleClock.nextFire(job, now)
        ?.let { "Next ${whenLabel(it, now)}" }
    ?: "No run left"

/**
 * Why this schedule will not fire, or null when nothing is stopping it.
 *
 * One list, read by the row and by the card above it, because the two disagreeing is how the card
 * ends up promising a next run for a job the row underneath says is blocked. The order is what the
 * reader has to fix first: the master switch before the job's own, and both before anything the
 * work itself needs. A card listing four problems at once fixes none of them.
 */
internal fun blockedReason(
    job: ScheduledJob,
    schedulesEnabled: Boolean,
    paidAllowed: Boolean = true,
    hasCredential: Boolean = true,
    knownChannelIds: Set<Long> = emptySet(),
): String? = when {
    !job.runnable -> "This version cannot run it"
    !schedulesEnabled -> "Schedules are off on this phone"
    !job.enabled -> "Switched off"
    job.spent -> "Done - it only ran once"
    job.work.spendsCredits && !paidAllowed -> "Paid runs are switched off"
    job.work.spendsCredits && !hasCredential -> "No provider credential saved"
    job.chatsAreGone(knownChannelIds) -> "Its chats are no longer in the app"
    else -> null
}

/**
 * Whether every chat this job froze has since gone from the app.
 *
 * All of them, not some: losing one chat of four leaves a run that still reads the other three, and
 * a warning about it would be noise on a schedule that works. Losing all four leaves a paid request
 * with nothing to send.
 */
private fun ScheduledJob.chatsAreGone(knownChannelIds: Set<Long>): Boolean {
    if (knownChannelIds.isEmpty()) return false
    val analysis = work as? JobWork.Analysis ?: return false
    return analysis.channels.none { it.id in knownChannelIds }
}

/**
 * The chats a job covers, short enough for a row.
 *
 * A schedule that names only what it does - "Analyse the next session" - cannot be checked without
 * opening it, and the whole point of freezing the selection is that it stops matching what is
 * ticked on screen. Two names and a count is enough to recognise the wrong job at a glance.
 */
internal fun coverageLine(work: JobWork, limit: Int = 2): String? {
    val analysis = work as? JobWork.Analysis ?: return null
    val names = analysis.channels.map(AnalysedChannel::name)
    return when {
        names.isEmpty() -> null
        names.size <= limit -> names.joinToString(", ")
        else -> names.take(limit).joinToString(", ") + " +${names.size - limit} more"
    }
}

/**
 * A new schedule, already filled in with the one most people want.
 *
 * 18:00 on trading days: the exchange closes at 14:30 Cairo, so an evening fetch is the first one
 * that reads a settled session, and it leaves the record right before the phone is next opened.
 */
private fun newSchedule(existing: List<ScheduledJob>): ScheduledJob = ScheduledJob(
    id = UUID.randomUUID().toString(),
    name = if (existing.isEmpty()) defaultPriceName else "New schedule",
    enabled = true,
    trigger = JobTrigger.Repeat(ScheduleClock.tradingDays, priceHour),
    work = JobWork.PriceRefresh,
)

/**
 * A schedule for the selection the Analyze screen is holding, ready to be given a time.
 *
 * The short way in: tick the chats you want, press one button, pick when. Going through the sheet's
 * own New schedule and finding the right chip is the long way round for the thing most people will
 * be doing.
 */
internal fun newAnalysisSchedule(work: JobWork.Analysis): ScheduledJob = ScheduledJob(
    id = UUID.randomUUID().toString(),
    name = defaultAnalysisName,
    enabled = true,
    trigger = JobTrigger.Repeat(ScheduleClock.tradingDays, analysisHour),
    work = work,
)

private const val defaultPriceName = "Evening prices"
private const val defaultAnalysisName = "Morning analysis"

/** The names this form fills in itself, and so may replace when the work changes. */
private val defaultNames = setOf(defaultPriceName, defaultAnalysisName, "New schedule")

/**
 * After the close, when the day's prices have settled and there is something new to fetch.
 *
 * The exchange shuts at 14:30 Cairo, so an evening refresh is the first one that reads a finished
 * session - and it leaves the record right for whenever the phone is next opened.
 */
private val priceHour = LocalTime.of(18, 0)

/**
 * Before the open, which is the only time an analysis is worth having.
 *
 * The exchange opens at 10:00 Cairo and the channels post their calls in the morning ahead of it.
 * A run at eight reads the night's messages and has the report ready while the levels can still be
 * acted on; the same run at six in the evening is a post-mortem.
 */
private val analysisHour = LocalTime.of(8, 0)

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

