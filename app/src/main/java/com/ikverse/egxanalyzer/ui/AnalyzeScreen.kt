package com.ikverse.egxanalyzer.ui

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ikverse.egxanalyzer.data.AnalysisNotifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.data.AnalysisChunking
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.SourceTrace
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.ui.theme.extraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun AnalyzeScreen(activity: Activity, appState: AppState) {
    val scope = rememberCoroutineScope()
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
    val blocker = analyzeBlocker(appState)
    // What pressing the button will actually cost, worked out by the same function that will do the
    // splitting - so the figure on the button and the number of requests the run makes cannot
    // disagree. This is the only screen that spends the owner's money, and the count used to sit as
    // grey text inside a card two thirds of the way up the page.
    val requests = remember(appState.inputs) {
        appState.inputs.takeIf(List<AnalysisInput>::isNotEmpty)
            ?.let { AnalysisChunking.chunk(it).size }
    }
    // Until Analyze is pressed this is guidance, not a complaint: painting it red on a freshly
    // opened app tells someone who has done nothing wrong that something is broken.
    var attempted by remember { mutableStateOf(false) }
    Screen(
        title = "Analyze",
        floatingAction = {
            val big = LocalWindowWidth.current != WindowWidth.COMPACT
            val actionModifier = Modifier.height(if (big) BigActionHeight else ActionHeight)
            val ai = extraColors
            val running = appState.analysisStatus == AnalysisStatus.RUNNING
            // One instance across both branches, so the mark keeps turning through the moment a run
            // starts rather than restarting from nothing there.
            val motion = rememberActionMotion()
            // The halo falls outside the surface, so it goes on the modifier the surface is given
            // rather than inside the shape's clip. The fill goes inside, where the flat tint was.
            val haloed = actionModifier.drawBehind {
                drawAiHalo(ai.actionGlow, ActionCorner.toPx(), motion.breath())
            }
            val teal = remember(ai.actionFill) { Brush.horizontalGradient(ai.actionFill) }
            // The edge, in the same family and its own stops - see ExtraColors.actionLine for why
            // it cannot simply be the fill: the fill sits inside this line.
            val actionEdge = remember(ai.actionLine) { Brush.horizontalGradient(ai.actionLine) }
            // Read in the draw lambda rather than the composable body: read at composition the
            // drifts would recompose this screen sixty times a second, where here a frame costs a
            // repaint and nothing else.
            val fill = Modifier.drawBehind {
                if (running) {
                    drawActionAurora(
                        ai.actionAuroraBase,
                        ai.actionAurora,
                        motion.lights(size.width, size.height),
                    )
                } else {
                    drawRect(teal)
                }
            }
            if (running) {
                AnalyzeAction(
                    onClick = { scope.launch { appState.cancelAnalysis() } },
                    container = Color.Transparent,
                    content = ai.onAction,
                    // No halo here. The aurora is what says the control is alive, and a glow around
                    // a button this wide lights the whole foot of the screen.
                    modifier = actionModifier,
                    painted = fill,
                    stretch = !big,
                    // The one red left on the button, and a hairline of it. The moving fill says a
                    // model is working, which is not the same as saying what pressing this does -
                    // and with the spinner gone the label was carrying that on its own. It keeps
                    // the red rather than the ready state's gradient for that reason: this is the
                    // only state where pressing cancels, and no edge in the action's own colours
                    // could say so.
                    outline = SolidColor(ai.aiStop),
                    // The mark rather than a spinner, turning once every few seconds: the fill
                    // moving under it is what reports the run is alive. A spinner on top of that is
                    // one control saying "waiting" twice.
                    icon = {
                        Spark(
                            ai.aiSpark,
                            if (big) BigActionIcon else ActionIcon,
                            Modifier.graphicsLayer { rotationZ = motion.angle },
                        )
                    },
                    label = { RunningLabel(appState.analysisStartedAt, big) },
                )
            } else {
                // Only the state that can actually spend money wears the fill. A blocked button in
                // the action colour would be the loudest thing on the screen and do nothing when
                // pressed, which is the one thing it must not be able to mean.
                val ready = blocker == null
                AnalyzeAction(
                    onClick = {
                        // Asked here rather than at first launch: a permission prompt before the
                        // app has done anything is the one people decline, and Android never asks
                        // twice.
                        if (blocker != null) {
                            attempted = true
                            return@AnalyzeAction
                        }
                        if (notificationsAllowed) {
                            appState.startAnalysis()
                        } else {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    container = if (ready) {
                        Color.Transparent
                    } else {
                        // A step down from surfaceContainerHighest, which read as nearly solid once
                        // the button went see-through and left the blocked state looking the most
                        // substantial of the three.
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    content = if (ready) ai.onAction else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (ready) haloed else actionModifier,
                    painted = fill.takeIf { ready },
                    stretch = !big,
                    // Only the state that can spend money wears the coloured edge, for the reason
                    // only it wears the fill: a blocked button with the action's own hairline round
                    // it would be inviting a press that does nothing. Blocked keeps the neutral
                    // outline every other floating thing has.
                    outline = if (ready) actionEdge else null,
                    icon = {
                        if (ready) {
                            Spark(ai.aiSpark, if (big) BigActionIcon else ActionIcon)
                        } else {
                            Icon(
                                Icons.Outlined.AutoGraph,
                                contentDescription = null,
                                modifier = Modifier.size(if (big) BigActionIcon else ActionIcon),
                            )
                        }
                    },
                    label = {
                        Column {
                            Text(
                                "Analyze",
                                style = if (big) {
                                    MaterialTheme.typography.titleMedium
                                } else {
                                    MaterialTheme.typography.labelLarge
                                },
                            )
                            // The price, on the thing that charges it. Only the request count: the
                            // messages behind it are named on the card that loaded them, and a
                            // button that repeats the card is a button doing two jobs.
                            requests?.let {
                                Text(
                                    "$it ${if (it == 1) "request" else "requests"}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    },
                )
            }
        },
        // Only once signed in: before that there are no chats to fetch, and a pull that did
        // nothing would read as the screen being stuck.
        onRefresh = if (appState.telegramAuthState.step == TelegramAuthStep.READY) {
            {
                scope.launch {
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
        // Chat selection leads, because choosing sources is the first step of a run - and on a
        // wide screen it sits beside the settings that shape it rather than above them, so both
        // are visible while either is being changed.
        AdaptivePanes(
            main = {
                ChannelsSection(appState)
            },
            side = {
        // What is read and what it is read for: two settings of equal standing, so they sit as a
        // pair rather than stacked. Through AdaptivePanes with equal weights rather than a Row of
        // this screen's own - it is the app's one rule for "beside each other, or stacked when
        // they will not fit", and a second one here would be a second threshold to keep in step.
        //
        // 600dp of container, which is the width at which each card still clears 300: the cover
        // screen (379) keeps them stacked, the unfolded Fold (638) gives 313 each and the tablet
        // (706) gives 347. `alignHeights` is what makes the two backgrounds line up - one card
        // holds a pair of checkboxes and the other two options, and left to themselves they end
        // at two different heights, which reads as one of them having failed rather than as one
        // simply having less in it.
        AdaptivePanes(
            minWidth = 600.dp,
            mainWeight = 1f,
            alignHeights = true,
            main = {
                // SectionCard, where both of these were hand-built copies of it. Two cards drawing
                // their own background is how the pair drifted apart in the first place: the same
                // container and shape, and then one tinting its icon and the other not.
                // `fillMaxHeight`, because `alignHeights` stretches the two *columns* and a card
                // inside one keeps its own height regardless - so the shorter card's background
                // ended early and the stretch was invisible empty space under it, which is the
                // exact mismatch alignHeights exists to remove. No-op when the panes stack, where
                // the column height is unbounded.
                SectionCard(
                    title = "Content types",
                    icon = Icons.Outlined.TextFields,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    // Wraps rather than switching, because the threshold it replaces had the fold
                    // exactly backwards. It was measured against the card's own content width, and
                    // the card is only narrow when there is room to put it *beside* the date card:
                    // the cover screen gets the full 379 and so 347 of content, clears 340 and lays
                    // three across, while the unfolded Fold splits 638 into two 313 columns, leaves
                    // 281 of content, misses the threshold and stacks three long labels down a
                    // column. The larger screen was getting the taller layout.
                    //
                    // A FlowRow asks the labels how wide they are instead of guessing from a number
                    // written here, so one row survives the cover screen, the unfolded Fold and the
                    // tablet alike, and a large font scale wraps rather than clips. It also takes a
                    // BoxWithConstraints back out of a pane that `alignHeights` has to measure,
                    // which is the nesting AdaptivePanes carries a warning about.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.l),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        // Drawn from OFFERED rather than from three calls written out here, so
                        // a type the app has stopped offering leaves this row by leaving that
                        // list. Voice went that way on 2026-09-03.
                        AnalysisContentType.OFFERED.forEach { type ->
                            ContentTypeToggle(contentTypeLabel(type), type, appState)
                        }
                    }
                    // Drawn where the boxes are, for the same reason the model card carries its own.
                    if (blocker == AnalyzeBlocker.NO_CONTENT_TYPE) {
                        Text(
                            blocker.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (attempted) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            },
            side = {
                SectionCard(
                    title = "Recommendation target date",
                    icon = Icons.Outlined.CalendarMonth,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    RecommendationDateOption(
                        selected = appState.analysisMode == AnalysisMode.NEXT_DAY,
                        title = "Current / next EGX session",
                        detail = appState.recommendationTargetDate.toString(),
                        onClick = { appState.selectAnalysisMode(AnalysisMode.NEXT_DAY) },
                    )
                    RecommendationDateOption(
                        selected = appState.analysisMode == AnalysisMode.SPECIFIC_DATE,
                        title = "Specific date",
                        // No "Change date" button under this any more. The row itself has always
                        // opened the picker, so the button was a second control doing one job - and
                        // it was the reason this card changed height the moment the mode changed,
                        // which is the one thing a card sitting beside another must not do. The
                        // affordance moves into the line that was already there: the date, and what
                        // pressing gets you.
                        detail = if (appState.analysisMode == AnalysisMode.SPECIFIC_DATE) {
                            "${appState.recommendationTargetDate} · tap to change"
                        } else {
                            "Choose today or an earlier date"
                        },
                        onClick = {
                            appState.selectAnalysisMode(AnalysisMode.SPECIFIC_DATE)
                            showRecommendationDatePicker(activity, appState)
                        },
                    )
                }
            },
        )
        // The reason a run cannot start sits with the button that loads what it is missing, rather
        // than as a loose line under the card it is about.
        MessagesPreview(appState, scope, blocker, attempted)
        AnalysisModelCard(appState, blocker, attempted)
        SchedulesSection(appState)
            },
        )
        DuplicateAnalysisDialog(appState)

        if (appState.analysisStatus != AnalysisStatus.RUNNING) {
            // Every blocker is now drawn once, by the card it is about. The line that used to sit
            // here said "Select chats at the top of this screen" whatever was actually wrong, so a
            // missing API key was reported as a missing chat.
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

@Composable
private fun RecommendationDateOption(
    selected: Boolean,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    // The whole row selects, not just the button. The title and the date are the part being read,
    // and on the specific-date option the detail line says "tap to change" - a label that names the
    // gesture and then ignores it is worse than no label. One `selectable` rather than a click on
    // each child, so a screen reader announces one option instead of a button and two loose texts.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            // Spelled out because the null `onClick` below gives it up. Material reserves the 48dp
            // target inside the control only on the interactive path, so this row was as tall as
            // whatever text happened to be in it. Two lines gets close enough to 48 on its own to
            // have hidden that, which is exactly why it is worth stating: the height was a property
            // of the copy rather than a decision, and a one-line detail would have lost it.
            .heightIn(min = 48.dp)
            .padding(vertical = Space.xs),
        // The same gap the checkbox card uses, and for the same reason it has to be stated at all:
        // a RadioButton drawn on the null path is 20dp of ring in 2dp of padding, so without this
        // the title started 2dp from the ring. Everywhere else in the app the control keeps its
        // callback, Material centres those 24dp in 48, and the label inherits 14dp of air for free.
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        // Against the title rather than the middle of both lines. Centring put the ring halfway
        // down a two-line column, which is the seam between the title and the date under it - so it
        // pointed at neither. Topped, it sits on the title's line, which is the line it is about.
        verticalAlignment = Alignment.Top,
    ) {
        // Null: the row owns the click now, and a button with its own would be a second target
        // sitting inside the first.
        RadioButton(selected = selected, onClick = null)
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
    // The label toggles as well as the box, for the reason the date rows beside this card do: two
    // cards side by side, one answering a tap on its text and the other not, reads as one of them
    // being broken.
    //
    // The gap beside the label is spelled out rather than trimmed off what the box brings, because
    // the box brings almost nothing. A Material checkbox reserves its 48dp target only on the
    // interactive path, and this one is passed a null `onCheckedChange` - the row is the target, so
    // the box is a drawing. That leaves it 20dp wide in 2dp of padding rather than the 48dp square
    // the trim was measured against, and pulling the label a further 4dp in on top of that ran it
    // into the box: `Text` read as one word on the phone, three boxes with no space after them.
    //
    // Space.m rather than the Space.s that fixed the collision, because clearing the box was only
    // half of it. Every other checkbox in the app - Settings, the schedule, the price feed - keeps
    // its callback and so gets Material's 48dp slot, which centres those 24dp and hands the label
    // 14dp. At 8 these two cards read as the tight ones on a screen of otherwise even rows; 12
    // puts them back on the same measure as everything around them.
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .toggleable(
                value = type in appState.selectedContentTypes,
                role = Role.Checkbox,
                onValueChange = { appState.toggleContentType(type) },
            )
            // The other half of what the null callback gives up. Three of these were 24dp tall and
            // 4dp apart: half the minimum target, close enough together to catch the wrong one.
            .heightIn(min = 48.dp)
            .padding(end = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Null for the same reason the date rows pass it: the row is the target.
        Checkbox(
            checked = type in appState.selectedContentTypes,
            onCheckedChange = null,
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
    val loading = appState.busyLabel != null
    SectionCard(
        title = "Messages preview",
        icon = Icons.Outlined.Preview,
        about = infoNote(
            "Messages preview",
            "Selected Telegram chats are collected automatically for the resolved source window.",
            "This is exactly which messages a run will send.",
        ),
    ) {
        // With messages on screen the standing line only restates the list, so it gives way to the
        // count. With none, telegramSyncMessage is the only thing that can say why - read forty and
        // none fell in the window, Telegram refused - and none of that is anywhere else.
        if (sources.isEmpty()) {
            Text(
                appState.telegramSyncMessage ?: "See exactly which messages a run will send.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // What was loaded, in the reader's own terms. "Model inputs" was the app naming its own
            // plumbing, and the figure that actually matters - what a run costs - is on the button
            // that spends it rather than here.
            Text(
                "${sources.size} ${if (sources.size == 1) "message" else "messages"} from " +
                    "$selected ${if (selected == 1) "chat" else "chats"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        blocker?.takeIf { it == AnalyzeBlocker.NO_SOURCES }?.let {
            Text(
                it.reason,
                style = MaterialTheme.typography.bodySmall,
                color = if (attempted) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    appState.runAction(
                        label = "Loading messages from Telegram",
                        success = { "${appState.inputs.size} sources ready" },
                    ) { appState.syncTelegramSources() }
                }
            },
            enabled = selected > 0 && !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Greying out says the button is unavailable without saying why, and something else
            // already holding the Telegram connection is the usual reason.
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(IconSize.Inline),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    if (sources.isEmpty()) Icons.Outlined.Preview else Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Inline),
                )
            }
            Spacer(Modifier.width(Space.s))
            Text(
                when {
                    loading -> "Loading…"
                    sources.isEmpty() -> "Preview messages"
                    else -> "Refresh messages"
                },
            )
        }

        if (sources.isNotEmpty()) {
            // Bounded like the chat list, so a busy day does not bury the Analyze button.
            Column(
                Modifier
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    contentTypeIcon(source.contentType),
                    contentDescription = source.contentType.name.lowercase(),
                    tint = MaterialTheme.colorScheme.primary,
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
private fun contentTypeLabel(type: AnalysisContentType): String = when (type) {
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

/** The action is the point of this screen, so it grows with the room a big screen gives it. */
private val BigActionHeight = 88.dp

/** Material's own extended button height, which this one no longer inherits by being one. */
private val ActionHeight = 56.dp

/**
 * The corner the halo has to match, which is `shapes.large` read as a number.
 *
 * Restated rather than measured: the shape is handed to the surface as a `Shape`, and the halo is
 * drawn outside that surface by a lambda that never sees it. Kept beside the heights so the two are
 * changed together if the action ever stops taking the page's card radius.
 */
private val ActionCorner = 22.dp
private val ActionIcon = 24.dp
private val BigActionIcon = 34.dp

/** Room either side of the icon and its label, where the button used to bring Material's own. */
private val ActionPadding = 20.dp

/**
 * The screen's action, wearing the same floating treatment as the navigation bar under it.
 *
 * Not an `ExtendedFloatingActionButton`: that one paints its own opaque container, and a button
 * floating over the page beside a bar that lets the page through read as two different kinds of
 * thing. Less rounded than the bar on purpose - the page's own card radius rather than a pill - so
 * the two are the same material without being the same shape.
 */
@Composable
private fun AnalyzeAction(
    onClick: () -> Unit,
    container: Color,
    content: Color,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    painted: Modifier? = null,
    outline: Brush? = null,
    /**
     * Whether the button runs the full width of the page, which it does wherever the navigation bar
     * does - see `Screen`. Centred there rather than left-aligned: the icon and its label take about
     * a third of a phone's width, and held to the start they leave the rest of the button empty.
     */
    stretch: Boolean = false,
) {
    FloatingSurface(
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = modifier,
        onClick = onClick,
        painted = painted,
        outline = outline,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            Row(
                Modifier
                    .then(if (stretch) Modifier.fillMaxWidth() else Modifier)
                    .padding(horizontal = ActionPadding),
                horizontalArrangement = if (stretch) {
                    Arrangement.spacedBy(Space.m, Alignment.CenterHorizontally)
                } else {
                    Arrangement.spacedBy(Space.m)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon()
                label()
            }
        }
    }
}

/**
 * What the button says while a run is going.
 *
 * The clock is there because a run takes anywhere from seventy seconds to eleven minutes, one has
 * already died on a timeout, and nothing else on screen says how long this one has been waiting.
 * It counts elapsed rather than remaining: the repository reports nothing until it finishes, so
 * any figure claiming to know how far along the run is would be invented.
 */
@Composable
private fun RunningLabel(startedAt: java.time.Instant?, big: Boolean) {
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
        elapsed?.let {
            Text(it, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun elapsedSince(startedAt: java.time.Instant?): String? {
    val start = startedAt ?: return null
    val seconds = java.time.Duration.between(start, java.time.Instant.now()).seconds.coerceAtLeast(0)
    return "%d:%02d elapsed".format(seconds / 60, seconds % 60)
}
