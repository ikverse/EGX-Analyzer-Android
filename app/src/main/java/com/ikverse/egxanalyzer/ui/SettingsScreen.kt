package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.data.saveDatabaseToDownloads

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ikverse.egxanalyzer.BuildConfig
import com.ikverse.egxanalyzer.data.UpdateState
import com.ikverse.egxanalyzer.data.ModelUsageRecord
import com.ikverse.egxanalyzer.model.ApproachAlerts
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.model.ResponseTimeout
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.TokenUsage
import com.ikverse.egxanalyzer.model.formatTokenCount
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(appState: AppState) {
    var providerMenuOpen by remember { mutableStateOf(false) }
    var endpointMenuOpen by remember { mutableStateOf(false) }
    var themeMenuOpen by remember { mutableStateOf(false) }
    var languageMenuOpen by remember { mutableStateOf(false) }
    var askModelMenuOpen by remember { mutableStateOf(false) }
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

    // One SharedPreferences read each, keyed on the revision the setters bump: neither value is
    // observable on its own, so without the key the card would go on printing what it opened with.
    val askModel = remember(appState.opinionSettingsRevision, appState.cloudConfiguration) {
        appState.opinionModel()
    }
    val askSearching = remember(appState.opinionSettingsRevision) { appState.opinionSearchEnabled() }
    val askWindow = remember(appState.opinionSettingsRevision) { appState.opinionNewsWindowDays() }
    val askDeep = remember(appState.opinionSettingsRevision) { appState.opinionDeepSearch() }
    val askResults = remember(appState.opinionSettingsRevision) { appState.opinionSearchResults() }
    // The window is in the summary because it is the setting most worth seeing without opening
    // the card: a fortnight and half a year are the same button and very different answers.
    val askAiSummary = if (askSearching) {
        "$askModel \u00b7 news from $askWindow days"
    } else {
        "$askModel \u00b7 no search"
    }

    // Counted here rather than in the summary line so the two cannot disagree about how many
    // switches the card actually holds.
    val notificationsOn = listOf(
        appState.appPreferences.overdueRemindersEnabled,
        appState.appPreferences.tradeAlertsEnabled,
        appState.appPreferences.callAlertsEnabled,
        appState.appPreferences.approachAlertsEnabled,
        appState.appPreferences.sessionDigestEnabled,
        appState.appPreferences.feedAlertsEnabled,
        appState.appPreferences.scheduleAlertsEnabled,
    ).count { it }

    // One line per group as well as one for the card. A closed group saying nothing about how much
    // of it is on would put the reader back to opening all three to find the switch they came for,
    // which is what the grouping was for. Built from the same flags as the total above, so a group
    // and the card can never disagree about what is switched on.
    val tradeNotificationsSummary = switchesOn(
        appState.appPreferences.overdueRemindersEnabled,
        appState.appPreferences.tradeAlertsEnabled,
        appState.appPreferences.approachAlertsEnabled,
    )
    val marketNotificationsSummary = switchesOn(
        appState.appPreferences.callAlertsEnabled,
        appState.appPreferences.sessionDigestEnabled,
    )
    val appNotificationsSummary = switchesOn(
        appState.appPreferences.feedAlertsEnabled,
        appState.appPreferences.scheduleAlertsEnabled,
    )

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
                about = infoNote(
                    "Model",
                    "Who reads the cards. The provider decides which endpoint the request goes " +
                        "to and which key pays for it; the model itself is chosen on the Analyze " +
                        "screen, because that is where a run is aimed.",
                    "The key is encrypted by Android Keystore, never synced to your other " +
                        "devices, and never written into a backup - a live cloud credential does " +
                        "not belong in a file about to be copied into a cloud folder.",
                    "Save and verify sends one free request to check the key is accepted, so a " +
                        "key typed wrongly is found now rather than on the first paid run.",
                ),
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
                about = infoNote(
                    "What to send",
                    "Which parts of a chat leave this phone, and in what language the answer " +
                        "comes back. These are the defaults a run starts from; the Analyze " +
                        "screen can still change them for one run.",
                    "Every type ticked here is content sent to the AI provider, so this is also " +
                        "the setting that decides what a run costs: images are the expensive " +
                        "half, and a channel that posts only text needs none of them.",
                ),
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
                SettingLabel("Default Telegram content")
                AnalysisContentType.entries.forEach { type ->
                    SettingToggle(
                        label = when (type) {
                            AnalysisContentType.TEXT -> "Text messages"
                            AnalysisContentType.IMAGES -> "Images and photos"
                            AnalysisContentType.AUDIO -> "Voice messages"
                        },
                        checked = type in appState.appPreferences.defaultContentTypes,
                        onCheckedChange = { appState.toggleDefaultContentType(type) },
                    )
                }
                SettingLabel(
                    "Cloud response timeout: ${appState.appPreferences.responseTimeoutSeconds} seconds",
                    infoNote(
                        "Cloud response timeout",
                        "How long the model may think about one batch of images. It sends nothing " +
                            "until it has finished, so a batch that needs longer than this is hung " +
                            "up on.",
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = appState.appPreferences.responseTimeoutSeconds.toFloat(),
                    onValueChange = {
                        val seconds = (it / 30f).roundToInt() * 30
                        appState.updateResponseTimeout(seconds)
                    },
                    valueRange = ResponseTimeout.MIN.toFloat()..ResponseTimeout.MAX.toFloat(),
                    steps = (ResponseTimeout.MAX - ResponseTimeout.MIN) / 30 - 1,
                )
            }
            }

            SubSection(
                "Wording",
                summary = appState.ruleSet.all.count { it.origin == RuleOrigin.USER }.let { mine ->
                    if (mine == 0) "Built-in wording only" else "$mine added"
                },
                about = WordingFlowNote,
            ) {
            AnalysisRulesSection(appState)
            }

            SubSection(
                "Prompt",
                about = GeneratedPromptNote,
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
                about = infoNote(
                    "Validation",
                    "What happens to an answer that comes back malformed. A correction retry " +
                        "hands the model its own reply and the error, and asks again - each one " +
                        "is another paid request, which is why the ceiling is two.",
                    "The catalog is the list of Cairo listings held on this phone. Enriching " +
                        "against it fills in a company's names and codes from that list rather " +
                        "than from the model, so a ticker read off a screenshot is checked " +
                        "against something that cannot be misremembered. It costs nothing and " +
                        "sends nothing.",
                ),
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                SettingLabel(
                    "Correction retries: ${appState.appPreferences.correctionRetries}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = appState.appPreferences.correctionRetries.toFloat(),
                    onValueChange = { appState.updateCorrectionRetries(it.roundToInt()) },
                    valueRange = 0f..2f,
                    steps = 1,
                )
                SettingToggle(
                    label = "Enrich results with the on-device EGX catalog",
                    checked = appState.appPreferences.catalogEnrichmentEnabled,
                    onCheckedChange = appState::updateCatalogEnrichment,
                )
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

            // Ask AI shares the provider and the key with a run and nothing else - not the
            // prompt, not the model, not the wording rules - so it sits last, after everything
            // a run actually uses.
            SubSection(
                "Ask AI",
                summary = askAiSummary,
                about = infoNote(
                    "Ask AI",
                    "The button on a call card in Insights. It asks what the model makes of the " +
                        "stock at today's price and what it makes of the levels the channel " +
                        "printed, and it answers in Arabic.",
                    "Each press is one paid request, confirmed first, and the answer is kept on " +
                        "the card - opening it again costs nothing.",
                ),
            ) {
            OutlinedTextField(
                value = askModel,
                onValueChange = appState::updateOpinionModel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true,
                // Inside the field rather than on a line of its own: the note is about what to type
                // here, and a row above the field would put a heading between the label and it.
                trailingIcon = {
                    InfoButton(
                        infoNote(
                            "Which model to name",
                            "A text model, not the vision one a run needs. The analysis reads " +
                                "screenshots; this request carries no image, and paying vision " +
                                "rates for it buys nothing.",
                        ),
                    )
                },
            )
            if (appState.availableModels.isNotEmpty()) {
                Box {
                    OutlinedButton(onClick = { askModelMenuOpen = true }) { Text("Choose model") }
                    DropdownMenu(
                        expanded = askModelMenuOpen,
                        onDismissRequest = { askModelMenuOpen = false },
                    ) {
                        // The list the analysis picker loaded, because it is the same key asking
                        // the same provider what it has. Typing over it stays allowed: a provider
                        // that lists nothing still has models.
                        appState.availableModels.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.id) },
                                onClick = {
                                    appState.updateOpinionModel(option.id)
                                    askModelMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
            SettingToggle(
                label = "Let it search the web",
                checked = askSearching,
                onCheckedChange = appState::updateOpinionSearch,
                about = infoNote(
                    "Let it search the web",
                    "Without a search the model has nothing this app does not already have, so its " +
                        "view rests on the call's own figures and on whatever it learned before it " +
                        "was trained. With one it can find real news, and can also repeat " +
                        "something unverified it found.",
                    "It costs more per press and takes longer. Only Qwen and OpenRouter are asked " +
                        "to search; other providers ignore it.",
                ),
            )
            // Everything below only bears on a searched request, so it is hidden with the search
            // rather than left greyed out under an unticked box.
            if (askSearching) {
                SettingLabel(
                    "How far back it looks for news",
                    infoNote(
                        "How far back it looks for news",
                        "The window is a lookback, and a shorter one is not a worse one: anything " +
                            "it brings back from a fortnight is genuinely current, and on most " +
                            "days most stocks will have nothing in it. An empty list is reported " +
                            "as an empty list rather than filled from further back.",
                        "Widen it when you want to know why a stock has moved rather than what " +
                            "happened this week. What is already scheduled ahead - results, " +
                            "coupons, assemblies - is reported whatever this is set to.",
                    ),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    NEWS_WINDOWS.forEach { days ->
                        FilterChip(
                            selected = askWindow == days,
                            onClick = { appState.updateOpinionNewsWindow(days) },
                            label = { Text("$days days") },
                        )
                    }
                }
                SettingLabel(
                    "How many results it reads",
                    infoNote(
                        "How many results it reads",
                        "This is what the search costs. Every result is put into the request " +
                            "whole, so twelve of them is several thousand characters the model is " +
                            "charged for reading before it answers.",
                        "Five is the provider's own default and was finding one usable Arabic item " +
                            "per press; twelve is why it now finds several. Turn it down when the " +
                            "bill matters more than the second source.",
                    ),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    SEARCH_RESULTS.forEach { count ->
                        FilterChip(
                            selected = askResults == count,
                            onClick = { appState.updateOpinionSearchResults(count) },
                            label = { Text("$count") },
                        )
                    }
                }
                SettingToggle(
                    label = "Search deeply",
                    checked = askDeep,
                    onCheckedChange = appState::updateOpinionDeepSearch,
                    about = infoNote(
                        "Search deeply",
                        "One pass finds that the company exists. This asks the model to read what " +
                            "it found and search again on it, which is what turns up a disclosure " +
                            "nobody wrote a headline about.",
                        "It costs more and takes longer, and a model that will not accept it is " +
                            "asked again without it rather than failing.",
                    ),
                )
            }
            }

            // Last in the section because it is about every request the section can start - a
            // run's chunks and its consolidation, and each Ask AI above.
            SubSection(
                "Token usage",
                summary = tokenUsageSummary(appState.modelUsage),
                about = infoNote(
                    "Token usage",
                    "Every request comes back with the token count the provider billed it at. " +
                        "This is those counts added up per model, on this phone alone - it is " +
                        "not synced, because a token count describes one phone's spending.",
                    "A record, not a limit: nothing here stops or slows a run. Clearing it " +
                        "forgets the record and not the spending, and the provider's own billing " +
                        "page remains the account that matters.",
                ),
            ) {
            ModelUsageSection(appState)
            }
        }

        SchedulesSettingsSection(appState, FormWidth)

        ExpandableSection(
            "Telegram",
            icon = Icons.Outlined.Forum,
            summary = if (appState.telegramAuthState.step == TelegramAuthStep.READY) "Signed in · ${appState.channels.size} chats" else "Not connected",
            contentMaxWidth = FormWidth,
            summaryTone = if (appState.telegramAuthState.step == TelegramAuthStep.READY) null else MaterialTheme.colorScheme.error,
            about = infoNote(
                "Telegram",
                "The account is how this app reads anything at all: the chats ticked on Analyze " +
                    "are read as you, on this device, and no chat you have not ticked is opened.",
                "It is also where your saved reports are kept - in a private channel of your own - " +
                    "so signing out here stops both. Sending them there is under General.",
            ),
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

        // Everything the phone can say out loud, in one card. All seven of these lived in Trades,
        // which was true of the first two and increasingly untrue of the rest: a feed that has gone
        // quiet and a schedule that did not fire are the app reporting on itself, and neither has
        // anything to do with how long to hold a position. They are one card because they are one
        // decision - how much this app is allowed to interrupt - and because the switch somebody
        // came here to turn off is now under the heading naming what they came to stop.
        //
        // Grouped rather than listed, because seven switches in a row is a list nobody reads to the
        // end of. The three headings answer the question a reader actually arrives with, which is
        // never "which of these seven" but "what kind of thing keeps interrupting me".
        ExpandableSection(
            "Notifications",
            icon = Icons.Outlined.NotificationsNone,
            summary = "$notificationsOn of $NOTIFICATION_COUNT on",
            contentMaxWidth = FormWidth,
            about = infoNote(
                "Notifications",
                "Each of these reports something that has already happened - a level reached, a " +
                    "deadline passed, a run that did not fire. None of them says what to do about " +
                    "it, and none of them can start a run or spend anything.",
                "Android silences a whole channel at a time, so these arrive on several channels " +
                    "rather than one: muting news about your trades cannot quietly mute the app " +
                    "telling you it has stopped working.",
            ),
        ) {
            SubSection(
                "Your trades",
                summary = tradeNotificationsSummary,
                about = infoNote(
                    "Your trades",
                    "The three about positions you actually hold. Two of them arrive after the " +
                        "fact - a stop announced is a stop already taken - and the third is the " +
                        "only thing on this card that reaches you while you can still decide " +
                        "something.",
                ),
            ) {
                SettingToggle(
                    label = "Tell me when a trade goes past its deadline",
                    checked = appState.appPreferences.overdueRemindersEnabled,
                    onCheckedChange = appState::updateOverdueReminders,
                    about = infoNote(
                        "Tell me when a trade goes past its deadline",
                        "Once a day, and only when something is actually overdue - a trade whose " +
                            "deadline has passed with no sale recorded.",
                        "Nothing is analyzed, so this never spends anything on the cloud. With either " +
                            "of these two switches on, the phone does fetch prices once after the " +
                            "exchange closes, so it knows what the session did before it says anything.",
                    ),
                )
                SettingToggle(
                    label = "Tell me when the market changes a trade",
                    checked = appState.appPreferences.tradeAlertsEnabled,
                    onCheckedChange = appState::updateTradeAlerts,
                    about = infoNote(
                        "Tell me when the market changes a trade",
                        "A target reached, the stop taken, the deadline passed - whenever the market " +
                            "moves one of your trades.",
                        "The phone looks once at 14:45, after the exchange has closed and its figures " +
                            "have settled, so a deadline that ran out this afternoon is said this " +
                            "afternoon. It looks again on every price refresh, and once more each day " +
                            "in case the alarm was dropped.",
                        "Not for anything you do yourself: recording a sale or closing a trade says " +
                            "nothing back to you.",
                    ),
                )
                SettingToggle(
                    label = "Warn me before a trade reaches a level",
                    checked = appState.appPreferences.approachAlertsEnabled,
                    onCheckedChange = appState::updateApproachAlerts,
                    about = infoNote(
                        "Warn me before a trade reaches a level",
                        "Everything else here tells you after the fact - a stop announced is a stop " +
                            "already taken. This is the one that arrives while you can still decide " +
                            "something: a trade of yours has come within reach of its stop, or of " +
                            "target 2.",
                        "It says the distance and never what to do about it. Off unless you switch it " +
                            "on, because a price near a level goes on being near it, and this is the " +
                            "noisiest thing the app can say.",
                        "It books no extra work: the check rides a recompute that was happening anyway.",
                    ),
                )
                if (appState.appPreferences.approachAlertsEnabled) {
                    val threshold = appState.appPreferences.approachThresholdPercent
                    SettingLabel(
                        "Close enough to warn about: $threshold%",
                        style = MaterialTheme.typography.bodyLarge,
                        about = infoNote(
                            "Close enough to warn about",
                            "How near the price has to get before the warning above is raised, as a " +
                                "percentage of the price itself.",
                            "There is no right answer for everybody: a tight stop on a liquid large " +
                                "cap and a wide one on a thin mid cap mean different things by close.",
                        ),
                    )
                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = { appState.updateApproachThreshold(it.roundToInt()) },
                        valueRange = ApproachAlerts.MIN_THRESHOLD_PERCENT.toFloat()..
                            ApproachAlerts.MAX_THRESHOLD_PERCENT.toFloat(),
                        steps = ApproachAlerts.MAX_THRESHOLD_PERCENT -
                            ApproachAlerts.MIN_THRESHOLD_PERCENT - 1,
                    )
                }
            }

            SubSection(
                "Calls and sessions",
                summary = marketNotificationsSummary,
                about = infoNote(
                    "Calls and sessions",
                    "The two about the market rather than about your money: a call you did not " +
                        "take reaching its entry band, and what the whole session did.",
                    "Both are off unless you switch them on, and neither depends on your holding " +
                        "anything.",
                ),
            ) {
                SettingToggle(
                    label = "Tell me when a stock reaches a buy zone",
                    checked = appState.appPreferences.callAlertsEnabled,
                    onCheckedChange = appState::updateCallAlerts,
                    about = infoNote(
                        "Tell me when a stock reaches a buy zone",
                        "For calls you have not taken: the price has traded into the entry band a " +
                            "channel printed. Off unless you switch it on - it is the one notification " +
                            "here about something you have not committed to.",
                        "It says what the market did and never what to do about it, and it books no " +
                            "extra work: the check rides a price refresh that was happening anyway.",
                    ),
                )
                SettingToggle(
                    label = "Tell me what the session did",
                    checked = appState.appPreferences.sessionDigestEnabled,
                    onCheckedChange = appState::updateSessionDigest,
                    about = infoNote(
                        "Tell me what the session did",
                        "One line after the close: how many calls reached targets, how many were " +
                            "stopped out, how many ran out of time, and how many of them were yours.",
                        "The gap it fills is the session where nothing of yours moved. Everything " +
                            "else here is about one trade or one call, so an afternoon when three " +
                            "calls from the channels you read hit targets and you held none of them " +
                            "passed in silence.",
                        "Off unless you switch it on: it arrives on a rhythm rather than because " +
                            "something happened, which is the kind of notification that wears out " +
                            "fastest. Said once per session, whatever else happens that evening.",
                    ),
                )
            }

            SubSection(
                "The app itself",
                summary = appNotificationsSummary,
                about = infoNote(
                    "The app itself",
                    "The two that report this app being unable to do its job, rather than " +
                        "anything the market did.",
                    "Both are on by default, and that is deliberate: the way each of these breaks " +
                        "is silence, so an app that stopped working quietly would look exactly " +
                        "like a quiet week.",
                ),
            ) {
                SettingToggle(
                    label = "Tell me when the price feed goes quiet",
                    checked = appState.appPreferences.feedAlertsEnabled,
                    onCheckedChange = appState::updateFeedAlerts,
                    about = infoNote(
                        "Tell me when the price feed goes quiet",
                        "A stock whose prices have stopped arriving looks exactly like a stock that " +
                            "has not moved - the feed answers every request and its newest session " +
                            "stays put. Meanwhile every rate on the record quietly rests on fewer " +
                            "calls than it looks like.",
                        "This has happened here once already and nothing noticed at the time.",
                        "On by default, because it reports the app being unable to do its job rather " +
                            "than something the market did. Said once when it starts, and not again " +
                            "until the feed comes back and goes quiet a second time. Prices under " +
                            "General says how many stocks are affected and how much of the record " +
                            "they are holding.",
                    ),
                )
                SettingToggle(
                    label = "Tell me when a scheduled run did not happen",
                    checked = appState.appPreferences.scheduleAlertsEnabled,
                    onCheckedChange = appState::updateScheduleAlerts,
                    about = infoNote(
                        "Tell me when a scheduled run did not happen",
                        "A schedule that was due and was missed, or one that failed - with the reason " +
                            "it gives. The way a schedule breaks on this platform is silence: nothing " +
                            "fires and nothing says so, and the two permissions that stop one working " +
                            "are named only on this screen.",
                        "It never mentions a run that was deliberately skipped, so it cannot become a " +
                            "daily reminder that paid runs are switched off.",
                        "It offers no way to start a run: that would be a way to spend from the lock " +
                            "screen, and starting one is always your own decision.",
                    ),
                )
            }
        }

        // The four settings that were a card each. Appearance, the trade window, Sync and the
        // price refresh had one control apiece, so each of them cost a card header, a summary line
        // and a tap to reach a single dropdown or a single button - four cards that could not be
        // told apart at a glance because each said nothing but its own name.
        //
        // They are not one subject and this card does not pretend they are. What they have in
        // common is that none of them is worth a card, which is what a General is for. The two at
        // the bottom do belong together: Sync and Prices are the free, unpaid ways this device
        // keeps its own copy current, and neither sends anything to the AI provider.
        ExpandableSection(
            "General",
            icon = Icons.Outlined.Tune,
            summary = "Theme, trade defaults, sync and prices",
            contentMaxWidth = FormWidth,
        ) {
            SubSection(
                "Appearance",
                summary = appState.appPreferences.themeMode.displayName,
                about = infoNote(
                    "Appearance",
                    "The choice is applied immediately on outer and inner displays.",
                ),
            ) {
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
            }

            // "Trade defaults" rather than "Trades": every control in here is about the user's own
            // trades, and the one that is left is a default rather than a rule. The window stopped
            // judging the channels - a call is followed until it reaches a target or the stop, and
            // nothing on this page moves that - so what is left is the one deadline anybody sets.
            SubSection(
                "Trade defaults",
                summary = "${appState.appPreferences.defaultTradeWindowSessions} trading sessions",
                about = infoNote(
                    "Trade defaults",
                    "What a new trade is offered when you press Bought. Nothing here changes " +
                        "anything already recorded, and nothing here affects how the sources are " +
                        "scored.",
                ),
            ) {
                val window = appState.appPreferences.defaultTradeWindowSessions
                SettingRow(
                    about = infoNote(
                        "Default trade window",
                        "What a new trade's deadline is offered as when you press Bought, counted " +
                            "from the session the call was made for. You can type over it there, " +
                            "or later from Edit trade.",
                        "It changes nothing already recorded, and it does not affect how the " +
                            "sources are scored - a call is followed until it reaches a target or " +
                            "the stop, which is what Insights reports the timings of.",
                    ),
                ) {
                    Text("Default trade window", modifier = Modifier.weight(1f))
                    Text(
                        "$window ${if (window == 1) "session" else "sessions"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = window.toFloat(),
                    onValueChange = { appState.updateDefaultTradeWindow(it.roundToInt()) },
                    valueRange = Scoring.MIN_WINDOW_SESSIONS.toFloat()..
                        Scoring.MAX_WINDOW_SESSIONS.toFloat(),
                    steps = Scoring.MAX_WINDOW_SESSIONS - Scoring.MIN_WINDOW_SESSIONS - 1,
                )
            }

            SubSection(
                "Sync",
                summary = "${appState.savedResults.size} reports on this device",
                about = infoNote(
                    "Sync",
                    "Reports are kept in a private Telegram channel of your own, so every device " +
                        "signed in to your account sees the same history.",
                    "A saved report never changes, so syncing only ever adds - nothing is overwritten " +
                        "and nothing is deleted.",
                ),
            ) {
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

            PricesSubSection(appState)
        }

        ExpandableSection(
            "Saved data and privacy",
            icon = Icons.Outlined.Shield,
            summary = "${appState.savedResults.size} saved analyses",
            contentMaxWidth = FormWidth,
            about = infoNote(
                "Saved data and privacy",
                "Provider keys and the Telegram database key are encrypted using Android " +
                    "Keystore. App backup is disabled and cloud requests use HTTPS.",
            ),
        ) {
            // Backup here rather than beside Save diagnostics in About, which is the other thing
            // that writes the record to a file. That one is for whoever is chasing a bug; this is
            // about the record itself, and it belongs with the section that says how much of it
            // there is and offers to delete it.
            SubSection(
                "Backup",
                summary = if (appState.backupFolder == null) {
                    "No folder chosen"
                } else {
                    "A folder is chosen"
                },
                about = infoNote(
                    "Backup",
                    "The whole record as one file: every saved analysis, every trade, and the " +
                        "settings - but no provider key and no Telegram key, which are encrypted " +
                        "separately by Android Keystore and have never been part of it.",
                    "Restoring one adds what it holds to what is already here rather than " +
                        "replacing it, so a backup taken on another phone can be read in without " +
                        "losing this one's own history.",
                ),
            ) {
                Text("${appState.savedResults.size} analyses saved on this device")
                BackupControls(appState)
            }

            // Its own group, at the bottom, with nothing else in it. The one irreversible button
            // on this page should not sit at the end of a list of buttons that are not.
            SubSection(
                "Delete",
                summary = "${appState.savedResults.size} saved analyses",
                about = infoNote(
                    "Delete",
                    "This is the only thing in the app that removes saved analyses, and it " +
                        "removes all of them - from this device, from your Telegram sync channel, " +
                        "and from every other device that syncs with it.",
                    "Take a backup first if there is any doubt: nothing here can be undone, and " +
                        "sync cannot bring back what it has been told to forget.",
                ),
            ) {
                OutlinedButton(
                    onClick = { confirmDeleteAll = true },
                    enabled = appState.savedResults.isNotEmpty(),
                ) {
                    Text("Delete all saved analyses")
                }
            }
        }

        ExpandableSection(
            "About",
            icon = Icons.Outlined.Info,
            summary = when (val state = appState.updateState) {
                is UpdateState.Available -> "Version ${state.update.versionName} available"
                is UpdateState.Ready -> "Version ${state.update.versionName} ready"
                else -> BuildConfig.VERSION_NAME
            },
            // The one thing on this card worth opening it for, when there is one.
            summaryTone = when (appState.updateState) {
                is UpdateState.Available, is UpdateState.Ready -> MaterialTheme.colorScheme.primary
                else -> null
            },
            contentMaxWidth = FormWidth,
            about = infoNote(
                "About",
                "Which build this phone is running, whether there is a newer one, and the button " +
                    "that puts this device's record into Downloads for somebody chasing a bug.",
                "Updates are read from this app's own GitHub releases. Nothing is downloaded or " +
                    "installed without you pressing for it, twice.",
            ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                SettingLabel(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    infoNote(
                        "Version",
                        "Shown so a device can be asked which build it is running.",
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                UpdateControls(appState)
                DiagnosticsControl(appState)
            }
        }
    }
}

/**
 * Puts this device's record into Downloads, so a problem can be read off the phone that has it.
 *
 * The only way off a release-signed build: `run-as` refuses a package that is not debuggable and
 * `adb backup` is closed by `allowBackup="false"`, while installing a debug build to get at the
 * data would mean uninstalling this one and taking the data with it.
 *
 * Off the main thread - it copies a file that grows with the record - and nothing opens afterwards,
 * so the status message is the only sign it worked. It names what Downloads actually created, which
 * differs from what was asked for the second time it is saved on one day.
 */
@Composable
private fun DiagnosticsControl(appState: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    // A row rather than a column, now that what sat under the button is behind the question mark
    // beside it: one line where there were four.
    SettingRow(
        about = infoNote(
            "Save diagnostics",
            "Copies this device's saved record into Downloads.",
            "No provider key and no Telegram key travels in it - those are encrypted " +
                "separately by Android Keystore and have never been part of it.",
        ),
    ) {
        OutlinedButton(
            enabled = !saving,
            onClick = {
                scope.launch {
                    saving = true
                    runCatching {
                        withContext(Dispatchers.IO) {
                            saveDatabaseToDownloads(
                                context,
                                appState.databaseFile(),
                                appState::checkpointDatabase,
                            )
                        }
                    }
                        .onSuccess {
                            appState.statusMessage =
                                StatusMessage("Saved to Downloads/$it", succeeded = true)
                        }
                        .onFailure {
                            appState.statusMessage = StatusMessage(
                                it.message?.takeIf(String::isNotBlank)
                                    ?: "Could not save diagnostics",
                                succeeded = false,
                            )
                        }
                    saving = false
                }
            },
        ) {
            Text(if (saving) "Saving…" else "Save diagnostics")
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * Finding, fetching and installing a newer build, which is all of it as the user meets it.
 *
 * Two deliberate taps, Download then Install, and nothing between them happens on its own. The
 * second hands the file to Android's installer, which asks again in its own words - an app that
 * could replace itself unasked is what the permission behind this exists to prevent.
 */
@Composable
private fun UpdateControls(appState: AppState) {
    val context = LocalContext.current
    val state = appState.updateState
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading

    // Re-read on the way back rather than only at composition. The permission is granted on a
    // system page, and returning from one does not recompose a card on its own - so the button went
    // on saying the thing it said before the user did what it asked.
    var canInstall by remember { mutableStateOf(appState.canInstallUpdates()) }
    LifecycleResumeEffect(Unit) {
        canInstall = appState.canInstallUpdates()
        onPauseOrDispose { }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        when (state) {
            UpdateState.Idle -> Unit

            UpdateState.Checking -> Text(
                "Checking GitHub…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is UpdateState.UpToDate -> Text(
                "Version ${state.versionName} is the newest release.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is UpdateState.Available -> {
                Text(
                    "Version ${state.update.versionName} · ${state.update.sizeLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (state.update.notes.isNotBlank()) {
                    // Capped rather than scrolled: this is a settings card, and what does not fit
                    // is on the release page the button below opens.
                    Text(
                        state.update.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(onClick = { appState.downloadUpdate(state.update) }) { Text("Download") }
            }

            is UpdateState.Downloading -> {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Downloading ${state.update.versionName} · " +
                        "${(state.progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is UpdateState.Ready -> {
                Text(
                    "Version ${state.update.versionName} is downloaded and signed by the same key " +
                        "as this build.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!canInstall) {
                    Text(
                        "Android has to allow this app to install apps first. The button opens that " +
                            "setting; come back here afterwards and press Install.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        // Read at the tap as well as on resume: whichever way the permission was
                        // granted, the button must do the right thing the moment it is pressed.
                        if (appState.canInstallUpdates()) {
                            appState.installUpdate(state.file)
                            return@Button
                        }
                        // A refusal here was invisible: Android closed the page without a word and
                        // the phone looked like it had ignored the button.
                        runCatching {
                            appState.installPermissionIntent()?.let(context::startActivity)
                        }.onFailure { error ->
                            appState.reportUpdateProblem(
                                error.message?.takeIf(String::isNotBlank)
                                    ?: "Android would not open the installer.",
                            )
                        }
                    },
                    // Two taps, and the label says which one this is. "Install" on a button that
                    // opens a settings page is how someone grants the permission, comes back, and
                    // believes the install has silently failed.
                ) { Text(if (canInstall) "Install" else "Allow installs") }
            }

            is UpdateState.Failed -> Text(state.reason, color = MaterialTheme.colorScheme.error)
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            OutlinedButton(enabled = !busy, onClick = appState::checkForUpdate) {
                Text(if (state is UpdateState.Idle) "Check for updates" else "Check again")
            }
            if (state is UpdateState.Available || state is UpdateState.Ready) {
                OutlinedButton(
                    onClick = { appState.releasesPageIntent()?.let(context::startActivity) },
                ) { Text("Release page") }
            }
            if (state is UpdateState.Failed || state is UpdateState.UpToDate) {
                TextButton(onClick = appState::dismissUpdate) { Text("Dismiss") }
            }
        }

        SettingToggle(
            label = "Check for updates when the app opens",
            checked = appState.appPreferences.updateChecksEnabled,
            onCheckedChange = appState::updateAutomaticUpdateChecks,
            about = infoNote(
                "Check for updates when the app opens",
                "One request to GitHub, and it says nothing unless there is a newer build. " +
                    "Nothing is downloaded or installed without you pressing for it.",
            ),
        )
    }
}

/** Wide enough for a long model name, narrow enough that a field still reads as a field. */
/**
 * The lookbacks offered, in days.
 *
 * Four rather than a free number field. The exact figure never mattered - what matters is whether
 * the reader is asking about this week, this quarter or this year - and a text box invites a 3 that
 * returns nothing on every stock.
 */
private val NEWS_WINDOWS = listOf(15, 30, 90, 180)

/**
 * The result counts offered.
 *
 * Five is the provider's own default, twelve is what the app ships, and twenty is the ceiling
 * `AnalysisRepository` clamps to - offering a number the request would quietly reduce would be a
 * setting that lies about what it does.
 */
private val SEARCH_RESULTS = listOf(5, 8, 12, 20)

/**
 * How many switches the Notifications card holds.
 *
 * Named rather than written into the summary, because "3 of 7 on" over a card showing eight
 * switches is the kind of wrong nobody notices for months.
 */
private const val NOTIFICATION_COUNT = 7

/**
 * "2 of 3 on", for a group of switches.
 *
 * Takes the flags rather than a count and a total, because those are two numbers a caller can get
 * out of step - which is the mistake [NOTIFICATION_COUNT] exists to stop the card making.
 */
private fun switchesOn(vararg flags: Boolean) = "${flags.count { it }} of ${flags.size} on"

private val FormWidth = 560.dp

/**
 * What each model has cost this phone, heaviest first.
 *
 * Per model rather than per run: the run's own total is on its report, and Ask AI leaves no report
 * at all, so a per-model tally is the only place the whole spend appears. Requests the provider
 * reported no usage for are named rather than folded in, because a total that is quietly short is
 * worse than one that says it is short.
 */
@Composable
private fun ModelUsageSection(appState: AppState) {
    // The tally is read from disk, so it is re-read when the section is opened rather than held
    // live: a scheduled run can have spent since this screen was built.
    LaunchedEffect(Unit) { appState.refreshModelUsage() }
    val usage = appState.modelUsage
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        if (usage.isEmpty()) {
            Text(
                "Nothing recorded yet. A run, or one Ask AI, writes what it cost here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val total = usage.fold(TokenUsage.NONE) { running, record -> running + record.usage }
        SettingLabel(
            "${groupedTokens(total.totalTokens)} tokens across ${usage.size} " +
                if (usage.size == 1) "model" else "models",
            style = MaterialTheme.typography.bodyLarge,
        )
        usage.forEach { record ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(record.model, style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull(
                        "${groupedTokens(record.usage.totalTokens)} tokens",
                        "${groupedTokens(record.usage.promptTokens)} in / " +
                            "${groupedTokens(record.usage.completionTokens)} out",
                        "${record.requests} requests",
                        record.lastUsed?.let {
                            "last ${AppDates.DayMonthYear.format(it.atZone(ZoneId.systemDefault()))}"
                        },
                    ).joinToString(" \u00b7 "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                record.unreportedRequests.takeIf { it > 0 }?.let {
                    Text(
                        "$it request(s) came back with no usage, so this total is short by them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    record.provider.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(onClick = appState::clearModelUsage) {
            Icon(Icons.Outlined.Delete, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Clear token usage")
        }
    }
}

/** The one line the closed section shows: what has been spent, and on how many models. */
private fun tokenUsageSummary(usage: List<ModelUsageRecord>): String {
    if (usage.isEmpty()) return "Nothing recorded"
    val total = usage.fold(TokenUsage.NONE) { running, record -> running + record.usage }
    return "${formatTokenCount(total.totalTokens)} tokens \u00b7 ${usage.size} " +
        if (usage.size == 1) "model" else "models"
}

/** Exact rather than rounded: this is the screen where the figure is being checked against a bill. */
private fun groupedTokens(value: Long): String = String.format(java.util.Locale.US, "%,d", value)
