package com.ikverse.egxanalyzer.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
    Screen(
        title = "Analyze",
        subtitle = "Send selected text, images, and voice-message content to " +
            appState.cloudConfiguration.provider.displayName + ".",
    ) {
        // Chat selection leads, because choosing sources is the first step of a run.
        ChannelsSection(appState)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Content types", fontWeight = FontWeight.Bold)
                ContentTypeToggle("Text messages", AnalysisContentType.TEXT, appState)
                ContentTypeToggle("Images / photos", AnalysisContentType.IMAGES, appState)
                ContentTypeToggle("Voice messages", AnalysisContentType.AUDIO, appState)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        // Sits below the target date and content types because it reads both. Above them it
        // fetched against whatever they were before the user had set them.
        SourcePreview(appState, scope)
        DuplicateAnalysisDialog(appState)

        // Collapsed by default: the usual route is loading sources from Telegram, and adding one
        // by hand is the exception.
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
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        val analyzeDisabledReason = when {
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
        if (appState.analysisStatus == AnalysisStatus.RUNNING) {
            Button(onClick = { scope.launch { appState.cancelAnalysis() } }) {
                Icon(Icons.Outlined.Cancel, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cancel analysis")
            }
        } else {
            Button(
                // Started rather than awaited: the run outlives this screen now.
                onClick = { appState.startAnalysis() },
                enabled = analyzeDisabledReason == null,
            ) {
                Icon(Icons.Outlined.AutoGraph, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Analyze selected sources")
            }
            analyzeDisabledReason?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                if (appState.inputs.isEmpty()) {
                    // The chat list now sits at the top of this screen, so there is nowhere to send
                    // the user - only somewhere to point them.
                    Text(
                        "Select chats at the top of this screen and load the source window.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
        Column(Modifier.padding(12.dp)) {
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
    val selected = appState.recommendationTargetDate
    DatePickerDialog(
        activity,
        { _, year, month, day ->
            appState.updateRecommendationTargetDate(LocalDate.of(year, month + 1, day))
        },
        selected.year,
        selected.monthValue - 1,
        selected.dayOfMonth,
    ).apply {
        datePicker.maxDate = LocalDate.now(ZoneId.of("Africa/Cairo"))
            .atStartOfDay(ZoneId.of("Africa/Cairo"))
            .toInstant()
            .toEpochMilli()
    }.show()
}

@Composable
private fun ContentTypeToggle(label: String, type: AnalysisContentType, appState: AppState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = type in appState.selectedContentTypes,
            onCheckedChange = { appState.toggleContentType(type) },
        )
        Text(label)
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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