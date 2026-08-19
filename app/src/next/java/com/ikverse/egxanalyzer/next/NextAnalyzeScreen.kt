package com.ikverse.egxanalyzer.next

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.ui.AppDestination
import com.ikverse.egxanalyzer.ui.AnalysisStatus
import com.ikverse.egxanalyzer.ui.AppState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Analyze - *what do I want read, and what will it cost me?*
 *
 * **The only screen in the app that spends the user's money.** A run sends their Telegram content to
 * a cloud provider and bills their account; it is not free and it is not undoable. Everything here
 * answers to that, and it is why this screen deliberately does not look like the record screens -
 * those are for reading, this one is for committing.
 *
 * Four numbered clauses assemble the run, and the commit refuses until all four are answered - each
 * with its **own** refusal naming the missing thing and where to fix it, because one greyed-out
 * button with no reason is the failure mode this screen exists to avoid.
 *
 * The commit itself is held rather than tapped. That is the whole answer to "make committing feel
 * different from browsing without a scary red button": the colour stays the app's own, and the
 * effort carries the warning.
 */
@Composable
internal fun NextAnalyzeScreen(
    activity: Activity,
    appState: AppState,
    page: NextPageState,
    contentPadding: PaddingValues,
) {
    val colors = LocalNextColors.current
    val scope = rememberCoroutineScope()

    val chatsChosen = appState.channels.count(ChannelSelection::selected)
    val typesChosen = appState.selectedContentTypes.size
    val hasKey = appState.cloudConfiguration.hasCredential
    val ready = chatsChosen > 0 && typesChosen > 0 && hasKey
    val running = appState.analysisStatus == AnalysisStatus.RUNNING

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The preview is the trust mechanism, so where there is room it is a pane that is simply
        // *there*. Below this it becomes a sheet, because a four-choice form squeezed beside a
        // message browser is neither.
        val panes = maxWidth >= 720.dp

        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(NextMetrics.space5),
            ) {
                NextScreenHeader(
                    title = "Analyze",
                    holding = "${appState.channels.size} chats known · " +
                        "${appState.cloudConfiguration.provider.displayName}",
                    trailing = if (running) "Running" else "Idle",
                )

                if (running) {
                    RunningBlock(appState, scope)
                }

                Clause(
                    number = "01",
                    title = "Which chats",
                    satisfied = chatsChosen > 0,
                    refusal = "Select at least one chat.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(NextMetrics.space3)) {
                        appState.channels.forEach { channel ->
                            ChoiceRow(
                                label = channel.displayName,
                                selected = channel.selected,
                                onClick = { appState.toggleChannel(channel) },
                            )
                        }
                        if (appState.channels.isEmpty()) {
                            NextEmpty(
                                kind = EmptyKind.INLINE,
                                title = "No chats known yet.",
                                body = "Refresh chats to read them from Telegram.",
                            )
                        }
                        NextButton(
                            label = "Refresh chats",
                            onClick = { scope.launch { appState.refreshTelegramChats() } },
                            tone = colors.rule,
                            minHeight = 40.dp,
                        )
                    }
                }

                Clause(
                    number = "02",
                    title = "Which session the calls are for",
                    satisfied = true,
                    refusal = null,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(NextMetrics.space3)) {
                        ChoiceRow(
                            label = "Current or next EGX session",
                            selected = appState.analysisMode == AnalysisMode.NEXT_DAY,
                            onClick = { appState.selectAnalysisMode(AnalysisMode.NEXT_DAY) },
                        )
                        ChoiceRow(
                            label = "A specific session · " +
                                formatFullDay(appState.recommendationTargetDate),
                            selected = appState.analysisMode == AnalysisMode.SPECIFIC_DATE,
                            onClick = {
                                appState.selectAnalysisMode(AnalysisMode.SPECIFIC_DATE)
                            },
                        )
                        if (appState.analysisMode == AnalysisMode.SPECIFIC_DATE) {
                            NextButton(
                                label = "Change \u00b7 ${formatFullDay(appState.recommendationTargetDate)}",
                                onClick = { page.pickingSession = true },
                                tone = colors.market,
                                labelColor = colors.market,
                                minHeight = 40.dp,
                            )
                        }
                        NextText(
                            "This is the session the recommendations are *for*, not the day the " +
                                "messages were posted. All dates are Cairo's.",
                            NextType.meta,
                            colors.ink3,
                        )
                    }
                }

                Clause(
                    number = "03",
                    title = "What to read",
                    satisfied = typesChosen > 0,
                    refusal = "Select at least one content type.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(NextMetrics.space3)) {
                        AnalysisContentType.entries.forEach { type ->
                            ChoiceRow(
                                label = when (type) {
                                    AnalysisContentType.TEXT -> "Text messages"
                                    AnalysisContentType.IMAGES -> "Images and photos"
                                    AnalysisContentType.AUDIO -> "Voice messages"
                                },
                                selected = type in appState.selectedContentTypes,
                                onClick = { appState.toggleContentType(type) },
                                note = if (type == AnalysisContentType.IMAGES) {
                                    "the expensive ones, and the ones most often misread"
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }

                Clause(
                    number = "04",
                    title = "Where it is sent",
                    satisfied = hasKey,
                    refusal = "Save the provider API key in Settings first.",
                    refusalAction = ShellAction("Open Settings") {
                        appState.navigate(AppDestination.SETTINGS)
                    },
                ) {
                    NextFigureGrid(
                        columns = 2,
                        cells = listOf(
                            {
                                NextFigureCell(
                                    "Provider",
                                    appState.cloudConfiguration.provider.displayName,
                                    colors.ink,
                                )
                            },
                            {
                                NextFigureCell(
                                    "Model",
                                    appState.cloudConfiguration.model,
                                    colors.market,
                                )
                            },
                        ),
                    )
                }

                // The commit, and the preview beside it: the trust mechanism sits with the act it
                // exists to justify rather than at the top of a form nobody scrolls back up.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .ruleTop(colors.ruleStrong)
                        .padding(top = NextMetrics.space5, bottom = NextMetrics.space7),
                    verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
                ) {
                    if (!ready) {
                        NextText(
                            text = when {
                                chatsChosen == 0 -> "Select at least one chat before running."
                                typesChosen == 0 -> "Select at least one content type."
                                else -> "Save the provider API key in Settings first."
                            },
                            style = NextType.meta,
                            color = colors.expired,
                        )
                    }
                    NextHoldButton(
                        label = "Hold to run this analysis",
                        holdingLabel = "Keep holding…",
                        onCommit = { appState.startAnalysis() },
                        enabled = ready && !running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NextText(
                        "A run sends the selected messages to " +
                            "${appState.cloudConfiguration.provider.displayName} and bills your " +
                            "account. It cannot be undone.",
                        NextType.meta,
                        colors.ink3,
                    )
                    NotificationsNote(activity)
                    if (!panes) {
                        NextButton(
                            label = "Preview the messages",
                            onClick = {
                                page.previewOpen = true
                                scope.launch { appState.syncTelegramSources() }
                            },
                            tone = colors.market,
                            labelColor = colors.market,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (panes) {
                Box(
                    Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .background(colors.well),
                ) {
                    PreviewPane(appState, scope, contentPadding)
                }
            }
        }
    }
}

/**
 * A run finishing while the app is in the background is the whole reason notifications matter here.
 *
 * Shown only while they are off, and it leads to the system page rather than claiming to switch
 * them on itself - which is the same rule the update card follows about never labelling a button
 * with something it cannot do.
 */
@Composable
private fun NotificationsNote(activity: Activity) {
    val colors = LocalNextColors.current
    val allowed = remember { NotificationManagerCompat.from(activity).areNotificationsEnabled() }
    if (allowed) return
    Row(
        Modifier
            .fillMaxWidth()
            .ruleTop(colors.expired)
            .padding(top = NextMetrics.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        NextText(
            "Notifications are off, so a run that finishes while you are elsewhere will not say so.",
            NextType.meta,
            colors.expired,
            modifier = Modifier.weight(1f),
        )
        NextButton(
            label = "Turn on notifications",
            onClick = {
                activity.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
                )
            },
            tone = colors.expired,
            labelColor = colors.expired,
            minHeight = 40.dp,
        )
    }
}

/**
 * One of the four things a run is made of.
 *
 * Numbered, because they are read as a sequence and a reader who is missing one needs to know which.
 * A satisfied clause is marked in the accent and says nothing; an unsatisfied one carries its own
 * refusal, in its own words, naming the thing that is missing and often where to go and fix it.
 */
@Composable
private fun Clause(
    number: String,
    title: String,
    satisfied: Boolean,
    refusal: String?,
    refusalAction: ShellAction? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalNextColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .ruleTop(if (satisfied) colors.rule else colors.expired)
            .padding(top = NextMetrics.space4),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
        ) {
            NextFigure(
                text = number,
                color = if (satisfied) colors.accent else colors.expired,
                style = NextType.columnLabel,
            )
            NextText(title, NextType.name, colors.ink, modifier = Modifier.weight(1f))
            if (!satisfied) NextChip("Needed", colors.expired, style = ChipStyle.DASHED)
        }
        content()
        if (!satisfied && refusal != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
            ) {
                NextText(refusal, NextType.meta, colors.expired, modifier = Modifier.weight(1f))
                refusalAction?.let {
                    NextButton(
                        label = it.label,
                        onClick = it.onClick,
                        tone = colors.expired,
                        labelColor = colors.expired,
                        minHeight = 40.dp,
                    )
                }
            }
        }
    }
}

