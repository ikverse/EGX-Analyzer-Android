package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.forChat
import com.ikverse.egxanalyzer.model.messagesPerSource
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.KitCheckbox
import com.ikverse.egxanalyzer.ui.kit.KitChip
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.theme.palette

/**
 * The 5.0 Analyze hero: what the next run will read, and the button that starts it.
 *
 * The button is the old action's states in the new shape - an ink capsule with the highlight's text
 * at rest, dimmed while something blocks a run, and filling batch by batch (sweeping while the model
 * writes) once a run is going, where a press asks before it cancels. What blocks a run is said here
 * in words, quietly until Analyze has been pressed and in the alert colour after.
 */
@Composable
internal fun AnalyzeHero(
    appState: AppState,
    blocker: AnalyzeBlocker?,
    attempted: Boolean,
    requests: Int?,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** Draws the summary only, for tabletop, where the button sits under the crease on its own. */
    showButton: Boolean = true,
) {
    val p = palette
    val running = appState.analysisStatus == AnalysisStatus.RUNNING
    val messages = appState.telegramSources.size
    val chats = remember(appState.telegramSources) { messagesPerSource(appState.telegramSources).size }
    Tile(modifier = modifier.fillMaxWidth(), tone = TileTone.HERO) {
        Text(if (running) "Analysis running" else "Ready to analyze", style = MaterialTheme.typography.bodyMedium)
        Text(
            if (messages == 0) "No messages loaded" else "$messages ${if (messages == 1) "message" else "messages"}",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = TabularFigures),
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            buildList {
                if (chats > 0) add("from $chats ${if (chats == 1) "source" else "sources"}")
                requests?.let { add("$it ${if (it == 1) "request" else "requests"}") }
                add("cards read before are skipped")
            }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = p.onHero.copy(alpha = 0.85f),
        )
        if (blocker != null && !running) {
            Text(
                blocker.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = if (attempted) p.alert else p.onHero,
                modifier = Modifier.padding(top = Space.m),
            )
        }
        if (showButton) {
            AnalyzeButton(appState, blocker, requests, onAnalyze, onCancel, Modifier.padding(top = Space.l))
        }
        appState.analysisMessage?.takeIf { !running }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (appState.analysisStatus == AnalysisStatus.FAILED) p.alert else p.onHero.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = Space.m),
            )
        }
    }
}

