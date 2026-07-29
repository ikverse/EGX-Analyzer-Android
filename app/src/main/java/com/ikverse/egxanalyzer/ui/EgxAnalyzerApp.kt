package com.ikverse.egxanalyzer.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.ikverse.egxanalyzer.R

private enum class WindowWidth { COMPACT, MEDIUM, EXPANDED }

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
    AdaptiveAppScaffold(windowWidth, appState) { padding ->
        AppContent(
            modifier = Modifier.padding(padding),
            activity = activity,
            appState = appState,
            showCompanionPane = windowWidth == WindowWidth.EXPANDED ||
                separatingFold?.orientation == FoldingFeature.Orientation.VERTICAL,
            hingeWidth = separatingFold?.bounds?.width()?.let { with(density) { it.toDp() } } ?: 0.dp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdaptiveAppScaffold(
    windowWidth: WindowWidth,
    appState: AppState,
    content: @Composable (PaddingValues) -> Unit,
) {
    val compact = windowWidth == WindowWidth.COMPACT
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.desktop_egx_icon),
                        contentDescription = "EGX Analyzer",
                        modifier = Modifier.size(38.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("EGX Analyzer", fontWeight = FontWeight.Bold)
                        Text(
                            "Standalone cloud analysis",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            })
        },
        bottomBar = {
            if (compact) NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = appState.destination == destination,
                        onClick = { appState.navigate(destination) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        alwaysShowLabel = false,
                    )
                }
            }
        },
    ) { padding ->
        if (compact) {
            content(padding)
        } else {
            Row(Modifier.padding(padding).fillMaxSize()) {
                NavigationRail {
                    Spacer(Modifier.height(12.dp))
                    AppDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = appState.destination == destination,
                            onClick = { appState.navigate(destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                Box(Modifier.fillMaxSize()) { content(PaddingValues()) }
            }
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

@Composable
private fun AppContent(
    modifier: Modifier,
    activity: Activity,
    appState: AppState,
    showCompanionPane: Boolean,
    hingeWidth: androidx.compose.ui.unit.Dp,
) {
    Row(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (showCompanionPane) 0.66f else 1f),
        ) {
            when (appState.destination) {
                AppDestination.CHANNELS -> ChannelsScreen(appState)
                AppDestination.ANALYZE -> AnalyzeScreen(activity, appState)
                AppDestination.RESULTS -> ResultsScreen(activity, appState)
                AppDestination.INSIGHTS -> InsightsScreen(appState)
                AppDestination.SETTINGS -> SettingsScreen(appState)
            }
        }
        if (showCompanionPane) {
            Spacer(Modifier.width(hingeWidth))
            CompanionPane(appState, Modifier.fillMaxSize())
        }
    }
}
