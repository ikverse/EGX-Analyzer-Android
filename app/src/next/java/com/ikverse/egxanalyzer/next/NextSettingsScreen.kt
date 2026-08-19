package com.ikverse.egxanalyzer.next

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.ikverse.egxanalyzer.data.UpdateState
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.AppState
import kotlinx.coroutines.launch

/**
 * Settings - *how is this thing configured?*
 *
 * Nine sections of wildly different weight, and the problem this screen had to solve is that they
 * used to be siblings: a theme toggle and "delete every analysis you have" drawn as two rows of the
 * same list. So there are three weights here and they are visible before anything is read.
 *
 * - **Connected services** - Telegram and the model - carry a live state and are drawn as blocks
 *   with a spine in the colour of that state. They are the two things that can be *wrong*.
 * - **Ordinary sections** fold, and say nothing until opened.
 * - **The destructive zone is fenced**: dashed, red, its own frame, at the bottom, with the blast
 *   radius stated in words rather than implied by an icon.
 *
 * And About is a state machine rather than a version number - nine states, of which the one that
 * matters is that Download and Install are two deliberate taps and the button never says Install
 * when it would actually open a settings page.
 */
@Composable
internal fun NextSettingsScreen(
    activity: Activity,
    appState: AppState,
    page: NextPageState,
    contentPadding: PaddingValues,
) {
    val colors = LocalNextColors.current
    val scope = rememberCoroutineScope()

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item {
            NextScreenHeader(
                title = "Settings",
                holding = "${appState.cloudConfiguration.provider.displayName} · " +
                    appState.appPreferences.themeMode.displayName,
            )
        }

        // ---- The two connected services ---------------------------------------------------
        item { TelegramService(appState, scope) }
        item { ModelService(appState, scope) }

        // ---- The one inline preference ----------------------------------------------------
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .ruleTop(colors.rule)
                    .padding(vertical = NextMetrics.space5),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
            ) {
                Column(Modifier.weight(1f)) {
                    NextText("Appearance", NextType.name, colors.ink)
                    NextText(
                        "The app follows its own setting, not the phone's.",
                        NextType.meta,
                        colors.ink3,
                    )
                }
                ThemeMode.entries.forEach { mode ->
                    NextButton(
                        label = when (mode) {
                            ThemeMode.SYSTEM -> "Auto"
                            ThemeMode.LIGHT -> "Paper"
                            ThemeMode.DARK -> "Ink"
                        },
                        onClick = { appState.updateThemeMode(mode) },
                        tone = if (appState.appPreferences.themeMode == mode) {
                            colors.accent
                        } else {
                            colors.rule
                        },
                        labelColor = if (appState.appPreferences.themeMode == mode) {
                            colors.accent
                        } else {
                            colors.ink3
                        },
                        fill = if (appState.appPreferences.themeMode == mode) {
                            NextFill.WASH
                        } else {
                            NextFill.NONE
                        },
                        minHeight = 40.dp,
                    )
                }
            }
        }

        // ---- The ordinary sections ---------------------------------------------------------
        item {
            Section("Analysis", "What a new run starts with", page) {
                AnalysisContentType.entries.forEach { type ->
                    SettingSwitch(
                        label = when (type) {
                            AnalysisContentType.TEXT -> "Text messages"
                            AnalysisContentType.IMAGES -> "Images and photos"
                            AnalysisContentType.AUDIO -> "Voice messages"
                        },
                        on = type in appState.appPreferences.defaultContentTypes,
                        onToggle = { appState.toggleDefaultContentType(type) },
                    )
                }
                SettingSwitch(
                    label = "Enrich results with the on-device EGX catalog",
                    on = appState.appPreferences.catalogEnrichmentEnabled,
                    onToggle = { appState.updateCatalogEnrichment(it) },
                )
                NextText(appState.catalogMessage, NextType.meta, colors.ink3)
                NextButton(
                    label = "Refresh the EGX catalog",
                    onClick = { scope.launch { appState.refreshEgxCatalog() } },
                    tone = colors.rule,
                    minHeight = 40.dp,
                )
                ModelBehaviour(appState)
            }
        }

        item {
            Section(
                title = "Scoring",
                summary = "${appState.appPreferences.scoringWindowSessions} " +
                    "${appState.appPreferences.scoringWindowSessions.sessionWord()} per call",
                page = page,
            ) {
                NextText(
                    "How many trading sessions a call is replayed over. Moving this re-judges the " +
                        "whole record — every channel rate on Insights moves with it. Trades you " +
                        "have already taken keep the window they were recorded with.",
                    NextType.name,
                    colors.ink2,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(5, 10, 15, 20, 30).forEach { sessions ->
                        NextButton(
                            label = "$sessions",
                            onClick = { appState.updateScoringWindow(sessions) },
                            tone = if (appState.appPreferences.scoringWindowSessions == sessions) {
                                colors.market
                            } else {
                                colors.rule
                            },
                            labelColor = if (
                                appState.appPreferences.scoringWindowSessions == sessions
                            ) {
                                colors.market
                            } else {
                                colors.ink3
                            },
                            minHeight = 40.dp,
                        )
                    }
                }
            }
        }

        item {
            Section("Notifications", "The one thing that runs while the app is closed", page) {
                SettingSwitch(
                    label = "Tell me when a trade goes past its deadline",
                    on = appState.appPreferences.overdueRemindersEnabled,
                    onToggle = { appState.updateOverdueReminders(it) },
                )
                NextText(
                    "Once a day, no network, no Telegram. Switching it off cancels the work rather " +
                        "than letting it wake up and find nothing to say.",
                    NextType.meta,
                    colors.ink3,
                )
            }
        }

        item {
            Section("Prompt", "Generated, never hand-edited", page) {
                NextText(
                    "The instruction the model receives is composed from a shipped template plus " +
                        "the wording rules you have enabled. It is shown rather than editable, " +
                        "because a prompt that can be typed over is a prompt nobody can reproduce.",
                    NextType.name,
                    colors.ink2,
                )
                NextText(
                    appState.activePrompt.text.take(1_200),
                    NextType.meta,
                    colors.ink3,
                )
            }
        }

        item {
            Section("Wording rules", "The phrases that drop a stale card", page) {
                WordingRules(appState, page)
            }
        }

        item {
            Section("Diagnostics", "Getting a problem off this phone", page) {
                Diagnostics(activity, appState, scope)
            }
        }

        // ---- About: the state machine ------------------------------------------------------
        item { AboutCard(activity, appState) }

        // ---- The fence ----------------------------------------------------------------------
        item { DestructiveZone(appState) }
    }
}

