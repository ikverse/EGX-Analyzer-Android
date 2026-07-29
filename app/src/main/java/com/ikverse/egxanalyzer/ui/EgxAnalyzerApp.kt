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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.ikverse.egxanalyzer.R
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.SavedAnalysis
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.model.resolveAnalysisWindow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private enum class WindowWidth { COMPACT, MEDIUM, EXPANDED }

@Composable
fun EgxAnalyzerApp(activity: Activity, appState: AppState) {
    val density = LocalDensity.current
    val width = with(density) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val windowWidth = when {
        width >= 840 -> WindowWidth.EXPANDED
        width >= 600 -> WindowWidth.MEDIUM
        else -> WindowWidth.COMPACT
    }
    val layoutInfo by produceState<WindowLayoutInfo?>(null, activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { value = it }
    }
    val separatingFold = layoutInfo?.displayFeatures.orEmpty().filterIsInstance<FoldingFeature>()
        .firstOrNull(FoldingFeature::isSeparating)
    AdaptiveAppScaffold(windowWidth, appState) { padding ->
        AppContent(
            modifier = Modifier.padding(padding),
            activity = activity,
            appState = appState,
            showCompanionPane = windowWidth == WindowWidth.EXPANDED ||
                separatingFold?.orientation == FoldingFeature.Orientation.VERTICAL,
            hingeWidth = separatingFold?.bounds?.width()?.let { with(density) { it.toDp() } } ?: 0.dp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdaptiveAppScaffold(
    windowWidth: WindowWidth,
    appState: AppState,
    content: @Composable (PaddingValues) -> Unit,
) {
    val compact = windowWidth == WindowWidth.COMPACT
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.desktop_egx_icon),
                        contentDescription = "EGX Analyzer",
                        modifier = Modifier.size(38.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("EGX Analyzer", fontWeight = FontWeight.Bold)
                        Text(
                            "Standalone cloud analysis",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            })
        },
        bottomBar = {
            if (compact) NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = appState.destination == destination,
                        onClick = { appState.navigate(destination) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        alwaysShowLabel = false,
                    )
                }
            }
        },
    ) { padding ->
        if (compact) {
            content(padding)
        } else {
            Row(Modifier.padding(padding).fillMaxSize()) {
                NavigationRail {
                    Spacer(Modifier.height(12.dp))
                    AppDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = appState.destination == destination,
                            onClick = { appState.navigate(destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                Box(Modifier.fillMaxSize()) { content(PaddingValues()) }
            }
        }
    }
}

private val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.CHANNELS -> Icons.Outlined.Forum
        AppDestination.ANALYZE -> Icons.Outlined.AutoGraph
        AppDestination.RESULTS -> Icons.Outlined.Assessment
        AppDestination.INSIGHTS -> Icons.Outlined.Insights
        AppDestination.SETTINGS -> Icons.Outlined.Settings
    }

@Composable
private fun AppContent(
    modifier: Modifier,
    activity: Activity,
    appState: AppState,
    showCompanionPane: Boolean,
    hingeWidth: androidx.compose.ui.unit.Dp,
) {
    Row(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (showCompanionPane) 0.66f else 1f),
        ) {
            when (appState.destination) {
                AppDestination.CHANNELS -> ChannelsScreen(appState)
                AppDestination.ANALYZE -> AnalyzeScreen(activity, appState)
                AppDestination.RESULTS -> ResultsScreen(activity, appState)
                AppDestination.INSIGHTS -> InsightsScreen(appState)
                AppDestination.SETTINGS -> SettingsScreen(appState)
            }
        }
        if (showCompanionPane) {
            Spacer(Modifier.width(hingeWidth))
            CompanionPane(appState, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Screen(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun ChannelsScreen(appState: AppState) {
    val scope = rememberCoroutineScope()
    var firstValue by remember(appState.telegramAuthState.step) { mutableStateOf("") }
    var secondValue by remember(appState.telegramAuthState.step) { mutableStateOf("") }
    Screen(
        title = "Channels",
        subtitle = "Select chats; the target-date rules determine the exact Cairo source window.",
    ) {
        Text(
            appState.telegramAuthState.message,
            color = if (appState.telegramAuthState.step == TelegramAuthStep.ERROR) {
                MaterialTheme.colorScheme.error
            } else MaterialTheme.colorScheme.primary,
        )
        when (appState.telegramAuthState.step) {
            TelegramAuthStep.API_CONFIGURATION -> {
                AuthCard("Telegram application") {
                    Text(
                        "Create an application at my.telegram.org and enter its API ID and API hash. " +
                            "They are encrypted on this device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AuthField(firstValue, { firstValue = it }, "API ID")
                    AuthField(secondValue, { secondValue = it }, "API hash", secret = true)
                    Button(onClick = {
                        scope.launch {
                            appState.saveTelegramApiConfiguration(firstValue, secondValue)
                            secondValue = ""
                        }
                    }) { Text("Initialize Telegram") }
                }
            }
            TelegramAuthStep.PHONE_NUMBER -> AuthCard("Phone number") {
                AuthField(firstValue, { firstValue = it }, "Phone number with country code")
                Button(onClick = {
                    scope.launch { appState.submitTelegramPhone(firstValue) }
                }) { Text("Send verification code") }
                TextButton(onClick = {
                    firstValue = ""
                    scope.launch { appState.resetTelegramApiConfiguration() }
                }) { Text("Change Telegram app ID / hash") }
            }
            TelegramAuthStep.VERIFICATION_CODE -> AuthCard("Verification code") {
                AuthField(firstValue, { firstValue = it }, "Telegram code")
                Button(onClick = {
                    scope.launch { appState.submitTelegramCode(firstValue) }
                }) { Text("Verify code") }
            }
            TelegramAuthStep.TWO_FACTOR_PASSWORD -> AuthCard("Two-step verification") {
                appState.telegramAuthState.hint?.takeIf(String::isNotBlank)?.let {
                    Text("Hint: $it")
                }
                AuthField(firstValue, { firstValue = it }, "Telegram password", secret = true)
                Button(onClick = {
                    scope.launch {
                        appState.submitTelegramPassword(firstValue)
                        firstValue = ""
                    }
                }) { Text("Continue") }
            }
            TelegramAuthStep.EMAIL_ADDRESS -> AuthCard("Login email") {
                AuthField(firstValue, { firstValue = it }, "Email address")
                Button(onClick = {
                    scope.launch { appState.submitTelegramEmail(firstValue) }
                }) { Text("Send email code") }
            }
            TelegramAuthStep.EMAIL_CODE -> AuthCard("Email verification") {
                AuthField(firstValue, { firstValue = it }, "Email code")
                Button(onClick = {
                    scope.launch { appState.submitTelegramEmailCode(firstValue) }
                }) { Text("Verify email") }
            }
            TelegramAuthStep.REGISTRATION -> AuthCard("Finish registration") {
                AuthField(firstValue, { firstValue = it }, "First name")
                AuthField(secondValue, { secondValue = it }, "Last name")
                Button(onClick = {
                    scope.launch { appState.registerTelegram(firstValue, secondValue) }
                }) { Text("Register") }
            }
            TelegramAuthStep.OTHER_DEVICE_CONFIRMATION -> AuthCard("Confirm on another device") {
                Text(appState.telegramAuthState.link.orEmpty())
                Text("Open this link in Telegram on an already signed-in device.")
            }
            TelegramAuthStep.READY -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = {
                        scope.launch { appState.refreshTelegramChats() }
                    }) { Text("Refresh chats") }
                    TextButton(onClick = {
                        scope.launch { appState.logoutTelegram() }
                    }) { Text("Sign out") }
                }
                Text(
                    "Target: ${appState.recommendationTargetDate} · " +
                        if (appState.analysisMode == AnalysisMode.NEXT_DAY) {
                            "current / next EGX session"
                        } else {
                            "historical analysis"
                        },
                    color = MaterialTheme.colorScheme.primary,
                )
                if (appState.channels.isEmpty()) {
                    Text("No Telegram chats loaded yet.")
                } else {
                    appState.channels.forEach { channel -> ChannelCard(channel, appState) }
                }
                Button(
                    onClick = {
                        scope.launch { appState.syncTelegramSources() }
                    },
                    enabled = appState.channels.any(ChannelSelection::selected),
                ) { Text("Load analysis source window") }
                appState.telegramSyncMessage?.let { Text(it) }
            }
            TelegramAuthStep.INITIALIZING,
            TelegramAuthStep.LOGGING_OUT,
            TelegramAuthStep.ERROR -> Unit
        }
    }
}

@Composable
private fun AuthCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = if (secret) PasswordVisualTransformation() else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChannelCard(channel: ChannelSelection, appState: AppState) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = channel.selected,
                onCheckedChange = { appState.toggleChannel(channel) },
            )
            Column {
                Text(channel.name, fontWeight = FontWeight.Bold)
                Text(channel.id.toString(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AnalyzeScreen(activity: Activity, appState: AppState) {
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

@Composable
private fun ResultsScreen(activity: Activity, appState: AppState) {
    var reportResultId by remember { mutableStateOf<Long?>(null) }
    Screen(
        title = "Results",
        subtitle = "Saved recommendations and their exact source traces remain on this device.",
    ) {
        if (appState.savedResults.isEmpty()) {
            Text("No saved results yet.")
        } else {
            appState.savedResults.forEach { saved ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${saved.result.recommendations.size} recommendations",
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${saved.provider.displayName} · ${saved.model}")
                        Text(
                            "Recommendation target: " +
                                (saved.result.recommendationTargetDate?.toString() ?: "Not recorded"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(saved.result.completedAt.toString())
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = { appState.selectResult(saved) }) { Text("View details") }
                            OutlinedButton(onClick = {
                                reportResultId = if (reportResultId == saved.id) null else saved.id
                            }) {
                                Icon(Icons.Outlined.Description, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Report")
                            }
                            OutlinedButton(onClick = {
                                shareReport(activity, appState.reportFor(saved))
                            }) {
                                Text("Share")
                            }
                            TextButton(onClick = { appState.deleteResult(saved) }) { Text("Delete") }
                        }
                        if (appState.selectedResult?.id == saved.id) ResultDetail(saved)
                        if (reportResultId == saved.id) {
                            val report = appState.reportFor(saved)
                            HorizontalDivider()
                            Text(report.title, fontWeight = FontWeight.Bold)
                            Text(report.markdown, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun shareReport(
    activity: Activity,
    report: com.ikverse.egxanalyzer.model.AnalysisReport,
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_SUBJECT, report.title)
        putExtra(Intent.EXTRA_TEXT, report.markdown)
    }
    activity.startActivity(Intent.createChooser(intent, "Share EGX analysis report"))
}

@Composable
private fun InsightsScreen(appState: AppState) {
    var query by remember { mutableStateOf("") }
    val hits = remember(query, appState.savedResults) { appState.searchAnalyses(query) }
    val consensus = remember(appState.savedResults) { appState.consensus() }
    Screen(
        title = "Insights",
        subtitle = "Search saved evidence, compare consensus, and inspect validation diagnostics.",
    ) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Search analyses", fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ticker, company, note, or source") },
                    singleLine = true,
                )
                if (query.length >= 2 && hits.isEmpty()) Text("No matching saved evidence.")
                hits.take(20).forEach { hit ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${hit.ticker} · ${hit.companyName}", fontWeight = FontWeight.Bold)
                            Text("Target: ${hit.targetDate ?: "Not recorded"}")
                            Text("Sources: ${hit.sourceNames.joinToString().ifBlank { "No source" }}")
                        }
                    }
                }
            }
        }

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Insights, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Recommendation consensus", fontWeight = FontWeight.Bold)
                }
                if (consensus.isEmpty()) Text("No recommendations available yet.")
                consensus.forEach { item ->
                    Column {
                        Text("${item.ticker} · ${item.companyName}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${item.recommendationCount} recommendations · ${item.sourceCount} sources · " +
                                "BUY ${item.buyCount} / SELL ${item.sellCount} / HOLD ${item.holdCount}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        item.averageConfidence?.let {
                            Text("Average confidence: ${"%.0f".format(it * 100)}%")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Recent diagnostics", fontWeight = FontWeight.Bold)
                }
                if (appState.savedResults.isEmpty()) Text("No analysis diagnostics yet.")
                appState.savedResults.take(10).forEach { saved ->
                    val diagnostics = saved.result.diagnostics
                    Text(
                        "${saved.result.recommendationTargetDate ?: "Unknown target"} · " +
                            "${diagnostics.acceptedInputCount}/${diagnostics.inputCount} inputs · " +
                            "${diagnostics.validationWarnings.size} warnings · " +
                            "${diagnostics.durationMilliseconds} ms",
                    )
                    diagnostics.validationWarnings.take(3).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ResultDetail(saved: SavedAnalysis) {
    HorizontalDivider()
    saved.result.recommendations.forEach { recommendation ->
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${recommendation.ticker} · ${recommendation.companyName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("Source: ${recommendation.sourceName}")
            Text(
                "Signal: ${recommendation.signal}" +
                    (recommendation.confidence?.let { " · ${"%.0f".format(it * 100)}%" } ?: ""),
                color = MaterialTheme.colorScheme.primary,
            )
            recommendation.riskLevel?.let { Text("Risk: $it") }
            recommendation.timeHorizon?.let { Text("Horizon: $it") }
            if (recommendation.indicators.isNotEmpty()) {
                Text("Indicators: ${recommendation.indicators.joinToString()}")
            }
            recommendation.entryLow?.let { Text("Entry: $it – ${recommendation.entryHigh ?: it}") }
            recommendation.takeProfit1?.let { Text("Take profit: $it") }
            recommendation.stopLoss?.let { Text("Stop loss: $it") }
            recommendation.notesArabic?.let { Text(it) }
            Text(
                "Evidence: ${recommendation.sourceIds.joinToString().ifBlank { "No source cited" }}",
                color = MaterialTheme.colorScheme.primary,
            )
        }
        HorizontalDivider()
    }
    Text("Source trace", fontWeight = FontWeight.Bold)
    saved.result.sources.forEach { source ->
        Text("${source.sourceId} · ${source.channelName} · ${source.contentType} · ${source.preview}")
    }
    HorizontalDivider()
    Text("Diagnostics", fontWeight = FontWeight.Bold)
    val diagnostics = saved.result.diagnostics
    Text("Source window: ${diagnostics.sourceWindowStart ?: "Not recorded"} — ${diagnostics.sourceWindowEnd ?: "Not recorded"}")
    Text("${diagnostics.acceptedInputCount}/${diagnostics.inputCount} inputs accepted")
    Text("${diagnostics.excludedSources.size} sources filtered before analysis")
    Text("${diagnostics.validationWarnings.size} validation warnings")
    Text("Correction attempted: ${if (diagnostics.correctionAttempted) "Yes" else "No"}")
    Text("Cloud analysis duration: ${diagnostics.durationMilliseconds} ms")
    diagnostics.excludedSources.forEach {
        Text("Excluded ${it.sourceId}: ${it.reason}", style = MaterialTheme.typography.bodySmall)
    }
    diagnostics.validationWarnings.forEach {
        Text("Warning: $it", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SettingsScreen(appState: AppState) {
    var providerMenuOpen by remember { mutableStateOf(false) }
    var endpointMenuOpen by remember { mutableStateOf(false) }
    var themeMenuOpen by remember { mutableStateOf(false) }
    var languageMenuOpen by remember { mutableStateOf(false) }
    var credential by remember { mutableStateOf("") }
    var customPrompt by remember(appState.appPreferences.customSystemPrompt) {
        mutableStateOf(appState.appPreferences.customSystemPrompt)
    }
    var includePhrases by remember(appState.appPreferences.includePhrases) {
        mutableStateOf(appState.appPreferences.includePhrases)
    }
    var excludePhrases by remember(appState.appPreferences.excludePhrases) {
        mutableStateOf(appState.appPreferences.excludePhrases)
    }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all saved analyses?") },
            text = {
                Text(
                    "This permanently removes ${appState.savedResults.size} saved analyses and " +
                        "their source traces from this device. Telegram and provider credentials are not changed.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        appState.deleteAllResults()
                        confirmDeleteAll = false
                    },
                ) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            },
        )
    }

    Screen(
        title = "Settings",
        subtitle = "Cloud models only. No Ollama or local-model runtime is included.",
    ) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("AI analysis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Box {
                    TextButton(onClick = { providerMenuOpen = true }) {
                        Text("Provider: ${appState.cloudConfiguration.provider.displayName}")
                    }
                    DropdownMenu(
                        expanded = providerMenuOpen,
                        onDismissRequest = { providerMenuOpen = false },
                    ) {
                        CloudProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName) },
                                onClick = {
                                    appState.selectProvider(provider)
                                    providerMenuOpen = false
                                    credential = ""
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = appState.cloudConfiguration.endpoint,
                    onValueChange = appState::updateEndpoint,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("HTTPS API endpoint") },
                    singleLine = true,
                )
                if (appState.cloudConfiguration.provider.endpointPresets.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { endpointMenuOpen = true }) {
                            Text("Choose endpoint region")
                        }
                        DropdownMenu(
                            expanded = endpointMenuOpen,
                            onDismissRequest = { endpointMenuOpen = false },
                        ) {
                            appState.cloudConfiguration.provider.endpointPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.displayName) },
                                    onClick = {
                                        appState.updateEndpoint(preset.endpoint)
                                        endpointMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                Text(
                    "Choose and load the analysis model on the Analyze screen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = credential,
                    onValueChange = { credential = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (appState.cloudConfiguration.hasCredential) {
                                "Replace saved API key (optional)"
                            } else "API key",
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = {
                        appState.saveSettings(credential)
                        credential = ""
                    }) { Text("Save settings") }
                    OutlinedButton(onClick = appState::resetProviderConfiguration) {
                        Text("Reset provider")
                    }
                    if (appState.cloudConfiguration.hasCredential) {
                        OutlinedButton(onClick = appState::removeCredential) {
                            Text("Remove credential")
                        }
                    }
                }
                appState.settingsMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        }

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tune, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Prompt and validation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "Leave the system prompt blank to use the protected evidence-backed default.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom system prompt") },
                    minLines = 4,
                )
                OutlinedTextField(
                    value = includePhrases,
                    onValueChange = { includePhrases = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Priority/include phrases") },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = excludePhrases,
                    onValueChange = { excludePhrases = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Exclude phrases") },
                    minLines = 2,
                )
                Text("Correction retries: ${appState.appPreferences.correctionRetries}")
                Slider(
                    value = appState.appPreferences.correctionRetries.toFloat(),
                    onValueChange = { appState.updateCorrectionRetries(it.roundToInt()) },
                    valueRange = 0f..2f,
                    steps = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = appState.appPreferences.catalogEnrichmentEnabled,
                        onCheckedChange = appState::updateCatalogEnrichment,
                    )
                    Text("Enrich results with the on-device EGX catalog")
                }
                OutlinedButton(onClick = {
                    scope.launch { appState.refreshEgxCatalog() }
                }) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh EGX catalog")
                }
                Text(
                    appState.catalogMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = {
                        appState.updatePromptCustomization(
                            customPrompt,
                            includePhrases,
                            excludePhrases,
                        )
                    }) {
                        Icon(Icons.Outlined.Tune, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save prompt")
                    }
                    OutlinedButton(onClick = {
                        appState.resetPromptCustomization()
                        customPrompt = ""
                        includePhrases = ""
                        excludePhrases = ""
                    }) {
                        Text("Restore default")
                    }
                }
                if (appState.promptHistory.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Prompt history", fontWeight = FontWeight.Bold)
                    }
                    appState.promptHistory.take(5).forEachIndexed { index, snapshot ->
                        TextButton(onClick = {
                            appState.restorePromptSnapshot(snapshot)
                            customPrompt = snapshot.systemPrompt
                            includePhrases = snapshot.includePhrases
                            excludePhrases = snapshot.excludePhrases
                        }) {
                            Text(
                                "Restore ${index + 1} · " +
                                    java.time.Instant.ofEpochMilli(
                                        snapshot.savedAtEpochMilliseconds,
                                    ).toString(),
                            )
                        }
                    }
                }
            }
        }

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(onClick = { themeMenuOpen = true }) {
                        Text("Theme: ${appState.appPreferences.themeMode.displayName}")
                    }
                    DropdownMenu(
                        expanded = themeMenuOpen,
                        onDismissRequest = { themeMenuOpen = false },
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.displayName) },
                                onClick = {
                                    appState.updateThemeMode(mode)
                                    themeMenuOpen = false
                                },
                            )
                        }
                    }
                }
                Text(
                    "The choice is applied immediately on outer and inner displays.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Analysis defaults",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Box {
                    OutlinedButton(onClick = { languageMenuOpen = true }) {
                        Text("Output language: ${appState.appPreferences.analysisLanguage.displayName}")
                    }
                    DropdownMenu(
                        expanded = languageMenuOpen,
                        onDismissRequest = { languageMenuOpen = false },
                    ) {
                        AnalysisLanguage.entries.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language.displayName) },
                                onClick = {
                                    appState.updateAnalysisLanguage(language)
                                    languageMenuOpen = false
                                },
                            )
                        }
                    }
                }
                Text("Default Telegram content")
                AnalysisContentType.entries.forEach { type ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = type in appState.appPreferences.defaultContentTypes,
                            onCheckedChange = { appState.toggleDefaultContentType(type) },
                        )
                        Text(
                            when (type) {
                                AnalysisContentType.TEXT -> "Text messages"
                                AnalysisContentType.IMAGES -> "Images and photos"
                                AnalysisContentType.AUDIO -> "Voice messages"
                            },
                        )
                    }
                }
                Text("Model temperature: ${"%.1f".format(appState.appPreferences.temperature)}")
                Slider(
                    value = appState.appPreferences.temperature.toFloat(),
                    onValueChange = { appState.updateTemperature(it.toDouble()) },
                    valueRange = 0f..1f,
                    steps = 9,
                )
                Text(
                    "Lower values keep extraction more deterministic.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Cloud response timeout: ${appState.appPreferences.responseTimeoutSeconds} seconds")
                Slider(
                    value = appState.appPreferences.responseTimeoutSeconds.toFloat(),
                    onValueChange = {
                        val seconds = (it / 30f).roundToInt() * 30
                        appState.updateResponseTimeout(seconds)
                    },
                    valueRange = 30f..300f,
                    steps = 8,
                )
            }
        }

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Telegram", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(appState.telegramAuthState.message)
                Text("${appState.channels.count(ChannelSelection::selected)} chats selected")
                if (appState.telegramAuthState.step == TelegramAuthStep.READY) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = {
                            scope.launch { appState.refreshTelegramChats() }
                        }) { Text("Refresh chats") }
                        OutlinedButton(onClick = {
                            scope.launch { appState.logoutTelegram() }
                        }) { Text("Sign out") }
                    }
                } else {
                    Button(onClick = { appState.navigate(AppDestination.CHANNELS) }) {
                        Text("Open Telegram sign-in")
                    }
                }
            }
        }

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Saved data and privacy",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text("${appState.savedResults.size} analyses saved on this device")
                Text(
                    "Provider keys and the Telegram database key are encrypted using Android Keystore. " +
                        "App backup is disabled and cloud requests use HTTPS.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { confirmDeleteAll = true },
                    enabled = appState.savedResults.isNotEmpty(),
                ) {
                    Text("Delete all saved analyses")
                }
            }
        }
    }
}