/** The run's one button. See [AnalyzeHero]. */
@Composable
internal fun AnalyzeButton(
    appState: AppState,
    blocker: AnalyzeBlocker?,
    requests: Int?,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val running = appState.analysisStatus == AnalysisStatus.RUNNING
    val press = remember { MutableInteractionSource() }
    val ready = blocker == null
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(press)
            .height(72.dp)
            .clip(Kit.Capsule)
            .then(
                if (running) {
                    Modifier.runFill(appState.analysisProgress?.fraction, ground = p.ink, fill = p.highlight.copy(alpha = RunFillAlpha))
                } else {
                    Modifier.background(if (ready) p.ink else p.ink.copy(alpha = 0.35f))
                },
            )
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = if (running) onCancel else onAnalyze)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Icon(
            if (running) Icons.Outlined.StopCircle else Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = p.onInk,
            modifier = Modifier.size(26.dp),
        )
        CompositionLocalProvider(LocalContentColor provides p.onInk) {
            if (running) {
                RunningLabel(appState.analysisStartedAt, appState.analysisProgress, big = true)
            } else {
                Column {
                    Text("Analyze", style = MaterialTheme.typography.titleLarge)
                    requests?.let {
                        Text("$it ${if (it == 1) "request" else "requests"}", style = MaterialTheme.typography.labelMedium, color = p.onInk.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

/**
 * The chats a run reads, with each one's record in Insights and how many of the loaded messages are
 * its. Ticking a chat in or out is the same toggle the chat list has always offered.
 */
@Composable
internal fun SourcesTile(appState: AppState, modifier: Modifier = Modifier) {
    val p = palette
    val counts = remember(appState.telegramSources) { messagesPerSource(appState.telegramSources) }
    val scores = remember(appState.performance) { appState.performance.channels.associateBy { it.channel.trim() } }
    val ranked = remember(appState.performance) {
        appState.performance.channels.filter { it.anyTargetRate != null }
            .sortedByDescending { it.anyTargetRate }
            .mapIndexed { i, c -> c.channel.trim() to i + 1 }
            .toMap()
    }
    Tile(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sources", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (appState.telegramSources.isNotEmpty()) {
                Text("${appState.telegramSources.size} in this run", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
        }
        if (appState.channels.isEmpty()) {
            Text(
                "No chats loaded. Pull down to refresh the chat list.",
                style = MaterialTheme.typography.bodySmall,
                color = p.muted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        appState.channels.forEachIndexed { index, channel ->
            if (index > 0) com.ikverse.egxanalyzer.ui.kit.KitRule(Modifier.padding(horizontal = 12.dp), color = p.ground)
            val name = channel.displayName.trim()
            val score = scores[name] ?: scores[channel.name.trim()]
            val rank = ranked[name] ?: ranked[channel.name.trim()]
            val inRun = counts.forChat(channel.id, channel.displayName)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Kit.PanelShape)
                    .clickable(role = Role.Checkbox, onClickLabel = if (channel.selected) "Skip this chat" else "Read this chat") {
                        appState.toggleChannel(channel)
                    }
                    .padding(12.dp)
                    .graphicsLayer { alpha = if (channel.selected) 1f else 0.6f },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChannelAvatar(channel.displayName, channel.photoPath, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(channel.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            rank?.let { "#$it" },
                            score?.anyTargetRate?.let { "${formatPercent(it * 100, signed = false)} hit" },
                        ).joinToString(" · ").ifBlank { "Not scored yet" },
                        style = MaterialTheme.typography.bodySmall,
                        color = p.muted,
                    )
                }
                if (inRun > 0) {
                    Text(
                        "$inRun",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                        color = p.onTile,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(p.highlight).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                KitCheckbox(checked = channel.selected, onCheckedChange = { appState.toggleChannel(channel) }, label = channel.displayName)
            }
        }
    }
}

/** What a run reads, as chips. */
@Composable
internal fun ReadTile(appState: AppState, blocker: AnalyzeBlocker?, attempted: Boolean, modifier: Modifier = Modifier) {
    Tile(modifier = modifier, tone = TileTone.HIGHLIGHT) {
        Text("Read", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            Modifier.padding(top = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            AnalysisContentType.OFFERED.forEach { type ->
                KitChip(contentTypeLabel(type), selected = type in appState.selectedContentTypes, onClick = { appState.toggleContentType(type) })
            }
        }
        if (blocker == AnalyzeBlocker.NO_CONTENT_TYPE) {
            Text(
                blocker.reason,
                style = MaterialTheme.typography.bodySmall,
                color = if (attempted) palette.loss else LocalContentColor.current,
                modifier = Modifier.padding(top = Space.s),
            )
        }
    }
}

/** Which session the calls are for: the next one, or a date picked. */
@Composable
internal fun CallsForTile(appState: AppState, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val next = appState.analysisMode == AnalysisMode.NEXT_DAY
    Tile(modifier = modifier, tone = TileTone.TILE1, onClick = onPick, onClickLabel = "Change the date") {
        Text("Calls for", style = MaterialTheme.typography.titleSmall)
        Text(if (next) "Next session" else "A chosen date", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Space.s))
        Text(
            appState.recommendationTargetDate.format(AppDates.DayMonthYear),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
        )
        Row(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Text("Change →", style = MaterialTheme.typography.labelLarge)
            if (!next) {
                Text(
                    "Use next session",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(role = Role.Button) { appState.selectAnalysisMode(AnalysisMode.NEXT_DAY) },
                )
            }
        }
    }
}

/** The model a run goes to, and the way to change it in Settings. */
@Composable
internal fun ModelTile(appState: AppState, blocker: AnalyzeBlocker?, attempted: Boolean, modifier: Modifier = Modifier) {
    val config = appState.cloudConfiguration
    Tile(modifier = modifier.fillMaxWidth(), tone = TileTone.TILE2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Model · ${config.provider.displayName}", style = MaterialTheme.typography.titleSmall)
                Text(
                    config.model.ifBlank { "No model chosen" },
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
            KitButton("Change", onClick = { appState.navigate(AppDestination.SETTINGS) }, kind = ButtonKind.ON_TONE, size = ButtonSize.SMALL)
        }
    }
}

/** The newest report: how many calls it found, and a way into it. */
@Composable
internal fun LastRunTile(appState: AppState, modifier: Modifier = Modifier) {
    val last = appState.savedResults.maxByOrNull { it.id } ?: return
    val p = palette
    val calls = last.result.consolidated.size
    Tile(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Last run", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            last.result.recommendationTargetDate?.let {
                Text("for ${it.format(AppDates.DayMonthYear)}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFigures), color = p.muted)
            }
        }
        Row(Modifier.padding(top = Space.m)) {
            Column(Modifier.weight(1f)) {
                Text("$calls", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TabularFigures))
                Text(if (calls == 1) "call" else "calls", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            Column(Modifier.weight(2f)) {
                Text(last.model, style = MaterialTheme.typography.titleSmall.copy(fontFamily = TabularFigures), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(last.provider.displayName, style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
        }
        KitButton(
            "Open report",
            onClick = { appState.openSavedResult(last.id) },
            kind = ButtonKind.SECONDARY,
            size = ButtonSize.SMALL,
            modifier = Modifier.padding(top = Space.m),
        )
    }
}

/** Notifications are off: a run gives no progress while the reader is in another app. */
@Composable
internal fun NotificationsOffTile(appState: AppState) {
    Tile(modifier = Modifier.fillMaxWidth(), tone = TileTone.ALERT) {
        Text("Notifications are off", style = MaterialTheme.typography.titleMedium)
        Text(
            "A run gives no progress while you are in another app. The analysis itself is unaffected.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Space.xs),
        )
        KitButton(
            "Turn on notifications",
            onClick = { appState.openNotificationSettings() },
            size = ButtonSize.SMALL,
            modifier = Modifier.padding(top = Space.m),
        )
    }
}
