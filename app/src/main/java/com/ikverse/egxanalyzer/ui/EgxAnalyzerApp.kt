package com.ikverse.egxanalyzer.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // A rail beside the page on anything wider than a phone, and a floating bar over the page on a
    // phone, at the same breakpoint the suite used when it chose for itself. The two are laid out
    // differently enough - one takes width from the page, the other takes nothing at all - that they
    // are separate shells rather than one shell with a choice of navigation in it.
    val rail = windowWidth != WindowWidth.COMPACT
    // Held here rather than inside the pages: the bar is drawn over them, and it has to survive a
    // change of destination.
    val navBarVisible = remember { mutableStateOf(true) }
    // What each page has open lives on AppState rather than anywhere below this line - see
    // PageState, which explains why the two shells below cannot hold it between them.
    CompositionLocalProvider(
        LocalWindowWidth provides windowWidth,
        LocalNavBarVisible provides navBarVisible,
    ) {
        if (rail) {
            NavigationSuiteScaffoldLayout(
                navigationSuite = { AppRail(appState) },
                navigationSuiteType = NavigationSuiteType.NavigationRail,
            ) {
                // The suite scaffold used to paint this; the layout does not. Without it the window
                // background shows through behind the status and navigation bars as a pale band. It
                // is the chrome colour rather than the page's, because this is what shows behind the
                // system bars, and through the rounded corners where the page stops short of them.
                Surface(
                    // The layout does not do this for us the way the full NavigationSuiteScaffold
                    // does, and without it the page keeps clear of the gesture strip that the rail
                    // beside it is already holding: a second empty band, the width of the strip, in
                    // the chrome colour, between the bottom of the page and the first row of icons.
                    Modifier.fillMaxSize().consumeWindowInsets(
                        NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start),
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    AppContent(activity, appState, rail = true)
                }
            }
        } else {
            // The bar floats over the page rather than sitting under it, so the page is the full
            // height of the window and nothing is laid out around the bar. It consumes no insets of
            // its own: both it and the page clear the gesture strip separately, and neither hands
            // the other anything when the bar hides.
            Box(Modifier.fillMaxSize()) {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    AppContent(activity, appState, rail = false)
                }
                FloatingNavBar(appState, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** Icons carry the rail on a glance, so they are a size up from the Material default of 24dp. */
private val NavigationIconSize = 28.dp

/**
 * The pill's own height.
 *
 * Material leaves a bar 80dp. This one is drawn over the page rather than under it, so its height is
 * page it is covering, and a shorter pill covers less of it.
 */
private val NavigationBarHeight = 74.dp

/** How far the pill is held in from the sides of the window, which is what makes it read as a pill. */
private val PillSideMargin = 12.dp

/** Room under the pill, above whatever the gesture strip claims, so it floats rather than sits. */
private val PillBottomMargin = 10.dp

/**
 * The bottom of a page the pill is covering.
 *
 * Nothing is laid out around a floating bar, so a page that padded itself by nothing would end its
 * last card underneath one. Deliberately not the gesture strip's inset as well: the page already
 * clears that through `safeDrawing`, and the pill is measured up from the same line.
 *
 * Published because a page has to know it twice over - to hold its content clear of the pill, and to
 * decide whether hiding the pill is worth it at all. A page with less than this left to scroll shows
 * nothing new by hiding it.
 */
internal val NavBarFootprint = NavigationBarHeight + PillBottomMargin

/** A shade under the rail's 28dp: the pill has to leave room for a label under the glyph. */
private val BarIconSize = 26.dp

/**
 * A size down from Material's 12sp `labelMedium`.
 *
 * The pill is [PillSideMargin] narrower on each side than the bar it replaced, and five destinations
 * divide what is left of a 411dp cover screen into slots too narrow for "Portfolio" at the full
 * size. Shrinking the label rather than dropping it keeps all five named.
 */
private val BarLabelSize = 11.sp

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
                painterResource(R.drawable.ic_egx_notification),
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
private fun AppRail(appState: AppState) {
    // Material leaves a rail the page's own colour; this one carries the header's, so the two meet
    // as one piece of chrome around the page rather than as a rail that has vanished into it.
    // Destinations only - the app's mark is in the header, where both layouts have one.
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
}

/**
 * The phone's navigation: a pill lying on the page rather than a bar holding it up.
 *
 * Slightly see-through, so what is underneath carries on being visible through it and the pill reads
 * as sitting on the page rather than as a hole cut in it. A flat tint and not a blur - Compose has no
 * backdrop blur - so it is kept opaque enough that a dense card scrolling under it cannot make the
 * labels hard to read.
 */
@Composable
private fun FloatingNavBar(appState: AppState, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        // Slides away while a page is scrolled down and comes back on the way up. Only the pill
        // moves: the page is the full height of the window either way, so there is nothing to
        // collapse and nothing underneath waiting for the room.
        visible = LocalNavBarVisible.current.value,
        modifier = modifier
            // The strip is the pill's to clear, the same as it is the page's - neither is laid out
            // around the other.
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(start = PillSideMargin, end = PillSideMargin, bottom = PillBottomMargin),
        enter = fadeIn() + slideInVertically { it },
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        FloatingSurface(
            // The same corner the action button above it takes, which is the page's own card radius.
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.height(NavigationBarHeight),
        ) {
            // No icon here: five destinations already divide a 411dp cover screen, and a sixth thing
            // taking width from them buys nothing the header above does not already say.
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppDestination.entries.forEach { destination ->
                    PillItem(
                        destination = destination,
                        selected = appState.destination == destination,
                        onClick = { appState.navigate(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * One destination in the pill.
 *
 * Material's own `NavigationBarItem` asks for around 82dp - a fixed 16dp above and below a 32dp
 * indicator - and a bar that tall would cover a fifth of the page it floats over. Given less, it
 * overflows rather than shrinks, and the indicator came out tangent to the bar's top edge with the
 * label sitting on the bottom one. This is the same item built to the height available, so the block
 * sits centred with room on both sides.
 */
@Composable
private fun PillItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(IndicatorWidth, IndicatorHeight)
                .background(
                    if (selected) colors.secondaryContainer else Color.Transparent,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // A size down from the rail's, which has the room for it. At the pill's height an icon
            // over a label needs the smaller glyph or the label clips.
            NavigationIcon(
                destination,
                selected,
                BarIconSize,
                if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(IndicatorLabelGap))
        Text(
            destination.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = BarLabelSize),
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Wide enough to hold the icon clear of a five-slot 411dp screen's edges, and no wider. */
private val IndicatorWidth = 60.dp

/** Material leaves 32dp around a 24dp icon; this holds the same margin around a 26dp one. */
private val IndicatorHeight = 34.dp

/** Close enough that the icon and its name read as one item, far enough that they do not touch. */
private val IndicatorLabelGap = 3.dp

@Composable
private fun NavigationIcon(
    destination: AppDestination,
    selected: Boolean,
    size: Dp = NavigationIconSize,
    // The rail's items colour their own content; the pill's are drawn by hand and have to say.
    tint: Color = LocalContentColor.current,
) {
    Icon(
        if (selected) destination.selectedIcon else destination.icon,
        contentDescription = destination.label,
        modifier = Modifier.size(size),
        tint = tint,
    )
}

@Composable
private fun AppContent(activity: Activity, appState: AppState, rail: Boolean) {
    val snackbarHost = remember { SnackbarHostState() }

    // Arriving somewhere new with the navigation still hidden reads as the bar having gone missing,
    // and the page that hid it is no longer on screen to bring it back.
    val navBarVisible = LocalNavBarVisible.current
    LaunchedEffect(appState.destination) { navBarVisible.value = true }

    // Actions report their outcome once, in a few words, then the message is cleared so it cannot
    // reappear on the next recomposition. Raised as visuals rather than as a bare string, which is
    // what carries whether it worked through to the toast.
    LaunchedEffect(appState.statusMessage) {
        appState.statusMessage?.let { message ->
            snackbarHost.showSnackbar(ToastVisuals(message.text, message.succeeded))
            appState.consumeStatusMessage()
        }
    }

    // A toast is raised at the foot of the Scaffold, and on a phone the foot of the Scaffold is
    // underneath the bar - in position and, since the bar is drawn over this whole layer, in front
    // of it as well. Lifted clear, and lowered again when the bar goes, so a toast raised on a
    // scrolled page does not hang over the gap where the bar used to be.
    val toastClearance by animateDpAsState(
        if (!rail && navBarVisible.value) NavBarFootprint else 0.dp,
        label = "toast clearance",
    )

    Scaffold(
        // The navigation suite draws edge to edge, so the content keeps itself clear of the status
        // bar and any cutout.
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = {
            SnackbarHost(snackbarHost, Modifier.padding(bottom = toastClearance)) { AppToast(it) }
        },
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
                if (rail) {
                    // Screens cross-fade and rise slightly, so changing destination reads as moving
                    // somewhere rather than the content being swapped underneath. Beside a rail the
                    // destinations are a column of buttons rather than a row of pages, and sliding
                    // between them sideways would answer a gesture this layout does not offer.
                    AnimatedContent(
                        targetState = appState.destination,
                        transitionSpec = {
                            (fadeIn(initialAlpha = 0.4f) + slideInVertically { it / 24 })
                                .togetherWith(fadeOut())
                                .using(SizeTransform(clip = false))
                        },
                        label = "destination",
                    ) { destination ->
                        DestinationScreen(destination, activity, appState)
                    }
                } else {
                    DestinationPager(activity, appState)
                }
            }
        }
    }
}

/**
 * The destinations laid out side by side, swiped through as well as tapped.
 *
 * The bar's items and the pager are two ways of moving the same pointer, so each follows the other:
 * a tap animates the pager to the page, and a swipe names the page it settles nearest. Neither can
 * chase the other round: each checks where it already is before moving, and the pages a tap scrolls
 * over do not answer for themselves.
 *
 * Compact only. A tab a swipe away is the phone's gesture; beside a rail there is no reason to expect
 * it and a page-wide horizontal drag would be caught by the tables that scroll sideways.
 */
@Composable
private fun DestinationPager(activity: Activity, appState: AppState) {
    val destinations = AppDestination.entries
    val pager = rememberPagerState(
        initialPage = destinations.indexOf(appState.destination),
        pageCount = { destinations.size },
    )
    // Set while the shell is scrolling the pager itself, so the pages it travels over do not report
    // themselves back as arrivals.
    //
    // Without it the two effects below cancelled each other: a scroll to a far page changed the
    // current page on the way, the current page changed the destination, and the destination is this
    // effect's own key - so Compose restarted the effect and killed the scroll it was running.
    // Analyze to Settings stopped on Portfolio, where the long jump had put it; Results to Portfolio
    // stopped between two pages with nothing left to snap it.
    val scrolling = remember { mutableStateOf(false) }
    // Tapped, not swiped: the bar has already moved the destination, and the pages follow it.
    LaunchedEffect(appState.destination) {
        val target = destinations.indexOf(appState.destination)
        if (pager.currentPage != target) {
            scrolling.value = true
            try {
                pager.animateScrollToPage(target)
            } finally {
                scrolling.value = false
            }
        }
    }
    // Swiped: the current page turns over as the drag passes the halfway mark, so the bar lights up
    // the destination being dragged towards rather than waiting for the pages to settle. A drag never
    // sets the flag, so this stays true of every scroll the reader starts - including one that
    // interrupts a scroll the shell started.
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect {
            if (!scrolling.value) appState.navigate(destinations[it])
        }
    }
    HorizontalPager(
        pager,
        Modifier.fillMaxSize(),
        // The neighbours are built while the reader is sitting still rather than on the first frame
        // of their drag. A screen's first composition is expensive - measured at 150ms and worse on
        // a cold start, against a budget of 8ms - and with nothing held beyond the viewport that
        // whole cost landed in the middle of the gesture. It is the same work either way; this only
        // moves it somewhere there is no gesture for it to stutter.
        beyondViewportPageCount = 1,
    ) { page ->
        DestinationScreen(destinations[page], activity, appState)
    }
}

@Composable
private fun DestinationScreen(
    destination: AppDestination,
    activity: Activity,
    appState: AppState,
) {
    when (destination) {
        AppDestination.ANALYZE -> AnalyzeScreen(activity, appState)
        AppDestination.RESULTS -> ResultsScreen(activity, appState)
        AppDestination.INSIGHTS -> InsightsScreen(appState)
        AppDestination.PORTFOLIO -> PortfolioScreen(appState)
        AppDestination.SETTINGS -> SettingsScreen(appState)
    }
}

/**
 * The page well's edge, drawn on every side but the bottom.
 *
 * A Surface border strokes the whole shape, and the bottom of this one runs along the foot of the
 * window, where a line reads as the frame of the screen rather than as the edge of the page. The
 * other three sides still earn their place: a card scrolled up under the header is the same colour as
 * the chrome, and without the top edge there is nothing to say where the page starts.
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
