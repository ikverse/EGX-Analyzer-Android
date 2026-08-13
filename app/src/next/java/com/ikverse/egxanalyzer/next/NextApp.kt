package com.ikverse.egxanalyzer.next

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.AppDestination
import com.ikverse.egxanalyzer.ui.AppState

/**
 * EGX Analyzer NEXT — the redesign, before it has been designed.
 *
 * **This is scaffolding, and it is meant to look like scaffolding.** It exists so the redesign has
 * somewhere to arrive one screen at a time: a second app on the phone, with its own data and its own
 * sync channel, that installs and runs today. Nothing here is a proposal. Every deliberate choice
 * about type, colour, shape, density and motion is the design work's to make, and making a
 * provisional one here would be the quickest way to have it copied.
 *
 * It draws Material's own defaults on purpose, unstyled and unloved, because that is the one look
 * nobody will mistake for a decision.
 *
 * What it does do is prove the wiring: five destinations, real counts read from the shared
 * [AppState], and a theme of its own that the design will replace. As each screen is designed, its
 * placeholder below gives way to a file of its own, and this shell gives way with them.
 */
@Composable
internal fun NextApp(activity: Activity, appState: AppState) {
    NextTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("EGX Next", style = MaterialTheme.typography.titleLarge)
                NextDestinationBar(appState)
                Box(Modifier.fillMaxSize()) {
                    NextScreen(appState.destination, appState)
                }
            }
        }
    }
}

/**
 * The five destinations as plain text buttons.
 *
 * Not a bar, not a rail, and deliberately neither: choosing between them - and whether the answer is
 * either - is one of the first questions the redesign has to settle, and it is the one thing on
 * screen at every width. See the navigation shell section of the brief.
 */
@Composable
private fun NextDestinationBar(appState: AppState) {
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = appState.destination == destination
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    )
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { appState.navigate(destination) },
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    destination.shortLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * What a destination draws until it has been designed.
 *
 * Each names itself, states the question it exists to answer - the same question the brief gives it
 * - and prints what the shared data layer actually holds for it right now. The counts are here to
 * prove the wiring: a placeholder reading "3 saved runs" on a phone with three saved runs says the
 * redesign is standing on the real app, not on fixtures.
 */
@Composable
private fun NextScreen(destination: AppDestination, appState: AppState) {
    val (question, holding) = when (destination) {
        AppDestination.ANALYZE ->
            "What do I want read, and what will it cost me?" to
                "${appState.channels.size} chats known"
        AppDestination.RESULTS ->
            "What did the model actually extract, and can I trust it?" to
                "${appState.savedResults.size} saved runs"
        AppDestination.INSIGHTS ->
            "Which channels are worth following?" to
                "${appState.performance.tracked} calls scored · " +
                "${appState.performance.channels.size} sources"
        AppDestination.PORTFOLIO ->
            "What am I holding, and what needs attention today?" to
                "${appState.portfolio.stats.openCount} open · " +
                "${appState.portfolio.stats.settledCount} settled"
        AppDestination.SETTINGS ->
            "How is this thing configured?" to
                "${appState.cloudConfiguration.provider.displayName} · ${appState.appPreferences.themeMode}"
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(destination.label, style = MaterialTheme.typography.headlineMedium)
        Text(
            question,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(holding, style = MaterialTheme.typography.bodyLarge)
        Text(
            "Not designed yet.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
