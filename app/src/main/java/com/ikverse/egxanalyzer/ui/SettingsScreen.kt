package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.ui.kit.MoreRow
import com.ikverse.egxanalyzer.ui.kit.KitChip
import com.ikverse.egxanalyzer.ui.kit.tileSignals
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.FlowRow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ikverse.egxanalyzer.BuildConfig
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.ApproachAlerts
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.model.ModelUsageRecord
import com.ikverse.egxanalyzer.model.ResponseTimeout
import com.ikverse.egxanalyzer.model.RuleOrigin
import com.ikverse.egxanalyzer.model.Scoring
import com.ikverse.egxanalyzer.model.TelegramAuthStep
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.model.TokenUsage
import com.ikverse.egxanalyzer.model.UpdateState
import com.ikverse.egxanalyzer.model.formatTokenCount
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.KitDialog
import com.ikverse.egxanalyzer.ui.kit.KitIconButton
import com.ikverse.egxanalyzer.ui.kit.KitMenu
import com.ikverse.egxanalyzer.ui.kit.KitProgress
import com.ikverse.egxanalyzer.ui.kit.KitSplitBar
import com.ikverse.egxanalyzer.ui.kit.KitStepper
import com.ikverse.egxanalyzer.ui.kit.KitTextField
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.SplitColumns
import com.ikverse.egxanalyzer.ui.kit.MenuItem
import com.ikverse.egxanalyzer.ui.kit.Panel
import com.ikverse.egxanalyzer.ui.kit.SegmentedControl
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.popIn
import com.ikverse.egxanalyzer.ui.theme.palette
import java.time.ZoneId

/**
 * The parts of Settings, each a page of its own since 5.0.
 *
 * The page used to be seven cards that opened in place down one long scroll, which on the cover
 * screen meant the switch somebody came for was under three other open cards. The hub now says what
 * each part holds and pressing it opens that part on its own; unfolded, the parts are a column down
 * the left and the open one fills the right.
 *
 * **Reader was split in two in 5.3.** It held the AI and its key, what to send, both wording lists,
 * the prompt and the company names: four pages' worth down one scroll. The connection stayed under
 * Reader and what a run is told to read moved to Reading rules.
 */
internal enum class SettingsSection(val title: String) {
    TELEGRAM("Telegram"),
    ANALYSIS("Reader"),
    READING_RULES("Reading rules"),
    ASK_AI("Ask AI"),
    NOTIFICATIONS("Notifications"),
    GENERAL("Prices and trades"),
    DATA("Backup and sync"),
    ABOUT("About"),
}

/**
 * Settings, laid out for the posture the phone is in.
 *
 * **Nothing here scrolls as one page.** [Screen] is given a `paneScroll`, so it draws a frame and
 * each column brings its own scroll. Until 5.3 the list of parts and the open part shared the
 * page's single scroll, so reading down a long part took the list away with it and left half the
 * unfolded screen empty, with nothing saying which part was open.
 *
 * - **Cover**: the hub, or an open part under its back button and name, which stay put.
 * - **Unfolded and book**: the parts on the left, still; the open part on the right, scrolling
 *   under its own name. Book splits on the hinge and has room for each part's full summary.
 * - **Tabletop**: what is read stands on the upright half and never scrolls away; the parts and
 *   the controls lie on the flat half. A question mark sends its note up to the upright half
 *   rather than opening a sheet over the half being pressed.
 */
@Composable
internal fun SettingsScreen(appState: AppState) {
    // In PageState rather than a remember, so folding the phone does not drop the reader back on
    // the hub - see the head of PageState.
    var open by appState.pages.settingsSection
    val posture = LocalFoldPosture.current
    val wide = posture is FoldPosture.Unfolded || posture is FoldPosture.Book
    // Back closes a part and returns to the hub, where a part covers the whole page. Only on this
    // tab, so a Settings part left open never answers the back press of another page.
    BackHandler(enabled = open != null && !wide && appState.destination == AppDestination.SETTINGS) {
        open = null
    }
    val summaries = settingsSummaries(appState)
    val briefs = settingsBriefs(appState)
    val status = currentStatus(appState)
    val faults = remember(status) { status.lines.filter { it.ok == false }.map { it.section }.toSet() }

    // The open part's own scroll, which is also what the header's collapse and a press on the tab
    // already showing answer to. Back at the top whenever the part changes.
    val paneScroll = rememberScrollState(initial = appState.pages.scrollOffset(AppDestination.SETTINGS).value)
    val navScroll = rememberScrollState()
    var scrolledFor by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (open != scrolledFor) {
            scrolledFor = open
            paneScroll.scrollTo(0)
        }
    }

    Screen(appState = appState, destination = AppDestination.SETTINGS, paneScroll = paneScroll) {
        when (posture) {
            is FoldPosture.Unfolded, is FoldPosture.Book -> {
                val shown = open ?: SettingsSection.TELEGRAM
                val book = posture is FoldPosture.Book
                SplitColumns(posture, unfoldedStart = 0.34f, modifier = Modifier.weight(1f), start = {
                    ScrollPane(navScroll, Modifier.weight(1f), gap = Kit.TileGap) {
                        SettingsStatusTile(status, onOpen = { open = it }, compact = true)
                        ThemeControl(appState)
                        // One line a part unfolded, where the column is a third of the screen; the
                        // whole summary in book posture, where it is half.
                        PartsList(shown, faults, if (book) summaries else briefs, stacked = book) { open = it }
                    }
                }, end = {
                    PartTitle(shown, summaries.getValue(shown))
                    ScrollPane(paneScroll, Modifier.weight(1f), gap = Kit.TileGap) {
                        SectionPage(shown, appState)
                    }
                })
            }

            is FoldPosture.Tabletop -> {
                val shown = open ?: SettingsSection.TELEGRAM
                var kind by appState.pages.settingsNotification
                // The note a question mark below last sent up. Dropped when the part changes: an
                // explanation of a switch that is no longer on the flat half explains nothing.
                var explained by remember(shown) { mutableStateOf<InfoNote?>(null) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(tabletopHeroHeight(posture))
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Kit.TileGap),
                ) {
                    val note = explained
                    when {
                        shown == SettingsSection.NOTIFICATIONS -> NotificationExplainer(kind)
                        note != null -> NoteExplainer(shown, note)
                        else -> {
                            SettingsStatusTile(status, onOpen = { open = it })
                            Text(
                                "Press ? on a line below and its explanation shows here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.muted,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                    ScrollPane(navScroll, Modifier.width(TabletopPartsWidth).fillMaxHeight(), gap = Kit.TileGap) {
                        PartsList(shown, faults, lines = null, stacked = false) { open = it }
                        ThemeControl(appState)
                    }
                    CompositionLocalProvider(LocalInfoNoteSink provides { explained = it }) {
                        ScrollPane(paneScroll, Modifier.weight(1f).fillMaxHeight(), gap = Kit.TileGap) {
                            if (shown == SettingsSection.NOTIFICATIONS) {
                                NotificationKeys(appState, selected = kind, onSelect = { kind = it })
                            } else {
                                SectionPage(shown, appState)
                            }
                        }
                    }
                }
            }

            else -> {
                val section = open
                if (section == null) {
                    ScrollPane(paneScroll, Modifier.weight(1f), gap = Kit.TileGap) {
                        SettingsHub(appState, status, faults, summaries) { open = it }
                    }
                } else {
                    SectionHeader(section) { open = null }
                    ScrollPane(paneScroll, Modifier.weight(1f), gap = Kit.TileGap) {
                        SectionPage(section, appState)
                    }
                }
            }
        }
    }
}

/** How wide the parts stand on the flat half in tabletop: a name and a dot, and the theme control. */
private val TabletopPartsWidth = 212.dp