/** A connected service, drawn with the weight of something that can be wrong. */
@Composable
private fun ServiceBlock(
    title: String,
    state: String,
    tone: Color,
    content: @Composable () -> Unit,
) {
    val colors = LocalNextColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.well)
            .spine(tone, 3.dp)
            .padding(
                start = NextMetrics.space5,
                end = NextMetrics.space5,
                top = NextMetrics.space5,
                bottom = NextMetrics.space5,
            )
            .padding(top = NextMetrics.space1),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
        ) {
            NextText(title, NextType.name, colors.ink, modifier = Modifier.weight(1f))
            NextChip(state, tone)
        }
        content()
    }
}

@Composable
private fun TelegramService(appState: AppState, scope: kotlinx.coroutines.CoroutineScope) {
    val colors = LocalNextColors.current
    val auth = appState.telegramAuthState
    val ready = auth.step == TelegramAuthStep.READY
    ServiceBlock(
        title = "Telegram",
        state = when (auth.step) {
            TelegramAuthStep.READY -> "Signed in"
            TelegramAuthStep.ERROR -> "Error"
            TelegramAuthStep.INITIALIZING -> "Starting"
            else -> "Signed out"
        },
        tone = when (auth.step) {
            TelegramAuthStep.READY -> colors.target
            TelegramAuthStep.ERROR -> colors.stop
            else -> colors.expired
        },
    ) {
        NextText(auth.message, NextType.meta, colors.ink2)
        auth.hint?.let { NextText(it, NextType.meta, colors.ink3) }

        if (ready) {
            NextText(
                "${appState.channels.size} chats known \u00b7 this build reads the record and never " +
                    "writes to it.",
                NextType.meta,
                colors.figMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3)) {
                NextButton(
                    label = "Refresh chats",
                    onClick = { scope.launch { appState.refreshTelegramChats() } },
                    tone = colors.rule,
                    minHeight = 40.dp,
                )
                NextButton(
                    label = "Sync now",
                    onClick = { scope.launch { appState.syncReports() } },
                    tone = colors.market,
                    labelColor = colors.market,
                    minHeight = 40.dp,
                )
                NextButton(
                    label = "Sign out",
                    onClick = { scope.launch { appState.logoutTelegram() } },
                    tone = colors.rule,
                    labelColor = colors.ink3,
                    minHeight = 40.dp,
                )
            }
        } else {
            SignIn(appState, scope)
        }
        appState.telegramSyncMessage?.let { NextText(it, NextType.meta, colors.ink3) }
    }
}

