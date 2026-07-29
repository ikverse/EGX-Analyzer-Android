package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.resolveAnalysisWindow

/**
 * The side panel shown when there is room for it.
 *
 * It summarises the state the current screen depends on, so it is grouped into the same cards the
 * screens use rather than being a run of text lines.
 */
@Composable
internal fun CompanionPane(appState: AppState, modifier: Modifier = Modifier) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            when (appState.destination) {
                AppDestination.CHANNELS -> "Source workspace"
                AppDestination.ANALYZE -> "Analysis setup"
                AppDestination.RESULTS -> "Result inspector"
                AppDestination.INSIGHTS -> "Intelligence summary"
                AppDestination.SETTINGS -> "Runtime status"
            },
            style = MaterialTheme.typography.headlineSmall,
        )

        SectionCard(title = "Cloud model", icon = Icons.Outlined.SmartToy) {
            Text(
                appState.cloudConfiguration.provider.displayName,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                appState.cloudConfiguration.model.ifBlank { "No model selected" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusPill(
                if (appState.cloudConfiguration.hasCredential) "Credential saved" else "Credential required",
                if (appState.cloudConfiguration.hasCredential) StatusTone.GOOD else StatusTone.BAD,
            )
        }

        SectionCard(title = "Workspace", icon = Icons.Outlined.AutoGraph) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatTile(
                    appState.channels.count(ChannelSelection::selected).toString(),
                    "channels",
                    tone = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                StatTile(appState.inputs.size.toString(), "sources", modifier = Modifier.weight(1f))
                StatTile(appState.savedResults.size.toString(), "saved", modifier = Modifier.weight(1f))
            }
            runCatching {
                resolveAnalysisWindow(appState.analysisMode, appState.recommendationTargetDate)
            }.getOrNull()?.let { window ->
                Text("Target ${window.targetDate}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${window.start} — ${window.endExclusive}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        appState.selectedResult?.let { saved ->
            SectionCard(title = "Selected result", icon = Icons.Outlined.Insights) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StatTile(
                        saved.result.consolidated.size.takeIf { it > 0 }?.toString()
                            ?: saved.result.recommendations.size.toString(),
                        "stocks",
                        tone = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        saved.result.sources.size.toString(),
                        "sources",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        "${saved.result.diagnostics.durationMilliseconds / 1000}s",
                        "took",
                        modifier = Modifier.weight(1f),
                    )
                }
                val warnings = saved.result.diagnostics.validationWarnings.size
                StatusPill(
                    if (warnings == 0) "No validation warnings" else "$warnings validation warnings",
                    if (warnings == 0) StatusTone.GOOD else StatusTone.BAD,
                )
            }
        }

        if (appState.destination == AppDestination.INSIGHTS) {
            val consensus = appState.consensus().take(5)
            if (consensus.isNotEmpty()) {
                SectionCard(title = "Top consensus", icon = Icons.Outlined.Insights) {
                    consensus.forEach {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                it.ticker,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${it.recommendationCount}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
        }

        if (appState.destination == AppDestination.SETTINGS) {
            SectionCard(title = "Runtime", icon = Icons.Outlined.Tune) {
                Text(appState.catalogMessage, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${appState.promptHistory.size} prompt snapshots · " +
                        "${appState.appPreferences.correctionRetries} correction retries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