/** A choice: selected or not, with the app's own press and no checkbox anywhere. */
@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    note: String? = null,
) {
    val colors = LocalNextColors.current
    val press = rememberPress()
    val fill by press.pressFill(colors.well)
    Row(
        Modifier
            .fillMaxWidth()
            .background(fill)
            .then(if (selected) Modifier.spine(colors.accent) else Modifier)
            .pressable(press, onClick)
            .padding(
                start = if (selected) NextMetrics.space4 else 0.dp,
                top = NextMetrics.space4,
                bottom = NextMetrics.space4,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        NextText(
            text = label,
            style = NextType.name,
            color = if (selected) colors.ink else colors.ink2,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (note != null) NextText(note, NextType.meta, colors.ink3)
        NextText(
            text = if (selected) "ON" else "OFF",
            style = NextType.navLabel,
            color = if (selected) colors.accent else colors.ink3,
        )
    }
}

/**
 * The run, while it is running.
 *
 * The elapsed clock is not decoration: runs take minutes, and a spinner with no number reads as a
 * hang. The figure itself never animates - it is replaced once a second, which is what a clock does.
 */
@Composable
private fun RunningBlock(
    appState: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val colors = LocalNextColors.current
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(appState.analysisStatus) {
        while (appState.analysisStatus == AnalysisStatus.RUNNING) {
            now = Instant.now()
            delay(1_000)
        }
    }
    val started = appState.analysisStartedAt
    val elapsed = started?.let { Duration.between(it, now) } ?: Duration.ZERO

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.accentFill)
            .ruleTop(colors.accent, NextMetrics.spine)
            .padding(NextMetrics.space5),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
        ) {
            NextLabel("Running", color = colors.accent)
            Spacer(Modifier.weight(1f))
            NextFigure(
                text = "%d:%02d".format(elapsed.toMinutes(), elapsed.seconds % 60),
                color = colors.ink,
                style = NextType.ticker,
            )
        }
        appState.analysisMessage?.let {
            NextText(it, NextType.meta, colors.ink2)
        }
        NextButton(
            label = "Cancel this run",
            onClick = { scope.launch { appState.cancelAnalysis() } },
            tone = colors.stop,
            labelColor = colors.stop,
        )
    }
}