/**
 * Every way in, in the order they are actually met.
 *
 * QR first because it is the one that needs nothing typed on a phone, and the rest below it because
 * a code sent to the wrong number, a two-factor password and a first-time registration all happen
 * to somebody. It is seen roughly once per device, so it is built to work rather than to impress.
 */
@Composable
private fun SignIn(appState: AppState, scope: kotlinx.coroutines.CoroutineScope) {
    val colors = LocalNextColors.current
    val auth = appState.telegramAuthState
    var typed by remember(auth.step) { mutableStateOf("") }
    var second by remember(auth.step) { mutableStateOf("") }

    when (auth.step) {
        TelegramAuthStep.API_CONFIGURATION -> {
            NextText(
                "This build has no Telegram application credentials of its own. Register one at " +
                    "my.telegram.org and enter it here.",
                NextType.meta,
                colors.ink3,
            )
            NextField("api_id", typed, { typed = it })
            NextField("api_hash", second, { second = it }, numeric = false)
            NextButton(
                label = "Save and start",
                onClick = {
                    scope.launch { appState.saveTelegramApiConfiguration(typed, second) }
                },
                tone = colors.accent,
                fill = NextFill.WASH,
                minHeight = 40.dp,
            )
        }

        TelegramAuthStep.PHONE_NUMBER -> {
            auth.link?.let { QrCode(it) }
            NextButton(
                label = if (auth.link == null) "Show the sign-in code" else "New code",
                onClick = { scope.launch { appState.startTelegramQrSignIn() } },
                tone = colors.accent,
                labelColor = colors.accent,
                fill = NextFill.WASH,
                minHeight = 40.dp,
            )
            NextText(
                "In Telegram: Settings \u2192 Devices \u2192 Link Desktop Device, and scan this code.",
                NextType.meta,
                colors.ink3,
            )
            NextModalDivider()
            NextField("Or a phone number, with its country code", typed, { typed = it })
            NextButton(
                label = "Send me a code",
                onClick = { scope.launch { appState.submitTelegramPhone(typed) } },
                tone = colors.rule,
                minHeight = 40.dp,
                enabled = typed.isNotBlank(),
            )
        }

        TelegramAuthStep.VERIFICATION_CODE -> {
            NextField("The code Telegram sent", typed, { typed = it })
            NextButton(
                label = "Confirm",
                onClick = { scope.launch { appState.submitTelegramCode(typed) } },
                tone = colors.accent,
                fill = NextFill.WASH,
                minHeight = 40.dp,
                enabled = typed.isNotBlank(),
            )
        }

        TelegramAuthStep.TWO_FACTOR_PASSWORD -> {
            NextField("Your two-factor password", typed, { typed = it }, numeric = false)
            NextButton(
                label = "Confirm",
                onClick = { scope.launch { appState.submitTelegramPassword(typed) } },
                tone = colors.accent,
                fill = NextFill.WASH,
                minHeight = 40.dp,
                enabled = typed.isNotBlank(),
            )
        }

        TelegramAuthStep.EMAIL_ADDRESS -> {
            NextField("Your email address", typed, { typed = it }, numeric = false)
            NextButton(
                label = "Continue",
                onClick = { scope.launch { appState.submitTelegramEmail(typed) } },
                tone = colors.accent,
                fill = NextFill.WASH,
                minHeight = 40.dp,
                enabled = typed.isNotBlank(),
            )
        }

        TelegramAuthStep.EMAIL_CODE -> {
            NextField("The code sent to that address", typed, { typed = it })
            NextButton(
                label = "Confirm",
                onClick = { scope.launch { appState.submitTelegramEmailCode(typed) } },
                tone = colors.accent,
                fill = NextFill.WASH,
                minHeight = 40.dp,
                enabled = typed.isNotBlank(),
            )
        }

        TelegramAuthStep.REGISTRATION -> {
            NextText(
                "This number has no Telegram account yet. Registering one here creates it.",
                NextType.meta,
                colors.ink3,
            )
            NextField("First name", typed, { typed = it }, numeric = false)
            NextField("Last name", second, { second = it }, numeric = false)
            NextButton(
                label = "Register",
                onClick = { scope.launch { appState.registerTelegram(typed, second) } },
                tone = colors.accent,
                fill = NextFill.WASH,
                minHeight = 40.dp,
                enabled = typed.isNotBlank(),
            )
        }

        TelegramAuthStep.OTHER_DEVICE_CONFIRMATION -> {
            auth.link?.let { QrCode(it) }
            NextText(
                "Confirm this sign-in on the device already signed in.",
                NextType.meta,
                colors.ink3,
            )
        }

        TelegramAuthStep.ERROR -> {
            NextButton(
                label = "Try again",
                onClick = { scope.launch { appState.startTelegramQrSignIn() } },
                tone = colors.stop,
                labelColor = colors.stop,
                minHeight = 40.dp,
            )
        }

        TelegramAuthStep.INITIALIZING,
        TelegramAuthStep.LOGGING_OUT,
        TelegramAuthStep.READY,
        -> Unit
    }
}

