package com.ikverse.egxanalyzer.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.RecommendationResult
import com.ikverse.egxanalyzer.model.SavedAnalysis

@Composable
internal fun ResultsScreen(activity: Activity, appState: AppState) {
    var reportResultId by remember { mutableStateOf<Long?>(null) }
    Screen(
        title = "Results",
        subtitle = "Saved recommendations and their exact source traces remain on this device.",
    ) {
        if (appState.savedResults.isEmpty()) {
            Text("No saved results yet.")
        } else {
            appState.savedResults.forEach { saved ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${saved.result.recommendations.size} recommendations",
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${saved.provider.displayName} · ${saved.model}")
                        Text(
                            "Recommendation target: " +
                                (saved.result.recommendationTargetDate?.toString() ?: "Not recorded"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(saved.result.completedAt.toString())
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = { appState.selectResult(saved) }) { Text("View details") }
                            OutlinedButton(onClick = {
                                reportResultId = if (reportResultId == saved.id) null else saved.id
                            }) {
                                Icon(Icons.Outlined.Description, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Report")
                            }
                            OutlinedButton(onClick = {
                                shareReport(activity, appState.reportFor(saved))
                            }) {
                                Text("Share")
                            }
                            TextButton(onClick = { appState.deleteResult(saved) }) { Text("Delete") }
                        }
                        if (appState.selectedResult?.id == saved.id) ResultDetail(saved)
                        if (reportResultId == saved.id) {
                            val report = appState.reportFor(saved)
                            HorizontalDivider()
                            Text(report.title, fontWeight = FontWeight.Bold)
                            Text(report.markdown, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun shareReport(
    activity: Activity,
    report: com.ikverse.egxanalyzer.model.AnalysisReport,
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_SUBJECT, report.title)
        putExtra(Intent.EXTRA_TEXT, report.markdown)
    }
    activity.startActivity(Intent.createChooser(intent, "Share EGX analysis report"))
}


@Composable
private fun ResultDetail(saved: SavedAnalysis) {
    HorizontalDivider()
    // Analyses saved before the consolidated contract have no nested occurrences, so fall back
    // to the flattened rows rather than showing an empty detail pane.
    if (saved.result.consolidated.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            saved.result.consolidated.forEach { stock -> RecommendationCard(stock) }
        }
    } else {
        saved.result.recommendations.forEach { recommendation -> LegacyDetail(recommendation) }
    }
    Text("Source trace", fontWeight = FontWeight.Bold)
    saved.result.sources.forEach { source ->
        Text("${source.sourceId} · ${source.channelName} · ${source.contentType} · ${source.preview}")
    }
    HorizontalDivider()
    Text("Diagnostics", fontWeight = FontWeight.Bold)
    val diagnostics = saved.result.diagnostics
    Text("Source window: ${diagnostics.sourceWindowStart ?: "Not recorded"} — ${diagnostics.sourceWindowEnd ?: "Not recorded"}")
    Text("${diagnostics.acceptedInputCount}/${diagnostics.inputCount} inputs accepted")
    Text("${diagnostics.excludedSources.size} sources filtered before analysis")
    Text("${diagnostics.validationWarnings.size} validation warnings")
    Text("Correction attempted: ${if (diagnostics.correctionAttempted) "Yes" else "No"}")
    Text("Cloud analysis duration: ${diagnostics.durationMilliseconds} ms")
    diagnostics.excludedSources.forEach {
        Text("Excluded ${it.sourceId}: ${it.reason}", style = MaterialTheme.typography.bodySmall)
    }
    diagnostics.validationWarnings.forEach {
        Text("Warning: $it", color = MaterialTheme.colorScheme.error)
    }
}

/** Detail for analyses saved before the consolidated contract existed. */
@Composable
private fun LegacyDetail(recommendation: RecommendationResult) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${recommendation.ticker} · ${recommendation.companyName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("Source: ${recommendation.sourceName}")
            Text(
                "Signal: ${recommendation.signal}" +
                    (recommendation.confidence?.let { " · ${"%.0f".format(it * 100)}%" } ?: ""),
                color = MaterialTheme.colorScheme.primary,
            )
            recommendation.riskLevel?.let { Text("Risk: $it") }
            recommendation.timeHorizon?.let { Text("Horizon: $it") }
            if (recommendation.indicators.isNotEmpty()) {
                Text("Indicators: ${recommendation.indicators.joinToString()}")
            }
            recommendation.entryLow?.let { Text("Entry: $it – ${recommendation.entryHigh ?: it}") }
            recommendation.takeProfit1?.let { Text("Take profit: $it") }
            recommendation.stopLoss?.let { Text("Stop loss: $it") }
            recommendation.notesArabic?.let { Text(it) }
            Text(
                "Evidence: ${recommendation.sourceIds.joinToString().ifBlank { "No source cited" }}",
                color = MaterialTheme.colorScheme.primary,
            )
    }
    HorizontalDivider()
}
