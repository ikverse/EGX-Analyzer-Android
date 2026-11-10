package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import com.ikverse.egxanalyzer.ui.theme.palette
import com.ikverse.egxanalyzer.ui.kit.popIn
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.KitDialog
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.SplitColumns
import com.ikverse.egxanalyzer.model.FoldPosture
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.geometry.Size
import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.AnalysisChunking
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.AnalysisProgress
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.model.egxTargetSession
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.ui.theme.extraColors
import com.ikverse.egxanalyzer.ui.theme.pageAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun AnalyzeScreen(appState: AppState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Android 13 and later start with notifications denied, and the app never asked. Everything
    // was built - channel, foreground service, deep link - and none of it could reach the screen,
    // which looked exactly like a broken notification rather than a missing permission.
    var notificationsAllowed by remember { mutableStateOf(appState.notificationsPermitted()) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        // Granting happens in system settings, so the answer arrives on the way back in.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = appState.notificationsPermitted()
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
    val blocker = analyzeBlocker(appState)
    // What pressing the button will cost at most, worked out by the same function that will do the
    // splitting, so the figure on the button and the run's own splitting cannot disagree about how
    // the sources divide. At most rather than exactly: the wording filter drops sources at the run,
    // and a message an earlier run has already read is not sent at all - both of which can only
    // ever make it fewer. This is the only screen that spends the owner's money, and the count used
    // to sit as grey text inside a card two thirds of the way up the page.
    val requests = remember(appState.inputs) {
        appState.inputs.takeIf(List<AnalysisInput>::isNotEmpty)
            ?.let { AnalysisChunking.chunk(it).size }
    }
    // Until Analyze is pressed this is guidance, not a complaint: painting it red on a freshly
    // opened app tells someone who has done nothing wrong that something is broken.
    var attempted by remember { mutableStateOf(false) }
    // A stray tap on the floating button while a run is going used to cancel it outright - no
    // confirmation, and everything the run had already read and paid for thrown away, since a
    // cancelled run's batches are only saved when it finishes. This asks first.
    var confirmingCancel by remember { mutableStateOf(false) }
    // Pressing Analyze: what blocks a run is said rather than acted on, and the notification
    // permission is asked here rather than at first launch - a prompt before the app has done
    // anything is the one people decline, and Android never asks twice.
    val onAnalyze: () -> Unit = {
        if (blocker != null) {
            attempted = true
        } else if (notificationsAllowed) {
            appState.startAnalysis()
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val onCancel: () -> Unit = { confirmingCancel = true }
    val signedIn = appState.telegramAuthState.step == TelegramAuthStep.READY
    val posture = LocalFoldPosture.current
    Screen(
        appState = appState,
        destination = AppDestination.ANALYZE,
        // Only once signed in: before that there are no chats to fetch, and a pull that did
        // nothing would read as the screen being stuck.
        onRefresh = if (signedIn) {
            {
                appState.startAction {
                    appState.runAction(
                        label = "Refreshing chats",
                        success = { "${appState.channels.size} chats" },
                    ) { appState.refreshTelegramChats() }
                }
            }
        } else {
            null
        },
        refreshing = appState.chatsRefreshing,
    ) {
        // **The 5.0 page.** The run's summary and its button first, the checklist while setup is
        // unfinished, then what a run reads (the sources, what kind of message, for which session,
        // on which model), the messages it would start from, and the last report.
        // The target session, picked on the kit's calendar. Never later than today: the target runs
        // ahead of today on a Friday, a Saturday or after the close, and a historical run refuses a
        // future date anyway. Picking the session the app would resolve on its own is how the
        // reader goes back to live mode.
        var pickingDate by remember { mutableStateOf(false) }
        if (pickingDate) {
            val today = LocalDate.now(ScheduleClock.ZONE)
            com.ikverse.egxanalyzer.ui.kit.KitCalendarSheet(
                title = "Calls for which session",
                selected = minOf(appState.recommendationTargetDate, today),
                onPick = { picked ->
                    if (picked == egxTargetSession()) {
                        appState.selectAnalysisMode(AnalysisMode.NEXT_DAY)
                    } else {
                        appState.updateRecommendationTargetDate(picked)
                    }
                    pickingDate = false
                },
                onDismiss = { pickingDate = false },
                posture = posture,
                allowed = { it <= today },
                latest = today,
            )
        }
        val hero: @Composable (Boolean) -> Unit = { withButton ->
            AnalyzeHero(appState, blocker, attempted, requests, onAnalyze, onCancel, Modifier.popIn(0), showButton = withButton)
        }
        val sources: @Composable () -> Unit = {
            // Before Telegram is signed in this is the sign-in itself, which keeps its look until the
            // Settings stage redraws it - the steps are shared with Settings.
            if (signedIn) SourcesTile(appState, Modifier.popIn(2)) else ChannelsSection(appState)
        }
        val readAndDate: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                ReadTile(appState, blocker, attempted, Modifier.weight(1f).popIn(3))
                CallsForTile(appState, onPick = { pickingDate = true }, modifier = Modifier.weight(1f).popIn(4))
            }
        }
        val notifications: @Composable () -> Unit = {
            if (appState.analysisStatus != AnalysisStatus.RUNNING && !notificationsAllowed) NotificationsOffTile(appState)
        }
        when (posture) {
            is FoldPosture.Unfolded, is FoldPosture.Book -> {
                SplitColumns(posture, unfoldedStart = 0.5f, start = {
                        hero(true)
                        SetupCard(appState)
                        notifications()
                        ModelTile(appState, blocker, attempted, Modifier.popIn(5))
                        LastRunTile(appState, Modifier.popIn(6))
                    }, end = {
                        sources()
                        readAndDate()
                        MessagesPreview(appState, scope, blocker, attempted)
                    })
            }
            is FoldPosture.Tabletop -> {
                // The summary stands on the upright half; the button and everything pressed are on
                // the flat half under the crease.
                hero(false)
                AnalyzeButton(appState, blocker, requests, onAnalyze, onCancel)
                SetupCard(appState)
                notifications()
                sources()
                readAndDate()
                ModelTile(appState, blocker, attempted)
                MessagesPreview(appState, scope, blocker, attempted)
                LastRunTile(appState)
            }
            else -> {
                hero(true)
                SetupCard(appState)
                notifications()
                sources()
                readAndDate()
                ModelTile(appState, blocker, attempted, Modifier.popIn(5))
                MessagesPreview(appState, scope, blocker, attempted)
                LastRunTile(appState, Modifier.popIn(6))
            }
        }
        DuplicateAnalysisDialog(appState)
        if (confirmingCancel) {
            KitDialog(
                title = "Stop this analysis?",
                text = "What it has read so far is lost.",
                confirmLabel = "Stop",
                onConfirm = {
                    confirmingCancel = false
                    scope.launch { appState.cancelAnalysis() }
                },
                dismissLabel = "Keep running",
                onDismiss = { confirmingCancel = false },
                posture = posture,
                destructive = true,
            )
        }
    }
}


/**
 * What would be sent, before it is sent.
 *
 * Analysis loads its own sources, so this is not a required step - it is here so the size of a
 * paid request can be seen first, and so the window can be sanity-checked against the chats.
 */
/** The busy label the preview button raises, and the only one it reacts to. */
private const val LoadingMessagesLabel = "Loading messages from Telegram"

@Composable
private fun MessagesPreview(
    appState: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
    /** What is stopping a run, or nothing when nothing is. Only [AnalyzeBlocker.NO_SOURCES] is
     * this card's to answer for; the others are drawn by the cards they are about. */
    blocker: AnalyzeBlocker?,
    /** Whether Analyze has been pressed: until it has, this is guidance rather than an error. */
    attempted: Boolean,
) {
    val selected = appState.channels.count(ChannelSelection::selected)
    val sources = appState.telegramSources
    // This button's own work and nothing else's: read as "anything is busy" it spun and greyed
    // during a price fetch and a sign-out, neither of which it could do anything about.
    val loading = appState.busyLabel == LoadingMessagesLabel
    // **A kit tile since 5.0**, with the list folded shut until it is asked for: the count says what
    // is loaded, and the messages themselves are a press away rather than a screen of them between
    // the settings and the last report.
    val p = palette
    var showing by remember { mutableStateOf(false) }
    Tile(modifier = Modifier.fillMaxWidth()) {
        Text("Messages", style = MaterialTheme.typography.headlineSmall)
        // With messages on screen the standing line only restates the list, so it gives way to the
        // count. With none, telegramSyncMessage is the only thing that can say why - read forty and
        // none fell in the window, Telegram refused - and none of that is anywhere else.
        Text(
            if (sources.isEmpty()) {
                appState.telegramSyncMessage ?: "See which messages a run would start from."
            } else {
                "${sources.size} ${if (sources.size == 1) "message" else "messages"} from " +
                    "$selected ${if (selected == 1) "chat" else "chats"}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = p.muted,
            modifier = Modifier.padding(top = Space.xs),
        )
        blocker?.takeIf { it == AnalyzeBlocker.NO_SOURCES }?.let {
            Text(
                it.reason,
                style = MaterialTheme.typography.bodySmall,
                color = if (attempted) p.loss else p.muted,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
        Row(Modifier.padding(top = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            KitButton(
                when {
                    loading -> "Loading…"
                    sources.isEmpty() -> "Preview messages"
                    else -> "Refresh messages"
                },
                onClick = {
                    appState.startAction {
                        appState.runAction(
                            label = LoadingMessagesLabel,
                            success = { load -> load.message },
                        ) {
                            // A failed load throws here so the status line paints it as the
                            // failure it is; the tile already shows the same sentence.
                            val load = appState.syncTelegramSources()
                            if (!load.succeeded) error(load.message)
                            load
                        }
                    }
                },
                enabled = selected > 0 && !loading,
                kind = ButtonKind.SECONDARY,
                size = ButtonSize.SMALL,
            )
            if (sources.isNotEmpty()) {
                KitButton(
                    if (showing) "Hide messages" else "Show messages",
                    onClick = { showing = !showing },
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                )
            }
        }
        AnimatedVisibility(visible = showing && sources.isNotEmpty(), enter = ExpandIn, exit = CollapseOut) {
            // Bounded like the chat list, so a busy day does not bury the rest of the page.
            Column(
                Modifier
                    .padding(top = Space.m)
                    .heightIn(max = MessageListMaxHeight)
                    .scrollableColumn(),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                sources.forEach { source -> MessageTile(source) }
            }
        }
    }
}

/**
 * One message, as a tile rather than four stacked lines under a rule.
 *
 * A step up in container colour is what makes a message read as something sitting in the card
 * rather than another paragraph of it, which is the job a column of dividers was doing badly.
 */
@Composable
private fun MessageTile(source: SourceTrace) {
    Box(Modifier.clip(Kit.PanelShape).background(palette.ground)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    contentTypeIcon(source.contentType),
                    contentDescription = source.contentType.name.lowercase(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Inline),
                )
                Spacer(Modifier.width(Space.s))
                Text(
                    source.channelName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.s))
                Text(
                    MessageTimeFormat.format(source.timestamp.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // An image or a voice note carries no text of its own, and drawing the blank left a
            // gap that read as a message which had failed to load.
            source.preview.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The word for a content type, for the checkboxes that offer it.
 *
 * Exhaustive over the enum rather than over OFFERED, so a type added to one is a compile error
 * here rather than a row drawn with no label.
 */
internal fun contentTypeLabel(type: AnalysisContentType): String = when (type) {
    AnalysisContentType.TEXT -> "Text"
    AnalysisContentType.IMAGES -> "Images"
    AnalysisContentType.AUDIO -> "Voice"
}

/** What kind of message it is, as a glyph: the word for it, repeated down a column, is noise. */
private fun contentTypeIcon(type: AnalysisContentType): ImageVector = when (type) {
    AnalysisContentType.TEXT -> Icons.Outlined.TextFields
    AnalysisContentType.IMAGES -> Icons.Outlined.Image
    AnalysisContentType.AUDIO -> Icons.Outlined.Mic
}

/** Built once. The formatter this replaces was constructed per message, per recomposition. */
private val MessageTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM · HH:mm")

/** Enough to scan the window without pushing the run off the screen. */
private val MessageListMaxHeight = 320.dp

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
    KitDialog(
        title = "This session is already analysed",
        text = "A report for ${duplicate.result.recommendationTargetDate} already covers exactly " +
            "these chats, run $ranAt. Running it again costs another request and replaces that " +
            "report in Insights.",
        confirmLabel = "Continue",
        onConfirm = {
            appState.dismissDuplicateWarning()
            appState.startAnalysis(confirmed = true)
        },
        dismissLabel = "Cancel",
        onDismiss = appState::dismissDuplicateWarning,
        posture = LocalFoldPosture.current,
        tone = TileTone.TILE1,
    ) {
        Text(
            duplicate.result.selectedChannels.joinToString { it.name },
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
            modifier = Modifier.padding(top = Space.s),
        )
    }
}

/**
 * Why a run cannot start.
 *
 * Typed rather than a bare string so each card can answer for its own: the reason used to be one
 * sentence rendered under the sources whatever it was about, which put "Save the provider API key"
 * beneath a list of messages and left the model card - the thing that was actually wrong - silent.
 */
internal enum class AnalyzeBlocker(
    val reason: String,
    /** Drawn by [AnalysisModelCard]; the rest belong to the cards their subject sits in. */
    val belongsToModelCard: Boolean = false,
) {
    NO_CREDENTIAL("Save the provider API key in Settings first.", belongsToModelCard = true),
    NO_MODEL("Choose the model this run will be sent to.", belongsToModelCard = true),
    NO_CONTENT_TYPE("Select at least one content type."),
    NO_SOURCES("No messages are loaded. Select chats in Telegram, then load them here."),
}

/**
 * One step of getting this app to the point where it can do anything.
 *
 * @param done whether it has been carried out on this phone.
 * @param title what to do, in the imperative - the reader is being given instructions.
 * @param detail where it is done, in one line. Never the word "above" or "below": the cards on this
 *   page move with the window width, so a direction is wrong on half the screens it runs on.
 * @param settings whether the step is carried out in Settings rather than on this page, which is
 *   what decides whether the row offers to go there.
 */
internal data class SetupStep(
    val done: Boolean,
    val title: String,
    val detail: String,
    val settings: Boolean = false,
)

/**
 * What a phone that has never produced a report still has to do, all of it at once.
 *
 * **Every one of these was already enforced and none of them was ever stated together.**
 * [analyzeBlocker] returns the *first* thing stopping a run and each card draws its own, which is
 * right once somebody knows the app: the complaint sits with the control that answers it. For a
 * first run it is four round trips - press the button, be told about the key, press again, be told
 * about the model, press again, the content types, press again, the sources - and each one is
 * discovered only by pressing a button that then refuses. Nowhere said what the four were.
 *
 * That matters more than it used to. The app was built for one person who knew all of this, and is
 * now meant for people who do not; a stranger opening it meets a page of controls with no order to
 * them and a button that says no.
 *
 * **It states, and it never does.** Every step here is carried out by the control that already owns
 * it - this card carries no key field, no chat picker and no model list, because a second way to do
 * something is a second thing to keep in step with the first. The one action it offers is *Open
 * Settings*, for the two steps that genuinely live there.
 *
 * Gone the moment there is a report on this phone, and dismissible before that: somebody using this
 * only for the portfolio must be able to wave it away. The dismissal is session-only and kept on
 * [PageState] rather than in this composition - see `PageState.analyzeSetupDismissed`.
 */
@Composable
internal fun SetupCard(appState: AppState) {
    if (appState.savedResults.isNotEmpty()) return
    var dismissed by appState.pages.analyzeSetupDismissed
    if (dismissed) return
    val steps = setupSteps(appState)
    // Nothing left to say. This goes before the first run rather than waiting for one, so a phone
    // that is ready and has simply not been asked yet is not still being told how to get ready.
    if (steps.all(SetupStep::done)) return
    // **A cobalt tile since 5.0**: the four steps numbered, each ticked once done, and the two
    // buttons at its foot.
    val p = palette
    Tile(modifier = Modifier.fillMaxWidth().popIn(0), tone = TileTone.HERO) {
        Text("Getting started", style = MaterialTheme.typography.headlineMedium)
        Text(
            "${steps.count(SetupStep::done)} of ${steps.size} done",
            style = MaterialTheme.typography.bodySmall,
            color = p.onHero.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = Space.s),
        )
        steps.forEachIndexed { index, step ->
            com.ikverse.egxanalyzer.ui.kit.KitRule(color = p.onHero.copy(alpha = 0.2f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.m)
                    .graphicsLayer { alpha = if (step.done) 1f else 0.95f }
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(Kit.Capsule)
                        .background(if (step.done) p.highlight else p.onHero.copy(alpha = 0.16f))
                        .semantics { contentDescription = if (step.done) "Done" else "Still to do" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (step.done) "✓" else "${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (step.done) p.onTile else p.onHero,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(step.title, style = MaterialTheme.typography.titleMedium)
                    Text(step.detail, style = MaterialTheme.typography.bodySmall, color = p.onHero.copy(alpha = 0.85f))
                }
            }
        }
        Row(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            // Offered only while something in Settings is actually outstanding. A button that takes
            // the reader somewhere with nothing to do there is the app wasting the one instruction
            // it has their attention for.
            if (steps.any { !it.done && it.settings }) {
                KitButton("Open Settings", onClick = { appState.navigate(AppDestination.SETTINGS) }, size = ButtonSize.MEDIUM)
            }
            KitButton("Not now", onClick = { dismissed = true }, kind = ButtonKind.ON_TONE, size = ButtonSize.MEDIUM)
        }
    }
}

/**
 * The four steps, in the order a run needs them.
 *
 * Internal rather than private so it can be tested as the plain function it is - the four states
 * and their order are the part worth holding, and none of it needs a composition to check.
 *
 * Read off the same state [analyzeBlocker] reads, deliberately: two lists of what a run requires,
 * kept in two places, is one list that will quietly stop matching what the button enforces.
 */
internal fun setupSteps(appState: AppState): List<SetupStep> = listOf(
    SetupStep(
        done = appState.telegramAuthState.step == TelegramAuthStep.READY,
        title = "Sign in to Telegram",
        detail = "Scan the code on the Chats card. It is the only account this app needs.",
    ),
    SetupStep(
        done = appState.channels.any(ChannelSelection::selected),
        title = "Choose the chats to read",
        detail = "Tick the channels whose recommendations should be scored.",
    ),
    SetupStep(
        done = appState.cloudConfiguration.hasCredential,
        title = "Add a provider API key",
        detail = "Settings, Cloud provider. A run is sent to that provider and billed by them.",
        settings = true,
    ),
    SetupStep(
        done = appState.cloudConfiguration.model.isNotBlank(),
        title = "Choose the model",
        detail = "It has to accept images: a run sends screenshots.",
        settings = true,
    ),
)

/**
 * What is stopping a run, or null when nothing is.
 *
 * Lifted out of the screen body so the floating action and the card that explains itself agree
 * without one of them re-deriving it.
 */
private fun analyzeBlocker(appState: AppState): AnalyzeBlocker? = when {
    !appState.cloudConfiguration.hasCredential -> AnalyzeBlocker.NO_CREDENTIAL
    appState.cloudConfiguration.model.isBlank() -> AnalyzeBlocker.NO_MODEL
    appState.selectedContentTypes.isEmpty() -> AnalyzeBlocker.NO_CONTENT_TYPE
    appState.inputs.isEmpty() &&
        (appState.telegramAuthState.step != TelegramAuthStep.READY ||
            appState.channels.none(ChannelSelection::selected)) -> AnalyzeBlocker.NO_SOURCES
    else -> null
}

/**
 * What the button says while a run is going.
 *
 * The clock is there because a run takes anywhere from seventy seconds to eleven minutes, one has
 * already died on a timeout, and nothing else on screen says how long this one has been waiting.
 *
 * **It used to count elapsed and nothing else, and the comment here said why**: the repository
 * reported nothing until it finished, so any figure claiming to know how far along a run was would
 * have been invented. It reports now - see [AnalysisProgress] - so the figure beside the clock is
 * measured rather than guessed, and the two answer different questions. How far along says whether
 * the run is nearly done; how long says whether it is moving at all, which on a run that has
 * stalled is the only one of the two that changes.
 *
 * The batch alone, not the image count: this line shares a button with the clock, and the fuller
 * version of the same fact is on the page. See [AnalysisProgress.badge].
 */
@Composable
internal fun RunningLabel(
    startedAt: java.time.Instant?,
    progress: AnalysisProgress?,
    big: Boolean,
) {
    var elapsed by remember(startedAt) { mutableStateOf(elapsedSince(startedAt)) }
    LaunchedEffect(startedAt) {
        while (startedAt != null) {
            elapsed = elapsedSince(startedAt)
            delay(1_000)
        }
    }
    Column {
        Text(
            "Cancel analysis",
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
        )
        // Null until the first batch goes out, which is the gap between the press and the first
        // request - so the clock stands alone there rather than the line jumping from one shape to
        // another, and the button's height never changes.
        listOfNotNull(progress?.badge(), elapsed).takeIf { it.isNotEmpty() }?.let {
            Text(
                it.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun elapsedSince(startedAt: java.time.Instant?): String? {
    val start = startedAt ?: return null
    val seconds = java.time.Duration.between(start, java.time.Instant.now()).seconds.coerceAtLeast(0)
    return "%d:%02d elapsed".format(seconds / 60, seconds % 60)
}


/** How strongly the read part of a run is drawn over the button's own container. */
internal const val RunFillAlpha = 0.30f

/** One pass of the sweep while the model writes, which has no count to fill to. */
private const val RunSweepMillis = 1600

/**
 * The button's ground while a run is going: [fill] from the left to [fraction] while the run reads,
 * and a band sweeping across while it writes ([fraction] null). With reduce motion on the sweep is
 * replaced by a still, faint fill, so the button still says a run is going without moving.
 */
@Composable
internal fun Modifier.runFill(fraction: Float?, ground: Color, fill: Color): Modifier {
    val still = LocalReduceMotion.current
    val shown by animateFloatAsState(
        targetValue = (fraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = if (still) snap() else uiSpring(),
        label = "runFill",
    )
    val sweep = if (fraction == null && !still) {
        rememberInfiniteTransition(label = "runSweep").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(RunSweepMillis, easing = LinearEasing)),
            label = "runSweepPosition",
        )
    } else {
        null
    }
    return drawBehind {
        drawRect(ground)
        when {
            fraction != null -> drawRect(fill, size = Size(size.width * shown, size.height))
            sweep == null -> drawRect(fill.copy(alpha = fill.alpha / 2))
            else -> {
                val band = size.width * 0.4f
                val x = -band + (size.width + band) * sweep.value
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, fill, Color.Transparent),
                        startX = x,
                        endX = x + band,
                    ),
                )
            }
        }
    }
}