/** The QR itself, drawn rather than described - it is the whole of the sign-in. */
@Composable
private fun QrCode(link: String) {
    val bitmap = remember(link) {
        runCatching {
            val size = 320
            val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, size, size)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[y * size + x] =
                        if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
                }
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
    if (bitmap != null) {
        // On white, always: a QR on this app's ground would be unreadable by half the scanners
        // that matter, and this is the one image in the app that has a job rather than a look.
        Box(
            Modifier
                .size(200.dp)
                .background(Color.White)
                .padding(NextMetrics.space4),
        ) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Telegram sign-in code")
        }
    }
}

@Composable
private fun ModelService(appState: AppState, scope: kotlinx.coroutines.CoroutineScope) {
    val colors = LocalNextColors.current
    var key by remember { mutableStateOf("") }
    val configuration = appState.cloudConfiguration
    val verified = appState.credentialVerified

    ServiceBlock(
        title = "Model",
        state = when {
            !configuration.hasCredential -> "No key"
            verified == true -> "Verified"
            verified == false -> "Refused"
            else -> "Saved"
        },
        tone = when {
            !configuration.hasCredential -> colors.expired
            verified == false -> colors.stop
            verified == true -> colors.target
            else -> colors.market
        },
    ) {
        NextFigureGrid(
            columns = 2,
            cells = listOf(
                { NextFigureCell("Provider", configuration.provider.displayName, colors.ink) },
                {
                    NextFigureCell(
                        "Model",
                        configuration.model.ifBlank { "No model chosen" },
                        colors.market,
                    )
                },
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3)) {
            CloudProvider.entries.forEach { provider ->
                NextButton(
                    label = provider.displayName,
                    onClick = { appState.selectProvider(provider) },
                    tone = if (configuration.provider == provider) colors.accent else colors.rule,
                    labelColor = if (configuration.provider == provider) {
                        colors.accent
                    } else {
                        colors.ink3
                    },
                    minHeight = 40.dp,
                )
            }
        }
        NextField(
            label = if (configuration.hasCredential) "Replace the saved key" else "API key",
            value = key,
            onValueChange = { key = it },
            numeric = false,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3)) {
            NextButton(
                label = "Save and verify",
                onClick = {
                    scope.launch {
                        appState.saveSettings(key)
                        key = ""
                        appState.verifyCredential()
                    }
                },
                tone = colors.accent,
                fill = NextFill.WASH,
                minHeight = 40.dp,
            )
            if (configuration.hasCredential) {
                NextButton(
                    label = "Remove the key",
                    onClick = { appState.removeCredential() },
                    tone = colors.stop,
                    labelColor = colors.stop,
                    minHeight = 40.dp,
                )
            }
        }
        appState.settingsMessage?.let {
            NextText(it, NextType.meta, if (verified == false) colors.stop else colors.ink3)
        }
        ModelChoice(appState, scope)
        NextText(
            "The key never syncs between devices. Putting a live cloud credential in a chat to " +
                "save typing one field once is a bad trade.",
            NextType.meta,
            colors.figMuted,
        )
    }
}

