package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ChannelSelection
import com.ikverse.egxanalyzer.model.resolveAnalysisWindow

@Composable
internal fun CompanionPane(appState: AppState, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            when (appState.destination) {
                AppDestination.CHANNELS -> "Source workspace"
                AppDestination.ANALYZE -> "Analysis setup"
                AppDestination.RESULTS -> "Result inspector"
                AppDestination.INSIGHTS -> "Intelligence summary"
                AppDestination.SETTINGS -> "Runtime status"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(appState.cloudConfiguration.provider.displayName)
        Text(
            appState.cloudConfiguration.model.ifBlank { "No model selected" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        Text("${appState.channels.count(ChannelSelection::selected)} channels selected")
        Text("${appState.inputs.size} sources ready")
        Text("${appState.savedResults.size} saved analyses")
        runCatching {
            resolveAnalysisWindow(appState.analysisMode, appState.recommendationTargetDate)
        }.getOrNull()?.let { window ->
            HorizontalDivider()
            Text("Target ${window.targetDate}", fontWeight = FontWeight.Bold)
            Text(
                "${window.start} — ${window.endExclusive}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (appState.cloudConfiguration.hasCredential) "Credential saved securely"
            else "Credential required",
            color = if (appState.cloudConfiguration.hasCredential) {
                MaterialTheme.colorScheme.primary
            } else MaterialTheme.colorScheme.error,
        )
        appState.selectedResult?.let {
            HorizontalDivider()
            Text("Latest result", fontWeight = FontWeight.Bold)
            Text("${it.result.recommendations.size} recommendations")
            Text("${it.result.sources.size} traced sources")
            Text("${it.result.diagnostics.validationWarnings.size} validation warnings")
            Text("${it.result.diagnostics.durationMilliseconds} ms")
        }
        if (appState.destination == AppDestination.INSIGHTS) {
            HorizontalDivider()
            Text("Top consensus", fontWeight = FontWeight.Bold)
            appState.consensus().take(5).forEach {
                Text("${it.ticker} · ${it.recommendationCount} recommendations")
            }
        }
        if (appState.destination == AppDestination.SETTINGS) {
            HorizontalDivider()
            Text(appState.catalogMessage)
            Text("${appState.promptHistory.size} prompt snapshots")
            Text("Correction retries: ${appState.appPreferences.correctionRetries}")
        }
    }
}
