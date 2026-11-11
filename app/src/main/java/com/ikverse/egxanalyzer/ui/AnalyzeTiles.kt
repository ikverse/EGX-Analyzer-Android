package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisMode
import com.ikverse.egxanalyzer.model.AnalysisProgress
import com.ikverse.egxanalyzer.model.ModelSuitability
import com.ikverse.egxanalyzer.model.ModelSuitabilityRules
import com.ikverse.egxanalyzer.model.ReadingEstimate
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.forChat
import com.ikverse.egxanalyzer.model.messagesPerSource
import com.ikverse.egxanalyzer.model.relativeDay
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.KitCheckbox
import com.ikverse.egxanalyzer.ui.kit.KitChip
import com.ikverse.egxanalyzer.ui.kit.KitTag
import com.ikverse.egxanalyzer.ui.kit.KitProgress
import com.ikverse.egxanalyzer.ui.kit.StepTone
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.Timeline
import com.ikverse.egxanalyzer.ui.kit.TimelineStep
import com.ikverse.egxanalyzer.ui.kit.tileSignals
import com.ikverse.egxanalyzer.ui.theme.palette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * The Analyze hero before a run: how many new posts the next run will read, from how many chats and
 * for which day, and the button that starts it.
 *
 * What blocks a run is said here in words, quietly until Analyze has been pressed and in sunflower
 * after - coral on cobalt was 2.6:1 in light and 2.3:1 in dark. The running state is [RunningTile].
 */
@Composable
internal fun AnalyzeHero(
    appState: AppState,
    blocker: AnalyzeBlocker?,
    attempted: Boolean,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
    /** Draws the summary only, for tabletop, where the button sits under the crease on its own. */
    showButton: Boolean = true,
) {
    val p = palette
    val posts = appState.telegramSources.size
    val earlier = appState.sourcesReadEarlier.size
    val chats = remember(appState.telegramSources) { messagesPerSource(appState.telegramSources).size }
    Tile(modifier = modifier.fillMaxWidth(), tone = TileTone.HERO) {
        Text("Ready when you are", style = MaterialTheme.typography.bodyMedium)
        if (posts == 0) {
            Text(
                "No posts loaded yet",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        } else {
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    "${posts - earlier}",
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = TabularFigures),
                )
                Text(
                    "new posts to read",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = Space.s, bottom = 6.dp),
                )
            }
        }
        Text(
            buildList {
                if (chats > 0) add("from $chats ${if (chats == 1) "chat" else "chats"}")
                add("calls for ${appState.recommendationTargetDate.format(DayFormat)}")
            }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = p.onHero.copy(alpha = 0.85f),
        )
        if (blocker != null) {
            Text(
                blocker.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = if (attempted) p.tile1 else p.onHero,
                modifier = Modifier.padding(top = Space.m),
            )
        }
        if (showButton) {
            AnalyzeButton(blocker, onAnalyze, Modifier.padding(top = Space.l))
        }
        if (earlier > 0 && posts > 0) {
            Text(
                "$earlier of them ${if (earlier == 1) "was" else "were"} read earlier and will not be read again.",
                style = MaterialTheme.typography.bodySmall,
                color = p.onHero.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = Space.s),
            )
        }
        appState.analysisMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (appState.analysisStatus == AnalysisStatus.FAILED) p.tile1 else p.onHero.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = Space.m),
            )
        }
    }
}

/** "Mon 9 Nov", the way the page names the day the calls are for. */
private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