/** An ordinary section: folded, and silent until opened. */
@Composable
private fun Section(
    title: String,
    summary: String,
    page: NextPageState,
    content: @Composable () -> Unit,
) {
    val colors = LocalNextColors.current
    val open = title in page.openSections
    NextRecordCard(
        open = open,
        onToggle = {
            if (!page.openSections.remove(title)) page.openSections.add(title)
        },
        header = { chevron ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
            ) {
                NextText(title, NextType.name, colors.ink)
                NextChevron(chevron)
                Spacer(Modifier.weight(1f))
                NextText(
                    text = summary,
                    style = NextType.meta,
                    color = colors.figMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(NextMetrics.space4)) { content() }
        },
    )
}

/** A switch, which in this app is a word rather than a toggle track. */
@Composable
private fun SettingSwitch(label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalNextColors.current
    val press = rememberPress()
    val fill by press.pressFill(colors.well)
    Row(
        Modifier
            .fillMaxWidth()
            .background(fill)
            .ruleTop(colors.ruleSoft)
            .padding(vertical = NextMetrics.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        NextText(
            text = label,
            style = NextType.name,
            color = colors.ink2,
            modifier = Modifier.weight(1f),
        )
        NextButton(
            label = if (on) "On" else "Off",
            onClick = { onToggle(!on) },
            tone = if (on) colors.accent else colors.rule,
            labelColor = if (on) colors.accent else colors.ink3,
            fill = if (on) NextFill.WASH else NextFill.NONE,
            minHeight = 40.dp,
        )
    }
}

/**
 * About, which is nine states rather than a version number.
 *
 * The rule that matters: **Download and Install are two deliberate taps**, and the button never
 * says Install when it would actually open a settings page. Someone who grants the permission,
 * comes back, and finds the same button is entitled to conclude the install silently failed.
 */
@Composable
private fun AboutCard(activity: Activity, appState: AppState) {
    val colors = LocalNextColors.current
    val state = appState.updateState
    val tone = when (state) {
        is UpdateState.Failed -> colors.stop
        is UpdateState.Ready, is UpdateState.Available -> colors.expired
        is UpdateState.Downloading, UpdateState.Checking -> colors.market
        else -> colors.figMuted
    }
    ServiceBlock(
        title = "About",
        state = when (state) {
            UpdateState.Idle -> "Idle"
            UpdateState.Checking -> "Checking"
            is UpdateState.UpToDate -> "Up to date"
            is UpdateState.Available -> "Update available"
            is UpdateState.Downloading -> "Downloading"
            is UpdateState.Ready -> "Ready to install"
            is UpdateState.Failed -> "Failed"
        },
        tone = tone,
    ) {
        NextText(
            when (state) {
                UpdateState.Idle -> "This build is EGX Next."
                UpdateState.Checking -> "Checking GitHub…"
                is UpdateState.UpToDate -> "Version ${state.versionName} is the newest there is."
                is UpdateState.Available -> "Version ${state.update.versionName} available · " +
                    state.update.sizeBytes.let { "${it / 1_048_576} MB" }

                is UpdateState.Downloading ->
                    "Downloading ${state.update.versionName} · " +
                        "${(state.progress * 100).toInt()}%"

                is UpdateState.Ready -> "Version ${state.update.versionName} ready"
                is UpdateState.Failed -> state.reason
            },
            NextType.name,
            colors.ink2,
        )

        if (state is UpdateState.Downloading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(colors.ruleSoft),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(colors.market),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3)) {
            when (state) {
                is UpdateState.Available -> NextButton(
                    label = "Download",
                    onClick = { appState.downloadUpdate(state.update) },
                    tone = colors.accent,
                    fill = NextFill.WASH,
                    minHeight = 40.dp,
                )

                is UpdateState.Ready -> if (appState.canInstallUpdates()) {
                    NextButton(
                        label = "Install",
                        onClick = { appState.installUpdate(state.file) },
                        tone = colors.accent,
                        fill = NextFill.WASH,
                        minHeight = 40.dp,
                    )
                } else {
                    // Never "Install" for a button that opens a permission page.
                    NextButton(
                        label = "Allow installs",
                        onClick = {
                            val intent = appState.installPermissionIntent()
                            if (intent == null) {
                                appState.reportUpdateProblem(
                                    "Android would not open the install-permission page.",
                                )
                            } else {
                                runCatching { activity.startActivity(intent) }.onFailure {
                                    appState.reportUpdateProblem(
                                        "Android would not open the install-permission page.",
                                    )
                                }
                            }
                        },
                        tone = colors.expired,
                        labelColor = colors.expired,
                        minHeight = 40.dp,
                    )
                }

                else -> NextButton(
                    label = if (state is UpdateState.Checking) "Checking…" else "Check for updates",
                    onClick = { appState.checkForUpdate() },
                    tone = colors.rule,
                    minHeight = 40.dp,
                    enabled = state !is UpdateState.Checking,
                )
            }
            NextButton(
                label = "Release page",
                onClick = {
                    val intent = appState.releasesPageIntent()
                    if (intent == null) {
                        appState.reportUpdateProblem("No browser could open the release page.")
                    } else {
                        runCatching { activity.startActivity(intent) }.onFailure {
                            appState.reportUpdateProblem("No browser could open the release page.")
                        }
                    }
                },
                tone = colors.rule,
                labelColor = colors.ink3,
                minHeight = 40.dp,
            )
            if (state !is UpdateState.Idle) {
                NextButton(
                    label = "Dismiss",
                    onClick = { appState.dismissUpdate() },
                    tone = colors.rule,
                    labelColor = colors.ink3,
                    minHeight = 40.dp,
                )
            }
        }

        SettingSwitch(
            label = "Check for updates when the app opens",
            on = appState.appPreferences.updateChecksEnabled,
            onToggle = { appState.updateAutomaticUpdateChecks(it) },
        )
    }
}

