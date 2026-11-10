package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.PromptVersion
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.KitRule
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.Panel
import com.ikverse.egxanalyzer.ui.theme.palette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Where the prompt comes from, for the heading this section is drawn under.
 *
 * Exported for the same reason `WordingFlowNote` is: the group's own explanation belongs on the
 * group's heading, not as the first paragraph inside it.
 */
internal val GeneratedPromptNote = infoNote(
    "Prompt",
    "Generated from the prompt this app version ships, plus the wording you switched on under " +
        "Wording.",
    "Editing a rule generates a new version; the old ones stay readable.",
)

/**
 * The prompt the app will actually send, and every one it has sent before.
 *
 * Each version is generated from the prompt the app ships plus the rules switched on at the time -
 * never from the version before it. That is what lets a rule be removed cleanly, and what stops a
 * prompt collecting instructions nobody remembers adding.
 */
@Composable
internal fun GeneratedPromptSection(appState: AppState) {
    var viewing by remember { mutableStateOf<PromptVersion?>(null) }
    val active = appState.activePrompt
    val current = appState.promptVersions.firstOrNull { it.id == active.id }
    val p = palette

    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        SettingToggle(
            label = "Use the default prompt only",
            checked = appState.useDefaultPromptOnly,
            onCheckedChange = appState::usePromptDefaultOnly,
            about = infoNote(
                "Use the default prompt only",
                "Sends the shipped prompt untouched.",
                "Your wording stays saved and the rules that work on this device keep working - " +
                    "only the prompt stops carrying them.",
            ),
        )

        // The version in force, on the ground, as the one figure this group is about.
        Panel(Modifier.fillMaxWidth(), color = p.ground, contentPadding = PaddingValues(12.dp)) {
            Text(
                buildString {
                    append("Active: ")
                    append(current?.let { "v${it.sequence}" } ?: "v1")
                    append(" · ")
                    append(active.id)
                    active.schemaVersion?.let { append(" · schema $it") }
                },
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                fontWeight = FontWeight.Bold,
                color = p.ink,
            )
            Text(
                if (active.ruleIds.isEmpty()) {
                    "No wording is being added to the prompt."
                } else {
                    "${active.ruleIds.size} rule${if (active.ruleIds.size == 1) "" else "s"} folded in."
                },
                style = MaterialTheme.typography.bodySmall,
                color = p.muted,
            )
        }

        Column {
            appState.promptVersions.take(10).forEachIndexed { index, version ->
                if (index > 0) KitRule()
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "v${version.sequence}" + if (version.id == active.id) " · active" else "",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
                            fontWeight = FontWeight.Bold,
                            color = if (version.id == active.id) p.ink else p.muted,
                        )
                        Text(version.reason, style = MaterialTheme.typography.labelSmall, color = p.muted)
                        Text(
                            "${version.createdAt.asLocalStamp()} · ${version.device}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TabularFigures),
                            color = p.muted,
                        )
                    }
                    KitButton("View", onClick = { viewing = version }, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
                }
            }
        }
    }

    viewing?.let { version ->
        // A sheet rather than a dialog: the prompt runs to pages, and a dialog is for a sentence.
        // Read-only on purpose: a version is a record of what was sent, and a record that can be
        // typed over is not a record.
        KitSheet(
            onDismiss = { viewing = null },
            posture = LocalFoldPosture.current,
            title = "v${version.sequence} · ${version.id}",
        ) {
            Text(
                version.text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFigures),
                color = p.ink,
            )
            KitButton(
                "Close",
                onClick = { viewing = null },
                kind = ButtonKind.SECONDARY,
                size = ButtonSize.SMALL,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")

private fun Long.asLocalStamp(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(STAMP)