/** Whether the three things this app cannot work without are working, read off the state. */
@Composable
private fun currentStatus(appState: AppState): SettingsStatus = settingsStatus(
    telegramReady = appState.telegramAuthState.step == TelegramAuthStep.READY,
    credentialVerified = appState.credentialVerified,
    hasCredential = appState.cloudConfiguration.hasCredential,
    model = appState.cloudConfiguration.model,
    lastPriceRefreshAt = appState.lastPriceRefreshAt,
    pricesRefreshing = appState.pricesRefreshing,
    feedClean = appState.priceHealth.clean,
    feedFaults = appState.priceHealth.faults.size,
    stocksNamed = appState.priceHealth.stocksNamed,
    now = java.time.Instant.now(),
)

/** Which prompt a run is sent with, as the parts say it: "Prompt v3", or the shipped one. */
private fun promptLabel(appState: AppState): String = if (appState.useDefaultPromptOnly) {
    "Default prompt"
} else {
    "Prompt v" + (appState.promptVersions.firstOrNull { it.id == appState.activePrompt.id }?.sequence ?: 1)
}

/**
 * A part in two or three words, for the single-line rows beside an open part unfolded, where the
 * column is a third of the screen and the whole summary wrapped to three lines.
 */
@Composable
private fun settingsBriefs(appState: AppState): Map<SettingsSection, String> {
    val askSearching = remember(appState.opinionSettingsRevision) { appState.opinionSearchEnabled() }
    val signedIn = appState.telegramAuthState.step == TelegramAuthStep.READY
    val health = appState.priceHealth
    return mapOf(
        SettingsSection.TELEGRAM to if (signedIn) "${appState.channels.size} chats" else "signed out",
        SettingsSection.ANALYSIS to when (appState.credentialVerified) {
            true -> "key working"
            false -> "key rejected"
            null -> if (appState.cloudConfiguration.hasCredential) "key not checked" else "no key"
        },
        SettingsSection.READING_RULES to promptLabel(appState).replaceFirstChar(Char::lowercase),
        SettingsSection.ASK_AI to if (askSearching) "web search on" else "no web search",
        SettingsSection.NOTIFICATIONS to "${notificationSwitches(appState).count { it }} of $NOTIFICATION_COUNT on",
        SettingsSection.GENERAL to when {
            !health.clean -> "${health.faults.size} quiet"
            appState.marketRefreshEnabled -> "all session"
            else -> "once a day"
        },
        SettingsSection.DATA to if (appState.telegramSyncEnabled) "sync on" else "sync off",
        SettingsSection.ABOUT to when (val state = appState.updateState) {
            is UpdateState.Available -> "${state.update.versionName} available"
            is UpdateState.Ready -> "${state.update.versionName} ready"
            else -> BuildConfig.VERSION_NAME
        },
    )
}

/** The one line each part carries on the hub and in the column beside an open part. */
@Composable
private fun settingsSummaries(appState: AppState): Map<SettingsSection, String> {
    val askSearching = remember(appState.opinionSettingsRevision) { appState.opinionSearchEnabled() }
    val askWindow = remember(appState.opinionSettingsRevision) { appState.opinionNewsWindowDays() }
    val signedIn = appState.telegramAuthState.step == TelegramAuthStep.READY
    val config = appState.cloudConfiguration
    val prefs = appState.appPreferences
    return mapOf(
        SettingsSection.TELEGRAM to if (signedIn) "Signed in · ${appState.channels.size} chats" else "Not signed in",
        SettingsSection.ANALYSIS to config.model.ifBlank { "No AI chosen" } + " · " + when (appState.credentialVerified) {
            true -> "key working"
            false -> "key rejected"
            null -> if (config.hasCredential) "key not checked" else "no key"
        },
        SettingsSection.READING_RULES to promptLabel(appState) + " · " +
            appState.ruleSet.all.count { it.origin == RuleOrigin.USER }.let { mine ->
                if (mine == 0) "built-in wording only" else "$mine wording added"
            },
        SettingsSection.ASK_AI to if (askSearching) "Searches the web · $askWindow days back" else "No web search",
        SettingsSection.NOTIFICATIONS to "${notificationSwitches(appState).count { it }} of $NOTIFICATION_COUNT on",
        SettingsSection.GENERAL to
            (if (appState.marketRefreshEnabled) "Fresh all session" else "Fetched once a day") +
            " · ${prefs.defaultTradeWindowSessions}-session trades",
        SettingsSection.DATA to "${appState.savedResults.size} reports · " +
            (if (appState.telegramSyncEnabled) "sync on" else "sync off") + " · " +
            (if (appState.backupFolder == null) "no backup folder" else "backing up to a folder"),
        SettingsSection.ABOUT to when (val state = appState.updateState) {
            is UpdateState.Available -> "Version ${state.update.versionName} available"
            is UpdateState.Ready -> "Version ${state.update.versionName} ready"
            else -> "Version ${BuildConfig.VERSION_NAME}"
        },
    )
}

/**
 * The seven switches, in one list, so the hub's count and the page's switches cannot disagree.
 * [NOTIFICATION_COUNT] is the same seven, checked against this.
 */
private fun notificationSwitches(appState: AppState): List<Boolean> =
    NotificationKind.entries.map { it.isOn(appState.appPreferences) }.also {
        check(it.size == NOTIFICATION_COUNT) { "NOTIFICATION_COUNT is $NOTIFICATION_COUNT but ${it.size} switches are drawn" }
    }

