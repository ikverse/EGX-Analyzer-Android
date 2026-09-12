package com.ikverse.egxanalyzer.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.theme.CardHue
import com.ikverse.egxanalyzer.ui.theme.color
import com.ikverse.egxanalyzer.model.AnalysisChunking
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisInput
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.SourceTrace
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
    Screen(
        appState = appState,
        destination = AppDestination.ANALYZE,
        floatingAction = {
            val big = LocalWindowWidth.current != WindowWidth.COMPACT
            val actionModifier = Modifier.height(if (big) BigActionHeight else ActionHeight)
            val ai = extraColors
            // The page's own hue, which on this page is cyan - the colour this button already wore.
            // Read from the accent rather than from `ExtraColors` so that the rule is the one
            // stated rather than a coincidence: **the screen's action wears the page's hue**, and a
            // floating action added to any other destination gets that page's without being told.
            val accent = pageAccent
            val running = appState.analysisStatus == AnalysisStatus.RUNNING
            // One instance across both branches, so the mark keeps turning through the moment a run
            // starts rather than restarting from nothing there.
            val motion = rememberActionMotion()
            // The halo falls outside the surface, so it goes on the modifier the surface is given
            // rather than inside the shape's clip. The fill goes inside, where the flat tint was.
            val haloed = actionModifier.drawBehind {
                drawAiHalo(accent.actionGlow, ActionCorner.toPx(), motion.breath())
            }
            val teal = remember(accent.actionFill) { Brush.horizontalGradient(accent.actionFill) }
            // The edge, in the same family and its own stops - see ExtraColors.actionLine for why
            // it cannot simply be the fill: the fill sits inside this line.
            val actionEdge = remember(accent.actionLine) { Brush.horizontalGradient(accent.actionLine) }
            // Read in the draw lambda rather than the composable body: read at composition the
            // drifts would recompose this screen sixty times a second, where here a frame costs a
            // repaint and nothing else.
            // The page behind the button, blurred, under everything the button paints. This is the
            // one place in the app where a frost is worth its frame: the page scrolls under this
            // button the whole time it is on screen, so there is something behind it to soften.
            // See frostedBackdrop - and ActionFrost for why the fills above it had to come down.
            val frost = Modifier.frostedBackdrop()
            val fill = frost.drawBehind {
                if (running) {
                    drawActionAurora(
                        accent.actionAuroraBase,
                        accent.actionAurora,
                        motion.lights(size.width, size.height),
                        alpha = ActionFrost,
                    )
                } else {
                    drawRect(teal, alpha = ActionFrost)
                }
            }
            if (running) {
                AnalyzeAction(
                    onClick = { scope.launch { appState.cancelAnalysis() } },
                    container = Color.Transparent,
                    content = accent.onAction,
                    // No halo here. The aurora is what says the control is alive, and a glow around
                    // a button this wide lights the whole foot of the screen.
                    modifier = actionModifier,
                    painted = fill,
                    stretch = !big,
                    // The page's own hairline, the same edge the ready state wears, so the button
                    // keeps one line round it through all three of its states. What a press does
                    // here is the label's job: a red edge on a button whose fill is already moving
                    // read as a warning about the run rather than as a control.
                    outline = actionEdge,
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
                val blocked = MaterialTheme.colorScheme.surfaceContainerHigh
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
                    // Painted in every state now, blocked included: the frost is the button's
                    // material, and a state that kept a flat tint would be the one state made of
                    // something else. The colour is still the step down from surfaceContainerHighest
                    // this state has worn since the button went see-through.
                    container = Color.Transparent,
                    content = if (ready) accent.onAction else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (ready) haloed else actionModifier,
                    painted = if (ready) {
                        fill
                    } else {
                        frost.drawBehind {
                            drawRect(blocked, alpha = ActionFrost)
                        }
                    },
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
        // Above everything on a phone that has never got a report out of this app, and gone
        // entirely on one that has. See SetupCard.
        SetupCard(appState)
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
                    accent = CardHue.VIOLET.color,
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
                    accent = CardHue.AMBER.color,
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
                            showRecommendationDatePicker(context, appState)
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
                TextButton(onClick = { appState.openNotificationSettings() }) {
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

private fun showRecommendationDatePicker(context: Context, appState: AppState) {
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    // Never open later than the latest date the picker allows. The target session runs ahead of
    // today on a Friday, a Saturday, or any weekday after the close, and a DatePicker asked to open
    // outside its own range throws rather than clamping. Historical analysis refuses a future date
    // anyway, so today is the right place to land.
    val opensOn = minOf(appState.recommendationTargetDate, today)
    DatePickerDialog(
        context,
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
        accent = CardHue.PINK.color,
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
        containerColor = Glass.solid(MaterialTheme.colorScheme.surfaceContainerHigh),
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
    SectionCard(
        title = "Getting started",
        icon = Icons.Outlined.Checklist,
        accent = CardHue.AMBER.color,
        about = infoNote(
            "Getting started",
            "The four things this app needs before it can read anything, in the order they are " +
                "wanted. Each one is done by the card that owns it; this only says what is left.",
            "It goes as soon as this phone has a report, and Not now hides it until the app is " +
                "next opened.",
        ),
    ) {
        steps.forEach { step ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Icon(
                    if (step.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (step.done) "Done" else "Still to do",
                    tint = if (step.done) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(IconSize.Inline),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        step.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (step.done) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        step.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            // Offered only while something in Settings is actually outstanding. A button that takes
            // the reader somewhere with nothing to do there is the app wasting the one instruction
            // it has their attention for.
            if (steps.any { !it.done && it.settings }) {
                SettingsButton(onClick = { appState.navigate(AppDestination.SETTINGS) }) {
                    Text("Open Settings")
                }
            }
            SettingsButton(onClick = { dismissed = true }) {
                Text("Not now")
            }
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
 * How much of the button's own fill is laid over the frost.
 *
 * The fills were built to sit on a page they hid, at an alpha that already read as glass against a
 * flat ground - and over a blurred page that same alpha simply covers the blur up. Multiplied
 * rather than restated in the theme, so the hues stay the one list `PageAccent` publishes and this
 * number means what it says: what the frost is worth against the colour.
 *
 * Low enough that the page moving underneath is visible, high enough that the label keeps its
 * contrast over whatever happens to pass under it. Below about 0.6 a white heading scrolling past
 * came through the button and fought the button's own words.
 */
private const val ActionFrost = 0.66f

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