/**
 * Exactly what a run will send.
 *
 * Genuinely inspectable rather than a count: every message that would go, with its chat, its time
 * and its own words. This is the answer to "what am I handing to a third party?", and a summary
 * would not be one.
 */
@Composable
internal fun PreviewPane(
    appState: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val colors = LocalNextColors.current
    val sources = appState.telegramSources
    LazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NextMetrics.space5),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .ruleBottom(colors.ruleStrong)
                    .padding(vertical = NextMetrics.space5),
                verticalArrangement = Arrangement.spacedBy(NextMetrics.space3),
            ) {
                NextLabel("What this run will send", color = colors.ink)
                NextText(
                    "${sources.size} ${if (sources.size == 1) "message" else "messages"} from " +
                        "${appState.channels.count(ChannelSelection::selected)} chats",
                    NextType.meta,
                    colors.figMuted,
                )
                NextButton(
                    label = "Refresh messages",
                    onClick = { scope.launch { appState.syncTelegramSources() } },
                    tone = colors.market,
                    labelColor = colors.market,
                    minHeight = 40.dp,
                )
            }
        }
        if (sources.isEmpty()) {
            item {
                NextEmpty(
                    kind = EmptyKind.INVITE,
                    title = "Nothing loaded yet",
                    body = "Refresh messages, and everything a run would send appears here first.",
                    modifier = Modifier.padding(top = NextMetrics.space5),
                )
            }
        }
        items(sources.size) { index ->
            SourceTile(sources[index])
        }
    }
}

