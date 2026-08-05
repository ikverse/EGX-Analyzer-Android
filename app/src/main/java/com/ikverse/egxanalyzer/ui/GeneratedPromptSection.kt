package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.PromptVersion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Text(
            "Generated from the prompt this app version ships, plus the wording you switched on in " +
                "Analysis rules. Editing a rule generates a new version; the old ones stay readable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use the default prompt only", fontWeight = FontWeight.Bold)
                Text(
                    "Sends the shipped prompt untouched. Your wording stays saved and the rules " +
                        "that work on this device keep working - only the prompt stops carrying them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = appState.useDefaultPromptOnly,
                onCheckedChange = appState::usePromptDefaultOnly,
            )
        }

        HorizontalDivider()

        Text(
            buildString {
                append("Active: ")
                append(current?.let { "v${it.sequence}" } ?: "v1")
                append(" · ")
                append(active.id)
                active.schemaVersion?.let { append(" · shipped prompt schema $it") }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            if (active.ruleIds.isEmpty()) {
                "No wording is being added to the prompt."
            } else {
                "${active.ruleIds.size} rule${if (active.ruleIds.size == 1) "" else "s"} folded in."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        appState.promptVersions.take(10).forEach { version ->
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "v${version.sequence}" + if (version.id == active.id) " · active" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (version.id == active.id) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        version.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${version.createdAt.asLocalStamp()} · ${version.device}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { viewing = version }) { Text("View") }
            }
        }
    }

    viewing?.let { version ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text("v${version.sequence} · ${version.id}") },
            text = {
                // Read-only on purpose: a version is a record of what was sent, and a record that
                // can be typed over is not a record.
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .scrollableColumn(),
                ) {
                    Text(
                        version.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewing = null }) { Text("Close") }
            },
        )
    }
}

private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")

private fun Long.asLocalStamp(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(STAMP)