/** The run's button, at rest: an ink capsule, dimmed while something blocks a run. */
@Composable
internal fun AnalyzeButton(
    blocker: AnalyzeBlocker?,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val press = remember { MutableInteractionSource() }
    val ready = blocker == null
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(press)
            .height(72.dp)
            .clip(Kit.Capsule)
            .background(if (ready) p.ink else p.ink.copy(alpha = 0.35f))
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onAnalyze)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = p.onInk, modifier = Modifier.size(26.dp))
        CompositionLocalProvider(LocalContentColor provides p.onInk) {
            Text("Analyze", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/**
 * A run going: what it is doing, how much of the reading is done, how long that should take, the
 * three steps of a run, and the way to stop it.
 *
 * The estimate is the reading's only: it is set at each batch from the pace so far, and the writing
 * is one answer of unknown length, so there is nothing honest to say about it. See [ReadingEstimate].
 */
@Composable
internal fun RunningTile(
    appState: AppState,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    showStop: Boolean = true,
) {
    val p = palette
    val progress = appState.analysisProgress
    val reading = progress?.stage == AnalysisProgress.Stage.READING
    val writing = progress?.stage == AnalysisProgress.Stage.WRITING
    // A clock for the phrase only: the end time is set at each batch, and this counts down to it.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(reading) {
        while (reading) {
            now = Instant.now()
            delay(1_000)
        }
    }
    Tile(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Analysis running" },
        tone = TileTone.HERO,
    ) {
        Text(
            when {
                reading -> "Reading the posts"
                writing -> "Writing the report"
                else -> "Starting"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (reading && progress != null) {
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    "${progress.unitsDone}/${progress.units}",
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = TabularFigures),
                )
                Text(
                    if (progress.images > 0) "pictures" else "batches",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = Space.s, bottom = 6.dp),
                )
            }
            ReadingEstimate.phrase(now, appState.analysisReadingEndsAt)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = p.onHero.copy(alpha = 0.85f))
            }
        }
        val fraction = progress?.fraction
        if (fraction != null) {
            KitProgress(fraction, Modifier.padding(top = Space.m), track = p.onHero.copy(alpha = 0.25f))
        } else {
            Box(
                Modifier
                    .padding(top = Space.m)
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(Kit.Capsule)
                    .runFill(null, ground = p.onHero.copy(alpha = 0.25f), fill = p.highlight.copy(alpha = RunFillAlpha)),
            )
        }
        // On the card's own white, so the steps read in the kit's colours rather than on cobalt.
        Box(
            Modifier
                .padding(top = Space.m)
                .fillMaxWidth()
                .clip(Kit.PanelShape)
                .background(p.card)
                .padding(Space.m),
        ) {
            Timeline(
                listOf(
                    TimelineStep("Collected the posts · ${appState.telegramSources.size}"),
                    TimelineStep(
                        "Reading the pictures and text" +
                            (progress?.takeIf { it.units > 0 }?.let { " · ${it.unitsDone} of ${it.units}" } ?: ""),
                        tone = when {
                            reading -> StepTone.ACTIVE
                            writing -> StepTone.DONE
                            else -> StepTone.WAITING
                        },
                    ),
                    TimelineStep(
                        "Writing the report",
                        detail = progress?.takeIf { writing && it.correction > 0 }?.line(),
                        tone = if (writing) StepTone.ACTIVE else StepTone.WAITING,
                    ),
                ),
            )
        }
        if (showStop) {
            KitButton("Stop", onClick = onStop, kind = ButtonKind.ON_TONE, size = ButtonSize.SMALL, modifier = Modifier.padding(top = Space.m))
        }
    }
}

/** Beside a run: what it is skipping, and that the page can be left. */
@Composable
internal fun RunNotesTile(appState: AppState, modifier: Modifier = Modifier) {
    val skipped = appState.analysisProgress?.skipped ?: 0
    val told = appState.notificationsPermitted() && appState.appPreferences.reportReadyEnabled
    Tile(modifier = modifier.fillMaxWidth()) {
        if (skipped > 0) {
            Text(
                "$skipped ${if (skipped == 1) "post was" else "posts were"} already read earlier, so " +
                    "${if (skipped == 1) "it is" else "they are"} skipped.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Space.s),
            )
        }
        Text("You can leave this page", style = MaterialTheme.typography.titleSmall)
        Text(
            "The run carries on" + if (told) ", and a notification tells you when the report is ready." else ".",
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
        )
    }
}

/** The line a locked tile carries while a run is going. */
@Composable
private fun LockedNote() {
    Text("locked while it runs", style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.7f))
}

/**
 * The chats a run reads, with each one's record in Insights and how many of the loaded posts are
 * its. Ticking a chat in or out is the same toggle the chat list has always offered; [locked] while
 * a run is going, because what the run reads was fixed when it started.
 */
@Composable
internal fun SourcesTile(appState: AppState, locked: Boolean = false, modifier: Modifier = Modifier) {
    val p = palette
    val counts = remember(appState.telegramSources) { messagesPerSource(appState.telegramSources) }
    val scores = remember(appState.performance) { appState.performance.channels.associateBy { it.channel.trim() } }
    val ranked = remember(appState.performance) {
        appState.performance.channels.filter { it.anyTargetRate != null }
            .sortedByDescending { it.anyTargetRate }
            .mapIndexed { i, c -> c.channel.trim() to i + 1 }
            .toMap()
    }
    Tile(
        modifier = modifier.fillMaxWidth().graphicsLayer { alpha = if (locked) 0.6f else 1f },
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Chats", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text("${appState.channels.count { it.selected }} chosen", style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
        if (locked) Box(Modifier.padding(horizontal = 12.dp)) { LockedNote() }
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
                    .clickable(
                        enabled = !locked,
                        role = Role.Checkbox,
                        onClickLabel = if (channel.selected) "Skip this chat" else "Read this chat",
                    ) {
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
                    KitTag("$inRun", tone = p.heroInk)
                }
                KitCheckbox(
                    checked = channel.selected,
                    onCheckedChange = { if (!locked) appState.toggleChannel(channel) },
                    label = channel.displayName,
                )
            }
        }
    }
}