/**
 * The fence.
 *
 * Dashed, red, its own frame and its own space at the bottom of the screen - because the one thing
 * on this screen that destroys the record must not be a sibling of the theme toggle. What goes and
 * what survives is stated in words before anything is pressed.
 */
@Composable
private fun DestructiveZone(appState: AppState) {
    val colors = LocalNextColors.current
    var armed by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = NextMetrics.space8, bottom = NextMetrics.space7)
            .dashedEdge(colors.stop)
            .padding(NextMetrics.space6),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        NextLabel("Saved data", color = colors.stop)
        NextText("Delete every analysis", NextType.name, colors.ink)
        NextText(
            "This removes all ${appState.savedResults.size} saved runs from this device. Every " +
                "channel rate on Insights is computed from them, so all of it goes with them. " +
                "Trades you recorded stay in Portfolio.",
            NextType.meta,
            colors.ink3,
        )
        if (!armed) {
            NextButton(
                label = "Delete all saved analyses",
                onClick = { armed = true },
                tone = colors.stop,
                labelColor = colors.stop,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3)) {
                NextHoldButton(
                    label = "Hold to delete everything",
                    holdingLabel = "Keep holding…",
                    onCommit = {
                        appState.deleteAllResults()
                        armed = false
                    },
                    tone = colors.stop,
                    modifier = Modifier.weight(1f),
                )
                NextButton(
                    label = "Keep it",
                    onClick = { armed = false },
                    tone = colors.rule,
                    labelColor = colors.ink3,
                )
            }
        }
    }
}
