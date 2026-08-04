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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
 * How wide the window is, for screens that size themselves by it.
 *
 * The shell already works this out to choose a rail or a bar; publishing it saves every screen
 * measuring the window again and disagreeing about where the line falls.
 */
internal val LocalWindowWidth = staticCompositionLocalOf { WindowWidth.COMPACT }

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

    // A bar on a phone and a rail on anything wider, at the same breakpoint the suite used when it
    // chose for itself. The choice is made here so the rail can be spaced and aligned deliberately;
    // the components underneath are still Material's own.
    val rail = windowWidth != WindowWidth.COMPACT
    CompositionLocalProvider(LocalWindowWidth provides windowWidth) {
    NavigationSuiteScaffoldLayout(
        navigationSuite = { AppNavigation(appState, rail) },
        navigationSuiteType = if (rail) {
            NavigationSuiteType.NavigationRail
        } else {
            NavigationSuiteType.NavigationBar
        },
    ) {
        // The suite scaffold used to paint this; the layout does not. Without it the window
        // background shows through behind the status and navigation bars as a pale band.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppContent(activity, appState)
        }
    }
    }
}

/** Icons carry the rail on a glance, so they are a size up from the Material default of 24dp. */
private val NavigationIconSize = 28.dp

/**
 * How far the rail's items sit below the top of the window.
 *
 * A rail item is an icon stacked over a label, so left at the top its icon lands beside the blank
 * space above the page title rather than beside the title itself. This drops the column until the
 * first icon meets the heading next to it.
 */
private val RailTopInset = 14.dp

/** Room between rail items, where Material leaves 4dp and the four destinations read as one block. */
private val RailItemGap = 16.dp

@Composable
private fun AppNavigation(appState: AppState, rail: Boolean) {
    if (rail) {
        NavigationRail {
            Spacer(Modifier.height(RailTopInset))
            AppDestination.entries.forEachIndexed { index, destination ->
                if (index > 0) Spacer(Modifier.height(RailItemGap))
                val selected = appState.destination == destination
                NavigationRailItem(
                    selected = selected,
                    onClick = { appState.navigate(destination) },
                    icon = { NavigationIcon(destination, selected) },
                    label = { Text(destination.label) },
                )
            }
        }
    } else {
        NavigationBar {
            AppDestination.entries.forEach { destination ->
                val selected = appState.destination == destination
                NavigationBarItem(
                    selected = selected,
                    onClick = { appState.navigate(destination) },
                    icon = { NavigationIcon(destination, selected) },
                    label = { Text(destination.label) },
                )
            }
        }
    }
}

@Composable
private fun NavigationIcon(destination: AppDestination, selected: Boolean) {
    Icon(
        if (selected) destination.selectedIcon else destination.icon,
        contentDescription = destination.label,
        modifier = Modifier.size(NavigationIconSize),
    )
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