/** What a run reads, as chips. */
@Composable
internal fun ReadTile(
    appState: AppState,
    blocker: AnalyzeBlocker?,
    attempted: Boolean,
    locked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Tile(modifier = modifier.graphicsLayer { alpha = if (locked) 0.6f else 1f }, tone = TileTone.HIGHLIGHT) {
        Text("What to read", style = MaterialTheme.typography.titleSmall)
        if (locked) LockedNote()
        FlowRow(
            Modifier.padding(top = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            AnalysisContentType.OFFERED.forEach { type ->
                KitChip(
                    contentTypeLabel(type),
                    selected = type in appState.selectedContentTypes,
                    onClick = { if (!locked) appState.toggleContentType(type) },
                )
            }
        }
        if (blocker == AnalyzeBlocker.NO_CONTENT_TYPE) {
            Text(
                blocker.reason,
                style = MaterialTheme.typography.bodySmall,
                // The lime tile's own red: the page's dark-theme red is 2.8:1 on lime.
                color = if (attempted) tileSignals(TileTone.HIGHLIGHT).loss else LocalContentColor.current,
                modifier = Modifier.padding(top = Space.s),
            )
        }
    }
}

/** Which trading day the calls are for: the next one, or a day picked. */
@Composable
internal fun CallsForTile(appState: AppState, onPick: () -> Unit, locked: Boolean = false, modifier: Modifier = Modifier) {
    val next = appState.analysisMode == AnalysisMode.NEXT_DAY
    Tile(
        modifier = modifier.graphicsLayer { alpha = if (locked) 0.6f else 1f },
        tone = TileTone.TILE1,
        onClick = if (locked) null else onPick,
        onClickLabel = if (locked) null else "Pick a day",
    ) {
        Text("Calls for which day", style = MaterialTheme.typography.titleSmall)
        if (locked) LockedNote()
        Text(if (next) "Next trading day" else "Picked day", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Space.s))
        Text(
            appState.recommendationTargetDate.format(AppDates.DayMonthYear),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
        )
        if (!locked) {
            Row(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Text("Pick a day", style = MaterialTheme.typography.labelLarge)
                if (!next) {
                    Text(
                        "Use next trading day",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable(role = Role.Button) { appState.selectAnalysisMode(AnalysisMode.NEXT_DAY) },
                    )
                }
            }
        }
    }
}

/** The model a run goes to, whether it can read pictures, and the way to change it in Settings. */
@Composable
internal fun ModelTile(
    appState: AppState,
    blocker: AnalyzeBlocker?,
    attempted: Boolean,
    locked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val config = appState.cloudConfiguration
    val info = appState.availableModels.firstOrNull { it.id == config.model }
    val suitability = info?.let { ModelSuitabilityRules.capabilitiesOf(it).suitability }
    Tile(modifier = modifier.fillMaxWidth().graphicsLayer { alpha = if (locked) 0.6f else 1f }, tone = TileTone.TILE2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Read by", style = MaterialTheme.typography.titleSmall)
                if (locked) LockedNote()
                Text(
                    config.model.ifBlank { "No model chosen" },
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.xs),
                )
                // Pictures are the bar: a run sends screenshots. Where the catalogue says nothing,
                // or has not loaded, the provider's name is all there is to say.
                when (suitability) {
                    ModelSuitability.SUITABLE -> Text("can read pictures", style = MaterialTheme.typography.bodySmall)
                    ModelSuitability.UNSUITABLE -> Text(
                        "cannot read pictures",
                        style = MaterialTheme.typography.bodySmall,
                        color = tileSignals(TileTone.TILE2).loss,
                    )
                    else -> Text(config.provider.displayName, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!locked) {
                KitButton("Change", onClick = { appState.navigate(AppDestination.SETTINGS) }, kind = ButtonKind.ON_TONE, size = ButtonSize.SMALL)
            }
        }
    }
}

/** The newest report: when it was made, how many calls it found, and a way into it. */
@Composable
internal fun LastRunTile(appState: AppState, modifier: Modifier = Modifier) {
    val last = appState.savedResults.maxByOrNull { it.id } ?: return
    val p = palette
    val calls = last.result.consolidated.sumOf { it.dataPoints.size }
    val made = last.result.completedAt.atZone(ZoneId.of(ScheduleClock.ZONE.id))
    val day = relativeDay(made.toLocalDate(), LocalDate.now(ScheduleClock.ZONE)) ?: made.format(DayFormat)
    Tile(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Last run", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            last.result.recommendationTargetDate?.let {
                Text("for ${it.format(AppDates.DayMonthYear)}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFigures), color = p.muted)
            }
        }
        Text(
            "$day at ${made.format(TimeFormat)} · $calls ${if (calls == 1) "call" else "calls"} found",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Space.s),
        )
        Text(last.model, style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFigures), color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        KitButton(
            "Open it",
            onClick = { appState.openSavedResult(last.id) },
            kind = ButtonKind.SECONDARY,
            size = ButtonSize.SMALL,
            modifier = Modifier.padding(top = Space.m),
        )
    }
}

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
