package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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

@Composable
internal fun InsightsScreen(appState: AppState) {
    var query by remember { mutableStateOf("") }
    val hits = remember(query, appState.savedResults) { appState.searchAnalyses(query) }
    val consensus = remember(appState.savedResults) { appState.consensus() }
    Screen(
        title = "Insights",
        subtitle = "Search saved evidence, compare consensus, and inspect validation diagnostics.",
    ) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Search analyses", fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ticker, company, note, or source") },
                    singleLine = true,
                )
                if (query.length >= 2 && hits.isEmpty()) Text("No matching saved evidence.")
                hits.take(20).forEach { hit ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${hit.ticker} · ${hit.companyName}", fontWeight = FontWeight.Bold)
                            Text("Target: ${hit.targetDate ?: "Not recorded"}")
                            Text("Sources: ${hit.sourceNames.joinToString().ifBlank { "No source" }}")
                        }
                    }
                }
            }
        }

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Insights, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Recommendation consensus", fontWeight = FontWeight.Bold)
                }
                if (consensus.isEmpty()) Text("No recommendations available yet.")
                consensus.forEach { item ->
                    Column {
                        Text("${item.ticker} · ${item.companyName}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${item.recommendationCount} recommendations · ${item.sourceCount} sources · " +
                                "BUY ${item.buyCount} / SELL ${item.sellCount} / HOLD ${item.holdCount}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        item.averageConfidence?.let {
                            Text("Average confidence: ${"%.0f".format(it * 100)}%")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Recent diagnostics", fontWeight = FontWeight.Bold)
                }
                if (appState.savedResults.isEmpty()) Text("No analysis diagnostics yet.")
                appState.savedResults.take(10).forEach { saved ->
                    val diagnostics = saved.result.diagnostics
                    Text(
                        "${saved.result.recommendationTargetDate ?: "Unknown target"} · " +
                            "${diagnostics.acceptedInputCount}/${diagnostics.inputCount} inputs · " +
                            "${diagnostics.validationWarnings.size} warnings · " +
                            "${diagnostics.durationMilliseconds} ms",
                    )
                    diagnostics.validationWarnings.take(3).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