// ── The hub ────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsHub(
    appState: AppState,
    status: SettingsStatus,
    faults: Set<SettingsSection>,
    summaries: Map<SettingsSection, String>,
    onOpen: (SettingsSection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        // What is working leads: nothing else on this page matters while one of the three is not.
        SettingsStatusTile(status, onOpen, modifier = Modifier.popIn(0))
        // The theme, on the hub itself since 5.3. It was a card inside Prices and trades, whose
        // summary had to lead with "Light" for anybody to find it.
        ThemeControl(appState, Modifier.popIn(1))
        // Every part the same shape, in two columns, its value and detail read off the same
        // summary the column beside an open part shows - so the two cannot disagree.
        val update = appState.updateState is UpdateState.Available || appState.updateState is UpdateState.Ready
        HubTones.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                pair.forEachIndexed { column, (section, tone) ->
                    val summary = summaries.getValue(section)
                    val about = section == SettingsSection.ABOUT
                    HubTile(
                        section,
                        // An update waiting is the one thing the hub says in cobalt.
                        if (about && update) TileTone.HERO else tone,
                        value = summary.substringBefore(" · "),
                        detail = if (about && update) "this build ${BuildConfig.VERSION_NAME}" else summary.substringAfter(" · ", ""),
                        fault = section in faults,
                        index = 2 + row * 2 + column,
                        onOpen = onOpen,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** The hub's eight parts, in order, and the fill each one stands on. */
private val HubTones = listOf(
    SettingsSection.TELEGRAM to TileTone.TILE3,
    SettingsSection.ANALYSIS to TileTone.TILE2,
    SettingsSection.READING_RULES to TileTone.CARD,
    SettingsSection.ASK_AI to TileTone.CARD,
    SettingsSection.NOTIFICATIONS to TileTone.TILE1,
    SettingsSection.GENERAL to TileTone.HIGHLIGHT,
    SettingsSection.DATA to TileTone.CARD,
    SettingsSection.ABOUT to TileTone.CARD,
)

/**
 * Whether the three things this app cannot work without are working. Coral while one of them is
 * not, and then a press opens the first part with something wrong; ink while all is well, with
 * nothing to press.
 *
 * It was ink either way until 5.3, beside a part marked as open in the same ink - so a warning and
 * a selection were the same dark tile, one above the other.
 *
 * @param compact the column beside an open part, unfolded: the headline, and under it only what is
 *   not working rather than all three lines.
 */
@Composable
private fun SettingsStatusTile(
    status: SettingsStatus,
    onOpen: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val fault = status.firstFault
    val tone = if (fault != null) TileTone.ALERT else TileTone.INK
    val signals = tileSignals(tone)
    Tile(
        modifier.fillMaxWidth(),
        tone = tone,
        onClick = fault?.let { { onOpen(it) } },
        onClickLabel = fault?.let { "Open ${it.title}" },
    ) {
        Text(status.headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        if (compact) {
            val unsettled = status.lines.filter { it.ok != true }
            if (unsettled.isNotEmpty()) {
                Text(
                    unsettled.joinToString(" · ") { it.text },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            Column(Modifier.padding(top = Space.s), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                status.lines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Icon(
                            when (line.ok) {
                                true -> Icons.Outlined.CheckCircle
                                false -> Icons.Outlined.Warning
                                null -> Icons.Outlined.Schedule
                            },
                            contentDescription = null,
                            tint = when (line.ok) {
                                true -> signals.gain
                                false -> signals.loss
                                null -> LocalContentColor.current.copy(alpha = 0.72f)
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            line.text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (line.ok == false) FontWeight.Bold else null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Light, dark or the phone's own, on the hub and beside an open part. Pure black sits under it
 * while the choice can be dark, and nowhere while it cannot.
 */
@Composable
private fun ThemeControl(appState: AppState, modifier: Modifier = Modifier) {
    val prefs = appState.appPreferences
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        SegmentedControl(
            options = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM),
            selected = prefs.themeMode,
            onSelect = appState::updateThemeMode,
            // "Follow system" does not fit a third of a narrow column.
            label = { if (it == ThemeMode.SYSTEM) "System" else it.displayName },
            modifier = Modifier.fillMaxWidth(),
        )
        if (prefs.themeMode != ThemeMode.LIGHT) {
            Tile(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                SettingToggle(
                    label = "Pure black background",
                    checked = prefs.pureBlackDarkMode,
                    onCheckedChange = appState::updatePureBlackDarkMode,
                    about = infoNote(
                        "Pure black background",
                        "Replaces dark mode's graphite ground and cards with true black.",
                        "Only visible while dark mode is actually showing - under System it takes " +
                            "effect whenever the phone's own setting lands on dark.",
                        "The theme is applied immediately on the outer and inner displays. Motion " +
                            "is separate: it follows the system's remove-animations setting.",
                    ),
                )
            }
        }
    }
}

/** The mark on a part with something wrong in it. Red, and said aloud as what it means. */
@Composable
private fun FaultDot(color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = "Needs a look" },
    )
}

@Composable
private fun HubTile(
    section: SettingsSection,
    tone: TileTone,
    value: String,
    detail: String,
    fault: Boolean,
    index: Int,
    onOpen: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Tile(
        modifier.popIn(index),
        tone = tone,
        onClick = { onOpen(section) },
        onClickLabel = "Open ${section.title}",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (fault) FaultDot(tileSignals(tone).loss)
            Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures),
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (detail.isNotBlank()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.78f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The parts as rows in one tile: beside an open part unfolded and in book posture, and on the flat
 * half in tabletop.
 *
 * The open part is a lighter row, not the ink tile it was until 5.3, so it cannot be read as a
 * second warning under the status. A part with something wrong carries a dot.
 *
 * @param lines what each part says after its name, or null for the names alone.
 * @param stacked the line under the name, wrapping, rather than beside it on one line.
 */
@Composable
private fun PartsList(
    shown: SettingsSection?,
    faults: Set<SettingsSection>,
    lines: Map<SettingsSection, String>?,
    stacked: Boolean,
    onOpen: (SettingsSection) -> Unit,
) {
    Tile(Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp)) {
        SettingsSection.entries.forEach { section ->
            val selected = section == shown
            val line = lines?.get(section)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) palette.raised else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onOpen(section) })
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (section in faults) FaultDot(palette.loss)
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!stacked && line != null) {
                        Text(line, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1)
                    }
                }
                if (stacked && line != null) {
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The open part's name over its column, unfolded and in book posture. It does not scroll. */
@Composable
private fun PartTitle(section: SettingsSection, summary: String) {
    Column(Modifier.padding(start = 4.dp)) {
        Text(section.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tabletop's upper half once a question mark below has been pressed: that note, in full. */
@Composable
private fun NoteExplainer(section: SettingsSection, note: InfoNote) {
    Tile(Modifier.fillMaxWidth()) {
        Text("${section.title} · explained", style = MaterialTheme.typography.labelMedium, color = palette.muted)
        Text(note.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Column(Modifier.padding(vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            note.paragraphs.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
        note.sample?.let { NotificationPreview(it) }
    }
}

/** An open part's own heading on the cover screen: the way back, and its name. It does not scroll. */
@Composable
private fun SectionHeader(section: SettingsSection, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KitIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Settings", onClick = onBack, filled = true)
        Column {
            Text("Settings", style = MaterialTheme.typography.labelMedium, color = palette.muted)
            Text(section.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** A titled group of settings, as one tile. */
@Composable
private fun Group(
    title: String,
    modifier: Modifier = Modifier,
    tone: TileTone = TileTone.CARD,
    summary: String? = null,
    about: InfoNote? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Tile(modifier.fillMaxWidth(), tone = tone) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            about?.let { InfoButton(it) }
        }
        summary?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.72f))
        }
        Column(
            Modifier.padding(top = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m),
            content = content,
        )
    }
}

/** A setting that is a whole number, its name on the left and the stepper on the right. */
@Composable
private fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    unit: String,
    about: InfoNote? = null,
    step: Int = 1,
) {
    SettingRow(about = about, trailing = {
        KitStepper(value, onValueChange, range, label = label, step = step)
    }) {
        Column(Modifier.weight(1f).padding(end = Space.s)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun SectionPage(section: SettingsSection, appState: AppState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        when (section) {
            SettingsSection.TELEGRAM -> TelegramPage(appState)
            SettingsSection.ANALYSIS -> AnalysisPage(appState)
            SettingsSection.READING_RULES -> ReadingRulesPage(appState)
            SettingsSection.ASK_AI -> AskAiPage(appState)
            SettingsSection.NOTIFICATIONS -> NotificationsPage(appState)
            SettingsSection.GENERAL -> GeneralPage(appState)
            SettingsSection.DATA -> DataPage(appState)
            SettingsSection.ABOUT -> AboutPage(appState)
        }
    }
}

// ── Telegram ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.TelegramPage(appState: AppState) {
    val note = infoNote(
        "Telegram",
        "The account is how this app reads anything at all: the chats ticked on Analyze " +
            "are read as you, on this device, and no chat you have not ticked is opened.",
        "It is also where your saved reports are kept - in a private channel of your own - " +
            "so signing out here stops both. Sending them there is under Backup and sync.",
    )
    if (appState.telegramAuthState.step == TelegramAuthStep.READY) {
        SignInProgress(step = SignInSteps + 1)
        Group("Signed in", summary = "${appState.channels.size} chats · choose which to read on Analyze", about = note) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                // Through runAction, like Analyze's pull-to-refresh of the same list: Telegram
                // answers a failed refresh by throwing, and launched bare that throw closed the app.
                KitButton(
                    "Refresh chats",
                    onClick = {
                        appState.startAction {
                            appState.runAction(label = "Refreshing chats", success = { "${appState.channels.size} chats" }) {
                                appState.refreshTelegramChats()
                            }
                        }
                    },
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                    enabled = appState.busyLabel == null,
                )
                // Through runAction too: signing out tears down the Telegram client, and without
                // the busy label nothing on screen says so.
                KitButton(
                    "Sign out",
                    onClick = {
                        appState.startAction {
                            appState.runAction(label = "Signing out", success = { "Signed out" }) { appState.logoutTelegram() }
                        }
                    },
                    kind = ButtonKind.DANGER,
                    size = ButtonSize.SMALL,
                    enabled = appState.busyLabel == null,
                )
            }
        }
    } else {
        // The flow itself rather than a trip to Analyze for it: signing out is a button on this
        // page, so signing back in has to be reachable from it too.
        TelegramSignIn(appState, boxed = false)
    }
}

// ── Analysis ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.AnalysisPage(appState: AppState) {
    val posture = LocalFoldPosture.current
    var providerMenuOpen by remember { mutableStateOf(false) }
    var endpointMenuOpen by remember { mutableStateOf(false) }
    var credential by remember { mutableStateOf("") }
    var confirmRemoveCredential by remember { mutableStateOf(false) }
    var confirmResetProvider by remember { mutableStateOf(false) }

    if (confirmRemoveCredential) {
        KitDialog(
            title = "Remove the saved API key?",
            text = "Erases the key for this provider and the model list loaded with it. Analysis " +
                "cannot run again until a key is saved.",
            confirmLabel = "Remove",
            onConfirm = {
                appState.removeCredential()
                confirmRemoveCredential = false
            },
            dismissLabel = "Cancel",
            onDismiss = { confirmRemoveCredential = false },
            posture = posture,
            tone = TileTone.ALERT,
            destructive = true,
        )
    }
    if (confirmResetProvider) {
        KitDialog(
            title = "Reset this provider?",
            text = "Erases the chosen endpoint, the chosen model and the model list loaded with " +
                "it, back to their defaults. The saved API key is not touched.",
            confirmLabel = "Reset",
            onConfirm = {
                appState.resetProviderConfiguration()
                confirmResetProvider = false
            },
            dismissLabel = "Cancel",
            onDismiss = { confirmResetProvider = false },
            posture = posture,
        )
    }

    // Whether the reader can work, said first, in words. It replaces the pill that said the same
    // thing about the key alone.
    ReaderStatusTile(appState)

    // The model that reads the cards, then who is paid for it. What a run is told to read is its
    // own part, Reading rules; everything only a careful reader needs is under Advanced.
    AnalysisModelCard(appState)

    Group(
        "Provider · ${appState.cloudConfiguration.provider.displayName}",
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
        Box {
            KitButton(
                "Provider: ${appState.cloudConfiguration.provider.displayName}",
                onClick = { providerMenuOpen = true },
                kind = ButtonKind.SECONDARY,
                size = ButtonSize.SMALL,
            )
            KitMenu(
                expanded = providerMenuOpen,
                onDismiss = { providerMenuOpen = false },
                items = CloudProvider.entries.map { provider ->
                    MenuItem(provider.displayName) {
                        appState.selectProvider(provider)
                        credential = ""
                    }
                },
            )
        }
        KitTextField(
            value = credential,
            onValueChange = { credential = it },
            label = if (appState.cloudConfiguration.hasCredential) "Replace saved API key (optional)" else "API key",
            modifier = Modifier.fillMaxWidth(),
            password = true,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            KitButton(
                "Save and verify",
                onClick = {
                    val entered = credential
                    credential = ""
                    appState.startAction { appState.saveSettings(entered) }
                },
                size = ButtonSize.SMALL,
                enabled = appState.busyLabel == null,
            )
            if (appState.cloudConfiguration.hasCredential) {
                KitButton("Remove key", onClick = { confirmRemoveCredential = true }, kind = ButtonKind.DANGER, size = ButtonSize.SMALL)
            }
        }
        appState.settingsMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (appState.credentialVerified == false) palette.loss else palette.muted,
            )
        }
    }

    // What only a careful reader needs, closed until asked for, on PageState so a fold while
    // editing the endpoint does not shut it.
    MoreRow(
        "Advanced",
        appState.pages.settingsAdvancedOpen,
        summary = "Connection, waiting time, retries and usage",
    ) {
        // Wrapped rather than held to one line: an address is longer than the field, and on one
        // line it was sliced mid-word with nothing to say there was more. An address holds no
        // spaces or line breaks, so none typed into a field that now takes them is kept.
        KitTextField(
            value = appState.cloudConfiguration.endpoint,
            onValueChange = { appState.updateEndpoint(it.filterNot(Char::isWhitespace)) },
            label = "HTTPS API endpoint",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            singleLine = false,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            if (appState.cloudConfiguration.provider.endpointPresets.isNotEmpty()) {
                Box {
                    KitButton(
                        "Choose endpoint region",
                        onClick = { endpointMenuOpen = true },
                        kind = ButtonKind.SECONDARY,
                        size = ButtonSize.SMALL,
                    )
                    KitMenu(
                        expanded = endpointMenuOpen,
                        onDismiss = { endpointMenuOpen = false },
                        items = appState.cloudConfiguration.provider.endpointPresets.map { preset ->
                            MenuItem(preset.displayName) { appState.updateEndpoint(preset.endpoint) }
                        },
                    )
                }
            }
            KitButton("Reset provider", onClick = { confirmResetProvider = true }, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
        }
        StepperRow(
            label = "Cloud response timeout",
            value = appState.appPreferences.responseTimeoutSeconds,
            range = ResponseTimeout.MIN..ResponseTimeout.MAX,
            onValueChange = appState::updateResponseTimeout,
            unit = "seconds, in steps of 30",
            step = 30,
            about = infoNote(
                "Cloud response timeout",
                "How long the model may think about one batch of images. It sends nothing " +
                    "until it has finished, so a batch that needs longer than this is hung " +
                    "up on.",
            ),
        )
        StepperRow(
            label = "Correction retries",
            value = appState.appPreferences.correctionRetries,
            range = 0..2,
            onValueChange = appState::updateCorrectionRetries,
            unit = "each one is another paid request",
            about = infoNote(
                "Correction retries",
                "What happens to an answer that comes back malformed. A correction retry " +
                    "hands the model its own reply and the error, and asks again - each one " +
                    "is another paid request, which is why the ceiling is two.",
            ),
        )
        // A heading on the row's own inset rather than a tile inside the tile, which set every
        // figure a second margin in from the settings above it.
        Row(Modifier.padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Token usage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(tokenUsageSummary(appState.modelUsage), style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
            InfoButton(infoNote(
                "Token usage",
                "Every request comes back with the token count the provider billed it at. " +
                    "This is those counts added up per model, on this phone alone - it is " +
                    "not synced, because a token count describes one phone's spending.",
                "A record, not a limit: nothing here stops or slows a run. Clearing it " +
                    "forgets the record and not the spending, and the provider's own billing " +
                    "page remains the account that matters.",
            ))
        }
        ModelUsageSection(appState)
    }
}

// ── Reading rules ──────────────────────────────────────────────────────────────────────────────

/**
 * What a run is told to read, in the order a run uses it: what is sent, the wording it keeps and
 * drops, the prompt that carries it, and the names checked afterwards. Split out of Reader in 5.3.
 */
@Composable
private fun ColumnScope.ReadingRulesPage(appState: AppState) {
    var languageMenuOpen by remember { mutableStateOf(false) }

    Group(
        "What to send",
        summary = "${appState.appPreferences.analysisLanguage.displayName} · " +
            // Counted over what is offered, not over what is stored: a preference saved while
            // voice was still on the screen still carries it.
            "${appState.appPreferences.defaultContentTypes.count { it in AnalysisContentType.OFFERED }} content types",
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
        Box {
            KitButton(
                "Output language: ${appState.appPreferences.analysisLanguage.displayName}",
                onClick = { languageMenuOpen = true },
                kind = ButtonKind.SECONDARY,
                size = ButtonSize.SMALL,
            )
            KitMenu(
                expanded = languageMenuOpen,
                onDismiss = { languageMenuOpen = false },
                items = AnalysisLanguage.entries.map { language ->
                    MenuItem(language.displayName) { appState.updateAnalysisLanguage(language) }
                },
            )
        }
        SettingLabel("Default Telegram content")
        AnalysisContentType.OFFERED.forEach { type ->
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
    }

    // A heading on the page rather than a tile round the two lists, because the lists are tiles of
    // their own.
    Row(Modifier.padding(start = 4.dp, top = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Wording", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(
                appState.ruleSet.all.count { it.origin == RuleOrigin.USER }.let { mine ->
                    if (mine == 0) "Built-in wording only" else "$mine added"
                } + " · read on this phone before anything is sent",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
        InfoButton(WordingFlowNote)
    }
    AnalysisRulesSection(appState)

    Group(
        "Prompt",
        about = GeneratedPromptNote,
        summary = if (appState.useDefaultPromptOnly) {
            "Default only"
        } else {
            "v" + (appState.promptVersions.firstOrNull { it.id == appState.activePrompt.id }?.sequence ?: 1)
        },
    ) {
        GeneratedPromptSection(appState)
    }

    Group(
        "Company names",
        summary = if (appState.appPreferences.catalogEnrichmentEnabled) "Added from the list on this phone" else "Not added",
        about = infoNote(
            "Company names",
            "The catalog is the list of Cairo listings held on this phone. Enriching " +
                "against it fills in a company's names and codes from that list rather " +
                "than from the model, so a ticker read off a screenshot is checked " +
                "against something that cannot be misremembered. It costs nothing and " +
                "sends nothing.",
        ),
    ) {
        SettingToggle(
            label = "Enrich results with the on-device EGX catalog",
            checked = appState.appPreferences.catalogEnrichmentEnabled,
            onCheckedChange = appState::updateCatalogEnrichment,
        )
        KitButton(
            "Refresh EGX catalog",
            onClick = { appState.startAction { appState.refreshEgxCatalog() } },
            kind = ButtonKind.SECONDARY,
            size = ButtonSize.SMALL,
            icon = Icons.Outlined.CloudDownload,
        )
        Text(appState.catalogMessage, style = MaterialTheme.typography.bodySmall, color = palette.muted)
    }
}

/** Whether the reader can work, in words, on a tile coloured by the answer. */
@Composable
private fun ReaderStatusTile(appState: AppState) {
    val config = appState.cloudConfiguration
    val (text, tone) = when {
        appState.credentialVerified == false -> "The key was rejected" to TileTone.ALERT
        !config.hasCredential -> "No key yet" to TileTone.TILE1
        config.model.isBlank() -> "No AI chosen" to TileTone.TILE1
        appState.credentialVerified == null -> "Key saved, not checked yet" to TileTone.TILE1
        else -> "Ready to read" to TileTone.HIGHLIGHT
    }
    Tile(Modifier.fillMaxWidth(), tone = tone) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = Space.s)) {
                Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(
                    config.model.ifBlank { config.provider.displayName },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.72f),
                )
            }
            // A saved key that has never been tried: the tile says so, and this tries it. Saving
            // with nothing typed keeps the stored key and sends the one free request that checks it.
            if (config.hasCredential && config.model.isNotBlank() && appState.credentialVerified == null) {
                KitButton(
                    "Check key",
                    onClick = { appState.startAction { appState.saveSettings("") } },
                    size = ButtonSize.SMALL,
                    enabled = appState.busyLabel == null,
                )
            }
        }
    }
}

// ── Ask AI ─────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.AskAiPage(appState: AppState) {
    var askModelMenuOpen by remember { mutableStateOf(false) }
    // One SharedPreferences read each, keyed on the revision the setters bump: neither value is
    // observable on its own, so without the key the page would go on printing what it opened with.
    val askModel = remember(appState.opinionSettingsRevision, appState.cloudConfiguration) { appState.opinionModel() }
    val askSearching = remember(appState.opinionSettingsRevision) { appState.opinionSearchEnabled() }
    val askWindow = remember(appState.opinionSettingsRevision) { appState.opinionNewsWindowDays() }
    val askDeep = remember(appState.opinionSettingsRevision) { appState.opinionDeepSearch() }
    val askResults = remember(appState.opinionSettingsRevision) { appState.opinionSearchResults() }

    // One heading and one question mark. The card used to name its single field three times -
    // "Model", "Which model to name", "Model" - with a question mark on two of them.
    Group(
        "Ask AI model",
        about = infoNote(
            "Ask AI model",
            "The button on a call card in Insights. It asks what the model makes of the " +
                "stock at today's price and what it makes of the levels the channel " +
                "printed, and it answers in Arabic.",
            "Each press is one paid request, confirmed first, and the answer is kept on " +
                "the card - opening it again costs nothing.",
            "Name a text model, not the vision one a run needs. The analysis reads " +
                "screenshots; this request carries no image, and paying vision " +
                "rates for it buys nothing.",
        ),
    ) {
        KitTextField(
            value = askModel,
            onValueChange = appState::updateOpinionModel,
            label = "Model",
            modifier = Modifier.fillMaxWidth(),
            figure = true,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
        )
        KitButton("Choose model", onClick = { askModelMenuOpen = true }, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
        if (askModelMenuOpen) {
            ModelPickerSheet(
                appState = appState,
                title = "Ask AI model",
                selected = askModel,
                // Sends no image - it is a plain question about a call already on screen - so a
                // text-only chat model the analysis picker would hold back is exactly right here.
                requireVision = false,
                onChoose = appState::updateOpinionModel,
                onDismiss = { askModelMenuOpen = false },
            )
        }
    }

    Group("Search") {
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
        // rather than left greyed out under an unticked switch.
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
            SegmentedControl(
                options = NEWS_WINDOWS,
                selected = askWindow,
                onSelect = appState::updateOpinionNewsWindow,
                label = { "$it days" },
                modifier = Modifier.fillMaxWidth(),
            )
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
            SegmentedControl(
                options = SEARCH_RESULTS,
                selected = askResults,
                onSelect = appState::updateOpinionSearchResults,
                label = { "$it" },
                modifier = Modifier.fillMaxWidth(),
            )
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
}

// ── Notifications ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.NotificationsPage(appState: AppState) {
    val prefs = appState.appPreferences

    @Composable
    fun toggle(kind: NotificationKind) = SettingToggle(
        label = kind.label,
        detail = kind.detail,
        checked = kind.isOn(prefs),
        onCheckedChange = { kind.set(appState, it) },
        about = notificationNote(kind).withSample(kind),
    )

    Group(
        "Your trades",
        summary = switchesOn(prefs.overdueRemindersEnabled, prefs.tradeAlertsEnabled, prefs.approachAlertsEnabled),
        about = infoNote(
            "Notifications",
            "Each of these reports something that has already happened - a level reached, a " +
                "deadline passed, the feed going quiet. None of them says what to do about " +
                "it, and none of them can start a run or spend anything.",
            "Android silences a whole channel at a time, so these arrive on several channels " +
                "rather than one: muting news about your trades cannot quietly mute the app " +
                "telling you it has stopped working.",
            "The first group is the three about positions you actually hold. Two of them arrive " +
                "after the fact - a stop announced is a stop already taken - and the third is the " +
                "only one that reaches you while you can still decide something.",
        ),
    ) {
        Text("Tap ? on any line to see an example.", style = MaterialTheme.typography.bodySmall, color = palette.muted)
        toggle(NotificationKind.OVERDUE)
        toggle(NotificationKind.TRADE)
        toggle(NotificationKind.APPROACH)
        if (prefs.approachAlertsEnabled) {
            StepperRow(
                label = "Close enough to warn about",
                value = prefs.approachThresholdPercent,
                range = ApproachAlerts.MIN_THRESHOLD_PERCENT..ApproachAlerts.MAX_THRESHOLD_PERCENT,
                onValueChange = appState::updateApproachThreshold,
                unit = "percent of the price",
                about = infoNote(
                    "Close enough to warn about",
                    "How near the price has to get before the warning above is raised, as a " +
                        "percentage of the price itself.",
                    "There is no right answer for everybody: a tight stop on a liquid large " +
                        "cap and a wide one on a thin mid cap mean different things by close.",
                ),
            )
        }
    }

    Group(
        "Calls and sessions",
        summary = switchesOn(prefs.callAlertsEnabled, prefs.sessionDigestEnabled),
        about = infoNote(
            "Calls and sessions",
            "The two about the market rather than about your money: a call you did not " +
                "take reaching its entry band, and what the whole session did.",
            "Both are off unless you switch them on, and neither depends on your holding " +
                "anything.",
        ),
    ) {
        toggle(NotificationKind.CALL)
        toggle(NotificationKind.DIGEST)
    }

    Group(
        "The app itself",
        summary = switchesOn(prefs.feedAlertsEnabled, prefs.reportReadyEnabled),
        about = infoNote(
            "The app itself",
            "Reports this app being unable to do its job, rather than anything the market " +
                "did, and the end of a run you left the app to wait for.",
            "On by default, and that is deliberate: the way this breaks is silence, so an " +
                "app that stopped working quietly would look exactly like a quiet week.",
        ),
    ) {
        toggle(NotificationKind.FEED)
        toggle(NotificationKind.REPORT_READY)
    }
}

/** What each notification is for, in the reader's words, titled with its label. */
private fun notificationNote(kind: NotificationKind): InfoNote = when (kind) {
    NotificationKind.OVERDUE -> infoNote(
        kind.label,
        "Once a day, and only while a trade you chose to keep open is past its " +
            "deadline with no sale recorded. A trade whose window simply ran out " +
            "closes as expired on its own and is never chased.",
        "Nothing is analyzed, so this never spends anything on the cloud. With either " +
            "of these two switches on, the phone does fetch prices once after the " +
            "exchange closes, so it knows what the session did before it says anything.",
    )
    NotificationKind.TRADE -> infoNote(
        kind.label,
        "A target reached, the stop taken, the deadline passed - whenever the market " +
            "moves one of your trades.",
        "The phone looks once at 14:45, after the exchange has closed and its figures " +
            "have settled, so a deadline that ran out this afternoon is said this " +
            "afternoon. It looks again on every price refresh, and once more each day " +
            "in case the alarm was dropped.",
        "Not for anything you do yourself: recording a sale or closing a trade says " +
            "nothing back to you.",
    )
    NotificationKind.APPROACH -> infoNote(
        kind.label,
        "Everything else here tells you after the fact - a stop announced is a stop " +
            "already taken. This is the one that arrives while you can still decide " +
            "something: a trade of yours has come within reach of its stop, or of " +
            "target 2.",
        "It says the distance and never what to do about it. Off unless you switch it " +
            "on, because a price near a level goes on being near it, and this is the " +
            "noisiest thing the app can say.",
        "It books no extra work: the check rides a recompute that was happening anyway.",
    )
    NotificationKind.CALL -> infoNote(
        kind.label,
        "For calls you have not taken: the price has traded into the entry band a " +
            "channel printed. Off unless you switch it on - it is the one notification " +
            "here about something you have not committed to.",
        "It says what the market did and never what to do about it, and it books no " +
            "extra work: the check rides a price refresh that was happening anyway.",
    )
    NotificationKind.DIGEST -> infoNote(
        kind.label,
        "One line after the close: how many calls reached targets, how many were " +
            "stopped out, how many ran out of time, and how many of them were yours.",
        "The gap it fills is the session where nothing of yours moved. Everything " +
            "else here is about one trade or one call, so an afternoon when three " +
            "calls from the channels you read hit targets and you held none of them " +
            "passed in silence.",
        "Off unless you switch it on: it arrives on a rhythm rather than because " +
            "something happened, which is the kind of notification that wears out " +
            "fastest. Said once per session, whatever else happens that evening.",
    )
    NotificationKind.FEED -> infoNote(
        kind.label,
        "A stock whose prices have stopped arriving looks exactly like a stock that " +
            "has not moved - the feed answers every request and its newest session " +
            "stays put. Meanwhile every rate on the record quietly rests on fewer " +
            "calls than it looks like.",
        "This has happened here once already and nothing noticed at the time.",
        "On by default, because it reports the app being unable to do its job rather " +
            "than something the market did. Said once when it starts, and not again " +
            "until the feed comes back and goes quiet a second time. Prices under " +
            "Prices and trades says how many stocks are affected and how much of the " +
            "record they are holding.",
    )
    NotificationKind.REPORT_READY -> infoNote(
        kind.label,
        "A run takes long enough to leave the app during. When it ends, one notification " +
            "says the report is ready and how many calls it found - or that the run did not " +
            "finish, and why. Tapping it opens the report, or the Analyze page to try again.",
        "On by default, and it makes a sound: it is the moment you left the app to wait " +
            "for. Stopping a run yourself is said by nothing.",
    )
}

/** Tabletop's upper half on Notifications: the one chosen, what it is for, and what it looks like. */
@Composable
private fun NotificationExplainer(kind: NotificationKind) {
    Tile(Modifier.fillMaxWidth()) {
        Text("Notifications · selected", style = MaterialTheme.typography.labelMedium, color = palette.muted)
        Text(kind.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Column(Modifier.padding(vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            notificationNote(kind).paragraphs.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
        NotificationPreview(kind.sample)
    }
}

/**
 * Tabletop's flat half on Notifications: a key per kind, two to a row. Pressing a key chooses it for
 * the explanation above the crease; only its switch switches it, so choosing one to read about
 * never changes it. No question marks, because the explanation is already above.
 */
@Composable
private fun NotificationKeys(appState: AppState, selected: NotificationKind, onSelect: (NotificationKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
        NotificationKind.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                pair.forEach { kind ->
                    val on = kind.isOn(appState.appPreferences)
                    Tile(
                        Modifier.weight(1f).fillMaxHeight(),
                        tone = if (kind == selected) TileTone.INK else TileTone.CARD,
                        onClick = { onSelect(kind) },
                        onClickLabel = "Explain ${kind.label}",
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(kind.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Box(
                                Modifier
                                    .toggleable(value = on, role = Role.Switch) {
                                        kind.set(appState, it)
                                        onSelect(kind)
                                    }
                                    .semantics { contentDescription = kind.label },
                            ) {
                                com.ikverse.egxanalyzer.ui.kit.KitSwitchFace(checked = on)
                            }
                        }
                        Text(
                            kind.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.72f),
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── General ────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.GeneralPage(appState: AppState) {
    // Prices here since 5.0: refreshing them and keeping their history are things the phone does
    // on its own, which is what this part is about, rather than data to be backed up.
    Tile(Modifier.fillMaxWidth()) {
        PricesSubSection(appState)
    }

    // "Trade defaults" rather than "Trades": the one control left is a default rather than a rule.
    // The window stopped judging the channels - a call is followed until it reaches a target or
    // the stop, and nothing on this page moves that.
    val window = appState.appPreferences.defaultTradeWindowSessions
    Group(
        "Trade defaults",
        about = infoNote(
            "Trade defaults",
            "What a new trade is offered when you press Bought. Nothing here changes " +
                "anything already recorded, and nothing here affects how the sources are " +
                "scored.",
        ),
    ) {
        StepperRow(
            label = "Default trade window",
            value = window,
            range = Scoring.MIN_WINDOW_SESSIONS..Scoring.MAX_WINDOW_SESSIONS,
            onValueChange = appState::updateDefaultTradeWindow,
            unit = if (window == 1) "trading session" else "trading sessions",
            about = infoNote(
                "Default trade window",
                "What a new trade's deadline is offered as when you press Bought, counted " +
                    "from the session the call was made for. You can type over it there, " +
                    "or later from Edit trade.",
                "It changes nothing already recorded, and it does not affect how the " +
                    "sources are scored - a call is followed for up to " +
                    "${Scoring.JUDGING_HORIZON_SESSIONS} sessions whatever this is set to, " +
                    "which is what Insights reports the timings of.",
            ),
        )
    }
}

// ── Data and backup ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.DataPage(appState: AppState) {
    val posture = LocalFoldPosture.current
    var confirmDeleteAll by remember { mutableStateOf(false) }
    if (confirmDeleteAll) {
        KitDialog(
            title = "Delete all saved analyses?",
            text = "This permanently removes ${appState.savedResults.size} saved analyses and " +
                "their source traces from this device, from your Telegram sync channel, " +
                "and from every other device that syncs with it. It cannot be undone. " +
                "Telegram and provider credentials are not changed.",
            confirmLabel = "Delete all",
            onConfirm = {
                appState.deleteAllResults()
                confirmDeleteAll = false
            },
            dismissLabel = "Cancel",
            onDismiss = { confirmDeleteAll = false },
            posture = posture,
            tone = TileTone.ALERT,
            destructive = true,
        )
    }
    val signedIn = appState.telegramAuthState.step == TelegramAuthStep.READY

    Group(
        "Sync",
        summary = if (appState.telegramSyncEnabled) "${appState.savedResults.size} reports on this device" else "Sync off",
        about = infoNote(
            "Sync",
            "Reports are kept in a private Telegram channel of your own, so every device " +
                "signed in to your account sees the same history.",
            "Correcting a call updates the report everywhere it has synced, and deleting a " +
                "report removes it from every device too. Nothing else changes on its " +
                "own: a report neither of those has touched simply travels once.",
        ),
    ) {
        SettingToggle(
            label = "Sync with Telegram",
            checked = appState.telegramSyncEnabled,
            onCheckedChange = appState::updateTelegramSync,
            about = infoNote(
                "Sync with Telegram",
                "Off, this phone sends nothing to your sync channel and reads nothing " +
                    "from it. Reading your source channels for an analysis is not affected.",
                "Nothing is lost while it is off: reports, trades, settings and deletions " +
                    "wait on this phone, and switching it back on catches up straight away. " +
                    "The switch is this phone's alone and does not travel to your others.",
            ),
        )
        KitButton(
            "Sync now",
            onClick = { appState.startAction { appState.syncReports() } },
            size = ButtonSize.SMALL,
            enabled = appState.telegramSyncEnabled && signedIn && appState.busyLabel == null,
        )
        if (!signedIn) {
            Text("Sign in to Telegram to sync.", style = MaterialTheme.typography.bodySmall)
        }
    }

    // Backup here rather than beside Save diagnostics below, which is the other thing that writes
    // the record to a file. That one is for whoever is chasing a bug; this is about the record.
    Group(
        "Backup",
        summary = "${appState.savedResults.size} analyses saved on this device · " +
            if (appState.backupFolder == null) "no folder chosen" else "a folder is chosen",
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
        BackupControls(appState)
    }

    Group(
        "Something not right?",
        summary = "Save a report of what the app was doing",
        about = infoNote(
            "Diagnostics",
            "Copies this device's saved record into Downloads, and the crash log with it " +
                "where the app has closed unexpectedly.",
            "No provider key and no Telegram key travels in it - those are encrypted " +
                "separately by Android Keystore and have never been part of it.",
        ),
    ) {
        DiagnosticsControl(appState)
    }

    // Its own tile, at the bottom, with nothing else on it. The one irreversible button on this
    // page should not sit at the end of a list of buttons that are not.
    val empty = appState.savedResults.isEmpty()
    Tile(
        Modifier.fillMaxWidth(),
        tone = TileTone.ALERT,
        onClick = if (empty) null else ({ confirmDeleteAll = true }),
        onClickLabel = "Delete all saved analyses",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Delete all analyses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (empty) "Nothing saved on this device" else "${appState.savedResults.size} on this device, and everywhere they synced",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            InfoButton(
                infoNote(
                    "Delete",
                    "This removes every saved analysis at once - from this device, from your " +
                        "Telegram sync channel, and from every other device that syncs with it. A " +
                        "single report can be deleted on its own from its own card, on Results.",
                    "Take a backup first if there is any doubt: nothing here can be undone, and " +
                        "sync cannot bring back what it has been told to forget.",
                ),
            )
        }
    }
}

// ── About ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.AboutPage(appState: AppState) {
    val state = appState.updateState
    val news = state is UpdateState.Available || state is UpdateState.Downloading || state is UpdateState.Ready
    Group(
        when (state) {
            is UpdateState.Available -> "Update available"
            is UpdateState.Downloading -> "Downloading"
            is UpdateState.Ready -> "Update ready"
            else -> "Updates"
        },
        tone = if (news) TileTone.HERO else TileTone.CARD,
        about = infoNote(
            "About",
            "Which build this phone is running, and whether there is a newer one.",
            "Updates are read from this app's own GitHub releases. Nothing is downloaded or " +
                "installed without you pressing for it, twice.",
        ),
    ) {
        UpdateControls(appState, onHero = news)
        Text(
            "This build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
            color = LocalContentColor.current.copy(alpha = 0.8f),
        )
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
 *
 * **The crash line above it is what makes the button get pressed.** A crash log nobody knows about
 * is a crash log nobody sends, and the reader of this app is not going to think to look: the app
 * reappeared, so as far as they know it recovered. The line is the app saying it did not, and it
 * only appears where there is something to say.
 */
@Composable
private fun DiagnosticsControl(appState: AppState) {
    var saving by remember { mutableStateOf(false) }
    // Read once into state rather than on every recomposition. It is a file read, it changes only
    // when the process dies - which is the one moment nothing is composing - and holding it is what
    // lets Forget take the line off screen without a reason to rebuild the whole page.
    var crash by remember { mutableStateOf(appState.lastCrash() to appState.crashCount()) }
    val (latest, count) = crash
    if (latest != null) {
        SettingRow(
            about = infoNote(
                "The app closed unexpectedly",
                "The app hit an error it could not carry on from and was shut down by Android.",
                "What it was doing is written down on this phone. Save a report puts it in " +
                    "Downloads as egx-crashes-….txt, beside the record, where it can be sent on.",
                "Forget removes it. Nothing is sent anywhere on its own.",
            ),
        ) {
            Text(
                if (count > 1) "Closed unexpectedly $count times, last $latest" else "Closed unexpectedly $latest",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = palette.loss,
            )
            KitButton(
                "Forget",
                onClick = {
                    appState.forgetCrashes()
                    crash = null to 0
                },
                kind = ButtonKind.SECONDARY,
                size = ButtonSize.SMALL,
            )
        }
    }
    KitButton(
        if (saving) "Saving…" else "Save a report",
        onClick = {
            appState.startAction {
                saving = true
                runCatching { appState.saveDiagnosticsToDownloads() }
                    .onSuccess { written ->
                        appState.statusMessage = StatusMessage("Saved to Downloads: " + written.joinToString(", "), succeeded = true)
                    }
                    .onFailure {
                        appState.statusMessage = StatusMessage(
                            it.message?.takeIf(String::isNotBlank) ?: "Could not save the report",
                            succeeded = false,
                        )
                    }
                saving = false
            }
        },
        kind = ButtonKind.SECONDARY,
        size = ButtonSize.SMALL,
        enabled = !saving,
    )
}

/**
 * Finding, fetching and installing a newer build, which is all of it as the user meets it.
 *
 * Two deliberate taps, Download then Install, and nothing between them happens on its own. The
 * second hands the file to Android's installer, which asks again in its own words - an app that
 * could replace itself unasked is what the permission behind this exists to prevent.
 */
@Composable
private fun UpdateControls(appState: AppState, onHero: Boolean) {
    val context = LocalContext.current
    val state = appState.updateState
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading
    val quiet = LocalContentColor.current.copy(alpha = 0.78f)
    val second = if (onHero) ButtonKind.ON_TONE else ButtonKind.SECONDARY
    // The one thing the card is asking for - Download, Install - is the filled button, and it
    // leads the row the others sit on. It was an outline alone on a line of its own, above them.
    val lead = ButtonKind.PRIMARY

    // Re-read on the way back rather than only at composition. The permission is granted on a
    // system page, and returning from one does not recompose a card on its own.
    var canInstall by remember { mutableStateOf(appState.canInstallUpdates()) }
    LifecycleResumeEffect(Unit) {
        canInstall = appState.canInstallUpdates()
        onPauseOrDispose { }
    }

    when (state) {
        UpdateState.Idle -> Unit
        UpdateState.Checking -> Text("Checking GitHub…", color = quiet)
        is UpdateState.UpToDate -> Text("Version ${state.versionName} is the newest release.", color = quiet)
        is UpdateState.Available -> {
            Text(
                "Version ${state.update.versionName} · ${state.update.sizeLabel}",
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures),
            )
            if (state.update.notes.isNotBlank()) {
                // Capped rather than scrolled: what does not fit is on the release page.
                Text(
                    state.update.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = quiet,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        is UpdateState.Downloading -> {
            KitProgress(
                state.progress,
                fill = palette.highlight,
                track = LocalContentColor.current.copy(alpha = 0.2f),
            )
            Text(
                "Downloading ${state.update.versionName} · ${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
            )
        }
        is UpdateState.Ready -> {
            Text(
                "Version ${state.update.versionName} is downloaded and signed by the same key as this build.",
                style = MaterialTheme.typography.bodySmall,
                color = quiet,
            )
            if (!canInstall) {
                Text(
                    "Android has to allow this app to install apps first. The button opens that " +
                        "setting; come back here afterwards and press Install.",
                    style = MaterialTheme.typography.bodySmall,
                    color = quiet,
                )
            }
        }
        is UpdateState.Failed -> Text(state.reason, color = if (onHero) LocalContentColor.current else palette.loss)
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        if (state is UpdateState.Available) {
            KitButton("Download", onClick = { appState.downloadUpdate(state.update) }, kind = lead, size = ButtonSize.SMALL)
        }
        if (state is UpdateState.Ready) {
        KitButton(
            // Two taps, and the label says which one this is. "Install" on a button that opens
            // a settings page is how someone grants the permission, comes back, and believes
            // the install has silently failed.
            if (canInstall) "Install" else "Allow installs",
            onClick = {
                // Read at the tap as well as on resume: whichever way the permission was
                // granted, the button must do the right thing the moment it is pressed.
                if (appState.canInstallUpdates()) {
                    appState.installUpdate(state.file)
                } else {
                    runCatching { appState.installPermissionIntent()?.let(context::startActivity) }
                        .onFailure { error ->
                            appState.reportUpdateProblem(
                                error.message?.takeIf(String::isNotBlank) ?: "Android would not open the installer.",
                            )
                        }
                }
            },
            kind = lead,
            size = ButtonSize.SMALL,
        )
        }
        KitButton(
            if (state is UpdateState.Idle) "Check for updates" else "Check again",
            onClick = appState::checkForUpdate,
            kind = second,
            size = ButtonSize.SMALL,
            enabled = !busy,
        )
        if (state is UpdateState.Available || state is UpdateState.Ready) {
            KitButton(
                "Release page",
                onClick = { appState.releasesPageIntent()?.let(context::startActivity) },
                kind = second,
                size = ButtonSize.SMALL,
            )
        }
        if (state is UpdateState.Failed || state is UpdateState.UpToDate) {
            KitButton("Dismiss", onClick = appState::dismissUpdate, kind = second, size = ButtonSize.SMALL)
        }
    }

    SettingToggle(
        label = "Check for updates when the app opens",
        checked = appState.appPreferences.updateChecksEnabled,
        onCheckedChange = appState::updateAutomaticUpdateChecks,
        onHero = onHero,
        about = infoNote(
            "Check for updates when the app opens",
            "One request to GitHub, and it says nothing unless there is a newer build. " +
                "Nothing is downloaded or installed without you pressing for it.",
        ),
    )
}

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
 * How many switches the Notifications page holds.
 *
 * Named rather than written into the summary, because "3 of 7 on" over a page showing eight
 * switches is the kind of wrong nobody notices for months.
 */
private const val NOTIFICATION_COUNT = 7

/**
 * "2 of 3 on", for a group of switches.
 *
 * Takes the flags rather than a count and a total, because those are two numbers a caller can get
 * out of step - which is the mistake [NOTIFICATION_COUNT] exists to stop the page making.
 */
private fun switchesOn(vararg flags: Boolean) = "${flags.count { it }} of ${flags.size} on"

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
    // The tally is read from disk, so it is re-read when the page is opened rather than held live:
    // a scheduled run can have spent since this screen was built.
    LaunchedEffect(Unit) { appState.refreshModelUsage() }
    val usage = appState.modelUsage
    if (usage.isEmpty()) {
        Text(
            "Nothing recorded yet. A run, or one Ask AI, writes what it cost here.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
        )
        return
    }
    val total = usage.fold(TokenUsage.NONE) { running, record -> running + record.usage }
    val requests = usage.sumOf(ModelUsageRecord::requests)
    // The three figures the whole tally amounts to. Abbreviated **here and nowhere else**: the
    // exact figure - the one checked against a provider's bill - is on every row below.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FigureCell(formatTokenCount(total.totalTokens), "tokens", Modifier.weight(1f))
        FigureCell(requests.toString(), "requests", Modifier.weight(1f))
        FigureCell(usage.size.toString(), if (usage.size == 1) "model" else "models", Modifier.weight(1f))
    }
    // Which way the spending runs. A run sends seventeen kilobytes of prompt against a few hundred
    // tokens of answer, so the split is lopsided by design - and seeing it lopsided is what tells
    // the reader that the cost is in what the app sends, not in what the model writes back.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KitSplitBar(total.promptTokens, total.totalTokens)
        Row(Modifier.fillMaxWidth()) {
            Text("${groupedTokens(total.promptTokens)} sent", style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures), modifier = Modifier.weight(1f))
            Text("${groupedTokens(total.completionTokens)} returned", style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures))
        }
    }
    usage.forEach { record -> ModelUsageRow(record, total.totalTokens) }
    KitButton(
        "Clear token usage",
        onClick = appState::clearModelUsage,
        kind = ButtonKind.SECONDARY,
        size = ButtonSize.SMALL,
        icon = Icons.Outlined.Delete,
    )
}

/**
 * One model's share of the tally, and what it is made of.
 *
 * The bar is this model against **everything spent**, not against its own total, which is the one
 * question a list of models raises: two models at 2.10M and 378K are a sixth apart, and reading that
 * off two grouped figures is arithmetic the screen can simply do. Every figure here stays exact.
 */
@Composable
private fun ModelUsageRow(record: ModelUsageRecord, spent: Long) {
    Panel(Modifier.fillMaxWidth(), color = palette.ground, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                record.model,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
                fontWeight = FontWeight.Bold,
                // A provider's id runs long and has no spaces to break on, so it takes the width
                // it needs and the figure beside it keeps its own.
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                groupedTokens(record.usage.totalTokens),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
                modifier = Modifier.padding(start = Space.s).widthIn(min = 48.dp),
            )
        }
        KitSplitBar(record.usage.totalTokens, spent, Modifier.padding(vertical = 6.dp), secondColor = palette.line, height = 4.dp)
        Text(
            listOfNotNull(
                "${groupedTokens(record.usage.promptTokens)} in / ${groupedTokens(record.usage.completionTokens)} out",
                "${record.requests} requests",
                record.lastUsed?.let { "last ${AppDates.DayMonthYear.format(it.atZone(ZoneId.systemDefault()))}" },
                record.provider.displayName,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
        )
        record.unreportedRequests.takeIf { it > 0 }?.let {
            Text(
                "$it request(s) came back with no usage, so this total is short by them.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
    }
}

/** The one line a closed part shows: what has been spent, and on how many models. */
private fun tokenUsageSummary(usage: List<ModelUsageRecord>): String {
    if (usage.isEmpty()) return "no tokens recorded"
    val total = usage.fold(TokenUsage.NONE) { running, record -> running + record.usage }
    return "${formatTokenCount(total.totalTokens)} tokens · ${usage.size} " + if (usage.size == 1) "model" else "models"
}

/** Exact rather than rounded: this is the screen where the figure is being checked against a bill. */
private fun groupedTokens(value: Long): String = String.format(java.util.Locale.US, "%,d", value)

/** How many steps the Telegram sign-in has, as the progress bar counts them. */
internal const val SignInSteps = 4
