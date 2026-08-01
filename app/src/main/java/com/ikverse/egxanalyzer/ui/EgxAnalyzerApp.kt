package com.ikverse.egxanalyzer.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

internal enum class WindowWidth { COMPACT, MEDIUM, EXPANDED }

/**
 * Width at which the layout switches to its wide form.
 *
 * Below Material's 840dp expanded breakpoint on purpose: a Fold 7 unfolded in portrait is roughly
 * 750dp, so an 840dp threshold would leave the largest screen the app runs on using the compact
 * layout.
 */
internal const val WIDE_LAYOUT_DP = 700f

@Composable
fun EgxAnalyzerApp(activity: Activity, appState: AppState) {
    val density = LocalDensity.current
    val width = with(density) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val windowWidth = when {
        width >= WIDE_LAYOUT_DP -> WindowWidth.EXPANDED
        width >= 600 -> WindowWidth.MEDIUM
        else -> WindowWidth.COMPACT
    }
    val layoutInfo by produceState<WindowLayoutInfo?>(null, activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { value = it }
    }
    val separatingFold = layoutInfo?.displayFeatures.orEmpty().filterIsInstance<FoldingFeature>()
        .firstOrNull(FoldingFeature::isSeparating)

    // The suite picks a bar on a phone and a rail on anything wider from the window size class,
    // which already accounts for a device being unfolded.
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                val selected = appState.destination == destination
                item(
                    selected = selected,
                    onClick = { appState.navigate(destination) },
                    icon = {
                        Icon(
                            if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = destination.label,
                        )
                    },
                    label = { Text(destination.label) },
                )
            }
        },
    ) {
        AppContent(activity, appState)
    }
}

@Composable
private fun AppContent(activity: Activity, appState: AppState) {
    val snackbarHost = remember { SnackbarHostState() }

    // Actions report their outcome once, in plain language, then the message is cleared so it
    // cannot reappear on the next recomposition.
    LaunchedEffect(appState.statusMessage) {
        appState.statusMessage?.let { message ->
            snackbarHost.showSnackbar(message.text, withDismissAction = true)
            appState.consumeStatusMessage()
        }
    }

    Scaffold(
        // The navigation suite draws edge to edge, so the content keeps itself clear of the status
        // bar and any cutout.
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            appState.busyLabel?.let { label ->
                BusyBar(label)
            }
            // Screens cross-fade and rise slightly, so changing destination reads as moving
            // somewhere rather than the content being swapped underneath.
            AnimatedContent(
                targetState = appState.destination,
                transitionSpec = {
                    (fadeIn(initialAlpha = 0.4f) + slideInVertically { it / 24 })
                        .togetherWith(fadeOut())
                        .using(SizeTransform(clip = false))
                },
                label = "destination",
            ) { destination ->
                when (destination) {
                    AppDestination.ANALYZE -> AnalyzeScreen(activity, appState)
                    AppDestination.RESULTS -> ResultsScreen(activity, appState)
                    AppDestination.INSIGHTS -> InsightsScreen(appState)
                    AppDestination.SETTINGS -> SettingsScreen(appState)
                }
            }
        }
    }
}

/** Names the running action rather than showing a bare spinner, so a slow step is explainable. */
@Composable
private fun BusyBar(label: String) {
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
}

private val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.ANALYZE -> Icons.Outlined.AutoGraph
        AppDestination.RESULTS -> Icons.Outlined.Assessment
        AppDestination.INSIGHTS -> Icons.Outlined.Insights
        AppDestination.SETTINGS -> Icons.Outlined.Settings
    }

private val AppDestination.selectedIcon: ImageVector
    get() = when (this) {
        AppDestination.ANALYZE -> Icons.Filled.AutoGraph
        AppDestination.RESULTS -> Icons.Filled.Assessment
        AppDestination.INSIGHTS -> Icons.Filled.Insights
        AppDestination.SETTINGS -> Icons.Filled.Settings
    }