@Composable
private fun SourceTile(trace: SourceTrace) {
    val colors = LocalNextColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .ruleTop(colors.ruleSoft)
            .padding(vertical = NextMetrics.space4),
        verticalArrangement = Arrangement.spacedBy(NextMetrics.space2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NextMetrics.space3),
        ) {
            NextText(
                text = trace.channelName,
                style = NextType.name,
                color = colors.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            NextText(
                text = trace.contentType.name.lowercase(Locale.ROOT),
                style = NextType.navLabel,
                color = colors.market,
            )
        }
        NextText(
            text = formatDay(
                trace.timestamp.atZone(ZoneId.systemDefault()).toLocalDate(),
            ),
            style = NextType.meta,
            color = colors.figMuted,
        )
        NextText(
            text = trace.preview.take(240),
            style = NextType.meta,
            color = colors.ink2,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The preview as a sheet, and the duplicate-run confirmation.
 *
 * Re-running a session is legitimate - a channel may have posted more since - so the duplicate is a
 * confirmation and never a block. It offers the run that already exists first, because most of the
 * time that is what was actually wanted.
 */
@Composable
internal fun NextAnalyzeSheets(appState: AppState, page: NextPageState) {
    val colors = LocalNextColors.current
    val scope = rememberCoroutineScope()

    appState.duplicateOfSelection?.let { existing ->
        NextModal(
            kicker = "You already ran this session",
            tone = colors.expired,
            onDismiss = appState::dismissDuplicateWarning,
            title = "Run it again anyway?",
            body = {
                NextText(
                    "This session was analysed on " +
                        formatFullDay(
                            existing.result.completedAt
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate(),
                        ) +
                        ". Running it again costs a full pass and adds a second, near-identical " +
                        "entry — the older one is kept and marked as superseded.",
                    NextType.name,
                    colors.ink2,
                )
                NextModalDivider()
                NextConsequence(
                    mark = "=",
                    markTone = colors.target,
                    what = "${existing.result.consolidated.size} stocks already extracted",
                    where = "Results",
                )
            },
            actions = {
                NextButton(
                    label = "Open the existing run",
                    onClick = {
                        appState.dismissDuplicateWarning()
                        appState.openSavedResult(existing.id)
                    },
                    tone = colors.accent,
                    fill = NextFill.SOLID,
                    modifier = Modifier.weight(1f),
                )
                NextButton(
                    label = "Run anyway",
                    onClick = { appState.startAnalysis(confirmed = true) },
                    tone = colors.expired,
                    labelColor = colors.expired,
                )
            },
        )
        return
    }

    if (page.pickingSession) {
        NextDateSheet(
            kicker = "Which session",
            initial = appState.recommendationTargetDate,
            onPick = appState::updateRecommendationTargetDate,
            onDismiss = { page.pickingSession = false },
            latest = LocalDate.now(),
        )
        return
    }

    if (page.previewOpen) {
        NextModal(
            kicker = "What this run will send",
            tone = colors.market,
            onDismiss = { page.previewOpen = false },
            body = {
                Box(Modifier.fillMaxWidth().height(360.dp)) {
                    PreviewPane(appState, scope)
                }
            },
            actions = {
                NextButton(
                    label = "Close",
                    onClick = { page.previewOpen = false },
                    tone = colors.rule,
                    modifier = Modifier.weight(1f),
                )
            },
        )
    }
}

/** A press with no ripple and no indication, which is every press in this app. */
private fun Modifier.pressable(
    press: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = clickable(interactionSource = press, indication = null, onClick = onClick)
