package com.ikverse.egxanalyzer.ui

import android.app.Activity
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.R
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
    // Held here rather than inside the pages: the bar is drawn beside them, and it has to survive a
    // change of destination.
    val navBarVisible = remember { mutableStateOf(true) }
    // Follows navBarVisible a beat behind: the bar drives this one, so it still reads true through
    // the slide and turns over only once the bar has actually left. What the page below asks it is
    // whether anything is covering the bottom of the window, and during the slide something is.
    val barState = remember { MutableTransitionState(true) }
    CompositionLocalProvider(
        LocalWindowWidth provides windowWidth,
        LocalNavBarVisible provides navBarVisible,
    ) {
    NavigationSuiteScaffoldLayout(
        navigationSuite = { AppNavigation(appState, rail, barState) },
        navigationSuiteType = if (rail) {
            NavigationSuiteType.NavigationRail
        } else {
            NavigationSuiteType.NavigationBar
        },
    ) {
        // The suite scaffold used to paint this; the layout does not. Without it the window
        // background shows through behind the status and navigation bars as a pale band. It is the
        // chrome colour rather than the page's, because this is what shows behind the system bars,
        // and through the rounded corners where the page stops short of them.
        Surface(
            // The layout does not do this for us the way the full NavigationSuiteScaffold does, and
            // without it the page keeps clear of the gesture strip that the navigation beside it is
            // already holding: a second empty band, the width of the strip, in the chrome colour,
            // between the bottom of the page and the first row of icons.
            Modifier.fillMaxSize().consumeWindowInsets(navigationInsets(rail, barState)),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            AppContent(activity, appState)
        }
    }
    }
}

/**
 * The part of the window the navigation is already holding, for the page to stop holding as well.
 *
 * Only the edge the navigation is on: the bar covers the bottom, the rail covers the start, and
 * everything else - the status bar, a cutout, the keyboard - is still the page's own to clear.
 */
@Composable
private fun navigationInsets(
    rail: Boolean,
    barState: MutableTransitionState<Boolean>,
): WindowInsets = when {
    rail -> NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)
    // Covered while any part of the bar is on screen, which is the whole of the slide either way.
    // Reading the settled state alone would hand the strip back the moment the bar started to
    // leave, and the page would jump the width of it before the bar had gone.
    barState.currentState || barState.targetState ->
        NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom)
    else -> WindowInsets(0, 0, 0, 0)
}

/** Icons carry the rail on a glance, so they are a size up from the Material default of 24dp. */
private val NavigationIconSize = 28.dp

/**
 * The bar's own height, above whatever the gesture bar claims.
 *
 * Material leaves 80dp. On a phone the bar is across the bottom of every page, and the height it
 * does not need is height the page does.
 */
private val NavigationBarHeight = 64.dp

/** Room under the items, so they clear the gesture bar rather than sitting straight on it. */
private val BarBottomPadding = 12.dp

/** Material's own icon size. The bar is short enough that the rail's 28dp would crowd the label. */
private val BarIconSize = 24.dp

/**
 * How far the rail's items sit below the top of the window.
 *
 * A rail item is an icon stacked over a label, so left at the top its icon lands beside the blank
 * space above the page title rather than beside the title itself. This drops the column until the
 * first icon meets the heading next to it: past the header band, the page's own top padding, and
 * half a heading. The gap it leaves is beside the band, in the same colour, so the rail reads as the
 * band turning the corner rather than as a rail that starts late.
 */
private val RailTopInset = 70.dp

/** Sized to the name beside it rather than to the navigation grid, which it does not belong to. */
private val AppMarkSize = 24.dp

/**
 * Says which app this is, above whatever page is showing.
 *
 * Fixed, where the page titles under it scroll away with their content: a name that goes with them
 * leaves the top of a long page saying nothing at all. The mark is the launcher artwork reduced to
 * one colour: the full tile in the rail read as a sticker among the flat navigation glyphs, and it
 * only ever appeared on the wide layout, where this says the same thing on both.
 */
@Composable
private fun AppHeader() {
    // A step up from the page it sits on, which separates it without a rule underneath as well.
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_app_mark),
                // The name is right beside it, and a reader announcing both says it twice.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppMarkSize),
            )
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Room between rail items, where Material leaves 4dp and the four destinations read as one block. */
private val RailItemGap = 16.dp

