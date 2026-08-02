package com.ikverse.egxanalyzer.ui

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ikverse.egxanalyzer.data.AnalysisNotifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun AnalyzeScreen(activity: Activity, appState: AppState) {
    var textSource by remember { mutableStateOf("") }
    var channelMenuOpen by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val images = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { persistReadPermission(activity, it) }
        appState.addImages(uris) { activity.contentResolver.getType(it) }
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistReadPermission(activity, uri)
            appState.addVoice(uri, activity.contentResolver.getType(uri))
        }
    }
    // Android 13 and later start with notifications denied, and the app never asked. Everything
    // was built - channel, foreground service, deep link - and none of it could reach the screen,
    // which looked exactly like a broken notification rather than a missing permission.
    var notificationsAllowed by remember { mutableStateOf(AnalysisNotifier(activity).permitted()) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        // Granting happens in system settings, so the answer arrives on the way back in.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = AnalysisNotifier(activity).permitted()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = granted
        // The run starts either way. A notification is how you watch a run, not a condition for one.
        appState.startAnalysis()
    }
    // Computed here rather than inside the content, because the floating action needs it too.
    val blockedReason = analyzeBlockedReason(appState)
    Screen(
        title = "Analyze",
        subtitle = "Send selected text, images, and voice-message content to " +
            appState.cloudConfiguration.provider.displayName + ".",
        floatingAction = {
            if (appState.analysisStatus == AnalysisStatus.RUNNING) {
                ExtendedFloatingActionButton(
                    onClick = { scope.launch { appState.cancelAnalysis() } },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    icon = { Icon(Icons.Outlined.Cancel, contentDescription = null) },
                    text = { Text("Cancel analysis") },
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = {
                        // Asked here rather than at first launch: a permission prompt before the
                        // app has done anything is the one people decline, and Android never asks
                        // twice.
                        if (blockedReason != null) return@ExtendedFloatingActionButton
                        if (notificationsAllowed) {
                            appState.startAnalysis()
                        } else {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    containerColor = if (blockedReason == null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (blockedReason == null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    icon = { Icon(Icons.Outlined.AutoGraph, contentDescription = null) },
                    text = { Text("Analyze") },
                )
            }
        },
    ) {
        // Chat selection leads, because choosing sources is the first step of a run - and on a
        // wide screen it sits beside the settings that shape it rather than above them, so both
        // are visible while either is being changed.
        AdaptivePanes(
            main = {
                ChannelsSection(appState)
                SourcePreview(appState, scope)
                blockedReason?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            },
            side = {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.Inline),
                    )
                    Spacer(Modifier.width(Space.s))
                    Text("Content types", fontWeight = FontWeight.Bold)
                }
                // Three checkboxes stacked is a phone layout; anywhere wider it is three rows of
                // empty space.
                AdaptiveInline(minWidth = 340.dp) { horizontal ->
                    if (horizontal) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                            ContentTypeToggle("Text", AnalysisContentType.TEXT, appState)
                            ContentTypeToggle("Images", AnalysisContentType.IMAGES, appState)
                            ContentTypeToggle("Voice", AnalysisContentType.AUDIO, appState)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            ContentTypeToggle("Text messages", AnalysisContentType.TEXT, appState)
                            ContentTypeToggle("Images / photos", AnalysisContentType.IMAGES, appState)
                            ContentTypeToggle("Voice messages", AnalysisContentType.AUDIO, appState)
                        }
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Recommendation target date", fontWeight = FontWeight.Bold)
                }
                RecommendationDateOption(
                    selected = appState.analysisMode == AnalysisMode.NEXT_DAY,
                    title = "Current / next EGX session",
                    detail = appState.recommendationTargetDate.toString(),
                    onClick = { appState.selectAnalysisMode(AnalysisMode.NEXT_DAY) },
                )
                RecommendationDateOption(
                    selected = appState.analysisMode == AnalysisMode.SPECIFIC_DATE,
                    title = "Specific date",
                    detail = if (appState.analysisMode == AnalysisMode.SPECIFIC_DATE) {
                        appState.recommendationTargetDate.toString()
                    } else {
                        "Choose today or an earlier date"
                    },
                    onClick = {
                        appState.selectAnalysisMode(AnalysisMode.SPECIFIC_DATE)
                        showRecommendationDatePicker(activity, appState)
                    },
                )
                if (appState.analysisMode == AnalysisMode.SPECIFIC_DATE) {
                    OutlinedButton(
                        onClick = { showRecommendationDatePicker(activity, appState) },
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Change date")
                    }
                }
            }
        }
        ExpandableSection("Advanced selection", Icons.Outlined.Tune) {
            // Only ever labelled hand-added sources; the chat list above already says which
            // channels a run reads from.
        Box {
                val activeChannel = appState.channels.firstOrNull {
                    it.id == appState.activeSourceChannelId
                }
                TextButton(onClick = { channelMenuOpen = true }) {
                    Text("Source channel: ${activeChannel?.name ?: "On-device import"}")
                }
                DropdownMenu(
                    expanded = channelMenuOpen,
                    onDismissRequest = { channelMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("On-device import") },
                        onClick = {
                            appState.selectSourceChannel(null)
                            channelMenuOpen = false
                        },
                    )
                    appState.channels.filter(ChannelSelection::selected).forEach { channel ->
                        DropdownMenuItem(
                            text = { Text(channel.name) },
                            onClick = {
                                appState.selectSourceChannel(channel.id)
                                channelMenuOpen = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = textSource,
                onValueChange = { textSource = it },
                label = { Text("Message text") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Row(
                modifier = Modifier.fillMaxWidth().scrollableRow(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Button(onClick = {
                    appState.addText(textSource)
                    textSource = ""
                }) {
                    Icon(Icons.Outlined.TextFields, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add text")
                }
                OutlinedButton(onClick = { images.launch(arrayOf("image/*")) }) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add images")
                }
                OutlinedButton(onClick = { voice.launch(arrayOf("audio/*")) }) {
                    Icon(Icons.Outlined.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add voice")
                }
            }
            // Only what was added by hand: the source preview above already shows everything
            // loaded from Telegram, and removing one of those individually would be undone by the
            // next load.
            appState.manualInputs.forEach { input ->
                SourceCard(input, onRemove = { appState.removeInput(input.sourceId) })
            }
        }
        ExpandableSection("Cloud model", Icons.Outlined.SmartToy) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(
                    appState.cloudConfiguration.provider.displayName,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = appState.cloudConfiguration.model,
                    onValueChange = appState::updateModel,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Analysis model") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().scrollableRow(),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    OutlinedButton(
                        onClick = { scope.launch { appState.loadCloudModels() } },
                        enabled = !appState.modelListLoading,
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (appState.modelListLoading) "Loading…" else "Load models")
                    }
                    if (appState.availableModels.isNotEmpty()) {
                        Box {
                            OutlinedButton(onClick = { modelMenuOpen = true }) {
                                Icon(Icons.Outlined.SmartToy, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Choose model (${appState.availableModels.size})")
                            }
                            DropdownMenu(
                                expanded = modelMenuOpen,
                                onDismissRequest = { modelMenuOpen = false },
                            ) {
                                appState.availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = {
                                            appState.updateModel(model)
                                            // Picking a model does not change the key, so this
                                            // persists the choice without re-verifying.
                                            appState.persistModelChoice()
                                            modelMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                appState.modelListMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
            },
        )
        DuplicateAnalysisDialog(appState)

        // Collapsed by default: the usual route is loading sources from Telegram, and adding one
        // by hand is the exception.
        val analyzeDisabledReason = blockedReason
        if (appState.analysisStatus != AnalysisStatus.RUNNING) {
            analyzeDisabledReason?.let {
                if (appState.inputs.isEmpty()) {
                    // The chat list now sits at the top of this screen, so there is nowhere to send
                    // the user - only somewhere to point them.
                    Text(
                        "Select chats at the top of this screen and load the source window.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!notificationsAllowed) {
                Text(
                    "Notifications are off, so a run gives no progress while you are in another " +
                        "app. The analysis itself is unaffected.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { activity.openNotificationSettings() }) {
                    Text("Turn on notifications")
                }
            }
            if (analyzeDisabledReason == null && appState.inputs.isEmpty()) {
                Text(
                    "Selected Telegram chats will be collected automatically for the resolved source window.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        appState.analysisMessage?.let {
            Text(
                it,
                color = if (appState.analysisStatus == AnalysisStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun persistReadPermission(activity: Activity, uri: Uri) {
    runCatching {
        activity.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

@Composable
private fun SourceCard(input: AnalysisInput, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(Space.m)) {
            Text(
                when (input) {
                    is AnalysisInput.Text -> "Text · ${input.value.take(100)}"
                    is AnalysisInput.Image -> "Image · ${input.uri.lastPathSegment}"
                    is AnalysisInput.Voice -> "Voice · ${input.uri.lastPathSegment}"
                },
            )
            Text(input.sourceId, style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Remove")
            }
        }
    }
}

@Composable
private fun RecommendationDateOption(
    selected: Boolean,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun showRecommendationDatePicker(activity: Activity, appState: AppState) {
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    // Never open later than the latest date the picker allows. The target session runs ahead of
    // today on a Friday, a Saturday, or any weekday after the close, and a DatePicker asked to open
    // outside its own range throws rather than clamping. Historical analysis refuses a future date
    // anyway, so today is the right place to land.
    val opensOn = minOf(appState.recommendationTargetDate, today)
    DatePickerDialog(
        activity,
        { _, year, month, day ->
            appState.updateRecommendationTargetDate(LocalDate.of(year, month + 1, day))
        },
        opensOn.year,
        opensOn.monthValue - 1,
        opensOn.dayOfMonth,
    ).apply {
        datePicker.maxDate = today
            .atStartOfDay(ZoneId.of("Africa/Cairo"))
            .toInstant()
            .toEpochMilli()
    }.show()
}

@Composable
private fun ContentTypeToggle(label: String, type: AnalysisContentType, appState: AppState) {
    // The checkbox keeps its own 48dp target; only the gap it leaves beside the label is pulled in,
    // so three of these stop reading as three separate paragraphs.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = type in appState.selectedContentTypes,
            onCheckedChange = { appState.toggleContentType(type) },
        )
        Text(label, modifier = Modifier.offset(x = (-4).dp))
    }
}

/**
 * What would be sent, before it is sent.
 *
 * Analysis loads its own sources, so this is not a required step - it is here so the size of a
 * paid request can be seen first, and so the window can be sanity-checked against the chats.
 */
@Composable
private fun SourcePreview(appState: AppState, scope: kotlinx.coroutines.CoroutineScope) {
    val selected = appState.channels.count(ChannelSelection::selected)
    val sources = appState.telegramSources
    SectionCard(title = "Source preview", icon = Icons.Outlined.Preview) {
        Text(
            appState.telegramSyncMessage
                ?: "Check which messages the target date and content types actually select.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                scope.launch {
                    appState.runAction(
                        label = "Loading sources from Telegram",
                        success = { "Loaded ${appState.inputs.size} sources ready to analyze." },
                    ) { appState.syncTelegramSources() }
                }
            },
            enabled = selected > 0 && appState.busyLabel == null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (sources.isEmpty()) "Preview sources" else "Refresh preview") }

        if (sources.isNotEmpty()) {
            // Bounded like the chat list, so a busy day does not bury the Analyze button.
            Column(
                Modifier
                    .heightIn(max = SourceListMaxHeight)
                    .scrollableColumn(),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                sources.forEach { source ->
                    Column {
                        Text(
                            "${source.channelName} · ${source.contentType.name.lowercase()}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            source.timestamp.atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        source.preview.takeIf(String::isNotBlank)?.let {
                            Text(
                                it.take(120),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Enough to scan the window without pushing the run off the screen. */
private val SourceListMaxHeight = 260.dp

/**
 * Asks before repeating an analysis that has already been paid for.
 *
 * Only shown when the session and the chats both match an existing report, so dismissing it is
 * never the routine action.
 */
@Composable
private fun DuplicateAnalysisDialog(appState: AppState) {
    val duplicate = appState.duplicateOfSelection ?: return
    val ranAt = remember(duplicate.id) {
        DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(duplicate.result.completedAt)
    }
    AlertDialog(
        onDismissRequest = appState::dismissDuplicateWarning,
        title = { Text("This session is already analysed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(
                    "A report for ${duplicate.result.recommendationTargetDate} already covers " +
                        "exactly these chats, run $ranAt.",
                )
                Text(
                    duplicate.result.selectedChannels.joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Running it again costs another request and replaces that report in Insights.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                appState.dismissDuplicateWarning()
                appState.startAnalysis(confirmed = true)
            }) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = appState::dismissDuplicateWarning) { Text("Cancel") }
        },
    )
}

/**
 * Opens this app's notification settings.
 *
 * Once the permission has been denied Android will not ask again, so the only way back is the
 * system screen.
 */
private fun Activity.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
    )
}

/**
 * Why a run cannot start, or null when it can.
 *
 * Lifted out of the screen body so the floating action and the explanation beneath the sources can
 * agree without one of them re-deriving it.
 */
private fun analyzeBlockedReason(appState: AppState): String? = when {
    !appState.cloudConfiguration.hasCredential ->
        "Save the provider API key in Settings first."
    appState.cloudConfiguration.model.isBlank() ->
        "Load or enter a cloud model before analyzing."
    appState.selectedContentTypes.isEmpty() ->
        "Select at least one content type."
    appState.inputs.isEmpty() &&
        (appState.telegramAuthState.step != TelegramAuthStep.READY ||
            appState.channels.none(ChannelSelection::selected)) ->
        "No sources are loaded. In Channels, select chats and load messages for a date, or add content above."
    else -> null
}
