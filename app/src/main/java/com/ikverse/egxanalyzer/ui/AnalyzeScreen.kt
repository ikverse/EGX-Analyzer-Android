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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Content types", fontWeight = FontWeight.Bold)
                ContentTypeToggle("Text messages", AnalysisContentType.TEXT, appState)
                ContentTypeToggle("Images / photos", AnalysisContentType.IMAGES, appState)
                ContentTypeToggle("Voice messages", AnalysisContentType.AUDIO, appState)
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
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
        appState.inputs.forEach { input ->
            SourceCard(input, onRemove = { appState.removeInput(input.sourceId) })
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SmartToy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cloud model", fontWeight = FontWeight.Bold)
                }
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
                                            appState.saveSettings("")
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
                onClick = { scope.launch { appState.analyze() } },
                enabled = analyzeDisabledReason == null,
            ) {
                Icon(Icons.Outlined.AutoGraph, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Analyze selected sources")
            }
            analyzeDisabledReason?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                if (appState.inputs.isEmpty()) {
                    TextButton(onClick = { appState.navigate(AppDestination.CHANNELS) }) {
                        Icon(Icons.Outlined.Forum, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Go to Channels to load sources")
                    }
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
    OutlinedCard(Modifier.fillMaxWidth()) {
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