@Composable
private fun CompanionPane(appState: AppState, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            when (appState.destination) {
                AppDestination.CHANNELS -> "Source workspace"
                AppDestination.ANALYZE -> "Analysis setup"
                AppDestination.RESULTS -> "Result inspector"
                AppDestination.INSIGHTS -> "Intelligence summary"
                AppDestination.SETTINGS -> "Runtime status"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(appState.cloudConfiguration.provider.displayName)
        Text(
            appState.cloudConfiguration.model.ifBlank { "No model selected" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        Text("${appState.channels.count(ChannelSelection::selected)} channels selected")
        Text("${appState.inputs.size} sources ready")
        Text("${appState.savedResults.size} saved analyses")
        runCatching {
            resolveAnalysisWindow(appState.analysisMode, appState.recommendationTargetDate)
        }.getOrNull()?.let { window ->
            HorizontalDivider()
            Text("Target ${window.targetDate}", fontWeight = FontWeight.Bold)
            Text(
                "${window.start} — ${window.endExclusive}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (appState.cloudConfiguration.hasCredential) "Credential saved securely"
            else "Credential required",
            color = if (appState.cloudConfiguration.hasCredential) {
                MaterialTheme.colorScheme.primary
            } else MaterialTheme.colorScheme.error,
        )
        appState.selectedResult?.let {
            HorizontalDivider()
            Text("Latest result", fontWeight = FontWeight.Bold)
            Text("${it.result.recommendations.size} recommendations")
            Text("${it.result.sources.size} traced sources")
            Text("${it.result.diagnostics.validationWarnings.size} validation warnings")
            Text("${it.result.diagnostics.durationMilliseconds} ms")
        }
        if (appState.destination == AppDestination.INSIGHTS) {
            HorizontalDivider()
            Text("Top consensus", fontWeight = FontWeight.Bold)
            appState.consensus().take(5).forEach {
                Text("${it.ticker} · ${it.recommendationCount} recommendations")
            }
        }
        if (appState.destination == AppDestination.SETTINGS) {
            HorizontalDivider()
            Text(appState.catalogMessage)
            Text("${appState.promptHistory.size} prompt snapshots")
            Text("Correction retries: ${appState.appPreferences.correctionRetries}")
        }
    }
}
