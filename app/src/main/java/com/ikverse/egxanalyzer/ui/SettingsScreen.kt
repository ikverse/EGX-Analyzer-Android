package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.BuildConfig
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.model.ResponseTimeout
import com.ikverse.egxanalyzer.model.RuleOrigin
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(appState: AppState) {
    var providerMenuOpen by remember { mutableStateOf(false) }
    var endpointMenuOpen by remember { mutableStateOf(false) }
    var themeMenuOpen by remember { mutableStateOf(false) }
    var languageMenuOpen by remember { mutableStateOf(false) }
    var credential by remember { mutableStateOf("") }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all saved analyses?") },
            text = {
                Text(
                    "This permanently removes ${appState.savedResults.size} saved analyses and " +
                        "their source traces from this device, from your Telegram sync channel, " +
                        "and from every other device that syncs with it. It cannot be undone. " +
                        "Telegram and provider credentials are not changed.",
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
    ) {
        // One card for everything a run depends on, in the order a run uses it: which model,
        // what is sent to it, the wording it is told about, the prompt that carries it, and
        // what is checked afterwards. Five cards asked which of them a setting lived in.
        ExpandableSection(
            "Analysis",
            icon = Icons.Outlined.SmartToy,
            summary = "${appState.cloudConfiguration.provider.displayName} · " +
                appState.cloudConfiguration.model.ifBlank { "no model" },
            summaryTone = if (appState.credentialVerified == false) {
                MaterialTheme.colorScheme.error
            } else {
                null
            },
            contentMaxWidth = FormWidth,
        ) {
            SubSection(
                "Model",
                summary = appState.cloudConfiguration.model.ifBlank { "No model chosen" },
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
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
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Button(
                        enabled = appState.busyLabel == null,
                        onClick = {
                            val entered = credential
                            credential = ""
                            scope.launch { appState.saveSettings(entered) }
                        },
                    ) { Text("Save and verify") }
                    OutlinedButton(onClick = appState::resetProviderConfiguration) {
                        Text("Reset provider")
                    }
                    if (appState.cloudConfiguration.hasCredential) {
                        OutlinedButton(onClick = appState::removeCredential) {
                            Text("Remove credential")
                        }
                    }
                }
                when (appState.credentialVerified) {
                    true -> StatusPill("API key verified", StatusTone.GOOD)
                    false -> StatusPill("API key rejected", StatusTone.BAD)
                    null -> if (appState.cloudConfiguration.hasCredential) {
                        StatusPill("API key not verified yet", StatusTone.NEUTRAL)
                    }
                }
                appState.settingsMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (appState.credentialVerified == false) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            }

            SubSection(
                "What to send",
                summary = "${appState.appPreferences.analysisLanguage.displayName} · " +
                    "${appState.appPreferences.defaultContentTypes.size} content types",
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
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
                Text("Cloud response timeout: ${appState.appPreferences.responseTimeoutSeconds} seconds")
                Slider(
                    value = appState.appPreferences.responseTimeoutSeconds.toFloat(),
                    onValueChange = {
                        val seconds = (it / 30f).roundToInt() * 30
                        appState.updateResponseTimeout(seconds)
                    },
                    valueRange = ResponseTimeout.MIN.toFloat()..ResponseTimeout.MAX.toFloat(),
                    steps = (ResponseTimeout.MAX - ResponseTimeout.MIN) / 30 - 1,
                )
                Text(
                    "How long the model may think about one batch of images. It sends nothing until " +
                        "it has finished, so a batch that needs longer than this is hung up on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }

            SubSection(
                "Wording",
                summary = appState.ruleSet.all.count { it.origin == RuleOrigin.USER }.let { mine ->
                    if (mine == 0) "Built-in wording only" else "$mine added"
                },
            ) {
            AnalysisRulesSection(appState)
            }

            SubSection(
                "Prompt",
                summary = if (appState.useDefaultPromptOnly) {
                    "Default only"
                } else {
                    "v" + (
                        appState.promptVersions
                            .firstOrNull { it.id == appState.activePrompt.id }?.sequence ?: 1
                        )
                },
            ) {
            GeneratedPromptSection(appState)
            }

            SubSection(
                "Validation",
                summary = "${appState.appPreferences.correctionRetries} correction " +
                    if (appState.appPreferences.correctionRetries == 1) "retry" else "retries",
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
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

            }
            }
        }

        ExpandableSection(
            "Appearance",
            icon = Icons.Outlined.Palette,
            summary = appState.appPreferences.themeMode.displayName,
            contentMaxWidth = FormWidth,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
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

        ExpandableSection(
            "Sync",
            icon = Icons.Outlined.CloudSync,
            summary = "${appState.savedResults.size} reports on this device",
            contentMaxWidth = FormWidth,
        ) {
            Text(
                "Reports are kept in a private Telegram channel of your own, so every device signed " +
                    "in to your account sees the same history. A saved report never changes, so " +
                    "syncing only ever adds - nothing is overwritten and nothing is deleted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { scope.launch { appState.syncReports() } },
                enabled = appState.telegramAuthState.step == TelegramAuthStep.READY &&
                    appState.busyLabel == null,
            ) { Text("Sync now") }
            if (appState.telegramAuthState.step != TelegramAuthStep.READY) {
                Text(
                    "Sign in to Telegram to sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ExpandableSection(
            "Scoring",
            icon = Icons.Outlined.Timeline,
            summary = "${appState.appPreferences.scoringWindowSessions} trading sessions",
            contentMaxWidth = FormWidth,
        ) {
            val window = appState.appPreferences.scoringWindowSessions
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Scoring window", modifier = Modifier.weight(1f))
                Text(
                    "$window ${if (window == 1) "session" else "sessions"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = window.toFloat(),
                onValueChange = { appState.updateScoringWindow(it.roundToInt()) },
                valueRange = Scoring.MIN_WINDOW_SESSIONS.toFloat()..
                    Scoring.MAX_WINDOW_SESSIONS.toFloat(),
                steps = Scoring.MAX_WINDOW_SESSIONS - Scoring.MIN_WINDOW_SESSIONS - 1,
            )
            Text(
                "Trading sessions a recommendation has to reach its target. Re-scores everything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ExpandableSection(
            "Telegram",
            icon = Icons.Outlined.Forum,
            summary = if (appState.telegramAuthState.step == TelegramAuthStep.READY) "Signed in · ${appState.channels.size} chats" else "Not connected",
            contentMaxWidth = FormWidth,
            summaryTone = if (appState.telegramAuthState.step == TelegramAuthStep.READY) null else MaterialTheme.colorScheme.error,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                if (appState.telegramAuthState.step == TelegramAuthStep.READY) {
                    // No status line of its own: the summary above this card already says signed
                    // in and how many chats there are, and saying it again a few pixels below made
                    // the two counts look like two different figures.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                        verticalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        OutlinedButton(onClick = {
                            scope.launch { appState.refreshTelegramChats() }
                        }) { Text("Refresh chats") }
                        // Through runAction like everywhere else: signing out tears down the
                        // Telegram client, and without the busy label nothing on screen says so.
                        OutlinedButton(
                            enabled = appState.busyLabel == null,
                            onClick = {
                                scope.launch {
                                    appState.runAction(
                                        label = "Signing out",
                                        success = { "Signed out" },
                                    ) { appState.logoutTelegram() }
                                }
                            },
                        ) { Text("Sign out") }
                    }
                } else {
                    // The flow itself rather than a trip to Analyze for it: signing out is a
                    // button on this card, so signing back in has to be reachable from it too.
                    TelegramSignIn(appState, boxed = false)
                }
            }
        }

        ExpandableSection(
            "About",
            icon = Icons.Outlined.Info,
            summary = BuildConfig.VERSION_NAME,
            contentMaxWidth = FormWidth,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text(
                    "Shown so a device can be asked which build it is running.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ExpandableSection(
            "Saved data and privacy",
            icon = Icons.Outlined.Shield,
            summary = "${appState.savedResults.size} saved analyses",
            contentMaxWidth = FormWidth,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
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

/** Wide enough for a long model name, narrow enough that a field still reads as a field. */
private val FormWidth = 560.dp