@Composable
private fun AppNavigation(
    appState: AppState,
    rail: Boolean,
    barState: MutableTransitionState<Boolean>,
) {
    if (rail) {
        // Material leaves a rail the page's own colour; this one carries the header's, so the two
        // meet as one piece of chrome around the page rather than as a rail that has vanished into
        // it. Destinations only - the app's mark is in the header, where both layouts have one.
        NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
        // Slides away while a page is scrolled down and comes back on the way up. It collapses as
        // well as slides, so the page takes the height rather than leaving a band of chrome behind.
        //
        // Aimed from here rather than from beside the state itself, so a scroll that hides the bar
        // recomposes the bar and not the whole shell around it.
        barState.targetState = LocalNavBarVisible.current.value
        AnimatedVisibility(
            visibleState = barState,
            enter = expandVertically() + slideInVertically { it },
            exit = slideOutVertically { it } + shrinkVertically(),
        ) {
            // No icon here: five destinations already divide a 411dp cover screen, and a sixth thing
            // taking width from them buys nothing the header above does not already say.
            //
            // Shorter than Material's 80dp. The gesture bar's inset is added rather than absorbed:
            // fixing the height alone leaves the items squeezed into what the system leaves over.
            // Padded rather than made taller: the room is wanted under the labels, and raising
            // NavigationBarHeight would put half of it above them as well. The bar is sized from
            // the same insets it pads itself with, so the two cannot drift apart.
            val barInsets = NavigationBarDefaults.windowInsets
                .add(WindowInsets(bottom = BarBottomPadding))
            NavigationBar(
                Modifier.height(
                    NavigationBarHeight + barInsets.asPaddingValues().calculateBottomPadding(),
                ),
                windowInsets = barInsets,
            ) {
                AppDestination.entries.forEach { destination ->
                    val selected = appState.destination == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { appState.navigate(destination) },
                        // A size down from the rail's, which has the room for it. At the bar's
                        // height an icon over a label needs the smaller glyph or the label clips.
                        icon = { NavigationIcon(destination, selected, BarIconSize) },
                        label = { Text(destination.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationIcon(
    destination: AppDestination,
    selected: Boolean,
    size: Dp = NavigationIconSize,
) {
    Icon(
        if (selected) destination.selectedIcon else destination.icon,
        contentDescription = destination.label,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun AppContent(activity: Activity, appState: AppState) {
    val snackbarHost = remember { SnackbarHostState() }

    // Arriving somewhere new with the navigation still hidden reads as the bar having gone missing,
    // and the page that hid it is no longer on screen to bring it back.
    val navBarVisible = LocalNavBarVisible.current
    LaunchedEffect(appState.destination) { navBarVisible.value = true }

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
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AppHeader()
            // Above the well rather than inside it, so a run starting does not push the rounded
            // edge down the screen.
            appState.busyLabel?.let { label ->
                BusyBar(label)
            }
            // The page sits in the chrome the way a card sits on the page, and takes the corner
            // radius the cards themselves use. The hairline is what keeps that edge drawn when a
            // card scrolls up under it, since a card is the same colour as the chrome around it.
            val wellShape = MaterialTheme.shapes.large.copy(
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp),
            )
            Surface(
                Modifier.fillMaxWidth().weight(1f)
                    .wellOutline(wellShape, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.background,
                shape = wellShape,
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
                        AppDestination.ANALYZE -> AnalyzeScreen(activity, appState)
                        AppDestination.RESULTS -> ResultsScreen(activity, appState)
                        AppDestination.INSIGHTS -> InsightsScreen(appState)
                        AppDestination.PORTFOLIO -> PortfolioScreen(appState)
                        AppDestination.SETTINGS -> SettingsScreen(appState)
                    }
                }
            }
        }
    }
}

/**
 * The page well's edge, drawn on every side but the bottom.
 *
 * A Surface border strokes the whole shape, and the bottom of this one now sits directly on the
 * navigation bar, where the line reads as an outline around the bar rather than as the edge of the
 * page. The other three sides still earn their place: a card scrolled up under the header is the
 * same colour as the chrome, and without the top edge there is nothing to say where the page starts.
 */
private fun Modifier.wellOutline(
    shape: CornerBasedShape,
    color: Color,
    width: Dp = 1.dp,
): Modifier = drawWithContent {
    drawContent()
    val stroke = width.toPx()
    // Half the stroke, so the line lands inside the bounds the way a border does rather than
    // straddling them and losing its outer half to the clip.
    val edge = stroke / 2
    val radius = shape.topStart.toPx(size, this)
    val path = Path().apply {
        // Up the left side, round the top, and back down the right - left open at the bottom.
        moveTo(edge, size.height)
        lineTo(edge, edge + radius)
        arcTo(Rect(edge, edge, edge + 2 * radius, edge + 2 * radius), 180f, 90f, false)
        lineTo(size.width - edge - radius, edge)
        arcTo(
            Rect(size.width - edge - 2 * radius, edge, size.width - edge, edge + 2 * radius),
            270f,
            90f,
            false,
        )
        lineTo(size.width - edge, size.height)
    }
    drawPath(path, color, style = Stroke(stroke))
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
        // A wallet rather than a chart: the other three destinations are all readings of the market,
        // and this one is the only place holding anything of the user's own.
        AppDestination.PORTFOLIO -> Icons.Outlined.AccountBalanceWallet
        AppDestination.SETTINGS -> Icons.Outlined.Settings
    }

private val AppDestination.selectedIcon: ImageVector
    get() = when (this) {
        AppDestination.ANALYZE -> Icons.Filled.AutoGraph
        AppDestination.RESULTS -> Icons.Filled.Assessment
        AppDestination.INSIGHTS -> Icons.Filled.Insights
        AppDestination.PORTFOLIO -> Icons.Filled.AccountBalanceWallet
        AppDestination.SETTINGS -> Icons.Filled.Settings
    }
