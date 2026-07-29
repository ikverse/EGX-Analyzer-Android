package com.ikverse.egxanalyzer.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

internal enum class WindowWidth { COMPACT, MEDIUM, EXPANDED }

@Composable
fun EgxAnalyzerApp(activity: Activity, appState: AppState) {
    val density = LocalDensity.current
    val width = with(density) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val windowWidth = when {
        width >= 840 -> WindowWidth.EXPANDED
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
        AppContent(
            activity = activity,
            appState = appState,
            showCompanionPane = windowWidth == WindowWidth.EXPANDED ||
                separatingFold?.orientation == FoldingFeature.Orientation.VERTICAL,
            hingeWidth = separatingFold?.bounds?.width()?.let { with(density) { it.toDp() } } ?: 0.dp,
        )
    }
}

@Composable
private fun AppContent(
    activity: Activity,
    appState: AppState,
    showCompanionPane: Boolean,
    hingeWidth: Dp,
) {
    // The navigation suite draws edge to edge, so the panes take responsibility for keeping their
    // own content clear of the status bar and any cutout.
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (showCompanionPane) 0.62f else 1f),
        ) {
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
                    AppDestination.CHANNELS -> ChannelsScreen(appState)
                    AppDestination.ANALYZE -> AnalyzeScreen(activity, appState)
                    AppDestination.RESULTS -> ResultsScreen(activity, appState)
                    AppDestination.INSIGHTS -> InsightsScreen(appState)
                    AppDestination.SETTINGS -> SettingsScreen(appState)
                }
            }
        }
        if (showCompanionPane) {
            Spacer(Modifier.width(hingeWidth))
            CompanionPane(appState, Modifier.fillMaxSize().padding(end = 4.dp))
        }
    }
}

private val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.CHANNELS -> Icons.Outlined.Forum
        AppDestination.ANALYZE -> Icons.Outlined.AutoGraph
        AppDestination.RESULTS -> Icons.Outlined.Assessment
        AppDestination.INSIGHTS -> Icons.Outlined.Insights
        AppDestination.SETTINGS -> Icons.Outlined.Settings
    }

private val AppDestination.selectedIcon: ImageVector
    get() = when (this) {
        AppDestination.CHANNELS -> Icons.Filled.Forum
        AppDestination.ANALYZE -> Icons.Filled.AutoGraph
        AppDestination.RESULTS -> Icons.Filled.Assessment
        AppDestination.INSIGHTS -> Icons.Filled.Insights
        AppDestination.SETTINGS -> Icons.Filled.Settings
    }
